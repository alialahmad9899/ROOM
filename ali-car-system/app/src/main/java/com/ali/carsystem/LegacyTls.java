package com.ali.carsystem;

import android.content.Context;
import android.net.SSLCertificateSocketFactory;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Android 5 HTTPS compatibility without disabling certificate or hostname verification.
 * Uses system trust plus the official ISRG Root X1 trust anchor, and TLS 1.2 sockets.
 */
public final class LegacyTls {
    private static volatile SSLSocketFactory cached;
    private LegacyTls() { }

    public static SSLSocketFactory socketFactory(Context context) throws Exception {
        SSLSocketFactory value = cached;
        if (value != null) return value;
        synchronized (LegacyTls.class) {
            if (cached != null) return cached;

            TrustManagerFactory systemFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            systemFactory.init((KeyStore) null);
            X509TrustManager systemTrust = findTrustManager(systemFactory.getTrustManagers());

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate root;
            try (InputStream in = context.getApplicationContext()
                    .getResources().openRawResource(R.raw.isrg_root_x1)) {
                root = certificateFactory.generateCertificate(in);
            }
            KeyStore extraStore = KeyStore.getInstance(KeyStore.getDefaultType());
            extraStore.load(null, null);
            extraStore.setCertificateEntry("isrg-root-x1", root);
            TrustManagerFactory extraFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            extraFactory.init(extraStore);
            X509TrustManager extraTrust = findTrustManager(extraFactory.getTrustManagers());

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, new TrustManager[]{
                    new CombinedTrustManager(systemTrust, extraTrust)
            }, null);
            cached = new Tls12SocketFactory(sslContext.getSocketFactory());
            return cached;
        }
    }

    private static X509TrustManager findTrustManager(TrustManager[] managers) {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) return (X509TrustManager) manager;
        }
        throw new IllegalStateException("No X509 trust manager available");
    }

    private static final class CombinedTrustManager implements X509TrustManager {
        private final X509TrustManager system;
        private final X509TrustManager extra;
        CombinedTrustManager(X509TrustManager a, X509TrustManager b) {
            system = a;
            extra = b;
        }
        @Override public void checkClientTrusted(Certificate[] chain, String authType)
                throws CertificateException {
            system.checkClientTrusted(toX509(chain), authType);
        }
        @Override public void checkServerTrusted(java.security.cert.X509Certificate[] chain,
                                                  String authType) throws CertificateException {
            try {
                system.checkServerTrusted(chain, authType);
            } catch (CertificateException systemFailure) {
                try {
                    extra.checkServerTrusted(chain, authType);
                } catch (CertificateException extraFailure) {
                    systemFailure.addSuppressed(extraFailure);
                    throw systemFailure;
                }
            }
        }
        @Override public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            java.util.LinkedHashMap<String, java.security.cert.X509Certificate> all =
                    new java.util.LinkedHashMap<>();
            for (java.security.cert.X509Certificate cert : system.getAcceptedIssuers())
                all.put(cert.getSubjectX500Principal().getName(), cert);
            for (java.security.cert.X509Certificate cert : extra.getAcceptedIssuers())
                all.put(cert.getSubjectX500Principal().getName(), cert);
            return all.values().toArray(new java.security.cert.X509Certificate[0]);
        }
        private java.security.cert.X509Certificate[] toX509(Certificate[] chain)
                throws CertificateException {
            java.security.cert.X509Certificate[] result =
                    new java.security.cert.X509Certificate[chain.length];
            for (int i = 0; i < chain.length; i++) {
                if (!(chain[i] instanceof java.security.cert.X509Certificate))
                    throw new CertificateException("Unexpected certificate type");
                result[i] = (java.security.cert.X509Certificate) chain[i];
            }
            return result;
        }
    }

    private static final class Tls12SocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        Tls12SocketFactory(SSLSocketFactory value) { delegate = value; }

        private Socket enable(Socket socket) throws IOException {
            if (socket instanceof SSLSocket) {
                SSLSocket ssl = (SSLSocket) socket;
                List<String> supported = Arrays.asList(ssl.getSupportedProtocols());
                if (supported.contains("TLSv1.2")) {
                    ssl.setEnabledProtocols(new String[]{"TLSv1.2"});
                }
            }
            return socket;
        }
        @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
        @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
        @Override public Socket createSocket() throws IOException { return enable(delegate.createSocket()); }
        @Override public Socket createSocket(String host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(String host, int port, InetAddress local, int localPort)
                throws IOException { return enable(delegate.createSocket(host, port, local, localPort)); }
        @Override public Socket createSocket(InetAddress host, int port) throws IOException {
            return enable(delegate.createSocket(host, port));
        }
        @Override public Socket createSocket(InetAddress host, int port, InetAddress local, int localPort)
                throws IOException { return enable(delegate.createSocket(host, port, local, localPort)); }
        @Override public Socket createSocket(Socket socket, String host, int port, boolean autoClose)
                throws IOException { return enable(delegate.createSocket(socket, host, port, autoClose)); }
    }
}
