package com.ali.carsystem;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class VideoApiClient {
    public static final class StreamInfo {
        public final String url;
        public final String title;
        public final boolean hls;
        StreamInfo(String url,String title,boolean hls){this.url=url;this.title=title;this.hls=hls;}
    }

    private static final String[] PIPED_SERVERS={
        "https://pipedapi.kavin.rocks",
        "https://pipedapi.adminforge.de",
        "https://piped-api.privacy.com.de",
        "https://pipedapi.nosebs.ru",
        "https://pipedapi.leptons.xyz",
        "https://pipedapi.drgns.space",
        "https://api.piped.private.coffee"
    };
    private static final String[] INVIDIOUS_SERVERS={
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.tiekoetter.com",
        "https://yt.chocolatemoo53.com"
    };
    private VideoApiClient(){}

    public static List<VideoItem> search(Context context,String query)throws Exception{
        final String encoded=URLEncoder.encode(query,"UTF-8");
        final List<String> servers=new ArrayList<>();
        for(String s:PIPED_SERVERS)servers.add("PIPED|"+s);
        for(String s:INVIDIOUS_SERVERS)servers.add("INVIDIOUS|"+s);
        ExecutorService pool=Executors.newFixedThreadPool(4);
        CompletionService<List<VideoItem>> completions=new ExecutorCompletionService<>(pool);
        List<Future<List<VideoItem>>> futures=new ArrayList<>();
        final AtomicReference<Exception> lastError=new AtomicReference<>();
        for(final String entry:servers){
            futures.add(completions.submit(new Callable<List<VideoItem>>(){
                @Override public List<VideoItem> call(){
                    String[] parts=entry.split("\\|",2);
                    try{return "PIPED".equals(parts[0])
                            ?searchPiped(context,parts[1],encoded)
                            :searchInvidious(context,parts[1],encoded);}
                    catch(Exception error){lastError.set(error);return Collections.emptyList();}
                }
            }));
        }
        final long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(24);
        try{
            int completed=0;
            while(completed<futures.size()){
                long remaining=deadline-System.nanoTime();
                if(remaining<=0)break;
                Future<List<VideoItem>> finished=completions.poll(remaining,TimeUnit.NANOSECONDS);
                if(finished==null)break;
                completed++;
                try{
                    List<VideoItem> result=finished.get();
                    if(result!=null&&!result.isEmpty())return result;
                }catch(ExecutionException ignored){}
            }
        }finally{
            for(Future<?> future:futures)future.cancel(true);
            pool.shutdownNow();
        }
        Exception failure=lastError.get();
        throw new Exception(failure==null
                ?"تعذر العثور على نتائج من خوادم الفيديو. حاول مرة أخرى."
                :NetworkCompat.arabicError(failure),failure);
    }

    private static List<VideoItem> searchPiped(Context context,String base,String encoded)throws Exception{
        String body=request(context,base+"/search?q="+encoded+"&filter=videos&hl=ar");
        JSONArray array;
        try {
            array=new JSONArray(body);
        } catch(org.json.JSONException notArray) {
            JSONObject root=new JSONObject(body);
            array=root.optJSONArray("items");
            if(array==null)array=root.optJSONArray("videos");
            if(array==null)throw new java.io.IOException("استجابة Piped لا تحتوي على قائمة فيديوهات صالحة.",notArray);
        }
        ArrayList<VideoItem> results=new ArrayList<>();
        for(int i=0;i<array.length()&&results.size()<30;i++){
            JSONObject item=array.optJSONObject(i);if(item==null)continue;
            String id=item.optString("id","");
            String videoUrl=item.optString("url","");
            if(id.length()==0&&videoUrl.length()>0){
                Uri parsed=Uri.parse(videoUrl.startsWith("http")?videoUrl:"https://piped.video"+videoUrl);
                id=parsed.getQueryParameter("v");
            }
            if(id==null||id.length()==0)continue;
            results.add(new VideoItem(id,item.optString("title","فيديو"),
                    item.optString("uploaderName",item.optString("uploader","قناة")),
                    item.optString("thumbnail",""),"PIPED",base));
        }
        return results;
    }

    private static List<VideoItem> searchInvidious(Context context,String host,String encoded)throws Exception{
        JSONArray array=new JSONArray(request(context,host+"/api/v1/search?q="+encoded+"&type=video&hl=ar"));
        ArrayList<VideoItem> results=new ArrayList<>();
        for(int i=0;i<array.length()&&results.size()<30;i++){
            JSONObject item=array.optJSONObject(i);
            if(item==null||!"video".equals(item.optString("type","video")))continue;
            String id=item.optString("videoId","");if(id.length()==0)continue;
            String thumbnail="";
            JSONArray thumbs=item.optJSONArray("videoThumbnails");
            if(thumbs!=null)for(int n=thumbs.length()-1;n>=0;n--){
                JSONObject thumb=thumbs.optJSONObject(n);
                if(thumb!=null&&(thumbnail=thumb.optString("url","")).length()>0)break;
            }
            results.add(new VideoItem(id,item.optString("title","فيديو"),
                    item.optString("author","قناة"),thumbnail,"INVIDIOUS",host));
        }
        return results;
    }

    /** Try each configured public instance for the same video ID before returning an error. */
    public static StreamInfo stream(Context context,VideoItem selected)throws Exception{
        return stream(context,selected,null);
    }

    public static StreamInfo stream(Context context,VideoItem selected,String excludedUrl)throws Exception{
        Exception last=null;
        List<String> piped=new ArrayList<>();
        if("PIPED".equals(selected.backend)&&selected.baseUrl!=null)piped.add(selected.baseUrl);
        for(String value:PIPED_SERVERS)if(!piped.contains(value))piped.add(value);
        for(String server:piped){
            try{
                JSONObject data=new JSONObject(request(context,server+"/streams/"+URLEncoder.encode(selected.id,"UTF-8")));
                JSONObject stream=choosePiped(data.optJSONArray("videoStreams"));
                if(stream!=null){
                    String url=stream.optString("url","");
                    if(url.length()>0&&!url.equals(excludedUrl))return new StreamInfo(url,data.optString("title",selected.title),false);
                }
                String hls=validHls(data.optString("hls",""));
                if(hls.length()>0&&!hls.equals(excludedUrl))return new StreamInfo(hls,data.optString("title",selected.title),true);
                last=new Exception("هذا الخادم لم يقدم صيغة فيديو قابلة للتشغيل.");
            }catch(Exception error){last=error;}
        }

        List<String> invidious=new ArrayList<>();
        if(selected.baseUrl!=null&&selected.baseUrl.startsWith("https://")&&!invidious.contains(selected.baseUrl))
            invidious.add(selected.baseUrl);
        for(String value:INVIDIOUS_SERVERS)if(!invidious.contains(value))invidious.add(value);
        for(String server:invidious){
            try{
                JSONObject data=new JSONObject(request(context,server+"/api/v1/videos/"+
                        URLEncoder.encode(selected.id,"UTF-8")+"?hl=ar"));
                JSONObject stream=chooseInvidious(data.optJSONArray("formatStreams"));
                if(stream!=null){
                    String url=stream.optString("url","");
                    if(url.length()>0)return new StreamInfo(url,data.optString("title",selected.title),false);
                }
                String hls=validHls(data.optString("hlsUrl",""));
                if(hls.length()>0)return new StreamInfo(hls,data.optString("title",selected.title),true);
                last=new Exception("لم يقدم الخادم صيغة فيديو مناسبة.");
            }catch(Exception error){last=error;}
        }
        throw new Exception(last==null?"لم نعثر على بث متوافق لهذا الفيديو.":NetworkCompat.arabicError(last),last);
    }

    private static JSONObject choosePiped(JSONArray streams){
        if(streams==null)return null;
        ArrayList<JSONObject> candidates=new ArrayList<>();
        for(int i=0;i<streams.length();i++){
            JSONObject item=streams.optJSONObject(i);if(item==null)continue;
            String mime=item.optString("mimeType","").toLowerCase();
            String url=item.optString("url","");
            if(!mime.startsWith("video/mp4")||url.length()==0||item.optBoolean("videoOnly",false))continue;
            candidates.add(item);
        }
        sortByPreferredQuality(candidates);
        return candidates.isEmpty()?null:candidates.get(0);
    }

    private static JSONObject chooseInvidious(JSONArray streams){
        if(streams==null)return null;
        ArrayList<JSONObject> candidates=new ArrayList<>();
        for(int i=0;i<streams.length();i++){
            JSONObject item=streams.optJSONObject(i);if(item==null)continue;
            String type=item.optString("type","").toLowerCase();
            String container=item.optString("container","");
            String url=item.optString("url","");
            // formatStreams are muxed/progressive; adaptiveFormats are intentionally excluded.
            if((type.startsWith("video/mp4")||"mp4".equalsIgnoreCase(container))&&url.length()>0)
                candidates.add(item);
        }
        sortByPreferredQuality(candidates);
        return candidates.isEmpty()?null:candidates.get(0);
    }

    private static void sortByPreferredQuality(List<JSONObject> candidates){
        Collections.sort(candidates,new Comparator<JSONObject>(){
            @Override public int compare(JSONObject a,JSONObject b){
                return Integer.compare(qualityRank(a),qualityRank(b));
            }
        });
    }
    private static int qualityRank(JSONObject item){
        String quality=item.optString("qualityLabel",item.optString("quality","")).toLowerCase();
        if(quality.contains("360"))return 0;
        if(quality.contains("480"))return 1;
        if(quality.contains("720"))return 2;
        if(quality.contains("240"))return 3;
        int height=0;
        java.util.regex.Matcher matcher=java.util.regex.Pattern.compile("(\\d{3,4})p").matcher(quality);
        if(matcher.find())try{height=Integer.parseInt(matcher.group(1));}catch(NumberFormatException ignored){}
        return height==0?4:5+height;
    }
    private static String validHls(String url){
        if(url==null)return "";
        String lower=url.toLowerCase();
        return lower.contains(".m3u8")?url:"";
    }

    public static Bitmap downloadThumbnail(Context context,String address){
        if(address==null||address.length()<8)return null;
        HttpURLConnection connection=null;
        try{
            String fixed=address.startsWith("//")?"https:"+address:address;
            connection=NetworkCompat.open(context,fixed);
            connection.setConnectTimeout(8000);connection.setReadTimeout(10000);
            int code=connection.getResponseCode();if(code<200||code>=300)return null;
            if(connection.getContentLength()>2*1024*1024)return null;
            try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buffer=new byte[8192];int count,total=0;
                while((count=in.read(buffer))!=-1){total+=count;if(total>2*1024*1024)return null;out.write(buffer,0,count);}
                byte[] bytes=out.toByteArray();
                BitmapFactory.Options options=new BitmapFactory.Options();options.inPreferredConfig=Bitmap.Config.RGB_565;options.inSampleSize=4;
                return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            }
        }catch(Exception ignored){return null;}
        finally{if(connection!=null)connection.disconnect();}
    }

    private static String request(Context context,String address)throws Exception{
        HttpURLConnection connection=NetworkCompat.open(context,address);
        connection.setConnectTimeout(8000);connection.setReadTimeout(10000);
        try{
            int code=connection.getResponseCode();
            if(code<200||code>=300){
                String detail="";
                try(InputStream error=connection.getErrorStream()){
                    if(error!=null){byte[] sample=new byte[512];int n=error.read(sample);if(n>0)detail=new String(sample,0,n,"UTF-8").replaceAll("\\s+"," ").trim();}
                }catch(Exception ignored){}
                throw new java.io.IOException("HTTP "+code+(detail.length()>0?": "+detail:""));
            }
            try(InputStream in=connection.getInputStream();
                BufferedReader reader=new BufferedReader(new InputStreamReader(in,"UTF-8"))){
                StringBuilder result=new StringBuilder();String line;
                while((line=reader.readLine())!=null){
                    result.append(line);if(result.length()>1800000)throw new java.io.IOException("استجابة الخادم أكبر من الحجم المسموح.");
                }
                return result.toString();
            }
        }finally{connection.disconnect();}
    }
}
