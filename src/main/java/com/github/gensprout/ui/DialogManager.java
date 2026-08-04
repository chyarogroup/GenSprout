package com.github.gensprout.ui;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.EconomyHook;
import com.github.gensprout.economy.SellWand;
import com.github.gensprout.farming.HoeEnchant;
import com.github.gensprout.generator.GeneratorBlock;
import com.github.gensprout.generator.GeneratorType;
import com.github.gensprout.lang.LanguageManager;
import com.github.gensprout.player.PlayerData;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.ActionButton;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DialogManager {

    /** Fallback price of a starter Sprout Hoe when a player opens the upgrade shop empty-handed. */
    private static final double STARTER_HOE_PRICE = 1000.0;

    private final GenSprout plugin;
    private final ClickCallback.Options clickOptions;

    public DialogManager(GenSprout plugin) {
        this.plugin = plugin;
        this.clickOptions = ClickCallback.Options.builder()
                .uses(ClickCallback.UNLIMITED_USES)
                .lifetime(java.time.Duration.ofDays(3650))
                .build();
    }

    private LanguageManager lang() {
        return plugin.getLanguageManager();
    }

    /**
     * True when the operator has not touched this config value, meaning the language files should
     * provide the text instead. A customised config value always wins, so servers that wrote their
     * own copy keep it.
     */
    private boolean isConfigDefault(String path) {
        Configuration defaults = plugin.getConfig().getDefaults();
        if (defaults == null) {
            return false;
        }
        Object def = defaults.get(path);
        return def != null && def.equals(plugin.getConfig().get(path));
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

    /** The player's current balance line, shared by every shop dialog. */
    private DialogBody balanceLine(Player player) {
        return DialogBody.plainMessage(lang().getComponent("dialog.main.stat-balance", player,
                LanguageManager.values("balance", EconomyHook.format(EconomyHook.getBalance(player)))));
    }

    private ActionButton closeButton(Player player) {
        return ActionButton.builder(lang().getComponent("dialog.common.button-close", player))
                .action(action((view, p) -> closeDialog(p)))
                .build();
    }

    private ActionButton backToMainButton(Player player) {
        return ActionButton.builder(lang().getComponent("dialog.common.button-back", player))
                .action(action((view, p) -> openMainMenu(p)))
                .build();
    }

    public void openMainMenu(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int activeGens = plugin.getGeneratorManager().getActiveCount(player.getUniqueId());
        int maxSlots = data.getMaxSlots(plugin.getGeneratorManager().getDefaultSlots());

        DialogBase base = DialogBase.builder(lang().getComponent("dialog.main.title", player))
                .body(List.of(
                        DialogBody.plainMessage(lang().getComponent("dialog.main.body", player)),
                        DialogBody.plainMessage(lang().getComponent("dialog.main.stat-level", player,
                                LanguageManager.values("level", String.valueOf(data.getLevel())))),
                        DialogBody.plainMessage(lang().getComponent("dialog.main.stat-xp", player,
                                LanguageManager.values("xp", String.format("%.1f", data.getFarmingXp())))),
                        DialogBody.plainMessage(lang().getComponent("dialog.main.stat-prestige", player,
                                LanguageManager.values(
                                        "prestige", String.valueOf(data.getPrestige()),
                                        "points", String.valueOf(data.getPrestigePoints())))),
                        DialogBody.plainMessage(lang().getComponent("dialog.main.stat-slots", player,
                                LanguageManager.values(
                                        "used", String.valueOf(activeGens),
                                        "max", String.valueOf(maxSlots)))),
                        DialogBody.plainMessage(lang().getComponent("dialog.main.stat-essence", player,
                                LanguageManager.values("essence", String.valueOf(data.getEssence())))),
                        balanceLine(player)
                ))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(
                        List.of(
                                ActionButton.builder(lang().getComponent("dialog.main.button-genshop", player))
                                        .action(action((view, p) -> openGeneratorShop(p)))
                                        .build(),
                                ActionButton.builder(lang().getComponent("dialog.main.button-supplies", player))
                                        .action(action((view, p) -> openSuppliesShopCategoryMenu(p)))
                                        .build(),
                                ActionButton.builder(lang().getComponent("dialog.main.button-prestige", player))
                                        .action(action((view, p) -> openPrestigeShop(p)))
                                        .build(),
                                ActionButton.builder(lang().getComponent("dialog.main.button-hoe", player))
                                        .action(action((view, p) -> openHoeUpgradeShop(p)))
                                        .build()
                        ),
                        closeButton(player),
                        1
                ))
        );
        player.showDialog(dialog);
    }

    public void openGeneratorShop(Player player) {
        openGeneratorShop(player, null, null, 1, 1);
    }

    /**
     * @param statusKey    language key for the status line shown under the stats, or null for none.
     *                     Carrying the key rather than a rendered string keeps the line in the
     *                     viewer's own language when the dialog reopens.
     * @param statusValues placeholder values for {@code statusKey}, may be null.
     */
    public void openGeneratorShop(Player player, String statusKey, Map<String, String> statusValues,
                                  int initialTier, int initialQty) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int activeGens = plugin.getGeneratorManager().getActiveCount(player.getUniqueId());
        int maxSlots = data.getMaxSlots(plugin.getGeneratorManager().getDefaultSlots());

        List<DialogBody> bodyList = new ArrayList<>();
        bodyList.add(DialogBody.plainMessage(lang().getComponent("dialog.genshop.body", player)));
        bodyList.add(DialogBody.plainMessage(lang().getComponent("dialog.main.stat-slots", player,
                LanguageManager.values(
                        "used", String.valueOf(activeGens),
                        "max", String.valueOf(maxSlots)))));
        bodyList.add(balanceLine(player));
        if (statusKey != null) {
            bodyList.add(DialogBody.plainMessage(lang().getComponent(statusKey, player, statusValues)));
        }

        int maxTier = Math.max(1, plugin.getGeneratorManager().getMaxTier());
        DialogBase base = DialogBase.builder(lang().getComponent("dialog.genshop.title", player))
                .body(bodyList)
                .inputs(List.of(
                        // Slider to select tier (1 to maxTier)
                        DialogInput.numberRange("tier", lang().getComponent("dialog.genshop.tier-select", player,
                                        LanguageManager.values("max_tier", String.valueOf(maxTier))), 1f, (float) maxTier)
                                .step(1f)
                                .initial((float) Math.min(initialTier, maxTier))
                                .build(),
                        // Slider to select quantity
                        DialogInput.numberRange("qty", lang().getComponent("dialog.genshop.qty-select", player), 1f, 64f)
                                .step(1f)
                                .initial((float) initialQty)
                                .build()
                ))
                .build();

        int slotCost = plugin.getConfig().getInt("essence-slots.cost-per-slot", 500);
        int maxEssenceSlots = plugin.getConfig().getInt("essence-slots.max-slots", 25);
        int currentEssenceSlots = data.getEssenceSlots();

        int sellWandCost = plugin.getConfig().getInt("sellwand.essence-cost", 1000);

        List<ActionButton> buttons = List.of(
                ActionButton.builder(lang().getComponent("dialog.genshop.buy-button", player))
                        .action(action((view, p) -> {
                            int tier = view.getFloat("tier").intValue();
                            int qty = view.getFloat("qty").intValue();
                            GeneratorType type = plugin.getGeneratorManager().getTierConfig(tier);
                            if (type == null) {
                                lang().send(p, "dialog.common.invalid-selection");
                                openGeneratorShop(p, null, null, tier, qty);
                                return;
                            }
                            double totalCost = type.getBuyPrice() * qty;
                            Map<String, String> costValues = LanguageManager.values("cost", EconomyHook.format(totalCost));
                            if (EconomyHook.has(p, totalCost)) {
                                EconomyHook.withdraw(p, totalCost);
                                plugin.getGeneratorManager().giveGenerator(p, tier, qty);
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                lang().send(p, "dialog.genshop.buy-success", LanguageManager.values(
                                        "amount", String.valueOf(qty),
                                        "tier", String.valueOf(tier),
                                        "cost", EconomyHook.format(totalCost)));
                                openGeneratorShop(p, null, null, tier, qty);
                            } else {
                                lang().send(p, "dialog.common.insufficient-funds", costValues);
                                openGeneratorShop(p, "dialog.common.insufficient-funds", costValues, tier, qty);
                            }
                        }))
                        .build(),
                ActionButton.builder(lang().getComponent("dialog.genshop.buy-essence-slot-button", player,
                                LanguageManager.values(
                                        "current", String.valueOf(currentEssenceSlots),
                                        "max", String.valueOf(maxEssenceSlots),
                                        "cost", String.valueOf(slotCost))))
                        .action(action((view, p) -> {
                            PlayerData pd = plugin.getPlayerManager().getPlayerData(p.getUniqueId());
                            int maxEssence = plugin.getConfig().getInt("essence-slots.max-slots", 25);
                            int cost = plugin.getConfig().getInt("essence-slots.cost-per-slot", 500);
                            if (pd.getEssenceSlots() >= maxEssence) {
                                lang().send(p, "dialog.genshop.error-max-essence-slots");
                            } else if (pd.removeEssence(cost)) {
                                pd.setEssenceSlots(pd.getEssenceSlots() + 1);
                                plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                lang().send(p, "dialog.genshop.buy-slot-success", LanguageManager.values(
                                        "slots", String.valueOf(pd.getEssenceSlots())));
                            } else {
                                lang().send(p, "dialog.hoe.error-no-essence",
                                        LanguageManager.values("cost", String.valueOf(cost)));
                            }
                            openGeneratorShop(p, null, null, view.getFloat("tier").intValue(), view.getFloat("qty").intValue());
                        }))
                        .build(),
                ActionButton.builder(lang().getComponent("dialog.genshop.buy-sellwand-button", player,
                                LanguageManager.values(
                                        "cost", String.valueOf(sellWandCost),
                                        "price", String.valueOf(sellWandCost))))
                        .action(action((view, p) -> {
                            PlayerData pd = plugin.getPlayerManager().getPlayerData(p.getUniqueId());
                            int cost = plugin.getConfig().getInt("sellwand.essence-cost", 1000);
                            if (pd.removeEssence(cost)) {
                                plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                ItemStack wand = SellWand.createSellWand(plugin, 1);
                                p.getInventory().addItem(wand).forEach((index, it) -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                lang().send(p, "dialog.genshop.buy-sellwand-success");
                            } else {
                                lang().send(p, "dialog.hoe.error-no-essence",
                                        LanguageManager.values("cost", String.valueOf(cost)));
                            }
                            openGeneratorShop(p, null, null, view.getFloat("tier").intValue(), view.getFloat("qty").intValue());
                        }))
                        .build()
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, closeButton(player), 1))
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
        int defaultBase = (configKey.equals("replenish") || configKey.equals("auto_sell") || configKey.equals("harvest_area")) ? 100 : 10;
        double defaultMult = (configKey.equals("replenish") || configKey.equals("auto_sell") || configKey.equals("harvest_area")) ? 50.0 : 2.0;
        int baseCost = plugin.getConfig().getInt("hoe-enchants." + configKey + ".base-cost", defaultBase);
        double costMultiplier = plugin.getConfig().getDouble("hoe-enchants." + configKey + ".cost-multiplier", defaultMult);
        double costExponent = plugin.getConfig().getDouble("hoe-enchants." + configKey + ".cost-exponent", 1.0);
        int nextLevel = currentLevel + 1;
        return (int) Math.round(baseCost + (Math.pow(nextLevel, costExponent) * costMultiplier));
    }

    public void openPrestigeShop(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int requiredLevel = plugin.getPlayerManager().getRequiredPrestigeLevel(data);

        DialogBase base = DialogBase.builder(lang().getComponent("dialog.prestige.title", player))
                .body(List.of(
                        DialogBody.plainMessage(lang().getComponent("dialog.prestige.body", player)),
                        DialogBody.plainMessage(lang().getComponent("dialog.prestige.stat-prestige", player,
                                LanguageManager.values("prestige", String.valueOf(data.getPrestige())))),
                        DialogBody.plainMessage(lang().getComponent("dialog.prestige.stat-req-level", player,
                                LanguageManager.values(
                                        "level", String.valueOf(data.getLevel()),
                                        "req_level", String.valueOf(requiredLevel)))),
                        DialogBody.plainMessage(lang().getComponent("dialog.prestige.stat-points", player,
                                LanguageManager.values("points", String.valueOf(data.getPrestigePoints())))),
                        DialogBody.plainMessage(lang().getComponent("dialog.prestige.stat-xp-mult", player,
                                LanguageManager.values(
                                        "percent", String.format("%.0f", data.getXpMultiplierLevel() * 5.0),
                                        "level", String.valueOf(data.getXpMultiplierLevel())))),
                        DialogBody.plainMessage(lang().getComponent("dialog.prestige.stat-money-mult", player,
                                LanguageManager.values(
                                        "percent", String.format("%.0f", data.getMoneyMultiplierLevel() * 5.0),
                                        "level", String.valueOf(data.getMoneyMultiplierLevel())))),
                        DialogBody.plainMessage(lang().getComponent("dialog.prestige.stat-essence-mult", player,
                                LanguageManager.values(
                                        "percent", String.format("%.0f", data.getEssenceMultiplierLevel() * 5.0),
                                        "level", String.valueOf(data.getEssenceMultiplierLevel()))))
                ))
                .build();

        List<ActionButton> buttons = List.of(
                ActionButton.builder(lang().getComponent("dialog.prestige.button-prestige", player,
                                LanguageManager.values("req_level", String.valueOf(requiredLevel))))
                        .action(action((view, p) -> {
                            PlayerData pd = plugin.getPlayerManager().getPlayerData(p.getUniqueId());
                            int required = plugin.getPlayerManager().getRequiredPrestigeLevel(pd);
                            if (pd.getLevel() >= required) {
                                pd.setLevel(1);
                                pd.setFarmingXp(0.0);
                                pd.setPrestige(pd.getPrestige() + 1);
                                pd.setPrestigePoints(pd.getPrestigePoints() + 1);
                                plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                                lang().send(p, "dialog.prestige.success");
                                // Instantly refresh their personalized view of the shared farm's crop, in case this prestige unlocked a new one
                                com.github.gensprout.farming.FarmCropView.refreshRegionForPlayer(plugin, p, plugin.getFarmManager().getActiveRegion());
                            } else {
                                lang().send(p, "dialog.prestige.error-level",
                                        LanguageManager.values("req_level", String.valueOf(required)));
                            }
                            openPrestigeShop(p);
                        }))
                        .build(),
                prestigeUpgradeButton(player, "dialog.prestige.button-buy-xp-mult", PrestigeUpgrade.XP),
                prestigeUpgradeButton(player, "dialog.prestige.button-buy-money-mult", PrestigeUpgrade.MONEY),
                prestigeUpgradeButton(player, "dialog.prestige.button-buy-essence-mult", PrestigeUpgrade.ESSENCE),
                backToMainButton(player)
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, closeButton(player), 1))
        );
        player.showDialog(dialog);
    }

    private enum PrestigeUpgrade { XP, MONEY, ESSENCE }

    private ActionButton prestigeUpgradeButton(Player viewer, String buttonKey, PrestigeUpgrade upgrade) {
        return ActionButton.builder(lang().getComponent(buttonKey, viewer))
                .action(action((view, p) -> {
                    PlayerData pd = plugin.getPlayerManager().getPlayerData(p.getUniqueId());
                    if (pd.getPrestigePoints() >= 1) {
                        pd.setPrestigePoints(pd.getPrestigePoints() - 1);
                        switch (upgrade) {
                            case XP -> pd.setXpMultiplierLevel(pd.getXpMultiplierLevel() + 1);
                            case MONEY -> pd.setMoneyMultiplierLevel(pd.getMoneyMultiplierLevel() + 1);
                            case ESSENCE -> pd.setEssenceMultiplierLevel(pd.getEssenceMultiplierLevel() + 1);
                        }
                        plugin.getPlayerManager().savePlayer(p.getUniqueId());
                        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                    } else {
                        lang().send(p, "dialog.prestige.error-no-points");
                    }
                    openPrestigeShop(p);
                }))
                .build();
    }

    public void openHoeUpgradeShop(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        ItemStack hoe = player.getInventory().getItemInMainHand();

        if (hoe.getType() == Material.AIR) {
            // Offer to purchase a Sprout Hoe
            Map<String, String> priceValues = LanguageManager.values("cost", EconomyHook.format(STARTER_HOE_PRICE));
            DialogBase base = DialogBase.builder(lang().getComponent("dialog.hoe.no-hoe-title", player))
                    .body(List.of(
                            DialogBody.plainMessage(lang().getComponent("dialog.hoe.no-hoe-body", player, priceValues)),
                            balanceLine(player)
                    ))
                    .build();

            Dialog dialog = Dialog.create(builder -> builder.empty()
                    .base(base)
                    .type(DialogType.multiAction(List.of(
                            ActionButton.builder(lang().getComponent("dialog.hoe.no-hoe-buy", player, priceValues))
                                    .action(action((view, p) -> {
                                        if (EconomyHook.has(p, STARTER_HOE_PRICE)) {
                                            EconomyHook.withdraw(p, STARTER_HOE_PRICE);
                                            ItemStack sproutHoe = HoeEnchant.createBaseHoe(plugin, p);
                                            p.getInventory().addItem(sproutHoe).forEach((index, item) -> p.getWorld().dropItemNaturally(p.getLocation(), item));
                                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                            lang().send(p, "dialog.hoe.buy-success");
                                        } else {
                                            lang().send(p, "dialog.common.insufficient-funds",
                                                    LanguageManager.values("cost", EconomyHook.format(STARTER_HOE_PRICE)));
                                        }
                                        openHoeUpgradeShop(p);
                                    }))
                                    .build(),
                            backToMainButton(player)
                    )).build())
            );
            player.showDialog(dialog);
            return;
        }

        if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
            // Re-apply sprout hoe configuration to the held hoe if it's a normal hoe
            if (hoe.getType().name().endsWith("_HOE")) {
                HoeEnchant.rebuildLore(hoe, plugin, player);
                lang().send(player, "dialog.hoe.initialized");
            } else {
                lang().send(player, "dialog.hoe.error-need-hoe");
                player.closeDialog();
                return;
            }
        }
        // Holding sprout hoe: calculate current enchant levels and upgrade costs
        int xpMax = plugin.getConfig().getInt("hoe-enchants.xp_booster.max-level", HoeEnchant.XP_BOOSTER.getMaxLevel());
        int essenceMax = plugin.getConfig().getInt("hoe-enchants.essence_finder.max-level", HoeEnchant.ESSENCE_FINDER.getMaxLevel());
        int doublerMax = plugin.getConfig().getInt("hoe-enchants.crop_doubler.max-level", HoeEnchant.CROP_DOUBLER.getMaxLevel());
        int reachMax = plugin.getConfig().getInt("hoe-enchants.reach.max-level", HoeEnchant.REACH.getMaxLevel());
        int areaMax = plugin.getConfig().getInt("hoe-enchants.harvest_area.max-level", HoeEnchant.HARVEST_AREA.getMaxLevel());

        int xpLevel = HoeEnchant.XP_BOOSTER.getLevel(hoe, plugin);
        int essenceLevel = HoeEnchant.ESSENCE_FINDER.getLevel(hoe, plugin);
        int doublerLevel = HoeEnchant.CROP_DOUBLER.getLevel(hoe, plugin);
        int replenishLevel = HoeEnchant.REPLENISH.getLevel(hoe, plugin);
        int areaLevel = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);
        int reachLevel = HoeEnchant.REACH.getLevel(hoe, plugin);
        int autoSellLevel = HoeEnchant.AUTO_SELL.getLevel(hoe, plugin);

        int replenishCost = plugin.getConfig().getInt("hoe-enchants.replenish.base-cost", 250);
        int areaCost = getEnchantUpgradeCost("harvest_area", areaLevel);
        int autoSellCost = plugin.getConfig().getInt("hoe-enchants.auto_sell.base-cost", 5000);

        // MAX, Unlocked and Locked are embedded inside other translated lines, so they are taken as
        // plain text from the language file rather than concatenated in English.
        String maxWord = lang().getPlainText("dialog.hoe.max", player);
        String unlockedWord = lang().getPlainText("dialog.hoe.unlocked", player);
        String lockedWord = lang().getPlainText("dialog.hoe.locked", player);

        boolean areaMaxed = areaLevel >= areaMax;
        String areaDim = harvestAreaDimension(areaMaxed ? areaLevel : areaLevel + 1);
        Component areaLine = lang().getComponent(
                areaMaxed ? "dialog.hoe.stat-harvest-area-max" : "dialog.hoe.stat-harvest-area", player,
                LanguageManager.values(
                        "level", String.valueOf(areaLevel),
                        "max_level", String.valueOf(areaMax),
                        "dimension", areaDim,
                        "cost", String.valueOf(areaCost)));

        DialogBase base = DialogBase.builder(lang().getComponent("dialog.hoe.title", player))
                .body(List.of(
                        DialogBody.plainMessage(lang().getComponent("dialog.hoe.body", player)),
                        DialogBody.plainMessage(lang().getComponent("dialog.hoe.stat-essence", player,
                                LanguageManager.values("essence", String.valueOf(data.getEssence())))),
                        DialogBody.plainMessage(enchantStatLine(player, "dialog.hoe.stat-xp-booster", xpLevel, xpMax,
                                getEnchantUpgradeCost("xp_booster", xpLevel), maxWord)),
                        DialogBody.plainMessage(enchantStatLine(player, "dialog.hoe.stat-essence-finder", essenceLevel, essenceMax,
                                getEnchantUpgradeCost("essence_finder", essenceLevel), maxWord)),
                        DialogBody.plainMessage(enchantStatLine(player, "dialog.hoe.stat-crop-doubler", doublerLevel, doublerMax,
                                getEnchantUpgradeCost("crop_doubler", doublerLevel), maxWord)),
                        DialogBody.plainMessage(enchantStatLine(player, "dialog.hoe.stat-reach", reachLevel, reachMax,
                                getEnchantUpgradeCost("reach", reachLevel), maxWord)),
                        DialogBody.plainMessage(lang().getComponent("dialog.hoe.stat-replenish", player,
                                LanguageManager.values(
                                        "status", replenishLevel > 0 ? unlockedWord : lockedWord,
                                        "cost", replenishLevel > 0 ? maxWord : String.valueOf(replenishCost)))),
                        DialogBody.plainMessage(areaLine),
                        DialogBody.plainMessage(lang().getComponent("dialog.hoe.stat-auto-sell", player,
                                LanguageManager.values(
                                        "status", autoSellLevel > 0 ? unlockedWord : lockedWord,
                                        "cost", autoSellLevel > 0 ? maxWord : String.valueOf(autoSellCost))))
                ))
                .build();

        List<ActionButton> buttons = List.of(
                hoeUpgradeButton(player, "dialog.hoe.button-xp-booster", HoeEnchant.XP_BOOSTER, "xp_booster", false, 0),
                hoeUpgradeButton(player, "dialog.hoe.button-essence-finder", HoeEnchant.ESSENCE_FINDER, "essence_finder", false, 0),
                hoeUpgradeButton(player, "dialog.hoe.button-crop-doubler", HoeEnchant.CROP_DOUBLER, "crop_doubler", false, 0),
                hoeUpgradeButton(player, "dialog.hoe.button-reach", HoeEnchant.REACH, "reach", false, 0),
                hoeUpgradeButton(player, "dialog.hoe.button-replenish", HoeEnchant.REPLENISH, "replenish", true, 250),
                hoeUpgradeButton(player, "dialog.hoe.button-harvest-area", HoeEnchant.HARVEST_AREA, "harvest_area", false, 0),
                hoeUpgradeButton(player, "dialog.hoe.button-auto-sell", HoeEnchant.AUTO_SELL, "auto_sell", true, 5000),
                backToMainButton(player)
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, closeButton(player), 1))
        );
        player.showDialog(dialog);
    }

    /** The harvested block area unlocked at the given Harvest Area level. */
    private String harvestAreaDimension(int level) {
        return switch (level) {
            case 1 -> "1x2";
            case 2 -> "2x2";
            case 3 -> "3x2";
            case 0 -> "1x1";
            default -> "3x3";
        };
    }

    private Component enchantStatLine(Player viewer, String key, int level, int maxLevel, int cost, String maxWord) {
        return lang().getComponent(key, viewer, LanguageManager.values(
                "level", String.valueOf(level),
                "max_level", String.valueOf(maxLevel),
                "cost", level >= maxLevel ? maxWord : String.valueOf(cost)));
    }

    /**
     * Computes the total essence cost to upgrade a hoe enchant by multiple levels.
     */
    private int getMultiLevelEnchantCost(String configKey, int currentLevel, int levelsToBuy) {
        int totalCost = 0;
        for (int i = 0; i < levelsToBuy; i++) {
            totalCost += getEnchantUpgradeCost(configKey, currentLevel + i);
        }
        return totalCost;
    }

    public void openHoeEnchantSliderDialog(Player player, HoeEnchant enchant, String configKey) {
        ItemStack currentHoe = player.getInventory().getItemInMainHand();
        if (!HoeEnchant.isSproutHoe(currentHoe, plugin)) {
            lang().send(player, "dialog.hoe.error-not-sprout-hoe");
            openHoeUpgradeShop(player);
            return;
        }

        PlayerData pd = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int curLvl = enchant.getLevel(currentHoe, plugin);
        int maxLvl = plugin.getConfig().getInt("hoe-enchants." + configKey + ".max-level", enchant.getMaxLevel());
        int maxAdd = maxLvl - curLvl;

        if (maxAdd <= 0) {
            lang().send(player, "dialog.hoe.error-already-max", LanguageManager.values("max_level", String.valueOf(maxLvl)));
            openHoeUpgradeShop(player);
            return;
        }

        String enchantName = lang().getPlainText("dialog.hoe.button-" + configKey.replace('_', '-'), player);

        DialogBase base = DialogBase.builder(lang().getComponent("dialog.hoe.upgrade-dialog-title", player,
                        LanguageManager.values("enchant", enchantName)))
                .body(List.of(
                        DialogBody.plainMessage(lang().getComponent("dialog.hoe.upgrade-dialog-body", player,
                                LanguageManager.values(
                                        "enchant", enchantName,
                                        "level", String.valueOf(curLvl),
                                        "max_level", String.valueOf(maxLvl)))),
                        DialogBody.plainMessage(lang().getComponent("dialog.hoe.stat-essence", player,
                                LanguageManager.values("essence", String.valueOf(pd.getEssence()))))
                ))
                .inputs(List.of(
                        DialogInput.numberRange("levels", lang().getComponent("dialog.hoe.upgrade-slider", player,
                                        LanguageManager.values("max_add", String.valueOf(maxAdd))), 1f, (float) maxAdd)
                                .step(1f)
                                .initial(1f)
                                .build()
                ))
                .build();

        List<ActionButton> buttons = List.of(
                ActionButton.builder(lang().getComponent("dialog.hoe.upgrade-buy-button", player))
                        .action(action((view, p) -> {
                            ItemStack hoe = p.getInventory().getItemInMainHand();
                            if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
                                lang().send(p, "dialog.hoe.error-not-sprout-hoe");
                                openHoeUpgradeShop(p);
                                return;
                            }
                            int currentLevel = enchant.getLevel(hoe, plugin);
                            int maxLevel = plugin.getConfig().getInt("hoe-enchants." + configKey + ".max-level", enchant.getMaxLevel());
                            int availableAdd = maxLevel - currentLevel;
                            if (availableAdd <= 0) {
                                lang().send(p, "dialog.hoe.error-already-max", LanguageManager.values("max_level", String.valueOf(maxLevel)));
                                openHoeUpgradeShop(p);
                                return;
                            }

                            int levelsToBuy = Math.min(Math.max(1, view.getFloat("levels").intValue()), availableAdd);
                            int totalCost = getMultiLevelEnchantCost(configKey, currentLevel, levelsToBuy);

                            PlayerData data = plugin.getPlayerManager().getPlayerData(p.getUniqueId());
                            if (data.removeEssence(totalCost)) {
                                enchant.setLevel(hoe, currentLevel + levelsToBuy, plugin, p);
                                plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                lang().send(p, "dialog.hoe.upgrade-multi-success", LanguageManager.values(
                                        "enchant", enchantName,
                                        "levels", String.valueOf(levelsToBuy),
                                        "level", String.valueOf(currentLevel + levelsToBuy),
                                        "cost", String.valueOf(totalCost)));
                            } else {
                                lang().send(p, "dialog.hoe.error-no-essence", LanguageManager.values("cost", String.valueOf(totalCost)));
                            }
                            openHoeUpgradeShop(p);
                        }))
                        .build(),
                ActionButton.builder(lang().getComponent("dialog.common.button-back", player))
                        .action(action((view, p) -> openHoeUpgradeShop(p)))
                        .build()
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, closeButton(player), 1))
        );
        player.showDialog(dialog);
    }

    /**
     * @param unlockStyle    true for one-off unlocks such as Replenish, which have no levels
     * @param defaultBaseCost fallback essence cost for unlock-style enchants
     */
    private ActionButton hoeUpgradeButton(Player viewer, String buttonKey, HoeEnchant enchant,
                                          String configKey, boolean unlockStyle, int defaultBaseCost) {
        return ActionButton.builder(lang().getComponent(buttonKey, viewer))
                .action(action((view, p) -> {
                    // The hand is re-read on click; the item captured when the dialog opened may be stale.
                    ItemStack currentHoe = p.getInventory().getItemInMainHand();
                    if (!HoeEnchant.isSproutHoe(currentHoe, plugin)) {
                        lang().send(p, "dialog.hoe.error-not-sprout-hoe");
                        openHoeUpgradeShop(p);
                        return;
                    }
                    if (unlockStyle) {
                        PlayerData pd = plugin.getPlayerManager().getPlayerData(p.getUniqueId());
                        int curLvl = enchant.getLevel(currentHoe, plugin);
                        int cost = plugin.getConfig().getInt("hoe-enchants." + configKey + ".base-cost", defaultBaseCost);
                        if (curLvl >= 1) {
                            lang().send(p, "dialog.hoe.error-already-unlocked");
                        } else if (pd.removeEssence(cost)) {
                            enchant.setLevel(currentHoe, 1, plugin, p);
                            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                        } else {
                            lang().send(p, "dialog.hoe.error-no-essence",
                                    LanguageManager.values("cost", String.valueOf(cost)));
                        }
                        openHoeUpgradeShop(p);
                    } else {
                        openHoeEnchantSliderDialog(p, enchant, configKey);
                    }
                }))
                .build();
    }

    public void openGeneratorBlockControl(Player player, GeneratorBlock gen) {
        if (!gen.getOwnerUuid().equals(player.getUniqueId()) && !player.hasPermission("gensprout.admin")) {
            String ownerName = org.bukkit.Bukkit.getOfflinePlayer(gen.getOwnerUuid()).getName();
            lang().send(player, "generator.not-owner-named", LanguageManager.values("player", ownerName != null ? ownerName : "another player"));
            return;
        }

        GeneratorType type = plugin.getGeneratorManager().getTierConfig(gen.getTier());
        if (type == null) return;

        GeneratorType nextType = plugin.getGeneratorManager().getTierConfig(gen.getTier() + 1);
        double upgradeCost = type.getUpgradePrice();
        int maxTier = Math.max(1, plugin.getGeneratorManager().getMaxTier());

        DialogBase base = DialogBase.builder(lang().getComponent("dialog.gencontrol.title", player))
                .body(List.of(
                        DialogBody.plainMessage(lang().getComponent("dialog.gencontrol.tier", player,
                                LanguageManager.values(
                                        "tier", String.valueOf(gen.getTier()),
                                        "max_tier", String.valueOf(maxTier)))),
                        DialogBody.plainMessage(lang().getComponent("dialog.gencontrol.drop-value", player,
                                LanguageManager.values("value", EconomyHook.format(type.getDropValue())))),
                        DialogBody.plainMessage(nextType == null
                                ? lang().getComponent("dialog.gencontrol.max-tier", player)
                                : lang().getComponent("dialog.gencontrol.upgrade-cost", player,
                                        LanguageManager.values("cost", EconomyHook.format(upgradeCost))))
                ))
                .build();

        List<ActionButton> buttons = List.of(
                ActionButton.builder(lang().getComponent("dialog.gencontrol.button-upgrade", player))
                        .action(action((view, p) -> {
                            if (!gen.getOwnerUuid().equals(p.getUniqueId()) && !p.hasPermission("gensprout.admin")) {
                                lang().send(p, "generator.not-owner");
                                closeDialog(p);
                                return;
                            }
                            if (nextType != null) {
                                if (EconomyHook.has(p, upgradeCost)) {
                                    EconomyHook.withdraw(p, upgradeCost);
                                    gen.setTier(gen.getTier() + 1);
                                    gen.getLocation().getBlock().setType(nextType.getBlockType());
                                    plugin.getGeneratorManager().saveGenerators();

                                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                    lang().send(p, "dialog.gencontrol.upgrade-success",
                                            LanguageManager.values("tier", String.valueOf(gen.getTier())));
                                } else {
                                    lang().send(p, "dialog.common.insufficient-funds",
                                            LanguageManager.values("cost", EconomyHook.format(upgradeCost)));
                                }
                            } else {
                                lang().send(p, "dialog.gencontrol.error-max-tier");
                            }
                            openGeneratorBlockControl(p, gen);
                        }))
                        .build(),
                ActionButton.builder(lang().getComponent("dialog.gencontrol.button-pickup", player))
                        .action(action((view, p) -> {
                            if (!gen.getOwnerUuid().equals(p.getUniqueId()) && !p.hasPermission("gensprout.admin")) {
                                lang().send(p, "generator.not-owner");
                                closeDialog(p);
                                return;
                            }
                            // Remove and give item
                            ItemStack item = plugin.getGeneratorManager().createGeneratorItem(gen.getTier(), 1, p);
                            plugin.getGeneratorManager().removeGenerator(gen.getLocation());
                            plugin.getGeneratorManager().saveGenerators();

                            p.getInventory().addItem(item).forEach((index, it) -> p.getWorld().dropItemNaturally(p.getLocation(), it));
                            p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
                            lang().send(p, "dialog.gencontrol.pickup-success");
                            closeDialog(p);
                        }))
                        .build()
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, closeButton(player), 1))
        );
        player.showDialog(dialog);
    }

    private String getCategoryDisplayNameRaw(Player player, String catKey) {
        String langKey = "dialog.supplies.categories." + catKey;
        if (lang().hasKey(langKey, player)) {
            return lang().getRawMessage(langKey, lang().resolveLocale(player));
        }
        return plugin.getConfig().getString("generator-supplies-shop.categories." + catKey + ".display-name", catKey);
    }

    private String getItemDisplayNameRaw(Player player, String catKey, String itemKey) {
        String langKey = "dialog.supplies.items." + itemKey;
        if (lang().hasKey(langKey, player)) {
            return lang().getRawMessage(langKey, lang().resolveLocale(player));
        }
        return plugin.getConfig().getString("generator-supplies-shop.categories." + catKey + ".items." + itemKey + ".display-name", itemKey);
    }

    public void openSuppliesShopCategoryMenu(Player player) {
        if (!plugin.getConfig().getBoolean("generator-supplies-shop.enabled", true)) {
            lang().send(player, "dialog.supplies.disabled");
            closeDialog(player);
            return;
        }

        ConfigurationSection categoriesSec = plugin.getConfig().getConfigurationSection("generator-supplies-shop.categories");
        if (categoriesSec == null) {
            lang().send(player, "dialog.supplies.no-categories");
            closeDialog(player);
            return;
        }

        DialogBase base = DialogBase.builder(lang().getComponent("dialog.supplies.title", player))
                .body(List.of(
                        DialogBody.plainMessage(lang().getComponent("dialog.supplies.body", player)),
                        balanceLine(player)
                ))
                .build();

        List<ActionButton> buttons = new ArrayList<>();

        // Add Generators category button (redirects to /genshop)
        String genCatName = lang().hasKey("dialog.supplies.categories.generators", player)
                ? lang().getRawMessage("dialog.supplies.categories.generators", lang().resolveLocale(player))
                : "<sprite:block/diamond_block> <gradient:green:aqua><bold>Generators</bold></gradient>";

        buttons.add(ActionButton.builder(lang().renderRaw(genCatName, player, null))
                .action(action((view, p) -> openGeneratorShop(p, null, null, 1, 1)))
                .build());

        for (String catKey : categoriesSec.getKeys(false)) {
            String name = getCategoryDisplayNameRaw(player, catKey);
            buttons.add(ActionButton.builder(lang().renderRaw(name, player, null))
                    .action(action((view, p) -> openSuppliesCategoryItemsMenu(p, catKey)))
                    .build());
        }

        ActionButton backBtn = backToMainButton(player);

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, backBtn, 1))
        );
        player.showDialog(dialog);
    }

    public void openSuppliesCategoryItemsMenu(Player player, String categoryKey) {
        String catName = getCategoryDisplayNameRaw(player, categoryKey);
        ConfigurationSection itemsSec = plugin.getConfig().getConfigurationSection("generator-supplies-shop.categories." + categoryKey + ".items");

        if (itemsSec == null) {
            lang().send(player, "dialog.supplies.no-items");
            openSuppliesShopCategoryMenu(player);
            return;
        }

        DialogBase base = DialogBase.builder(lang().getComponent("dialog.supplies.cat-title", player,
                        LanguageManager.values("category", catName)))
                .body(List.of(
                        DialogBody.plainMessage(lang().getComponent("dialog.supplies.cat-body", player)),
                        balanceLine(player)
                ))
                .build();

        List<ActionButton> buttons = new ArrayList<>();
        for (String itemKey : itemsSec.getKeys(false)) {
            String itemName = getItemDisplayNameRaw(player, categoryKey, itemKey);
            double price = plugin.getConfig().getDouble("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".price", 1.0);

            buttons.add(ActionButton.builder(lang().getComponent("dialog.supplies.item-button", player,
                            LanguageManager.values(
                                    "item_name", itemName,
                                    "unit_price", EconomyHook.format(price))))
                    .action(action((view, p) -> openSuppliesItemBuyDialog(p, categoryKey, itemKey, null, null, 1)))
                    .build());
        }

        ActionButton backBtn = ActionButton.builder(lang().getComponent("dialog.supplies.button-back-categories", player))
                .action(action((view, p) -> openSuppliesShopCategoryMenu(p)))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, backBtn, 1))
        );
        player.showDialog(dialog);
    }

    /**
     * @param statusKey    language key for the status line, or null for none. See
     *                     {@link #openGeneratorShop(Player, String, Map, int, int)}.
     * @param statusValues placeholder values for {@code statusKey}, may be null.
     */
    public void openSuppliesItemBuyDialog(Player player, String categoryKey, String itemKey,
                                          String statusKey, Map<String, String> statusValues, int initialQty) {
        String itemName = getItemDisplayNameRaw(player, categoryKey, itemKey);
        String matName = plugin.getConfig().getString("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".material", "COBBLESTONE");
        double unitPrice = plugin.getConfig().getDouble("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".price", 1.0);

        List<DialogBody> bodyList = new ArrayList<>();
        bodyList.add(DialogBody.plainMessage(lang().getComponent("dialog.supplies.buy-item-line", player,
                LanguageManager.values("item_name", itemName))));
        bodyList.add(DialogBody.plainMessage(lang().getComponent("dialog.supplies.buy-body", player,
                LanguageManager.values("unit_price", EconomyHook.format(unitPrice)))));
        bodyList.add(balanceLine(player));
        if (statusKey != null) {
            bodyList.add(DialogBody.plainMessage(lang().getComponent(statusKey, player, statusValues)));
        }

        DialogBase base = DialogBase.builder(lang().getComponent("dialog.supplies.item-title", player))
                .body(bodyList)
                .inputs(List.of(
                        DialogInput.numberRange("qty", lang().getComponent("dialog.supplies.qty-select", player), 1f, 64f)
                                .step(1f)
                                .initial((float) Math.max(1, Math.min(64, initialQty)))
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(
                        ActionButton.builder(lang().getComponent("dialog.supplies.buy-button", player))
                                .action(action((view, p) -> {
                                    int qty = Math.max(1, Math.min(64, view.getFloat("qty").intValue()));
                                    if (itemKey.equalsIgnoreCase("sell_wand")) {
                                        PlayerData pd = plugin.getPlayerManager().getPlayerData(p.getUniqueId());
                                        int wandCost = plugin.getConfig().getInt("sellwand.essence-cost", 1000);
                                        if (pd.removeEssence(wandCost)) {
                                            plugin.getPlayerManager().savePlayer(p.getUniqueId());
                                            ItemStack wand = SellWand.createSellWand(plugin, 1);
                                            p.getInventory().addItem(wand).forEach((index, item) -> p.getWorld().dropItemNaturally(p.getLocation(), item));
                                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                            lang().send(p, "dialog.genshop.buy-sellwand-success");
                                        } else {
                                            lang().send(p, "dialog.hoe.error-no-essence", LanguageManager.values("cost", String.valueOf(wandCost)));
                                        }
                                        openSuppliesCategoryItemsMenu(p, categoryKey);
                                        return;
                                    }
                                    double totalCost = unitPrice * qty;
                                    Material mat = Material.matchMaterial(matName);
                                    if (mat == null) mat = Material.COBBLESTONE;

                                    if (EconomyHook.has(p, totalCost)) {
                                        EconomyHook.withdraw(p, totalCost);
                                        ItemStack stack = new ItemStack(mat, qty);
                                        int effLevel = plugin.getConfig().getInt("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".efficiency", 0);
                                        if (effLevel > 0) {
                                            stack.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.EFFICIENCY, effLevel);
                                        }
                                        int sharpLevel = plugin.getConfig().getInt("generator-supplies-shop.categories." + categoryKey + ".items." + itemKey + ".sharpness", 0);
                                        if (sharpLevel > 0) {
                                            stack.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SHARPNESS, sharpLevel);
                                        }
                                        p.getInventory().addItem(stack).forEach((index, item) -> p.getWorld().dropItemNaturally(p.getLocation(), item));
                                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                                        Map<String, String> boughtValues = LanguageManager.values(
                                                "amount", String.valueOf(qty),
                                                "item", itemName,
                                                "cost", EconomyHook.format(totalCost));
                                        lang().send(p, "dialog.supplies.buy-success", boughtValues);
                                        openSuppliesItemBuyDialog(p, categoryKey, itemKey, "dialog.supplies.buy-success", boughtValues, qty);
                                    } else {
                                        Map<String, String> costValues = LanguageManager.values("cost", EconomyHook.format(totalCost));
                                        lang().send(p, "dialog.common.insufficient-funds", costValues);
                                        openSuppliesItemBuyDialog(p, categoryKey, itemKey, "dialog.common.insufficient-funds", costValues, qty);
                                    }
                                }))
                                .build(),
                        ActionButton.builder(lang().getComponent("dialog.supplies.check-price-button", player))
                                .action(action((view, p) -> {
                                    int qty = Math.max(1, Math.min(64, view.getFloat("qty").intValue()));
                                    double totalCost = unitPrice * qty;
                                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.0f);
                                    openSuppliesItemBuyDialog(p, categoryKey, itemKey, "dialog.supplies.total-price",
                                            LanguageManager.values(
                                                    "amount", String.valueOf(qty),
                                                    "total_price", EconomyHook.format(totalCost)), qty);
                                }))
                                .build(),
                        ActionButton.builder(lang().getComponent("dialog.supplies.button-back-items", player))
                                .action(action((view, p) -> openSuppliesCategoryItemsMenu(p, categoryKey)))
                                .build()
                )).build())
        );
        player.showDialog(dialog);
    }

    public void openFirstJoinTutorialDialog(Player player) {
        // An operator who customised the tutorial in config.yml keeps their own text. Servers still
        // on the shipped defaults get the translated version from the language files instead.
        Component title = isConfigDefault("first-join-tutorial.dialog-title")
                ? lang().getComponent("dialog.tutorial.title", player)
                : lang().renderRaw(plugin.getConfig().getString("first-join-tutorial.dialog-title", ""), player, null);

        List<DialogBody> bodyList = new ArrayList<>();
        bodyList.add(DialogBody.plainMessage(lang().getComponent("dialog.tutorial.welcome", player)));

        List<String> configMessages = plugin.getConfig().getStringList("first-join-tutorial.messages");
        if (!configMessages.isEmpty() && !isConfigDefault("first-join-tutorial.messages")) {
            for (String msg : configMessages) {
                bodyList.add(DialogBody.plainMessage(lang().renderRaw(msg, player, null)));
            }
        } else {
            for (Component line : lang().getComponentList("dialog.tutorial.lines", player, null)) {
                bodyList.add(DialogBody.plainMessage(line));
            }
        }

        DialogBase base = DialogBase.builder(title)
                .body(bodyList)
                .build();

        List<ActionButton> buttons = List.of(
                ActionButton.builder(lang().getComponent("dialog.tutorial.button-genshop", player))
                        .action(action((view, p) -> openGeneratorShop(p, null, null, 1, 1)))
                        .build(),
                ActionButton.builder(lang().getComponent("dialog.tutorial.button-shop", player))
                        .action(action((view, p) -> openSuppliesShopCategoryMenu(p)))
                        .build(),
                ActionButton.builder(lang().getComponent("dialog.tutorial.close", player))
                        .action(action((view, p) -> {
                            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.2f);
                            closeDialog(p);
                        }))
                        .build()
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(buttons, null, 1))
        );
        player.showDialog(dialog);
    }
}
