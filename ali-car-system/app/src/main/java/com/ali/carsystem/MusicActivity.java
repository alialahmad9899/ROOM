package com.ali.carsystem;

import android.app.Activity;
import android.content.ContentUris;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import android.view.ViewGroup;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Locale;

public final class MusicActivity extends Activity {
    private static final class Song {
        final String title,artist;final Uri uri;final int duration;
        Song(String t,String a,Uri u,int d){title=t;artist=a;uri=u;duration=d;}
    }
    private final ArrayList<Song> songs=new ArrayList<>();
    private final Handler h=new Handler(Looper.getMainLooper());
    private MediaPlayer player;
    private int current=-1;
    private boolean prepared,tracking;
    private TextView now,play,elapsed,total;
    private SeekBar progress;
    private final Runnable tick=new Runnable(){public void run(){
        if(player!=null&&prepared&&!tracking){
            try{if(player.isPlaying()){int p=player.getCurrentPosition(),d=player.getDuration();progress.setProgress(d>0?p*1000/d:0);elapsed.setText(time(p));h.postDelayed(this,750);}}catch(Exception ignored){}
        }
    }};
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void immersive(){getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);immersive();loadSongs();build();
        String action=getIntent().getStringExtra("ali.music.action");
        current=getPreferences(0).getInt("current_index",-1);
        if("previous".equals(action))select(Math.max(0,current-1));
        else if("next".equals(action))select(current<0?0:Math.min(songs.size()-1,current+1));
        else if("play".equals(action))select(current<0?0:current);
    }
    private void loadSongs(){
        String[] cols={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION};
        Cursor c=null;
        try{c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,
            MediaStore.Audio.Media.IS_MUSIC+" != 0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC");
            if(c!=null)while(c.moveToNext()){
                long id=c.getLong(0);songs.add(new Song(c.getString(1)==null?"مقطع صوتي":c.getString(1),
                    c.getString(2)==null?"Unknown Artist":c.getString(2),
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,id),c.getInt(3)));
            }
        }catch(Exception ignored){}finally{if(c!=null)c.close();}
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(14),dp(9),dp(14),dp(8));root.setBackgroundColor(0xFF050A13);
        TextView title=label("ALI MUSIC",24,Color.WHITE,true);root.addView(title,new LinearLayout.LayoutParams(-1,dp(40)));
        TextView sub=label("مشغل محلي خفيف • لا خدمات تعمل بالخلفية",12,0xFF5EDCF5,true);root.addView(sub,new LinearLayout.LayoutParams(-1,dp(23)));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER);panel.setPadding(dp(12),dp(6),dp(12),dp(6));panel.setBackground(shape(0xFF16283B,18,0xFF46CFF5));
        now=label("اختر أغنية من القائمة",18,Color.WHITE,true);now.setGravity(Gravity.CENTER);panel.addView(now,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout buttons=new LinearLayout(this);buttons.setGravity(Gravity.CENTER);
        TextView prev=button("⏮ السابق"),next=button("التالي ⏭");play=button("▶ تشغيل");
        buttons.addView(prev,new LinearLayout.LayoutParams(0,dp(47),1));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,dp(47),1.2f);cp.leftMargin=dp(5);cp.rightMargin=dp(5);buttons.addView(play,cp);buttons.addView(next,new LinearLayout.LayoutParams(0,dp(47),1));
        panel.addView(buttons,new LinearLayout.LayoutParams(-1,dp(52)));
        LinearLayout pr=new LinearLayout(this);pr.setGravity(Gravity.CENTER_VERTICAL);
        elapsed=label("00:00",10,0xFFFFD78A,true);progress=new SeekBar(this);progress.setMax(1000);total=label("00:00",10,0xFFB3C8D8,true);
        if(android.os.Build.VERSION.SDK_INT>=21)progress.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFF4B9B));
        pr.addView(elapsed,new LinearLayout.LayoutParams(dp(42),dp(32)));pr.addView(progress,new LinearLayout.LayoutParams(0,dp(32),1));pr.addView(total,new LinearLayout.LayoutParams(dp(42),dp(32)));
        panel.addView(pr,new LinearLayout.LayoutParams(-1,dp(33)));
        root.addView(panel,new LinearLayout.LayoutParams(-1,dp(150)));
        TextView heading=label("قائمة الملفات الصوتية ("+songs.size()+")",14,0xFFFFD78A,true);root.addView(heading,new LinearLayout.LayoutParams(-1,dp(30)));
        RecyclerView recycler=new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new SongAdapter());
        root.addView(recycler,new LinearLayout.LayoutParams(-1,0,1));
        TextView back=button("العودة إلى ALI CAR");back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(42)));
        prev.setOnClickListener(v->select(Math.max(0,current-1)));next.setOnClickListener(v->select(Math.min(songs.size()-1,current+1)));
        play.setOnClickListener(v->toggle());progress.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int v,boolean user){if(user&&player!=null&&prepared){try{elapsed.setText(time(player.getDuration()*v/1000));}catch(Exception ignored){}}}
            public void onStartTrackingTouch(SeekBar b){tracking=true;}
            public void onStopTrackingTouch(SeekBar b){tracking=false;if(player!=null&&prepared)try{player.seekTo(player.getDuration()*b.getProgress()/1000);}catch(Exception ignored){}}
        });
        setContentView(root);
    }
    private final class SongAdapter extends RecyclerView.Adapter<SongAdapter.Holder>{
        @Override public Holder onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout row=new LinearLayout(MusicActivity.this);row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(12),dp(5),dp(12),dp(5));row.setBackground(shape(0xFF102338,10,0xFF28465E));
            TextView title=label("",13,Color.WHITE,true),artist=label("",10,0xFFB3C8D8,false);
            row.addView(title,new LinearLayout.LayoutParams(-1,dp(25)));
            row.addView(artist,new LinearLayout.LayoutParams(-1,dp(18)));
            return new Holder(row,title,artist);
        }
        @Override public void onBindViewHolder(Holder holder,int position){
            if(songs.isEmpty()){holder.title.setText("لا توجد ملفات موسيقى في الذاكرة أو بطاقة SD.");holder.artist.setText("");holder.itemView.setOnClickListener(null);return;}
            Song song=songs.get(position);holder.title.setText(song.title);holder.artist.setText(song.artist);
            holder.itemView.setOnClickListener(v->{int p=holder.getAdapterPosition();if(p>=0)select(p);});
        }
        @Override public int getItemCount(){return Math.max(1,songs.size());}
        final class Holder extends RecyclerView.ViewHolder{
            final TextView title,artist;
            Holder(View v,TextView t,TextView a){super(v);title=t;artist=a;}
        }
    }
    private TextView label(String s,int z,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextColor(c);v.setTextSize(z);v.setTypeface(Typeface.create("sans-serif",bold?1:0));v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private android.graphics.drawable.GradientDrawable shape(int c,int r,int stroke){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(c);d.setCornerRadius(dp(r));d.setStroke(dp(1),stroke);return d;}
    private TextView button(String s){TextView v=label(s,14,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(shape(0xFF15538A,12,0xFF52D9FF));v.setClickable(true);return v;}
    private void select(int i){
        if(i<0||i>=songs.size())return;release();current=i;Song song=songs.get(i);now.setText(song.title+"\n"+song.artist);
        progress.setProgress(0);elapsed.setText("00:00");total.setText(time(song.duration));play.setText("جارٍ التحميل...");
        try{player=new MediaPlayer();player.setAudioStreamType(AudioManager.STREAM_MUSIC);prepared=false;
            player.setOnPreparedListener(mp->{if(isFinishing())return;prepared=true;try{mp.start();play.setText("Ⅱ إيقاف");h.removeCallbacks(tick);h.post(tick);}catch(Exception e){play.setText("تعذر التشغيل");}});
            player.setOnCompletionListener(mp->{play.setText("▶ إعادة");if(current+1<songs.size())select(current+1);});
            player.setOnErrorListener((mp,w,e)->{play.setText("خطأ في الملف");release();return true;});
            player.setDataSource(this,song.uri);player.prepareAsync();
        }catch(Exception e){play.setText("تعذر فتح الملف");release();}
    }
    private void toggle(){if(player==null){select(current<0?0:current);return;}if(!prepared)return;try{if(player.isPlaying()){player.pause();h.removeCallbacks(tick);play.setText("▶ متابعة");}else{player.start();play.setText("Ⅱ إيقاف");h.post(tick);}}catch(Exception ignored){}}
    private String time(int millis){int sec=Math.max(0,millis/1000);return String.format(Locale.US,"%02d:%02d",sec/60,sec%60);}
    private void release(){h.removeCallbacks(tick);prepared=false;if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    @Override protected void onResume(){super.onResume();immersive();}
    @Override protected void onPause(){release();super.onPause();}
    @Override protected void onDestroy(){release();h.removeCallbacksAndMessages(null);super.onDestroy();}
}
