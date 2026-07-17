package jp.milahub.weatherwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public final class WeatherWidgetProvider extends AppWidgetProvider {
    private static final String ACTION_PAGE = "jp.milahub.weatherwidget.PAGE";
    private static final String ACTION_REFRESH = "jp.milahub.weatherwidget.REFRESH";
    private static final String EXTRA_DELTA = "delta";
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter UPDATE_TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.JAPAN);
    private static final DateTimeFormatter UPDATE_DATE_TIME = DateTimeFormatter.ofPattern("M/d HH:mm", Locale.JAPAN);

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        WeatherUpdateJobService.ensurePeriodic(context);
        beginRefresh(context, appWidgetIds, false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            WeatherUpdateJobService.ensurePeriodic(context);
            beginRefresh(context, allWidgetIds(context), false);
            return;
        }
        if (ACTION_PAGE.equals(action)) {
            int appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                WidgetStore store = new WidgetStore(context);
                int current = store.getPage(appWidgetId);
                int delta = intent.getIntExtra(EXTRA_DELTA, 0);
                store.setPage(appWidgetId, Math.max(0, Math.min(WidgetStore.TOTAL_PAGES - 1, current + delta)));
                renderCached(context, appWidgetId, false, false);
            }
            return;
        }
        if (ACTION_REFRESH.equals(action)) {
            int requestedWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
            int[] appWidgetIds = requestedWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID
                    ? allWidgetIds(context)
                    : new int[]{requestedWidgetId};
            beginRefresh(context, appWidgetIds, true);
            return;
        }
        super.onReceive(context, intent);
    }

    @Override
    public void onEnabled(Context context) {
        WeatherUpdateJobService.ensurePeriodic(context);
    }

    @Override
    public void onDisabled(Context context) {
        WeatherUpdateJobService.cancelAll(context);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        WidgetStore store = new WidgetStore(context);
        for (int appWidgetId : appWidgetIds) store.removeWidget(appWidgetId);
    }

    static void requestRefresh(Context context) {
        beginRefresh(context, allWidgetIds(context), true);
    }

    static void redrawWidgets(Context context) {
        for (int appWidgetId : allWidgetIds(context)) {
            renderCached(context, appWidgetId, false, false);
        }
    }

    static void renderAll(Context context, boolean updating, boolean updateFailed) {
        for (int appWidgetId : allWidgetIds(context)) {
            renderCached(context, appWidgetId, updating, updateFailed);
        }
    }

    static void renderCached(Context context, int appWidgetId, boolean updating, boolean updateFailed) {
        WidgetStore store = new WidgetStore(context);
        List<ForecastHour> forecast = store.getForecast();
        RemoteViews views = buildViews(context, appWidgetId, store, forecast, updating, updateFailed);
        AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, views);
    }

    private static void beginRefresh(Context context, int[] appWidgetIds, boolean resetPages) {
        if (appWidgetIds.length == 0) return;
        WidgetStore store = new WidgetStore(context);
        for (int appWidgetId : appWidgetIds) {
            if (resetPages) store.setPage(appWidgetId, 0);
            // Clear a stale transient label before Android starts the update job.
            renderCached(context, appWidgetId, false, false);
        }
        if (!WeatherUpdateJobService.enqueueImmediate(context)) {
            store.markUpdateFailed(System.currentTimeMillis(), "Android rejected update job");
            for (int appWidgetId : appWidgetIds) {
                renderCached(context, appWidgetId, false, true);
            }
        }
    }

    private static RemoteViews buildViews(
            Context context,
            int appWidgetId,
            WidgetStore store,
            List<ForecastHour> forecast,
            boolean updating,
            boolean updateFailed
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.weather_widget);
        int page = store.getPage(appWidgetId);
        int start = page * WidgetStore.HOURS_PER_PAGE;
        boolean failed = updateFailed || store.hasUpdateError();

        views.setTextViewText(R.id.widget_location, store.getLocationName());
        views.setTextViewText(R.id.widget_page, (page + 1) + " / " + WidgetStore.TOTAL_PAGES);
        views.setTextViewText(
                R.id.widget_updated,
                updateLabel(store.getUpdatedAt(), store.getLastAttemptAt(), updating, failed)
        );
        views.setBoolean(R.id.widget_previous, "setEnabled", page > 0);
        views.setBoolean(R.id.widget_next, "setEnabled", page < WidgetStore.TOTAL_PAGES - 1);
        views.setImageViewBitmap(
                R.id.widget_chart,
                WidgetChartRenderer.render(context, forecast, start, failed)
        );
        views.setContentDescription(
                R.id.widget_chart,
                chartDescription(forecast, start, failed)
        );

        PendingIntent openApp = openAppIntent(context, appWidgetId);
        views.setOnClickPendingIntent(R.id.widget_location, openApp);
        views.setOnClickPendingIntent(R.id.widget_chart, openApp);
        views.setOnClickPendingIntent(R.id.widget_previous, pageIntent(context, appWidgetId, -1, 1));
        views.setOnClickPendingIntent(R.id.widget_next, pageIntent(context, appWidgetId, 1, 2));
        views.setOnClickPendingIntent(R.id.widget_refresh, refreshIntent(context, appWidgetId));
        return views;
    }

    private static PendingIntent openAppIntent(Context context, int appWidgetId) {
        Intent open = new Intent(context, MainActivity.class)
                .putExtra("tab", "weather")
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(
                context,
                appWidgetId * 10 + 4,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent pageIntent(Context context, int appWidgetId, int delta, int offset) {
        Intent page = new Intent(context, WeatherWidgetProvider.class)
                .setAction(ACTION_PAGE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(EXTRA_DELTA, delta);
        return PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + offset,
                page,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent refreshIntent(Context context, int appWidgetId) {
        Intent refresh = new Intent(context, WeatherWidgetProvider.class)
                .setAction(ACTION_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getBroadcast(
                context,
                appWidgetId * 10 + 3,
                refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    static int[] allWidgetIds(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        return manager.getAppWidgetIds(new ComponentName(context, WeatherWidgetProvider.class));
    }

    private static String updateLabel(
            long updatedAt,
            long lastAttemptAt,
            boolean updating,
            boolean updateFailed
    ) {
        if (updating) return "更新中…";
        if (updateFailed) {
            if (lastAttemptAt <= 0) return "更新失敗";
            return "更新失敗 " + UPDATE_TIME.format(
                    java.time.Instant.ofEpochMilli(lastAttemptAt).atZone(JAPAN)
            );
        }
        if (updatedAt <= 0) return "未更新";
        java.time.ZonedDateTime updated = java.time.Instant.ofEpochMilli(updatedAt).atZone(JAPAN);
        DateTimeFormatter format = updated.toLocalDate().equals(LocalDate.now(JAPAN))
                ? UPDATE_TIME
                : UPDATE_DATE_TIME;
        return "更新 " + format.format(updated);
    }

    static String hourLabel(String time) {
        try {
            LocalDateTime value = LocalDateTime.parse(time);
            LocalDate today = LocalDate.now(JAPAN);
            if (value.toLocalDate().equals(today.plusDays(1))) return "明" + value.getHour() + "時";
            if (!value.toLocalDate().equals(today)) return value.getMonthValue() + "/" + value.getDayOfMonth();
            return value.getHour() + "時";
        } catch (Exception ignored) {
            return "--";
        }
    }

    static String weatherLabel(int code) {
        if (code == 0) return "晴";
        if (code == 1 || code == 2) return "晴曇";
        if (code == 3) return "曇";
        if (code == 45 || code == 48) return "霧";
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) return "雨";
        if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) return "雪";
        if (code >= 95) return "雷";
        return "--";
    }

    static int weatherIcon(ForecastHour hour) {
        int code = hour.weatherCode;
        if (code == 0) return isNight(hour.time)
                ? R.drawable.ic_weather_clear_night
                : R.drawable.ic_weather_clear_day;
        if (code == 1 || code == 2) return R.drawable.ic_weather_partly_cloudy;
        if (code == 3) return R.drawable.ic_weather_cloudy;
        if (code == 45 || code == 48) return R.drawable.ic_weather_fog;
        if ((code >= 51 && code <= 67) || (code >= 80 && code <= 82)) {
            return R.drawable.ic_weather_rain;
        }
        if ((code >= 71 && code <= 77) || (code >= 85 && code <= 86)) {
            return R.drawable.ic_weather_snow;
        }
        if (code >= 95) return R.drawable.ic_weather_thunder;
        return R.drawable.ic_weather_unknown;
    }

    private static boolean isNight(String time) {
        try {
            int hour = LocalDateTime.parse(time).getHour();
            return hour < 6 || hour >= 18;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String chartDescription(
            List<ForecastHour> forecast,
            int start,
            boolean updateFailed
    ) {
        if (forecast.isEmpty()) return updateFailed ? "予報の更新に失敗しました" : "予報を取得中です";
        StringBuilder description = new StringBuilder();
        for (int slot = 0; slot < WidgetStore.HOURS_PER_PAGE; slot++) {
            int index = start + slot;
            if (index >= forecast.size()) break;
            ForecastHour hour = forecast.get(index);
            if (description.length() > 0) description.append("、");
            description.append(hourLabel(hour.time))
                    .append(" ")
                    .append(weatherLabel(hour.weatherCode))
                    .append(" ")
                    .append(hour.temperature)
                    .append("度 降水量")
                    .append(WidgetChartRenderer.formatPrecipitation(hour.precipitation));
        }
        return description.toString();
    }
}
