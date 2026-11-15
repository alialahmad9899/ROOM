package com.ali.carsystem;

import android.animation.ObjectAnimator;
import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.pm.*;
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
import java.util.*;
import java.text.SimpleDateFormat;

public final class MainActivity extends BaseActivity implements LocationListener, TextToSpeech.OnInitListener {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private ViewPager pager;
    private FrameLayout dashboardRoot;
    private NavigationBannerView navigationHud;
    private boolean navigationRouteActive;
    private static final int REQUEST_DASHBOARD_SETTINGS=9100;
    private SpeedometerView speedometer, clusterGauge;
    private TextView gpsStatus, songTitle, digitalClock, dateLabel, ambientTime, connectionStatus, tripSummary;
    private View nightOverlay;
    private LocationManager locationManager;
    private Location previousFix;
    private boolean gpsStarted, dashboardCriteriaRegistered, splashPending=true, ttsReady, pendingSpeak, activityResumed, ttsInitializationAttempted;
    private TextToSpeech tts;
    private double tripKm, odometerKm;
    private float maxSpeed;
    private long lastTripTimestamp;
    private final Runnable clockTick=new Runnable(){@Override public void run(){updateClock();handler.postDelayed(this,1000);}};
    private final Runnable idleTick=()->showAmbientClock();
    private View ambientOverlay,welcomeOverlay;
    private float previousWindowBrightness=-1f;
    private boolean previousBrightnessCaptured;
    private BroadcastReceiver musicReceiver,powerReceiver,trackingReceiver,navigationReceiver;
    private boolean charging, musicPlaying;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        tripKm=prefs().contains("trip_meters")?prefs().getLong("trip_meters",0L)/1000d:prefs().getFloat("trip_km",0f);
        odometerKm=prefs().contains("odometer_meters")?prefs().getLong("odometer_meters",0L)/1000d:prefs().getFloat("odometer_km",0f);
        maxSpeed=prefs().getFloat("max_speed",0f);
        // Text-to-Speech is initialized only after the first-boot welcome screen.
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        pager=new ViewPager(this);pager.setId(View.generateViewId());pager.setTag("dashboard_view_pager");
        pager.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        pager.setAdapter(new DashboardPager());
        pager.setPageTransformer(true,(page,position)->{page.setAlpha(Math.max(.4f,1-Math.abs(position)*.35f));page.setScaleY(1-Math.min(.06f,Math.abs(position)*.06f));});
        dashboardRoot=new FrameLayout(this);dashboardRoot.setBackgroundColor(0xFF020812);dashboardRoot.addView(pager,new FrameLayout.LayoutParams(-1,-1));setContentView(dashboardRoot);createNavigationHud();
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener(){
            @Override public void onPageSelected(int position){
                applySleepPolicy();
                updateStats();
            }
        });
        // Do not replay the welcome screen every time the display wakes.
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
            if(tripSummary!=null)tripSummary.setText(String.format(Locale.US,"الرحلة %.1f كم  •  السرعة %.0f كم/س",tripKm,lastDisplayedSpeed));
            updateStats();
        }};
        registerReceiver(trackingReceiver,new IntentFilter(LocationTrackingService.ACTION_UPDATE));
        navigationReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(!NavigationGuidanceService.ACTION_STATE.equals(i.getAction()))return;
            String guidance=i.getStringExtra("guidance");
            boolean active=i.getBooleanExtra("active",false);
            if(gpsStatus!=null&&guidance!=null)gpsStatus.setText(active?guidance:"GPS نشط • "+guidance);
            updateNavigationHud(i,active);
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
        // Keep first-paint startup quiet; GPS, TTS and the live clock start after welcome.
        if(splashPending)handler.postDelayed(()->showWelcome(),250);
    }

    private android.content.SharedPreferences prefs(){return getSharedPreferences("ali_car_v3",MODE_PRIVATE);}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private int accentColor(){
        String theme=prefs().getString("dashboard_theme","ocean");
        if("sport".equals(theme))return 0xFFFF745F;
        if("graphite".equals(theme))return 0xFFB9CAD5;
        return 0xFF62DDF4;
    }
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
        applyNightDim();
        updateConnectionIndicators();
    }
    private boolean isNightMode(){
        if(!prefs().getBoolean("smart_night_mode",true))return false;
        Calendar c=Calendar.getInstance();int h=c.get(Calendar.HOUR_OF_DAY);return h>=20||h<6;
    }
    private void applyNightDim(){
        if(nightOverlay==null)return;
        boolean night=isNightMode();
        nightOverlay.setVisibility(night?View.VISIBLE:View.GONE);
        if(night)nightOverlay.bringToFront();
    }
    private void updateConnectionIndicators(){
        if(connectionStatus==null)return;
        boolean wifi=false,bluetooth=false,gps=false;
        try{WifiManager manager=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);wifi=manager!=null&&manager.isWifiEnabled();}catch(Exception ignored){}
        try{BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();bluetooth=adapter!=null&&adapter.isEnabled();}catch(Exception ignored){}
        try{gps=locationManager!=null&&locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}
        connectionStatus.setText("Wi-Fi "+(wifi?"✓":"×")+"  •  BT "+(bluetooth?"✓":"×")+"  •  GPS "+(gps?"✓":"×"));
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
        // Android 5 RenderThread/RenderScript crashes when the blur radius exceeds 25 px.
        ambientTime.setShadowLayer(Math.min(dp(20), 24f), 0, 0, 0xFF218DB8);
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
        splashPending=false;
        dismissAmbient();handler.removeCallbacks(idleTick);
        NeonSplashView splash=new NeonSplashView(this);welcomeOverlay=splash;
        splash.setBackgroundColor(0xFF030B16);
        addContentView(splash,new FrameLayout.LayoutParams(-1,-1));
        // Prepare the welcome voice after the panel has appeared; GPS remains deferred.
        pendingSpeak=true;
        handler.postDelayed(this::initializeTts,350L);
        handler.postDelayed(this::dismissWelcome,4600L);
    }
    private void dismissWelcome(){
        boolean dismissed=welcomeOverlay!=null;
        if(dismissed){
            View v=welcomeOverlay;welcomeOverlay=null;
            if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);
        }
        if(!isFinishing()&&dismissed&&activityResumed){
            handler.removeCallbacks(clockTick);
            handler.post(clockTick);
            initializeTts();
            startGps();
            scheduleIdle();
        }
    }
    private void initializeTts(){
        if(ttsInitializationAttempted||isFinishing())return;
        ttsInitializationAttempted=true;
        try{tts=new TextToSpeech(this,this);}
        catch(Exception error){tts=null;ttsReady=false;AppDiagnostics.record(this,"Initialize dashboard TTS",error);}
    }
    boolean isGpsStartedForTest(){return gpsStarted;}
    boolean isTtsInitializationAttemptedForTest(){return ttsInitializationAttempted;}
    @Override public void onInit(int status){
        ttsReady=status==TextToSpeech.SUCCESS&&tts!=null;
        if(!ttsReady)return;
        try{
            tts.setLanguage(Locale.US);
            float rate=Math.max(0.60f,Math.min(1.00f,prefs().getFloat("welcome_voice_rate",0.78f)));
            tts.setSpeechRate(rate);
            tts.setPitch(1.10f);
            selectPreferredWelcomeVoice();
            if(pendingSpeak){
                pendingSpeak=false;
                Bundle speech=new Bundle();
                speech.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME,0.96f);
                tts.speak("Welcome, Ali. Your ALI CAR cockpit is ready. Have a safe drive.",
                        TextToSpeech.QUEUE_FLUSH,speech,"ali-welcome");
            }
        }catch(Exception error){AppDiagnostics.record(this,"Configure ALI CAR welcome voice",error);}
    }
    private void selectPreferredWelcomeVoice(){
        if(Build.VERSION.SDK_INT<21||tts==null)return;
        try{
            Set<android.speech.tts.Voice> voices=tts.getVoices();
            if(voices==null)return;
            for(android.speech.tts.Voice voice:voices){
                if(voice==null||voice.getLocale()==null)continue;
                String language=voice.getLocale().getLanguage();
                String name=voice.getName()==null?"":voice.getName().toLowerCase(Locale.US);
                boolean female=name.contains("female")||name.contains("feminine")
                        ||name.contains("woman")||name.contains("sfg#female");
                if("en".equalsIgnoreCase(language)&&female){tts.setVoice(voice);return;}
            }
        }catch(Exception error){AppDiagnostics.record(this,"Select installed female welcome voice",error);}
    }

    private void createNavigationHud(){
        if(navigationHud==null&&dashboardRoot!=null)
            navigationHud=(NavigationBannerView)dashboardRoot.findViewWithTag("dashboard_navigation_hud");
        if(navigationHud==null&&pager!=null)pager.post(()->{
            if(navigationHud==null&&dashboardRoot!=null)
                navigationHud=(NavigationBannerView)dashboardRoot.findViewWithTag("dashboard_navigation_hud");
            if(navigationHud!=null)
                navigationHud.setVisibility(navigationRouteActive?View.VISIBLE:View.GONE);
        });
        if(navigationHud!=null)
            navigationHud.setVisibility(navigationRouteActive?View.VISIBLE:View.GONE);
    }
    private void updateNavigationHud(Intent state,boolean active){
        navigationRouteActive=active;
        if(navigationHud==null&&dashboardRoot!=null)
            navigationHud=(NavigationBannerView)dashboardRoot.findViewWithTag("dashboard_navigation_hud");
        if(navigationHud==null&&pager!=null){
            pager.post(()->{
                if(navigationHud==null&&dashboardRoot!=null)
                    navigationHud=(NavigationBannerView)dashboardRoot.findViewWithTag("dashboard_navigation_hud");
                if(navigationHud!=null)
                    navigationHud.setVisibility(navigationRouteActive?View.VISIBLE:View.GONE);
            });
            return;
        }
        if(navigationHud!=null)navigationHud.setVisibility(active?View.VISIBLE:View.GONE);
    }

    private final class DashboardPager extends PagerAdapter {
        @Override public int getCount(){return 2;}
        @Override public Object instantiateItem(ViewGroup container,int position){View v=position==0?buildDashboard():buildCluster();container.addView(v);return v;}
        @Override public void destroyItem(ViewGroup c,int p,Object o){c.removeView((View)o);}
        @Override public boolean isViewFromObject(View v,Object o){return v==o;}
    }
    private View buildDashboard(){
        FrameLayout root=new FrameLayout(this);
        root.setTag("dashboard_main_page");
        root.setBackgroundColor(0xFF020812);
        ImageView bg=new ImageView(this);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap wallpaper=CarApplication.getBitmap("dashboard");
        if(wallpaper==null){
            int res=getResources().getIdentifier("bg_dashboard","drawable",getPackageName());
            try(java.io.InputStream in=getResources().openRawResource(res)){
                BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.RGB_565;
                wallpaper=BitmapFactory.decodeStream(in,null,o);CarApplication.putBitmap("dashboard",wallpaper);
            }catch(Exception ignored){}
        }
        if(wallpaper!=null)bg.setImageBitmap(wallpaper);
        root.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x66020812,0x00020812,0x55020812}));
        root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        nightOverlay=new View(this);nightOverlay.setTag("dashboard_night_overlay");
        nightOverlay.setBackgroundColor(0x5D020915);nightOverlay.setVisibility(View.GONE);
        root.addView(nightOverlay,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout all=vertical();
        all.setPadding(dp(16),dp(6),dp(16),dp(6));
        root.addView(all,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout header=horizontal();
        TextView brand=label("ALI CAR",26,Color.WHITE,true);
        brand.setLetterSpacing(.14f);brand.setShadowLayer(dp(8),0,0,0xFF47DFFF);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(42),1f));
        connectionStatus=label("Wi-Fi ×  •  BT ×  •  GPS ×",10,0xFFE1EFF7,true);
        connectionStatus.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        header.addView(connectionStatus,new LinearLayout.LayoutParams(-2,dp(42)));
        TextView settings=label("⚙",24,0xFFEAFBFF,true);
        settings.setGravity(Gravity.CENTER);
        settings.setBackground(glass(0x443A7D99,0x886CDFFF,24));
        LinearLayout.LayoutParams settingsLp=new LinearLayout.LayoutParams(dp(42),dp(40));
        settingsLp.leftMargin=dp(10);header.addView(settings,settingsLp);
        settings.setOnClickListener(v->startActivityForResult(
                new Intent(this,SettingsActivity.class),REQUEST_DASHBOARD_SETTINGS));
        TextView sleep=label("☾",21,0xFFEAFBFF,true);
        sleep.setTag("dashboard_sleep_toggle");sleep.setContentDescription("تفعيل وضع السكون");
        sleep.setGravity(Gravity.CENTER);sleep.setBackground(glass(0x443A7D99,0x886CDFFF,24));
        LinearLayout.LayoutParams sleepLp=new LinearLayout.LayoutParams(dp(40),dp(40));
        sleepLp.leftMargin=dp(7);header.addView(sleep,sleepLp);
        sleep.setOnClickListener(v->showAmbientClock(true));
        TextView phone=label("☎",21,0xFFEAFBFF,true);
        phone.setTag("dashboard_phone_button");phone.setGravity(Gravity.CENTER);
        phone.setBackground(glass(0x443A7D99,0x886CDFFF,24));
        LinearLayout.LayoutParams phoneLp=new LinearLayout.LayoutParams(dp(40),dp(40));
        phoneLp.leftMargin=dp(7);header.addView(phone,phoneLp);
        phone.setOnClickListener(v->startActivity(new Intent(this,PhoneActivity.class)));
        all.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));

        // Route and trip information stay at the top, visible before the driver looks at the map.
        tripSummary=label(String.format(Locale.US,"الرحلة %.1f كم  •  السرعة %.0f كم/س",tripKm,lastDisplayedSpeed),
                12,0xFFEAF7FC,true);
        tripSummary.setTag("dashboard_trip_top");
        tripSummary.setGravity(Gravity.CENTER);
        all.addView(tripSummary,new LinearLayout.LayoutParams(-1,dp(24)));

        navigationHud=new NavigationBannerView(this);
        navigationHud.setCompactMode(true);
        navigationHud.setTag("dashboard_navigation_hud");
        LinearLayout.LayoutParams hudLp=new LinearLayout.LayoutParams(-1,dp(66));
        hudLp.bottomMargin=dp(4);all.addView(navigationHud,hudLp);
        navigationHud.setVisibility(navigationRouteActive?View.VISIBLE:View.GONE);

        // Compact music strip at the top; no radio controls remain in the launcher.
        LinearLayout mediaBar=horizontal();
        mediaBar.setTag("dashboard_media_bar");
        mediaBar.setGravity(Gravity.CENTER_VERTICAL);
        mediaBar.setPadding(dp(10),dp(2),dp(10),dp(2));
        mediaBar.setBackground(glass(0xB5101F2C,accentColor(),16));
        LinearLayout trackInfo=vertical();trackInfo.setGravity(Gravity.CENTER_VERTICAL);
        TextView mediaKicker=label("MUSIC  •  LOCAL LIBRARY",9,accentColor(),true);
        trackInfo.addView(mediaKicker,new LinearLayout.LayoutParams(-1,dp(15)));
        songTitle=label(prefs().getString("last_song","لا توجد أغنية قيد التشغيل"),13,Color.WHITE,true);
        songTitle.setTag("dashboard_song_title");songTitle.setSingleLine(true);
        songTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        trackInfo.addView(songTitle,new LinearLayout.LayoutParams(-1,dp(25)));
        mediaBar.addView(trackInfo,new LinearLayout.LayoutParams(0,dp(42),1f));
        mediaBar.addView(mediaButton("⏮","previous"),new LinearLayout.LayoutParams(dp(42),dp(40)));
        mediaBar.addView(mediaButton("▶","play"),new LinearLayout.LayoutParams(dp(46),dp(40)));
        mediaBar.addView(mediaButton("⏭","next"),new LinearLayout.LayoutParams(dp(42),dp(40)));
        LinearLayout.LayoutParams mediaLp=new LinearLayout.LayoutParams(-1,dp(48));
        mediaLp.bottomMargin=dp(2);all.addView(mediaBar,mediaLp);

        // The speedometer and clocks deliberately have no surrounding cards or rectangular frames.
        LinearLayout main=horizontal();
        main.setTag("dashboard_cockpit_body");
        main.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        main.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams mainLp=new LinearLayout.LayoutParams(-1,0,1f);
        mainLp.topMargin=dp(2);mainLp.bottomMargin=dp(2);all.addView(main,mainLp);

        LinearLayout speedPanel=vertical();
        speedPanel.setTag("dashboard_speed_panel");
        speedPanel.setGravity(Gravity.CENTER);
        speedometer=new SpeedometerView(this);
        speedometer.setTag("dashboard_speed_gauge");
        speedometer.setSpeed(lastDisplayedSpeed);
        LinearLayout.LayoutParams speedLp=new LinearLayout.LayoutParams(dp(194),dp(194));
        speedLp.gravity=Gravity.CENTER;
        speedPanel.addView(speedometer,speedLp);
        TextView speedCaption=label("GPS SPEED  •  KM/H",10,accentColor(),true);
        speedCaption.setGravity(Gravity.CENTER);
        speedPanel.addView(speedCaption,new LinearLayout.LayoutParams(-1,dp(22)));
        gpsStatus=label("بانتظار إشارة GPS",10,0xFFD7E9F1,false);
        gpsStatus.setTag("dashboard_gps_status");gpsStatus.setGravity(Gravity.CENTER);
        gpsStatus.setMaxLines(2);gpsStatus.setEllipsize(android.text.TextUtils.TruncateAt.END);
        speedPanel.addView(gpsStatus,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout.LayoutParams speedPanelLp=new LinearLayout.LayoutParams(0,-1,1.0f);
        main.addView(speedPanel,speedPanelLp);

        LinearLayout center=vertical();
        center.setTag("dashboard_four_way_swipe_surface");
        center.setGravity(Gravity.CENTER);
        TextView centerHint=label("اسحب يساراً أو يميناً أو للأعلى أو للأسفل",10,0xFFEAF5FA,true);
        centerHint.setGravity(Gravity.CENTER);
        centerHint.setShadowLayer(dp(4),0,dp(1),0xCC06111B);
        center.addView(centerHint,new LinearLayout.LayoutParams(-1,dp(32)));
        final float[] touchStart=new float[2];
        center.setOnTouchListener((view,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN){
                touchStart[0]=event.getX();touchStart[1]=event.getY();return true;
            }
            if(event.getAction()==MotionEvent.ACTION_UP){
                float dx=event.getX()-touchStart[0],dy=event.getY()-touchStart[1];
                int direction=DashboardGesturePolicy.direction(dx,dy,dp(54));
                switch(direction){
                    case DashboardGesturePolicy.LEFT:pager.setCurrentItem(1,true);break;
                    case DashboardGesturePolicy.RIGHT:pager.setCurrentItem(0,true);break;
                    case DashboardGesturePolicy.UP:startActivity(new Intent(MainActivity.this,NavigationActivity.class));break;
                    case DashboardGesturePolicy.DOWN:startActivity(new Intent(MainActivity.this,MusicActivity.class));break;
                    default:break;
                }
                return true;
            }
            return true;
        });
        LinearLayout.LayoutParams centerLp=new LinearLayout.LayoutParams(0,-1,1.15f);
        centerLp.leftMargin=dp(4);centerLp.rightMargin=dp(4);main.addView(center,centerLp);

        LinearLayout clockPanel=vertical();
        clockPanel.setTag("dashboard_clock_panel");
        clockPanel.setGravity(Gravity.CENTER);
        digitalClock=label("",26,Color.WHITE,true);
        digitalClock.setTag("dashboard_digital_clock");digitalClock.setGravity(Gravity.CENTER);
        clockPanel.addView(digitalClock,new LinearLayout.LayoutParams(-1,dp(38)));
        AnalogClockView analog=new AnalogClockView(this);
        analog.setTag("dashboard_analog_clock");
        clockPanel.addView(analog,new LinearLayout.LayoutParams(dp(166),dp(166)));
        dateLabel=label("",10,0xFFE5F4FF,true);dateLabel.setGravity(Gravity.CENTER);
        clockPanel.addView(dateLabel,new LinearLayout.LayoutParams(-1,dp(22)));
        LinearLayout.LayoutParams clockLp=new LinearLayout.LayoutParams(0,-1,1.0f);
        main.addView(clockPanel,clockLp);

        LinearLayout dock=horizontal();dock.setTag("dashboard_bottom_dock");
        dock.setGravity(Gravity.CENTER);dock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        String[] names={"الملاحة","بلوتوث","الموسيقى","التطبيقات","YouTube Lite"};
        int[] types={1,3,2,5,6};
        int[] colors={accentColor(),0xFF58B5F0,0xFFE98AFF,0xFFFFD05E,0xFFFF5555};
        for(int i=0;i<names.length;i++){
            final int index=i;
            LinearLayout item=vertical();item.setGravity(Gravity.CENTER);
            item.setPadding(dp(4),dp(2),dp(4),dp(2));
            item.setBackground(glass(0x77304457,0x66DCEEF7,16));
            if(i==4){
                TextView playIcon=label("▶",25,0xFFFF5555,true);
                playIcon.setGravity(Gravity.CENTER);
                item.addView(playIcon,new LinearLayout.LayoutParams(-1,dp(42)));
            }else{
                NeonIconView icon=new NeonIconView(this,types[i],colors[i]);
                item.addView(icon,new LinearLayout.LayoutParams(-1,dp(42)));
            }
            TextView caption=label(names[i],10,Color.WHITE,true);caption.setGravity(Gravity.CENTER);
            item.addView(caption,new LinearLayout.LayoutParams(-1,dp(20)));
            item.setOnClickListener(v->onDock(index));
            LinearLayout.LayoutParams itemLp=new LinearLayout.LayoutParams(0,dp(64),1f);
            itemLp.leftMargin=dp(3);itemLp.rightMargin=dp(3);dock.addView(item,itemLp);
        }
        LinearLayout.LayoutParams dockLp=new LinearLayout.LayoutParams(-1,dp(66));
        dockLp.topMargin=dp(2);all.addView(dock,dockLp);
        TextView footer=label("ALI CAR  •  SMART COCKPIT",8,0xFFD7EAF2,true);
        footer.setGravity(Gravity.CENTER);
        all.addView(footer,new LinearLayout.LayoutParams(-1,dp(12)));
        updateClock();
        return root;
    }
    private TextView mediaButton(String symbol,String action){
        TextView b=label(symbol,28,Color.WHITE,true);b.setGravity(Gravity.CENTER);b.setBackground(glass(0x444B91B0,0x556EEBFF,24));
        b.setOnClickListener(v->MusicPlaybackService.dispatch(MainActivity.this,action));return b;
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQUEST_DASHBOARD_SETTINGS&&resultCode==RESULT_OK&&pager!=null){
            pager.setAdapter(new DashboardPager());
            pager.setCurrentItem(0,false);
            navigationHud=null;
            pager.post(this::createNavigationHud);
        }
    }
    private void onDock(int which){
        switch(which){
            case 0:startActivity(new Intent(this,NavigationActivity.class));break;
            case 1:toggleBluetooth();break;
            case 2:startActivity(new Intent(this,MusicActivity.class));break;
            case 3:showAppsDialog();break;
            case 4:openYouTubeLite();break;
        }
    }
    private void showAppsDialog(){
        String[] labels={"YouTube Lite","الهاتف","إعدادات الجهاز","إعدادات Wi-Fi","ضبط السطوع"};
        new AlertDialog.Builder(this).setTitle("تطبيقات السيارة")
                .setItems(labels,(dialog,which)->{
                    switch(which){
                        case 0:openYouTubeLite();break;
                        case 1:startActivity(new Intent(this,PhoneActivity.class));break;
                        case 2:startActivity(new Intent(this,SettingsActivity.class));break;
                        case 3:toggleWifi();break;
                        case 4:showBrightness();break;
                    }
                }).setNegativeButton("إغلاق",null).show();
    }
    private void openYouTubeLite(){
        // Google no longer publishes a standalone YouTube Lite app; use the lightweight mobile site.
        android.net.Uri uri=android.net.Uri.parse("https://m.youtube.com");
        Intent view=new Intent(Intent.ACTION_VIEW,uri);
        String[] browserPackages={"com.android.chrome","com.android.browser","org.mozilla.firefox"};
        for(String pkg:browserPackages){
            try{
                getPackageManager().getPackageInfo(pkg,0);
                view.setPackage(pkg);
                startActivity(view);
                return;
            }catch(Exception ignored){}
        }
        view.setPackage(null);
        try{startActivity(Intent.createChooser(view,"فتح YouTube Lite"));}
        catch(Exception error){Toast.makeText(this,"لم يتم العثور على متصفح متوافق لفتح YouTube Lite",Toast.LENGTH_LONG).show();}
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
        FrameLayout root=new FrameLayout(this);root.setTag("dashboard_secondary_page");
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF101F2B,0xFF030A12,0xFF1C3442}));
        LinearLayout all=vertical();all.setPadding(dp(18),dp(10),dp(18),dp(10));
        root.addView(all,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=horizontal();
        TextView back=label("›",32,accentColor(),true);back.setGravity(Gravity.CENTER);
        back.setOnClickListener(v->pager.setCurrentItem(0,true));
        header.addView(back,new LinearLayout.LayoutParams(dp(52),dp(38)));
        TextView title=label("DRIVE CENTER  •  لوحة القيادة",20,Color.WHITE,true);
        title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,dp(38),1));
        all.addView(header);
        TextView swipeBack=label("اسحب لليمين للعودة إلى الشاشة الرئيسية",10,0xFFB9D3DF,false);
        swipeBack.setTag("dashboard_secondary_swipe_hint");swipeBack.setGravity(Gravity.CENTER);
        all.addView(swipeBack,new LinearLayout.LayoutParams(-1,dp(20)));
        clusterGauge=new SpeedometerView(this);clusterGauge.setTag("dashboard_speed_gauge");clusterGauge.setSpeed(lastDisplayedSpeed);
        LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(dp(224),dp(224));
        gl.gravity=Gravity.CENTER;all.addView(clusterGauge,gl);
        LinearLayout stats=horizontal();stats.setGravity(Gravity.CENTER);
        stats.addView(statCard("رحلة حالية","trip"),new LinearLayout.LayoutParams(0,dp(94),1));
        stats.addView(statCard("كيلومترات إجمالية","odometer"),new LinearLayout.LayoutParams(0,dp(94),1));
        stats.addView(statCard("أقصى سرعة","max"),new LinearLayout.LayoutParams(0,dp(94),1));
        all.addView(stats,new LinearLayout.LayoutParams(-1,dp(98)));
        LinearLayout quick=horizontal();quick.setTag("dashboard_secondary_shortcuts");quick.setGravity(Gravity.CENTER);
        String[] quickLabels={"الملاحة","الموسيقى","الهاتف"};
        Runnable[] quickActions={
                ()->startActivity(new Intent(this,NavigationActivity.class)),
                ()->startActivity(new Intent(this,MusicActivity.class)),
                ()->startActivity(new Intent(this,PhoneActivity.class))
        };
        for(int i=0;i<quickLabels.length;i++){
            final Runnable action=quickActions[i];
            TextView chip=label(quickLabels[i],13,Color.WHITE,true);
            chip.setGravity(Gravity.CENTER);chip.setBackground(glass(0x77304457,accentColor(),16));
            LinearLayout.LayoutParams chipLp=new LinearLayout.LayoutParams(0,dp(42),1f);
            chipLp.leftMargin=dp(4);chipLp.rightMargin=dp(4);quick.addView(chip,chipLp);
            chip.setOnClickListener(v->action.run());
        }
        LinearLayout.LayoutParams quickLp=new LinearLayout.LayoutParams(-1,dp(46));
        quickLp.topMargin=dp(4);all.addView(quick,quickLp);
        TextView reset=label("تصفير الرحلة والسرعة القصوى",17,0xFFFFD16B,true);reset.setTag("trip_reset_button");reset.setGravity(Gravity.CENTER);reset.setBackground(glass(0x443A2730,0x88FFD16B,18));
        LinearLayout.LayoutParams rr=new LinearLayout.LayoutParams(dp(220),dp(50));rr.gravity=Gravity.CENTER;rr.topMargin=dp(10);all.addView(reset,rr);
        reset.setOnClickListener(v->new AlertDialog.Builder(MainActivity.this)
                .setTitle("تصفير عداد الرحلة")
                .setMessage("سيتم تصفير مسافة الرحلة الحالية وأقصى سرعة مسجلة. لن يتغير عداد الكيلومترات الإجمالي.")
                .setNegativeButton("إلغاء",null)
                .setPositiveButton("تصفير",(d,w)->{
                    tripKm=0d;maxSpeed=0f;
                    prefs().edit().putLong("trip_meters",0L).putFloat("trip_km",0f).putFloat("max_speed",0f).apply();
                    Intent resetTrip=new Intent(MainActivity.this,LocationTrackingService.class);
                    resetTrip.setAction(LocationTrackingService.ACTION_RESET_TRIP);startService(resetTrip);updateStats();
                }).show());
        TextView history=label("▤  سجل الرحلات",15,accentColor(),true);
        history.setTag("trip_history_button");history.setGravity(Gravity.CENTER);
        history.setBackground(glass(0x443A2730,0x886CDFFF,16));
        LinearLayout.LayoutParams historyLp=new LinearLayout.LayoutParams(dp(220),dp(44));
        historyLp.gravity=Gravity.CENTER;historyLp.topMargin=dp(5);all.addView(history,historyLp);
        history.setOnClickListener(v->showTripHistory());
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
        if(tripSummary!=null)tripSummary.setText(String.format(Locale.US,"الرحلة %.1f كم  •  السرعة %.0f كم/س",tripKm,lastDisplayedSpeed));
    }
    private void showTripHistory(){
        String historyText=TripHistoryStore.format(this);
        new AlertDialog.Builder(this).setTitle("سجل الرحلات المحفوظ")
                .setMessage(historyText).setPositiveButton("إغلاق",null)
                .setNeutralButton("مسح السجل",(dialog,which)->
                        new AlertDialog.Builder(this).setTitle("مسح سجل الرحلات؟")
                                .setMessage("سيتم حذف سجل الرحلات المحفوظ فقط، ولن يتغير عداد الكيلومترات.")
                                .setNegativeButton("إلغاء",null)
                                .setPositiveButton("مسح",(d,w)->{
                                    TripHistoryStore.clear(this);
                                    Toast.makeText(this,"تم مسح سجل الرحلات",Toast.LENGTH_SHORT).show();
                                }).show())
                .show();
    }
    private void updateStat(View root,String key,String text){
        if(root instanceof ViewGroup){ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++){View v=g.getChildAt(i);if(key.equals(v.getTag())&&v instanceof TextView){((TextView)v).setText(text);return;}updateStat(v,key,text);}}
    }

    private void startGps(){
        if(gpsStarted)return;
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
        super.onResume();
        activityResumed=true;
        refreshChargingState();
        musicPlaying=MusicPlaybackService.isAudioPlaying(this);
        if(!splashPending&&welcomeOverlay==null){
            handler.removeCallbacks(clockTick);
            handler.post(clockTick);
            initializeTts();
            startGps();
        }
        applySleepPolicy();
    }
    @Override protected void onPause(){activityResumed=false;handler.removeCallbacks(idleTick);super.onPause();}
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
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
