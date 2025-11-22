package pl.tremeq.simplesession.manager;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import pl.tremeq.simplesession.SimpleSession;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SessionManager handles player session tracking.
 *
 * This class manages the start time of each player's session and calculates
 * the elapsed time since they joined the server.
 *
 * @author TremeQ
 */
public class SessionManager implements Listener {

    private final SimpleSession plugin;
    private final Map<UUID, Long> sessionStartTimes;
    private boolean debugMode;

    // Leaderboard cache to prevent excessive sorting
    private List<Player> cachedSortedPlayers = null;
    private long lastCacheUpdate = 0;
    private static final long CACHE_DURATION_MS = 1000; // 1 second cache

    /**
     * Creates a new SessionManager instance.
     *
     * @param plugin The main plugin instance
     */
    public SessionManager(SimpleSession plugin) {
        this.plugin = plugin;
        this.sessionStartTimes = new ConcurrentHashMap<>();
        this.debugMode = plugin.getConfig().getBoolean("debug", false);

        // Register event listener
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Initialize sessions for already online players (in case of reload)
        initializeOnlinePlayers();
    }

    /**
     * Updates the debug mode setting from config.
     * Should be called when config is reloaded.
     */
    public void reloadDebugMode() {
        this.debugMode = plugin.getConfig().getBoolean("debug", false);
    }

    /**
     * Initializes sessions for all currently online players.
     * This is useful when the plugin is loaded while players are already online.
     * Only initializes sessions for players who don't already have one (prevents overwriting on reload).
     */
    private void initializeOnlinePlayers() {
        long currentTime = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            // Only add session if player doesn't have one (prevents overwriting on reload)
            sessionStartTimes.putIfAbsent(player.getUniqueId(), currentTime);
        }

