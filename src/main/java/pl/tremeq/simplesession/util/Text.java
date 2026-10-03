package pl.tremeq.simplesession.util;

import org.bukkit.ChatColor;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text helpers: legacy (&a) and hex (&#RRGGBB) color translation.
 *
 * @author TremeQ
 */
public final class Text {

    private static final Pattern HEX = Pattern.compile("&#([A-Fa-f0-9]{6})");

    private Text() {
    }

    /**
     * Translates & color codes and &#RRGGBB hex colors.
     *
     * @param text Raw text (null-safe)
     * @return Colored text
     */
    public static String color(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        Matcher matcher = HEX.matcher(text);
        StringBuilder builder = new StringBuilder();
        while (matcher.find()) {
            StringBuilder replacement = new StringBuilder("§x");
            for (char c : matcher.group(1).toCharArray()) {
                replacement.append('§').append(c);
            }
            matcher.appendReplacement(builder, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(builder);
        return ChatColor.translateAlternateColorCodes('&', builder.toString());
    }

    /**
     * Replaces placeholder pairs in a text.
     *
     * @param text Text
     * @param replacements Pairs: key, value, key, value...
     * @return Text with replacements applied
     */
    public static String replace(String text, String... replacements) {
        if (text == null) {
            return "";
        }
        String result = text;
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            result = result.replace(replacements[i], replacements[i + 1] == null ? "" : replacements[i + 1]);
        }
        return result;
    }
}
