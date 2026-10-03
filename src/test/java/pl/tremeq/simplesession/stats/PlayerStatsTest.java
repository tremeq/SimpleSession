package pl.tremeq.simplesession.stats;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerStatsTest {

    private final Periods periods = Periods.of("UTC", "MONDAY", "dd.MM.yyyy");

    private PlayerStats fresh(long today) {
        long day = periods.today();
        return new PlayerStats(new StoredStats(UUID.randomUUID(), "Tester", 100, 50, 2, 0, 0,
                day, today, periods.weekStart(day), today, periods.monthStart(day), today));
    }

    @Test
    void startCountsNewSessionsOnly() {
        PlayerStats stats = fresh(0);
        stats.start("Tester", 1000, true);
        assertEquals(3, stats.snapshot().sessions());
        assertEquals(1000, stats.firstJoin());
        stats.start("Tester", 2000, false);
        assertEquals(3, stats.snapshot().sessions());
        assertEquals(1000, stats.firstJoin());
    }

    @Test
    void accrueKeepsMillisecondRemainder() {
        PlayerStats stats = fresh(10);
        stats.start("Tester", 0, true);
        stats.accrue(1_500, periods, 1);
        stats.accrue(3_000, periods, 3);
        PlayerStats.Snapshot snapshot = stats.snapshot();
        assertEquals(103, snapshot.total());
        assertEquals(13, snapshot.today());
        assertEquals(50, snapshot.record());
    }

    @Test
    void liveValuesIncludePendingTime() {
        PlayerStats stats = fresh(10);
        stats.start("Tester", 0, true);
        assertEquals(110, stats.live(StatType.TOTAL, 10_000, periods, 10));
        assertEquals(20, stats.live(StatType.TODAY, 10_000, periods, 10));
        assertEquals(20, stats.live(StatType.WEEK, 10_000, periods, 10));
        assertEquals(70, stats.live(StatType.RECORD, 10_000, periods, 70));
        assertEquals(3, stats.live(StatType.SESSIONS, 10_000, periods, 10));
        assertEquals(36, stats.liveAverage(10_000));
    }

    @Test
    void periodsRollOver() {
        long day = periods.today();
        PlayerStats stats = new PlayerStats(new StoredStats(UUID.randomUUID(), "Tester", 100, 50, 2, 0, 0,
                day - 40, 500, periods.weekStart(day - 40), 500, periods.monthStart(day - 40), 500));
        stats.start("Tester", 0, false);
        assertEquals(5, stats.live(StatType.TODAY, 5_000, periods, 5));
        assertEquals(5, stats.live(StatType.MONTH, 5_000, periods, 5));
        stats.accrue(5_000, periods, 5);
        assertEquals(5, stats.snapshot().today());
        assertEquals(day, stats.snapshot().dayKey());
    }

    @Test
    void modifyAndReset() {
        PlayerStats stats = fresh(10);
        assertEquals(3700, stats.modify(StatField.TOTAL, StatField.Operation.ADD, 3600));
        assertEquals(0, stats.modify(StatField.RECORD, StatField.Operation.TAKE, 999));
        stats.reset(StatField.ResetTarget.PERIODS);
        assertEquals(0, stats.snapshot().today());
        assertEquals(3700, stats.snapshot().total());
        stats.reset(StatField.ResetTarget.ALL);
        assertEquals(0, stats.snapshot().total());
        assertEquals(0, stats.snapshot().sessions());
    }
}
