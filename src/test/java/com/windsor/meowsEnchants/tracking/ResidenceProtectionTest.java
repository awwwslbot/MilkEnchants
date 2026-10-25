package com.windsor.meowsEnchants.tracking;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.Test;

class ResidenceProtectionTest {
    private final World world = mock(World.class);
    private final Location wild = new Location(world, 0, 64, 0), home = new Location(world, 100, 64, 0), arena = new Location(world, 200, 64, 0);
    private final Player shooter = mock(Player.class), target = mock(Player.class);
    private final FakeResidence api = new FakeResidence();
    private final ResidenceProtection protection = new ResidenceProtection(() -> api);

    ResidenceProtectionTest() {
        api.claims.put(home, "home");
        api.claims.put(arena, "arena");
        api.falseFlags.add("home:pvp");
        api.falseFlags.add("home:animalkilling");
    }

    @Test void pvpIsDeniedWhenEitherEndIsInANoPvpClaim() {
        when(target.getLocation()).thenReturn(wild);
        assertFalse(protection.allowsPvp(shooter, home, target), "shooter inside a no-pvp claim");
        assertTrue(protection.allowsPvp(shooter, arena, target), "pvp claim to the wilderness");
        when(target.getLocation()).thenReturn(home);
        assertFalse(protection.allowsPvp(shooter, wild, target), "target inside a no-pvp claim");
        assertFalse(protection.allowsPvp(shooter, home, target), "both in the same no-pvp claim");
    }

    @Test void wildernessUsesTheWorldFlagOnlyWhenNeitherEndIsClaimed() {
        when(target.getLocation()).thenReturn(wild);
        assertTrue(protection.allowsPvp(shooter, wild, target));
        api.worldPvpFalse = true;
        assertFalse(protection.allowsPvp(shooter, wild, target));
        when(target.getLocation()).thenReturn(arena);
        assertTrue(protection.allowsPvp(shooter, wild, target), "a pvp claim at one end skips the world flag");
    }

    @Test void sameClaimFriendlyFireNeedsBothPlayersDenied() {
        when(target.getLocation()).thenReturn(arena);
        assertTrue(protection.allowsPvp(shooter, arena, target));
        api.playerFalse.add(target);
        assertTrue(protection.allowsPvp(shooter, arena, target));
        api.playerFalse.add(shooter);
        assertFalse(protection.allowsPvp(shooter, arena, target));
    }

    @Test void disabledResidenceWorldsAndAMissingPluginAllowEverything() {
        when(target.getLocation()).thenReturn(home);
        api.listenerDisabled = true;
        assertTrue(protection.allowsPvp(shooter, home, target));
        ResidenceProtection absent = new ResidenceProtection(() -> null);
        assertTrue(absent.allowsPvp(shooter, home, target));
        assertTrue(absent.allowsHarm(shooter, mob(Cow.class, home)));
    }

    @Test void animalsAndMonstersUseTheirOwnKillingFlags() {
        api.animals.add(Cow.class);
        api.animals.add(Villager.class);
        Cow cow = mob(Cow.class, home);
        assertFalse(protection.allowsHarm(shooter, cow), "animalkilling false in the claim");
        assertTrue(protection.allowsHarm(shooter, mob(Cow.class, wild)));
        assertTrue(protection.allowsHarm(shooter, mob(Zombie.class, home)), "mobkilling is allowed by default");
        api.falseFlags.add("home:mobkilling");
        assertFalse(protection.allowsHarm(shooter, mob(Zombie.class, home)));
        api.admins.add(shooter);
        assertTrue(protection.allowsHarm(shooter, cow), "residence admins bypass");
        api.admins.clear();
        api.ignored.add("animalkilling");
        assertTrue(protection.allowsHarm(shooter, cow), "flag disabled globally or world listener off");
    }

    /** Hoglins are both Enemy and Animals: Residence runs both killing checks on them. */
    @Test void hoglinsNeedBothMobAndAnimalKilling() {
        api.animals.add(Hoglin.class);
        Hoglin hoglin = mob(Hoglin.class, home);
        assertTrue(hoglin instanceof Enemy && hoglin instanceof org.bukkit.entity.Animals);
        assertFalse(protection.allowsHarm(shooter, hoglin), "default claim: animalkilling false, mobkilling allowed");
        api.falseFlags.remove("home:animalkilling");
        api.falseFlags.add("home:mobkilling");
        assertFalse(protection.allowsHarm(shooter, hoglin), "animalkilling allowed, mobkilling false");
        api.falseFlags.remove("home:mobkilling");
        assertTrue(protection.allowsHarm(shooter, hoglin), "both allowed");
        assertTrue(protection.allowsHarm(shooter, mob(Hoglin.class, wild)));
    }

