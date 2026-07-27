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

        if (command.getName().equalsIgnoreCase("shop") || command.getName().equalsIgnoreCase("supplies") || command.getName().equalsIgnoreCase("bshop") || command.getName().equalsIgnoreCase("buildshop")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the Generator Building Supplies Shop!");
                return true;
            }
            plugin.getDialogManager().openSuppliesShopCategoryMenu(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("start") || command.getName().equalsIgnoreCase("sproutstart") || command.getName().equalsIgnoreCase("tutorial")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can start the tutorial!");
                return true;
            }
            handleStartCommand(player);
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
                    sender.sendMessage("Use /" + mainCmd + " <start|givegen|givegenslots|addxp|addessence|addmoney|setlevel|setprestige|givehoe|givesellwand|clearstats|reload>");
                }
                return true;
            }

            // Player commands
            if (args.length > 0) {
                String sub = args[0].toLowerCase();
                if (sub.equals("start") || sub.equals("tutorial")) {
                    handleStartCommand(player);
                    return true;
                }
                if (sub.equals("shop") || sub.equals("supplies") || sub.equals("bshop") || sub.equals("buildshop")) {
                    plugin.getDialogManager().openSuppliesShopCategoryMenu(player);
                    return true;
                }
                if (sub.equals("givegen") || sub.equals("addxp") || sub.equals("addessence") || sub.equals("removeessence") || sub.equals("setessence") || sub.equals("reload") || sub.equals("definefarm") || sub.equals("savefarm")
                        || sub.equals("givegenslots") || sub.equals("setlevel") || sub.equals("setprestige") || sub.equals("addmoney") || sub.equals("givehoe") || sub.equals("givesellwand") || sub.equals("clearstats")) {
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
        com.github.gensprout.economy.SellManager.sellAllInInventory(plugin, player, 1.0);
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

        if (sub.equals("givehoe")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + mainCmd + " givehoe <player>");
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("Player not found!");
                return;
            }
            ItemStack hoe = com.github.gensprout.farming.HoeEnchant.createBaseHoe(plugin);
            target.getInventory().addItem(hoe).forEach((index, item) -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            sender.sendMessage(plugin.getMiniMessage().deserialize("<green>Gave a Sprout Hoe to " + target.getName() + ".</green>"));
            target.sendMessage(plugin.getMiniMessage().deserialize("<green>You received a Sprout Hoe from an admin.</green>"));
            return;
        }

        if (sub.equals("givesellwand")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + mainCmd + " givesellwand <player> [tier]");
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage("Player not found!");
                return;
            }
            int tier = 1;
            if (args.length >= 3) {
                try {
                    tier = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("Tier must be a number!");
                    return;
                }
            }
            ItemStack wand = com.github.gensprout.economy.SellWand.createSellWand(plugin, tier);
            target.getInventory().addItem(wand).forEach((index, item) -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            sender.sendMessage(plugin.getMiniMessage().deserialize("<green>Gave a Sell Wand (Tier " + tier + ") to " + target.getName() + ".</green>"));
            target.sendMessage(plugin.getMiniMessage().deserialize("<green>You received a Sell Wand from an admin.</green>"));
            return;
        }

        if (sub.equals("clearstats")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + mainCmd + " clearstats <player>");
                return;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target != null && target.isOnline()) {
                PlayerData data = plugin.getPlayerManager().getPlayerData(target.getUniqueId());
                double currentBal = EconomyHook.getBalance(target);
                if (currentBal > 0) {
                    EconomyHook.withdraw(target, currentBal);
                }
                data.clearStats();
                plugin.getPlayerManager().savePlayer(target.getUniqueId());
                com.github.gensprout.farming.FarmCropView.refreshRegionForPlayer(plugin, target, plugin.getFarmManager().getActiveRegion());
                sender.sendMessage(plugin.getMiniMessage().deserialize("<green>Cleared all stats (Level, XP, Prestige, Essence, Money) for " + target.getName() + ".</green>"));
                target.sendMessage(plugin.getMiniMessage().deserialize("<red>All your stats (Level, XP, Prestige, Essence, Money) have been cleared by an admin.</red>"));
            } else {
                org.bukkit.OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(args[1]);
                if (offlineTarget.hasPlayedBefore() || offlineTarget.isOnline()) {
                    PlayerData data = plugin.getPlayerManager().getPlayerData(offlineTarget.getUniqueId());
                    double currentBal = EconomyHook.getBalance(offlineTarget);
                    if (currentBal > 0) {
                        EconomyHook.withdraw(offlineTarget, currentBal);
                    }
                    data.clearStats();
                    plugin.getPlayerManager().savePlayer(offlineTarget.getUniqueId());
                    sender.sendMessage(plugin.getMiniMessage().deserialize("<green>Cleared all stats (Level, XP, Prestige, Essence, Money) for offline player " + (offlineTarget.getName() != null ? offlineTarget.getName() : args[1]) + ".</green>"));
                } else {
                    sender.sendMessage("Player not found!");
                }
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("Usage: /" + mainCmd + " <givegen|givegenslots|addxp|addessence|addmoney|setlevel|setprestige> <player> <amount/tier> [amount]");
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
                    plugin.getPlayerManager().addXp(target, xp);
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
            case "removeessence" -> {
                try {
                    int essence = Math.max(0, Integer.parseInt(args[2]));
                    int newEssence = Math.max(0, data.getEssence() - essence);
                    data.setEssence(newEssence);
                    plugin.getPlayerManager().savePlayer(target.getUniqueId());
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Removed " + essence + " Essence from " + target.getName() + " (New Total: " + newEssence + ").</green>"
                    ));
                    target.sendMessage(plugin.getMiniMessage().deserialize(
                            "<red>An admin removed " + essence + " Essence from your balance.</red>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("Essence amount must be a number!");
                }
            }
            case "setessence" -> {
                try {
                    int essence = Math.max(0, Integer.parseInt(args[2]));
                    data.setEssence(essence);
                    plugin.getPlayerManager().savePlayer(target.getUniqueId());
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Set " + target.getName() + "'s Essence balance to " + essence + ".</green>"
                    ));
                    target.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Your Essence balance was set to " + essence + " by an admin.</green>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("Essence amount must be a number!");
                }
            }
            case "givegenslots" -> {
                try {
                    int amount = Integer.parseInt(args[2]);
                    for (int i = 0; i < amount; i++) {
                        data.addPurchasedSlot();
                    }
                    plugin.getPlayerManager().savePlayer(target.getUniqueId());
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Gave " + amount + " extra generator slot(s) to " + target.getName() + ".</green>"
                    ));
                    target.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Received " + amount + " extra generator slot(s) from admin.</green>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("Slot amount must be a number!");
                }
            }
            case "setlevel" -> {
                try {
                    int level = Math.max(1, Integer.parseInt(args[2]));
                    data.setLevel(level);
                    data.setFarmingXp(0.0);
                    plugin.getPlayerManager().savePlayer(target.getUniqueId());
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Set " + target.getName() + "'s Farming Level to " + level + ".</green>"
                    ));
                    target.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Your Farming Level was set to " + level + " by an admin.</green>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("Level must be a number!");
                }
            }
            case "setprestige" -> {
                try {
                    int prestige = Math.max(0, Integer.parseInt(args[2]));
                    data.setPrestige(prestige);
                    plugin.getPlayerManager().savePlayer(target.getUniqueId());
                    com.github.gensprout.farming.FarmCropView.refreshRegionForPlayer(plugin, target, plugin.getFarmManager().getActiveRegion());
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Set " + target.getName() + "'s Prestige to " + prestige + ".</green>"
                    ));
                    target.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Your Prestige was set to " + prestige + " by an admin.</green>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("Prestige must be a number!");
                }
            }
            case "addmoney" -> {
                try {
                    double amount = Double.parseDouble(args[2]);
                    com.github.gensprout.economy.EconomyHook.deposit(target, amount);
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Gave " + EconomyHook.format(amount) + " to " + target.getName() + ".</green>"
                    ));
                    target.sendMessage(plugin.getMiniMessage().deserialize(
                            "<green>Received " + EconomyHook.format(amount) + " from admin.</green>"
                    ));
                } catch (NumberFormatException e) {
                    sender.sendMessage("Amount must be a number!");
                }
            }
            default -> sender.sendMessage("Unknown admin command.");
        }
    }

    private void handleStartCommand(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        boolean isTester = player.hasPermission("gensprout.tester");
        if (!isTester && data.hasCompletedTutorial()) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>You have already completed the tutorial and claimed your starter items!</red>"));
            return;
        }

        data.setCompletedTutorial(true);
        plugin.getPlayerManager().savePlayer(player.getUniqueId());

        // Starter items: 1x Sprout Hoe + default max generators (20)
        ItemStack sproutHoe = com.github.gensprout.farming.HoeEnchant.createBaseHoe(plugin);
        player.getInventory().addItem(sproutHoe).forEach((index, item) -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        int defaultSlots = plugin.getGeneratorManager().getDefaultSlots();
        plugin.getGeneratorManager().giveGenerator(player, 1, defaultSlots);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
        player.sendMessage(plugin.getMiniMessage().deserialize("<green>Tutorial started! You've received a <gradient:green:aqua>Sprout Hoe</gradient> and " + defaultSlots + " Tier 1 Generators to get started!</green>"));

        // Open Tutorial Dialog
        plugin.getDialogManager().openFirstJoinTutorialDialog(player);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
        if (command.getName().equalsIgnoreCase(mainCmd)) {
            if (args.length == 1) {
                list.add("start");
                list.add("tutorial");
                list.add("shop");
                if (sender.hasPermission("gensprout.admin")) {
                    list.addAll(Arrays.asList("givegen", "givegenslots", "addxp", "addessence", "removeessence", "setessence", "addmoney", "setlevel", "setprestige", "givehoe", "givesellwand", "clearstats", "reload", "definefarm", "savefarm"));
                }
            } else if (args.length == 2 && sender.hasPermission("gensprout.admin")) {
                String sub = args[0].toLowerCase();
                if (sub.equals("givegen") || sub.equals("addxp") || sub.equals("addessence") || sub.equals("removeessence") || sub.equals("setessence") || sub.equals("givegenslots")
                        || sub.equals("setlevel") || sub.equals("setprestige") || sub.equals("addmoney") || sub.equals("givehoe") || sub.equals("givesellwand") || sub.equals("clearstats")) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        list.add(player.getName());
                    }
                }
            } else if (args.length == 3 && sender.hasPermission("gensprout.admin")) {
                String sub = args[0].toLowerCase();
                if (sub.equals("givegen") || sub.equals("givesellwand")) {
                    for (int i = 1; i <= 25; i++) {
                        list.add(String.valueOf(i));
                    }
                }
            }
        }
        return list;
    }
}
