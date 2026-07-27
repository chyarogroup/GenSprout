package com.github.gensprout.farming;

import org.bukkit.Material;

/**
 * Metadata for the 4 "real" farmland crops that participate in Prestige-based crop progression
 * (Wheat, Potatoes, Carrots, Beetroot). Melon, Pumpkin, Cocoa, Sugar Cane, and Nether Wart are
 * not farmland crops in the traditional sense and are intentionally excluded from this list.
 */
public enum FarmCropType {
    WHEAT("WHEAT", Material.WHEAT, Material.WHEAT, Material.WHEAT_SEEDS, 7),
    POTATOES("POTATOES", Material.POTATOES, Material.POTATO, null, 7),
    CARROTS("CARROTS", Material.CARROTS, Material.CARROT, null, 7),
    BEETROOTS("BEETROOTS", Material.BEETROOTS, Material.BEETROOT, Material.BEETROOT_SEEDS, 3);

    private final String configKey;
    private final Material cropBlock;
    private final Material harvestItem;
    private final Material seedItem; // null if the harvest item itself doubles as the seed (potato/carrot)
    private final int maxAge;

    FarmCropType(String configKey, Material cropBlock, Material harvestItem, Material seedItem, int maxAge) {
        this.configKey = configKey;
        this.cropBlock = cropBlock;
        this.harvestItem = harvestItem;
        this.seedItem = seedItem;
        this.maxAge = maxAge;
    }

    public String getConfigKey() {
        return configKey;
    }

    public Material getCropBlock() {
        return cropBlock;
    }

    public Material getHarvestItem() {
        return harvestItem;
    }

    /**
     * The distinct seed item used to replant this crop, or null if the harvest item itself
     * doubles as its own seed (as with real Potatoes/Carrots).
     */
    public Material getSeedItem() {
        return seedItem;
    }

    public int getMaxAge() {
        return maxAge;
    }

    public static FarmCropType byConfigKey(String key) {
        for (FarmCropType type : values()) {
            if (type.configKey.equalsIgnoreCase(key)) return type;
        }
        return WHEAT;
    }
}
