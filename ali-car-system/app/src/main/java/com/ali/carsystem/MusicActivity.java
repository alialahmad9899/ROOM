package com.ali.carsystem;

import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.audiofx.Visualizer;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.io.InputStream;
import java.util.*;

public final class MusicActivity extends BaseActivity {
    private MusicPlaybackService service;private boolean bound;
    private RecyclerView list;private SongAdapter adapter;private ImageView cover;private WaveView wave;
    private TextView title,artist,time,playButton,shuffleButton,repeatButton;private SeekBar progress;private Visualizer visualizer;
    private long loadedAlbum=-1;private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){updateProgress();handler.postDelayed(this,250);}};
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){
            service=((MusicPlaybackService.LocalBinder)b).getService();bound=true;
            service.setListener(()->runOnUiThread(()->refreshPlayer()));adapter.set(service.getSongs());refreshPlayer();
        }
        @Override public void onServiceDisconnected(ComponentName n){bound=false;service=null;stopVisualizer();}
    };
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable card(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{fill,0xEE07111D});
        d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
    }
    private LinearLayout panel(){LinearLayout l=new LinearLayout(this);l.setBackground(card(0xCC122A3E,0x665ACFEF,22));return l;}
    private TextView text(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);return t;
    }
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(10),dp(16),dp(10));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF142D43,0xFF030912,0xFF071B2C}));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text("‹",34,0xFF93E9FF,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(42)));
        TextView heading=text("مركز الموسيقى",21,Color.WHITE,true);heading.setGravity(Gravity.CENTER);
        head.addView(heading,new LinearLayout.LayoutParams(0,dp(42),1));head.addView(text("مكتبتك المحلية  •  ALI CAR",11,0xFF8ABAD0,false));root.addView(head);
        LinearLayout body=new LinearLayout(this);LinearLayout.LayoutParams bodyLp=new LinearLayout.LayoutParams(-1,0,1);
        bodyLp.topMargin=dp(8);root.addView(body,bodyLp);
        LinearLayout now=panel();now.setOrientation(LinearLayout.VERTICAL);now.setPadding(dp(14),dp(12),dp(14),dp(10));
        LinearLayout.LayoutParams nowLp=new LinearLayout.LayoutParams(0,-1,1.08f);nowLp.rightMargin=dp(10);body.addView(now,nowLp);
        LinearLayout nowHead=new LinearLayout(this);nowHead.setGravity(Gravity.CENTER_VERTICAL);
        nowHead.addView(text("يعمل الآن",13,0xFF70DFFF,true),new LinearLayout.LayoutParams(0,dp(28),1));
        nowHead.addView(text("LOCAL AUDIO",10,0xFFFFD27B,true));now.addView(nowHead);
        cover=new ImageView(this);cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cover.setBackground(card(0xFF132B40,0xFF48B9DD,22));cover.setClipToOutline(true);
        LinearLayout.LayoutParams coverLp=new LinearLayout.LayoutParams(dp(184),dp(184));
        coverLp.gravity=Gravity.CENTER;coverLp.topMargin=dp(4);now.addView(cover,coverLp);
        title=text("اختر أغنية من مكتبتك",18,Color.WHITE,true);title.setGravity(Gravity.CENTER);title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,dp(32));titleLp.topMargin=dp(8);now.addView(title,titleLp);
        artist=text("لا يوجد مسار قيد التشغيل",12,0xFF9DB6C6,false);artist.setGravity(Gravity.CENTER);
        now.addView(artist,new LinearLayout.LayoutParams(-1,dp(22)));
        wave=new WaveView(this);LinearLayout.LayoutParams waveLp=new LinearLayout.LayoutParams(-1,dp(38));
        waveLp.topMargin=dp(4);now.addView(wave,waveLp);
        progress=new SeekBar(this);progress.setMax(1000);
        progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean user){
                if(user&&service!=null&&service.isPrepared())service.seekTo((int)(p*service.getDuration()/1000f));
            }
            public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}
        });now.addView(progress,new LinearLayout.LayoutParams(-1,dp(28)));
        time=text("00:00  /  00:00",11,0xFF9FBCCB,false);time.setGravity(Gravity.CENTER);now.addView(time);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        shuffleButton=control("⇄",()->{if(service!=null)service.setShuffle(!service.isShuffle());});
        controls.addView(shuffleButton,new LinearLayout.LayoutParams(dp(44),dp(44)));
        controls.addView(control("⏮",()->{if(service!=null)service.previous();}),new LinearLayout.LayoutParams(dp(48),dp(44)));
        playButton=control("▶",()->{if(service!=null)service.toggle();});
        controls.addView(playButton,new LinearLayout.LayoutParams(dp(58),dp(50)));
        controls.addView(control("⏭",()->{if(service!=null)service.next();}),new LinearLayout.LayoutParams(dp(48),dp(44)));
        repeatButton=control("↻",()->{if(service!=null)service.setRepeat(!service.isRepeat());});
        controls.addView(repeatButton,new LinearLayout.LayoutParams(dp(44),dp(44)));now.addView(controls);

        LinearLayout library=panel();library.setOrientation(LinearLayout.VERTICAL);library.setPadding(dp(12),dp(10),dp(12),dp(8));
        body.addView(library,new LinearLayout.LayoutParams(0,-1,.92f));
        LinearLayout libraryHead=new LinearLayout(this);libraryHead.setGravity(Gravity.CENTER_VERTICAL);
        libraryHead.addView(text("قائمة التشغيل",17,Color.WHITE,true),new LinearLayout.LayoutParams(0,dp(34),1));
        libraryHead.addView(text("اختيار وتشغيل",10,0xFF8ABAD0,false));library.addView(libraryHead);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new SongAdapter();list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp=new LinearLayout.LayoutParams(-1,0,1);listLp.topMargin=dp(5);library.addView(list,listLp);
        setContentView(root);startService(new Intent(this,MusicPlaybackService.class));
    }
    private TextView control(String symbol,Runnable action){
        TextView t=text(symbol,23,Color.WHITE,true);t.setGravity(Gravity.CENTER);
        t.setBackground(card(0xCC1B4059,0x665CD9F4,24));t.setOnClickListener(v->action.run());return t;
    }
    private void refreshPlayer(){
        if(service==null)return;MusicPlaybackService.Song s=service.getCurrentSong();
        title.setText(s==null?"اختر أغنية من مكتبتك":s.name);
        artist.setText(s==null?"لا يوجد مسار قيد التشغيل":(s.artist==null||s.artist.isEmpty()?"فنان غير معروف":s.artist));
        playButton.setText(service.isPlaying()?"Ⅱ":"▶");shuffleButton.setText(service.isShuffle()?"⇄•":"⇄");
        repeatButton.setText(service.isRepeat()?"↻•":"↻");
        if(s!=null&&loadedAlbum!=s.albumId){loadedAlbum=s.albumId;loadAlbumArt(s.albumId);}
        if(adapter!=null)adapter.notifyDataSetChanged();updateProgress();
        if(service.isPlaying())startVisualizerIfPossible();
    }
    private void updateProgress(){
        if(service==null||!service.isPrepared()){progress.setProgress(0);time.setText("00:00  /  00:00");return;}
        int duration=service.getDuration(),position=service.getPosition();
        progress.setProgress(duration>0?(int)Math.max(0,Math.min(1000,position*1000L/duration)):0);
        time.setText(clock(position)+"  /  "+clock(duration));
        if(!service.isPlaying())stopVisualizer();else if(visualizer==null)startVisualizerIfPossible();
    }
    private String clock(int ms){int sec=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",sec/60,sec%60);}
    private void loadAlbumArt(long id){
        Bitmap bitmap=null;
        try(InputStream in=getContentResolver().openInputStream(Uri.parse("content://media/external/audio/albumart/"+id))){
            if(in!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;bitmap=BitmapFactory.decodeStream(in,null,o);}
        }catch(Exception ignored){}
        if(bitmap!=null)cover.setImageBitmap(bitmap);else cover.setImageResource(android.R.drawable.ic_media_play);
    }
    private void startVisualizerIfPossible(){
        if(service==null||!service.isPlaying()||visualizer!=null)return;
        int session=service.getAudioSessionId();if(session<=0)return;
        try{
            visualizer=new Visualizer(session);visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
                public void onWaveFormDataCapture(Visualizer v,byte[] d,int rate){if(wave!=null)wave.setWave(d);}
                public void onFftDataCapture(Visualizer v,byte[] d,int rate){}
            },Visualizer.getMaxCaptureRate(),true,false);visualizer.setEnabled(true);
        }catch(Exception ignored){stopVisualizer();wave.setUnavailable();}
    }
    private void stopVisualizer(){
        if(visualizer!=null){try{visualizer.setEnabled(false);visualizer.release();}catch(Exception ignored){}visualizer=null;}
    }
    @Override protected void onStart(){
        super.onStart();if(!bound)bindService(new Intent(this,MusicPlaybackService.class),connection,BIND_AUTO_CREATE);
        handler.post(ticker);
    }
    @Override protected void onStop(){
        handler.removeCallbacks(ticker);
        if(bound){if(service!=null)service.setListener(null);unbindService(connection);bound=false;service=null;}
        stopVisualizer();super.onStop();
    }
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);stopVisualizer();super.onDestroy();}
    private final class SongAdapter extends RecyclerView.Adapter<SongRow>{
        private List<MusicPlaybackService.Song> items=new ArrayList<>();
        void set(List<MusicPlaybackService.Song> x){items=x;notifyDataSetChanged();}
        @Override public SongRow onCreateViewHolder(ViewGroup p,int type){
            LinearLayout row=new LinearLayout(MusicActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10),dp(7),dp(10),dp(7));row.setBackground(card(0x66325268,0x223FD2EE,12));
            TextView t=text("",13,Color.WHITE,true);row.addView(t,new LinearLayout.LayoutParams(-1,dp(52)));return new SongRow(row,t);
        }
        @Override public void onBindViewHolder(SongRow h,int p){
            MusicPlaybackService.Song s=items.get(p),current=service==null?null:service.getCurrentSong();
            boolean active=current!=null&&current.id==s.id;
            h.label.setText((active?"♫  ":"")+String.format(Locale.US,"%02d",p+1)+"   "+s.name+"\n"+(s.artist==null?"":s.artist));
            h.itemView.setAlpha(active?1f:.78f);h.itemView.setOnClickListener(v->{if(service!=null)service.playAt(p);});
        }
        @Override public int getItemCount(){return items.size();}
    }
    private static final class SongRow extends RecyclerView.ViewHolder{final TextView label;SongRow(View v,TextView t){super(v);label=t;}}
    private final class WaveView extends View{
        private final Paint p=new Paint(3);private byte[] samples=new byte[0];private boolean unavailable;
        WaveView(Context c){super(c);}void setWave(byte[] d){samples=d.clone();unavailable=false;postInvalidate();}
        void setUnavailable(){unavailable=true;invalidate();}
        @Override protected void onDraw(Canvas c){
            float mid=getHeight()/2f,w=getWidth();p.setColor(0xFF52DFFF);p.setStrokeWidth(dp(2));
            if(unavailable){p.setTextSize(dp(10));p.setTextAlign(Paint.Align.CENTER);p.setColor(0xFF8197A7);c.drawText("المؤثر الصوتي غير متاح على هذا الجهاز",w/2,mid+dp(4),p);return;}
            if(samples.length<2)return;float step=w/samples.length;
            for(int i=0;i<samples.length;i++){float h=(samples[i]&255)/255f*mid;c.drawLine(i*step,mid-h,i*step,mid+h,p);}
        }
    }
}
