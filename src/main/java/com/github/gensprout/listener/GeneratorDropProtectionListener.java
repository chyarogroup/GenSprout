package com.github.gensprout.listener;

import com.github.gensprout.GenSprout;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GeneratorDropProtectionListener implements Listener {

    private final GenSprout plugin;
    private final NamespacedKey ownerKey;
    private final Map<UUID, Long> messageCooldowns = new ConcurrentHashMap<>();

    public GeneratorDropProtectionListener(GenSprout plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "generator_owner");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Item itemEntity = event.getItem();
        String ownerUuidStr = getOwnerFromItem(itemEntity);

        if (ownerUuidStr == null) return; // Not a protected generator drop

        try {
            UUID ownerUuid = UUID.fromString(ownerUuidStr);
            if (!player.getUniqueId().equals(ownerUuid) && !player.hasPermission("gensprout.admin")) {
                event.setCancelled(true);

                // Throttle actionbar notice to once every 2 seconds per player
                long now = System.currentTimeMillis();
                Long lastMsg = messageCooldowns.get(player.getUniqueId());
                if (lastMsg == null || (now - lastMsg) > 2000L) {
                    messageCooldowns.put(player.getUniqueId(), now);
                    plugin.getLanguageManager().sendActionBar(player, "generator.drop-not-owner");
                }
            }
        } catch (IllegalArgumentException ignored) {}
    }

    private String getOwnerFromItem(Item itemEntity) {
        if (itemEntity == null) return null;

        // Check Entity PDC
        PersistentDataContainer entityPdc = itemEntity.getPersistentDataContainer();
        if (entityPdc.has(ownerKey, PersistentDataType.STRING)) {
            return entityPdc.get(ownerKey, PersistentDataType.STRING);
        }

        // Check ItemStack PDC
        ItemStack itemStack = itemEntity.getItemStack();
        if (itemStack.hasItemMeta()) {
            PersistentDataContainer itemPdc = itemStack.getItemMeta().getPersistentDataContainer();
            if (itemPdc.has(ownerKey, PersistentDataType.STRING)) {
                return itemPdc.get(ownerKey, PersistentDataType.STRING);
            }
        }

        // Fallback check for Paper Entity.getOwner()
        try {
            UUID ownerUuid = itemEntity.getOwner();
            if (ownerUuid != null) {
                return ownerUuid.toString();
            }
        } catch (Throwable ignored) {}

        return null;
    }
}
