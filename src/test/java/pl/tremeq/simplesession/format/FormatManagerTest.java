package pl.tremeq.simplesession.format;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringReader;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the bundled config.yml produces the documented outputs.
 */
class FormatManagerTest {

    private YamlConfiguration bundled;
    private FormatManager formats;

    @BeforeEach
    void setUp() {
        bundled = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        formats = new FormatManager(Logger.getLogger("test"));
        formats.load(bundled);
    }

    @Test
    void bundledFormats() {
        assertEquals("2 dni, 5 godzin, 21 minut i 12 sekund", formats.format("full", 2 * 86400 + 5 * 3600 + 21 * 60 + 12));
        assertEquals("1 godzina i 5 sekund", formats.format("full", 3605));
        assertEquals("2d 5h 21m 12s", formats.format("short", 2 * 86400 + 5 * 3600 + 21 * 60 + 12));
        assertEquals("5m 3s", formats.format("short", 303));
        assertEquals("5h 21m", formats.format("compact", 5 * 3600 + 21 * 60 + 12));
        assertEquals("8s", formats.format("compact", 8));
        assertEquals("5 godzin i 21 minut", formats.format("long", 5 * 3600 + 21 * 60 + 12));
        assertEquals("125:04:09", formats.format("clock", 125 * 3600 + 4 * 60 + 9));
        assertEquals("0d 1h 0m 5s", formats.format("custom", 3605));
    }

    @Test
    void readmeFormatTable() {
        assertEquals("1 godzina, 1 minuta i 5 sekund", formats.format("full", 3665));
        assertEquals("1h 1m 5s", formats.format("short", 3665));
        assertEquals("1h 1m", formats.format("compact", 3665));
        assertEquals("1 godzina i 1 minuta", formats.format("long", 3665));
        assertEquals("1:01:05", formats.format("clock", 3665));
        assertEquals("0d 1h 1m 5s", formats.format("custom", 3665));
    }

    @Test
    void defaultAndDisplayMapping() {
        assertEquals(formats.format("full", 3605), formats.formatDefault(3605));
        assertEquals("1h 1m", formats.display("leaderboard-command", 3665));
        assertEquals("1h 1m", formats.display("leaderboard-placeholder", 3665));
        assertEquals("1 godzina i 1 minuta", formats.display("stats-placeholder", 3665));
        assertEquals("1 godzina, 1 minuta i 5 sekund", formats.display("check-command", 3665));
        assertEquals(formats.formatDefault(10), formats.display("does-not-exist", 10));
    }

    @Test
    void namesAreCaseInsensitiveAndUnknownIsNull() {
        assertNotNull(formats.get("COMPACT"));
        assertNull(formats.get("nope"));
        assertEquals(formats.formatDefault(42), formats.format("nope", 42));
    }

    @Test
    void smartDisabledGloballyUsesPatterns() {
        bundled.set("smart-formats.enabled", false);
        formats.load(bundled);
        assertEquals("0 dni, 1 godzin, 0 minut, 5 sekund", formats.format("full", 3605));
        assertEquals("1h 0m", formats.format("compact", 3605));
    }

    @Test
    void smartDisabledPerFormat() {
        bundled.set("time-formats.short.smart", false);
        formats.load(bundled);
        assertEquals("0d 1h 0m 5s", formats.format("short", 3605));
        assertEquals("1 godzina i 5 sekund", formats.format("full", 3605));
    }

    @Test
    void legacyConfigWithJarDefaults() {
        // A 1.0.0 config: string formats only, no display / smart-formats sections
        YamlConfiguration legacy = YamlConfiguration.loadConfiguration(new StringReader(
                "time-formats:\n"
                        + "  full: \"{days} dni, {hours} godzin, {minutes} minut, {seconds} sekund\"\n"
                        + "  short: \"{days}d {hours}h {minutes}m {seconds}s\"\n"
                        + "  custom: \"{hours}:{minutes}:{seconds}\"\n"
                        + "default-format: \"short\"\n"));
        legacy.setDefaults(bundled);
        formats.load(legacy);
        assertEquals("0 dni, 1 godzin, 0 minut, 5 sekund", formats.format("full", 3605));
        assertEquals("1:0:5", formats.format("custom", 3605));
        assertEquals("0d 1h 0m 5s", formats.formatDefault(3605));
        // formats added in 2.0.0 come from the jar defaults
        assertEquals("1h 1m", formats.format("compact", 3665));
        assertEquals("1h 1m", formats.display("leaderboard-command", 3665));
    }

    @Test
    void customUnitNamesPreset() {
        bundled.set("time-formats.en.names", "english");
        bundled.set("time-formats.en.plural-rule", "english");
        bundled.set("time-formats.en.separator", ", ");
        bundled.set("time-formats.en.last-separator", " and ");
        formats.load(bundled);
        assertEquals("1 hour, 2 minutes and 1 second", formats.format("en", 3721));
    }
}
