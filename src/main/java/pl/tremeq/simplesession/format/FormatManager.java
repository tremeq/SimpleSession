package pl.tremeq.simplesession.format;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Loads named time formats from config.yml and resolves which format is used where.
 *
 * A format entry in "time-formats" is either a plain pattern string (classic format)
 * or a section with smart format settings and a pattern fallback.
 * The "display" section maps every place in the plugin to a format name.
 *
 * All state is immutable after loading, so formatting is safe from any thread.
 *
 * @author TremeQ
 */
public final class FormatManager {

    private static final String FALLBACK_PATTERN = "{days}d {hours}h {minutes}m {seconds}s";
    private static final TimeFormat FALLBACK = new PatternTimeFormat(FALLBACK_PATTERN, null, PluralRule.NONE);

    private final Logger logger;
    private volatile Map<String, TimeFormat> formats = Map.of();
    private volatile Map<String, String> display = Map.of();
    private volatile String defaultFormat = "full";

    /**
     * @param logger Logger for configuration warnings
     */
    public FormatManager(Logger logger) {
        this.logger = logger;
    }

    /**
     * Loads (or reloads) all formats from the plugin configuration.
     *
     * @param config Root configuration
     */
    public void load(ConfigurationSection config) {
        boolean smartEnabled = bool(config, "smart-formats.enabled", true);
        PluralRule globalRule = PluralRule.parse(string(config, "smart-formats.plural-rule", "polish"), PluralRule.POLISH);
        Map<String, UnitNames> presets = loadPresets(config.getConfigurationSection("smart-formats.unit-names"));

        Map<String, TimeFormat> loaded = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("time-formats");
        for (String key : keys(section)) {
            loaded.put(key.toLowerCase(Locale.ROOT), parseFormat(section, key, presets, globalRule, smartEnabled));
        }

        String def = string(config, "default-format", "full").toLowerCase(Locale.ROOT);
        if (!loaded.containsKey(def)) {
            logger.warning("default-format '" + def + "' does not exist in time-formats! Using built-in format.");
        }

        Map<String, String> usages = new HashMap<>();
        ConfigurationSection displaySection = config.getConfigurationSection("display");
        for (String key : keys(displaySection)) {
            String name = string(displaySection, key, def).toLowerCase(Locale.ROOT);
            if (!loaded.containsKey(name)) {
                logger.warning("display." + key + " uses unknown format '" + name + "'. Using default-format.");
                name = def;
            }
            usages.put(key.toLowerCase(Locale.ROOT), name);
        }

        this.formats = Collections.unmodifiableMap(loaded);
        this.display = Collections.unmodifiableMap(usages);
        this.defaultFormat = def;
    }

    /**
     * Gets a format by name.
     *
     * @param name Format name (case-insensitive)
     * @return Format or null if it does not exist
     */
    public TimeFormat get(String name) {
        return name == null ? null : formats.get(name.toLowerCase(Locale.ROOT));
    }

    /**
     * @return Names of all loaded formats
     */
    public Set<String> names() {
        return formats.keySet();
    }

    /**
     * Formats a duration with the named format, falling back to default-format.
     *
     * @param name Format name
     * @param seconds Duration
     * @return Formatted text
     */
    public String format(String name, long seconds) {
        TimeFormat format = get(name);
        if (format == null) {
            format = get(defaultFormat);
        }
        return (format == null ? FALLBACK : format).format(seconds);
    }

    /**
     * Formats a duration with default-format (used by %simplesession_formatted%).
     *
     * @param seconds Duration
     * @return Formatted text
     */
    public String formatDefault(long seconds) {
        return format(defaultFormat, seconds);
    }

    /**
     * Formats a duration with the format assigned to a place in the "display" section.
     *
     * @param usage Display key (e.g. "leaderboard-command")
     * @param seconds Duration
     * @return Formatted text
     */
    public String display(String usage, long seconds) {
        return format(display.getOrDefault(usage, defaultFormat), seconds);
    }

