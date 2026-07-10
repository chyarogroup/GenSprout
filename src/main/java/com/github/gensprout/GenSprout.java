package com.github.gensprout;

import com.github.gensprout.command.GenSproutCommand;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.farming.FarmingListener;
import com.github.gensprout.generator.GeneratorManager;
import com.github.gensprout.listener.GeneratorPlaceListener;
import com.github.gensprout.player.PlayerManager;
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

    @Override
    public void onEnable() {
        // Ensure running on Paper or Purpur (checking for Paper-exclusive class)
        try {
            Class.forName("io.papermc.paper.dialog.Dialog");
        } catch (ClassNotFoundException e) {
            getLogger().severe("==================================================");
            getLogger().severe("GenSprout is ONLY compatible with Paper or Purpur!");
            getLogger().severe("This plugin cannot run on Spigot or CraftBukkit.");
            getLogger().severe("Please switch to Paper/Purpur: https://papermc.io");
            getLogger().severe("==================================================");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Save default config
        saveDefaultConfig();

        // Initialize MiniMessage
        this.miniMessage = MiniMessage.miniMessage();

        // Hook Vault Economy
        if (!EconomyHook.setupEconomy()) {
            getLogger().log(Level.WARNING, "Vault and/or a compatible economy plugin (like EssentialsX) not found. Economy operations will be unavailable!");
        } else {
            getLogger().info("Successfully hooked into Vault Economy!");
        }

        // Initialize Managers
        this.playerManager = new PlayerManager(this);
        this.generatorManager = new GeneratorManager(this);
        this.dialogManager = new DialogManager(this);
        this.farmManager = new FarmManager(this);
        this.scoreboardManager = new ScoreboardManager(this);

        // Register core event listener
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new FarmingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new GeneratorPlaceListener(this), this);
        Bukkit.getPluginManager().registerEvents(new FarmSelectorListener(this), this);

        // Register commands dynamically in CommandMap (supports both plugin.yml and paper-plugin.yml loaders)
        GenSproutCommand cmd = new GenSproutCommand(this);
        org.bukkit.command.CommandMap commandMap = Bukkit.getCommandMap();
        String mainCmd = getConfig().getString("commands.gensprout", "gensprout");
        commandMap.register(mainCmd, new DynamicCommand(mainCmd, "Main command for GenSprout", "/" + mainCmd, java.util.List.of("gs", "sprout"), cmd, cmd));
        commandMap.register("sell", new DynamicCommand("sell", "Sell all crop items in your inventory", "/sell", java.util.List.of("sellall"), cmd, cmd));
        commandMap.register("genshop", new DynamicCommand("genshop", "Open the generator shop directly", "/genshop", java.util.List.of("gshop"), cmd, cmd));
        commandMap.register("prestige", new DynamicCommand("prestige", "Open the Prestige Menu", "/prestige", java.util.List.of(), cmd, cmd));

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
            generatorManager.saveGenerators();
        }

        getLogger().info("GenSprout has been disabled.");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Pre-load data into memory cache
        playerManager.getPlayerData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
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
