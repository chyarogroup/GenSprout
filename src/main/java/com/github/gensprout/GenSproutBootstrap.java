package com.github.gensprout;

import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.DialogKeys;
import io.papermc.paper.registry.keys.tags.DialogTagKeys;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Set;

public class GenSproutBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        Key dialogKey = Key.key("gensprout:menu_launcher");

        // 1. Register the custom launcher dialog
        context.getLifecycleManager().registerEventHandler(RegistryEvents.DIALOG.compose(), event -> {
            net.kyori.adventure.text.minimessage.MiniMessage miniMessage = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage();
            
            java.io.File configFile = new java.io.File("plugins/GenSprout/config.yml");
            org.bukkit.configuration.file.YamlConfiguration config;
            if (configFile.exists()) {
                config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
            } else {
                config = new org.bukkit.configuration.file.YamlConfiguration();
            }
            String serverName = config.getString("server.name", "GenSprout");
            String titleRaw = config.getString("menus.main-menu-title", "<gradient:green:aqua><bold>" + serverName + " Menu</bold></gradient>");

            event.registry().register(
                DialogKeys.create(dialogKey),
                builder -> builder
                    .base(DialogBase.builder(miniMessage.deserialize(titleRaw))
                        .body(List.of(
                            DialogBody.plainMessage(Component.text("Welcome to the menu! Select an option below to manage generators, prestige, or view stats."))
                        ))
                        .build())
                    .type(DialogType.multiAction(List.of(
                        ActionButton.builder(miniMessage.deserialize("<green>Generator Shop</green>"))
                            .action(DialogAction.customClick((view, audience) -> {
                                if (audience instanceof org.bukkit.entity.Player player) {
                                    GenSprout plugin = JavaPlugin.getPlugin(GenSprout.class);
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        plugin.getDialogManager().openGeneratorShop(player);
                                    });
                                }
                            }, net.kyori.adventure.text.event.ClickCallback.Options.builder()
                                    .uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                                    .lifetime(java.time.Duration.ofDays(3650))
                                    .build()))
                            .build(),
                        ActionButton.builder(miniMessage.deserialize("<yellow>Prestige Menu</yellow>"))
                            .action(DialogAction.customClick((view, audience) -> {
                                if (audience instanceof org.bukkit.entity.Player player) {
                                    GenSprout plugin = JavaPlugin.getPlugin(GenSprout.class);
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        plugin.getDialogManager().openPrestigeShop(player);
                                    });
                                }
                            }, net.kyori.adventure.text.event.ClickCallback.Options.builder()
                                    .uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                                    .lifetime(java.time.Duration.ofDays(3650))
                                    .build()))
                            .build(),
                        ActionButton.builder(miniMessage.deserialize("<light_purple>Hoe Enchanting</light_purple>"))
                            .action(DialogAction.customClick((view, audience) -> {
                                if (audience instanceof org.bukkit.entity.Player player) {
                                    GenSprout plugin = JavaPlugin.getPlugin(GenSprout.class);
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        plugin.getDialogManager().openHoeUpgradeShop(player);
                                    });
                                }
                            }, net.kyori.adventure.text.event.ClickCallback.Options.builder()
                                    .uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                                    .lifetime(java.time.Duration.ofDays(3650))
                                    .build()))
                            .build(),
                        ActionButton.builder(miniMessage.deserialize("<gray>View Level & Stats</gray>"))
                            .action(DialogAction.customClick((view, audience) -> {
                                if (audience instanceof org.bukkit.entity.Player player) {
                                    GenSprout plugin = JavaPlugin.getPlugin(GenSprout.class);
                                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                                        plugin.getDialogManager().openMainMenu(player);
                                    });
                                }
                            }, net.kyori.adventure.text.event.ClickCallback.Options.builder()
                                    .uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                                    .lifetime(java.time.Duration.ofDays(3650))
                                    .build()))
                            .build(),
                        ActionButton.builder(miniMessage.deserialize("<red>Close</red>"))
                            .action(DialogAction.customClick((view, audience) -> {
                                if (audience instanceof org.bukkit.entity.Player player) {
                                    player.closeDialog();
                                }
                            }, net.kyori.adventure.text.event.ClickCallback.Options.builder()
                                    .uses(net.kyori.adventure.text.event.ClickCallback.UNLIMITED_USES)
                                    .lifetime(java.time.Duration.ofDays(3650))
                                    .build()))
                            .build()
                    )).build())
            );
        });

        // 2. Add our dialog key to PAUSE_SCREEN_ADDITIONS and QUICK_ACTIONS tags
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.TAGS.postFlatten(RegistryKey.DIALOG), event -> {
            event.registrar().addToTag(DialogTagKeys.PAUSE_SCREEN_ADDITIONS, Set.of(DialogKeys.create(dialogKey)));
            event.registrar().addToTag(DialogTagKeys.QUICK_ACTIONS, Set.of(DialogKeys.create(dialogKey)));
        });
    }
}
