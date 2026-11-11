package com.ali.carsystem;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class YouTubeLiteActivity extends Activity {
    private WebView web;
    private static final Set<String> BLOCKED_HOSTS = new HashSet<>(Arrays.asList(
        "doubleclick.net", "googleadservices.com", "googlesyndication.com",
        "google-analytics.com", "adservice.google.com"
    ));

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF080D13);
        getWindow().setNavigationBarColor(0xFF080D13);
        applyImmersiveMode();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF080D13);
        TextView bar = new TextView(this);
        bar.setText("ALI  •  YOUTUBE LITE                         رجوع");
        bar.setTextColor(0xFFF2F6F9);
        bar.setTextSize(16);
        bar.setTypeface(Typeface.DEFAULT_BOLD);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), 0, dp(16), 0);
        bar.setOnClickListener(v -> finish());
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(44)));

        web = new WebView(this);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
        settings.setUserAgentString(settings.getUserAgentString() + " ALIYouTubeLite/1.2");
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                if (isAdUrl(url)) return new WebResourceResponse("text/plain", "UTF-8",
                    new ByteArrayInputStream(new byte[0]));
                return super.shouldInterceptRequest(view, url);
            }
            @Override public WebResourceResponse shouldInterceptRequest(
                WebView view, WebResourceRequest request) {
                return shouldInterceptRequest(view, request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !url.startsWith("https://") && !url.startsWith("http://");
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        root.addView(web, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        web.loadUrl("https://m.youtube.com/");
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private boolean isAdUrl(String address) {
        try {
            String host = android.net.Uri.parse(address).getHost();
            if (host == null) return false;
            host = host.toLowerCase(Locale.US);
            for (String blocked : BLOCKED_HOSTS) {
                if (host.equals(blocked) || host.endsWith("." + blocked)) return true;
            }
        } catch (RuntimeException ignored) { }
        return false;
    }

    @Override protected void onPause() {
        if (web != null) {
            web.onPause();
            web.pauseTimers();
        }
        super.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            web.resumeTimers();
        }
        applyImmersiveMode();
    }

    @Override protected void onDestroy() {
        if (web != null) {
            web.stopLoading();
            web.loadUrl("about:blank");
            web.setWebChromeClient(null);
            web.setWebViewClient(null);
            web.removeAllViews();
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    @Override public void onBackPressed() { finish(); }
}
