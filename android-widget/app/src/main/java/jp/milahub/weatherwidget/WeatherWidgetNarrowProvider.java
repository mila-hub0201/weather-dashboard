package jp.milahub.weatherwidget;

/**
 * 半分の幅のウィジェット。1ページに2時間ずつ表示する。
 *
 * <p>動きは標準幅と同じで、ページの刻み方と描画幅だけが違う。
 */
public final class WeatherWidgetNarrowProvider extends WeatherWidgetProvider {

    @Override
    WidgetVariant variant() {
        return WidgetVariant.NARROW;
    }
}
