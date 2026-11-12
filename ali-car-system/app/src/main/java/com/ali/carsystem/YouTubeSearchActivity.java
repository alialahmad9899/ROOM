package com.ali.carsystem;

import android.app.Activity;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public final class YouTubeSearchActivity extends Activity {
    private EditText query;
    private TextView status;
    private VideoAdapter adapter;
    private AsyncTask<String, Void, List<VideoItem>> task;

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private void immersive() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        immersive();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(9), dp(12), dp(8));
        root.setBackgroundColor(0xFF050B13);

        TextView title = text("البحث عن الفيديوهات", 22, Color.WHITE, true);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        LinearLayout search = new LinearLayout(this);
        search.setGravity(Gravity.CENTER_VERTICAL);
        query = new EditText(this);
        query.setSingleLine(true);
        query.setTextColor(0xFF111111);
        query.setHintTextColor(0xFF666666);
        query.setHint("اكتب اسم الفيديو أو كلمات البحث");
        android.graphics.drawable.GradientDrawable inputBg=new android.graphics.drawable.GradientDrawable();
        inputBg.setColor(0xFFFFFFFF);inputBg.setCornerRadius(dp(12));inputBg.setStroke(dp(1),0xFF84DFFF);query.setBackground(inputBg);
        query.setPadding(dp(12),0,dp(12),0);
        query.setTextSize(15);
        query.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        query.setTextDirection(View.TEXT_DIRECTION_RTL);
        search.addView(query, new LinearLayout.LayoutParams(0, dp(46), 1));

        Button go = new Button(this);
        go.setText("بحث");
        search.addView(go, new LinearLayout.LayoutParams(dp(92), dp(46)));
        root.addView(search);

        status = text("اكتب عبارة البحث بالعربية أو الإنجليزية.", 12, 0xFFFFD88A, false);
        root.addView(status, new LinearLayout.LayoutParams(-1, dp(32)));

        RecyclerView list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new VideoAdapter(this::play);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        Button back = new Button(this);
        back.setText("العودة إلى الواجهة الرئيسية");
        back.setOnClickListener(v -> finish());
        root.addView(back, new LinearLayout.LayoutParams(-1, dp(42)));

        go.setOnClickListener(v -> search());
        query.setOnEditorActionListener((v, action, event) -> {
            search();
            return true;
        });
        setContentView(root);
    }

    private void search() {
        String value = query.getText().toString().trim();
        if (value.length() < 2) {
            query.setError("اكتب كلمتين على الأقل للبحث");
            return;
        }
        if (task != null) task.cancel(true);
        adapter.setItems(null);
        status.setText("جارٍ الاتصال بخوادم البحث...");
        task = new AsyncTask<String, Void, List<VideoItem>>() {
            Exception failure;
            @Override protected List<VideoItem> doInBackground(String... values) {
                try {
                    return VideoApiClient.search(getApplicationContext(), values[0]);
                } catch (Exception error) {
                    failure = error;
                    return null;
                }
            }
            @Override protected void onPostExecute(List<VideoItem> result) {
                if (isFinishing()) return;
                if (result == null || result.isEmpty()) {
                    status.setText(failure == null
                            ? "لم نعثر على نتائج. جرّب كلمات أخرى."
                            : failure.getMessage());
                    return;
                }
                adapter.setItems(result);
                status.setText("تم العثور على " + result.size() + " نتيجة. اختر فيديو للتشغيل.");
            }
        }.execute(value);
    }

    private void play(VideoItem item) {
        android.content.Intent intent = new android.content.Intent(this, VideoPlayerActivity.class);
        intent.putExtra("id", item.id);
        intent.putExtra("title", item.title);
        intent.putExtra("author", item.author);
        intent.putExtra("backend", item.backend);
        intent.putExtra("base", item.baseUrl);
        startActivity(intent);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(size);
        view.setTypeface(android.graphics.Typeface.create("sans-serif",
                bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL));
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        return view;
    }

    @Override protected void onResume() {
        super.onResume();
        immersive();
    }

    @Override protected void onDestroy() {
        if (task != null) task.cancel(true);
        super.onDestroy();
    }
}
