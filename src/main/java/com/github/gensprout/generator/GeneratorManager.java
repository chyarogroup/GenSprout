package com.github.gensprout.generator;

import com.github.gensprout.GenSprout;
import com.github.gensprout.lang.LanguageManager;
import com.github.gensprout.util.BlockPos;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
    private final Map<BlockPos, GeneratorBlock> placedGenerators = new ConcurrentHashMap<>();
    private final Map<Integer, GeneratorType> generatorTiers = new ConcurrentHashMap<>();
    private final NamespacedKey tierKey;
    private final NamespacedKey dropValueKey;
    private final NamespacedKey dropOwnerKey;
    private final File dataFile;
    private FileConfiguration dataConfig;

    private int tickIntervalSeconds;
    private int defaultSlots;
    private boolean isDirty = false;

    public GeneratorManager(GenSprout plugin) {
        this.plugin = plugin;
        this.tierKey = new NamespacedKey(plugin, "generator_tier");
        this.dropValueKey = new NamespacedKey(plugin, "drop_value");
        this.dropOwnerKey = new NamespacedKey(plugin, "generator_owner");
        this.dataFile = new File(plugin.getGenSproutDataFolder(), "generators.yml");
        
        loadConfigTiers();
        loadGenerators();
        startTicking();
        startAutoSaveTask();
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
                    double buyPrice = computeTierBuyPrice(tier);
                    double upgradePrice = buyPrice * plugin.getConfig().getDouble("generators.tier-economy.upgrade-price-ratio", 0.72);
                    double dropValue = computeTierDropValue(tier);
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

    private double computeTierBuyPrice(int tier) {
        FileConfiguration config = plugin.getConfig();
        double base = config.getDouble("generators.tier-economy.base-buy-price", 250.0);
        int earlyTiers = Math.max(1, config.getInt("generators.tier-economy.early-tiers", 5));
        double earlyStep = config.getDouble("generators.tier-economy.early-step", 250.0);
        int midThreshold = Math.max(earlyTiers, config.getInt("generators.tier-economy.mid-tier-threshold", 25));
        double midGrowth = config.getDouble("generators.tier-economy.mid-growth-rate", 1.28);
        double lateGrowth = config.getDouble("generators.tier-economy.late-growth-rate", 1.32);

        if (tier <= earlyTiers) {
            return base + ((tier - 1) * earlyStep);
        }

        double priceAtEarly = base + ((earlyTiers - 1) * earlyStep);
        if (tier <= midThreshold) {
            return priceAtEarly * Math.pow(midGrowth, tier - earlyTiers);
        }

        double priceAtMid = priceAtEarly * Math.pow(midGrowth, midThreshold - earlyTiers);
        return priceAtMid * Math.pow(lateGrowth, tier - midThreshold);
    }

    private double computeTierDropValue(int tier) {
        FileConfiguration config = plugin.getConfig();
        double base = config.getDouble("generators.drop-value-economy.base-drop-value", 2.0);
        int earlyTiers = Math.max(1, config.getInt("generators.drop-value-economy.early-tiers", 5));
        double earlyStep = config.getDouble("generators.drop-value-economy.early-step", 2.0);
        int midThreshold = Math.max(earlyTiers, config.getInt("generators.drop-value-economy.mid-tier-threshold", 25));
        double midGrowth = config.getDouble("generators.drop-value-economy.mid-growth-rate", 1.18);
        double lateGrowth = config.getDouble("generators.drop-value-economy.late-growth-rate", 1.20);
        double cap = config.getDouble("generators.drop-value-economy.max-drop-value-cap", 30000.0);

        double value;
        if (tier <= earlyTiers) {
            value = base + ((tier - 1) * earlyStep);
        } else {
            double valueAtEarly = base + ((earlyTiers - 1) * earlyStep);
            if (tier <= midThreshold) {
                value = valueAtEarly * Math.pow(midGrowth, tier - earlyTiers);
            } else {
                double valueAtMid = valueAtEarly * Math.pow(midGrowth, midThreshold - earlyTiers);
                value = valueAtMid * Math.pow(lateGrowth, tier - midThreshold);
            }
        }
        return Math.min(value, cap);
    }
    public void loadGenerators() {
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
                    BlockPos pos = BlockPos.fromLocation(loc);

                    UUID owner = UUID.fromString(sec.getString(key + ".owner"));
                    int tier = sec.getInt(key + ".tier");
                    long lastHarvest = sec.getLong(key + ".last-harvest");
                    int drops = sec.getInt(key + ".drops");

                    GeneratorBlock gen = new GeneratorBlock(loc, owner, tier, lastHarvest, drops);
                    placedGenerators.put(pos, gen);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not load placed generator " + key + "!", e);
                }
            }
        }
    }

    private void startAutoSaveTask() {
        // Auto-save generators data asynchronously every 5 minutes (6000 ticks)
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveGeneratorsToFile, 6000L, 6000L);
    }

    public void saveGenerators() {
        isDirty = true;
    }

    public synchronized void saveGeneratorsToFile() {
        if (!isDirty) return;
        
        synchronized (dataFile) {
            dataConfig.set("placed", null); // Clear existing
            int index = 0;
            for (Map.Entry<BlockPos, GeneratorBlock> entry : placedGenerators.entrySet()) {
                BlockPos pos = entry.getKey();
                GeneratorBlock gen = entry.getValue();

                String path = "placed.gen_" + index;
                dataConfig.set(path + ".world", pos.world());
                dataConfig.set(path + ".x", pos.x());
                dataConfig.set(path + ".y", pos.y());
                dataConfig.set(path + ".z", pos.z());
                dataConfig.set(path + ".owner", gen.getOwnerUuid().toString());
                dataConfig.set(path + ".tier", gen.getTier());
                dataConfig.set(path + ".last-harvest", gen.getLastHarvestTime());
                dataConfig.set(path + ".drops", 0);
                index++;
            }

            try {
                dataConfig.save(dataFile);
                isDirty = false;
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save generators.yml!", e);
            }
        }
    }

    public void saveGeneratorsSync() {
        saveGeneratorsToFile();
    }

    public void reload() {
        plugin.reloadConfig();
        generatorTiers.clear();
        loadConfigTiers();
    }

    private void startTicking() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<BlockPos, GeneratorBlock>> iterator = placedGenerators.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<BlockPos, GeneratorBlock> entry = iterator.next();
                BlockPos pos = entry.getKey();
                GeneratorBlock gen = entry.getValue();
                Location loc = gen.getLocation();
                World world = loc.getWorld();
                if (world == null) continue;

                // Generators only MAKE live drops when the generator owner is ONLINE
                Player owner = Bukkit.getPlayer(gen.getOwnerUuid());
                if (owner == null || !owner.isOnline()) {
                    continue; // Owner is offline: DO NOT SPAWN LIVE DROPS (20% offline generation is handled on join)
                }

                int chunkX = pos.x() >> 4;
                int chunkZ = pos.z() >> 4;
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    continue;
                }

                GeneratorType type = getTierConfig(gen.getTier());
                if (type == null) {
                    iterator.remove();
                    saveGenerators();
                    continue;
                }

                if (loc.getBlock().getType() != type.getBlockType()) {
                    if (loc.getBlock().getType() != Material.AIR) {
                        // Seamlessly migrate physical block to the updated tier block type
                        loc.getBlock().setType(type.getBlockType());
                    } else {
                        iterator.remove();
                        saveGenerators();
                        continue;
                    }
                }

                if (!isBelowItemCap(world, loc)) {
                    continue;
                }

                spawnPhysicalDrops(loc, gen.getTier(), 1, gen.getOwnerUuid());
                gen.setLastHarvestTime(now);

                world.spawnParticle(Particle.HAPPY_VILLAGER, loc.clone().add(0.5, 1.2, 0.5), 3, 0.2, 0.1, 0.2, 0.02);
            }
        }, 200L, tickIntervalSeconds * 20L);
    }

    private boolean isBelowItemCap(World world, Location loc) {
        int maxItems = plugin.getConfig().getInt("generators.max-nearby-items", 100);
        if (maxItems <= 0) return true;

        double radius = plugin.getConfig().getDouble("generators.item-check-radius", 12);
        Collection<org.bukkit.entity.Entity> nearbyItems = world.getNearbyEntities(loc, radius, radius, radius, e -> e instanceof org.bukkit.entity.Item);
        return nearbyItems.size() < maxItems;
    }

    public void spawnPhysicalDrops(Location loc, int tier, int amount) {
        spawnPhysicalDrops(loc, tier, amount, null);
    }

    public void spawnPhysicalDrops(Location loc, int tier, int amount, UUID ownerUuid) {
        ItemStack dropItem = createDropItem(tier, ownerUuid);
        if (dropItem == null) return;

        Location spawnLoc = loc.getBlock().getLocation().add(0.5, 1.05, 0.5);
        int remaining = amount;
        while (remaining > 0) {
            int currentBatch = Math.min(remaining, 64);
            ItemStack stack = dropItem.clone();
            stack.setAmount(currentBatch);
            org.bukkit.entity.Item itemEntity = loc.getWorld().dropItem(spawnLoc, stack);
            itemEntity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            itemEntity.teleport(spawnLoc);
            if (ownerUuid != null) {
                try {
                    itemEntity.setOwner(ownerUuid);
                } catch (Throwable ignored) {}
                itemEntity.getPersistentDataContainer().set(dropOwnerKey, PersistentDataType.STRING, ownerUuid.toString());
            }
            remaining -= currentBatch;
        }
    }

    public ItemStack createDropItem(int tier) {
        return createDropItem(tier, null);
    }

    public ItemStack createDropItem(int tier, UUID ownerUuid) {
        GeneratorType type = getTierConfig(tier);
        if (type == null) return null;

        Material mat = type.getDropMaterial();
        ItemStack item = new ItemStack(mat, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String priceLabel = com.github.gensprout.economy.EconomyHook.format(type.getDropValue());
            // Drop items exist in the world for all players to see, so they render in default language (null viewer).
            Component name = plugin.getMiniMessage().deserialize("<white>" + type.getDropName() + "</white> <green>" + priceLabel + "</green>")
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(name);

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.getLanguageManager().getComponent("items.generator-drop.lore-label", null)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(plugin.getLanguageManager().getComponent("items.generator-drop.lore-value", null, LanguageManager.values("value", priceLabel))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);

            meta.setEnchantmentGlintOverride(true);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(tierKey, PersistentDataType.INTEGER, tier);
            pdc.set(dropValueKey, PersistentDataType.DOUBLE, type.getDropValue());
            if (ownerUuid != null) {
                pdc.set(dropOwnerKey, PersistentDataType.STRING, ownerUuid.toString());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public Double getDropValueFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (isGeneratorItem(item)) {
            return null; // Placeable generator blocks cannot be sold
        }
        if (pdc.has(dropValueKey, PersistentDataType.DOUBLE)) {
            return pdc.get(dropValueKey, PersistentDataType.DOUBLE);
        }
        if (pdc.has(tierKey, PersistentDataType.INTEGER)) {
            int tier = pdc.get(tierKey, PersistentDataType.INTEGER);
            GeneratorType type = getTierConfig(tier);
            if (type != null) {
                return type.getDropValue();
            }
        }
        return null;
    }

    public GeneratorType getTierConfig(int tier) {
        return generatorTiers.get(tier);
    }

    public int getMaxTier() {
        if (generatorTiers.isEmpty()) return 25;
        return Collections.max(generatorTiers.keySet());
    }

    public GeneratorBlock getGenerator(Location loc) {
        if (loc == null) return null;
        return placedGenerators.get(BlockPos.fromLocation(loc));
    }

    public GeneratorBlock getGenerator(BlockPos pos) {
        if (pos == null) return null;
        return placedGenerators.get(pos);
    }

    public Map<Location, GeneratorBlock> getPlacedGenerators() {
        Map<Location, GeneratorBlock> map = new HashMap<>();
        for (Map.Entry<BlockPos, GeneratorBlock> entry : placedGenerators.entrySet()) {
            Location loc = entry.getKey().toLocation();
            if (loc != null) {
                map.put(loc, entry.getValue());
            }
        }
        return map;
    }

    public Map<BlockPos, GeneratorBlock> getPlacedGeneratorBlocks() {
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
        placedGenerators.put(BlockPos.fromLocation(loc), gen);
        saveGenerators();
        
        loc.getBlock().setType(type.getBlockType());
        return true;
    }

    public boolean removeGenerator(Location loc) {
        GeneratorBlock gen = placedGenerators.remove(BlockPos.fromLocation(loc));
        if (gen != null) {
            saveGenerators();
            loc.getBlock().setType(Material.AIR);
            return true;
        }
        return false;
    }

    public void giveGenerator(Player player, int tier, int amount) {
        ItemStack genItem = createGeneratorItem(tier, amount, player);
        player.getInventory().addItem(genItem).forEach((index, item) -> {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
        });
    }

    public boolean isGeneratorItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(tierKey, PersistentDataType.INTEGER) && !pdc.has(dropValueKey, PersistentDataType.DOUBLE);
    }

    public boolean isGeneratorDrop(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(dropValueKey, PersistentDataType.DOUBLE);
    }

    public void rebuildGeneratorLore(ItemStack item, Player viewer) {
        if (!isGeneratorItem(item)) return;
        Integer tier = getGeneratorTierFromItem(item);
        if (tier == null) return;
        GeneratorType type = getTierConfig(tier);
        if (type == null) return;

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component nameComp = plugin.getLanguageManager().renderRaw(type.getDisplayName(), viewer, null)
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(nameComp);

            String formattedVal = com.github.gensprout.economy.EconomyHook.format(type.getDropValue());

            List<Component> lore = new ArrayList<>();
            lore.add(plugin.getLanguageManager().getComponent("items.generator.lore-description", viewer)
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(plugin.getLanguageManager().getComponent("items.generator.lore-tier", viewer,
                    LanguageManager.values("tier", String.valueOf(tier)))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(plugin.getLanguageManager().getComponent("items.generator.lore-income", viewer,
                    LanguageManager.values("value", formattedVal))
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);

            item.setItemMeta(meta);
        }
    }

    public ItemStack createGeneratorItem(int tier, int amount) {
        return createGeneratorItem(tier, amount, null);
    }

    public ItemStack createGeneratorItem(int tier, int amount, Player viewer) {
        GeneratorType type = getTierConfig(tier);
        if (type == null) return null;

        ItemStack item = new ItemStack(type.getBlockType(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(tierKey, PersistentDataType.INTEGER, tier);
            item.setItemMeta(meta);
        }
        rebuildGeneratorLore(item, viewer);
        return item;
    }

    public Integer getGeneratorTierFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (pdc.has(tierKey, PersistentDataType.INTEGER) && !pdc.has(dropValueKey, PersistentDataType.DOUBLE)) {
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
