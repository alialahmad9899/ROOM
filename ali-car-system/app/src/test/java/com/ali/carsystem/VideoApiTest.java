package com.ali.carsystem;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URLDecoder;
import org.json.JSONObject;
import org.json.JSONArray;
public class VideoApiTest {
 @Test public void arabicSearchEncodingRoundTripsUtf8() throws Exception {
  String query="موسيقى عربية للسيارة", encoded=VideoApi.encodeQuery(query);
  assertEquals(query,URLDecoder.decode(encoded,"UTF-8"));
  assertTrue(encoded.contains("%D9"));
 }
 @Test public void identifiesProgressiveMp4(){assertEquals("MP4",VideoApi.classifyUrl("https://cdn.example/video.mp4?expire=123"));}
 @Test public void identifiesHls(){assertEquals("HLS",VideoApi.classifyUrl("https://cdn.example/master.m3u8?token=x"));}
 @Test public void identifiesDash(){assertEquals("DASH",VideoApi.classifyUrl("https://cdn.example/manifest.mpd"));}
 @Test public void splitIdentityIncludesSeparateAudio(){
  assertNotEquals(VideoApi.streamIdentity("https://v/video","https://a/audio","SPLIT"),
                  VideoApi.streamIdentity("https://v/video","https://b/audio","SPLIT"));
 }
 @Test public void refusesUnknownStreamType(){assertEquals("UNKNOWN",VideoApi.classifyUrl("https://example.com/watch?id=1"));}

 @Test public void verifiedInvidiousFallbackIsFirstAndIncludesPublicAlternates(){
  assertFalse(VideoApi.defaultInvidiousEndpoints().isEmpty());
  assertEquals("https://yt.chocolatemoo53.com",VideoApi.defaultInvidiousEndpoints().get(0));
  assertTrue(VideoApi.defaultInvidiousEndpoints().contains("https://inv.nadeko.net"));
 }
 @Test public void extractsInvidiousDashStream() throws Exception {
  JSONObject response=new JSONObject();
  response.put("title","Dash video");
  response.put("dashUrl","https://cdn.example/manifest.mpd");
  VideoApi.StreamInfo result=VideoApi.fromInvidious(response,"fallback",null);
  assertNotNull(result);
  assertEquals(VideoApi.StreamInfo.DASH,result.kind);
  assertEquals("https://cdn.example/manifest.mpd",result.url);
 }
 @Test public void extractsInvidiousHlsStream() throws Exception {
  JSONObject response=new JSONObject();
  response.put("title","HLS video");
  response.put("hlsUrl","https://cdn.example/master.m3u8");
  VideoApi.StreamInfo result=VideoApi.fromInvidious(response,"fallback",null);
  assertNotNull(result);
  assertEquals(VideoApi.StreamInfo.HLS,result.kind);
  assertEquals("https://cdn.example/master.m3u8",result.url);
 }
 @Test public void extractsInvidiousAdaptiveMp4AudioAndVideo() throws Exception {
  JSONObject video=new JSONObject();
  video.put("type","video/mp4; codecs=avc1");
  video.put("url","https://cdn.example/video.mp4");
  video.put("height",480);
  JSONObject audio=new JSONObject();
  audio.put("type","audio/mp4; codecs=mp4a");
  audio.put("url","https://cdn.example/audio.m4a");
  audio.put("bitrate",128000);
  JSONArray adaptive=new JSONArray();
  adaptive.put(video);
  adaptive.put(audio);
  JSONObject response=new JSONObject();
  response.put("title","Adaptive video");
  response.put("adaptiveFormats",adaptive);
  VideoApi.StreamInfo result=VideoApi.fromInvidious(response,"fallback",null);
  assertNotNull(result);
  assertEquals(VideoApi.StreamInfo.SPLIT,result.kind);
  assertEquals("https://cdn.example/video.mp4",result.url);
  assertEquals("https://cdn.example/audio.m4a",result.audioUrl);
 }
}
