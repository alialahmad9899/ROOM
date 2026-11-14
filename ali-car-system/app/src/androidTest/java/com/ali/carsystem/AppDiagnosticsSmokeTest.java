package com.ali.carsystem;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class AppDiagnosticsSmokeTest {
    @Test public void appErrorsArePersistedInsidePrivateAppStorage() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        AppDiagnostics.record(context, "instrumentation-diagnostic-test",
                new IllegalStateException("expected-test-entry"));
        File file = AppDiagnostics.getLogFile(context);
        assertTrue("Internal error log must be created", file.isFile());
        assertTrue("Internal error log must contain the test entry", file.length() > 0L);
    }
}
