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
    private SpeedometerView speedometer;
    private TextView gpsStatus, songTitle, digitalClock, dateLabel;
    private LocationManager locationManager;
    private Location previousFix;
    private boolean gpsStarted, splashPending=true, ttsReady, pendingSpeak;
    private TextToSpeech tts;
    private double tripKm, odometerKm;
    private float maxSpeed;
    private long lastTripTimestamp;
    private final Runnable clockTick=new Runnable(){@Override public void run(){updateClock();handler.postDelayed(this,1000);}};
    private final Runnable idleTick=()->showAmbientClock();
    private View ambientOverlay;
    private BroadcastReceiver screenReceiver;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        tripKm=prefs().getFloat("trip_km",0f);odometerKm=prefs().getFloat("odometer_km",0f);maxSpeed=prefs().getFloat("max_speed",0f);
        tts=new TextToSpeech(this,this);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        pager=new ViewPager(this);pager.setId(View.generateViewId());
        pager.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        pager.setAdapter(new DashboardPager());
        pager.setPageTransformer(true,(page,position)->{page.setAlpha(Math.max(.4f,1-Math.abs(position)*.35f));page.setScaleY(1-Math.min(.06f,Math.abs(position)*.06f));});
        setContentView(pager);
        pager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener(){
            @Override public void onPageSelected(int position){
                if(position==1){getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);handler.removeCallbacks(idleTick);}
                else {getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);scheduleIdle();}
                updateStats();
            }
        });
        screenReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(Intent.ACTION_SCREEN_ON.equals(i.getAction()))handler.postDelayed(()->showWelcome(),450);}};
        registerReceiver(screenReceiver,new IntentFilter(Intent.ACTION_SCREEN_ON));
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
        if(digitalClock!=null)digitalClock.setText(new SimpleDateFormat("HH:mm",Locale.US).format(new Date(now)));
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
    private void scheduleIdle(){
        handler.removeCallbacks(idleTick);
        if(pager!=null&&pager.getCurrentItem()==0&&ambientOverlay==null)handler.postDelayed(idleTick,90000);
    }
    @Override public void onUserInteraction(){super.onUserInteraction();dismissAmbient();scheduleIdle();}
    private void showAmbientClock(){
        if(isFinishing()||pager==null||pager.getCurrentItem()!=0||ambientOverlay!=null)return;
        FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(Color.BLACK);
        LinearLayout center=vertical();center.setGravity(Gravity.CENTER);
        TextView time=label(new SimpleDateFormat("HH:mm",Locale.US).format(new Date()),126,0xFFE6F8FF,true);
        time.setGravity(Gravity.CENTER);time.setShadowLayer(dp(18),0,0,0xFF32BFFF);
        TextView date=label(new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(new Date()),20,0xFFA6C7D9,false);date.setGravity(Gravity.CENTER);
        TextView motto=label("ALI CAR  •  قيادة آمنة وممتعة",15,0xFF64CBEA,true);motto.setGravity(Gravity.CENTER);
        center.addView(time,new LinearLayout.LayoutParams(-1,dp(160)));center.addView(date);center.addView(motto);
        overlay.addView(center,new FrameLayout.LayoutParams(-1,-1));
        overlay.setOnClickListener(v->dismissAmbient());ambientOverlay=overlay;
        addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
    }
    private void dismissAmbient(){if(ambientOverlay!=null){ViewGroup p=(ViewGroup)ambientOverlay.getParent();if(p!=null)p.removeView(ambientOverlay);ambientOverlay=null;}}
    private void showWelcome(){
        if(isFinishing()||pager==null)return;dismissAmbient();handler.removeCallbacks(idleTick);
        NeonSplashView splash=new NeonSplashView(this);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,-1);
        addContentView(splash,lp);
        splash.setAlpha(0);splash.animate().alpha(1).setDuration(180).start();
        if(ttsReady)try{tts.speak("Welcome Ali",TextToSpeech.QUEUE_FLUSH,null,"ali-welcome");}catch(Exception ignored){pendingSpeak=true;}
        else pendingSpeak=true;
        handler.postDelayed(()->{ViewGroup p=(ViewGroup)splash.getParent();if(p!=null)p.removeView(splash);scheduleIdle();},4100);
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
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0xCC020812,0x22020812,0xAA020812}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout all=vertical();all.setPadding(dp(18),dp(8),dp(18),dp(8));root.addView(all,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout top=horizontal();
        TextView brand=label("ALI CAR",28,Color.WHITE,true);brand.setLetterSpacing(.14f);brand.setShadowLayer(dp(10),0,0,0xFF47DFFF);
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView status=label("◉  Wi-Fi    ᛒ  Bluetooth    ▰  Battery",13,0xFFC8EFFF,true);status.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        top.addView(status,new LinearLayout.LayoutParams(-2,dp(44)));
        TextView settings=label("⚙",26,0xFFEAFBFF,true);settings.setGravity(Gravity.CENTER);settings.setBackground(glass(0x443A7D99,0x886CDFFF,26));
        LinearLayout.LayoutParams gear=new LinearLayout.LayoutParams(dp(48),dp(44));gear.leftMargin=dp(12);top.addView(settings,gear);
        settings.setOnClickListener(v->startActivity(new Intent(this,SettingsActivity.class)));
        all.addView(top,new LinearLayout.LayoutParams(-1,dp(50)));

        LinearLayout main=horizontal();LinearLayout.LayoutParams mainLp=new LinearLayout.LayoutParams(-1,0,1);mainLp.topMargin=dp(4);all.addView(main,mainLp);
        LinearLayout left=vertical();left.setGravity(Gravity.CENTER);
        speedometer=new SpeedometerView(this);left.addView(speedometer,new LinearLayout.LayoutParams(dp(236),dp(236)));
        gpsStatus=label("بانتظار إشارة GPS",12,0xFFC7EFFF,false);gpsStatus.setGravity(Gravity.CENTER);
        left.addView(gpsStatus,new LinearLayout.LayoutParams(-1,dp(24)));
        songTitle=label(prefs().getString("last_song","لا توجد أغنية قيد التشغيل"),12,Color.WHITE,true);songTitle.setGravity(Gravity.CENTER);
        left.addView(songTitle,new LinearLayout.LayoutParams(-1,dp(24)));
        LinearLayout media=horizontal();media.setGravity(Gravity.CENTER);
        media.addView(mediaButton("⏮","previous"),new LinearLayout.LayoutParams(dp(48),dp(42)));
        media.addView(mediaButton("▶","play"),new LinearLayout.LayoutParams(dp(50),dp(42)));
        media.addView(mediaButton("⏭","next"),new LinearLayout.LayoutParams(dp(48),dp(42)));
        left.addView(media,new LinearLayout.LayoutParams(-1,dp(46)));
        SeekBar vol=new SeekBar(this);AudioManager am=(AudioManager)getSystemService(AUDIO_SERVICE);
        vol.setMax(am.getStreamMaxVolume(AudioManager.STREAM_MUSIC));vol.setProgress(am.getStreamVolume(AudioManager.STREAM_MUSIC));
        vol.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(u)am.setStreamVolume(AudioManager.STREAM_MUSIC,p,0);}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        left.addView(vol,new LinearLayout.LayoutParams(dp(190),dp(28)));
        LinearLayout.LayoutParams leftLp=new LinearLayout.LayoutParams(0,-1,.36f);leftLp.rightMargin=dp(10);main.addView(left,leftLp);

        View center=new View(this);main.addView(center,new LinearLayout.LayoutParams(0,-1,.30f));
        LinearLayout right=vertical();right.setGravity(Gravity.CENTER);
        digitalClock=label("00:00",62,0xFFFFC65C,true);digitalClock.setGravity(Gravity.CENTER);digitalClock.setShadowLayer(dp(14),0,0,0xFFFFA82E);
        right.addView(digitalClock,new LinearLayout.LayoutParams(-1,dp(80)));
        AnalogClockView analog=new AnalogClockView(this);right.addView(analog,new LinearLayout.LayoutParams(dp(184),dp(184)));
        dateLabel=label("",14,0xFFE5F4FF,true);dateLabel.setGravity(Gravity.CENTER);right.addView(dateLabel,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout.LayoutParams rightLp=new LinearLayout.LayoutParams(0,-1,.34f);main.addView(right,rightLp);

        LinearLayout dock=horizontal();dock.setGravity(Gravity.CENTER);dock.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        String[] names={"يوتيوب لايت","الملاحة","الموسيقى","بلوتوث","واي فاي","السطوع"};
        int[] colors={0xFFFF4365,0xFF46E7FF,0xFFE98AFF,0xFF53AFFF,0xFF5EF1D0,0xFFFFD05E};
        for(int i=0;i<6;i++){
            final int index=i;LinearLayout item=vertical();item.setGravity(Gravity.CENTER);
            NeonIconView icon=new NeonIconView(this,i,colors[i]);item.addView(icon,new LinearLayout.LayoutParams(-1,dp(52)));
            TextView caption=label(names[i],11,Color.WHITE,true);caption.setGravity(Gravity.CENTER);
            item.addView(caption,new LinearLayout.LayoutParams(-1,dp(22)));
            item.setOnClickListener(v->onDock(index));icon.setOnClickListener(v->onDock(index));
            dock.addView(item,new LinearLayout.LayoutParams(0,dp(78),1));
        }
        LinearLayout.LayoutParams dockLp=new LinearLayout.LayoutParams(-1,dp(80));dockLp.topMargin=dp(4);all.addView(dock,dockLp);
        TextView footer=label("تم تطوير الواجهة بواسطة : ali ahmad",10,0xFFE8E1D1,true);footer.setGravity(Gravity.CENTER);
        all.addView(footer,new LinearLayout.LayoutParams(-1,dp(20)));
        updateClock();return root;
    }
    private TextView mediaButton(String symbol,String action){
        TextView b=label(symbol,22,Color.WHITE,true);b.setGravity(Gravity.CENTER);b.setBackground(glass(0x444B91B0,0x556EEBFF,24));
        b.setOnClickListener(v->{Intent i=new Intent(this,MusicActivity.class);i.putExtra("action",action);startActivity(i);});return b;
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
        TextView title=label("مستوى سطوع الشاشة",17,Color.WHITE,true);title.setGravity(Gravity.CENTER);pill.addView(title);
        SeekBar seek=new SeekBar(this);seek.setMax(255);
        int current=128;try{current=Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS);}catch(Exception ignored){}
        seek.setProgress(current);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int p,boolean u){if(!u)return;try{if(Build.VERSION.SDK_INT>=23&&!Settings.System.canWrite(MainActivity.this)){startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,android.net.Uri.parse("package:"+getPackageName())));return;}Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,p);}catch(Exception ignored){}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        pill.addView(seek,new LinearLayout.LayoutParams(dp(320),dp(45)));overlay.addView(pill,new FrameLayout.LayoutParams(-2,-2,Gravity.CENTER));
        overlay.setOnClickListener(v->((ViewGroup)overlay.getParent()).removeView(overlay));addContentView(overlay,new ViewGroup.LayoutParams(-1,-1));
        handler.postDelayed(()->{if(overlay.getParent()!=null)((ViewGroup)overlay.getParent()).removeView(overlay);},3000);
    }

    private View buildCluster(){
        FrameLayout root=new FrameLayout(this);root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF081727,0xFF02060C,0xFF142637}));
        LinearLayout all=vertical();all.setPadding(dp(18),dp(12),dp(18),dp(12));root.addView(all,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout header=horizontal();TextView back=label("›",32,0xFF9DEFFF,true);back.setGravity(Gravity.CENTER);back.setOnClickListener(v->pager.setCurrentItem(0,true));header.addView(back,new LinearLayout.LayoutParams(dp(52),dp(40)));
        TextView title=label("عدادات القيادة والكيلومتراج",20,Color.WHITE,true);title.setGravity(Gravity.CENTER);header.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));all.addView(header);
        SpeedometerView gauge=new SpeedometerView(this);gauge.setSpeed(lastDisplayedSpeed);LinearLayout.LayoutParams gl=new LinearLayout.LayoutParams(dp(260),dp(260));gl.gravity=Gravity.CENTER;all.addView(gauge,gl);
        LinearLayout stats=horizontal();stats.setGravity(Gravity.CENTER);
        stats.addView(statCard("رحلة حالية","trip"),new LinearLayout.LayoutParams(0,dp(104),1));
        stats.addView(statCard("كيلومترات إجمالية","odometer"),new LinearLayout.LayoutParams(0,dp(104),1));
        stats.addView(statCard("أقصى سرعة","max"),new LinearLayout.LayoutParams(0,dp(104),1));
        all.addView(stats,new LinearLayout.LayoutParams(-1,dp(110)));
        TextView reset=label("تصفير عداد الرحلة",15,0xFFFFD16B,true);reset.setGravity(Gravity.CENTER);reset.setBackground(glass(0x443A2730,0x88FFD16B,18));
        LinearLayout.LayoutParams rr=new LinearLayout.LayoutParams(dp(230),dp(44));rr.gravity=Gravity.CENTER;rr.topMargin=dp(10);all.addView(reset,rr);
        reset.setOnClickListener(v->{tripKm=0;prefs().edit().putFloat("trip_km",0).apply();updateStats();});
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
        if(locationManager==null||gpsStarted)return;
        if(checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")!=android.content.pm.PackageManager.PERMISSION_GRANTED&&checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        boolean registered=false;
        try{if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,1,this);registered=true;}}catch(Exception ignored){}
        try{if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1500,3,this);registered=true;}}catch(Exception ignored){}
        gpsStarted=registered;
        Location last=bestLastKnown();if(last!=null)onLocationChanged(last);
        if(!registered&&gpsStatus!=null)gpsStatus.setText("فعّل خدمات الموقع للحصول على السرعة");
    }
    private Location bestLastKnown(){
        Location best=null;for(String p:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
            try{Location l=locationManager.getLastKnownLocation(p);if(l!=null&&(best==null||l.getTime()>best.getTime()))best=l;}catch(Exception ignored){}
        }return best;
    }
    @Override protected void onResume(){super.onResume();startGps();scheduleIdle();}
    @Override protected void onPause(){handler.removeCallbacks(idleTick);if(locationManager!=null){try{locationManager.removeUpdates(this);}catch(Exception ignored){}}gpsStarted=false;super.onPause();}
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);
        if(screenReceiver!=null)try{unregisterReceiver(screenReceiver);}catch(Exception ignored){}
        if(tts!=null){try{tts.stop();tts.shutdown();}catch(Exception ignored){}}
        super.onDestroy();
    }
    @Override public void onLocationChanged(Location l){
        if(l==null)return;lastDisplayedSpeed=l.hasSpeed()?l.getSpeed()*3.6f:0;
        if(previousFix!=null&&l.getTime()>previousFix.getTime()){
            long dt=l.getTime()-previousFix.getTime();float acc=l.hasAccuracy()?l.getAccuracy():999;
            double m=GeoMath.distanceMeters(previousFix.getLatitude(),previousFix.getLongitude(),l.getLatitude(),l.getLongitude());
            if(dt<60000&&dt>0&&m>3&&m<700&&acc<=35){
                tripKm+=m/1000;odometerKm+=m/1000;prefs().edit().putFloat("trip_km",(float)tripKm).putFloat("odometer_km",(float)odometerKm).apply();
            }
        }
        previousFix=new Location(l);maxSpeed=Math.max(maxSpeed,lastDisplayedSpeed);prefs().edit().putFloat("max_speed",maxSpeed).apply();
        if(speedometer!=null)speedometer.setSpeed(lastDisplayedSpeed);
        if(gpsStatus!=null)gpsStatus.setText(l.hasAccuracy()?String.format(Locale.US,"GPS دقة ±%.0f متر",l.getAccuracy()):"GPS نشط");
        if(lastDisplayedSpeed>120)announceSpeedLimit();
        updateStats();
    }
    private long lastSpeedWarning;
    private void announceSpeedLimit(){if(System.currentTimeMillis()-lastSpeedWarning<30000)return;lastSpeedWarning=System.currentTimeMillis();if(ttsReady)tts.speak("Please slow down",TextToSpeech.QUEUE_ADD,null,"speed-warning");}
    @Override public void onProviderEnabled(String p){gpsStarted=false;startGps();}
    @Override public void onProviderDisabled(String p){gpsStarted=false;if(gpsStatus!=null)gpsStatus.setText("إشارة الموقع متوقفة");}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
}
