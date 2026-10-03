package pl.tremeq.simplesession.format;

import java.util.List;
import java.util.Locale;

/**
 * Grammatical plural rules used to pick the correct unit name for a number.
 *
 * Unit names are configured as a list of forms: [one, few, many].
 * - POLISH:  1 minuta, 2-4 minuty (except 12-14), 0/5+ minut
 * - ENGLISH: 1 minute, everything else minutes (uses the last form)
 * - NONE:    always the first form
 *
 * @author TremeQ
 */
public enum PluralRule {
    POLISH {
        @Override
        int formIndex(long n) {
            if (n == 1) {
                return 0;
            }
            long lastDigit = n % 10;
            long lastTwo = n % 100;
            if (lastDigit >= 2 && lastDigit <= 4 && (lastTwo < 12 || lastTwo > 14)) {
                return 1;
            }
            return 2;
        }
    },
    ENGLISH {
        @Override
        int formIndex(long n) {
            return n == 1 ? 0 : 2;
        }
    },
    NONE {
        @Override
        int formIndex(long n) {
            return 0;
        }
    };

    abstract int formIndex(long n);

    /**
     * Selects the correct form for the given number.
     *
     * @param forms Configured forms [one, few, many]; shorter lists are clamped
     * @param n Number the unit refers to
     * @return Selected form, or empty string if no forms are configured
     */
    public String select(List<String> forms, long n) {
        if (forms == null || forms.isEmpty()) {
            return "";
        }
        int index = Math.min(formIndex(Math.abs(n)), forms.size() - 1);
        return forms.get(index);
    }

    /**
     * Parses a rule name from config.
     *
     * @param name Rule name (polish, english, none)
     * @param fallback Rule used when the name is unknown
     * @return Parsed rule
     */
    public static PluralRule parse(String name, PluralRule fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
