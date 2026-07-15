package jp.milahub.weatherwidget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

import java.util.List;
import java.util.Locale;

final class WidgetChartRenderer {
    private static final int WIDTH = 600;
    private static final int HEIGHT = 250;
    private static final int SLOT_COUNT = WidgetStore.HOURS_PER_PAGE;
    private static final int COLOR_TEXT = Color.rgb(23, 33, 43);
    private static final int COLOR_MUTED = Color.rgb(82, 96, 109);
    private static final int COLOR_LINE = Color.rgb(217, 119, 6);
    private static final int COLOR_RAIN = Color.rgb(59, 130, 196);
    private static final int COLOR_GRID = Color.rgb(217, 226, 236);

    private WidgetChartRenderer() {}

    static Bitmap render(
            Context context,
            List<ForecastHour> forecast,
            int start,
            boolean updateFailed
    ) {
        Bitmap bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        paint.setColor(Color.rgb(248, 250, 252));
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(0, 0, WIDTH, HEIGHT), 14, 14, paint);

        if (forecast.isEmpty()) {
            paint.setColor(COLOR_MUTED);
            paint.setTextSize(24);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            canvas.drawText(updateFailed ? "更新できませんでした" : "予報を取得中…", WIDTH / 2f, 132, paint);
            return bitmap;
        }

        ForecastHour[] hours = new ForecastHour[SLOT_COUNT];
        int minTemperature = Integer.MAX_VALUE;
        int maxTemperature = Integer.MIN_VALUE;
        double maxPrecipitation = 0;
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            int index = start + slot;
            if (index >= forecast.size()) continue;
            ForecastHour hour = forecast.get(index);
            hours[slot] = hour;
            minTemperature = Math.min(minTemperature, hour.temperature);
            maxTemperature = Math.max(maxTemperature, hour.temperature);
            maxPrecipitation = Math.max(maxPrecipitation, hour.precipitation);
        }

        drawGrid(canvas, paint);
        drawWeather(context, canvas, paint, hours);
        drawPrecipitation(canvas, paint, hours, Math.max(1.0, maxPrecipitation));
        drawTemperature(canvas, paint, hours, minTemperature, maxTemperature);
        drawTimes(canvas, paint, hours);
        return bitmap;
    }

    private static void drawGrid(Canvas canvas, Paint paint) {
        paint.setColor(COLOR_GRID);
        paint.setStrokeWidth(2);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawLine(12, 201, WIDTH - 12, 201, paint);
        for (int slot = 1; slot < SLOT_COUNT; slot++) {
            float x = WIDTH * slot / (float) SLOT_COUNT;
            canvas.drawLine(x, 76, x, 229, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private static void drawWeather(
            Context context,
            Canvas canvas,
            Paint paint,
            ForecastHour[] hours
    ) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(18);
        paint.setColor(COLOR_MUTED);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ForecastHour hour = hours[slot];
            if (hour == null) continue;
            float x = centerX(slot);
            Drawable icon = context.getDrawable(WeatherWidgetProvider.weatherIcon(hour));
            if (icon != null) {
                int size = 44;
                icon.setBounds(
                        Math.round(x) - size / 2,
                        4,
                        Math.round(x) + size / 2,
                        4 + size
                );
                icon.draw(canvas);
            }
            canvas.drawText(WeatherWidgetProvider.weatherLabel(hour.weatherCode), x, 69, paint);
        }
    }

    private static void drawPrecipitation(
            Canvas canvas,
            Paint paint,
            ForecastHour[] hours,
            double scale
    ) {
        final float baseline = 200;
        final float maxHeight = 56;
        paint.setColor(Color.argb(82, 59, 130, 196));
        paint.setStyle(Paint.Style.FILL);
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ForecastHour hour = hours[slot];
            if (hour == null) continue;
            float x = centerX(slot);
            float height = (float) (Math.max(0, hour.precipitation) / scale * maxHeight);
            if (hour.precipitation > 0 && height < 5) height = 5;
            if (height > 0) {
                canvas.drawRoundRect(new RectF(x - 25, baseline - height, x + 25, baseline), 7, 7, paint);
            }

            paint.setColor(COLOR_RAIN);
            paint.setTextSize(16);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            canvas.drawText(
                    formatPrecipitation(hour.precipitation),
                    x,
                    194,
                    paint
            );
            paint.setColor(Color.argb(82, 59, 130, 196));
        }
    }

    private static void drawTemperature(
            Canvas canvas,
            Paint paint,
            ForecastHour[] hours,
            int minTemperature,
            int maxTemperature
    ) {
        float span = Math.max(4, maxTemperature - minTemperature);
        Path path = new Path();
        boolean started = false;
        float[] points = new float[SLOT_COUNT * 2];
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ForecastHour hour = hours[slot];
            if (hour == null) continue;
            float x = centerX(slot);
            float y = 106 + ((maxTemperature - hour.temperature) / span) * 54;
            points[slot * 2] = x;
            points[slot * 2 + 1] = y;
            if (!started) {
                path.moveTo(x, y);
                started = true;
            } else {
                path.lineTo(x, y);
            }
        }

        paint.setColor(COLOR_LINE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        canvas.drawPath(path, paint);

        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ForecastHour hour = hours[slot];
            if (hour == null) continue;
            float x = points[slot * 2];
            float y = points[slot * 2 + 1];
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, 8, paint);
            paint.setColor(COLOR_LINE);
            canvas.drawCircle(x, y, 5, paint);

            paint.setColor(COLOR_TEXT);
            paint.setTextSize(23);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            canvas.drawText(hour.temperature + "°", x, y - 12, paint);
        }
    }

    private static void drawTimes(Canvas canvas, Paint paint, ForecastHour[] hours) {
        paint.setColor(COLOR_MUTED);
        paint.setTextSize(19);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ForecastHour hour = hours[slot];
            if (hour == null) continue;
            canvas.drawText(WeatherWidgetProvider.hourLabel(hour.time), centerX(slot), 237, paint);
        }
    }

    static String formatPrecipitation(double amount) {
        if (!Double.isFinite(amount) || amount <= 0) return "0mm";
        if (Math.abs(amount - Math.rint(amount)) < 0.05) {
            return String.format(Locale.JAPAN, "%.0fmm", amount);
        }
        return String.format(Locale.JAPAN, "%.1fmm", amount);
    }

    private static float centerX(int slot) {
        return WIDTH * (slot + 0.5f) / SLOT_COUNT;
    }
}
