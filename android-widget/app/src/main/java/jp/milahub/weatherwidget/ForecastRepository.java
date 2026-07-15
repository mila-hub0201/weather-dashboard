package jp.milahub.weatherwidget;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class ForecastRepository {
    private static final ZoneId JAPAN = ZoneId.of("Asia/Tokyo");

    List<ForecastHour> fetch(double latitude, double longitude) throws Exception {
        String endpoint = String.format(
                Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                        + "&hourly=temperature_2m,precipitation_probability,weather_code"
                        + "&timezone=Asia%%2FTokyo&forecast_days=3",
                latitude,
                longitude
        );
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "WeatherDashboardWidget/1.0");
        try {
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Open-Meteo returned HTTP " + status);
            }
            return parse(readAll(connection.getInputStream()));
        } finally {
            connection.disconnect();
        }
    }

    List<ForecastHour> parse(String json) throws Exception {
        JSONObject hourly = new JSONObject(json).getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temperatures = hourly.getJSONArray("temperature_2m");
        JSONArray probabilities = hourly.getJSONArray("precipitation_probability");
        JSONArray weatherCodes = hourly.getJSONArray("weather_code");

        LocalDateTime currentHour = LocalDateTime.now(JAPAN)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        int start = 0;
        for (int i = 0; i < times.length(); i++) {
            LocalDateTime candidate = LocalDateTime.parse(times.getString(i));
            if (!candidate.isBefore(currentHour)) {
                start = i;
                break;
            }
        }

        int available = Math.min(
                Math.min(times.length(), temperatures.length()),
                Math.min(probabilities.length(), weatherCodes.length())
        );
        List<ForecastHour> result = new ArrayList<>(24);
        for (int i = start; i < available && result.size() < 24; i++) {
            result.add(new ForecastHour(
                    times.getString(i),
                    (int) Math.round(temperatures.optDouble(i, 0)),
                    probabilities.isNull(i) ? 0 : probabilities.optInt(i, 0),
                    weatherCodes.isNull(i) ? -1 : weatherCodes.optInt(i, -1)
            ));
        }
        if (result.size() < 24) {
            throw new IOException("Open-Meteo returned fewer than 24 forecast hours");
        }
        return result;
    }

    private static String readAll(InputStream input) throws IOException {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
