package pl.tremeq.simplesession.stats;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Stored statistics that can be modified with /ss set|add|take.
 *
 * @author TremeQ
 */
public enum StatField {
    TOTAL("total_seconds", true),
    RECORD("record_seconds", true),
    SESSIONS("sessions", false);

    private final String column;
    private final boolean time;

    StatField(String column, boolean time) {
        this.column = column;
        this.time = time;
    }

    /**
     * @return Database column
     */
    public String column() {
        return column;
    }

    /**
     * @return true if the value is a duration in seconds
     */
    public boolean isTime() {
        return time;
    }

    /**
     * @return Command key
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param key Field key, case-insensitive
     * @return Matching field or null
     */
    public static StatField byKey(String key) {
        if (key == null) {
            return null;
        }
        for (StatField field : values()) {
            if (field.key().equalsIgnoreCase(key.trim())) {
                return field;
            }
        }
        return null;
    }

    /**
     * @return All keys, for tab completion
     */
    public static List<String> keys() {
        return Arrays.stream(values()).map(StatField::key).toList();
    }

    /**
     * Modification operation.
     */
    public enum Operation {
        SET, ADD, TAKE;

        /**
         * Applies the operation, never going below 0.
         *
         * @param current Current value
         * @param value Operand
         * @return New value
         */
        public long apply(long current, long value) {
            long result = switch (this) {
                case SET -> value;
                case ADD -> current + value;
                case TAKE -> current - value;
            };
            return Math.max(0, result);
        }

        /**
         * @param key "set", "add" or "take"
         * @return Matching operation or null
         */
        public static Operation byKey(String key) {
            return Arrays.stream(values())
                    .filter(op -> op.name().equalsIgnoreCase(key))
                    .findFirst().orElse(null);
        }
    }

    /**
     * What /ss reset can reset.
     */
    public enum ResetTarget {
        SESSION, TOTAL, RECORD, SESSIONS, PERIODS, ALL;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static ResetTarget byKey(String key) {
            return Arrays.stream(values())
                    .filter(t -> t.name().equalsIgnoreCase(key))
                    .findFirst().orElse(null);
        }

        public static List<String> keys() {
            return Arrays.stream(values()).map(ResetTarget::key).toList();
        }
    }
}