        if (debugMode) {
            plugin.getLogger().info("[DEBUG] Initialized sessions for " +
                plugin.getServer().getOnlinePlayers().size() + " online players");
        }
    }

    /**
     * Handles player join event.
     * Records the time when a player joins the server.
     *
     * @param event PlayerJoinEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        long joinTime = System.currentTimeMillis();
        sessionStartTimes.put(playerId, joinTime);

        // Invalidate leaderboard cache since player count changed
        invalidateCache();

        if (debugMode) {
            plugin.getLogger().info("[DEBUG] Session started for player: " + event.getPlayer().getName());
        }
    }

    /**
     * Handles player quit event.
     * Removes the player's session data when they leave.
     *
     * @param event PlayerQuitEvent
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        long sessionDuration = getSessionDuration(playerId);
        sessionStartTimes.remove(playerId);

        // Invalidate leaderboard cache since player count changed
        invalidateCache();

        if (debugMode) {
            plugin.getLogger().info("[DEBUG] Session ended for player: " + event.getPlayer().getName() +
                    " | Duration: " + (sessionDuration / 1000) + " seconds");
        }
    }

    /**
     * Gets the session duration in milliseconds for a player.
     *
     * @param playerId UUID of the player
     * @return Session duration in milliseconds, or 0 if no active session
     */
    public long getSessionDuration(UUID playerId) {
        Long startTime = sessionStartTimes.get(playerId);
        if (startTime == null) {
            return 0L;
        }
        return System.currentTimeMillis() - startTime;
    }

    /**
     * Gets the session duration in seconds for a player.
     *
     * @param playerId UUID of the player
     * @return Session duration in seconds
     */
    public long getSessionSeconds(UUID playerId) {
        return getSessionDuration(playerId) / 1000;
    }

    /**
     * Gets the session duration in minutes for a player.
     *
     * @param playerId UUID of the player
     * @return Session duration in minutes
     */
    public long getSessionMinutes(UUID playerId) {
        return getSessionSeconds(playerId) / 60;
    }

    /**
     * Gets the session duration in hours for a player.
     *
     * @param playerId UUID of the player
     * @return Session duration in hours
     */
    public long getSessionHours(UUID playerId) {
        return getSessionMinutes(playerId) / 60;
    }

    /**
     * Gets the session duration in days for a player.
     *
     * @param playerId UUID of the player
     * @return Session duration in days
     */
    public long getSessionDays(UUID playerId) {
        return getSessionHours(playerId) / 24;
    }

    /**
     * Gets the remaining seconds after calculating full minutes.
     *
     * @param playerId UUID of the player
     * @return Remaining seconds
     */
    public long getRemainingSeconds(UUID playerId) {
        return getSessionSeconds(playerId) % 60;
    }

    /**
     * Gets the remaining minutes after calculating full hours.
     *
     * @param playerId UUID of the player
     * @return Remaining minutes
     */
    public long getRemainingMinutes(UUID playerId) {
        return getSessionMinutes(playerId) % 60;
    }

    /**
     * Gets the remaining hours after calculating full days.
     *
     * @param playerId UUID of the player
     * @return Remaining hours
     */
    public long getRemainingHours(UUID playerId) {
        return getSessionHours(playerId) % 24;
    }

    /**
     * Formats the session time using the specified format from config.
     *
     * @param playerId UUID of the player
     * @param formatType Format type from config (full, short, custom)
     * @return Formatted session time string
     */
    public String getFormattedSessionTime(UUID playerId, String formatType) {
        long days = getSessionDays(playerId);
        long hours = getRemainingHours(playerId);
        long minutes = getRemainingMinutes(playerId);
        long seconds = getRemainingSeconds(playerId);

        // Get format from config with proper fallback
        String format = plugin.getConfig().getString("time-formats." + formatType);

        // If format is null, try to get the full format
        if (format == null) {
            format = plugin.getConfig().getString("time-formats.full");
        }

        // If still null, use hardcoded fallback
        if (format == null) {
            format = "{days}d {hours}h {minutes}m {seconds}s";
            plugin.getLogger().warning("Format '" + formatType + "' not found in config! Using default.");
        }

        return format
                .replace("{days}", String.valueOf(days))
                .replace("{hours}", String.valueOf(hours))
                .replace("{minutes}", String.valueOf(minutes))
                .replace("{seconds}", String.valueOf(seconds));
    }

    /**
     * Formats the session time using the default format from config.
     *
     * @param playerId UUID of the player
     * @return Formatted session time string
     */
    public String getFormattedSessionTime(UUID playerId) {
        String defaultFormat = plugin.getConfig().getString("default-format", "full");
        return getFormattedSessionTime(playerId, defaultFormat);
    }

    /**
     * Clears all active sessions.
     * Called when the plugin is disabled.
     */
    public void clearAllSessions() {
        sessionStartTimes.clear();
    }

    /**
     * Checks if a player has an active session.
     *
     * @param playerId UUID of the player
     * @return true if the player has an active session, false otherwise
     */
    public boolean hasActiveSession(UUID playerId) {
        return sessionStartTimes.containsKey(playerId);
    }

    /**
     * Gets the player's rank in the current session leaderboard.
     * Rank is based on current session time (online players only).
     * Uses the cached sorted list for optimal performance.
     *
     * @param playerId UUID of the player
     * @return Player's rank (1 = longest session), or 0 if not online
     */
    public int getPlayerRank(UUID playerId) {
        if (!hasActiveSession(playerId)) {
            return 0; // Player not online
        }

        // Get sorted list (uses cache if available)
        List<Player> sortedPlayers = getSortedPlayers();

        // Find player's position in the sorted list
        for (int i = 0; i < sortedPlayers.size(); i++) {
            if (sortedPlayers.get(i).getUniqueId().equals(playerId)) {
                return i + 1; // Rank is 1-indexed
            }
        }

        // Player not found (shouldn't happen if hasActiveSession is true)
        return 0;
    }

    /**
     * Gets a sorted list of online players by session time (descending).
     * Uses a cache with 1 second TTL to prevent excessive sorting.
     * Public to allow command handlers to use the same cached list.
     *
     * @return List of players sorted by session time (longest first)
     */
    public List<Player> getSortedPlayers() {
        long currentTime = System.currentTimeMillis();

        // Check if cache is still valid
        if (cachedSortedPlayers != null && (currentTime - lastCacheUpdate) < CACHE_DURATION_MS) {
            return cachedSortedPlayers;
        }

        // Cache expired or doesn't exist - rebuild it
        cachedSortedPlayers = plugin.getServer().getOnlinePlayers().stream()
                .sorted((p1, p2) -> {
                    long time1 = getSessionSeconds(p1.getUniqueId());
                    long time2 = getSessionSeconds(p2.getUniqueId());
                    return Long.compare(time2, time1); // Descending order
                })
                .collect(Collectors.toList());

        lastCacheUpdate = currentTime;

        if (debugMode) {
            plugin.getLogger().info("[DEBUG] Leaderboard cache rebuilt (" + cachedSortedPlayers.size() + " players)");
        }

        return cachedSortedPlayers;
    }

    /**
     * Invalidates the leaderboard cache.
     * Should be called when player count changes (join/quit).
     */
    private void invalidateCache() {
        cachedSortedPlayers = null;
        lastCacheUpdate = 0;

        if (debugMode) {
            plugin.getLogger().info("[DEBUG] Leaderboard cache invalidated");
        }
    }

    /**
     * Gets the name of the player at a specific position in the leaderboard.
     *
     * @param position Position in the leaderboard (1 = longest session)
     * @return Player name, or empty string if position is invalid
     */
    public String getTopPlayerName(int position) {
        if (position < 1) {
            return "";
        }

        List<Player> sortedPlayers = getSortedPlayers();

        // Check if position is within bounds
        if (position > sortedPlayers.size()) {
            return "";
        }

        // Position is 1-indexed, list is 0-indexed
        return sortedPlayers.get(position - 1).getName();
    }

    /**
     * Gets the formatted session time of the player at a specific position in the leaderboard.
     *
     * @param position Position in the leaderboard (1 = longest session)
     * @return Formatted session time, or empty string if position is invalid
     */
    public String getTopPlayerTime(int position) {
        if (position < 1) {
            return "";
        }

        List<Player> sortedPlayers = getSortedPlayers();

        // Check if position is within bounds
        if (position > sortedPlayers.size()) {
            return "";
        }

        // Position is 1-indexed, list is 0-indexed
        Player player = sortedPlayers.get(position - 1);
        return getFormattedSessionTime(player.getUniqueId());
    }
}
