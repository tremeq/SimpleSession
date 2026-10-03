package pl.tremeq.simplesession.stats;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodsTest {

    @Test
    void weekAndMonthStart() {
        Periods monday = Periods.of("UTC", "MONDAY", "dd.MM.yyyy");
        long sunday = LocalDate.of(2026, 10, 4).toEpochDay();
        assertEquals(LocalDate.of(2026, 9, 28).toEpochDay(), monday.weekStart(sunday));
        assertEquals(LocalDate.of(2026, 10, 1).toEpochDay(), monday.monthStart(sunday));

        Periods sundayStart = Periods.of("UTC", "sunday", "dd.MM.yyyy");
        assertEquals(sunday, sundayStart.weekStart(sunday));
    }

    @Test
    void invalidSettingsFallBack() {
        Periods periods = Periods.of("Not/AZone", "FUNDAY", "invalid pattern {{");
        long day = LocalDate.of(2026, 10, 7).toEpochDay();
        assertEquals(LocalDate.of(2026, 10, 5).toEpochDay(), periods.weekStart(day));
    }

    @Test
    void formatsDates() {
        Periods periods = Periods.of("UTC", "MONDAY", "dd.MM.yyyy HH:mm");
        assertEquals("01.01.1970 00:01", periods.formatDate(60_000L, "-"));
        assertEquals("-", periods.formatDate(0, "-"));
    }
}
