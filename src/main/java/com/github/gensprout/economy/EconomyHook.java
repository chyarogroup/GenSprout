package com.github.gensprout.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class EconomyHook {

    private static Economy econ = null;

    /**
     * Set up the Vault Economy provider connection.
     * @return true if successfully linked, false otherwise.
     */
    public static boolean setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static boolean isLinked() {
        return econ != null;
    }

    public static double getBalance(OfflinePlayer player) {
        if (!isLinked()) return 0.0;
        return econ.getBalance(player);
    }

    public static boolean has(OfflinePlayer player, double amount) {
        if (!isLinked()) return false;
        return econ.has(player, amount);
    }

    public static boolean withdraw(OfflinePlayer player, double amount) {
        if (!isLinked()) return false;
        return econ.withdrawPlayer(player, amount).transactionSuccess();
    }

    public static boolean deposit(OfflinePlayer player, double amount) {
        if (!isLinked()) return false;
        return econ.depositPlayer(player, amount).transactionSuccess();
    }

    public static String format(double amount) {
        if (!isLinked()) return String.format("$%.2f", amount);
        return econ.format(amount);
    }
}
