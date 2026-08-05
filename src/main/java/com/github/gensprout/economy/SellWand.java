package com.github.gensprout.economy;

import com.github.gensprout.GenSprout;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Sell Wand item: right-click to instantly sell every sellable item in your inventory,
 * with a configurable bonus multiplier. Only a single 1x tier ships by default, but the
 * system reads all tiers from config.yml under 'sellwand.multipliers' so more can be added
 * later without any code changes.
 */
public class SellWand {

    private SellWand() {
    }

    public static NamespacedKey tierKey(GenSprout plugin) {
        return new NamespacedKey(plugin, "sellwand_tier");
    }

    public static NamespacedKey multiplierKey(GenSprout plugin) {
        return new NamespacedKey(plugin, "sellwand_multiplier");
    }

    public static boolean isSellWand(ItemStack item, GenSprout plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(tierKey(plugin), PersistentDataType.INTEGER);
    }

    public static double getMultiplier(ItemStack item, GenSprout plugin) {
        if (item == null || !item.hasItemMeta()) return 1.0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.getOrDefault(multiplierKey(plugin), PersistentDataType.DOUBLE, 1.0);
    }

    /**
     * Returns all Sell Wand tiers currently defined in config.yml (sorted ascending).
     * For now this will just return [1], but scales automatically if more tiers are added.
     */
    public static List<Integer> getAvailableTiers(GenSprout plugin) {
        List<Integer> tiers = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("sellwand.multipliers");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    tiers.add(Integer.parseInt(key));
                } catch (NumberFormatException ignored) {
                    // Skip malformed tier keys
                }
            }
        }
        tiers.sort(Integer::compareTo);
        return tiers;
    }

    public static double getMultiplierForTier(GenSprout plugin, int tier) {
        return plugin.getConfig().getDouble("sellwand.multipliers." + tier + ".multiplier", 1.0);
    }

    public static double getPriceForTier(GenSprout plugin, int tier) {
        return plugin.getConfig().getDouble("sellwand.multipliers." + tier + ".price", 2500.0);
    }

    public static int getEssenceCostForTier(GenSprout plugin, int tier) {
        return plugin.getConfig().getInt("sellwand.multipliers." + tier + ".essence-cost", 2500 * tier);
    }

    public static String getDisplayNameForTier(GenSprout plugin, int tier) {
        return plugin.getConfig().getString("sellwand.multipliers." + tier + ".display-name", "<green>Sell Wand</green>");
    }

    public static void rebuildLore(ItemStack item, GenSprout plugin, org.bukkit.entity.Player viewer) {
        if (!isSellWand(item, plugin)) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int tier = pdc.getOrDefault(tierKey(plugin), PersistentDataType.INTEGER, 1);
        double multiplier = getMultiplierForTier(plugin, tier);
        String rawName = getDisplayNameForTier(plugin, tier);

        meta.displayName(plugin.getLanguageManager().renderRaw(rawName, viewer, null).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getLanguageManager().getComponent("items.sellwand.lore-line1", viewer).decoration(TextDecoration.ITALIC, false));
        lore.add(plugin.getLanguageManager().getComponent("items.sellwand.lore-line2", viewer).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(plugin.getLanguageManager().getComponent("items.sellwand.lore-line3", viewer).decoration(TextDecoration.ITALIC, false));
        lore.add(plugin.getLanguageManager().getComponent("items.sellwand.lore-line4", viewer).decoration(TextDecoration.ITALIC, false));
        lore.add(plugin.getLanguageManager().getComponent("items.sellwand.lore-line5", viewer).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(plugin.getLanguageManager().getComponent("items.sellwand.lore-multiplier", viewer,
                com.github.gensprout.lang.LanguageManager.values("multiplier", String.format("%.1f", multiplier)))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    public static ItemStack createSellWand(GenSprout plugin, int tier) {
        return createSellWand(plugin, tier, null);
    }

    public static ItemStack createSellWand(GenSprout plugin, int tier, org.bukkit.entity.Player viewer) {
        double multiplier = getMultiplierForTier(plugin, tier);

        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(tierKey(plugin), PersistentDataType.INTEGER, tier);
            pdc.set(multiplierKey(plugin), PersistentDataType.DOUBLE, multiplier);
            item.setItemMeta(meta);
        }
        rebuildLore(item, plugin, viewer);
        return item;
    }

    /**
     * Deserializes a lore line with italics explicitly disabled (Minecraft otherwise renders
     * custom item names/lore in italics by default).
     */
    private static Component plainLore(GenSprout plugin, String raw) {
        return plugin.getMiniMessage().deserialize(raw).decoration(TextDecoration.ITALIC, false);
    }
}
