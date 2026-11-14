package com.ali.carsystem;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONArray;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class TripHistoryStoreSmokeTest {
    @Test public void completedTripsAreStoredNewestFirstAndCappedAtThirty() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs=context.getSharedPreferences("ali_car_v3",Context.MODE_PRIVATE);
        TripHistoryStore.clear(context);
        long now=System.currentTimeMillis();
        assertTrue(TripHistoryStore.record(context,12500L,96f,now-600000L,now));
        JSONArray first=TripHistoryStore.read(context);
        assertEquals(1,first.length());
        assertEquals(12500L,first.getJSONObject(0).getLong("distance_meters"));
        assertEquals(600000L,first.getJSONObject(0).getLong("duration_ms"));
        for(int i=0;i<36;i++)
            TripHistoryStore.record(context,1000L+i,50f,now+i*1000L,now+(i+1)*1000L);
        JSONArray capped=TripHistoryStore.read(context);
        assertEquals("Only the 30 newest trips are retained",30,capped.length());
        assertEquals(1035L,capped.getJSONObject(0).getLong("distance_meters"));
        assertTrue(TripHistoryStore.format(context).contains("المسافة"));
        TripHistoryStore.clear(context);
        assertEquals(0,TripHistoryStore.read(context).length());
    }
}
