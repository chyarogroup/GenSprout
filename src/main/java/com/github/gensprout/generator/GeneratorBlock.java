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
}
