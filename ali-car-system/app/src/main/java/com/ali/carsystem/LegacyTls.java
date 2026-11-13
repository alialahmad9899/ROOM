package com.ali.carsystem;

import android.content.Context;
import android.util.Base64;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import javax.net.ssl.SSLSocketFactory;
import java.net.HttpURLConnection;
import javax.net.ssl.HttpsURLConnection;

public final class LegacyTls {
    private static volatile SSLSocketFactory factory;
    private LegacyTls() {}
    public static SSLSocketFactory factory(Context context) throws Exception {
        if (factory != null) return factory;
        synchronized (LegacyTls.class) {
            if (factory != null) return factory;
            // AndroidCAStore is read-only on Android. Copy the platform roots into a
            // private in-memory keystore, then append ISRG Root X1 for API21 devices.
            KeyStore platform = KeyStore.getInstance("AndroidCAStore");
            platform.load(null, null);
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            java.util.Enumeration<String> aliases = platform.aliases();
            while (aliases.hasMoreElements()) {
                String alias = aliases.nextElement();
                Certificate trusted = platform.getCertificate(alias);
                if (trusted != null) store.setCertificateEntry(alias, trusted);
            }
            try (InputStream in = context.getAssets().open("isrg_root_x1.pem")) {
                Certificate ca = CertificateFactory.getInstance("X.509").generateCertificate(in);
                store.setCertificateEntry("isrg-root-x1-ali-car", ca);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(store);
            SSLContext ssl = SSLContext.getInstance("TLSv1.2");
            ssl.init(null, tmf.getTrustManagers(), null);
            factory = ssl.getSocketFactory();
            return factory;
        }
    }
    public static HttpURLConnection open(Context context, String url) throws Exception {
        return open(context, url, "GET", 8000, 10000);
    }

    public static HttpURLConnection open(Context context, String url, String method,
                                         int connectTimeoutMs, int readTimeoutMs) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new java.net.URL(url).openConnection();
        c.setConnectTimeout(Math.max(1000, connectTimeoutMs));
        c.setReadTimeout(Math.max(1000, readTimeoutMs));
        c.setUseCaches(false);
        c.setRequestMethod(method == null ? "GET" : method);
        c.setRequestProperty("User-Agent", "ALI-CAR/3.6.0 (Android 5; https://github.com/alialahmad9899/ROOM)");
        c.setRequestProperty("Accept", "application/json,text/plain,*/*");
        c.setRequestProperty("Accept-Encoding", "identity");
        c.setRequestProperty("Accept-Language", "ar,en;q=0.8");
        c.setRequestProperty("Referer", "https://github.com/alialahmad9899/ROOM");
        if (c instanceof HttpsURLConnection) {
            ((HttpsURLConnection)c).setSSLSocketFactory(factory(context));
        }
        return c;
    }
}
