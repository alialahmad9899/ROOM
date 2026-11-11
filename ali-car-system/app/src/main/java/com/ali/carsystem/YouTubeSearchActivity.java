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
    private AsyncTask<String,Void,List<VideoItem>> task;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void immersive(){getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|
        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);immersive();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(12),dp(9),dp(12),dp(8));root.setBackgroundColor(0xFF050B13);
        TextView title=new TextView(this);title.setText("ALI VIDEO  •  Native MP4");title.setTextColor(Color.WHITE);title.setTextSize(22);title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title,new LinearLayout.LayoutParams(-1,dp(42)));
        LinearLayout search=new LinearLayout(this);search.setGravity(Gravity.CENTER_VERTICAL);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(Color.WHITE);query.setHintTextColor(0xFF8AA4B7);query.setHint("ابحث عن فيديو");query.setTextSize(15);
        search.addView(query,new LinearLayout.LayoutParams(0,dp(46),1));
        Button go=new Button(this);go.setText("بحث");search.addView(go,new LinearLayout.LayoutParams(dp(100),dp(46)));root.addView(search);
        status=new TextView(this);status.setTextColor(0xFFFFD88A);status.setText("Piped ثم Invidious — اتصال مباشر دون WebView");root.addView(status,new LinearLayout.LayoutParams(-1,dp(30)));
        RecyclerView list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new VideoAdapter(this::play);list.setAdapter(adapter);
        root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        Button back=new Button(this);back.setText("رجوع إلى ALI CAR");back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(42)));
        go.setOnClickListener(v->search());query.setOnEditorActionListener((v,a,e)->{search();return true;});setContentView(root);
    }
    private void search(){
        String q=query.getText().toString().trim();if(q.length()<2){query.setError("اكتب كلمتين على الأقل");return;}
        if(task!=null)task.cancel(true);status.setText("جارٍ تجربة خوادم Piped وInvidious...");
        task=new AsyncTask<String,Void,List<VideoItem>>(){
            Exception error;
            protected List<VideoItem> doInBackground(String...v){try{return VideoApiClient.search(v[0]);}catch(Exception e){error=e;return null;}}
            protected void onPostExecute(List<VideoItem> values){if(isFinishing())return;if(values==null){status.setText("تعذر البحث: "+(error==null?"الخوادم غير متاحة":error.getMessage()));return;}adapter.setItems(values);status.setText(values.size()+" فيديو — اختر نتيجة للتشغيل");}
        }.execute(q);
    }
    private void play(VideoItem item){
        android.content.Intent i=new android.content.Intent(this,VideoPlayerActivity.class);
        i.putExtra("id",item.id);i.putExtra("title",item.title);i.putExtra("author",item.author);
        i.putExtra("backend",item.backend);i.putExtra("base",item.baseUrl);startActivity(i);
    }
    @Override protected void onResume(){super.onResume();immersive();}
    @Override protected void onDestroy(){if(task!=null)task.cancel(true);super.onDestroy();}
}
