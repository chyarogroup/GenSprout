package com.github.gensprout.ui;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.economy.SellWand;
import com.github.gensprout.farming.HoeEnchant;
import com.github.gensprout.generator.GeneratorBlock;
import com.github.gensprout.generator.GeneratorType;
import com.github.gensprout.player.PlayerData;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.ActionButton;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public class DialogManager {

    private final GenSprout plugin;
    private final ClickCallback.Options clickOptions;

    public DialogManager(GenSprout plugin) {
        this.plugin = plugin;
        this.clickOptions = ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(java.time.Duration.ofDays(3650))
                .build();
    }

    private DialogAction action(DialogActionCallback callback) {
        return DialogAction.customClick((view, audience) -> {
            if (audience instanceof Player player) {
                // Ensure runs on main thread
                plugin.getServer().getScheduler().runTask(plugin, () -> callback.onClick(view, player));
            }
        }, clickOptions);
    }

    @FunctionalInterface
    public interface DialogActionCallback {
        void onClick(DialogResponseView view, Player player);
    }

    public void closeDialog(Player player) {
        if (player == null) return;
        com.github.gensprout.listener.GeneratorPlaceListener.setCloseCooldown(player.getUniqueId());
        player.closeDialog();
        player.closeInventory();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.closeInventory();
            }
        }, 1L);
    }

    public void openMainMenu(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int activeGens = plugin.getGeneratorManager().getActiveCount(player.getUniqueId());
        int maxSlots = data.getMaxSlots(plugin.getGeneratorManager().getDefaultSlots());

        String serverName = plugin.getConfig().getString("server.name", "GenSprout MC");
        String titleRaw = plugin.getConfig().getString("menus.main-menu-title", "<gradient:green:aqua><bold>{servername} Menu</bold></gradient>")
                .replace("{servername}", serverName)
                .replace("{server_name}", serverName)
                .replace("{player}", player.getName());
        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize(titleRaw))
                .body(List.of(
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Level: <gold>" + data.getLevel() + "</gold></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>XP: <gold>" + String.format("%.1f", data.getFarmingXp()) + "</gold></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Prestige: <gold>" + data.getPrestige() + "</gold> (<yellow>" + data.getPrestigePoints() + " Points</yellow>)</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Generator Slots: <gold>" + activeGens + "/" + maxSlots + "</gold></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Farming Essence: <light_purple>" + data.getEssence() + "</light_purple></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Balance: <green>" + EconomyHook.format(EconomyHook.getBalance(player)) + "</green></gray>"))
                ))
                .build();

        ActionButton closeBtn = ActionButton.builder(plugin.getMiniMessage().deserialize("<red>❌ Close Menu</red>"))
                .action(action((view, p) -> closeDialog(p)))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(
                        List.of(
                                ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Generator Shop</green>"))
                                        .action(action((view, p) -> openGeneratorShop(p)))
                                        .build(),
                                ActionButton.builder(plugin.getMiniMessage().deserialize("<blue>Building Shop</blue>"))
                                        .action(action((view, p) -> openSuppliesShopCategoryMenu(p)))
                                        .build(),
                                ActionButton.builder(plugin.getMiniMessage().deserialize("<yellow>Prestige Menu</yellow>"))
                                        .action(action((view, p) -> openPrestigeShop(p)))
                                        .build(),
                                ActionButton.builder(plugin.getMiniMessage().deserialize("<light_purple>Hoe Upgrades</light_purple>"))
                                        .action(action((view, p) -> openHoeUpgradeShop(p)))
                                        .build()
                        ),
                        closeBtn,
                        1
                ))
        );
        player.showDialog(dialog);
    }

    public void openGeneratorShop(Player player) {
        openGeneratorShop(player, null, 1, 1);
    }

    public void openGeneratorShop(Player player, String priceMessage, int initialTier, int initialQty) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int activeGens = plugin.getGeneratorManager().getActiveCount(player.getUniqueId());
        int maxSlots = data.getMaxSlots(plugin.getGeneratorManager().getDefaultSlots());

        List<DialogBody> bodyList = new java.util.ArrayList<>();
        bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Slots: <gold>" + activeGens + "/" + maxSlots + "</gold></gray>")));
        bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Balance: <green>" + EconomyHook.format(EconomyHook.getBalance(player)) + "</green></gray>")));
        if (priceMessage != null) {
            bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize(priceMessage)));
        }

        int maxTier = Math.max(1, plugin.getGeneratorManager().getMaxTier());
        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize("<gradient:#2ecc71:#00b894><bold>Generator Shop</bold></gradient>"))
                .body(bodyList)
                .inputs(List.of(
                        // Slider to select tier (1 to maxTier)
                        DialogInput.numberRange("tier", plugin.getMiniMessage().deserialize("Select Tier (1-" + maxTier + ")"), 1f, (float) maxTier)
                                .step(1f)
                                .initial((float) Math.min(initialTier, maxTier))
                                .build(),
                        // Slider to select quantity
                        DialogInput.numberRange("qty", plugin.getMiniMessage().deserialize("Quantity (1-64)"), 1f, 64f)
                                .step(1f)
                                .initial((float) initialQty)
                                .build()
                ))
                .build();

        List<ActionButton> buttons = List.of(
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Buy Selected</green>"))
                                .action(action((view, p) -> {
                                    int tier = view.getFloat("tier").intValue();
                                    int qty = view.getFloat("qty").intValue();
                                    GeneratorType type = plugin.getGeneratorManager().getTierConfig(tier);
                                    if (type == null) {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>Invalid generator tier selected!</red>"));
                                        openGeneratorShop(p, null, tier, qty);
                                        return;
                                    }
                                    double totalCost = type.getBuyPrice() * qty;
                                    if (EconomyHook.has(p, totalCost)) {
                                        EconomyHook.withdraw(p, totalCost);
                                        plugin.getGeneratorManager().giveGenerator(p, tier, qty);
                                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<green>Successfully bought " + qty + "x Tier " + tier + " Generator(s) for <gold>" + EconomyHook.format(totalCost) + "</gold>!</green>"));
                                        openGeneratorShop(p, null, tier, qty);
                                    } else {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>Insufficient funds! You need " + EconomyHook.format(totalCost) + ".</red>"));
                                        openGeneratorShop(p, "<red>Insufficient funds! Need " + EconomyHook.format(totalCost) + "</red>", tier, qty);
                                    }
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<aqua>Check Price</aqua>"))
                                .action(action((view, p) -> {
                                    int tier = view.getFloat("tier").intValue();
                                    int qty = view.getFloat("qty").intValue();
                                    GeneratorType type = plugin.getGeneratorManager().getTierConfig(tier);
                                    if (type == null) {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>Invalid generator tier selected!</red>"));
                                        openGeneratorShop(p, null, tier, qty);
                                    } else {
                                        double totalCost = type.getBuyPrice() * qty;
                                        String msg = "<yellow>Price for " + qty + "x Tier " + tier + " Gen: </yellow><green>" + EconomyHook.format(totalCost) + "</green>";
                                        openGeneratorShop(p, msg, tier, qty);
                                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                                    }
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize(
                                        "<gold>Buy Sell Wand (" + EconomyHook.format(SellWand.getPriceForTier(plugin, getLowestSellWandTier())) + ")</gold>"))
                                .action(action((view, p) -> {
                                    int tier = getLowestSellWandTier();
                                    double price = SellWand.getPriceForTier(plugin, tier);
                                    if (EconomyHook.has(p, price)) {
                                        EconomyHook.withdraw(p, price);
                                        ItemStack wand = SellWand.createSellWand(plugin, tier);
                                        p.getInventory().addItem(wand).forEach((index, it) -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<green>Purchased a Sell Wand!</green>"));
                                    } else {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>Insufficient funds! You need " + EconomyHook.format(price) + ".</red>"));
                                    }
                                    openGeneratorShop(p, null, view.getFloat("tier").intValue(), view.getFloat("qty").intValue());
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>⬅ Back to Main Menu</gray>"))
                                .action(action((view, p) -> openMainMenu(p)))
                                .build()
                );

        ActionButton closeBtn = ActionButton.builder(plugin.getMiniMessage().deserialize("<red>❌ Close Menu</red>"))
                .action(action((view, p) -> closeDialog(p)))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, closeBtn, 1))
        );
        player.showDialog(dialog);
    }

    /**
     * Returns the lowest-numbered Sell Wand tier defined in config (defaults to 1).
     */
    private int getLowestSellWandTier() {
        List<Integer> tiers = SellWand.getAvailableTiers(plugin);
        return tiers.isEmpty() ? 1 : tiers.get(0);
    }

    /**
     * Computes the essence cost to upgrade a hoe enchant from currentLevel to currentLevel + 1,
     * using the same "fast start, tapered growth" economy philosophy as the leveling system:
     * cost = base-cost + ((next_level ^ cost-exponent) * cost-multiplier)
     */
    private int getEnchantUpgradeCost(String configKey, int currentLevel) {
        int baseCost = plugin.getConfig().getInt("hoe-enchants." + configKey + ".base-cost", 100);
        double costMultiplier = plugin.getConfig().getDouble("hoe-enchants." + configKey + ".cost-multiplier", 50);
        double costExponent = plugin.getConfig().getDouble("hoe-enchants." + configKey + ".cost-exponent", 1.0);
        int nextLevel = currentLevel + 1;
        return (int) Math.round(baseCost + (Math.pow(nextLevel, costExponent) * costMultiplier));
    }

    public void openPrestigeShop(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int requiredLevel = plugin.getPlayerManager().getRequiredPrestigeLevel(data);

        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize("<gradient:yellow:gold><bold>Prestige Menu</bold></gradient>"))
                .body(List.of(
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Prestige Level: <gold>" + data.getPrestige() + "</gold></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Level required for next prestige: <gold>" + data.getLevel() + "/" + requiredLevel + "</gold></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Prestige Points: <yellow>" + data.getPrestigePoints() + "</yellow></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>XP Multiplier: <gold>+" + String.format("%.0f", (data.getXpMultiplierLevel() * 5.0)) + "%</gold> (<yellow>" + data.getXpMultiplierLevel() + "</yellow>)</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Money Multiplier: <gold>+" + String.format("%.0f", (data.getMoneyMultiplierLevel() * 5.0)) + "%</gold> (<yellow>" + data.getMoneyMultiplierLevel() + "</yellow>)</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Essence Multiplier: <gold>+" + String.format("%.0f", (data.getEssenceMultiplierLevel() * 5.0)) + "%</gold> (<yellow>" + data.getEssenceMultiplierLevel() + "</yellow>)</gray>"))
                ))
                .build();

        List<ActionButton> buttons = List.of(
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<yellow>Trigger Prestige</yellow>"))
                                .action(action((view, p) -> {
                                    if (data.getLevel() >= requiredLevel) {
                                        data.setLevel(1);
                                        data.setFarmingXp(0.0);
                                        data.setPrestige(data.getPrestige() + 1);
                                        data.setPrestigePoints(data.getPrestigePoints() + 1);
                                        plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<green><bold>PRESTIGED!</bold> Reset to Level 1. Slot count and prestige point increased!</green>"));
                                        // Instantly refresh their personalized view of the shared farm's crop, in case this prestige unlocked a new one
                                        com.github.gensprout.farming.FarmCropView.refreshRegionForPlayer(plugin, p, plugin.getFarmManager().getActiveRegion());
                                    } else {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>You must reach Farming Level " + requiredLevel + " to prestige!</red>"));
                                    }
                                    openPrestigeShop(p);
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<aqua>Buy XP Mult (+5%)</aqua>"))
                                .action(action((view, p) -> {
                                    if (data.getPrestigePoints() >= 1) {
                                        data.setPrestigePoints(data.getPrestigePoints() - 1);
                                        data.setXpMultiplierLevel(data.getXpMultiplierLevel() + 1);
                                        plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                                    } else {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>No prestige points available!</red>"));
                                    }
                                    openPrestigeShop(p);
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Buy Money Mult (+5%)</green>"))
                                .action(action((view, p) -> {
                                    if (data.getPrestigePoints() >= 1) {
                                        data.setPrestigePoints(data.getPrestigePoints() - 1);
                                        data.setMoneyMultiplierLevel(data.getMoneyMultiplierLevel() + 1);
                                        plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                                    } else {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>No prestige points available!</red>"));
                                    }
                                    openPrestigeShop(p);
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<light_purple>Buy Essence Mult (+5%)</light_purple>"))
                                .action(action((view, p) -> {
                                    if (data.getPrestigePoints() >= 1) {
                                        data.setPrestigePoints(data.getPrestigePoints() - 1);
                                        data.setEssenceMultiplierLevel(data.getEssenceMultiplierLevel() + 1);
                                        plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                                    } else {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>No prestige points available!</red>"));
                                    }
                                    openPrestigeShop(p);
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>⬅ Back to Main Menu</gray>"))
                                .action(action((view, p) -> openMainMenu(p)))
                                .build()
                );

        ActionButton closeBtn = ActionButton.builder(plugin.getMiniMessage().deserialize("<red>❌ Close Menu</red>"))
                .action(action((view, p) -> closeDialog(p)))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, closeBtn, 1))
        );
        player.showDialog(dialog);
    }

    public void openHoeUpgradeShop(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        ItemStack hoe = player.getInventory().getItemInMainHand();

        if (hoe.getType() == Material.AIR) {
            // Offer to purchase a Sprout Hoe
            DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize("<light_purple>Sprout Hoe Shop</light_purple>"))
                    .body(List.of(
                            DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<red>You must hold a Sprout Hoe to upgrade enchants!</red>")),
                            DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Purchase Sprout Hoe for $1,000.00?</gray>")),
                            DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Balance: <green>" + EconomyHook.format(EconomyHook.getBalance(player)) + "</green></gray>"))
                    ))
                    .build();

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(
                            ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Buy Sprout Hoe ($1,000)</green>"))
                                    .action(action((view, p) -> {
                                        if (EconomyHook.has(p, 1000.0)) {
                                            EconomyHook.withdraw(p, 1000.0);
                                            ItemStack sproutHoe = HoeEnchant.createBaseHoe(plugin);
                                            p.getInventory().addItem(sproutHoe).forEach((index, item) -> p.getWorld().dropItemNaturally(p.getLocation(), item));
                                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                            p.sendMessage(plugin.getMiniMessage().deserialize("<green>Bought Sprout Hoe!</green>"));
                                        } else {
                                            p.sendMessage(plugin.getMiniMessage().deserialize("<red>Insufficient funds!</red>"));
                                        }
                                        openHoeUpgradeShop(p);
                                    }))
                                    .build(),
                            ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>Back</gray>"))
                                    .action(action((view, p) -> openMainMenu(p)))
                                    .build()
                    )).build())
            );
            player.showDialog(dialog);
            return;
        }

        if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
            // Re-apply sprout hoe configuration to the held hoe if it's a normal hoe
            if (hoe.getType().name().endsWith("_HOE")) {
                HoeEnchant.rebuildLore(hoe, plugin);
                player.sendMessage(plugin.getMiniMessage().deserialize("<green>Initialized your held hoe as a Sprout Hoe!</green>"));
            } else {
                player.sendMessage(plugin.getMiniMessage().deserialize("<red>Hold a hoe to access upgrades!</red>"));
                player.closeDialog();
                return;
            }
        }

        // Holding sprout hoe: calculate current enchant levels and upgrade costs
        int xpLevel = HoeEnchant.XP_BOOSTER.getLevel(hoe, plugin);
        int essenceLevel = HoeEnchant.ESSENCE_FINDER.getLevel(hoe, plugin);
        int doublerLevel = HoeEnchant.CROP_DOUBLER.getLevel(hoe, plugin);
        int replenishLevel = HoeEnchant.REPLENISH.getLevel(hoe, plugin);
        int areaLevel = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);

        int xpCost = getEnchantUpgradeCost("xp_booster", xpLevel);
        int essenceCost = getEnchantUpgradeCost("essence_finder", essenceLevel);
        int doublerCost = getEnchantUpgradeCost("crop_doubler", doublerLevel);
        int replenishCost = plugin.getConfig().getInt("hoe-enchants.replenish.base-cost", 250);
        int areaCost = plugin.getConfig().getInt("hoe-enchants.harvest_area.base-cost", 500);

        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize("<gradient:light_purple:aqua><bold>Hoe Enchanting</bold></gradient>"))
                .body(List.of(
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Essence: <light_purple>" + data.getEssence() + "</light_purple></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>XP Booster: Level <gold>" + xpLevel + "/5</gold> (Cost: <light_purple>" + xpCost + " Essence</light_purple>)</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Essence Finder: Level <gold>" + essenceLevel + "/5</gold> (Cost: <light_purple>" + essenceCost + " Essence</light_purple>)</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Crop Doubler: Level <gold>" + doublerLevel + "/5</gold> (Cost: <light_purple>" + doublerCost + " Essence</light_purple>)</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Replenish: <gold>" + (replenishLevel > 0 ? "Unlocked" : "Locked") + "</gold> (Cost: <light_purple>" + replenishCost + " Essence</light_purple>)</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Harvest Area (3x3): <gold>" + (areaLevel > 0 ? "Unlocked" : "Locked") + "</gold> (Cost: <light_purple>" + areaCost + " Essence</light_purple>)</gray>"))
                 ))
                 .build();
 
        List<ActionButton> buttons = List.of(
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<yellow>XP Booster</yellow>"))
                                 .action(action((view, p) -> {
                                     ItemStack currentHoe = p.getInventory().getItemInMainHand();
                                     if (!HoeEnchant.isSproutHoe(currentHoe, plugin)) {
                                         p.sendMessage(plugin.getMiniMessage().deserialize("<red>Hold a Sprout Hoe to upgrade!</red>"));
                                     } else if (xpLevel < 5) {
                                         if (data.removeEssence(xpCost)) {
                                             HoeEnchant.XP_BOOSTER.setLevel(currentHoe, xpLevel + 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already max level!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<light_purple>Essence Finder</light_purple>"))
                                 .action(action((view, p) -> {
                                     ItemStack currentHoe = p.getInventory().getItemInMainHand();
                                     if (!HoeEnchant.isSproutHoe(currentHoe, plugin)) {
                                         p.sendMessage(plugin.getMiniMessage().deserialize("<red>Hold a Sprout Hoe to upgrade!</red>"));
                                     } else if (essenceLevel < 5) {
                                         if (data.removeEssence(essenceCost)) {
                                             HoeEnchant.ESSENCE_FINDER.setLevel(currentHoe, essenceLevel + 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already max level!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Crop Doubler</green>"))
                                 .action(action((view, p) -> {
                                     ItemStack currentHoe = p.getInventory().getItemInMainHand();
                                     if (!HoeEnchant.isSproutHoe(currentHoe, plugin)) {
                                         p.sendMessage(plugin.getMiniMessage().deserialize("<red>Hold a Sprout Hoe to upgrade!</red>"));
                                     } else if (doublerLevel < 5) {
                                         if (data.removeEssence(doublerCost)) {
                                             HoeEnchant.CROP_DOUBLER.setLevel(currentHoe, doublerLevel + 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already max level!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<aqua>Replenish</aqua>"))
                                 .action(action((view, p) -> {
                                     ItemStack currentHoe = p.getInventory().getItemInMainHand();
                                     if (!HoeEnchant.isSproutHoe(currentHoe, plugin)) {
                                         p.sendMessage(plugin.getMiniMessage().deserialize("<red>Hold a Sprout Hoe to upgrade!</red>"));
                                     } else if (replenishLevel == 0) {
                                         if (data.removeEssence(replenishCost)) {
                                             HoeEnchant.REPLENISH.setLevel(currentHoe, 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already unlocked!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<gold>Harvest Area</gold>"))
                                 .action(action((view, p) -> {
                                     ItemStack currentHoe = p.getInventory().getItemInMainHand();
                                     if (!HoeEnchant.isSproutHoe(currentHoe, plugin)) {
                                         p.sendMessage(plugin.getMiniMessage().deserialize("<red>Hold a Sprout Hoe to upgrade!</red>"));
                                     } else if (areaLevel == 0) {
                                         if (data.removeEssence(areaCost)) {
                                             HoeEnchant.HARVEST_AREA.setLevel(currentHoe, 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already unlocked!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>⬅ Back to Main Menu</gray>"))
                                 .action(action((view, p) -> openMainMenu(p)))
                                 .build()
                 );

         ActionButton closeBtn = ActionButton.builder(plugin.getMiniMessage().deserialize("<red>❌ Close Menu</red>"))
                 .action(action((view, p) -> closeDialog(p)))
                 .build();

         Dialog dialog = Dialog.create(builder -> builder.empty()
                 .base(base)
                 .type(DialogType.multiAction(buttons, closeBtn, 1))
         );
         player.showDialog(dialog);
     }

    public void openGeneratorBlockControl(Player player, GeneratorBlock gen) {
        GeneratorType type = plugin.getGeneratorManager().getTierConfig(gen.getTier());
        if (type == null) return;

        GeneratorType nextType = plugin.getGeneratorManager().getTierConfig(gen.getTier() + 1);
        double upgradeCost = type.getUpgradePrice();

        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize("<gradient:gold:yellow><bold>Generator Control</bold></gradient>"))
                .body(List.of(
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Generator Tier: <gold>" + gen.getTier() + "/25</gold></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Drop Value: <green>" + EconomyHook.format(type.getDropValue()) + "</green></gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Next Tier Upgrade Cost: " + (nextType == null ? "<red>MAX TIER</red>" : "<green>" + EconomyHook.format(upgradeCost) + "</green>") + "</gray>"))
                ))
                .build();

        List<ActionButton> buttons = List.of(
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<yellow>Upgrade Tier</yellow>"))
                                .action(action((view, p) -> {
                                    if (nextType != null) {
                                        if (EconomyHook.has(p, upgradeCost)) {
                                            EconomyHook.withdraw(p, upgradeCost);
                                            gen.setTier(gen.getTier() + 1);
                                            gen.getLocation().getBlock().setType(nextType.getBlockType());
                                            plugin.getGeneratorManager().saveGenerators();

                                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                            p.sendMessage(plugin.getMiniMessage().deserialize("<green>Upgraded Generator to Tier " + gen.getTier() + "!</green>"));
                                        } else {
                                            p.sendMessage(plugin.getMiniMessage().deserialize("<red>Insufficient funds! Upgrade costs " + EconomyHook.format(upgradeCost) + ".</red>"));
                                        }
                                    } else {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>Generator is already at maximum tier!</red>"));
                                    }
                                    openGeneratorBlockControl(p, gen);
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<gold>Pick Up Generator</gold>"))
                                .action(action((view, p) -> {
                                    // Remove and give item
                                    ItemStack item = plugin.getGeneratorManager().createGeneratorItem(gen.getTier(), 1);
                                    plugin.getGeneratorManager().removeGenerator(gen.getLocation());
                                    plugin.getGeneratorManager().saveGenerators();

                                    p.getInventory().addItem(item).forEach((index, it) -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                                    p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
                                    p.sendMessage(plugin.getMiniMessage().deserialize("<green>Picked up your generator block!</green>"));
                                    closeDialog(p);
                                }))
                                .build()
                );

        ActionButton closeBtn = ActionButton.builder(plugin.getMiniMessage().deserialize("<red>❌ Close Menu</red>"))
                .action(action((view, p) -> closeDialog(p)))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, closeBtn, 1))
        );
        player.showDialog(dialog);
    }

    public void openSuppliesShopCategoryMenu(Player player) {
        if (!plugin.getConfig().getBoolean("generator-supplies-shop.enabled", true)) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>Building Shop is currently disabled!</red>"));
            closeDialog(player);
            return;
        }

        org.bukkit.configuration.ConfigurationSection categoriesSec = plugin.getConfig().getConfigurationSection("generator-supplies-shop.categories");
        if (categoriesSec == null) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>No supplies categories configured!</red>"));
            closeDialog(player);
            return;
        }

        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize("<gradient:#4ea8de:#48bfe3><bold>Building Shop</bold></gradient>"))
                .body(List.of(
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Select a category to browse generator building materials.</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Balance: <green>" + EconomyHook.format(EconomyHook.getBalance(player)) + "</green></gray>"))
                ))
                .build();

        List<ActionButton> buttons = new java.util.ArrayList<>();
        for (String catKey : categoriesSec.getKeys(false)) {
            String name = plugin.getConfig().getString("generator-supplies-shop.categories." + catKey + ".display-name", catKey);
            buttons.add(ActionButton.builder(plugin.getMiniMessage().deserialize(name))
                    .action(action((view, p) -> openSuppliesCategoryItemsMenu(p, catKey)))
                    .build());
        }

        ActionButton backBtn = ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>⬅ Back to Main Menu</gray>"))
                .action(action((view, p) -> openMainMenu(p)))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, backBtn, 1))
        );
        player.showDialog(dialog);
    }

    public void openSuppliesCategoryItemsMenu(Player player, String categoryKey) {
        String catName = plugin.getConfig().getString("generator-supplies-shop.categories." + categoryKey + ".display-name", categoryKey);
        org.bukkit.configuration.ConfigurationSection itemsSec = plugin.getConfig().getConfigurationSection("generator-supplies-shop.categories." + categoryKey + ".items");

        if (itemsSec == null) {
            player.sendMessage(plugin.getMiniMessage().deserialize("<red>No items found in this category!</red>"));
            openSuppliesShopCategoryMenu(player);
            return;
        }

        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize(catName))
                .body(List.of(
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Select an item to purchase:</gray>")),
                        DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Balance: <green>" + EconomyHook.format(EconomyHook.getBalance(player)) + "</green></gray>"))
                ))
                .build();

        List<ActionButton> buttons = new java.util.ArrayList<>();
        for (String itemKey : itemsSec.getKeys(false)) {
            String itemName = plugin.getConfig().getString("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".display-name", itemKey);
            double price = plugin.getConfig().getDouble("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".price", 1.0);

            buttons.add(ActionButton.builder(plugin.getMiniMessage().deserialize(itemName + " <gray>($" + String.format("%.2f", price) + " ea)</gray>"))
                    .action(action((view, p) -> openSuppliesItemBuyDialog(p, categoryKey, itemKey, null, 1)))
                    .build());
        }

        ActionButton backBtn = ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>⬅ Back to Categories</gray>"))
                .action(action((view, p) -> openSuppliesShopCategoryMenu(p)))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, backBtn, 1))
        );
        player.showDialog(dialog);
    }

    public void openSuppliesItemBuyDialog(Player player, String categoryKey, String itemKey, String statusMsg, int initialQty) {
        String itemName = plugin.getConfig().getString("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".display-name", itemKey);
        String matName = plugin.getConfig().getString("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".material", "COBBLESTONE");
        double unitPrice = plugin.getConfig().getDouble("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".price", 1.0);

        List<DialogBody> bodyList = new java.util.ArrayList<>();
        bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Item: " + itemName + "</gray>")));
        bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Unit Price: <green>" + EconomyHook.format(unitPrice) + "</green></gray>")));
        bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Balance: <green>" + EconomyHook.format(EconomyHook.getBalance(player)) + "</green></gray>")));
        if (statusMsg != null) {
            bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize(statusMsg)));
        }

        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize("<gradient:#4ea8de:#48bfe3><bold>Buy Item</bold></gradient>"))
                .body(bodyList)
                .inputs(List.of(
                        DialogInput.numberRange("qty", plugin.getMiniMessage().deserialize("Quantity (1-64)"), 1f, 64f)
                                .step(1f)
                                .initial((float) Math.max(1, Math.min(64, initialQty)))
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Buy Selected</green>"))
                                .action(action((view, p) -> {
                                    int qty = Math.max(1, Math.min(64, view.getFloat("qty").intValue()));
                                    double totalCost = unitPrice * qty;
                                    Material mat = Material.matchMaterial(matName);
                                    if (mat == null) mat = Material.COBBLESTONE;

                                    if (EconomyHook.has(p, totalCost)) {
                                        EconomyHook.withdraw(p, totalCost);
                                        ItemStack stack = new ItemStack(mat, qty);
                                        p.getInventory().addItem(stack).forEach((index, item) -> p.getWorld().dropItemNaturally(p.getLocation(), item));
                                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<green>Successfully purchased " + qty + "x " + itemName + " for <gold>" + EconomyHook.format(totalCost) + "</gold>!</green>"));
                                        openSuppliesItemBuyDialog(p, categoryKey, itemKey, "<green>Purchased " + qty + "x for " + EconomyHook.format(totalCost) + "!</green>", qty);
                                    } else {
                                        p.sendMessage(plugin.getMiniMessage().deserialize("<red>Insufficient funds! Need " + EconomyHook.format(totalCost) + ".</red>"));
                                        openSuppliesItemBuyDialog(p, categoryKey, itemKey, "<red>Insufficient funds! Need " + EconomyHook.format(totalCost) + "</red>", qty);
                                    }
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<aqua>Check Total Price</aqua>"))
                                .action(action((view, p) -> {
                                    int qty = Math.max(1, Math.min(64, view.getFloat("qty").intValue()));
                                    double totalCost = unitPrice * qty;
                                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                                    openSuppliesItemBuyDialog(p, categoryKey, itemKey, "<yellow>Total Price for " + qty + "x: </yellow><green>" + EconomyHook.format(totalCost) + "</green>", qty);
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>Back to Items</gray>"))
                                .action(action((view, p) -> openSuppliesCategoryItemsMenu(p, categoryKey)))
                                .build()
                )).build())
        );
        player.showDialog(dialog);
    }

    public void openFirstJoinTutorialDialog(Player player) {
        String serverName = plugin.getConfig().getString("server.name", "GenSprout");
        String titleRaw = plugin.getConfig().getString("first-join-tutorial.dialog-title", "<white><bold>{servername} Starter Guide</bold></white>")
                .replace("{servername}", serverName)
                .replace("{server_name}", serverName)
                .replace("{player}", player.getName());

        List<String> msgList = plugin.getConfig().getStringList("first-join-tutorial.messages");

        List<DialogBody> bodyList = new java.util.ArrayList<>();
        if (msgList.isEmpty()) {
            bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize("<gray>Welcome to " + serverName + "! Type <yellow>/gensprout</yellow> to get started.</gray>")));
        } else {
            for (String msg : msgList) {
                String formatted = msg.replace("{servername}", serverName)
                                      .replace("{server_name}", serverName)
                                      .replace("{player}", player.getName());
                bodyList.add(DialogBody.plainMessage(plugin.getMiniMessage().deserialize(formatted)));
            }
        }

        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize(titleRaw))
                .body(bodyList)
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Got It! Start Playing</green>"))
                                .action(action((view, p) -> {
                                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                    closeDialog(p);
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>Close</gray>"))
                                .action(action((view, p) -> closeDialog(p)))
                                .build()
                )).build())
        );
        player.showDialog(dialog);
    }
}
