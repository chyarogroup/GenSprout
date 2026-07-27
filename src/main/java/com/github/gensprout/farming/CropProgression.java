package com.github.gensprout.farming;

import com.github.gensprout.GenSprout;
import com.github.gensprout.player.PlayerData;

import java.util.List;

/**
 * Ties which crops actually pay out rewards to a player's Prestige level: every
 * {@code crop-progression.prestiges-per-crop} prestiges, the next crop in the configured order
 * unlocks. The first crop in the list is always unlocked (starter crop).
 */
public class CropProgression {

    private CropProgression() {
    }

    /**
     * The default crop unlock order, used if crop-progression.order is missing or empty in
     * config.yml (e.g. on a server whose config.yml predates this feature and was never
     * regenerated - Bukkit does not merge new keys into an existing config file automatically).
     */
    private static final List<String> DEFAULT_ORDER = List.of("WHEAT", "POTATOES", "CARROTS", "BEETROOTS");

    public static List<String> getCropOrder(GenSprout plugin) {
        List<String> order = plugin.getConfig().getStringList("crop-progression.order");
        return order.isEmpty() ? DEFAULT_ORDER : order;
    }

    /**
     * Number of crops currently unlocked for this player (always at least 1, unless the config
     * list is empty).
     */
    public static int getUnlockedCount(GenSprout plugin, PlayerData data) {
        List<String> order = getCropOrder(plugin);
        if (order.isEmpty()) return 0;
        int perCrop = Math.max(1, plugin.getConfig().getInt("crop-progression.prestiges-per-crop", 10));
        int unlocked = 1 + (data.getPrestige() / perCrop);
        return Math.min(unlocked, order.size());
    }

    /**
     * Whether the given crop config key (e.g. "WHEAT") is currently unlocked for this player.
     * Crops not present in the configured order always count as unlocked (fail open, so a typo
     * in the config never silently voids rewards for a whole crop type).
     */
    public static boolean isUnlocked(GenSprout plugin, PlayerData data, String cropConfigKey) {
        List<String> order = getCropOrder(plugin);
        int idx = order.indexOf(cropConfigKey);
        if (idx < 0) return true;
        return idx < getUnlockedCount(plugin, data);
    }

    /**
     * The crop config key currently "active" for this player - i.e. the most recently unlocked
     * crop in the order. This is what a player personally sees/harvests in the shared farm region.
     */
    public static String getCurrentCropKey(GenSprout plugin, PlayerData data) {
        List<String> order = getCropOrder(plugin);
        if (order.isEmpty()) return "WHEAT";
        int unlockedCount = getUnlockedCount(plugin, data);
        int idx = Math.max(0, Math.min(order.size(), unlockedCount) - 1);
        return order.get(idx);
    }

    /**
     * The prestige level required to unlock the given crop, or -1 if it's not in the list.
     */
    public static int getRequiredPrestigeForCrop(GenSprout plugin, String cropConfigKey) {
        List<String> order = getCropOrder(plugin);
        int idx = order.indexOf(cropConfigKey);
        if (idx < 0) return -1;
        int perCrop = Math.max(1, plugin.getConfig().getInt("crop-progression.prestiges-per-crop", 10));
        return idx * perCrop;
    }
}
