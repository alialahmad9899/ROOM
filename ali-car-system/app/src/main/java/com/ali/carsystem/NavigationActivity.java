package com.ali.carsystem;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.location.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.view.WindowManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

public final class NavigationActivity extends Activity implements LocationListener {
    private FrameLayout root;
    private MapView map;
    private EditText query;
    private TextView routeStatus, maneuverBanner;
    private LocationManager locationManager;
    private Location currentLocation;
    private AsyncTask<String,Void,NavResult> searchTask;
    private AsyncTask<Void,Void,NavResult> routeTask;
    private Marker destinationMarker,carMarker;
    private Polyline routeLine;
    private GeoPoint pendingDestination;
    private String pendingDestinationName;
    private boolean waitingForGps, firstFix=true;
    private ValueAnimator carAnimator;
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private void immersive(){
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus)immersive();}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);immersive();
        Configuration.getInstance().setUserAgentValue(NetworkCompat.USER_AGENT);
        try{HttpsURLConnection.setDefaultSSLSocketFactory(LegacyTls.socketFactory(getApplicationContext()));}catch(Exception ignored){}
        Configuration.getInstance().setCacheMapTileCount((short)4);
        Configuration.getInstance().setTileDownloadThreads((short)1);
        Configuration.getInstance().setTileFileSystemThreads((short)1);
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(8L*1024L*1024L);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(4L*1024L*1024L);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);

        root=new FrameLayout(this);root.setBackgroundColor(Color.BLACK);root.setFitsSystemWindows(false);
        map=new MapView(this);map.setFitsSystemWindows(false);map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);map.setUseDataConnection(true);map.setFlingEnabled(true);
        map.getController().setZoom(13.0);map.getController().setCenter(new GeoPoint(34.8,38.9));
        root.addView(map,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);top.setPadding(dp(8),0,dp(8),0);
        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
        query=new EditText(this);query.setSingleLine(true);query.setTextColor(0xFF111111);query.setHintTextColor(0xFF666666);
        query.setHint("ابحث عن مدينة أو محافظة أو شارع");query.setTextSize(15);query.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        query.setTextDirection(View.TEXT_DIRECTION_RTL);query.setPadding(dp(14),0,dp(14),0);
        GradientDrawable inputBg=new GradientDrawable();inputBg.setColor(0xF7FFFFFF);inputBg.setCornerRadius(dp(13));inputBg.setStroke(dp(1),0xFF83DFFF);
        query.setBackground(inputBg);searchRow.addView(query,new LinearLayout.LayoutParams(0,dp(48),1f));
        Button search=new Button(this);search.setText("بحث");search.setOnClickListener(v->searchDestination());
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(dp(82),dp(48));searchLp.leftMargin=dp(6);searchRow.addView(search,searchLp);
        top.addView(searchRow,new LinearLayout.LayoutParams(-1,dp(50)));

        maneuverBanner=text("اكتب وجهتك لبدء الملاحة",14,Color.WHITE,true);
        maneuverBanner.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);maneuverBanner.setPadding(dp(14),0,dp(14),0);
        maneuverBanner.setBackground(glass(0xE8152B40,0x9978DFFF,13));
        LinearLayout.LayoutParams bannerLp=new LinearLayout.LayoutParams(-1,dp(40));bannerLp.topMargin=dp(5);
        top.addView(maneuverBanner,bannerLp);
        routeStatus=text("المسافة والوقت يظهران هنا",12,Color.WHITE,true);
        routeStatus.setGravity(Gravity.CENTER);routeStatus.setPadding(dp(10),0,dp(10),0);
        routeStatus.setBackground(glass(0xC9152B40,0x6678DFFF,14));
        LinearLayout.LayoutParams statusLp=new LinearLayout.LayoutParams(-1,dp(34));
        statusLp.topMargin=dp(4);top.addView(routeStatus,statusLp);
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(top,topLp);
        setContentView(root);
        query.setOnEditorActionListener((v,action,event)->{searchDestination();return true;});
        loadLastKnownLocation();
    }
    private GradientDrawable glass(int color,int stroke,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{color,0xE9182638,color});d.setCornerRadius(dp(radius));d.setStroke(dp(1),stroke);return d;
    }
    private TextView text(String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(android.graphics.Typeface.create("sans-serif",bold?1:0));t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private void loadLastKnownLocation(){
        try{
            if(checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED
                    &&checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
            Location best=null;
            for(String provider:new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}){
                try{
                    Location candidate=locationManager.getLastKnownLocation(provider);
                    if(candidate!=null&&(best==null||candidate.getTime()>best.getTime()))best=candidate;
                }catch(SecurityException|IllegalArgumentException ignored){}
            }
            if(best!=null)currentLocation=best;
        }catch(Exception ignored){}
    }
    private void searchDestination(){
        String value=query.getText().toString().trim();
        if(value.length()<2){query.setError("اكتب اسم وجهة صحيحاً");return;}
        if(searchTask!=null)searchTask.cancel(true);
        if(routeTask!=null)routeTask.cancel(true);
        loadLastKnownLocation();routeStatus.setText("جارٍ البحث عن الوجهة...");
        maneuverBanner.setText("يتم تحديد موقع الوجهة");
        searchTask=new AsyncTask<String,Void,NavResult>(){
            @Override protected NavResult doInBackground(String...args){return geocodeAndRoute(args[0],currentLocation);}
            @Override protected void onPostExecute(NavResult result){if(!isFinishing())displayResult(result);}
        }.execute(value);
    }
    private NavResult geocodeAndRoute(String text,Location origin){
        try{
            String encoded=URLEncoder.encode(text,"UTF-8");
            String url="https://nominatim.openstreetmap.org/search?q="+encoded+
                    "&format=jsonv2&limit=5&addressdetails=1&accept-language=ar,en";
            JSONArray results=new JSONArray(request(url));
            if(results.length()==0)return NavResult.error("لم يتم العثور على الوجهة. جرّب اسماً آخر.");
            JSONObject best=results.getJSONObject(0);double bestScore=-1;
            for(int i=0;i<results.length();i++){
                JSONObject candidate=results.getJSONObject(i);double score=candidate.optDouble("importance",0);
                String type=candidate.optString("type","");
                if("city".equals(type)||"town".equals(type)||"state".equals(type)||
                        "administrative".equals(type)||"road".equals(type)||"residential".equals(type))score+=1;
                if(score>bestScore){bestScore=score;best=candidate;}
            }
            String name=best.optString("display_name",text);
            GeoPoint destination=new GeoPoint(best.getDouble("lat"),best.getDouble("lon"));
            if(origin==null)
                return new NavResult(null,name,destination,null,null,"تم تحديد الوجهة. بانتظار إشارة GPS لرسم الطريق.",0,0);
            return requestRoute(origin,destination,name);
        }catch(Exception error){return NavResult.error(NetworkCompat.arabicError(error));}
    }
    private NavResult requestRoute(Location origin,GeoPoint destination,String name){
        try{
            String url=String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true&alternatives=false",
                    origin.getLongitude(),origin.getLatitude(),destination.getLongitude(),destination.getLatitude());
            JSONObject response=new JSONObject(request(url));
            JSONArray routes=response.optJSONArray("routes");
            if(!"Ok".equals(response.optString("code"))||routes==null||routes.length()==0)
                return new NavResult(null,name,destination,null,null,"تعذر العثور على طريق قيادة لهذه الوجهة.",0,0);
            JSONObject route=routes.getJSONObject(0);
            JSONObject geometry=route.optJSONObject("geometry");
            JSONArray coordinates=geometry==null?null:geometry.optJSONArray("coordinates");
            ArrayList<GeoPoint> points=new ArrayList<>();
            if(coordinates!=null){
                for(int i=0;i<coordinates.length()&&i<30000;i++){
                    JSONArray pair=coordinates.optJSONArray(i);
                    if(pair!=null&&pair.length()>=2)points.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));
                }
            }
            if(points.size()<2)return new NavResult(null,name,destination,null,null,"لم يرجع محرك التوجيه خط مسار صالحاً.",0,0);
            StringBuilder directions=new StringBuilder();
            String next="اتبع الطريق المحدد";
            JSONArray legs=route.optJSONArray("legs");
            if(legs!=null&&legs.length()>0){
                JSONArray steps=legs.getJSONObject(0).optJSONArray("steps");
                if(steps!=null){
                    for(int i=0;i<steps.length()&&i<35;i++){
                        String instruction=maneuverText(steps.getJSONObject(i));
                        if(i==0)next=instruction;
                        directions.append("• ").append(instruction).append('\n');
                    }
                }
            }
            return new NavResult(null,name,destination,new GeoPoint(origin.getLatitude(),origin.getLongitude()),
                    points,directions.toString(),route.optDouble("distance")/1000.0,route.optDouble("duration")/60.0,next);
        }catch(Exception error){return NavResult.error(NetworkCompat.arabicError(error));}
    }
    private void displayResult(NavResult result){
        if(result.error!=null){routeStatus.setText(result.error);maneuverBanner.setText("تحقق من الإنترنت وحاول مرة أخرى");return;}
        pendingDestination=result.destination;pendingDestinationName=result.name;
        if(destinationMarker!=null)map.getOverlays().remove(destinationMarker);
        destinationMarker=new Marker(map);destinationMarker.setPosition(result.destination);destinationMarker.setTitle(result.name);
        destinationMarker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);map.getOverlays().add(destinationMarker);
        if(result.start==null){
            waitingForGps=true;map.getController().setCenter(result.destination);map.getController().setZoom(13.5);
            routeStatus.setText("الوجهة: "+result.name);
            maneuverBanner.setText("تم تحديد الوجهة — بانتظار إشارة GPS لرسم الطريق");
            map.invalidate();return;
        }
        waitingForGps=false;
        if(routeLine!=null)map.getOverlays().remove(routeLine);
        routeLine=new Polyline(map);routeLine.setPoints(result.points);routeLine.setColor(0xFF00CFFF);
        routeLine.setWidth(dp(14));map.getOverlays().add(routeLine);
        ensureCarMarker();moveCarMarker(result.start,true);
        map.getController().setZoom(18.5);
        map.getController().animateTo(result.start);
        routeStatus.setText(String.format(Locale.US,"المسافة %.1f كم   •   الوقت المتبقي %.0f دقيقة",result.km,result.minutes));
        maneuverBanner.setText(TextUtils.isEmpty(result.nextInstruction)?"اتبع الخط السماوي":result.nextInstruction);
        map.invalidate();
    }
    private void ensureCarMarker(){
        if(carMarker!=null)return;
        carMarker=new Marker(map);carMarker.setTitle("موقع السيارة");carMarker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER);
        carMarker.setIcon(new BitmapDrawable(getResources(),createCarBitmap()));map.getOverlays().add(carMarker);
    }
    private Bitmap createCarBitmap(){
        int w=dp(56),h=dp(86);Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(bitmap);
        Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(0x88000000);c.drawRoundRect(dp(10),dp(9),w-dp(7),h-dp(3),dp(15),dp(15),p);
        p.setShader(new LinearGradient(0,0,w,h,0xFFEAF4FC,0xFF35495C,Shader.TileMode.CLAMP));
        c.drawRoundRect(dp(9),dp(5),w-dp(9),h-dp(8),dp(17),dp(17),p);p.setShader(null);
        p.setColor(0xFF172738);c.drawRoundRect(dp(15),dp(20),w-dp(15),h-dp(37),dp(9),dp(9),p);
        p.setColor(0xFF8BA6BB);c.drawRoundRect(dp(17),dp(12),w-dp(17),dp(24),dp(5),dp(5),p);
        p.setColor(0xFF243A50);c.drawRoundRect(dp(14),dp(53),w-dp(14),h-dp(15),dp(7),dp(7),p);
        p.setColor(0xFFFF444D);c.drawRoundRect(dp(12),h-dp(17),dp(21),h-dp(11),dp(3),dp(3),p);
        c.drawRoundRect(w-dp(21),h-dp(17),w-dp(12),h-dp(11),dp(3),dp(3),p);
        p.setColor(0xFF101820);c.drawRoundRect(dp(5),dp(26),dp(11),dp(43),dp(3),dp(3),p);
        c.drawRoundRect(w-dp(11),dp(26),w-dp(5),dp(43),dp(3),dp(3),p);
        p.setColor(0xFFFFFFFF);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(dp(1.4f));
        c.drawRoundRect(dp(9),dp(5),w-dp(9),h-dp(8),dp(17),dp(17),p);return bitmap;
    }
    private void moveCarMarker(GeoPoint target,boolean immediate){
        ensureCarMarker();
        if(carAnimator!=null){carAnimator.cancel();carAnimator=null;}
        GeoPoint from=carMarker.getPosition();
        if(immediate||from==null){carMarker.setPosition(target);return;}
        final double startLat=from.getLatitude(),startLon=from.getLongitude();
        carAnimator=ValueAnimator.ofFloat(0f,1f);carAnimator.setDuration(650);
        carAnimator.setInterpolator(new DecelerateInterpolator());
        carAnimator.addUpdateListener(animation->{
            float f=(Float)animation.getAnimatedValue();
            carMarker.setPosition(new GeoPoint(startLat+(target.getLatitude()-startLat)*f,
                    startLon+(target.getLongitude()-startLon)*f));
            map.invalidate();
        });carAnimator.start();
    }
    private String request(String address)throws Exception{
        HttpURLConnection connection=NetworkCompat.open(this,address);
        connection.setConnectTimeout(4000);connection.setReadTimeout(5000);
        try{
            int code=connection.getResponseCode();
            if(code==403)throw new java.io.IOException("رفض خادم البحث الطلب. حاول بعد قليل.");
            if(code<200||code>=300)throw new java.io.IOException("تعذر الوصول إلى خدمة الخرائط.");
            try(InputStream input=connection.getInputStream();
                BufferedReader reader=new BufferedReader(new InputStreamReader(input,"UTF-8"))){
                StringBuilder response=new StringBuilder();String line;
                while((line=reader.readLine())!=null){
                    response.append(line);if(response.length()>2000000)throw new java.io.IOException("استجابة الخرائط أكبر من الحجم المسموح.");
                }return response.toString();
            }
        }finally{connection.disconnect();}
    }
    private String maneuverText(JSONObject step){
        JSONObject maneuver=step.optJSONObject("maneuver");
        String type=maneuver==null?"":maneuver.optString("type","");
        String modifier=maneuver==null?"":maneuver.optString("modifier","");
        String road=step.optString("name","");
        String direction=modifier.contains("left")?"يسار":modifier.contains("right")?"يمين":
                modifier.contains("uturn")?"استدر":"إلى الأمام";
        String action="arrive".equals(type)?"وصلت إلى الوجهة":
                "roundabout".equals(type)?"ادخل الدوار":"تابع "+direction;
        return action+(road.length()>0?" باتجاه "+road:"")+" بعد "+Math.round(step.optDouble("distance"))+" متر";
    }
    @Override protected void onResume(){
        super.onResume();immersive();if(map!=null)map.onResume();
        if(locationManager!=null&&(checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED
                ||checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED)){
            try{locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,1f,this);}catch(Exception ignored){}
            try{locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1800L,4f,this);}catch(Exception ignored){}
            loadLastKnownLocation();
            if(currentLocation!=null)onLocationChanged(currentLocation);
        }
    }
    @Override protected void onPause(){
        if(locationManager!=null){try{locationManager.removeUpdates(this);}catch(Exception ignored){}}
        if(carAnimator!=null){carAnimator.cancel();carAnimator=null;}
        if(map!=null)map.onPause();super.onPause();
    }
    @Override protected void onDestroy(){
        if(searchTask!=null)searchTask.cancel(true);if(routeTask!=null)routeTask.cancel(true);
        if(map!=null){map.onDetach();map=null;}super.onDestroy();
    }
    @Override public void onLocationChanged(Location location){
        if(location==null)return;currentLocation=location;
        if(location.hasAccuracy()&&location.getAccuracy()>45f)return;
        GeoPoint point=new GeoPoint(location.getLatitude(),location.getLongitude());
        ensureCarMarker();moveCarMarker(point,false);
        if(location.hasBearing()&&location.hasSpeed()&&location.getSpeed()>0.8f){
            carMarker.setRotation(location.getBearing());
            map.setMapOrientation(-location.getBearing());
        }
        if(waitingForGps&&pendingDestination!=null&&routeTask==null){
            waitingForGps=false;routeTask=new AsyncTask<Void,Void,NavResult>(){
                @Override protected NavResult doInBackground(Void...args){return requestRoute(location,pendingDestination,pendingDestinationName);}
                @Override protected void onPostExecute(NavResult result){routeTask=null;if(!isFinishing())displayResult(result);}
            }.execute();
        }
        if(routeLine!=null){map.getController().animateTo(point);if(map.getZoomLevelDouble()<17.5)map.getController().setZoom(18.5);}
        else if(firstFix){map.getController().animateTo(point);firstFix=false;}
        map.invalidate();
    }
    @Override public void onProviderDisabled(String provider){
        if(LocationManager.GPS_PROVIDER.equals(provider)){routeStatus.setText("نظام تحديد الموقع متوقف");}
    }
    @Override public void onProviderEnabled(String provider){if(LocationManager.GPS_PROVIDER.equals(provider))routeStatus.setText("تم تفعيل نظام تحديد الموقع");}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    private static final class NavResult{
        final String error,name,steps,nextInstruction;final GeoPoint destination,start;final ArrayList<GeoPoint> points;final double km,minutes;
        NavResult(String e,String n,GeoPoint d,GeoPoint s,ArrayList<GeoPoint> p,String st,double k,double m){this(e,n,d,s,p,st,k,m,"اتبع الطريق المحدد");}
        NavResult(String e,String n,GeoPoint d,GeoPoint s,ArrayList<GeoPoint> p,String st,double k,double m,String next){
            error=e;name=n;destination=d;start=s;points=p;steps=st;km=k;minutes=m;nextInstruction=next;
        }
        static NavResult error(String value){return new NavResult(value,"",null,null,null,"",0,0);}
    }
}
