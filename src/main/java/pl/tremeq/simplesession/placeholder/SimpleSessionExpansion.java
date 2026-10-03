package pl.tremeq.simplesession.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.tremeq.simplesession.SimpleSession;
import pl.tremeq.simplesession.format.FormatManager;
import pl.tremeq.simplesession.format.TimeFormat;
import pl.tremeq.simplesession.session.Session;
import pl.tremeq.simplesession.session.SessionManager;
import pl.tremeq.simplesession.stats.LeaderboardEntry;
import pl.tremeq.simplesession.stats.PlayerStats;
import pl.tremeq.simplesession.stats.StatType;
import pl.tremeq.simplesession.stats.StatsManager;
import pl.tremeq.simplesession.util.Text;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * PlaceholderAPI expansion for SimpleSession. Safe to call from async threads.
 *
 * Current session:
 *   seconds, minutes, hours, days, total_seconds, total_minutes, total_hours, total_days,
 *   formatted, formatted_<format>, rank, rank_<type>
 * Persistent statistics (stats.enabled):
 *   stats_<total|record|today|week|month|average>[_seconds|_formatted_<format>],
 *   stats_sessions, stats_first_join, stats_last_seen
 * Leaderboards:
 *   top_<n>_<name|time|value>                 (current sessions)
 *   top_<type>_<n>_<name|time|value>          (session, total, record, today, week, month, sessions)
 *   top_<type>_<n>_time_<format>              (time with a chosen format)
 *
 * @author TremeQ
 */
public class SimpleSessionExpansion extends PlaceholderExpansion {

    private static final String[] STAT_TIME_KEYS = {"total", "record", "today", "week", "month", "average"};
    private static final List<String> TEXT_PATHS = List.of(
            "placeholders.empty-name", "placeholders.empty-time", "placeholders.unranked", "placeholders.unavailable");

    private final SimpleSession plugin;
    private volatile Map<String, String> texts = Map.of();

    /**
     * @param plugin The main plugin instance
     */
    public SimpleSessionExpansion(SimpleSession plugin) {
        this.plugin = plugin;
        reload();
    }

    @Override
    @NotNull
    public String getIdentifier() {
        return "simplesession";
    }

    @Override
    @NotNull
    public String getAuthor() {
        return "TremeQ";
    }

    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    @Nullable
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        String key = params.toLowerCase(Locale.ROOT);
        UUID id = player == null ? null : player.getUniqueId();

        if (key.startsWith("top_")) {
            return top(key.substring(4));
        }
        if (key.startsWith("stats_")) {
            return stats(id, key.substring(6));
        }
        if (key.equals("rank")) {
            return rank(id, StatType.SESSION);
        }
        if (key.startsWith("rank_")) {
            StatType type = StatType.byKey(key.substring(5));
            return type == null ? null : rank(id, type);
        }

