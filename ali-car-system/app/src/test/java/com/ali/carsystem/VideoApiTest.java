package com.ali.carsystem;
import org.junit.Test;
import static org.junit.Assert.*;
import java.net.URLDecoder;
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
}
