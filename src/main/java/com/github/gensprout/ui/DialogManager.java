package com.github.gensprout.ui;

import com.github.gensprout.GenSprout;
import com.github.gensprout.economy.EconomyHook;
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
        this.clickOptions = ClickCallback.Options.builder().uses(1).build();
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

    public void openMainMenu(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int activeGens = plugin.getGeneratorManager().getActiveCount(player.getUniqueId());
        int maxSlots = data.getMaxSlots(plugin.getGeneratorManager().getDefaultSlots());

        String serverName = plugin.getConfig().getString("server.name", "GenSprout");
        String titleRaw = plugin.getConfig().getString("menus.main-menu-title", "<gradient:green:aqua><bold>" + serverName + " Menu</bold></gradient>");
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

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Generator Shop</green>"))
                                .action(action((view, p) -> openGeneratorShop(p)))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<yellow>Prestige Menu</yellow>"))
                                .action(action((view, p) -> openPrestigeShop(p)))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<light_purple>Hoe Enchanting</light_purple>"))
                                .action(action((view, p) -> openHoeUpgradeShop(p)))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<red>Close</red>"))
                                .action(action((view, p) -> p.closeDialog()))
                                .build()
                )).build())
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

        DialogBase base = DialogBase.builder(plugin.getMiniMessage().deserialize("<gradient:#2ecc71:#00b894><bold>Generator Shop</bold></gradient>"))
                .body(bodyList)
                .inputs(List.of(
                        // Slider to select tier (1-25)
                        DialogInput.numberRange("tier", plugin.getMiniMessage().deserialize("Select Tier (1-25)"), 1f, 25f)
                                .step(1f)
                                .initial((float) initialTier)
                                .build(),
                        // Slider to select quantity
                        DialogInput.numberRange("qty", plugin.getMiniMessage().deserialize("Quantity (1-64)"), 1f, 64f)
                                .step(1f)
                                .initial((float) initialQty)
                                .build()
                ))
                .build();

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(
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
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>Back</gray>"))
                                .action(action((view, p) -> openMainMenu(p)))
                                .build()
                )).build())
        );
        player.showDialog(dialog);
    }

    public void openPrestigeShop(Player player) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        int basePrestigeLevel = plugin.getConfig().getInt("leveling.prestige.base-level", 50);
        int levelIncrement = plugin.getConfig().getInt("leveling.prestige.levels-per-prestige", 10);
        int requiredLevel = basePrestigeLevel + (data.getPrestige() * levelIncrement);

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

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(
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
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>Back</gray>"))
                                .action(action((view, p) -> openMainMenu(p)))
                                .build()
                )).build())
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
                                            ItemStack sproutHoe = new ItemStack(Material.NETHERITE_HOE);
                                            HoeEnchant.rebuildLore(sproutHoe, plugin);
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

        int xpCost = plugin.getConfig().getInt("hoe-enchants.xp_booster.base-cost", 100) + (xpLevel * plugin.getConfig().getInt("hoe-enchants.xp_booster.cost-multiplier", 50));
        int essenceCost = plugin.getConfig().getInt("hoe-enchants.essence_finder.base-cost", 150) + (essenceLevel * plugin.getConfig().getInt("hoe-enchants.essence_finder.cost-multiplier", 75));
        int doublerCost = plugin.getConfig().getInt("hoe-enchants.crop_doubler.base-cost", 200) + (doublerLevel * plugin.getConfig().getInt("hoe-enchants.crop_doubler.cost-multiplier", 100));
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
 
         Dialog dialog = Dialog.create(builder -> builder.empty()
                 .base(base)
                 .type(DialogType.multiAction(List.of(
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<yellow>XP Booster</yellow>"))
                                 .action(action((view, p) -> {
                                     if (xpLevel < 5) {
                                         if (data.removeEssence(xpCost)) {
                                             HoeEnchant.XP_BOOSTER.setLevel(hoe, xpLevel + 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already max level!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<light_purple>Essence Finder</light_purple>"))
                                 .action(action((view, p) -> {
                                     if (essenceLevel < 5) {
                                         if (data.removeEssence(essenceCost)) {
                                             HoeEnchant.ESSENCE_FINDER.setLevel(hoe, essenceLevel + 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already max level!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<green>Crop Doubler</green>"))
                                 .action(action((view, p) -> {
                                     if (doublerLevel < 5) {
                                         if (data.removeEssence(doublerCost)) {
                                             HoeEnchant.CROP_DOUBLER.setLevel(hoe, doublerLevel + 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already max level!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<aqua>Replenish</aqua>"))
                                 .action(action((view, p) -> {
                                     if (replenishLevel == 0) {
                                         if (data.removeEssence(replenishCost)) {
                                             HoeEnchant.REPLENISH.setLevel(hoe, 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already unlocked!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                         ActionButton.builder(plugin.getMiniMessage().deserialize("<gold>Harvest Area</gold>"))
                                 .action(action((view, p) -> {
                                     if (areaLevel == 0) {
                                         if (data.removeEssence(areaCost)) {
                                             HoeEnchant.HARVEST_AREA.setLevel(hoe, 1, plugin);
                                             p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.2f);
                                         } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Not enough Essence!</red>"));
                                     } else p.sendMessage(plugin.getMiniMessage().deserialize("<red>Already unlocked!</red>"));
                                     openHoeUpgradeShop(p);
                                 }))
                                 .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<gray>Back</gray>"))
                                .action(action((view, p) -> openMainMenu(p)))
                                .build()
                )).build())
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

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(base)
                .type(DialogType.multiAction(List.of(
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
                                    p.closeDialog();
                                }))
                                .build(),
                        ActionButton.builder(plugin.getMiniMessage().deserialize("<red>Close</red>"))
                                .action(action((view, p) -> p.closeDialog()))
                                .build()
                )).build())
        );
        player.showDialog(dialog);
    }
}
