package pl.tremeq.simplesession.session;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import pl.tremeq.simplesession.SimpleSession;
import pl.tremeq.simplesession.stats.StatsManager;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks play sessions of online players.
 *
 * Features:
 * - session starts on join (LOWEST priority, so other plugins already see it in their join handlers)
 * - relog protection: a player who returns within the grace period continues the old session
 * - sessions survive /reload and (optionally) a quick restart, stored in sessions.yml
 * - thread-safe ranking cache (placeholders may be requested asynchronously)
 *
 * @author TremeQ
 */
public final class SessionManager implements Listener {

    private static final long CACHE_DURATION_MS = 1000;

    private final SimpleSession plugin;
    private final File file;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();
    private volatile Ranking ranking;
    private BukkitTask cleanupTask;

    private volatile boolean relogEnabled;
    private volatile long graceMillis;
    private volatile boolean countOfflineTime;
    private volatile boolean keepAfterRestart;
    private volatile boolean keepOnReload;

    private record Pending(Session session, long quitAt) {
    }

    private record Ranking(List<Session> sessions, long builtAt) {
    }

    /**
     * @param plugin Main plugin instance
     */
    public SessionManager(SimpleSession plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sessions.yml");
        loadSettings();
    }

    /**
     * Restores saved sessions, starts sessions for players already online and registers listeners.
     * Must be called after the manager is reachable through the plugin instance.
     */
    public void initialize() {
        restorePersisted();
        long now = System.currentTimeMillis();
        StatsManager stats = plugin.getStatsManager();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = sessions.get(player.getUniqueId());
            boolean restored = session != null;
            if (session == null) {
                session = new Session(player.getUniqueId(), player.getName(), now);
                sessions.put(player.getUniqueId(), session);
            }
            if (stats != null) {
                stats.onSessionStart(player, restored);
            }
        }
        invalidate();
        plugin.debug("Initialized sessions for " + sessions.size() + " online players");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        cleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, this::purgeExpired, 400L, 400L);
    }

    /**
     * Reads relog protection settings from config.
     */
    public void loadSettings() {
        FileConfiguration config = plugin.getConfig();
        relogEnabled = config.getBoolean("relog-protection.enabled");
        graceMillis = Math.max(0, config.getLong("relog-protection.grace-period")) * 1000L;
        countOfflineTime = config.getBoolean("relog-protection.count-offline-time");
        keepAfterRestart = config.getBoolean("relog-protection.keep-after-restart");
        keepOnReload = config.getBoolean("session.keep-on-reload");
        if (!relogEnabled) {
            pending.clear();
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        Pending previous = pending.remove(id);
        Session session;
        boolean restored = false;
        long offlineMillis = 0;
        if (previous != null && relogEnabled && now - previous.quitAt() <= graceMillis) {
            session = previous.session();
            offlineMillis = now - previous.quitAt();
            if (!countOfflineTime) {
                session.shift(offlineMillis);
            }
            restored = true;
        } else {
            session = new Session(id, player.getName(), now);
        }
        session.name(player.getName());
        sessions.put(id, session);
        invalidate();

        StatsManager stats = plugin.getStatsManager();
        if (stats != null) {
            stats.onSessionStart(player, restored);
        }

        if (restored) {
            String message = plugin.getMessageManager().getMessage("session.restored",
                    "{time}", plugin.getFormatManager().display("relog-message", session.seconds(now)),
                    "{offline}", plugin.getFormatManager().display("relog-message", offlineMillis / 1000));
            if (!message.isEmpty()) {
                player.sendMessage(message);
            }
            plugin.debug("Session restored for " + player.getName() + " (offline " + offlineMillis / 1000 + "s)");
        } else {
            plugin.debug("Session started for " + player.getName());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        Session session = sessions.remove(id);
        invalidate();
        if (session == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long seconds = session.seconds(now);

        StatsManager stats = plugin.getStatsManager();
        if (stats != null) {
            stats.onSessionEnd(player, seconds);
        }
        if (relogEnabled && graceMillis > 0) {
            pending.put(id, new Pending(session, now));
        }
        plugin.debug("Session ended for " + player.getName() + " | Duration: " + seconds + " seconds");
    }

    /**
     * @param playerId Player UUID
     * @return Active session or null
     */
    public Session getSession(UUID playerId) {
        return playerId == null ? null : sessions.get(playerId);
    }

    /**
     * @param playerId Player UUID
     * @return true if the player has an active session
     */
    public boolean hasActiveSession(UUID playerId) {
        return playerId != null && sessions.containsKey(playerId);
    }

    /**
     * @param playerId Player UUID
     * @return Current session length in seconds, 0 without an active session
     */
    public long getSessionSeconds(UUID playerId) {
        Session session = getSession(playerId);
        return session == null ? 0 : session.seconds(System.currentTimeMillis());
    }

    /**
     * Gets online sessions sorted by length (longest first). Cached for 1 second, thread-safe.
     *
     * @return Immutable sorted list
     */
    public List<Session> getSorted() {
        long now = System.currentTimeMillis();
        Ranking cached = ranking;
        if (cached != null && now - cached.builtAt() < CACHE_DURATION_MS) {
            return cached.sessions();
        }
        List<Session> list = new ArrayList<>(sessions.values());
        list.sort(Comparator.comparingLong(Session::startMillis)
                .thenComparing(s -> s.name().toLowerCase(Locale.ROOT)));
        Ranking built = new Ranking(List.copyOf(list), now);
        ranking = built;
        return built.sessions();
    }

    /**
     * @param playerId Player UUID
     * @return Position in the current session ranking (1 = longest), 0 without an active session
     */
    public int getRank(UUID playerId) {
        if (!hasActiveSession(playerId)) {
            return 0;
        }
        List<Session> sorted = getSorted();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).uuid().equals(playerId)) {
                return i + 1;
            }
        }
        return 0;
    }

    /**
     * Restarts the session of an online player from now.
     *
     * @param playerId Player UUID
     * @return true if the player had an active session
     */
    public boolean restartSession(UUID playerId) {
        Session session = getSession(playerId);
        if (session == null) {
            return false;
        }
        session.restart(System.currentTimeMillis());
        invalidate();
        return true;
    }

    /**
     * @param playerId Player UUID
     * @return Seconds left of relog protection for an offline player, or -1 if not protected
     */
    public long getRelogRemaining(UUID playerId) {
        Pending entry = playerId == null ? null : pending.get(playerId);
        if (entry == null || !relogEnabled) {
            return -1;
        }
        long left = graceMillis - (System.currentTimeMillis() - entry.quitAt());
        return left <= 0 ? -1 : left / 1000;
    }

    public boolean isRelogEnabled() {
        return relogEnabled;
    }

    public long getGraceSeconds() {
        return graceMillis / 1000;
    }

    /**
     * Saves sessions so they can survive /reload or a restart. Called on disable.
     */
    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        persist();
    }

    private void invalidate() {
        ranking = null;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        pending.entrySet().removeIf(e -> now - e.getValue().quitAt() > graceMillis);
    }

    private void persist() {
        if (!keepOnReload && !(relogEnabled && keepAfterRestart)) {
            if (file.exists()) {
                file.delete();
            }
            return;
        }
        long now = System.currentTimeMillis();
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("saved-at", now);
        for (Session session : sessions.values()) {
            write(yaml, "active." + session.uuid(), session, 0);
        }
        for (Map.Entry<UUID, Pending> entry : pending.entrySet()) {
            if (now - entry.getValue().quitAt() <= graceMillis) {
                write(yaml, "pending." + entry.getKey(), entry.getValue().session(), entry.getValue().quitAt());
            }
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save sessions.yml: " + e.getMessage());
        }
    }

    private static void write(YamlConfiguration yaml, String path, Session session, long quitAt) {
        yaml.set(path + ".name", session.name());
        yaml.set(path + ".start", session.startMillis());
        yaml.set(path + ".milestones", new ArrayList<>(session.milestones()));
        if (quitAt > 0) {
            yaml.set(path + ".quit", quitAt);
        }
    }

    private void restorePersisted() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        file.delete();

        long savedAt = yaml.getLong("saved-at");
        // The JVM started after the save -> server restart, otherwise it was /reload or a plugin reload
        boolean restart = ManagementFactory.getRuntimeMXBean().getStartTime() > savedAt;
        int restored = 0;

        ConfigurationSection active = yaml.getConfigurationSection("active");
        if (active != null) {
            for (String key : active.getKeys(false)) {
                Session session = read(active, key);
                if (session == null) {
                    continue;
                }
                if (!restart) {
                    if (!keepOnReload) {
                        continue;
                    }
                    if (Bukkit.getPlayer(session.uuid()) != null) {
                        sessions.put(session.uuid(), session);
                    } else if (relogEnabled) {
                        pending.put(session.uuid(), new Pending(session, savedAt));
                    }
                    restored++;
                } else if (relogEnabled && keepAfterRestart) {
                    pending.put(session.uuid(), new Pending(session, savedAt));
                    restored++;
                }
            }
        }

        ConfigurationSection waiting = yaml.getConfigurationSection("pending");
        if (waiting != null && relogEnabled && (restart ? keepAfterRestart : keepOnReload)) {
            for (String key : waiting.getKeys(false)) {
                Session session = read(waiting, key);
                if (session != null) {
                    pending.put(session.uuid(), new Pending(session, waiting.getLong(key + ".quit", savedAt)));
                    restored++;
                }
            }
        }
        plugin.debug("Restored " + restored + " saved sessions (" + (restart ? "restart" : "reload") + ")");
    }

    private static Session read(ConfigurationSection section, String key) {
        try {
            UUID uuid = UUID.fromString(key);
            Session session = new Session(uuid, section.getString(key + ".name", "?"), section.getLong(key + ".start"));
            session.restoreMilestones(section.getStringList(key + ".milestones"));
            return session;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
