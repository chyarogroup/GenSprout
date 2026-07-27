package com.github.gensprout.listener;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.SellManager;
import com.github.gensprout.economy.SellWand;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Handles right-clicking a Sell Wand item.
 * - Right-click a chest/barrel/shulker box/other container: sells everything sellable inside it.
 * - Right-click air or any other block: does nothing but reminds the player to target a chest.
 */
public class SellWandListener implements Listener {

    private final GenSprout plugin;

    public SellWandListener(GenSprout plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!SellWand.isSellWand(item, plugin)) return;

        event.setCancelled(true);
        double multiplier = SellWand.getMultiplier(item, plugin);

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block != null && block.getState() instanceof Container container) {
                SellManager.sellAllItems(plugin, player, container.getInventory(), multiplier);
                return;
            }
        }

        // Sell Wands only work on containers - remind the player instead of selling their inventory
        player.sendActionBar(plugin.getMiniMessage().deserialize("<red>Right-click a chest, barrel, or other container to sell its contents!</red>"));
    }
}
