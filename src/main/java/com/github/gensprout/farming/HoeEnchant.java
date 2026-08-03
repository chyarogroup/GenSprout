package com.github.gensprout.farming;

import com.github.gensprout.GenSprout;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public enum HoeEnchant {
    XP_BOOSTER("xp_booster", "<yellow>XP Booster</yellow>", 5),
    ESSENCE_FINDER("essence_finder", "<light_purple>Essence Finder</light_purple>", 5),
    CROP_DOUBLER("crop_doubler", "<green>Crop Doubler</green>", 5),
    REPLENISH("replenish", "<aqua>Replenish</aqua>", 1),
    HARVEST_AREA("harvest_area", "<gold>Harvest Area</gold>", 4),
    REACH("reach", "<blue>Reach</blue>", 5),
    AUTO_SELL("auto_sell", "<gold>Auto-Sell (Crops Only)</gold>", 1);

    private final String configKey;
    private final String rawDisplayName;
    private final int maxLevel;

    HoeEnchant(String configKey, String rawDisplayName, int maxLevel) {
        this.configKey = configKey;
        this.rawDisplayName = rawDisplayName;
        this.maxLevel = maxLevel;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getRawDisplayName() {
        return rawDisplayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public NamespacedKey getPdcKey(GenSprout plugin) {
        return new NamespacedKey(plugin, "enchant_" + name().toLowerCase());
    }

    /**
     * Get the level of this enchant on the given item. Returns 0 if not present.
     */
    public int getLevel(ItemStack item, GenSprout plugin) {
        if (item == null || !item.hasItemMeta()) return 0;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey key = getPdcKey(plugin);
        return pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    /**
     * Set the level of this enchant on the given item. Removes it if level is 0.
     */
    public void setLevel(ItemStack item, int level, GenSprout plugin) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey key = getPdcKey(plugin);
        
        if (level > 0) {
            pdc.set(key, PersistentDataType.INTEGER, level);
        } else {
            pdc.remove(key);
        }

        if (this == REACH) {
            NamespacedKey attrKey = new NamespacedKey(plugin, "hoe_reach");
            meta.removeAttributeModifier(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE, new org.bukkit.attribute.AttributeModifier(attrKey, 0.0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND));
            if (level > 0) {
                org.bukkit.attribute.AttributeModifier modifier = new org.bukkit.attribute.AttributeModifier(
                    attrKey,
                    (double) level,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlotGroup.MAINHAND
                );
                meta.addAttributeModifier(org.bukkit.attribute.Attribute.BLOCK_INTERACTION_RANGE, modifier);
            }
        }

        item.setItemMeta(meta);
        rebuildLore(item, plugin);
    }

    /**
     * Parse roman numerals for levels 1 to 5.
     */
    private static String getRomanNumeral(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    /**
     * Reads all enchants from the PDC, and updates the lore of the hoe.
     */
    public static void rebuildLore(ItemStack item, GenSprout plugin) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        
        // Mark this as a custom hoe
        NamespacedKey mainHoeKey = new NamespacedKey(plugin, "sprout_hoe");
        meta.getPersistentDataContainer().set(mainHoeKey, PersistentDataType.BYTE, (byte) 1);
        
        meta.displayName(plugin.getMiniMessage().deserialize("<gradient:green:aqua><bold>Sprout Hoe</bold></gradient>"));

        List<Component> lore = new ArrayList<>();
        lore.add(plugin.getMiniMessage().deserialize("<gray>A specialized farming hoe upgraded with essence.</gray>"));
        lore.add(Component.empty());
        lore.add(plugin.getMiniMessage().deserialize("<dark_aqua><bold>Custom Enchantments:</bold></dark_aqua>"));

        boolean hasEnchants = false;
        for (HoeEnchant enchant : HoeEnchant.values()) {
            int lvl = enchant.getLevel(item, plugin);
            if (lvl > 0) {
                String displayName = enchant.getRawDisplayName();
                if (enchant == HARVEST_AREA) {
                    String dim = switch (lvl) {
                        case 1 -> " (1x2)";
                        case 2 -> " (2x2)";
                        case 3 -> " (3x2)";
                        default -> " (3x3)";
                    };
                    displayName = "<gold>Harvest Area" + dim + "</gold>";
                }
                lore.add(plugin.getMiniMessage().deserialize(" <gray>•</gray> " + displayName + " <gold>" + getRomanNumeral(lvl) + "</gold>"));
                hasEnchants = true;
            }
        }
        if (!hasEnchants) {
            lore.add(plugin.getMiniMessage().deserialize(" <gray><i>None (Right click to enchant)</i></gray>"));
        }

        lore.add(Component.empty());
        lore.add(plugin.getMiniMessage().deserialize("<gray>Right click in the air to open the upgrade shop.</gray>"));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Creates a fresh Sprout Hoe item (used for the starter kit and the in-shop purchase).
     */
    public static ItemStack createBaseHoe(GenSprout plugin) {
        ItemStack hoe = new ItemStack(Material.NETHERITE_HOE);
        rebuildLore(hoe, plugin);
        return hoe;
    }

    /**
     * Check if the item is a Sprout Hoe.
     */
    public static boolean isSproutHoe(ItemStack item, GenSprout plugin) {
        if (item == null || !item.hasItemMeta()) return false;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        NamespacedKey mainHoeKey = new NamespacedKey(plugin, "sprout_hoe");
        return pdc.has(mainHoeKey, PersistentDataType.BYTE);
    }
}
