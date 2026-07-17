package jp.milahub.weatherwidget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class WeatherUpdateJobService extends JobService {
    static final int PERIODIC_JOB_ID = 41_201;
    static final int IMMEDIATE_JOB_ID = 41_202;

    private static final String TAG = "WeatherWidgetUpdate";
    private static final long PERIODIC_INTERVAL_MS = 4 * 60 * 60 * 1000L;
    private static final long PERIODIC_FLEX_MS = 30 * 60 * 1000L;
    private static final long IMMEDIATE_DEBOUNCE_MS = 5_000L;
    private static final long JOB_TIMEOUT_MS = 35_000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicLong LAST_IMMEDIATE_ENQUEUE = new AtomicLong(0L);

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private JobParameters activeJob;
    private Runnable timeoutTask;

    static void ensurePeriodic(Context context) {
        if (WeatherWidgetProvider.allWidgetIds(context).length == 0) return;
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        JobInfo existing = scheduler.getPendingJob(PERIODIC_JOB_ID);
        if (existing != null
                && existing.isPeriodic()
                && existing.getIntervalMillis() == PERIODIC_INTERVAL_MS) {
            return;
        }
        if (existing != null) scheduler.cancel(PERIODIC_JOB_ID);

        JobInfo job = new JobInfo.Builder(
                PERIODIC_JOB_ID,
                new ComponentName(context, WeatherUpdateJobService.class)
        )
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIODIC_INTERVAL_MS, PERIODIC_FLEX_MS)
                .build();
        try {
            int result = scheduler.schedule(job);
            Log.i(TAG, "Periodic update scheduled: " + (result == JobScheduler.RESULT_SUCCESS));
        } catch (RuntimeException error) {
            Log.e(TAG, "Periodic update could not be scheduled", error);
        }
    }

    static boolean enqueueImmediate(Context context) {
        ensurePeriodic(context);
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return false;
        long now = System.currentTimeMillis();
        if (now - LAST_IMMEDIATE_ENQUEUE.get() < IMMEDIATE_DEBOUNCE_MS) return true;

        JobInfo job = new JobInfo.Builder(
                IMMEDIATE_JOB_ID,
                new ComponentName(context, WeatherUpdateJobService.class)
        )
                .setMinimumLatency(0)
                .setOverrideDeadline(1_000)
                .setBackoffCriteria(30_000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        try {
            boolean scheduled = scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS;
            if (scheduled) LAST_IMMEDIATE_ENQUEUE.set(now);
            return scheduled;
        } catch (RuntimeException error) {
            Log.e(TAG, "Immediate update could not be scheduled", error);
            return false;
        }
    }

    static void cancelAll(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        scheduler.cancel(PERIODIC_JOB_ID);
        scheduler.cancel(IMMEDIATE_JOB_ID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        if (WeatherWidgetProvider.allWidgetIds(this).length == 0) {
            cancelAll(this);
            return false;
        }
        synchronized (this) {
            if (activeJob != null) return false;
            activeJob = params;
        }

        WidgetStore store = new WidgetStore(this);
        store.markUpdateStarted(System.currentTimeMillis());
        WeatherWidgetProvider.renderAll(this, true, false);
        Runnable timeout = () -> timeOutJob(params);
        synchronized (this) {
            if (activeJob == params) timeoutTask = timeout;
        }
        mainHandler.postDelayed(timeout, JOB_TIMEOUT_MS);
        Context appContext = getApplicationContext();
        EXECUTOR.execute(() -> runUpdate(appContext, params));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Runnable timeout;
        synchronized (this) {
            if (activeJob != params) return false;
            activeJob = null;
            timeout = timeoutTask;
            timeoutTask = null;
        }
        if (timeout != null) mainHandler.removeCallbacks(timeout);
        WidgetStore store = new WidgetStore(this);
        store.markUpdateFailed(System.currentTimeMillis(), "Update interrupted by Android");
        WeatherWidgetProvider.renderAll(this, false, true);
        return true;
    }

    private void runUpdate(Context context, JobParameters params) {
        List<ForecastHour> forecast;
        try {
            WidgetStore store = new WidgetStore(context);
            forecast = new ForecastRepository().fetch(
                    store.getLatitude(),
                    store.getLongitude()
            );
        } catch (Exception error) {
            finishWithFailure(context, params, error);
            return;
        }

        if (!claimJob(params)) return;
        try {
            new WidgetStore(context).saveForecast(forecast, System.currentTimeMillis());
            Log.i(TAG, "Forecast updated with " + forecast.size() + " hours");
            WeatherWidgetProvider.renderAll(context, false, false);
            jobFinished(params, false);
        } catch (Exception error) {
            String failure = error.getClass().getSimpleName() + ": " + error.getMessage();
            new WidgetStore(context).markUpdateFailed(System.currentTimeMillis(), failure);
            Log.e(TAG, "Forecast could not be saved", error);
            WeatherWidgetProvider.renderAll(context, false, true);
            jobFinished(params, params.getJobId() == IMMEDIATE_JOB_ID);
        }
    }

    private void finishWithFailure(Context context, JobParameters params, Exception error) {
        if (!claimJob(params)) return;
        String failure = error.getClass().getSimpleName() + ": " + error.getMessage();
        new WidgetStore(context).markUpdateFailed(System.currentTimeMillis(), failure);
        Log.e(TAG, "Forecast update failed", error);
        WeatherWidgetProvider.renderAll(context, false, true);
        jobFinished(params, params.getJobId() == IMMEDIATE_JOB_ID);
    }

    private void timeOutJob(JobParameters params) {
        if (!claimJob(params)) return;
        WidgetStore store = new WidgetStore(this);
        store.markUpdateFailed(System.currentTimeMillis(), "Forecast update timed out");
        Log.e(TAG, "Forecast update timed out");
        WeatherWidgetProvider.renderAll(this, false, true);
        jobFinished(params, params.getJobId() == IMMEDIATE_JOB_ID);
    }

    private boolean claimJob(JobParameters params) {
        Runnable timeout;
        synchronized (this) {
            if (activeJob != params) return false;
            activeJob = null;
            timeout = timeoutTask;
            timeoutTask = null;
        }
        if (timeout != null) mainHandler.removeCallbacks(timeout);
        return true;
    }
}
