package com.ali.carsystem;

import android.content.Context;
import android.content.SharedPreferences;

final class DashboardTheme {
    static final class Palette {
        final String id, name, description;
        final int background, backgroundEnd, surface, raised, border;
        final int accent, secondary, needle, track, textPrimary, textSecondary, glow;

        Palette(String id, String name, String description, int background, int backgroundEnd,
                int surface, int raised, int border, int accent, int secondary, int needle,
                int track, int textPrimary, int textSecondary, int glow) {
            this.id=id; this.name=name; this.description=description;
            this.background=background; this.backgroundEnd=backgroundEnd;
            this.surface=surface; this.raised=raised; this.border=border;
            this.accent=accent; this.secondary=secondary; this.needle=needle;
            this.track=track; this.textPrimary=textPrimary; this.textSecondary=textSecondary;
            this.glow=glow;
        }
    }

    private static final Palette[] THEMES = {
        new Palette("ocean","Ocean Blue","الأزرق الهادئ الأصلي",
                0xFF020812,0xFF102A3A,0xB5101F2C,0x77304457,0x8862DDF4,
                0xFF62DDF4,0xFFFFD36B,0xFFFF5876,0x664B90AE,0xFFF4FBFF,0xFFB9D5E2,0xFF42CDEB),
        new Palette("graphite","Graphite","الرمادي المعدني الأصلي",
                0xFF0A0E13,0xFF27323B,0xC018222B,0x88414B55,0x88B9CAD5,
                0xFFB9CAD5,0xFF79D4E7,0xFFFFB15C,0x665D6A76,0xFFF6F8FA,0xFFC0CBD2,0xFF91A5B3),
        new Palette("sport","Sport","الطابع الرياضي الأصلي",
                0xFF100D0E,0xFF35201D,0xC01D1718,0x88432D2A,0x88FF745F,
                0xFFFF745F,0xFFFFD08A,0xFFFF4558,0x665F3938,0xFFFFF6F0,0xFFE6C9BE,0xFFFF654F),
        new Palette("obsidian_red","Obsidian Red","أسود كربوني مع أحمر رياضي",
                0xFF08090D,0xFF220D15,0xCC17141A,0x88312630,0x99FF465D,
                0xFFFF465D,0xFFFFA36D,0xFFFF304F,0x77502A37,0xFFFFF7F8,0xFFD7C5CB,0xFFFF3855),
        new Palette("arctic_blue","Arctic Blue","كحلي عميق وأزرق جليدي",
                0xFF06111F,0xFF0C2947,0xCC10263B,0x88304B66,0x9954D9FF,
                0xFF54D9FF,0xFF9CCBFF,0xFF35B5FF,0x66416E91,0xFFF1FAFF,0xFFB9D4EA,0xFF36BDF2),
        new Palette("emerald_luxe","Emerald Luxe","زمردي فاخر مع لمسات ذهبية",
                0xFF06140F,0xFF12362A,0xCC102A22,0x88304D3F,0x9945D6A0,
                0xFF45D6A0,0xFFE5C478,0xFF48E0A8,0x66416D5B,0xFFF2FFF8,0xFFB8D8C8,0xFF35C994),
        new Palette("titanium_silver","Titanium Silver","تيتانيوم فضي بتصميم معدني",
                0xFF0F1318,0xFF303A44,0xCC202A33,0x88414E59,0x99DCE6EC,
                0xFFDCE6EC,0xFF9BB7C8,0xFFF5A65B,0x665B6A76,0xFFFFFFFF,0xFFC2CDD4,0xFFB8CAD6),
        new Palette("neon_pulse","Neon Pulse","بنفسجي مستقبلي بإضاءة نيون",
                0xFF10091A,0xFF29103D,0xCC21142F,0x883D2852,0x99D28BFF,
                0xFFD28BFF,0xFF62F4E5,0xFFFF69C9,0x665B3975,0xFFFFF7FF,0xFFD7C3E8,0xFFBE70F4)
    };

    private DashboardTheme() {}

    static Palette[] all() { return THEMES.clone(); }

    static Palette byId(String id) {
        if (id != null) for (Palette palette : THEMES) if (palette.id.equals(id)) return palette;
        return THEMES[0];
    }

    static Palette from(Context context) {
        SharedPreferences prefs=context.getSharedPreferences("ali_car_v3",Context.MODE_PRIVATE);
        return byId(prefs.getString("dashboard_theme","ocean"));
    }
}
