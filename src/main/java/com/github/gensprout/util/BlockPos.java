package com.github.gensprout.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

/**
 * Immutable block coordinate record for safe, lightweight, O(1) map and set hashing.
 * Avoids Bukkit Location issues with pitch/yaw variations and World instance equality.
 */
public record BlockPos(String world, int x, int y, int z) {

    public static BlockPos fromLocation(Location loc) {
        String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "";
        return new BlockPos(worldName, loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        return w != null ? new Location(w, x, y, z) : null;
    }

    public boolean isSameBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        return loc.getWorld().getName().equals(world)
                && loc.getBlockX() == x
                && loc.getBlockY() == y
                && loc.getBlockZ() == z;
    }
}
