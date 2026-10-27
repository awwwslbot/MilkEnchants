package com.windsor.meowsEnchants.utils;

import com.windsor.meowsEnchants.MeowsEnchants;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class SkylliaCompatibility {

    private SkylliaCompatibility() {
    }

    private static int regionSize() {
        return MeowsEnchants.getGlobalSettings().skylliaRegionSize();
    }

    public static boolean isEnabled() {
        return MeowsEnchants.isSkylliaCompatibilityEnabled();
    }

    public static boolean canUseIslandRestrictedAction(Player player) {
        if (!isEnabled()) {
            return true;
        }
        if (player.isOp()) {
            return true;
        }
        if (!PlaceholderAPIHook.isAvailable()) {
            return false;
        }
        String result = PlaceholderAPIHook.setPlaceholders(player, "%skyllia_visited_on_island%");
        return "true".equalsIgnoreCase(result);
    }

    public static boolean isInProtectedRegion(Player player) {
        if (!isEnabled() || player.isOp()) {
            return false;
        }
        Location loc = player.getLocation();
        double x = loc.getX();
        double z = loc.getZ();
        int size = regionSize();
        return x >= 0 && x <= size && z >= 0 && z <= size;
    }

    public static boolean isInSameRegion(int baseX, int baseZ, int x, int z) {
        if (!isEnabled()) {
            return true;
        }
        int size = regionSize();
        int baseRegionX = Math.floorDiv(baseX, size);
        int baseRegionZ = Math.floorDiv(baseZ, size);
        int regionX = Math.floorDiv(x, size);
        int regionZ = Math.floorDiv(z, size);
        return regionX == baseRegionX && regionZ == baseRegionZ;
    }

    public static double limitHorizontalDistanceToRegion(Location start, double dx, double dz, double maxDistance) {
        if (!isEnabled()) {
            return maxDistance;
        }
        double startX = start.getX();
        double startZ = start.getZ();
        int size = regionSize();
        int startRegionX = Math.floorDiv(start.getBlockX(), size);
        int startRegionZ = Math.floorDiv(start.getBlockZ(), size);

        double maxHoriz = Double.MAX_VALUE;
        if (dx > 0) {
            double boundaryX = (startRegionX + 1) * size;
            double dist = (boundaryX - startX) / dx;
            if (dist > 0) maxHoriz = Math.min(maxHoriz, dist);
        } else if (dx < 0) {
            double boundaryX = startRegionX * size;
            double dist = (boundaryX - startX) / dx;
            if (dist > 0) maxHoriz = Math.min(maxHoriz, dist);
        }
        if (dz > 0) {
            double boundaryZ = (startRegionZ + 1) * size;
            double dist = (boundaryZ - startZ) / dz;
            if (dist > 0) maxHoriz = Math.min(maxHoriz, dist);
        } else if (dz < 0) {
            double boundaryZ = startRegionZ * size;
            double dist = (boundaryZ - startZ) / dz;
            if (dist > 0) maxHoriz = Math.min(maxHoriz, dist);
        }
        return Math.min(maxDistance, maxHoriz);
    }
}
