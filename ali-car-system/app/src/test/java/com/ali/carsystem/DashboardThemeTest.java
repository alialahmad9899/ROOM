package com.ali.carsystem;

import org.junit.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.Assert.*;

public final class DashboardThemeTest {
    @Test public void keepsLegacyThemesAndAddsFiveDistinctProfessionalThemes() {
        DashboardTheme.Palette[] themes=DashboardTheme.all();
        assertEquals("Three existing themes plus five additions",8,themes.length);
        Set<String> ids=new HashSet<>();
        Set<Integer> accents=new HashSet<>();
        for(DashboardTheme.Palette theme:themes){
            assertTrue("Theme id must be unique",ids.add(theme.id));
            assertTrue("Each theme should have a distinct accent",accents.add(theme.accent));
            assertNotEquals("Background and foreground must remain distinguishable",
                    theme.background,theme.textPrimary);
        }
        assertTrue(ids.contains("ocean"));
        assertTrue(ids.contains("graphite"));
        assertTrue(ids.contains("sport"));
        assertTrue(ids.contains("obsidian_red"));
        assertTrue(ids.contains("arctic_blue"));
        assertTrue(ids.contains("emerald_luxe"));
        assertTrue(ids.contains("titanium_silver"));
        assertTrue(ids.contains("neon_pulse"));
    }

    @Test public void unknownThemeFallsBackToStableOceanPalette() {
        assertEquals("ocean",DashboardTheme.byId("not-a-theme").id);
        assertEquals("ocean",DashboardTheme.byId(null).id);
    }
}
