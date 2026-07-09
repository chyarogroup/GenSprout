package com.github.gensprout.listener;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.farming.HoeEnchant;
import com.github.gensprout.generator.GeneratorBlock;
import com.github.gensprout.generator.GeneratorType;
import com.github.gensprout.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class GeneratorPlaceListener implements Listener {

    private final GenSprout plugin;

    public GeneratorPlaceListener(GenSprout plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        Integer tier = plugin.getGeneratorManager().getGeneratorTierFromItem(item);
        if (tier == null) return; // Not a custom generator block

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int active = plugin.getGeneratorManager().getActiveCount(player.getUniqueId());
        int max = data.getMaxSlots(plugin.getGeneratorManager().getDefaultSlots());

        if (active >= max) {
            event.setCancelled(true);
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<red>You have reached your placed generator limit of <gold>" + max + "</gold>! Buy more slots or prestige to place more.</red>"
            ));
            return;
        }

        // Place the generator block
        Block block = event.getBlockPlaced();
        boolean placed = plugin.getGeneratorManager().placeGenerator(block.getLocation(), player.getUniqueId(), tier);
        if (placed) {
            plugin.getGeneratorManager().saveGenerators();
            player.playSound(block.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.5f, 1.5f);
            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<green>Placed Tier " + tier + " Generator! (" + (active + 1) + "/" + max + " active)</green>"
            ));
        } else {
            event.setCancelled(true);
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>Failed to place generator!</red>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        Location loc = block.getLocation();

        GeneratorBlock gen = plugin.getGeneratorManager().getGenerator(loc);
        if (gen == null) return; // Not a generator block

        // Intercept block break
        event.setCancelled(true);

        handlePickUp(player, block);
    }

    private void handlePickUp(Player player, Block block) {
        Location loc = block.getLocation();
        GeneratorBlock gen = plugin.getGeneratorManager().getGenerator(loc);
        if (gen == null) return;

        if (!gen.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("gensprout.admin")) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>This generator belongs to someone else!</red>"));
            return;
        }

        // Give the block back to the player
        ItemStack item = plugin.getGeneratorManager().createGeneratorItem(gen.getTier(), 1);
        plugin.getGeneratorManager().removeGenerator(loc);
        plugin.getGeneratorManager().saveGenerators();

        // Put in inventory or drop
        player.getInventory().addItem(item).forEach((index, it) -> player.getWorld().dropItemNaturally(loc, it));
        player.playSound(loc, Sound.BLOCK_WOOD_BREAK, 0.7f, 0.8f);
        player.sendMessage(plugin.getMiniMessage().deserialize("<green>Picked up your Tier " + gen.getTier() + " Generator.</green>"));
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        // 1. Sprout Hoe Shift + Right-Click actions (opens upgrade dialog)
        if (item != null && (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            if (HoeEnchant.isSproutHoe(item, plugin) && player.isSneaking()) {
                event.setCancelled(true);
                plugin.getDialogManager().openHoeUpgradeShop(player);
                return;
            }
        }

        // 2. Placed generator left-click actions (Just punching should pick it up)
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null) return;

            GeneratorBlock gen = plugin.getGeneratorManager().getGenerator(block.getLocation());
            if (gen == null) return; // Not a custom generator block

            event.setCancelled(true);
            handlePickUp(player, block);
            return;
        }

        // 3. Placed generator right-click actions (opens stats / control panel or upgrades if sneaking)
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block == null) return;

            GeneratorBlock gen = plugin.getGeneratorManager().getGenerator(block.getLocation());
            if (gen == null) return; // Not a custom generator block

            event.setCancelled(true);

            if (!gen.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("gensprout.admin")) {
                UUID ownerUuid = gen.getOwnerUuid();
                String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                player.sendMessage(plugin.getMiniMessage().deserialize(
                        "<red>This generator belongs to " + (ownerName != null ? ownerName : "another player") + ".</red>"
                ));
                return;
            }

            if (player.isSneaking()) {
                // Direct Shift + Right Click Upgrade
                GeneratorType type = plugin.getGeneratorManager().getTierConfig(gen.getTier());
                if (type == null) return;

                GeneratorType nextType = plugin.getGeneratorManager().getTierConfig(gen.getTier() + 1);
                if (nextType == null) {
                    player.sendMessage(plugin.getMiniMessage().deserialize("<red>This generator is already at maximum tier!</red>"));
                    return;
                }

                double upgradeCost = type.getUpgradePrice();
                if (EconomyHook.has(player, upgradeCost)) {
                    EconomyHook.withdraw(player, upgradeCost);
                    gen.setTier(gen.getTier() + 1);
                    gen.getLocation().getBlock().setType(nextType.getBlockType());
                    plugin.getGeneratorManager().saveGenerators();

                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                    player.sendMessage(plugin.getMiniMessage().deserialize("<green>Upgraded Generator to Tier " + gen.getTier() + "!</green>"));
                } else {
                    player.sendMessage(plugin.getMiniMessage().deserialize(
                            "<red>Insufficient funds! You need " + EconomyHook.format(upgradeCost) + " to upgrade to Tier " + nextType.getTier() + ".</red>"
                    ));
                }
            } else {
                // Open GUI panel
                plugin.getDialogManager().openGeneratorBlockControl(player, gen);
            }
        }
    }
}
