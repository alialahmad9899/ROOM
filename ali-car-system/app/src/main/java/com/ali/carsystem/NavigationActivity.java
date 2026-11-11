package com.ali.carsystem;
import android.app.Activity;import android.content.pm.PackageManager;import android.location.*;import android.os.*;import android.view.*;import android.view.WindowManager;import android.widget.*;
import org.json.*;import org.osmdroid.config.Configuration;import org.osmdroid.tileprovider.tilesource.TileSourceFactory;import org.osmdroid.util.GeoPoint;import org.osmdroid.views.MapView;import org.osmdroid.views.overlay.*;
import java.io.*;import java.net.*;import java.util.*;
public final class NavigationActivity extends Activity implements LocationListener{
private MapView map;private EditText query;private TextView status,steps;private LocationManager lm;private Location current;private AsyncTask<String,Void,Result> task;private Marker destMarker,currentMarker;private Polyline line;
private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
private void immersive(){getWindow().setFlags(1024,1024);getWindow().getDecorView().setSystemUiVisibility(5894|1024|512|4096|256);}
@Override protected void onCreate(Bundle b){super.onCreate(b);immersive();Configuration.getInstance().setUserAgentValue("ALI-CAR-Native/2.0 Android-21");
Configuration.getInstance().setCacheMapTileCount((short)4);
Configuration.getInstance().setTileDownloadThreads((short)1);
Configuration.getInstance().setTileFileSystemThreads((short)1);
Configuration.getInstance().setTileFileSystemCacheMaxBytes(8L*1024*1024);Configuration.getInstance().setTileFileSystemCacheTrimBytes(4L*1024*1024);
lm=(LocationManager)getSystemService(LOCATION_SERVICE);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(8),dp(6),dp(8),dp(6));root.setBackgroundColor(0xFF06101B);
TextView title=new TextView(this);title.setText("ALI NAVIGATION");title.setTextColor(-1);title.setTextSize(20);root.addView(title,new LinearLayout.LayoutParams(-1,dp(34)));
LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);query=new EditText(this);query.setSingleLine(true);query.setTextColor(-1);query.setHintTextColor(0xFF9BB3C4);query.setHint("مدينة أو محافظة: دمشق، حلب...");
row.addView(query,new LinearLayout.LayoutParams(0,dp(44),1));Button go=new Button(this);go.setText("بحث ومسار");row.addView(go,new LinearLayout.LayoutParams(dp(145),dp(44)));root.addView(row);
status=new TextView(this);status.setTextColor(0xFFFFD88A);status.setText("OpenStreetMap • Nominatim • OSRM");root.addView(status,new LinearLayout.LayoutParams(-1,dp(26)));
map=new MapView(this);map.setTileSource(TileSourceFactory.MAPNIK);map.setMultiTouchControls(false);map.setUseDataConnection(true);map.getController().setZoom(5.0);map.getController().setCenter(new GeoPoint(34.8,38.9));
root.addView(map,new LinearLayout.LayoutParams(-1,0,1));
ScrollView sv=new ScrollView(this);steps=new TextView(this);steps.setTextColor(-1);steps.setTextSize(12);steps.setPadding(dp(8),dp(4),dp(8),dp(4));sv.addView(steps);root.addView(sv,new LinearLayout.LayoutParams(-1,dp(68)));
Button back=new Button(this);back.setText("رجوع إلى ALI CAR");back.setOnClickListener(v->finish());root.addView(back,new LinearLayout.LayoutParams(-1,dp(40)));setContentView(root);
go.setOnClickListener(v->search());query.setOnEditorActionListener((v,a,e)->{search();return true;});}
private void loadLast(){try{if(checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED){Location a=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),b=lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);current=a==null?b:b==null?a:(a.getTime()>b.getTime()?a:b);}}catch(Exception ignored){}}
private void search(){String q=query.getText().toString().trim();if(q.length()<2){query.setError("اكتب اسم المدينة");return;}if(task!=null)task.cancel(true);loadLast();status.setText("جارٍ البحث عن "+q+"...");
task=new AsyncTask<String,Void,Result>(){protected Result doInBackground(String...v){return find(v[0],current);}protected void onPostExecute(Result r){if(!isFinishing())show(r);}}.execute(q);}
private Result find(String q,Location origin){try{
String u="https://nominatim.openstreetmap.org/search?q="+URLEncoder.encode(q,"UTF-8")+"&format=jsonv2&limit=5&addressdetails=1&accept-language=ar,en";
JSONArray arr=new JSONArray(get(u));if(arr.length()==0)return Result.error("لم يتم العثور على المدينة");JSONObject best=arr.getJSONObject(0);double sc=-9;
for(int i=0;i<arr.length();i++){JSONObject x=arr.getJSONObject(i);double n=x.optDouble("importance",0);String type=x.optString("type","");if("city".equals(type)||"town".equals(type)||"state".equals(type)||"administrative".equals(type))n+=1;if(n>sc){sc=n;best=x;}}
String name=best.optString("display_name",q);GeoPoint destination=new GeoPoint(best.getDouble("lat"),best.getDouble("lon"));
if(origin==null)return new Result(null,name,destination,null,null,"لم يصل GPS بعد. تم تحديد المدينة؛ فعّل GPS لرسم مسار الانطلاق.",0,0);
String route=String.format(Locale.US,"https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline&steps=true",origin.getLongitude(),origin.getLatitude(),destination.getLongitude(),destination.getLatitude());
JSONObject r=new JSONObject(get(route));JSONArray routes=r.optJSONArray("routes");if(!"Ok".equals(r.optString("code"))||routes==null||routes.length()==0)return new Result(null,name,destination,null,null,"تم تحديد الوجهة، لكن لم يتوفر مسار قيادة من موقعك.",0,0);
JSONObject first=routes.getJSONObject(0);ArrayList<GeoPoint> pts=decode(first.optString("geometry",""));StringBuilder text=new StringBuilder("تعليمات الطريق:\n");JSONArray legs=first.optJSONArray("legs");
if(legs!=null&&legs.length()>0){JSONArray s=legs.getJSONObject(0).optJSONArray("steps");if(s!=null)for(int i=0;i<s.length()&&i<35;i++)text.append(step(s.getJSONObject(i))).append('\n');}
return new Result(null,name,destination,new GeoPoint(origin.getLatitude(),origin.getLongitude()),pts,text.toString(),first.optDouble("distance")/1000,first.optDouble("duration")/60);
}catch(Exception e){return Result.error("خطأ البحث: "+e.getMessage());}}
private void show(Result r){if(r.error!=null){status.setText(r.error);return;}if(destMarker!=null)map.getOverlays().remove(destMarker);
destMarker=new Marker(map);destMarker.setPosition(r.destination);destMarker.setTitle(r.name);map.getOverlays().add(destMarker);
if(r.start==null){map.getController().setCenter(r.destination);map.getController().setZoom(11.5);status.setText(r.name);steps.setText(r.steps);map.invalidate();return;}
if(line!=null)map.getOverlays().remove(line);line=new Polyline(map);line.setPoints(r.points);line.setColor(0xFF00CFFF);line.setWidth(dp(5));map.getOverlays().add(line);
if(currentMarker==null){currentMarker=new Marker(map);currentMarker.setTitle("موقعك الحالي");map.getOverlays().add(currentMarker);}currentMarker.setPosition(r.start);
GeoPoint center=new GeoPoint((r.start.getLatitude()+r.destination.getLatitude())/2,(r.start.getLongitude()+r.destination.getLongitude())/2);map.getController().animateTo(center);map.getController().setZoom(9.5);
status.setText(String.format(Locale.US,"%.1f كم • %.0f دقيقة • %s",r.km,r.min,r.name));steps.setText(r.steps+"\n© OpenStreetMap contributors • OSRM");map.invalidate();}
private String get(String u)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(u).openConnection();c.setConnectTimeout(9000);c.setReadTimeout(12000);c.setRequestProperty("User-Agent","ALI-CAR-Native/2.0");c.setRequestProperty("Accept-Language","ar,en;q=0.8");
try{int code=c.getResponseCode();if(code<200||code>=300)throw new Exception("HTTP "+code);BufferedReader r=new BufferedReader(new InputStreamReader(c.getInputStream(),"UTF-8"));StringBuilder s=new StringBuilder();String l;while((l=r.readLine())!=null){s.append(l);if(s.length()>1600000)throw new Exception("response too large");}r.close();return s.toString();}finally{c.disconnect();}}
private ArrayList<GeoPoint> decode(String s){ArrayList<GeoPoint> out=new ArrayList<>();int i=0,lat=0,lon=0;while(i<s.length()){int[] a=val(s,i);lat+=a[0];i=a[1];int[] b=val(s,i);lon+=b[0];i=b[1];out.add(new GeoPoint(lat/1E5,lon/1E5));if(out.size()>20000)break;}return out;}
private int[] val(String s,int start){int result=0,shift=0,i=start,v;do{if(i>=s.length())throw new IllegalArgumentException("polyline");v=s.charAt(i++)-63;result|=(v&31)<<shift;shift+=5;}while(v>=32);return new int[]{(result&1)!=0?~(result>>1):result>>1,i};}
private String step(JSONObject o){JSONObject m=o.optJSONObject("maneuver");String t=m==null?"":m.optString("type",""),d=m==null?"":m.optString("modifier",""),road=o.optString("name","");String dir=d.contains("left")?"يسار":d.contains("right")?"يمين":"مباشرة";String action="arrive".equals(t)?"وصلت": "roundabout".equals(t)?"ادخل الدوار":"انعطف "+dir;return "• "+action+(road.length()>0?" نحو "+road:"")+" بعد "+Math.round(o.optDouble("distance"))+" م";}
@Override protected void onResume(){super.onResume();immersive();if(map!=null)map.onResume();if(lm!=null&&checkCallingOrSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)try{lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,2500,8,this);}catch(Exception ignored){}}
@Override protected void onPause(){if(lm!=null)try{lm.removeUpdates(this);}catch(Exception ignored){}if(map!=null)map.onPause();super.onPause();}
@Override protected void onDestroy(){if(task!=null)task.cancel(true);if(map!=null){map.onDetach();map=null;}super.onDestroy();}
@Override public void onLocationChanged(Location l){current=l;}@Override public void onProviderDisabled(String p){if(LocationManager.GPS_PROVIDER.equals(p))status.setText("GPS متوقف");}@Override public void onProviderEnabled(String p){}@Override public void onStatusChanged(String p,int s,Bundle b){}
private static final class Result{final String error,name,steps;final GeoPoint destination,start;final ArrayList<GeoPoint> points;final double km,min;Result(String e,String n,GeoPoint d,GeoPoint s,ArrayList<GeoPoint> p,String st,double k,double m){error=e;name=n;destination=d;start=s;points=p;steps=st;km=k;min=m;}static Result error(String s){return new Result(s,"",null,null,null,"",0,0);}}
}