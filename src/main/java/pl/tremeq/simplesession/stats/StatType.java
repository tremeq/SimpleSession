package pl.tremeq.simplesession.stats;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Leaderboard / ranking types.
 *
 * @author TremeQ
 */
public enum StatType {
    /** Current session of online players (not stored in the database). */
    SESSION(true),
    /** All-time play time. */
    TOTAL(true),
    /** Longest single session. */
    RECORD(true),
    /** Play time today. */
    TODAY(true),
    /** Play time this week. */
    WEEK(true),
    /** Play time this month. */
    MONTH(true),
    /** Number of sessions (joins). */
    SESSIONS(false);

    private final boolean time;

    StatType(boolean time) {
        this.time = time;
    }

    /**
     * @return true if values are durations in seconds, false for plain counts
     */
    public boolean isTime() {
        return time;
    }

    /**
     * @return true if the type is stored in the database
     */
    public boolean isPersistent() {
        return this != SESSION;
    }

    /**
     * @return Config / command key
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param key Type key, case-insensitive
     * @return Matching type or null
     */
    public static StatType byKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (StatType type : values()) {
            if (type.key().equals(normalized)) {
                return type;
            }
        }
        return null;
    }

    /**
     * @return All keys, for tab completion and messages
     */
    public static List<String> keys() {
        return Arrays.stream(values()).map(StatType::key).toList();
    }
}
