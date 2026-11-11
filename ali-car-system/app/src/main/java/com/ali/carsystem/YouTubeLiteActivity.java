package com.ali.carsystem;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Keep YouTube out of the launcher's process.
 * Android 5 devices with 1 GB RAM struggle with an embedded modern YouTube WebView.
 */
public final class YouTubeLiteActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent app = getPackageManager().getLaunchIntentForPackage("com.google.android.youtube");
            if (app != null) {
                app.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(app);
            } else {
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://m.youtube.com/"));
                browser.addCategory(Intent.CATEGORY_BROWSABLE);
                startActivity(browser);
            }
        } catch (Exception error) {
            Toast.makeText(this, "تعذّر فتح YouTube أو المتصفح", Toast.LENGTH_LONG).show();
        }
        finish();
    }
}