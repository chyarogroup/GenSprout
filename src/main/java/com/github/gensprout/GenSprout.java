package com.github.gensprout;

import com.github.gensprout.command.GenSproutCommand;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.farming.FarmingListener;
import com.github.gensprout.generator.GeneratorManager;
import com.github.gensprout.listener.GeneratorPlaceListener;
import com.github.gensprout.player.PlayerManager;
import com.github.gensprout.player.PlayerData;
import com.github.gensprout.farming.FarmManager;
import com.github.gensprout.ui.ScoreboardManager;
import com.github.gensprout.listener.FarmSelectorListener;
import com.github.gensprout.ui.DialogManager;
import com.github.gensprout.lang.LanguageManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.logging.Level;

public class GenSprout extends JavaPlugin implements Listener {

    private PlayerManager playerManager;
    private GeneratorManager generatorManager;
    private DialogManager dialogManager;
    private MiniMessage miniMessage;
    private FarmManager farmManager;
    private ScoreboardManager scoreboardManager;
    private FarmingListener farmingListener;
    private com.github.gensprout.lang.LanguageManager languageManager;

    @Override
    public void onEnable() {
        // Check for Paper 1.21.7+ Paper Dialog API compatibility
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
            Class.forName("io.papermc.paper.registry.data.dialog.action.DialogAction");
        } catch (Throwable t) {
            getLogger().severe("==================================================");
            getLogger().severe("GenSprout: This version of Paper is unsupported!");
            getLogger().severe("Paper Dialog UIs require Paper 1.21.7 or higher.");
            getLogger().severe("Please update your Paper server JAR to 1.21.7+: https://papermc.io/downloads/paper");
            getLogger().severe("==================================================");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Save default config
        saveDefaultConfig();

        // Migrate legacy data folder plugins/GenSprout/data/ -> plugins/GenSproutData/ if present
        migrateLegacyDataFolder();

        // Initialize MiniMessage & LanguageManager
        this.miniMessage = MiniMessage.miniMessage();
        this.languageManager = new com.github.gensprout.lang.LanguageManager(this);

        // Initialize Managers
        this.playerManager = new PlayerManager(this);
        this.generatorManager = new GeneratorManager(this);

        // Hook Vault Economy (only requires Vault)
        try {
            EconomyHook.setupEconomy(this);
        } catch (Throwable t) {
            getLogger().warning("Vault plugin was not found! Economy operations will be unavailable until Vault is installed.");
        }

        this.dialogManager = new DialogManager(this);
        this.farmManager = new FarmManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.farmingListener = new FarmingListener(this);

        // Register core event listener
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(farmingListener, this);
        Bukkit.getPluginManager().registerEvents(new GeneratorPlaceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.github.gensprout.listener.GeneratorDropProtectionListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FarmSelectorListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.github.gensprout.listener.SellWandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.github.gensprout.listener.AutoSellListener(this), this);

        // Register commands dynamically in CommandMap (Paper 1.21+ compatible)
        GenSproutCommand cmd = new GenSproutCommand(this);
        org.bukkit.command.CommandMap commandMap = Bukkit.getCommandMap();

        java.util.List<String> mainCmdList = getCommandConfig("gensprout", java.util.List.of("gensprout", "sprout", "gs"));
        String mainCmd = mainCmdList.get(0);
        java.util.List<String> mainAliases = mainCmdList.size() > 1 ? mainCmdList.subList(1, mainCmdList.size()) : java.util.List.of();
        commandMap.register(mainCmd, new DynamicCommand(mainCmd, "Main command for GenSprout", "/" + mainCmd, mainAliases, cmd, cmd));

        java.util.List<String> sellCmdList = getCommandConfig("sell", java.util.List.of("sell", "sellall"));
        String sellCmd = sellCmdList.get(0);
        java.util.List<String> sellAliases = sellCmdList.size() > 1 ? sellCmdList.subList(1, sellCmdList.size()) : java.util.List.of();
        commandMap.register(mainCmd, new DynamicCommand(sellCmd, "Sell all crop items in your inventory", "/" + sellCmd, sellAliases, cmd, cmd));

        java.util.List<String> genshopCmdList = getCommandConfig("genshop", java.util.List.of("genshop", "gshop", "generatorshop"));
        String genshopCmd = genshopCmdList.get(0);
        java.util.List<String> genshopAliases = genshopCmdList.size() > 1 ? genshopCmdList.subList(1, genshopCmdList.size()) : java.util.List.of();
        commandMap.register(mainCmd, new DynamicCommand(genshopCmd, "Open the generator shop directly", "/" + genshopCmd, genshopAliases, cmd, cmd));

        java.util.List<String> prestigeCmdList = getCommandConfig("prestige", java.util.List.of("prestige", "prestigemenu", "pmenu"));
        String prestigeCmd = prestigeCmdList.get(0);
        java.util.List<String> prestigeAliases = prestigeCmdList.size() > 1 ? prestigeCmdList.subList(1, prestigeCmdList.size()) : java.util.List.of();
        commandMap.register(mainCmd, new DynamicCommand(prestigeCmd, "Open the Prestige Menu", "/" + prestigeCmd, prestigeAliases, cmd, cmd));

        java.util.List<String> shopCmdList = getCommandConfig("shop", java.util.List.of("shop", "supplies", "bshop", "buildshop"));
        String shopCmd = shopCmdList.get(0);
        java.util.List<String> shopAliases = shopCmdList.size() > 1 ? shopCmdList.subList(1, shopCmdList.size()) : java.util.List.of();
        commandMap.register(mainCmd, new DynamicCommand(shopCmd, "Open the Shop", "/" + shopCmd, shopAliases, cmd, cmd));

        java.util.List<String> startCmdList = getCommandConfig("start", java.util.List.of("start", "sproutstart", "tutorial"));
        String startCmd = startCmdList.get(0);
        java.util.List<String> startAliases = startCmdList.size() > 1 ? startCmdList.subList(1, startCmdList.size()) : java.util.List.of();
        commandMap.register(mainCmd, new DynamicCommand(startCmd, "Start the GenSprout tutorial and receive starter items", "/" + startCmd, startAliases, cmd, cmd));

        java.util.List<String> helpCmdList = getCommandConfig("help", java.util.List.of("help", "gensprouthelp", "sprouthelp"));
        String helpCmdName = helpCmdList.get(0);
        java.util.List<String> helpAliases = helpCmdList.size() > 1 ? helpCmdList.subList(1, helpCmdList.size()) : java.util.List.of();
        DynamicCommand helpCmd = new DynamicCommand(helpCmdName, "View GenSprout starting guide & help tutorial", "/" + helpCmdName, helpAliases, cmd, cmd);
        commandMap.register(mainCmd, helpCmd);

        java.util.List<String> discordCmdList = getCommandConfig("discord", java.util.List.of("discord", "dc"));
        String discordCmdName = discordCmdList.get(0);
        java.util.List<String> discordAliases = discordCmdList.size() > 1 ? discordCmdList.subList(1, discordCmdList.size()) : java.util.List.of();
        DynamicCommand discordCmd = new DynamicCommand(discordCmdName, "View the server Discord link", "/" + discordCmdName, discordAliases, cmd, cmd);
        commandMap.register(mainCmd, discordCmd);

        // Override existing help & discord commands in Bukkit CommandMap
        try {
            java.util.Map<String, org.bukkit.command.Command> knownCommands = commandMap.getKnownCommands();
            for (String alias : helpCmdList) {
                knownCommands.put(alias.toLowerCase(), helpCmd);
            }
            knownCommands.put(mainCmd + ":" + helpCmdName, helpCmd);
            knownCommands.put("minecraft:help", helpCmd);
            knownCommands.put("bukkit:help", helpCmd);
            for (String alias : discordCmdList) {
                knownCommands.put(alias.toLowerCase(), discordCmd);
            }
            knownCommands.put(mainCmd + ":" + discordCmdName, discordCmd);
        } catch (Throwable t) {
            getLogger().warning("Could not override help command in command map: " + t.getMessage());
        }

        getLogger().info("GenSprout has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        // Stop Scoreboard and clean up sidebars
        if (scoreboardManager != null) {
            scoreboardManager.stopTask();
        }

        // Save player cache
        if (playerManager != null) {
            playerManager.saveAll();
        }

        // Save placed generators
        if (generatorManager != null) {
            generatorManager.saveGeneratorsSync();
        }

        getLogger().info("GenSprout has been disabled.");
    }

    public void openTutorialOrHelp(org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline()) return;
        if (getConfig().getBoolean("first-join-tutorial.use-dialog", true)) {
            dialogManager.openFirstJoinTutorialDialog(player);
        } else {
            java.util.List<String> tutorialLines = getConfig().getStringList("first-join-tutorial.messages");
            for (String line : tutorialLines) {
                player.sendMessage(languageManager.renderRaw(line, player, null));
            }
        }
    }

