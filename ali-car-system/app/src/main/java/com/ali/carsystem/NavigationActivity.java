package com.ali.carsystem;

import android.content.*;
import android.location.*;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.*;
import org.osmdroid.views.*;
import org.osmdroid.views.overlay.*;
import java.net.URLEncoder;
import java.util.*;
import java.io.*;

public final class NavigationActivity extends BaseActivity implements LocationListener {
    private MapView map;private LocationManager lm;private Location current;
    private EditText search;private TextView status,maneuver;
    private GeoPoint destination;private String destinationName="";private Polyline routeLine;private Marker destMarker,carMarker;
    private AsyncTask<Void,Void,RouteResult> routeTask;private AsyncTask<String,Void,GeoResult> geocodeTask;
    private List<Maneuver> activeManeuvers=Collections.emptyList();
    private long lastSearch;private boolean waitingForFix;
    private boolean gpsRegistered,networkRegistered,criteriaRegistered,gpsStatusRegistered,gpsEngineStarted;
    private String providerError="",criteriaProvider="لم يتم اختياره",lastFixProvider="لا يوجد";
    private String gpsProviderStatus="غير معروف",networkProviderStatus="غير معروف",assistanceResult="لم يتم طلب تحديث A-GPS";
    private int satellitesVisible,satellitesUsed,timeToFirstFixMs=-1;
    private float lastFixAccuracy=-1f;
    private long lastFixElapsedNanos;
    private final Handler locationHandler=new Handler(Looper.getMainLooper());
    private final Runnable locationStatusTicker=new Runnable(){
        @Override public void run(){
            if(status!=null&&destination!=null&&waitingForFix)status.setText(gpsStatusText());
            locationHandler.postDelayed(this,5000);
        }
    };
    private final GpsStatus.Listener gpsStatusListener=new GpsStatus.Listener(){
        @Override public void onGpsStatusChanged(int event){
            if(lm==null)return;
            if(event==GpsStatus.GPS_EVENT_STARTED)gpsEngineStarted=true;
            if(event==GpsStatus.GPS_EVENT_STOPPED){gpsEngineStarted=false;satellitesVisible=0;satellitesUsed=0;}
            if(event==GpsStatus.GPS_EVENT_SATELLITE_STATUS||event==GpsStatus.GPS_EVENT_FIRST_FIX){
                try{
                    GpsStatus gps=lm.getGpsStatus(null);int visible=0,used=0;
                    if(gps!=null){
                        for(GpsSatellite satellite:gps.getSatellites()){
                            visible++;if(satellite.usedInFix())used++;
                        }
                        if(event==GpsStatus.GPS_EVENT_FIRST_FIX)timeToFirstFixMs=gps.getTimeToFirstFix();
                    }
                    satellitesVisible=visible;satellitesUsed=used;
                    if(status!=null&&destination!=null&&waitingForFix)status.setText(gpsStatusText());
                }catch(SecurityException ignored){}
            }
        }
    };
    private int dp(int x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    @Override protected void onCreate(Bundle b){super.onCreate(b);
        Configuration.getInstance().load(this,getSharedPreferences("osmdroid",MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("ALI-CAR/3.0 (Android 5; https://github.com/alialahmad9899/ROOM)");
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(8L*1024*1024);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(4L*1024*1024);
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF07111C);
        map=new MapView(this);map.setTileSource(TileSourceFactory.MAPNIK);map.setMultiTouchControls(true);map.setTilesScaledToDpi(true);
        map.getController().setZoom(12.5);root.addView(map,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(8),dp(10),dp(8));
        panel.setBackgroundColor(0xDD061522);
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(0xFF111111);search.setHintTextColor(0xFF666666);search.setHint("ابحث عن وجهتك");search.setBackgroundColor(-1);line.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        Button go=new Button(this);go.setText("اذهب");line.addView(go,new LinearLayout.LayoutParams(dp(78),dp(48)));panel.addView(line);
        status=new TextView(this);status.setTextColor(-1);status.setTextSize(13);status.setText("جارٍ تحديد موقعك...");panel.addView(status);
        maneuver=new TextView(this);maneuver.setTextColor(0xFF83EFFF);maneuver.setTextSize(15);maneuver.setPadding(0,dp(4),0,0);panel.addView(maneuver);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);root.addView(panel,pp);
        TextView attribution=new TextView(this);attribution.setText("© OpenStreetMap contributors");attribution.setTextColor(-1);attribution.setTextSize(10);attribution.setBackgroundColor(0xAA061522);attribution.setPadding(dp(7),dp(4),dp(7),dp(4));
        FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);root.addView(attribution,ap);
        setContentView(root);go.setOnClickListener(v->searchDestination());search.setOnEditorActionListener((v,a,e)->{searchDestination();return true;});
        loadLastKnown();if(current!=null)showCar(current);status.setOnClickListener(v->showGpsDiagnostics());
    }
    private void searchDestination(){
        String q=search.getText().toString().trim();if(q.length()<2){search.setError("أدخل اسم وجهة");return;}
        long now=SystemClock.elapsedRealtime();if(now-lastSearch<1100){status.setText("انتظر لحظة بين طلبات البحث");return;}lastSearch=now;
        if(geocodeTask!=null)geocodeTask.cancel(true);if(routeTask!=null)routeTask.cancel(true);
        status.setText("جارٍ البحث عن الوجهة...");
        geocodeTask=new AsyncTask<String,Void,GeoResult>(){
            protected GeoResult doInBackground(String...v){try{
                String u="https://nominatim.openstreetmap.org/search?q="+URLEncoder.encode(v[0],"UTF-8")+"&format=jsonv2&limit=1&accept-language=ar,en";
                JSONArray a=new JSONArray(VideoApi.read(getApplicationContext(),u));if(a.length()==0)return new GeoResult("لم يتم العثور على الوجهة");
                JSONObject x=a.getJSONObject(0);return new GeoResult(new GeoPoint(x.getDouble("lat"),x.getDouble("lon")),x.optString("display_name","الوجهة"));
            }catch(Exception e){return new GeoResult(e.getMessage());}}
            protected void onPostExecute(GeoResult r){if(isFinishing())return;if(r.error!=null){status.setText(r.error);return;}destination=r.point;destinationName=r.name;showDestination();drawRouteIfPossible();}
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,q);
    }
    private boolean hasLocationPermission(){
        return checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED||
               checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    private boolean hasFineLocationPermission(){
        return checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    private boolean isFreshLocation(Location l,long maxAgeMs){
        if(l==null)return false;
        long fixElapsed=l.getElapsedRealtimeNanos(),nowElapsed=SystemClock.elapsedRealtimeNanos();
        if(fixElapsed>0)return GeoMath.isFreshElapsedFix(fixElapsed,nowElapsed,maxAgeMs);
        return GeoMath.isUsableFix(l.getTime(),System.currentTimeMillis(),maxAgeMs,0);
    }
    private boolean isNavigationFixUsable(Location l){
        return isFreshLocation(l,180000)&&l.hasAccuracy()&&GeoMath.isAccuracyUsable(l.getAccuracy(),250f);
    }
    private void loadLastKnown(){
        if(lm==null)return;
        Location best=null;
        ArrayList<String> providers=new ArrayList<>();
        providers.add(LocationManager.GPS_PROVIDER);
        providers.add(LocationManager.NETWORK_PROVIDER);
        providers.add(LocationManager.PASSIVE_PROVIDER);
        if(criteriaProvider!=null&&!criteriaProvider.isEmpty()&&!providers.contains(criteriaProvider))providers.add(criteriaProvider);
        for(String provider:providers)try{
            if(!lm.getAllProviders().contains(provider))continue;
            Location l=lm.getLastKnownLocation(provider);
            if(l==null||!isFreshLocation(l,300000))continue;
            float acc=l.hasAccuracy()?l.getAccuracy():999f;
            if(acc>350f)continue;
            boolean newer=best==null;
            if(best!=null){
                long candidateElapsed=l.getElapsedRealtimeNanos(),bestElapsed=best.getElapsedRealtimeNanos();
                newer=candidateElapsed>0&&bestElapsed>0?candidateElapsed>bestElapsed:l.getTime()>best.getTime();
            }
            if(newer)best=l;
        }catch(SecurityException e){providerError="صلاحية الموقع مرفوضة من النظام";}catch(Exception ignored){}
        if(best!=null){
            current=new Location(best);lastFixProvider=best.getProvider();
            lastFixAccuracy=best.hasAccuracy()?best.getAccuracy():-1f;
            lastFixElapsedNanos=best.getElapsedRealtimeNanos();
        }
    }
    private void openLocationSettings(){
        try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}
        catch(Exception e){status.setText("افتح إعدادات الجهاز ثم الموقع");}
    }
    private void requestLocationSources(){
        gpsRegistered=false;networkRegistered=false;criteriaRegistered=false;providerError="";
        if(lm==null){status.setText("خدمة الموقع غير متاحة في نظام Android");return;}
        if(!hasLocationPermission()){status.setText("إذن الموقع غير ممنوح للتطبيق");return;}
        boolean gpsOn=false,networkOn=false,passiveAvailable=false;
        try{gpsOn=lm.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}
        try{networkOn=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}catch(Exception ignored){}
        try{passiveAvailable=lm.getAllProviders().contains(LocationManager.PASSIVE_PROVIDER);}catch(Exception ignored){}

        if(gpsOn&&!gpsStatusRegistered)try{gpsStatusRegistered=lm.addGpsStatusListener(gpsStatusListener);}
            catch(SecurityException e){providerError="رفض النظام صلاحية GPS Status";}catch(Exception e){providerError="تعذر مراقبة حالة GPS";}
        if(gpsOn)try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);gpsRegistered=true;}
            catch(SecurityException e){providerError="رفض النظام إذن GPS الدقيق";}catch(Exception e){providerError="تعذر تسجيل GPS: "+e.getClass().getSimpleName();}
        if(networkOn)try{lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1500,0,this);networkRegistered=true;}
            catch(SecurityException e){if(providerError.isEmpty())providerError="رفض النظام إذن مزود الشبكة";}catch(Exception e){if(providerError.isEmpty())providerError="تعذر تسجيل مزود الشبكة";}
        if(passiveAvailable)try{lm.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER,3000,0,this);}
            catch(Exception ignored){}

        // Android 4.4+ routes Criteria-based requests through its fused location provider.
        // This is a separate path from directly subscribing to GPS_PROVIDER and NETWORK_PROVIDER.
        try{
            Criteria criteria=new Criteria();
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            criteria.setPowerRequirement(Criteria.POWER_HIGH);
            criteria.setAltitudeRequired(false);criteria.setBearingRequired(false);criteria.setSpeedRequired(false);
            criteria.setCostAllowed(true);
            criteriaProvider=lm.getBestProvider(criteria,true);
            lm.requestLocationUpdates(1000,0,criteria,this,Looper.getMainLooper());
            criteriaRegistered=true;
        }catch(SecurityException e){if(providerError.isEmpty())providerError="رفض النظام إذن خدمة الموقع المدمجة";}
         catch(Exception e){if(providerError.isEmpty())providerError="خدمة الموقع المدمجة غير متاحة: "+e.getClass().getSimpleName();}

        loadLastKnown();
        if(!gpsOn&&!networkOn)status.setText("خدمة الموقع مغلقة — اضغط لعرض التشخيص");
        else if(current!=null&&isNavigationFixUsable(current))
            status.setText("تم استقبال موقع حديث من "+lastFixProvider+" — دقة ±"+Math.round(lastFixAccuracy)+" متر");
        else if(!providerError.isEmpty())status.setText(providerError+" — اضغط لعرض التشخيص");
        else status.setText(gpsStatusText());
    }
    private String gpsStatusText(){
        String satelliteState;
        if(!gpsEngineStarted)satelliteState="محرك GPS لم يبدأ بعد";
        else if(satellitesVisible==0)satelliteState="GPS يعمل لكن لا يرى أقماراً";
        else if(satellitesUsed==0)satelliteState="يرى "+satellitesVisible+" أقمار، لكن لا يوجد تثبيت";
        else satelliteState="تثبيت الأقمار "+satellitesUsed+"/"+satellitesVisible;
        return satelliteState+" • GPS "+(gpsRegistered?"مطلوب":"غير مسجّل")+
                " • الشبكة "+(networkRegistered?"مطلوبة":"غير متاحة")+
                " • الموقع المدمج "+(criteriaRegistered?"مطلوب":"غير متاح")+" — اضغط للتشخيص";
    }
    private String providerReport(String provider){
        if(lm==null)return provider+": غير متاح";
        try{
            if(!lm.getAllProviders().contains(provider))return provider+": غير موجود في النظام";
            boolean enabled=lm.isProviderEnabled(provider);
            Location l=lm.getLastKnownLocation(provider);
            StringBuilder out=new StringBuilder(provider+": ").append(enabled?"مفعّل":"متوقف");
            if(l==null)return out.append(" • لا توجد قراءة محفوظة").toString();
            float accuracy=l.hasAccuracy()?l.getAccuracy():-1f;
            long elapsedFix=l.getElapsedRealtimeNanos(),elapsedNow=SystemClock.elapsedRealtimeNanos();
            long ageMs=elapsedFix>0&&elapsedNow>=elapsedFix
                    ?(elapsedNow-elapsedFix)/1000000L
                    :Math.max(0,System.currentTimeMillis()-l.getTime());
            out.append(" • آخر قراءة قبل ").append(ageMs/1000).append(" ثانية");
            out.append(" • الدقة ").append(accuracy>0?Math.round(accuracy)+" متر":"غير معروفة");
            return out.toString();
        }catch(SecurityException e){return provider+": لا توجد صلاحية لقراءة الموقع";}
        catch(Exception e){return provider+": خطأ "+e.getClass().getSimpleName();}
    }
    private String buildGpsDiagnostics(){
        StringBuilder b=new StringBuilder();
        b.append("الجهاز: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
        b.append("Firmware / Build: ").append(Build.DISPLAY).append(" • ").append(Build.VERSION.INCREMENTAL).append("\n");
        b.append("Android: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        b.append("إذن الموقع الدقيق: ").append(hasFineLocationPermission()?"ممنوح":"غير ممنوح").append("\n");
        b.append("إذن الموقع التقريبي: ").append(checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED?"ممنوح":"غير ممنوح").append("\n");
        try{
            int mode=Settings.Secure.getInt(getContentResolver(),Settings.Secure.LOCATION_MODE,Settings.Secure.LOCATION_MODE_OFF);
            b.append("وضع الموقع في Android: ").append(mode==Settings.Secure.LOCATION_MODE_OFF?"متوقف":mode==Settings.Secure.LOCATION_MODE_HIGH_ACCURACY?"دقة عالية":mode==Settings.Secure.LOCATION_MODE_BATTERY_SAVING?"توفير بطارية":"GPS فقط").append("\n");
        }catch(Exception ignored){}
        b.append("GPS: ").append(providerReport(LocationManager.GPS_PROVIDER)).append(" • حالة المزود: ").append(gpsProviderStatus).append("\n");
        b.append("الشبكة: ").append(providerReport(LocationManager.NETWORK_PROVIDER)).append(" • حالة المزود: ").append(networkProviderStatus).append("\n");
        b.append("Passive: ").append(providerReport(LocationManager.PASSIVE_PROVIDER)).append("\n");
        b.append("مسار Criteria/Fused: ").append(criteriaRegistered?"تم تسجيل الطلب":"لم يتم تسجيل الطلب").append(" • أفضل مزود: ").append(criteriaProvider).append("\n");
        b.append("محرك GPS: ").append(gpsEngineStarted?"بدأ":"لم يبدأ / لا توجد إشارة حالة").append("\n");
        b.append("A-GPS/XTRA: ").append(assistanceResult).append("\n");
        b.append("الأقمار: مرئية ").append(satellitesVisible).append(" • مستخدمة ").append(satellitesUsed);
        if(timeToFirstFixMs>=0)b.append(" • زمن أول تثبيت ").append(timeToFirstFixMs/1000f).append(" ثانية");
        b.append("\n");
        b.append("آخر تحديث استلمه التطبيق: ").append(lastFixProvider);
        if(lastFixAccuracy>0)b.append(" • دقة ±").append(Math.round(lastFixAccuracy)).append(" متر");
        if(lastFixElapsedNanos>0)b.append(" • قبل ").append(Math.max(0,(SystemClock.elapsedRealtimeNanos()-lastFixElapsedNanos)/1000000000L)).append(" ثانية");
        b.append("\n");
        try{
            android.content.pm.PackageInfo gms=getPackageManager().getPackageInfo("com.google.android.gms",0);
            b.append("Google Play services: موجودة ").append(gms.versionName);
        }catch(Exception e){b.append("Google Play services: غير موجودة / لا يمكن قراءتها");}
        b.append("\n\n");
        b.append("ملاحظة: ظهور «الشبكة متاحة» لا يعني أن مزود الشبكة أعاد إحداثيات. إذا كانت الأقمار المرئية صفر، فالمشكلة استقبال GPS أو برنامج تشغيله أو مكان تثبيت الجهاز، وليست صلاحيات ALI CAR.");
        return b.toString();
    }
    private void showGpsDiagnostics(){
        if(isFinishing())return;
        new android.app.AlertDialog.Builder(this)
                .setTitle("تشخيص الموقع و GPS")
                .setMessage(buildGpsDiagnostics())
                .setPositiveButton("إعادة طلب الموقع",(d,w)->{
                    current=null;requestLocationSources();
                    if(destination!=null){waitingForFix=true;drawRouteIfPossible();}
                })
                .setNeutralButton("تحديث A-GPS",(d,w)->refreshGpsAssistanceData())
                .setNegativeButton("إعدادات الموقع",(d,w)->openLocationSettings())
                .show();
    }
    private void showDestination(){
        if(destMarker!=null)map.getOverlays().remove(destMarker);
        destMarker=new Marker(map);destMarker.setPosition(destination);destMarker.setTitle(destinationName);map.getOverlays().add(destMarker);
        if(current==null){map.getController().setCenter(destination);map.getController().setZoom(13.5);status.setText("تم تحديد الوجهة — "+gpsStatusText());waitingForFix=true;}
        else map.invalidate();
    }
    private void showCar(Location l){
        GeoPoint p=new GeoPoint(l.getLatitude(),l.getLongitude());if(carMarker==null){carMarker=new Marker(map);carMarker.setTitle("موقع السيارة");map.getOverlays().add(carMarker);}carMarker.setPosition(p);if(l.hasBearing())carMarker.setRotation(l.getBearing());map.invalidate();
    }
    private void drawRouteIfPossible(){
        if(destination==null)return;
        if(!isNavigationFixUsable(current)){waitingForFix=true;status.setText("تم تحديد الوجهة — "+gpsStatusText());return;}
        requestRoute(current,destination,destinationName);
    }
    private void requestRoute(Location start,GeoPoint end,String name){
        if(routeTask!=null)routeTask.cancel(true);
        routeTask=new AsyncTask<Void,Void,RouteResult>(){
            protected RouteResult doInBackground(Void...v){try{
                String u=String.format(Locale.US,"https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true&alternatives=false",start.getLongitude(),start.getLatitude(),end.getLongitude(),end.getLatitude());
                JSONObject root=new JSONObject(VideoApi.read(getApplicationContext(),u));if(!"Ok".equals(root.optString("code")))return new RouteResult("تعذر إيجاد مسار قيادة");
                JSONArray routes=root.getJSONArray("routes");if(routes.length()==0)return new RouteResult("لا يوجد مسار");
                JSONObject r=routes.getJSONObject(0);JSONArray coords=r.getJSONObject("geometry").getJSONArray("coordinates");
                ArrayList<GeoPoint> points=new ArrayList<>();for(int i=0;i<coords.length();i++){JSONArray pair=coords.getJSONArray(i);points.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));}
                ArrayList<Maneuver> maneuvers=new ArrayList<>();JSONArray legs=r.getJSONArray("legs");
                if(legs.length()>0){JSONArray steps=legs.getJSONObject(0).optJSONArray("steps");if(steps!=null)for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);JSONObject m=step.optJSONObject("maneuver");if(m==null)continue;JSONArray loc=m.optJSONArray("location");if(loc==null||loc.length()<2)continue;maneuvers.add(new Maneuver(new GeoPoint(loc.getDouble(1),loc.getDouble(0)),instruction(step)));}}
                return new RouteResult(points,r.optDouble("distance")/1000,r.optDouble("duration")/60,maneuvers);
            }catch(Exception e){return new RouteResult("تعذر الاتصال بخدمة المسارات: "+e.getMessage());}}
            protected void onPostExecute(RouteResult r){routeTask=null;if(isFinishing())return;if(r.error!=null){status.setText(r.error);waitingForFix=true;return;}
                if(routeLine!=null)map.getOverlays().remove(routeLine);routeLine=new Polyline(map);routeLine.setPoints(r.points);routeLine.setColor(0xFF00DFFF);routeLine.setWidth(dp(5));map.getOverlays().add(routeLine);
                BoundingBox bounds=BoundingBox.fromGeoPoints(r.points);map.zoomToBoundingBox(bounds,true);
                status.setText(String.format(Locale.US,"المسافة %.1f كم  •  الوقت %.0f دقيقة",r.km,r.minutes));
                activeManeuvers=r.maneuvers==null?Collections.emptyList():r.maneuvers;updateManeuver(current,activeManeuvers);waitingForFix=false;map.invalidate();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    private static String instruction(JSONObject step){
        JSONObject m=step.optJSONObject("maneuver");String type=m==null?"":m.optString("type","");String mod=m==null?"":m.optString("modifier","");
        String road=step.optString("name","");String dir=mod.contains("left")?"يسار":mod.contains("right")?"يمين":mod.contains("uturn")?"استدر":"مباشرة";
        String action="arrive".equals(type)?"وصلت إلى الوجهة":"roundabout".equals(type)?"ادخل الدوار":"تابع "+dir;
        return action+(road.isEmpty()?"":" باتجاه "+road);
    }
    private void updateManeuver(Location l,List<Maneuver> steps){
        if(steps==null||steps.isEmpty()||l==null){maneuver.setText("اتبع الخط السماوي");return;}
        double best=Double.MAX_VALUE;String next=steps.get(steps.size()-1).text;
        for(Maneuver m:steps){double d=GeoMath.distanceMeters(l.getLatitude(),l.getLongitude(),m.point.getLatitude(),m.point.getLongitude());if(d>15&&d<best){best=d;next=m.text+" — بعد "+Math.round(d)+" متر";}}
        maneuver.setText(next);
    }
    private String gpsStatusText(){
        return "GPS "+(gpsRegistered?"يبحث عن الأقمار":"غير مسجّل")+" • الأقمار مرئية "+satellitesVisible+" / مستخدمة "+satellitesUsed+
                " • الشبكة "+(networkRegistered?"متاحة":"غير متاحة")+" — اضغط هنا للتشخيص";
    }
    @Override public void onLocationChanged(Location l){
        if(l==null)return;current=new Location(l);showCar(l);updateManeuver(current,activeManeuvers);
        float accuracy=l.hasAccuracy()?l.getAccuracy():999;
        if(destination!=null&&(waitingForFix||routeLine==null)&&isNavigationFixUsable(l))drawRouteIfPossible();
        lastFixProvider=l.getProvider()==null?"غير معروف":l.getProvider();
        lastFixAccuracy=accuracy;lastFixElapsedNanos=l.getElapsedRealtimeNanos();
        if(status!=null&&destination==null)status.setText("تم استقبال موقع "+lastFixProvider+" — الدقة ±"+Math.round(accuracy)+" متر");
    }
    @Override protected void onResume(){
        super.onResume();if(map!=null)map.onResume();
        requestLocationSources();if(current!=null)showCar(current);
        if(destination!=null){waitingForFix=true;drawRouteIfPossible();}
        locationHandler.removeCallbacks(locationStatusTicker);locationHandler.post(locationStatusTicker);
    }
    @Override protected void onPause(){
        locationHandler.removeCallbacks(locationStatusTicker);
        if(lm!=null){
            try{lm.removeUpdates(this);}catch(Exception ignored){}
            if(gpsStatusRegistered){try{lm.removeGpsStatusListener(gpsStatusListener);}catch(Exception ignored){}}
        }
        gpsRegistered=false;networkRegistered=false;criteriaRegistered=false;gpsStatusRegistered=false;gpsEngineStarted=false;
        if(map!=null)map.onPause();super.onPause();
    }
    @Override protected void onDestroy(){
        locationHandler.removeCallbacksAndMessages(null);
        if(geocodeTask!=null)geocodeTask.cancel(true);if(routeTask!=null)routeTask.cancel(true);
        if(lm!=null){try{lm.removeUpdates(this);}catch(Exception ignored){}if(gpsStatusRegistered)try{lm.removeGpsStatusListener(gpsStatusListener);}catch(Exception ignored){}}
        if(map!=null){map.onDetach();map=null;}super.onDestroy();
    }
    @Override public void onProviderEnabled(String p){if(status!=null)status.setText("تم تفعيل "+p+" — جارٍ طلب تحديث جديد");requestLocationSources();}
    @Override public void onProviderDisabled(String p){if(status!=null)status.setText("تم إيقاف "+p+" — فعّل الموقع من الإعدادات");}
    @Override public void onStatusChanged(String p,int state,Bundle b){
        String label=state==LocationProvider.AVAILABLE?"متاح":state==LocationProvider.TEMPORARILY_UNAVAILABLE?"بانتظار إشارة":state==LocationProvider.OUT_OF_SERVICE?"خارج الخدمة":"غير معروف";
        if(LocationManager.GPS_PROVIDER.equals(p))gpsProviderStatus=label;
        if(LocationManager.NETWORK_PROVIDER.equals(p))networkProviderStatus=label;
        if(LocationManager.GPS_PROVIDER.equals(p)&&state==LocationProvider.TEMPORARILY_UNAVAILABLE&&status!=null&&destination!=null)
            status.setText("GPS مفعّل لكن ينتظر تثبيت الأقمار • مرئية "+satellitesVisible+" / مستخدمة "+satellitesUsed);
    }
    private void refreshGpsAssistanceData(){
        if(lm==null||!hasFineLocationPermission()){
            assistanceResult="تعذر طلب تحديث المساعدة: لا توجد صلاحية موقع دقيق";
            android.widget.Toast.makeText(this,assistanceResult,android.widget.Toast.LENGTH_LONG).show();return;
        }
        boolean timeInjected=false,xtraRequested=false;
        try{timeInjected=lm.sendExtraCommand(LocationManager.GPS_PROVIDER,"force_time_injection",null);}catch(Exception ignored){}
        try{xtraRequested=lm.sendExtraCommand(LocationManager.GPS_PROVIDER,"force_xtra_injection",null);}catch(Exception ignored){}
        if(timeInjected||xtraRequested)
            assistanceResult="قبل برنامج GPS طلب المساعدة (وقت: "+timeInjected+"، XTRA: "+xtraRequested+"). التنزيل يعتمد على دعم Firmware.";
        else
            assistanceResult="برنامج GPS لم يقبل أو لا يدعم أوامر XTRA/وقت المساعدة.";
        android.widget.Toast.makeText(this,assistanceResult,android.widget.Toast.LENGTH_LONG).show();
        try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);}catch(Exception ignored){}
    }
    private static final class GeoResult{final GeoPoint point;final String name,error;GeoResult(String e){point=null;name="";error=e;}GeoResult(GeoPoint p,String n){point=p;name=n;error=null;}}
    private static final class Maneuver{final GeoPoint point;final String text;Maneuver(GeoPoint p,String t){point=p;text=t;}}
    private static final class RouteResult{final ArrayList<GeoPoint> points;final double km,minutes;final List<Maneuver> maneuvers;final String error;RouteResult(String e){points=null;km=minutes=0;maneuvers=null;error=e;}RouteResult(ArrayList<GeoPoint> p,double k,double m,List<Maneuver> n){points=p;km=k;minutes=m;maneuvers=n;error=null;}}
}
