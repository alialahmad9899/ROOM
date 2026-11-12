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
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.UnknownHostException)
                return "تعذر الوصول إلى الخادم. تأكد من اتصال الإنترنت.";
            if (cause instanceof java.net.SocketTimeoutException)
                return "استغرق الاتصال وقتاً طويلاً. حاول مرة أخرى.";
            if (cause instanceof javax.net.ssl.SSLHandshakeException
                    || cause instanceof javax.net.ssl.SSLPeerUnverifiedException
                    || cause instanceof java.security.cert.CertificateException)
                return "تعذر التحقق من شهادة أمان الخادم. تحقق من تاريخ الجهاز ووقته.";
            if (cause instanceof java.net.ConnectException)
                return "الخادم لا يستجيب حالياً. حاول لاحقاً.";
        }
        return "تعذر الاتصال بالخادم. تأكد من الإنترنت ثم أعد المحاولة.";
    }}
