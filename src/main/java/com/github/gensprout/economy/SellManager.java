package com.github.gensprout.economy;

import com.github.gensprout.GenSprout;
import com.github.gensprout.player.PlayerData;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Centralized selling logic shared by the /sell command, the Sell Wand item,
 * and the auto-sell-on-pickup feature, so all three stay perfectly in sync.
 */
public class SellManager {

    private SellManager() {
    }

    /**
     * Result of selling a single ItemStack's worth of items.
     */
    public static class SellResult {
        public double earnings = 0.0;
        public double xpEarned = 0.0;
        public int itemsSold = 0;
    }

    /**
     * Returns the per-unit sell price for an item, or 0.0 if it cannot be sold.
     */
    public static double getUnitPrice(GenSprout plugin, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return 0.0;

        Double dropVal = plugin.getGeneratorManager().getDropValueFromItem(item);
        if (dropVal != null) return dropVal;

        String configKey = getCropConfigName(item.getType());
        if (configKey != null) {
            return plugin.getConfig().getDouble("farming.crops." + configKey + ".sell-price", 0.0);
        }
        return 0.0;
    }

    public static boolean isSellable(GenSprout plugin, ItemStack item) {
        return getUnitPrice(plugin, item) > 0.0;
    }

    public static String getCropConfigName(Material mat) {
        return switch (mat) {
            case WHEAT -> "WHEAT";
            case POTATO -> "POTATOES";
            case CARROT -> "CARROTS";
            case BEETROOT -> "BEETROOTS";
            case MELON_SLICE -> "MELON";
            case PUMPKIN -> "PUMPKIN";
            case COCOA_BEANS -> "COCOA";
            case SUGAR_CANE -> "SUGAR_CANE";
            case NETHER_WART -> "NETHER_WART";
            default -> null;
        };
    }

    /**
     * Computes the earnings/xp for selling an entire ItemStack (does not mutate the stack or
     * deposit/award anything itself). extraMultiplier stacks on top of the player's prestige
     * money multiplier (e.g. a Sell Wand's bonus multiplier).
     */
    public static SellResult sellItemStack(GenSprout plugin, Player player, ItemStack item, double extraMultiplier) {
        SellResult result = new SellResult();
        if (item == null) return result;

        double unitPrice = getUnitPrice(plugin, item);
        if (unitPrice <= 0.0) return result;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        double mult = data.getMoneyMultiplier() * extraMultiplier;
        int amount = item.getAmount();

        result.earnings = unitPrice * amount * mult;
        result.itemsSold = amount;

        Double dropVal = plugin.getGeneratorManager().getDropValueFromItem(item);
        if (dropVal != null) {
            // Award a small amount of XP for selling generator drops: 0.5% of the base drop value
            double baseItemXp = dropVal * 0.005;
            result.xpEarned = baseItemXp * amount * data.getXpMultiplier();
        }

        return result;
    }

    /**
     * Sells every sellable item currently in the given inventory, depositing earnings and
     * awarding XP to the player. Works for the player's own inventory as well as any other
     * container inventory (chest, barrel, shulker box, etc.) - used by both the /sell command
     * and the Sell Wand item (right-click air/inventory sells the player, right-click a
     * container sells the container's contents instead).
     */
    public static void sellAllItems(GenSprout plugin, Player player, org.bukkit.inventory.Inventory inventory, double extraMultiplier) {
        double totalEarnings = 0.0;
        int totalItemsSold = 0;
        double totalXpEarned = 0.0;

        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            if (!isSellable(plugin, item)) continue;

            SellResult res = sellItemStack(plugin, player, item, extraMultiplier);
            if (res.itemsSold <= 0) continue;

            totalEarnings += res.earnings;
            totalItemsSold += res.itemsSold;
            totalXpEarned += res.xpEarned;

            inventory.setItem(i, null); // Clear the slot
        }

        if (totalItemsSold > 0) {
            EconomyHook.deposit(player, totalEarnings);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);

            String xpMsg = "";
            if (totalXpEarned > 0.0) {
                plugin.getPlayerManager().addXp(player, totalXpEarned);
                xpMsg = " <gray>(+" + String.format("%.2f", totalXpEarned) + " XP)</gray>";
            }

            String multMsg = extraMultiplier > 1.0
                    ? " <light_purple>(Sell Wand " + String.format("%.1f", extraMultiplier) + "x)</light_purple>"
                    : "";

            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<green>Successfully sold <gold>" + totalItemsSold + "</gold> item(s) for <gold>" + EconomyHook.format(totalEarnings) + "</gold>!" + multMsg + xpMsg + "</green>"
            ));
        } else {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>There is nothing sellable in there!</red>"));
        }
    }

    /**
     * Sells every sellable item currently in the player's inventory, depositing earnings and
     * awarding XP. Used by both the /sell command and the Sell Wand item.
     */
    public static void sellAllInInventory(GenSprout plugin, Player player, double extraMultiplier) {
        double totalEarnings = 0.0;
        int totalItemsSold = 0;
        double totalXpEarned = 0.0;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;
            if (!isSellable(plugin, item)) continue;

            SellResult res = sellItemStack(plugin, player, item, extraMultiplier);
            if (res.itemsSold <= 0) continue;

            totalEarnings += res.earnings;
            totalItemsSold += res.itemsSold;
            totalXpEarned += res.xpEarned;

            player.getInventory().setItem(i, null); // Clear the slot
        }

        if (totalItemsSold > 0) {
            EconomyHook.deposit(player, totalEarnings);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);

            String xpMsg = "";
            if (totalXpEarned > 0.0) {
                plugin.getPlayerManager().addXp(player, totalXpEarned);
                xpMsg = " <gray>(+" + String.format("%.2f", totalXpEarned) + " XP)</gray>";
            }

            String multMsg = extraMultiplier > 1.0
                    ? " <light_purple>(Sell Wand " + String.format("%.1f", extraMultiplier) + "x)</light_purple>"
                    : "";

            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<green>Successfully sold <gold>" + totalItemsSold + "</gold> item(s) for <gold>" + EconomyHook.format(totalEarnings) + "</gold>!" + multMsg + xpMsg + "</green>"
            ));
        } else {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>You do not have any crops or generator drops to sell in your inventory!</red>"));
        }
    }

    /**
     * Attempts to instantly auto-sell a single ItemStack the moment it's obtained (harvested or
     * picked up), provided auto-sell is enabled in the config. Returns true if the item was sold
     * (and should therefore NOT be handed to the player), false if it should be given normally.
     */
    public static boolean tryAutoSell(GenSprout plugin, Player player, ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!isSellable(plugin, item)) return false;

        SellResult res = sellItemStack(plugin, player, item, 1.0);
        if (res.itemsSold <= 0) return false;

        EconomyHook.deposit(player, res.earnings);
        if (res.xpEarned > 0.0) {
            plugin.getPlayerManager().addXp(player, res.xpEarned);
        }
        player.sendActionBar(plugin.getMiniMessage().deserialize("<green>Auto-Sold <gold>" + res.itemsSold + "x</gold> for <gold>" + EconomyHook.format(res.earnings) + "</gold></green>"));
        return true;
    }
}
