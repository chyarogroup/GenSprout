package com.github.gensprout.listener;

import com.github.gensprout.GenSprout;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class FarmSelectorListener implements Listener {

    private final GenSprout plugin;

    public FarmSelectorListener(GenSprout plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item == null || !plugin.getFarmManager().isSelectorStick(item)) {
            return;
        }

        if (!player.hasPermission("gensprout.admin")) {
            event.setCancelled(true);
            plugin.getLanguageManager().send(player, "system.no-permission");
            return;
        }

        // Cancel default interact actions
        event.setCancelled(true);

        // Ensure we only process one hand event (offhand raises it too)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        Location loc = block.getLocation();

        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            plugin.getFarmManager().setPos1(player.getUniqueId(), loc);
            plugin.getLanguageManager().send(player, "command.farm.pos1", com.github.gensprout.lang.LanguageManager.values(
                    "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()),
                    "z", String.valueOf(loc.getBlockZ()),
                    "world", loc.getWorld().getName()
            ));
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            plugin.getFarmManager().setPos2(player.getUniqueId(), loc);
            plugin.getLanguageManager().send(player, "command.farm.pos2", com.github.gensprout.lang.LanguageManager.values(
                    "x", String.valueOf(loc.getBlockX()),
                    "y", String.valueOf(loc.getBlockY()),
                    "z", String.valueOf(loc.getBlockZ()),
                    "world", loc.getWorld().getName()
            ));
        }
    }
}
