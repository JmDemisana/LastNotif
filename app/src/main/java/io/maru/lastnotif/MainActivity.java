package io.maru.lastnotif;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

/**
 * Single-activity host for the settings WebView.
 *
 * The WebView loads assets/index.html which communicates back via
 * the "NativeBridge" JavaScript interface (see LastNotifNativeBridge).
 *
 * On first launch: asks for POST_NOTIFICATIONS permission (Android 13+).
 */
public class MainActivity extends AppCompatActivity {

    private WebView webView;

    private final ActivityResultLauncher<String> notifPermLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            granted -> { /* permission result handled silently */ }
        );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        webView = new WebView(this);
        setContentView(webView);

        configureWebView();

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void configureWebView() {
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Inject the native bridge
        webView.addJavascriptInterface(
            new LastNotifNativeBridge(this), "NativeBridge"
        );

        // Keep the WebView simple — no external navigation
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(
                    WebView view, android.webkit.WebResourceRequest request) {
                // Only allow local asset URLs
                String url = request.getUrl().toString();
                return !url.startsWith("file://");
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Notify JS that we're back so it can refresh status
        if (webView != null) {
            webView.post(() ->
                webView.evaluateJavascript("if(window.onAppResume) onAppResume();", null)
            );
        }
    }

    @Override
    @android.annotation.SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
