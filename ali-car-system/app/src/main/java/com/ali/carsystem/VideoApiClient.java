package com.ali.carsystem;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoApiClient {
    public static final class StreamInfo {
        public final String url;
        public final String title;
        StreamInfo(String u,String t){url=u;title=t;}
    }
    private static final String[] PIPED_SEEDS={
        "https://pipedapi.kavin.rocks","https://pipedapi.leptons.xyz",
        "https://pipedapi.nosebs.ru","https://pipedapi-libre.kavin.rocks",
        "https://piped-api.privacy.com.de","https://pipedapi.adminforge.de",
        "https://api.piped.yt","https://pipedapi.drgns.space",
        "https://pipedapi.owo.si","https://pipedapi.ducks.party",
        "https://piped-api.codespace.cz","https://pipedapi.reallyaweso.me",
        "https://api.piped.private.coffee"
    };
    private static final String[] INVIDIOUS_SEEDS={
        "https://inv.nadeko.net","https://invidious.nerdvpn.de",
        "https://yt.chocolatemoo53.com","https://invidious.tiekoetter.com"
    };
    private VideoApiClient(){}

    public static List<VideoItem> search(String query) throws Exception {
        Exception last=null;
        int pipedTried=0;
        for(String base:pipedInstances()){
            if(pipedTried++>=5)break;
            try{
                String url=base+"/search?q="+Uri.encode(query)+"&filter=videos";
                JSONArray arr=new JSONArray(request(url));
                ArrayList<VideoItem> out=new ArrayList<>();
                for(int i=0;i<arr.length()&&out.size()<35;i++){
                    JSONObject o=arr.optJSONObject(i);if(o==null)continue;
                    String id=o.optString("id","");
                    String videoUrl=o.optString("url","");
                    if(id.length()==0&&videoUrl.contains("v="))id=Uri.parse("https://x"+videoUrl).getQueryParameter("v");
                    if(id.length()==0)continue;
                    out.add(new VideoItem(id,o.optString("title","Video"),
                        o.optString("uploaderName",o.optString("uploader","")),
                        o.optString("thumbnail",""),"PIPED",base));
                }
                if(!out.isEmpty())return out;
            }catch(Exception e){last=e;}
        }
        int invidiousTried=0;
        for(String host:invidiousInstances()){
            if(invidiousTried++>=4)break;
            try{
                JSONArray arr=new JSONArray(request(host+"/api/v1/search?q="+Uri.encode(query)+"&type=video&hl=ar"));
                ArrayList<VideoItem> out=new ArrayList<>();
                for(int i=0;i<arr.length()&&out.size()<35;i++){
                    JSONObject o=arr.optJSONObject(i);if(o==null||!"video".equals(o.optString("type","video")))continue;
                    String id=o.optString("videoId","");if(id.length()==0)continue;
                    String thumb="";
                    JSONArray thumbs=o.optJSONArray("videoThumbnails");
                    if(thumbs!=null&&thumbs.length()>0)thumb=thumbs.optJSONObject(thumbs.length()-1).optString("url","");
                    out.add(new VideoItem(id,o.optString("title","Video"),o.optString("author",""),
                        thumb,"INVIDIOUS",host));
                }
                if(!out.isEmpty())return out;
            }catch(Exception e){last=e;}
        }
        throw new Exception(last==null?"تعذر الاتصال بخوادم البحث الاحتياطية":last.getMessage());
    }

    public static StreamInfo stream(VideoItem item) throws Exception {
        if("PIPED".equals(item.backend)){
            JSONObject data=new JSONObject(request(item.baseUrl+"/streams/"+Uri.encode(item.id)));
            JSONArray streams=data.optJSONArray("videoStreams");
            JSONObject selected=choosePiped(streams);
            if(selected==null)throw new Exception("الخادم لم يقدم فيديو MP4 تدريجي بدقة 360p أو 480p");
            String url=selected.optString("url","");
            if(url.length()==0)throw new Exception("رابط الفيديو فارغ");
            return new StreamInfo(url,data.optString("title",item.title));
        }
        JSONObject data=new JSONObject(request(item.baseUrl+"/api/v1/videos/"+Uri.encode(item.id)+"?hl=ar"));
        JSONArray streams=data.optJSONArray("formatStreams");
        JSONObject selected=chooseInvidious(streams);
        if(selected==null)throw new Exception("الخادم لم يقدم MP4 360p/480p؛ تم رفض DASH لتجنب حمل الذاكرة");
        String url=selected.optString("url","");
        if(url.length()==0)throw new Exception("رابط الفيديو فارغ");
        return new StreamInfo(url,data.optString("title",item.title));
    }

    private static JSONObject choosePiped(JSONArray a){
        if(a==null)return null;JSONObject best=null;int score=-1;
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;
            String mime=o.optString("mimeType",""),quality=o.optString("quality","");
            boolean progressive=!o.optBoolean("videoOnly",true);
            if(!progressive||!mime.toLowerCase().startsWith("video/mp4"))continue;
            int n=quality.contains("480p")?480:quality.contains("360p")?360:0;
            if(n>score){score=n;best=o;}
        }
        return score>=360?best:null;
    }
    private static JSONObject chooseInvidious(JSONArray a){
        if(a==null)return null;JSONObject best=null;int score=-1;
        for(int i=0;i<a.length();i++){
            JSONObject o=a.optJSONObject(i);if(o==null)continue;
            String type=o.optString("type",""),container=o.optString("container","");
            String q=o.optString("qualityLabel",o.optString("quality",""));
            if(!type.toLowerCase().contains("video/mp4")&&!"mp4".equalsIgnoreCase(container))continue;
            int n=q.contains("480p")?480:q.contains("360p")?360:0;
            if(n>score){score=n;best=o;}
        }
        return score>=360?best:null;
    }

    private static List<String> pipedInstances(){
        Set<String> values=new LinkedHashSet<>();
        // Dynamic list maintained by the Piped community wiki; fall back to documented API servers.
        try{
            String wiki=request("https://raw.githubusercontent.com/TeamPiped/documentation/main/content/docs/public-instances/index.md");
            Matcher m=Pattern.compile("https?://(?:pipedapi|piped-api|api\\.piped)[a-zA-Z0-9.-]*").matcher(wiki);
            while(m.find())values.add(trimSlash(m.group()));
        }catch(Exception ignored){}
        for(String s:PIPED_SEEDS)values.add(s);
        return new ArrayList<>(values);
    }
    private static List<String> invidiousInstances(){
        Set<String> values=new LinkedHashSet<>();
        try{
            JSONArray a=new JSONArray(request("https://api.invidious.io/instances.json?sort_by=health"));
            for(int i=0;i<a.length();i++){
                JSONArray pair=a.optJSONArray(i);if(pair==null||pair.length()<2)continue;
                String host=pair.optString(0,"");JSONObject flags=pair.optJSONObject(1);
                if(host.length()>0&&flags!=null&&flags.optBoolean("api",false)&&!host.contains(".onion"))
                    values.add("https://"+host);
            }
        }catch(Exception ignored){}
        for(String s:INVIDIOUS_SEEDS)values.add(s);
        return new ArrayList<>(values);
    }
    private static String trimSlash(String s){while(s.endsWith("/"))s=s.substring(0,s.length()-1);return s;}
    private static String request(String address)throws Exception{
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(2500);c.setReadTimeout(4500);
        c.setRequestProperty("User-Agent","ALI-CAR-Native/2.0 Android-21");
        c.setRequestProperty("Accept","application/json,text/plain,*/*");
        try{
            int code=c.getResponseCode();if(code<200||code>=300)throw new Exception("HTTP "+code);
            InputStream in=c.getInputStream();BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"));
            StringBuilder b=new StringBuilder();String line;
            while((line=r.readLine())!=null){b.append(line);if(b.length()>1800000)throw new Exception("Response too large");}
            r.close();return b.toString();
        }finally{c.disconnect();}
    }
}
