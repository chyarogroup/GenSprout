package com.github.gensprout.farming;

import com.github.gensprout.GenSprout;
import com.github.gensprout.player.PlayerData;
import com.github.gensprout.util.BlockPos;
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
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles per-player instanced crop harvesting within defined farm regions.
 * Each player has an independent farm state and regrowth timer—harvesting a crop
 * puts it into regrowth ONLY for that specific player, keeping it fully visible and
 * harvestable for other players.
 */
public class FarmingListener implements Listener {

    private final GenSprout plugin;
    private final Random random = new Random();

    // ThreadLocal context flags to avoid state contamination across recursive or concurrent event calls
    private final ThreadLocal<Boolean> isAreaHarvesting = ThreadLocal.withInitial(() -> false);
    private final ThreadLocal<Boolean> isProgrammaticChange = ThreadLocal.withInitial(() -> false);

    // Per-player concurrent map: UUID -> (BlockPos -> RegrowthBlock)
    private final Map<UUID, Map<BlockPos, RegrowthBlock>> playerRegrowingBlocks = new ConcurrentHashMap<>();

    public FarmingListener(GenSprout plugin) {
        this.plugin = plugin;
        startRegrowthTask();
    }

    public static class RegrowthBlock {
        public final Location location;
        public final BlockPos blockPos;
        public final Material material;
        public final int maxAge;
        public final long harvestTime;

        public RegrowthBlock(Location location, Material material, int maxAge) {
            this.location = location;
            this.blockPos = BlockPos.fromLocation(location);
            this.material = material;
            this.maxAge = maxAge;
            this.harvestTime = System.currentTimeMillis();
        }
    }

    public Map<BlockPos, RegrowthBlock> getPlayerRegrowingBlockMap(UUID playerUuid) {
        return playerRegrowingBlocks.get(playerUuid);
    }

