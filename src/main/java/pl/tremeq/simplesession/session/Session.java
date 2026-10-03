package pl.tremeq.simplesession.session;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A single play session of a player. Thread-safe.
 *
 * @author TremeQ
 */
public final class Session {

    private final UUID uuid;
    private volatile String name;
    private volatile long startMillis;
    private final Set<String> milestones = ConcurrentHashMap.newKeySet();

    /**
     * @param uuid Player UUID
     * @param name Player name
     * @param startMillis Session start (epoch millis)
     */
    public Session(UUID uuid, String name, long startMillis) {
        this.uuid = uuid;
        this.name = name;
        this.startMillis = startMillis;
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    void name(String name) {
        this.name = name;
    }

    public long startMillis() {
        return startMillis;
    }

    /**
     * @param now Current time millis
     * @return Session length in whole seconds
     */
    public long seconds(long now) {
        return Math.max(0, (now - startMillis) / 1000);
    }

    /**
     * Moves the start forward (used to skip time spent offline during relog protection).
     *
     * @param millis Milliseconds to skip
     */
    void shift(long millis) {
        startMillis += Math.max(0, millis);
    }

    /**
     * Restarts the session from now and forgets achieved milestones.
     *
     * @param now Current time millis
     */
    void restart(long now) {
        startMillis = now;
        milestones.clear();
    }

    /**
     * Marks a milestone as achieved.
     *
     * @param id Milestone id
     * @return true if it was not achieved before
     */
    public boolean achieve(String id) {
        return milestones.add(id);
    }

    /**
     * @return Achieved milestone ids
     */
    public Set<String> milestones() {
        return Set.copyOf(milestones);
    }

    void restoreMilestones(Collection<String> ids) {
        milestones.addAll(ids);
    }
}
