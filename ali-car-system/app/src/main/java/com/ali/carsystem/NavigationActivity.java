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
    private long lastSearch;private boolean waitingForFix;private boolean gpsRegistered,networkRegistered;private String providerError="";
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
        loadLastKnown();if(current!=null)showCar(current);status.setOnClickListener(v->openLocationSettings());
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
    private void loadLastKnown(){
        long now=System.currentTimeMillis();Location best=null;
        for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER})try{
            if(!lm.isProviderEnabled(provider))continue;
            Location l=lm.getLastKnownLocation(provider);if(l==null)continue;
            float acc=l.hasAccuracy()?l.getAccuracy():999;
            if(!GeoMath.isUsableFix(l.getTime(),now,120000,acc))continue;
            if(best==null||l.getTime()>best.getTime())best=l;
        }catch(SecurityException e){providerError="إذن الموقع غير متاح";}catch(Exception ignored){}
        if(best!=null)current=best;
    }
    private boolean hasLocationPermission(){
        return checkCallingOrSelfPermission("android.permission.ACCESS_FINE_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED||
               checkCallingOrSelfPermission("android.permission.ACCESS_COARSE_LOCATION")==android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    private void openLocationSettings(){
        try{startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));}
        catch(Exception e){status.setText("افتح إعدادات الجهاز ثم الموقع وفعّل تحديد الموقع");}
    }
    private void requestLocationSources(){
        gpsRegistered=false;networkRegistered=false;providerError="";
        if(lm==null){status.setText("خدمة الموقع غير متاحة في نظام الجهاز");return;}
        if(!hasLocationPermission()){status.setText("إذن الموقع غير ممنوح — اضغط هنا لفتح إعدادات الموقع");return;}
        boolean gpsOn=false,networkOn=false;
        try{gpsOn=lm.isProviderEnabled(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}
        try{networkOn=lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);}catch(Exception ignored){}
        if(gpsOn)try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000,0,this);gpsRegistered=true;}
            catch(SecurityException e){providerError="رفض النظام إذن GPS";}
            catch(Exception e){providerError="تعذر التسجيل في GPS: "+e.getClass().getSimpleName();}
        if(networkOn)try{lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1500,0,this);networkRegistered=true;}
            catch(SecurityException e){providerError="رفض النظام إذن الشبكة";}
            catch(Exception e){if(providerError.isEmpty())providerError="تعذر التسجيل في مزود الشبكة";}
        if(!gpsOn&&!networkOn)status.setText("خدمة الموقع مغلقة — اضغط هنا لفتح إعدادات الموقع");
        else if(current!=null&&GeoMath.isUsableFix(current.getTime(),System.currentTimeMillis(),120000,current.hasAccuracy()?current.getAccuracy():999))
            status.setText("آخر موقع صالح: "+current.getProvider()+" — دقة ±"+(current.hasAccuracy()?Math.round(current.getAccuracy()):0)+" متر — ننتظر تحديثاً جديداً");
        else if(!providerError.isEmpty())status.setText(providerError+" — اضغط هنا للإعدادات");
        else status.setText("GPS "+(gpsRegistered?"مفعّل":"غير متاح")+" • الشبكة "+(networkRegistered?"مفعّلة":"غير متاحة")+" — بانتظار أول تثبيت للموقع");
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
        if(current==null||!GeoMath.isUsableFix(current.getTime(),System.currentTimeMillis(),120000,current.hasAccuracy()?current.getAccuracy():999)){waitingForFix=true;status.setText("تم تحديد الوجهة — "+gpsStatusText());return;}
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
    private String gpsStatusText(){return "GPS "+(gpsRegistered?"يبحث عن الأقمار":"غير مسجّل")+" — الشبكة "+(networkRegistered?"متاحة":"غير متاحة")+" — اضغط هنا للتشخيص";}
    @Override public void onLocationChanged(Location l){
        if(l==null)return;current=new Location(l);showCar(l);updateManeuver(current,activeManeuvers);
        float accuracy=l.hasAccuracy()?l.getAccuracy():999;
        boolean fresh=GeoMath.isUsableFix(l.getTime(),System.currentTimeMillis(),120000,accuracy);
        if(destination!=null&&(waitingForFix||routeLine==null)&&fresh&&accuracy<=150)drawRouteIfPossible();
        if(status!=null&&destination==null)status.setText("تم استقبال موقع "+l.getProvider()+" — الدقة ±"+Math.round(accuracy)+" متر");
    }
    @Override protected void onResume(){super.onResume();if(map!=null)map.onResume();loadLastKnown();requestLocationSources();if(current!=null)showCar(current);if(destination!=null)drawRouteIfPossible();}
    @Override protected void onPause(){if(lm!=null)try{lm.removeUpdates(this);}catch(Exception ignored){}gpsRegistered=false;networkRegistered=false;if(map!=null)map.onPause();super.onPause();}
    @Override protected void onDestroy(){if(geocodeTask!=null)geocodeTask.cancel(true);if(routeTask!=null)routeTask.cancel(true);if(map!=null){map.onDetach();map=null;}super.onDestroy();}
    @Override public void onProviderEnabled(String p){if(status!=null)status.setText("تم تفعيل "+p+" — جارٍ طلب تحديث جديد");requestLocationSources();}
    @Override public void onProviderDisabled(String p){if(status!=null)status.setText("تم إيقاف "+p+" — فعّل الموقع من الإعدادات");}
    @Override public void onStatusChanged(String p,int state,Bundle b){
        if(LocationManager.GPS_PROVIDER.equals(p)&&state==LocationProvider.TEMPORARILY_UNAVAILABLE&&status!=null)
            status.setText("GPS مفعّل لكن لم يصل تثبيت أقمار بعد — ابقَ قرب نافذة أو في مكان مفتوح");
    }
    private static final class GeoResult{final GeoPoint point;final String name,error;GeoResult(String e){point=null;name="";error=e;}GeoResult(GeoPoint p,String n){point=p;name=n;error=null;}}
    private static final class Maneuver{final GeoPoint point;final String text;Maneuver(GeoPoint p,String t){point=p;text=t;}}
    private static final class RouteResult{final ArrayList<GeoPoint> points;final double km,minutes;final List<Maneuver> maneuvers;final String error;RouteResult(String e){points=null;km=minutes=0;maneuvers=null;error=e;}RouteResult(ArrayList<GeoPoint> p,double k,double m,List<Maneuver> n){points=p;km=k;minutes=m;maneuvers=n;error=null;}}
}
