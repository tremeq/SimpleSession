package pl.tremeq.simplesession.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.tremeq.simplesession.SimpleSession;
import pl.tremeq.simplesession.format.FormatManager;
import pl.tremeq.simplesession.format.TimeParser;
import pl.tremeq.simplesession.manager.MessageManager;
import pl.tremeq.simplesession.session.Session;
import pl.tremeq.simplesession.stats.LeaderboardEntry;
import pl.tremeq.simplesession.stats.PlayerStats;
import pl.tremeq.simplesession.stats.StatField;
import pl.tremeq.simplesession.stats.StatType;
import pl.tremeq.simplesession.stats.StatsManager;
import pl.tremeq.simplesession.stats.StoredStats;
import pl.tremeq.simplesession.util.Text;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main command executor for SimpleSession plugin.
 *
 * /ss help | info | top [type] [page] | check [player] | reset <player> <what>
 *     | set|add|take <player> <total|record|sessions> <value> | reload | debug
 *
 * @author TremeQ
 */
public class SimpleSessionCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS =
            List.of("help", "info", "top", "check", "reset", "set", "add", "take", "reload", "debug");
    private static final Pattern DEBUG_LINE = Pattern.compile("(?m)^(debug:[ \\t]*)[^\\s#]*");

    private final SimpleSession plugin;

    /**
     * @param plugin The main plugin instance
     */
    public SimpleSessionCommand(SimpleSession plugin) {
        this.plugin = plugin;
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    private static String permission(String subCommand) {
        return switch (subCommand) {
            case "top" -> "simplesession.top";
            case "check" -> "simplesession.check";
            case "reset" -> "simplesession.reset";
            case "set", "add", "take" -> "simplesession.modify";
            case "reload" -> "simplesession.reload";
            case "debug" -> "simplesession.debug";
            default -> "simplesession.use";
        };
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String sub = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("version")) {
            sub = "info";
        }
        if (!SUBCOMMANDS.contains(sub)) {
            messages().send(sender, "commands.unknown-command");
            return true;
        }
        if (!sender.hasPermission(permission(sub))) {
            messages().send(sender, "commands.no-permission");
            return true;
        }

        switch (sub) {
            case "info" -> handleInfo(sender);
            case "top" -> handleTop(sender, args);
            case "check" -> handleCheck(sender, args);
            case "reset" -> handleReset(sender, args);
            case "set", "add", "take" -> handleModify(sender, StatField.Operation.byKey(sub), args);
            case "reload" -> handleReload(sender);
            case "debug" -> handleDebug(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    // ── help / info / reload / debug ────────────────────────

    private void sendHelp(CommandSender sender) {
        messages().send(sender, "commands.help.header");
        for (String sub : SUBCOMMANDS) {
            if (sender.hasPermission(permission(sub))) {
                messages().send(sender, "commands.help.entries." + sub);
            }
        }
        messages().send(sender, "commands.help.footer");
    }

    private void handleInfo(CommandSender sender) {
        String papi = messages().getMessage(plugin.isPlaceholderAPIEnabled()
                ? "commands.info.placeholderapi-enabled" : "commands.info.placeholderapi-disabled");
        String stats = messages().getMessage(plugin.getStatsManager().isEnabled()
                ? "commands.info.stats-enabled" : "commands.info.stats-disabled");
        String relog = plugin.getSessionManager().isRelogEnabled()
                ? messages().getMessage("commands.info.relog-enabled", "{seconds}", String.valueOf(plugin.getSessionManager().getGraceSeconds()))
                : messages().getMessage("commands.info.relog-disabled");

        messages().send(sender, "commands.info.header");
        messages().sendList(sender, "commands.info.content",
                "{version}", plugin.getDescription().getVersion(),
                "{placeholderapi}", papi,
                "{stats}", stats,
                "{relog}", relog,
                "{milestones}", String.valueOf(plugin.getMilestoneManager().getMilestoneCount()));
        messages().send(sender, "commands.info.footer");
    }

    private void handleReload(CommandSender sender) {
        try {
            plugin.reloadPlugin();
            messages().send(sender, "commands.reload.success");
            plugin.debug("Configuration reloaded by " + sender.getName());
        } catch (Exception e) {
            messages().send(sender, "commands.reload.error");
            plugin.getLogger().log(Level.SEVERE, messages().getMessage("errors.config-reload-failed"), e);
        }
    }

    private void handleDebug(CommandSender sender) {
        boolean newDebug = !plugin.getConfig().getBoolean("debug", false);
        plugin.getConfig().set("debug", newDebug);

        // Change only the "debug:" line, so comments and formatting of config.yml stay untouched
        File file = new File(plugin.getDataFolder(), "config.yml");
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            Matcher matcher = DEBUG_LINE.matcher(content);
            content = matcher.find()
                    ? matcher.replaceFirst("$1" + newDebug)
                    : content + System.lineSeparator() + "debug: " + newDebug + System.lineSeparator();
            Files.writeString(file.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save debug mode to config.yml: " + e.getMessage());
        }

        messages().send(sender, newDebug ? "commands.debug.enabled" : "commands.debug.disabled");
        plugin.getLogger().info((newDebug ? "[DEBUG] Debug mode enabled by " : "Debug mode disabled by ") + sender.getName());
    }

    // ── top ─────────────────────────────────────────────────

    private void handleTop(CommandSender sender, String[] args) {
        StatType type = StatType.SESSION;
        int page = 1;
        if (args.length >= 2) {
            StatType parsed = StatType.byKey(args[1]);
            if (parsed != null) {
                type = parsed;
                if (args.length >= 3) {
                    page = parsePage(args[2]);
                }
            } else if (args[1].chars().allMatch(Character::isDigit)) {
                page = parsePage(args[1]);
            } else {
                messages().send(sender, "commands.top.invalid-type",
                        "{type}", args[1], "{types}", String.join(", ", StatType.keys()));
                return;
            }
        }
        if (page < 1) {
            messages().send(sender, "commands.top.invalid-page", "{page}", args[args.length - 1], "{pages}", "?");
            return;
        }

        int size = topSize();
        if (type == StatType.SESSION) {
            List<Session> sorted = plugin.getSessionManager().getSorted();
            if (sorted.isEmpty()) {
                messages().send(sender, "commands.top.no-players");
                return;
            }
            int pages = (sorted.size() + size - 1) / size;
            if (page > pages) {
                messages().send(sender, "commands.top.invalid-page", "{page}", String.valueOf(page), "{pages}", String.valueOf(pages));
                return;
            }
            long now = System.currentTimeMillis();
            List<LeaderboardEntry> entries = new ArrayList<>();
            for (Session session : sorted.subList((page - 1) * size, Math.min(sorted.size(), page * size))) {
                entries.add(new LeaderboardEntry(session.uuid(), session.name(), session.seconds(now)));
            }
            displayTop(sender, type, page, pages, size, entries);
            return;
        }

        StatsManager stats = plugin.getStatsManager();
        if (!stats.isEnabled()) {
            messages().send(sender, "commands.top.stats-disabled");
            return;
        }
        StatType finalType = type;
        int finalPage = page;
        reply(stats.queryTop(type, page, size), (result, error) -> {
            if (error != null) {
                handleError(sender, error, "");
                return;
            }
            if (result.total() == 0) {
                messages().send(sender, "commands.top.no-data");
                return;
            }
            int pages = (result.total() + size - 1) / size;
            if (finalPage > pages) {
                messages().send(sender, "commands.top.invalid-page", "{page}", String.valueOf(finalPage), "{pages}", String.valueOf(pages));
                return;
            }
            displayTop(sender, finalType, finalPage, pages, size, result.entries());
        });
    }

    private void displayTop(CommandSender sender, StatType type, int page, int pages, int size, List<LeaderboardEntry> entries) {
        FormatManager formats = plugin.getFormatManager();
        String typeName = cfg("leaderboard.type-names." + type.key());
        if (typeName.isEmpty()) {
            typeName = type.key();
        }
        String lineFormat = cfg("leaderboard.format.line");

        sendRaw(sender, cfg("leaderboard.format.header"));
        sendRaw(sender, Text.replace(cfg("leaderboard.title"),
                "{size}", String.valueOf(size), "{type}", typeName,
                "{page}", String.valueOf(page), "{pages}", String.valueOf(pages)));
        sendRaw(sender, cfg("leaderboard.format.separator"));

        for (int i = 0; i < entries.size(); i++) {
            LeaderboardEntry entry = entries.get(i);
            int rank = (page - 1) * size + i + 1;
            String place = switch (rank) {
                case 1 -> "first";
                case 2 -> "second";
                case 3 -> "third";
                default -> "other";
            };
            String value = type.isTime()
                    ? formats.display("leaderboard-command", entry.value())
                    : Text.replace(cfg("leaderboard.format.count"), "{value}", String.valueOf(entry.value()));
            sendRaw(sender, Text.replace(lineFormat,
                    "{medal}", cfg("leaderboard.format.medals." + place),
                    "{color}", cfg("leaderboard.format.colors." + place),
                    "{rank}", String.valueOf(rank),
                    "{player}", entry.name(),
                    "{time}", value,
                    "{value}", String.valueOf(entry.value())));
        }

        sendRaw(sender, cfg("leaderboard.format.footer"));
        if (pages > 1) {
            String next = page < pages
                    ? Text.replace(cfg("leaderboard.format.next-page"),
                    "{type}", type.key(), "{next_page}", String.valueOf(page + 1))
                    : "";
            sendRaw(sender, Text.replace(cfg("leaderboard.format.navigation"),
                    "{page}", String.valueOf(page), "{pages}", String.valueOf(pages), "{next}", next));
        }
    }

    /**
     * Reads a text from config.yml, falling back to the bundled default (never null).
     */
    private String cfg(String path) {
        String value = plugin.getConfig().getString(path);
        return value == null ? "" : value;
    }

    private int topSize() {
        int size = plugin.getConfig().getInt("leaderboard.top-size", 10);
        if (size <= 0) {
            plugin.getLogger().warning(messages().getMessage("errors.invalid-leaderboard-size", "{size}", String.valueOf(size)));
            return 10;
        }
        return size;
    }

    private static int parsePage(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── check ───────────────────────────────────────────────

    private void handleCheck(CommandSender sender, String[] args) {
        String target;
        if (args.length < 2) {
            if (!(sender instanceof Player)) {
                messages().send(sender, "commands.player-only");
                return;
            }
            target = sender.getName();
        } else {
            target = args[1];
            if (!target.equalsIgnoreCase(sender.getName()) && !sender.hasPermission("simplesession.check.others")) {
                messages().send(sender, "commands.no-permission");
                return;
            }
        }

        StatsManager stats = plugin.getStatsManager();
        Player online = Bukkit.getPlayerExact(target);
        if (online != null) {
            PlayerStats playerStats = stats.isEnabled() ? stats.get(online.getUniqueId()) : null;
            sendCheck(sender, online.getName(), true, -1,
                    plugin.getSessionManager().getSessionSeconds(online.getUniqueId()),
                    plugin.getSessionManager().getRank(online.getUniqueId()),
                    playerStats == null ? null : playerStats.toStored(), playerStats);
            return;
        }
        if (!stats.isEnabled()) {
            messages().send(sender, "commands.player-not-found", "{player}", target);
            return;
        }
        reply(stats.lookup(target), (result, error) -> {
            if (error != null) {
                handleError(sender, error, target);
                return;
            }
            if (result.isEmpty()) {
                messages().send(sender, "commands.player-not-found", "{player}", target);
                return;
            }
            StoredStats stored = result.get();
            sendCheck(sender, stored.name(), false, plugin.getSessionManager().getRelogRemaining(stored.uuid()),
                    0, 0, stored, null);
        });
    }

    private void sendCheck(CommandSender sender, String name, boolean online, long relogLeft, long sessionSeconds,
                           int sessionRank, StoredStats stored, PlayerStats live) {
        FormatManager formats = plugin.getFormatManager();
        StatsManager stats = plugin.getStatsManager();
        String unranked = cfg("placeholders.unranked");

        String status;
        if (online) {
            status = messages().getMessage("commands.check.status-online");
        } else if (relogLeft >= 0) {
            status = messages().getMessage("commands.check.status-protected", "{time}", formats.display("check-command", relogLeft));
        } else {
            status = messages().getMessage("commands.check.status-offline");
        }

        String[] base = {
                "{player}", name,
                "{status}", status,
                "{session}", online ? formats.display("check-command", sessionSeconds) : messages().getMessage("commands.check.no-session"),
                "{rank}", sessionRank > 0 ? String.valueOf(sessionRank) : unranked
        };
        messages().send(sender, "commands.check.header", base);
        messages().sendList(sender, "commands.check.lines", base);

        if (stats.isEnabled()) {
            if (stored == null) {
                messages().send(sender, "commands.check.stats-loading", base);
            } else {
                long total = live != null ? stats.live(live, StatType.TOTAL) : stored.total();
                long record = live != null ? stats.live(live, StatType.RECORD) : stored.record();
                long today = live != null ? stats.live(live, StatType.TODAY) : stored.today();
                long week = live != null ? stats.live(live, StatType.WEEK) : stored.week();
                long month = live != null ? stats.live(live, StatType.MONTH) : stored.month();
                long average = live != null ? live.liveAverage(System.currentTimeMillis()) : stored.average();
                String empty = cfg("placeholders.unavailable");

                List<String> values = new ArrayList<>(List.of(base));
                values.addAll(List.of(
                        "{total}", formats.display("check-command", total),
                        "{record}", formats.display("check-command", record),
                        "{today}", formats.display("check-command", today),
                        "{week}", formats.display("check-command", week),
                        "{month}", formats.display("check-command", month),
                        "{average}", formats.display("check-command", average),
                        "{sessions}", String.valueOf(stored.sessions()),
                        "{first_join}", stats.periods().formatDate(stored.firstJoin(), empty),
                        "{last_seen}", online ? messages().getMessage("commands.check.now") : stats.periods().formatDate(stored.lastSeen(), empty)));
                for (StatType type : StatType.values()) {
                    if (type.isPersistent()) {
                        int rank = stats.cachedRank(stored.uuid(), type);
                        values.add("{rank_" + type.key() + "}");
                        values.add(rank > 0 ? String.valueOf(rank) : unranked);
                    }
                }
                messages().sendList(sender, "commands.check.stats-lines", values.toArray(new String[0]));
            }
        }
        messages().send(sender, "commands.check.footer", base);
    }

    // ── reset / set / add / take ────────────────────────────

    private void handleReset(CommandSender sender, String[] args) {
        StatField.ResetTarget target = args.length >= 3 ? StatField.ResetTarget.byKey(args[2]) : null;
        if (target == null) {
            messages().send(sender, "commands.reset.usage", "{targets}", String.join("|", StatField.ResetTarget.keys()));
            return;
        }
        String name = args[1];

        if (target == StatField.ResetTarget.SESSION) {
            Player player = Bukkit.getPlayerExact(name);
            if (player == null || !plugin.getSessionManager().restartSession(player.getUniqueId())) {
                messages().send(sender, "commands.reset.session-offline", "{player}", name);
                return;
            }
            messages().send(sender, "commands.reset.success", "{player}", player.getName(), "{type}", target.key());
            return;
        }

        StatsManager stats = plugin.getStatsManager();
        if (!stats.isEnabled()) {
            messages().send(sender, "commands.stats-disabled");
            return;
        }
        reply(stats.reset(name, target), (resolved, error) -> {
            if (error != null) {
                handleError(sender, error, name);
                return;
            }
            messages().send(sender, "commands.reset.success", "{player}", resolved, "{type}", target.key());
        });
    }

    private void handleModify(CommandSender sender, StatField.Operation operation, String[] args) {
        String action = operation.name().toLowerCase(Locale.ROOT);
        StatField field = args.length >= 4 ? StatField.byKey(args[2]) : null;
        if (field == null) {
            messages().send(sender, "commands.modify.usage", "{action}", action);
            return;
        }
        long value = field.isTime() ? TimeParser.parse(args[3]) : parseCount(args[3]);
        if (value < 0) {
            messages().send(sender, "commands.modify.invalid-value", "{value}", args[3]);
            return;
        }

        StatsManager stats = plugin.getStatsManager();
        if (!stats.isEnabled()) {
            messages().send(sender, "commands.stats-disabled");
            return;
        }
        String name = args[1];
        reply(stats.modify(name, field, operation, value), (result, error) -> {
            if (error != null) {
                handleError(sender, error, name);
                return;
            }
            String shown = field.isTime()
                    ? plugin.getFormatManager().display("check-command", result.value())
                    : String.valueOf(result.value());
            messages().send(sender, "commands.modify.success",
                    "{player}", result.name(), "{type}", field.key(), "{value}", shown, "{action}", action);
        });
    }

    private static long parseCount(String text) {
        try {
            long value = Long.parseLong(text);
            return value < 0 ? -1 : value;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ── helpers ─────────────────────────────────────────────

    private void handleError(CommandSender sender, Throwable error, String name) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof StatsManager.StatsException statsError) {
            switch (statsError.reason()) {
                case NOT_FOUND -> messages().send(sender, "commands.player-not-found", "{player}", name);
                case LOADING -> messages().send(sender, "commands.player-loading", "{player}", name);
                case DISABLED -> messages().send(sender, "commands.stats-disabled");
            }
            return;
        }
        messages().send(sender, "commands.database-error");
        plugin.getLogger().log(Level.WARNING, "Statistics operation failed", cause);
    }

    /**
     * Runs the handler on the main thread when the future completes.
     * Already completed futures are handled immediately, so the reply also reaches RCON senders.
     */
    private <T> void reply(CompletableFuture<T> future, BiConsumer<T, Throwable> handler) {
        if (future.isDone() && Bukkit.isPrimaryThread()) {
            T value = null;
            Throwable error = null;
            try {
                value = future.join();
            } catch (CompletionException | CancellationException e) {
                error = e;
            }
            handler.accept(value, error);
            return;
        }
        future.whenComplete((value, error) -> sync(() -> handler.accept(value, error)));
    }

    private void sync(Runnable task) {
        if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static void sendRaw(CommandSender sender, String text) {
        if (text != null && !text.isEmpty()) {
            sender.sendMessage(Text.color(text));
        }
    }

    // ── tab completion ──────────────────────────────────────

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sender.hasPermission(permission(sub))) {
                    options.add(sub);
                }
            }
            if (sender.hasPermission("simplesession.use")) {
                options.add("version");
            }
        } else {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (!SUBCOMMANDS.contains(sub) || !sender.hasPermission(permission(sub))) {
                return options;
            }
            if (args.length == 2) {
                switch (sub) {
                    case "top" -> options.addAll(StatType.keys());
                    case "check", "reset", "set", "add", "take" ->
                            Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
                    default -> {
                    }
                }
            } else if (args.length == 3) {
                switch (sub) {
                    case "reset" -> options.addAll(StatField.ResetTarget.keys());
                    case "set", "add", "take" -> options.addAll(StatField.keys());
                    default -> {
                    }
                }
            } else if (args.length == 4 && (sub.equals("set") || sub.equals("add") || sub.equals("take"))) {
                StatField field = StatField.byKey(args[2]);
                options.addAll(field != null && !field.isTime() ? List.of("1", "10") : List.of("30m", "1h", "1d", "3600"));
            }
        }
        String input = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(o -> o.toLowerCase(Locale.ROOT).startsWith(input)).sorted().toList();
    }
}
