package com.windsor.meowsEnchants.arrowrain;

import java.util.Collection;
import java.util.OptionalDouble;

/**
 * Where one rain arrow spawns in its own column, from the blocks of that column only (no Bukkit, unit-tested
 * directly). A ceiling is the lower face of the first block above that stops an arrow, counted as a full block;
 * the world's build limit is a ceiling too. Headroom is measured from the impact height {@code hY}. Only the block
 * holding the impact height is looked at more closely, by its collision boxes at the column's point: a partial
 * block the point stands on (bottom slab, dirt path, carpet, snow) is ground, not a ceiling.
 */
public final class RainHeight {
    /** Rain arrows are 0.5 blocks tall: spawning at {@code ceiling - 0.5} keeps the whole arrow under it. */
    public static final double ARROW_HEIGHT = 0.5;
    /** How far the impact column is probed back along the source arrow's flight, to leave a wall or ceiling. */
    public static final double PROBE_BACKOFF = 1.0e-3;

    private RainHeight() {}

    /** One column of blocks at a fixed point x/z; {@code blocks(y)} is true when block y stops arrows. */
    @FunctionalInterface
    public interface Column {
        boolean blocks(int y);

        /**
         * Whether block y stops an arrow at this column's point, {@code fy} (0 &le; fy &lt; 1) up inside that block:
         * see {@link #stops}. Asked only for the block holding the impact height, and only when {@code blocks(y)}.
         * Defaults to the whole block.
         */
        default boolean blocksPoint(int y, double fy) { return true; }
    }

    /** One collision box of a block, in block-relative coordinates (a fence reaches maxY 1.5). */
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}

    /**
     * Whether a block with these collision boxes stops an arrow at the point (fx, fy, fz) inside it: one of the
     * boxes over the point's x/z reaches above fy. That is the point inside the block, or the block hanging over
     * it (a top slab or upper trapdoor hit from below). A point standing on a bottom slab, dirt path, farmland,
     * carpet, snow layers or a stair's low step, or beside a fence post or glass pane, is not stopped.
     */
    public static boolean stops(Collection<Box> boxes, double fx, double fy, double fz) {
        for (Box box : boxes) {
            if (fx >= box.minX() && fx <= box.maxX() && fz >= box.minZ() && fz <= box.maxZ() && box.maxY() > fy) {
                return true;
            }
        }
        return false;
    }

    /**
     * The ceiling above the impact point itself, worked out once on the source arrow's region thread.
     *
     * @param hY      impact height (the source arrow's position)
     * @param probeY  height of the point the arrow hit from: its position moved a hair back along its flight
     * @param column  the probe point's column
     * @param maxY    blocks exist below this y (exclusive build limit)
     * @return absolute y of the ceiling, at most {@link #reach}; {@code hY} (no headroom) when the probe block
     *         stops arrows at the probe point ({@link Column#blocksPoint}) or lies outside the world
     */
    public static double centreCeiling(double hY, double probeY, double height, double minHeight,
                                       int minY, int maxY, Column column) {
        int probe = floor(probeY);
        if (probe < minY || probe >= maxY || column.blocks(probe) && column.blocksPoint(probe, probeY - probe)) return hY;
        return firstCeiling(probe + 1, Math.min(reach(hY, height, minHeight), maxY), column);
    }

    /**
     * The spawn height of one rain arrow in {@code column}, or empty when this arrow is skipped.
     *
     * @param centreCeiling {@link #centreCeiling} of this rain's impact point; caps columns whose impact-height
     *                      block stops arrows at the column's point (slopes, walls), so they never reach a
     *                      separate cave above
     */
    public static OptionalDouble spawnY(double hY, double height, double minHeight, double centreCeiling,
                                        int minY, int maxY, Column column) {
        if (!(hY >= minY && hY < maxY)) return OptionalDouble.empty();
        double top = top(hY, height);
        double limit = Math.min(reach(hY, height, minHeight), maxY);
        int base = floor(hY);
        double lowest = base; // The arrow may not spawn below this.
        double ceiling;
        boolean solidBase = column.blocks(base);
        if (!solidBase || !column.blocksPoint(base, hY - base)) {
            // Air at the impact height, or a partial block the point stands on: then only above hY is free.
            if (solidBase) lowest = hY;
            ceiling = firstCeiling(base + 1, limit, column);
        } else {
            // Climb out of the solid part, then look for the ceiling of that air, never above the impact's own.
            double cap = Math.min(limit, centreCeiling);
            int air = base + 1;
            while (air < cap && column.blocks(air)) air++;
            if (air >= cap) return OptionalDouble.empty();
            ceiling = firstCeiling(air + 1, cap, column);
            if (ceiling - air < ARROW_HEIGHT) return OptionalDouble.empty();
            lowest = air;
        }
        if (ceiling - hY < minHeight) return OptionalDouble.empty();
        // Enough room for the full height: exactly hY + height, as before this rule existed.
        double y = ceiling >= top ? hY + height : Math.min(hY + height, ceiling - ARROW_HEIGHT);
        // Only reachable with min_height > height + 0.5 (this column's air starts above the highest spawn height),
        // or min_height < 0.5 over a partial block (the arrow would start inside it).
        return y < lowest ? OptionalDouble.empty() : OptionalDouble.of(y);
    }

    /** {@code hY + height + 0.5}: a ceiling at or above this leaves the full height free. */
    static double top(double hY, double height) { return hY + height + ARROW_HEIGHT; }

    /**
     * How far up a ceiling can still change the result: {@link #top}, or {@code hY + minHeight} when
     * min_height is configured above it (headroom must reach min_height, the spawn height stays at most height).
     */
    static double reach(double hY, double height, double minHeight) {
        return hY + Math.max(height + ARROW_HEIGHT, minHeight);
    }

    /** Lower face of the first blocking block in {@code [from, limit)}, else {@code limit}. */
    private static double firstCeiling(int from, double limit, Column column) {
        for (int y = from; y < limit; y++) {
            if (column.blocks(y)) return y;
        }
        return limit;
    }

    private static int floor(double y) { return (int) Math.floor(y); }
}
