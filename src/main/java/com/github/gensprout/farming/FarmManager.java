package com.github.gensprout.farming;

import com.github.gensprout.GenSprout;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.type.Farmland;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class FarmManager {

    private final GenSprout plugin;
    private final NamespacedKey selectorKey;
    private final File dataFile;
    private FileConfiguration dataConfig;

    private FarmRegion activeRegion = null;
    private final Map<UUID, Location[]> selectionCache = new ConcurrentHashMap<>();

    public FarmManager(GenSprout plugin) {
        this.plugin = plugin;
        this.selectorKey = new NamespacedKey(plugin, "selector_tool");
        this.dataFile = new File(plugin.getGenSproutDataFolder(), "farm.yml");
        loadFarmRegion();
    }

    public void loadFarmRegion() {
        if (!dataFile.exists()) {
            return; // No farm defined yet
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        if (dataConfig.contains("farm")) {
            String world = dataConfig.getString("farm.world");
            int x1 = dataConfig.getInt("farm.x1");
            int y1 = dataConfig.getInt("farm.y1");
            int z1 = dataConfig.getInt("farm.z1");
            int x2 = dataConfig.getInt("farm.x2");
            int y2 = dataConfig.getInt("farm.y2");
            int z2 = dataConfig.getInt("farm.z2");
            activeRegion = new FarmRegion(world, x1, y1, z1, x2, y2, z2);
        }
    }

    public void saveFarmRegion(FarmRegion region) {
        this.activeRegion = region;
        if (dataConfig == null) {
            dataConfig = new YamlConfiguration();
        }

        dataConfig.set("farm.world", region.getWorldName());
        dataConfig.set("farm.x1", region.getMinX());
        dataConfig.set("farm.y1", region.getMinY());
        dataConfig.set("farm.z1", region.getMinZ());
        dataConfig.set("farm.x2", region.getMaxX());
        dataConfig.set("farm.y2", region.getMaxY());
        dataConfig.set("farm.z2", region.getMaxZ());

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save farm.yml!", e);
        }

        populateFarmRegion(region);
    }

    private void populateFarmRegion(FarmRegion region) {
        org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
        if (world == null) return;

        // Block data presets
        Farmland farmlandData = (Farmland) Material.FARMLAND.createBlockData();
        farmlandData.setMoisture(farmlandData.getMaximumMoisture());

        Ageable cropData = (Ageable) Material.WHEAT.createBlockData();
        cropData.setAge(cropData.getMaximumAge());

        int minX = region.getMinX();
        int maxX = region.getMaxX();
        int minZ = region.getMinZ();
        int maxZ = region.getMaxZ();
        int minY = region.getMinY();
        int maxY = region.getMaxY();

        // Loop and set blocks
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                // Set bottom-most block (minY) to Farmland
                world.getBlockAt(x, minY, z).setBlockData(farmlandData, false);

                // Always set block above it (minY + 1) to fully grown Wheat crop
                world.getBlockAt(x, minY + 1, z).setBlockData(cropData, false);

                // Set any blocks above (minY + 2 to maxY) to Air
                for (int y = minY + 2; y <= maxY; y++) {
                    world.getBlockAt(x, y, z).setType(Material.AIR, false);
                }
            }
        }
    }

    public FarmRegion getActiveRegion() {
        return activeRegion;
    }

    public void setPos1(UUID uuid, Location loc) {
        Location[] sel = selectionCache.computeIfAbsent(uuid, k -> new Location[2]);
        sel[0] = loc;
    }

    public void setPos2(UUID uuid, Location loc) {
        Location[] sel = selectionCache.computeIfAbsent(uuid, k -> new Location[2]);
        sel[1] = loc;
    }

    public Location[] getSelection(UUID uuid) {
        return selectionCache.get(uuid);
    }

    public ItemStack createSelectorStick() {
        return createSelectorStick(null);
    }

    public void rebuildSelectorStickLore(ItemStack item, org.bukkit.entity.Player viewer) {
        if (!isSelectorStick(item)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.displayName(plugin.getLanguageManager().getComponent("items.selector.name", viewer)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        List<Component> rawLore = plugin.getLanguageManager().getComponentList("items.selector.lore", viewer);
        List<Component> finalLore = new ArrayList<>();
        for (Component c : rawLore) {
            finalLore.add(c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
        finalLore.add(plugin.getLanguageManager().getComponent("items.selector.lore-savefarm", viewer,
                com.github.gensprout.lang.LanguageManager.values("command", mainCmd))
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(finalLore);
        item.setItemMeta(meta);
    }

    public ItemStack createSelectorStick(org.bukkit.entity.Player viewer) {
        ItemStack stick = new ItemStack(Material.STICK);
        ItemMeta meta = stick.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(selectorKey, PersistentDataType.BYTE, (byte) 1);
            stick.setItemMeta(meta);
        }
        rebuildSelectorStickLore(stick, viewer);
        return stick;
    }

    public boolean isSelectorStick(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(selectorKey, PersistentDataType.BYTE);
    }
}
