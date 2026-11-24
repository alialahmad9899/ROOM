package com.ali.carsystem;

import java.text.Normalizer;
import java.util.Locale;

/** Artist matching across Arabic and common Latin spellings used in audio metadata. */
public final class ArtistMatcher {
    private ArtistMatcher() {}

    private static final String[] SUPPORTED_ARTISTS = {
            "فيروز","نصيف زيتون","أصالة نصري","جورج وسوف","صباح فخري","ميادة بسيليس",
            "ليندا بيطار","الشامي","فايا يونان","شادي جميل","إليسا","نانسي عجرم",
            "وائل كفوري","راغب علامة","ملحم زين","مروان خوري","كارول سماحة","فضل شاكر",
            "جوليا بطرس","عاصي الحلاني","فارس كرم","وائل جسار","ميريام فارس","هبة طوجي","زياد برجي"
    };
    private static final String[][] NORMALIZED_SUPPORTED = buildSupportedAliases();

    private static String[][] buildSupportedAliases() {
        String[][] result = new String[SUPPORTED_ARTISTS.length][];
        for (int i = 0; i < SUPPORTED_ARTISTS.length; i++) {
            String[] aliases = aliasesFor(SUPPORTED_ARTISTS[i]);
            String[] normalized = new String[aliases.length + 1];
            normalized[0] = normalize(SUPPORTED_ARTISTS[i]);
            for (int j = 0; j < aliases.length; j++) normalized[j + 1] = normalize(aliases[j]);
            result[i] = normalized;
        }
        return result;
    }

    public static boolean isSupportedArtist(String artist, String title) {
        String a = normalize(artist), t = normalize(title);
        if (a.isEmpty() && t.isEmpty()) return false;
        for (String[] aliases : NORMALIZED_SUPPORTED) {
            for (String alias : aliases) {
                if (matchesPhrase(a, alias) || matchesPhrase(t, alias)) return true;
            }
        }
        return false;
    }

    public static boolean matchesAny(String artist, String title, String... aliases) {
        String a = normalize(artist), t = normalize(title);
        if (a.isEmpty() && t.isEmpty() || aliases == null) return false;
        for (String alias : aliases) {
            if (containsAlias(a, alias) || containsAlias(t, alias)) return true;
            for (String alternate : aliasesFor(alias)) {
                if (containsAlias(a, alternate) || containsAlias(t, alternate)) return true;
            }
        }
        return false;
    }

    private static boolean containsAlias(String text, String alias) {
        return matchesPhrase(text, normalize(alias));
    }

