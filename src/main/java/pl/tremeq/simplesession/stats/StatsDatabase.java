package pl.tremeq.simplesession.stats;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * SQLite storage for persistent statistics.
 *
 * Not thread-safe: all calls must come from a single thread (StatsManager uses one DB thread).
 *
 * Tables:
 * - {prefix}players: one row per player (totals, record, sessions, first join, last seen)
 * - {prefix}daily:   seconds played per player per day (used for today / week / month)
 *
 * @author TremeQ
 */
public final class StatsDatabase implements AutoCloseable {

    private final Connection connection;
    private final String players;
    private final String daily;

    /**
     * Opens (and creates if needed) the database.
     *
     * @param file SQLite file
     * @param tablePrefix Table name prefix (letters, digits, underscore)
     * @throws SQLException when the database cannot be opened
     */
    public StatsDatabase(File file, String tablePrefix) throws SQLException {
        String prefix = tablePrefix == null || !tablePrefix.matches("[A-Za-z0-9_]*") ? "ss_" : tablePrefix;
        this.players = prefix + "players";
        this.daily = prefix + "daily";

        File parent = file.getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException ignored) {
            // DriverManager may still find it through the service loader
        }
        this.connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA synchronous=NORMAL");
            st.execute("PRAGMA busy_timeout=5000");
            st.execute("CREATE TABLE IF NOT EXISTS " + players + " ("
                    + "uuid TEXT PRIMARY KEY,"
                    + "name TEXT NOT NULL,"
                    + "name_lower TEXT NOT NULL,"
                    + "total_seconds INTEGER NOT NULL DEFAULT 0,"
                    + "record_seconds INTEGER NOT NULL DEFAULT 0,"
                    + "sessions INTEGER NOT NULL DEFAULT 0,"
                    + "first_join INTEGER NOT NULL DEFAULT 0,"
                    + "last_seen INTEGER NOT NULL DEFAULT 0)");
            st.execute("CREATE INDEX IF NOT EXISTS " + players + "_name ON " + players + "(name_lower)");
            st.execute("CREATE INDEX IF NOT EXISTS " + players + "_total ON " + players + "(total_seconds)");
            st.execute("CREATE INDEX IF NOT EXISTS " + players + "_record ON " + players + "(record_seconds)");
            st.execute("CREATE INDEX IF NOT EXISTS " + players + "_sessions ON " + players + "(sessions)");
            st.execute("CREATE TABLE IF NOT EXISTS " + daily + " ("
                    + "uuid TEXT NOT NULL,"
                    + "day INTEGER NOT NULL,"
                    + "seconds INTEGER NOT NULL DEFAULT 0,"
                    + "PRIMARY KEY (uuid, day))");
            st.execute("CREATE INDEX IF NOT EXISTS " + daily + "_day ON " + daily + "(day)");
        }
    }

    /**
     * Loads statistics of a player. Returns empty statistics if the player is not stored yet.
     *
     * @param uuid Player UUID
     * @param fallbackName Name used when the player is not stored
     * @param today Today's epoch day
     * @param weekStart First epoch day of the current week
     * @param monthStart First epoch day of the current month
     * @return Stored statistics
     * @throws SQLException on database errors
     */
    public StoredStats load(UUID uuid, String fallbackName, long today, long weekStart, long monthStart) throws SQLException {
        String name = fallbackName;
        long total = 0, record = 0, sessions = 0, firstJoin = 0, lastSeen = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT name, total_seconds, record_seconds, sessions, first_join, last_seen FROM " + players + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    name = rs.getString(1);
                    total = rs.getLong(2);
                    record = rs.getLong(3);
                    sessions = rs.getLong(4);
                    firstJoin = rs.getLong(5);
                    lastSeen = rs.getLong(6);
                }
            }
        }
        long from = Math.min(weekStart, monthStart);
        long todaySum = 0, weekSum = 0, monthSum = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT day, seconds FROM " + daily + " WHERE uuid = ? AND day >= ? AND day <= ?")) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, from);
            ps.setLong(3, today);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long day = rs.getLong(1);
                    long seconds = rs.getLong(2);
                    if (day == today) {
                        todaySum += seconds;
                    }
                    if (day >= weekStart) {
                        weekSum += seconds;
                    }
                    if (day >= monthStart) {
                        monthSum += seconds;
                    }
                }
            }
        }
        return new StoredStats(uuid, name, total, record, sessions, firstJoin, lastSeen,
                today, todaySum, weekStart, weekSum, monthStart, monthSum);
    }

    /**
     * Saves absolute values of online players in one transaction.
     *
     * @param snapshots Snapshots to save
     * @throws SQLException on database errors
     */
    public void save(Collection<PlayerStats.Snapshot> snapshots) throws SQLException {
        if (snapshots.isEmpty()) {
            return;
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (PreparedStatement player = connection.prepareStatement(
                "INSERT INTO " + players + " (uuid, name, name_lower, total_seconds, record_seconds, sessions, first_join, last_seen) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET "
                        + "name = excluded.name, name_lower = excluded.name_lower, total_seconds = excluded.total_seconds, "
                        + "record_seconds = excluded.record_seconds, sessions = excluded.sessions, "
                        + "first_join = excluded.first_join, last_seen = excluded.last_seen");
             PreparedStatement day = connection.prepareStatement(
                     "INSERT INTO " + daily + " (uuid, day, seconds) VALUES (?, ?, ?) "
                             + "ON CONFLICT(uuid, day) DO UPDATE SET seconds = excluded.seconds")) {
            for (PlayerStats.Snapshot s : snapshots) {
                player.setString(1, s.uuid().toString());
                player.setString(2, s.name());
                player.setString(3, s.name().toLowerCase(Locale.ROOT));
                player.setLong(4, s.total());
                player.setLong(5, s.record());
                player.setLong(6, s.sessions());
                player.setLong(7, s.firstJoin());
                player.setLong(8, s.lastSeen());
                player.addBatch();
                if (s.today() > 0) {
                    day.setString(1, s.uuid().toString());
                    day.setLong(2, s.dayKey());
                    day.setLong(3, s.today());
                    day.addBatch();
                }
            }
            player.executeBatch();
            day.executeBatch();
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /**
     * Finds a stored player by name (case-insensitive).
     *
     * @param name Player name
     * @return UUID and stored name
     * @throws SQLException on database errors
     */
    public Optional<LeaderboardEntry> findByName(String name) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT uuid, name FROM " + players + " WHERE name_lower = ? ORDER BY last_seen DESC LIMIT 1")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new LeaderboardEntry(UUID.fromString(rs.getString(1)), rs.getString(2), 0));
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Gets a leaderboard page.
     *
     * @param type Leaderboard type (must be persistent)
     * @param today Today's epoch day
     * @param weekStart First epoch day of the current week
     * @param monthStart First epoch day of the current month
     * @param limit Max rows
     * @param offset Rows to skip
     * @return Rows sorted by value, highest first
     * @throws SQLException on database errors
     */
    public List<LeaderboardEntry> top(StatType type, long today, long weekStart, long monthStart, int limit, int offset) throws SQLException {
        String sql = switch (type) {
            case TOTAL, RECORD, SESSIONS -> {
                String column = column(type);
                yield "SELECT uuid, name, " + column + " AS v FROM " + players + " WHERE " + column + " > 0 "
                        + "ORDER BY v DESC, name_lower ASC LIMIT ? OFFSET ?";
            }
            case TODAY, WEEK, MONTH -> "SELECT d.uuid, p.name, SUM(d.seconds) AS v FROM " + daily + " d "
                    + "JOIN " + players + " p ON p.uuid = d.uuid WHERE d.day >= ? AND d.day <= ? "
                    + "GROUP BY d.uuid, p.name HAVING v > 0 ORDER BY v DESC, p.name_lower ASC LIMIT ? OFFSET ?";
            default -> throw new IllegalArgumentException("Not a persistent type: " + type);
        };
        List<LeaderboardEntry> result = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int i = 1;
            if (type == StatType.TODAY || type == StatType.WEEK || type == StatType.MONTH) {
                ps.setLong(i++, periodStart(type, today, weekStart, monthStart));
                ps.setLong(i++, today);
            }
            ps.setInt(i++, limit);
            ps.setInt(i, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new LeaderboardEntry(UUID.fromString(rs.getString(1)), rs.getString(2), rs.getLong(3)));
                }
            }
        }
        return result;
    }

    /**
     * Counts rows of a leaderboard.
     *
     * @param type Leaderboard type (must be persistent)
     * @param today Today's epoch day
     * @param weekStart First epoch day of the current week
     * @param monthStart First epoch day of the current month
     * @return Number of ranked players
     * @throws SQLException on database errors
     */
    public int count(StatType type, long today, long weekStart, long monthStart) throws SQLException {
        String sql = switch (type) {
            case TOTAL, RECORD, SESSIONS -> "SELECT COUNT(*) FROM " + players + " WHERE " + column(type) + " > 0";
            case TODAY, WEEK, MONTH -> "SELECT COUNT(*) FROM (SELECT uuid FROM " + daily
                    + " WHERE day >= ? AND day <= ? GROUP BY uuid HAVING SUM(seconds) > 0)";
            default -> throw new IllegalArgumentException("Not a persistent type: " + type);
        };
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            if (type == StatType.TODAY || type == StatType.WEEK || type == StatType.MONTH) {
                ps.setLong(1, periodStart(type, today, weekStart, monthStart));
                ps.setLong(2, today);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /**
     * Modifies a stored value of an offline player.
     *
     * @param uuid Player UUID
     * @param field Field to change
     * @param operation Operation
     * @param value Operand
     * @return New value
     * @throws SQLException on database errors
     */
    public long modify(UUID uuid, StatField field, StatField.Operation operation, long value) throws SQLException {
        long current = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT " + field.column() + " FROM " + players + " WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    current = rs.getLong(1);
                }
            }
        }
        long updated = operation.apply(current, value);
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE " + players + " SET " + field.column() + " = ? WHERE uuid = ?")) {
            ps.setLong(1, updated);
            ps.setString(2, uuid.toString());
            ps.executeUpdate();
        }
        return updated;
    }

    /**
     * Resets stored statistics of a player.
     *
     * @param uuid Player UUID
     * @param target What to reset (SESSION is ignored - it is not stored)
     * @throws SQLException on database errors
     */
    public void reset(UUID uuid, StatField.ResetTarget target) throws SQLException {
        String set = switch (target) {
            case TOTAL -> "total_seconds = 0";
            case RECORD -> "record_seconds = 0";
            case SESSIONS -> "sessions = 0";
            case ALL -> "total_seconds = 0, record_seconds = 0, sessions = 0";
            default -> null;
        };
        if (set != null) {
            try (PreparedStatement ps = connection.prepareStatement("UPDATE " + players + " SET " + set + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        }
        if (target == StatField.ResetTarget.PERIODS || target == StatField.ResetTarget.ALL) {
            try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + daily + " WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            }
        }
    }

    /**
     * Deletes daily rows older than the given day.
     *
     * @param beforeDay Rows with day lower than this are removed
     * @return Number of removed rows
     * @throws SQLException on database errors
     */
    public int purgeDaily(long beforeDay) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM " + daily + " WHERE day < ?")) {
            ps.setLong(1, beforeDay);
            return ps.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    private static String column(StatType type) {
        return switch (type) {
            case TOTAL -> "total_seconds";
            case RECORD -> "record_seconds";
            case SESSIONS -> "sessions";
            default -> throw new IllegalArgumentException("No column for " + type);
        };
    }

    private static long periodStart(StatType type, long today, long weekStart, long monthStart) {
        return switch (type) {
            case WEEK -> weekStart;
            case MONTH -> monthStart;
            default -> today;
        };
    }
}
