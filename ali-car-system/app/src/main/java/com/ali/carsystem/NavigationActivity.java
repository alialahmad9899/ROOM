package com.ali.carsystem;

import android.content.*;
import android.app.AlertDialog;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;
import android.text.InputType;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.GradientDrawable;
import android.widget.*;
import org.json.*;
import org.osmdroid.config.Configuration;
import org.osmdroid.mapsforge.MapsForgeTileProvider;
import org.osmdroid.mapsforge.MapsForgeTileSource;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.*;
import org.osmdroid.views.*;
import org.osmdroid.views.overlay.*;
import java.util.*;
import java.io.*;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public final class NavigationActivity extends BaseActivity implements LocationListener {
    private MapView map;private LocationManager lm;private Location current;
    private MapsForgeTileSource offlineTileSource;
    private static final int REQUEST_IMPORT_OFFLINE_MAPS=8401;
    private FusedLocationProviderClient fusedClient;private LocationCallback fusedCallback;
    private boolean fusedRegistered,fusedRequestPending,offlinePreparationRunning;private int gmsAvailabilityCode=-1;
    private String gmsLocationStatus="لم يبدأ اتصال Google Fused";
    private EditText search;private TextView status,maneuver,trafficInfo;private LinearLayout routeChoices;
    private Button freeModeButton,guidedModeButton,followButton,retryRouteButton,backgroundNavigationButton,stopBackgroundNavigationButton;
    private BroadcastReceiver navigationStateReceiver;
    private GeoPoint destination;private String destinationName="";private Polyline routeLine;private Marker destMarker,carMarker;
    private AsyncTask<Void,Void,RouteResult> routeTask;private AsyncTask<String,Void,GeoResult> geocodeTask;
    private List<Maneuver> activeManeuvers=Collections.emptyList();private List<RouteOption> routeOptions=Collections.emptyList();
    private int selectedRouteIndex=-1,routeRequestId,geocodeRequestId;private boolean guidedMode=true,followCar=true,routeAttemptedForDestination;
    private GeoPoint lastFollowPoint,previousCarPoint;private long lastFollowAt;
    private float lastCarHeading;
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
    private GradientDrawable rounded(int color,int stroke,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(stroke!=0)d.setStroke(dp(1),stroke);return d;}
    private void styleButton(Button b,String text,int color){b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(13);b.setAllCaps(false);b.setGravity(Gravity.CENTER);b.setPadding(dp(8),dp(3),dp(8),dp(3));b.setMinHeight(dp(40));b.setBackground(rounded(color,0x334F95B1,12));}
    private void setGuidedMode(boolean enabled){guidedMode=enabled;if(maneuver!=null)maneuver.setVisibility(enabled?View.VISIBLE:View.GONE);
        if(routeLine!=null){map.getOverlays().remove(routeLine);if(enabled)map.getOverlays().add(routeLine);}
        if(freeModeButton!=null)freeModeButton.setBackground(rounded(enabled?0xFF183447:0xFF087FA4,0x553B718B,12));
        if(guidedModeButton!=null)guidedModeButton.setBackground(rounded(enabled?0xFF087FA4:0xFF183447,0x553B718B,12));
        if(status!=null&&destination!=null)status.setText(enabled?"وضع القيادة الموجّهة":"وضع الخريطة الحرة — حرّك الخريطة وقرّبها بحرية");
        if(enabled&&current!=null&&followCar)centerOnCar(current);updateManeuver(current,activeManeuvers);map.invalidate();}
    private void updateFollowButton(){if(followButton!=null)styleButton(followButton,followCar?"تتبّع السيارة ✓":"تتبّع السيارة",followCar?0xFF087FA4:0xF20A1B2A);}
    private void centerOnCar(Location l){if(map==null||l==null)return;GeoPoint p=new GeoPoint(l.getLatitude(),l.getLongitude());map.getController().animateTo(p);lastFollowPoint=p;lastFollowAt=SystemClock.elapsedRealtime();}
    private BitmapDrawable createLocationDot(){
        int size=dp(36);Bitmap bitmap=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        float center=size/2f;
        paint.setColor(0x66000000);canvas.drawCircle(center,center+dp(1),dp(16),paint);
        paint.setColor(Color.WHITE);canvas.drawCircle(center,center,dp(15),paint);
        paint.setColor(0xFF168BFF);canvas.drawCircle(center,center,dp(12),paint);
        // Small red pointer rotates with GPS bearing; the marker itself is a dot, not a car graphic.
        Path pointer=new Path();pointer.moveTo(center,center-dp(9));
        pointer.lineTo(center+dp(4),center+dp(4));pointer.lineTo(center,center+dp(2));
        pointer.lineTo(center-dp(4),center+dp(4));pointer.close();
        paint.setColor(0xFFFF5866);canvas.drawPath(pointer,paint);
        paint.setColor(0xFFFFFFFF);canvas.drawCircle(center,center,dp(2),paint);
        return new BitmapDrawable(getResources(),bitmap);
    }
    private void addRouteChoice(String label,int index,boolean selected){Button b=new Button(this);styleButton(b,label,selected?0xFF087FA4:0xFF183447);b.setTextSize(11);b.setTag("route_"+index);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40));lp.setMargins(0,0,dp(7),0);routeChoices.addView(b,lp);b.setOnClickListener(v->selectRoute(index));}
    private void rebuildRouteChoices(){if(routeChoices==null)return;routeChoices.removeAllViews();if(routeOptions==null||routeOptions.isEmpty())return;int n=routeOptions.size();
        double[] d=new double[n],t=new double[n];int[] turns=new int[n];for(int i=0;i<n;i++){RouteOption o=routeOptions.get(i);d[i]=o.km;t[i]=o.minutes;turns[i]=o.turns;}
        int shortest=RouteChoiceLogic.shortestIndex(d),fastest=RouteChoiceLogic.fastestIndex(t),fewest=RouteChoiceLogic.fewestTurnsIndex(turns),longest=RouteChoiceLogic.longestIndex(d);
        addRouteChoice("الأقصر",shortest,selectedRouteIndex==shortest);if(fastest!=shortest)addRouteChoice("الأسرع",fastest,selectedRouteIndex==fastest);
        if(fewest!=shortest&&fewest!=fastest)addRouteChoice("أقل انعطافات",fewest,selectedRouteIndex==fewest);
        if(longest!=shortest&&longest!=fastest&&longest!=fewest)addRouteChoice("الأطول",longest,selectedRouteIndex==longest);
        if(n==1){TextView note=new TextView(this);note.setText("المحرك أعاد مساراً واحداً فقط");note.setTextColor(0xFFCBDCE4);note.setTextSize(11);note.setPadding(dp(5),dp(9),dp(5),0);routeChoices.addView(note);}}
    private void selectRoute(int index){if(index<0||routeOptions==null||index>=routeOptions.size())return;selectedRouteIndex=index;RouteOption o=routeOptions.get(index);
        if(routeLine!=null)map.getOverlays().remove(routeLine);routeLine=new Polyline(map);routeLine.setPoints(o.points);routeLine.setColor(0xFF00C8F0);routeLine.setWidth(dp(6));if(guidedMode)map.getOverlays().add(routeLine);
        status.setText(String.format(Locale.US,"المسافة %.1f كم  •  الوقت %.0f دقيقة  •  %d انعطاف",o.km,o.minutes,o.turns));
        activeManeuvers=o.maneuvers;updateManeuver(current,activeManeuvers);rebuildRouteChoices();
        if(backgroundNavigationButton!=null)backgroundNavigationButton.setEnabled(true);map.invalidate();}
    @Override protected void onCreate(Bundle b){super.onCreate(b);getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        Configuration.getInstance().load(this,getSharedPreferences("osmdroid",MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue("ALI-CAR/3.7.0 (Android 5; https://github.com/alialahmad9899/ROOM)");
        Configuration.getInstance().setTileFileSystemCacheMaxBytes(8L*1024*1024);
        Configuration.getInstance().setTileFileSystemCacheTrimBytes(4L*1024*1024);
        lm=(LocationManager)getSystemService(LOCATION_SERVICE);
        try{fusedClient=LocationServices.getFusedLocationProviderClient(this);}catch(Exception e){gmsLocationStatus="تعذر إنشاء عميل Google Fused: "+e.getClass().getSimpleName();}
        fusedCallback=new LocationCallback(){
            @Override public void onLocationResult(LocationResult result){
                if(result==null||result.getLastLocation()==null)return;
                Location fix=result.getLastLocation();
                onLocationChanged(fix);
                if(status!=null&&destination==null)status.setText("موقع Google Fused وصل • دقة ±"+(fix.hasAccuracy()?Math.round(fix.getAccuracy()):0)+" متر");
            }
        };
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(0xFF07111C);
        map=new MapView(this);map.setUseDataConnection(false);map.setMultiTouchControls(true);map.setTilesScaledToDpi(true);
        configureMapTiles();
        map.getController().setZoom(12.5);root.addView(map,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(12),dp(14),dp(12));
        panel.setBackground(rounded(0xF20A1B2A,0xFF24465B,18));
        TextView heading=new TextView(this);heading.setText("ALI CAR  •  NAVIGATION");heading.setTextColor(0xFF69D8F2);heading.setTextSize(11);heading.setGravity(Gravity.RIGHT);panel.addView(heading);
        TextView subheading=new TextView(this);subheading.setText("الملاحة الذكية");subheading.setTextColor(Color.WHITE);subheading.setTextSize(20);subheading.setGravity(Gravity.RIGHT);subheading.setPadding(0,dp(2),0,dp(8));panel.addView(subheading);
        LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);
        search=new EditText(this);search.setSingleLine(true);search.setTextColor(0xFF10202B);search.setHintTextColor(0xFF73818B);search.setHint("اكتب اسم المدينة أو الوجهة");search.setTextSize(15);search.setPadding(dp(12),0,dp(12),0);
        search.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);search.setBackground(rounded(0xFFF4F7F9,0xFFD5E0E6,12));
        line.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
        Button go=new Button(this);styleButton(go,"ابحث",0xFF087FA4);LinearLayout.LayoutParams goLp=new LinearLayout.LayoutParams(dp(88),dp(48));goLp.setMargins(dp(7),0,0,0);line.addView(go,goLp);panel.addView(line);
        LinearLayout modes=new LinearLayout(this);modes.setOrientation(LinearLayout.HORIZONTAL);modes.setPadding(0,dp(8),0,dp(5));
        freeModeButton=new Button(this);styleButton(freeModeButton,"خريطة حرة",0xFF183447);freeModeButton.setTag("navigation_free_mode");
        guidedModeButton=new Button(this);styleButton(guidedModeButton,"قيادة موجّهة",0xFF087FA4);guidedModeButton.setTag("navigation_guided_mode");
        modes.addView(freeModeButton,new LinearLayout.LayoutParams(0,dp(42),1));
        LinearLayout.LayoutParams glp=new LinearLayout.LayoutParams(0,dp(42),1);glp.setMargins(dp(7),0,0,0);modes.addView(guidedModeButton,glp);panel.addView(modes);
        status=new TextView(this);status.setTextColor(0xFFD5E5EC);status.setTextSize(12);status.setGravity(Gravity.RIGHT);status.setText("جارٍ تحديد موقعك...");status.setPadding(0,dp(4),0,dp(3));panel.addView(status);
        retryRouteButton=new Button(this);retryRouteButton.setTag("navigation_retry_route");
        styleButton(retryRouteButton,"إعادة محاولة حساب المسار",0xFF087FA4);retryRouteButton.setTextSize(12);
        retryRouteButton.setVisibility(View.GONE);panel.addView(retryRouteButton,new LinearLayout.LayoutParams(-1,dp(38)));
                Button offlineMapsButton=new Button(this);styleButton(offlineMapsButton,"استيراد خريطة سوريا والمسارات دون إنترنت",0xFF1B665F);offlineMapsButton.setTextSize(12);
        panel.addView(offlineMapsButton,new LinearLayout.LayoutParams(-1,dp(42)));
        TextView offlineStatus=new TextView(this);offlineStatus.setTextColor(0xFF9BD9D5);offlineStatus.setTextSize(10);offlineStatus.setText(OfflineMapsManager.status(this));offlineStatus.setGravity(Gravity.RIGHT);panel.addView(offlineStatus);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(560),-2,Gravity.TOP|Gravity.LEFT);pp.setMargins(dp(16),dp(16),dp(16),0);root.addView(panel,pp);
        followButton=new Button(this);styleButton(followButton,"تتبّع السيارة ✓",0xF20A1B2A);FrameLayout.LayoutParams flp=new FrameLayout.LayoutParams(dp(150),dp(44),Gravity.TOP|Gravity.RIGHT);flp.setMargins(0,dp(18),dp(18),0);root.addView(followButton,flp);
        LinearLayout bottom=new LinearLayout(this);bottom.setOrientation(LinearLayout.VERTICAL);bottom.setPadding(dp(14),dp(10),dp(14),dp(10));bottom.setBackground(rounded(0xF20A1B2A,0xFF24465B,18));
        maneuver=new TextView(this);maneuver.setTag("navigation_maneuver");maneuver.setTextColor(Color.WHITE);maneuver.setTextSize(20);maneuver.setGravity(Gravity.RIGHT);maneuver.setText("بانتظار اختيار وجهة");maneuver.setPadding(0,0,0,dp(5));bottom.addView(maneuver);
        trafficInfo=new TextView(this);trafficInfo.setTextColor(0xFFFFD28A);trafficInfo.setTextSize(11);trafficInfo.setGravity(Gravity.RIGHT);trafficInfo.setText("الازدحام المباشر غير متاح من مصدر الخرائط الحالي");bottom.addView(trafficInfo);
        HorizontalScrollView routeScroll=new HorizontalScrollView(this);routeScroll.setHorizontalScrollBarEnabled(false);
        routeChoices=new LinearLayout(this);routeChoices.setTag("navigation_route_choices");routeChoices.setOrientation(LinearLayout.HORIZONTAL);routeChoices.setPadding(0,dp(7),0,0);routeScroll.addView(routeChoices,new HorizontalScrollView.LayoutParams(-2,-2));bottom.addView(routeScroll);
        LinearLayout backgroundActions=new LinearLayout(this);backgroundActions.setOrientation(LinearLayout.HORIZONTAL);
        backgroundNavigationButton=new Button(this);backgroundNavigationButton.setTag("navigation_start_background");
        styleButton(backgroundNavigationButton,"تشغيل المسار بالخلفية",0xFF087FA4);backgroundNavigationButton.setTextSize(12);
        backgroundNavigationButton.setEnabled(false);
        stopBackgroundNavigationButton=new Button(this);stopBackgroundNavigationButton.setTag("navigation_stop_background");
        styleButton(stopBackgroundNavigationButton,"إيقاف الملاحة",0xFF8A3340);stopBackgroundNavigationButton.setTextSize(12);
        backgroundNavigationButton.setOnClickListener(v->startBackgroundNavigation());
        stopBackgroundNavigationButton.setOnClickListener(v->stopBackgroundNavigation());
        backgroundActions.addView(backgroundNavigationButton,new LinearLayout.LayoutParams(0,dp(42),1));
        LinearLayout.LayoutParams stopLp=new LinearLayout.LayoutParams(0,dp(42),1);stopLp.setMargins(dp(7),0,0,0);
        backgroundActions.addView(stopBackgroundNavigationButton,stopLp);bottom.addView(backgroundActions);

        FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(dp(660),-2,Gravity.BOTTOM|Gravity.LEFT);bp.setMargins(dp(16),0,dp(16),dp(16));root.addView(bottom,bp);
        TextView attribution=new TextView(this);attribution.setText("© OpenStreetMap contributors");attribution.setTextColor(-1);attribution.setTextSize(10);attribution.setBackground(rounded(0xAA061522,0x553B6074,8));attribution.setPadding(dp(8),dp(5),dp(8),dp(5));
        FrameLayout.LayoutParams ap=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.RIGHT);ap.setMargins(0,0,dp(8),dp(7));root.addView(attribution,ap);
        setContentView(root);go.setOnClickListener(v->searchDestination());search.setOnEditorActionListener((v,a,e)->{searchDestination();return true;});
        freeModeButton.setOnClickListener(v->setGuidedMode(false));guidedModeButton.setOnClickListener(v->setGuidedMode(true));
        followButton.setOnClickListener(v->{followCar=!followCar;updateFollowButton();if(followCar&&current!=null)centerOnCar(current);});
                offlineMapsButton.setOnClickListener(v->startOfflineMapImport());
        retryRouteButton.setOnClickListener(v->drawRouteIfPossible());
        loadLastKnown();if(current!=null)showCar(current);status.setOnClickListener(v->showGpsDiagnostics());setGuidedMode(true);ensureBundledOfflineData();
        stopBackgroundNavigationButton.setEnabled(NavigationGuidanceService.isActive(this));
        navigationStateReceiver=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){
            if(!NavigationGuidanceService.ACTION_STATE.equals(i.getAction()))return;
            boolean active=i.getBooleanExtra("active",false);
            String guidance=i.getStringExtra("guidance");
            if(maneuver!=null&&guidance!=null)maneuver.setText(guidance);
            if(stopBackgroundNavigationButton!=null)stopBackgroundNavigationButton.setEnabled(active);
        }};
        registerReceiver(navigationStateReceiver,new IntentFilter(NavigationGuidanceService.ACTION_STATE));
    }
    private String buildBackgroundRoutePayload() throws Exception {
        if(selectedRouteIndex<0||routeOptions==null||selectedRouteIndex>=routeOptions.size())return null;
        RouteOption option=routeOptions.get(selectedRouteIndex);
        JSONObject payload=new JSONObject();
        JSONArray points=new JSONArray();
        for(GeoPoint point:option.points)points.put(new JSONArray().put(point.getLatitude()).put(point.getLongitude()));
        JSONArray turns=new JSONArray();
        for(Maneuver turn:option.maneuvers){
            turns.put(new JSONObject().put("lat",turn.point.getLatitude())
                    .put("lon",turn.point.getLongitude()).put("text",turn.text));
        }
        payload.put("points",points);payload.put("maneuvers",turns);
        payload.put("destination_name",destinationName);
        payload.put("destination_lat",destination.getLatitude());
        payload.put("destination_lon",destination.getLongitude());
        payload.put("duration_seconds",option.minutes*60d);
        return payload.toString();
    }
    private void startBackgroundNavigation(){
        if(destination==null||selectedRouteIndex<0){
            Toast.makeText(this,"احسب المسار واختره أولاً",Toast.LENGTH_LONG).show();return;
        }
        try{
            String payload=buildBackgroundRoutePayload();
            if(payload==null||payload.isEmpty())throw new IllegalStateException("بيانات المسار غير جاهزة");
            Intent intent=new Intent(this,NavigationGuidanceService.class);
            intent.setAction(NavigationGuidanceService.ACTION_START);
            intent.putExtra(NavigationGuidanceService.EXTRA_ROUTE_JSON,payload);
            intent.putExtra(NavigationGuidanceService.EXTRA_DEST_NAME,destinationName);
            intent.putExtra(NavigationGuidanceService.EXTRA_DEST_LAT,destination.getLatitude());
            intent.putExtra(NavigationGuidanceService.EXTRA_DEST_LON,destination.getLongitude());
            startService(intent);
            if(stopBackgroundNavigationButton!=null)stopBackgroundNavigationButton.setEnabled(true);
            Toast.makeText(this,"تم حفظ المسار وتشغيل خدمة الملاحة الخلفية",Toast.LENGTH_LONG).show();
        }catch(Exception error){
            Toast.makeText(this,"تعذر تشغيل المسار بالخلفية: "+error.getMessage(),Toast.LENGTH_LONG).show();
        }
    }
    private void stopBackgroundNavigation(){
        Intent intent=new Intent(this,NavigationGuidanceService.class);
        intent.setAction(NavigationGuidanceService.ACTION_STOP);
        startService(intent);
        if(stopBackgroundNavigationButton!=null)stopBackgroundNavigationButton.setEnabled(false);
    }

    private void searchDestination(){
        if(offlinePreparationRunning){
            status.setText("جارٍ تجهيز خريطة سوريا والأماكن للعمل دون إنترنت...");
            return;
        }
        String q=search.getText().toString().trim();if(q.length()<2){search.setError("أدخل اسم وجهة");return;}
        long now=SystemClock.elapsedRealtime();if(now-lastSearch<1100){status.setText("انتظر لحظة بين عمليات البحث");return;}lastSearch=now;
        InputMethodManager keyboard=(InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if(keyboard!=null)keyboard.hideSoftInputFromWindow(search.getWindowToken(),0);search.clearFocus();
        if(geocodeTask!=null)geocodeTask.cancel(true);final int requestId=++geocodeRequestId;
        if(routeTask!=null){routeRequestId++;routeTask.cancel(true);routeTask=null;}
        routeAttemptedForDestination=false;routeOptions=Collections.emptyList();selectedRouteIndex=-1;activeManeuvers=Collections.emptyList();
        if(routeLine!=null){map.getOverlays().remove(routeLine);routeLine=null;}routeChoices.removeAllViews();
        status.setText("جارٍ البحث محلياً في قاعدة بيانات سوريا...");maneuver.setText("البحث المحلي دون إنترنت");
        geocodeTask=new AsyncTask<String,Void,GeoResult>(){
            protected GeoResult doInBackground(String...v){
                try{
                    ArrayList<GeoCandidate> candidates=new ArrayList<>();
                    List<OfflinePlacesDb.Place> local=OfflinePlacesDb.search(getApplicationContext(),v[0],8);
                    for(OfflinePlacesDb.Place place:local)candidates.add(new GeoCandidate(place.point,place.label(),"قاعدة سوريا المحلية"));
                    return candidates.isEmpty()
                            ?new GeoResult("لم يتم العثور على «"+v[0]+"» في قاعدة الأماكن المحلية. البحث الخارجي معطّل لضمان العمل دون إنترنت.")
                            :new GeoResult(candidates);
                }catch(Exception e){return new GeoResult(e.getMessage());}
            }
            protected void onPostExecute(GeoResult r){
                if(requestId!=geocodeRequestId||isFinishing())return;geocodeTask=null;
                if(r.error!=null){status.setText("تعذر البحث عن الوجهة: "+r.error);return;}
                chooseDestination(r.candidates);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR,q);
    }
    private void configureMapTiles(){
        if(map==null)return;
        File[] maps=OfflineMapsManager.getMapFiles(this);
        if(maps.length==0){if(status!=null)status.setText("الخريطة المحلية غير مثبتة — لا يتم تحميل خرائط من الإنترنت");return;}
        try{
            if(offlineTileSource==null){
                MapsForgeTileSource.createInstance(getApplication());
                offlineTileSource=MapsForgeTileSource.createFromFiles(maps,null,"ALI-CAR-SYRIA-OFFLINE","ar");
            }
            map.setTileProvider(new MapsForgeTileProvider(new SimpleRegisterReceiver(this),offlineTileSource,null));
            if(status!=null)status.setText("خريطة سوريا المحلية جاهزة • "+OfflineMapsManager.status(this));
        }catch(Exception error){
            offlineTileSource=null;
            map.setUseDataConnection(false);
            if(status!=null)status.setText("تعذر فتح الخريطة المحلية: "+error.getClass().getSimpleName()+" — تم إيقاف اتصال الخرائط بالإنترنت");
        }
    }

    private void ensureBundledOfflineData(){
        if(OfflineMapsManager.getMapFile(this)!=null&&OfflineMapsManager.getPlacesDatabase(this)!=null&&OfflineMapsManager.hasOfflineRouting(this))return;
        offlinePreparationRunning=true;
        status.setText("جارٍ تجهيز الخريطة المحلية...");
        new AsyncTask<Void,Void,String>(){
            protected String doInBackground(Void...ignored){
                try{OfflineMapsManager.ensureBundledAssets(getApplicationContext());return null;}
                catch(Exception error){return error.getMessage()==null?error.getClass().getSimpleName():error.getMessage();}
            }
            protected void onPostExecute(String error){
                offlinePreparationRunning=false;
                if(isFinishing())return;
                configureMapTiles();
                String packageState=OfflineMapsManager.status(NavigationActivity.this);
                if(error!=null)status.setText("تعذر تجهيز خريطة سوريا المضمّنة: "+error);
                else status.setText(packageState);
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void startOfflineMapImport(){
        try{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("application/zip");
            startActivityForResult(pick,REQUEST_IMPORT_OFFLINE_MAPS);
        }catch(Exception error){new AlertDialog.Builder(this).setMessage("تعذر فتح مدير الملفات").setPositiveButton("موافق",null).show();}
    }

    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode!=REQUEST_IMPORT_OFFLINE_MAPS||resultCode!=RESULT_OK||data==null||data.getData()==null)return;
        final Uri source=data.getData();status.setText("جارٍ التحقق من حزمة خرائط سوريا واستيرادها...");
        new AsyncTask<Void,Void,String>(){
            protected String doInBackground(Void...v){try{OfflineMapsManager.installArchive(getApplicationContext(),source);return null;}catch(Exception e){return e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();}}
            protected void onPostExecute(String error){
                if(isFinishing())return;
                if(error!=null){status.setText("فشل استيراد الخريطة: "+error);return;}
                offlineTileSource=null;configureMapTiles();status.setText(OfflineMapsManager.status(NavigationActivity.this));
                new AlertDialog.Builder(NavigationActivity.this).setTitle("خرائط سوريا جاهزة").setMessage(OfflineMapsManager.status(NavigationActivity.this))
                        .setPositiveButton("موافق",null).show();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void chooseDestination(List<GeoCandidate> candidates){
        if(candidates==null||candidates.isEmpty()){status.setText("لم يتم العثور على الوجهة");return;}
        if(candidates.size()==1){setDestination(candidates.get(0));return;}
        String[] labels=new String[candidates.size()];
        for(int i=0;i<candidates.size();i++){GeoCandidate c=candidates.get(i);labels[i]=c.name+"  •  "+c.source;}
        new AlertDialog.Builder(this).setTitle("اختر الموقع المقصود").setItems(labels,(dialog,which)->setDestination(candidates.get(which))).setNegativeButton("إلغاء",null).show();
    }

    private void setDestination(GeoCandidate candidate){
        destination=candidate.point;destinationName=candidate.name;routeAttemptedForDestination=false;
        showDestination();drawRouteIfPossible();
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

        startGoogleFusedLocation();
        loadLastKnown();
        if(!gpsOn&&!networkOn&&gmsAvailabilityCode!=ConnectionResult.SUCCESS)status.setText("خدمات الموقع متوقفة — اضغط لعرض التشخيص");
        else if(current!=null&&isNavigationFixUsable(current))
            status.setText("تم استقبال موقع حديث من "+lastFixProvider+" — دقة ±"+Math.round(lastFixAccuracy)+" متر");
        else if(!providerError.isEmpty())status.setText(providerError+" — اضغط لعرض التشخيص");
        else status.setText(gpsStatusText());
    }
    private void startGoogleFusedLocation(){
        if(!hasLocationPermission()){
            gmsLocationStatus="لم يبدأ: إذن الموقع غير ممنوح";return;
        }
        if(fusedClient==null){
            try{fusedClient=LocationServices.getFusedLocationProviderClient(this);}
            catch(Exception e){gmsLocationStatus="تعذر إنشاء Google Fused: "+e.getClass().getSimpleName();return;}
        }
        gmsAvailabilityCode=GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this);
        if(gmsAvailabilityCode!=ConnectionResult.SUCCESS){
            String reason=GoogleApiAvailability.getInstance().getErrorString(gmsAvailabilityCode);
            gmsLocationStatus="Google Play services غير جاهزة (code "+gmsAvailabilityCode+") "+reason;
            return;
        }
        if(fusedRegistered||fusedRequestPending)return;
        fusedRequestPending=true;gmsLocationStatus="Google Fused يتصل ويطلب موقعاً حديثاً";
        try{
            fusedClient.getLocationAvailability()
                    .addOnSuccessListener(availability->{
                        if(availability==null)gmsLocationStatus="Google Fused: لم يُرجع حالة التوفر";
                        else gmsLocationStatus="Google Fused متاح="+availability.isLocationAvailable();
                    })
                    .addOnFailureListener(error->gmsLocationStatus="LocationAvailability: "+error.getClass().getSimpleName());
            fusedClient.getLastLocation()
                    .addOnSuccessListener(location->{
                        if(location!=null){
                            onLocationChanged(location);
                            gmsLocationStatus="استلمنا موقعاً محفوظاً من Google Fused";
                        }else gmsLocationStatus="Google Fused لا يملك موقعاً محفوظاً؛ ننتظر تحديد موقع جديد";
                    })
                    .addOnFailureListener(error->gmsLocationStatus="قراءة آخر موقع فشلت: "+error.getClass().getSimpleName());
            LocationRequest request=LocationRequest.create();
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
            request.setInterval(1000);
            request.setFastestInterval(500);
            request.setSmallestDisplacement(0);
            fusedClient.requestLocationUpdates(request,fusedCallback,Looper.getMainLooper())
                    .addOnSuccessListener(ignored->{
                        fusedRequestPending=false;fusedRegistered=true;
                        gmsLocationStatus="Google Fused يسجّل تحديثات الموقع عالية الدقة";
                        if(status!=null&&destination!=null&&waitingForFix)status.setText(gpsStatusText());
                    })
                    .addOnFailureListener(error->{
                        fusedRequestPending=false;fusedRegistered=false;
                        gmsLocationStatus="فشل طلب Google Fused: "+error.getClass().getSimpleName()+" — "+error.getMessage();
                        if(status!=null)status.setText("فشل محرك Google للموقع؛ GPS المباشر ما زال يعمل");
                    });
        }catch(SecurityException e){
            fusedRequestPending=false;gmsRegisteredFailure("صلاحية الموقع مرفوضة: "+e.getMessage());
        }catch(Exception e){
            fusedRequestPending=false;gmsRegisteredFailure(e.getClass().getSimpleName()+": "+e.getMessage());
        }
    }
    private void gmsRegisteredFailure(String reason){
        fusedRegistered=false;gmsLocationStatus="Google Fused: "+reason;
    }
    private void stopGoogleFusedLocation(){
        if(fusedClient!=null&&fusedCallback!=null){
            try{fusedClient.removeLocationUpdates(fusedCallback);}catch(Exception ignored){}
        }
        fusedRegistered=false;fusedRequestPending=false;
    }
    private void restartGoogleFusedLocation(){
        if(fusedClient==null||fusedCallback==null){startGoogleFusedLocation();return;}
        try{
            fusedClient.removeLocationUpdates(fusedCallback).addOnCompleteListener(task->{
                fusedRegistered=false;fusedRequestPending=false;startGoogleFusedLocation();
            });
        }catch(Exception e){fusedRegistered=false;fusedRequestPending=false;startGoogleFusedLocation();}
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
        b.append("مسار Android Criteria: ").append(criteriaRegistered?"تم تسجيل الطلب":"لم يتم تسجيل الطلب").append(" • أفضل مزود: ").append(criteriaProvider).append("\n");
        b.append("Google Play services availability: ").append(gmsAvailabilityCode==ConnectionResult.SUCCESS?"متاحة":gmsAvailabilityCode<0?"لم يتم الفحص":gmsAvailabilityCode+" ("+GoogleApiAvailability.getInstance().getErrorString(gmsAvailabilityCode)+")").append("\n");
        b.append("Google Fused Location: ").append(fusedRegistered?"يستقبل تحديثات":fusedRequestPending?"طلب الموقع قيد التنفيذ":"غير مسجّل").append(" • ").append(gmsLocationStatus).append("\n");
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
                    current=null;requestLocationSources();restartGoogleFusedLocation();
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
        GeoPoint p=new GeoPoint(l.getLatitude(),l.getLongitude());
        if(carMarker==null){
            carMarker=new Marker(map);carMarker.setTitle("الموقع الحالي واتجاه الحركة");
            carMarker.setIcon(createLocationDot());carMarker.setAnchor(Marker.ANCHOR_CENTER,Marker.ANCHOR_CENTER);
            map.getOverlays().add(carMarker);
        }
        double movedMeters=previousCarPoint==null?Double.MAX_VALUE:
                GeoMath.distanceMeters(previousCarPoint.getLatitude(),previousCarPoint.getLongitude(),
                        p.getLatitude(),p.getLongitude());
        if(l.hasBearing()&&(!l.hasSpeed()||l.getSpeed()>0.5f)){
            lastCarHeading=l.getBearing();
        }else if(previousCarPoint!=null&&movedMeters>4.0){
            double lat1=Math.toRadians(previousCarPoint.getLatitude());
            double lat2=Math.toRadians(p.getLatitude());
            double dLon=Math.toRadians(p.getLongitude()-previousCarPoint.getLongitude());
            double y=Math.sin(dLon)*Math.cos(lat2);
            double x=Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(dLon);
            lastCarHeading=(float)((Math.toDegrees(Math.atan2(y,x))+360.0)%360.0);
        }
        carMarker.setPosition(p);carMarker.setRotation(lastCarHeading);
        if(previousCarPoint==null||movedMeters>4.0)previousCarPoint=p;
        map.invalidate();
    }
    private void drawRouteIfPossible(){
        if(destination==null)return;
        if(!isNavigationFixUsable(current)){waitingForFix=true;status.setText("تم تحديد الوجهة — "+gpsStatusText());return;}
        requestRoute(current,destination,destinationName);
    }
    private void requestRoute(Location start,GeoPoint end,String name){
        // Keep one request in flight; failed requests are not restarted by GPS callbacks.
        if(routeTask!=null)return;routeAttemptedForDestination=true;final int requestId=++routeRequestId;
        final Location routeStart=new Location(start);final GeoPoint routeEnd=end;status.setText("جارٍ حساب المسار محلياً دون إنترنت...");
        if(retryRouteButton!=null)retryRouteButton.setVisibility(View.GONE);
        routeTask=new AsyncTask<Void,Void,RouteResult>(){
            protected RouteResult doInBackground(Void...v){try{
                JSONObject root=OfflineRoutingEngine.requestRoutes(getApplicationContext(),routeStart,routeEnd);
                if(root==null)throw new IOException("بيانات التوجيه المحلية غير مثبتة أو غير مكتملة؛ لا توجد خدمة مسارات عبر الإنترنت كبديل");
                if(!"Ok".equals(root.optString("code")))return new RouteResult("محرك المسارات رفض الطلب: "+root.optString("message",root.optString("code","خطأ غير معروف")));
                JSONArray routes=root.getJSONArray("routes");if(routes.length()==0)return new RouteResult("لم يعثر محرك المسارات على طريق قيادة");
                ArrayList<RouteOption> options=new ArrayList<>();
                for(int ri=0;ri<routes.length();ri++){
                    JSONObject route=routes.getJSONObject(ri);JSONArray coords=route.getJSONObject("geometry").getJSONArray("coordinates");ArrayList<GeoPoint> points=new ArrayList<>();
                    for(int i=0;i<coords.length();i++){JSONArray pair=coords.getJSONArray(i);points.add(new GeoPoint(pair.getDouble(1),pair.getDouble(0)));}
                    ArrayList<Maneuver> maneuvers=new ArrayList<>();int turns=0;JSONArray legs=route.optJSONArray("legs");
                    if(legs!=null)for(int li=0;li<legs.length();li++){JSONArray steps=legs.getJSONObject(li).optJSONArray("steps");if(steps==null)continue;
                        for(int i=0;i<steps.length();i++){JSONObject step=steps.getJSONObject(i);JSONObject m=step.optJSONObject("maneuver");if(m==null)continue;String type=m.optString("type","");
                            if("turn".equals(type)||"fork".equals(type)||"end of road".equals(type)||"roundabout".equals(type)||"rotary".equals(type)||"roundabout turn".equals(type)||"uturn".equals(type))turns++;
                            JSONArray loc=m.optJSONArray("location");if(loc!=null&&loc.length()>=2)maneuvers.add(new Maneuver(new GeoPoint(loc.getDouble(1),loc.getDouble(0)),instruction(step)));
                        }
                    }
                    if(points.size()>1)options.add(new RouteOption(points,route.optDouble("distance")/1000,route.optDouble("duration")/60,maneuvers,turns));
                }
                return options.isEmpty()?new RouteResult("وصل رد المسارات دون هندسة صالحة"):new RouteResult(options,root.optString("_ali_provider",""));
            }catch(Exception e){String detail=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage();return new RouteResult("تعذر حساب المسار المحلي دون إنترنت: "+detail);}}
            protected void onPostExecute(RouteResult r){
                if(requestId!=routeRequestId||isFinishing())return;routeTask=null;
                if(r.error!=null){
                    waitingForFix=false;routeAttemptedForDestination=true;
                    status.setText(r.error);maneuver.setText("لم يتم تحميل المسار");
                    if(retryRouteButton!=null)retryRouteButton.setVisibility(View.VISIBLE);
                    trafficInfo.setText("تحقق من تثبيت حزمة خريطة سوريا وملفات BRouter المحلية ثم أعد المحاولة • لا يلزم الإنترنت");
                    return;
                }
                if(retryRouteButton!=null)retryRouteButton.setVisibility(View.GONE);
                routeOptions=r.options;selectedRouteIndex=RouteChoiceLogic.shortestIndex(routeDistances(routeOptions));if(selectedRouteIndex<0)selectedRouteIndex=0;
                rebuildRouteChoices();selectRoute(selectedRouteIndex);BoundingBox bounds=BoundingBox.fromGeoPoints(routeOptions.get(selectedRouteIndex).points);
                map.zoomToBoundingBox(bounds,true);waitingForFix=false;map.invalidate();
                trafficInfo.setText("BRouter المحلي • "+routeOptions.size()+" مسار/مسارات • حساب كامل دون إنترنت؛ الازدحام المباشر غير متاح");
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
    private static double[] routeDistances(List<RouteOption> o){double[] a=new double[o.size()];for(int i=0;i<a.length;i++)a[i]=o.get(i).km;return a;}
    private static String instruction(JSONObject step){
        JSONObject m=step.optJSONObject("maneuver");String type=m==null?"":m.optString("type","");String mod=m==null?"":m.optString("modifier","");
        String road=step.optString("name","");String dir=mod.contains("left")?"يسار":mod.contains("right")?"يمين":mod.contains("uturn")?"استدر":"مباشرة";
        int exit=m==null?0:m.optInt("exit",0);
        String action="arrive".equals(type)?"وصلت إلى الوجهة"
                :"roundabout".equals(type)?(exit>0?"ادخل الدوار وخذ المخرج رقم "+exit:"ادخل الدوار")
                :"تابع "+dir;
        return action+(road.isEmpty()?"":" باتجاه "+road);
    }
    private void updateManeuver(Location l,List<Maneuver> steps){
        if(maneuver==null||!guidedMode)return;if(steps==null||steps.isEmpty()||l==null){maneuver.setText("اتبع الخط السماوي");return;}
        double best=Double.MAX_VALUE;String next=steps.get(steps.size()-1).text;
        for(Maneuver m:steps){double d=GeoMath.distanceMeters(l.getLatitude(),l.getLongitude(),m.point.getLatitude(),m.point.getLongitude());if(d>15&&d<best){best=d;next=m.text+" — بعد "+Math.round(d)+" متر";}}
        maneuver.setText(next);
    }
    @Override public void onLocationChanged(Location l){
        if(l==null)return;current=new Location(l);showCar(l);updateManeuver(current,activeManeuvers);
        if(guidedMode&&followCar&&map!=null){long now=SystemClock.elapsedRealtime();GeoPoint p=new GeoPoint(l.getLatitude(),l.getLongitude());
            if(now-lastFollowAt>1800&&(lastFollowPoint==null||GeoMath.distanceMeters(lastFollowPoint.getLatitude(),lastFollowPoint.getLongitude(),p.getLatitude(),p.getLongitude())>18)){map.getController().animateTo(p);lastFollowPoint=p;lastFollowAt=now;}}
        float accuracy=l.hasAccuracy()?l.getAccuracy():999;
        if(destination!=null&&waitingForFix&&!routeAttemptedForDestination&&isNavigationFixUsable(l))drawRouteIfPossible();
        lastFixProvider=l.getProvider()==null?"غير معروف":l.getProvider();
        lastFixAccuracy=accuracy;lastFixElapsedNanos=l.getElapsedRealtimeNanos();
        if(status!=null&&destination==null)status.setText("تم استقبال موقع "+lastFixProvider+" — الدقة ±"+Math.round(accuracy)+" متر");
    }
    @Override protected void onResume(){
        super.onResume();if(map!=null)map.onResume();
        requestLocationSources();if(current!=null)showCar(current);
        if(destination!=null&&!routeAttemptedForDestination){waitingForFix=true;drawRouteIfPossible();}
        locationHandler.removeCallbacks(locationStatusTicker);locationHandler.post(locationStatusTicker);
    }
    @Override protected void onPause(){
        locationHandler.removeCallbacks(locationStatusTicker);
        if(lm!=null){
            try{lm.removeUpdates(this);}catch(Exception ignored){}
            if(gpsStatusRegistered){try{lm.removeGpsStatusListener(gpsStatusListener);}catch(Exception ignored){}}
        }
        gpsRegistered=false;networkRegistered=false;criteriaRegistered=false;gpsStatusRegistered=false;gpsEngineStarted=false;
        stopGoogleFusedLocation();
        if(map!=null)map.onPause();super.onPause();
    }
    @Override protected void onDestroy(){
        locationHandler.removeCallbacksAndMessages(null);
        if(geocodeTask!=null)geocodeTask.cancel(true);if(routeTask!=null)routeTask.cancel(true);
        if(lm!=null){try{lm.removeUpdates(this);}catch(Exception ignored){}if(gpsStatusRegistered)try{lm.removeGpsStatusListener(gpsStatusListener);}catch(Exception ignored){}}
        stopGoogleFusedLocation();
        if(navigationStateReceiver!=null)try{unregisterReceiver(navigationStateReceiver);}catch(Exception ignored){}
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
    private static final class GeoCandidate{final GeoPoint point;final String name,source;GeoCandidate(GeoPoint p,String n,String s){point=p;name=n==null||n.isEmpty()?"الوجهة":n;source=s;}}
    private static final class GeoResult{final List<GeoCandidate> candidates;final String error;GeoResult(String e){candidates=Collections.emptyList();error=e==null?"فشل البحث":e;}GeoResult(List<GeoCandidate> c){candidates=c;error=null;}}
    private static final class Maneuver{final GeoPoint point;final String text;Maneuver(GeoPoint p,String t){point=p;text=t;}}
    private static final class RouteOption{final ArrayList<GeoPoint> points;final double km,minutes;final List<Maneuver> maneuvers;final int turns;RouteOption(ArrayList<GeoPoint> p,double k,double m,List<Maneuver> n,int t){points=p;km=k;minutes=m;maneuvers=n;turns=t;}}
    private static final class RouteResult{final List<RouteOption> options;final String error,provider;RouteResult(String e){options=Collections.emptyList();error=e;provider="";}RouteResult(List<RouteOption> o,String p){options=o;error=null;provider=p;}}
}
