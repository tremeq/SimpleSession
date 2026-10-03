package pl.tremeq.simplesession.stats;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitTask;
import pl.tremeq.simplesession.SimpleSession;
import pl.tremeq.simplesession.session.SessionManager;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Persistent statistics (SQLite): total play time, longest session, number of sessions,
 * play time today / this week / this month, first join and last seen.
 *
 * Threading model:
 * - every database call runs on one dedicated thread (calls are executed in order)
 * - statistics of online players live in memory and are saved periodically
 * - leaderboards for placeholders are cached and refreshed periodically
 *
 * @author TremeQ
 */
public final class StatsManager implements Listener {

    private static final long RECENT_TTL_MS = 10 * 60 * 1000L;

    private final SimpleSession plugin;
    private final Map<UUID, PlayerStats> online = new ConcurrentHashMap<>();
    private final Map<UUID, Timed<StoredStats>> preloaded = new ConcurrentHashMap<>();
    private final Map<UUID, Timed<PlayerStats>> recent = new ConcurrentHashMap<>();
    private volatile Map<StatType, List<LeaderboardEntry>> leaderboards = Map.of();
    private volatile Periods periods;
    private volatile boolean enabled;
    private volatile StatsDatabase database;
    private volatile ExecutorService executor;
    private BukkitTask saveTask;
    private BukkitTask refreshTask;
    private volatile int cacheSize;

    private record Timed<T>(T value, long at) {
    }

    @FunctionalInterface
    private interface SqlCall<T> {
        T call(StatsDatabase database) throws SQLException;
    }

    /**
     * Result of a leaderboard page query.
     *
     * @param entries Rows of the page
     * @param total Number of ranked players
     */
    public record TopPage(List<LeaderboardEntry> entries, int total) {
    }

    /**
     * Result of a modification command.
     *
     * @param name Player name
     * @param value New value
     */
    public record ModifyResult(String name, long value) {
    }

    /**
     * Expected failure of a statistics operation (shown to the command sender).
     */
    public static final class StatsException extends RuntimeException {
        public enum Reason { NOT_FOUND, LOADING, DISABLED }

        private final Reason reason;

        public StatsException(Reason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }

        public Reason reason() {
            return reason;
        }
    }

    /**
     * @param plugin Main plugin instance
     */
    public StatsManager(SimpleSession plugin) {
        this.plugin = plugin;
        loadSettings();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (plugin.getConfig().getBoolean("stats.enabled")) {
            open();
        }
        startTasks();
    }

    // ── Lifecycle ───────────────────────────────────────────

    private void loadSettings() {
        FileConfiguration config = plugin.getConfig();
        periods = Periods.of(config.getString("stats.timezone"), config.getString("stats.week-start"),
                config.getString("stats.date-format"));
        cacheSize = Math.max(1, Math.min(1000, config.getInt("leaderboard.placeholder-cache-size")));
    }

