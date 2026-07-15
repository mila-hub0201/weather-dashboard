package jp.milahub.weatherwidget;

final class ForecastHour {
    final String time;
    final int temperature;
    final int precipitationProbability;
    final double precipitation;
    final int weatherCode;

    ForecastHour(
            String time,
            int temperature,
            int precipitationProbability,
            double precipitation,
            int weatherCode
    ) {
        this.time = time;
        this.temperature = temperature;
        this.precipitationProbability = precipitationProbability;
        this.precipitation = precipitation;
        this.weatherCode = weatherCode;
    }
}
