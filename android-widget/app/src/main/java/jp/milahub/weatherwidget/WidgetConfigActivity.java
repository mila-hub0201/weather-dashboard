package jp.milahub.weatherwidget;

import android.Manifest;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

public final class WidgetConfigActivity extends Activity {
    private static final int REQUEST_LOCATION = 100;
    private static final long LOCATION_TIMEOUT_MS = 12_000;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private TextView status;
    private Button currentLocationButton;
    private Spinner favoritesSpinner;
    private List<SavedLocation> favoriteLocations;
    private boolean locationCompleted;
    private Location fallbackLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_widget_config);

        appWidgetId = getIntent().getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
        );
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        status = findViewById(R.id.config_status);
        currentLocationButton = findViewById(R.id.use_current_location);
        currentLocationButton.setOnClickListener(view -> requestCurrentLocation());
        findViewById(R.id.use_tokyo).setOnClickListener(view -> {
            new WidgetStore(this).setLocation(
                    WidgetStore.DEFAULT_NAME,
                    WidgetStore.DEFAULT_LATITUDE,
                    WidgetStore.DEFAULT_LONGITUDE
            );
            finishConfiguration();
        });

        favoritesSpinner = findViewById(R.id.favorite_locations);
        Button favoriteButton = findViewById(R.id.use_favorite_location);
        TextView favoritesEmpty = findViewById(R.id.favorite_locations_empty);
        favoriteLocations = new WidgetStore(this).getFavorites();
        if (favoriteLocations.isEmpty()) {
            favoritesSpinner.setVisibility(View.GONE);
            favoriteButton.setVisibility(View.GONE);
            favoritesEmpty.setVisibility(View.VISIBLE);
        } else {
            String[] names = new String[favoriteLocations.size()];
            for (int i = 0; i < favoriteLocations.size(); i++) {
                names[i] = favoriteLocations.get(i).name;
            }
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    this,
                    android.R.layout.simple_spinner_item,
                    names
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            favoritesSpinner.setAdapter(adapter);
            favoriteButton.setOnClickListener(view -> useSelectedFavorite());
        }
    }

    private void useSelectedFavorite() {
        int position = favoritesSpinner.getSelectedItemPosition();
        if (position < 0 || position >= favoriteLocations.size()) return;
        SavedLocation selected = favoriteLocations.get(position);
        new WidgetStore(this).setLocation(selected.name, selected.latitude, selected.longitude);
        finishConfiguration();
    }

    private void requestCurrentLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
            }, REQUEST_LOCATION);
            return;
        }
        loadCurrentLocation();
    }

    private void loadCurrentLocation() {
        status.setText(R.string.location_fetching);
        currentLocationButton.setEnabled(false);
        locationCompleted = false;
        LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
        fallbackLocation = bestLastKnown(manager);

        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_COARSE);
        String provider = manager.getBestProvider(criteria, true);
        if (provider == null) {
            completeLocation(fallbackLocation);
            return;
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                manager.getCurrentLocation(
                        provider,
                        new CancellationSignal(),
                        getMainExecutor(),
                        this::completeLocation
                );
            } else {
                manager.requestSingleUpdate(provider, new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        completeLocation(location);
                    }

                    @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    @Override public void onProviderEnabled(String provider) {}
                    @Override public void onProviderDisabled(String provider) {}
                }, Looper.getMainLooper());
            }
            new Handler(Looper.getMainLooper()).postDelayed(
                    () -> completeLocation(fallbackLocation),
                    LOCATION_TIMEOUT_MS
            );
        } catch (SecurityException error) {
            completeLocation(fallbackLocation);
        }
    }

    private Location bestLastKnown(LocationManager manager) {
        Location best = null;
        try {
            List<String> providers = manager.getProviders(true);
            for (String provider : providers) {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null || candidate.getTime() > best.getTime())) {
                    best = candidate;
                }
            }
        } catch (SecurityException ignored) {
            return null;
        }
        return best;
    }

    private void completeLocation(Location location) {
        if (locationCompleted) return;
        locationCompleted = true;
        if (location == null) {
            status.setText(R.string.location_unavailable);
            currentLocationButton.setEnabled(true);
            return;
        }
        new WidgetStore(this).setLocation(
                getString(R.string.current_location_name),
                location.getLatitude(),
                location.getLongitude()
        );
        finishConfiguration();
    }

    private void finishConfiguration() {
        WeatherWidgetProvider.renderCached(this, appWidgetId, true, false);
        WeatherWidgetProvider.requestRefresh(this);
        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_LOCATION) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadCurrentLocation();
        } else {
            status.setText(R.string.location_permission_needed);
        }
    }
}
