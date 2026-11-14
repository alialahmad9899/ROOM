package com.ali.carsystem;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class CarBackupManagerSmokeTest {
    @Test public void backupRestoresFavoritesNavigationAndTypedSettings() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences audio=context.getSharedPreferences("ali_car_audio",Context.MODE_PRIVATE);
        SharedPreferences navigation=context.getSharedPreferences("ali_car_navigation",Context.MODE_PRIVATE);
        SharedPreferences ui=context.getSharedPreferences("ali_car_navigation_ui",Context.MODE_PRIVATE);
        Set<String> favorites=new HashSet<>();favorites.add("101");favorites.add("202");
        audio.edit().putStringSet("favorites",favorites).putBoolean("playing",true).apply();
        navigation.edit().putBoolean("voice_enabled",false).putFloat("voice_volume",0.65f).apply();
        ui.edit().putString("selected_route_payload","{\"destination_name\":\"دمشق\"}")
                .putFloat("map_zoom",13.5f).apply();

        String backup=CarBackupManager.exportJson(context);
        audio.edit().clear().apply();navigation.edit().clear().apply();ui.edit().clear().apply();
        assertEquals(5,CarBackupManager.restoreJson(context,backup));
        assertEquals(favorites,audio.getStringSet("favorites",new HashSet<>()));
        assertFalse("A backup must never restart audio by itself",audio.getBoolean("playing",true));
        assertFalse(audio.getBoolean("resume_enabled",true));
        assertFalse(navigation.getBoolean("voice_enabled",true));
        assertEquals(0.65f,navigation.getFloat("voice_volume",0f),0.001f);
        assertTrue(ui.getString("selected_route_payload","").contains("دمشق"));
        assertEquals(13.5f,ui.getFloat("map_zoom",0f),0.001f);
    }

    @Test public void invalidBackupIsRejectedBeforePreferencesAreChanged() throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences audio=context.getSharedPreferences("ali_car_audio",Context.MODE_PRIVATE);
        audio.edit().putString("backup_validation_marker","keep").apply();
        try{
            CarBackupManager.restoreJson(context,"{\"format\":\"NOT_ALI_CAR\"}");
            fail("Invalid backup must be rejected");
        }catch(IllegalArgumentException expected){
            assertEquals("keep",audio.getString("backup_validation_marker",null));
        }
    }
}
