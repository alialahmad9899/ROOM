package com.ali.carsystem;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class MainActivity extends Activity implements LocationListener {
    private static final int BG=0xFF07111F, WHITE=0xFFF4F7FB, MUTED=0xFF9EB0C5;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private WifiManager wifi;
    private TextView clock, date, speed, gps, wifiState, btState;
    private View idleOverlay;
    private long lastGps;
    private final Runnable ticker=new Runnable(){ public void run(){
        Date now=new Date();
        if(clock!=null) clock.setText(new SimpleDateFormat("HH:mm",Locale.US).format(now));
        if(date!=null) date.setText(new SimpleDateFormat("EEE • dd MMM",Locale.US).format(now));
        if(lastGps==0 || android.os.SystemClock.elapsedRealtime()-lastGps>8000){
            if(speed!=null) speed.setText("--");
            if(gps!=null) gps.setText("بانتظار إشارة GPS");
        }
        refreshConnectivity();
        handler.postDelayed(this,5000);
    }};
    private final Runnable idle=new Runnable(){ public void run(){ showIdleClock(); }};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        build();
        applyImmersive();
        handler.post(ticker);
        resetIdle();
        Intent i=getIntent();
        if(i!=null && i.getBooleanExtra(EXTRA_BOOT_GREETING,false)) speakGreeting();
    }
    public static final String EXTRA_BOOT_GREETING="ali.boot.greeting";
    public static final String EXTRA_SHOW_WELCOME="ali.show.welcome";
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    private void applyImmersive(){
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)applyImmersive();}
    @Override protected void onResume(){super.onResume();applyImmersive();startGps();handler.removeCallbacks(ticker);handler.post(ticker);}
    @Override protected void onPause(){handler.removeCallbacks(ticker);handler.removeCallbacks(idle);stopGps();super.onPause();}
    @Override public void onUserInteraction(){super.onUserInteraction();resetIdle();if(idleOverlay!=null){((android.view.ViewGroup)idleOverlay.getParent()).removeView(idleOverlay);idleOverlay=null;}}
    private void resetIdle(){handler.removeCallbacks(idle);handler.postDelayed(idle,90000);}
    private GradientDrawable bg(int color,int radius,int stroke){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{color,blend(color,0xFF182D49),color});
        d.setCornerRadius(dp(radius)); if(stroke!=0)d.setStroke(dp(1),stroke); return d;
    }
    private int blend(int a,int b){return Color.rgb((Color.red(a)+Color.red(b))/2,(Color.green(a)+Color.green(b))/2,(Color.blue(a)+Color.blue(b))/2);}
    private TextView txt(String s,int size,int color,boolean bold){
        TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);
        v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setGravity(Gravity.CENTER);return v;
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(12),dp(18),dp(12));root.setBackgroundColor(BG);
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=txt("ALI CAR",25,0xFF42D7FF,true);brand.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(0,dp(52),1);header.addView(brand,bp);
        LinearLayout center=new LinearLayout(this);center.setOrientation(LinearLayout.VERTICAL);center.setGravity(Gravity.CENTER);
        clock=txt("--:--",27,WHITE,true);date=txt("",10,MUTED,false);
        center.addView(clock);center.addView(date);
        header.addView(center,new LinearLayout.LayoutParams(0,dp(55),1));
        TextView settings=txt("⚙",25,WHITE,true);settings.setBackground(bg(0xFF203B5C,28,0xFF3A6C91));
        settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_SETTINGS)));
        header.addView(settings,new LinearLayout.LayoutParams(dp(52),dp(52)));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(62)));

        LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER);
        wifiState=txt("◉ Wi-Fi",13,0xFF4DE1C1,true);btState=txt("ᛒ Bluetooth",13,0xFF71B7FF,true);
        statusRow.addView(wifiState,new LinearLayout.LayoutParams(0,dp(34),1));
        statusRow.addView(btState,new LinearLayout.LayoutParams(0,dp(34),1));
        wifiState.setOnClickListener(v->toggleWifi());btState.setOnClickListener(v->toggleBluetooth());
        root.addView(statusRow);

        LinearLayout body=new LinearLayout(this);body.setGravity(Gravity.CENTER);body.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout gauge=new LinearLayout(this);gauge.setOrientation(LinearLayout.VERTICAL);gauge.setGravity(Gravity.CENTER);
        gauge.setBackground(bg(0xFF10243A,26,0xFF23628B));
        TextView label=txt("GPS SPEED",12,0xFF76D8FF,true);
        speed=txt("--",72,WHITE,true);TextView unit=txt("KM/H",15,0xFF43D7C2,true);
        gps=txt("بانتظار إشارة GPS",12,MUTED,false);
        gauge.addView(label);gauge.addView(speed);gauge.addView(unit);gauge.addView(gps);
        body.addView(gauge,new LinearLayout.LayoutParams(0,-1,1.05f));
        LinearLayout tiles=new LinearLayout(this);tiles.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,-1,1f);tp.leftMargin=dp(12);
        body.addView(tiles,tp);
        row(tiles,new Tile("🧭","الملاحة",()->open(NavigationActivity.class),0xFF126B87),
            new Tile("▶","YouTube",this::openYouTube,0xFF9B3152));
        row(tiles,new Tile("♫","الموسيقى",()->open(MusicActivity.class),0xFF7042A3),
            new Tile("◉","Wi-Fi",this::toggleWifi,0xFF147C72));
        row(tiles,new Tile("ᛒ","Bluetooth",this::toggleBluetooth,0xFF245C9C),
            new Tile("⚙","الإعدادات",()->startActivity(new Intent(Settings.ACTION_SETTINGS)),0xFF59677C));
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        TextView footer=txt("تم تطوير الواجهة بواسطة : ali ahmad",11,0xFF63CBEA,false);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(25)));
        root.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)resetIdle();return false;});
        setContentView(root);
    }
    private void row(LinearLayout p,Tile a,Tile b){
        LinearLayout r=new LinearLayout(this);r.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);rp.bottomMargin=dp(8);p.addView(r,rp);
        addTile(r,a);addTile(r,b);
    }
    private void addTile(LinearLayout r,Tile t){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setGravity(Gravity.CENTER);
        c.setBackground(bg(t.color,20,0x553FFFFFF));
        TextView icon=txt(t.icon,32,WHITE,true),name=txt(t.name,14,WHITE,true);
        c.addView(icon);c.addView(name);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.leftMargin=dp(4);p.rightMargin=dp(4);r.addView(c,p);
        c.setOnClickListener(v->t.action.run());
    }
    private void open(Class<?> c){startActivity(new Intent(this,c));}
    private void openYouTube(){
        try{Intent i=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://m.youtube.com/"));
            i.addCategory(Intent.CATEGORY_BROWSABLE);startActivity(i);}
        catch(Exception e){android.widget.Toast.makeText(this,"لا يوجد متصفح متاح",0).show();}
    }
    private void toggleWifi(){if(wifi==null)return;try{wifi.setWifiEnabled(!wifi.isWifiEnabled());}catch(Exception ignored){}refreshConnectivity();}
    private void toggleBluetooth(){BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null)return;try{if(a.isEnabled())a.disable();else a.enable();}catch(Exception ignored){}refreshConnectivity();}
    private void refreshConnectivity(){
        if(wifiState!=null&&wifi!=null)wifiState.setText(wifi.isWifiEnabled()?"● Wi-Fi يعمل":"○ Wi-Fi متوقف");
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();
        if(btState!=null)btState.setText(a!=null&&a.isEnabled()?"● Bluetooth يعمل":"○ Bluetooth متوقف");
    }
    private void startGps(){
        if(locationManager==null)return;
        try{if(checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==0){
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,1,this);
            Location l=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);if(l!=null)onLocationChanged(l);
        }}catch(Exception ignored){}
    }
    private void stopGps(){if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(Exception ignored){}}
    @Override public void onLocationChanged(Location l){
        if(l==null)return;lastGps=android.os.SystemClock.elapsedRealtime();
        float kmh=l.hasSpeed()?l.getSpeed()*3.6f:0f;speed.setText(String.format(Locale.US,"%.0f",kmh));
        gps.setText(l.hasAccuracy()?"GPS دقة ±"+(int)l.getAccuracy()+"م":"GPS متصل");
    }
    @Override public void onProviderDisabled(String p){if(gps!=null)gps.setText("GPS متوقف");}
    @Override public void onProviderEnabled(String p){}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
    private void showIdleClock(){
        if(isFinishing())return;
        LinearLayout overlay=new LinearLayout(this);overlay.setGravity(Gravity.CENTER);overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setBackgroundColor(0xF0050A10);
        TextView time=txt(new SimpleDateFormat("HH:mm",Locale.US).format(new Date()),88,0xFFB8DFFF,true);
        TextView day=txt(new SimpleDateFormat("EEEE • dd MMMM",new Locale("ar")).format(new Date()),20,MUTED,false);
        overlay.addView(time);overlay.addView(day);
        overlay.setOnClickListener(v->{((android.view.ViewGroup)overlay.getParent()).removeView(overlay);idleOverlay=null;resetIdle();});
        ((android.view.ViewGroup)findViewById(android.R.id.content)).addView(overlay,new android.view.ViewGroup.LayoutParams(-1,-1));
        idleOverlay=overlay;
    }
    private void speakGreeting(){
        try{android.speech.tts.TextToSpeech engine=new android.speech.tts.TextToSpeech(this,status->{
            if(status==android.speech.tts.TextToSpeech.SUCCESS){
                int result=engine.setLanguage(new Locale("ar"));
                if(result!=android.speech.tts.TextToSpeech.LANG_MISSING_DATA&&result!=android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED)
                    engine.speak("أهلاً وسهلاً بك سيد علي، بسم الله نبدأ",android.speech.tts.TextToSpeech.QUEUE_FLUSH,null);
            }
        });}catch(Exception ignored){}
    }
    private static final class Tile{final String icon,name;final Runnable action;final int color;Tile(String i,String n,Runnable a,int c){icon=i;name=n;action=a;color=c;}}
}