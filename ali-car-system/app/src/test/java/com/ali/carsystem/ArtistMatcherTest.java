package com.ali.carsystem;

import org.junit.Test;
import static org.junit.Assert.*;

public final class ArtistMatcherTest {
    @Test public void matchesArabicAndLatinArtistMetadata() {
        assertTrue(ArtistMatcher.matchesAny("Nassif Zeytoun", "Toul Al Yom", "نصيف زيتون"));
        assertTrue(ArtistMatcher.matchesAny("George Wassouf", "Kalam El Nass", "جورج وسوف"));
        assertTrue(ArtistMatcher.matchesAny("فيروز", "كان عنا طاحون", "fairuz"));
        assertTrue(ArtistMatcher.matchesAny("Nancy Ajram", "Ah W Noss", "نانسي عجرم"));
        assertTrue(ArtistMatcher.matchesAny("Wael Kfoury", "Law Hobna Ghalta", "وائل كفوري"));
        assertTrue(ArtistMatcher.matchesAny("Assala Nasri", "Bent Akaber", "أصالة نصري"));
    }

    @Test public void normalizesArabicDiacriticsAndAlefVariants() {
        assertEquals(ArtistMatcher.normalize("إِلْيِسَا"), ArtistMatcher.normalize("اليسا"));
        assertEquals(ArtistMatcher.normalize("هبة طوجي"), ArtistMatcher.normalize("هَبَة طُوجِي"));
    }

    @Test public void doesNotMatchUnrelatedArtists() {
        assertFalse(ArtistMatcher.matchesAny("Unknown Artist", "Unknown Song", "فيروز", "fairuz"));
        assertFalse("The music library must exclude artists outside the requested list",
                ArtistMatcher.isSupportedArtist("Unknown Singer", "Unknown Song"));
    }

    @Test public void everyConfiguredArtistHasAliases() {
        String[] artists = {
            "فيروز","نصيف زيتون","أصالة نصري","جورج وسوف","صباح فخري","ميادة بسيليس",
            "ليندا بيطار","الشامي","فايا يونان","شادي جميل","إليسا","نانسي عجرم",
            "وائل كفوري","راغب علامة","ملحم زين","مروان خوري","كارول سماحة","فضل شاكر",
            "جوليا بطرس","عاصي الحلاني","فارس كرم","وائل جسار","ميريام فارس","هبة طوجي","زياد برجي"
        };
        for (String artist : artists) {
            assertTrue("Missing alias mapping for " + artist,
                    ArtistMatcher.aliasesFor(artist).length > 1);
            assertTrue("Supported singer must be accepted: " + artist,
                    ArtistMatcher.isSupportedArtist(artist, "sample song"));
        }
    }
}
