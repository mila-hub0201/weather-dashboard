package jp.milahub.weatherwidget;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Bundle;

enum WidgetVariant {
    WIDE(4, 600, R.layout.weather_widget),
    NARROW(2, 300, R.layout.weather_widget_narrow);

    static final int TOTAL_HOURS = 12;

    private static final int NARROW_MAX_WIDTH_DP = 180;
    private static final String SAMSUNG_COLUMN_SPAN = "semAppWidgetColumnSpan";

    final int hoursPerPage;
    final int chartWidth;
    final int layoutId;

    WidgetVariant(int hoursPerPage, int chartWidth, int layoutId) {
        this.hoursPerPage = hoursPerPage;
        this.chartWidth = chartWidth;
        this.layoutId = layoutId;
    }

    int totalPages() {
        return TOTAL_HOURS / hoursPerPage;
    }

    static WidgetVariant forWidgetId(Context context, int appWidgetId) {
        Bundle options = AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId);
        // One UI reports the actual grid span, while its width value is much wider than AOSP's.
        int columnSpan = options.getInt(SAMSUNG_COLUMN_SPAN, 0);
        if (columnSpan == 2) return NARROW;
        if (columnSpan > 0) return WIDE;
        int minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        if (minWidth > 0 && minWidth <= NARROW_MAX_WIDTH_DP) return NARROW;
        return WIDE;
    }
}
