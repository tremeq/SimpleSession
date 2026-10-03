package pl.tremeq.simplesession.stats;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsDatabaseTest {

    private static final long TODAY = 20_000;
    private static final long WEEK = 19_997;
    private static final long MONTH = 19_990;

    @TempDir
    Path dir;

    private StatsDatabase db;
    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void open() throws Exception {
        db = new StatsDatabase(dir.resolve("stats.db").toFile(), "ss_");
    }

    @AfterEach
    void close() throws Exception {
        db.close();
    }

    private void save(UUID id, String name, long total, long record, long sessions, long day, long today) throws Exception {
        db.save(List.of(new PlayerStats.Snapshot(id, name, total, record, sessions, 1000, 2000, day, today)));
    }

    @Test
    void unknownPlayerLoadsEmpty() throws Exception {
        StoredStats stats = db.load(alice, "Alice", TODAY, WEEK, MONTH);
        assertEquals("Alice", stats.name());
        assertEquals(0, stats.total());
        assertEquals(0, stats.sessions());
        assertEquals(TODAY, stats.dayKey());
    }

    @Test
    void saveAndLoadWithPeriods() throws Exception {
        save(alice, "Alice", 500, 300, 4, MONTH + 1, 100);   // this month, before this week
        save(alice, "Alice", 600, 300, 4, WEEK + 1, 50);     // this week
        save(alice, "Alice", 700, 300, 5, TODAY, 30);        // today
        save(alice, "Alice", 700, 300, 5, MONTH - 1, 999);   // previous month

        StoredStats stats = db.load(alice, "x", TODAY, WEEK, MONTH);
        assertEquals("Alice", stats.name());
        assertEquals(700, stats.total());
        assertEquals(300, stats.record());
        assertEquals(5, stats.sessions());
        assertEquals(30, stats.today());
        assertEquals(80, stats.week());
        assertEquals(180, stats.month());
        assertEquals(140, stats.average());
    }

    @Test
    void dailyRowIsOverwrittenWithAbsoluteValue() throws Exception {
        save(alice, "Alice", 10, 10, 1, TODAY, 10);
        save(alice, "Alice", 25, 25, 1, TODAY, 25);
        assertEquals(25, db.load(alice, "Alice", TODAY, WEEK, MONTH).today());
    }

    @Test
    void leaderboardsAndCounts() throws Exception {
        save(alice, "Alice", 1000, 200, 3, TODAY, 40);
        save(bob, "Bob", 2000, 100, 9, TODAY, 60);
        save(bob, "Bob", 2000, 100, 9, WEEK, 500);

        List<LeaderboardEntry> total = db.top(StatType.TOTAL, TODAY, WEEK, MONTH, 10, 0);
        assertEquals(List.of("Bob", "Alice"), total.stream().map(LeaderboardEntry::name).toList());
        assertEquals(2000, total.get(0).value());

        assertEquals("Alice", db.top(StatType.RECORD, TODAY, WEEK, MONTH, 10, 0).get(0).name());
        assertEquals(9, db.top(StatType.SESSIONS, TODAY, WEEK, MONTH, 10, 0).get(0).value());
        assertEquals(60, db.top(StatType.TODAY, TODAY, WEEK, MONTH, 10, 0).get(0).value());
        assertEquals(560, db.top(StatType.WEEK, TODAY, WEEK, MONTH, 10, 0).get(0).value());

        List<LeaderboardEntry> page2 = db.top(StatType.TOTAL, TODAY, WEEK, MONTH, 1, 1);
        assertEquals("Alice", page2.get(0).name());
        assertEquals(2, db.count(StatType.TOTAL, TODAY, WEEK, MONTH));
        assertEquals(2, db.count(StatType.TODAY, TODAY, WEEK, MONTH));
        assertEquals(0, db.count(StatType.TODAY, TODAY + 1, WEEK, MONTH));
    }

    @Test
    void findByNameIsCaseInsensitive() throws Exception {
        save(alice, "Alice", 1, 1, 1, TODAY, 1);
        assertEquals(alice, db.findByName("aLiCe").orElseThrow().uuid());
        assertTrue(db.findByName("nobody").isEmpty());
    }

    @Test
    void modifyAndReset() throws Exception {
        save(alice, "Alice", 1000, 200, 3, TODAY, 40);
        assertEquals(4600, db.modify(alice, StatField.TOTAL, StatField.Operation.ADD, 3600));
        assertEquals(0, db.modify(alice, StatField.TOTAL, StatField.Operation.TAKE, 999_999));
        assertEquals(50, db.modify(alice, StatField.SESSIONS, StatField.Operation.SET, 50));

        db.reset(alice, StatField.ResetTarget.PERIODS);
        StoredStats afterPeriods = db.load(alice, "Alice", TODAY, WEEK, MONTH);
        assertEquals(0, afterPeriods.today());
        assertEquals(200, afterPeriods.record());

        db.reset(alice, StatField.ResetTarget.ALL);
        StoredStats afterAll = db.load(alice, "Alice", TODAY, WEEK, MONTH);
        assertEquals(0, afterAll.record());
        assertEquals(0, afterAll.sessions());
        assertEquals(1000, afterAll.firstJoin());
    }

    @Test
    void purgeRemovesOldDays() throws Exception {
        save(alice, "Alice", 1, 1, 1, TODAY - 500, 10);
        save(alice, "Alice", 1, 1, 1, TODAY, 20);
        assertEquals(1, db.purgeDaily(TODAY - 400));
    }

    @Test
    void invalidPrefixFallsBack() throws Exception {
        try (StatsDatabase other = new StatsDatabase(dir.resolve("other.db").toFile(), "bad;DROP")) {
            other.save(List.of(new PlayerStats.Snapshot(alice, "Alice", 5, 5, 1, 1, 1, TODAY, 5)));
            assertEquals(5, other.load(alice, "Alice", TODAY, WEEK, MONTH).total());
        }
    }
}
