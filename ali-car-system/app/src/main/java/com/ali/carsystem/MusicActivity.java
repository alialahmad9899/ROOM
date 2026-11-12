package com.ali.carsystem;

import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

public final class MusicActivity extends BaseActivity {
    private final ArrayList<Song> songs=new ArrayList<>();
    private RecyclerView list;private SongAdapter adapter;private MediaPlayer player;private Visualizer visualizer;
    private Disc disc;private WaveView wave;private TextView title,artist,time;private SeekBar progress;private int index=-1;private boolean repeat,shuffle;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Runnable ticker=new Runnable(){@Override public void run(){if(player!=null&&progress!=null)try{if(player.isPlaying()){int pos=player.getCurrentPosition();progress.setProgress(pos);time.setText(clock(pos)+" / "+clock(player.getDuration()));}}catch(Exception ignored){}handler.postDelayed(this,500);}};
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(10),dp(16),dp(10));root.setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xFF102438,0xFF030810,0xFF1B2940}));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);TextView back=text("‹",34);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(44)));
        TextView h=text("الموسيقى",21);h.setGravity(Gravity.CENTER);head.addView(h,new LinearLayout.LayoutParams(0,dp(44),1));root.addView(head);
        LinearLayout playerPanel=new LinearLayout(this);playerPanel.setGravity(Gravity.CENTER_VERTICAL);
        disc=new Disc(this);playerPanel.addView(disc,new LinearLayout.LayoutParams(dp(150),dp(150)));
        LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.VERTICAL);meta.setPadding(dp(12),0,0,0);
        title=text("اختر أغنية من القائمة",18);artist=text("مكتبة الموسيقى المحلية",13);meta.addView(title);meta.addView(artist);playerPanel.addView(meta,new LinearLayout.LayoutParams(0,-2,1));root.addView(playerPanel,new LinearLayout.LayoutParams(-1,dp(164)));
        wave=new WaveView(this);root.addView(wave,new LinearLayout.LayoutParams(-1,dp(42)));
        progress=new SeekBar(this);progress.setMax(1000);progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){if(u&&player!=null)try{player.seekTo(p*player.getDuration()/1000);}catch(Exception ignored){}}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        root.addView(progress,new LinearLayout.LayoutParams(-1,dp(30)));time=text("00:00 / 00:00",11);time.setGravity(Gravity.CENTER);root.addView(time);
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(control("⇄",()->{shuffle=!shuffle;Toast.makeText(this,shuffle?"التشغيل العشوائي مفعّل":"التشغيل العشوائي متوقف",Toast.LENGTH_SHORT).show();}));
        controls.addView(control("⏮",this::previous));controls.addView(control("▶",this::toggle));
        controls.addView(control("⏭",this::next));controls.addView(control("↻",()->{repeat=!repeat;Toast.makeText(this,repeat?"التكرار مفعّل":"التكرار متوقف",Toast.LENGTH_SHORT).show();}));
        root.addView(controls);
        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new SongAdapter();list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);loadSongs();handler.post(ticker);
    }
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(Color.WHITE);t.setGravity(Gravity.CENTER_VERTICAL);return t;}
    private TextView control(String s,Runnable r){TextView t=text(s,25);t.setGravity(Gravity.CENTER);t.setOnClickListener(v->r.run());return t;}
    private String clock(int ms){int sec=Math.max(0,ms/1000);return String.format(Locale.US,"%d:%02d",sec/60,sec%60);}
    private void loadSongs(){
        String[] cols={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION};
        try(Cursor c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,MediaStore.Audio.Media.IS_MUSIC+"=1",null,MediaStore.Audio.Media.TITLE+" ASC")){
            if(c!=null)while(c.moveToNext())songs.add(new Song(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3)));
        }catch(Exception e){Toast.makeText(this,"تعذر قراءة مكتبة الموسيقى",Toast.LENGTH_SHORT).show();}
        adapter.notifyDataSetChanged();
        if(songs.isEmpty())title.setText("لا توجد ملفات موسيقى على الجهاز");
        String last=getSharedPreferences("ali_car_v3",MODE_PRIVATE).getString("last_song","");
        for(int i=0;i<songs.size();i++)if(songs.get(i).name.equals(last)){index=i;break;}
    }
    private void playAt(int i){
        if(i<0||i>=songs.size())return;release();index=i;Song song=songs.get(i);
        try{
            player=new MediaPlayer();Uri uri=Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,String.valueOf(song.id));
            player.setDataSource(this,uri);player.setOnPreparedListener(mp->{
                mp.start();title.setText(song.name);artist.setText(song.artist==null?"فنان غير معروف":song.artist);disc.setPlaying(true);startVisualizer();
                getSharedPreferences("ali_car_v3",MODE_PRIVATE).edit().putString("last_song",song.name).apply();adapter.notifyDataSetChanged();
            });
            player.setOnCompletionListener(mp->{if(repeat){mp.seekTo(0);mp.start();}else next();});
            player.setOnErrorListener((mp,w,e)->{Toast.makeText(this,"تعذر تشغيل هذا الملف الصوتي",Toast.LENGTH_SHORT).show();return true;});
            player.prepareAsync();title.setText("جارٍ تجهيز: "+song.name);progress.setProgress(0);
        }catch(Exception e){release();Toast.makeText(this,"تعذر فتح ملف الموسيقى",Toast.LENGTH_SHORT).show();}
    }
    private void startVisualizer(){
        stopVisualizer();if(player==null)return;
        try{
            visualizer=new Visualizer(player.getAudioSessionId());visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener(){
                public void onWaveFormDataCapture(Visualizer v,byte[] data,int rate){if(wave!=null)wave.setWave(data);}
                public void onFftDataCapture(Visualizer v,byte[] data,int rate){}
            },Visualizer.getMaxCaptureRate(),true,false);visualizer.setEnabled(true);
        }catch(Exception e){if(wave!=null)wave.setFallback(true);}
    }
    private void stopVisualizer(){if(visualizer!=null){try{visualizer.setEnabled(false);visualizer.release();}catch(Exception ignored){}visualizer=null;}}
    private void toggle(){if(player==null){if(!songs.isEmpty())playAt(index<0?0:index);return;}try{if(player.isPlaying()){player.pause();disc.setPlaying(false);}else{player.start();disc.setPlaying(true);}}catch(Exception ignored){}}
    private void previous(){if(songs.isEmpty())return;if(player!=null&&player.getCurrentPosition()>3000){player.seekTo(0);return;}playAt(Math.floorMod(index-1,songs.size()));}
    private void next(){if(songs.isEmpty())return;int n=shuffle?new Random().nextInt(songs.size()):Math.floorMod(index+1,songs.size());playAt(n);}
    private void release(){stopVisualizer();if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}if(disc!=null)disc.setPlaying(false);}
    @Override protected void onPause(){handler.removeCallbacks(ticker);if(player!=null)try{if(player.isPlaying())player.pause();}catch(Exception ignored){}if(disc!=null)disc.setPlaying(false);super.onPause();}
    @Override protected void onResume(){super.onResume();handler.post(ticker);}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);release();super.onDestroy();}
    private static final class Song{final long id,duration;final String name,artist;Song(long i,String n,String a,long d){id=i;name=n==null?"ملف صوتي":n;artist=a;duration=d;}}
    private final class SongAdapter extends RecyclerView.Adapter<SongRow>{
        @Override public SongRow onCreateViewHolder(android.view.ViewGroup p,int t){TextView row=text("",15);row.setPadding(dp(12),dp(4),dp(12),dp(4));return new SongRow(row);}
        @Override public void onBindViewHolder(SongRow h,int p){Song s=songs.get(p);h.text.setText((p+1)+". "+s.name+"\n"+(s.artist==null?"":s.artist));h.text.setBackgroundColor(p==index?0x553E98BB:Color.TRANSPARENT);h.itemView.setOnClickListener(v->playAt(p));}
        @Override public int getItemCount(){return songs.size();}
    }
    private static final class SongRow extends RecyclerView.ViewHolder{final TextView text;SongRow(TextView v){super(v);text=v;}}
    private final class Disc extends View{
        private final Paint p=new Paint(3);private float angle;private boolean playing;private final Runnable spin=new Runnable(){public void run(){if(playing){angle=(angle+1.2f)%360;invalidate();postDelayed(this,55);}}};
        Disc(Context c){super(c);}
        void setPlaying(boolean b){playing=b;removeCallbacks(spin);if(b)post(spin);invalidate();}
        protected void onDraw(Canvas c){float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.44f;p.setShader(new RadialGradient(x,y,r,0xFF789AB3,0xFF071522,Shader.TileMode.CLAMP));c.drawCircle(x,y,r,p);p.setShader(null);c.save();c.rotate(angle,x,y);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(0xFF42D9FF);for(int i=1;i<6;i++)c.drawCircle(x,y,r*i/6f,p);p.setStyle(Paint.Style.FILL);p.setColor(0xFFB99CFF);c.drawCircle(x,y,r*.22f,p);c.restore();}
    }
    private final class WaveView extends View{
        private final Paint p=new Paint(3);private byte[] wave=new byte[0];private boolean fallback;
        WaveView(Context c){super(c);}
        void setWave(byte[] b){wave=b.clone();fallback=false;postInvalidate();}
        void setFallback(boolean f){fallback=f;invalidate();}
        protected void onDraw(Canvas c){float mid=getHeight()/2f,w=getWidth();p.setColor(0xFF52DFFF);p.setStrokeWidth(dp(2));int count=wave.length;if(count<2||fallback){for(int i=0;i<70;i++){float h=dp(3)+(float)Math.abs(Math.sin(i*.5+SystemClock.uptimeMillis()*.003))*getHeight()*.4f;c.drawLine(i*w/70,mid-h,i*w/70,mid+h,p);}if(fallback)postInvalidateDelayed(80);return;}float step=w/count;for(int i=0;i<count;i++){float h=(wave[i]&255)/255f*mid;c.drawLine(i*step,mid-h,i*step,mid+h,p);}}
    }
}
