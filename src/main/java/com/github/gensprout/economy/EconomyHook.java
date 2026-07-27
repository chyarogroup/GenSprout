package com.github.gensprout.economy;

import com.github.gensprout.GenSprout;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;

public class EconomyHook {

    private static Economy econ = null;

    /**
     * Set up the Vault Economy provider connection.
     * Only requires Vault to be installed. If no third-party economy plugin (like EssentialsX)
     * is found, GenSprout automatically registers its own internal Vault Economy provider.
     * @return true if successfully linked, false if Vault itself is missing.
     */
    public static boolean setupEconomy(GenSprout plugin) {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().warning("==================================================");
            plugin.getLogger().warning("Vault plugin was not found!");
            plugin.getLogger().warning("Please install Vault: https://www.spigotmc.org/resources/vault.34315/");
            plugin.getLogger().warning("Economy operations will be unavailable until Vault is installed.");
            plugin.getLogger().warning("==================================================");
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp != null && rsp.getProvider() != null) {
            econ = rsp.getProvider();
            plugin.getLogger().info("Successfully hooked into external Vault Economy provider: " + econ.getName());
            return true;
        }

        // No external economy provider found: register GenSprout's built-in Vault Economy provider!
        GenSproutEconomy internalEconomy = new GenSproutEconomy(plugin);
        Bukkit.getServicesManager().register(Economy.class, internalEconomy, plugin, ServicePriority.Normal);
        econ = internalEconomy;
        plugin.getLogger().info("Registered internal GenSprout Economy provider into Vault!");
        return true;
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
