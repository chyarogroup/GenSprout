package com.github.gensprout.listener;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.SellManager;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * When enabled (auto-sell.enabled in config.yml, on by default), sellable items picked up off
 * the ground - such as physical generator drops - are instantly sold instead of entering the
 * player's inventory. Directly-harvested crops are handled separately in FarmingListener.
 */
public class AutoSellListener implements Listener {

    private final GenSprout plugin;

    public AutoSellListener(GenSprout plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!plugin.getConfig().getBoolean("auto-sell.enabled", true)) return;

        Item itemEntity = event.getItem();
        ItemStack stack = itemEntity.getItemStack();

        if (!SellManager.isSellable(plugin, stack)) return;

        event.setCancelled(true);
        if (SellManager.tryAutoSell(plugin, player, stack)) {
            itemEntity.remove();
        }
    }
}
