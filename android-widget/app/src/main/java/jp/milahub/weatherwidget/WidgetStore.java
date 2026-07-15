package jp.milahub.weatherwidget;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class WidgetStore {
    static final String DEFAULT_NAME = "東京 (千代田区)";
    static final double DEFAULT_LATITUDE = 35.6812;
    static final double DEFAULT_LONGITUDE = 139.7671;
    static final int HOURS_PER_PAGE = 4;
    static final int TOTAL_PAGES = 6;

    private static final String PREFS = "weather_widget";
    private static final String KEY_NAME = "location_name";
    private static final String KEY_LATITUDE = "location_latitude";
    private static final String KEY_LONGITUDE = "location_longitude";
    private static final String KEY_FORECAST = "forecast_json";
    private static final String KEY_FORECAST_LOCATION = "forecast_location";
    private static final String KEY_UPDATED = "forecast_updated";
    private static final String KEY_FAVORITES = "favorite_locations";
    private static final int MAX_FAVORITES = 12;

    private final SharedPreferences prefs;

    WidgetStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String getLocationName() {
        return prefs.getString(KEY_NAME, DEFAULT_NAME);
    }

    double getLatitude() {
        return Double.longBitsToDouble(prefs.getLong(
                KEY_LATITUDE,
                Double.doubleToRawLongBits(DEFAULT_LATITUDE)
        ));
    }

    double getLongitude() {
        return Double.longBitsToDouble(prefs.getLong(
                KEY_LONGITUDE,
                Double.doubleToRawLongBits(DEFAULT_LONGITUDE)
        ));
    }

    void setLocation(String name, double latitude, double longitude) {
        if (!isValidLocation(latitude, longitude)) return;
        String safeName = name == null || name.trim().isEmpty() ? "現在地" : name.trim();
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);
        boolean changed = locationKey(latitude, longitude).equals(currentLocationKey()) == false;
        SharedPreferences.Editor editor = prefs.edit()
                .putString(KEY_NAME, safeName)
                .putLong(KEY_LATITUDE, Double.doubleToRawLongBits(latitude))
                .putLong(KEY_LONGITUDE, Double.doubleToRawLongBits(longitude));
        if (changed) {
            editor.remove(KEY_FORECAST)
                    .remove(KEY_FORECAST_LOCATION)
                    .remove(KEY_UPDATED);
        }
        editor.apply();
    }

    void saveForecast(List<ForecastHour> hours, long updatedAt) throws JSONException {
        JSONArray items = new JSONArray();
        for (ForecastHour hour : hours) {
            items.put(new JSONObject()
                    .put("time", hour.time)
                    .put("temperature", hour.temperature)
                    .put("precipitationProbability", hour.precipitationProbability)
                    .put("precipitation", hour.precipitation)
                    .put("weatherCode", hour.weatherCode));
        }
        prefs.edit()
                .putString(KEY_FORECAST, items.toString())
                .putString(KEY_FORECAST_LOCATION, currentLocationKey())
                .putLong(KEY_UPDATED, updatedAt)
                .apply();
    }

    List<ForecastHour> getForecast() {
        List<ForecastHour> result = new ArrayList<>();
        if (!currentLocationKey().equals(prefs.getString(KEY_FORECAST_LOCATION, ""))) {
            return result;
        }
        String raw = prefs.getString(KEY_FORECAST, "[]");
        try {
            JSONArray items = new JSONArray(raw);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                result.add(new ForecastHour(
                        item.getString("time"),
                        item.getInt("temperature"),
                        item.getInt("precipitationProbability"),
                        item.optDouble("precipitation", 0),
                        item.getInt("weatherCode")
                ));
            }
        } catch (JSONException ignored) {
            result.clear();
        }
        return result;
    }

    long getUpdatedAt() {
        return prefs.getLong(KEY_UPDATED, 0L);
    }

    void setFavoritesJson(String favoritesJson) {
        try {
            JSONArray source = new JSONArray(favoritesJson == null ? "[]" : favoritesJson);
            JSONArray safeFavorites = new JSONArray();
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < source.length() && safeFavorites.length() < MAX_FAVORITES; i++) {
                JSONObject item = source.optJSONObject(i);
                if (item == null) continue;
                double latitude = item.optDouble("latitude", Double.NaN);
                double longitude = item.optDouble("longitude", Double.NaN);
                if (!isValidLocation(latitude, longitude)) continue;
                String key = locationKey(latitude, longitude);
                if (!seen.add(key)) continue;
                String name = sanitizeName(item.optString("name", "お気に入り"));
                safeFavorites.put(new JSONObject()
                        .put("name", name)
                        .put("latitude", latitude)
                        .put("longitude", longitude));
            }
            prefs.edit().putString(KEY_FAVORITES, safeFavorites.toString()).apply();
        } catch (JSONException ignored) {
            // Keep the last valid favorites when malformed data reaches the bridge.
        }
    }

    List<SavedLocation> getFavorites() {
        List<SavedLocation> favorites = new ArrayList<>();
        try {
            JSONArray items = new JSONArray(prefs.getString(KEY_FAVORITES, "[]"));
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) continue;
                double latitude = item.optDouble("latitude", Double.NaN);
                double longitude = item.optDouble("longitude", Double.NaN);
                if (!isValidLocation(latitude, longitude)) continue;
                favorites.add(new SavedLocation(
                        sanitizeName(item.optString("name", "お気に入り")),
                        latitude,
                        longitude
                ));
            }
        } catch (JSONException ignored) {
            favorites.clear();
        }
        return favorites;
    }

    int getPage(int appWidgetId) {
        return normalizePage(prefs.getInt(pageKey(appWidgetId), 0));
    }

    void setPage(int appWidgetId, int page) {
        prefs.edit().putInt(pageKey(appWidgetId), normalizePage(page)).apply();
    }

    void removeWidget(int appWidgetId) {
        prefs.edit().remove(pageKey(appWidgetId)).apply();
    }

    private String currentLocationKey() {
        return locationKey(getLatitude(), getLongitude());
    }

    private static String locationKey(double latitude, double longitude) {
        return String.format(Locale.US, "%.4f,%.4f", latitude, longitude);
    }

    private static String pageKey(int appWidgetId) {
        return "page_" + appWidgetId;
    }

    private static int normalizePage(int page) {
        int value = page % TOTAL_PAGES;
        return value < 0 ? value + TOTAL_PAGES : value;
    }

    static boolean isValidLocation(double latitude, double longitude) {
        return Double.isFinite(latitude)
                && Double.isFinite(longitude)
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }

    private static String sanitizeName(String name) {
        String safeName = name == null || name.trim().isEmpty() ? "お気に入り" : name.trim();
        return safeName.length() > 40 ? safeName.substring(0, 40) : safeName;
    }
}
