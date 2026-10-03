package pl.tremeq.simplesession.manager;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import pl.tremeq.simplesession.SimpleSession;
import pl.tremeq.simplesession.util.Text;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * MessageManager handles all plugin messages from messages.yml.
 *
 * Missing keys fall back to the defaults bundled in the jar.
 * Every message supports & colors, &#RRGGBB hex colors and the {prefix} placeholder.
 *
 * @author TremeQ
 */
public class MessageManager {

    private final SimpleSession plugin;
    private FileConfiguration messagesConfig;
    private String prefix = "";

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
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
            messagesConfig.setDefaults(defaultConfig);
        }

        String rawPrefix = messagesConfig.getString("prefix");
        prefix = rawPrefix == null ? "" : Text.color(rawPrefix);
    }

    /**
     * Gets a message with colors translated and {prefix} replaced.
     *
     * @param path Path to the message in messages.yml
     * @return Formatted message, or the path if the message does not exist
     */
    public String getMessage(String path) {
        return getMessage(path, new String[0]);
    }

    /**
     * Gets a message with placeholders replaced.
     *
     * @param path Path to the message
     * @param placeholders Placeholder replacements (key, value, key, value, ...)
     * @return Formatted message
     */
    public String getMessage(String path, String... placeholders) {
        String message = messagesConfig.getString(path);
        if (message == null) {
            plugin.getLogger().warning("Message not found: " + path);
            return path;
        }
        return format(message, placeholders);
    }

    /**
     * Gets a list of messages with placeholders replaced.
     *
     * @param path Path to the message list
     * @param placeholders Placeholder replacements (key, value, key, value, ...)
     * @return Formatted lines
     */
    public List<String> getMessageList(String path, String... placeholders) {
        return messagesConfig.getStringList(path).stream()
                .map(line -> format(line, placeholders))
                .toList();
    }

    /**
     * Sends a message; empty messages are not sent (lets admins disable a message).
     *
     * @param sender Receiver
     * @param path Path to the message
     * @param placeholders Placeholder replacements (key, value, key, value, ...)
     */
    public void send(CommandSender sender, String path, String... placeholders) {
        String message = getMessage(path, placeholders);
        if (!message.isEmpty()) {
            sender.sendMessage(message);
        }
    }

    /**
     * Sends every line of a message list.
     *
     * @param sender Receiver
     * @param path Path to the message list
     * @param placeholders Placeholder replacements (key, value, key, value, ...)
     */
    public void sendList(CommandSender sender, String path, String... placeholders) {
        for (String line : getMessageList(path, placeholders)) {
            sender.sendMessage(line);
        }
    }

    private String format(String message, String... placeholders) {
        String replaced = Text.replace(message, placeholders);
        return Text.color(replaced).replace("{prefix}", prefix);
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
