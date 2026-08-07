package jp.milahub.weatherwidget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;

/**
 * ウィジェットの表示バリエーション。
 *
 * <p>どちらも見られる予報は同じ 12 時間で、1ページに何時間を載せるかだけが違う。
 * 1スロットの幅を揃えてあるので、グラフの見た目は幅が変わっても同じになる。
 */
enum WidgetVariant {
    /** 標準幅。4時間ずつ3ページ。 */
    WIDE(WeatherWidgetProvider.class, 4, 600),
    /** 標準の半分の幅。2時間ずつ6ページ。 */
    NARROW(WeatherWidgetNarrowProvider.class, 2, 300);

    /** 表示できる予報の長さ。ページ構成が違ってもここは共通。 */
    static final int TOTAL_HOURS = 12;

    final Class<?> provider;
    final int hoursPerPage;
    final int chartWidth;

    WidgetVariant(Class<?> provider, int hoursPerPage, int chartWidth) {
        this.provider = provider;
        this.hoursPerPage = hoursPerPage;
        this.chartWidth = chartWidth;
    }

    int totalPages() {
        return TOTAL_HOURS / hoursPerPage;
    }

    ComponentName component(Context context) {
        return new ComponentName(context, provider);
    }

    int[] widgetIds(Context context) {
        return AppWidgetManager.getInstance(context).getAppWidgetIds(component(context));
    }

    /**
     * ウィジェットIDから、どちらのバリエーションかを調べる。
     * 設定中 (まだ設置が確定していない) のIDでも引けるよう、登録情報から判定する。
     */
    static WidgetVariant forWidgetId(Context context, int appWidgetId) {
        AppWidgetProviderInfo info =
                AppWidgetManager.getInstance(context).getAppWidgetInfo(appWidgetId);
        if (info != null && info.provider != null) {
            String className = info.provider.getClassName();
            for (WidgetVariant variant : values()) {
                if (variant.provider.getName().equals(className)) return variant;
            }
        }
        return WIDE;
    }

    static WidgetVariant of(Class<?> providerClass) {
        for (WidgetVariant variant : values()) {
            if (variant.provider.equals(providerClass)) return variant;
        }
        return WIDE;
    }
}
