package com.github.gensprout.farming;

import com.github.gensprout.GenSprout;
import com.github.gensprout.player.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.time.Duration;
import java.util.Collection;
import java.util.Random;

public class FarmingListener implements Listener {

    private final GenSprout plugin;
    private final Random random = new Random();
    private boolean isAreaHarvesting = false; // Flag to prevent infinite recursion during 3x3 harvest
    private final java.util.List<RegrowthBlock> regrowingBlocks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private boolean isProgrammaticChange = false; // Flag to prevent block physics cancellation on programmatic edits

    public FarmingListener(GenSprout plugin) {
        this.plugin = plugin;
        startRegrowthTask();
        startMissingCropDetectorTask();
    }

    private static class RegrowthBlock {
        private final Location location;
        private final Material material;
        private final int maxAge;
        private final long harvestTime;

        public RegrowthBlock(Location location, Material material, int maxAge) {
            this.location = location;
            this.material = material;
            this.maxAge = maxAge;
            this.harvestTime = System.currentTimeMillis();
        }
    }

    private void startRegrowth(Block block, Material material) {
        org.bukkit.block.data.BlockData data = material.createBlockData();
        if (data instanceof Ageable ageable) {
            ageable.setAge(0);
            isProgrammaticChange = true;
            try {
                block.setType(material, false); // Set type first so setBlockData is accepted by Bukkit on AIR blocks!
                block.setBlockData(ageable, true);
            } finally {
                isProgrammaticChange = false;
            }
            regrowingBlocks.add(new RegrowthBlock(block.getLocation(), material, ageable.getMaximumAge()));
        } else {
            isProgrammaticChange = true;
            try {
                block.setType(Material.AIR, true);
            } finally {
                isProgrammaticChange = false;
            }
            regrowingBlocks.add(new RegrowthBlock(block.getLocation(), material, 0));
        }
    }

