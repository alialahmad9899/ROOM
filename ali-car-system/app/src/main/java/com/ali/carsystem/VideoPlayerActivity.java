package com.ali.carsystem;

import android.media.MediaPlayer;
import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.content.Context;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public final class VideoPlayerActivity extends BaseActivity implements SurfaceHolder.Callback {
    private SurfaceView surface;private MediaPlayer player;private TextView status;private VideoApi.Item item;private String activeUrl="";private int attempts;private boolean surfaceReady;
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id=getIntent().getStringExtra("id");String title=getIntent().getStringExtra("title");String backend=getIntent().getStringExtra("backend");String base=getIntent().getStringExtra("base");
        item=new VideoApi.Item(id==null?"":id,title==null?"فيديو":title,"","","PIPED".equals(backend)?"PIPED":"INVIDIOUS",base);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);surface=new SurfaceView(this);surface.getHolder().addCallback(this);root.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);overlay.setPadding(dp(12),dp(10),dp(12),dp(10));
        TextView close=new TextView(this);close.setText("إغلاق ✕");close.setTextColor(Color.WHITE);close.setTextSize(18);close.setOnClickListener(v->finish());overlay.addView(close,new LinearLayout.LayoutParams(-1,dp(44)));
        status=new TextView(this);status.setText("جارٍ جلب مصدر فيديو متاح...");status.setTextColor(Color.WHITE);status.setGravity(Gravity.CENTER);overlay.addView(status);
        FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(overlay,op);setContentView(root);
        resolveNext(null);
    }
    private void resolveNext(String excluded){
        if(attempts>=4){status.setText("تعذر تشغيل هذا الفيديو من الخوادم المتاحة. جرّب نتيجة أخرى.");return;}
        attempts++;status.setText("جارٍ تجربة مصدر البث "+attempts+"...");
        new AsyncTask<Void,Void,VideoApi.StreamInfo>(){
            Exception error;
            protected VideoApi.StreamInfo doInBackground(Void...v){try{return VideoApi.resolve(getApplicationContext(),item,excluded);}catch(Exception e){error=e;return null;}}
            protected void onPostExecute(VideoApi.StreamInfo info){if(isFinishing())return;if(info==null){status.setText(error==null?"لا يوجد مصدر بديل":error.getMessage());return;}activeUrl=info.url;startPlayback();}
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    private void startPlayback(){
        if(!surfaceReady){status.setText("تم العثور على المصدر — بانتظار سطح العرض");return;}
        release();try{
            player=new MediaPlayer();player.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);player.setDisplay(surface.getHolder());
            player.setOnPreparedListener(mp->{status.setText("");mp.start();});
            player.setOnErrorListener((mp,w,e)->{String failed=activeUrl;release();resolveNext(failed);return true;});
            player.setOnCompletionListener(mp->{status.setText("انتهى الفيديو");});
            player.setDataSource(activeUrl);player.prepareAsync();status.setText("جارٍ تجهيز الفيديو...");
        }catch(Exception e){String failed=activeUrl;release();resolveNext(failed);}
    }
    private void release(){if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    @Override public void surfaceCreated(SurfaceHolder h){surfaceReady=true;if(!activeUrl.isEmpty())startPlayback();}
    @Override public void surfaceChanged(SurfaceHolder h,int f,int w,int he){}
    @Override public void surfaceDestroyed(SurfaceHolder h){surfaceReady=false;release();}
    @Override protected void onPause(){release();super.onPause();}
    @Override protected void onDestroy(){release();super.onDestroy();}
}
