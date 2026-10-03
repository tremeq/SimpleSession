package pl.tremeq.simplesession.stats;

import java.util.UUID;

/**
 * Statistics of a player as stored in the database.
 * Period values (today / week / month) are already summed for the given period keys.
 *
 * @param uuid Player UUID
 * @param name Last known name
 * @param total All-time play time in seconds
 * @param record Longest session in seconds
 * @param sessions Number of sessions
 * @param firstJoin First join (epoch millis, 0 = unknown)
 * @param lastSeen Last seen (epoch millis, 0 = unknown)
 * @param dayKey Epoch day the "today" value belongs to
 * @param today Seconds played on dayKey
 * @param weekKey First epoch day of the week the "week" value belongs to
 * @param week Seconds played in that week
 * @param monthKey First epoch day of the month the "month" value belongs to
 * @param month Seconds played in that month
 */
public record StoredStats(UUID uuid, String name, long total, long record, long sessions,
                          long firstJoin, long lastSeen,
                          long dayKey, long today, long weekKey, long week, long monthKey, long month) {

    /**
     * @return Average session length in seconds
     */
    public long average() {
        return sessions <= 0 ? 0 : total / sessions;
    }
}
