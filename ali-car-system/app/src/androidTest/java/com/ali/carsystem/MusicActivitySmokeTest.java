package com.ali.carsystem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public final class MusicActivitySmokeTest {
    @Rule public ActivityTestRule<MusicActivity> activity =
            new ActivityTestRule<>(MusicActivity.class);

    @Test public void legacyAndroidArtistMatcherAndAutomaticSleepGuardsAreSafe() {
        assertTrue("Arabic artist lookup must initialize on the device runtime",
                ArtistMatcher.matchesAny("Nassif Zeytoun", "Toul Al Yom", "نصيف زيتون"));
        assertTrue("Arabic and Latin artist aliases must both work",
                ArtistMatcher.matchesAny("فيروز", "Fairuz Classics", "fairuz"));
        assertFalse("Charging must always block automatic sleep",
                MusicActivity.shouldAllowAutomaticSleep(true, false, false));
        assertFalse("Playing music must block automatic sleep",
                MusicActivity.shouldAllowAutomaticSleep(false, true, false));
        assertFalse("Active navigation must block automatic sleep",
                MusicActivity.shouldAllowAutomaticSleep(false, false, true));
        assertTrue("Idle sleep is permitted only with all blockers absent",
                MusicActivity.shouldAllowAutomaticSleep(false, false, false));
    }

    @Test public void musicScreenStartsAndInstallsItsLayout() {
        MusicActivity screen=activity.getActivity();
        assertNotNull(screen);
        assertFalse(screen.isFinishing());
        View content=screen.findViewById(android.R.id.content);
        assertTrue(content instanceof ViewGroup);
        assertTrue(((ViewGroup)content).getChildCount()>0);
        assertNotNull("Navigation guidance banner",
                content.findViewWithTag("navigation_guidance_banner"));
        assertNotNull("Album filters",content.findViewWithTag("music_playlist_filters"));
        assertNotNull("Local audio import",content.findViewWithTag("music_add_local_files"));
        assertNotNull("Song and artist search",content.findViewWithTag("music_search"));
        assertNotNull("Music sorting control",content.findViewWithTag("music_sort"));
        assertNotNull("Duplicate review control",content.findViewWithTag("music_duplicates"));
        assertNotNull("Empty playlist state",content.findViewWithTag("music_playlist_empty"));

        TextView variety=(TextView)content.findViewWithTag("music_variety_album");
        assertNotNull("Ready-made variety album",variety);
        assertEquals("متنوعة",variety.getText().toString());
        assertTrue("Album labels readable",
                variety.getTextSize()/screen.getResources().getDisplayMetrics().scaledDensity>=14f);

        TextView archive=(TextView)content.findViewWithTag("music_archive_album");
        assertNotNull("Ready-made archive album",archive);
        assertEquals("الأرشيف",archive.getText().toString());
        assertTrue("Archive album name readable",archive.getTextSize()
                / screen.getResources().getDisplayMetrics().scaledDensity>=14f);

        TextView heading=(TextView)content.findViewWithTag("music_library_title");
        assertNotNull(heading);
        assertTrue("Library heading enlarged",
                heading.getTextSize()/screen.getResources().getDisplayMetrics().scaledDensity>=19f);
    }
}
