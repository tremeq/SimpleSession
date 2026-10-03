package pl.tremeq.simplesession.util;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * One-time update of configuration files created by an older plugin version.
 *
 * When "config-version" in the file is lower than the bundled one, the file is backed up
 * and every missing option is added together with its comments. Values set by the user
 * are never changed, and user-defined lists (e.g. milestones) are not extended.
 *
 * @author TremeQ
 */
public final class ConfigUpdater {

    private static final String VERSION_KEY = "config-version";

    private ConfigUpdater() {
    }

    /**
     * Updates a configuration file if it is older than the bundled version.
     *
     * @param plugin Plugin owning the file
     * @param name File name inside the data folder (and jar)
     * @param userSections Sections fully owned by the user - defaults are not merged into them if present
     * @param obsolete Keys removed in the new version (replaced by other keys)
     * @return true if the file was updated
     */
    public static boolean update(JavaPlugin plugin, String name, Set<String> userSections, Set<String> obsolete) {
        File file = new File(plugin.getDataFolder(), name);
        InputStream resource = plugin.getResource(name);
        if (!file.exists() || resource == null) {
            return false;
        }
        YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        YamlConfiguration user = YamlConfiguration.loadConfiguration(file);
        int bundledVersion = defaults.getInt(VERSION_KEY, 1);
        int fileVersion = user.getInt(VERSION_KEY, 1);
        if (fileVersion >= bundledVersion) {
            return false;
        }

        // Sections the user already has (decided before anything is added)
        Set<String> owned = userSections.stream().filter(user::isConfigurationSection).collect(Collectors.toSet());

        int added = 0;
        for (String path : defaults.getKeys(true)) {
            if (path.equals(VERSION_KEY) || user.contains(path, true) || blocked(user, path, owned)) {
                continue;
            }
            Object value = defaults.get(path);
            if (value instanceof ConfigurationSection) {
                user.createSection(path);
            } else {
                user.set(path, value);
            }
            user.setComments(path, defaults.getComments(path));
            user.setInlineComments(path, defaults.getInlineComments(path));
            added++;
        }
        for (String key : obsolete) {
            if (user.contains(key, true)) {
                user.set(key, null);
            }
        }
        user.set(VERSION_KEY, bundledVersion);
        user.setComments(VERSION_KEY, List.of("Do not change / Nie zmieniaj"));

        String backupName = name.replace(".yml", "") + "-backup-v" + fileVersion + ".yml";
        try {
            Files.copy(file.toPath(), new File(plugin.getDataFolder(), backupName).toPath(), StandardCopyOption.REPLACE_EXISTING);
            user.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not update " + name + ": " + e.getMessage());
            return false;
        }
        plugin.getLogger().info("Updated " + name + " to version " + bundledVersion + ": added " + added
                + " new options (backup: " + backupName + ")");
        return true;
    }

    /**
     * A default option is not added when the user owns its section or an ancestor is a plain value.
     */
    private static boolean blocked(YamlConfiguration user, String path, Set<String> ownedSections) {
        for (String section : ownedSections) {
            if (path.startsWith(section + ".")) {
                return true;
            }
        }
        int dot = path.lastIndexOf('.');
        while (dot > 0) {
            String parent = path.substring(0, dot);
            if (user.contains(parent, true) && !user.isConfigurationSection(parent)) {
                return true;
            }
            dot = parent.lastIndexOf('.');
        }
        return false;
    }
}