    private static boolean matchesPhrase(String text, String phrase) {
        if (text == null || text.isEmpty() || phrase == null || phrase.isEmpty()) return false;
        return (" " + text + " ").contains(" " + phrase + " ");
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String normalizedInput = value.replace('ئ','ي').replace('ؤ','و').replace("ء","");
        String s = Normalizer.normalize(normalizedInput, Normalizer.Form.NFD)
                .toLowerCase(Locale.ROOT)
                .replace('أ','ا').replace('إ','ا').replace('آ','ا')
                .replace('ى','ي').replace('ة','ه');

        // Avoid Unicode regex properties such as IsAlphabetic: Android 5's
        // ICU regex engine rejects them during class initialization.
        StringBuilder result = new StringBuilder(s.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < s.length();) {
            int codePoint = s.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);
            if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                continue;
            }
            if (Character.isLetterOrDigit(codePoint)) {
                if (pendingSpace && result.length() > 0) result.append(' ');
                result.appendCodePoint(codePoint);
                pendingSpace = false;
            } else if (result.length() > 0) {
                pendingSpace = true;
            }
        }
        return result.toString();
    }

    public static String[] aliasesFor(String artist) {
        String key = normalize(artist);
        if (key.contains("فيروز") || key.contains("fairuz") || key.contains("fairouz"))
            return new String[]{"فيروز","fairuz","fairouz","fairouz rahbani"};
        if (key.contains("نصيف") || key.contains("nassif"))
            return new String[]{"نصيف زيتون","نصيف","nassif zeytoun","nassif zeitoun","nassif"};
        if (key.contains("اصاله") || key.contains("asala") || key.contains("assala"))
            return new String[]{"أصالة نصري","اصالة نصري","asala nasri","assala nasri","asala","assala"};
        if (key.contains("جورج وسوف") || key.contains("george wassouf") || key.equals("wassouf"))
            return new String[]{"جورج وسوف","ابو وديع","george wassouf","georges wassouf","george wasouf","wassouf"};
        if (key.contains("صباح فخري") || key.contains("sabah fakhri"))
            return new String[]{"صباح فخري","sabah fakhri","sabah fakri"};
        if (key.contains("مياده بسيليس") || key.contains("ميادة بسيليس") || key.contains("mayada"))
            return new String[]{"ميادة بسيليس","mayada basilis","mayada basiliss","mayada"};
        if (key.contains("ليندا بيطار") || key.contains("linda baytar"))
            return new String[]{"ليندا بيطار","linda baytar","linda baitar"};
        if (key.contains("الشامي") || key.contains("al shami"))
            return new String[]{"الشامي","الشامي السوري","al shami","alshami","shami"};
        if (key.contains("فايا يونان") || key.contains("faya younan"))
            return new String[]{"فايا يونان","faya younan","faya"};
        if (key.contains("شادي جميل") || key.contains("shadi jamil"))
            return new String[]{"شادي جميل","shadi jamil"};
        if (key.contains("اليسا") || key.contains("elissa"))
            return new String[]{"إليسا","اليسا","elissa","elissar","elisa"};
        if (key.contains("نانسي") || key.contains("nancy"))
            return new String[]{"نانسي عجرم","nancy ajram","nancy"};
        if (key.contains("وايل كفوري") || key.contains("وائل كفوري") || key.contains("wael kfoury"))
            return new String[]{"وائل كفوري","wael kfoury","wael kafoury"};
        if (key.contains("راغب") || key.contains("ragheb"))
            return new String[]{"راغب علامة","ragheb alama","ragheb allama","ragheb"};
        if (key.contains("ملحم زين") || key.contains("melhem"))
            return new String[]{"ملحم زين","melhem zein","melhem zain"};
        if (key.contains("مروان خوري") || key.contains("marwan khoury"))
            return new String[]{"مروان خوري","marwan khoury","marwan khouri"};
        if (key.contains("كارول سماحه") || key.contains("carole"))
            return new String[]{"كارول سماحة","carole samaha","carol samaha"};
        if (key.contains("فضل شاكر") || key.contains("fadl"))
            return new String[]{"فضل شاكر","fadl shaker","fadel shaker"};
        if (key.contains("جوليا بطرس") || key.contains("julia"))
            return new String[]{"جوليا بطرس","julia boutros","julia butros"};
        if (key.contains("عاصي الحلاني") || key.contains("assie"))
            return new String[]{"عاصي الحلاني","assie el helani","assi al helani"};
        if (key.contains("فارس كرم") || key.contains("fares karam"))
            return new String[]{"فارس كرم","fares karam","farris karam"};
        if (key.contains("وايل جسار") || key.contains("وائل جسار") || key.contains("wael jassar"))
            return new String[]{"وائل جسار","wael jassar","wael jaser"};
        if (key.contains("ميريام فارس") || key.contains("مريام فارس") || key.contains("myriam"))
            return new String[]{"ميريام فارس","مريام فارس","myriam fares","myriam faris"};
        if (key.contains("هبه طوجي") || key.contains("hiba tawaji"))
            return new String[]{"هبة طوجي","hiba tawaji","hiba tawagy"};
        if (key.contains("زياد برجي") || key.contains("ziad bourji"))
            return new String[]{"زياد برجي","ziad bourji","ziad borji"};
        return new String[]{artist};
    }
}