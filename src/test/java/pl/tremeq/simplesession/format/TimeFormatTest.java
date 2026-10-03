package pl.tremeq.simplesession.format;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TimeFormatTest {

    private static final List<TimeUnitType> DHMS =
            List.of(TimeUnitType.DAYS, TimeUnitType.HOURS, TimeUnitType.MINUTES, TimeUnitType.SECONDS);

    private static SmartTimeFormat polish(int maxUnits, SmartTimeFormat.HideZero hide) {
        return new SmartTimeFormat(DHMS, UnitNames.POLISH_LONG, PluralRule.POLISH, "{value} {unit}",
                ", ", " i ", null, maxUnits, hide);
    }

    @Test
    void polishPluralForms() {
        List<String> forms = List.of("minuta", "minuty", "minut");
        assertEquals("minut", PluralRule.POLISH.select(forms, 0));
        assertEquals("minuta", PluralRule.POLISH.select(forms, 1));
        assertEquals("minuty", PluralRule.POLISH.select(forms, 2));
        assertEquals("minuty", PluralRule.POLISH.select(forms, 4));
        assertEquals("minut", PluralRule.POLISH.select(forms, 5));
        assertEquals("minut", PluralRule.POLISH.select(forms, 12));
        assertEquals("minut", PluralRule.POLISH.select(forms, 14));
        assertEquals("minuty", PluralRule.POLISH.select(forms, 22));
        assertEquals("minut", PluralRule.POLISH.select(forms, 25));
        assertEquals("minuty", PluralRule.POLISH.select(forms, 102));
        assertEquals("minut", PluralRule.POLISH.select(forms, 112));
    }

    @Test
    void englishAndNoneRules() {
        assertEquals("hour", PluralRule.ENGLISH.select(List.of("hour", "hours"), 1));
        assertEquals("hours", PluralRule.ENGLISH.select(List.of("hour", "hours"), 2));
        assertEquals("hours", PluralRule.ENGLISH.select(List.of("hour", "hours"), 0));
        assertEquals("h", PluralRule.NONE.select(List.of("h"), 5));
        assertEquals("", PluralRule.POLISH.select(List.of(), 5));
    }

    @Test
    void smartHidesZeroUnitsAndUsesLastSeparator() {
        SmartTimeFormat format = polish(0, SmartTimeFormat.HideZero.ALL);
        assertEquals("1 godzina i 5 sekund", format.format(3605));
        assertEquals("2 dni, 3 godziny, 21 minut i 1 sekunda", format.format(2 * 86400 + 3 * 3600 + 21 * 60 + 1));
        assertEquals("45 sekund", format.format(45));
        assertEquals("1 minuta", format.format(60));
        assertEquals("0 sekund", format.format(0));
        assertEquals("0 sekund", format.format(-10));
    }

    @Test
    void smartHideLeadingAndNone() {
        assertEquals("1 godzina, 0 minut i 5 sekund", polish(0, SmartTimeFormat.HideZero.LEADING).format(3605));
        assertEquals("0 dni, 1 godzina, 0 minut i 5 sekund", polish(0, SmartTimeFormat.HideZero.NONE).format(3605));
    }

    @Test
    void maxUnitsCountsFromLargestNonZeroUnit() {
        SmartTimeFormat two = polish(2, SmartTimeFormat.HideZero.ALL);
        assertEquals("1 godzina", two.format(3605));
        assertEquals("1 godzina i 1 minuta", two.format(3665));
        assertEquals("2 dni i 5 godzin", two.format(2 * 86400 + 5 * 3600 + 59));
        assertEquals("3 minuty i 10 sekund", two.format(190));
    }

    @Test
    void compactShortNames() {
        SmartTimeFormat compact = new SmartTimeFormat(DHMS, UnitNames.SHORT, PluralRule.NONE, "{value}{unit}",
                " ", " ", null, 2, SmartTimeFormat.HideZero.ALL);
        assertEquals("1h 1m", compact.format(3665));
        assertEquals("8s", compact.format(8));
        assertEquals("1d 2h", compact.format(86400 + 7200 + 30));
        assertEquals("0s", compact.format(0));
    }

    @Test
    void unitsListLimitsLargestUnit() {
        SmartTimeFormat hours = new SmartTimeFormat(List.of(TimeUnitType.HOURS, TimeUnitType.MINUTES), UnitNames.SHORT,
                PluralRule.NONE, "{value}{unit}", " ", " ", "brak", 0, SmartTimeFormat.HideZero.ALL);
        assertEquals("50h 1m", hours.format(50 * 3600 + 60 + 59));
        assertEquals("brak", hours.format(59));
    }

    @Test
    void weeksUnit() {
        SmartTimeFormat weeks = new SmartTimeFormat(List.of(TimeUnitType.WEEKS, TimeUnitType.DAYS), UnitNames.POLISH_LONG,
                PluralRule.POLISH, "{value} {unit}", ", ", " i ", null, 0, SmartTimeFormat.HideZero.ALL);
        assertEquals("2 tygodnie i 1 dzień", weeks.format(15 * 86400));
    }

    @Test
    void patternVariables() {
        PatternTimeFormat classic = new PatternTimeFormat("{days}d {hours}h {minutes}m {seconds}s", null, PluralRule.NONE);
        assertEquals("1d 1h 1m 5s", classic.format(86400 + 3665));

        PatternTimeFormat clock = new PatternTimeFormat("{total_hours}:{minutes_pad}:{seconds_pad}", null, PluralRule.NONE);
        assertEquals("25:01:05", clock.format(86400 + 3665));
        assertEquals("0:00:00", clock.format(0));

        PatternTimeFormat totals = new PatternTimeFormat("{total_days}|{total_minutes}|{total_seconds}", null, PluralRule.NONE);
        assertEquals("1|1501|90065", totals.format(86400 + 3665));

        PatternTimeFormat names = new PatternTimeFormat("{hours} {hours_name}, {minutes} {minutes_name}",
                UnitNames.POLISH_LONG, PluralRule.POLISH);
        assertEquals("2 godziny, 5 minut", names.format(2 * 3600 + 300));
        assertEquals("1 godzina, 1 minuta", names.format(3660));
    }
}
