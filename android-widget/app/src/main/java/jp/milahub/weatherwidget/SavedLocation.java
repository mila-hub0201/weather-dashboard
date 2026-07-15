package jp.milahub.weatherwidget;

final class SavedLocation {
    final String name;
    final double latitude;
    final double longitude;

    SavedLocation(String name, double latitude, double longitude) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }
}
