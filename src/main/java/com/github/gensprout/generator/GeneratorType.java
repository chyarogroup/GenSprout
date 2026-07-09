package com.github.gensprout.generator;

import org.bukkit.Material;

public class GeneratorType {

    private final int tier;
    private final String displayName;
    private final Material blockType;
    private final double buyPrice;
    private final double upgradePrice;
    private final double dropValue;
    private final String dropName;

    public GeneratorType(int tier, String displayName, Material blockType, double buyPrice, double upgradePrice, double dropValue, String dropName) {
        this.tier = tier;
        this.displayName = displayName;
        this.blockType = blockType;
        this.buyPrice = buyPrice;
        this.upgradePrice = upgradePrice;
        this.dropValue = dropValue;
        this.dropName = dropName;
    }

    public int getTier() {
        return tier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Material getBlockType() {
        return blockType;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public double getUpgradePrice() {
        return upgradePrice;
    }

    public double getDropValue() {
        return dropValue;
    }

    public String getDropName() {
        return dropName;
    }
}
