package com.ali.carsystem;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

public final class NavigationActivity extends Activity implements LocationListener {
    private static final int BG=0xFF081426, WHITE=0xFFF4F7FB, MUTED=0xFF9EB0C5;
    private MapView map;
    private EditText input;
    private TextView status, directions;
    private LocationManager locationManager;
    private Location currentLocation;
    private Marker currentMarker, destinationMarker;
    private Polyline routeLine;
    private AsyncTask<String,Void,SearchResult> task;
    private long lastSearch;
    private int dp(int n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        try{
            Configuration.getInstance().load(getApplicationContext(),PreferenceManager.getDefaultSharedPreferences(this));
            File base=new File(getFilesDir(),"osmdroid"), tiles=new File(base,"tiles");
            if(!tiles.exists())tiles.mkdirs();
            Configuration.getInstance().setOsmdroidBasePath(base);
            Configuration.getInstance().setOsmdroidTileCache(tiles);
            Configuration.getInstance().setUserAgentValue("ALI-Car-System/1.2 (com.ali.carsystem)");
        }catch(Exception ignored){}
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);
        build();
        loadLastLocation();
    }
    private TextView label(String s,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);
        t.setTypeface(android.graphics.Typeface.create("sans-serif",bold?1:0));t.setGravity(Gravity.CENTER_VERTICAL);return t;
    }
    private void build(){
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(10),dp(8),dp(10),dp(6));root.setBackgroundColor(BG);
        LinearLayout head=new LinearLayout(this);head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=label("ALI CAR  •  الملاحة",20,0xFF63D7FF,true);head.addView(title,new LinearLayout.LayoutParams(0,dp(40),1));
        Button back=new Button(this);back.setText("رجوع");back.setAllCaps(false);back.setOnClickListener(v->finish());head.addView(back,new LinearLayout.LayoutParams(dp(82),dp(42)));root.addView(head);
        LinearLayout search=new LinearLayout(this);search.setGravity(Gravity.CENTER_VERTICAL);
        input=new EditText(this);input.setSingleLine(true);input.setTextSize(16);input.setHint("اكتب مدينة أو محافظة: دمشق، حلب...");
        input.setHintTextColor(MUTED);input.setTextColor(WHITE);input.setSelectAllOnFocus(false);
        search.addView(input,new LinearLayout.LayoutParams(0,dp(52),1));
        Button go=new Button(this);go.setText("بحث");go.setAllCaps(false);go.setOnClickListener(v->search());search.addView(go,new LinearLayout.LayoutParams(dp(82),dp(52)));
        root.addView(search);
        status=label("اكتب اسم المدينة ثم اضغط بحث — لا يشترط GPS لعرض المدينة",13,0xFF64D7C7,false);
        root.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));
        map=new MapView(this);map.setTileSource(TileSourceFactory.MAPNIK);map.setMultiTouchControls(true);
        map.getController().setZoom(6.0);map.getController().setCenter(new GeoPoint(34.8,38.9));
        root.addView(map,new LinearLayout.LayoutParams(-1,0,1));
        directions=label("الخريطة © OpenStreetMap contributors",12,MUTED,false);
        root.addView(directions,new LinearLayout.LayoutParams(-1,dp(62)));
        TextView attribution=label("© OpenStreetMap contributors  •  توجيه تجريبي عبر OSRM",9,MUTED,false);
        attribution.setGravity(Gravity.CENTER);root.addView(attribution,new LinearLayout.LayoutParams(-1,dp(17)));
        setContentView(root);
    }
    private void loadLastLocation(){
        if(locationManager==null||checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return;
        try{
            Location a=locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location b=locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            currentLocation=a==null?b:(b==null||a.getTime()>=b.getTime()?a:b);
            if(currentLocation!=null)updateCurrentMarker();
        }catch(Exception ignored){}
    }
    @Override protected void onResume(){
        super.onResume();if(map!=null)map.onResume();
        if(locationManager!=null&&checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){
            try{locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,2500,5,this);}catch(Exception ignored){}
        }
    }
    @Override protected void onPause(){
        if(locationManager!=null)try{locationManager.removeUpdates(this);}catch(Exception ignored){}
        if(map!=null)map.onPause();super.onPause();
    }
    @Override protected void onDestroy(){if(task!=null)task.cancel(true);if(map!=null)map.onDetach();super.onDestroy();}
    @Override public void onLocationChanged(Location l){if(l!=null){currentLocation=l;updateCurrentMarker();}}
    @Override public void onProviderDisabled(String p){if(LocationManager.GPS_PROVIDER.equals(p))status.setText("GPS متوقف — يمكنك البحث عن المدينة بدون GPS");}
    @Override public void onProviderEnabled(String p){}
    @Override public void onStatusChanged(String p,int s,Bundle b){}
    private void updateCurrentMarker(){
        if(map==null||currentLocation==null)return;
        GeoPoint p=new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude());
        if(currentMarker==null){currentMarker=new Marker(map);currentMarker.setTitle("موقعك الحالي");map.getOverlays().add(currentMarker);}
        currentMarker.setPosition(p);map.invalidate();
    }
    private void search(){
        String q=input.getText().toString().trim();if(q.length()<2){input.setError("اكتب اسم مدينة أو محافظة");return;}
        long now=System.currentTimeMillis();if(now-lastSearch<1100){status.setText("انتظر لحظة قبل البحث مرة أخرى");return;}lastSearch=now;
        ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);
        loadLastLocation();if(task!=null)task.cancel(true);
        status.setText("جارٍ البحث عن "+q+"...");
        directions.setText("يتم تحديد موقع المدينة على الخريطة");
        Location start=currentLocation==null?null:new Location(currentLocation);
        task=new SearchTask(this,start).execute(q);
    }
    private void showResult(SearchResult r){
        if(isFinishing())return;
        if(r.error!=null){status.setText(r.error);directions.setText("جرّب اسم المدينة بالعربية أو الإنجليزية وتحقق من الإنترنت.");return;}
        if(destinationMarker!=null)map.getOverlays().remove(destinationMarker);
        destinationMarker=new Marker(map);destinationMarker.setPosition(r.destination);destinationMarker.setTitle(r.name);destinationMarker.setSnippet("الوجهة المطلوبة");map.getOverlays().add(destinationMarker);
        if(r.start!=null&&r.points!=null&&r.points.size()>1){
            if(routeLine!=null)map.getOverlays().remove(routeLine);
            routeLine=new Polyline(map);routeLine.setColor(0xFF16B8E6);routeLine.setWidth(dp(5));routeLine.setPoints(r.points);map.getOverlays().add(routeLine);
            if(currentMarker==null){currentMarker=new Marker(map);currentMarker.setTitle("موقعك الحالي");map.getOverlays().add(currentMarker);}
            currentMarker.setPosition(r.start);
            status.setText(String.format(Locale.US,"المسافة %.1f كم  •  الوقت %d دقيقة",r.distance,r.minutes));
            directions.setText(r.steps);
            map.getController().setZoom(9.0);
            map.getController().setCenter(new GeoPoint((r.start.getLatitude()+r.destination.getLatitude())/2,(r.start.getLongitude()+r.destination.getLongitude())/2));
        }else{
            if(routeLine!=null){map.getOverlays().remove(routeLine);routeLine=null;}
            map.getController().setZoom(12.0);map.getController().animateTo(r.destination);
            status.setText("تم العثور على: "+r.name);
            directions.setText("تم تحديد المدينة على الخريطة. فعّل GPS وحدد موقعك للحصول على مسار القيادة من موقعك الحالي.");
        }
        map.invalidate();
    }
    private static JSONObject getObject(String url)throws Exception{return new JSONObject(fetch(url));}
    private static JSONArray getArray(String url)throws Exception{return new JSONArray(fetch(url));}
    private static String fetch(String address)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setRequestProperty("User-Agent","ALI-Car-System/1.2 (com.ali.carsystem)");
        c.setRequestProperty("Accept","application/json");c.setRequestProperty("Accept-Language","ar,en;q=0.8");
        try{int code=c.getResponseCode();if(code<200||code>=300)throw new Exception("HTTP "+code);
            InputStream in=c.getInputStream();BufferedReader rd=new BufferedReader(new InputStreamReader(in,"UTF-8"));
            StringBuilder s=new StringBuilder();String line;while((line=rd.readLine())!=null){s.append(line);if(s.length()>2000000)throw new Exception("response too large");}
            rd.close();return s.toString();
        }finally{c.disconnect();}
    }
    private static ArrayList<GeoPoint> decode(String e){
        ArrayList<GeoPoint> out=new ArrayList<>();int i=0,lat=0,lon=0;
        while(i<e.length()){int[] a=decodeOne(e,i);lat+=a[0];i=a[1];int[] b=decodeOne(e,i);lon+=b[0];i=b[1];out.add(new GeoPoint(lat/1E5,lon/1E5));}return out;
    }
    private static int[] decodeOne(String s,int i){int result=0,shift=0,v;do{if(i>=s.length())throw new IllegalArgumentException("bad polyline");v=s.charAt(i++)-63;result|=(v&31)<<shift;shift+=5;}while(v>=32);return new int[]{(result&1)!=0?~(result>>1):(result>>1),i};}
    private static final class SearchResult{
        String error,name,steps;GeoPoint destination,start;ArrayList<GeoPoint> points;double distance;int minutes;
        SearchResult(String e,String n,GeoPoint d,GeoPoint s,ArrayList<GeoPoint> p,String st,double km,int m){error=e;name=n;destination=d;start=s;points=p;steps=st;distance=km;minutes=m;}
    }
    private static final class SearchTask extends AsyncTask<String,Void,SearchResult>{
        private final NavigationActivity activity;private final Location start;
        SearchTask(NavigationActivity a,Location s){activity=a;start=s;}
        @Override protected SearchResult doInBackground(String... q){
            try{
                String query=URLEncoder.encode(q[0],"UTF-8");
                JSONArray hits=getArray("https://nominatim.openstreetmap.org/search?q="+query+"&format=jsonv2&limit=5&accept-language=ar");
                if(hits.length()==0)return new SearchResult("لم يتم العثور على المدينة أو المحافظة",null,null,null,null,null,0,0);
                JSONObject hit=hits.getJSONObject(0);double lat=Double.parseDouble(hit.getString("lat")),lon=Double.parseDouble(hit.getString("lon"));
                GeoPoint dest=new GeoPoint(lat,lon);String name=hit.optString("display_name",q[0]);
                if(start==null)return new SearchResult(null,name,dest,null,null,"لا تتوفر إشارة GPS حالياً.",0,0);
                String url=String.format(Locale.US,"https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline&steps=true",start.getLongitude(),start.getLatitude(),lon,lat);
                JSONObject routeResponse=getObject(url);JSONArray routes=routeResponse.optJSONArray("routes");
                if(!"Ok".equals(routeResponse.optString("code"))||routes==null||routes.length()==0)
                    return new SearchResult(null,name,dest,null,null,"لم يتوفر مسار قيادة لهذه الوجهة.",0,0);
                JSONObject route=routes.getJSONObject(0);ArrayList<GeoPoint> points=decode(route.getString("geometry"));
                StringBuilder steps=new StringBuilder("تعليمات الطريق:\n");
                JSONArray legs=route.optJSONArray("legs");
                if(legs!=null&&legs.length()>0){JSONArray ss=legs.getJSONObject(0).optJSONArray("steps");if(ss!=null){
                    for(int i=0;i<Math.min(ss.length(),12);i++){JSONObject step=ss.getJSONObject(i);JSONObject man=step.optJSONObject("maneuver");
                        String mod=man==null?"":man.optString("modifier","");String dir="يسار".equals(mod)?"يسار":"يمين".equals(mod)?"يمين":"تابع مباشرة";
                        String road=step.optString("name","");steps.append(dir);if(!road.isEmpty())steps.append(" نحو ").append(road);
                        steps.append(" — ").append((int)(step.optDouble("distance",0))).append(" م\n");
                    }
                }}
                return new SearchResult(null,name,dest,new GeoPoint(start.getLatitude(),start.getLongitude()),points,steps.toString(),route.optDouble("distance",0)/1000.0,(int)Math.ceil(route.optDouble("duration",0)/60.0));
            }catch(Exception e){return new SearchResult("تعذّر البحث: "+(e.getMessage()==null?"تحقق من الإنترنت":e.getMessage()),null,null,null,null,null,0,0);}
        }
        @Override protected void onPostExecute(SearchResult r){if(!isCancelled()&&!activity.isFinishing())activity.showResult(r);}
    }
}