package com.ali.carsystem;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.media.ToneGenerator;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.telephony.TelephonyManager;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.bluetooth.BluetoothAdapter;

import java.util.Locale;

public final class MainActivity extends Activity implements LocationListener {
    public static final String EXTRA_BOOT_GREETING = "ali.boot.greeting";
    private static final int BG=0xFF080D13, PANEL=0xFF111C27, TILE=0xFF172633;
    private static final int WHITE=0xFFF2F6F9, MUTED=0xFF91A4B2, ACCENT=0xFF42D9D0, GOLD=0xFFFFC56E;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationManager locationManager;
    private SpeedView speedView;
    private TextView gpsText, wifiText, btText, simText;
    private long lastGps=0;
    private float speed=0;
    private TextToSpeech tts;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        buildDashboard();
        if (getIntent()!=null && getIntent().getBooleanExtra(EXTRA_BOOT_GREETING,false)) playBootGreeting();
    }

    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private GradientBox box(int color,int radius){
        GradientBox b=new GradientBox(color,dp(radius)); return b;
    }

    private void buildDashboard(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(10),dp(18),dp(10)); root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL); header.setPadding(dp(4),0,dp(4),0);
        LinearLayout brand=new LinearLayout(this); brand.setOrientation(LinearLayout.VERTICAL);
        TextView logo=label("ALI",27,WHITE,true); logo.setLetterSpacing(0.16f);
        TextView sub=label("AL-AHMAD  •  VAO",10,ACCENT,true);
        brand.addView(logo); brand.addView(sub);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(54),1));
        LinearLayout status=new LinearLayout(this); status.setGravity(Gravity.CENTER_VERTICAL);
        wifiText=label("Wi-Fi",12,MUTED,true); btText=label("Bluetooth",12,MUTED,true);
        simText=label("SIM  ••",12,MUTED,true);
        addStatus(status,wifiText); addStatus(status,btText); addStatus(status,simText);
        header.addView(status);
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(60)));

        LinearLayout body=new LinearLayout(this); body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER); body.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        LinearLayout gaugePanel=new LinearLayout(this); gaugePanel.setOrientation(LinearLayout.VERTICAL);
        gaugePanel.setGravity(Gravity.CENTER); gaugePanel.setPadding(dp(8),dp(4),dp(8),dp(4));
        gaugePanel.setBackground(box(PANEL,24));
        speedView=new SpeedView(this);
        gaugePanel.addView(speedView,new LinearLayout.LayoutParams(-1,0,1));
        gpsText=label("بانتظار إشارة GPS",12,MUTED,false); gpsText.setGravity(Gravity.CENTER);
        gaugePanel.addView(gpsText,new LinearLayout.LayoutParams(-1,dp(26)));
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,-1,0.94f); gp.rightMargin=dp(12);
        body.addView(gaugePanel,gp);

        LinearLayout right=new LinearLayout(this); right.setOrientation(LinearLayout.VERTICAL);
        TextView title=label("لوحة القيادة",18,WHITE,true); title.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        right.addView(title,new LinearLayout.LayoutParams(-1,dp(36)));
        tileRow(right,new Tile("⌖","الملاحة",()->openNavigation()),new Tile("▶","يوتيوب لايت",()->startActivity(new Intent(this,YouTubeLiteActivity.class))));
        tileRow(right,new Tile("♫","الموسيقى",()->startActivity(new Intent(this,MusicActivity.class))),new Tile("☎","الهاتف",()->open(new Intent(Intent.ACTION_DIAL),"تطبيق الهاتف غير متوفر")));
        tileRow(right,new Tile("◉","Wi-Fi",()->open(new Intent(Settings.ACTION_WIFI_SETTINGS),"إعدادات Wi-Fi غير متاحة")),new Tile("ᛒ","Bluetooth",()->open(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS),"إعدادات Bluetooth غير متاحة")));
        tileRow(right,new Tile("▤","القراءة",()->openReader()),new Tile("⚙","إعدادات الجهاز",()->open(new Intent(Settings.ACTION_SETTINGS),"الإعدادات غير متاحة")));
        tileRow(right,new Tile("▣","متجر Play",()->openPlayStore()),new Tile("▤","إدارة التطبيقات",()->open(new Intent(Settings.ACTION_APPLICATION_SETTINGS),"إعدادات التطبيقات غير متاحة")));
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(0,-1,1.06f);
        body.addView(right,rp);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout footer=new LinearLayout(this); footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView left=label("نظام السيارة الذكي",11,MUTED,false);
        TextView credit=label("تم تطويري بواسطة ALI AHMAD",11,ACCENT,true);
        footer.addView(left,new LinearLayout.LayoutParams(0,dp(24),1));
        footer.addView(credit);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(28)));
        setContentView(root);
        refreshStatus();
    }

    private TextView label(String value,int size,int color,boolean bold){
        TextView t=new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER_VERTICAL); t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        return t;
    }
    private void addStatus(LinearLayout row,TextView t){
        t.setPadding(dp(9),dp(5),dp(9),dp(5)); row.addView(t);
    }
    private void tileRow(LinearLayout parent,Tile a,Tile b){
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams rr=new LinearLayout.LayoutParams(-1,0,1); rr.bottomMargin=dp(8); parent.addView(row,rr);
        addTile(row,a); addTile(row,b);
    }
    private void addTile(LinearLayout row,Tile tile){
        LinearLayout card=new LinearLayout(this); card.setOrientation(LinearLayout.VERTICAL); card.setGravity(Gravity.CENTER);
        card.setBackground(box(TILE,18)); card.setClickable(true); card.setFocusable(true);
        TextView icon=label(tile.icon,28,ACCENT,true); icon.setGravity(Gravity.CENTER);
        TextView text=label(tile.title,15,WHITE,true); text.setGravity(Gravity.CENTER);
        card.addView(icon,new LinearLayout.LayoutParams(-1,dp(42)));
        card.addView(text,new LinearLayout.LayoutParams(-1,dp(30)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1); p.leftMargin=dp(4); p.rightMargin=dp(4);
        row.addView(card,p); card.setOnClickListener(v->tile.action.run());
    }
    private void open(Intent i,String message){
        try{if(i.resolveActivity(getPackageManager())==null){toast(message);return;} startActivity(i);}
        catch(ActivityNotFoundException|SecurityException e){toast(message);}
    }
    private void openNavigation(){
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q=&mode=d"));
        if(i.resolveActivity(getPackageManager())==null)i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="));
        open(i,"لم يتم العثور على تطبيق خرائط");
    }
    private void openPlayStore(){
        Intent store=getPackageManager().getLaunchIntentForPackage("com.android.vending");
        if(store==null){toast("متجر Play غير متوفر");return;}
        open(store,"تعذّر فتح متجر Play");
    }
    private void openReader(){
        String[] pkgs={"com.amazon.kindle","com.zinio.mobile.android.reader","com.asus.supernote","com.google.android.apps.docs"};
        for(String p:pkgs){Intent i=getPackageManager().getLaunchIntentForPackage(p);if(i!=null){startActivity(i);return;}}
        toast("لا يوجد تطبيق قراءة مثبت");
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    private void refreshStatus(){
        try{WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE); wifiText.setText(wm!=null&&wm.isWifiEnabled()?"Wi-Fi  • متصل/مفعّل":"Wi-Fi  • متوقف");}catch(Exception ignored){}
        try{BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();btText.setText(a!=null&&a.isEnabled()?"Bluetooth  • مفعّل":"Bluetooth  • متوقف");}catch(Exception ignored){}
        try{TelephonyManager tm=(TelephonyManager)getSystemService(TELEPHONY_SERVICE);simText.setText(tm!=null&&tm.getSimState()==TelephonyManager.SIM_STATE_READY?"الشريحة  • جاهزة":"SIM  • تحقق من الشريحة");}catch(Exception ignored){}
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        if(intent!=null && intent.getBooleanExtra(EXTRA_BOOT_GREETING,false)) playBootGreeting();
    }
    private void playBootGreeting(){
        ToneGenerator chime = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
        chime.startTone(ToneGenerator.TONE_PROP_ACK, 240);
        handler.postDelayed(() -> {
            chime.startTone(ToneGenerator.TONE_PROP_BEEP2, 320);
            handler.postDelayed(chime::release, 420);
        }, 280);
        tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS){
                int language=tts.setLanguage(Locale.US);
                if(language!=TextToSpeech.LANG_MISSING_DATA && language!=TextToSpeech.LANG_NOT_SUPPORTED){
                    handler.postDelayed(()->tts.speak("Welcome Ali. Developed by Ali Ahmad.",TextToSpeech.QUEUE_FLUSH,null),1100);
                }
            }
        });
    }
    @Override protected void onResume(){super.onResume();startGps();refreshStatus();}
    @Override protected void onPause(){stopGps();super.onPause();}
    @Override protected void onDestroy(){if(tts!=null){tts.stop();tts.shutdown();}super.onDestroy();}
    private void startGps(){
        if(locationManager==null)return;
        try{
            if(checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
            if(!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){gpsText.setText("فعّل GPS لعرض السرعة");speedView.setGps(false);return;}
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,1,this);
        }catch(Exception ignored){}
    }
    private void stopGps(){
        if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(SecurityException ignored){}
        lastGps=0; speed=0; if(speedView!=null){speedView.setSpeed(0);speedView.setGps(false);}
    }
    @Override public void onLocationChanged(Location l){
        if(l==null)return; lastGps=SystemClock.elapsedRealtime();
        float s=l.hasSpeed()?l.getSpeed()*3.6f:0; if(Float.isNaN(s)||Float.isInfinite(s)||s<0)s=0;
        speed=speed==0?s:(speed*.65f+s*.35f); speedView.setSpeed(speed); speedView.setGps(true); gpsText.setText("إشارة GPS متصلة");
    }
    @Override public void onProviderDisabled(String p){if(LocationManager.GPS_PROVIDER.equals(p)){speedView.setGps(false);gpsText.setText("تم إيقاف GPS");}}
    @Override public void onProviderEnabled(String p){if(LocationManager.GPS_PROVIDER.equals(p))startGps();}
    @Override public void onStatusChanged(String p,int s,Bundle b){}

    private static final class Tile{final String icon,title;final Runnable action;Tile(String i,String t,Runnable a){icon=i;title=t;action=a;}}
    private final class SpeedView extends View{
        private final Paint p=new Paint(3); private final RectF arc=new RectF(); private float value=0; private boolean gps=false;
        SpeedView(Activity a){super(a);}
        void setSpeed(float v){value=Math.max(0,Math.min(220,v));invalidate();}
        void setGps(boolean v){gps=v;invalidate();}
        @Override protected void onDraw(Canvas c){
            super.onDraw(c); float cx=getWidth()/2f,cy=getHeight()*.49f,r=Math.min(getWidth()*.42f,getHeight()*.44f);
            arc.set(cx-r,cy-r,cx+r,cy+r); p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(9));p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(0xFF2B3B47);c.drawArc(arc,135,270,false,p);
            p.setColor(gps?ACCENT:0xFF637783);c.drawArc(arc,135,(Math.min(value,220)/220f)*270,false,p);
            for(int n=0;n<=220;n+=10){double a=Math.toRadians(135+n/220f*270);float o=r*.87f,i=r*(n%20==0?.74f:.80f);
                p.setStrokeWidth(n%20==0?dp(2):dp(1));p.setColor(n%20==0?WHITE:MUTED);
                c.drawLine(cx+(float)Math.cos(a)*o,cy+(float)Math.sin(a)*o,cx+(float)Math.cos(a)*i,cy+(float)Math.sin(a)*i,p);
                if(n%40==0){p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(10));p.setColor(MUTED);c.drawText(""+n,cx+(float)Math.cos(a)*r*.62f,cy+(float)Math.sin(a)*r*.62f+dp(4),p);p.setStyle(Paint.Style.STROKE);}
            }
            double na=Math.toRadians(135+Math.min(value,220)/220f*270);p.setColor(GOLD);p.setStrokeWidth(dp(3));
            c.drawLine(cx,cy,cx+(float)Math.cos(na)*r*.58f,cy+(float)Math.sin(na)*r*.58f,p);
            p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,dp(6),p);p.setTextAlign(Paint.Align.CENTER);p.setColor(WHITE);
            p.setTypeface(Typeface.create("sans-serif-light",0));p.setTextSize(Math.min(dp(52),r*.40f));c.drawText(gps?""+Math.round(value):"--",cx,cy+r*.34f,p);
            p.setTextSize(dp(13));p.setColor(MUTED);c.drawText("km/h",cx,cy+r*.52f,p);
            p.setTextSize(dp(11));p.setColor(ACCENT);c.drawText("ALI  •  GPS SPEED",cx,cy-r*.57f,p);
        }
    }
    private static final class GradientBox extends android.graphics.drawable.GradientDrawable{
        GradientBox(int color,float radius){setColor(color);setCornerRadius(radius);setStroke(1,0xFF263B49);}
    }
}
