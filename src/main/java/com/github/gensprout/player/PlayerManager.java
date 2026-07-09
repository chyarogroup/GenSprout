package com.github.gensprout.player;

import com.github.gensprout.GenSprout;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlayerManager {

    private final GenSprout plugin;
    private final Map<UUID, PlayerData> dataCache = new ConcurrentHashMap<>();
    private final File dataFile;
    private FileConfiguration dataConfig;

    public PlayerManager(GenSprout plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/players.yml");
        loadDataFile();
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
            data.setPurchasedSlots(dataConfig.getInt(path + ".purchased-slots", 0));
        } else {
            // First time player setup
            // Schedule giving of 15 tier 1 generators to the player
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && player.isOnline()) {
                    plugin.getGeneratorManager().giveGenerator(player, 1, 15);
                    player.sendMessage(plugin.getMiniMessage().deserialize("<green>Welcome! You've received 15 Tier 1 Generators to get started!</green>"));
                }
            }, 40L); // 2 seconds after join
        }
        return data;
    }

    public void savePlayer(UUID uuid) {
        PlayerData data = dataCache.get(uuid);
        if (data == null) return;

        String path = uuid.toString();
        dataConfig.set(path + ".level", data.getLevel());
        dataConfig.set(path + ".xp", data.getFarmingXp());
        dataConfig.set(path + ".prestige", data.getPrestige());
        dataConfig.set(path + ".prestige-points", data.getPrestigePoints());
        dataConfig.set(path + ".xp-mult-level", data.getXpMultiplierLevel());
        dataConfig.set(path + ".money-mult-level", data.getMoneyMultiplierLevel());
        dataConfig.set(path + ".essence-mult-level", data.getEssenceMultiplierLevel());
        dataConfig.set(path + ".essence", data.getEssence());
        dataConfig.set(path + ".purchased-slots", data.getPurchasedSlots());

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player " + uuid + " to players.yml!", e);
        }
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        dataCache.remove(uuid);
    }

    public void saveAll() {
        for (UUID uuid : dataCache.keySet()) {
            savePlayer(uuid);
        }
    }

    public void addXp(Player player, double amount) {
        PlayerData data = getPlayerData(player.getUniqueId());
        double currentXp = data.getFarmingXp() + amount;
        int level = data.getLevel();
        int baseRequiredXp = plugin.getConfig().getInt("leveling.formula.base-xp", 100);
        int multiplier = plugin.getConfig().getInt("leveling.formula.multiplier", 50);

        boolean leveledUp = false;
        while (true) {
            double required = baseRequiredXp + (level * multiplier);
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
            player.sendActionBar(plugin.getMiniMessage().deserialize("<green><bold>LEVEL UP!</bold> You are now Level <gold>" + level + "</gold>!</green>"));
            player.sendMessage(plugin.getMiniMessage().deserialize("<green><bold>LEVEL UP!</bold> You are now Level <gold>" + level + "</gold>!</green>"));
        }
        
        savePlayer(player.getUniqueId());
    }
}
