package com.github.gensprout.ui;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.lang.LanguageManager;
import com.github.gensprout.player.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ScoreboardManager {

    /**
     * Sidebar entry keys are built from legacy colour codes, which gives 16 usable invisible keys.
     * Lines beyond this are dropped rather than colliding with an existing entry.
     */
    private static final int MAX_SIDEBAR_LINES = 16;

    private final GenSprout plugin;
    private BukkitTask task;

    public ScoreboardManager(GenSprout plugin) {
        this.plugin = plugin;
        startUpdateTask();
    }

    public void startUpdateTask() {
        if (task != null) {
            task.cancel();
        }
        int intervalTicks = plugin.getConfig().getInt("scoreboard.update-interval-ticks", 20);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateScoreboard(player);
                updateTabList(player);
            }
        }, 40L, intervalTicks);
    }

    public void stopTask() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            player.playerListName(null);
        }
    }

    public void reload() {
        startUpdateTask();
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                    player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                }
            }
        }
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
                player.playerListName(null);
            }
        }
    }

    /**
     * Every placeholder a scoreboard or tab list line may use. Server-wide values such as
     * {@code servername} and {@code online} are added by the language layer, so only the
     * player-specific values are built here.
     */
    private Map<String, String> buildPlaceholders(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int activeGens = plugin.getGeneratorManager().getActiveCount(player.getUniqueId());
        int maxSlots = data.getMaxSlots(plugin.getGeneratorManager().getDefaultSlots());
        String playerPrefix = getPlayerPrefix(player);

        return LanguageManager.values(
                "player", player.getName(),
                "player_prefix", playerPrefix,
                "vault_prefix", playerPrefix,
                "level", String.valueOf(data.getLevel()),
                "xp", String.format("%.1f", data.getFarmingXp()),
                "prestige", String.valueOf(data.getPrestige()),
                "points", String.valueOf(data.getPrestigePoints()),
                "essence", String.valueOf(data.getEssence()),
                "placed_gens", String.valueOf(activeGens),
                "max_gens", String.valueOf(maxSlots),
                "balance", EconomyHook.format(EconomyHook.getBalance(player))
        );
    }

    private String getPlayerPrefix(Player player) {
        String rawPrefix = "";
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                org.bukkit.plugin.RegisteredServiceProvider<net.milkbowl.vault.chat.Chat> rsp =
                        Bukkit.getServicesManager().getRegistration(net.milkbowl.vault.chat.Chat.class);
                if (rsp != null && rsp.getProvider() != null) {
                    net.milkbowl.vault.chat.Chat vaultChat = rsp.getProvider();
                    rawPrefix = vaultChat.getPlayerPrefix(player);
                }
            } catch (Throwable ignored) {
            }
        }

        if (rawPrefix == null || rawPrefix.isEmpty()) {
            try {
                Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
                if (team == null && player.getScoreboard() != null) {
                    team = player.getScoreboard().getEntryTeam(player.getName());
                }
                if (team != null) {
                    Component teamPrefix = team.prefix();
                    rawPrefix = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(teamPrefix);
                }
            } catch (Throwable ignored) {
            }
        }

        if (rawPrefix == null || rawPrefix.isEmpty()) {
            return "";
        }

        try {
            Component prefixComp = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand()
                    .deserialize(rawPrefix.replace('§', '&'));
            return plugin.getMiniMessage().serialize(prefixComp);
        } catch (Throwable t) {
            return rawPrefix;
        }
    }

    public void updateScoreboard(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            if (player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }

        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        Map<String, String> placeholders = buildPlaceholders(player);

        Component title = plugin.getLanguageManager().getComponent("scoreboard.title", player, placeholders);

        Objective obj = board.getObjective("gensprout");
        if (obj == null) {
            obj = board.registerNewObjective("gensprout", "dummy", title);
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(title);
        }

        List<Component> lines = plugin.getLanguageManager().getComponentList("scoreboard.lines", player, placeholders);
        if (lines.isEmpty()) {
            // Operators who customised the sidebar in config.yml before it moved into the language
            // files keep working; the language files take priority when they define the key.
            List<String> configLines = plugin.getConfig().getStringList("scoreboard.lines");
            lines = new ArrayList<>(configLines.size());
            for (String raw : configLines) {
                lines.add(plugin.getLanguageManager().renderRaw(raw, player, placeholders));
            }
        }

        if (lines.size() > MAX_SIDEBAR_LINES) {
            lines = lines.subList(0, MAX_SIDEBAR_LINES);
        }
        int totalLines = lines.size();

        // Remove teams and entries left behind when the line count shrinks
        for (int i = totalLines; i < MAX_SIDEBAR_LINES; i++) {
            board.resetScores(entryKey(i));
            Team stale = board.getTeam("gs_line_" + i);
            if (stale != null) {
                stale.unregister();
            }
        }

        for (int i = 0; i < totalLines; i++) {
            int score = totalLines - i;
            String teamName = "gs_line_" + i;
            String entryKey = entryKey(i);

            Team team = board.getTeam(teamName);
            if (team == null) {
                team = board.registerNewTeam(teamName);
                team.addEntry(entryKey);
            }

            team.prefix(lines.get(i));
            obj.getScore(entryKey).setScore(score);
        }
    }

    /**
     * An invisible, unique sidebar entry key for the given line index. Only the 16 colour codes are
     * used; the formatting codes that follow them in {@link ChatColor#values()} are not valid
     * standalone entries.
     */
    private String entryKey(int index) {
        return ChatColor.getByChar("0123456789abcdef".charAt(index % 16)) + "§r";
    }

    public void updateTabList(Player player) {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            player.playerListName(null);
            return;
        }

        Map<String, String> placeholders = buildPlaceholders(player);

        Component header = plugin.getLanguageManager().getComponent("scoreboard.tablist.header", player, placeholders);
        Component footer = plugin.getLanguageManager().getComponent("scoreboard.tablist.footer", player, placeholders);

        player.sendPlayerListHeaderAndFooter(header, footer);

        Component playerTabName = plugin.getLanguageManager().getComponent("scoreboard.tablist.player-name", player, placeholders);
        if (playerTabName.equals(Component.empty())) {
            String rawFormat = plugin.getConfig().getString("tablist.player-name", "<white>{player}</white> <gray>[P{prestige}]</gray>");
            playerTabName = plugin.getLanguageManager().renderRaw(rawFormat, player, placeholders);
        }
        player.playerListName(playerTabName);
    }
}