    private void startRegrowth(Player player, Block block, Material material) {
        BlockPos pos = BlockPos.fromLocation(block.getLocation());
        int maxAge = 7;
        org.bukkit.block.data.BlockData data = material.createBlockData();
        if (data instanceof Ageable ageable) {
            maxAge = ageable.getMaximumAge();
        }
        Map<BlockPos, RegrowthBlock> pMap = playerRegrowingBlocks.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());
        pMap.put(pos, new RegrowthBlock(block.getLocation(), material, maxAge));
        FarmCropView.sendFakeCrop(plugin, player, block.getLocation(), 0, maxAge);
    }

    private void startRegrowthTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Map.Entry<UUID, Map<BlockPos, RegrowthBlock>> pEntry : playerRegrowingBlocks.entrySet()) {
                UUID uuid = pEntry.getKey();
                Player player = Bukkit.getPlayer(uuid);
                Map<BlockPos, RegrowthBlock> pMap = pEntry.getValue();

                if (player == null || !player.isOnline()) {
                    // Clean up completed regrowths for offline players
                    pMap.entrySet().removeIf(e -> (now - e.getValue().harvestTime) >= 5000L);
                    if (pMap.isEmpty()) {
                        playerRegrowingBlocks.remove(uuid);
                    }
                    continue;
                }

                List<BlockPos> toRemove = new ArrayList<>();
                for (Map.Entry<BlockPos, RegrowthBlock> entry : pMap.entrySet()) {
                    BlockPos pos = entry.getKey();
                    RegrowthBlock reg = entry.getValue();
                    long elapsed = now - reg.harvestTime;
                    double progress = Math.min(1.0, (double) elapsed / 5000.0);

                    if (progress < 1.0) {
                        int currentAge = (int) Math.round(progress * reg.maxAge);
                        FarmCropView.sendFakeCrop(plugin, player, reg.location, currentAge, reg.maxAge);
                    } else {
                        FarmCropView.sendFakeCrop(plugin, player, reg.location, reg.maxAge, reg.maxAge);
                        toRemove.add(pos);
                    }
                }
                for (BlockPos pos : toRemove) {
                    pMap.remove(pos);
                }
                if (pMap.isEmpty()) {
                    playerRegrowingBlocks.remove(uuid);
                }
            }
        }, 10L, 10L);
    }

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
        if (isAreaHarvesting.get()) return;

        Player player = event.getPlayer();
        Block block = event.getBlock();
        ItemStack hoe = player.getInventory().getItemInMainHand();

        Material material = block.getType();
        if (!isCrop(material)) return;

        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        boolean insideFarm = (region != null && region.contains(block.getLocation()));

        if (insideFarm) {
            if (!isMature(player, block)) {
                event.setCancelled(true);
                Map<BlockPos, RegrowthBlock> pMap = playerRegrowingBlocks.get(player.getUniqueId());
                BlockPos pos = BlockPos.fromLocation(block.getLocation());
                RegrowthBlock reg = pMap != null ? pMap.get(pos) : null;
                int currentAge = 0;
                if (reg != null) {
                    long elapsed = System.currentTimeMillis() - reg.harvestTime;
                    double progress = Math.min(1.0, (double) elapsed / 5000.0);
                    currentAge = (int) Math.round(progress * reg.maxAge);
                }
                final int finalAge = currentAge;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        FarmCropView.sendFakeCrop(plugin, player, block.getLocation(), finalAge, 7);
                    }
                });
                return;
            }

            if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
                event.setCancelled(true);
                player.sendActionBar(plugin.getMiniMessage().deserialize("<red>Sprout Hoe required!</red>"));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        FarmCropView.sendFakeCrop(plugin, player, block.getLocation(), 7, 7);
                    }
                });
                return;
            }

            event.setCancelled(true);

            player.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 1.0f);
            player.spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5, 0.3, 0.5), 12, 0.25, 0.25, 0.25, 0.05, block.getBlockData());

            isAreaHarvesting.set(true);
            try {
                harvestBlock(player, block, hoe);

                int areaLvl = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);
                if (areaLvl > 0) {
                    Location center = block.getLocation();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            Block adjBlock = center.clone().add(dx, 0, dz).getBlock();
                            if (isCrop(adjBlock.getType()) && isMature(player, adjBlock) && region.contains(adjBlock.getLocation())) {
                                harvestBlock(player, adjBlock, hoe);
                            }
                        }
                    }
                }
            } finally {
                isAreaHarvesting.set(false);
            }
        } else {
            if (region != null) {
                return;
            }

            if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
                return;
            }

            event.setCancelled(true);
            isAreaHarvesting.set(true);
            try {
                harvestBlock(player, block, hoe);

                int areaLvl = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);
                if (areaLvl > 0) {
                    Location center = block.getLocation();
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dz == 0) continue;
                            Block adjBlock = center.clone().add(dx, 0, dz).getBlock();
                            if (isCrop(adjBlock.getType()) && isMature(player, adjBlock)) {
                                harvestBlock(player, adjBlock, hoe);
                            }
                        }
                    }
                }
            } finally {
                isAreaHarvesting.set(false);
            }
        }
    }

    private void harvestBlock(Player player, Block block, ItemStack hoe) {
        Material material = block.getType();
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());

        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        boolean insideFarm = (region != null && region.contains(block.getLocation()));

        String cropName = insideFarm ? CropProgression.getCurrentCropKey(plugin, data) : material.name();
        if (cropName.equals("POTATOES")) cropName = "POTATOES";
        if (cropName.equals("BEETROOTS")) cropName = "BEETROOTS";

        boolean cropUnlocked = CropProgression.isUnlocked(plugin, data, cropName);
        if (!cropUnlocked) {
            int requiredPrestige = CropProgression.getRequiredPrestigeForCrop(plugin, cropName);
            player.sendActionBar(plugin.getMiniMessage().deserialize("<red>\uD83D\uDD12 This crop unlocks at Prestige " + requiredPrestige + "!</red>"));
        }

        int baseXp = plugin.getConfig().getInt("farming.crops." + cropName + ".xp", 5);
        double essenceChance = plugin.getConfig().getDouble("farming.crops." + cropName + ".essence-chance", 0.10);
        int baseEssence = plugin.getConfig().getInt("farming.crops." + cropName + ".essence-amount", 1);

        int xpLvl = HoeEnchant.XP_BOOSTER.getLevel(hoe, plugin);
        int essenceLvl = HoeEnchant.ESSENCE_FINDER.getLevel(hoe, plugin);
        int doubleLvl = HoeEnchant.CROP_DOUBLER.getLevel(hoe, plugin);
        int replenishLvl = HoeEnchant.REPLENISH.getLevel(hoe, plugin);

        if (cropUnlocked) {
            double xpMultiplier = data.getXpMultiplier();
            double enchantXpMultiplier = 1.0 + (xpLvl * 0.20);
            double netXpGained = baseXp * xpMultiplier * enchantXpMultiplier;
            addFarmingXp(player, data, netXpGained);

            double enchantEssenceChance = essenceChance * (1.0 + (essenceLvl * 0.25));
            if (random.nextDouble() < enchantEssenceChance) {
                int netEssence = baseEssence + (essenceLvl / 2);
                double essenceMultiplier = data.getEssenceMultiplier();
                int finalEssence = (int) Math.round(netEssence * essenceMultiplier);
                data.addEssence(finalEssence);
                player.sendMessage(plugin.getMiniMessage().deserialize("<light_purple>+ " + finalEssence + " Essence</light_purple>"));
            }
        }

        Collection<ItemStack> drops;
        if (insideFarm) {
            FarmCropType cropType = FarmCropType.byConfigKey(cropName);
            List<ItemStack> synthDrops = new ArrayList<>();
            synthDrops.add(new ItemStack(cropType.getHarvestItem(), 1 + random.nextInt(3)));
            if (cropType.getSeedItem() != null && random.nextDouble() < 0.5) {
                synthDrops.add(new ItemStack(cropType.getSeedItem(), 1));
            }
            drops = synthDrops;
        } else {
            drops = block.getDrops(hoe);
        }
        double doubleChance = doubleLvl * 0.10;
        boolean doubleDrops = random.nextDouble() < doubleChance;

        for (ItemStack drop : drops) {
            if (insideFarm && isSeed(drop.getType())) continue;
            if (doubleDrops) {
                drop.setAmount(drop.getAmount() * 2);
            }
            if (cropUnlocked && com.github.gensprout.economy.SellManager.tryAutoSell(plugin, player, drop)) {
                continue;
            }
            player.getInventory().addItem(drop).forEach((index, item) -> {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            });
        }
        if (doubleDrops) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
        }

        if (insideFarm) {
            startRegrowth(player, block, material);
        } else {
            if (replenishLvl > 0 && isReplantable(material)) {
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

    private boolean isMature(Player player, Block block) {
        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        boolean insideFarm = (region != null && region.contains(block.getLocation()));

        if (insideFarm) {
            Map<BlockPos, RegrowthBlock> pMap = playerRegrowingBlocks.get(player.getUniqueId());
            if (pMap == null || pMap.isEmpty()) return true;
            return !pMap.containsKey(BlockPos.fromLocation(block.getLocation()));
        }

        Material material = block.getType();
        if (material == Material.MELON || material == Material.PUMPKIN) {
            if (block.hasMetadata("placed_crop")) {
                block.removeMetadata("placed_crop", plugin);
                block.setType(Material.AIR);
                return false;
            }
            return true;
        }
        
        if (material == Material.SUGAR_CANE) {
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
            default -> false;
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
        if (isProgrammaticChange.get()) return;
        
        Block block = event.getBlock();
        if (isCrop(block.getType())) {
            FarmRegion region = plugin.getFarmManager().getActiveRegion();
            if (region != null && region.contains(block.getLocation())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerChunkLoad(PlayerChunkLoadEvent event) {
        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        if (region == null) return;
        FarmCropView.refreshChunkForPlayer(plugin, event.getPlayer(), event.getChunk(), region);
    }

    @EventHandler
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        if (region != null && region.contains(block.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockFertilize(BlockFertilizeEvent event) {
        Block block = event.getBlock();
        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        if (region != null && region.contains(block.getLocation())) {
            event.setCancelled(true);
        }
    }
}
