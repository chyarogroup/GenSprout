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
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

public class GenSprout extends JavaPlugin implements Listener {

    private PlayerManager playerManager;
    private GeneratorManager generatorManager;
    private DialogManager dialogManager;
    private MiniMessage miniMessage;
    private FarmManager farmManager;
    private ScoreboardManager scoreboardManager;
    private FarmingListener farmingListener;

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

        // Initialize MiniMessage
        this.miniMessage = MiniMessage.miniMessage();

        // Initialize Managers
        this.playerManager = new PlayerManager(this);
        this.generatorManager = new GeneratorManager(this);

        // Hook Vault Economy (only requires Vault)
        EconomyHook.setupEconomy(this);

        this.dialogManager = new DialogManager(this);
        this.farmManager = new FarmManager(this);
        this.scoreboardManager = new ScoreboardManager(this);
        this.farmingListener = new FarmingListener(this);

        // Register core event listener
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(farmingListener, this);
        Bukkit.getPluginManager().registerEvents(new GeneratorPlaceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FarmSelectorListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.github.gensprout.listener.SellWandListener(this), this);
        Bukkit.getPluginManager().registerEvents(new com.github.gensprout.listener.AutoSellListener(this), this);

        // Register commands dynamically in CommandMap (Paper 1.21+ compatible)
        GenSproutCommand cmd = new GenSproutCommand(this);
        org.bukkit.command.CommandMap commandMap = Bukkit.getCommandMap();
        String mainCmd = getConfig().getString("commands.gensprout", "gensprout");
        commandMap.register(mainCmd, new DynamicCommand(mainCmd, "Main command for GenSprout", "/" + mainCmd, java.util.List.of("gs", "sprout"), cmd, cmd));
        commandMap.register(mainCmd, new DynamicCommand("sell", "Sell all crop items in your inventory", "/sell", java.util.List.of("sellall"), cmd, cmd));
        commandMap.register(mainCmd, new DynamicCommand("genshop", "Open the generator shop directly", "/genshop", java.util.List.of("gshop"), cmd, cmd));
        commandMap.register(mainCmd, new DynamicCommand("prestige", "Open the Prestige Menu", "/prestige", java.util.List.of(), cmd, cmd));
        commandMap.register(mainCmd, new DynamicCommand("shop", "Open the Generator Building Supplies Shop", "/shop", java.util.List.of("supplies", "bshop", "buildshop"), cmd, cmd));
        commandMap.register(mainCmd, new DynamicCommand("start", "Start the GenSprout tutorial and receive starter items", "/start", java.util.List.of("sproutstart", "tutorial"), cmd, cmd));

        DynamicCommand helpCmd = new DynamicCommand("help", "View GenSprout starting guide & help tutorial", "/help", java.util.List.of("gensprouthelp", "sprouthelp"), cmd, cmd);
        commandMap.register(mainCmd, helpCmd);

        // Override existing help commands in Bukkit CommandMap
        try {
            java.util.Map<String, org.bukkit.command.Command> knownCommands = commandMap.getKnownCommands();
            knownCommands.put("help", helpCmd);
            knownCommands.put("gensprout:help", helpCmd);
            knownCommands.put("minecraft:help", helpCmd);
            knownCommands.put("bukkit:help", helpCmd);
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
            String serverName = getConfig().getString("server.name", "GenSprout");
            java.util.List<String> tutorialLines = getConfig().getStringList("first-join-tutorial.messages");
            for (String line : tutorialLines) {
                String formatted = line.replace("{servername}", serverName)
                                       .replace("{server_name}", serverName)
                                       .replace("{player}", player.getName());
                player.sendMessage(miniMessage.deserialize(formatted));
            }
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
                    String serverName = getConfig().getString("server.name", "GenSprout");
                    java.util.List<String> motdLines = getConfig().getStringList("motd.messages");
                    for (String line : motdLines) {
                        String formatted = line.replace("{servername}", serverName)
                                               .replace("{server_name}", serverName)
                                               .replace("{player}", event.getPlayer().getName());
                        event.getPlayer().sendMessage(miniMessage.deserialize(formatted));
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

        // Setup Pause Menu Links (Minecraft 1.21+ ServerLinks API)
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (event.getPlayer().isOnline()) {
                setupServerLinks(event.getPlayer());
            }
        }, 10L);
    }

    public void setupServerLinks(org.bukkit.entity.Player player) {
        if (!getConfig().getBoolean("pause-menu-links.enabled", true)) return;

        try {
            Class<?> linksClass = Class.forName("org.bukkit.ServerLinks");
            Object links = Bukkit.getServer().getClass().getMethod("getServerLinks").invoke(Bukkit.getServer());
            if (links != null) {
                java.lang.reflect.Method copyMethod = links.getClass().getMethod("copy");
                Object newLinks = copyMethod.invoke(links);

                java.util.List<java.util.Map<?, ?>> rawLinks = getConfig().getMapList("pause-menu-links.links");
                for (java.util.Map<?, ?> entry : rawLinks) {
                    Object labelObj = entry.get("label");
                    Object urlObj = entry.get("url");
                    if (labelObj != null && urlObj != null) {
                        String labelStr = String.valueOf(labelObj);
                        String urlStr = String.valueOf(urlObj);
                        try {
                            java.net.URI uri = java.net.URI.create(urlStr);
                            net.kyori.adventure.text.Component labelComp = miniMessage.deserialize(labelStr);

                            try {
                                java.lang.reflect.Method addLink = newLinks.getClass().getMethod("addLink", net.kyori.adventure.text.Component.class, java.net.URI.class);
                                addLink.invoke(newLinks, labelComp, uri);
                            } catch (NoSuchMethodException e) {
                                java.lang.reflect.Method addLink = newLinks.getClass().getMethod("addLink", String.class, java.net.URI.class);
                                addLink.invoke(newLinks, labelStr, uri);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                }

                java.lang.reflect.Method setLinks = player.getClass().getMethod("setServerLinks", linksClass);
                setLinks.invoke(player, newLinks);
            }
        } catch (Throwable ignored) {
        }
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Record the moment they went offline (used to calculate offline-generation earnings)
        PlayerData data = playerManager.getCachedData(event.getPlayer().getUniqueId());
        if (data != null) {
            data.setLastSeen(System.currentTimeMillis());
        }

        // Save and unload from memory
        playerManager.unloadPlayer(event.getPlayer().getUniqueId());
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

    public MiniMessage getMiniMessage() {
        return miniMessage;
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
