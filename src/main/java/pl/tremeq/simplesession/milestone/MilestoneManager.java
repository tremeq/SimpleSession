package pl.tremeq.simplesession.milestone;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import pl.tremeq.simplesession.SimpleSession;
import pl.tremeq.simplesession.format.TimeParser;
import pl.tremeq.simplesession.session.Session;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Manages session milestones for players.
 *
 * Achieved milestones are stored in the session itself, so they are kept
 * when the session is restored by relog protection or after /reload.
 *
 * @author TremeQ
 */
public class MilestoneManager {

    private final SimpleSession plugin;
    private final List<Milestone> milestones = new ArrayList<>();
    private BukkitTask checkTask;
    private boolean enabled;

    /**
     * Creates a new MilestoneManager.
     *
     * @param plugin The main plugin instance
     */
    public MilestoneManager(SimpleSession plugin) {
        this.plugin = plugin;
        loadMilestones();
        if (enabled && !milestones.isEmpty()) {
            startCheckTask();
        }
    }

    private void loadMilestones() {
        milestones.clear();
        enabled = plugin.getConfig().getBoolean("milestones.enabled");

        if (!enabled) {
            plugin.getLogger().info("Milestones are disabled in config");
            return;
        }

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("milestones.list");
        if (section == null) {
            plugin.getLogger().warning("No milestones configured in config.yml!");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection milestoneSection = section.getConfigurationSection(key);
            if (milestoneSection == null) {
                continue;
            }

            Object rawTime = milestoneSection.get("time");
            long time = rawTime instanceof Number number ? number.longValue() : TimeParser.parse(String.valueOf(rawTime));
            if (time <= 0) {
                plugin.getLogger().warning("Milestone '" + key + "' has invalid time (" + rawTime + "). Skipping.");
                continue;
            }

            milestones.add(new Milestone(key, time,
                    milestoneSection.getString("message", ""),
                    milestoneSection.getString("broadcast", ""),
                    milestoneSection.getStringList("commands"),
                    milestoneSection.getString("permission", "")));
            plugin.debug("Loaded milestone: " + key + " at " + time + "s");
        }

        milestones.sort(Comparator.comparingLong(Milestone::getTimeSeconds));
        plugin.getLogger().info("Loaded " + milestones.size() + " milestones");
    }

    private void startCheckTask() {
        long intervalSeconds = plugin.getConfig().getLong("milestones.check-interval");
        if (intervalSeconds <= 0) {
            plugin.getLogger().warning("Invalid check-interval (" + intervalSeconds + "s). Using default 10s.");
            intervalSeconds = 10;
        }
        long intervalTicks = intervalSeconds * 20L;
        checkTask = Bukkit.getScheduler().runTaskTimer(plugin, this::checkMilestones, intervalTicks, intervalTicks);
        plugin.debug("Milestone check task started (interval: " + intervalSeconds + "s)");
    }

    private void checkMilestones() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Session session = plugin.getSessionManager().getSession(player.getUniqueId());
            if (session == null) {
                continue;
            }
            long seconds = session.seconds(now);
            for (Milestone milestone : milestones) {
                if (seconds < milestone.getTimeSeconds()) {
                    break;
                }
                if (milestone.canReceive(player) && session.achieve(milestone.getId())) {
                    milestone.execute(plugin, player, seconds);
                    plugin.debug("Player " + player.getName() + " achieved milestone: " + milestone.getId());
                }
            }
        }
    }

    /**
     * Reloads milestones from config.
     */
    public void reload() {
        shutdown();
        loadMilestones();
        if (enabled && !milestones.isEmpty()) {
            startCheckTask();
        }
        plugin.debug("MilestoneManager reloaded");
    }

    /**
     * Stops the milestone checking task.
     */
    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
            checkTask = null;
        }
    }

    public int getMilestoneCount() {
        return milestones.size();
    }

    public boolean isEnabled() {
        return enabled;
    }
}