    private void startRegrowthTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            java.util.List<RegrowthBlock> toRemove = new java.util.ArrayList<>();
            for (RegrowthBlock reg : regrowingBlocks) {
                Block block = reg.location.getBlock();
                long elapsed = now - reg.harvestTime;
                double progress = Math.min(1.0, (double) elapsed / 5000.0);

                if (progress < 1.0) {
                    if (block.getType() == reg.material && block.getBlockData() instanceof Ageable ageable) {
                        int currentAge = (int) Math.round(progress * reg.maxAge);
                        if (ageable.getAge() < currentAge) {
                            ageable.setAge(currentAge);
                            isProgrammaticChange = true;
                            try {
                                block.setBlockData(ageable, true);
                            } finally {
                                isProgrammaticChange = false;
                            }
                        }
                    }
                } else {
                    if (block.getType() == reg.material && block.getBlockData() instanceof Ageable ageable) {
                        ageable.setAge(reg.maxAge);
                        isProgrammaticChange = true;
                        try {
                            block.setBlockData(ageable, true);
                        } finally {
                            isProgrammaticChange = false;
                        }
                    } else if (block.getType() == Material.AIR) {
                        isProgrammaticChange = true;
                        try {
                            block.setType(reg.material, true);
                        } finally {
                            isProgrammaticChange = false;
                        }
                    }
                    toRemove.add(reg);
                }
            }
            if (!toRemove.isEmpty()) {
                regrowingBlocks.removeAll(toRemove);
            }
        }, 10L, 10L); // Tick every 0.5s
    }

    private void startMissingCropDetectorTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            
            FarmRegion region = plugin.getFarmManager().getActiveRegion();
            if (region == null) return;
            org.bukkit.World world = Bukkit.getWorld(region.getWorldName());
            if (world == null) return;

            int minY = region.getMinY();
            int cropY = minY + 1;

            for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
                for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                    Block block = world.getBlockAt(x, cropY, z);
                    if (block.getType() == Material.AIR) {
                        // Check if already in regrowth queue to avoid duplicate registry
                        boolean alreadyRegrowing = false;
                        for (RegrowthBlock reg : regrowingBlocks) {
                            if (reg.location.getBlockX() == x && reg.location.getBlockY() == cropY && reg.location.getBlockZ() == z) {
                                alreadyRegrowing = true;
                                break;
                            }
                        }
                        if (!alreadyRegrowing) {
                            startRegrowth(block, Material.WHEAT);
                        }
                    }
                }
            }
        }, 10L, 10L); // Tick every 0.5s
    }

    /**
     * Mark placed Melon/Pumpkin blocks to prevent placing-and-breaking XP exploits.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material mat = block.getType();
        if (mat == Material.MELON || mat == Material.PUMPKIN) {
            block.setMetadata("placed_crop", new FixedMetadataValue(plugin, true));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (isAreaHarvesting) return; // Ignore events triggered by our own 3x3 harvest loop

        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack hoe = player.getInventory().getItemInMainHand();

        // Verify if it's a valid crop
        Material material = block.getType();
        if (!isCrop(material)) return;

        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        boolean insideFarm = (region != null && region.contains(block.getLocation()));

        if (insideFarm) {
            // 1. Cancel the break silently if the crop is not mature (meaning it is currently regrowing)
            if (!isMature(block)) {
                event.setCancelled(true);
                return;
            }

            // 2. Must require Sprout Hoe to harvest mature crops inside the farm!
            if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
                event.setCancelled(true);
                player.sendActionBar(plugin.getMiniMessage().deserialize("<red>Sprout Hoe required!</red>"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                return;
            }

            // Mature crop inside the farm: cancel break to prevent any drops (including seeds)
            event.setCancelled(true);

            // Play crop break sound and particle effects
            block.getWorld().playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 1.0f);
            block.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5, 0.3, 0.5), 12, 0.25, 0.25, 0.25, 0.05, block.getBlockData());

            isAreaHarvesting = true;
            try {
                harvestBlock(player, block, hoe);

                // If Harvest Area (3x3) enchant is active, break adjacent blocks
                int areaLvl = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);
                if (areaLvl > 0) {
                    Location center = block.getLocation();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            Block adjBlock = center.clone().add(dx, 0, dz).getBlock();
                            if (isCrop(adjBlock.getType()) && isMature(adjBlock) && region.contains(adjBlock.getLocation())) {
                                // Check block break permissions for the player at this location
                                BlockBreakEvent subEvent = new BlockBreakEvent(adjBlock, player);
                                Bukkit.getPluginManager().callEvent(subEvent);
                                if (!subEvent.isCancelled()) {
                                    Material adjMat = adjBlock.getType();
                                    harvestBlock(player, adjBlock, hoe);
                                    isProgrammaticChange = true;
                                    try {
                                        adjBlock.setType(Material.AIR); // Manually set to AIR to trigger regrowth next tick
                                    } finally {
                                        isProgrammaticChange = false;
                                    }
                                    Bukkit.getScheduler().runTask(plugin, () -> startRegrowth(adjBlock, adjMat));
                                }
                            }
                        }
                    }
                }
            } finally {
                isAreaHarvesting = false;
            }
        } else {
            // Outside defined farm:
            if (region != null) {
                // Do not cancel break, treat as normal vanilla block break outside the farm region
                return;
            }

            // Fallback: No farm defined at all. Allow farming anywhere if using Sprout Hoe
            if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
                return;
            }

            event.setCancelled(true);
            isAreaHarvesting = true;
            try {
                harvestBlock(player, block, hoe);

                int areaLvl = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);
                if (areaLvl > 0) {
                    Location center = block.getLocation();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            Block adjBlock = center.clone().add(dx, 0, dz).getBlock();
                            if (isCrop(adjBlock.getType()) && isMature(adjBlock)) {
                                BlockBreakEvent subEvent = new BlockBreakEvent(adjBlock, player);
                                Bukkit.getPluginManager().callEvent(subEvent);
                                if (!subEvent.isCancelled()) {
                                    harvestBlock(player, adjBlock, hoe);
                                }
                            }
                        }
                    }
                }
            } finally {
                isAreaHarvesting = false;
            }
        }
    }

    private void harvestBlock(Player player, Block block, ItemStack hoe) {
        Material material = block.getType();
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());

        // Get config info
        String cropName = material.name();
        // Fallback for Potatoes/Beetroots names
        if (cropName.equals("POTATOES")) cropName = "POTATOES";
        if (cropName.equals("BEETROOTS")) cropName = "BEETROOTS";

        int baseXp = plugin.getConfig().getInt("farming.crops." + cropName + ".xp", 5);
        double essenceChance = plugin.getConfig().getDouble("farming.crops." + cropName + ".essence-chance", 0.10);
        int baseEssence = plugin.getConfig().getInt("farming.crops." + cropName + ".essence-amount", 1);
        double sellPrice = plugin.getConfig().getDouble("farming.crops." + cropName + ".sell-price", 1.0);

        // Fetch enchants
        int xpLvl = HoeEnchant.XP_BOOSTER.getLevel(hoe, plugin);
        int essenceLvl = HoeEnchant.ESSENCE_FINDER.getLevel(hoe, plugin);
        int doubleLvl = HoeEnchant.CROP_DOUBLER.getLevel(hoe, plugin);
        int replenishLvl = HoeEnchant.REPLENISH.getLevel(hoe, plugin);

        // 1. Calculate XP
        double xpMultiplier = data.getXpMultiplier(); // prestige multipliers
        double enchantXpMultiplier = 1.0 + (xpLvl * 0.20); // enchant +20% per level
        double netXpGained = baseXp * xpMultiplier * enchantXpMultiplier;
        addFarmingXp(player, data, netXpGained);

        // 2. Calculate Essence
        double enchantEssenceChance = essenceChance * (1.0 + (essenceLvl * 0.25)); // +25% chance per level
        if (random.nextDouble() < enchantEssenceChance) {
            int netEssence = baseEssence + (essenceLvl / 2); // scaling amount slightly
            double essenceMultiplier = data.getEssenceMultiplier(); // prestige multiplier
            int finalEssence = (int) Math.round(netEssence * essenceMultiplier);
            data.addEssence(finalEssence);
            player.sendMessage(plugin.getMiniMessage().deserialize("<light_purple>+ " + finalEssence + " Essence</light_purple>"));
        }

        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        boolean insideFarm = (region != null && region.contains(block.getLocation()));

        // 3. Drop Crops directly to player inventory
        Collection<ItemStack> drops = block.getDrops(hoe);
        double doubleChance = doubleLvl * 0.10; // 10% double chance per level
        boolean doubleDrops = random.nextDouble() < doubleChance;

        for (ItemStack drop : drops) {
            if (insideFarm && isSeed(drop.getType())) continue; // Skip seeds inside the farm
            if (doubleDrops) {
                drop.setAmount(drop.getAmount() * 2);
            }
            // Add items directly to player inventory
            player.getInventory().addItem(drop).forEach((index, item) -> {
                // Drop items on the ground if inventory is full
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            });
        }
        if (doubleDrops) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        }

        // 4. Handle Replenish / Block clearing / Phase Regrow inside Farm
        if (insideFarm) {
            // Schedule regrowth on next tick so Spigot's cancellation routine doesn't override our block data edits
            Bukkit.getScheduler().runTask(plugin, () -> startRegrowth(block, material));
        } else {
            // Outside farm (if no farm region is defined)
            if (replenishLvl > 0 && isReplantable(material)) {
                // Replant at age 0
                Ageable ageable = (Ageable) block.getBlockData();
                ageable.setAge(0);
                block.setBlockData(ageable);
            } else {
                block.setType(Material.AIR);
            }
        }
    }

    private void addFarmingXp(Player player, PlayerData data, double amount) {
        plugin.getPlayerManager().addXp(player, amount);
    }

    private boolean isCrop(Material material) {
        return switch (material) {
            case WHEAT, POTATOES, CARROTS, BEETROOTS, MELON, PUMPKIN, COCOA, SUGAR_CANE, NETHER_WART -> true;
            default -> false;
        };
    }

    private boolean isSeed(Material material) {
        return switch (material) {
            case WHEAT_SEEDS, BEETROOT_SEEDS, PUMPKIN_SEEDS, MELON_SEEDS -> true;
            default -> false;
        };
    }

    private boolean isMature(Block block) {
        Material material = block.getType();
        if (material == Material.MELON || material == Material.PUMPKIN) {
            // Prevent placed melons/pumpkins from giving rewards
            if (block.hasMetadata("placed_crop")) {
                block.removeMetadata("placed_crop", plugin);
                block.setType(Material.AIR); // Break naturally without rewards
                return false;
            }
            return true;
        }
        
        if (material == Material.SUGAR_CANE) {
            // Sugar cane is mature if there is a sugar cane below it (representing height 2 or 3)
            Block below = block.getRelative(0, -1, 0);
            return below.getType() == Material.SUGAR_CANE;
        }

        if (block.getBlockData() instanceof Ageable ageable) {
            return ageable.getAge() >= ageable.getMaximumAge();
        }
        return true;
    }

    private boolean isReplantable(Material material) {
        return switch (material) {
            case WHEAT, POTATOES, CARROTS, BEETROOTS, NETHER_WART -> true;
            default -> false; // Sugar cane, melon, pumpkin replant from base
        };
    }

    @EventHandler
    public void onMoistureChange(org.bukkit.event.block.MoistureChangeEvent event) {
        Block block = event.getBlock();
        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        if (region != null && region.contains(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerTrample(org.bukkit.event.player.PlayerInteractEvent event) {
        if (event.getAction() == org.bukkit.event.block.Action.PHYSICAL) {
            Block block = event.getClickedBlock();
            if (block != null && block.getType() == Material.FARMLAND) {
                FarmRegion region = plugin.getFarmManager().getActiveRegion();
                if (region != null && region.contains(block.getLocation())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onEntityTrample(org.bukkit.event.entity.EntityInteractEvent event) {
        Block block = event.getBlock();
        if (block.getType() == Material.FARMLAND) {
            FarmRegion region = plugin.getFarmManager().getActiveRegion();
            if (region != null && region.contains(block.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPhysics(org.bukkit.event.block.BlockPhysicsEvent event) {
        if (isProgrammaticChange) return; // Do not cancel physics on our own programmatic updates
        
        Block block = event.getBlock();
        if (isCrop(block.getType())) {
            FarmRegion region = plugin.getFarmManager().getActiveRegion();
            if (region != null && region.contains(block.getLocation())) {
                event.setCancelled(true);
            }
        }
    }
}
