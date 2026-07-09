package com.github.gensprout.farming;

import org.bukkit.Location;
import org.bukkit.World;

public class FarmRegion {

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public FarmRegion(String worldName, int x1, int y1, int z1, int x2, int y2, int z2) {
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    /**
     * Check if a location lies inside this cuboid region.
     */
    public boolean contains(Location loc) {
        World world = loc.getWorld();
        if (world == null || !world.getName().equals(worldName)) {
            return false;
        }
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();
        
        // Farmland is at minY, crop block is at minY + 1.
        // If the region was selected as a single horizontal plane (minY == maxY), we want to include minY + 1 (the crop layer).
        int effectiveMaxY = Math.max(maxY, minY + 1);
        
        return x >= minX && x <= maxX &&
               y >= minY && y <= effectiveMaxY &&
               z >= minZ && z <= maxZ;
    }
}
