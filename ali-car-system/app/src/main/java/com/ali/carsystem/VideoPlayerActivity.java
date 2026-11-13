package com.ali.carsystem;

import android.os.*;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.MergingMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import java.util.*;

public final class VideoPlayerActivity extends BaseActivity {
    private PlayerView playerView;private ExoPlayer player;private TextView status,titleView;
    private VideoApi.Item item;private VideoApi.StreamInfo active;private int attempts;private boolean resolving,released;
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        String id=getIntent().getStringExtra("id"),title=getIntent().getStringExtra("title");
        String backend=getIntent().getStringExtra("backend"),base=getIntent().getStringExtra("base");
        item=new VideoApi.Item(id==null?"":id,title==null?"فيديو":title,"","",
                "PIPED".equals(backend)?"PIPED":"INVIDIOUS",base);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        playerView=new PlayerView(this);playerView.setUseController(true);playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setResizeMode(com.google.android.exoplayer2.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT);
        root.addView(playerView,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(14),dp(10),dp(14),dp(10));
        overlay.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xD9081421,0x00000000}));
        TextView close=new TextView(this);close.setText("إغلاق  ✕");close.setTextColor(Color.WHITE);close.setTextSize(17);
        close.setGravity(Gravity.CENTER_VERTICAL);close.setOnClickListener(v->finish());overlay.addView(close,new LinearLayout.LayoutParams(-1,dp(42)));
        titleView=new TextView(this);titleView.setTextColor(Color.WHITE);titleView.setTextSize(14);titleView.setMaxLines(1);titleView.setText(item.title);overlay.addView(titleView);
        status=new TextView(this);status.setTextColor(0xFF91E9FF);status.setTextSize(14);status.setGravity(Gravity.CENTER);status.setPadding(0,dp(10),0,dp(10));
        root.addView(status,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        root.addView(overlay,new FrameLayout.LayoutParams(-1,-2,Gravity.TOP));setContentView(root);resolveNext(null);
    }
    private void resolveNext(String excluded){
        if(resolving||released)return;
        if(attempts>=7){status.setText("تعذر إيجاد مصدر متوافق بعد تجربة عدة خوادم وصيغ بث. جرّب نتيجة أخرى.");return;}
        resolving=true;attempts++;status.setText("جارٍ تجربة المصدر "+attempts+" من 7…");
        new AsyncTask<Void,Void,VideoApi.StreamInfo>(){
            Exception error;
            @Override protected VideoApi.StreamInfo doInBackground(Void...v){try{return VideoApi.resolve(getApplicationContext(),item,excluded);}catch(Exception e){error=e;return null;}}
            @Override protected void onPostExecute(VideoApi.StreamInfo info){
                resolving=false;if(isFinishing()||released)return;
                if(info==null){status.setText(error==null?"المصدر غير متاح":"فشل جلب المصدر: "+safe(error.getMessage()));return;}
                active=info;startPlayback(info);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    private String safe(String value){if(value==null||value.trim().isEmpty())return "خطأ غير معروف";return value.length()>120?value.substring(0,120):value;}
    private DefaultDataSource.Factory dataSourceFactory(){
        Map<String,String> headers=new HashMap<>();
        headers.put("User-Agent","Mozilla/5.0 (Linux; Android 5.0.2; Mobile) AppleWebKit/537.36 Chrome/91.0 Mobile Safari/537.36");
        headers.put("Referer","https://www.youtube.com/");headers.put("Origin","https://www.youtube.com");
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory()
                .setUserAgent(headers.get("User-Agent")).setConnectTimeoutMs(12000).setReadTimeoutMs(20000)
                .setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(headers);
        return new DefaultDataSource.Factory(this,http);
    }
    private MediaSource makeSource(VideoApi.StreamInfo info){
        DefaultDataSource.Factory data=dataSourceFactory();MediaItem source=MediaItem.fromUri(info.url);
        if(VideoApi.StreamInfo.DASH.equals(info.kind))return new DashMediaSource.Factory(data).createMediaSource(source);
        if(VideoApi.StreamInfo.HLS.equals(info.kind))return new HlsMediaSource.Factory(data).createMediaSource(source);
        if(VideoApi.StreamInfo.SPLIT.equals(info.kind)){
            MediaSource video=new ProgressiveMediaSource.Factory(data).createMediaSource(MediaItem.fromUri(info.url));
            MediaSource audio=new ProgressiveMediaSource.Factory(data).createMediaSource(MediaItem.fromUri(info.audioUrl));
            return new MergingMediaSource(video,audio);
        }
        return new ProgressiveMediaSource.Factory(data).createMediaSource(source);
    }
    private void startPlayback(VideoApi.StreamInfo info){
        releasePlayer();
        try{
            player=new ExoPlayer.Builder(this).build();playerView.setPlayer(player);
            player.addListener(new Player.Listener(){
                @Override public void onPlaybackStateChanged(int state){
                    if(state==Player.STATE_BUFFERING)status.setText("جارٍ تحميل الفيديو والصوت…");
                    else if(state==Player.STATE_READY)status.setText("");
                    else if(state==Player.STATE_ENDED)status.setText("انتهى الفيديو");
                }
                @Override public void onPlayerError(PlaybackException error){
                    status.setText("تعذر هذا المصدر، جارٍ تجربة صيغة أو خادم بديل…");
                    String bad=active==null?null:active.identity();releasePlayer();resolveNext(bad);
                }
            });
            player.setMediaSource(makeSource(info));player.setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT);
            player.prepare();player.setPlayWhenReady(true);titleView.setText(info.title);
            status.setText("تم العثور على المصدر — جارٍ تجهيز الفيديو والصوت معاً…");
        }catch(Exception e){
            String bad=info.identity();status.setText("صيغة المصدر غير متوافقة، تجربة بديل…");releasePlayer();resolveNext(bad);
        }
    }
    private void releasePlayer(){if(player!=null){try{playerView.setPlayer(null);player.release();}catch(Exception ignored){}player=null;}}
    @Override protected void onPause(){super.onPause();if(player!=null)player.setPlayWhenReady(false);}
    @Override protected void onResume(){super.onResume();if(player!=null)player.setPlayWhenReady(true);}
    @Override protected void onDestroy(){released=true;releasePlayer();super.onDestroy();}
}
