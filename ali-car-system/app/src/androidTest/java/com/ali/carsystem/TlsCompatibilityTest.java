package com.ali.carsystem;

import android.content.Context;
import android.test.InstrumentationTestCase;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public final class TlsCompatibilityTest extends InstrumentationTestCase {
    public void testTls12IsAvailableOnApi21() throws Exception {
        Context context = getInstrumentation().getTargetContext();
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
    public void testBundledRootCertificateIsReadable() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        assertNotNull(context.getAssets().open("isrg_root_x1.pem"));
    }
}
