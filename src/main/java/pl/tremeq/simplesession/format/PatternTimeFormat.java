package pl.tremeq.simplesession.format;

/**
 * Classic pattern based time format (e.g. "{days}d {hours}h {minutes}m {seconds}s").
 *
 * Variables:
 * - {days}, {hours}, {minutes}, {seconds}         - components (days total, hours 0-23, minutes/seconds 0-59)
 * - {total_days}, {total_hours}, {total_minutes}, {total_seconds} - whole duration in one unit
 * - {hours_pad}, {minutes_pad}, {seconds_pad}      - components padded to 2 digits (05)
 * - {days_name}, {hours_name}, {minutes_name}, {seconds_name} - unit name with correct plural form
 *
 * @author TremeQ
 */
public final class PatternTimeFormat implements TimeFormat {

    private final String pattern;
    private final UnitNames names;
    private final PluralRule rule;

    /**
     * @param pattern Pattern with variables
     * @param names Unit names for {x_name} variables (may be null)
     * @param rule Plural rule for {x_name} variables
     */
    public PatternTimeFormat(String pattern, UnitNames names, PluralRule rule) {
        this.pattern = pattern == null ? "" : pattern;
        this.names = names;
        this.rule = rule == null ? PluralRule.NONE : rule;
    }

    @Override
    public String format(long totalSeconds) {
        long total = Math.max(0, totalSeconds);
        long days = total / 86_400;
        long hours = (total % 86_400) / 3_600;
        long minutes = (total % 3_600) / 60;
        long seconds = total % 60;

        String result = pattern;
        if (result.indexOf('{') < 0) {
            return result;
        }
        result = result
                .replace("{total_days}", String.valueOf(days))
                .replace("{total_hours}", String.valueOf(total / 3_600))
                .replace("{total_minutes}", String.valueOf(total / 60))
                .replace("{total_seconds}", String.valueOf(total))
                .replace("{hours_pad}", pad(hours))
                .replace("{minutes_pad}", pad(minutes))
                .replace("{seconds_pad}", pad(seconds))
                .replace("{days_name}", name(TimeUnitType.DAYS, days))
                .replace("{hours_name}", name(TimeUnitType.HOURS, hours))
                .replace("{minutes_name}", name(TimeUnitType.MINUTES, minutes))
                .replace("{seconds_name}", name(TimeUnitType.SECONDS, seconds))
                .replace("{days}", String.valueOf(days))
                .replace("{hours}", String.valueOf(hours))
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{seconds}", String.valueOf(seconds));
        return result;
    }

    private String name(TimeUnitType unit, long value) {
        return names == null ? "" : names.name(unit, value, rule);
    }

    private static String pad(long value) {
        return value < 10 ? "0" + value : String.valueOf(value);
    }
}
