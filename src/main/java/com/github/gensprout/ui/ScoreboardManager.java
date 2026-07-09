package com.github.gensprout.ui;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.ArrayList;
import java.util.List;

public class ScoreboardManager {

    private final GenSprout plugin;
    private BukkitTask task;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();

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
        // Restore main scoreboard on players to clean up sidebars
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
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
            }
        }
    }

    public void updateScoreboard(Player player) {
        if (!plugin.getConfig().getBoolean("scoreboard.enabled", true)) {
            if (player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            }
            return;
        }

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int activeGens = plugin.getGeneratorManager().getActiveCount(player.getUniqueId());
        int maxSlots = data.getMaxSlots(plugin.getGeneratorManager().getDefaultSlots());

        Scoreboard board = player.getScoreboard();
        if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
            board = Bukkit.getScoreboardManager().getNewScoreboard();
            player.setScoreboard(board);
        }

        String titleRaw = plugin.getConfig().getString("scoreboard.title", "<gradient:green:aqua><bold>GENSPROUT</bold></gradient>");
        Objective obj = board.getObjective("gensprout");
        if (obj == null) {
            obj = board.registerNewObjective("gensprout", "dummy", plugin.getMiniMessage().deserialize(titleRaw));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(plugin.getMiniMessage().deserialize(titleRaw));
        }

        // Reset old scores
        for (String entry : board.getEntries()) {
            board.resetScores(entry);
        }

        List<String> rawLines = plugin.getConfig().getStringList("scoreboard.lines");
        if (rawLines.isEmpty()) {
            rawLines = List.of(
                "<gray>-------------------</gray>",
                "<gray>Player: <yellow>{player}</yellow></gray>",
                "<gray>Level: <gold>{level}</gold></gray>",
                "<gray>XP: <gold>{xp}</gold></gray>",
                "<gray>Prestige: <gold>{prestige}</gold></gray>",
                "<gray>Essence: <light_purple>{essence}</light_purple></gray>",
                "<gray>Placed Gens: <gold>{placed_gens}/{max_gens}</gold></gray>",
                "<gray>Balance: <green>{balance}</green></gray>",
                "<gray>-------------------</gray>",
                "{server_ip}"
            );
        }

        List<Component> lines = new ArrayList<>();
        String serverIp = plugin.getConfig().getString("server.ip", "play.gensprout.net");
        String serverName = plugin.getConfig().getString("server.name", "<gradient:green:aqua><bold>GENSPROUT MC</bold></gradient>");

        for (String rawLine : rawLines) {
            String processed = rawLine
                .replace("{player}", player.getName())
                .replace("{level}", String.valueOf(data.getLevel()))
                .replace("{xp}", String.format("%.1f", data.getFarmingXp()))
                .replace("{prestige}", String.valueOf(data.getPrestige()))
                .replace("{essence}", String.valueOf(data.getEssence()))
                .replace("{placed_gens}", String.valueOf(activeGens))
                .replace("{max_gens}", String.valueOf(maxSlots))
                .replace("{balance}", EconomyHook.format(EconomyHook.getBalance(player)))
                .replace("{server_ip}", serverIp)
                .replace("{server_name}", serverName)
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{ping}", String.valueOf(player.getPing()));
            lines.add(plugin.getMiniMessage().deserialize(processed));
        }

        int score = lines.size();
        for (Component line : lines) {
            String legacyLine = legacySerializer.serialize(line);
            if (legacyLine.isEmpty()) {
                legacyLine = " ".repeat(score);
            }
            obj.getScore(legacyLine).setScore(score);
            score--;
        }
    }

    public void updateTabList(Player player) {
        if (!plugin.getConfig().getBoolean("tablist.enabled", true)) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
            return;
        }

        String rawHeader = plugin.getConfig().getString("tablist.header", "\n{server_name}\n{tagline}\n");
        String rawFooter = plugin.getConfig().getString("tablist.footer", "\n<gray>Online Players: <gold>{online}</gold> | Ping: <gold>{ping}ms</gold></gray>\n{server_ip}\n");

        String serverIp = plugin.getConfig().getString("server.ip", "play.gensprout.net");
        String serverName = plugin.getConfig().getString("server.name", "<gradient:green:aqua><bold>GENSPROUT MC</bold></gradient>");
        String tagline = plugin.getConfig().getString("server.tagline", "<gray>The ultimate optimized farming server</gray>");

        String headerProcessed = rawHeader
            .replace("{server_name}", serverName)
            .replace("{server_ip}", serverIp)
            .replace("{tagline}", tagline)
            .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("{ping}", String.valueOf(player.getPing()));

        String footerProcessed = rawFooter
            .replace("{server_name}", serverName)
            .replace("{server_ip}", serverIp)
            .replace("{tagline}", tagline)
            .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
            .replace("{ping}", String.valueOf(player.getPing()));

        player.sendPlayerListHeaderAndFooter(
            plugin.getMiniMessage().deserialize(headerProcessed),
            plugin.getMiniMessage().deserialize(footerProcessed)
        );
    }
}
