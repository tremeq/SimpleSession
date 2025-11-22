package pl.tremeq.simplesession.manager;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.tremeq.simplesession.SimpleSession;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

/**
 * MessageManager handles all plugin messages from messages.yml.
 *
 * This class manages loading, caching, and formatting of messages
 * with support for color codes and placeholders.
 *
 * @author TremeQ
 */
public class MessageManager {

    private final SimpleSession plugin;
    private FileConfiguration messagesConfig;
    private File messagesFile;
    private String prefix;

    /**
     * Creates a new MessageManager instance.
     *
     * @param plugin The main plugin instance
     */
    public MessageManager(SimpleSession plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    /**
     * Loads or reloads the messages configuration.
     */
    public void loadMessages() {
        // Create messages file if it doesn't exist
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        // Load messages configuration
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        // Load defaults from jar
        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                new InputStreamReader(defaultStream)
            );
            messagesConfig.setDefaults(defaultConfig);
        }

        // Cache prefix for performance
        prefix = getMessage("prefix");
    }

    /**
     * Saves the messages configuration to file.
     */
    public void saveMessages() {
        try {
            messagesConfig.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save messages.yml!");
            e.printStackTrace();
        }
    }

    /**
     * Gets a message from messages.yml with color codes translated.
     *
     * @param path Path to the message in messages.yml
     * @return Formatted message with colors, or path if not found
     */
    public String getMessage(String path) {
        String message = messagesConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Message not found: " + path);
            return path;
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Gets a message with placeholders replaced.
     *
     * @param path Path to the message
     * @param placeholders Placeholder replacements (key, value, key, value, ...)
     * @return Formatted message with placeholders replaced
     */
    public String getMessage(String path, String... placeholders) {
        String message = getMessage(path);

        // Replace placeholders
        for (int i = 0; i < placeholders.length - 1; i += 2) {
            String placeholder = placeholders[i];
            String value = placeholders[i + 1];
            message = message.replace(placeholder, value);
        }

        // Always replace {prefix}
        message = message.replace("{prefix}", prefix);

        return message;
    }

    /**
     * Gets a list of messages from messages.yml.
     *
     * @param path Path to the message list
     * @return List of formatted messages with colors
     */
    public List<String> getMessageList(String path) {
        List<String> messages = messagesConfig.getStringList(path);
        return messages.stream()
                .map(msg -> ChatColor.translateAlternateColorCodes('&', msg))
                .collect(Collectors.toList());
    }

    /**
     * Gets a list of messages with placeholders replaced.
     *
     * @param path Path to the message list
     * @param placeholders Placeholder replacements (key, value, key, value, ...)
     * @return List of formatted messages with placeholders replaced
     */
    public List<String> getMessageList(String path, String... placeholders) {
        List<String> messages = getMessageList(path);

        return messages.stream()
                .map(msg -> {
                    String message = msg;
                    // Replace placeholders
                    for (int i = 0; i < placeholders.length - 1; i += 2) {
                        String placeholder = placeholders[i];
                        String value = placeholders[i + 1];
                        message = message.replace(placeholder, value);
                    }
                    // Always replace {prefix}
                    message = message.replace("{prefix}", prefix);
                    return message;
                })
                .collect(Collectors.toList());
    }

    /**
     * Gets the plugin prefix.
     *
     * @return Formatted prefix with colors
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * Reloads the messages configuration.
     */
    public void reload() {
        loadMessages();
    }
}
