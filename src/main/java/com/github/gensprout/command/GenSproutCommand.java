package com.github.gensprout.command;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.lang.LanguageManager;
import com.github.gensprout.player.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
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

    private List<Player> resolveTargetPlayers(CommandSender sender, String input) {
        List<Player> players = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) return players;

        if (input.startsWith("@")) {
            try {
                List<Entity> selected = Bukkit.selectEntities(sender, input);
                for (Entity entity : selected) {
                    if (entity instanceof Player p) {
                        players.add(p);
                    }
                }
                if (!players.isEmpty()) {
                    return players;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }

        Player direct = Bukkit.getPlayer(input);
        if (direct != null && direct.isOnline()) {
            players.add(direct);
            return players;
        }

        try {
            List<Entity> selected = Bukkit.selectEntities(sender, input);
            for (Entity entity : selected) {
                if (entity instanceof Player p) {
                    players.add(p);
                }
            }
        } catch (IllegalArgumentException ignored) {
        }

        return players;
    }

    private Player getProxiedOrEntityTarget(CommandSender sender) {
        if (sender == null) return null;

        // 1. Check ProxiedCommandSender (/execute as <target>)
        if (sender instanceof ProxiedCommandSender pcs) {
            if (pcs.getCallee() instanceof Player callee) return callee;
            if (pcs.getCallee() != null && pcs.getCallee().getName() != null) {
                Player p = Bukkit.getPlayer(pcs.getCallee().getName());
                if (p != null) return p;
            }
        }

        // 2. Check getEntity() via reflection on Paper CommandSourceStack wrapper
        try {
            java.lang.reflect.Method getEntityMethod = sender.getClass().getMethod("getEntity");
            Object entityObj = getEntityMethod.invoke(sender);
            if (entityObj instanceof Player p) {
                return p;
            }
            if (entityObj instanceof Entity entity && entity instanceof Player p) {
                return p;
            }
            if (entityObj != null) {
                java.lang.reflect.Method getNameMethod = entityObj.getClass().getMethod("getName");
                Object nameObj = getNameMethod.invoke(entityObj);
                if (nameObj instanceof String name) {
                    Player p = Bukkit.getPlayer(name);
                    if (p != null) return p;
                }
            }
        } catch (Throwable ignored) {
        }

        // 3. Check getCallee() via reflection
        try {
            java.lang.reflect.Method getCalleeMethod = sender.getClass().getMethod("getCallee");
            Object calleeObj = getCalleeMethod.invoke(sender);
            if (calleeObj instanceof Player p) {
                return p;
            }
            if (calleeObj != null) {
                java.lang.reflect.Method getNameMethod = calleeObj.getClass().getMethod("getName");
                Object nameObj = getNameMethod.invoke(calleeObj);
                if (nameObj instanceof String name) {
                    Player p = Bukkit.getPlayer(name);
                    if (p != null) return p;
                }
            }
        } catch (Throwable ignored) {
        }

        // 4. Check getHandle() returning NMS/Paper source stack
        try {
            java.lang.reflect.Method getHandleMethod = sender.getClass().getMethod("getHandle");
            Object handleObj = getHandleMethod.invoke(sender);
            if (handleObj != null) {
                try {
                    java.lang.reflect.Method getEntityMethod = handleObj.getClass().getMethod("getEntity");
                    Object entityObj = getEntityMethod.invoke(handleObj);
                    if (entityObj != null) {
                        java.lang.reflect.Method getBukkitEntity = entityObj.getClass().getMethod("getBukkitEntity");
                        Object bukkitEntity = getBukkitEntity.invoke(entityObj);
                        if (bukkitEntity instanceof Player p) return p;
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private List<Player> resolveTargets(CommandSender sender, String[] args, int targetArgIndex) {
        List<Player> targets = new ArrayList<>();

        // Priority 1: Target argument specified in args (e.g. @p, @a, username)
        if (targetArgIndex >= 0 && args != null && args.length > targetArgIndex) {
            String arg = args[targetArgIndex];
            if (arg != null && !arg.trim().isEmpty()) {
                List<Player> argTargets = resolveTargetPlayers(sender, arg);
                if (!argTargets.isEmpty()) {
                    return argTargets;
                }
            }
        }

        // Priority 2: Reflection / Proxied / Entity target (/execute as <target>)
        Player proxiedOrEntityTarget = getProxiedOrEntityTarget(sender);
        if (proxiedOrEntityTarget != null) {
            targets.add(proxiedOrEntityTarget);
            return targets;
        }

        // Priority 3: Direct sender if sender is a Player
        if (sender instanceof Player p) {
            targets.add(p);
            return targets;
        }

        if (sender instanceof Entity entity && entity instanceof Player p) {
            targets.add(p);
            return targets;
        }

        // Priority 4: Sender name lookup if sender represents a player entity
        if (sender != null && sender.getName() != null 
                && !sender.getName().equalsIgnoreCase("CONSOLE") 
                && !sender.getName().equalsIgnoreCase("Server") 
                && !sender.getName().startsWith("@")) {
            Player pName = Bukkit.getPlayer(sender.getName());
            if (pName != null && pName.isOnline()) {
                targets.add(pName);
                return targets;
            }
        }

        return targets;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("prestige")) {
            List<Player> targets = resolveTargets(sender, args, 0);
            if (targets.isEmpty()) {
                plugin.getLanguageManager().send(sender, "system.player-only-action", LanguageManager.values("action", "open the Prestige Menu"));
                return true;
            }
            for (Player target : targets) {
                plugin.getDialogManager().openPrestigeShop(target);
                if (!sender.equals(target)) {
                    plugin.getLanguageManager().send(sender, "command.opened.prestige", LanguageManager.values("player", target.getName()));
                }
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("genshop")) {
            List<Player> targets = resolveTargets(sender, args, 0);
            if (targets.isEmpty()) {
                plugin.getLanguageManager().send(sender, "system.player-only-action", LanguageManager.values("action", "use the generator shop"));
                return true;
            }
            for (Player target : targets) {
                plugin.getDialogManager().openGeneratorShop(target);
                if (!sender.equals(target)) {
                    plugin.getLanguageManager().send(sender, "command.opened.genshop", LanguageManager.values("player", target.getName()));
                }
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("sellall") || command.getName().equalsIgnoreCase("sell")) {
            List<Player> targets = resolveTargets(sender, args, 0);
            if (targets.isEmpty()) {
                plugin.getLanguageManager().send(sender, "system.player-only-action", LanguageManager.values("action", "sell crops"));
                return true;
            }
            for (Player target : targets) {
                handleSellAll(target);
                if (!sender.equals(target)) {
                    plugin.getLanguageManager().send(sender, "command.executed.sellall", LanguageManager.values("player", target.getName()));
                }
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("shop") || command.getName().equalsIgnoreCase("supplies") || command.getName().equalsIgnoreCase("bshop") || command.getName().equalsIgnoreCase("buildshop")) {
            List<Player> targets = resolveTargets(sender, args, 0);
            if (targets.isEmpty()) {
                plugin.getLanguageManager().send(sender, "system.player-only-action", LanguageManager.values("action", "open the Generator Building Supplies Shop"));
                return true;
            }
            for (Player target : targets) {
                plugin.getDialogManager().openSuppliesShopCategoryMenu(target);
                if (!sender.equals(target)) {
                    plugin.getLanguageManager().send(sender, "command.opened.supplies", LanguageManager.values("player", target.getName()));
                }
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("help") || command.getName().equalsIgnoreCase("gensprouthelp") || command.getName().equalsIgnoreCase("sprouthelp")) {
            List<Player> targets = resolveTargets(sender, args, 0);
            if (targets.isEmpty()) {
                plugin.getLanguageManager().send(sender, "system.player-only-action", LanguageManager.values("action", "view the tutorial guide"));
                return true;
            }
            for (Player target : targets) {
                plugin.openTutorialOrHelp(target);
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("start") || command.getName().equalsIgnoreCase("sproutstart") || command.getName().equalsIgnoreCase("tutorial")) {
            List<Player> targets = resolveTargets(sender, args, 0);
            if (targets.isEmpty()) {
                plugin.getLanguageManager().send(sender, "system.player-only-action", LanguageManager.values("action", "start the tutorial"));
                return true;
            }
            for (Player target : targets) {
                handleStartCommand(target);
                if (!sender.equals(target)) {
                    plugin.getLanguageManager().send(sender, "command.executed.start", LanguageManager.values("player", target.getName()));
                }
            }
            return true;
        }

        // Handle main command
        String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
        if (command.getName().equalsIgnoreCase(mainCmd)) {
            if (args.length > 0) {
                String sub = args[0].toLowerCase();
                if (sub.equals("help")) {
                    List<Player> targets = resolveTargets(sender, args, 1);
                    if (targets.isEmpty()) {
                        plugin.getLanguageManager().send(sender, "system.player-only-action", LanguageManager.values("action", "view the tutorial guide"));
                        return true;
                    }
                    for (Player target : targets) {
                        plugin.openTutorialOrHelp(target);
                    }
                    return true;
                }
                if (sub.equals("start") || sub.equals("tutorial")) {
                    List<Player> targets = resolveTargets(sender, args, 1);
                    if (targets.isEmpty()) {
                        plugin.getLanguageManager().send(sender, "system.player-not-found", LanguageManager.values("player", args.length > 1 ? args[1] : "target"));
                        return true;
                    }
                    for (Player target : targets) {
                        handleStartCommand(target);
                        if (!sender.equals(target)) {
                            plugin.getLanguageManager().send(sender, "command.executed.start", LanguageManager.values("player", target.getName()));
                        }
                    }
                    return true;
                }
                if (sub.equals("shop") || sub.equals("supplies") || sub.equals("bshop") || sub.equals("buildshop")) {
                    List<Player> targets = resolveTargets(sender, args, 1);
                    if (targets.isEmpty()) {
                        plugin.getLanguageManager().send(sender, "system.player-only-action", LanguageManager.values("action", "open the Generator Building Supplies Shop"));
                        return true;
                    }
                    for (Player target : targets) {
                        plugin.getDialogManager().openSuppliesShopCategoryMenu(target);
                        if (!sender.equals(target)) {
                            plugin.getLanguageManager().send(sender, "command.opened.supplies", LanguageManager.values("player", target.getName()));
                        }
                    }
                    return true;
                }
                if (sub.equals("givegen") || sub.equals("addxp") || sub.equals("addessence") || sub.equals("removeessence") || sub.equals("setessence") || sub.equals("reload") || sub.equals("definefarm") || sub.equals("savefarm")
                        || sub.equals("givegenslots") || sub.equals("setlevel") || sub.equals("setprestige") || sub.equals("addmoney") || sub.equals("givehoe") || sub.equals("givesellwand") || sub.equals("clearstats")) {
                    if (!sender.hasPermission("gensprout.admin")) {
                        plugin.getLanguageManager().send(sender, "system.no-permission");
                        return true;
                    }
                    handleAdminCommands(sender, args);
                    return true;
                }
            }

            List<Player> targets = resolveTargets(sender, args, -1);
            if (targets.isEmpty()) {
                plugin.getLanguageManager().send(sender, "command.usage", LanguageManager.values("command", mainCmd));
                return true;
            }

            // Open main menu if no args or unrecognized player args
            for (Player target : targets) {
                plugin.getDialogManager().openMainMenu(target);
                if (!sender.equals(target)) {
                    plugin.getLanguageManager().send(sender, "command.opened.main", LanguageManager.values("player", target.getName()));
                }
            }
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
            plugin.reloadPlugin();
            plugin.getLanguageManager().send(sender, "system.reload-success");
            return;
        }

        if (sub.equals("definefarm")) {
            List<Player> targets = resolveTargets(sender, args, -1);
            if (targets.isEmpty()) {
                sender.sendMessage("Only players can use definefarm!");
                return;
            }
            Player player = targets.get(0);
            org.bukkit.inventory.ItemStack stick = plugin.getFarmManager().createSelectorStick();
            player.getInventory().addItem(stick).forEach((index, item) -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
            String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
            player.sendMessage(plugin.getMiniMessage().deserialize("<green>Received the Farm Selector Stick. Left-click a block to set Pos 1, and Right-click to set Pos 2. Run <gold>/" + mainCmd + " savefarm</gold> to define the region.</green>"));
            return;
        }

        if (sub.equals("savefarm")) {
            List<Player> targets = resolveTargets(sender, args, -1);
            if (targets.isEmpty()) {
                sender.sendMessage("Only players can use savefarm!");
                return;
            }
            Player player = targets.get(0);
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
                sender.sendMessage("Usage: /" + mainCmd + " givehoe <player/selector>");
                return;
            }
            List<Player> targets = resolveTargets(sender, args, 1);
            if (targets.isEmpty()) {
                sender.sendMessage("No players found matching target: " + args[1]);
                return;
            }
            for (Player target : targets) {
                ItemStack hoe = com.github.gensprout.farming.HoeEnchant.createBaseHoe(plugin, target);
                target.getInventory().addItem(hoe).forEach((index, item) -> target.getWorld().dropItemNaturally(target.getLocation(), item));
                sender.sendMessage(plugin.getMiniMessage().deserialize("<green>Gave a Sprout Hoe to " + target.getName() + ".</green>"));
                target.sendMessage(plugin.getMiniMessage().deserialize("<green>You received a Sprout Hoe from an admin.</green>"));
            }
            return;
        }

        if (sub.equals("givesellwand")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + mainCmd + " givesellwand <player/selector> [tier]");
                return;
            }
            List<Player> targets = resolveTargets(sender, args, 1);
            if (targets.isEmpty()) {
                sender.sendMessage("No players found matching target: " + args[1]);
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
            for (Player target : targets) {
                ItemStack wand = com.github.gensprout.economy.SellWand.createSellWand(plugin, tier);
                target.getInventory().addItem(wand).forEach((index, item) -> target.getWorld().dropItemNaturally(target.getLocation(), item));
                sender.sendMessage(plugin.getMiniMessage().deserialize("<green>Gave a Sell Wand (Tier " + tier + ") to " + target.getName() + ".</green>"));
                target.sendMessage(plugin.getMiniMessage().deserialize("<green>You received a Sell Wand from an admin.</green>"));
            }
            return;
        }

        if (sub.equals("clearstats")) {
            if (args.length < 2) {
                sender.sendMessage("Usage: /" + mainCmd + " clearstats <player/selector>");
                return;
            }
            List<Player> targets = resolveTargets(sender, args, 1);
            if (!targets.isEmpty()) {
                for (Player target : targets) {
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
                }
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
                    sender.sendMessage("No players found matching target: " + args[1]);
                }
            }
            return;
        }

        if (args.length < 3) {
            sender.sendMessage("Usage: /" + mainCmd + " <givegen|givegenslots|addxp|addessence|removeessence|setessence|addmoney|setlevel|setprestige> <player/selector> <amount/tier> [amount]");
            return;
        }

        List<Player> targets = resolveTargets(sender, args, 1);
        if (targets.isEmpty()) {
            sender.sendMessage("No players found matching target: " + args[1]);
            return;
        }

        for (Player target : targets) {
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
    }

    private void handleStartCommand(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        boolean isTester = player.hasPermission("gensprout.tester");
        if (!isTester && data.hasCompletedTutorial()) {
            plugin.getLanguageManager().send(player, "command.start.already-completed");
            return;
        }

        data.setCompletedTutorial(true);
        plugin.getPlayerManager().savePlayer(player.getUniqueId());

        // Starter items: 1x Sprout Hoe + default max generators (20)
        ItemStack sproutHoe = com.github.gensprout.farming.HoeEnchant.createBaseHoe(plugin, player);
        player.getInventory().addItem(sproutHoe).forEach((index, item) -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        int defaultSlots = plugin.getGeneratorManager().getDefaultSlots();
        plugin.getGeneratorManager().giveGenerator(player, 1, defaultSlots);

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
        plugin.getLanguageManager().send(player, "command.start.success", LanguageManager.values("amount", String.valueOf(defaultSlots)));

        executeStartCommands(player);
    }

    private void executeStartCommands(Player player) {
        List<String> playerCmds = plugin.getConfig().getStringList("start-command.player-commands");
        if (playerCmds.isEmpty()) {
            playerCmds = plugin.getConfig().getStringList("start-command.commands");
        }
        for (String rawCmd : playerCmds) {
            if (rawCmd == null || rawCmd.trim().isEmpty()) continue;
            String cmd = rawCmd.replace("{player}", player.getName()).trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            if (!cmd.isEmpty()) {
                player.performCommand(cmd);
            }
        }

        List<String> consoleCmds = plugin.getConfig().getStringList("start-command.console-commands");
        for (String rawCmd : consoleCmds) {
            if (rawCmd == null || rawCmd.trim().isEmpty()) continue;
            String cmd = rawCmd.replace("{player}", player.getName()).trim();
            if (cmd.startsWith("/")) {
                cmd = cmd.substring(1);
            }
            if (!cmd.isEmpty()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        List<String> list = new ArrayList<>();
        String mainCmd = plugin.getConfig().getString("commands.gensprout", "gensprout");
        
        boolean isMainCommand = command.getName().equalsIgnoreCase(mainCmd)
                || command.getName().equalsIgnoreCase("gensprout")
                || alias.equalsIgnoreCase("gs")
                || alias.equalsIgnoreCase("sprout");

        if (!isMainCommand) {
            if (command.getName().equalsIgnoreCase("help") || command.getName().equalsIgnoreCase("gensprouthelp") || command.getName().equalsIgnoreCase("sprouthelp")) {
                return list;
            }
            return list;
        }

        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("help", "start", "tutorial", "shop", "supplies", "bshop", "buildshop"));
            if (sender.hasPermission("gensprout.admin")) {
                options.addAll(Arrays.asList(
                        "givegen", "givegenslots", "addxp", "addessence", "removeessence",
                        "setessence", "addmoney", "setlevel", "setprestige", "givehoe",
                        "givesellwand", "clearstats", "reload", "definefarm", "savefarm"
                ));
            }
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], options, list);
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sender.hasPermission("gensprout.admin")) {
                if (sub.equals("givegen") || sub.equals("givegenslots") || sub.equals("addxp")
                        || sub.equals("addessence") || sub.equals("removeessence") || sub.equals("setessence")
                        || sub.equals("addmoney") || sub.equals("setlevel") || sub.equals("setprestige")
                        || sub.equals("givehoe") || sub.equals("givesellwand") || sub.equals("clearstats")) {
                    List<String> targets = new ArrayList<>(Arrays.asList("@p", "@a", "@r", "@s"));
                    targets.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    org.bukkit.util.StringUtil.copyPartialMatches(args[1], targets, list);
                }
            }
        } else if (args.length == 3 && sender.hasPermission("gensprout.admin")) {
            String sub = args[0].toLowerCase();
            if (sub.equals("givegen") || sub.equals("givesellwand")) {
                List<String> tiers = new ArrayList<>();
                int maxTier = plugin.getGeneratorManager().getMaxTier();
                for (int i = 1; i <= (maxTier > 0 ? maxTier : 25); i++) {
                    tiers.add(String.valueOf(i));
                }
                org.bukkit.util.StringUtil.copyPartialMatches(args[2], tiers, list);
            } else if (sub.equals("givegenslots") || sub.equals("addxp") || sub.equals("addessence")
                    || sub.equals("removeessence") || sub.equals("setessence") || sub.equals("addmoney")
                    || sub.equals("setlevel") || sub.equals("setprestige")) {
                List<String> samples = Arrays.asList("1", "5", "10", "50", "100");
                org.bukkit.util.StringUtil.copyPartialMatches(args[2], samples, list);
            }
        } else if (args.length == 4 && sender.hasPermission("gensprout.admin")) {
            String sub = args[0].toLowerCase();
            if (sub.equals("givegen")) {
                List<String> amounts = Arrays.asList("1", "8", "16", "32", "64");
                org.bukkit.util.StringUtil.copyPartialMatches(args[3], amounts, list);
            }
        }

        java.util.Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }
}
