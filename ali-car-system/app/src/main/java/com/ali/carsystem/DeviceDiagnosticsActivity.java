package com.ali.carsystem;

import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothProfile;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.AsyncTask;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.provider.Settings;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Locale;

/** Read-only device health report for the Android 5 car tablet. */
public final class DeviceDiagnosticsActivity extends BaseActivity {
    private LinearLayout rows;
    private String latestReport="";
    private TextView offlineShaStatus;

    private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        ScrollView scroll=new ScrollView(this);
        scroll.setBackgroundColor(0xFF07121E);
        LinearLayout page=new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18),dp(14),dp(18),dp(18));
        scroll.addView(page);
        TextView title=new TextView(this);
        title.setTag("device_diagnostics_title");
        title.setText("تشخيص حالة الجهاز");
        title.setTextColor(Color.WHITE);title.setTextSize(23);title.setGravity(Gravity.CENTER);
        page.addView(title,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView subtitle=new TextView(this);
        subtitle.setText("فحص قراءة فقط • لا يغيّر إعدادات النظام ولا يوقف أي خدمة");
        subtitle.setTextColor(0xFF9FB9C8);subtitle.setTextSize(11);subtitle.setGravity(Gravity.CENTER);
        page.addView(subtitle,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh=button("تحديث الفحص");refresh.setTag("device_diagnostics_refresh");
        refresh.setOnClickListener(v->renderReport());
        Button copy=button("نسخ التقرير");copy.setTag("device_diagnostics_copy");
        copy.setOnClickListener(v->copyReport());
        actions.addView(refresh,new LinearLayout.LayoutParams(0,dp(46),1f));
        LinearLayout.LayoutParams copyLp=new LinearLayout.LayoutParams(0,dp(46),1f);
        copyLp.leftMargin=dp(8);actions.addView(copy,copyLp);
        page.addView(actions);
        rows=new LinearLayout(this);rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams rowsLp=new LinearLayout.LayoutParams(-1,-2);rowsLp.topMargin=dp(10);
        page.addView(rows,rowsLp);
        Button locationSettings=button("إعدادات الموقع");locationSettings.setTag("device_diagnostics_location_settings");
        locationSettings.setOnClickListener(v->{try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}catch(Exception e){Toast.makeText(this,"إعدادات الموقع غير متاحة",Toast.LENGTH_SHORT).show();}});
        page.addView(locationSettings,new LinearLayout.LayoutParams(-1,dp(44)));
        Button openNavigation=button("فتح شاشة الملاحة وفحص GPS");openNavigation.setTag("device_diagnostics_open_navigation");
        openNavigation.setOnClickListener(v->startActivity(new Intent(this,NavigationActivity.class)));
        page.addView(openNavigation,new LinearLayout.LayoutParams(-1,dp(44)));
        setContentView(scroll);
        renderReport();
    }

    private Button button(String value){
        Button b=new Button(this);b.setText(value);b.setTextSize(13);
        b.setTextColor(Color.WHITE);b.setAllCaps(false);
        b.setBackground(round(0xFF123248,0xFF39728B));
        return b;
    }

    private GradientDrawable round(int fill,int stroke){
        GradientDrawable d=new GradientDrawable();d.setColor(fill);d.setCornerRadius(dp(12));
        d.setStroke(dp(1),stroke);return d;
    }

    private TextView addRow(String tag,String label,String value,boolean good){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12),dp(8),dp(12),dp(8));card.setBackground(round(0xCC102332,0x663D7188));
        TextView heading=new TextView(this);heading.setText(label);heading.setTextSize(12);
        heading.setTextColor(0xFF8FDFF1);heading.setGravity(Gravity.RIGHT);
        card.addView(heading,new LinearLayout.LayoutParams(-1,dp(20)));
        TextView detail=new TextView(this);detail.setTag(tag);detail.setText(value);
        detail.setTextSize(13);detail.setTextColor(good?0xFFE8F6F8:0xFFFFD18B);
        detail.setGravity(Gravity.RIGHT);detail.setTextIsSelectable(true);
        card.addView(detail,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(7);
        rows.addView(card,lp);
        return detail;
    }

    private String yesNo(boolean value){return value?"مفعّل":"غير مفعّل";}
    private String mb(long bytes){return String.format(Locale.US,"%.0f MB",bytes/(1024d*1024d));}

    private void renderReport(){
        rows.removeAllViews();
        StringBuilder report=new StringBuilder();
        report.append("ALI CAR — Device diagnostics\n");
        report.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        report.append("Android: ").append(Build.VERSION.RELEASE).append(" / API ").append(Build.VERSION.SDK_INT).append("\n");
        addRow("device_diagnostics_android","النظام والجهاز",
                Build.MANUFACTURER+" "+Build.MODEL+" • Android "+Build.VERSION.RELEASE+" • API "+Build.VERSION.SDK_INT,true);

        boolean gps=false,networkProvider=false;String locationLine="لا توجد قراءة موقع حديثة";
        String satelliteLine="الأقمار الصناعية: غير متاحة حالياً";
        int satellitesVisible=0,satellitesUsed=0,ttffMs=0;
        boolean freshLocation=false;
        try{
            LocationManager lm=(LocationManager)getSystemService(LOCATION_SERVICE);
            gps=lm!=null&&lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
            networkProvider=lm!=null&&lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            Location best=null;
            if(lm!=null){
                for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER,LocationManager.PASSIVE_PROVIDER}){
                    try{
                        Location candidate=lm.getLastKnownLocation(provider);
                        if(candidate==null)continue;
                        long age=candidate.getElapsedRealtimeNanos()>0
                                ?(SystemClock.elapsedRealtimeNanos()-candidate.getElapsedRealtimeNanos())/1000000L
                                :System.currentTimeMillis()-candidate.getTime();
                        if(age<0L||age>180000L)continue;
                        if(best==null||candidate.getTime()>best.getTime())best=candidate;
                    }catch(SecurityException ignored){}
                }
                try{
                    GpsStatus gpsStatus=lm.getGpsStatus(null);
                    if(gpsStatus!=null){
                        for(GpsSatellite satellite:gpsStatus.getSatellites()){
                            satellitesVisible++;
                            if(satellite.usedInFix())satellitesUsed++;
                        }
                        ttffMs=gpsStatus.getTimeToFirstFix();
                    }
                }catch(Exception ignored){}
            }
            if(best!=null){
                long age=best.getElapsedRealtimeNanos()>0
                        ?(SystemClock.elapsedRealtimeNanos()-best.getElapsedRealtimeNanos())/1000000L
                        :System.currentTimeMillis()-best.getTime();
                if(age>=0L&&age<=15000L&&best.hasAccuracy()&&best.getAccuracy()<=100f)freshLocation=true;
                locationLine=(best.getProvider()==null?"موقع":best.getProvider())+" • دقة ±"+
                        (best.hasAccuracy()?Math.round(best.getAccuracy())+" متر":"غير معروفة")+" • منذ "+(age/1000)+" ثانية";
            }
            satelliteLine="الأقمار المرئية: "+satellitesVisible+" • المستخدمة في التثبيت: "+satellitesUsed
                    +(ttffMs>0?" • TTFF: "+(ttffMs/1000f)+" ثانية":" • TTFF: لا توجد قراءة متاحة");
        }catch(Exception error){locationLine="تعذر قراءة حالة GPS: "+error.getClass().getSimpleName();}
        addRow("device_diagnostics_gps","الموقع وGPS",
                "GPS: "+(gps?"مفعّل":"متوقف")+" • الشبكة: "+(networkProvider?"مفعّلة":"متوقفة")
                        +" • تثبيت حديث ودقيق: "+yesNo(freshLocation)+"\n"+locationLine+"\n"+satelliteLine,
                freshLocation);
        report.append("GPS enabled: ").append(gps).append(", network provider: ").append(networkProvider)
                .append(", fresh accurate fix: ").append(freshLocation).append(", last fix: ").append(locationLine)
                .append(", ").append(satelliteLine).append("\n");

        BluetoothAdapter bt=null;boolean bluetooth=false,a2dpConnected=false;
        try{
            bt=BluetoothAdapter.getDefaultAdapter();bluetooth=bt!=null&&bt.isEnabled();
            a2dpConnected=bluetooth&&bt.getProfileConnectionState(BluetoothProfile.A2DP)
                    ==BluetoothProfile.STATE_CONNECTED;
        }catch(Exception ignored){}
        addRow("device_diagnostics_bluetooth","البلوتوث",
                bt==null?"لا يوجد محول Bluetooth":"المحول: "+(bluetooth?"مفعّل":"متوقف")
                        +" • اتصال A2DP بالسيارة: "+(a2dpConnected?"متصل":"غير متصل"),
                bt!=null&&bluetooth);
        report.append("Bluetooth: ").append(bt==null?"not available":bluetooth)
                .append(", A2DP connected: ").append(a2dpConnected).append("\n");

        boolean wifi=false,connected=false;
        try{WifiManager wm=(WifiManager)getApplicationContext().getSystemService(WIFI_SERVICE);wifi=wm!=null&&wm.isWifiEnabled();}catch(Exception ignored){}
        try{ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);NetworkInfo ni=cm==null?null:cm.getActiveNetworkInfo();connected=ni!=null&&ni.isConnected();}catch(Exception ignored){}
        addRow("device_diagnostics_network","الاتصال",
                "Wi-Fi: "+yesNo(wifi)+" • اتصال الشبكة: "+yesNo(connected),connected);
        report.append("Wi-Fi: ").append(wifi).append(", network connected: ").append(connected).append("\n");

        AudioManager audio=(AudioManager)getSystemService(AUDIO_SERVICE);
        String audioLine="خدمة الصوت غير متاحة";boolean audioGood=audio!=null;
        if(audio!=null){
            int volume=audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maximum=audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            boolean btRoute=audio.isBluetoothA2dpOn();
            audioLine="صوت الموسيقى: "+volume+"/"+maximum+" • وضع الصوت: "+audio.getMode()
                    +" • مسار الإخراج: "+(btRoute?"Bluetooth A2DP":"مكبر/مسار الجهاز");
        }
        addRow("device_diagnostics_audio","الصوت",
                audioLine,audioGood);
        report.append("Audio: ").append(audioLine).append("\n");

        ActivityManager.MemoryInfo memory=new ActivityManager.MemoryInfo();
        ActivityManager am=(ActivityManager)getSystemService(ACTIVITY_SERVICE);
        if(am!=null)am.getMemoryInfo(memory);
        long totalRam=Build.VERSION.SDK_INT>=16?memory.totalMem:Runtime.getRuntime().maxMemory();
        long availableRam=memory.availMem;long appPss=Debug.getPss()*1024L;
        addRow("device_diagnostics_memory","الذاكرة",
                "RAM: "+mb(totalRam)+" • المتاح: "+mb(availableRam)+" • التطبيق PSS: "+mb(appPss),
                availableRam>64L*1024L*1024L);
        report.append("RAM total: ").append(mb(totalRam)).append(", available: ").append(mb(availableRam))
                .append(", app PSS: ").append(mb(appPss)).append("\n");

        long totalStorage=0,freeStorage=0;
        try{StatFs fs=new StatFs(Environment.getDataDirectory().getPath());totalStorage=fs.getTotalBytes();freeStorage=fs.getAvailableBytes();}catch(Exception ignored){}
        addRow("device_diagnostics_storage","التخزين الداخلي",
                "المتاح: "+mb(freeStorage)+" من "+mb(totalStorage),freeStorage>200L*1024L*1024L);
        report.append("Storage free: ").append(mb(freeStorage)).append(" / ").append(mb(totalStorage)).append("\n");

        Intent battery=null;try{battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));}catch(Exception ignored){}
        String batteryLine="غير متاح";boolean batteryGood=true;
        if(battery!=null){
            int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
            int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
            int pct=level>=0&&scale>0?Math.round(level*100f/scale):-1;
            int plugged=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
            batteryLine=(pct>=0?pct+"%":"غير معروف")+" • "+(plugged==0?"غير موصول بالشاحن":"موصول بالشاحن");
            batteryGood=pct<0||pct>10;
        }
        addRow("device_diagnostics_battery","البطارية",batteryLine,batteryGood);
        report.append("Battery: ").append(batteryLine).append("\n");

        String offline=OfflineMapsManager.status(this);
        boolean mapReady=OfflineMapsManager.getMapFile(this)!=null;
        boolean placesReady=OfflineMapsManager.getPlacesDatabase(this)!=null;
        boolean routingReady=OfflineMapsManager.hasOfflineRouting(this);
        boolean manifestReady=OfflineMapsManager.hasValidManifest(OfflineMapsManager.getRoot(this));
        long requiredMapBytes=OfflineMapsManager.requiredBundleBytes(this);
        long availableMapBytes=OfflineMapsManager.availableStorageBytes(this);
        boolean offlineGood=mapReady&&placesReady&&routingReady&&manifestReady;
        String mapLine="ملف الخريطة: "+yesNo(mapReady)+" • قاعدة الأماكن: "+yesNo(placesReady)
                +" • BRouter: "+yesNo(routingReady)+" • manifest والأحجام: "+yesNo(manifestReady)
                +" • مساحة الحزمة: "+mb(requiredMapBytes)+" • المتاح: "+mb(availableMapBytes);
        addRow("device_diagnostics_offline_maps","الخرائط دون إنترنت",mapLine,offlineGood);
        offlineShaStatus=addRow("device_diagnostics_offline_sha256","سلامة محتوى الخرائط",
                "جارٍ التحقق من SHA-256 في الخلفية...",false);
        report.append("Offline maps: ").append(offline).append("\n")
                .append("Map package required: ").append(mb(requiredMapBytes))
                .append(", available: ").append(mb(availableMapBytes))
                .append(", manifest/size valid: ").append(manifestReady)
                .append(", SHA-256: verification running").append("\n");
        final String integrityPrefix="Offline maps SHA-256: ";
        new AsyncTask<Void,Void,Boolean>(){
            @Override protected Boolean doInBackground(Void... ignored){
                return OfflineMapsManager.verifyInstalledBundle(getApplicationContext());
            }
            @Override protected void onPostExecute(Boolean verified){
                if(isFinishing()||offlineShaStatus==null)return;
                String line=Boolean.TRUE.equals(verified)
                        ?"SHA-256 لجميع ملفات الخريطة والأماكن والتوجيه مطابق"
                        :"فشل SHA-256 أو الحزمة غير مثبتة بالكامل — أعد التحقق أو تثبيت الخرائط";
                offlineShaStatus.setText(line);
                offlineShaStatus.setTextColor(Boolean.TRUE.equals(verified)?0xFFE8F6F8:0xFFFFD18B);
                latestReport=latestReport+"\n"+integrityPrefix+verified;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        boolean mapThemeReady=false;
        try(java.io.InputStream theme=getAssets().open("mapsforge/ali-car-dark.xml")){
            mapThemeReady=theme.read()>=0;
        }catch(Exception ignored){}
        addRow("device_diagnostics_map_renderer","نمط عرض الخريطة",
                mapThemeReady?"ملف نمط MapsForge موجود؛ اختبار الرسم الفعلي يتم داخل شاشة الملاحة":"ملف نمط MapsForge غير موجود",
                mapThemeReady);
        report.append("MapsForge theme asset: ").append(mapThemeReady)
                .append(" (actual tile rendering is validated by navigation screen instrumentation)\n");

        Intent browserIntent=new Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://m.youtube.com"));
        boolean browser=getPackageManager().resolveActivity(browserIntent,0)!=null;
        addRow("device_diagnostics_browser","المتصفح وYouTube Lite",
                browser?"يوجد تطبيق قادر على فتح موقع YouTube للموبايل":"لا يوجد متصفح متاح",browser);
        report.append("Browser for m.youtube.com: ").append(browser).append("\n");
        latestReport=report.toString();
    }

    private void copyReport(){
        if(latestReport==null||latestReport.isEmpty())renderReport();
        ClipboardManager clipboard=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(clipboard==null){Toast.makeText(this,"تعذر الوصول إلى الحافظة",Toast.LENGTH_SHORT).show();return;}
        clipboard.setPrimaryClip(ClipData.newPlainText("ALI CAR device diagnostics",latestReport));
        Toast.makeText(this,"تم نسخ تقرير التشخيص",Toast.LENGTH_SHORT).show();
    }
}