    private void open() {
        FileConfiguration config = plugin.getConfig();
        File file = new File(plugin.getDataFolder(), config.getString("stats.storage.file", "stats.db"));
        String prefix = config.getString("stats.storage.table-prefix", "ss_");
        ExecutorService service = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "SimpleSession-Database");
            thread.setDaemon(true);
            return thread;
        });
        try {
            database = service.submit(() -> new StatsDatabase(file, prefix)).get(15, TimeUnit.SECONDS);
            executor = service;
            enabled = true;
            plugin.getLogger().info("Statistics enabled (SQLite: " + file.getName() + ")");
            purgeHistory();
        } catch (Exception e) {
            service.shutdownNow();
            enabled = false;
            plugin.getLogger().log(Level.SEVERE, "Could not open statistics database - statistics are disabled!", e);
        }
    }

    private void startTasks() {
        cancelTasks();
        if (!enabled) {
            return;
        }
        FileConfiguration config = plugin.getConfig();
        long saveTicks = Math.max(10, config.getLong("stats.save-interval")) * 20L;
        long refreshTicks = Math.max(5, config.getLong("leaderboard.refresh-interval")) * 20L;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            flushAll();
            long now = System.currentTimeMillis();
            recent.entrySet().removeIf(e -> now - e.getValue().at() > RECENT_TTL_MS);
            preloaded.entrySet().removeIf(e -> now - e.getValue().at() > 60_000L);
        }, saveTicks, saveTicks);
        refreshTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLeaderboards, 40L, refreshTicks);
    }

    private void cancelTasks() {
        if (saveTask != null) {
            saveTask.cancel();
            saveTask = null;
        }
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /**
     * Applies config changes. Enabling/disabling statistics works without restart;
     * changing the database file requires a restart.
     */
    public void reload() {
        boolean wanted = plugin.getConfig().getBoolean("stats.enabled");
        loadSettings();
        if (enabled && !wanted) {
            closeStorage();
        } else if (!enabled && wanted) {
            open();
            if (enabled) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    onSessionStart(player, true);
                }
            }
        } else if (enabled) {
            purgeHistory();
        }
        startTasks();
        if (enabled) {
            refreshLeaderboards();
        }
    }

    /**
     * Saves everything and closes the database. Called on disable.
     */
    public void shutdown() {
        closeStorage();
    }

    private void closeStorage() {
        cancelTasks();
        if (!enabled) {
            return;
        }
        flushAll();
        enabled = false;
        ExecutorService service = executor;
        executor = null;
        service.shutdown();
        try {
            if (!service.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Database thread did not finish in time - some statistics may be lost.");
                service.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            database.close();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not close statistics database: " + e.getMessage());
        }
        database = null;
        online.clear();
        preloaded.clear();
        recent.clear();
        leaderboards = Map.of();
    }

    private void purgeHistory() {
        int days = plugin.getConfig().getInt("stats.history-days");
        if (days <= 0) {
            return;
        }
        // Keep at least the current month and week
        long before = periods.today() - Math.max(32, days);
        async(db -> db.purgeDaily(before)).thenAccept(removed -> {
            if (removed > 0) {
                plugin.debug("Removed " + removed + " old daily statistics rows");
            }
        });
    }

    // ── Join / quit ─────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!enabled || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID id = event.getUniqueId();
        if (recent.containsKey(id)) {
            return;
        }
        try {
            StoredStats stats = load(id, event.getName()).get(5, TimeUnit.SECONDS);
            preloaded.put(id, new Timed<>(stats, System.currentTimeMillis()));
        } catch (Exception e) {
            plugin.getLogger().warning("Could not preload statistics of " + event.getName() + ": " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onLogin(PlayerLoginEvent event) {
        if (event.getResult() != PlayerLoginEvent.Result.ALLOWED) {
            preloaded.remove(event.getPlayer().getUniqueId());
        }
    }

    /**
     * Starts tracking statistics of a player whose session started (or was restored).
     * Called by the session manager on the main thread.
     *
     * @param player Player
     * @param restored true if the session was restored (does not count as a new session)
     */
    public void onSessionStart(Player player, boolean restored) {
        if (!enabled) {
            return;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        Timed<PlayerStats> last = recent.remove(id);
        if (last != null) {
            preloaded.remove(id);
            attach(player, last.value(), restored, now);
            return;
        }
        Timed<StoredStats> pre = preloaded.remove(id);
        if (pre != null) {
            attach(player, new PlayerStats(pre.value()), restored, now);
            return;
        }
        load(id, player.getName()).whenComplete((stats, error) -> {
            if (!plugin.isEnabled()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    plugin.getLogger().warning("Could not load statistics of " + player.getName() + ": " + error.getMessage());
                    return;
                }
                if (enabled && player.isOnline() && !online.containsKey(id)) {
                    attach(player, new PlayerStats(stats), restored, now);
                }
            });
        });
    }

    private void attach(Player player, PlayerStats stats, boolean restored, long startMillis) {
        stats.start(player.getName(), startMillis, !restored);
        online.put(player.getUniqueId(), stats);
        save(List.of(stats.snapshot()));
        plugin.debug("Statistics attached for " + player.getName() + (restored ? " (restored session)" : ""));
    }

    /**
     * Saves and stops tracking statistics of a player who left.
     *
     * @param player Player
     * @param sessionSeconds Length of the finished session
     */
    public void onSessionEnd(Player player, long sessionSeconds) {
        PlayerStats stats = online.remove(player.getUniqueId());
        if (stats == null || !enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        stats.accrue(now, periods, sessionSeconds);
        recent.put(player.getUniqueId(), new Timed<>(stats, now));
        save(List.of(stats.snapshot()));
    }

    // ── Saving / caching ────────────────────────────────────

    /**
     * Accrues play time of all online players and saves it. Main thread only.
     *
     * @return Future completed when the data is written
     */
    public CompletableFuture<Void> flushAll() {
        if (!enabled) {
            return CompletableFuture.completedFuture(null);
        }
        long now = System.currentTimeMillis();
        List<PlayerStats.Snapshot> snapshots = new ArrayList<>();
        for (PlayerStats stats : online.values()) {
            stats.accrue(now, periods, sessionSeconds(stats.uuid()));
            snapshots.add(stats.snapshot());
        }
        return save(snapshots);
    }

    private CompletableFuture<Void> save(List<PlayerStats.Snapshot> snapshots) {
        return this.<Void>async(db -> {
            db.save(snapshots);
            return null;
        }).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not save statistics", error);
            return null;
        });
    }

    /**
     * Saves online players and refreshes cached leaderboards used by placeholders.
     */
    public void refreshLeaderboards() {
        if (!enabled) {
            return;
        }
        flushAll();
        Periods p = periods;
        int size = cacheSize;
        async(db -> {
            long today = p.today();
            Map<StatType, List<LeaderboardEntry>> map = new EnumMap<>(StatType.class);
            for (StatType type : StatType.values()) {
                if (type.isPersistent()) {
                    map.put(type, List.copyOf(db.top(type, today, p.weekStart(today), p.monthStart(today), size, 0)));
                }
            }
            return map;
        }).thenAccept(map -> leaderboards = Map.copyOf(map)).exceptionally(error -> {
            plugin.getLogger().log(Level.WARNING, "Could not refresh leaderboards", error);
            return null;
        });
    }

    // ── Queries for commands ────────────────────────────────

    /**
     * Gets a leaderboard page directly from the database (after saving online players).
     *
     * @param type Persistent leaderboard type
     * @param page Page number (1-based)
     * @param pageSize Rows per page
     * @return Future with the page
     */
    public CompletableFuture<TopPage> queryTop(StatType type, int page, int pageSize) {
        if (!enabled) {
            return CompletableFuture.failedFuture(new StatsException(StatsException.Reason.DISABLED));
        }
        flushAll();
        Periods p = periods;
        return async(db -> {
            long today = p.today();
            int total = db.count(type, today, p.weekStart(today), p.monthStart(today));
            List<LeaderboardEntry> rows = db.top(type, today, p.weekStart(today), p.monthStart(today),
                    pageSize, Math.max(0, page - 1) * pageSize);
            return new TopPage(rows, total);
        });
    }

    /**
     * Loads stored statistics of an offline player by name.
     *
     * @param name Player name
     * @return Future with statistics, empty if the player is unknown
     */
    public CompletableFuture<Optional<StoredStats>> lookup(String name) {
        Periods p = periods;
        return async(db -> {
            Optional<LeaderboardEntry> found = db.findByName(name);
            if (found.isEmpty()) {
                return Optional.empty();
            }
            long today = p.today();
            return Optional.of(db.load(found.get().uuid(), found.get().name(), today, p.weekStart(today), p.monthStart(today)));
        });
    }

    /**
     * Changes a stored statistic (/ss set|add|take). Main thread only.
     *
     * @param name Player name (online or offline)
     * @param field Field
     * @param operation Operation
     * @param value Operand
     * @return Future with the player name and new value
     */
    public CompletableFuture<ModifyResult> modify(String name, StatField field, StatField.Operation operation, long value) {
        if (!enabled) {
            return CompletableFuture.failedFuture(new StatsException(StatsException.Reason.DISABLED));
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            PlayerStats stats = online.get(player.getUniqueId());
            if (stats == null) {
                return CompletableFuture.failedFuture(new StatsException(StatsException.Reason.LOADING));
            }
            stats.accrue(System.currentTimeMillis(), periods, sessionSeconds(player.getUniqueId()));
            long updated = stats.modify(field, operation, value);
            save(List.of(stats.snapshot()));
            refreshLeaderboards();
            return CompletableFuture.completedFuture(new ModifyResult(player.getName(), updated));
        }
        recent.values().removeIf(t -> t.value().snapshot().name().equalsIgnoreCase(name));
        CompletableFuture<ModifyResult> result = async(db -> {
            LeaderboardEntry entry = db.findByName(name).orElseThrow(() -> new StatsException(StatsException.Reason.NOT_FOUND));
            return new ModifyResult(entry.name(), db.modify(entry.uuid(), field, operation, value));
        });
        return result.whenComplete((r, e) -> scheduleRefresh());
    }

    /**
     * Resets stored statistics (/ss reset). SESSION is handled by the session manager. Main thread only.
     *
     * @param name Player name (online or offline)
     * @param target What to reset
     * @return Future with the resolved player name
     */
    public CompletableFuture<String> reset(String name, StatField.ResetTarget target) {
        if (!enabled) {
            return CompletableFuture.failedFuture(new StatsException(StatsException.Reason.DISABLED));
        }
        Player player = Bukkit.getPlayerExact(name);
        if (player != null) {
            PlayerStats stats = online.get(player.getUniqueId());
            if (stats == null) {
                return CompletableFuture.failedFuture(new StatsException(StatsException.Reason.LOADING));
            }
            UUID id = player.getUniqueId();
            stats.accrue(System.currentTimeMillis(), periods, sessionSeconds(id));
            stats.reset(target);
            CompletableFuture<Void> cleared = async(db -> {
                db.reset(id, target);
                return null;
            });
            save(List.of(stats.snapshot()));
            refreshLeaderboards();
            return cleared.thenApply(v -> player.getName());
        }
        recent.values().removeIf(t -> t.value().snapshot().name().equalsIgnoreCase(name));
        CompletableFuture<String> result = async(db -> {
            LeaderboardEntry entry = db.findByName(name).orElseThrow(() -> new StatsException(StatsException.Reason.NOT_FOUND));
            db.reset(entry.uuid(), target);
            return entry.name();
        });
        return result.whenComplete((r, e) -> scheduleRefresh());
    }

    private void scheduleRefresh() {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, this::refreshLeaderboards);
        }
    }

    // ── Live values for placeholders (thread-safe) ──────────

    public boolean isEnabled() {
        return enabled;
    }

    public Periods periods() {
        return periods;
    }

    /**
     * @param playerId Player UUID
     * @return In-memory statistics of an online player, or null (offline / not loaded / disabled)
     */
    public PlayerStats get(UUID playerId) {
        return playerId == null ? null : online.get(playerId);
    }

    /**
     * Live value of a statistic of an online player.
     *
     * @param stats In-memory statistics
     * @param type Statistic type
     * @return Value in seconds (count for SESSIONS)
     */
    public long live(PlayerStats stats, StatType type) {
        return stats.live(type, System.currentTimeMillis(), periods, sessionSeconds(stats.uuid()));
    }

    /**
     * @param type Persistent leaderboard type
     * @return Cached leaderboard (refreshed every leaderboard.refresh-interval)
     */
    public List<LeaderboardEntry> cached(StatType type) {
        return leaderboards.getOrDefault(type, List.of());
    }

    /**
     * @param playerId Player UUID
     * @param type Persistent leaderboard type
     * @return Cached position (1-based) or 0 if not within the cached leaderboard
     */
    public int cachedRank(UUID playerId, StatType type) {
        List<LeaderboardEntry> list = cached(type);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).uuid().equals(playerId)) {
                return i + 1;
            }
        }
        return 0;
    }

    private long sessionSeconds(UUID id) {
        SessionManager sessions = plugin.getSessionManager();
        return sessions == null ? 0 : sessions.getSessionSeconds(id);
    }

    private CompletableFuture<StoredStats> load(UUID id, String name) {
        Periods p = periods;
        return async(db -> {
            long today = p.today();
            return db.load(id, name, today, p.weekStart(today), p.monthStart(today));
        });
    }

    private <T> CompletableFuture<T> async(SqlCall<T> call) {
        ExecutorService service = executor;
        if (!enabled || service == null) {
            return CompletableFuture.failedFuture(new StatsException(StatsException.Reason.DISABLED));
        }
        try {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    return call.call(database);
                } catch (SQLException e) {
                    throw new CompletionException(e);
                }
            }, service);
        } catch (RejectedExecutionException e) {
            return CompletableFuture.failedFuture(new StatsException(StatsException.Reason.DISABLED));
        }
    }
}