    public void sendDiscordLink(org.bukkit.entity.Player player) {
        if (player == null || !player.isOnline()) return;
        String link = getConfig().getString("server.discord", "https://discord.gg/bJjyy8q9wb");
        if (languageManager.hasKey("system.discord-link", player)) {
            languageManager.send(player, "system.discord-link", LanguageManager.values("link", link));
        } else {
            String raw = "<gradient:green:aqua><b>Discord:</b></gradient> <click:open_url:'" + link + "'><gold><u>" + link + "</u></gold></click>";
            player.sendMessage(languageManager.renderRaw(raw, player, null));
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerCommandPreprocess(org.bukkit.event.player.PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (message == null || message.trim().isEmpty()) return;

        String clean = message.trim();
        String commandLine = clean.startsWith("/") ? clean.substring(1) : clean;
        String[] parts = commandLine.split("\\s+", 2);
        String label = parts[0].toLowerCase();

        if (label.equals("help") || label.equals("gensprouthelp") || label.equals("sprouthelp")
                || label.equals("gensprout:help") || label.equals("minecraft:help") || label.equals("bukkit:help")) {
            event.setCancelled(true);
            openTutorialOrHelp(event.getPlayer());
        } else if (label.equals("discord") || label.equals("dc") || label.equals("gensprout:discord")) {
            event.setCancelled(true);
            sendDiscordLink(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Pre-load data into memory cache
        playerManager.getPlayerData(event.getPlayer().getUniqueId());

        boolean isFirstJoin = !event.getPlayer().hasPlayedBefore();

        // Send configurable MOTD on join
        if (getConfig().getBoolean("motd.enabled", true)) {
            long delay = getConfig().getLong("motd.delay-ticks", 15L);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (event.getPlayer().isOnline()) {
                    java.util.List<String> motdLines = getConfig().getStringList("motd.messages");
                    for (String line : motdLines) {
                        event.getPlayer().sendMessage(languageManager.renderRaw(line, event.getPlayer(), null));
                    }
                }
            }, delay);
        }

        // Send/show configurable First Join Tutorial
        if (isFirstJoin && getConfig().getBoolean("first-join-tutorial.enabled", true)) {
            long delay = getConfig().getLong("first-join-tutorial.delay-ticks", 30L);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (event.getPlayer().isOnline()) {
                    openTutorialOrHelp(event.getPlayer());
                }
            }, delay);
        }

        // Pay out offline generation earnings shortly after join (once economy/data is settled)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) {
                playerManager.processOfflineEarnings(event.getPlayer());
            }
        }, 20L);

        // Sync their personalized view of the shared farm's crop (based on their own Prestige)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) {
                com.github.gensprout.farming.FarmCropView.refreshRegionForPlayer(this, event.getPlayer(), farmManager.getActiveRegion());
            }
        }, 40L);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodLevelChange(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (!getConfig().getBoolean("disable-hunger", true)) return;
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            event.setCancelled(true);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (languageManager != null) {
            languageManager.invalidateLocale(event.getPlayer().getUniqueId());
        }
        // Record the moment they went offline (used to calculate offline-generation earnings)
        PlayerData data = playerManager.getCachedData(event.getPlayer().getUniqueId());
        if (data != null) {
            data.setLastSeen(System.currentTimeMillis());
        }

        // Save and unload from memory
        playerManager.unloadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerLocaleChange(org.bukkit.event.player.PlayerLocaleChangeEvent event) {
        Player player = event.getPlayer();
        if (player.isOnline()) {
            if (languageManager != null) {
                languageManager.invalidateLocale(player.getUniqueId());
            }
            if (scoreboardManager != null) {
                scoreboardManager.updateScoreboard(player);
                scoreboardManager.updateTabList(player);
            }
            refreshPlayerItemLocales(player);
        }
    }

    public void refreshPlayerItemLocales(Player player) {
        if (player == null || !player.isOnline()) return;
        org.bukkit.inventory.PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (item == null || item.getType() == Material.AIR) continue;

            if (com.github.gensprout.farming.HoeEnchant.isSproutHoe(item, this)) {
                com.github.gensprout.farming.HoeEnchant.rebuildLore(item, this, player);
            } else if (generatorManager != null && generatorManager.isGeneratorItem(item)) {
                generatorManager.rebuildGeneratorLore(item, player);
            } else if (com.github.gensprout.economy.SellWand.isSellWand(item, this)) {
                com.github.gensprout.economy.SellWand.rebuildLore(item, this, player);
            } else if (farmManager != null && farmManager.isSelectorStick(item)) {
                farmManager.rebuildSelectorStickLore(item, player);
            }
        }
    }

    public PlayerManager getPlayerManager() {
        return playerManager;
    }

    public GeneratorManager getGeneratorManager() {
        return generatorManager;
    }

    public DialogManager getDialogManager() {
        return dialogManager;
    }

    public FarmManager getFarmManager() {
        return farmManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public FarmingListener getFarmingListener() {
        return farmingListener;
    }

    public com.github.gensprout.lang.LanguageManager getLanguageManager() {
        return languageManager;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public void reloadPlugin() {
        reloadConfig();
        if (languageManager != null) {
            languageManager.loadLanguages();
        }
        if (generatorManager != null) {
            generatorManager.loadGenerators();
        }
        if (farmManager != null) {
            farmManager.loadFarmRegion();
        }
        if (scoreboardManager != null) {
            scoreboardManager.reload();
        }
    }

    public File getGenSproutDataFolder() {
        File dataFolder = new File(getDataFolder().getParentFile(), "GenSproutData");
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        return dataFolder;
    }

    public java.util.List<String> getCommandConfig(String key, java.util.List<String> defaultList) {
        java.util.List<String> list = getConfig().getStringList("commands." + key);
        if (list != null && !list.isEmpty()) {
            return list;
        }
        String single = getConfig().getString("commands." + key);
        if (single != null && !single.isEmpty()) {
            java.util.List<String> result = new java.util.ArrayList<>();
            result.add(single);
            for (String def : defaultList) {
                if (!def.equalsIgnoreCase(single)) {
                    result.add(def);
                }
            }
            return result;
        }
        return defaultList;
    }

    public String getMainCommandName() {
        return getCommandConfig("gensprout", java.util.List.of("gensprout", "sprout", "gs")).get(0);
    }

    private void migrateLegacyDataFolder() {
        File oldDataFolder = new File(getDataFolder(), "data");
        File newDataFolder = getGenSproutDataFolder();
        if (oldDataFolder.exists() && oldDataFolder.isDirectory()) {
            File[] oldFiles = oldDataFolder.listFiles();
            if (oldFiles != null) {
                for (File oldFile : oldFiles) {
                    File newFile = new File(newDataFolder, oldFile.getName());
                    if (!newFile.exists()) {
                        try {
                            java.nio.file.Files.move(oldFile.toPath(), newFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            getLogger().info("Migrated legacy data file " + oldFile.getName() + " to " + newDataFolder.getPath());
                        } catch (java.io.IOException e) {
                            getLogger().warning("Could not migrate legacy data file " + oldFile.getName() + ": " + e.getMessage());
                        }
                    }
                }
            }
        }
    }

    public EconomyHook getEconomyHook() {
        return new EconomyHook();
    }

    private static class DynamicCommand extends org.bukkit.command.Command {
        private final org.bukkit.command.CommandExecutor executor;
        private final org.bukkit.command.TabCompleter completer;

        public DynamicCommand(String name, String description, String usageMessage, java.util.List<String> aliases, org.bukkit.command.CommandExecutor executor, org.bukkit.command.TabCompleter completer) {
            super(name, description, usageMessage, aliases);
            this.executor = executor;
            this.completer = completer;
        }

        @Override
        public boolean execute(org.bukkit.command.CommandSender sender, String commandLabel, String[] args) {
            return executor.onCommand(sender, this, commandLabel, args);
        }

        @Override
        public java.util.List<String> tabComplete(org.bukkit.command.CommandSender sender, String alias, String[] args) throws IllegalArgumentException {
            if (completer != null) {
                return completer.onTabComplete(sender, this, alias, args);
            }
            return super.tabComplete(sender, alias, args);
        }
    }
}
