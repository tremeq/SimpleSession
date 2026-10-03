package pl.tremeq.simplesession.stats;

import java.util.UUID;

/**
 * A single leaderboard row.
 *
 * @param uuid Player UUID
 * @param name Last known player name
 * @param value Value (seconds for time types, count for sessions)
 */
public record LeaderboardEntry(UUID uuid, String name, long value) {
}
