package com.github.gensprout.farming;

import com.github.gensprout.GenSprout;
import com.github.gensprout.player.PlayerData;
import com.github.gensprout.util.BlockPos;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Makes the shared farm region's crop visually appear as each player's own current
 * Prestige-unlocked crop, without changing the real underlying block (which stays Wheat
 * server-side, so the existing growth-animation/regrowth system needs no changes at all).
 * This uses Bukkit's native per-player {@code sendBlockChange}, so no packet library is needed -
 * two players standing at the exact same coordinates can each see a different crop growing.
 */
public class FarmCropView {

    private FarmCropView() {
    }

    /**
     * Sends a single player a fake crop block matching their own current tier, scaling the given
     * real age/maxAge onto that crop's own max age (e.g. Wheat age 7/7 maps to Beetroot's 3/3).
     */
    public static void sendFakeCrop(GenSprout plugin, Player player, Location loc, int realAge, int realMaxAge) {
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        String cropKey = CropProgression.getCurrentCropKey(plugin, data);
        FarmCropType type = FarmCropType.byConfigKey(cropKey);

        BlockData fakeData = type.getCropBlock().createBlockData();
        if (fakeData instanceof Ageable ageable) {
            int scaledAge = realMaxAge <= 0 ? ageable.getMaximumAge()
                    : (int) Math.round(((double) realAge / realMaxAge) * ageable.getMaximumAge());
            ageable.setAge(Math.max(0, Math.min(ageable.getMaximumAge(), scaledAge)));
            fakeData = ageable;
        }
        player.sendBlockChange(loc, fakeData);
    }

    /**
     * Sends a player multiple fake crop block updates in a single batched packet using sendBlockChanges.
     */
    public static void sendFakeCropsBatch(GenSprout plugin, Player player, Map<Location, Integer> locationAgeMap, int realMaxAge) {
        if (locationAgeMap == null || locationAgeMap.isEmpty()) return;
        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        String cropKey = CropProgression.getCurrentCropKey(plugin, data);
        FarmCropType type = FarmCropType.byConfigKey(cropKey);
        BlockData templateData = type.getCropBlock().createBlockData();

        for (Map.Entry<Location, Integer> entry : locationAgeMap.entrySet()) {
            Location loc = entry.getKey();
            int realAge = entry.getValue();
            BlockData fakeData = templateData.clone();
            if (fakeData instanceof Ageable ageable) {
                int scaledAge = realMaxAge <= 0 ? ageable.getMaximumAge()
                        : (int) Math.round(((double) realAge / realMaxAge) * ageable.getMaximumAge());
                ageable.setAge(Math.max(0, Math.min(ageable.getMaximumAge(), scaledAge)));
                fakeData = ageable;
            }
            player.sendBlockChange(loc, fakeData);
        }
    }

    /**
     * Sends every online player in the region's world a fake crop matching their own tier, at the
     * given real age/maxAge. Call this whenever the real (shared) block's growth state changes so
     * everyone's personalized view stays in sync with the real animation timing.
     */
    public static void syncAllPlayers(GenSprout plugin, Location loc, int realAge, int realMaxAge) {
        World world = loc.getWorld();
        if (world == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getWorld().equals(world)) {
                sendFakeCrop(plugin, player, loc, realAge, realMaxAge);
            }
        }
    }

    /**
     * Refreshes the visual representation of crops within a single chunk for a specific player.
     * Uses Paper's sendBlockChanges API to update multiple blocks in a single packet.
     */
    public static void refreshChunkForPlayer(GenSprout plugin, Player player, Chunk chunk, FarmRegion region) {
        if (region == null) return;
        if (!chunk.getWorld().getName().equals(region.getWorldName())) return;
        if (!player.getWorld().equals(chunk.getWorld())) return;

        int chunkMinX = chunk.getX() << 4;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getZ() << 4;
        int chunkMaxZ = chunkMinZ + 15;

        int startX = Math.max(region.getMinX(), chunkMinX);
        int endX = Math.min(region.getMaxX(), chunkMaxX);
        int startZ = Math.max(region.getMinZ(), chunkMinZ);
        int endZ = Math.min(region.getMaxZ(), chunkMaxZ);

        if (startX > endX || startZ > endZ) return;

        PlayerData data = plugin.getPlayerManager().getPlayerData(player.getUniqueId());
        String cropKey = CropProgression.getCurrentCropKey(plugin, data);
        FarmCropType type = FarmCropType.byConfigKey(cropKey);
        BlockData fakeData = type.getCropBlock().createBlockData();

        int cropY = region.getMinY() + 1;
        List<BlockState> states = new ArrayList<>();
        World world = chunk.getWorld();
        Map<BlockPos, FarmingListener.RegrowthBlock> regrowingMap = plugin.getFarmingListener().getPlayerRegrowingBlockMap(player.getUniqueId());

        for (int x = startX; x <= endX; x++) {
            for (int z = startZ; z <= endZ; z++) {
                Block block = chunk.getBlock(x & 15, cropY, z & 15);
                if (block.getType() == Material.AIR) continue;

                BlockPos pos = new BlockPos(world.getName(), x, cropY, z);
                FarmingListener.RegrowthBlock regBlock = regrowingMap != null ? regrowingMap.get(pos) : null;

                BlockData finalFakeData = fakeData.clone();
                if (finalFakeData instanceof Ageable fakeAgeable) {
                    int age;
                    if (regBlock != null) {
                        long elapsed = System.currentTimeMillis() - regBlock.harvestTime;
                        double progress = Math.min(1.0, (double) elapsed / 5000.0);
                        age = (int) Math.round(progress * fakeAgeable.getMaximumAge());
                    } else {
                        age = fakeAgeable.getMaximumAge();
                    }
                    fakeAgeable.setAge(Math.max(0, Math.min(fakeAgeable.getMaximumAge(), age)));
                    finalFakeData = fakeAgeable;
                }

                BlockState state = block.getState();
                state.setBlockData(finalFakeData);
                states.add(state);
            }
        }

        if (!states.isEmpty()) {
            player.sendBlockChanges(states);
        }
    }

    /**
     * Fully refreshes one player's view of the entire farm region to match their current tier.
     */
    public static void refreshRegionForPlayer(GenSprout plugin, Player player, FarmRegion region) {
        if (region == null) return;
        World world = Bukkit.getWorld(region.getWorldName());
        if (world == null || !player.getWorld().equals(world)) return;

        int minChunkX = region.getMinX() >> 4;
        int maxChunkX = region.getMaxX() >> 4;
        int minChunkZ = region.getMinZ() >> 4;
        int maxChunkZ = region.getMaxZ() >> 4;

        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                if (world.isChunkLoaded(cx, cz)) {
                    refreshChunkForPlayer(plugin, player, world.getChunkAt(cx, cz), region);
                }
            }
        }
    }
}
