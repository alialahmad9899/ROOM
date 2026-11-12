package com.ali.carsystem;

import android.app.Activity;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.view.WindowManager;
import android.widget.*;
import java.io.InputStream;
import java.util.*;

public final class MusicActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ArrayList<Song> songs=new ArrayList<>();
    private MediaPlayer player;
    private int index=-1;
    private FrameLayout root;
    private DiscView disc;
    private VisualizerView visualizer;
    private TextView title,artist,playButton,empty;
    private SeekBar seek;
    private TextView elapsed,total;
    private boolean seeking;
    private final Runnable progressTick=new Runnable(){@Override public void run(){
        if(player!=null&&player.isPlaying()){
            int duration=Math.max(1,player.getDuration()),position=player.getCurrentPosition();
            if(seek!=null&&!seeking){seek.setMax(duration);seek.setProgress(position);}
            if(elapsed!=null)elapsed.setText(clock(position));
            if(total!=null)total.setText(clock(duration));
            if(disc!=null)disc.setPlaying(true);
            if(visualizer!=null)visualizer.setActive(true);
        }else{
            if(disc!=null)disc.setPlaying(false);
            if(visualizer!=null)visualizer.setActive(false);
        }
        handler.postDelayed(this,400);
    }};
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private void immersive(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);immersive();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        buildUi();loadSongs();
        String action=getIntent().getStringExtra("ali.music.action");
        if(songs.isEmpty()){empty.setVisibility(View.VISIBLE);}
        else if("next".equals(action)){playAt(0);next();}
        else if("previous".equals(action)){playAt(songs.size()>1?songs.size()-1:0);}
        else if("play".equals(action)){playAt(0);}
        handler.post(progressTick);
    }
    private void buildUi(){
        root=new FrameLayout(this);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0B1B2A,0xFF03070D,0xFF182338}));
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(18),dp(10),dp(18),dp(10));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text("‹",34,0xFFB9ECFF,true);back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->finish());header.addView(back,new LinearLayout.LayoutParams(dp(42),dp(42)));
        TextView heading=text("ALI CAR  •  MUSIC",18,Color.WHITE,true);heading.setGravity(Gravity.CENTER);
        header.addView(heading,new LinearLayout.LayoutParams(0,dp(42),1f));
        column.addView(header,new LinearLayout.LayoutParams(-1,dp(42)));

        LinearLayout now=new LinearLayout(this);now.setGravity(Gravity.CENTER_VERTICAL);
        disc=new DiscView(this);now.addView(disc,new LinearLayout.LayoutParams(dp(174),dp(174)));
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setGravity(Gravity.CENTER);
        title=text("اختر أغنية من القائمة",17,Color.WHITE,true);title.setGravity(Gravity.CENTER);
        artist=text("مشغل موسيقى محلي",12,0xFF9AC0D8,false);artist.setGravity(Gravity.CENTER);
        info.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        info.addView(artist,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout.LayoutParams infoLp=new LinearLayout.LayoutParams(0,-2,1f);infoLp.leftMargin=dp(12);now.addView(info,infoLp);
        column.addView(now,new LinearLayout.LayoutParams(-1,dp(180)));

        visualizer=new VisualizerView(this);LinearLayout.LayoutParams visualLp=new LinearLayout.LayoutParams(-1,dp(46));
        visualLp.topMargin=dp(5);column.addView(visualizer,visualLp);
        LinearLayout timeline=new LinearLayout(this);timeline.setGravity(Gravity.CENTER_VERTICAL);
        elapsed=text("00:00",10,0xFFB4CDDC,false);total=text("00:00",10,0xFFB4CDDC,false);
        timeline.addView(elapsed,new LinearLayout.LayoutParams(dp(42),dp(24)));
        seek=new SeekBar(this);timeline.addView(seek,new LinearLayout.LayoutParams(0,dp(30),1f));
        timeline.addView(total,new LinearLayout.LayoutParams(dp(42),dp(24)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar b,int p,boolean from){if(from&&player!=null)elapsed.setText(clock(p));}
            @Override public void onStartTrackingTouch(SeekBar b){seeking=true;}
            @Override public void onStopTrackingTouch(SeekBar b){if(player!=null)player.seekTo(b.getProgress());seeking=false;}
        });
        column.addView(timeline,new LinearLayout.LayoutParams(-1,dp(34)));

        LinearLayout controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        controls.addView(control("⏮",()->previous()),new LinearLayout.LayoutParams(dp(62),dp(54)));
        playButton=control("▶",()->togglePlayback());LinearLayout.LayoutParams playLp=new LinearLayout.LayoutParams(dp(74),dp(58));
        playLp.leftMargin=dp(13);playLp.rightMargin=dp(13);controls.addView(playButton,playLp);
        controls.addView(control("⏭",()->next()),new LinearLayout.LayoutParams(dp(62),dp(54)));
        column.addView(controls,new LinearLayout.LayoutParams(-1,dp(60)));

        TextView listHeading=text("قائمة الأغاني",14,0xFFE6F7FF,true);listHeading.setPadding(dp(4),dp(5),dp(4),dp(5));
        column.addView(listHeading,new LinearLayout.LayoutParams(-1,dp(30)));
        ScrollView scroll=new ScrollView(this);LinearLayout songList=new LinearLayout(this);
        songList.setOrientation(LinearLayout.VERTICAL);songList.setPadding(0,dp(4),0,dp(4));scroll.addView(songList);
        root.addView(column,new FrameLayout.LayoutParams(-1,-1));
        FrameLayout.LayoutParams scrollLp=new FrameLayout.LayoutParams(-1,dp(155),Gravity.BOTTOM);
        scrollLp.leftMargin=dp(16);scrollLp.rightMargin=dp(16);scrollLp.bottomMargin=dp(8);
        root.addView(scroll,scrollLp);
        // Main layout leaves space for the song list without nested full-size black panels.
        column.setPadding(dp(18),dp(10),dp(18),dp(170));
        empty=text("لم يتم العثور على ملفات موسيقى على الجهاز",13,0xFFF4C5C5,true);
        empty.setGravity(Gravity.CENTER);empty.setVisibility(View.GONE);
        root.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        listContainer=songList;
    }
    private LinearLayout listContainer;
    private TextView control(String glyph,Runnable action){
        TextView button=text(glyph,26,0xFFECF8FF,true);button.setGravity(Gravity.CENTER);
        button.setBackground(glass(0xBB15354B,0xAA55DFFF,dp(25)));button.setElevation(dp(5));
        button.setOnClickListener(v->action.run());return button;
    }
    private GradientDrawable glass(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{color,0xDD0C1D2C});
        d.setCornerRadius(radius);d.setStroke(dp(1),stroke);return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;
    }
    private void loadSongs(){
        String[] projection={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM_ID,MediaStore.Audio.Media.DURATION};
        Cursor cursor=null;
        try{
            cursor=getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,projection,
                    MediaStore.Audio.Media.IS_MUSIC+"!=0",null,MediaStore.Audio.Media.TITLE+" ASC");
            if(cursor!=null){
                while(cursor.moveToNext()){
                    long id=cursor.getLong(0);String name=cursor.getString(1),singer=cursor.getString(2);
                    long album=cursor.getLong(3),duration=cursor.getLong(4);
                    if(name!=null)songs.add(new Song(id,name,singer==null?"غير معروف":singer,album,duration));
                }
            }
        }catch(Exception ignored){}finally{if(cursor!=null)cursor.close();}
        if(listContainer!=null){
            listContainer.removeAllViews();
            for(int i=0;i<songs.size();i++){
                final int item=i;Song song=songs.get(i);
                LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(4),dp(12),dp(4));
                row.setBackground(glass(0x66152D40,0x334DDFFF,dp(12)));
                TextView number=text(String.format(Locale.US,"%02d",i+1),12,0xFF7DDCFF,true);number.setGravity(Gravity.CENTER);
                row.addView(number,new LinearLayout.LayoutParams(dp(40),dp(44)));
                LinearLayout labels=new LinearLayout(this);labels.setOrientation(LinearLayout.VERTICAL);
                TextView name=text(song.title,13,Color.WHITE,true);
                TextView by=text(song.artist,10,0xFF92AFC2,false);
                labels.addView(name,new LinearLayout.LayoutParams(-1,dp(22)));
                labels.addView(by,new LinearLayout.LayoutParams(-1,dp(18)));
                row.addView(labels,new LinearLayout.LayoutParams(0,dp(42),1f));
                TextView duration=text(clock((int)song.duration),10,0xFFB8D1DF,false);
                row.addView(duration,new LinearLayout.LayoutParams(dp(54),dp(42)));
                LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,dp(50));rowLp.bottomMargin=dp(5);
                listContainer.addView(row,rowLp);row.setOnClickListener(v->playAt(item));
            }
        }
    }
    private void playAt(int position){
        if(position<0||position>=songs.size())return;
        releasePlayer();index=position;Song song=songs.get(index);
        try{
            player=new MediaPlayer();
            Uri uri=Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,String.valueOf(song.id));
            player.setDataSource(this,uri);player.setOnPreparedListener(mp->{mp.start();refreshTrack(song);loadAlbumArt(song.albumId);});
            player.setOnCompletionListener(mp->next());player.setOnErrorListener((mp,what,extra)->{Toast.makeText(this,"تعذر تشغيل الملف الصوتي",Toast.LENGTH_SHORT).show();return true;});
            player.prepareAsync();
            title.setText(song.title);artist.setText(song.artist);
        }catch(Exception error){Toast.makeText(this,"تعذر فتح الأغنية",Toast.LENGTH_SHORT).show();releasePlayer();}
    }
    private void refreshTrack(Song song){
        if(title!=null)title.setText(song.title);if(artist!=null)artist.setText(song.artist);
        if(playButton!=null)playButton.setText("Ⅱ");
        if(disc!=null)disc.setAlbumArt(null);
        if(songTitlePreferenceAvailable())getSharedPreferences("ali_car_trip_data",MODE_PRIVATE).edit().putString("last_song_title",song.title).apply();
    }
    private boolean songTitlePreferenceAvailable(){return true;}
    private void loadAlbumArt(long albumId){
        Bitmap bitmap=null;
        try{
            Uri uri=Uri.parse("content://media/external/audio/albumart/"+albumId);
            try(InputStream in=getContentResolver().openInputStream(uri)){
                if(in!=null){BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.RGB_565;o.inSampleSize=2;bitmap=BitmapFactory.decodeStream(in,null,o);}
            }
        }catch(Exception ignored){}
        if(disc!=null)disc.setAlbumArt(bitmap);
    }
    private void togglePlayback(){
        if(player==null){if(index<0&&!songs.isEmpty())playAt(0);return;}
        try{
            if(player.isPlaying()){player.pause();playButton.setText("▶");disc.setPlaying(false);}
            else{player.start();playButton.setText("Ⅱ");disc.setPlaying(true);}
        }catch(Exception ignored){}
    }
    private void previous(){if(songs.isEmpty())return;if(player!=null&&player.getCurrentPosition()>3500){player.seekTo(0);return;}playAt(index<=0?songs.size()-1:index-1);}
    private void next(){if(songs.isEmpty())return;playAt(index<0?0:(index+1)%songs.size());}
    private String clock(int milliseconds){int seconds=Math.max(0,milliseconds/1000);return String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
    private void releasePlayer(){
        handler.removeCallbacks(progressTick);
        if(player!=null){try{player.setOnPreparedListener(null);player.setOnCompletionListener(null);player.stop();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
    }
    @Override protected void onResume(){super.onResume();immersive();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);handler.removeCallbacks(progressTick);handler.post(progressTick);}
    @Override protected void onPause(){handler.removeCallbacks(progressTick);if(player!=null&&player.isPlaying()){try{player.pause();}catch(Exception ignored){}}super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);releasePlayer();super.onDestroy();}

    private static final class Song{
        final long id,albumId,duration;final String title,artist;
        Song(long id,String title,String artist,long album,long duration){this.id=id;this.title=title;this.artist=artist;albumId=album;this.duration=duration;}
    }
    private final class DiscView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap art;private float angle;private boolean playing;
        private final Runnable spin=new Runnable(){@Override public void run(){if(playing){angle=(angle+1.8f)%360;invalidate();postDelayed(this,32);}}};
        DiscView(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setAlbumArt(Bitmap b){art=b;invalidate();}
        void setPlaying(boolean value){if(playing==value)return;playing=value;removeCallbacks(spin);if(value)post(spin);}
        @Override protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
            p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx,cy,r,0xFF637B91,0xFF0A111A,Shader.TileMode.CLAMP));
            c.drawCircle(cx,cy,r,p);p.setShader(null);
            c.save();c.rotate(angle,cx,cy);
            if(art!=null){
                BitmapShader shader=new BitmapShader(art,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);
                p.setShader(shader);c.drawCircle(cx,cy,r*.76f,p);p.setShader(null);
            }else{
                for(int i=0;i<5;i++){p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(i%2==0?0xFF38CFFF:0xFF6F8BA4);c.drawCircle(cx,cy,r*(.30f+i*.11f),p);}
            }
            p.setStyle(Paint.Style.FILL);p.setColor(0xFF0A1118);c.drawCircle(cx,cy,r*.19f,p);
            p.setColor(0xFF6ADFFF);c.drawCircle(cx,cy,r*.07f,p);
            c.restore();
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(0xAA87E8FF);c.drawCircle(cx,cy,r,p);
        }
    }
    private final class VisualizerView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private long phase;private boolean active;
        private final Runnable tick=new Runnable(){@Override public void run(){if(active){phase+=42;invalidate();postDelayed(this,42);}}};
        VisualizerView(Context c){super(c);}
        void setActive(boolean value){if(active==value)return;active=value;removeCallbacks(tick);if(active)post(tick);invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);int bars=44;float width=getWidth()/(float)bars;
            for(int i=0;i<bars;i++){
                float wave=(float)(Math.sin((phase*.008)+(i*.44))*0.5+0.5);
                float h=active?dp(5)+wave*getHeight()*.72f:dp(3);
                p.setColor(active?(i%3==0?0xFF75F4D6:0xFF48D3FF):0x665A8BA3);
                p.setShadowLayer(dp(5),0,0,p.getColor());
                c.drawRoundRect(i*width+dp(1),getHeight()/2f-h/2f,(i+1)*width-dp(1),getHeight()/2f+h/2f,dp(4),dp(4),p);
            }p.clearShadowLayer();
        }
    }
}
