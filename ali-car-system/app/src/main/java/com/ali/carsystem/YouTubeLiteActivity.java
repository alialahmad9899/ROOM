package com.ali.carsystem;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.Typeface;

public final class YouTubeLiteActivity extends Activity {
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);getWindow().setStatusBarColor(0xFF080D13);getWindow().setNavigationBarColor(0xFF080D13);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(0xFF080D13);
        TextView bar=new TextView(this);bar.setText("ALI  •  YOUTUBE LITE                         رجوع");bar.setTextColor(0xFFF2F6F9);bar.setTextSize(16);bar.setTypeface(Typeface.DEFAULT_BOLD);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(16),0,dp(16),0);
        bar.setOnClickListener(v->finish());root.addView(bar,new LinearLayout.LayoutParams(-1,dp(48)));
        WebView web=new WebView(this);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setDatabaseEnabled(true);settings.setLoadsImagesAutomatically(true);settings.setMediaPlaybackRequiresUserGesture(true);settings.setSupportZoom(false);settings.setBuiltInZoomControls(false);
        settings.setUserAgentString(settings.getUserAgentString()+" ALIYouTubeLite/1.0");
        web.setWebViewClient(new WebViewClient());web.setWebChromeClient(new WebChromeClient());web.loadUrl("https://m.youtube.com/");
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));setContentView(root);
    }
    @Override public void onBackPressed(){finish();}
}
