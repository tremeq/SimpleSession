package pl.tremeq.simplesession.stats;

import java.util.UUID;

/**
 * In-memory statistics of an online player.
 *
 * Holds absolute values; play time is accrued from the last accrue timestamp,
 * so "live" getters include time not yet written to the database.
 * All methods are synchronized because placeholders may be requested asynchronously.
 *
 * @author TremeQ
 */
public final class PlayerStats {

    private final UUID uuid;
    private String name;
    private long total;
    private long record;
    private long sessions;
    private long firstJoin;
    private long lastSeen;
    private long dayKey;
    private long today;
    private long weekKey;
    private long week;
    private long monthKey;
    private long month;
    private long lastAccrue;
    private long carryMillis;

    /**
     * Creates in-memory statistics from stored values.
     *
     * @param stored Stored statistics
     */
    public PlayerStats(StoredStats stored) {
        this.uuid = stored.uuid();
        this.name = stored.name();
        this.total = stored.total();
        this.record = stored.record();
        this.sessions = stored.sessions();
        this.firstJoin = stored.firstJoin();
        this.lastSeen = stored.lastSeen();
        this.dayKey = stored.dayKey();
        this.today = stored.today();
        this.weekKey = stored.weekKey();
        this.week = stored.week();
        this.monthKey = stored.monthKey();
        this.month = stored.month();
    }

    /**
     * Starts counting play time for a joining player.
     *
     * @param name Current player name
     * @param now Current time millis
     * @param newSession true for a new session (increments session count)
     */
    public synchronized void start(String name, long now, boolean newSession) {
        this.name = name;
        this.lastAccrue = now;
        this.carryMillis = 0;
        this.lastSeen = now;
        if (firstJoin <= 0) {
            firstJoin = now;
        }
        if (newSession) {
            sessions++;
        }
    }

    /**
     * Adds play time since the last accrue to all counters.
     *
     * @param now Current time millis
     * @param periods Calendar settings
     * @param sessionSeconds Current session length (updates the record)
     */
    public synchronized void accrue(long now, Periods periods, long sessionSeconds) {
        long delta = Math.max(0, now - lastAccrue) + carryMillis;
        long seconds = delta / 1000;
        carryMillis = delta % 1000;
        lastAccrue = now;
        roll(periods);
        total += seconds;
        today += seconds;
        week += seconds;
        month += seconds;
        record = Math.max(record, sessionSeconds);
        lastSeen = now;
    }

    private void roll(Periods periods) {
        long day = periods.today();
        if (day != dayKey) {
            dayKey = day;
            today = 0;
        }
        long weekStart = periods.weekStart(day);
        if (weekStart != weekKey) {
            weekKey = weekStart;
            week = 0;
        }
        long monthStart = periods.monthStart(day);
        if (monthStart != monthKey) {
            monthKey = monthStart;
            month = 0;
        }
    }

    private long pending(long now) {
        return (Math.max(0, now - lastAccrue) + carryMillis) / 1000;
    }

    /**
     * Gets a live value including not yet accrued time.
     *
     * @param type Statistic type (SESSION is not supported here)
     * @param now Current time millis
     * @param periods Calendar settings
     * @param sessionSeconds Current session length (for RECORD)
     * @return Value in seconds (or count for SESSIONS)
     */
    public synchronized long live(StatType type, long now, Periods periods, long sessionSeconds) {
        long day = periods.today();
        return switch (type) {
            case TOTAL -> total + pending(now);
            case RECORD -> Math.max(record, sessionSeconds);
            case SESSIONS -> sessions;
            case TODAY -> (day == dayKey ? today : 0) + pending(now);
            case WEEK -> (periods.weekStart(day) == weekKey ? week : 0) + pending(now);
            case MONTH -> (periods.monthStart(day) == monthKey ? month : 0) + pending(now);
            case SESSION -> sessionSeconds;
        };
    }

    /**
     * @param now Current time millis
     * @return Live average session length in seconds
     */
    public synchronized long liveAverage(long now) {
        return sessions <= 0 ? 0 : (total + pending(now)) / sessions;
    }

    /**
     * Changes a stored value (command /ss set|add|take).
     *
     * @param field Field
     * @param operation Operation
     * @param value Operand
     * @return New value
     */
    public synchronized long modify(StatField field, StatField.Operation operation, long value) {
        return switch (field) {
            case TOTAL -> total = operation.apply(total, value);
            case RECORD -> record = operation.apply(record, value);
            case SESSIONS -> sessions = operation.apply(sessions, value);
        };
    }

    /**
     * Resets values (command /ss reset). SESSION is handled by the session manager.
     *
     * @param target What to reset
     */
    public synchronized void reset(StatField.ResetTarget target) {
        switch (target) {
            case TOTAL -> total = 0;
            case RECORD -> record = 0;
            case SESSIONS -> sessions = 0;
            case PERIODS -> {
                today = 0;
                week = 0;
                month = 0;
            }
            case ALL -> {
                total = 0;
                record = 0;
                sessions = 0;
                today = 0;
                week = 0;
                month = 0;
            }
            default -> {
            }
        }
    }

    /**
     * @return Immutable copy for saving on the database thread
     */
    public synchronized Snapshot snapshot() {
        return new Snapshot(uuid, name, total, record, sessions, firstJoin, lastSeen, dayKey, today);
    }

    /**
     * @return Current values as stored statistics (used by /ss check for online players)
     */
    public synchronized StoredStats toStored() {
        return new StoredStats(uuid, name, total, record, sessions, firstJoin, lastSeen,
                dayKey, today, weekKey, week, monthKey, month);
    }

    public synchronized long firstJoin() {
        return firstJoin;
    }

    public synchronized long lastSeen() {
        return lastSeen;
    }

    public UUID uuid() {
        return uuid;
    }

    /**
     * Values written to the database.
     */
    public record Snapshot(UUID uuid, String name, long total, long record, long sessions,
                           long firstJoin, long lastSeen, long dayKey, long today) {
    }
}
