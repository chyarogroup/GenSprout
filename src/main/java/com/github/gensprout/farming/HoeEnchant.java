package com.github.gensprout.farming;

import com.github.gensprout.GenSprout;
import com.github.gensprout.lang.LanguageManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public enum HoeEnchant {
    XP_BOOSTER("xp_booster", "<yellow>XP Booster</yellow>", 500),
    ESSENCE_FINDER("essence_finder", "<light_purple>Essence Finder</light_purple>", 500),
    CROP_DOUBLER("crop_doubler", "<green>Crop Doubler</green>", 500),
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
     * The translated name of this enchant as raw MiniMessage, so it can be embedded into the lore
     * entry line with its colours intact.
     *
     * @param viewer the player the item is being built for, or null to use the server default
     * @param level  used only by {@link #HARVEST_AREA} to fill in the area dimensions
     */
    public String getDisplayName(GenSprout plugin, Player viewer, int level) {
        String raw = plugin.getLanguageManager().getMessageString("items.hoe.enchant." + configKey, viewer);
        if (raw == null) {
            raw = rawDisplayName;
        }
        String dimension = this == HARVEST_AREA ? "(" + getAreaDimension(level) + ")" : "";
        return raw.replace("<dimension>", dimension).replace("{dimension}", dimension);
    }

    /** The harvested block area unlocked at the given Harvest Area level. */
    public static String getAreaDimension(int level) {
        return switch (level) {
            case 1 -> "1x2";
            case 2 -> "2x2";
            case 3 -> "3x2";
            case 0 -> "1x1";
            default -> "3x3";
        };
    }

    /**
     * Set the level of this enchant on the given item. Removes it if level is 0.
     *
     * @param viewer the player whose language the rebuilt lore is written in, may be null
     */
    public void setLevel(ItemStack item, int level, GenSprout plugin, Player viewer) {
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
        rebuildLore(item, plugin, viewer);
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
     *
     * @param viewer the player who owns the hoe, whose language the name and lore are written in.
     *               May be null, in which case the server default language is used. Item text is
     *               baked in at write time, so a hoe reads in whichever language it was last
     *               rebuilt in until the owner next upgrades it.
     */
    public static void rebuildLore(ItemStack item, GenSprout plugin, Player viewer) {
        if (item == null) return;
        ItemMeta meta = item.getItemMeta();
        LanguageManager lang = plugin.getLanguageManager();

        // Mark this as a custom hoe
        NamespacedKey mainHoeKey = new NamespacedKey(plugin, "sprout_hoe");
        meta.getPersistentDataContainer().set(mainHoeKey, PersistentDataType.BYTE, (byte) 1);

        meta.displayName(noItalic(lang.getComponent("items.hoe.name", viewer)));

        List<Component> lore = new ArrayList<>();
        lore.add(noItalic(lang.getComponent("items.hoe.lore-description", viewer)));
        lore.add(Component.empty());
        lore.add(noItalic(lang.getComponent("items.hoe.lore-enchants-header", viewer)));

        boolean hasEnchants = false;
        for (HoeEnchant enchant : HoeEnchant.values()) {
            int lvl = enchant.getLevel(item, plugin);
            if (lvl > 0) {
                // The enchant name carries its own colours, so it is inserted as markup, not text.
                lore.add(noItalic(lang.getComponent("items.hoe.lore-enchant-entry", viewer,
                        LanguageManager.values(
                                "name", enchant.getDisplayName(plugin, viewer, lvl),
                                "level", getRomanNumeral(lvl)))));
                hasEnchants = true;
            }
        }
        if (!hasEnchants) {
            lore.add(noItalic(lang.getComponent("items.hoe.lore-no-enchants", viewer)));
        }

        lore.add(Component.empty());
        lore.add(noItalic(lang.getComponent("items.hoe.lore-hint", viewer)));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    /**
     * Item names and lore are rendered in italics by the client unless italics are explicitly
     * turned off, which would fight the colours defined in the language files.
     */
    private static Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    /**
     * Creates a fresh Sprout Hoe item (used for the starter kit and the in-shop purchase).
     *
     * @param viewer the player receiving the hoe, whose language the item text uses
     */
    public static ItemStack createBaseHoe(GenSprout plugin, Player viewer) {
        ItemStack hoe = new ItemStack(Material.NETHERITE_HOE);
        rebuildLore(hoe, plugin, viewer);
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
