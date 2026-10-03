package pl.tremeq.simplesession.format;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses durations typed in commands, e.g. "3600", "1h30m", "2d 5h", "45s".
 *
 * @author TremeQ
 */
public final class TimeParser {

    private static final Pattern TOKEN = Pattern.compile("(\\d+)\\s*([wdhms])");
    private static final Pattern FULL = Pattern.compile("(\\s*\\d+\\s*[wdhms]\\s*)+");

    private TimeParser() {
    }

    /**
     * Parses a duration.
     *
     * @param input Plain number of seconds or units (w, d, h, m, s)
     * @return Seconds, or -1 when the input is invalid
     */
    public static long parse(String input) {
        if (input == null) {
            return -1;
        }
        String text = input.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return -1;
        }
        try {
            if (text.chars().allMatch(Character::isDigit)) {
                return Long.parseLong(text);
            }
            if (!FULL.matcher(text).matches()) {
                return -1;
            }
            long total = 0;
            Matcher matcher = TOKEN.matcher(text);
            while (matcher.find()) {
                long value = Long.parseLong(matcher.group(1));
                TimeUnitType unit = switch (matcher.group(2)) {
                    case "w" -> TimeUnitType.WEEKS;
                    case "d" -> TimeUnitType.DAYS;
                    case "h" -> TimeUnitType.HOURS;
                    case "m" -> TimeUnitType.MINUTES;
                    default -> TimeUnitType.SECONDS;
                };
                total = Math.addExact(total, Math.multiplyExact(value, unit.seconds()));
            }
            return total;
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }
}
