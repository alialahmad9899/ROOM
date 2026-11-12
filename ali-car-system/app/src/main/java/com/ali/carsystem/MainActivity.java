package com.ali.carsystem;

import android.app.*;
import android.bluetooth.BluetoothAdapter;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.location.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.view.*;
import android.widget.*;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity implements LocationListener {
    private static final int CYAN=0xFF30D9FF, GOLD=0xFFFFC34D;
    private final Handler h=new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LocationManager locations;
    private WifiManager wifi;
    private SpeedView speedView;
    private TextView timeText, largeTime, dateText, wifiText, btText, gpsText;
    private View ambient;
    private TextToSpeech tts;
    private boolean gpsRunning;
    private float speed;
    private long lastGps;
    private float oldBrightness=-1f;

    private final Runnable tick=new Runnable(){public void run(){
        Date now=new Date();
        String t=new SimpleDateFormat("hh:mm",Locale.US).format(now);
        if(timeText!=null)timeText.setText(t);
        if(largeTime!=null)largeTime.setText(t);
        if(dateText!=null)dateText.setText(new SimpleDateFormat("EEEE، d MMMM yyyy",new Locale("ar")).format(now));
        if(lastGps!=0 && SystemClock.elapsedRealtime()-lastGps>7000){
            speed=0; speedView.setSpeed(0); speedView.setGps(false);
            if(gpsText!=null)gpsText.setText("بانتظار إشارة تحديد الموقع");
        }
        if(speedView!=null)speedView.invalidate();
        h.postDelayed(this,1000);
    }};
    private final Runnable idle=new Runnable(){public void run(){showAmbient();}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b); immersive();getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        locations=(LocationManager)getSystemService(LOCATION_SERVICE);
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        build(); scheduleIdle(); greet();
    }
    private int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private void immersive(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean f){super.onWindowFocusChanged(f);if(f)immersive();}
    @Override public void onUserInteraction(){super.onUserInteraction();removeAmbient();scheduleIdle();}
    private void scheduleIdle(){h.removeCallbacks(idle);h.postDelayed(idle,90000);}

    private void build(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        ImageView bg=new ImageView(this);bg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try(InputStream in=getAssets().open("dashboard.webp")){
            BitmapFactory.Options o=new BitmapFactory.Options();o.inPreferredConfig=Bitmap.Config.RGB_565;o.inDither=true;
            bg.setImageBitmap(BitmapFactory.decodeStream(in,null,o));
        }catch(Exception e){bg.setBackgroundColor(0xFF07162A);}
        root.addView(bg,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackgroundColor(0x66000A17);
        root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout screen=new LinearLayout(this);screen.setOrientation(LinearLayout.VERTICAL);
        screen.setPadding(dp(12),dp(7),dp(12),dp(5));screen.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);

        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView logo=label("ALI CAR",26,Color.WHITE,true);logo.setLetterSpacing(.12f);logo.setShadowLayer(dp(7),0,0,CYAN);
        header.addView(logo,new LinearLayout.LayoutParams(0,dp(45),1));
        LinearLayout state=new LinearLayout(this);state.setGravity(Gravity.CENTER_VERTICAL);
        wifiText=label("◉ واي فاي",12,0xFF75E6FF,true);btText=label("ᛒ بلوتوث",12,0xFF8BC7FF,true);
        timeText=label("--:--",21,Color.WHITE,true);
        wifiText.setPadding(dp(4),0,dp(8),0);btText.setPadding(dp(4),0,dp(8),0);timeText.setPadding(dp(8),0,dp(8),0);
        state.addView(wifiText);state.addView(btText);state.addView(timeText);
        TextView set=icon("⚙",0xFF1767B8,0xFF35D8FF,42);set.setOnClickListener(v->settingsMenu(v));
        LinearLayout.LayoutParams setLp=new LinearLayout.LayoutParams(dp(42),dp(42));setLp.leftMargin=dp(7);state.addView(set,setLp);
        header.addView(state);screen.addView(header,new LinearLayout.LayoutParams(-1,dp(49)));

        LinearLayout body=new LinearLayout(this);body.setGravity(Gravity.CENTER);body.setPadding(dp(3),dp(4),dp(3),dp(4));
        LinearLayout left=panel();left.setOrientation(LinearLayout.VERTICAL);left.setGravity(Gravity.CENTER);
        TextView sl=label("مؤشر السرعة",12,0xFF8BDFFF,true);sl.setGravity(Gravity.CENTER);
        left.addView(sl,new LinearLayout.LayoutParams(-1,dp(23)));
        speedView=new SpeedView();left.addView(speedView,new LinearLayout.LayoutParams(-1,0,1));
        gpsText=label("بانتظار إشارة الموقع",11,0xFFE4F3FF,true);gpsText.setGravity(Gravity.CENTER);
        left.addView(gpsText,new LinearLayout.LayoutParams(-1,dp(22)));
        LinearLayout media=new LinearLayout(this);media.setGravity(Gravity.CENTER);
        media.addView(mediaButton("⏮","previous"),new LinearLayout.LayoutParams(dp(58),dp(38)));
        LinearLayout.LayoutParams playLp=new LinearLayout.LayoutParams(dp(72),dp(42));playLp.leftMargin=dp(7);playLp.rightMargin=dp(7);
        media.addView(mediaButton("▶","play"),playLp);media.addView(mediaButton("⏭","next"),new LinearLayout.LayoutParams(dp(58),dp(38)));
        left.addView(media,new LinearLayout.LayoutParams(-1,dp(48)));

        LinearLayout right=panel();right.setGravity(Gravity.CENTER);right.setOrientation(LinearLayout.VERTICAL);
        largeTime=label("--:--",44,Color.WHITE,true);largeTime.setGravity(Gravity.CENTER);largeTime.setShadowLayer(dp(10),0,0,GOLD);
        right.addView(largeTime,new LinearLayout.LayoutParams(-1,dp(54)));
        right.addView(new AnalogClock(),new LinearLayout.LayoutParams(dp(160),0,1));
        dateText=label("",13,0xFFFFD88A,true);dateText.setGravity(Gravity.CENTER);
        right.addView(dateText,new LinearLayout.LayoutParams(-1,dp(26)));
        LinearLayout.LayoutParams ll=new LinearLayout.LayoutParams(0,-1,1.1f);ll.rightMargin=dp(7);body.addView(left,ll);
        body.addView(right,new LinearLayout.LayoutParams(0,-1,.9f));
        screen.addView(body,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout dock=new LinearLayout(this);dock.setGravity(Gravity.CENTER);
        dock.addView(dockItem("▶","يوتيوب",0xFFB8142B,0xFFFF6378,()->open(YouTubeSearchActivity.class)),dockLp());
        dock.addView(dockItem("⌖","الملاحة",0xFF087C4A,0xFF64E9A3,()->open(NavigationActivity.class)),dockLp());
        dock.addView(dockItem("♫","الموسيقى",0xFF7920A5,0xFFE17BFF,()->open(MusicActivity.class)),dockLp());
        dock.addView(dockItem("ᛒ","بلوتوث",0xFF104EAA,0xFF6BB9FF,this::toggleBluetooth),dockLp());
        dock.addView(dockItem("◉","واي فاي",0xFF087E72,0xFF54EACB,this::toggleWifi),dockLp());
        dock.addView(dockItem("☼","السطوع",0xFF9E5B08,0xFFFFD266,this::brightnessDialog),dockLp());
        screen.addView(dock,new LinearLayout.LayoutParams(-1,dp(82)));
        TextView credit=label("تم تطوير الواجهة بواسطة: علي أحمد",11,0xFFFFDFA1,true);credit.setGravity(Gravity.CENTER);
        screen.addView(credit,new LinearLayout.LayoutParams(-1,dp(21)));
        root.addView(screen,new FrameLayout.LayoutParams(-1,-1));setContentView(root);wirelessState();
    }
    private LinearLayout panel(){LinearLayout p=new LinearLayout(this);p.setPadding(dp(8),dp(6),dp(8),dp(6));p.setBackgroundColor(Color.TRANSPARENT);return p;}
    private LinearLayout.LayoutParams dockLp(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1);p.leftMargin=dp(3);p.rightMargin=dp(3);return p;}
    private View dockItem(String symbol,String title,int c1,int c2,Runnable run){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setBackground(shape(c1,15,c2));
        TextView i=label(symbol,27,Color.WHITE,true);i.setGravity(Gravity.CENTER);i.setShadowLayer(dp(5),0,dp(2),Color.BLACK);
        TextView t=label(title,10,Color.WHITE,true);t.setGravity(Gravity.CENTER);t.setTextDirection(View.TEXT_DIRECTION_RTL);
        card.addView(i,new LinearLayout.LayoutParams(-1,dp(43)));card.addView(t,new LinearLayout.LayoutParams(-1,dp(23)));
        card.setOnClickListener(v->run.run());card.setClickable(true);return card;
    }
    private TextView mediaButton(String symbol,String action){
        TextView v=icon(symbol,0xFF0A4D82,0xFF4EE6FF,40);
        v.setOnClickListener(w->{Intent i=new Intent(this,MusicActivity.class);i.putExtra("ali.music.action",action);startActivity(i);});return v;
    }
    private TextView icon(String value,int color,int stroke,int radius){TextView v=label(value,23,Color.WHITE,true);v.setGravity(Gravity.CENTER);v.setBackground(shape(color,radius,stroke));return v;}
    private TextView label(String s,int z,int c,boolean bold){TextView v=new TextView(this);v.setText(s);v.setTextSize(z);v.setTextColor(c);v.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);return v;}
    private android.graphics.drawable.GradientDrawable shape(int c,int r,int stroke){
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{bright(c),c});
        d.setCornerRadius(dp(r));d.setStroke(dp(1),stroke);return d;
    }
    private int bright(int c){return Color.rgb(Math.min(255,Color.red(c)+27),Math.min(255,Color.green(c)+27),Math.min(255,Color.blue(c)+27));}

    private void settingsMenu(View anchor){
        PopupMenu m=new PopupMenu(this,anchor);
        m.getMenu().add("إعدادات الجهاز").setOnMenuItemClickListener(x->{try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception ignored){}return true;});
        m.getMenu().add("إدارة الجهاز ووضع القيادة المغلق").setOnMenuItemClickListener(x->{open(AdminToolsActivity.class);return true;});m.show();
    }
    private void brightnessDialog(){
        LinearLayout box=new LinearLayout(this);box.setPadding(dp(15),dp(8),dp(15),dp(8));box.setOrientation(LinearLayout.VERTICAL);
        SeekBar bar=new SeekBar(this);bar.setMax(255);bar.setProgress(readBrightness());
        if(Build.VERSION.SDK_INT>=21)bar.setProgressTintList(ColorStateList.valueOf(CYAN));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar b,int v,boolean u){if(u)setBrightness(v);}
            public void onStartTrackingTouch(SeekBar b){} public void onStopTrackingTouch(SeekBar b){}
        });
        box.addView(bar,new LinearLayout.LayoutParams(-1,dp(50)));
        new AlertDialog.Builder(this).setTitle("سطوع الشاشة").setView(box).setPositiveButton("تم",null).show();
    }
    private int readBrightness(){try{return Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,160);}catch(Exception e){return 160;}}
    private void setBrightness(int v){int safe=Math.max(12,Math.min(255,v));WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=safe/255f;getWindow().setAttributes(p);try{Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,safe);}catch(SecurityException ignored){}}
    private void toggleWifi(){try{boolean ok=wifi.setWifiEnabled(!wifi.isWifiEnabled());Toast.makeText(this,ok?"تم إرسال أمر تشغيل أو إيقاف الواي فاي":"تعذر تغيير حالة الواي فاي",Toast.LENGTH_SHORT).show();}catch(Exception e){Toast.makeText(this,"لا يسمح النظام بتغيير حالة الواي فاي",Toast.LENGTH_SHORT).show();}h.postDelayed(this::wirelessState,1300);}
    private void toggleBluetooth(){BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(a==null)return;try{boolean ok=a.isEnabled()?a.disable():a.enable();Toast.makeText(this,ok?"تم إرسال أمر تشغيل أو إيقاف البلوتوث":"تعذر تغيير حالة البلوتوث",Toast.LENGTH_SHORT).show();}catch(Exception e){}h.postDelayed(this::wirelessState,1300);}
    private void wirelessState(){
        if(wifiText!=null){boolean on=wifi!=null&&wifi.isWifiEnabled();wifiText.setText(on?"◉ واي فاي يعمل":"◉ واي فاي متوقف");wifiText.setTextColor(on?0xFF7AF4C1:0xFFFFA2A2);}
        BluetoothAdapter a=BluetoothAdapter.getDefaultAdapter();if(btText!=null){boolean on=a!=null&&a.isEnabled();btText.setText(on?"ᛒ بلوتوث يعمل":"ᛒ بلوتوث متوقف");btText.setTextColor(on?0xFF8BC7FF:0xFFFFA2A2);}
    }
    private void open(Class<?> c){try{startActivity(new Intent(this,c));}catch(Exception e){Toast.makeText(this,"تعذر فتح الشاشة",Toast.LENGTH_SHORT).show();}}
    private void greet(){try{tts=new TextToSpeech(this,s->{if(s==TextToSpeech.SUCCESS&&tts!=null){int a=tts.setLanguage(new Locale("ar"));if(a!=TextToSpeech.LANG_MISSING_DATA&&a!=TextToSpeech.LANG_NOT_SUPPORTED)tts.speak("أهلاً وسهلاً بك سيد علي، بسم الله نبدأ",TextToSpeech.QUEUE_FLUSH,null);}});}catch(Exception ignored){}}

    private void startGps(){
        if(gpsRunning||locations==null)return;
        try{if(!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)){gpsText.setText("فعّل نظام تحديد الموقع");return;}
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,1,this);gpsRunning=true;
        }catch(Exception ignored){}
    }
    private void stopGps(){if(locations!=null&&gpsRunning){try{locations.removeUpdates(this);}catch(Exception ignored){}}gpsRunning=false;lastGps=0;speed=0;}
    @Override public void onLocationChanged(Location l){
        if(l==null||speedView==null)return;
        if(l.hasAccuracy()&&l.getAccuracy()>35)return;
        float v=l.hasSpeed()?l.getSpeed()*3.6f:0;
        if(Float.isNaN(v)||Float.isInfinite(v)||v<0)return;
        if(speed>15&&Math.abs(v-speed)>75)return;
        speed=speed==0?v:speed*.72f+v*.28f;lastGps=SystemClock.elapsedRealtime();speedView.setSpeed(speed);speedView.setGps(true);
        gpsText.setText(l.hasAccuracy()?String.format(new Locale("ar"),"دقة الموقع ±%.0f متر",l.getAccuracy()):"إشارة الموقع مستقرة");
    }
    @Override public void onProviderDisabled(String p){if(LocationManager.GPS_PROVIDER.equals(p)&&gpsText!=null){gpsText.setText("نظام تحديد الموقع متوقف");speedView.setGps(false);}}
    @Override public void onProviderEnabled(String p){}
    @Override public void onStatusChanged(String p,int s,Bundle b){}

    private void showAmbient(){
        if(root==null||ambient!=null||isFinishing())return;
        TextView clock=label(new SimpleDateFormat("hh:mm",Locale.US).format(new Date()),68,0xFF8996A6,true);
        clock.setGravity(Gravity.CENTER);clock.setBackgroundColor(Color.BLACK);
        oldBrightness=getWindow().getAttributes().screenBrightness;
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=.055f;getWindow().setAttributes(p);
        ambient=clock;root.addView(clock,new FrameLayout.LayoutParams(-1,-1));
        clock.setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_DOWN)removeAmbient();return true;});
        Runnable update=new Runnable(){public void run(){if(ambient==clock){clock.setText(new SimpleDateFormat("hh:mm",Locale.US).format(new Date()));clock.postDelayed(this,15000);}}};
        clock.setTag(update);update.run();
    }
    private void removeAmbient(){
        if(ambient==null)return;Object o=ambient.getTag();if(o instanceof Runnable){h.removeCallbacks((Runnable)o);ambient.removeCallbacks((Runnable)o);}
        root.removeView(ambient);ambient=null;WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=oldBrightness;getWindow().setAttributes(p);oldBrightness=-1;
    }
    @Override protected void onResume(){super.onResume();immersive();startGps();h.removeCallbacks(tick);h.post(tick);scheduleIdle();}
    @Override protected void onPause(){h.removeCallbacks(tick);h.removeCallbacks(idle);removeAmbient();stopGps();super.onPause();}
    @Override protected void onDestroy(){h.removeCallbacksAndMessages(null);stopGps();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);if(tts!=null){try{tts.stop();tts.shutdown();}catch(Exception ignored){}}super.onDestroy();}

    private final class SpeedView extends View{
        private final Paint p=new Paint(3);private final RectF a=new RectF();private float value;private boolean hasGps;
        SpeedView(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setSpeed(float v){value=Math.max(0,Math.min(240,v));invalidate();}void setGps(boolean b){hasGps=b;invalidate();}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(getWidth()*.38f,getHeight()*.42f);a.set(x-r,y-r,x+r,y+r);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(7));p.setStrokeCap(Paint.Cap.ROUND);p.setColor(0xFF344C60);c.drawArc(a,135,270,false,p);
            p.setColor(hasGps?CYAN:0xFF788A9C);c.drawArc(a,135,value/240f*270,false,p);p.setStyle(Paint.Style.FILL);p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-light",0));p.setTextSize(Math.min(dp(55),r*.65f));p.setColor(Color.WHITE);p.setShadowLayer(dp(10),0,0,0xFF42CFFF);c.drawText(hasGps?String.valueOf(Math.round(value)):"--",x,y+dp(8),p);
            p.setTypeface(Typeface.DEFAULT_BOLD);p.setTextSize(dp(14));p.setColor(GOLD);c.drawText("km/h",x,y+dp(31),p);
        }
    }
    private final class AnalogClock extends View{
        private final Paint p=new Paint(3);
        AnalogClock(){super(MainActivity.this);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
            p.setStyle(Paint.Style.FILL);p.setShader(new LinearGradient(x-r,y-r,x+r,y+r,0xFF9AAABA,0xFF1D2B38,Shader.TileMode.CLAMP));c.drawCircle(x,y,r+dp(4),p);
            p.setShader(null);p.setColor(0xFFF5F5F0);c.drawCircle(x,y,r,p);
            p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1));p.setColor(0xFF99A4AE);c.drawCircle(x,y,r-dp(3),p);
            for(int i=0;i<60;i++){double a=Math.toRadians(i*6-90);float o=r-dp(9),in=o-(i%5==0?dp(10):dp(4));p.setStrokeWidth(i%5==0?dp(2):dp(1));p.setColor(i%5==0?0xFF17222B:0xFF87929B);c.drawLine(x+(float)Math.cos(a)*in,y+(float)Math.sin(a)*in,x+(float)Math.cos(a)*o,y+(float)Math.sin(a)*o,p);}
            Calendar z=Calendar.getInstance();float sec=z.get(Calendar.SECOND),min=z.get(Calendar.MINUTE)+sec/60f,hr=z.get(Calendar.HOUR)%12+min/60f;
            hand(c,x,y,hr*30,r*.48f,dp(4),0xFF15212D);hand(c,x,y,min*6,r*.70f,dp(2.4f),0xFF15212D);hand(c,x,y,sec*6,r*.78f,dp(1.2f),0xFFD93045);
            p.setStyle(Paint.Style.FILL);p.setColor(0xFF192531);c.drawCircle(x,y,dp(4),p);
        }
        private void hand(Canvas c,float x,float y,float deg,float len,float w,int color){double a=Math.toRadians(deg-90);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(w);p.setStrokeCap(Paint.Cap.ROUND);p.setColor(color);c.drawLine(x,y,x+(float)Math.cos(a)*len,y+(float)Math.sin(a)*len,p);}
    }
}
