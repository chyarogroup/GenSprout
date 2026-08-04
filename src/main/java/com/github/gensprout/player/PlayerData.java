package com.github.gensprout.player;

import java.util.UUID;

public class PlayerData {

    private final UUID uuid;
    private int level = 1;
    private double farmingXp = 0.0;
    private int prestige = 0;
    private int prestigePoints = 0;
    
    // Multiplier levels purchased in the Prestige Shop
    private int xpMultiplierLevel = 0;
    private int moneyMultiplierLevel = 0;
    private int essenceMultiplierLevel = 0;

    private int essence = 0;
    private double balance = 0.0;
    private int purchasedSlots = 0;
    private int essenceSlots = 0;
    private long lastSeen = System.currentTimeMillis();
    private boolean completedTutorial = false;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getFarmingXp() {
        return farmingXp;
    }

    public void setFarmingXp(double farmingXp) {
        this.farmingXp = farmingXp;
    }

    public int getPrestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
    }

    public int getPrestigePoints() {
        return prestigePoints;
    }

    public void setPrestigePoints(int prestigePoints) {
        this.prestigePoints = prestigePoints;
    }

    public int getXpMultiplierLevel() {
        return xpMultiplierLevel;
    }

    public void setXpMultiplierLevel(int xpMultiplierLevel) {
        this.xpMultiplierLevel = xpMultiplierLevel;
    }

    public int getMoneyMultiplierLevel() {
        return moneyMultiplierLevel;
    }

    public void setMoneyMultiplierLevel(int moneyMultiplierLevel) {
        this.moneyMultiplierLevel = moneyMultiplierLevel;
    }

    public int getEssenceMultiplierLevel() {
        return essenceMultiplierLevel;
    }

    public void setEssenceMultiplierLevel(int essenceMultiplierLevel) {
        this.essenceMultiplierLevel = essenceMultiplierLevel;
    }

    public int getEssence() {
        return essence;
    }

    public void setEssence(int essence) {
        this.essence = essence;
    }

    public void addEssence(int amount) {
        this.essence += amount;
    }

    public boolean removeEssence(int amount) {
        if (this.essence >= amount) {
            this.essence -= amount;
            return true;
        }
        return false;
    }

    public int getPurchasedSlots() {
        return purchasedSlots;
    }

    public void setPurchasedSlots(int purchasedSlots) {
        this.purchasedSlots = purchasedSlots;
    }

    public void addPurchasedSlot() {
        this.purchasedSlots++;
    }

    public int getEssenceSlots() {
        return essenceSlots;
    }

    public void setEssenceSlots(int essenceSlots) {
        this.essenceSlots = Math.min(25, Math.max(0, essenceSlots));
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    /**
     * Total slot limit: default slots (starts at 20) + 1 per prestige + purchased slots + essence slots
     */
    public int getMaxSlots(int defaultSlots) {
        return defaultSlots + prestige + purchasedSlots + essenceSlots;
    }

    /**
     * Compute multiplier value based on level (1.0x baseline + 0.05x per shop upgrade)
     */
    public double getXpMultiplier() {
        return 1.0 + (xpMultiplierLevel * 0.05);
    }

    public double getMoneyMultiplier() {
        return 1.0 + (moneyMultiplierLevel * 0.05);
    }

    public boolean hasCompletedTutorial() {
        return completedTutorial;
    }

    public void setCompletedTutorial(boolean completedTutorial) {
        this.completedTutorial = completedTutorial;
    }

    /**
     * Clears all stats about a player (level, xp, prestige, prestige points, multipliers, essence, money).
     */
    public void clearStats() {
        this.level = 1;
        this.farmingXp = 0.0;
        this.prestige = 0;
        this.prestigePoints = 0;
        this.xpMultiplierLevel = 0;
        this.moneyMultiplierLevel = 0;
        this.essenceMultiplierLevel = 0;
        this.essence = 0;
        this.balance = 0.0;
        this.completedTutorial = false;
    }

    public double getEssenceMultiplier() {
        return 1.0 + (essenceMultiplierLevel * 0.05);
    }
}