    private TimeFormat parseFormat(ConfigurationSection parent, String key, Map<String, UnitNames> presets,
                                   PluralRule globalRule, boolean smartEnabled) {
        Object raw = parent.get(key);
        if (raw instanceof String pattern) {
            return new PatternTimeFormat(pattern, presets.get("long"), globalRule);
        }
        if (!(raw instanceof ConfigurationSection section)) {
            logger.warning("Invalid time format '" + key + "' - expected text or section. Using built-in format.");
            return FALLBACK;
        }

        PluralRule rule = PluralRule.parse(section.getString("plural-rule"), globalRule);
        UnitNames names = resolveNames(section.get("names"), presets, key);
        String pattern = string(section, "pattern", FALLBACK_PATTERN);

        if (!smartEnabled || !bool(section, "smart", true)) {
            return new PatternTimeFormat(pattern, names, rule);
        }

        List<TimeUnitType> units = new ArrayList<>();
        for (String unitKey : section.getStringList("units")) {
            TimeUnitType unit = TimeUnitType.byKey(unitKey);
            if (unit == null) {
                logger.warning("Time format '" + key + "' has unknown unit '" + unitKey + "' (use weeks, days, hours, minutes, seconds).");
            } else {
                units.add(unit);
            }
        }

        String separator = string(section, "separator", " ");
        return new SmartTimeFormat(
                units,
                names,
                rule,
                string(section, "unit-format", "{value} {unit}"),
                separator,
                string(section, "last-separator", separator),
                section.getString("zero"),
                section.contains("max-units") ? section.getInt("max-units") : 0,
                SmartTimeFormat.HideZero.parse(section.getString("hide-zero"), SmartTimeFormat.HideZero.ALL));
    }

    /**
     * Reads a text value including bundled defaults (getString(path, def) would skip them).
     */
    private static String string(ConfigurationSection section, String path, String fallback) {
        String value = section.getString(path);
        return value == null ? fallback : value;
    }

    private static boolean bool(ConfigurationSection section, String path, boolean fallback) {
        return section.contains(path) ? section.getBoolean(path) : fallback;
    }

    private UnitNames resolveNames(Object value, Map<String, UnitNames> presets, String formatKey) {
        if (value instanceof ConfigurationSection inline) {
            return parseNames(inline);
        }
        String preset = value == null ? "long" : String.valueOf(value).toLowerCase(Locale.ROOT);
        UnitNames names = presets.get(preset);
        if (names == null) {
            logger.warning("Time format '" + formatKey + "' uses unknown unit-names preset '" + preset + "'. Using 'long'.");
            names = presets.get("long");
        }
        return names;
    }

    private Map<String, UnitNames> loadPresets(ConfigurationSection section) {
        Map<String, UnitNames> presets = new HashMap<>();
        presets.put("long", UnitNames.POLISH_LONG);
        presets.put("short", UnitNames.SHORT);
        for (String key : keys(section)) {
            ConfigurationSection preset = section.getConfigurationSection(key);
            if (preset != null) {
                presets.put(key.toLowerCase(Locale.ROOT), parseNames(preset));
            }
        }
        return presets;
    }

    private static UnitNames parseNames(ConfigurationSection section) {
        Map<TimeUnitType, List<String>> forms = new EnumMap<>(TimeUnitType.class);
        for (TimeUnitType unit : TimeUnitType.values()) {
            Object value = section.get(unit.key());
            if (value instanceof List<?> list) {
                forms.put(unit, list.stream().map(String::valueOf).toList());
            } else if (value != null) {
                forms.put(unit, List.of(String.valueOf(value)));
            }
        }
        return new UnitNames(forms);
    }

    /**
     * Keys of a section including keys that exist only in the bundled defaults.
     */
    private static Set<String> keys(ConfigurationSection section) {
        Set<String> keys = new LinkedHashSet<>();
        if (section == null) {
            return keys;
        }
        keys.addAll(section.getKeys(false));
        ConfigurationSection defaults = section.getDefaultSection();
        if (defaults != null) {
            keys.addAll(defaults.getKeys(false));
        }
        return keys;
    }
}
