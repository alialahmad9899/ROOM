package com.ali.carsystem;

import android.content.*;
import android.app.AlertDialog;
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
    private final ArrayList<MusicPlaybackService.Song> allSongs=new ArrayList<>();
    private TextView emptyLibrary;
    private String activePlaylist="كل الأغاني";
    private static final String[] PLAYLISTS={
            "كل الأغاني","فيروزيات","نصيف زيتون","إليسا","أصالة",
            "أغاني سورية","أغاني لبنانية","أغاني رومانسية",
            "أغاني هادئة للقيادة","أغاني حماسية","ترند عربي 2026",
            "المفضلة","آخر تشغيل"
    };
    private boolean charging,playing;
    private BroadcastReceiver stateReceiver,powerReceiver;
    private FrameLayout sleepOverlay;
    private TextView sleepClock;
    private float sleepPreviousBrightness=-1f;
    private boolean sleepBrightnessCaptured;
    private final Runnable idleSleep=new Runnable(){@Override public void run(){showMusicSleep();}};
    private final Runnable sleepClockTick=new Runnable(){@Override public void run(){
        if(sleepClock!=null){sleepClock.setText(new java.text.SimpleDateFormat("HH:mm",Locale.US).format(new Date()));handler.postDelayed(this,1000L);}
    }};
    private final Runnable ticker=new Runnable(){@Override public void run(){updateProgress();handler.postDelayed(this,250);}};
    private final ServiceConnection connection=new ServiceConnection(){
        @Override public void onServiceConnected(ComponentName n,IBinder b){
            service=((MusicPlaybackService.LocalBinder)b).getService();bound=true;
            service.setListener(()->runOnUiThread(()->{
                allSongs.clear();allSongs.addAll(service.getSongs());
                applyPlaylistFilter();
                refreshPlayer();
            }));
            allSongs.clear();allSongs.addAll(service.getSongs());
            applyPlaylistFilter();refreshPlayer();
        }
        @Override public void onServiceDisconnected(ComponentName n){bound=false;service=null;stopVisualizer();}
    };
    private void refreshCharging(){
        try{Intent i=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));charging=i!=null&&i.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,0)!=0;}
        catch(Exception ignored){charging=false;}
    }
    private boolean sleepBlocked(){return charging||playing;}
    private void applyScreenPolicy(){
        if(sleepBlocked()){
            handler.removeCallbacks(idleSleep);
            dismissMusicSleep();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }else{
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            scheduleMusicSleep();
        }
    }
    private void scheduleMusicSleep(){
        handler.removeCallbacks(idleSleep);
        if(!sleepBlocked()&&sleepOverlay==null&&!isFinishing())handler.postDelayed(idleSleep,90000L);
    }
    @Override public void onUserInteraction(){
        super.onUserInteraction();
        if(sleepOverlay==null)scheduleMusicSleep();
    }
    private void showMusicSleep(){
        if(isFinishing()||sleepBlocked()||sleepOverlay!=null)return;
        FrameLayout overlay=new FrameLayout(this);
        overlay.setClickable(true);overlay.setBackgroundColor(0xFF02060C);
        LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER);
        sleepClock=text(new java.text.SimpleDateFormat("HH:mm",Locale.US).format(new Date()),150,0xFFE7F8FF,true);
        sleepClock.setGravity(Gravity.CENTER);sleepClock.setShadowLayer(dp(20),0,0,0xFF218DB8);
        TextView date=text(new java.text.SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(new Date()),22,0xFF9BB9CA,false);
        date.setGravity(Gravity.CENTER);
        TextView motto=text("REST  •  RESET  •  DRIVE SAFE",18,0xFF76DFFF,true);
        motto.setGravity(Gravity.CENTER);
        center.addView(sleepClock,new LinearLayout.LayoutParams(-1,dp(190)));
        center.addView(date);
        LinearLayout.LayoutParams mottoLp=new LinearLayout.LayoutParams(-1,dp(44));mottoLp.topMargin=dp(18);
        center.addView(motto,mottoLp);
        overlay.addView(center,new FrameLayout.LayoutParams(-1,-1));
        TextView wake=text("إلغاء وضع السكون",14,0xFFD6EAF4,true);
        wake.setGravity(Gravity.CENTER);wake.setPadding(dp(16),dp(8),dp(16),dp(8));
        wake.setBackground(card(0x443A596B,0x556CB6D0,18));
        FrameLayout.LayoutParams wakeLp=new FrameLayout.LayoutParams(-2,dp(46),Gravity.TOP|Gravity.RIGHT);
        wakeLp.setMargins(0,dp(14),dp(14),0);overlay.addView(wake,wakeLp);
        wake.setOnClickListener(v->{dismissMusicSleep();scheduleMusicSleep();});
        sleepOverlay=overlay;handler.removeCallbacks(sleepClockTick);handler.post(sleepClockTick);
        WindowManager.LayoutParams params=getWindow().getAttributes();
        if(!sleepBrightnessCaptured){sleepPreviousBrightness=params.screenBrightness;sleepBrightnessCaptured=true;}
        params.screenBrightness=.045f;getWindow().setAttributes(params);
        addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
    }
    private void dismissMusicSleep(){
        handler.removeCallbacks(sleepClockTick);
        if(sleepOverlay!=null){
            ViewGroup parent=(ViewGroup)sleepOverlay.getParent();
            if(parent!=null)parent.removeView(sleepOverlay);
            sleepOverlay=null;sleepClock=null;
        }
        if(sleepBrightnessCaptured){
            WindowManager.LayoutParams params=getWindow().getAttributes();
            params.screenBrightness=sleepPreviousBrightness;getWindow().setAttributes(params);
            sleepPreviousBrightness=-1f;sleepBrightnessCaptured=false;
        }
    }
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
        super.onCreate(b);
        playing=MusicPlaybackService.isAudioPlaying(this);
        stateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){playing=i.getBooleanExtra("playing",false);applyScreenPolicy();}};
        registerReceiver(stateReceiver,new IntentFilter(MusicPlaybackService.ACTION_STATE));
        powerReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){refreshCharging();applyScreenPolicy();}};
        IntentFilter filter=new IntentFilter();filter.addAction(Intent.ACTION_POWER_CONNECTED);filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(powerReceiver,filter);refreshCharging();
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(10),dp(16),dp(10));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF142D43,0xFF030912,0xFF071B2C}));
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView back=text("‹",34,0xFF93E9FF,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->finish());
        head.addView(back,new LinearLayout.LayoutParams(dp(48),dp(42)));
        TextView heading=text("مركز الموسيقى",24,Color.WHITE,true);heading.setGravity(Gravity.CENTER);
        head.addView(heading,new LinearLayout.LayoutParams(0,dp(42),1));head.addView(text("مكتبتك المحلية  •  ALI CAR",13,0xFF8ABAD0,false));root.addView(head);
        NavigationBannerView navigationBanner = new NavigationBannerView(this);
        LinearLayout.LayoutParams navigationBannerLp = new LinearLayout.LayoutParams(-1,dp(56));
        navigationBannerLp.topMargin = dp(5);
        root.addView(navigationBanner, navigationBannerLp);
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
        title=text("اختر أغنية من مكتبتك",20,Color.WHITE,true);title.setGravity(Gravity.CENTER);title.setMaxLines(1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-1,dp(32));titleLp.topMargin=dp(8);now.addView(title,titleLp);
        artist=text("لا يوجد مسار قيد التشغيل",12,0xFF9DB6C6,false);artist.setGravity(Gravity.CENTER);
        now.addView(artist,new LinearLayout.LayoutParams(-1,dp(22)));
        wave=new WaveView(this);LinearLayout.LayoutParams waveLp=new LinearLayout.LayoutParams(-1,dp(56));
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
        controls.addView(shuffleButton,new LinearLayout.LayoutParams(dp(54),dp(50)));
        controls.addView(control("⏮",()->{if(service!=null)service.previous();}),new LinearLayout.LayoutParams(dp(58),dp(50)));
        playButton=control("▶",()->{if(service!=null)service.toggle();});
        controls.addView(playButton,new LinearLayout.LayoutParams(dp(68),dp(58)));
        controls.addView(control("⏭",()->{if(service!=null)service.next();}),new LinearLayout.LayoutParams(dp(58),dp(50)));
        repeatButton=control("↻",()->{if(service!=null)service.setRepeat(!service.isRepeat());});
        controls.addView(repeatButton,new LinearLayout.LayoutParams(dp(54),dp(50)));now.addView(controls);

        LinearLayout library=panel();library.setOrientation(LinearLayout.VERTICAL);library.setPadding(dp(12),dp(10),dp(12),dp(8));
        body.addView(library,new LinearLayout.LayoutParams(0,-1,.92f));
        LinearLayout libraryHead=new LinearLayout(this);libraryHead.setGravity(Gravity.CENTER_VERTICAL);
        libraryHead.addView(text("المكتبة وقوائم التشغيل",17,Color.WHITE,true),new LinearLayout.LayoutParams(0,dp(34),1));
        libraryHead.addView(text("محلية • بدون إنترنت",10,0xFF8ABAD0,false));library.addView(libraryHead);

        HorizontalScrollView playlistScroll=new HorizontalScrollView(this);
        playlistScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout playlistRow=new LinearLayout(this);playlistRow.setGravity(Gravity.CENTER_VERTICAL);
        for(String playlist:PLAYLISTS){
            TextView chip=text(playlist,11,Color.WHITE,true);
            chip.setGravity(Gravity.CENTER);chip.setPadding(dp(12),dp(7),dp(12),dp(7));
            chip.setBackground(card(playlist.equals(activePlaylist)?0xFF0A7898:0x66325268,
                    0x665CD9F4,18));
            LinearLayout.LayoutParams chipLp=new LinearLayout.LayoutParams(-2,dp(36));
            chipLp.rightMargin=dp(6);playlistRow.addView(chip,chipLp);
            chip.setOnClickListener(v->{activePlaylist=playlist;applyPlaylistFilter();refreshPlaylistChipStyles(playlistRow);});
        }
        playlistScroll.addView(playlistRow);
        LinearLayout.LayoutParams filtersLp=new LinearLayout.LayoutParams(-1,dp(42));
        filtersLp.topMargin=dp(3);library.addView(playlistScroll,filtersLp);

        emptyLibrary=text("لا توجد أغنيات ضمن هذه القائمة. أضف ملفاتك إلى مكتبة الجهاز أو اضغط مطولاً على أغنية لإضافتها إلى قائمة مخصصة.",
                12,0xFFB7D0DC,false);
        emptyLibrary.setGravity(Gravity.CENTER);emptyLibrary.setVisibility(View.GONE);
        library.addView(emptyLibrary,new LinearLayout.LayoutParams(-1,dp(54)));

        list=new RecyclerView(this);list.setLayoutManager(new LinearLayoutManager(this));adapter=new SongAdapter();list.setAdapter(adapter);
        LinearLayout.LayoutParams listLp=new LinearLayout.LayoutParams(-1,0,1);listLp.topMargin=dp(4);library.addView(list,listLp);
        setContentView(root);startService(new Intent(this,MusicPlaybackService.class));
    }
    private void refreshPlaylistChipStyles(LinearLayout row){
        for(int i=0;i<row.getChildCount();i++){
            View child=row.getChildAt(i);
            if(child instanceof TextView){
                String value=((TextView)child).getText().toString();
                child.setBackground(card(value.equals(activePlaylist)?0xFF0A7898:0x66325268,
                        0x665CD9F4,18));
            }
        }
    }

    private void applyPlaylistFilter(){
        if(adapter==null)return;
        ArrayList<MusicPlaybackService.Song> filtered=new ArrayList<>();
        if(service!=null){
            List<Long> manual=service.getPlaylistSongIds(activePlaylist);
            List<Long> recent=service.getRecentSongIds();
            for(MusicPlaybackService.Song song:allSongs){
                boolean include;
                switch(activePlaylist){
                    case "كل الأغاني": include=true; break;
                    case "فيروزيات": include=artistMatches(song,"فيروز","fairuz","fairouz")||manual.contains(song.id); break;
                    case "نصيف زيتون": include=artistMatches(song,"نصيف زيتون","nassif zeytoun","nassif")||manual.contains(song.id); break;
                    case "إليسا": include=artistMatches(song,"إليسا","elissa","elissar")||manual.contains(song.id); break;
                    case "أصالة": include=artistMatches(song,"أصالة","asala","assala")||manual.contains(song.id); break;
                    case "أغاني سورية": include=artistMatches(song,"نصيف","أصالة","جورج وسوف","صباح فخري","ميادة","ليندا بيطار","nassif","asala")||manual.contains(song.id); break;
                    case "أغاني لبنانية": include=artistMatches(song,"فيروز","إليسا","نانسي","وائل كفوري","راغب علامة","ملحم زين","مروان خوري","كارول سماحة","فضل شاكر","جوليا بطرس","زياد برجي","fairuz","elissa","nancy","wael kfoury")||manual.contains(song.id); break;
                    case "المفضلة": include=service.isFavorite(song.id); break;
                    case "آخر تشغيل": include=recent.contains(song.id); break;
                    default: include=manual.contains(song.id); break;
                }
                if(include)filtered.add(song);
            }
        }
        adapter.set(filtered);
        if(emptyLibrary!=null)emptyLibrary.setVisibility(filtered.isEmpty()?View.VISIBLE:View.GONE);
    }

    private boolean artistMatches(MusicPlaybackService.Song song,String... values){
        String artist=(song.artist==null?"":song.artist).toLowerCase(Locale.ROOT);
        String titleText=(song.name==null?"":song.name).toLowerCase(Locale.ROOT);
        for(String value:values){
            String needle=value.toLowerCase(Locale.ROOT);
            if(artist.contains(needle)||titleText.contains(needle))return true;
        }
        return false;
    }

    private void addSongToPlaylist(MusicPlaybackService.Song song){
        if(service==null||song==null)return;
        new AlertDialog.Builder(this).setTitle("إضافة إلى قائمة تشغيل")
                .setItems(new String[]{"فيروزيات","نصيف زيتون","إليسا","أصالة",
                        "أغاني سورية","أغاني لبنانية","أغاني رومانسية",
                        "أغاني هادئة للقيادة","أغاني حماسية","ترند عربي 2026"},
                        (dialog,which)->{
                            String selected=new String[]{"فيروزيات","نصيف زيتون","إليسا","أصالة",
                                    "أغاني سورية","أغاني لبنانية","أغاني رومانسية",
                                    "أغاني هادئة للقيادة","أغاني حماسية","ترند عربي 2026"}[which];
                            service.addSongToPlaylist(song.id,selected);
                            activePlaylist=selected;applyPlaylistFilter();
                            Toast.makeText(this,"تمت إضافة الأغنية إلى "+selected,Toast.LENGTH_SHORT).show();
                        }).setNegativeButton("إلغاء",null).show();
    }

    private TextView control(String symbol,Runnable action){
        TextView t=text(symbol,28,Color.WHITE,true);t.setGravity(Gravity.CENTER);
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
        super.onStart();refreshCharging();playing=MusicPlaybackService.isAudioPlaying(this);applyScreenPolicy();
        if(!bound)bindService(new Intent(this,MusicPlaybackService.class),connection,BIND_AUTO_CREATE);
        handler.post(ticker);
    }
    @Override protected void onStop(){
        handler.removeCallbacks(ticker);handler.removeCallbacks(idleSleep);dismissMusicSleep();
        if(bound){if(service!=null)service.setListener(null);unbindService(connection);bound=false;service=null;}
        stopVisualizer();if(cover!=null)cover.setSpinning(false);super.onStop();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);dismissMusicSleep();
        if(stateReceiver!=null)try{unregisterReceiver(stateReceiver);}catch(Exception ignored){}
        if(powerReceiver!=null)try{unregisterReceiver(powerReceiver);}catch(Exception ignored){}
        stopVisualizer();super.onDestroy();
    }
    private final class SongAdapter extends RecyclerView.Adapter<SongRow>{
        private List<MusicPlaybackService.Song> items=new ArrayList<>();
        void set(List<MusicPlaybackService.Song> x){items=x==null?new ArrayList<>():x;notifyDataSetChanged();}
        @Override public SongRow onCreateViewHolder(ViewGroup parent,int type){
            LinearLayout row=new LinearLayout(MusicActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(10),dp(5),dp(8),dp(5));row.setBackground(card(0x66325268,0x223FD2EE,12));
            TextView label=text("",13,Color.WHITE,true);
            row.addView(label,new LinearLayout.LayoutParams(0,dp(52),1f));
            TextView favorite=text("☆",25,0xFFFFD27B,true);favorite.setGravity(Gravity.CENTER);
            row.addView(favorite,new LinearLayout.LayoutParams(dp(42),dp(48)));
            return new SongRow(row,label,favorite);
        }
        @Override public void onBindViewHolder(SongRow holder,int position){
            MusicPlaybackService.Song song=items.get(position);
            MusicPlaybackService.Song current=service==null?null:service.getCurrentSong();
            boolean active=current!=null&&current.id==song.id;
            holder.label.setText((active?"♫  ":"")+String.format(Locale.US,"%02d",position+1)
                    +"   "+song.name+"\n"+(song.artist==null?"":song.artist));
            boolean favorite=service!=null&&service.isFavorite(song.id);
            holder.favorite.setText(favorite?"★":"☆");
            holder.favorite.setOnClickListener(v->{
                if(service!=null){service.toggleFavorite(song.id);applyPlaylistFilter();}
            });
            holder.itemView.setAlpha(active?1f:.82f);
            holder.itemView.setOnClickListener(v->{if(service!=null)service.playSongId(song.id);});
            holder.itemView.setOnLongClickListener(v->{addSongToPlaylist(song);return true;});
        }
        @Override public int getItemCount(){return items.size();}
    }
    private static final class SongRow extends RecyclerView.ViewHolder{final TextView label,favorite;SongRow(View v,TextView t,TextView f){super(v);label=t;favorite=f;}}
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
        private final float[] levels=new float[36];
        private boolean unavailable;
        WaveView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setWave(byte[] data){
            if(data==null||data.length==0)return;
            for(int i=0;i<levels.length;i++){
                int from=i*data.length/levels.length,to=Math.max(from+1,(i+1)*data.length/levels.length);
                int peak=0;
                for(int j=from;j<Math.min(to,data.length);j++)peak=Math.max(peak,Math.abs((data[j]&255)-128));
                float raw=Math.max(.05f,peak/128f);
                levels[i]=Math.min(1f,Math.max(raw,levels[i]*.72f));
            }
            unavailable=false;postInvalidate();
        }
        void setUnavailable(){unavailable=true;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);
            float w=getWidth(),h=getHeight();
            if(unavailable){p.setShader(null);p.setColor(0xFF9BB8C8);p.setTextSize(dp(12));p.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("المؤشر الصوتي غير متاح على هذا الجهاز",w/2f,h/2f+dp(4),p);return;}
            float gap=dp(3),barW=(w-gap*(levels.length-1))/levels.length,cx=w/2f;
            p.setShader(new LinearGradient(0,h*.12f,0,h*.88f,
                    new int[]{0xFF76EEFF,0xFF24BBD8,0xFF2483BC},null,Shader.TileMode.CLAMP));
            p.setShadowLayer(dp(4),0,0,0x5536D9F5);
            for(int i=0;i<levels.length;i++){
                float x=i*(barW+gap),factor=.32f+.68f*levels[i];
                float barH=Math.max(dp(4),factor*h*.78f),top=(h-barH)/2f;
                canvas.drawRoundRect(x,top,x+barW,top+barH,barW/2f,barW/2f,p);
            }
            p.clearShadowLayer();p.setShader(null);
            p.setColor(0x5569B7CA);p.setStrokeWidth(dp(1));
            canvas.drawLine(0,h/2f,w,h/2f,p);
            p.setColor(0x2257AFC7);
            for(int i=0;i<5;i++){float x=cx+(i-2)*dp(18);canvas.drawCircle(x,h/2f,dp(1),p);}
        }
    }
}