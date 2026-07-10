package com.github.gensprout.command;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GenSproutCommand implements CommandExecutor, TabCompleter {

    private final GenSprout plugin;

    public GenSproutCommand(GenSprout plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("prestige")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the Prestige Menu!");
                return true;
            }
            plugin.getDialogManager().openPrestigeShop(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("genshop")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use the generator shop!");
                return true;
            }
            plugin.getDialogManager().openGeneratorShop(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("sellall") || command.getName().equalsIgnoreCase("sell")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can sell crops!");
                return true;
            }
            handleSellAll(player);
            return true;
        }

        // Handle main command
        String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
        if (command.getName().equalsIgnoreCase(mainCmd)) {
            if (!(sender instanceof Player player)) {
                // Console admin commands
                if (args.length > 0) {
                    handleAdminCommands(sender, args);
                } else {
                    sender.sendMessage("Use /" + mainCmd + " <givegen|addxp|addessence|reload>");
                }
                return true;
            }

            // Player commands
            if (args.length > 0) {
                String sub = args[0].toLowerCase();
                if (sub.equals("givegen") || sub.equals("addxp") || sub.equals("addessence") || sub.equals("reload") || sub.equals("definefarm") || sub.equals("savefarm")) {
                    if (!player.hasPermission("gensprout.admin")) {
                        player.sendMessage(plugin.getMiniMessage().deserialize("<red>No permission for admin commands!</red>"));
                        return true;
                    }
                    handleAdminCommands(player, args);
                    return true;
                }
            }

            // Open main menu if no args or unrecognized player args
            plugin.getDialogManager().openMainMenu(player);
            return true;
        }

        return true;
    }

    private void handleSellAll(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        double mult = data.getMoneyMultiplier();
        double totalEarnings = 0.0;
        int totalItemsSold = 0;
        double totalXpEarned = 0.0;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR) continue;

            double unitPrice = 0.0;
            Double dropVal = plugin.getGeneratorManager().getDropValueFromItem(item);
            if (dropVal != null) {
                unitPrice = dropVal;
                // Award a small amount of XP for selling generator drops: 0.5% of the base drop value
                double baseItemXp = dropVal * 0.005;
                totalXpEarned += baseItemXp * item.getAmount() * data.getXpMultiplier();
            } else {
                String configKey = getCropConfigName(item.getType());
                if (configKey != null) {
                    unitPrice = plugin.getConfig().getDouble("farming.crops." + configKey + ".sell-price", 0.0);
                }
            }

            if (unitPrice <= 0.0) continue;

            int amount = item.getAmount();
            double earnings = unitPrice * amount * mult;

            totalEarnings += earnings;
            totalItemsSold += amount;

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

            player.sendMessage(plugin.getMiniMessage().deserialize(
                    "<green>Successfully sold <gold>" + totalItemsSold + "</gold> item(s) for <gold>" + EconomyHook.format(totalEarnings) + "</gold> (Multiplier: " + String.format("%.2f", mult) + "x)!" + xpMsg + "</green>"
            ));
        } else {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>You do not have any crops or generator drops to sell in your inventory!</red>"));
        }
    }

    private String getCropConfigName(Material mat) {
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

    private void handleAdminCommands(CommandSender sender, String[] args) {
        String sub = args[0].toLowerCase();
        
        if (sub.equals("reload")) {
            plugin.reloadConfig();
            plugin.getGeneratorManager().reload();
            plugin.getScoreboardManager().reload();
            String serverName = plugin.getConfig().getString("server.name", "GenSprout");
            sender.sendMessage(plugin.getMiniMessage().deserialize("<green>" + serverName + " configuration reloaded!</green>"));
            return;
        }

        if (sub.equals("definefarm")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use definefarm!");
                return;
            }
            org.bukkit.inventory.ItemStack stick = plugin.getFarmManager().createSelectorStick();
            player.getInventory().addItem(stick).forEach((index, item) -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
            player.sendMessage(plugin.getMiniMessage().deserialize("<green>Received the Farm Selector Stick. Left-click a block to set Pos 1, and Right-click to set Pos 2. Run <gold>/" + mainCmd + " savefarm</gold> to define the region.</green>"));
            return;
        }

        if (sub.equals("savefarm")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can use savefarm!");
                return;
            }
            org.bukkit.Location[] sel = plugin.getFarmManager().getSelection(player.getUniqueId());
            if (sel == null || sel[0] == null || sel[1] == null) {
                player.sendMessage(plugin.getMiniMessage().deserialize("<red>You have not selected both positions! Left-click and right-click blocks with the Farm Selector Stick first.</red>"));
                return;
            }
            if (sel[0].getWorld() == null || !sel[0].getWorld().equals(sel[1].getWorld())) {
                player.sendMessage(plugin.getMiniMessage().deserialize("<red>Positions must be in the same world!</red>"));
                return;
            }
            com.github.gensprout.farming.FarmRegion region = new com.github.gensprout.farming.FarmRegion(
                    sel[0].getWorld().getName(),
                    sel[0].getBlockX(), sel[0].getBlockY(), sel[0].getBlockZ(),
                    sel[1].getBlockX(), sel[1].getBlockY(), sel[1].getBlockZ()
            );
            plugin.getFarmManager().saveFarmRegion(region);
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.5f);
            player.sendMessage(plugin.getMiniMessage().deserialize("<green>Farm region defined and saved successfully!</green>"));
            return;
        }

        String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
        if (args.length < 3) {
            sender.sendMessage("Usage: /" + mainCmd + " <givegen|addxp|addessence> <player> <amount/tier> [amount]");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            sender.sendMessage("Player not found!");
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(target.getUniqueId());

        switch (sub) {
            case "givegen" -> {
                try {
                    int tier = Integer.parseInt(args[2]);
                    int amount = 1;
                    if (args.length >= 4) {
                        amount = Integer.parseInt(args[3]);
                    }
                    if (plugin.getGeneratorManager().getTierConfig(tier) == null) {
                        sender.sendMessage("Invalid generator tier (1-25)!");
                        return;
                    }
                    plugin.getGeneratorManager().giveGenerator(target, tier, amount);
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Gave " + amount + "x Tier " + tier + " Generator(s) to " + target.getName() + ".</green>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("Tier and amount must be numbers!");
                }
            }
            case "addxp" -> {
                try {
                    double xp = Double.parseDouble(args[2]);
                    data.setFarmingXp(data.getFarmingXp() + xp);
                    plugin.getPlayerManager().savePlayer(target.getUniqueId());
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Added " + xp + " Farming XP to " + target.getName() + ".</green>"
                    ));
                    target.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Received " + xp + " Farming XP from admin.</green>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("XP amount must be a number!");
                }
            }
            case "addessence" -> {
                try {
                    int essence = Integer.parseInt(args[2]);
                    data.addEssence(essence);
                    plugin.getPlayerManager().savePlayer(target.getUniqueId());
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Added " + essence + " Essence to " + target.getName() + ".</green>"
                    ));
                    target.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Received " + essence + " Essence from admin.</green>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("Essence amount must be a number!");
                }
            }
            default -> sender.sendMessage("Unknown admin command.");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
        if (command.getName().equalsIgnoreCase(mainCmd)) {
            if (args.length == 1) {
                if (sender.hasPermission("gensprout.admin")) {
                    list.addAll(Arrays.asList("givegen", "addxp", "addessence", "reload", "definefarm", "savefarm"));
                }
            } else if (args.length == 2 && sender.hasPermission("gensprout.admin")) {
                String sub = args[0].toLowerCase();
                if (sub.equals("givegen") || sub.equals("addxp") || sub.equals("addessence")) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        list.add(player.getName());
                    }
                }
            } else if (args.length == 3 && sender.hasPermission("gensprout.admin")) {
                String sub = args[0].toLowerCase();
                if (sub.equals("givegen")) {
                    for (int i = 1; i <= 25; i++) {
                        list.add(String.valueOf(i));
                    }
                }
            }
        }
        return list;
    }
}
