package pl.tremeq.simplesession.format;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Set of unit names (with plural forms) used by smart time formats.
 *
 * @author TremeQ
 */
public final class UnitNames {

    /** Built-in Polish long names, used when a preset is missing. */
    public static final UnitNames POLISH_LONG = new UnitNames(Map.of(
            TimeUnitType.WEEKS, List.of("tydzień", "tygodnie", "tygodni"),
            TimeUnitType.DAYS, List.of("dzień", "dni", "dni"),
            TimeUnitType.HOURS, List.of("godzina", "godziny", "godzin"),
            TimeUnitType.MINUTES, List.of("minuta", "minuty", "minut"),
            TimeUnitType.SECONDS, List.of("sekunda", "sekundy", "sekund")));

    /** Built-in short names (w, d, h, m, s). */
    public static final UnitNames SHORT = new UnitNames(Map.of(
            TimeUnitType.WEEKS, List.of("w"),
            TimeUnitType.DAYS, List.of("d"),
            TimeUnitType.HOURS, List.of("h"),
            TimeUnitType.MINUTES, List.of("m"),
            TimeUnitType.SECONDS, List.of("s")));

    private final Map<TimeUnitType, List<String>> forms;

    /**
     * @param forms Plural forms per unit
     */
    public UnitNames(Map<TimeUnitType, List<String>> forms) {
        Map<TimeUnitType, List<String>> copy = new EnumMap<>(TimeUnitType.class);
        forms.forEach((unit, list) -> copy.put(unit, List.copyOf(list)));
        this.forms = copy;
    }

    /**
     * Gets the unit name matching the number.
     *
     * @param unit Time unit
     * @param value Number of units
     * @param rule Plural rule
     * @return Unit name, empty if not configured
     */
    public String name(TimeUnitType unit, long value, PluralRule rule) {
        return rule.select(forms.get(unit), value);
    }
}
