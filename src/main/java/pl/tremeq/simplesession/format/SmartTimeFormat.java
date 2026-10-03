package pl.tremeq.simplesession.format;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Smart time format: builds the text only from meaningful units, with correct plural forms.
 *
 * Examples (Polish names): "1 godzina i 5 minut", "2 dni, 3 godziny", "45 sekund".
 *
 * @author TremeQ
 */
public final class SmartTimeFormat implements TimeFormat {

    /**
     * Which zero-valued units are hidden.
     */
    public enum HideZero {
        /** Hide every unit equal to 0 ("1 godzina i 5 sekund"). */
        ALL,
        /** Hide only zero units before the first non-zero one ("1 godzina, 0 minut i 5 sekund"). */
        LEADING,
        /** Never hide units ("0 dni, 1 godzina, 0 minut i 5 sekund"). */
        NONE;

        public static HideZero parse(String name, HideZero fallback) {
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

    private final List<TimeUnitType> units;
    private final UnitNames names;
    private final PluralRule rule;
    private final String unitFormat;
    private final String separator;
    private final String lastSeparator;
    private final String zeroText;
    private final int maxUnits;
    private final HideZero hideZero;

    /**
     * @param units Units to use; the largest one absorbs everything above it
     * @param names Unit names
     * @param rule Plural rule
     * @param unitFormat Format of a single unit, variables {value} and {unit}
     * @param separator Separator between units
     * @param lastSeparator Separator before the last unit
     * @param zeroText Text used when the duration is 0 (null = "0" + smallest unit)
     * @param maxUnits Max number of consecutive units counted from the largest non-zero one (0 = all)
     * @param hideZero Zero unit hiding mode
     */
    public SmartTimeFormat(List<TimeUnitType> units, UnitNames names, PluralRule rule, String unitFormat,
                           String separator, String lastSeparator, String zeroText, int maxUnits, HideZero hideZero) {
        List<TimeUnitType> sorted = new ArrayList<>(units == null || units.isEmpty()
                ? List.of(TimeUnitType.DAYS, TimeUnitType.HOURS, TimeUnitType.MINUTES, TimeUnitType.SECONDS)
                : units.stream().distinct().toList());
        sorted.sort(Comparator.comparingLong(TimeUnitType::seconds).reversed());
        this.units = List.copyOf(sorted);
        this.names = names == null ? UnitNames.SHORT : names;
        this.rule = rule == null ? PluralRule.NONE : rule;
        this.unitFormat = unitFormat == null ? "{value} {unit}" : unitFormat;
        this.separator = separator == null ? " " : separator;
        this.lastSeparator = lastSeparator == null ? this.separator : lastSeparator;
        this.zeroText = zeroText;
        this.maxUnits = Math.max(0, maxUnits);
        this.hideZero = hideZero == null ? HideZero.ALL : hideZero;
    }

    @Override
    public String format(long totalSeconds) {
        long rest = Math.max(0, totalSeconds);
        long[] values = new long[units.size()];
        for (int i = 0; i < units.size(); i++) {
            long unitSeconds = units.get(i).seconds();
            values[i] = rest / unitSeconds;
            rest %= unitSeconds;
        }

        int first = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] != 0) {
                first = i;
                break;
            }
        }
        if (first < 0) {
            return zeroText != null ? zeroText : unit(units.get(units.size() - 1), 0);
        }

        int start = (hideZero == HideZero.NONE && maxUnits == 0) ? 0 : first;
        int end = maxUnits > 0 ? Math.min(units.size(), start + maxUnits) : units.size();

        List<String> parts = new ArrayList<>();
        for (int i = start; i < end; i++) {
            if (values[i] == 0 && hideZero == HideZero.ALL) {
                continue;
            }
            parts.add(unit(units.get(i), values[i]));
        }
        return join(parts);
    }

    private String unit(TimeUnitType unit, long value) {
        return unitFormat
                .replace("{value}", String.valueOf(value))
                .replace("{unit}", names.name(unit, value, rule));
    }

    private String join(List<String> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                builder.append(i == parts.size() - 1 ? lastSeparator : separator);
            }
            builder.append(parts.get(i));
        }
        return builder.toString();
    }
}
