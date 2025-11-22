package pl.tremeq.simplesession.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import pl.tremeq.simplesession.SimpleSession;
import pl.tremeq.simplesession.manager.SessionManager;

import java.util.UUID;

/**
 * PlaceholderAPI expansion for SimpleSession.
 *
 * Provides placeholders for displaying player session time in various formats.
 *
 * Available placeholders:
 * - %simplesession_seconds% - Remaining seconds (0-59)
 * - %simplesession_minutes% - Remaining minutes (0-59)
 * - %simplesession_hours% - Remaining hours (0-23)
 * - %simplesession_days% - Total days
 * - %simplesession_total_seconds% - Total session duration in seconds
 * - %simplesession_total_minutes% - Total session duration in minutes
 * - %simplesession_total_hours% - Total session duration in hours
 * - %simplesession_total_days% - Total session duration in days
 * - %simplesession_formatted% - Formatted time using default format
 * - %simplesession_formatted_full% - Formatted time using full format
 * - %simplesession_formatted_short% - Formatted time using short format
 * - %simplesession_formatted_custom% - Formatted time using custom format
 * - %simplesession_rank% - Player's rank in current session leaderboard
 *
 * @author TremeQ
 */
public class SimpleSessionExpansion extends PlaceholderExpansion {

    private final SimpleSession plugin;

    /**
     * Creates a new PlaceholderAPI expansion for SimpleSession.
     *
     * @param plugin The main plugin instance
     */
    public SimpleSessionExpansion(SimpleSession plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the identifier for this expansion.
     *
     * @return The identifier string
     */
    @Override
    @NotNull
    public String getIdentifier() {
        return "simplesession";
    }

    /**
     * Gets the author of this expansion.
     *
     * @return The author name
     */
    @Override
    @NotNull
    public String getAuthor() {
        return "TremeQ";
    }

    /**
     * Gets the version of this expansion.
     *
     * @return The version string
     */
    @Override
    @NotNull
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    /**
     * Indicates that this expansion should persist through reloads.
     *
     * @return true to persist
     */
    @Override
    public boolean persist() {
        return true;
    }

    /**
     * Handles placeholder requests.
     *
     * @param player The player for which the placeholder is being requested
     * @param params The placeholder parameters (after %simplesession_)
     * @return The placeholder value, or null if invalid
     */
    @Override
    @Nullable
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        SessionManager sessionManager = plugin.getSessionManager();
        String lowerParams = params.toLowerCase();

        // Handle top leaderboard placeholders (don't require player)
        if (lowerParams.startsWith("top_")) {
            return handleTopPlaceholder(lowerParams, sessionManager);
        }

        // All other placeholders require a player
        if (player == null) {
            return "";
        }

        UUID playerId = player.getUniqueId();

        // Check if player has an active session
        if (!sessionManager.hasActiveSession(playerId)) {
            return "0";
        }

        // Parse the placeholder request
        switch (lowerParams) {
            // Individual time components (remaining)
            case "seconds":
                return String.valueOf(sessionManager.getRemainingSeconds(playerId));

            case "minutes":
                return String.valueOf(sessionManager.getRemainingMinutes(playerId));

            case "hours":
                return String.valueOf(sessionManager.getRemainingHours(playerId));

            case "days":
                return String.valueOf(sessionManager.getSessionDays(playerId));

            // Total time in different units
            case "total_seconds":
                return String.valueOf(sessionManager.getSessionSeconds(playerId));

            case "total_minutes":
                return String.valueOf(sessionManager.getSessionMinutes(playerId));

            case "total_hours":
                return String.valueOf(sessionManager.getSessionHours(playerId));

            case "total_days":
                return String.valueOf(sessionManager.getSessionDays(playerId));

            // Formatted time strings
            case "formatted":
                return sessionManager.getFormattedSessionTime(playerId);

            case "formatted_full":
                return sessionManager.getFormattedSessionTime(playerId, "full");

            case "formatted_short":
                return sessionManager.getFormattedSessionTime(playerId, "short");

            case "formatted_custom":
                return sessionManager.getFormattedSessionTime(playerId, "custom");

            // Ranking
            case "rank":
                int rank = sessionManager.getPlayerRank(playerId);
                return rank > 0 ? String.valueOf(rank) : "N/A";

            default:
                // Return null for unknown placeholders
                return null;
        }
    }

    /**
     * Handles top leaderboard placeholders.
     * Format: top_<position>_<type> where type is 'name' or 'time'
     *
     * @param params Placeholder parameters
     * @param sessionManager SessionManager instance
     * @return Placeholder value, or empty string if invalid
     */
    private String handleTopPlaceholder(String params, SessionManager sessionManager) {
        // Expected format: top_1_name or top_1_time
        String[] parts = params.split("_");

        // Validate format: should have exactly 3 parts (top, number, type)
        if (parts.length != 3) {
            return "";
        }

        // Parse position number
        int position;
        try {
            position = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return "";
        }

        // Validate position is in range 1-10
        if (position < 1 || position > 10) {
            return "";
        }

        // Get type (name or time)
        String type = parts[2];

        switch (type) {
            case "name":
                return sessionManager.getTopPlayerName(position);

            case "time":
                return sessionManager.getTopPlayerTime(position);

            default:
                return "";
        }
    }
}
