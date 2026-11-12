package com.ali.carsystem;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
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
    private static final int CYAN=0xFF39D9FF, GOLD=0xFFFFD36A;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private LocationManager locations;
    private WifiManager wifi;
    private SpeedView speedView;
    private TextView digitalClock, dateText, gpsText;
    private View brightnessPill, ambient;
    private SeekBar brightnessBar;
    private TextToSpeech tts;
    private boolean gpsRunning;
    private float filteredSpeed;
    private long lastGps;
    private float savedBrightness=-1f;

    private final Runnable clockTick=new Runnable(){@Override public void run(){
        Date now=new Date();
        String time=new SimpleDateFormat("hh:mm",Locale.US).format(now);
        if(digitalClock!=null)digitalClock.setText(time);
        if(dateText!=null)dateText.setText(new SimpleDateFormat("EEEE, dd MMM yyyy",Locale.US).format(now));
        if(speedView!=null&&lastGps!=0&&SystemClock.elapsedRealtime()-lastGps>7000){
            speedView.setSpeed(0);speedView.setGps(false);filteredSpeed=0;
            if(gpsText!=null)gpsText.setText("بانتظار إشارة الموقع");
        }
        handler.postDelayed(this,1000);
    }};
    private final Runnable idleTimer=()->showAmbientClock();
    private final Runnable hideBrightness=()->dismissBrightnessPill();

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        immersive();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        locations=(LocationManager)getSystemService(LOCATION_SERVICE);
        wifi=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);
        buildDashboard();
        speakWelcome();
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void immersive(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}
    @Override public void onUserInteraction(){super.onUserInteraction();dismissAmbientClock();handler.removeCallbacks(idleTimer);handler.postDelayed(idleTimer,90000);}

    private void buildDashboard(){
        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);
        ImageView background=new ImageView(this);background.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try(InputStream input=getAssets().open("dashboard.webp")){
            BitmapFactory.Options options=new BitmapFactory.Options();
            options.inPreferredConfig=Bitmap.Config.RGB_565;options.inDither=true;options.inScaled=false;
            background.setImageBitmap(BitmapFactory.decodeStream(input,null,options));
        }catch(Exception ignored){background.setBackgroundColor(0xFF06101B);}
        root.addView(background,new FrameLayout.LayoutParams(-1,-1));

        // Header: brand at top-left and the only top-right action is device settings.
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(24),dp(6),dp(22),0);
        TextView brand=label("ALI CAR",28,Color.WHITE,true);
        brand.setLetterSpacing(.14f);brand.setShadowLayer(dp(8),0,0,CYAN);
        header.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1f));
        TextView settings=iconBubble("⚙",0xFF176AAB,0xFF76DFFF,46);
        settings.setOnClickListener(v->openDeviceSettings(v));
        header.addView(settings,new LinearLayout.LayoutParams(dp(46),dp(46)));
        FrameLayout.LayoutParams headerLp=new FrameLayout.LayoutParams(-1,dp(58),Gravity.TOP);
        root.addView(header,headerLp);

        // The middle is transparent; it deliberately leaves the car in the background visible.
        LinearLayout content=new LinearLayout(this);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        content.setPadding(dp(26),0,dp(26),0);
        content.setBackgroundColor(Color.TRANSPARENT);

        LinearLayout left=new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setGravity(Gravity.CENTER);
        left.setBackgroundColor(Color.TRANSPARENT);
        TextView speedCaption=label("السرعة",15,0xFFD8F4FF,true);speedCaption.setGravity(Gravity.CENTER);
        left.addView(speedCaption,new LinearLayout.LayoutParams(-1,dp(28)));
        speedView=new SpeedView(this);
        left.addView(speedView,new LinearLayout.LayoutParams(-1,dp(112)));
        gpsText=label("بانتظار إشارة الموقع",11,0xFFE2F5FF,false);gpsText.setGravity(Gravity.CENTER);
        left.addView(gpsText,new LinearLayout.LayoutParams(-1,dp(22)));

        LinearLayout media=new LinearLayout(this);media.setGravity(Gravity.CENTER);
        media.addView(mediaControl("⏮","previous"),new LinearLayout.LayoutParams(dp(54),dp(50)));
        LinearLayout.LayoutParams playLp=new LinearLayout.LayoutParams(dp(62),dp(54));playLp.leftMargin=dp(8);playLp.rightMargin=dp(8);
        media.addView(mediaControl("▶","play"),playLp);
        media.addView(mediaControl("⏭","next"),new LinearLayout.LayoutParams(dp(54),dp(50)));
        left.addView(media,new LinearLayout.LayoutParams(-1,dp(58)));

        View spacer=new View(this);
        LinearLayout right=new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setGravity(Gravity.CENTER);
        right.setBackgroundColor(Color.TRANSPARENT);
        digitalClock=label("--:--",52,Color.WHITE,true);
        digitalClock.setGravity(Gravity.CENTER);
        digitalClock.setShadowLayer(dp(12),0,0,0xFF9BDFFF);
        right.addView(digitalClock,new LinearLayout.LayoutParams(-1,dp(66)));
        right.addView(new AnalogClock(this),new LinearLayout.LayoutParams(dp(160),dp(160)));
        dateText=label("",14,Color.WHITE,true);dateText.setGravity(Gravity.CENTER);
        dateText.setShadowLayer(dp(5),0,0,Color.BLACK);
        right.addView(dateText,new LinearLayout.LayoutParams(-1,dp(30)));

        content.addView(left,new LinearLayout.LayoutParams(0,-2,0.39f));
        content.addView(spacer,new LinearLayout.LayoutParams(0,1,0.22f));
        content.addView(right,new LinearLayout.LayoutParams(0,-2,0.39f));
        FrameLayout.LayoutParams contentLp=new FrameLayout.LayoutParams(-1,-1);
        contentLp.topMargin=dp(58);contentLp.bottomMargin=dp(142);
        root.addView(content,contentLp);

        // Only the six requested dock actions; glossy icons sit directly over the road image.
        LinearLayout dock=new LinearLayout(this);
        dock.setGravity(Gravity.CENTER);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setPadding(dp(18),dp(4),dp(18),dp(2));
        dock.addView(dockItem("▶","يوتيوب",0xFFB52235,0xFFFF7887,()->openActivity(YouTubeSearchActivity.class)),dockParams());
        dock.addView(dockItem("⌖","الملاحة",0xFF13754F,0xFF79E9B0,()->openActivity(NavigationActivity.class)),dockParams());
        dock.addView(dockItem("♫","الموسيقى",0xFF7727A7,0xFFE48CFF,()->openActivity(MusicActivity.class)),dockParams());
        dock.addView(dockItem("ᛒ","بلوتوث",0xFF15569E,0xFF7CCBFF,this::toggleBluetooth),dockParams());
        dock.addView(dockItem("◉","واي فاي",0xFF087B70,0xFF74F3D6,this::toggleWifi),dockParams());
        dock.addView(dockItem("☼","السطوع",0xFFA9680B,0xFFFFE18B,this::showBrightnessPill),dockParams());
        FrameLayout.LayoutParams dockLp=new FrameLayout.LayoutParams(-1,dp(92),Gravity.BOTTOM);
        dockLp.bottomMargin=dp(23);
        root.addView(dock,dockLp);

        TextView credit=label("تم تطوير الواجهة بواسطة : ali ahmad",11,0xFFF6E7CD,true);
        credit.setGravity(Gravity.CENTER);
        credit.setShadowLayer(dp(4),0,dp(1),Color.BLACK);
        FrameLayout.LayoutParams creditLp=new FrameLayout.LayoutParams(-1,dp(20),Gravity.BOTTOM);
        creditLp.bottomMargin=dp(2);root.addView(credit,creditLp);
        setContentView(root);
    }

    private LinearLayout.LayoutParams dockParams(){
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-1,1f);
        p.leftMargin=dp(3);p.rightMargin=dp(3);return p;
    }
    private View dockItem(String symbol,String caption,int top,int bottom,Runnable action){
        LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setGravity(Gravity.CENTER);
        TextView icon=iconBubble(symbol,top,bottom,54);
        TextView title=label(caption,10,Color.WHITE,true);title.setGravity(Gravity.CENTER);title.setShadowLayer(dp(4),0,dp(1),Color.BLACK);
        item.addView(icon,new LinearLayout.LayoutParams(dp(60),dp(58)));
        item.addView(title,new LinearLayout.LayoutParams(-1,dp(23)));
        item.setOnClickListener(v->action.run());item.setClickable(true);return item;
    }
    private TextView iconBubble(String symbol,int top,int bottom,int size){
        TextView icon=label(symbol,size>=50?25:22,Color.WHITE,true);icon.setGravity(Gravity.CENTER);
        GradientDrawable bg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{bottom,top,0xFF101B2B});
        bg.setShape(GradientDrawable.OVAL);bg.setStroke(dp(1),0xAFFFFFFF);icon.setBackground(bg);
        icon.setElevation(dp(5));icon.setShadowLayer(dp(5),0,dp(3),Color.BLACK);return icon;
    }
    private TextView mediaControl(String symbol,String action){
        TextView button=iconBubble(symbol,0xFF123C69,0xFF70DFFF,48);
        button.setOnClickListener(v->{Intent intent=new Intent(this,MusicActivity.class);intent.putExtra("ali.music.action",action);startActivity(intent);});
        return button;
    }
    private TextView label(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(Typeface.create("sans-serif",bold?Typeface.BOLD:Typeface.NORMAL));
        t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;
    }

    private void openDeviceSettings(View anchor){
        PopupMenu menu=new PopupMenu(this,anchor);
        menu.getMenu().add("إعدادات الجهاز").setOnMenuItemClickListener(item->{
            try{startActivity(new Intent(Settings.ACTION_SETTINGS));}catch(Exception e){toast("تعذر فتح إعدادات الجهاز");}return true;});
        menu.getMenu().add("إدارة الجهاز ووضع القيادة").setOnMenuItemClickListener(item->{
            openActivity(AdminToolsActivity.class);return true;});
        menu.show();
    }
    private void showBrightnessPill(){
        dismissBrightnessPill();
        LinearLayout pill=new LinearLayout(this);pill.setGravity(Gravity.CENTER_VERTICAL);pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setPadding(dp(18),dp(12),dp(18),dp(12));
        GradientDrawable glass=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE91A3550,0xE9162337,0xE91A3550});
        glass.setCornerRadius(dp(32));glass.setStroke(dp(1),0xAA73DFFF);pill.setBackground(glass);pill.setElevation(dp(12));
        TextView sun=label("☼",30,0xFFFFD56F,true);sun.setGravity(Gravity.CENTER);
        pill.addView(sun,new LinearLayout.LayoutParams(dp(48),dp(48)));
        brightnessBar=new SeekBar(this);brightnessBar.setMax(255);brightnessBar.setProgress(readBrightness());
        if(Build.VERSION.SDK_INT>=21){
            brightnessBar.setProgressTintList(ColorStateList.valueOf(0xFF54E4FF));
            brightnessBar.setThumbTintList(ColorStateList.valueOf(0xFFFFD56F));
        }
        LinearLayout.LayoutParams barLp=new LinearLayout.LayoutParams(dp(260),dp(48));barLp.leftMargin=dp(10);
        pill.addView(brightnessBar,barLp);
        brightnessBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            @Override public void onProgressChanged(SeekBar bar,int value,boolean fromUser){if(fromUser){setBrightness(value);resetBrightnessTimer();}}
            @Override public void onStartTrackingTouch(SeekBar bar){handler.removeCallbacks(hideBrightness);}
            @Override public void onStopTrackingTouch(SeekBar bar){resetBrightnessTimer();}
        });
        brightnessBar.setOnTouchListener((v,event)->{
            if(event.getAction()==MotionEvent.ACTION_DOWN||event.getAction()==MotionEvent.ACTION_MOVE)handler.removeCallbacks(hideBrightness);
            if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL)resetBrightnessTimer();
            return false;
        });
        brightnessPill=pill;
        FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-2,dp(76),Gravity.CENTER);
        root.addView(pill,lp);pill.bringToFront();resetBrightnessTimer();
    }
    private void resetBrightnessTimer(){handler.removeCallbacks(hideBrightness);handler.postDelayed(hideBrightness,3000);}
    private void dismissBrightnessPill(){
        handler.removeCallbacks(hideBrightness);
        if(brightnessPill!=null&&root!=null){root.removeView(brightnessPill);brightnessPill=null;brightnessBar=null;}
    }
    private int readBrightness(){
        try{return Settings.System.getInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,160);}
        catch(Exception e){return 160;}
    }
    private void setBrightness(int value){
        int safe=Math.max(10,Math.min(255,value));
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=safe/255f;getWindow().setAttributes(p);
        try{
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS_MODE,Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(),Settings.System.SCREEN_BRIGHTNESS,safe);
        }catch(SecurityException ignored){}
    }
    private void toggleWifi(){
        try{boolean ok=wifi!=null&&wifi.setWifiEnabled(!wifi.isWifiEnabled());toast(ok?"تم إرسال أمر تغيير الواي فاي":"تعذر تغيير حالة الواي فاي");}
        catch(Exception e){toast("لا يسمح النظام بتغيير حالة الواي فاي");}
    }
    private void toggleBluetooth(){
        BluetoothAdapter adapter=BluetoothAdapter.getDefaultAdapter();if(adapter==null){toast("البلوتوث غير متاح");return;}
        try{boolean ok=adapter.isEnabled()?adapter.disable():adapter.enable();toast(ok?"تم إرسال أمر تغيير البلوتوث":"تعذر تغيير حالة البلوتوث");}
        catch(Exception e){toast("لا يسمح النظام بتغيير حالة البلوتوث");}
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    private void openActivity(Class<?> target){
        try{startActivity(new Intent(this,target));}catch(Exception e){toast("تعذر فتح الشاشة المطلوبة");}
    }
    private void speakWelcome(){
        try{tts=new TextToSpeech(this,status->{
            if(status==TextToSpeech.SUCCESS&&tts!=null){
                int result=tts.setLanguage(new Locale("ar"));
                if(result!=TextToSpeech.LANG_MISSING_DATA&&result!=TextToSpeech.LANG_NOT_SUPPORTED)
                    tts.speak("أهلاً وسهلاً بك سيد علي، بسم الله نبدأ",TextToSpeech.QUEUE_FLUSH,null);
            }
        });}catch(Exception ignored){}
    }

    private void startGps(){
        if(gpsRunning||locations==null)return;
        try{
            if(!locations.isProviderEnabled(LocationManager.GPS_PROVIDER)){if(gpsText!=null)gpsText.setText("فعّل نظام تحديد الموقع");return;}
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,1f,this);gpsRunning=true;
        }catch(SecurityException|IllegalArgumentException ignored){}
    }
    private void stopGps(){
        if(locations!=null&&gpsRunning){try{locations.removeUpdates(this);}catch(Exception ignored){}}
        gpsRunning=false;lastGps=0;filteredSpeed=0;
    }
    @Override public void onLocationChanged(Location location){
        if(location==null||speedView==null)return;
        if(location.hasAccuracy()&&location.getAccuracy()>35f)return;
        float value=location.hasSpeed()?location.getSpeed()*3.6f:0f;
        if(Float.isNaN(value)||Float.isInfinite(value)||value<0)return;
        if(filteredSpeed>15f&&Math.abs(value-filteredSpeed)>75f)return;
        filteredSpeed=filteredSpeed==0f?value:filteredSpeed*.72f+value*.28f;
        lastGps=SystemClock.elapsedRealtime();speedView.setSpeed(filteredSpeed);speedView.setGps(true);
        if(gpsText!=null)gpsText.setText(location.hasAccuracy()?String.format(new Locale("ar"),"دقة الموقع ±%.0f متر",location.getAccuracy()):"إشارة الموقع مستقرة");
    }
    @Override public void onProviderDisabled(String provider){
        if(LocationManager.GPS_PROVIDER.equals(provider)){if(gpsText!=null)gpsText.setText("نظام تحديد الموقع متوقف");if(speedView!=null)speedView.setGps(false);}
    }
    @Override public void onProviderEnabled(String provider){}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    private void showAmbientClock(){
        if(root==null||ambient!=null||isFinishing())return;
        TextView clock=label(new SimpleDateFormat("hh:mm",Locale.US).format(new Date()),72,0xFF8795A6,true);
        clock.setGravity(Gravity.CENTER);clock.setBackgroundColor(Color.BLACK);
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=.045f;getWindow().setAttributes(p);
        ambient=clock;root.addView(clock,new FrameLayout.LayoutParams(-1,-1));
        clock.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN)dismissAmbientClock();return true;});
        Runnable update=new Runnable(){@Override public void run(){if(ambient==clock){clock.setText(new SimpleDateFormat("hh:mm",Locale.US).format(new Date()));clock.postDelayed(this,15000);}}};
        clock.setTag(update);update.run();
    }
    private void dismissAmbientClock(){
        if(ambient==null)return;
        Object tag=ambient.getTag();if(tag instanceof Runnable){handler.removeCallbacks((Runnable)tag);ambient.removeCallbacks((Runnable)tag);}
        root.removeView(ambient);ambient=null;
        WindowManager.LayoutParams p=getWindow().getAttributes();p.screenBrightness=-1f;getWindow().setAttributes(p);
    }

    @Override protected void onResume(){
        super.onResume();immersive();startGps();
        handler.removeCallbacks(clockTick);handler.post(clockTick);
        handler.removeCallbacks(idleTimer);handler.postDelayed(idleTimer,90000);
    }
    @Override protected void onPause(){
        handler.removeCallbacks(clockTick);handler.removeCallbacks(idleTimer);handler.removeCallbacks(hideBrightness);
        dismissBrightnessPill();dismissAmbientClock();stopGps();super.onPause();
    }
    @Override protected void onDestroy(){
        handler.removeCallbacksAndMessages(null);stopGps();getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(tts!=null){try{tts.stop();tts.shutdown();}catch(Exception ignored){}}
        super.onDestroy();
    }

    private final class SpeedView extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float speed;private boolean gps;
        SpeedView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        void setSpeed(float value){speed=Math.max(0,Math.min(260,value));invalidate();}
        void setGps(boolean value){gps=value;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float center=getWidth()/2f,mid=getHeight()/2f;
            paint.setTextAlign(Paint.Align.CENTER);paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
            paint.setTextSize(Math.min(dp(74),getWidth()*.37f));paint.setColor(Color.WHITE);
            paint.setShadowLayer(dp(14),0,0,0xFF61DFFF);
            canvas.drawText(gps?String.format(Locale.US,"%.0f",speed):"--",center,mid+dp(19),paint);
            paint.clearShadowLayer();paint.setTextSize(dp(17));paint.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
            paint.setColor(0xFFE8F7FF);canvas.drawText("km/h",center,mid+dp(47),paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(2));paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(gps?0xFF6ADFFF:0xFF5A7188);
            float width=Math.min(dp(160),getWidth()*.70f);canvas.drawLine(center-width/2,mid+dp(65),center+width/2,mid+dp(65),paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }
    private final class AnalogClock extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        AnalogClock(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(getWidth(),getHeight())*.46f;
            paint.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(cx-r,cy-r,cx+r,cy+r,0xFFDDE7EF,0xFF596B7A,Shader.TileMode.CLAMP));
            canvas.drawCircle(cx,cy,r+dp(5),paint);paint.setShader(null);paint.setColor(0xFFF9FBFC);canvas.drawCircle(cx,cy,r,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0xFF9AA7B3);canvas.drawCircle(cx,cy,r-dp(3),paint);
            for(int i=0;i<60;i++){double angle=Math.toRadians(i*6-90);float outer=r-dp(8),inner=outer-(i%5==0?dp(9):dp(4));
                paint.setStrokeWidth(i%5==0?dp(2):dp(1));paint.setColor(i%5==0?0xFF233340:0xFF9AA7B3);
                canvas.drawLine(cx+(float)Math.cos(angle)*inner,cy+(float)Math.sin(angle)*inner,cx+(float)Math.cos(angle)*outer,cy+(float)Math.sin(angle)*outer,paint);}
            Calendar c=Calendar.getInstance();float sec=c.get(Calendar.SECOND),min=c.get(Calendar.MINUTE)+sec/60f,hr=c.get(Calendar.HOUR)%12+min/60f;
            drawHand(canvas,cx,cy,hr*30,r*.48f,dp(4),0xFF15232E);
            drawHand(canvas,cx,cy,min*6,r*.69f,dp(2.5f),0xFF15232E);
            drawHand(canvas,cx,cy,sec*6,r*.78f,dp(1.2f),0xFFC83A46);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xFF1C2A34);canvas.drawCircle(cx,cy,dp(4),paint);
        }
        private void drawHand(Canvas canvas,float x,float y,float degree,float length,float width,int color){
            double a=Math.toRadians(degree-90);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(width);paint.setStrokeCap(Paint.Cap.ROUND);paint.setColor(color);
            canvas.drawLine(x,y,x+(float)Math.cos(a)*length,y+(float)Math.sin(a)*length,paint);
        }
    }
}
