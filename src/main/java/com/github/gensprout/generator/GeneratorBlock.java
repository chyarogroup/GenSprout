package com.github.gensprout.generator;

import org.bukkit.Location;
import java.util.UUID;

public class GeneratorBlock {

    private final Location location;
    private final UUID ownerUuid;
    private int tier;
    private long lastHarvestTime;
    private int accumulatedDrops;

    public GeneratorBlock(Location location, UUID ownerUuid, int tier) {
        this.location = location;
        this.ownerUuid = ownerUuid;
        this.tier = tier;
        this.lastHarvestTime = System.currentTimeMillis();
        this.accumulatedDrops = 0;
    }

    public GeneratorBlock(Location location, UUID ownerUuid, int tier, long lastHarvestTime, int accumulatedDrops) {
        this.location = location;
        this.ownerUuid = ownerUuid;
        this.tier = tier;
        this.lastHarvestTime = lastHarvestTime;
        this.accumulatedDrops = accumulatedDrops;
    }

    public Location getLocation() {
        return location;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public int getTier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = tier;
    }

    public long getLastHarvestTime() {
        return lastHarvestTime;
    }

    public void setLastHarvestTime(long lastHarvestTime) {
        this.lastHarvestTime = lastHarvestTime;
    }

    public int getAccumulatedDrops() {
        return accumulatedDrops;
    }

    public void setAccumulatedDrops(int accumulatedDrops) {
        this.accumulatedDrops = accumulatedDrops;
    }

    public void addAccumulatedDrops(int amount) {
        this.accumulatedDrops += amount;
    }

    /**
     * Compute offline catch-up drops based on current time and tick interval (in seconds).
     * Returns the count of newly accrued drops.
     */
    public int calculateCatchUp(long currentTime, int tickIntervalSeconds) {
        long elapsedMs = currentTime - lastHarvestTime;
        long intervalMs = tickIntervalSeconds * 1000L;
        if (elapsedMs >= intervalMs) {
            int newDrops = (int) (elapsedMs / intervalMs);
            // Cap it at a reasonable limit to prevent database/economy inflation (e.g. max 10,000 drops accumulated)
            if (accumulatedDrops + newDrops > 10000) {
                newDrops = Math.max(0, 10000 - accumulatedDrops);
            }
            this.accumulatedDrops += newDrops;
            this.lastHarvestTime += newDrops * intervalMs;
            return newDrops;
        }
        return 0;
    }
}
