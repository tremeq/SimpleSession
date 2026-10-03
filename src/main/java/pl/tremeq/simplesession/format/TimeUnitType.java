package pl.tremeq.simplesession.format;

import java.util.Locale;

/**
 * Time units supported by time formats, ordered from the largest to the smallest.
 *
 * @author TremeQ
 */
public enum TimeUnitType {
    WEEKS(604_800L),
    DAYS(86_400L),
    HOURS(3_600L),
    MINUTES(60L),
    SECONDS(1L);

    private final long seconds;

    TimeUnitType(long seconds) {
        this.seconds = seconds;
    }

    /**
     * @return Length of this unit in seconds
     */
    public long seconds() {
        return seconds;
    }

    /**
     * @return Config key of this unit (e.g. "hours")
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Finds a unit by its config key.
     *
     * @param key Unit key, case-insensitive (e.g. "days")
     * @return Matching unit or null
     */
    public static TimeUnitType byKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (TimeUnitType unit : values()) {
            if (unit.key().equals(normalized)) {
                return unit;
            }
        }
        return null;
    }
}
