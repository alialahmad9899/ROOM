package com.ali.carsystem;

import android.app.Service;
import android.content.*;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.provider.MediaStore;
import java.util.*;

public final class MusicPlaybackService extends Service {
    public static final String ACTION_STATE="com.ali.carsystem.MUSIC_STATE";
    private static final String ACTION_TOGGLE="com.ali.carsystem.TOGGLE",ACTION_NEXT="com.ali.carsystem.NEXT",ACTION_PREVIOUS="com.ali.carsystem.PREVIOUS";
    private final IBinder binder=new LocalBinder();private final ArrayList<Song> songs=new ArrayList<>();
    private MediaPlayer player;private int index=-1;private boolean prepared,repeat,shuffle;private Listener listener;
    public interface Listener{void onMusicStateChanged();}
    public final class LocalBinder extends Binder{MusicPlaybackService getService(){return MusicPlaybackService.this;}}
    public static final class Song{
        public final long id,duration,albumId;public final String name,artist;
        Song(long i,String n,String a,long d,long album){id=i;name=n;artist=a;duration=d;albumId=album;}
    }
    public static void dispatch(Context c,String action){
        Intent i=new Intent(c,MusicPlaybackService.class);
        if("previous".equals(action))i.setAction(ACTION_PREVIOUS);else if("next".equals(action))i.setAction(ACTION_NEXT);else i.setAction(ACTION_TOGGLE);
        c.startService(i);
    }
    @Override public void onCreate(){super.onCreate();loadLibrary();}
    @Override public IBinder onBind(Intent i){return binder;}
    @Override public int onStartCommand(Intent intent,int flags,int id){
        loadLibrary();if(intent!=null&&intent.getAction()!=null){
            switch(intent.getAction()){case ACTION_TOGGLE:toggle();break;case ACTION_NEXT:next();break;case ACTION_PREVIOUS:previous();break;}
        }else notifyChanged();return START_STICKY;
    }
    private void loadLibrary(){
        if(!songs.isEmpty())return;
        String[] cols={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.ALBUM_ID};
        try(Cursor c=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,cols,MediaStore.Audio.Media.IS_MUSIC+"=1",null,MediaStore.Audio.Media.TITLE+" ASC")){
            if(c!=null)while(c.moveToNext())songs.add(new Song(c.getLong(0),c.getString(1),c.getString(2),c.getLong(3),c.getLong(4)));
        }catch(Exception ignored){}
    }
    public List<Song> getSongs(){loadLibrary();return new ArrayList<>(songs);}
    public Song getCurrentSong(){return index>=0&&index<songs.size()?songs.get(index):null;}
    public int getPosition(){try{return player!=null&&prepared?player.getCurrentPosition():0;}catch(Exception e){return 0;}}
    public int getDuration(){try{return player!=null&&prepared?player.getDuration():0;}catch(Exception e){return 0;}}
    public int getAudioSessionId(){try{return player==null?0:player.getAudioSessionId();}catch(Exception e){return 0;}}
    public boolean isPlaying(){try{return player!=null&&prepared&&player.isPlaying();}catch(Exception e){return false;}}
    public boolean isPrepared(){return prepared;}
    public void setListener(Listener l){listener=l;if(l!=null)l.onMusicStateChanged();}
    public void playAt(int i){
        loadLibrary();if(i<0||i>=songs.size())return;releasePlayer();index=i;Song song=songs.get(i);
        try{
            player=new MediaPlayer();prepared=false;player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            Uri uri=Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,String.valueOf(song.id));player.setDataSource(this,uri);
            player.setOnPreparedListener(mp->{prepared=true;mp.start();notifyChanged();});
            player.setOnCompletionListener(mp->{if(repeat){mp.seekTo(0);mp.start();}else next();});
            player.setOnErrorListener((mp,w,e)->{prepared=false;notifyChanged();return true;});
            player.prepareAsync();notifyChanged();
        }catch(Exception e){releasePlayer();notifyChanged();}
    }
    public void toggle(){
        if(player==null||index<0){if(!songs.isEmpty())playAt(0);return;}
        try{if(prepared){if(player.isPlaying())player.pause();else player.start();}}catch(Exception ignored){}notifyChanged();
    }
    public void next(){if(songs.isEmpty())return;int n=shuffle?new Random().nextInt(songs.size()):Math.floorMod(index+1,songs.size());playAt(n);}
    public void previous(){
        if(songs.isEmpty())return;if(player!=null&&prepared&&getPosition()>3000){player.seekTo(0);notifyChanged();return;}
        playAt(index<0?0:Math.floorMod(index-1,songs.size()));
    }
    public void seekTo(int ms){if(player!=null&&prepared)try{player.seekTo(Math.max(0,Math.min(ms,getDuration())));}catch(Exception ignored){}}
    public void setShuffle(boolean v){shuffle=v;notifyChanged();}public boolean isShuffle(){return shuffle;}
    public void setRepeat(boolean v){repeat=v;notifyChanged();}public boolean isRepeat(){return repeat;}
    private void notifyChanged(){
        if(listener!=null)listener.onMusicStateChanged();Song s=getCurrentSong();Intent i=new Intent(ACTION_STATE);
        i.putExtra("title",s==null?"":s.name);i.putExtra("playing",isPlaying());sendBroadcast(i);
    }
    private void releasePlayer(){prepared=false;if(player!=null){try{player.reset();player.release();}catch(Exception ignored){}player=null;}}
    @Override public void onDestroy(){releasePlayer();listener=null;super.onDestroy();}
}
