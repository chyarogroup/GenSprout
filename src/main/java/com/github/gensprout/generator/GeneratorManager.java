package com.github.gensprout.generator;

import com.github.gensprout.GenSprout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GeneratorManager {

    private final GenSprout plugin;
    private final Map<Location, GeneratorBlock> placedGenerators = new ConcurrentHashMap<>();
    private final Map<Integer, GeneratorType> generatorTiers = new HashMap<>();
    private final NamespacedKey tierKey;
    private final NamespacedKey dropValueKey;
    private final File dataFile;
    private FileConfiguration dataConfig;

    private int tickIntervalSeconds;
    private int defaultSlots;

    public GeneratorManager(GenSprout plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "generator_tier");
        this.dropValueKey = new NamespacedKey(plugin, "drop_value");
        this.dataFile = new File(plugin.getDataFolder(), "data/generators.yml");
        
        loadConfigTiers();
        loadGenerators();
        startTicking();
    }

    private void loadConfigTiers() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("generators.tiers");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    int tier = Integer.parseInt(key);
                    String name = sec.getString(key + ".display-name");
                    Material blockType = Material.valueOf(sec.getString(key + ".block-type").toUpperCase());
                    String dropMatStr = sec.getString(key + ".drop-material");
                    Material dropMaterial;
                    if (dropMatStr != null && !dropMatStr.isEmpty()) {
                        dropMaterial = Material.valueOf(dropMatStr.toUpperCase());
                    } else {
                        dropMaterial = blockType;
                    }
                    double buyPrice = sec.getDouble(key + ".buy-price");
                    double upgradePrice = sec.getDouble(key + ".upgrade-price");
                    double dropValue = sec.getDouble(key + ".drop-value");
                    String dropName = sec.getString(key + ".drop-name");

                    GeneratorType type = new GeneratorType(tier, name, blockType, dropMaterial, buyPrice, upgradePrice, dropValue, dropName);
                    generatorTiers.put(tier, type);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not load generator tier " + key + " in config.yml!", e);
                }
            }
        }
        this.tickIntervalSeconds = plugin.getConfig().getInt("generators.tick-interval-seconds", 10);
        this.defaultSlots = plugin.getConfig().getInt("generators.default-slots", 20);
    }

    private void loadGenerators() {
        if (!dataFile.exists()) {
            dataFile.getParentFile().mkdirs();
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create generators.yml!", e);
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection sec = dataConfig.getConfigurationSection("placed");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    String worldName = sec.getString(key + ".world");
                    World world = Bukkit.getWorld(worldName);
                    if (world == null) continue; // World not loaded yet or deleted

                    double x = sec.getDouble(key + ".x");
                    double y = sec.getDouble(key + ".y");
                    double z = sec.getDouble(key + ".z");
                    Location loc = new Location(world, x, y, z);

                    UUID owner = UUID.fromString(sec.getString(key + ".owner"));
                    int tier = sec.getInt(key + ".tier");
                    long lastHarvest = sec.getLong(key + ".last-harvest");
                    int drops = sec.getInt(key + ".drops");

                    GeneratorBlock gen = new GeneratorBlock(loc, owner, tier, lastHarvest, drops);
                    placedGenerators.put(loc, gen);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not load placed generator " + key + "!", e);
                }
            }
        }
    }

    public void saveGenerators() {
        dataConfig.set("placed", null); // Clear existing
        int index = 0;
        long now = System.currentTimeMillis();
        for (Map.Entry<Location, GeneratorBlock> entry : placedGenerators.entrySet()) {
            Location loc = entry.getKey();
            GeneratorBlock gen = entry.getValue();

            // Lazy catch-up to ensure values are updated before serialization
            int offlineDrops = gen.calculateCatchUp(now, tickIntervalSeconds);
            if (offlineDrops > 0) {
                spawnPhysicalDrops(loc, gen.getTier(), offlineDrops);
            }

            String path = "placed.gen_" + index;
            dataConfig.set(path + ".world", loc.getWorld().getName());
            dataConfig.set(path + ".x", loc.getX());
            dataConfig.set(path + ".y", loc.getY());
            dataConfig.set(path + ".z", loc.getZ());
            dataConfig.set(path + ".owner", gen.getOwnerUuid().toString());
            dataConfig.set(path + ".tier", gen.getTier());
            dataConfig.set(path + ".last-harvest", gen.getLastHarvestTime());
            dataConfig.set(path + ".drops", 0);
            index++;
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save generators.yml!", e);
        }
    }

    public void reload() {
        plugin.reloadConfig();
        generatorTiers.clear();
        loadConfigTiers();
    }

    private void startTicking() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (GeneratorBlock gen : placedGenerators.values()) {
                Location loc = gen.getLocation();
                World world = loc.getWorld();
                if (world == null) continue;

                // Optimization: Paper-specific O(1) chunk loaded check (doesn't load chunks)
                int chunkX = loc.getBlockX() >> 4;
                int chunkZ = loc.getBlockZ() >> 4;
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    // Update drops and last active timestamp
                    int catchUp = gen.calculateCatchUp(now, tickIntervalSeconds);
                    if (catchUp > 0) {
                        spawnPhysicalDrops(loc, gen.getTier(), catchUp);
                    }
                    spawnPhysicalDrops(loc, gen.getTier(), 1);
                    gen.setLastHarvestTime(now);

                    // Premium particle effect
                    world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.2, 0.5), 3, 0.2, 0.1, 0.2, 0.02);
                }
            }
        }, 200L, tickIntervalSeconds * 20L); // Run every X seconds
    }

    public void spawnPhysicalDrops(Location loc, int tier, int amount) {
        ItemStack dropItem = createDropItem(tier);
        if (dropItem == null) return;

        Location spawnLoc = loc.clone().add(0.5, 1.1, 0.5);
        int remaining = amount;
        while (remaining > 0) {
            int currentBatch = Math.min(remaining, 64);
            ItemStack stack = dropItem.clone();
            stack.setAmount(currentBatch);
            loc.getWorld().dropItem(spawnLoc, stack);
            remaining -= currentBatch;
        }
    }

    public ItemStack createDropItem(int tier) {
        GeneratorType type = getTierConfig(tier);
        if (type == null) return null;

        Material mat = type.getDropMaterial();
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize(type.getDropName()));
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.getMiniMessage().deserialize("<gray>Generator Drop</gray>"));
            lore.add(plugin.getMiniMessage().deserialize("<gray>Sell Value: <green>" + plugin.getEconomyHook().format(type.getDropValue()) + "</green></gray>"));
            meta.lore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(dropValueKey, PersistentDataType.DOUBLE, type.getDropValue());
            item.setItemMeta(meta);
        }
        return item;
    }

    public Double getDropValueFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(dropValueKey, PersistentDataType.DOUBLE)) {
            return pdc.get(dropValueKey, PersistentDataType.DOUBLE);
        }
        return null;
    }

    public GeneratorType getTierConfig(int tier) {
        return generatorTiers.get(tier);
    }

    public GeneratorBlock getGenerator(Location loc) {
        GeneratorBlock gen = placedGenerators.get(loc);
        if (gen != null) {
            // Lazy catch-up: spawn physical drops for offline duration
            long now = System.currentTimeMillis();
            int offlineDrops = gen.calculateCatchUp(now, tickIntervalSeconds);
            if (offlineDrops > 0) {
                spawnPhysicalDrops(loc, gen.getTier(), offlineDrops);
            }
            gen.setLastHarvestTime(now);
        }
        return gen;
    }

    public Map<Location, GeneratorBlock> getPlacedGenerators() {
        return placedGenerators;
    }

    public int getActiveCount(UUID ownerUuid) {
        int count = 0;
        for (GeneratorBlock gen : placedGenerators.values()) {
            if (gen.getOwnerUuid().equals(ownerUuid)) {
                count++;
            }
        }
        return count;
    }

    public boolean placeGenerator(Location loc, UUID ownerUuid, int tier) {
        GeneratorType type = getTierConfig(tier);
        if (type == null) return false;

        GeneratorBlock gen = new GeneratorBlock(loc, ownerUuid, tier);
        placedGenerators.put(loc, gen);
        
        // Change block in the world
        loc.getBlock().setType(type.getBlockType());
        return true;
    }

    public boolean removeGenerator(Location loc) {
        GeneratorBlock gen = placedGenerators.remove(loc);
        if (gen != null) {
            loc.getBlock().setType(Material.AIR);
            return true;
        }
        return false;
    }

    public void giveGenerator(Player player, int tier, int amount) {
        ItemStack genItem = createGeneratorItem(tier, amount);
        player.getInventory().addItem(genItem).forEach((index, item) -> {
            // Drop on floor if inventory full
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        });
    }

    public ItemStack createGeneratorItem(int tier, int amount) {
        GeneratorType type = getTierConfig(tier);
        if (type == null) return null;

        ItemStack item = new ItemStack(type.getBlockType(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(plugin.getMiniMessage().deserialize(type.getDisplayName()));
            
            List<Component> lore = new ArrayList<>();
            lore.add(plugin.getMiniMessage().deserialize("<gray>Place this generator block to earn income.</gray>"));
            lore.add(plugin.getMiniMessage().deserialize("<gray>Tier: <gold>" + tier + "</gold></gray>"));
            lore.add(plugin.getMiniMessage().deserialize("<gray>Income per drop: <green>" + plugin.getEconomyHook().format(type.getDropValue()) + "</green></gray>"));
            meta.lore(lore);

            // Save the tier metadata inside PDC
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(tierKey, PersistentDataType.INTEGER, tier);
            
            item.setItemMeta(meta);
        }
        return item;
    }

    public Integer getGeneratorTierFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(tierKey, PersistentDataType.INTEGER)) {
            return pdc.get(tierKey, PersistentDataType.INTEGER);
        }
        return null;
    }

    public int getDefaultSlots() {
        return defaultSlots;
    }

    public int getTickIntervalSeconds() {
        return tickIntervalSeconds;
    }
}
