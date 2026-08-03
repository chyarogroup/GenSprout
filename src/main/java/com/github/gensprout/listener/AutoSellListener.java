package com.github.gensprout.listener;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.SellManager;
import com.github.gensprout.farming.HoeEnchant;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles auto-selling items on pickup based on global config and held Sprout Hoe AUTO_SELL enchant.
 */
public class AutoSellListener implements Listener {

    private final GenSprout plugin;

    public AutoSellListener(GenSprout plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();

        if (!SellManager.isSellable(plugin, stack)) return;

        boolean globalAutoSell = plugin.getConfig().getBoolean("auto-sell.enabled", true);
        boolean isCropItem = SellManager.getCropConfigName(stack.getType()) != null;
        ItemStack hoe = player.getInventory().getItemInMainHand();
        boolean hoeAutoSell = HoeEnchant.isSproutHoe(hoe, plugin) && HoeEnchant.AUTO_SELL.getLevel(hoe, plugin) > 0;

        boolean shouldAutoSell = globalAutoSell && hoeAutoSell && isCropItem;
        if (!shouldAutoSell) return;

        if (SellManager.tryAutoSell(plugin, player, stack)) {
            event.setCancelled(true);
            itemEntity.remove();
        }
    }
}