    /** Target scans and the firework blast filter both go through TargetRules.trackable. */
    @Test void aHoglinInADefaultClaimIsNotTrackableSoTheBlastSparesItToo() {
        api.animals.add(Hoglin.class);
        Hoglin hoglin = mob(Hoglin.class, home);
        when(hoglin.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(shooter.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        TargetRules rules = new TargetRules(TrackingSettings.DEFAULTS, id -> false, protection, w -> true);
        Shooter.View view = new Shooter.View(shooter.getUniqueId(), shooter, wild);
        assertFalse(rules.trackable(hoglin, view));
        api.falseFlags.remove("home:animalkilling");
        assertTrue(rules.trackable(hoglin, view));
    }

    @Test void ignoredChecksAreJudgedPerFlag() {
        api.animals.add(Hoglin.class);
        Hoglin hoglin = mob(Hoglin.class, home); // animalkilling false in the claim
        api.ignored.add("mobkilling");
        assertFalse(protection.allowsHarm(shooter, hoglin), "ignoring mobkilling does not skip animalkilling");
        api.ignored.clear();
        api.ignored.add("animalkilling");
        assertTrue(protection.allowsHarm(shooter, hoglin));
        api.falseFlags.add("home:mobkilling");
        assertFalse(protection.allowsHarm(shooter, hoglin), "ignoring animalkilling does not skip mobkilling");
    }

    @Test void anUnrecognisedResidenceIsIgnoredWithOneWarning() {
        Plugin fake = mock(Plugin.class); // Its class loader has no Residence classes.
        when(fake.isEnabled()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        int[] warnings = {0};
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { warnings[0]++; }
            @Override public void flush() {}
            @Override public void close() {}
        });
        ResidenceProtection bound = ResidenceProtection.bind(() -> fake, logger);
        when(target.getLocation()).thenReturn(home);
        assertTrue(bound.allowsPvp(shooter, home, target));
        assertTrue(bound.allowsHarm(shooter, mob(Cow.class, home)));
        assertEquals(1, warnings[0]);
        when(fake.isEnabled()).thenReturn(false);
        assertTrue(bound.allowsPvp(shooter, home, target));
    }

    private <T extends Mob> T mob(Class<T> type, Location at) {
        T mob = mock(type);
        when(mob.getLocation()).thenReturn(at);
        return mob;
    }

    /** Claims keyed by location; a flag is false when "claim:flag" is listed. */
    private static final class FakeResidence implements ResidenceProtection.Api {
        final Map<Location, String> claims = new HashMap<>();
        final Set<String> falseFlags = new HashSet<>();
        final Set<Player> playerFalse = new HashSet<>();
        final Set<Player> admins = new HashSet<>();
        final Set<Class<?>> animals = new HashSet<>();
        final Set<String> ignored = new HashSet<>();
        boolean worldPvpFalse, listenerDisabled;

        @Override public boolean listenerDisabled(World world) { return listenerDisabled; }
        @Override public Object residenceAt(Location location) { return claims.get(location); }
        @Override public boolean flagFalse(Object claim, String flag) { return falseFlags.contains(claim + ":" + flag); }
        @Override public boolean playerFlagFalse(Object claim, Player player, String flag) { return playerFalse.contains(player); }
        @Override public boolean worldFlagFalse(World world, String flag) { return worldPvpFalse; }
        @Override public boolean ignoresCheck(String flag, Entity entity) { return ignored.contains(flag); }
        @Override public boolean playerHasAt(Location location, Player player, String flag) {
            String claim = claims.get(location);
            return claim == null || !falseFlags.contains(claim + ":" + flag);
        }
        @Override public boolean isAnimal(Entity entity) { return animals.stream().anyMatch(type -> type.isInstance(entity)); }
        @Override public boolean isResAdmin(Player player) { return admins.contains(player); }
    }
}
