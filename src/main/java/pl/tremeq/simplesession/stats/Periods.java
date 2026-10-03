package pl.tremeq.simplesession.stats;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * Calendar settings for daily / weekly / monthly statistics.
 * Days are stored as epoch days (days since 1970-01-01) in the configured time zone.
 *
 * @author TremeQ
 */
public final class Periods {

    private final ZoneId zone;
    private final DayOfWeek weekStart;
    private final DateTimeFormatter dateFormat;

    /**
     * @param zone Time zone used to decide where a day starts
     * @param weekStart First day of the week
     * @param dateFormat Format for dates (first join, last seen)
     */
    public Periods(ZoneId zone, DayOfWeek weekStart, DateTimeFormatter dateFormat) {
        this.zone = zone;
        this.weekStart = weekStart;
        this.dateFormat = dateFormat.withZone(zone);
    }

    /**
     * Creates settings from config values, falling back to safe defaults.
     *
     * @param zoneName "system" or a zone id like "Europe/Warsaw"
     * @param weekStartName Day name like "MONDAY"
     * @param datePattern Pattern like "dd.MM.yyyy HH:mm"
     * @return Parsed settings
     */
    public static Periods of(String zoneName, String weekStartName, String datePattern) {
        ZoneId zone;
        try {
            zone = zoneName == null || zoneName.isBlank() || zoneName.equalsIgnoreCase("system")
                    ? ZoneId.systemDefault() : ZoneId.of(zoneName.trim());
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        DayOfWeek start;
        try {
            start = DayOfWeek.valueOf(weekStartName.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            start = DayOfWeek.MONDAY;
        }
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(datePattern);
        } catch (Exception e) {
            formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
        }
        return new Periods(zone, start, formatter);
    }

    /**
     * @return Today's epoch day
     */
    public long today() {
        return LocalDate.now(zone).toEpochDay();
    }

    /**
     * @param day Epoch day
     * @return Epoch day of the first day of that week
     */
    public long weekStart(long day) {
        return LocalDate.ofEpochDay(day).with(TemporalAdjusters.previousOrSame(weekStart)).toEpochDay();
    }

    /**
     * @param day Epoch day
     * @return Epoch day of the first day of that month
     */
    public long monthStart(long day) {
        return LocalDate.ofEpochDay(day).withDayOfMonth(1).toEpochDay();
    }

    /**
     * Formats a timestamp as a date.
     *
     * @param millis Epoch millis
     * @param empty Text returned for 0 (unknown)
     * @return Formatted date
     */
    public String formatDate(long millis, String empty) {
        return millis <= 0 ? empty : dateFormat.format(Instant.ofEpochMilli(millis));
    }
}
