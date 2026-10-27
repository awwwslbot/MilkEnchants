package com.windsor.meowsEnchants.arrowrain;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** Pure geometry and timing for one settings snapshot, shared by the listener and deterministic tests. */
public final class RainPattern {
    private final ArrowRainSettings settings;

    public RainPattern(ArrowRainSettings settings) { this.settings = settings; }

    public ArrowRainSettings settings() { return settings; }
    public double height() { return settings.height(); }
    public double speed() { return settings.speed(); }
    public int count(int level) { return Math.max(0, (int) settings.arrows().getValue(level)); }
    public double radius(int level) { return Math.max(0.0, settings.radius().getValue(level)); }
    public int extraDurability(int level) { return Math.max(0, (int) settings.extraDurability().getValue(level)); }
    public int lastSpawnTick(int level) { return Math.max(1, (int) settings.releaseTicks().getValue(level)); }

    public record Drop(double x, double z, long tick) {}

    public List<Drop> drops(int level, RandomGenerator random) {
        return drops(level, count(level), random);
    }

    public List<Drop> drops(int level, int ammunition, RandomGenerator random) {
        int count = Math.clamp(ammunition, 0, count(level));
        double radius = radius(level);
        int lastSpawnTick = lastSpawnTick(level);
        List<Drop> drops = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            // sqrt produces uniform area density, rather than concentrating arrows at the centre.
            double distance = radius * Math.sqrt(random.nextDouble());
            double angle = random.nextDouble() * Math.TAU;
            drops.add(new Drop(Math.cos(angle) * distance, Math.sin(angle) * distance,
                    count == 1 ? 1L : 1L + (long) i * (lastSpawnTick - 1) / (count - 1)));
        }
        return List.copyOf(drops);
    }
}
