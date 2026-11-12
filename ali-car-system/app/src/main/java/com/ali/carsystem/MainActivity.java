package com.ali.carsystem;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity implements LocationListener {
    private static final int CYAN=0xFF46DEFF;
    private static final int GOLD=0xFFFFD46B;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private ViewPager pager;
    private LocationManager locations;
    private WifiManager wifi;
    private AudioManager audio;
    private SpeedGaugeView mainGauge, clusterGauge;
    private TextView digitalClock,dateText,gpsText,songTitle,clusterTrip,clusterOdometer,clusterMax;
    private LinearLayout ambientOverlay;
    private View splashOverlay;
    private SeekBar volumeSeek;
    private TextView muteButton;
    private TextToSpeech tts;
    private boolean gpsRunning,receiverRegistered,isResumed,muted,screenWakePending;
    private int oldVolume=-1;
    private Location previousAcceptedLocation;
    private float filteredSpeed;
    private long lastGpsElapsed;
    private double tripKm,odometerKm,maxSpeed;
    private SharedPreferences prefs;
    private final DecimalFormat kmFormat=new DecimalFormat("0.00");

    private final Runnable clockTick=new Runnable(){@Override public void run(){
        Date now=new Date();
        if(digitalClock!=null)digitalClock.setText(new SimpleDateFormat("hh:mm",Locale.US).format(now));
        if(dateText!=null)dateText.setText(new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(now));
        if(mainGauge!=null&&lastGpsElapsed!=0&&SystemClock.elapsedRealtime()-lastGpsElapsed>7000){
            mainGauge.setSpeed(0);clusterGauge.setSpeed(0);filteredSpeed=0;
            if(gpsText!=null)gpsText.setText("إشارة الموقع غير متاحة حالياً");
        }
        handler.postDelayed(this,1000);
    }};
    private final Runnable idleTimer=this::showAmbientClock;
    private final BroadcastReceiver screenOnReceiver=new BroadcastReceiver(){
        @Override public void onReceive(Context context,Intent intent){
            if(Intent.ACTION_SCREEN_ON.equals(intent.getAction())){
                if(isResumed){dismissAmbientClock();showWelcomeSplash();scheduleIdleForMainPage();}
                else screenWakePending=true;
            }
        }
    };

    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void immersive(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);immersive();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        locations=(LocationManager)getSystemService(LOCATION_SERVICE);
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        prefs=getSharedPreferences("ali_car_trip_data",MODE_PRIVATE);
        tripKm=readDouble("trip_km",0);odometerKm=readDouble("odometer_km",0);maxSpeed=readDouble("max_speed",0);
        try{tts=new TextToSpeech(this,status->{if(status==TextToSpeech.SUCCESS){tts.setLanguage(Locale.US);}});}catch(Exception ignored){}
        buildDashboard();
        if(!receiverRegistered){registerReceiver(screenOnReceiver,new IntentFilter(Intent.ACTION_SCREEN_ON));receiverRegistered=true;}
        showWelcomeSplash();
    }

    private void buildDashboard(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);root.setClipChildren(false);
        pager=new ViewPager(this);
        pager.setId(View.generateViewId());
        pager.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        pager.setOffscreenPageLimit(1);
        pager.setAdapter(new CockpitPagerAdapter());
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener(){
            @Override public void onPageSelected(int position){
                dismissAmbientClock();
                if(position==0)scheduleIdleForMainPage();
                else handler.removeCallbacks(idleTimer);
            }
        });
        root.addView(pager,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        scheduleIdleForMainPage();
    }

    private final class CockpitPagerAdapter extends PagerAdapter{
        @Override public int getCount(){return 2;}
        @Override public boolean isViewFromObject(View view,Object object){return view==object;}
        @Override public Object instantiateItem(ViewGroup container,int position){
            View page=position==0?buildMainPage():buildClusterPage();
            container.addView(page,new ViewGroup.LayoutParams(-1,-1));return page;
        }
        @Override public void destroyItem(ViewGroup container,int position,Object object){container.removeView((View)object);}
    }

    private View buildMainPage(){
        FrameLayout page=new FrameLayout(this);page.setBackgroundColor(Color.BLACK);
        ImageView background=new ImageView(this);background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        int resource=getResources().getIdentifier("bg_car_dashboard","drawable",getPackageName());
        Bitmap wallpaper=null;
        if(resource!=0){
            try(InputStream input=getResources().openRawResource(resource)){
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inPreferredConfig=Bitmap.Config.RGB_565;options.inDither=true;options.inScaled=false;
                wallpaper=BitmapFactory.decodeStream(input,null,options);
            }catch(Exception ignored){wallpaper=null;}
        }
        // The bundled clean copy is the same supplied car/highway photograph, retained as a safe fallback.
        if(wallpaper==null){
            try(InputStream input=getAssets().open("dashboard.webp")){
                BitmapFactory.Options options=new BitmapFactory.Options();
                options.inPreferredConfig=Bitmap.Config.RGB_565;options.inDither=true;
                wallpaper=BitmapFactory.decodeStream(input,null,options);
            }catch(Exception ignored){wallpaper=null;}
        }
        if(wallpaper!=null)background.setImageBitmap(wallpaper);
        page.addView(background,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(22),dp(4),dp(20),0);
        TextView brand=label("ALI CAR",27,Color.WHITE,true);brand.setLetterSpacing(.16f);brand.setShadowLayer(dp(10),0,0,CYAN);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(46),1f));
        TextView settings=label("⚙",25,0xFFE9F8FF,true);settings.setGravity(Gravity.CENTER);
        settings.setOnClickListener(v->openDeviceSettings(settings));
        header.addView(settings,new LinearLayout.LayoutParams(dp(48),dp(48)));
        page.addView(header,new FrameLayout.LayoutParams(-1,dp(54),Gravity.TOP));

        LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);content.setPadding(dp(20),0,dp(18),0);
        LinearLayout left=new LinearLayout(this);left.setOrientation(LinearLayout.VERTICAL);left.setGravity(Gravity.CENTER);
        mainGauge=new SpeedGaugeView(this);left.addView(mainGauge,new LinearLayout.LayoutParams(dp(196),dp(196)));
        gpsText=label("بانتظار إشارة الموقع",10,0xFFE3F4FF,false);gpsText.setGravity(Gravity.CENTER);
        left.addView(gpsText,new LinearLayout.LayoutParams(-1,dp(20)));
        songTitle=label("لا توجد أغنية قيد التشغيل",11,Color.WHITE,true);songTitle.setGravity(Gravity.CENTER);
        songTitle.setSingleLine(true);songTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        left.addView(songTitle,new LinearLayout.LayoutParams(-1,dp(24)));
        LinearLayout media=new LinearLayout(this);media.setGravity(Gravity.CENTER);
        media.addView(mediaKey("⏮","previous"),new LinearLayout.LayoutParams(dp(44),dp(40)));
        TextView play=mediaKey("▶","play");LinearLayout.LayoutParams playLp=new LinearLayout.LayoutParams(dp(48),dp(44));
        playLp.leftMargin=dp(7);playLp.rightMargin=dp(7);media.addView(play,playLp);
        media.addView(mediaKey("⏭","next"),new LinearLayout.LayoutParams(dp(44),dp(40)));
        left.addView(media,new LinearLayout.LayoutParams(-1,dp(48)));
        LinearLayout sound=new LinearLayout(this);sound.setGravity(Gravity.CENTER_VERTICAL);
        muteButton=label("◖",22,0xFFFFD36B,true);muteButton.setGravity(Gravity.CENTER);
        muteButton.setOnClickListener(v->toggleMute());
        sound.addView(muteButton,new LinearLayout.LayoutParams(dp(35),dp(36)));
        volumeSeek=new SeekBar(this);volumeSeek.setMax(audio==null?15:audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volumeSeek.setProgress(audio==null?8:audio.getStreamVolume(AudioManager.STREAM_MUSIC));
        if(Build.VERSION.SDK_INT>=21){volumeSeek.setProgressTintList(ColorStateList.valueOf(CYAN));volumeSeek.setThumbTintList(ColorStateList.valueOf(GOLD));}
        sound.addView(volumeSeek,new LinearLayout.LayoutParams(0,dp(36),1f));
        volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar seek,int value,boolean fromUser){
                if(fromUser&&audio!=null){audio.setStreamVolume(AudioManager.STREAM_MUSIC,value,0);if(value>0)muted=false;updateMuteIcon();}
            }
            @Override public void onStartTrackingTouch(SeekBar seek){}
            @Override public void onStopTrackingTouch(SeekBar seek){}
        });
        left.addView(sound,new LinearLayout.LayoutParams(-1,dp(38)));

        View centerSpace=new View(this);
        LinearLayout right=new LinearLayout(this);right.setOrientation(LinearLayout.VERTICAL);right.setGravity(Gravity.CENTER);
        digitalClock=label("--:--",48,Color.WHITE,true);digitalClock.setGravity(Gravity.CENTER);
        digitalClock.setShadowLayer(dp(12),0,0,0xFF9BDFFF);
        right.addView(digitalClock,new LinearLayout.LayoutParams(-1,dp(60)));
        right.addView(new AnalogClock(this),new LinearLayout.LayoutParams(dp(145),dp(145)));
        dateText=label("",13,Color.WHITE,true);dateText.setGravity(Gravity.CENTER);
        right.addView(dateText,new LinearLayout.LayoutParams(-1,dp(26)));
        content.addView(left,new LinearLayout.LayoutParams(0,-2,0.40f));
        content.addView(centerSpace,new LinearLayout.LayoutParams(0,1,0.20f));
        content.addView(right,new LinearLayout.LayoutParams(0,-2,0.40f));
        FrameLayout.LayoutParams contentLp=new FrameLayout.LayoutParams(-1,-1);
        contentLp.topMargin=dp(52);contentLp.bottomMargin=dp(118);page.addView(content,contentLp);

        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER);dock.setOrientation(LinearLayout.HORIZONTAL);dock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        dock.setPadding(dp(16),dp(4),dp(16),0);
        dock.addView(dockItem(R.drawable.ic_car_youtube,"يوتيوب",0xFFFF536C,()->openActivity(YouTubeSearchActivity.class)),dockParams());
        dock.addView(dockItem(R.drawable.ic_car_navigation,"الملاحة",0xFF4DE5B2,()->openActivity(NavigationActivity.class)),dockParams());
        dock.addView(dockItem(R.drawable.ic_car_music,"الموسيقى",0xFFE48CFF,()->openActivity(MusicActivity.class)),dockParams());
        dock.addView(dockItem(R.drawable.ic_car_bluetooth,"بلوتوث",0xFF65BFFF,this::toggleBluetooth),dockParams());
        dock.addView(dockItem(R.drawable.ic_car_wifi,"واي فاي",0xFF6DF5E2,this::toggleWifi),dockParams());
        dock.addView(dockItem(R.drawable.ic_car_brightness,"السطوع",0xFFFFD36B,this::showBrightnessPill),dockParams());
        FrameLayout.LayoutParams dockLp=new FrameLayout.LayoutParams(-1,dp(82),Gravity.BOTTOM);
        dockLp.bottomMargin=dp(22);page.addView(dock,dockLp);
        TextView credit=label("تم تطوير الواجهة بواسطة : ali ahmad",10,0xFFF4EBD9,true);credit.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams creditLp=new FrameLayout.LayoutParams(-1,dp(19),Gravity.BOTTOM);
        page.addView(credit,creditLp);

        TextView swipeHint=label("اسحب لليمين لعرض العدادات  ›",10,0xFFE5F6FF,false);
        swipeHint.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hintLp=new FrameLayout.LayoutParams(-1,dp(20),Gravity.BOTTOM);
        hintLp.bottomMargin=dp(104);page.addView(swipeHint,hintLp);
        return page;
    }

    private View buildClusterPage(){
        FrameLayout page=new FrameLayout(this);
        page.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF081421,0xFF02070D,0xFF111E2C}));
        LinearLayout column=new LinearLayout(this);column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(dp(16),dp(12),dp(16),dp(12));column.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView title=label("ALI CAR  •  PERFORMANCE",19,Color.WHITE,true);title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(.12f);column.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
        clusterGauge=new SpeedGaugeView(this);
        column.addView(clusterGauge,new LinearLayout.LayoutParams(dp(250),dp(250)));
        LinearLayout stats=new LinearLayout(this);stats.setGravity(Gravity.CENTER);stats.setOrientation(LinearLayout.HORIZONTAL);
        clusterTrip=statCard("TRIP KM",kmFormat.format(tripKm));
        clusterOdometer=statCard("ODOMETER",kmFormat.format(odometerKm));
        clusterMax=statCard("MAX SPEED",String.format(Locale.US,"%.0f km/h",maxSpeed));
        stats.addView(clusterTrip,new LinearLayout.LayoutParams(0,dp(72),1f));
        stats.addView(clusterOdometer,new LinearLayout.LayoutParams(0,dp(72),1f));
        stats.addView(clusterMax,new LinearLayout.LayoutParams(0,dp(72),1f));
        column.addView(stats,new LinearLayout.LayoutParams(-1,dp(78)));
        Button reset=new Button(this);reset.setText("تصفير عداد الرحلة");
        reset.setOnClickListener(v->{tripKm=0;saveDouble("trip_km",0);refreshCluster();});
        column.addView(reset,new LinearLayout.LayoutParams(dp(220),dp(46)));
        TextView back=label("اسحب لليسار للعودة إلى لوحة القيادة",11,0xFFB5D3E3,false);back.setGravity(Gravity.CENTER);
        column.addView(back,new LinearLayout.LayoutParams(-1,dp(28)));
        page.addView(column,new FrameLayout.LayoutParams(-1,-1));
        page.setKeepScreenOn(true);
        return page;
    }

    private TextView statCard(String title,String value){
        TextView card=label(title+"\n"+value,12,Color.WHITE,true);card.setGravity(Gravity.CENTER);
        card.setBackground(glassBackground(0xAA132A3C,0x6659DFFF,dp(12)));card.setElevation(dp(3));
        return card;
    }
    private GradientDrawable glassBackground(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{color,0xCC102033,color});
        d.setCornerRadius(radius);d.setStroke(dp(1),stroke);return d;
    }
    private LinearLayout.LayoutParams dockParams(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f);p.leftMargin=dp(2);p.rightMargin=dp(2);return p;
    }
    private LinearLayout dockItem(int iconResource,String caption,int color,Runnable action){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
        ImageView glyph=new ImageView(this);glyph.setImageResource(iconResource);
        glyph.setColorFilter(color,android.graphics.PorterDuff.Mode.SRC_IN);
        glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);glyph.setPadding(dp(8),dp(4),dp(8),dp(2));
        glyph.setElevation(dp(5));
        TextView name=label(caption,11,Color.WHITE,true);name.setGravity(Gravity.CENTER);
        name.setShadowLayer(dp(4),0,dp(1),Color.BLACK);name.setBackgroundColor(Color.TRANSPARENT);
        item.addView(glyph,new LinearLayout.LayoutParams(-1,dp(52)));
        item.addView(name,new LinearLayout.LayoutParams(-1,dp(22)));
        item.setBackgroundColor(Color.TRANSPARENT);item.setOnClickListener(v->action.run());item.setClickable(true);
        item.setFocusable(true);
        return item;
    }
    private TextView mediaKey(String glyph,String action){
        TextView button=label(glyph,23,0xFFF4FBFF,true);button.setGravity(Gravity.CENTER);
        button.setBackgroundColor(Color.TRANSPARENT);button.setShadowLayer(dp(5),0,dp(2),CYAN);
        button.setOnClickListener(v->{Intent i=new Intent(this,MusicActivity.class);i.putExtra("ali.music.action",action);startActivity(i);});
        return button;
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;
    }

    private void toggleWifi(){
        try{boolean ok=wifi!=null&&wifi.setWifiEnabled(!wifi.isWifiEnabled());toast(ok?"تم إرسال أمر تغيير الواي فاي":"تعذر تغيير حالة الواي فاي");}
        catch(Exception e){toast("لا يسمح النظام بتغيير حالة الواي فاي");}
    }
    private void toggleBluetooth(){
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();
        if(adapter==null){toast("البلوتوث غير متاح");return;}
        try{boolean ok=adapter.isEnabled()?adapter.disable():adapter.enable();toast(ok?"تم إرسال أمر تغيير البلوتوث":"تعذر تغيير حالة البلوتوث");}
        catch(Exception e){toast("لا يسمح النظام بتغيير حالة البلوتوث");}
    }
    private void toggleMute(){
        if(audio==null)return;
        if(!muted){oldVolume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);muted=true;}
        else{audio.setStreamVolume(AudioManager.STREAM_MUSIC,oldVolume<0?Math.max(1,audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)/2):oldVolume,0);muted=false;}
        if(volumeSeek!=null)volumeSeek.setProgress(audio.getStreamVolume(AudioManager.STREAM_MUSIC));updateMuteIcon();
    }
    private void updateMuteIcon(){if(muteButton!=null)muteButton.setText(muted?"×":"◖");}
    private void openDeviceSettings(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add("إعدادات الجهاز").setOnMenuItemClickListener(item->{try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){toast("تعذر فتح الإعدادات");}return true;});
        menu.getMenu().add("إدارة الجهاز ووضع القيادة").setOnMenuItemClickListener(item->{openActivity(AdminToolsActivity.class);return true;});
        menu.show();
    }
    private void openActivity(Class<?> target){try{startActivity(new Intent(this,target));}catch(Exception e){toast("تعذر فتح الشاشة المطلوبة");}}
    private void toast(String message){Toast.makeText(this,message,Toast.LENGTH_SHORT).show();}

    private void showBrightnessPill(){
        if(root==null)return;
        View old=root.findViewWithTag("ali-brightness-pill");if(old!=null)root.removeView(old);
        LinearLayout pill=new LinearLayout(this);pill.setTag("ali-brightness-pill");
        pill.setOrientation(LinearLayout.HORIZONTAL);pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(18),dp(8),dp(18),dp(8));
        pill.setBackground(glassBackground(0xE91A3550,0xAA73DFFF,dp(30)));pill.setElevation(dp(12));
        TextView sun=label("☼",29,GOLD,true);sun.setGravity(Gravity.CENTER);
        pill.addView(sun,new LinearLayout.LayoutParams(dp(44),dp(44)));
        SeekBar bar=new SeekBar(this);bar.setMax(255);bar.setProgress(readBrightness());
        if(Build.VERSION.SDK_INT>=21){bar.setProgressTintList(ColorStateList.valueOf(CYAN));bar.setThumbTintList(ColorStateList.valueOf(GOLD));}
        pill.addView(bar,new LinearLayout.LayoutParams(dp(250),dp(44)));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar s,int p,boolean from){if(from){setBrightness(p);resetBrightnessPillTimeout(pill);}}
            @Override public void onStartTrackingTouch(SeekBar s){handler.removeCallbacksAndMessages(pill);}
            @Override public void onStopTrackingTouch(SeekBar s){resetBrightnessPillTimeout(pill);}
        });
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,dp(64),Gravity.CENTER);
        root.addView(pill,lp);resetBrightnessPillTimeout(pill);
    }
    private void resetBrightnessPillTimeout(View pill){
        handler.removeCallbacksAndMessages(pill);
        handler.postAtTime(()->{if(root!=null)root.removeView(pill);},pill,SystemClock.uptimeMillis()+3000);
    }
    private int readBrightness(){try{return Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,150);}catch(Exception e){return 150;}}
    private void setBrightness(int value){
        int safe=Math.max(10,Math.min(255,value));
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=safe/255f;getWindow().setAttributes(p);
        try{Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,safe);}catch(SecurityException ignored){}
    }

    private void scheduleIdleForMainPage(){
        handler.removeCallbacks(idleTimer);
        if(pager!=null&&pager.getCurrentItem()==0&&!isAmbientShowing()&&!isFinishing())handler.postDelayed(idleTimer,90000);
    }
    private boolean isAmbientShowing(){return ambientOverlay!=null;}
    @Override public void onUserInteraction(){super.onUserInteraction();if(pager!=null&&pager.getCurrentItem()==0){dismissAmbientClock();scheduleIdleForMainPage();}}
    private void showAmbientClock(){
        if(root==null||pager==null||pager.getCurrentItem()!=0||ambientOverlay!=null||!isResumed||splashOverlay!=null)return;
        ambientOverlay=new LinearLayout(this);ambientOverlay.setGravity(Gravity.CENTER);ambientOverlay.setOrientation(LinearLayout.VERTICAL);
        ambientOverlay.setBackgroundColor(Color.BLACK);ambientOverlay.setPadding(dp(16),dp(16),dp(16),dp(16));
        TextView clock=label(new SimpleDateFormat("hh:mm",Locale.US).format(new Date()),120,0xFFBDEEFF,true);
        clock.setGravity(Gravity.CENTER);clock.setShadowLayer(dp(22),0,0,0xFF39CFFF);
        TextView date=label(new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(new Date()),18,0xFF9AAFC2,true);
        date.setGravity(Gravity.CENTER);date.setPadding(0,dp(12),0,dp(8));
        TextView caption=label("ALI CAR  •  قيادة آمنة وممتعة",15,0xFF79DFFF,true);caption.setGravity(Gravity.CENTER);
        ambientOverlay.addView(clock,new LinearLayout.LayoutParams(-1,dp(165)));
        ambientOverlay.addView(date,new LinearLayout.LayoutParams(-1,dp(42)));
        ambientOverlay.addView(caption,new LinearLayout.LayoutParams(-1,dp(34)));
        root.addView(ambientOverlay,new FrameLayout.LayoutParams(-1,-1));
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=.035f;getWindow().setAttributes(p);
        ambientOverlay.setOnClickListener(v->{dismissAmbientClock();scheduleIdleForMainPage();});
    }
    private void dismissAmbientClock(){
        if(ambientOverlay==null)return;
        if(root!=null)root.removeView(ambientOverlay);ambientOverlay=null;
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=-1f;getWindow().setAttributes(p);
    }

    private void showWelcomeSplash(){
        if(root==null||splashOverlay!=null)return;
        dismissAmbientClock();handler.removeCallbacks(idleTimer);
        SplashNeonView splash=new SplashNeonView(this);splashOverlay=splash;
        root.addView(splash,new FrameLayout.LayoutParams(-1,-1));splash.bringToFront();
        if(tts!=null){try{tts.setLanguage(Locale.US);tts.speak("Welcome Ali",TextToSpeech.QUEUE_FLUSH,null);}catch(Exception ignored){}}
        splash.animate().alpha(1f).setDuration(160).start();
        handler.postDelayed(()->{
            if(root!=null&&splashOverlay==splash){root.removeView(splash);splashOverlay=null;scheduleIdleForMainPage();}
        },4000);
    }
    private final class SplashNeonView extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Handler frames=new Handler(Looper.getMainLooper());
        private long start=SystemClock.uptimeMillis();
        private final Path textPath=new Path(),visiblePath=new Path();
        private final PathMeasure measure;
        private float fraction;
        private final Runnable frame= new Runnable(){@Override public void run(){
            fraction=Math.min(1f,(SystemClock.uptimeMillis()-start)/4000f);invalidate();
            if(fraction<1f)frames.postDelayed(this,16);
        }};
        SplashNeonView(Context c){
            super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);
            paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            Paint textPaint=new Paint(Paint.ANTI_ALIAS_FLAG);textPaint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
            textPaint.setTextSize(dp(48));String title="Ali Ahmed";
            float width=textPaint.measureText(title);textPaint.getTextPath(title,0,title.length(),-width/2f,0,textPath);
            measure=new PathMeasure(textPath,false);frames.post(frame);
            setAlpha(0f);
        }
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f;
            paint.setStyle(Paint.Style.FILL);paint.setShader(new RadialGradient(cx,cy,Math.max(getWidth(),getHeight())*.65f,
                    new int[]{0xFF132B42,0xFF050A13,Color.BLACK},null,Shader.TileMode.CLAMP));
            canvas.drawRect(0,0,getWidth(),getHeight(),paint);paint.setShader(null);
            for(int i=0;i<12;i++){
                float x=(i*dp(113)+(fraction*dp(2600)))%(getWidth()+dp(80))-dp(40);
                float y=(i*dp(71))%Math.max(dp(1),getHeight());
                paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1)+(i%3));
                paint.setColor(i%2==0?0x5546DEFF:0x55FFFFFF);
                canvas.drawLine(x,y,x+dp(90),y-dp(35),paint);
            }
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(3));paint.setColor(0xAA39DFFF);
            paint.setShadowLayer(dp(18),0,0,CYAN);
            RectF ring=new RectF(cx-dp(185),cy-dp(62),cx+dp(185),cy+dp(62));
            canvas.drawArc(ring,190,160*fraction,false,paint);
            paint.clearShadowLayer();
            visiblePath.reset();float length=measure.getLength();
            measure.getSegment(0,length*Math.max(0f,Math.min(1f,(fraction-.16f)/.58f)),visiblePath,true);
            canvas.save();canvas.translate(cx,cy);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2.6f));
            paint.setShader(new LinearGradient(-dp(180),0,dp(180),0, new int[]{0xFF77E9FF,0xFFFFFFFF,0xFF5BCBFF,0xFFDAF7FF},null,Shader.TileMode.CLAMP));
            paint.setShadowLayer(dp(16),0,0,0xFF2FCFFF);
            canvas.drawPath(visiblePath,paint);paint.clearShadowLayer();paint.setShader(null);
            if(fraction>.76f){
                paint.setStyle(Paint.Style.FILL);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));
                paint.setTextSize(dp(48));paint.setTextAlign(Paint.Align.CENTER);
                paint.setShader(new LinearGradient(0,-dp(25),0,dp(25),0xFFFFFFFF,0xFF69DFFF,Shader.TileMode.CLAMP));
                paint.setShadowLayer(dp(12),0,0,CYAN);
                paint.setAlpha((int)(255*Math.min(1f,(fraction-.76f)/.16f)));
                canvas.drawText("Ali Ahmed",0,dp(17),paint);paint.setShader(null);paint.clearShadowLayer();
                paint.setAlpha(255);paint.setTextSize(dp(13));paint.setColor(0xFFDAF8FF);
                
                canvas.drawText("ALI CAR  •  SMART COCKPIT",0,dp(57),paint);
            }
            canvas.restore();
        }
    }

    private void startGps(){
        if(locations==null)return;
        if(checkSelfPermissionCompat(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                &&checkSelfPermissionCompat(android.Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED){
            if(gpsText!=null)gpsText.setText("اسمح للتطبيق بالوصول إلى الموقع");
            return;
        }
        if(gpsRunning)return;
        boolean registered=false;
        try{
            if(locations.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,1f,this);
                registered=true;
            }
        }catch(SecurityException|IllegalArgumentException error){
            if(gpsText!=null)gpsText.setText("تعذر تشغيل GPS — تحقق من الإذن");
        }
        try{
            if(locations.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                locations.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1800L,4f,this);
                registered=true;
            }
        }catch(SecurityException|IllegalArgumentException ignored){}
        gpsRunning=registered;
        if(!registered){
            if(gpsText!=null)gpsText.setText("فعّل GPS وخدمات الموقع ثم أعد المحاولة");
            return;
        }
        Location latest=bestLastKnownLocation();
        if(latest!=null)onLocationChanged(latest);
    }
    private int checkSelfPermissionCompat(String permission){
        if(Build.VERSION.SDK_INT>=23)return checkSelfPermission(permission);
        return checkCallingOrSelfPermission(permission);
    }
    private Location bestLastKnownLocation(){
        if(locations==null)return null;Location best=null;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            try{Location candidate=locations.getLastKnownLocation(provider);if(candidate!=null&&(best==null||candidate.getTime()>best.getTime()))best=candidate;}
            catch(SecurityException|IllegalArgumentException ignored){}
        }return best;
    }
    private void stopGps(){
        if(locations!=null){try{locations.removeUpdates(this);}catch(Exception ignored){}}
        gpsRunning=false;previousAcceptedLocation=null;lastGpsElapsed=0;
    }
    @Override public void onLocationChanged(Location location){
        if(location==null||mainGauge==null)return;
        if(location.hasAccuracy()&&location.getAccuracy()>100f){
            if(gpsText!=null)gpsText.setText(String.format(new Locale("ar"),"إشارة GPS ضعيفة ±%.0f m",location.getAccuracy()));
            return;
        }
        long now=SystemClock.elapsedRealtime();
        float raw=location.hasSpeed()?location.getSpeed()*3.6f:0f;
        if((!location.hasSpeed()||raw<.5f)&&previousAcceptedLocation!=null){
            float seconds=Math.max(.1f,(location.getTime()-previousAcceptedLocation.getTime())/1000f);
            raw=previousAcceptedLocation.distanceTo(location)/seconds*3.6f;
        }
        if(Float.isNaN(raw)||Float.isInfinite(raw)||raw<0||raw>260)return;
        if(filteredSpeed>20&&Math.abs(raw-filteredSpeed)>90)return;
        filteredSpeed=filteredSpeed==0?raw:filteredSpeed*.70f+raw*.30f;
        if(previousAcceptedLocation!=null){
            float meters=previousAcceptedLocation.distanceTo(location);
            long delta=location.getTime()-previousAcceptedLocation.getTime();
            if(delta>0&&delta<60000&&meters>2.5f&&meters<1200f&&(!location.hasAccuracy()||location.getAccuracy()<35f)){
                double km=meters/1000d;tripKm+=km;odometerKm+=km;
                saveDouble("trip_km",tripKm);saveDouble("odometer_km",odometerKm);
            }
        }
        previousAcceptedLocation=new Location(location);
        lastGpsElapsed=now;maxSpeed=Math.max(maxSpeed,filteredSpeed);saveDouble("max_speed",maxSpeed);
        mainGauge.setSpeed(filteredSpeed);if(clusterGauge!=null)clusterGauge.setSpeed(filteredSpeed);
        if(gpsText!=null)gpsText.setText(location.hasAccuracy()?String.format(new Locale("ar"),"GPS ±%.0f m",location.getAccuracy()):"GPS نشط");
        refreshCluster();
    }
    private void refreshCluster(){
        if(clusterTrip!=null)clusterTrip.setText("TRIP KM\n"+kmFormat.format(tripKm));
        if(clusterOdometer!=null)clusterOdometer.setText("ODOMETER\n"+kmFormat.format(odometerKm));
        if(clusterMax!=null)clusterMax.setText("MAX SPEED\n"+String.format(Locale.US,"%.0f km/h",maxSpeed));
    }
    private void saveDouble(String key,double value){prefs.edit().putLong(key,Double.doubleToRawLongBits(value)).apply();}
    private double readDouble(String key,double fallback){return Double.longBitsToDouble(prefs.getLong(key,Double.doubleToRawLongBits(fallback)));}
    @Override public void onProviderDisabled(String provider){if(gpsText!=null)gpsText.setText("مزود الموقع متوقف");}
    @Override public void onProviderEnabled(String provider){
        if(gpsText!=null)gpsText.setText("تم تفعيل الموقع — جارٍ إعادة تشغيل GPS");
        gpsRunning=false;
        handler.post(()->{stopGps();startGps();});
    }
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    private void refreshSongTitle(){
        if(songTitle==null||prefs==null)return;
        String savedTitle=prefs.getString("last_song_title","");
        songTitle.setText(savedTitle==null||savedTitle.trim().isEmpty()
                ?"لا توجد أغنية قيد التشغيل":savedTitle);
    }
    @Override protected void onResume(){
        super.onResume();isResumed=true;immersive();startGps();
        refreshSongTitle();
        handler.removeCallbacks(clockTick);handler.post(clockTick);
        if(screenWakePending){screenWakePending=false;showWelcomeSplash();}
        scheduleIdleForMainPage();
    }
    @Override protected void onPause(){
        isResumed=false;handler.removeCallbacks(idleTimer);handler.removeCallbacks(clockTick);
        if(splashOverlay!=null&&root!=null){root.removeView(splashOverlay);splashOverlay=null;}
        dismissAmbientClock();stopGps();super.onPause();
    }
    @Override protected void onDestroy(){
        if(receiverRegistered){try{unregisterReceiver(screenOnReceiver);}catch(Exception ignored){}receiverRegistered=false;}
        handler.removeCallbacksAndMessages(null);stopGps();
        if(tts!=null){try{tts.stop();tts.shutdown();}catch(Exception ignored){}}
        super.onDestroy();
    }

    private final class SpeedGaugeView extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float value,displayValue;
        private ValueAnimator animator;
        SpeedGaugeView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setSpeed(float speed){
            value=Math.max(0,Math.min(220,speed));
            if(animator!=null)animator.cancel();
            animator=ValueAnimator.ofFloat(displayValue,value);animator.setDuration(360);
            animator.setInterpolator(new DecelerateInterpolator());
            animator.addUpdateListener(a->{displayValue=(Float)a.getAnimatedValue();invalidate();});animator.start();
        }
        @Override protected void onDraw(Canvas c){
            super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f;
            float radius=Math.min(getWidth(),getHeight())*.46f,stroke=Math.max(dp(5),radius*.045f);
            p.setStyle(Paint.Style.FILL);p.setShader(new RadialGradient(cx,cy,radius*1.2f,
                    new int[]{0x66113C58,0x3325A5D5,0x00000000},null,Shader.TileMode.CLAMP));
            c.drawCircle(cx,cy,radius*1.2f,p);p.setShader(null);
            RectF arc=new RectF(cx-radius,cy-radius,cx+radius,cy+radius);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(stroke);p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0x553F6075);c.drawArc(arc,135,270,false,p);
            p.setShader(new SweepGradient(cx,cy,new int[]{0xFF23C9F4,0xFF58F3D0,0xFFFFD56A,0xFFFF625F,0xFF23C9F4},null));
            c.drawArc(arc,135,270*(displayValue/220f),false,p);p.setShader(null);
            for(int i=0;i<=22;i++){
                float angle=(float)Math.toRadians(135+i*(270f/22f));
                float outer=radius-stroke*.75f,inner=outer-(i%2==0?dp(11):dp(6));
                p.setStrokeWidth(i%2==0?dp(2):dp(1));p.setColor(i%2==0?0xFFE4F6FF:0xFF7897AC);
                float x1=cx+(float)Math.cos(angle)*inner,y1=cy+(float)Math.sin(angle)*inner;
                float x2=cx+(float)Math.cos(angle)*outer,y2=cy+(float)Math.sin(angle)*outer;
                c.drawLine(x1,y1,x2,y2,p);
                if(i%2==0){
                    p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
                    p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));p.setTextSize(radius*.105f);
                    p.setColor(0xFFECF8FF);
                    float tx=cx+(float)Math.cos(angle)*(radius*.72f),ty=cy+(float)Math.sin(angle)*(radius*.72f)+p.getTextSize()*.35f;
                    c.drawText(String.valueOf(i*10),tx,ty,p);p.setStyle(Paint.Style.STROKE);
                }
            }
            p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));p.setTextSize(radius*.43f);
            p.setColor(Color.WHITE);p.setShadowLayer(dp(12),0,0,CYAN);
            c.drawText(String.format(Locale.US,"%.0f",displayValue),cx,cy+radius*.14f,p);p.clearShadowLayer();
            p.setTextSize(radius*.105f);p.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            p.setColor(0xFFACDDF0);c.drawText("km/h",cx,cy+radius*.39f,p);
            p.setColor(0xFF61DFFF);c.drawCircle(cx,cy-radius*.57f,dp(3),p);
        }
    }

    private final class AnalogClock extends View{
        private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        AnalogClock(Context c){super(c);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.47f;
            p.setStyle(Paint.Style.FILL);p.setColor(0xCCEEF5FA);c.drawCircle(cx,cy,r,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(2));p.setColor(0xFF6BCFEF);c.drawCircle(cx,cy,r,p);
            for(int i=0;i<60;i++){
                double a=Math.toRadians(i*6-90);float o=r-dp(5),in=o-(i%5==0?dp(8):dp(3));
                p.setStrokeWidth(i%5==0?dp(2):dp(1));p.setColor(i%5==0?0xFF1A2A37:0xFF8094A4);
                c.drawLine(cx+(float)Math.cos(a)*in,cy+(float)Math.sin(a)*in,cx+(float)Math.cos(a)*o,cy+(float)Math.sin(a)*o,p);
            }
            Calendar now=Calendar.getInstance();float sec=now.get(Calendar.SECOND),min=now.get(Calendar.MINUTE)+sec/60f,hr=now.get(Calendar.HOUR)%12+min/60f;
            hand(c,cx,cy,hr*30,r*.47f,dp(4),0xFF15232E);hand(c,cx,cy,min*6,r*.68f,dp(2),0xFF15232E);
            hand(c,cx,cy,sec*6,r*.78f,dp(1),0xFFD33E4F);p.setStyle(Paint.Style.FILL);p.setColor(0xFF132839);c.drawCircle(cx,cy,dp(4),p);
        }
        private void hand(Canvas c,float x,float y,float degrees,float length,float width,int color){
            double a=Math.toRadians(degrees-90);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(width);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);
            c.drawLine(x,y,x+(float)Math.cos(a)*length,y+(float)Math.sin(a)*length,p);
        }
    }
}
