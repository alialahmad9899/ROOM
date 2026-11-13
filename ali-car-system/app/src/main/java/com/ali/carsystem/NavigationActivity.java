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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.DecelerateInterpolator;
import android.view.WindowManager;
import android.widget.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.BoundingBox;
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
    private TextView routeStatus, maneuverBanner, trafficStatus;
    private LinearLayout routeChoices;
    private HorizontalScrollView routeChoicesScroll;
    private Button searchButton, retryRouteButton;
    private TextView modeToggle;
    private boolean keyboardRequested, driverMode;
    private ArrayList<RouteOption> routeOptions = new ArrayList<>();
    private RouteOption selectedRoute;
    private int activeManeuverIndex;
    private String selectedPreference = "الأسرع";
    private LocationManager locationManager;
    private Location currentLocation;
    private AsyncTask<String,Void,NavResult> searchTask;
    private AsyncTask<Void,Void,RouteResult> routeTask;
    private Marker destinationMarker,carMarker;
    private Polyline routeLine;
    private GeoPoint pendingDestination;
    private String pendingDestinationName;
    private boolean waitingForGps, firstFix=true;
    private long lastRouteAttemptMs;
    private ValueAnimator carAnimator;
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private void immersive(){
        if(keyboardRequested)return;
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&!keyboardRequested)immersive();}

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        immersive();
        Configuration.getInstance().setUserAgentValue(NetworkCompat.USER_AGENT);
        try{HttpsURLConnection.setDefaultSSLSocketFactory(LegacyTls.socketFactory(getApplicationContext()));}catch(Exception ignored){}
        Configuration.getInstance().setCacheMapTileCount((short)4);
        Configuration.getInstance().setTileDownloadThreads((short)1);
        Configuration.getInstance().setTileFileSystemThreads((short)1);
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(8L*1024L*1024L);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(4L*1024L*1024L);
        locationManager=(LocationManager)getSystemService(LOCATION_SERVICE);

        root=new FrameLayout(this);root.setBackgroundColor(0xFF06111D);root.setFitsSystemWindows(false);
        map=new MapView(this);map.setFitsSystemWindows(false);map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);map.setUseDataConnection(true);map.setFlingEnabled(true);
        map.getController().setZoom(13.0);map.getController().setCenter(new GeoPoint(34.8,38.9));
        root.addView(map,new FrameLayout.LayoutParams(-1,-1));

        LinearLayout top=new LinearLayout(this);top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(9),dp(7),dp(9),0);
        LinearLayout searchRow=new LinearLayout(this);searchRow.setGravity(Gravity.CENTER_VERTICAL);
        query=new EditText(this);query.setSingleLine(true);query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setTextColor(0xFF101820);query.setHintTextColor(0xFF697A89);
        query.setHint("ابحث عن مدينة أو محافظة أو شارع");query.setTextSize(15);
        query.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);query.setTextDirection(View.TEXT_DIRECTION_RTL);
        query.setPadding(dp(14),0,dp(14),0);
        GradientDrawable inputBg=new GradientDrawable();inputBg.setColor(0xF8FFFFFF);
        inputBg.setCornerRadius(dp(14));inputBg.setStroke(dp(1),0xFF69D5F5);
        query.setBackground(inputBg);searchRow.addView(query,new LinearLayout.LayoutParams(0,dp(48),1f));

        searchButton=new Button(this);searchButton.setText("بحث");
        searchButton.setTextSize(13);searchButton.setOnClickListener(v->searchDestination());
        LinearLayout.LayoutParams searchLp=new LinearLayout.LayoutParams(dp(72),dp(46));
        searchLp.leftMargin=dp(5);searchRow.addView(searchButton,searchLp);

        modeToggle=new TextView(this);modeToggle.setText("وضع القيادة");
        modeToggle.setTextColor(Color.WHITE);modeToggle.setTextSize(12);
        modeToggle.setGravity(Gravity.CENTER);modeToggle.setPadding(dp(11),0,dp(11),0);
        modeToggle.setBackground(glass(0xFF12617A,0xFF54DBFA,12));
        LinearLayout.LayoutParams modeLp=new LinearLayout.LayoutParams(dp(104),dp(46));
        modeLp.leftMargin=dp(5);searchRow.addView(modeToggle,modeLp);
        modeToggle.setOnClickListener(v->toggleDisplayMode());
        top.addView(searchRow,new LinearLayout.LayoutParams(-1,dp(50)));

        maneuverBanner=text("اكتب وجهتك لبدء الملاحة",17,Color.WHITE,true);
        maneuverBanner.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
        maneuverBanner.setPadding(dp(15),dp(5),dp(15),dp(5));
        maneuverBanner.setBackground(glass(0xEE10283B,0xFF48BFE8,15));
        LinearLayout.LayoutParams bannerLp=new LinearLayout.LayoutParams(-1,dp(58));
        bannerLp.topMargin=dp(5);top.addView(maneuverBanner,bannerLp);
        FrameLayout.LayoutParams topLp=new FrameLayout.LayoutParams(-1,-2,Gravity.TOP);
        root.addView(top,topLp);

        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(10),dp(8),dp(10),dp(8));
        bottom.setBackground(glass(0xF20A1A29,0x9958CBEF,16));
        routeStatus=text("المسافة والوقت يظهران هنا",14,Color.WHITE,true);
        routeStatus.setGravity(Gravity.CENTER);routeStatus.setPadding(dp(6),dp(3),dp(6),dp(3));
        bottom.addView(routeStatus,new LinearLayout.LayoutParams(-1,dp(30)));
        trafficStatus=text("الازدحام المباشر غير متاح حالياً • الوقت تقديري",11,0xFFFFD78A,false);
        trafficStatus.setGravity(Gravity.CENTER);trafficStatus.setPadding(0,dp(2),0,dp(3));
        bottom.addView(trafficStatus,new LinearLayout.LayoutParams(-1,dp(25)));

        routeChoicesScroll=new HorizontalScrollView(this);
        routeChoicesScroll.setHorizontalScrollBarEnabled(false);routeChoicesScroll.setFillViewport(false);
        routeChoices=new LinearLayout(this);routeChoices.setOrientation(LinearLayout.HORIZONTAL);
        routeChoices.setGravity(Gravity.CENTER_VERTICAL);
        routeChoicesScroll.addView(routeChoices,new HorizontalScrollView.LayoutParams(-2,-1));
        bottom.addView(routeChoicesScroll,new LinearLayout.LayoutParams(-1,dp(48)));

        retryRouteButton=new Button(this);retryRouteButton.setText("إعادة محاولة حساب المسار");
        retryRouteButton.setTextSize(12);retryRouteButton.setVisibility(View.GONE);
        retryRouteButton.setOnClickListener(v->startRouteRequest());
        bottom.addView(retryRouteButton,new LinearLayout.LayoutParams(-1,dp(40)));

        FrameLayout.LayoutParams bottomLp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        bottomLp.setMargins(dp(7),0,dp(7),dp(7));root.addView(bottom,bottomLp);

        TextView attribution=text("© OpenStreetMap contributors",10,Color.WHITE,false);
        attribution.setPadding(dp(7),dp(3),dp(7),dp(3));
        attribution.setBackground(glass(0xCC101820,0x6678DFFF,7));
        FrameLayout.LayoutParams attributionLp=new FrameLayout.LayoutParams(-2,dp(24),Gravity.BOTTOM|Gravity.RIGHT);
        attributionLp.setMargins(dp(6),dp(6),dp(8),dp(154));
        root.addView(attribution,attributionLp);
        setContentView(root);

        query.setOnFocusChangeListener((view,hasFocus)->{
            keyboardRequested=hasFocus;
            if(hasFocus){
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }else{
                root.postDelayed(this::immersive,180);
            }
        });
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
        if(value.length()<2){query.setError("اكتب اسم وجهة صحيحة");return;}
        hideKeyboard();
        if(searchTask!=null)searchTask.cancel(true);
        if(routeTask!=null)routeTask.cancel(true);
        routeTask=null;routeOptions.clear();selectedRoute=null;waitingForGps=false;activeManeuverIndex=0;
        if(routeLine!=null){map.getOverlays().remove(routeLine);routeLine=null;}
        if(destinationMarker!=null){map.getOverlays().remove(destinationMarker);destinationMarker=null;}
        pendingDestination=null;pendingDestinationName=null;
        retryRouteButton.setVisibility(View.GONE);renderRouteChoices();
        loadLastKnownLocation();routeStatus.setText("جارٍ البحث عن الوجهة...");
        maneuverBanner.setText("يتم تحديد موقع الوجهة");
        searchTask=new AsyncTask<String,Void,NavResult>(){
            @Override protected NavResult doInBackground(String...args){return geocodeDestination(args[0]);}
            @Override protected void onPostExecute(NavResult result){if(!isFinishing())displayDestination(result);}
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,value);
    }
    private void hideKeyboard(){
        if(query==null)return;
        try{
            InputMethodManager input=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if(input!=null)input.hideSoftInputFromWindow(query.getWindowToken(),0);
        }catch(Exception ignored){}
        query.clearFocus();keyboardRequested=false;
    }
    private NavResult geocodeDestination(String text){
        try{
            String encoded=URLEncoder.encode(text,"UTF-8");
            String url="https://nominatim.openstreetmap.org/search?q="+encoded+
                    "&format=jsonv2&limit=5&addressdetails=1&accept-language=ar,en";
            JSONArray results=new JSONArray(request(url));
            if(results.length()==0)return NavResult.error("لم يتم العثور على الوجهة. جرّب اسماً أوضح.");
            JSONObject best=results.getJSONObject(0);double bestScore=-1;
            for(int i=0;i<results.length();i++){
                JSONObject candidate=results.getJSONObject(i);double score=candidate.optDouble("importance",0);
                String type=candidate.optString("type","");
                if("city".equals(type)||"town".equals(type)||"state".equals(type)||
                        "administrative".equals(type)||"road".equals(type)||"residential".equals(type))score+=1;
                if(score>bestScore){bestScore=score;best=candidate;}
            }
            return new NavResult(null,best.optString("display_name",text),
                    new GeoPoint(best.getDouble("lat"),best.getDouble("lon")));
        }catch(Exception error){return NavResult.error(NetworkCompat.arabicError(error));}
    }
    private void displayDestination(NavResult result){
        if(result==null||result.error!=null){
            String message=result==null?"حدث خطأ غير معروف":result.error;
            routeStatus.setText(message);maneuverBanner.setText("تعذر العثور على الوجهة — تحقق من الاسم أو الاتصال");return;
        }
        pendingDestination=result.destination;pendingDestinationName=result.name;
        if(destinationMarker!=null)map.getOverlays().remove(destinationMarker);
        destinationMarker=new Marker(map);destinationMarker.setPosition(pendingDestination);
        destinationMarker.setTitle(pendingDestinationName);destinationMarker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_BOTTOM);
        map.getOverlays().add(destinationMarker);
        if(currentLocation==null||!isLocationUsable(currentLocation)){
            waitingForGps=true;map.getController().setCenter(pendingDestination);map.getController().setZoom(13.5);
            routeStatus.setText("الوجهة: "+pendingDestinationName);
            maneuverBanner.setText("تم تحديد الوجهة — بانتظار موقع GPS حديث لرسم الطريق");
            map.invalidate();return;
        }
        startRouteRequest();
    }
    private boolean isLocationUsable(Location location){
        if(location==null)return false;
        long age=Math.max(0,System.currentTimeMillis()-location.getTime());
        return age<=180000L&&(!location.hasAccuracy()||location.getAccuracy()<=100f);
    }
    private void startRouteRequest(){
        if(pendingDestination==null){routeStatus.setText("ابحث عن وجهة أولاً");return;}
        if(routeTask!=null&&routeTask.getStatus()!=AsyncTask.Status.FINISHED)return;
        if(!isLocationUsable(currentLocation)){
            waitingForGps=true;routeStatus.setText("بانتظار موقع GPS حديث ودقيق لإيجاد الطريق");
            maneuverBanner.setText("لم يصل موقع صالح بعد — أبقِ الملاحة مفتوحة");return;
        }
        final Location origin=new Location(currentLocation);
        final GeoPoint destination=pendingDestination;
        final String name=pendingDestinationName;
        waitingForGps=false;retryRouteButton.setVisibility(View.GONE);
        routeStatus.setText("جارٍ حساب البدائل والمسافة والوقت...");
        maneuverBanner.setText("يتم تحليل الطرق المتاحة");
        lastRouteAttemptMs=SystemClock.elapsedRealtime();
        routeTask=new AsyncTask<Void,Void,RouteResult>(){
            @Override protected RouteResult doInBackground(Void...args){return requestRoutes(origin,destination,name);}
            @Override protected void onPostExecute(RouteResult result){
                routeTask=null;if(isFinishing())return;
                if(result==null||result.error!=null){
                    String message=result==null?"تعذر حساب المسار":result.error;
                    routeStatus.setText(message);maneuverBanner.setText("فشل حساب الطريق — يمكنك إعادة المحاولة");
                    retryRouteButton.setVisibility(View.VISIBLE);waitingForGps=false;return;
                }
                routeOptions=result.options;selectedRoute=null;activeManeuverIndex=0;
                renderRouteChoices();
                if(routeOptions.isEmpty()){
                    routeStatus.setText("لم يُرجع محرك الطرق مساراً صالحاً");retryRouteButton.setVisibility(View.VISIBLE);return;
                }
                selectRoutePreference(0,false);
                if(routeOptions.size()==1)
                    trafficStatus.setText("توفر مسار واحد فقط من الخادم • الازدحام المباشر غير متاح");
            }
            @Override protected void onCancelled(){routeTask=null;}
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    private RouteResult requestRoutes(Location origin,GeoPoint destination,String name){
        try{
            String url=String.format(Locale.US,
                    "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson&steps=true&alternatives=3",
                    origin.getLongitude(),origin.getLatitude(),destination.getLongitude(),destination.getLatitude());
            JSONObject response=new JSONObject(request(url));
            JSONArray routes=response.optJSONArray("routes");
            String code=response.optString("code","");
            if(!"Ok".equals(code)||routes==null||routes.length()==0){
                String message="NoRoute".equals(code)?"لم يعثر محرك الطرق على طريق قيادة متصل بين الموقع والوجهة":
                        "خدمة المسارات أعادت الحالة "+(code.isEmpty()?"غير معروفة":code);
                return RouteResult.error(message);
            }
            ArrayList<RouteOption> options=new ArrayList<>();
            for(int i=0;i<routes.length()&&i<4;i++){
                RouteOption option=parseRouteOption(routes.getJSONObject(i),i);
                if(option!=null&&option.points.size()>1)options.add(option);
            }
            if(options.isEmpty())return RouteResult.error("وصل رد من خدمة الطرق، لكنه لا يحتوي على هندسة صالحة للرسم");
            return new RouteResult(options);
        }catch(Exception error){return RouteResult.error("تعذر الاتصال بخدمة المسارات: "+NetworkCompat.arabicError(error));}
    }
    private RouteOption parseRouteOption(JSONObject route,int index)throws Exception{
        JSONObject geometry=route.optJSONObject("geometry");
        JSONArray coordinates=geometry==null?null:geometry.optJSONArray("coordinates");
        ArrayList<GeoPoint> points=new ArrayList<>();
        if(coordinates!=null){
            for(int i=0;i<coordinates.length()&&i<30000;i++){
                JSONArray pair=coordinates.optJSONArray(i);
                if(pair!=null&&pair.length()>=2)points.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));
            }
        }
        if(points.size()<2)return null;
        ArrayList<Maneuver> maneuvers=new ArrayList<>();int turns=0;
        JSONArray legs=route.optJSONArray("legs");
        if(legs!=null&&legs.length()>0){
            JSONArray steps=legs.getJSONObject(0).optJSONArray("steps");
            if(steps!=null){
                for(int i=0;i<steps.length()&&i<300;i++){
                    JSONObject step=steps.getJSONObject(i);JSONObject m=step.optJSONObject("maneuver");
                    if(m==null)continue;
                    String type=m.optString("type","");
                    if(!type.isEmpty()&&!"depart".equals(type)&&!"arrive".equals(type)&&
                            ("turn".equals(type)||"fork".equals(type)||"roundabout".equals(type)||
                             "rotary".equals(type)||"merge".equals(type)||"on ramp".equals(type)||"off ramp".equals(type)))turns++;
                    JSONArray loc=m.optJSONArray("location");
                    if(loc!=null&&loc.length()>=2&&!"depart".equals(type)){
                        GeoPoint p=new GeoPoint(loc.getDouble(1),loc.getDouble(0));
                        maneuvers.add(new Maneuver(p,maneuverText(step)));
                    }
                }
            }
        }
        int bends=countBends(points);
        return new RouteOption(index,points,maneuvers,route.optDouble("distance")/1000.0,
                route.optDouble("duration")/60.0,turns,bends);
    }
    private int countBends(ArrayList<GeoPoint> points){
        int bends=0;float previousBearing=Float.NaN;int lastCounted=-3;
        for(int i=1;i<points.size();i++){
            GeoPoint a=points.get(i-1),b=points.get(i);
            if(distanceMeters(a,b)<22)continue;
            float direction=bearing(a,b);
            if(!Float.isNaN(previousBearing)){
                float delta=Math.abs(((direction-previousBearing+540f)%360f)-180f);
                if(delta>=32f&&i-lastCounted>=2){bends++;lastCounted=i;}
            }
            previousBearing=direction;
        }
        return bends;
    }
    private float bearing(GeoPoint a,GeoPoint b){
        double lat1=Math.toRadians(a.getLatitude()),lat2=Math.toRadians(b.getLatitude());
        double dLon=Math.toRadians(b.getLongitude()-a.getLongitude());
        double y=Math.sin(dLon)*Math.cos(lat2);
        double x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);
        return (float)((Math.toDegrees(Math.atan2(y,x))+360.0)%360.0);
    }
    private double distanceMeters(GeoPoint a,GeoPoint b){
        float[] result=new float[1];
        Location.distanceBetween(a.getLatitude(),a.getLongitude(),b.getLatitude(),b.getLongitude(),result);
        return result[0];
    }
    private String maneuverText(JSONObject step){
        JSONObject maneuver=step.optJSONObject("maneuver");
        String type=maneuver==null?"":maneuver.optString("type","");
        String modifier=maneuver==null?"":maneuver.optString("modifier","");
        String road=step.optString("name","");
        String direction=modifier.contains("sharp left")?"انعطف بشدة يساراً":
                modifier.contains("slight left")?"انعطف قليلاً يساراً":
                modifier.contains("left")?"انعطف يساراً":
                modifier.contains("sharp right")?"انعطف بشدة يميناً":
                modifier.contains("slight right")?"انعطف قليلاً يميناً":
                modifier.contains("right")?"انعطف يميناً":
                modifier.contains("uturn")?"استدر للخلف":"تابع إلى الأمام";
        String action="arrive".equals(type)?"وصلت إلى الوجهة":
                ("roundabout".equals(type)||"rotary".equals(type))?"ادخل الدوار":"";
        if(action.isEmpty())action=direction;
        return action+(road.length()>0?" باتجاه "+road:"");
    }
    private RouteOption routeForPreference(int preference){
        if(routeOptions.isEmpty())return null;
        RouteOption best=routeOptions.get(0);
        for(RouteOption candidate:routeOptions){
            boolean better=preference==0?candidate.minutes<best.minutes:
                    preference==1?candidate.km<best.km:
                    preference==2?(candidate.turns*4+candidate.bends)<(best.turns*4+best.bends):
                    candidate.km>best.km;
            if(better)best=candidate;
        }
        return best;
    }
    private void renderRouteChoices(){
        if(routeChoices==null)return;
        routeChoices.removeAllViews();
        if(routeOptions.size()<2){
            TextView info=text(routeOptions.isEmpty()?"ستظهر خيارات الطريق بعد حساب المسار":
                    "الخادم أعاد مساراً واحداً فقط؛ لا توجد بدائل للمقارنة",11,0xFFCAD8E4,false);
            info.setGravity(Gravity.CENTER);routeChoices.addView(info,new LinearLayout.LayoutParams(-1,-1));return;
        }
        RouteOption fastest=routeForPreference(0),shortest=routeForPreference(1);
        RouteOption smooth=routeForPreference(2),longest=routeForPreference(3);
        addRouteChoice("الأسرع  "+Math.round(fastest.minutes)+" د",0,"الأسرع");
        addRouteChoice("الأقصر  "+String.format(Locale.US,"%.1f كم",shortest.km),1,"الأقصر");
        addRouteChoice("أقل انعطاف  "+smooth.turns,2,"الأقل انعطافاً");
        addRouteChoice("الأطول  "+String.format(Locale.US,"%.1f كم",longest.km),3,"الأطول");
    }
    private void addRouteChoice(String label,int preference,String key){
        TextView choice=text(label,12,Color.WHITE,true);
        choice.setGravity(Gravity.CENTER);choice.setPadding(dp(13),0,dp(13),0);
        boolean selected=key.equals(selectedPreference);
        choice.setBackground(glass(selected?0xFF087F9E:0xFF203649,
                selected?0xFF8BE8FF:0xFF5B7184,18));
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(38));
        lp.rightMargin=dp(6);routeChoices.addView(choice,lp);
        choice.setOnClickListener(v->selectRoutePreference(preference,true));
    }
    private void selectRoutePreference(int preference,boolean notify){
        if(routeOptions.isEmpty())return;
        if(routeOptions.size()==1&&notify){
            Toast.makeText(this,"محرك الطرق أعاد مساراً واحداً فقط، لذلك لا يمكن المقارنة بين البدائل",Toast.LENGTH_LONG).show();
            return;
        }
        selectedPreference=preference==0?"الأسرع":preference==1?"الأقصر":preference==2?"الأقل انعطافاً":"الأطول";
        selectedRoute=routeForPreference(preference);activeManeuverIndex=0;
        renderRouteChoices();drawSelectedRoute();
    }
    private void drawSelectedRoute(){
        if(selectedRoute==null)return;
        if(routeLine!=null)map.getOverlays().remove(routeLine);
        routeLine=new Polyline(map);routeLine.setPoints(selectedRoute.points);
        routeLine.setColor(0xFF00CFFF);routeLine.setWidth(dp(12));map.getOverlays().add(routeLine);
        if(currentLocation!=null){
            ensureCarMarker();moveCarMarker(new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude()),true);
        }
        routeStatus.setText(String.format(Locale.US,"%.1f كم  •  %.0f دقيقة تقديرية  •  %d بدائل",
                selectedRoute.km,selectedRoute.minutes,routeOptions.size()));
        trafficStatus.setText("لا توجد بيانات ازدحام مباشرة من مزوّد المسارات الحالي • الوقت تقديري");
        retryRouteButton.setVisibility(View.GONE);
        updateTurnInstruction(currentLocation);
        if(driverMode){
            map.getController().setZoom(17.0f);
            if(currentLocation!=null)map.getController().animateTo(new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude()));
        }else{
            map.setMapOrientation(0f);
            double north=-90,south=90,east=-180,west=180;
            for(GeoPoint p:selectedRoute.points){
                north=Math.max(north,p.getLatitude());south=Math.min(south,p.getLatitude());
                east=Math.max(east,p.getLongitude());west=Math.min(west,p.getLongitude());
            }
            map.zoomToBoundingBox(new BoundingBox(north,east,south,west),true);
        }
        map.invalidate();
    }
    private void updateTurnInstruction(Location location){
        if(selectedRoute==null||selectedRoute.maneuvers.isEmpty()){
            maneuverBanner.setText("اتبع الخط السماوي باتجاه الوجهة");return;
        }
        if(location==null){maneuverBanner.setText(selectedRoute.maneuvers.get(0).text);return;}
        while(activeManeuverIndex<selectedRoute.maneuvers.size()-1){
            Maneuver target=selectedRoute.maneuvers.get(activeManeuverIndex);
            double distance=distanceMeters(new GeoPoint(location.getLatitude(),location.getLongitude()),target.point);
            if(distance<28)activeManeuverIndex++;else break;
        }
        Maneuver next=selectedRoute.maneuvers.get(activeManeuverIndex);
        double metres=distanceMeters(new GeoPoint(location.getLatitude(),location.getLongitude()),next.point);
        String distance=metres>=1000?String.format(Locale.US,"%.1f كم",metres/1000.0):Math.round(metres)+" متر";
        String arrow=next.text.contains("يسار")?"↰":next.text.contains("يمين")?"↱":
                next.text.contains("الوجهة")?"●":"↑";
        maneuverBanner.setText(arrow+"  "+next.text+"  •  بعد "+distance);
    }
    private void toggleDisplayMode(){
        driverMode=!driverMode;
        modeToggle.setText(driverMode?"عرض الخريطة":"وضع القيادة");
        query.setVisibility(driverMode?View.GONE:View.VISIBLE);
        searchButton.setVisibility(driverMode?View.GONE:View.VISIBLE);
        routeChoicesScroll.setVisibility(driverMode?View.GONE:View.VISIBLE);
        trafficStatus.setVisibility(driverMode?View.GONE:View.VISIBLE);
        maneuverBanner.setTextSize(driverMode?21:17);
        LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)maneuverBanner.getLayoutParams();
        lp.height=dp(driverMode?78:58);maneuverBanner.setLayoutParams(lp);
        if(driverMode){
            if(currentLocation!=null){
                map.getController().setZoom(17.0);
                map.getController().animateTo(new GeoPoint(currentLocation.getLatitude(),currentLocation.getLongitude()));
                if(currentLocation.hasBearing())map.setMapOrientation(-currentLocation.getBearing());
            }
            updateTurnInstruction(currentLocation);
        }else{
            map.setMapOrientation(0f);
            if(selectedRoute!=null)drawSelectedRoute();
        }
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
        connection.setConnectTimeout(10000);connection.setReadTimeout(12000);
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
    @Override protected void onResume(){
        super.onResume();if(map!=null)map.onResume();if(!keyboardRequested)immersive();
        if(locationManager==null)return;
        boolean fine=checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        boolean coarse=checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        if(!fine&&!coarse){routeStatus.setText("يلزم السماح بإذن الموقع من إعدادات التطبيق");return;}
        boolean registered=false;
        try{
            if(locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)){
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,1000L,1f,this);registered=true;
            }
        }catch(Exception error){routeStatus.setText("تعذر تشغيل مستشعر GPS: "+NetworkCompat.arabicError(error));}
        try{
            if(locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)){
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,1800L,4f,this);registered=true;
            }
        }catch(Exception error){if(!registered)routeStatus.setText("تعذر تشغيل تحديد الموقع عبر الشبكة");}
        if(!registered)routeStatus.setText("فعّل GPS وخدمات الموقع ثم أعد المحاولة");
        loadLastKnownLocation();
        if(currentLocation!=null)onLocationChanged(currentLocation);
        if(pendingDestination!=null&&selectedRoute==null&&isLocationUsable(currentLocation))startRouteRequest();
    }
    @Override protected void onPause(){
        if(locationManager!=null){try{locationManager.removeUpdates(this);}catch(Exception ignored){}}
        if(carAnimator!=null){carAnimator.cancel();carAnimator=null;}
        if(map!=null)map.onPause();super.onPause();
    }
    @Override protected void onDestroy(){
        if(searchTask!=null)searchTask.cancel(true);if(routeTask!=null)routeTask.cancel(true);
        routeTask=null;searchTask=null;
        if(map!=null){map.onDetach();map=null;}super.onDestroy();
    }
    @Override public void onLocationChanged(Location location){
        if(location==null)return;
        currentLocation=new Location(location);
        GeoPoint point=new GeoPoint(location.getLatitude(),location.getLongitude());
        ensureCarMarker();moveCarMarker(point,false);
        if(driverMode&&location.hasBearing()&&location.hasSpeed()&&location.getSpeed()>0.8f){
            carMarker.setRotation(location.getBearing());
            map.setMapOrientation(-location.getBearing());
        }else if(!driverMode){
            map.setMapOrientation(0f);
        }
        if(waitingForGps&&pendingDestination!=null&&routeTask==null&&isLocationUsable(location)
                &&SystemClock.elapsedRealtime()-lastRouteAttemptMs>=5000L){
            startRouteRequest();
        }else if(waitingForGps&&pendingDestination!=null&&!isLocationUsable(location)){
            routeStatus.setText("إشارة الموقع ضعيفة حالياً — ننتظر دقة أفضل");
        }
        updateTurnInstruction(currentLocation);
        if(driverMode){
            map.getController().animateTo(point);
            if(map.getZoomLevelDouble()<16.5)map.getController().setZoom(17.0);
        }else if(firstFix&&selectedRoute==null){
            map.getController().animateTo(point);firstFix=false;
        }
        map.invalidate();
    }
    @Override public void onProviderDisabled(String provider){
        if(LocationManager.GPS_PROVIDER.equals(provider)){routeStatus.setText("نظام تحديد الموقع متوقف");}
    }
    @Override public void onProviderEnabled(String provider){if(LocationManager.GPS_PROVIDER.equals(provider))routeStatus.setText("تم تفعيل نظام تحديد الموقع");}
    @Override public void onStatusChanged(String provider,int status,Bundle extras){}

    private static final class NavResult{
        final String error,name;final GeoPoint destination;
        NavResult(String e,String n,GeoPoint d){error=e;name=n;destination=d;}
        static NavResult error(String value){return new NavResult(value,"",null);}
    }
    private static final class RouteResult{
        final ArrayList<RouteOption> options;final String error;
        RouteResult(ArrayList<RouteOption> values){options=values;error=null;}
        private RouteResult(String message){options=new ArrayList<>();error=message;}
        static RouteResult error(String message){return new RouteResult(message);}
    }
    private static final class RouteOption{
        final int index;final ArrayList<GeoPoint> points;final ArrayList<Maneuver> maneuvers;
        final double km,minutes;final int turns,bends;
        RouteOption(int i,ArrayList<GeoPoint> p,ArrayList<Maneuver> m,double k,double min,int t,int b){
            index=i;points=p;maneuvers=m;km=k;minutes=min;turns=t;bends=b;
        }
    }
    private static final class Maneuver{
        final GeoPoint point;final String text;
        Maneuver(GeoPoint p,String t){point=p;text=t;}
    }
}
