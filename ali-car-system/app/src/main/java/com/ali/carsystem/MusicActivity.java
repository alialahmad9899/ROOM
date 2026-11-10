package com.ali.carsystem;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.graphics.Typeface;

import java.io.IOException;
import java.util.ArrayList;

public final class MusicActivity extends Activity {
    private final ArrayList<Song> songs=new ArrayList<>();
    private MediaPlayer player;
    private int current=-1;
    private TextView nowPlaying,playButton;
    private boolean receiverRegistered=false;
    private final BroadcastReceiver screenOff=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_OFF.equals(i.getAction()))pausePlayback();}
    };
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().setStatusBarColor(0xFF080D13);
        getWindow().setNavigationBarColor(0xFF080D13);
        loadSongs();
        build();
        registerReceiver(screenOff,new IntentFilter(Intent.ACTION_SCREEN_OFF));
        receiverRegistered=true;
    }
    @Override protected void onPause(){super.onPause();}
    @Override protected void onDestroy(){
        if(receiverRegistered){unregisterReceiver(screenOff);receiverRegistered=false;}
        release();
        super.onDestroy();
    }
    private void loadSongs(){
        String[] projection={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION};
        String selection=MediaStore.Audio.Media.IS_MUSIC+" != 0";
        Cursor c=null;
        try{c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection,selection,null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC");
            if(c!=null)while(c.moveToNext()){
                long id=c.getLong(0);String title=c.getString(1);String artist=c.getString(2);long duration=c.getLong(3);
                Uri uri=Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,String.valueOf(id));
                songs.add(new Song(title==null?"مقطع بدون اسم":title,artist==null?"فنان غير معروف":artist,uri,duration));
            }
        }catch(Exception ignored){}finally{if(c!=null)c.close();}
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(18),dp(14),dp(18),dp(14));root.setBackgroundColor(0xFF080D13);root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        TextView title=text("ALI MUSIC",24,0xFFF2F6F9,true);root.addView(title,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView subtitle=text("مكتبة الموسيقى • الذاكرة وبطاقة SD",13,0xFF42D9D0,false);root.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(30)));
        nowPlaying=text("اختر أغنية للبدء",16,0xFFF2F6F9,true);nowPlaying.setGravity(Gravity.CENTER);root.addView(nowPlaying,new LinearLayout.LayoutParams(-1,dp(58)));
        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        TextView prev=button("السابق");playButton=button("تشغيل");TextView next=button("التالي");
        controls.addView(prev);controls.addView(playButton);controls.addView(next);root.addView(controls,new LinearLayout.LayoutParams(-1,dp(58)));
        prev.setOnClickListener(v->playIndex(Math.max(0,current-1)));next.setOnClickListener(v->playIndex(Math.min(songs.size()-1,current+1)));
        playButton.setOnClickListener(v->{if(player!=null&&player.isPlaying())pausePlayback();else if(player!=null){player.start();playButton.setText("إيقاف مؤقت");}else if(!songs.isEmpty())playIndex(current<0?0:current);});
        ScrollView scroll=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);
        if(songs.isEmpty()){TextView empty=text("لم يتم العثور على ملفات موسيقى. تأكد من وجود ملفات MP3 أو AAC على الذاكرة أو بطاقة SD.",14,0xFF91A4B2,false);empty.setPadding(dp(8),dp(20),dp(8),dp(20));list.addView(empty);}
        for(int i=0;i<songs.size();i++){final int index=i;Song s=songs.get(i);TextView row=text((i+1)+".  "+s.title+"\n      "+s.artist,15,0xFFF2F6F9,false);row.setPadding(dp(12),dp(8),dp(12),dp(8));row.setBackgroundColor(i%2==0?0xFF111C27:0xFF172633);list.addView(row,new LinearLayout.LayoutParams(-1,dp(58)));row.setOnClickListener(v->playIndex(index));}
        scroll.addView(list);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView back=button("العودة إلى ALI");back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(48)));
        setContentView(root);
    }
    private TextView text(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);t.setTypeface(Typeface.create("sans-serif",bold?1:0));return t;}
    private TextView button(String s){TextView t=text(s,15,0xFF42D9D0,true);t.setGravity(Gravity.CENTER);t.setPadding(dp(12),dp(6),dp(12),dp(6));return t;}
    private void playIndex(int index){
        if(index<0||index>=songs.size())return;release();current=index;Song s=songs.get(index);
        try{player=new MediaPlayer();player.setDataSource(this,s.uri);player.setOnCompletionListener(mp->{if(current+1<songs.size())playIndex(current+1);else{playButton.setText("تشغيل");}});player.prepare();player.start();nowPlaying.setText(s.title+"\n"+s.artist);playButton.setText("إيقاف مؤقت");}
        catch(IOException|RuntimeException e){release();nowPlaying.setText("تعذّر تشغيل الملف");playButton.setText("تشغيل");}
    }
    private void pausePlayback(){if(player!=null&&player.isPlaying()){try{player.pause();playButton.setText("متوقف عند إطفاء الشاشة");}catch(Exception ignored){}}}
    private void release(){if(player!=null){try{player.stop();}catch(Exception ignored){}player.release();player=null;}}
    private static final class Song{final String title,artist;final Uri uri;final long duration;Song(String t,String a,Uri u,long d){title=t;artist=a;uri=u;duration=d;}}
}
