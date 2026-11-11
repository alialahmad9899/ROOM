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
import android.widget.FrameLayout;
import android.widget.TextView;

public final class VideoPlayerActivity extends Activity implements SurfaceHolder.Callback {
    private SurfaceView surface;
    private SurfaceHolder holder;
    private MediaPlayer player;
    private TextView status;
    private String title, id, backend, base;
    private boolean surfaceReady;
    private AsyncTask<Void,Void,VideoApiClient.StreamInfo> task;
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void immersive(){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);immersive();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        id=getIntent().getStringExtra("id");title=getIntent().getStringExtra("title");backend=getIntent().getStringExtra("backend");base=getIntent().getStringExtra("base");
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF000000);surface=new SurfaceView(this);holder=surface.getHolder();holder.addCallback(this);
        root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        status=new TextView(this);status.setText(title+"\nجارٍ تجهيز فيديو MP4 بدقة منخفضة...");status.setTextColor(-1);status.setTextSize(16);status.setGravity(Gravity.CENTER);status.setBackgroundColor(0x99000000);
        root.addView(status,new FrameLayout.LayoutParams(-1,dp(70),Gravity.TOP));setContentView(root);
        task=new AsyncTask<Void,Void,VideoApiClient.StreamInfo>(){
            Exception error;
            protected VideoApiClient.StreamInfo doInBackground(Void...v){try{return VideoApiClient.stream(new VideoItem(id,title,"","",backend,base));}catch(Exception e){error=e;return null;}}
            protected void onPostExecute(VideoApiClient.StreamInfo stream){if(isFinishing())return;if(stream==null){status.setText("تعذر تجهيز الفيديو: "+(error==null?"لا يوجد MP4 متوافق":error.getMessage()));return;}startStream(stream.url);}
        }.execute();
    }
    private void startStream(String url){
        release();try{
            player=new MediaPlayer();player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            if(surfaceReady)player.setDisplay(holder);
            player.setOnPreparedListener(mp->{if(surfaceReady){status.setVisibility(View.GONE);mp.start();}});
            player.setOnCompletionListener(mp->status.setText("انتهى الفيديو"));
            player.setOnErrorListener((mp,w,e)->{status.setVisibility(View.VISIBLE);status.setText("خطأ تشغيل الوسائط ("+w+"/"+e+")");release();return true;});
            player.setDataSource(url);player.prepareAsync();
        }catch(Exception e){status.setText("فشل بدء مشغل الفيديو: "+e.getMessage());release();}
    }
    @Override public void surfaceCreated(SurfaceHolder h){surfaceReady=true;if(player!=null)try{player.setDisplay(h);if(player.isPlaying())status.setVisibility(View.GONE);}catch(Exception ignored){}}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int ht){}
    @Override public void surfaceDestroyed(SurfaceHolder h){surfaceReady=false;if(player!=null)try{if(player.isPlaying())player.pause();player.setDisplay(null);}catch(Exception ignored){}}
    private void release(){if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    @Override protected void onPause(){if(player!=null)try{if(player.isPlaying())player.pause();}catch(Exception ignored){}super.onPause();}
    @Override protected void onDestroy(){if(task!=null)task.cancel(true);release();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);super.onDestroy();}
    @Override protected void onResume(){super.onResume();immersive();}
}
