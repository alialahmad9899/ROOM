package com.ali.carsystem;

import android.animation.ObjectAnimator;
import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.media.AudioManager;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import java.util.*;
import java.text.SimpleDateFormat;

public final class MainActivity extends BaseActivity implements LocationListener, TextToSpeech.OnInitListener {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ViewPager pager;
    private SpeedometerView speedometer, clusterGauge;
    private TextView gpsStatus, songTitle, digitalClock, dateLabel, ambientTime;
    private LocationManager locationManager;
    private Location previousFix;
    private boolean gpsStarted, dashboardCriteriaRegistered, splashPending=true, ttsReady, pendingSpeak;
    private TextToSpeech tts;
    private double tripKm, odometerKm;
    private float maxSpeed;
    private long lastTripTimestamp;
    private final Runnable clockTick=new Runnable(){@Override public void run(){updateClock();handler.postDelayed(this,1000);}};
    private final Runnable idleTick=()->showAmbientClock();
    private View ambientOverlay,welcomeOverlay;
    private float previousWindowBrightness=-1f;
    private boolean previousBrightnessCaptured;
    private BroadcastReceiver screenReceiver,musicReceiver,powerReceiver,trackingReceiver,navigationReceiver;
    private boolean charging, musicPlaying;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        tripKm=prefs().contains("trip_meters")?prefs().getLong("trip_meters",0L)/1000d:prefs().getFloat("trip_km",0f);
        odometerKm=prefs().contains("odometer_meters")?prefs().getLong("odometer_meters",0L)/1000d:prefs().getFloat("odometer_km",0f);
        maxSpeed=prefs().getFloat("max_speed",0f);
        tts=new TextToSpeech(this,this);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        pager=new ViewPager(this);pager.setId(View.generateViewId());
        pager.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        pager.setAdapter(new DashboardPager());
        pager.setPageTransformer(true,(page,position)->{page.setAlpha(Math.max(.4f,1-Math.abs(position)*.35f));page.setScaleY(1-Math.min(.06f,Math.abs(position)*.06f));});
        setContentView(pager);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener(){
            @Override public void onPageSelected(int position){
                applySleepPolicy();
                updateStats();
            }
        });
        screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_ON.equals(i.getAction()))handler.postDelayed(()->showWelcome(),350);}};
        registerReceiver(screenReceiver,new IntentFilter(Intent.ACTION_SCREEN_ON));
        musicPlaying=MusicPlaybackService.isAudioPlaying(this);
        musicReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(songTitle!=null){String name=i.getStringExtra("title");songTitle.setText(name==null||name.isEmpty()?"لا توجد أغنية قيد التشغيل":name);}
            musicPlaying=i.getBooleanExtra("playing",false);applySleepPolicy();
        }};
        registerReceiver(musicReceiver,new IntentFilter(MusicPlaybackService.ACTION_STATE));
        trackingReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(!LocationTrackingService.ACTION_UPDATE.equals(i.getAction()))return;
            lastDisplayedSpeed=i.getFloatExtra(LocationTrackingService.EXTRA_SPEED_KMH,lastDisplayedSpeed);
            tripKm=i.getLongExtra(LocationTrackingService.EXTRA_TRIP_METERS,Math.round(tripKm*1000d))/1000d;
            odometerKm=i.getLongExtra(LocationTrackingService.EXTRA_ODOMETER_METERS,Math.round(odometerKm*1000d))/1000d;
            maxSpeed=i.getFloatExtra(LocationTrackingService.EXTRA_MAX_SPEED_KMH,maxSpeed);
            if(speedometer!=null)speedometer.setSpeed(lastDisplayedSpeed);
            if(clusterGauge!=null)clusterGauge.setSpeed(lastDisplayedSpeed);
            float accuracy=i.getFloatExtra(LocationTrackingService.EXTRA_ACCURACY_METERS,-1f);
            String message=i.getStringExtra("status");
            if(gpsStatus!=null)gpsStatus.setText(message!=null?message:(accuracy>0?String.format(Locale.US,"GPS دقة ±%.0f متر",accuracy):"GPS نشط"));
            updateStats();
        }};
        registerReceiver(trackingReceiver,new IntentFilter(LocationTrackingService.ACTION_UPDATE));
        navigationReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(!NavigationGuidanceService.ACTION_STATE.equals(i.getAction()))return;
            String guidance=i.getStringExtra("guidance");
            boolean active=i.getBooleanExtra("active",false);
            if(gpsStatus!=null&&guidance!=null)gpsStatus.setText(active?guidance:"GPS نشط • "+guidance);
            applySleepPolicy();
        }};
        registerReceiver(navigationReceiver,new IntentFilter(NavigationGuidanceService.ACTION_STATE));
        powerReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(Intent.ACTION_POWER_CONNECTED.equals(i.getAction()))charging=true;
            else if(Intent.ACTION_POWER_DISCONNECTED.equals(i.getAction()))charging=false;
            else refreshChargingState();
            applySleepPolicy();
        }};
        IntentFilter powerFilter=new IntentFilter();powerFilter.addAction(Intent.ACTION_POWER_CONNECTED);powerFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        registerReceiver(powerReceiver,powerFilter);
        refreshChargingState();
        handler.post(clockTick);
        if(splashPending){handler.postDelayed(()->showWelcome(),250);splashPending=false;}
    }

    private android.content.SharedPreferences prefs(){return getSharedPreferences("ali_car_v3",MODE_PRIVATE);}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private GradientDrawable glass(int fill,int stroke,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{fill,0x66101F31});
        d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
    }
    private TextView label(String text,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));t.setIncludeFontPadding(false);return t;
    }
    private LinearLayout vertical(){LinearLayout v=new LinearLayout(this);v.setOrientation(LinearLayout.VERTICAL);return v;}
    private LinearLayout horizontal(){LinearLayout v=new LinearLayout(this);v.setGravity(Gravity.CENTER_VERTICAL);v.setOrientation(LinearLayout.HORIZONTAL);return v;}
    private void updateClock(){
        long now=System.currentTimeMillis();
        String currentTime=new SimpleDateFormat("HH:mm",Locale.US).format(new Date(now));
        if(digitalClock!=null)digitalClock.setText(currentTime);
        if(ambientTime!=null)ambientTime.setText(currentTime);
        if(dateLabel!=null)dateLabel.setText(new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(new Date(now)));
        if(pager!=null&&pager.getCurrentItem()==0&&isNightMode())applyNightDim();
    }
    private boolean isNightMode(){Calendar c=Calendar.getInstance();int h=c.get(Calendar.HOUR_OF_DAY);return h>=20||h<6;}
    private void applyNightDim(){
        if(pager==null||pager.getChildCount()==0)return;
        View v=pager.getChildAt(0);if(v==null)return;
        // Keep content readable: no global brightness writes without explicit user consent.
        v.setAlpha(.94f);
    }
    static boolean shouldBlockSleep(boolean isCharging,boolean isMusicPlaying){return isCharging||isMusicPlaying;}
    static boolean shouldAllowManualSleep(){return true;}
    static boolean shouldAllowAutomaticSleep(boolean isCharging,boolean isMusicPlaying,boolean isNavigationActive){
        return !isCharging&&!isMusicPlaying&&!isNavigationActive;
    }
    private boolean sleepBlocked(){return !shouldAllowAutomaticSleep(charging,musicPlaying,NavigationGuidanceService.isActive(this));}
    private boolean manualSleep;
    private void refreshChargingState(){
        try{
            Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            charging=battery!=null&&battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,0)!=0;
        }catch(Exception ignored){charging=false;}
    }
    private void applySleepPolicy(){
        if(pager==null)return;
        if(sleepBlocked()){
            handler.removeCallbacks(idleTick);
            // A manually requested sleep screen must remain visible even if charging
            // starts or music begins. Only automatic sleep is blocked by those states.
            if(ambientOverlay!=null&&!manualSleep)dismissAmbient();
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }else{
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            scheduleIdle();
        }
    }
    private void scheduleIdle(){
        handler.removeCallbacks(idleTick);
        if(pager!=null&&ambientOverlay==null&&!sleepBlocked())handler.postDelayed(idleTick,90000);
    }
    @Override public void onUserInteraction(){
        super.onUserInteraction();
        // Do not dismiss sleep mode for an arbitrary touch; only the explicit wake control exits it.
        if(ambientOverlay==null)scheduleIdle();
    }
    private void showAmbientClock(){showAmbientClock(false,false);}
    void showAmbientClockForTest(){showAmbientClock(true,true);}
    private void showAmbientClock(boolean manual){showAmbientClock(manual,false);}
    private void showAmbientClock(boolean manual,boolean bypassPowerLocks){
        if(isFinishing()||pager==null||ambientOverlay!=null)return;
        if(!manual&&!bypassPowerLocks&&sleepBlocked())return;
        manualSleep=manual;
        FrameLayout overlay=new FrameLayout(this);
        overlay.setTag("ambient_sleep_overlay");
        overlay.setClickable(true);
        overlay.setBackgroundColor(0xFF02060C);
        LinearLayout center=vertical();center.setGravity(Gravity.CENTER);
        ambientTime=label(new SimpleDateFormat("HH:mm",Locale.US).format(new Date()),150,0xFFE7F8FF,true);
        ambientTime.setTag("ambient_sleep_time");
        ambientTime.setGravity(Gravity.CENTER);
        ambientTime.setShadowLayer(dp(20),0,0,0xFF218DB8);
        TextView date=label(new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(new Date()),22,0xFF9BB9CA,false);
        date.setGravity(Gravity.CENTER);
        TextView motto=label("REST  •  RESET  •  DRIVE SAFE",18,0xFF76DFFF,true);
        motto.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams timeLp=new LinearLayout.LayoutParams(-1,dp(190));
        center.addView(ambientTime,timeLp);center.addView(date);
        LinearLayout.LayoutParams mottoLp=new LinearLayout.LayoutParams(-1,dp(42));
        mottoLp.topMargin=dp(18);center.addView(motto,mottoLp);
        overlay.addView(center,new FrameLayout.LayoutParams(-1,-1));

        TextView wake=label("إلغاء وضع السكون",14,0xFFD6EAF4,true);
        wake.setTag("ambient_wake_button");
        wake.setGravity(Gravity.CENTER);
        wake.setPadding(dp(16),dp(8),dp(16),dp(8));
        wake.setBackground(glass(0x443A596B,0x556CB6D0,18));
        FrameLayout.LayoutParams wakeLp=new FrameLayout.LayoutParams(-2,dp(46),Gravity.TOP|Gravity.RIGHT);
        wakeLp.setMargins(0,dp(14),dp(14),0);
        overlay.addView(wake,wakeLp);
        wake.setOnClickListener(v->{manualSleep=false;dismissAmbient();scheduleIdle();});

        ambientOverlay=overlay;
        dimWindowForSleep();
        addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
    }
    private void dimWindowForSleep(){
        WindowManager.LayoutParams params=getWindow().getAttributes();
        if(!previousBrightnessCaptured){
            previousWindowBrightness=params.screenBrightness;
            previousBrightnessCaptured=true;
        }
        params.screenBrightness=0.045f;
        getWindow().setAttributes(params);
    }
    private void restoreWindowBrightness(){
        if(!previousBrightnessCaptured)return;
        WindowManager.LayoutParams params=getWindow().getAttributes();
        params.screenBrightness=previousWindowBrightness;
        getWindow().setAttributes(params);
        previousWindowBrightness=-1f;
        previousBrightnessCaptured=false;
    }
    private void dismissAmbient(){
        if(ambientOverlay!=null){
            ViewGroup parent=(ViewGroup)ambientOverlay.getParent();
            if(parent!=null)parent.removeView(ambientOverlay);
            ambientOverlay=null;ambientTime=null;
        }
        restoreWindowBrightness();
    }
    private void showWelcome(){
        if(isFinishing()||pager==null||welcomeOverlay!=null)return;
        dismissAmbient();handler.removeCallbacks(idleTick);
        NeonSplashView splash=new NeonSplashView(this);welcomeOverlay=splash;
        addContentView(splash,new FrameLayout.LayoutParams(-1,-1));
        splash.setAlpha(0);splash.animate().alpha(1).setDuration(520).setInterpolator(new DecelerateInterpolator()).start();
        if(ttsReady)try{tts.speak("Welcome Ali",TextToSpeech.QUEUE_FLUSH,null,"ali-welcome");}catch(Exception ignored){pendingSpeak=true;}
        else pendingSpeak=true;
        handler.postDelayed(this::dismissWelcome,5200);
    }
    private void dismissWelcome(){
        if(welcomeOverlay!=null){
            View v=welcomeOverlay;welcomeOverlay=null;
            Runnable remove=()->{if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);};
            if(isFinishing())remove.run();
            else v.animate().alpha(0f).setDuration(420).setInterpolator(new DecelerateInterpolator()).withEndAction(remove).start();
        }
        if(!isFinishing())scheduleIdle();
    }
    @Override public void onInit(int status){
        ttsReady=status==TextToSpeech.SUCCESS;
        if(ttsReady){try{tts.setLanguage(Locale.US);}catch(Exception ignored){}if(pendingSpeak){pendingSpeak=false;tts.speak("Welcome Ali",TextToSpeech.QUEUE_FLUSH,null,"ali-welcome");}}
    }

    private final class DashboardPager extends PagerAdapter {
        @Override public int getCount(){return 2;}
        @Override public Object instantiateItem(ViewGroup container,int position){View v=position==0?buildDashboard():buildCluster();container.addView(v);return v;}
        @Override public void destroyItem(ViewGroup c,int p,Object o){c.removeView((View)o);}
        @Override public boolean isViewFromObject(View v,Object o){return v==o;}
    }
    private View buildDashboard(){
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF020812);
        ImageView bg=new ImageView(this);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap wallpaper=CarApplication.getBitmap("dashboard");
        if(wallpaper==null){int res=getResources().getIdentifier("bg_dashboard","drawable",getPackageName());
        try(java.io.InputStream in=getResources().openRawResource(res)){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.RGB_565;
            wallpaper=BitmapFactory.decodeStream(in,null,o);CarApplication.putBitmap("dashboard",wallpaper);
        }catch(Exception ignored){}}
        if(wallpaper!=null)bg.setImageBitmap(wallpaper);
        root.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x55020812,0x00020812,0x42020812}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout all=vertical();all.setPadding(dp(18),dp(8),dp(18),dp(8));root.addView(all,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=horizontal();
        TextView brand=label("ALI CAR",28,Color.WHITE,true);brand.setLetterSpacing(.14f);brand.setShadowLayer(dp(10),0,0,0xFF47DFFF);
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView status=label("Wi-Fi   •   Bluetooth   •   GPS",12,0xFFE1EFF7,true);status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        top.addView(status,new LinearLayout.LayoutParams(-2,dp(44)));
        TextView settings=label("⚙",26,0xFFEAFBFF,true);settings.setGravity(Gravity.CENTER);settings.setBackground(glass(0x443A7D99,0x886CDFFF,26));
        LinearLayout.LayoutParams gear=new LinearLayout.LayoutParams(dp(48),dp(44));gear.leftMargin=dp(12);top.addView(settings,gear);
        settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        TextView sleep=label("☾",22,0xFFEAFBFF,true);sleep.setTag("dashboard_sleep_toggle");
        sleep.setContentDescription("تفعيل وضع السكون");sleep.setGravity(Gravity.CENTER);
        sleep.setBackground(glass(0x443A7D99,0x886CDFFF,24));
        LinearLayout.LayoutParams sleepLp=new LinearLayout.LayoutParams(dp(42),dp(40));
        sleepLp.leftMargin=dp(8);top.addView(sleep,sleepLp);
        sleep.setOnClickListener(v->showAmbientClock(true));
        all.addView(top,new LinearLayout.LayoutParams(-1,dp(50)));

        // Put media controls in the top-center strip instead of stacking them below the speed dial.
        LinearLayout mediaBar=horizontal();mediaBar.setGravity(Gravity.CENTER_VERTICAL);
        mediaBar.setPadding(dp(10),dp(3),dp(10),dp(3));
        mediaBar.setBackground(glass(0xAA14283A,0x5572BFD3,16));
        songTitle=label(prefs().getString("last_song","لا توجد أغنية قيد التشغيل"),16,Color.WHITE,true);
        songTitle.setTag("dashboard_song_title");songTitle.setSingleLine(true);songTitle.setGravity(Gravity.CENTER);
        mediaBar.addView(songTitle,new LinearLayout.LayoutParams(0,dp(46),1));
        LinearLayout media=horizontal();media.setGravity(Gravity.CENTER);
        media.addView(mediaButton("⏮","previous"),new LinearLayout.LayoutParams(dp(58),dp(48)));
        media.addView(mediaButton("▶","play"),new LinearLayout.LayoutParams(dp(62),dp(48)));
        media.addView(mediaButton("⏭","next"),new LinearLayout.LayoutParams(dp(58),dp(48)));
        LinearLayout.LayoutParams mediaLp=new LinearLayout.LayoutParams(-2,dp(48));
        mediaLp.leftMargin=dp(8);mediaBar.addView(media,mediaLp);
        SeekBar vol=new SeekBar(this);AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
        vol.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));vol.setProgress(am.getStreamVolume(AudioManager.STREAM_MUSIC));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u)am.setStreamVolume(AudioManager.STREAM_MUSIC,p,0);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        LinearLayout.LayoutParams volumeLp=new LinearLayout.LayoutParams(dp(178),dp(36));
        volumeLp.leftMargin=dp(9);mediaBar.addView(vol,volumeLp);
        LinearLayout.LayoutParams barLp=new LinearLayout.LayoutParams(-1,dp(56));
        barLp.topMargin=dp(5);all.addView(mediaBar,barLp);

        LinearLayout main=horizontal();main.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);LinearLayout.LayoutParams mainLp=new LinearLayout.LayoutParams(-1,0,1);
        mainLp.topMargin=dp(5);all.addView(main,mainLp);
        // Clock on the left and speedometer on the right as requested.
        LinearLayout left=vertical();left.setGravity(Gravity.CENTER);
        TextView timeTitle=label("LOCAL TIME",15,0xFFFFD68A,true);timeTitle.setGravity(Gravity.CENTER);
        left.addView(timeTitle,new LinearLayout.LayoutParams(-1,dp(26)));
        AnalogClockView analog=new AnalogClockView(this);analog.setTag("dashboard_analog_clock");
        analog.setTranslationX(-dp(12));
        left.addView(analog,new LinearLayout.LayoutParams(dp(270),dp(270)));
        dateLabel=label("",16,0xFFE5F4FF,true);dateLabel.setGravity(Gravity.CENTER);
        left.addView(dateLabel,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,-1,1f);
        leftLp.rightMargin=dp(8);main.addView(left,leftLp);

        LinearLayout right=vertical();right.setGravity(Gravity.CENTER);
        TextView speedTitle=label("SPEED  •  KM/H",15,0xFF8DE9FF,true);speedTitle.setGravity(Gravity.CENTER);
        right.addView(speedTitle,new LinearLayout.LayoutParams(-1,dp(26)));
        speedometer=new SpeedometerView(this);speedometer.setTag("dashboard_speed_gauge");
        speedometer.setTranslationX(dp(12));
        right.addView(speedometer,new LinearLayout.LayoutParams(dp(270),dp(270)));
        gpsStatus=label("بانتظار إشارة GPS",13,0xFFC7EFFF,false);gpsStatus.setTag("dashboard_gps_status");
        gpsStatus.setGravity(Gravity.CENTER);
        right.addView(gpsStatus,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(0,-1,1f);
        rightLp.leftMargin=dp(8);main.addView(right,rightLp);

        LinearLayout dock=horizontal();dock.setGravity(Gravity.CENTER);dock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        String[] names={"يوتيوب","الملاحة","الموسيقى","بلوتوث","Wi-Fi","السطوع"};
        int[] colors={0xFFFF4365,0xFF46E7FF,0xFFE98AFF,0xFF53AFFF,0xFF5EF1D0,0xFFFFD05E};
        for(int i=0;i<6;i++){
            final int index=i;LinearLayout item=vertical();item.setGravity(Gravity.CENTER);item.setPadding(dp(4),dp(3),dp(4),dp(3));item.setBackground(glass(0x55344C63,0x66DCEEF7,17));
            NeonIconView icon=new NeonIconView(this,i,colors[i]);item.addView(icon,new LinearLayout.LayoutParams(-1,dp(52)));
            TextView caption=label(names[i],11,Color.WHITE,true);caption.setGravity(Gravity.CENTER);
            item.addView(caption,new LinearLayout.LayoutParams(-1,dp(22)));
            item.setOnClickListener(v->onDock(index));icon.setOnClickListener(v->onDock(index));
            dock.addView(item,new LinearLayout.LayoutParams(0,dp(78),1));
        }
        LinearLayout.LayoutParams dockLp=new LinearLayout.LayoutParams(-1,dp(80));dockLp.topMargin=dp(4);all.addView(dock,dockLp);
        TextView footer=label("ALI CAR  •  SMART COCKPIT",10,0xFFD7EAF2,true);footer.setGravity(Gravity.CENTER);
        all.addView(footer,new LinearLayout.LayoutParams(-1,dp(20)));
        updateClock();return root;
    }
    private TextView mediaButton(String symbol,String action){
        TextView b=label(symbol,28,Color.WHITE,true);b.setGravity(Gravity.CENTER);b.setBackground(glass(0x444B91B0,0x556EEBFF,24));
        b.setOnClickListener(v->MusicPlaybackService.dispatch(MainActivity.this,action));return b;
    }
    private void onDock(int which){
        switch(which){
            case 0:startActivity(new Intent(this,YouTubeSearchActivity.class));break;
            case 1:startActivity(new Intent(this,NavigationActivity.class));break;
            case 2:startActivity(new Intent(this,MusicActivity.class));break;
            case 3:toggleBluetooth();break;
            case 4:toggleWifi();break;
            case 5:showBrightness();break;
        }
    }
    private void toggleBluetooth(){
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
        if(a==null){Toast.makeText(this,"البلوتوث غير متاح على هذا الجهاز",Toast.LENGTH_SHORT).show();return;}
        try{if(a.isEnabled())startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));else startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));}catch(Exception e){startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));}
    }
    private void toggleWifi(){startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}
    private void showBrightness(){
        FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(0x55000000);
        LinearLayout pill=vertical();pill.setPadding(dp(22),dp(16),dp(22),dp(16));pill.setGravity(Gravity.CENTER);pill.setBackground(glass(0xEE12283B,0xCC62DFFF,24));
        TextView title=label("مستوى سطوع الشاشة",22,Color.WHITE,true);title.setGravity(Gravity.CENTER);pill.addView(title);
        SeekBar seek=new SeekBar(this);seek.setMax(255);
        int current=128;try{current=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS);}catch(Exception ignored){}
        seek.setProgress(current);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(!u)return;try{if(Build.VERSION.SDK_INT>=23&&!Settings.System.canWrite(MainActivity.this)){startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));return;}Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,p);}catch(Exception ignored){}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        pill.addView(seek,new LinearLayout.LayoutParams(dp(420),dp(68)));overlay.addView(pill,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        overlay.setOnClickListener(v->((ViewGroup)overlay.getParent()).removeView(overlay));addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
        handler.postDelayed(()->{if(overlay.getParent()!=null)((ViewGroup)overlay.getParent()).removeView(overlay);},15000);
    }

    private View buildCluster(){
        FrameLayout root=new FrameLayout(this);root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF081727,0xFF02060C,0xFF142637}));
        LinearLayout all=vertical();all.setPadding(dp(18),dp(12),dp(18),dp(12));root.addView(all,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=horizontal();TextView back=label("›",32,0xFF9DEFFF,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->pager.setCurrentItem(0,true));header.addView(back,new LinearLayout.LayoutParams(dp(52),dp(40)));
        TextView title=label("عدادات القيادة والكيلومتراج",23,Color.WHITE,true);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));all.addView(header);
        clusterGauge=new SpeedometerView(this);clusterGauge.setSpeed(lastDisplayedSpeed);LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(dp(288),dp(288));gl.gravity=Gravity.CENTER;all.addView(clusterGauge,gl);
        LinearLayout stats=horizontal();stats.setGravity(Gravity.CENTER);
        stats.addView(statCard("رحلة حالية","trip"),new LinearLayout.LayoutParams(0,dp(112),1));
        stats.addView(statCard("كيلومترات إجمالية","odometer"),new LinearLayout.LayoutParams(0,dp(112),1));
        stats.addView(statCard("أقصى سرعة","max"),new LinearLayout.LayoutParams(0,dp(112),1));
        all.addView(stats,new LinearLayout.LayoutParams(-1,dp(116)));
        TextView reset=label("تصفير عداد الرحلة",17,0xFFFFD16B,true);reset.setGravity(Gravity.CENTER);reset.setBackground(glass(0x443A2730,0x88FFD16B,18));
        LinearLayout.LayoutParams rr=new LinearLayout.LayoutParams(dp(270),dp(50));rr.gravity=Gravity.CENTER;rr.topMargin=dp(10);all.addView(reset,rr);
        reset.setOnClickListener(v->new AlertDialog.Builder(MainActivity.this)
                .setTitle("تصفير عداد الرحلة")
                .setMessage("هل تريد تصفير مسافة الرحلة الحالية؟ لن يتغير العداد الإجمالي.")
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("تصفير",(d,w)->{
                    tripKm=0d;prefs().edit().putLong("trip_meters",0L).putFloat("trip_km",0f).apply();
                    Intent resetTrip=new Intent(MainActivity.this,LocationTrackingService.class);
                    resetTrip.setAction(LocationTrackingService.ACTION_RESET_TRIP);startService(resetTrip);updateStats();
                }).show());
        return root;
    }
    private float lastDisplayedSpeed;
    private LinearLayout statCard(String title,String key){
        LinearLayout box=vertical();box.setGravity(Gravity.CENTER);box.setBackground(glass(0x55264357,0x668BCAE0,18));box.setPadding(dp(8),dp(8),dp(8),dp(8));
        TextView t=label(title,13,0xFFB9E7F5,true);t.setGravity(Gravity.CENTER);box.addView(t);
        TextView value=label("0.0",24,Color.WHITE,true);value.setGravity(Gravity.CENTER);value.setTag(key);box.addView(value);
        return box;
    }
    private void updateStats(){
        if(pager==null||pager.getCurrentItem()!=1)return;
        View page=pager.getChildAt(1);if(page==null)return;
        updateStat(page,"trip",String.format(Locale.US,"%.1f km",tripKm));
        updateStat(page,"odometer",String.format(Locale.US,"%.1f km",odometerKm));
        updateStat(page,"max",String.format(Locale.US,"%.0f km/h",maxSpeed));
    }
    private void updateStat(View root,String key,String text){
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(key.equals(v.getTag())&&v instanceof TextView){((TextView)v).setText(text);return;}updateStat(v,key,text);}}
    }

    private void startGps(){
        try{startService(new Intent(this,LocationTrackingService.class));gpsStarted=true;}
        catch(Exception e){if(gpsStatus!=null)gpsStatus.setText("تعذر تشغيل خدمة تتبع GPS");}
    }
    private boolean freshDashboardLocation(Location l){
        if(l==null||!l.hasAccuracy()||!GeoMath.isAccuracyUsable(l.getAccuracy(),150f))return false;
        long fix=l.getElapsedRealtimeNanos(),now=SystemClock.elapsedRealtimeNanos();
        if(fix>0)return GeoMath.isFreshElapsedFix(fix,now,120000);
        return GeoMath.isUsableFix(l.getTime(),System.currentTimeMillis(),120000,l.getAccuracy());
    }
    private Location bestLastKnown(){
        Location best=null;
        for(String p:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER,LocationManager.PASSIVE_PROVIDER}){
            try{
                if(!locationManager.getAllProviders().contains(p))continue;
                Location l=locationManager.getLastKnownLocation(p);
                if(!freshDashboardLocation(l))continue;
                if(best==null||l.getElapsedRealtimeNanos()>best.getElapsedRealtimeNanos())best=l;
            }catch(Exception ignored){}
        }return best;
    }
    @Override protected void onResume(){
        super.onResume();refreshChargingState();musicPlaying=MusicPlaybackService.isAudioPlaying(this);startGps();applySleepPolicy();
    }
    @Override protected void onPause(){handler.removeCallbacks(idleTick);super.onPause();}
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(screenReceiver!=null)try{unregisterReceiver(screenReceiver);}catch(Exception ignored){}
        if(musicReceiver!=null)try{unregisterReceiver(musicReceiver);}catch(Exception ignored){}
        if(powerReceiver!=null)try{unregisterReceiver(powerReceiver);}catch(Exception ignored){}
        if(trackingReceiver!=null)try{unregisterReceiver(trackingReceiver);}catch(Exception ignored){}
        if(navigationReceiver!=null)try{unregisterReceiver(navigationReceiver);}catch(Exception ignored){}
        dismissWelcome();
        if(tts!=null){try{tts.stop();tts.shutdown();}catch(Exception ignored){}}
        super.onDestroy();
    }
    @Override public void onLocationChanged(Location l){
        if(l==null)return;
        lastDisplayedSpeed=l.hasSpeed()?l.getSpeed()*3.6f:0f;
        if(speedometer!=null)speedometer.setSpeed(lastDisplayedSpeed);
        if(clusterGauge!=null)clusterGauge.setSpeed(lastDisplayedSpeed);
        updateStats();
    }
    private long lastSpeedWarning;
    private void announceSpeedLimit(){if(System.currentTimeMillis()-lastSpeedWarning<30000)return;lastSpeedWarning=System.currentTimeMillis();if(ttsReady)tts.speak("Please slow down",TextToSpeech.QUEUE_ADD,null,"speed-warning");}
    @Override public void onProviderEnabled(String p){gpsStarted=false;startGps();}
    @Override public void onProviderDisabled(String p){gpsStarted=false;if(gpsStatus!=null)gpsStatus.setText("إشارة الموقع متوقفة");}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
}
