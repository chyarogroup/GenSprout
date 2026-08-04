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
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import io.papermc.paper.event.packet.PlayerChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles per-player instanced crop harvesting within defined farm regions.
 * Each player has an independent farm state and regrowth timer. Harvesting a crop
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
                Map<Location, Integer> batchUpdates = new HashMap<>();
                for (Map.Entry<BlockPos, RegrowthBlock> entry : pMap.entrySet()) {
                    BlockPos pos = entry.getKey();
                    RegrowthBlock reg = entry.getValue();
                    long elapsed = now - reg.harvestTime;
                    double progress = Math.min(1.0, (double) elapsed / 5000.0);

                    int currentAge = (int) Math.round(progress * reg.maxAge);
                    batchUpdates.put(reg.location, currentAge);
                    if (progress >= 1.0) {
                        toRemove.add(pos);
                    }
                }
                if (!batchUpdates.isEmpty()) {
                    FarmCropView.sendFakeCropsBatch(plugin, player, batchUpdates, 7);
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

    @EventHandler(priority = EventPriority.HIGH)
    public void onLeftClickCrop(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Block block = event.getClickedBlock();
        if (block == null) return;

        Material material = block.getType();
        if (!isCrop(material)) return;

        Player player = event.getPlayer();
        FarmRegion region = plugin.getFarmManager().getActiveRegion();
        boolean insideFarm = (region != null && region.contains(block.getLocation()));
        if (!insideFarm) return;

        ItemStack hoe = player.getInventory().getItemInMainHand();

        if (!isMature(player, block)) {
            return;
        }

        if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
            plugin.getLanguageManager().sendActionBar(player, "actionbar.hoe-required");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
            return;
        }

        event.setCancelled(true);

        List<Block> targetBlocks = new ArrayList<>();
        targetBlocks.add(block);

        int areaLvl = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);
        if (areaLvl > 0) {
            List<Block> areaBlocks = getHarvestAreaBlocks(player, block, areaLvl);
            for (Block adjBlock : areaBlocks) {
                if (isCrop(adjBlock.getType()) && isMature(player, adjBlock) && region.contains(adjBlock.getLocation())) {
                    targetBlocks.add(adjBlock);
                }
            }
        }

        harvestBlocksBatch(player, targetBlocks, hoe, true);
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
                plugin.getLanguageManager().sendActionBar(player, "actionbar.hoe-required");
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.5f);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        FarmCropView.sendFakeCrop(plugin, player, block.getLocation(), 7, 7);
                    }
                });
                return;
            }

            event.setCancelled(true);

            List<Block> targetBlocks = new ArrayList<>();
            targetBlocks.add(block);

            int areaLvl = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);
            if (areaLvl > 0) {
                List<Block> areaBlocks = getHarvestAreaBlocks(player, block, areaLvl);
                for (Block adjBlock : areaBlocks) {
                    if (isCrop(adjBlock.getType()) && isMature(player, adjBlock) && region.contains(adjBlock.getLocation())) {
                        targetBlocks.add(adjBlock);
                    }
                }
            }

            harvestBlocksBatch(player, targetBlocks, hoe, true);
        } else {
            if (region != null) {
                return;
            }

            if (!HoeEnchant.isSproutHoe(hoe, plugin)) {
                return;
            }

            event.setCancelled(true);

            List<Block> targetBlocks = new ArrayList<>();
            targetBlocks.add(block);

            int areaLvl = HoeEnchant.HARVEST_AREA.getLevel(hoe, plugin);
            if (areaLvl > 0) {
                List<Block> areaBlocks = getHarvestAreaBlocks(player, block, areaLvl);
                for (Block adjBlock : areaBlocks) {
                    if (isCrop(adjBlock.getType()) && isMature(player, adjBlock)) {
                        targetBlocks.add(adjBlock);
                    }
                }
            }

            harvestBlocksBatch(player, targetBlocks, hoe, false);
        }
    }

    private List<Block> getHarvestAreaBlocks(Player player, Block centerBlock, int level) {
        List<Block> blocks = new ArrayList<>();
        Location centerLoc = centerBlock.getLocation();
        org.bukkit.block.BlockFace facing = player.getFacing();

        switch (level) {
            case 1 -> {
                int fX = facing.getModX();
                int fZ = facing.getModZ();
                if (fX == 0 && fZ == 0) fZ = 1;
                blocks.add(centerLoc.clone().add(fX, 0, fZ).getBlock());
            }
            case 2 -> {
                int fX = facing.getModX();
                int fZ = facing.getModZ();
                int rX = -fZ;
                int rZ = fX;
                blocks.add(centerLoc.clone().add(fX, 0, fZ).getBlock());
                blocks.add(centerLoc.clone().add(rX, 0, rZ).getBlock());
                blocks.add(centerLoc.clone().add(fX + rX, 0, fZ + rZ).getBlock());
            }
            case 3 -> {
                int fX = facing.getModX();
                int fZ = facing.getModZ();
                int rX = -fZ;
                int rZ = fX;
                for (int depth = 0; depth <= 1; depth++) {
                    for (int side = -1; side <= 1; side++) {
                        if (depth == 0 && side == 0) continue;
                        Location loc = centerLoc.clone().add(fX * depth + rX * side, 0, fZ * depth + rZ * side);
                        blocks.add(loc.getBlock());
                    }
                }
            }
            default -> {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) continue;
                        blocks.add(centerLoc.clone().add(dx, 0, dz).getBlock());
                    }
                }
            }
        }
        return blocks;
    }

    private void harvestBlocksBatch(Player player, List<Block> blocks, ItemStack hoe, boolean insideFarm) {
        if (blocks == null || blocks.isEmpty()) return;

        isAreaHarvesting.set(true);
        try {
            PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());

            int xpLvl = HoeEnchant.XP_BOOSTER.getLevel(hoe, plugin);
            int essenceLvl = HoeEnchant.ESSENCE_FINDER.getLevel(hoe, plugin);
            int doubleLvl = HoeEnchant.CROP_DOUBLER.getLevel(hoe, plugin);
            int replenishLvl = HoeEnchant.REPLENISH.getLevel(hoe, plugin);
            int autoSellLvl = HoeEnchant.AUTO_SELL.getLevel(hoe, plugin);

            boolean globalAutoSell = plugin.getConfig().getBoolean("auto-sell.enabled", true);

            double totalXpGained = 0.0;
            int totalEssenceGained = 0;
            boolean anyDoubleDrops = false;
            List<ItemStack> allDrops = new ArrayList<>();

            Map<Location, Integer> ageZeroUpdates = new HashMap<>();
            Map<BlockPos, RegrowthBlock> pMap = playerRegrowingBlocks.computeIfAbsent(player.getUniqueId(), k -> new ConcurrentHashMap<>());

            for (Block b : blocks) {
                Material material = b.getType();
                String cropName = insideFarm ? CropProgression.getCurrentCropKey(plugin, data) : material.name();
                if (cropName.equals("POTATOES")) cropName = "POTATOES";
                if (cropName.equals("BEETROOTS")) cropName = "BEETROOTS";

                boolean cropUnlocked = CropProgression.isUnlocked(plugin, data, cropName);

                int baseXp = plugin.getConfig().getInt("farming.crops." + cropName + ".xp", 5);
                double essenceChance = plugin.getConfig().getDouble("farming.crops." + cropName + ".essence-chance", 0.10);
                int baseEssence = plugin.getConfig().getInt("farming.crops." + cropName + ".essence-amount", 1);

                if (cropUnlocked) {
                    double xpMultiplier = data.getXpMultiplier();
                    double enchantXpMultiplier = 1.0 + (xpLvl * 0.005); // +0.5% XP per level (up to +250% at lv 500)
                    totalXpGained += baseXp * xpMultiplier * enchantXpMultiplier;

                    // Essence is now a lot easier to obtain (high base chance + bonus for essenceLvl)
                    double enchantEssenceChance = Math.min(1.0, 0.50 + (essenceLvl * 0.001));
                    if (random.nextDouble() < enchantEssenceChance) {
                        int netEssence = Math.max(1, baseEssence + (essenceLvl / 5));
                        int finalEssence = (int) Math.round(netEssence * data.getEssenceMultiplier());
                        totalEssenceGained += finalEssence;
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
                    drops = b.getDrops(hoe);
                }

                double doubleChance = Math.min(1.0, doubleLvl * 0.002); // +0.2% per level (up to 100% at lv 500)
                boolean doubleDrops = random.nextDouble() < doubleChance;
                if (doubleDrops) anyDoubleDrops = true;

                for (ItemStack drop : drops) {
                    if (insideFarm && isSeed(drop.getType())) continue;
                    if (doubleDrops) {
                        drop.setAmount(drop.getAmount() * 2);
                    }
                    allDrops.add(drop);
                }

                player.spawnParticle(org.bukkit.Particle.BLOCK, b.getLocation().add(0.5, 0.3, 0.5), 6, 0.2, 0.2, 0.2, 0.05, b.getBlockData());

                if (insideFarm) {
                    BlockPos pos = BlockPos.fromLocation(b.getLocation());
                    int maxAge = 7;
                    org.bukkit.block.data.BlockData bd = material.createBlockData();
                    if (bd instanceof Ageable ageable) {
                        maxAge = ageable.getMaximumAge();
                    }
                    pMap.put(pos, new RegrowthBlock(b.getLocation(), material, maxAge));
                    ageZeroUpdates.put(b.getLocation(), 0);
                } else {
                    if (replenishLvl > 0 && isReplantable(material)) {
                        Ageable ageable = (Ageable) b.getBlockData();
                        ageable.setAge(0);
                        b.setBlockData(ageable);
                    } else {
                        b.setType(Material.AIR);
                    }
                }
            }

            Block center = blocks.get(0);
            player.playSound(center.getLocation(), center.getBlockData().getSoundGroup().getBreakSound(), 1.0f, 1.0f);
            if (anyDoubleDrops) {
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.5f);
            }

            if (totalXpGained > 0.0) {
                addFarmingXp(player, data, totalXpGained);
            }

            if (totalEssenceGained > 0) {
                data.addEssence(totalEssenceGained);
                plugin.getLanguageManager().sendActionBar(player, "actionbar.essence-found", com.github.gensprout.lang.LanguageManager.values("amount", String.valueOf(totalEssenceGained)));
            }

            boolean shouldAutoSell = globalAutoSell && (autoSellLvl > 0);
            double totalAutoSellEarnings = 0.0;
            int totalAutoSoldQuantity = 0;

            for (ItemStack drop : allDrops) {
                boolean isCropItem = com.github.gensprout.economy.SellManager.getCropConfigName(drop.getType()) != null;
                if (shouldAutoSell && isCropItem) {
                    com.github.gensprout.economy.SellManager.SellResult res = com.github.gensprout.economy.SellManager.sellItemStack(plugin, player, drop, 1.0);
                    if (res.itemsSold > 0) {
                        totalAutoSellEarnings += res.earnings;
                        totalAutoSoldQuantity += res.itemsSold;
                        if (res.xpEarned > 0.0) {
                            addFarmingXp(player, data, res.xpEarned);
                        }
                        continue;
                    }
                }
                player.getInventory().addItem(drop).forEach((index, item) -> {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                });
            }

            if (totalAutoSoldQuantity > 0) {
                com.github.gensprout.economy.EconomyHook.deposit(player, totalAutoSellEarnings);
                plugin.getLanguageManager().sendActionBar(player, "actionbar.autosell", com.github.gensprout.lang.LanguageManager.values(
                        "amount", String.valueOf(totalAutoSoldQuantity),
                        "total", com.github.gensprout.economy.EconomyHook.format(totalAutoSellEarnings)
                ));
            }

            if (!ageZeroUpdates.isEmpty()) {
                FarmCropView.sendFakeCropsBatch(plugin, player, ageZeroUpdates, 7);
            }
        } finally {
            isAreaHarvesting.set(false);
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
