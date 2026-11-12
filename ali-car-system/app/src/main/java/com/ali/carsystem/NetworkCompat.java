package com.ali.carsystem;

import android.content.Context;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public final class NetworkCompat {
    public static final String USER_AGENT = "AliCarLauncher/2.0 (Android 5.0)";
    private NetworkCompat() { }

    public static HttpURLConnection open(Context context, String address) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(4000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", USER_AGENT);
        connection.setRequestProperty("Accept", "application/json,text/plain,image/*,*/*");
        connection.setRequestProperty("Accept-Language", "ar,en;q=0.8");
        if (connection instanceof HttpsURLConnection) {
            try {
                HttpsURLConnection secure = (HttpsURLConnection) connection;
                secure.setSSLSocketFactory(LegacyTls.socketFactory(context));
                secure.setHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier());
            } catch (Exception e) {
                connection.disconnect();
                throw new IOException("تعذر تهيئة الاتصال الآمن بهذا الخادم", e);
            }
        }
        return connection;
    }

    public static String arabicError(Throwable error) {
        String detail = "";
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && detail.length() == 0) detail = cause.getMessage();
            if (cause instanceof java.net.UnknownHostException)
                return "تعذر العثور على عنوان الخادم. تحقق من الإنترنت أو جرّب خادماً آخر.";
            if (cause instanceof java.net.SocketTimeoutException)
                return "انتهت مهلة استجابة الخادم. اتصال الإنترنت موجود لكن الخدمة بطيئة أو لا تستجيب.";
            if (cause instanceof javax.net.ssl.SSLHandshakeException
                    || cause instanceof javax.net.ssl.SSLPeerUnverifiedException
                    || cause instanceof java.security.cert.CertificateException)
                return "فشل الاتصال الآمن بالخادم. تحقق من تاريخ الجهاز ووقته أو من توافق TLS.";
            if (cause instanceof java.net.ConnectException)
                return "تعذر فتح اتصال مع الخادم؛ قد يكون الخادم متوقفاً أو يحظر الاتصال.";
            if (cause instanceof org.json.JSONException)
                return "وصل رد من الخادم لكن تنسيق البيانات غير متوافق. جرّب خادماً آخر.";
        }
        java.util.regex.Matcher status = java.util.regex.Pattern.compile("HTTP\\s+(\\d{3})").matcher(detail);
        if (status.find()) {
            int code = Integer.parseInt(status.group(1));
            if (code == 401 || code == 403)
                return "الخادم رفض الطلب (HTTP " + code + "). هذا عطل في صلاحية الخادم وليس دليلاً على انقطاع الإنترنت.";
            if (code == 429)
                return "الخادم حدّ عدد الطلبات مؤقتاً (HTTP 429). انتظر قليلاً ثم أعد المحاولة.";
            if (code >= 500)
                return "خدمة الخادم تواجه عطلاً داخلياً (HTTP " + code + "). جرّب خادماً آخر.";
            return "رفض الخادم الطلب برمز HTTP " + code + ".";
        }
        if (detail.contains("استجابة") || detail.contains("تنسيق"))
            return detail;
        if (detail.length() > 0)
            return "فشل طلب الخدمة: " + detail;
        return "حدث خطأ غير معروف أثناء الاتصال بالخدمة.";
    }}
