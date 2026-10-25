package com.windsor.meowsEnchants.tracking;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

/** Tunables under {@code tracking:} in tracking.yml; a key missing from an older file keeps its default. */
public record TrackingSettings(
        List<Double> radii,
        int scanIntervalTicks,
        int lookaheadTicks,
        double maxTurnDegrees,
        double directAimDistance,
        double giveUpAngleDegrees,
        int maxTrackingTicks,
        int pvpCombatSeconds,
        Set<String> alwaysPvpWorlds,
        String particle,
        int particleCountMin,
        int particleCountMax,
        double particleOffset,
        double particleSpeed,
        double tailOffset,
        String sound,
        String soundCategory,
        float soundVolume,
        float soundPitch) {

    public static final TrackingSettings DEFAULTS = new TrackingSettings(
            List.of(1.5, 2.5, 3.5), 5, 10, 20.0, 6.0, 90.0, 200, 15, Set.of("milk:pvp"),
            "ENCHANT", 1, 2, 0.05, 0.0, 0.3,
            "block.note_block.chime", "PLAYERS", 1.0f, 1.0f);

    public TrackingSettings {
        radii = List.copyOf(radii);
        alwaysPvpWorlds = Set.copyOf(alwaysPvpWorlds);
    }

    public static TrackingSettings from(ConfigurationSection section) {
        TrackingSettings d = DEFAULTS;
        if (section == null) return d;
        List<Double> radii = new ArrayList<>(d.radii);
        List<?> configured = section.getList("radius");
        if (configured != null) {
            for (int i = 0; i < configured.size(); i++) {
                if (configured.get(i) instanceof Number number && number.doubleValue() > 0.0) {
                    if (i < radii.size()) radii.set(i, number.doubleValue());
                    else radii.add(number.doubleValue());
                }
            }
        }
        Set<String> worlds = new LinkedHashSet<>();
        List<String> configuredWorlds = section.isList("always_pvp_worlds")
                ? section.getStringList("always_pvp_worlds") : List.copyOf(d.alwaysPvpWorlds);
        for (String world : configuredWorlds) worlds.add(world.toLowerCase(Locale.ROOT));
        int countMin = Math.max(0, section.getInt("particle.count_min", d.particleCountMin));
        return new TrackingSettings(
                radii,
                Math.max(1, section.getInt("scan_interval_ticks", d.scanIntervalTicks)),
                Math.max(1, section.getInt("lookahead_ticks", d.lookaheadTicks)),
                Math.clamp(section.getDouble("max_turn_degrees", d.maxTurnDegrees), 0.1, 180.0),
                Math.max(0.0, section.getDouble("direct_aim_distance", d.directAimDistance)),
                Math.clamp(section.getDouble("give_up_angle_degrees", d.giveUpAngleDegrees), 0.1, 180.0),
                Math.max(1, section.getInt("max_tracking_ticks", d.maxTrackingTicks)),
                Math.max(0, section.getInt("pvp_combat_seconds", d.pvpCombatSeconds)),
                worlds,
                section.getString("particle.type", d.particle),
                countMin,
                Math.max(countMin, section.getInt("particle.count_max", d.particleCountMax)),
                Math.max(0.0, section.getDouble("particle.offset", d.particleOffset)),
                Math.max(0.0, section.getDouble("particle.speed", d.particleSpeed)),
                Math.max(0.0, section.getDouble("particle.tail_offset", d.tailOffset)),
                section.getString("sound.type", d.sound),
                section.getString("sound.category", d.soundCategory),
                (float) Math.max(0.0, section.getDouble("sound.volume", d.soundVolume)),
                (float) Math.clamp(section.getDouble("sound.pitch", d.soundPitch), 0.5, 2.0));
    }

    /** Levels above the configured list reuse its last radius. */
    public double radius(int level) {
        return radii.get(Math.clamp(level, 1, radii.size()) - 1);
    }

    public double maxTurnRadians() { return Math.toRadians(maxTurnDegrees); }

    public double giveUpAngleRadians() { return Math.toRadians(giveUpAngleDegrees); }

    public long pvpCombatNanos() { return TimeUnit.SECONDS.toNanos(pvpCombatSeconds); }

    /** Matches either the namespaced world key ({@code milk:pvp}) or the Bukkit world name ({@code milk_pvp}). */
    public boolean isAlwaysPvp(World world) {
        if (world == null) return false;
        return alwaysPvpWorlds.contains(world.getKey().toString().toLowerCase(Locale.ROOT))
                || alwaysPvpWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }
}
