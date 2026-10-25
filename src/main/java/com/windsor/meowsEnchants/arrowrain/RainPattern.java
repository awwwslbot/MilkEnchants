package com.windsor.meowsEnchants.arrowrain;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Pure geometry and timing, shared by the listener and deterministic tests. */
public final class RainPattern {
    public static final double HEIGHT = 20.0;
    public static final double SPEED = 2.5;
    private RainPattern() {}

    public static final int MAX_LEVEL = 5;
    public static int level(int level) { return Math.clamp(level, 1, MAX_LEVEL); }
    public static int count(int level) { return 8 << (level(level) - 1); }
    public static double radius(int level) { return level(level) + 0.5; }
    public static int extraDurability(int level) { return (4 << (level(level) - 1)) - 1; }
    public static int lastSpawnTick(int level) { return 20 * level(level); }

    public record Drop(double x, double z, long tick) {}

    public static List<Drop> drops(int level, RandomGenerator random) {
        return drops(level, count(level), random);
    }

    public static List<Drop> drops(int level, int ammunition, RandomGenerator random) {
        int count = Math.clamp(ammunition, 0, count(level));
        List<Drop> drops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // sqrt produces uniform area density, rather than concentrating arrows at the centre.
            double distance = radius(level) * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Math.TAU;
            drops.add(new Drop(Math.cos(angle) * distance, Math.sin(angle) * distance,
                    count == 1 ? 1L : 1L + (long) i * (lastSpawnTick(level) - 1) / (count - 1)));
        }
        return List.copyOf(drops);
    }
}
