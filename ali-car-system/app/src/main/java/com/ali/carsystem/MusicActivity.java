package com.ali.carsystem;

import android.app.Activity;
import android.app.AlertDialog;
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
    private int index=-1;
    private String activeCategory="الكل";
    private LinearLayout categoryStrip;
    private boolean musicReceiverRegistered;
    private final BroadcastReceiver musicStateReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(intent!=null&&MusicPlaybackService.ACTION_STATE.equals(intent.getAction())){
                String error=intent.getStringExtra("error");
                if(error!=null&&!error.isEmpty())Toast.makeText(MusicActivity.this,error,Toast.LENGTH_SHORT).show();
                refreshPlaybackFromPreferences();
            }
        }
    };
    private FrameLayout root;
    private DiscView disc;
    private VisualizerView visualizer;
    private TextView title,artist,playButton,empty;
    private SeekBar seek;
    private TextView elapsed,total;
    private boolean seeking;
    private final Runnable progressTick=new Runnable(){@Override public void run(){
        refreshPlaybackFromPreferences();
        handler.postDelayed(this,500);
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
        else if("next".equals(action)){playAt((lastMusicIndex()+1)%songs.size());}
        else if("previous".equals(action)){playAt((lastMusicIndex()-1+ songs.size())%songs.size());}
        else if("play".equals(action)){playAt(lastMusicIndex());}
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
            @Override public void onProgressChanged(SeekBar b,int p,boolean from){if(from&&elapsed!=null)elapsed.setText(clock(p));}
            @Override public void onStartTrackingTouch(SeekBar b){seeking=true;}
            @Override public void onStopTrackingTouch(SeekBar b){startMusicCommand(MusicPlaybackService.ACTION_SEEK,b.getProgress());seeking=false;}
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
        FrameLayout.LayoutParams scrollLp=new FrameLayout.LayoutParams(-1,dp(150),Gravity.BOTTOM);
        scrollLp.leftMargin=dp(16);scrollLp.rightMargin=dp(16);scrollLp.bottomMargin=dp(8);
        root.addView(scroll,scrollLp);

        HorizontalScrollView categoriesScroll=new HorizontalScrollView(this);
        categoriesScroll.setHorizontalScrollBarEnabled(false);categoriesScroll.setFillViewport(false);
        categoryStrip=new LinearLayout(this);categoryStrip.setGravity(Gravity.CENTER_VERTICAL);
        categoriesScroll.addView(categoryStrip,new HorizontalScrollView.LayoutParams(-2,-1));
        FrameLayout.LayoutParams categoriesLp=new FrameLayout.LayoutParams(-1,dp(36),Gravity.BOTTOM);
        categoriesLp.leftMargin=dp(12);categoriesLp.rightMargin=dp(12);categoriesLp.bottomMargin=dp(161);
        root.addView(categoriesScroll,categoriesLp);

        // Reserve room for the local playlist/category strip above the song list.
        column.setPadding(dp(18),dp(10),dp(18),dp(205));
        empty=text("لم يتم العثور على ملفات موسيقى على الجهاز",13,0xFFF4C5C5,true);
        empty.setGravity(Gravity.CENTER);empty.setVisibility(View.GONE);
        root.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        listContainer=songList;
        renderCategoryTabs();
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
        songs.clear();
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
        renderSongs();
    }

    private void renderSongs(){
        if(listContainer==null)return;
        listContainer.removeAllViews();
        ArrayList<Song> visible=getSongsForCategory(activeCategory);
        if(visible.isEmpty()){
            TextView noTracks=text(songs.isEmpty()?"لم يتم العثور على موسيقى على الجهاز":
                    "لا توجد أغنيات ضمن هذا القسم في ملفات الجهاز",12,0xFFF4C5C5,true);
            noTracks.setGravity(Gravity.CENTER);
            listContainer.addView(noTracks,new LinearLayout.LayoutParams(-1,dp(54)));
            return;
        }
        for(int i=0;i<visible.size();i++){
            Song song=visible.get(i);
            int songIndex=songs.indexOf(song);
            final int item=songIndex;
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
            listContainer.addView(row,rowLp);
            row.setOnClickListener(v->playAt(item));
            row.setOnLongClickListener(v->{showAddToPlaylistDialog(song);return true;});
        }
    }

    private ArrayList<String> categoryNames(){
        ArrayList<String> names=new ArrayList<>(Arrays.asList("الكل","وفيق حبيب","ناصيف زيتون",
                "فيروز الصباح","إليسا","أصالة","أغاني أجنبية","أغاني السفر","ترندات 2026"));
        Set<String> custom=getSharedPreferences("ali_music_playlists",MODE_PRIVATE)
                .getStringSet("custom_playlists",Collections.<String>emptySet());
        if(custom!=null)for(String name:custom)if(!names.contains(name))names.add(name);
        return names;
    }

    private void renderCategoryTabs(){
        if(categoryStrip==null)return;
        categoryStrip.removeAllViews();
        for(String name:categoryNames()){
            TextView chip=text(name,11,Color.WHITE,true);
            chip.setGravity(Gravity.CENTER);chip.setPadding(dp(13),0,dp(13),0);
            chip.setBackground(glass(name.equals(activeCategory)?0xFF087F9E:0xCC173247,
                    name.equals(activeCategory)?0xFF8BE8FF:0x665B7184,dp(18)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(30));
            lp.rightMargin=dp(6);categoryStrip.addView(chip,lp);
            chip.setOnClickListener(v->{activeCategory=name;renderCategoryTabs();renderSongs();});
        }
        TextView add=text("＋ قائمة",11,0xFF80E5FF,true);
        add.setGravity(Gravity.CENTER);add.setPadding(dp(13),0,dp(13),0);
        add.setBackground(glass(0xFF27384A,0xFF65D8F4,dp(18)));
        LinearLayout.LayoutParams addLp=new LinearLayout.LayoutParams(-2,dp(30));
        addLp.rightMargin=dp(6);categoryStrip.addView(add,addLp);
        add.setOnClickListener(v->createPlaylist());
    }

    private ArrayList<Song> getSongsForCategory(String category){
        ArrayList<Song> visible=new ArrayList<>();
        if("الكل".equals(category)){visible.addAll(songs);return visible;}
        if("أغاني السفر".equals(category)||"ترندات 2026".equals(category)
                ||getCustomPlaylistIds(category)!=null){
            Set<String> ids=getCustomPlaylistIds(category);
            if(ids!=null)for(Song song:songs)if(ids.contains(String.valueOf(song.id)))visible.add(song);
            return visible;
        }
        for(Song song:songs){
            String artistName=song.artist==null?"":song.artist.toLowerCase(new Locale("ar"));
            String titleName=song.title==null?"":song.title.toLowerCase(new Locale("ar"));
            if("وفيق حبيب".equals(category)&&artistName.contains("وفيق"))visible.add(song);
            else if("ناصيف زيتون".equals(category)&&artistName.contains("ناصيف"))visible.add(song);
            else if("فيروز الصباح".equals(category)&&(artistName.contains("فيروز")||artistName.contains("fairuz")))visible.add(song);
            else if("إليسا".equals(category)&&artistName.contains("إليسا"))visible.add(song);
            else if("أصالة".equals(category)&&artistName.contains("أصالة"))visible.add(song);
            else if("أغاني أجنبية".equals(category)&&!containsArabic(titleName+artistName))visible.add(song);
        }
        return visible;
    }

    private boolean containsArabic(String value){
        if(value==null)return false;
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            if(c>='\\u0600'&&c<='\\u06FF')return true;
        }
        return false;
    }

    private Set<String> getCustomPlaylistIds(String name){
        if(name==null||"الكل".equals(name)||"وفيق حبيب".equals(name)||"ناصيف زيتون".equals(name)
                ||"فيروز الصباح".equals(name)||"إليسا".equals(name)||"أصالة".equals(name)
                ||"أغاني أجنبية".equals(name))return null;
        Set<String> ids=getSharedPreferences("ali_music_playlists",MODE_PRIVATE)
                .getStringSet("list_"+name,Collections.<String>emptySet());
        return ids==null?Collections.<String>emptySet():new HashSet<>(ids);
    }

    private void createPlaylist(){
        EditText input=new EditText(this);input.setSingleLine(true);input.setHint("اسم قائمة التشغيل");
        new AlertDialog.Builder(this).setTitle("إنشاء قائمة تشغيل")
                .setView(input).setNegativeButton("إلغاء",null)
                .setPositiveButton("إنشاء",(dialog,which)->{
                    String name=input.getText().toString().trim();
                    if(name.length()<2){Toast.makeText(this,"اكتب اسماً أطول للقائمة",Toast.LENGTH_SHORT).show();return;}
                    SharedPreferences prefs=getSharedPreferences("ali_music_playlists",MODE_PRIVATE);
                    Set<String> names=new HashSet<>(prefs.getStringSet("custom_playlists",Collections.<String>emptySet()));
                    if(!names.contains(name)){
                        names.add(name);prefs.edit().putStringSet("custom_playlists",names).apply();
                    }
                    activeCategory=name;renderCategoryTabs();renderSongs();
                }).show();
    }

    private void showAddToPlaylistDialog(Song song){
        ArrayList<String> names=new ArrayList<>(Arrays.asList("أغاني السفر","ترندات 2026"));
        SharedPreferences prefs=getSharedPreferences("ali_music_playlists",MODE_PRIVATE);
        Set<String> custom=prefs.getStringSet("custom_playlists",Collections.<String>emptySet());
        if(custom!=null)for(String name:custom)if(!names.contains(name))names.add(name);
        names.add("＋ إنشاء قائمة جديدة");
        String[] choices=names.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle("إضافة إلى قائمة تشغيل")
                .setItems(choices,(dialog,which)->{
                    String selected=choices[which];
                    if(selected.startsWith("＋")){createPlaylist();return;}
                    Set<String> ids=new HashSet<>(prefs.getStringSet("list_"+selected,Collections.<String>emptySet()));
                    ids.add(String.valueOf(song.id));
                    prefs.edit().putStringSet("list_"+selected,ids).apply();
                    Toast.makeText(this,"تمت إضافة الأغنية إلى "+selected,Toast.LENGTH_SHORT).show();
                    if(selected.equals(activeCategory))renderSongs();
                }).show();
    }

    private void playAt(int position){
        if(position<0||position>=songs.size())return;
        index=position;Song song=songs.get(index);
        getSharedPreferences("ali_car_trip_data",MODE_PRIVATE).edit()
                .putInt("last_music_index",index).putString("last_song_title",song.title).apply();
        Uri uri=Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,String.valueOf(song.id));
        Intent command=new Intent(this,MusicPlaybackService.class);
        command.setAction(MusicPlaybackService.ACTION_PLAY);
        command.putExtra(MusicPlaybackService.EXTRA_URI,uri.toString());
        command.putExtra(MusicPlaybackService.EXTRA_TITLE,song.title);
        command.putExtra(MusicPlaybackService.EXTRA_ARTIST,song.artist);
        startService(command);
        refreshTrack(song);loadAlbumArt(song.albumId);
    }
    private void refreshTrack(Song song){
        if(title!=null)title.setText(song.title);if(artist!=null)artist.setText(song.artist);
        if(playButton!=null)playButton.setText("Ⅱ");
        if(disc!=null)disc.setAlbumArt(null);
    }
    private int lastMusicIndex(){
        android.content.SharedPreferences saved=getSharedPreferences("ali_car_trip_data",MODE_PRIVATE);
        String lastTitle=saved.getString("last_song_title","");
        if(lastTitle!=null&&!lastTitle.isEmpty()){
            for(int i=0;i<songs.size();i++){
                if(lastTitle.equalsIgnoreCase(songs.get(i).title))return i;
            }
        }
        int lastIndex=saved.getInt("last_music_index",-1);
        return lastIndex>=0&&lastIndex<songs.size()?lastIndex:0;
    }
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
    private Intent sendMusicCommand(String action){
        Intent command=new Intent(this,MusicPlaybackService.class);command.setAction(action);return command;
    }
    private void startMusicCommand(String action){
        startService(sendMusicCommand(action));
    }
    private void startMusicCommand(String action,int position){
        Intent command=sendMusicCommand(action);command.putExtra(MusicPlaybackService.EXTRA_POSITION,position);startService(command);
    }
    private void togglePlayback(){
        if(index<0){
            String lastTitle=getSharedPreferences("ali_car_trip_data",MODE_PRIVATE).getString("last_song_title","");
            if(!lastTitle.isEmpty())index=lastMusicIndex();
            else if(!songs.isEmpty()){playAt(0);return;}
        }
        startMusicCommand(MusicPlaybackService.ACTION_TOGGLE);
    }
    private void previous(){
        if(songs.isEmpty())return;
        SharedPreferences saved=getSharedPreferences("ali_car_music_state",MODE_PRIVATE);
        if(saved.getInt("position_ms",0)>3500){startMusicCommand(MusicPlaybackService.ACTION_SEEK,0);return;}
        playAt(index<=0?songs.size()-1:index-1);
    }
    private void next(){if(songs.isEmpty())return;playAt(index<0?0:(index+1)%songs.size());}
    private void refreshPlaybackFromPreferences(){
        SharedPreferences state=getSharedPreferences("ali_car_music_state",MODE_PRIVATE);
        boolean playing=state.getBoolean("is_playing",false);
        int duration=Math.max(0,state.getInt("duration_ms",0));
        int position=Math.max(0,state.getInt("position_ms",0));
        String savedTitle=state.getString("song_title","");
        String savedArtist=state.getString("song_artist","");
        if(title!=null&&!savedTitle.isEmpty())title.setText(savedTitle);
        if(artist!=null&&!savedArtist.isEmpty())artist.setText(savedArtist);
        if(playButton!=null)playButton.setText(playing?"Ⅱ":"▶");
        if(seek!=null&&!seeking){seek.setMax(Math.max(1,duration));seek.setProgress(Math.min(position,Math.max(1,duration)));}
        if(elapsed!=null)elapsed.setText(clock(position));
        if(total!=null)total.setText(clock(duration));
        if(disc!=null)disc.setPlaying(playing);
        if(visualizer!=null)visualizer.setActive(playing);
    }
    private void registerMusicReceiver(){
        if(musicReceiverRegistered)return;
        try{registerReceiver(musicStateReceiver,new IntentFilter(MusicPlaybackService.ACTION_STATE));musicReceiverRegistered=true;}
        catch(Exception ignored){}
    }
    private void unregisterMusicReceiver(){
        if(!musicReceiverRegistered)return;
        try{unregisterReceiver(musicStateReceiver);}catch(Exception ignored){}
        musicReceiverRegistered=false;
    }
    private String clock(int milliseconds){int seconds=Math.max(0,milliseconds/1000);return String.format(Locale.US,"%02d:%02d",seconds/60,seconds%60);}
    @Override protected void onResume(){
        super.onResume();immersive();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        registerMusicReceiver();refreshPlaybackFromPreferences();
        handler.removeCallbacks(progressTick);handler.post(progressTick);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(progressTick);unregisterMusicReceiver();super.onPause();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);unregisterMusicReceiver();super.onDestroy();
    }

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
