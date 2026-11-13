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
    static final class StreamInfo {
        static final String MP4="MP4",SPLIT="SPLIT",DASH="DASH",HLS="HLS";
        final String url,audioUrl,title,kind;
        StreamInfo(String u,String a,String t,String k){url=u;audioUrl=a;title=t;kind=k;}
        String identity(){return streamIdentity(url,audioUrl,kind);}
    }
    static String streamIdentity(String url,String audio,String kind){
        return (url==null?"":url)+"|"+(audio==null?"":audio)+"|"+(kind==null?"":kind);
    }
    private static volatile List<String> pipedCache, invidiousCache;
    private static volatile long discoveryTime;
    private static List<String> piped(Context c) {
        refresh(c);return pipedCache==null?Collections.singletonList("https://pipedapi.kavin.rocks"):pipedCache;
    }
    private static List<String> invidious(Context c){refresh(c);return invidiousCache==null?Collections.emptyList():invidiousCache;}
    private static synchronized void refresh(Context context){
        if(pipedCache!=null&&System.currentTimeMillis()-discoveryTime<20*60*1000L)return;
        List<String> p=new ArrayList<>(),v=new ArrayList<>();
        // Known live API host aliases. The official wiki currently uses both
        // "api.piped.*" and "piped-api.*" naming forms; keep these explicit
        // fallbacks so a stale public-instance page cannot empty the list.
        p.add("https://api.piped.private.coffee");
        p.add("https://piped-api.privacy.com.de");
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
        if(value.contains(".mpd")||value.contains("application/dash+xml"))return "DASH";
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
        Exception last=null;List<String> p=new ArrayList<>();
        if(item.base!=null&&"PIPED".equals(item.backend))p.add(item.base);
        for(String x:piped(c))if(!p.contains(x))p.add(x);
        for(String base:p)try{
            JSONObject data=new JSONObject(read(c,base+"/streams/"+URLEncoder.encode(item.id,"UTF-8")));
            StreamInfo result=fromPiped(data,item.title,excluded);if(result!=null)return result;
        }catch(Exception e){last=e;}
        List<String> v=new ArrayList<>();if(item.base!=null&&"INVIDIOUS".equals(item.backend))v.add(item.base);
        for(String x:invidious(c))if(!v.contains(x))v.add(x);
        for(String base:v)try{
            JSONObject data=new JSONObject(read(c,base+"/api/v1/videos/"+URLEncoder.encode(item.id,"UTF-8")+"?hl=ar"));
            StreamInfo result=fromInvidious(data,item.title,excluded);if(result!=null)return result;
        }catch(Exception e){last=e;}
        throw new IOException(last==null?"لا يوجد مصدر فيديو متوافق (MP4 أو DASH أو HLS)":last.getMessage(),last);
    }
    private static StreamInfo fromPiped(JSONObject data,String fallback,String excluded){
        String title=data.optString("title",fallback);
        JSONArray videos=data.optJSONArray("videoStreams"),audios=data.optJSONArray("audioStreams");
        for(JSONObject x:sortedStreams(videos,true,false)){
            String u=x.optString("url","");
            if(validStream(u)&&!streamIdentity(u,null,StreamInfo.MP4).equals(excluded))
                return new StreamInfo(u,null,title,StreamInfo.MP4);
        }
        List<JSONObject> videoOnly=sortedStreams(videos,true,true),audioOnly=sortedStreams(audios,false,false);
        for(JSONObject v:videoOnly)for(JSONObject au:audioOnly){
            String vu=v.optString("url",""),auUrl=au.optString("url","");
            if(validStream(vu)&&validStream(auUrl)&&!streamIdentity(vu,auUrl,StreamInfo.SPLIT).equals(excluded))
                return new StreamInfo(vu,auUrl,title,StreamInfo.SPLIT);
        }
        String dash=data.optString("dash","");
        if(validStream(dash)&&!streamIdentity(dash,null,StreamInfo.DASH).equals(excluded))
            return new StreamInfo(dash,null,title,StreamInfo.DASH);
        String hls=data.optString("hls","");
        if(validStream(hls)&&!streamIdentity(hls,null,StreamInfo.HLS).equals(excluded))
            return new StreamInfo(hls,null,title,StreamInfo.HLS);
        return null;
    }
    private static StreamInfo fromInvidious(JSONObject data,String fallback,String excluded){
        String title=data.optString("title",fallback);
        List<JSONObject> muxed=new ArrayList<>();JSONArray formats=data.optJSONArray("formatStreams");
        if(formats!=null)for(int i=0;i<formats.length();i++){
            JSONObject x=formats.optJSONObject(i);
            if(x!=null&&x.optString("type","").toLowerCase(Locale.US).startsWith("video/mp4"))muxed.add(x);
        }
        sortVideoStreams(muxed);
        for(JSONObject x:muxed){
            String u=x.optString("url","");
            if(validStream(u)&&!streamIdentity(u,null,StreamInfo.MP4).equals(excluded))
                return new StreamInfo(u,null,title,StreamInfo.MP4);
        }
        List<JSONObject> videos=new ArrayList<>(),audios=new ArrayList<>();JSONArray adaptive=data.optJSONArray("adaptiveFormats");
        if(adaptive!=null)for(int i=0;i<adaptive.length();i++){
            JSONObject x=adaptive.optJSONObject(i);if(x==null)continue;
            String type=x.optString("type","").toLowerCase(Locale.US);
            if(type.startsWith("video/mp4"))videos.add(x);else if(type.startsWith("audio/mp4"))audios.add(x);
        }
        sortVideoStreams(videos);sortAudioStreams(audios);
        for(JSONObject v:videos)for(JSONObject au:audios){
            String vu=v.optString("url",""),auUrl=au.optString("url","");
            if(validStream(vu)&&validStream(auUrl)&&!streamIdentity(vu,auUrl,StreamInfo.SPLIT).equals(excluded))
                return new StreamInfo(vu,auUrl,title,StreamInfo.SPLIT);
        }
        String dash=data.optString("dashUrl","");
        if(validStream(dash)&&!streamIdentity(dash,null,StreamInfo.DASH).equals(excluded))
            return new StreamInfo(dash,null,title,StreamInfo.DASH);
        String hls=data.optString("hlsUrl","");
        if(validStream(hls)&&!streamIdentity(hls,null,StreamInfo.HLS).equals(excluded))
            return new StreamInfo(hls,null,title,StreamInfo.HLS);
        return null;
    }
    private static List<JSONObject> sortedStreams(JSONArray values,boolean video,boolean videoOnly){
        List<JSONObject> out=new ArrayList<>();if(values==null)return out;
        for(int i=0;i<values.length();i++){
            JSONObject x=values.optJSONObject(i);if(x==null)continue;
            String mime=x.optString("mimeType","").toLowerCase(Locale.US);
            if(video&&mime.startsWith("video/mp4")&&x.optBoolean("videoOnly",false)==videoOnly)out.add(x);
            if(!video&&mime.startsWith("audio/mp4"))out.add(x);
        }
        if(video)sortVideoStreams(out);else sortAudioStreams(out);
        return out;
    }
    private static void sortVideoStreams(List<JSONObject> values){
        Collections.sort(values,(a,b)->Integer.compare(videoScore(b),videoScore(a)));
    }
    private static int videoScore(JSONObject x){
        int h=x.optInt("height",0);
        if(h==0){Matcher m=Pattern.compile("(\\d{2,4})").matcher(x.optString("resolution",x.optString("qualityLabel",x.optString("quality",""))));if(m.find())try{h=Integer.parseInt(m.group(1));}catch(Exception ignored){}}
        return h<=480?1000+h:1000-(h-480)*3;
    }
    private static void sortAudioStreams(List<JSONObject> values){
        Collections.sort(values,(a,b)->Integer.compare(audioScore(b),audioScore(a)));
    }
    private static int audioScore(JSONObject x){
        int rate=x.optInt("bitrate",0);return rate>1000&&rate<1000000?rate:(rate==0?64000:rate/1000);
    }
    private static boolean validStream(String url){return url!=null&&url.startsWith("https://")&&!url.contains("null");}
    static String read(Context context,String url)throws Exception{
        java.net.HttpURLConnection c=LegacyTls.open(context,url);
        try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>2*1024*1024)throw new IOException("رد الخادم أكبر من الحد المسموح");out.write(b,0,n);}
            return new String(out.toByteArray(),"UTF-8");
        }finally{c.disconnect();}
    }
}
