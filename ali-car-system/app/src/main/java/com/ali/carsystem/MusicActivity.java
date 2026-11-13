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
    private RecyclerView list;private SongAdapter adapter;private VinylDiscView cover;private WaveView wave;
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
        cover=new VinylDiscView(this);
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
        playButton.setText(service.isPlaying()?"Ⅱ":"▶");cover.setSpinning(service.isPlaying());shuffleButton.setText(service.isShuffle()?"⇄•":"⇄");
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
        cover.setSpinning(service.isPlaying());
        if(!service.isPlaying())stopVisualizer();else if(visualizer==null)startVisualizerIfPossible();
    }
    private String clock(int ms){int sec=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",sec/60,sec%60);}
    private void loadAlbumArt(long id){
        Bitmap bitmap=null;
        try(InputStream in=getContentResolver().openInputStream(Uri.parse("content://media/external/audio/albumart/"+id))){
            if(in!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;bitmap=BitmapFactory.decodeStream(in,null,o);}
        }catch(Exception ignored){}
        cover.setAlbumArt(bitmap);
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
        stopVisualizer();if(cover!=null)cover.setSpinning(false);super.onStop();
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
    private final class VinylDiscView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        private Bitmap artwork;
        private boolean spinning;
        private float angle;
        VinylDiscView(Context context){super(context);setLayerType(View.LAYER_TYPE_HARDWARE,null);}
        void setAlbumArt(Bitmap bitmap){artwork=bitmap;invalidate();}
        void setSpinning(boolean value){
            if(spinning==value)return;spinning=value;
            if(spinning)postInvalidateOnAnimation();else invalidate();
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.465f;
            canvas.save();canvas.rotate(angle,cx,cy);
            p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx-r*.3f,cy-r*.35f,r*1.5f,
                    new int[]{0xFF384454,0xFF101722,0xFF03060B},null,Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,r,p);p.setShader(null);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.1f);
            for(int i=0;i<13;i++){
                float ring=r*(.66f+i*.023f);p.setColor(i%3==0?0x886C7B90:0x554C586A);
                canvas.drawCircle(cx,cy,ring,p);
            }
            p.setStrokeWidth(2.2f);p.setColor(0x668CEBFF);
            canvas.drawArc(cx-r*.91f,cy-r*.91f,cx+r*.91f,cy+r*.91f,218,38,false,p);
            p.setColor(0x66FFFFFF);canvas.drawArc(cx-r*.84f,cy-r*.84f,cx+r*.84f,cy+r*.84f,24,23,false,p);
            p.setStyle(Paint.Style.FILL);p.setColor(0xFF05090F);canvas.drawCircle(cx,cy,r*.59f,p);
            if(artwork!=null&&!artwork.isRecycled()){
                BitmapShader shader=new BitmapShader(artwork,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
                Matrix matrix=new Matrix();float size=r*1.12f;
                float scale=Math.max(size/artwork.getWidth(),size/artwork.getHeight());
                matrix.setScale(scale,scale);
                matrix.postTranslate(cx-artwork.getWidth()*scale/2f,cy-artwork.getHeight()*scale/2f);
                shader.setLocalMatrix(matrix);p.setShader(shader);canvas.drawCircle(cx,cy,r*.55f,p);p.setShader(null);
            }else{
                p.setShader(new SweepGradient(cx,cy,new int[]{0xFF37DDF6,0xFF6A7BFF,0xFFFF60AA,0xFF37DDF6},null));
                canvas.drawCircle(cx,cy,r*.55f,p);p.setShader(null);
                p.setColor(Color.WHITE);p.setTextAlign(Paint.Align.CENTER);p.setTypeface(Typeface.DEFAULT_BOLD);
                p.setTextSize(r*.24f);canvas.drawText("ALI",cx,cy+r*.08f,p);
            }
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2.5f);p.setColor(0x99EAFBFF);
            canvas.drawCircle(cx,cy,r*.58f,p);
            p.setStyle(Paint.Style.FILL);p.setColor(0xFF111923);canvas.drawCircle(cx,cy,r*.095f,p);
            p.setColor(0xFF9BEFFF);canvas.drawCircle(cx,cy,r*.035f,p);
            p.setColor(0xFFD6E9F2);canvas.drawCircle(cx-r*.80f,cy-r*.31f,r*.025f,p);
            canvas.restore();
            if(spinning){angle=(angle+1.35f)%360f;postInvalidateDelayed(40);}
        }
    }
    private final class WaveView extends View {
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float[] levels=new float[28];
        private boolean unavailable;
        WaveView(Context context){super(context);}
        void setWave(byte[] data){
            if(data==null||data.length==0)return;
            for(int i=0;i<levels.length;i++){
                int from=i*data.length/levels.length,to=Math.max(from+1,(i+1)*data.length/levels.length);
                int peak=0;
                for(int j=from;j<Math.min(to,data.length);j++)peak=Math.max(peak,Math.abs((data[j]&255)-128));
                float value=Math.max(.08f,peak/128f);
                levels[i]=Math.min(1f,Math.max(value,levels[i]*.64f));
            }
            unavailable=false;postInvalidate();
        }
        void setUnavailable(){unavailable=true;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float width=getWidth(),height=getHeight(),gap=dp(3),barWidth=(width-gap*(levels.length-1))/levels.length;
            if(unavailable){
                p.setColor(0xFF8197A7);p.setTextSize(dp(10));p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("المؤشر الصوتي غير متاح على هذا الجهاز",width/2,height/2+dp(4),p);return;
            }
            p.setShader(new LinearGradient(0,height,0,0,
                    new int[]{0xFF27D9E8,0xFF368FFF,0xFF8D63F7,0xFFFF5FA7},null,Shader.TileMode.CLAMP));
            for(int i=0;i<levels.length;i++){
                float x=i*(barWidth+gap),max=height*.88f;
                float barH=Math.max(dp(3),levels[i]*max);
                float top=(height-barH)/2f;
                canvas.drawRoundRect(x,top,x+barWidth,top+barH,dp(2),dp(2),p);
            }
            p.setShader(null);
            p.setColor(0x334ADBF0);p.setStrokeWidth(1f);canvas.drawLine(0,height/2f,width,height/2f,p);
        }
    }
}