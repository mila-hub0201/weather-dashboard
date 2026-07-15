package jp.milahub.weatherwidget;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedDispatcher;
import android.widget.FrameLayout;

import org.json.JSONObject;

import java.util.Locale;

public final class MainActivity extends Activity {
    private static final int REQUEST_WEB_LOCATION = 200;
    private static final String NATIVE_INSETS_STYLE =
            "(function(){var id='weather-widget-native-insets';"
                    + "if(document.getElementById(id))return;"
                    + "var s=document.createElement('style');s.id=id;"
                    + "s.textContent='@media(max-width:720px){.bottomnav{padding-bottom:8px!important}}';"
                    + "document.head.appendChild(s);})()";
    private static final String NATIVE_FAVORITES_SYNC =
            "(function(){var key='weather-dashboard-settings-v2';"
                    + "function sync(){try{var s=JSON.parse(localStorage.getItem(key)||'{}');"
                    + "if(window.WeatherWidget&&typeof WeatherWidget.setFavorites==='function')"
                    + "WeatherWidget.setFavorites(JSON.stringify(Array.isArray(s.favorites)?s.favorites:[]));"
                    + "}catch(e){}}sync();"
                    + "if(window.__weatherWidgetFavoritesHook)return;"
                    + "window.__weatherWidgetFavoritesHook=true;"
                    + "var original=Storage.prototype.setItem;"
                    + "Storage.prototype.setItem=function(k,v){original.call(this,k,v);"
                    + "if(k===key)setTimeout(sync,0);};})()";
    private WebView webView;
    private String pendingOrigin;
    private GeolocationPermissions.Callback pendingLocationCallback;
    private boolean nativeStateSynced;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));

        View statusBarShade = new View(this);
        statusBarShade.setBackgroundColor(getColor(R.color.system_bar_blue));
        FrameLayout.LayoutParams shadeParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                0
        );
        shadeParams.gravity = android.view.Gravity.TOP;
        root.addView(statusBarShade, shadeParams);
        root.setOnApplyWindowInsetsListener(
                (view, insets) -> applySystemBarInsets(content, statusBarShade, insets)
        );

        webView = new WebView(this);
        content.addView(webView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        ));
        setContentView(root);
        configureSystemBars();
        root.requestApplyInsets();
        WeatherWidgetProvider.redrawWidgets(this);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.addJavascriptInterface(new WidgetBridge(), "WeatherWidget");
        webView.setWebViewClient(new DashboardWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(
                    String origin,
                    GeolocationPermissions.Callback callback
            ) {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false);
                    return;
                }
                pendingOrigin = origin;
                pendingLocationCallback = callback;
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                }, REQUEST_WEB_LOCATION);
            }
        });

        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBack
            );
        }

        webView.loadUrl(buildDashboardUrl());
    }

    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.system_bar_blue));
        window.setNavigationBarColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= 29) {
            window.setNavigationBarContrastEnforced(true);
        }
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                controller.setSystemBarsAppearance(lightBars, lightBars);
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            );
        }
    }

    @SuppressWarnings("deprecation")
    private WindowInsets applySystemBarInsets(
            View content,
            View statusBarShade,
            WindowInsets insets
    ) {
        int left;
        int top;
        int right;
        int bottom;
        if (Build.VERSION.SDK_INT >= 30) {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
            left = bars.left;
            top = bars.top;
            right = bars.right;
            bottom = bars.bottom;
        } else {
            left = insets.getSystemWindowInsetLeft();
            top = insets.getSystemWindowInsetTop();
            right = insets.getSystemWindowInsetRight();
            bottom = insets.getSystemWindowInsetBottom();
        }
        content.setPadding(left, top, right, bottom);
        if (statusBarShade.getLayoutParams().height != top) {
            statusBarShade.getLayoutParams().height = top;
            statusBarShade.requestLayout();
        }
        return insets;
    }

    private String buildDashboardUrl() {
        WidgetStore store = new WidgetStore(this);
        return Uri.parse(BuildConfig.WEB_APP_URL).buildUpon()
                .appendQueryParameter("tab", "weather")
                .appendQueryParameter("lat", Double.toString(store.getLatitude()))
                .appendQueryParameter("lon", Double.toString(store.getLongitude()))
                .appendQueryParameter("name", store.getLocationName())
                .build()
                .toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_WEB_LOCATION || pendingLocationCallback == null) return;
        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        pendingLocationCallback.invoke(pendingOrigin, granted, false);
        pendingOrigin = null;
        pendingLocationCallback = null;
    }

    private void handleBack() {
        if (webView.canGoBack()) webView.goBack();
        else finishAfterTransition();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (Build.VERSION.SDK_INT < 33 && keyCode == KeyEvent.KEYCODE_BACK) {
            handleBack();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        webView.removeJavascriptInterface("WeatherWidget");
        webView.destroy();
        super.onDestroy();
    }

    private final class DashboardWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            Uri trusted = Uri.parse(BuildConfig.WEB_APP_URL);
            boolean isDashboard = trusted.getHost() != null
                    && trusted.getHost().equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith(trusted.getPath());
            if (isDashboard) return false;
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
            return true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            Uri uri = Uri.parse(url);
            Uri trusted = Uri.parse(BuildConfig.WEB_APP_URL);
            boolean isDashboard = trusted.getHost() != null
                    && trusted.getHost().equalsIgnoreCase(uri.getHost())
                    && uri.getPath() != null
                    && uri.getPath().startsWith(trusted.getPath());
            if (!isDashboard) return;

            view.evaluateJavascript(NATIVE_INSETS_STYLE, null);
            view.evaluateJavascript(NATIVE_FAVORITES_SYNC, null);

            if (!nativeStateSynced) {
                nativeStateSynced = true;
                WidgetStore store = new WidgetStore(MainActivity.this);
                String script = String.format(
                        Locale.US,
                        "(function(){try{var k='weather-dashboard-settings-v2';"
                                + "var s=JSON.parse(localStorage.getItem(k)||'{}');"
                                + "s.location={name:%s,latitude:%.6f,longitude:%.6f};"
                                + "s.fromGeolocation=true;s.tab='weather';"
                                + "localStorage.setItem(k,JSON.stringify(s));}catch(e){}})();",
                        JSONObject.quote(store.getLocationName()),
                        store.getLatitude(),
                        store.getLongitude()
                );
                view.evaluateJavascript(script, ignored -> view.reload());
                return;
            }

            view.evaluateJavascript(
                    "(function(){var b=document.querySelector('[data-tab=\"weather\"]');"
                            + "if(b&&!b.getAttribute('aria-selected'))b.click();})()",
                    null
            );
        }
    }

    private final class WidgetBridge {
        @JavascriptInterface
        public void setLocation(String name, double latitude, double longitude) {
            if (!WidgetStore.isValidLocation(latitude, longitude)) return;
            WidgetStore store = new WidgetStore(getApplicationContext());
            boolean changed = Math.abs(store.getLatitude() - latitude) > 0.00005
                    || Math.abs(store.getLongitude() - longitude) > 0.00005;
            store.setLocation(name, latitude, longitude);
            if (changed) WeatherWidgetProvider.requestRefresh(getApplicationContext());
        }

        @JavascriptInterface
        public void setFavorites(String favoritesJson) {
            new WidgetStore(getApplicationContext()).setFavoritesJson(favoritesJson);
        }
    }
}
