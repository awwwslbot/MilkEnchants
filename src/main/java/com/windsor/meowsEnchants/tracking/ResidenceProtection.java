package com.windsor.meowsEnchants.tracking;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Mirrors the damage checks of Residence 6.0.3.3's own listeners (ResidenceEntityListener
 * onPlayerDamageByPlayer / MonsterKilling / AnimalKilling) without their deny messages. Residence is
 * a soft dependency with no public Maven artifact for this version, so it is bound by reflection;
 * when it is absent, disabled or incompatible every check allows, as if no claims existed.
 */
final class ResidenceProtection implements TargetRules.Protection {
    /** The few Residence calls the damage semantics need. */
    interface Api {
        boolean listenerDisabled(World world);
        /** The claim at a location, or null in the wilderness. */
        Object residenceAt(Location location);
        /** {@code res.getPermissions().has(flag, FlagCombo.OnlyFalse)}. */
        boolean flagFalse(Object residence, String flag);
        /** {@code res.getPermissions().playerHas(player, flag, FlagCombo.OnlyFalse)}. */
        boolean playerFlagFalse(Object residence, Player player, String flag);
        /** {@code getWorldFlags().getPerms(world).has(flag, FlagCombo.OnlyFalse)}. */
        boolean worldFlagFalse(World world, String flag);
        /** {@code FlagPermissions.shouldIgnoreCheck(flag, entity)}. */
        boolean ignoresCheck(String flag, Entity entity);
        /** {@code FlagPermissions.getPerms(location, player).playerHas(player, flag, true)}. */
        boolean playerHasAt(Location location, Player player, String flag);
        boolean isAnimal(Entity entity);
        boolean isResAdmin(Player player);
    }

    private final Supplier<Api> api;

    ResidenceProtection(Supplier<Api> api) { this.api = api; }

    /** Binds lazily: Residence enables after this STARTUP plugin and may be disabled or reloaded later. */
    static ResidenceProtection bind(Supplier<Plugin> residence, Logger logger) {
        return new ResidenceProtection(new Binder(residence, logger));
    }

    @Override
    public boolean allowsPvp(Player shooter, Location shooterLocation, Player target) {
        Api residence = api.get();
        if (residence == null || residence.listenerDisabled(target.getWorld())) return true;
        Object attackerRes = residence.residenceAt(shooterLocation);
        Object victimRes = residence.residenceAt(target.getLocation());
        if (attackerRes != null && attackerRes.equals(victimRes)) {
            if (residence.flagFalse(attackerRes, "pvp")) return false;
            return !(residence.playerFlagFalse(attackerRes, target, "friendlyfire")
                    && residence.playerFlagFalse(attackerRes, shooter, "friendlyfire"));
        }
        if (attackerRes != null && residence.flagFalse(attackerRes, "pvp")) return false;
        if (victimRes != null && residence.flagFalse(victimRes, "pvp")) return false;
        return attackerRes != null || victimRes != null || !residence.worldFlagFalse(shooterLocation.getWorld(), "pvp");
    }

    /**
     * Residence checks monsters (MonsterKilling, mobkilling) and animals (AnimalKilling,
     * animalkilling) in separate listeners, so a mob that is both, such as a hoglin, needs both flags.
     */
    @Override
    public boolean allowsHarm(Player shooter, Mob target) {
        Api residence = api.get();
        if (residence == null) return true;
        if (target instanceof Enemy && !allows(residence, shooter, target, "mobkilling")) return false;
        return !residence.isAnimal(target) || allows(residence, shooter, target, "animalkilling");
    }

    private static boolean allows(Api residence, Player shooter, Mob target, String flag) {
        if (residence.ignoresCheck(flag, target)) return true;
        return residence.playerHasAt(target.getLocation(), shooter, flag) || residence.isResAdmin(shooter);
    }

    /** Resolves the reflective API per Residence plugin instance and disables itself after a failure. */
    private static final class Binder implements Supplier<Api> {
        private final Supplier<Plugin> plugin;
        private final Logger logger;
        private final AtomicBoolean warned = new AtomicBoolean();
        private volatile Plugin boundPlugin;
        private volatile Api bound;

        Binder(Supplier<Plugin> plugin, Logger logger) {
            this.plugin = plugin;
            this.logger = logger;
        }

