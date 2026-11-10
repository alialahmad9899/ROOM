package com.aliahmad.ali;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Locale;

public final class MainActivity extends Activity implements LocationListener {
    private static final int BG=0xFF080D14, PANEL=0xFF111D29, TILE=0xFF182A39;
    private static final int WHITE=0xFFF4F7FA, MUTED=0xFF91A6B6, CYAN=0xFF42DCCB, GOLD=0xFFFFC46B;
    private LocationManager gps;
    private SpeedView gauge;
    private TextView gpsText;
    private long lastFix;
    private float speed;
    private Handler handler=new Handler();
    private TextToSpeech tts;
    private boolean welcomed;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        gps=(LocationManager)getSystemService(LOCATION_SERVICE);
        render();
        welcomeOnce();
    }

    private void welcomeOnce() {
        if (welcomed) return;
        welcomed=true;
        tts=new TextToSpeech(this, status -> {
            if(status==TextToSpeech.SUCCESS && tts!=null) {
                int result=tts.setLanguage(new Locale("ar"));
                if(result!=TextToSpeech.LANG_MISSING_DATA && result!=TextToSpeech.LANG_NOT_SUPPORTED)
                    tts.speak("أهلاً بك في نظام علي", TextToSpeech.QUEUE_FLUSH, null);
            }
        });
    }

    private void render() {
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(8),dp(14),dp(8));
        root.setBackgroundColor(BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout header=new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=new TextView(this);
        brand.setText("ALI");
        brand.setTextColor(WHITE); brand.setTextSize(26);
        brand.setTypeface(Typeface.create("sans-serif-black",Typeface.NORMAL));
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(44),1));
        TextView sub=new TextView(this);
        sub.setText("الأحمد  •  نظام السيارة");
        sub.setTextColor(CYAN); sub.setTextSize(15); sub.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
        header.addView(sub,new LinearLayout.LayoutParams(0,dp(44),2));
        root.addView(header,new LinearLayout.LayoutParams(-1,dp(46)));

        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.HORIZONTAL);
        body.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout gaugePanel=new LinearLayout(this);
        gaugePanel.setOrientation(LinearLayout.VERTICAL);
        gaugePanel.setGravity(Gravity.CENTER);
        gaugePanel.setPadding(dp(8),dp(8),dp(8),dp(8));
        gaugePanel.setBackground(panel());
        gauge=new SpeedView(this);
        gaugePanel.addView(gauge,new LinearLayout.LayoutParams(-1,0,1));
        gpsText=new TextView(this);
        gpsText.setText("بانتظار إشارة GPS");
        gpsText.setTextColor(MUTED); gpsText.setTextSize(12); gpsText.setGravity(Gravity.CENTER);
        gaugePanel.addView(gpsText,new LinearLayout.LayoutParams(-1,dp(25)));
        LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(0,-1,0.95f);
        gp.leftMargin=dp(8); body.addView(gaugePanel,gp);

        LinearLayout grid=new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        TextView heading=new TextView(this);
        heading.setText("التطبيقات والاتصال");
        heading.setTextColor(WHITE); heading.setTextSize(17);
        heading.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        grid.addView(heading,new LinearLayout.LayoutParams(-1,dp(34)));
        addRow(grid,new Tile("⌖","الملاحة",this::navigation),new Tile("▶","يوتيوب لايت",this::youtube));
        addRow(grid,new Tile("♫","الموسيقى",this::music),new Tile("☎","الهاتف",this::phone));
        addRow(grid,new Tile("◉","Wi-Fi",()->settings(Settings.ACTION_WIFI_SETTINGS)),new Tile("ᛒ","بلوتوث",()->settings(Settings.ACTION_BLUETOOTH_SETTINGS)));
        addRow(grid,new Tile("▣","الشريحة والاتصال",()->settings(Settings.ACTION_WIRELESS_SETTINGS)),new Tile("⚙","إعدادات الجهاز",()->settings(Settings.ACTION_SETTINGS)));
        LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-1,1.35f);
        cp.rightMargin=dp(4); body.addView(grid,cp);
        root.addView(body,new LinearLayout.LayoutParams(-1,0,1));

        TextView footer=new TextView(this);
        footer.setText("AL-AHMAD  •  قيادة أذكى، واجهة أبسط");
        footer.setTextColor(0xFF637B8D); footer.setTextSize(10); footer.setGravity(Gravity.CENTER);
        root.addView(footer,new LinearLayout.LayoutParams(-1,dp(20)));
        setContentView(root);
    }

    private void addRow(LinearLayout parent,Tile a,Tile b) {
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,0,1);
        rp.bottomMargin=dp(7); parent.addView(row,rp);
        addTile(row,a); addTile(row,b);
    }
    private void addTile(LinearLayout row,Tile tile) {
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER);
        box.setBackground(tileBg()); box.setClickable(true); box.setFocusable(true);
        TextView icon=new TextView(this); icon.setText(tile.icon); icon.setTextColor(CYAN);
        icon.setTextSize(27); icon.setGravity(Gravity.CENTER);
        box.addView(icon,new LinearLayout.LayoutParams(-1,dp(36)));
        TextView label=new TextView(this); label.setText(tile.label); label.setTextColor(WHITE);
        label.setTextSize(14); label.setGravity(Gravity.CENTER);
        box.addView(label,new LinearLayout.LayoutParams(-1,dp(27)));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);
        p.leftMargin=dp(4); p.rightMargin=dp(4); row.addView(box,p);
        box.setOnClickListener(v->tile.action.run());
    }
    private GradientDrawable panel() {
        GradientDrawable d=new GradientDrawable(); d.setColor(PANEL); d.setCornerRadius(dp(20));
        d.setStroke(dp(1),0xFF263B4B); return d;
    }
    private GradientDrawable tileBg() {
        GradientDrawable d=new GradientDrawable(); d.setColor(TILE); d.setCornerRadius(dp(16));
        d.setStroke(dp(1),0xFF294252); return d;
    }
    private void navigation() {
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("google.navigation:q=&mode=d"));
        if(i.resolveActivity(getPackageManager())==null) i=new Intent(Intent.ACTION_VIEW,Uri.parse("geo:0,0?q="));
        launch(i,"تطبيق الخرائط غير مثبت");
    }
    private void youtube() { startActivity(new Intent(this,YouTubeLiteActivity.class)); }
    private void music() { startActivity(new Intent(this,MusicActivity.class)); }
    private void phone() { launch(new Intent(Intent.ACTION_DIAL),"تطبيق الهاتف غير متاح"); }
    private void settings(String action) { launch(new Intent(action),"الإعداد المطلوب غير متاح"); }
    private void launch(Intent i,String error) {
        try { if(i.resolveActivity(getPackageManager())!=null) startActivity(i);
            else Toast.makeText(this,error,Toast.LENGTH_SHORT).show();
        } catch(Exception e) { Toast.makeText(this,error,Toast.LENGTH_SHORT).show(); }
    }

    @Override protected void onResume() { super.onResume(); startGps(); }
    @Override protected void onPause() { stopGps(); super.onPause(); }
    @Override protected void onDestroy() {
        if(tts!=null) { tts.stop(); tts.shutdown(); tts=null; }
        super.onDestroy();
    }
    private void startGps() {
        if(checkCallingOrSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) {
            gpsText.setText("فعّل صلاحية الموقع لعداد السرعة"); return;
        }
        try {
            if(gps!=null && gps.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                gps.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,1,this);
                gpsText.setText("GPS • بانتظار الإشارة");
                handler.post(watchdog);
            } else gpsText.setText("يرجى تشغيل GPS");
        } catch(Exception e) { gpsText.setText("GPS غير متاح"); }
    }
    private final Runnable watchdog=()->{
        if(lastFix==0 || SystemClock.elapsedRealtime()-lastFix>7000) {
            gauge.setSpeed(0,false); gpsText.setText("بانتظار إشارة GPS");
        }
        handler.postDelayed(watchdog,2500);
    };
    private void stopGps() {
        handler.removeCallbacks(watchdog);
        lastFix=0; speed=0;
        if(gauge!=null) gauge.setSpeed(0,false);
        if(gps!=null) try { gps.removeUpdates(this); } catch(Exception ignored) {}
    }
    @Override public void onLocationChanged(Location l) {
        if(l==null) return;
        lastFix=SystemClock.elapsedRealtime();
        float next=l.hasSpeed()?l.getSpeed()*3.6f:0;
        if(Float.isNaN(next)||Float.isInfinite(next)) next=0;
        speed=speed==0?next:speed*.65f+next*.35f;
        gauge.setSpeed(speed,true); gpsText.setText("إشارة GPS متصلة");
    }
    @Override public void onProviderDisabled(String p) { if(LocationManager.GPS_PROVIDER.equals(p)) { gauge.setSpeed(0,false); gpsText.setText("GPS متوقف"); } }
    @Override public void onProviderEnabled(String p) { if(LocationManager.GPS_PROVIDER.equals(p)) startGps(); }
    @Override public void onStatusChanged(String p,int s,Bundle b) {}
    private int dp(int n) { return Math.round(n*getResources().getDisplayMetrics().density); }

    private static final class Tile {
        final String icon,label; final Runnable action;
        Tile(String i,String l,Runnable a){icon=i;label=l;action=a;}
    }
    private final class SpeedView extends View {
        private final Paint p=new Paint(3); private final RectF arc=new RectF();
        private float value; private boolean valid;
        SpeedView(Activity a){super(a);}
        void setSpeed(float v,boolean ok){value=Math.max(0,Math.min(220,v));valid=ok;invalidate();}
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx=getWidth()/2f,cy=getHeight()*.49f,r=Math.min(getWidth()*.43f,getHeight()*.43f);
            arc.set(cx-r,cy-r,cx+r,cy+r);
            p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeWidth(dp(8));
            p.setColor(0xFF2B3D4A);c.drawArc(arc,135,270,false,p);
            p.setColor(valid?CYAN:0xFF607482);c.drawArc(arc,135,Math.min(270,value/220f*270),false,p);
            for(int n=0;n<=220;n+=20) {
                double a=Math.toRadians(135+n/220f*270);
                float x1=cx+(float)Math.cos(a)*r*.84f,y1=cy+(float)Math.sin(a)*r*.84f;
                float x2=cx+(float)Math.cos(a)*r*.72f,y2=cy+(float)Math.sin(a)*r*.72f;
                p.setColor(WHITE);p.setStrokeWidth(dp(2));c.drawLine(x1,y1,x2,y2,p);
                p.setStyle(Paint.Style.FILL);p.setColor(MUTED);p.setTextSize(dp(10));p.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.valueOf(n),cx+(float)Math.cos(a)*r*.61f,cy+(float)Math.sin(a)*r*.61f+dp(4),p);
                p.setStyle(Paint.Style.STROKE);
            }
            double a=Math.toRadians(135+value/220f*270);
            p.setColor(GOLD);p.setStrokeWidth(dp(3));c.drawLine(cx,cy,cx+(float)Math.cos(a)*r*.56f,cy+(float)Math.sin(a)*r*.56f,p);
            p.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,dp(6),p);
            p.setColor(WHITE);p.setTextSize(Math.min(dp(50),r*.4f));p.setTypeface(Typeface.create("sans-serif-light",0));p.setTextAlign(Paint.Align.CENTER);
            c.drawText(valid?String.valueOf(Math.round(value)):"--",cx,cy+r*.32f,p);
            p.setColor(MUTED);p.setTextSize(dp(12));c.drawText("كم/سا",cx,cy+r*.51f,p);
            p.setColor(CYAN);p.setTextSize(dp(10));c.drawText("ALI • GPS",cx,cy-r*.55f,p);
        }
    }
}