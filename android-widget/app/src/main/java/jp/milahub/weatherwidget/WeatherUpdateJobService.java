package jp.milahub.weatherwidget;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class WeatherUpdateJobService extends JobService {
    static final int PERIODIC_JOB_ID = 41_201;
    static final int IMMEDIATE_JOB_ID = 41_202;

    private static final String TAG = "WeatherWidgetUpdate";
    private static final long PERIODIC_INTERVAL_MS = 30 * 60 * 1000L;
    private static final long PERIODIC_FLEX_MS = 10 * 60 * 1000L;
    private static final long IMMEDIATE_DEBOUNCE_MS = 5_000L;
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final AtomicLong LAST_IMMEDIATE_ENQUEUE = new AtomicLong(0L);

    private JobParameters activeJob;

    static void ensurePeriodic(Context context) {
        if (WeatherWidgetProvider.allWidgetIds(context).length == 0) return;
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return;

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
        Context appContext = getApplicationContext();
        EXECUTOR.execute(() -> runUpdate(appContext, params));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        synchronized (this) {
            if (activeJob == params) activeJob = null;
        }
        WidgetStore store = new WidgetStore(this);
        store.markUpdateFailed(System.currentTimeMillis(), "Update interrupted by Android");
        WeatherWidgetProvider.renderAll(this, false, true);
        return true;
    }

    private void runUpdate(Context context, JobParameters params) {
        boolean failed = false;
        String failure = null;
        try {
            WidgetStore store = new WidgetStore(context);
            List<ForecastHour> forecast = new ForecastRepository().fetch(
                    store.getLatitude(),
                    store.getLongitude()
            );
            store.saveForecast(forecast, System.currentTimeMillis());
            Log.i(TAG, "Forecast updated with " + forecast.size() + " hours");
        } catch (Exception error) {
            failed = true;
            failure = error.getClass().getSimpleName() + ": " + error.getMessage();
            new WidgetStore(context).markUpdateFailed(System.currentTimeMillis(), failure);
            Log.e(TAG, "Forecast update failed", error);
        }

        synchronized (this) {
            if (activeJob != params) return;
            activeJob = null;
        }
        WeatherWidgetProvider.renderAll(context, false, failed);
        jobFinished(params, failed && params.getJobId() == IMMEDIATE_JOB_ID);
    }
}
