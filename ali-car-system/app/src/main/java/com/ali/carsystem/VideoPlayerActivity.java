package com.ali.carsystem;

import android.app.Activity;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.net.Uri;
import java.util.HashMap;
import java.util.Map;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class VideoPlayerActivity extends Activity implements SurfaceHolder.Callback {
    private SurfaceView surface;
    private SurfaceHolder holder;
    private MediaPlayer player;
    private TextView status, pauseButton;
    private String title, id, backend, base;
    private boolean surfaceReady, prepared;
    private AsyncTask<Void,Void,VideoApiClient.StreamInfo> task;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void immersive(){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);immersive();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        id=getIntent().getStringExtra("id");title=getIntent().getStringExtra("title");backend=getIntent().getStringExtra("backend");base=getIntent().getStringExtra("base");
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF000000);surface=new SurfaceView(this);holder=surface.getHolder();holder.addCallback(this);
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        status=new TextView(this);status.setText(title+"\nجارٍ تجهيز الفيديو بجودة مناسبة...");status.setTextColor(-1);status.setTextSize(16);status.setGravity(Gravity.CENTER);status.setBackgroundColor(0x99000000);
        root.addView(status,new FrameLayout.LayoutParams(-1,dp(70),Gravity.TOP));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        pauseButton=new TextView(this);pauseButton.setText("Ⅱ");pauseButton.setTextColor(-1);pauseButton.setTextSize(22);pauseButton.setGravity(Gravity.CENTER);
        pauseButton.setBackgroundColor(0x99000000);pauseButton.setOnClickListener(v->togglePlayback());
        TextView close=new TextView(this);close.setText("✕");close.setTextColor(-1);close.setTextSize(22);close.setGravity(Gravity.CENTER);close.setBackgroundColor(0x99000000);close.setOnClickListener(v->finish());
        LinearLayout.LayoutParams c=new LinearLayout.LayoutParams(dp(48),dp(48));c.leftMargin=dp(5);
        controls.addView(pauseButton,c);LinearLayout.LayoutParams x=new LinearLayout.LayoutParams(dp(48),dp(48));x.leftMargin=dp(5);controls.addView(close,x);
        FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-2,dp(50),Gravity.TOP|Gravity.RIGHT);cp.topMargin=dp(7);cp.rightMargin=dp(8);
        root.addView(controls,cp);setContentView(root);
        task=new AsyncTask<Void,Void,VideoApiClient.StreamInfo>(){
            Exception error;
            protected VideoApiClient.StreamInfo doInBackground(Void...v){try{return VideoApiClient.stream(getApplicationContext(), new VideoItem(id,title,"","",backend,base));}catch(Exception e){error=e;return null;}}
            protected void onPostExecute(VideoApiClient.StreamInfo stream){if(isFinishing())return;if(stream==null){status.setText(error==null?"الخادم لا يوفر صيغة فيديو متوافقة.":(error.getMessage()==null?"تعذر الاتصال بخادم الفيديو. تحقق من الإنترنت وحاول مرة أخرى.":error.getMessage()));return;}startStream(stream.url);}
        }.execute();
    }
    private void togglePlayback(){
        if(player==null||!prepared)return;
        try{if(player.isPlaying()){player.pause();pauseButton.setText("▶");}
            else{if(surfaceReady){player.setDisplay(holder);player.start();pauseButton.setText("Ⅱ");}}}
        catch(Exception ignored){}
    }
    private void startStream(String url){
        release();try{
            player=new MediaPlayer();player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if(surfaceReady)player.setDisplay(holder);
            player.setOnPreparedListener(mp->{prepared=true;startWhenReady();});
            player.setOnCompletionListener(mp->status.setText("انتهى تشغيل الفيديو"));
            player.setOnErrorListener((mp,w,e)->{status.setVisibility(View.VISIBLE);status.setText("تعذر تشغيل الفيديو على هذا الجهاز. جرّب نتيجة أخرى.");release();return true;});
            Map<String,String> headers=new HashMap<>();headers.put("User-Agent",NetworkCompat.USER_AGENT);\n            if(base!=null&&base.startsWith("https://"))headers.put("Referer",base);\n            player.setDataSource(this,Uri.parse(url),headers);player.prepareAsync();
        }catch(Exception e){status.setText("تعذر بدء تشغيل الفيديو. جرّب نتيجة أخرى.");release();}
    }
    @Override public void surfaceCreated(SurfaceHolder h){surfaceReady=true;startWhenReady();}
    private void startWhenReady(){if(player==null||!surfaceReady||!prepared)return;try{player.setDisplay(holder);if(!player.isPlaying())player.start();status.setVisibility(View.GONE);if(pauseButton!=null)pauseButton.setText("Ⅱ");}catch(Exception e){status.setVisibility(View.VISIBLE);status.setText("تعذر عرض الفيديو على الشاشة.");}}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){}
    @Override public void surfaceDestroyed(SurfaceHolder h){surfaceReady=false;if(player!=null)try{if(player.isPlaying())player.pause();player.setDisplay(null);}catch(Exception ignored){}}
    private void release(){prepared=false;if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    @Override protected void onPause(){if(player!=null)try{if(player.isPlaying())player.pause();}catch(Exception ignored){}super.onPause();}
    @Override protected void onDestroy(){if(task!=null)task.cancel(true);release();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy();}
    @Override protected void onResume(){super.onResume();immersive();}
}
