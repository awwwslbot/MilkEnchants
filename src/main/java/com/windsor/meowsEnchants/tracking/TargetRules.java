package com.windsor.meowsEnchants.tracking;

import com.windsor.meowsEnchants.tracking.TargetChoice.Priority;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Allay;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Tameable;
import org.bukkit.metadata.MetadataValue;

/**
 * Who a tracking projectile may pick and in which priority. The firework blast filter uses the same
 * rules without the path and line-of-sight conditions. Callers run on the projectile's region thread
 * and pass only targets that region owns; the shooter is read through its shot-time view.
 */
final class TargetRules {
    /** Land-claim rules; {@link #NONE} when no protection plugin is installed. */
    public interface Protection {
        boolean allowsPvp(Player shooter, Location shooterLocation, Player target);
        boolean allowsHarm(Player shooter, Mob target);

        Protection NONE = new Protection() {
            @Override public boolean allowsPvp(Player shooter, Location shooterLocation, Player target) { return true; }
            @Override public boolean allowsHarm(Player shooter, Mob target) { return true; }
        };
    }

    /** The PvP table, kept as an interface so tests can stub it. */
    public interface Combat { boolean inCombat(UUID player); }

    /** The vanilla {@code minecraft:pvp} game rule of a world (true when unset). */
    public static final Predicate<World> VANILLA_PVP = world -> {
        Boolean value = world.getGameRuleValue(GameRules.PVP);
        return value == null || value;
    };

    /** Friendly mobs that are never targets (and never hurt by a tracking firework's blast). */
    static final List<Class<? extends Mob>> NEVER_TRACKED = List.of(
            AbstractVillager.class, // villagers and wandering traders
            IronGolem.class, Snowman.class, CopperGolem.class,
            Allay.class);

    private final TrackingSettings settings;
    private final Combat combat;
    private final Protection protection;
    private final Predicate<World> pvpRule;

    public TargetRules(TrackingSettings settings, Combat combat, Protection protection, Predicate<World> pvpRule) {
        this.settings = settings;
        this.combat = combat;
        this.protection = protection;
        this.pvpRule = pvpRule;
    }

    /** A custom name protects mobs only; armour stands, mannequins and players are not "creatures". */
    public static boolean isNamed(Entity entity) {
        return entity instanceof Mob && entity.customName() != null;
    }

    /** Category from the target's own state, or null when it can never be picked. */
    public Priority priority(LivingEntity entity, Shooter.View shooter) {
        if (entity.isDead() || entity.getUniqueId().equals(shooter.id())) return null;
        if (entity instanceof Player player) return pvpPlayer(player, shooter) ? Priority.PVP_PLAYER : null;
        if (!(entity instanceof Mob mob) || isNamed(mob)) return null;
        if (mob instanceof Enemy) {
            // Neutral monsters count only while they are attacking a player.
            if ((mob instanceof Enderman || mob instanceof PigZombie || mob instanceof Piglin)
                    && !(mob.getTarget() instanceof Player)) return null;
            return Priority.HOSTILE;
        }
        if (mob instanceof Tameable tameable && tameable.isTamed()) return null;
        for (Class<? extends Mob> type : NEVER_TRACKED) if (type.isInstance(mob)) return null;
        if (mob.isLeashed()) return null;
        for (Entity passenger : mob.getPassengers()) if (passenger instanceof Player) return null;
        return Priority.FRIENDLY;
    }

    private boolean pvpPlayer(Player target, Shooter.View shooter) {
        GameMode mode = target.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
        if (target.hasMetadata("NPC") || vanished(target, shooter)) return false;
        return settings.isAlwaysPvp(target.getWorld()) || combat.inCombat(target.getUniqueId());
    }

    /** Hidden from the shooter (CMI vanish hides players per viewer) or carrying a common vanish flag. */
    static boolean vanished(Player target, Shooter.View shooter) {
        if (!shooter.player().canSee(target)) return true;
        for (MetadataValue value : target.getMetadata("vanished")) if (value.asBoolean()) return true;
        return false;
    }

    /**
     * Location rules after the cheaper filters: vanilla pvp at both ends for players, and the
     * protection plugin's pvp / animal-killing / mob-killing decision for the shooter.
     */
    public boolean permitted(LivingEntity entity, Priority priority, Shooter.View shooter) {
        if (priority == Priority.PVP_PLAYER) {
            Location from = shooter.location();
            if (from == null || from.getWorld() == null || !(entity instanceof Player target)) return false;
            return pvpRule.test(from.getWorld()) && pvpRule.test(target.getWorld())
                    && protection.allowsPvp(shooter.player(), from, target);
        }
        return entity instanceof Mob mob && protection.allowsHarm(shooter.player(), mob);
    }

    /** Whether the target could be picked at all; the firework blast only hurts these. */
    public boolean trackable(LivingEntity entity, Shooter.View shooter) {
        Priority priority = priority(entity, shooter);
        return priority != null && permitted(entity, priority, shooter);
    }
}