        @Override
        public Api get() {
            Plugin current = plugin.get();
            if (current == null || !current.isEnabled()) return null;
            if (current != boundPlugin) {
                Api api;
                try {
                    api = new ReflectiveApi(current.getClass().getClassLoader());
                } catch (VirtualMachineError e) {
                    throw e;
                } catch (Throwable e) {
                    warn("Residence API not recognised; tracking ignores land claims", e);
                    api = null;
                }
                bound = api == null ? null : new Guarded(api, this);
                boundPlugin = current;
            }
            return bound;
        }

        void warn(String message, Throwable error) {
            if (warned.compareAndSet(false, true)) logger.log(Level.WARNING, message, error);
        }
    }

    /** A call that throws allows the target, as if Residence were absent, and logs once. */
    private record Guarded(Api api, Binder binder) implements Api {
        private <T> T call(Supplier<T> call, T fallback) {
            try {
                return call.get();
            } catch (RuntimeException | LinkageError e) {
                binder.warn("Residence call failed; tracking ignores land claims for this check", e);
                return fallback;
            }
        }

        @Override public boolean listenerDisabled(World world) { return call(() -> api.listenerDisabled(world), true); }
        @Override public Object residenceAt(Location location) { return call(() -> api.residenceAt(location), null); }
        @Override public boolean flagFalse(Object residence, String flag) { return call(() -> api.flagFalse(residence, flag), false); }
        @Override public boolean playerFlagFalse(Object residence, Player player, String flag) { return call(() -> api.playerFlagFalse(residence, player, flag), false); }
        @Override public boolean worldFlagFalse(World world, String flag) { return call(() -> api.worldFlagFalse(world, flag), false); }
        @Override public boolean ignoresCheck(String flag, Entity entity) { return call(() -> api.ignoresCheck(flag, entity), true); }
        @Override public boolean playerHasAt(Location location, Player player, String flag) { return call(() -> api.playerHasAt(location, player, flag), true); }
        @Override public boolean isAnimal(Entity entity) { return call(() -> api.isAnimal(entity), false); }
        @Override public boolean isResAdmin(Player player) { return call(() -> api.isResAdmin(player), false); }
    }

    /**
     * Method handles over the Residence 6.0.3.3 classes, loaded through Residence's own class loader.
     * Each handle is resolved by exact name and type: Class.getMethod would resolve every public
     * signature of the class, and Residence declares methods typed with optional dependencies
     * (WorldEditPlugin), which throw NoClassDefFoundError when those plugins are absent.
     */
    private static final class ReflectiveApi implements Api {
        private static final MethodHandles.Lookup LOOKUP = MethodHandles.publicLookup();
        private final Object residence;
        private final MethodHandle getResidenceManager, getWorldFlags, isDisabledWorldListener, getByLoc, getPermissions, has,
                playerHasCombo, playerHasDefault, getPerms, shouldIgnoreCheck, worldPerms, isAnimal, isResAdmin;
        private final Object onlyFalse;
        private final Map<String, Object> flags = new HashMap<>();

