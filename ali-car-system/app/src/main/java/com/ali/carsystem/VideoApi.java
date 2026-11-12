package com.ali.carsystem;

import android.content.Context;
import android.net.Uri;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

final class VideoApi {
    static final class Item {
        final String id,title,author,thumbnail,backend,base;
        Item(String i,String t,String a,String th,String be,String ba){id=i;title=t;author=a;thumbnail=th;backend=be;base=ba;}
    }
    static final class StreamInfo { final String url,title; final boolean hls; StreamInfo(String u,String t,boolean h){url=u;title=t;hls=h;} }
    private static volatile List<String> pipedCache, invidiousCache;
    private static volatile long discoveryTime;
    private static List<String> piped(Context c) {
        refresh(c);return pipedCache==null?Collections.singletonList("https://pipedapi.kavin.rocks"):pipedCache;
    }
    private static List<String> invidious(Context c){refresh(c);return invidiousCache==null?Collections.emptyList():invidiousCache;}
    private static synchronized void refresh(Context context){
        if(pipedCache!=null&&System.currentTimeMillis()-discoveryTime<20*60*1000L)return;
        List<String> p=new ArrayList<>(),v=new ArrayList<>();
        try{
            String page=read(context,"https://github.com/TeamPiped/Piped/wiki/Instances");
            Matcher m=Pattern.compile("https?://[A-Za-z0-9.-]*piped-?api[A-Za-z0-9.-]*").matcher(page);
            while(m.find()){String u=m.group().replaceAll("[),.;]+$","");if(u.matches("https://[A-Za-z0-9.-]+")&&!p.contains(u))p.add(u);}
        }catch(Exception ignored){}
        if(!p.contains("https://pipedapi.kavin.rocks"))p.add("https://pipedapi.kavin.rocks");
        try{
            JSONArray a=new JSONArray(read(context,"https://api.invidious.io/instances.json?sort_by=health"));
            for(int i=0;i<a.length();i++){
                Object row=a.opt(i);if(!(row instanceof JSONArray))continue;
                JSONArray pair=(JSONArray)row;if(pair.length()<2)continue;
                String host=pair.optString(0,"");JSONObject meta=pair.optJSONObject(1);
                if(meta!=null&&meta.optBoolean("api",false)&&host.matches("[A-Za-z0-9.-]+")){
                    String scheme=meta.optString("type","https");String u=scheme+"://"+host;
                    if(!v.contains(u))v.add(u);
                }
            }
        }catch(Exception ignored){}
        pipedCache=Collections.unmodifiableList(p);invidiousCache=Collections.unmodifiableList(v);discoveryTime=System.currentTimeMillis();
    }
    static String encodeQuery(String value) throws Exception { return URLEncoder.encode(value,"UTF-8"); }
    static String classifyUrl(String raw) {
        if(raw==null)return "UNKNOWN";
        String value=raw.toLowerCase(java.util.Locale.US);
        if(value.contains(".m3u8")||value.contains("application/vnd.apple.mpegurl")||value.contains("application/x-mpegurl"))return "HLS";
        if(value.contains(".mp4")||value.contains("mime=video%2fmp4")||value.contains("mime=video/mp4"))return "MP4";
        return "UNKNOWN";
    }
    static List<Item> search(Context context,String query)throws Exception{
        final String encoded=encodeQuery(query);
        final List<String> endpoints=new ArrayList<>();
        for(String s:piped(context))endpoints.add("PIPED|"+s);
        for(String s:invidious(context))endpoints.add("INVIDIOUS|"+s);
        ExecutorService pool=Executors.newFixedThreadPool(Math.min(6,Math.max(2,endpoints.size())));
        CompletionService<List<Item>> cs=new ExecutorCompletionService<>(pool);
        List<Future<List<Item>>> jobs=new ArrayList<>();
        for(String endpoint:endpoints)jobs.add(cs.submit(()->{
            String[] p=endpoint.split("\\|",2);
            if("PIPED".equals(p[0]))return searchPiped(context,p[1],encoded);
            return searchInvidious(context,p[1],encoded);
        }));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(24);int done=0;
        try{
            while(done<jobs.size()){
                long remaining=deadline-System.nanoTime();if(remaining<=0)break;
                Future<List<Item>> f=cs.poll(remaining,TimeUnit.NANOSECONDS);if(f==null)break;done++;
                try{List<Item> result=f.get();if(result!=null&&!result.isEmpty())return result;}catch(Exception ignored){}
            }
        }finally{for(Future<?> f:jobs)f.cancel(true);pool.shutdownNow();}
        throw new IOException("لم تُرجع خوادم الفيديو نتائج صالحة. قد تكون الخوادم العامة متوقفة أو محجوبة.");
    }
    private static List<Item> searchPiped(Context c,String base,String q)throws Exception{
        String json=read(c,base+"/search?q="+q+"&filter=videos&hl=ar");
        JSONArray items;
        try{items=new JSONArray(json);}catch(JSONException e){JSONObject root=new JSONObject(json);items=root.optJSONArray("items");if(items==null)items=root.optJSONArray("videos");if(items==null)throw new IOException("بنية استجابة Piped غير معروفة",e);}
        List<Item> out=new ArrayList<>();
        for(int i=0;i<items.length()&&out.size()<30;i++){
            JSONObject x=items.optJSONObject(i);if(x==null)continue;String id=x.optString("id","");
            if(id.isEmpty()){String u=x.optString("url","");try{id=Uri.parse(u.startsWith("http")?u:"https://piped.video"+u).getQueryParameter("v");}catch(Exception ignored){}}
            if(id!=null&&!id.isEmpty())out.add(new Item(id,x.optString("title","فيديو"),x.optString("uploaderName",x.optString("uploader","قناة")),x.optString("thumbnail",""),"PIPED",base));
        }return out;
    }
    private static List<Item> searchInvidious(Context c,String base,String q)throws Exception{
        JSONArray a=new JSONArray(read(c,base+"/api/v1/search?q="+q+"&type=video&hl=ar"));
        List<Item> out=new ArrayList<>();
        for(int i=0;i<a.length()&&out.size()<30;i++){
            JSONObject x=a.optJSONObject(i);if(x==null||!"video".equals(x.optString("type","video")))continue;
            String id=x.optString("videoId","");JSONArray thumbs=x.optJSONArray("videoThumbnails");String thumb="";
            if(thumbs!=null&&thumbs.length()>0)thumb=thumbs.optJSONObject(thumbs.length()-1).optString("url","");
            if(!id.isEmpty())out.add(new Item(id,x.optString("title","فيديو"),x.optString("author","قناة"),thumb,"INVIDIOUS",base));
        }return out;
    }
    static StreamInfo resolve(Context c,Item item,String excluded)throws Exception{
        Exception last=null;List<String> pipedList=new ArrayList<>();
        if(item.base!=null&&"PIPED".equals(item.backend))pipedList.add(item.base);
        for(String s:piped(c))if(!pipedList.contains(s))pipedList.add(s);
        for(String base:pipedList){
            try{
                JSONObject data=new JSONObject(read(c,base+"/streams/"+URLEncoder.encode(item.id,"UTF-8")));
                JSONArray streams=data.optJSONArray("videoStreams");
                if(streams!=null)for(int i=0;i<streams.length();i++){
                    JSONObject x=streams.optJSONObject(i);if(x==null)continue;
                    String url=x.optString("url",""),mime=x.optString("mimeType","").toLowerCase(Locale.US);
                    boolean videoOnly=x.optBoolean("videoOnly",false);
                    if(!url.isEmpty()&&!url.equals(excluded)&&mime.contains("video/mp4")&&!videoOnly)return new StreamInfo(url,data.optString("title",item.title),false);
                }
                String hls=data.optString("hls","");
                if(!hls.isEmpty()&&!hls.equals(excluded))return new StreamInfo(hls,data.optString("title",item.title),true);
            }catch(Exception e){last=e;}
        }
        List<String> inv=new ArrayList<>();if(item.base!=null&&"INVIDIOUS".equals(item.backend))inv.add(item.base);
        for(String s:invidious(c))if(!inv.contains(s))inv.add(s);
        for(String base:inv){
            try{
                JSONObject data=new JSONObject(read(c,base+"/api/v1/videos/"+URLEncoder.encode(item.id,"UTF-8")+"?hl=ar"));
                JSONArray streams=data.optJSONArray("formatStreams");
                if(streams!=null)for(int i=0;i<streams.length();i++){
                    JSONObject x=streams.optJSONObject(i);if(x==null)continue;
                    String url=x.optString("url",""),type=x.optString("type","").toLowerCase(Locale.US);
                    if(!url.isEmpty()&&!url.equals(excluded)&&type.contains("video/mp4"))return new StreamInfo(url,data.optString("title",item.title),false);
                }
                String hls=data.optString("hlsUrl","");
                if(!hls.isEmpty()&&!hls.equals(excluded))return new StreamInfo(hls,data.optString("title",item.title),true);
            }catch(Exception e){last=e;}
        }
        throw new IOException(last==null?"لا يوجد مصدر بث متوافق لهذا الفيديو":last.getMessage(),last);
    }
    static String classifyUrl(String url){
        String u=url==null?"":url.toLowerCase(Locale.US);
        if(u.contains(".m3u8"))return "HLS";
        if(u.contains(".mp4")||u.contains("mime=video%2fmp4")||u.contains("mime=video/mp4"))return "MP4";
        return "UNKNOWN";
    }
    static String read(Context context,String url)throws Exception{
        java.net.HttpURLConnection c=LegacyTls.open(context,url);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>2*1024*1024)throw new IOException("رد الخادم أكبر من الحد المسموح");out.write(b,0,n);}
            return new String(out.toByteArray(),"UTF-8");
        }finally{c.disconnect();}
    }
}
