package com.github.gensprout.player;

import com.github.gensprout.GenSprout;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerManager {

    private final GenSprout plugin;
    private final Map<UUID, PlayerData> dataCache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyPlayers = ConcurrentHashMap.newKeySet();
    private final File dataFile;
    private FileConfiguration dataConfig;

    public PlayerManager(GenSprout plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/players.yml");
        loadDataFile();
        startAutoSaveTask();
    }

    private void loadDataFile() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create players.yml!", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    private void startAutoSaveTask() {
        // Auto-save dirty player data asynchronously every 3 minutes (3600 ticks)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveDirtyPlayers, 3600L, 3600L);
    }

    public PlayerData getPlayerData(UUID uuid) {
        return dataCache.computeIfAbsent(uuid, this::loadPlayerFromConfig);
    }

    public PlayerData getCachedData(UUID uuid) {
        return dataCache.get(uuid);
    }

    private PlayerData loadPlayerFromConfig(UUID uuid) {
        PlayerData data = new PlayerData(uuid);
        String path = uuid.toString();
        
        if (dataConfig.contains(path)) {
            data.setLevel(dataConfig.getInt(path + ".level", 1));
            data.setFarmingXp(dataConfig.getDouble(path + ".xp", 0.0));
            data.setPrestige(dataConfig.getInt(path + ".prestige", 0));
            data.setPrestigePoints(dataConfig.getInt(path + ".prestige-points", 0));
            data.setXpMultiplierLevel(dataConfig.getInt(path + ".xp-mult-level", 0));
            data.setMoneyMultiplierLevel(dataConfig.getInt(path + ".money-mult-level", 0));
            data.setEssenceMultiplierLevel(dataConfig.getInt(path + ".essence-mult-level", 0));
            data.setEssence(dataConfig.getInt(path + ".essence", 0));
            data.setBalance(dataConfig.getDouble(path + ".balance", 0.0));
            data.setPurchasedSlots(dataConfig.getInt(path + ".purchased-slots", 0));
            data.setLastSeen(dataConfig.getLong(path + ".last-seen", System.currentTimeMillis()));
            data.setCompletedTutorial(dataConfig.getBoolean(path + ".completed-tutorial", false));
        } else {
            // First time player setup (starter items are claimed manually via /start)
        }
        return data;
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = dataCache.get(uuid);
        if (data == null) return;

        String path = uuid.toString();
        synchronized (dataConfig) {
            dataConfig.set(path + ".level", data.getLevel());
            dataConfig.set(path + ".xp", data.getFarmingXp());
            dataConfig.set(path + ".prestige", data.getPrestige());
            dataConfig.set(path + ".prestige-points", data.getPrestigePoints());
            dataConfig.set(path + ".xp-mult-level", data.getXpMultiplierLevel());
            dataConfig.set(path + ".money-mult-level", data.getMoneyMultiplierLevel());
            dataConfig.set(path + ".essence-mult-level", data.getEssenceMultiplierLevel());
            dataConfig.set(path + ".essence", data.getEssence());
            dataConfig.set(path + ".balance", data.getBalance());
            dataConfig.set(path + ".purchased-slots", data.getPurchasedSlots());
            dataConfig.set(path + ".last-seen", data.getLastSeen());
            dataConfig.set(path + ".completed-tutorial", data.hasCompletedTutorial());
        }
        dirtyPlayers.add(uuid);
    }

    public synchronized void saveDirtyPlayers() {
        if (dirtyPlayers.isEmpty()) return;
        synchronized (dataConfig) {
            try {
                dataConfig.save(dataFile);
                dirtyPlayers.clear();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save players.yml!", e);
            }
        }
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        dataCache.remove(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveDirtyPlayers);
    }

    public void saveAll() {
        for (UUID uuid : dataCache.keySet()) {
            savePlayer(uuid);
        }
        // Save synchronously on plugin disable/shutdown
        saveDirtyPlayers();
    }

    /**
     * Estimates how much a player's generators would have earned while they were offline,
     * and pays out a configurable percentage of that as a welcome-back bonus. This is a pure
     * balance deposit - no physical items are spawned. Call this once per join.
     */
    public void processOfflineEarnings(Player player) {
        PlayerData data = getPlayerData(player.getUniqueId());
        long now = System.currentTimeMillis();
        long elapsedMs = now - data.getLastSeen();

        if (!plugin.getConfig().getBoolean("offline-generation.enabled", true)) return;
        if (elapsedMs <= 0) return;

        double maxOfflineHours = plugin.getConfig().getDouble("offline-generation.max-offline-hours", 72);
        long maxElapsedMs = (long) (maxOfflineHours * 3_600_000L);
        long cappedElapsedMs = Math.min(elapsedMs, maxElapsedMs);

        int tickIntervalSeconds = plugin.getGeneratorManager().getTickIntervalSeconds();
        if (tickIntervalSeconds <= 0) return;

        long elapsedSeconds = cappedElapsedMs / 1000L;
        long ticksElapsed = elapsedSeconds / tickIntervalSeconds;
        if (ticksElapsed <= 0) return;

        double estimatedEarnings = 0.0;
        for (com.github.gensprout.generator.GeneratorBlock gen : plugin.getGeneratorManager().getPlacedGenerators().values()) {
            if (!gen.getOwnerUuid().equals(player.getUniqueId())) continue;
            com.github.gensprout.generator.GeneratorType type = plugin.getGeneratorManager().getTierConfig(gen.getTier());
            if (type == null) continue;
            estimatedEarnings += type.getDropValue() * ticksElapsed;
        }
        if (estimatedEarnings <= 0.0) return;

        estimatedEarnings *= data.getMoneyMultiplier();

        double percentage = plugin.getConfig().getDouble("offline-generation.percentage", 0.20);
        double payout = estimatedEarnings * percentage;
        if (payout <= 0.0) return;

        com.github.gensprout.economy.EconomyHook.deposit(player, payout);
        player.sendMessage(plugin.getMiniMessage().deserialize(
                "<green>Welcome back! Your generators earned an estimated <gold>" + com.github.gensprout.economy.EconomyHook.format(estimatedEarnings) + "</gold> while you were away, "
                        + "and you've been paid <gold>" + com.github.gensprout.economy.EconomyHook.format(payout) + "</gold> (" + Math.round(percentage * 100) + "%) of that!</green>"
        ));
    }

    public void addXp(Player player, double amount) {
        PlayerData data = getPlayerData(player.getUniqueId());
        double currentXp = data.getFarmingXp() + amount;
        int levelBefore = data.getLevel();
        int level = levelBefore;

        boolean leveledUp = false;
        while (true) {
            double required = getRequiredXp(level);
            if (currentXp >= required) {
                currentXp -= required;
                level++;
                leveledUp = true;
            } else {
                break;
            }
        }

        data.setFarmingXp(currentXp);
        if (leveledUp) {
            data.setLevel(level);
            
            // Level up feedback (non-disturbing action bar and chat alert)
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
            player.sendMessage(plugin.getMiniMessage().deserialize("<green><bold>LEVEL UP!</bold> You are now Level <gold>" + level + "</gold>!</green>"));

            // If this level-up just crossed the threshold for their next prestige, announce it
            int requiredPrestigeLevel = getRequiredPrestigeLevel(data);
            if (levelBefore < requiredPrestigeLevel && level >= requiredPrestigeLevel) {
                showPrestigeAvailableTitle(player);
            }
        }

        // Show a live progress bar towards the next level in the action bar while farming
        double requiredForCurrentLevel = getRequiredXp(level);
        player.sendActionBar(plugin.getMiniMessage().deserialize(buildProgressBarMessage(level, currentXp, requiredForCurrentLevel)));
        
        savePlayer(player.getUniqueId());
    }

    /**
     * The Farming Level required for a player to trigger their next prestige.
     */
    public int getRequiredPrestigeLevel(PlayerData data) {
        int basePrestigeLevel = plugin.getConfig().getInt("leveling.prestige.base-level", 20);
        int levelIncrement = plugin.getConfig().getInt("leveling.prestige.levels-per-prestige", 5);
        return basePrestigeLevel + (data.getPrestige() * levelIncrement);
    }

    /**
     * Shows a big on-screen title telling the player a prestige is now available.
     */
    private void showPrestigeAvailableTitle(Player player) {
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                plugin.getMiniMessage().deserialize("<gradient:gold:yellow><bold>PRESTIGE AVAILABLE</bold></gradient>"),
                plugin.getMiniMessage().deserialize("<gray>Use <gold>/prestige</gold> to prestige now!</gray>"),
                net.kyori.adventure.title.Title.Times.times(
                        java.time.Duration.ofMillis(500),
                        java.time.Duration.ofSeconds(3),
                        java.time.Duration.ofMillis(500)
                )
        );
        player.showTitle(title);
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    /**
     * Computes the XP required to advance from the given level to the next one.
     * Progression is split into 3 phases:
     *  - Levels 1..early-levels: quick, small increments (fast early game to hook players)
     *  - Levels (early-levels+1)..late-level-threshold: steady linear growth
     *  - Levels above late-level-threshold: sqrt-based tapering growth so the grind never
     *    becomes excessively slow at very high levels
     */
    public double getRequiredXp(int level) {
        int baseXp = plugin.getConfig().getInt("leveling.formula.base-xp", 60);
        int earlyLevels = plugin.getConfig().getInt("leveling.formula.early-levels", 15);
        double earlyMultiplier = plugin.getConfig().getDouble("leveling.formula.early-multiplier", 15);
        double multiplier = plugin.getConfig().getDouble("leveling.formula.multiplier", 40);
        int lateLevelThreshold = plugin.getConfig().getInt("leveling.formula.late-level-threshold", 40);
        double lateMultiplier = plugin.getConfig().getDouble("leveling.formula.late-multiplier", 25);

        if (level <= earlyLevels) {
            return baseXp + (level * earlyMultiplier);
        }

        double xpAtEarlyThreshold = baseXp + (earlyLevels * earlyMultiplier);
        if (level <= lateLevelThreshold) {
            return xpAtEarlyThreshold + ((level - earlyLevels) * multiplier);
        }

        double xpAtLateThreshold = xpAtEarlyThreshold + ((lateLevelThreshold - earlyLevels) * multiplier);
        return xpAtLateThreshold + (Math.sqrt(level - lateLevelThreshold) * lateMultiplier * 10.0);
    }

    /**
     * Builds a MiniMessage progress bar string like: Lv.5 [||||||||||||||||||||] 63%
     * where completed segments are white and remaining segments are gray.
     */
    private String buildProgressBarMessage(int level, double currentXp, double requiredXp) {
        int segments = 20;
        int filled = requiredXp <= 0 ? segments : (int) Math.round((currentXp / requiredXp) * segments);
        filled = Math.max(0, Math.min(segments, filled));
        int percent = requiredXp <= 0 ? 100 : (int) Math.round((currentXp / requiredXp) * 100);
        percent = Math.max(0, Math.min(100, percent));

        StringBuilder bar = new StringBuilder();
        bar.append("<gold>Lv.").append(level).append("</gold> <gray>[</gray>");
        if (filled > 0) {
            bar.append("<white>").append("|".repeat(filled)).append("</white>");
        }
        if (segments - filled > 0) {
            bar.append("<gray>").append("|".repeat(segments - filled)).append("</gray>");
        }
        bar.append("<gray>]</gray> <yellow>").append(percent).append("%</yellow>");
        return bar.toString();
    }
}
