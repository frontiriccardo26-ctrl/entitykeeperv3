package com.entitykeeper.data;

/**
 * A global rule applied to every entity of a given resource-location type.
 *
 * forceLoad    – the chunk(s) the entity is in are kept loaded by the server
 * despawnTicks – after this many ticks the entity is forcibly removed
 *                (-1 means "do not touch despawn behaviour")
 */
public class EntityKeeperRule {

    private final String entityType;   // e.g. "minecraft:arrow" or "mymod:my_arrow"
    private boolean forceLoad;
    private int despawnTicks;          // -1 = disabled

    public EntityKeeperRule(String entityType, boolean forceLoad, int despawnTicks) {
        this.entityType   = entityType;
        this.forceLoad    = forceLoad;
        this.despawnTicks = despawnTicks;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public String  getEntityType()   { return entityType; }
    public boolean isForceLoad()     { return forceLoad; }
    public int     getDespawnTicks() { return despawnTicks; }

    // ── Setters ──────────────────────────────────────────────────────────────

    public void setForceLoad(boolean forceLoad)     { this.forceLoad = forceLoad; }
    public void setDespawnTicks(int despawnTicks)   { this.despawnTicks = despawnTicks; }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Seconds ↔ ticks conversion helpers (20 ticks = 1 second). */
    public static int secondsToTicks(int seconds) { return seconds * 20; }
    public static int ticksToSeconds(int ticks)   { return ticks / 20; }

    @Override
    public String toString() {
        String despawn = despawnTicks < 0
                ? "never"
                : ticksToSeconds(despawnTicks) + "s (" + despawnTicks + " ticks)";
        return entityType + " [forceLoad=" + forceLoad + ", despawn=" + despawn + "]";
    }
}
