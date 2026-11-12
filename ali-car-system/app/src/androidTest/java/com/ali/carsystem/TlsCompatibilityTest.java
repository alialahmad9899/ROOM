package com.ali.carsystem;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class TlsCompatibilityTest {
    @Test public void tls12IsAvailableOnApi21() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SSLSocketFactory factory = LegacyTls.factory(context);
        SSLSocket socket = (SSLSocket) factory.createSocket();
        try {
            boolean tls12 = false;
            for (String protocol : socket.getSupportedProtocols()) {
                if ("TLSv1.2".equals(protocol)) tls12 = true;
            }
            assertTrue("TLS 1.2 must be available on Android API 21", tls12);
        } finally {
            socket.close();
        }
    }

    @Test public void bundledIsrgRootX1IsReadableAndParseable() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (InputStream input = context.getAssets().open("isrg_root_x1.pem")) {
            Certificate certificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
            assertNotNull(certificate);
        }
    }
}
