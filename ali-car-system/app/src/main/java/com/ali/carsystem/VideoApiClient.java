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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class VideoApiClient {
    public static final class StreamInfo {
        public final String url;
        public final String title;
        StreamInfo(String u, String t) { url = u; title = t; }
    }

    private static final String[] PIPED_SEEDS = {
        "https://pipedapi.kavin.rocks",
        "https://api.piped.silkky.cloud",
        "https://pipedapi.tokhmi.xyz",
        "https://pipedapi.moomoo.me",
        "https://pipedapi.adminforge.de",
        "https://pipedapi.privacy.com.de"
    };
    private static final String[] INVIDIOUS_SEEDS = {
        "https://inv.nadeko.net",
        "https://invidious.nerdvpn.de",
        "https://invidious.tiekoetter.com"
    };

    private VideoApiClient() { }

    public static List<VideoItem> search(Context context, String query) throws Exception {
        final String encoded = URLEncoder.encode(query, "UTF-8");
        Exception last = null;
        int tried = 0;
        for (String base : pipedInstances(context)) {
            if (tried++ >= 3) break;
            try {
                JSONArray array = new JSONArray(request(context,
                        base + "/search?q=" + encoded + "&filter=videos&hl=ar"));
                ArrayList<VideoItem> result = new ArrayList<>();
                for (int i = 0; i < array.length() && result.size() < 30; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) continue;
                    String id = item.optString("id", "");
                    String videoUrl = item.optString("url", "");
                    if (id.length() == 0 && videoUrl.length() > 0) {
                        Uri parsed = Uri.parse(videoUrl.startsWith("http") ? videoUrl : "https://piped.video" + videoUrl);
                        id = parsed.getQueryParameter("v");
                    }
                    if (id == null || id.length() == 0) continue;
                    result.add(new VideoItem(id, item.optString("title", "فيديو"),
                            item.optString("uploaderName", item.optString("uploader", "قناة")),
                            item.optString("thumbnail", ""), "PIPED", base));
                }
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                last = e;
            }
        }

        tried = 0;
        for (String host : invidiousInstances(context)) {
            if (tried++ >= 3) break;
            try {
                JSONArray array = new JSONArray(request(context,
                        host + "/api/v1/search?q=" + encoded + "&type=video&hl=ar"));
                ArrayList<VideoItem> result = new ArrayList<>();
                for (int i = 0; i < array.length() && result.size() < 30; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null || !"video".equals(item.optString("type", "video"))) continue;
                    String id = item.optString("videoId", "");
                    if (id.length() == 0) continue;
                    String thumbnail = "";
                    JSONArray thumbs = item.optJSONArray("videoThumbnails");
                    if (thumbs != null) {
                        for (int n = thumbs.length() - 1; n >= 0; n--) {
                            JSONObject thumb = thumbs.optJSONObject(n);
                            if (thumb != null) {
                                thumbnail = thumb.optString("url", "");
                                if (thumbnail.length() > 0) break;
                            }
                        }
                    }
                    result.add(new VideoItem(id, item.optString("title", "فيديو"),
                            item.optString("author", "قناة"), thumbnail, "INVIDIOUS", host));
                }
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                last = e;
            }
        }
        throw new Exception(last == null
                ? "لم تقدم خوادم البحث نتائج. حاول مرة أخرى."
                : NetworkCompat.arabicError(last), last);
    }

    public static StreamInfo stream(Context context, VideoItem item) throws Exception {
        if ("PIPED".equals(item.backend)) {
            JSONObject data = new JSONObject(request(context,
                    item.baseUrl + "/streams/" + URLEncoder.encode(item.id, "UTF-8")));
            JSONObject selected = choosePiped(data.optJSONArray("videoStreams"));
            if (selected == null)
                throw new Exception("لا يوفر هذا الخادم بثاً متوافقاً بدقة 360 أو 480.");
            String url = selected.optString("url", "");
            if (url.length() == 0) throw new Exception("رابط الفيديو غير متاح.");
            return new StreamInfo(url, data.optString("title", item.title));
        }

        JSONObject data = new JSONObject(request(context,
                item.baseUrl + "/api/v1/videos/" + URLEncoder.encode(item.id, "UTF-8") + "?hl=ar"));
        JSONObject selected = chooseInvidious(data.optJSONArray("formatStreams"));
        if (selected == null)
            throw new Exception("لا يوفر هذا الخادم فيديو MP4 متوافقاً بدقة 360 أو 480.");
        String url = selected.optString("url", "");
        if (url.length() == 0) throw new Exception("رابط الفيديو غير متاح.");
        return new StreamInfo(url, data.optString("title", item.title));
    }

    public static Bitmap downloadThumbnail(Context context, String address) {
        if (address == null || address.length() < 8) return null;
        HttpURLConnection connection = null;
        try {
            String fixed = address.startsWith("//") ? "https:" + address : address;
            connection = NetworkCompat.open(context, fixed);
            connection.setConnectTimeout(4000);
            connection.setReadTimeout(5000);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) return null;
            int length = connection.getContentLength();
            if (length > 2 * 1024 * 1024) return null;
            try (InputStream in = connection.getInputStream();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count, total = 0;
                while ((count = in.read(buffer)) != -1) {
                    total += count;
                    if (total > 2 * 1024 * 1024) return null;
                    out.write(buffer, 0, count);
                }
                byte[] bytes = out.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inPreferredConfig = Bitmap.Config.RGB_565;
                options.inSampleSize = 4;
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static JSONObject choosePiped(JSONArray streams) {
        if (streams == null) return null;
        JSONObject best = null;
        int score = -1;
        for (int i = 0; i < streams.length(); i++) {
            JSONObject item = streams.optJSONObject(i);
            if (item == null) continue;
            String mime = item.optString("mimeType", "").toLowerCase();
            String quality = item.optString("quality", "");
            boolean progressive = !item.optBoolean("videoOnly", true);
            if (!progressive || !mime.startsWith("video/mp4")) continue;
            int value = quality.contains("480p") ? 480 : quality.contains("360p") ? 360 : 0;
            if (value > score) { score = value; best = item; }
        }
        return score >= 360 ? best : null;
    }

    private static JSONObject chooseInvidious(JSONArray streams) {
        if (streams == null) return null;
        JSONObject best = null;
        int score = -1;
        for (int i = 0; i < streams.length(); i++) {
            JSONObject item = streams.optJSONObject(i);
            if (item == null) continue;
            String type = item.optString("type", "").toLowerCase();
            String container = item.optString("container", "");
            String quality = item.optString("qualityLabel", item.optString("quality", ""));
            if (!type.startsWith("video/mp4") && !"mp4".equalsIgnoreCase(container)) continue;
            int value = quality.contains("480p") ? 480 : quality.contains("360p") ? 360 : 0;
            if (value > score) { score = value; best = item; }
        }
        return score >= 360 ? best : null;
    }

    private static List<String> pipedInstances(Context context) {
        Set<String> values = new LinkedHashSet<>();
        try {
            String source = request(context, "https://raw.githubusercontent.com/wiki/TeamPiped/Piped/Instances.md");
            Matcher matcher = Pattern.compile("https?://(?:pipedapi|piped-api|api\\.piped)[a-zA-Z0-9.-]*").matcher(source);
            while (matcher.find() && values.size() < 3) values.add(trimSlash(matcher.group()));
        } catch (Exception ignored) { }
        for (String seed : PIPED_SEEDS) values.add(seed);
        return new ArrayList<>(values);
    }

    private static List<String> invidiousInstances(Context context) {
        Set<String> values = new LinkedHashSet<>();
        try {
            JSONArray array = new JSONArray(request(context,
                    "https://api.invidious.io/instances.json?sort_by=health"));
            for (int i = 0; i < array.length() && values.size() < 2; i++) {
                JSONArray pair = array.optJSONArray(i);
                if (pair == null || pair.length() < 2) continue;
                String host = pair.optString(0, "");
                JSONObject flags = pair.optJSONObject(1);
                if (host.length() > 0 && flags != null && flags.optBoolean("api", false)
                        && !host.contains(".onion")) values.add("https://" + host);
            }
        } catch (Exception ignored) { }
        for (String seed : INVIDIOUS_SEEDS) values.add(seed);
        return new ArrayList<>(values);
    }

    private static String trimSlash(String value) {
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private static String request(Context context, String address) throws Exception {
        HttpURLConnection connection = NetworkCompat.open(context, address);
        connection.setConnectTimeout(4500);
        connection.setReadTimeout(6500);
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("تعذر الوصول إلى خادم الفيديو.");
            try (InputStream in = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                    if (result.length() > 1800000) throw new Exception("استجابة الخادم أكبر من الحجم المسموح.");
                }
                return result.toString();
            }
        } finally {
            connection.disconnect();
        }
    }
}
