package com.windsor.meowsEnchants.tracking;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/** World reads limited to chunks owned by the current Folia region; anything beyond is skipped. */
final class RegionView {
    private RegionView() {}

    /** Entities in the area, skipping chunks another region owns (one query when all are owned). */
    static Collection<Entity> nearbyOwned(World world, BoundingBox area) {
        int minX = (int) Math.floor(area.getMinX()) >> 4, maxX = (int) Math.floor(area.getMaxX()) >> 4;
        int minZ = (int) Math.floor(area.getMinZ()) >> 4, maxZ = (int) Math.floor(area.getMaxZ()) >> 4;
        if (allOwned(world, minX, minZ, maxX, maxZ, false)) return world.getNearbyEntities(area);
        Map<UUID, Entity> found = new LinkedHashMap<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz)) continue;
                double x0 = Math.max(area.getMinX(), cx << 4), x1 = Math.min(area.getMaxX(), (cx << 4) + 16);
                double z0 = Math.max(area.getMinZ(), cz << 4), z1 = Math.min(area.getMaxZ(), (cz << 4) + 16);
                if (x0 > x1 || z0 > z1) continue;
                BoundingBox part = new BoundingBox(x0, area.getMinY(), z0, x1, area.getMaxY(), z1);
                // An entity box spanning two owned chunks is returned by both queries.
                for (Entity entity : world.getNearbyEntities(part)) found.putIfAbsent(entity.getUniqueId(), entity);
            }
        }
        return found.values();
    }

    /**
     * No collidable block between the two points (fluids and passable blocks such as grass do not
     * block). A ray crossing a chunk that is unloaded or owned by another region counts as blocked.
     */
    static boolean visible(World world, Vector from, Vector to) {
        int minX = (int) Math.floor(Math.min(from.getX(), to.getX())) >> 4, maxX = (int) Math.floor(Math.max(from.getX(), to.getX())) >> 4;
        int minZ = (int) Math.floor(Math.min(from.getZ(), to.getZ())) >> 4, maxZ = (int) Math.floor(Math.max(from.getZ(), to.getZ())) >> 4;
        if (!allOwned(world, minX, minZ, maxX, maxZ, true)) return false;
        Vector direction = to.clone().subtract(from);
        double distance = direction.length();
        if (distance < 1.0e-6) return true;
        return world.rayTraceBlocks(from.toLocation(world), direction.multiply(1.0 / distance), distance,
                FluidCollisionMode.NEVER, true) == null;
    }

    private static boolean allOwned(World world, int minX, int minZ, int maxX, int maxZ, boolean loaded) {
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                if (!Bukkit.isOwnedByCurrentRegion(world, cx, cz) || loaded && !world.isChunkLoaded(cx, cz)) return false;
            }
        }
        return true;
    }
}