        // Current session: players without a session (offline / null) are treated as 0 seconds
        long seconds = plugin.getSessionManager().getSessionSeconds(id);
        FormatManager formats = plugin.getFormatManager();
        switch (key) {
            case "seconds":
                return String.valueOf(seconds % 60);
            case "minutes":
                return String.valueOf((seconds / 60) % 60);
            case "hours":
                return String.valueOf((seconds / 3600) % 24);
            case "days":
            case "total_days":
                return String.valueOf(seconds / 86400);
            case "total_seconds":
                return String.valueOf(seconds);
            case "total_minutes":
                return String.valueOf(seconds / 60);
            case "total_hours":
                return String.valueOf(seconds / 3600);
            case "formatted":
                return formats.formatDefault(seconds);
            default:
                break;
        }
        if (key.startsWith("formatted_")) {
            TimeFormat format = formats.get(key.substring(10));
            return format == null ? null : format.format(seconds);
        }
        return null;
    }

    private String rank(UUID id, StatType type) {
        int rank;
        if (type == StatType.SESSION) {
            rank = plugin.getSessionManager().getRank(id);
        } else {
            StatsManager stats = plugin.getStatsManager();
            if (!stats.isEnabled()) {
                return text("placeholders.unavailable");
            }
            rank = id == null ? 0 : stats.cachedRank(id, type);
        }
        return rank > 0 ? String.valueOf(rank) : text("placeholders.unranked");
    }

    private String stats(UUID id, String key) {
        StatsManager stats = plugin.getStatsManager();
        if (!stats.isEnabled()) {
            return text("placeholders.unavailable");
        }
        PlayerStats playerStats = stats.get(id);
        if (playerStats == null) {
            return text("placeholders.unavailable");
        }
        switch (key) {
            case "sessions":
                return String.valueOf(stats.live(playerStats, StatType.SESSIONS));
            case "first_join":
                return stats.periods().formatDate(playerStats.firstJoin(), text("placeholders.unavailable"));
            case "last_seen":
                return stats.periods().formatDate(playerStats.lastSeen(), text("placeholders.unavailable"));
            default:
                break;
        }
        for (String timeKey : STAT_TIME_KEYS) {
            if (!key.startsWith(timeKey)) {
                continue;
            }
            long value = timeKey.equals("average")
                    ? playerStats.liveAverage(System.currentTimeMillis())
                    : stats.live(playerStats, StatType.byKey(timeKey));
            String rest = key.substring(timeKey.length());
            if (rest.isEmpty()) {
                return plugin.getFormatManager().display("stats-placeholder", value);
            }
            if (rest.equals("_seconds")) {
                return String.valueOf(value);
            }
            if (rest.startsWith("_formatted_")) {
                TimeFormat format = plugin.getFormatManager().get(rest.substring(11));
                return format == null ? null : format.format(value);
            }
        }
        return null;
    }

    /**
     * Handles top_<n>_<field> and top_<type>_<n>_<field>[_<format>].
     */
    private String top(String key) {
        String[] parts = key.split("_");
        int index = 0;
        StatType type = StatType.SESSION;
        if (parts.length > 0 && StatType.byKey(parts[0]) != null) {
            type = StatType.byKey(parts[0]);
            index = 1;
        }
        if (parts.length < index + 2) {
            return "";
        }
        int position;
        try {
            position = Integer.parseInt(parts[index]);
        } catch (NumberFormatException e) {
            return "";
        }
        String field = parts[index + 1];
        String formatName = parts.length > index + 2
                ? String.join("_", Arrays.copyOfRange(parts, index + 2, parts.length)) : null;
        if (position < 1 || (formatName != null && !field.equals("time"))) {
            return "";
        }

        LeaderboardEntry entry = entry(type, position);
        if (entry == null) {
            if (type.isPersistent() && !plugin.getStatsManager().isEnabled()) {
                return text("placeholders.unavailable");
            }
            return switch (field) {
                case "name" -> text("placeholders.empty-name");
                case "time", "value" -> text("placeholders.empty-time");
                default -> "";
            };
        }
        return switch (field) {
            case "name" -> entry.name();
            case "value" -> String.valueOf(entry.value());
            case "time" -> {
                if (!type.isTime()) {
                    yield String.valueOf(entry.value());
                }
                if (formatName != null) {
                    TimeFormat format = plugin.getFormatManager().get(formatName);
                    yield format == null ? "" : format.format(entry.value());
                }
                yield plugin.getFormatManager().display("leaderboard-placeholder", entry.value());
            }
            default -> "";
        };
    }

    private LeaderboardEntry entry(StatType type, int position) {
        if (type == StatType.SESSION) {
            SessionManager sessions = plugin.getSessionManager();
            List<Session> sorted = sessions.getSorted();
            if (position > sorted.size()) {
                return null;
            }
            Session session = sorted.get(position - 1);
            return new LeaderboardEntry(session.uuid(), session.name(), session.seconds(System.currentTimeMillis()));
        }
        StatsManager stats = plugin.getStatsManager();
        if (!stats.isEnabled()) {
            return null;
        }
        List<LeaderboardEntry> cached = stats.cached(type);
        return position > cached.size() ? null : cached.get(position - 1);
    }

    private String text(String path) {
        return texts.getOrDefault(path, "");
    }

    /**
     * Caches texts from config.yml so async placeholder requests never touch the configuration.
     */
    public void reload() {
        FileConfiguration config = plugin.getConfig();
        Map<String, String> loaded = new HashMap<>();
        for (String path : TEXT_PATHS) {
            String value = config.getString(path);
            loaded.put(path, value == null ? "" : Text.color(value));
        }
        texts = Map.copyOf(loaded);
    }
}