        ReflectiveApi(ClassLoader loader) throws Throwable {
            Class<?> residenceClass = Class.forName("com.bekvon.bukkit.residence.Residence", false, loader);
            Class<?> managerClass = Class.forName("com.bekvon.bukkit.residence.protection.ResidenceManager", false, loader);
            Class<?> claimClass = Class.forName("com.bekvon.bukkit.residence.protection.ClaimedResidence", false, loader);
            Class<?> claimPermsClass = Class.forName("com.bekvon.bukkit.residence.protection.ResidencePermissions", false, loader);
            Class<?> permsClass = Class.forName("com.bekvon.bukkit.residence.protection.FlagPermissions", false, loader);
            Class<?> comboClass = Class.forName("com.bekvon.bukkit.residence.protection.FlagPermissions$FlagCombo", false, loader);
            Class<?> worldClass = Class.forName("com.bekvon.bukkit.residence.protection.WorldFlagManager", false, loader);
            Class<?> utilsClass = Class.forName("com.bekvon.bukkit.residence.utils.Utils", false, loader);
            Class<?> adminClass = Class.forName("com.bekvon.bukkit.residence.containers.ResAdmin", false, loader);
            Class<?> flagsClass = Class.forName("com.bekvon.bukkit.residence.containers.Flags", false, loader);
            residence = LOOKUP.findStatic(residenceClass, "getInstance", MethodType.methodType(residenceClass)).invoke();
            if (residence == null) throw new IllegalStateException("Residence.getInstance() is null");
            getResidenceManager = LOOKUP.findVirtual(residenceClass, "getResidenceManager", MethodType.methodType(managerClass));
            getWorldFlags = LOOKUP.findVirtual(residenceClass, "getWorldFlags", MethodType.methodType(worldClass));
            isDisabledWorldListener = LOOKUP.findVirtual(residenceClass, "isDisabledWorldListener", MethodType.methodType(boolean.class, World.class));
            getByLoc = LOOKUP.findVirtual(managerClass, "getByLoc", MethodType.methodType(claimClass, Location.class));
            getPermissions = LOOKUP.findVirtual(claimClass, "getPermissions", MethodType.methodType(claimPermsClass));
            has = LOOKUP.findVirtual(permsClass, "has", MethodType.methodType(boolean.class, flagsClass, comboClass));
            playerHasCombo = LOOKUP.findVirtual(permsClass, "playerHas", MethodType.methodType(boolean.class, Player.class, flagsClass, comboClass));
            playerHasDefault = LOOKUP.findVirtual(permsClass, "playerHas", MethodType.methodType(boolean.class, Player.class, flagsClass, boolean.class));
            getPerms = LOOKUP.findStatic(permsClass, "getPerms", MethodType.methodType(permsClass, Location.class, Player.class));
            shouldIgnoreCheck = LOOKUP.findStatic(permsClass, "shouldIgnoreCheck", MethodType.methodType(boolean.class, flagsClass, Entity.class));
            worldPerms = LOOKUP.findVirtual(worldClass, "getPerms", MethodType.methodType(permsClass, World.class));
            isAnimal = LOOKUP.findStatic(utilsClass, "isAnimal", MethodType.methodType(boolean.class, Entity.class));
            isResAdmin = LOOKUP.findStatic(adminClass, "isResAdmin", MethodType.methodType(boolean.class, Player.class));
            MethodHandle flagOf = LOOKUP.findStatic(flagsClass, "valueOf", MethodType.methodType(flagsClass, String.class));
            for (String flag : new String[]{"pvp", "friendlyfire", "animalkilling", "mobkilling"}) flags.put(flag, flagOf.invoke(flag));
            onlyFalse = LOOKUP.findStatic(comboClass, "valueOf", MethodType.methodType(comboClass, String.class)).invoke("OnlyFalse");
        }

        private Object flag(String name) {
            Object flag = flags.get(name);
            if (flag == null) throw new IllegalArgumentException("Unbound Residence flag " + name);
            return flag;
        }

        private static Object call(MethodHandle handle, Object... args) {
            try {
                return handle.invokeWithArguments(args);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException("Residence call failed", e);
            }
        }

        // Managers are fetched per call: /resreload may replace them on the same plugin instance.
        @Override public boolean listenerDisabled(World world) {
            return (Boolean) call(isDisabledWorldListener, residence, world);
        }

        @Override public Object residenceAt(Location location) {
            return call(getByLoc, call(getResidenceManager, residence), location);
        }

        @Override public boolean flagFalse(Object claim, String flag) {
            return (Boolean) call(has, call(getPermissions, claim), flag(flag), onlyFalse);
        }

        @Override public boolean playerFlagFalse(Object claim, Player player, String flag) {
            return (Boolean) call(playerHasCombo, call(getPermissions, claim), player, flag(flag), onlyFalse);
        }

        @Override public boolean worldFlagFalse(World world, String flag) {
            return (Boolean) call(has, call(worldPerms, call(getWorldFlags, residence), world), flag(flag), onlyFalse);
        }

        @Override public boolean ignoresCheck(String flag, Entity entity) {
            return (Boolean) call(shouldIgnoreCheck, flag(flag), entity);
        }

        @Override public boolean playerHasAt(Location location, Player player, String flag) {
            return (Boolean) call(playerHasDefault, call(getPerms, location, player), player, flag(flag), true);
        }

        @Override public boolean isAnimal(Entity entity) { return (Boolean) call(isAnimal, entity); }

        @Override public boolean isResAdmin(Player player) { return (Boolean) call(isResAdmin, player); }
    }
}
