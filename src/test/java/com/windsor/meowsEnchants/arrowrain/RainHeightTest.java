package com.windsor.meowsEnchants.arrowrain;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.function.IntPredicate;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/** Spawn height of one rain arrow from its column's blocks (impl-list items 1–6, test list item 11). */
class RainHeightTest {
    static final int MIN_Y = -64;
    static final int MAX_Y = 320;
    static final double HEIGHT = BundledRain.RAIN.height();
    static final double MIN_HEIGHT = BundledRain.RAIN.minHeight();
    /** A cave floor: the block below y 64 is solid; impacts on it are at hY = 64. */
    static final double FLOOR = 64.0;

    /** Blocks at these y stop arrows; everything else is air. */
    static RainHeight.Column solid(Set<Integer> ys) { return ys::contains; }

    static Set<Integer> range(int fromInclusive, int toInclusive) {
        return IntStream.rangeClosed(fromInclusive, toInclusive).boxed().collect(Collectors.toSet());
    }

    /** Ground up to and including y 63 plus the given blocks. */
    static RainHeight.Column ground(Set<Integer> above) {
        return y -> y <= 63 || above.contains(y);
    }

    static RainHeight.Column groundAnd(int... ys) {
        return ground(IntStream.of(ys).boxed().collect(Collectors.toSet()));
    }

    /** Ground, and solid from {@code ceiling} upward (a cave ceiling). */
    static RainHeight.Column caveWithCeilingAt(int ceiling) {
        return y -> y <= 63 || y >= ceiling;
    }

    static OptionalDouble spawn(double hY, double centreCeiling, RainHeight.Column column) {
        return RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, centreCeiling, MIN_Y, MAX_Y, column);
    }

    /** A column whose impact-height block is air: the centre ceiling does not matter there. */
    static OptionalDouble spawn(double hY, RainHeight.Column column) {
        return spawn(hY, hY, column);
    }

    static double centre(double hY, RainHeight.Column column) {
        return RainHeight.centreCeiling(hY, hY, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, column);
    }

    static void assertSpawn(double expected, OptionalDouble actual) {
        assertTrue(actual.isPresent(), "expected a spawn at " + expected);
        assertEquals(expected, actual.getAsDouble());
    }

    @Test void bundledDefaultsAreTwentyAndFour() {
        assertEquals(20.0, HEIGHT);
        assertEquals(4.0, MIN_HEIGHT);
    }

    @Test void openSkySpawnsExactlyTwentyAboveTheImpactAsBefore() {
        RainHeight.Column open = groundAnd();
        assertSpawn(84.0, spawn(FLOOR, open));
        assertEquals(84.5, centre(FLOOR, open));
        // A fractional impact (an entity hit) keeps the exact old height too.
        assertSpawn(65.3 + 20, spawn(65.3, open));
    }

    @Test void exactlyFourBlocksOfHeadroomStillRainsJustUnderTheCeiling() {
        assertSpawn(67.5, spawn(FLOOR, caveWithCeilingAt(68)));
        assertSpawn(68.5, spawn(64.5, caveWithCeilingAt(69)));
    }

    @Test void lessThanFourBlocksOfHeadroomSkipsTheArrow() {
        assertTrue(spawn(FLOOR, caveWithCeilingAt(67)).isEmpty());
        assertTrue(spawn(64.2, caveWithCeilingAt(68)).isEmpty()); // 3.8 blocks.
        assertTrue(spawn(FLOOR, caveWithCeilingAt(65)).isEmpty());
    }

    @Test void twentyAndAHalfBlocksIsTheBoundaryOfTheFullHeight() {
        // Ceiling 20.5 above the impact: the full 20 blocks, the arrow's top touching the ceiling.
        assertSpawn(84.5, spawn(64.5, caveWithCeilingAt(85)));
        // 20 blocks: lowered by half a block to keep the arrow under the ceiling.
        assertSpawn(83.5, spawn(FLOOR, caveWithCeilingAt(84)));
        // Ceiling 21 above: the scan stops at 20.5, the full height.
        assertSpawn(84.0, spawn(FLOOR, caveWithCeilingAt(85)));
        // 19.5 blocks.
        assertSpawn(83.5, spawn(64.5, caveWithCeilingAt(84)));
    }

    @Test void stalactitesAndHollowsGiveEachColumnItsOwnHeight() {
        // A cave 6 high (ceiling y 70), one stalactite hanging to y 67, one to y 68, and a hollow up to y 76.
        RainHeight.Column cave = caveWithCeilingAt(70);
        RainHeight.Column longStalactite = caveWithCeilingAt(67);
        RainHeight.Column stalactite = caveWithCeilingAt(68);
        RainHeight.Column hollow = caveWithCeilingAt(76);
        double ceiling = centre(FLOOR, cave);
        assertEquals(70.0, ceiling);
        assertSpawn(69.5, spawn(FLOOR, ceiling, cave));
        assertTrue(spawn(FLOOR, ceiling, longStalactite).isEmpty());
        assertSpawn(67.5, spawn(FLOOR, ceiling, stalactite));
        assertSpawn(75.5, spawn(FLOOR, ceiling, hollow));
    }

    @Test void aFiveHighCaveHitOnAZombieBodyHasNoRainButItsFloorDoes() {
        // Headroom is measured from the impact point (Q3=A): 69 - 65.3 = 3.7 blocks on a zombie's body.
        RainHeight.Column cave = caveWithCeilingAt(69);
        assertTrue(spawn(65.3, centre(65.3, cave), cave).isEmpty());
        assertSpawn(68.5, spawn(FLOOR, centre(FLOOR, cave), cave));
        // A 6-high cave keeps rain on the body: 70 - 65.3 = 4.7.
        assertSpawn(69.5, spawn(65.3, centre(65.3, caveWithCeilingAt(70)), caveWithCeilingAt(70)));
    }

    @Test void slopesClimbOutOfTheSolidPartUnderAnOpenSky() {
        double open = centre(FLOOR, groundAnd());
        // Ground rises to y 69 in this column: the arrow spawns in the air above it, at the usual height.
        assertSpawn(84.0, spawn(FLOOR, open, ground(range(64, 69))));
        // Ground up to y 83: air from 84, half a block below the 20.5 limit is still enough.
        assertSpawn(84.0, spawn(FLOOR, open, ground(range(64, 83))));
        // Ground up to y 84: no air below the limit.
        assertTrue(spawn(FLOOR, open, ground(range(64, 84))).isEmpty());
        // Ground up to y 66 under a roof at y 75 in that column.
        assertSpawn(74.5, spawn(FLOOR, open, y -> y <= 66 || y >= 75));
    }

    @Test void wallColumnsAreCappedByTheImpactsOwnCeiling() {
        double ceiling = centre(FLOOR, caveWithCeilingAt(70));
        // Wall up to y 71 with a separate cavity (y 72–75) inside the rock above: never into the cavity.
        assertTrue(spawn(FLOOR, ceiling, y -> y <= 71 || y >= 76).isEmpty());
        // The wall's own air reaches y 80, but it is capped at the impact's ceiling (y 70).
        assertSpawn(69.5, spawn(FLOOR, ceiling, y -> y <= 65 || y >= 80));
        // A ledge up to y 67: still 6 blocks from the impact to the cap.
        assertSpawn(69.5, spawn(FLOOR, ceiling, y -> y <= 67 || y >= 90));
        // A ledge up to y 69: its air starts at the cap.
        assertTrue(spawn(FLOOR, ceiling, y -> y <= 69 || y >= 90).isEmpty());
        // Not enough room between the ledge top and the cap for the arrow (0.3 blocks).
        assertTrue(spawn(FLOOR, 69.3, y -> y <= 68 || y >= 90).isEmpty());
        // The "4 blocks" are still measured from the impact: cap 3.5 above it.
        assertTrue(spawn(FLOOR, 67.5, y -> y <= 65 || y >= 90).isEmpty());
    }

    @Test void hittingACaveCeilingHasNoHeadroomAndNoRain() {
        // The arrow flew up into the ceiling block at y 70; impact at its lower face.
        RainHeight.Column cave = caveWithCeilingAt(70);
        double probeBelow = 70.0 - RainHeight.PROBE_BACKOFF;
        double ceiling = RainHeight.centreCeiling(70.0, probeBelow, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, cave);
        assertEquals(70.0, ceiling);
        // Without the back-off the probe is the ceiling block itself: also no headroom.
        assertEquals(70.0, centre(70.0, cave));
        assertTrue(spawn(70.0, ceiling, cave).isEmpty());
        assertTrue(spawn(70.0, ceiling, y -> y <= 63 || y >= 70 && y != 71).isEmpty()); // A pocket inside the rock.
        // A shallow dent in the ceiling (y 70–71 open) is less than 4 blocks above the impact.
        assertTrue(spawn(70.0, ceiling, y -> y <= 63 || y >= 72).isEmpty());
    }

    @Test void aDeepHollowAboveAHitCaveCeilingStillRainsInThatColumn() {
        // Decided (round 3, Q13=A): every column on its own. The impact is the ceiling's lower face (no headroom
        // there), but a column whose impact-height block is the open bottom of a hollow is handled like any air
        // column: its arrows spawn in the hollow and fall into the cave.
        double ceiling = RainHeight.centreCeiling(70.0, 70.0 - RainHeight.PROBE_BACKOFF, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y,
                caveWithCeilingAt(70));
        assertEquals(70.0, ceiling);
        // That column: the cave's air y 64–69 continues into a hollow in the ceiling rock.
        assertSpawn(75.5, spawn(70.0, ceiling, y -> y <= 63 || y >= 76)); // Hollow y 70–75: 6 deep.
        assertSpawn(73.5, spawn(70.0, ceiling, y -> y <= 63 || y >= 74)); // Hollow y 70–73: exactly 4 deep.
        assertTrue(spawn(70.0, ceiling, y -> y <= 63 || y >= 73).isEmpty()); // 3 deep: not enough.
    }

    @Test void aSealedCavityInACaveWallBelowTheCeilingRainsLikeALedge() {
        // Decided (round 3, Q12=A), a known limitation: only this column's blocks are read, so rock sealing a
        // cavity in the cave wall cannot be told apart from a ledge under an overhang with the same column.
        double ceiling = centre(FLOOR, caveWithCeilingAt(70));
        // Wall rock y 64–65, sealed cavity y 66–68, rock from y 69 up through the cave ceiling level.
        RainHeight.Column sealedCavity = y -> y <= 65 || y >= 69;
        assertSpawn(68.5, spawn(FLOOR, ceiling, sealedCavity));
        // A cavity reaching above the cave ceiling level is capped at it, exactly like the ledge column with the
        // same solid part (rock y 64–65, then air) in wallColumnsAreCappedByTheImpactsOwnCeiling.
        RainHeight.Column tallCavity = y -> y <= 65 || y >= 73;
        RainHeight.Column ledge = y -> y <= 65 || y >= 80;
        assertSpawn(69.5, spawn(FLOOR, ceiling, tallCavity));
        assertEquals(spawn(FLOOR, ceiling, ledge), spawn(FLOOR, ceiling, tallCavity));
    }

    @Test void aWallHitReadsTheAirInFrontOfTheWall() {
        // The wall block itself (probe without back-off) has no headroom; the air in front has a 6-high cave.
        RainHeight.Column wall = y -> true;
        assertEquals(FLOOR + 0.5, RainHeight.centreCeiling(FLOOR + 0.5, FLOOR + 0.5, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, wall));
        assertEquals(70.0, RainHeight.centreCeiling(FLOOR + 0.5, FLOOR + 0.5, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y,
                caveWithCeilingAt(70)));
    }

    @Test void theBuildLimitIsACeiling() {
        RainHeight.Column open = y -> y < 290;
        assertSpawn(319.5, spawn(310.0, open));
        assertSpawn(319.5, spawn(316.0, open)); // Exactly 4 blocks below the limit.
        assertTrue(spawn(316.5, open).isEmpty());
        assertSpawn(319.5, spawn(299.5, open)); // 20.5 below: the full height, exactly.
        assertEquals(320.0, centre(310.0, open));
        // Impacts outside the world have no rain at all.
        assertTrue(spawn(320.0, open).isEmpty());
        assertTrue(spawn(400.0, open).isEmpty());
        assertTrue(spawn(-64.5, y -> false).isEmpty());
        assertSpawn(-44.0, spawn(-64.0, y -> false));
        assertEquals(64.0 + RainHeight.PROBE_BACKOFF, RainHeight.centreCeiling(64.0 + RainHeight.PROBE_BACKOFF,
                320.0, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, y -> false), "a probe above the limit has no headroom");
    }

    /** Arrow collision as Block.isPassable() reports it (verified on the Folia 26.1.2 test server). */
    static final Map<String, Boolean> STOPS_ARROWS = Map.ofEntries(
            Map.entry("AIR", false), Map.entry("WATER", false), Map.entry("LAVA", false),
            Map.entry("SHORT_GRASS", false), Map.entry("TORCH", false), Map.entry("VINE", false),
            Map.entry("COBWEB", false), Map.entry("OAK_SIGN", false), Map.entry("STONE_BUTTON", false),
            Map.entry("OAK_LEAVES", true), Map.entry("GLASS", true), Map.entry("OAK_FENCE", true),
            Map.entry("BARRIER", true), Map.entry("STONE_SLAB", true), Map.entry("STONE", true));

    static RainHeight.Column materials(Map<Integer, String> blocks) {
        TreeMap<Integer, String> column = new TreeMap<>(blocks);
        return y -> STOPS_ARROWS.get(column.getOrDefault(y, y <= 63 ? "STONE" : "AIR"));
    }

    @Test void fluidsPlantsAndTorchesDoNotStopTheRain() {
        Map<Integer, String> underwater = new TreeMap<>();
        for (int y = 64; y <= 90; y++) underwater.put(y, "WATER");
        assertSpawn(84.0, spawn(FLOOR, materials(underwater)));
        assertSpawn(84.0, spawn(FLOOR, materials(Map.of(64, "LAVA", 65, "LAVA", 70, "LAVA"))));
        assertSpawn(84.0, spawn(FLOOR, materials(Map.of(64, "SHORT_GRASS", 66, "TORCH", 70, "VINE",
                72, "COBWEB", 74, "OAK_SIGN", 76, "STONE_BUTTON"))));
    }

    @Test void leavesGlassFencesBarriersAndPartialBlocksAreWholeCeilings() {
        assertSpawn(71.5, spawn(FLOOR, materials(Map.of(72, "OAK_LEAVES"))));     // Under a tree.
        assertSpawn(69.5, spawn(FLOOR, materials(Map.of(70, "GLASS"))));          // A glass roof.
        assertSpawn(67.5, spawn(FLOOR, materials(Map.of(68, "OAK_FENCE"))));      // Fence height is ignored.
        assertSpawn(68.5, spawn(FLOOR, materials(Map.of(69, "STONE_SLAB"))));     // Bottom slab: its block counts.
        assertTrue(spawn(FLOOR, materials(Map.of(67, "BARRIER"))).isEmpty());
    }

    @Test void aMinimumAboveTheHeightIsAppliedAsWritten() {
        double height = 20.0;
        double minimum = 25.0;
        RainHeight.Column open = groundAnd();
        double ceiling = RainHeight.centreCeiling(FLOOR, FLOOR, height, minimum, MIN_Y, MAX_Y, open);
        assertEquals(89.0, ceiling, "headroom is looked for up to the minimum");
        // 25 blocks of headroom are needed, the spawn height stays at most 20.
        assertSpawn(84.0, RainHeight.spawnY(FLOOR, height, minimum, ceiling, MIN_Y, MAX_Y, open));
        assertSpawn(84.0, RainHeight.spawnY(FLOOR, height, minimum, ceiling, MIN_Y, MAX_Y, caveWithCeilingAt(89)));
        assertTrue(RainHeight.spawnY(FLOOR, height, minimum, ceiling, MIN_Y, MAX_Y, caveWithCeilingAt(88)).isEmpty());
        assertTrue(RainHeight.spawnY(FLOOR, height, minimum, ceiling, MIN_Y, MAX_Y, caveWithCeilingAt(86)).isEmpty());
        // A slope column whose air starts above the highest spawn height has nowhere to put the arrow.
        assertSpawn(84.0, RainHeight.spawnY(FLOOR, height, minimum, ceiling, MIN_Y, MAX_Y, ground(range(64, 83))));
        assertTrue(RainHeight.spawnY(FLOOR, height, minimum, ceiling, MIN_Y, MAX_Y, ground(range(64, 84))).isEmpty());
    }

    @Test void aZeroMinimumOnlyNeedsRoomForTheArrow() {
        assertSpawn(64.5, RainHeight.spawnY(FLOOR, 20.0, 0.0, FLOOR, MIN_Y, MAX_Y, caveWithCeilingAt(65)));
        // 0.8 blocks above an entity hit: the arrow sits in the impact's own block, under the ceiling.
        assertSpawn(64.5, RainHeight.spawnY(64.2, 20.0, 0.0, 64.2, MIN_Y, MAX_Y, caveWithCeilingAt(65)));
    }

    // Collision boxes (block-relative) as Block.getCollisionShape() reports them on the Folia 26.1.2 test server.
    static RainHeight.Box box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return new RainHeight.Box(minX, minY, minZ, maxX, maxY, maxZ);
    }

    static final List<RainHeight.Box> FULL = List.of(box(0, 0, 0, 1, 1, 1));
    static final List<RainHeight.Box> BOTTOM_SLAB = List.of(box(0, 0, 0, 1, 0.5, 1));
    static final List<RainHeight.Box> TOP_SLAB = List.of(box(0, 0.5, 0, 1, 1, 1));
    static final List<RainHeight.Box> DIRT_PATH = List.of(box(0, 0, 0, 1, 15 / 16.0, 1));
    static final List<RainHeight.Box> CARPET = List.of(box(0, 0, 0, 1, 1 / 16.0, 1));
    static final List<RainHeight.Box> SNOW_4 = List.of(box(0, 0, 0, 1, 6 / 16.0, 1)); // Collision: layers − 1.
    /** Bottom stairs facing north: full bottom half, top half over z 0–0.5. */
    static final List<RainHeight.Box> STAIRS = List.of(box(0, 0, 0, 1, 0.5, 1), box(0, 0.5, 0, 1, 1, 0.5));
    static final List<RainHeight.Box> UPPER_TRAPDOOR = List.of(box(0, 13 / 16.0, 0, 1, 1, 1));
    static final List<RainHeight.Box> FENCE_POST = List.of(box(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1.5, 10 / 16.0));
    static final List<RainHeight.Box> GLASS_PANE = List.of(box(7 / 16.0, 0, 7 / 16.0, 9 / 16.0, 1, 9 / 16.0));

    /**
     * The column through the point (fx, fz) inside its blocks: the blocks in {@code cells} by y (an empty list is
     * passable), full blocks where {@code solid} says so elsewhere.
     */
    static RainHeight.Column shaped(double fx, double fz, Map<Integer, List<RainHeight.Box>> cells, IntPredicate solid) {
        return new RainHeight.Column() {
            @Override public boolean blocks(int y) { return cells.containsKey(y) ? !cells.get(y).isEmpty() : solid.test(y); }
            @Override public boolean blocksPoint(int y, double fy) {
                return RainHeight.stops(cells.getOrDefault(y, FULL), fx, fy, fz);
            }
        };
    }

    static final IntPredicate GROUND = y -> y <= 63;
    static final IntPredicate GROUND_AND_CAVE_CEILING_70 = y -> y <= 63 || y >= 70;

    @Test void partialFloorsRainLikeOpenGround() {
        Map<String, List<RainHeight.Box>> floors = Map.of("bottom slab", BOTTOM_SLAB, "dirt path", DIRT_PATH,
                "carpet", CARPET, "4 snow layers", SNOW_4);
        for (Map.Entry<String, List<RainHeight.Box>> floor : floors.entrySet()) {
            double hY = 64 + floor.getValue().getFirst().maxY(); // An arrow falling onto the block's top.
            for (IntPredicate around : List.of(GROUND, GROUND_AND_CAVE_CEILING_70)) {
                RainHeight.Column column = shaped(0.3, 0.7, Map.of(64, floor.getValue()), around);
                boolean cave = around == GROUND_AND_CAVE_CEILING_70;
                // The probe moved back up along the fall stays in the floor block; so does an unmoved probe.
                for (double probeY : new double[]{hY + RainHeight.PROBE_BACKOFF, hY}) {
                    double ceiling = RainHeight.centreCeiling(hY, probeY, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, column);
                    assertEquals(cave ? 70.0 : hY + 20.5, ceiling, floor.getKey());
                    OptionalDouble y = RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, ceiling, MIN_Y, MAX_Y, column);
                    assertTrue(y.isPresent(), floor.getKey());
                    assertEquals(cave ? 69.5 : hY + 20, y.getAsDouble(), floor.getKey());
                }
            }
        }
    }

    @Test void theLowStepOfStairsIsGround() {
        RainHeight.Column lowStep = shaped(0.5, 0.75, Map.of(64, STAIRS), GROUND);
        double ceiling = RainHeight.centreCeiling(64.5, 64.5 + RainHeight.PROBE_BACKOFF, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, lowStep);
        assertEquals(85.0, ceiling);
        assertSpawn(84.5, RainHeight.spawnY(64.5, HEIGHT, MIN_HEIGHT, ceiling, MIN_Y, MAX_Y, lowStep));
        // A column whose point is under the high step: the stairs block there, it climbs over them as before.
        assertSpawn(84.5, RainHeight.spawnY(64.5, HEIGHT, MIN_HEIGHT, ceiling, MIN_Y, MAX_Y,
                shaped(0.5, 0.25, Map.of(64, STAIRS), GROUND)));
    }

    @Test void aTopSlabOrUpperTrapdoorHitFromBelowLeavesNoHeadroom() {
        // A 6-high cave whose ceiling block at y 70 is a top slab / an upper trapdoor; the arrow flew up into it.
        for (List<RainHeight.Box> roof : List.of(TOP_SLAB, UPPER_TRAPDOOR)) {
            double hY = 70 + roof.getFirst().minY();
            RainHeight.Column column = shaped(0.3, 0.7, Map.of(70, roof), GROUND_AND_CAVE_CEILING_70);
            double ceiling = RainHeight.centreCeiling(hY, hY - RainHeight.PROBE_BACKOFF, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, column);
            assertEquals(hY, ceiling);
            assertTrue(RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, ceiling, MIN_Y, MAX_Y, column).isEmpty());
            // A flat roof of these: every column alike, no rain at all.
            assertTrue(RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, ceiling, MIN_Y, MAX_Y,
                    shaped(0.9, 0.1, Map.of(70, roof), GROUND_AND_CAVE_CEILING_70)).isEmpty());
        }
    }

    @Test void anEntityStandingOnASlabHitInTheLegsRainsAsUsual() {
        double hY = 64.7; // In the slab's block, above its 0.5 top.
        for (IntPredicate around : List.of(GROUND, GROUND_AND_CAVE_CEILING_70)) {
            RainHeight.Column column = shaped(0.5, 0.5, Map.of(64, BOTTOM_SLAB), around);
            double ceiling = RainHeight.centreCeiling(hY, hY, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, column);
            boolean cave = around == GROUND_AND_CAVE_CEILING_70;
            assertEquals(cave ? 70.0 : hY + 20.5, ceiling);
            assertSpawn(cave ? 69.5 : hY + 20, RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, ceiling, MIN_Y, MAX_Y, column));
        }
    }

    @Test void aSideHitOnAFencePostOrGlassPaneRainsAsUsual() {
        for (List<RainHeight.Box> thin : List.of(FENCE_POST, GLASS_PANE)) {
            double face = thin.getFirst().minX(); // The arrow flies +x into the west face.
            double hY = 64.6;
            Map<Integer, List<RainHeight.Box>> cells = Map.of(64, thin);
            double ceiling = RainHeight.centreCeiling(hY, hY, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y,
                    shaped(face - RainHeight.PROBE_BACKOFF, 0.5, cells, GROUND));
            assertEquals(hY + 20.5, ceiling, "the point backed off the face is beside the thin block");
            // Not backed off, the point is on the face: the face counts as covered, no headroom.
            assertEquals(hY, RainHeight.centreCeiling(hY, hY, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, shaped(face, 0.5, cells, GROUND)));
            // Columns beside the thin block are open ground; a column through it climbs over it (air above).
            assertSpawn(hY + 20, RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, ceiling, MIN_Y, MAX_Y, shaped(0.1, 0.5, cells, GROUND)));
            assertSpawn(hY + 20, RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, ceiling, MIN_Y, MAX_Y, shaped(0.5, 0.5, cells, GROUND)));
        }
    }

    @Test void aDiscOverMixedSlabAndFullBlockGround() {
        double hY = 64.5; // Hit on a slab; part of the disc is full blocks one half block higher.
        RainHeight.Column slab = shaped(0.2, 0.2, Map.of(64, BOTTOM_SLAB), GROUND);
        RainHeight.Column full = shaped(0.2, 0.2, Map.of(64, FULL), GROUND);
        double open = RainHeight.centreCeiling(hY, hY + RainHeight.PROBE_BACKOFF, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, slab);
        assertSpawn(84.5, RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, open, MIN_Y, MAX_Y, slab));
        assertSpawn(84.5, RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, open, MIN_Y, MAX_Y, full)); // Climbs to y 65.
        RainHeight.Column caveSlab = shaped(0.2, 0.2, Map.of(64, BOTTOM_SLAB), GROUND_AND_CAVE_CEILING_70);
        RainHeight.Column caveFull = shaped(0.2, 0.2, Map.of(64, FULL), GROUND_AND_CAVE_CEILING_70);
        double cave = RainHeight.centreCeiling(hY, hY + RainHeight.PROBE_BACKOFF, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, caveSlab);
        assertEquals(70.0, cave);
        assertSpawn(69.5, RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, cave, MIN_Y, MAX_Y, caveSlab));
        assertSpawn(69.5, RainHeight.spawnY(hY, HEIGHT, MIN_HEIGHT, cave, MIN_Y, MAX_Y, caveFull));
        // Hit on the full blocks (hY 65): the slab columns' block at y 65 is air, plain open ground.
        assertSpawn(85.0, RainHeight.spawnY(65.0, HEIGHT, MIN_HEIGHT, 85.5, MIN_Y, MAX_Y, slab));
    }

    @Test void aTinyMinimumNeverStartsAnArrowInsideThePartialBlockItStandsOn() {
        // min_height 0 under a ceiling 0.0625 above a dirt path: the arrow would start below the path's top.
        RainHeight.Column column = shaped(0.5, 0.5, Map.of(64, DIRT_PATH), y -> y <= 63 || y >= 65);
        assertTrue(RainHeight.spawnY(64.9375, 20.0, 0.0, 65.0, MIN_Y, MAX_Y, column).isEmpty());
        // On a carpet the same ceiling leaves room: the arrow spawns above the carpet, at y 64.5.
        assertSpawn(64.5, RainHeight.spawnY(64.0625, 20.0, 0.0, 65.0, MIN_Y, MAX_Y,
                shaped(0.5, 0.5, Map.of(64, CARPET), y -> y <= 63 || y >= 65)));
    }

    @Test void stopsLooksOnlyAtBoxesOverThePoint() {
        assertFalse(RainHeight.stops(BOTTOM_SLAB, 0.5, 0.5, 0.5));  // On top of it.
        assertTrue(RainHeight.stops(BOTTOM_SLAB, 0.5, 0.49, 0.5));  // Inside it.
        assertTrue(RainHeight.stops(TOP_SLAB, 0.5, 0.2, 0.5));      // Under it.
        assertFalse(RainHeight.stops(FENCE_POST, 0.2, 0.5, 0.5));   // Beside the post.
        assertTrue(RainHeight.stops(FENCE_POST, 0.5, 0.99, 0.5));   // In the post.
        assertFalse(RainHeight.stops(STAIRS, 0.5, 0.5, 0.75));      // Low step.
        assertTrue(RainHeight.stops(STAIRS, 0.5, 0.5, 0.25));       // Under the high step.
        assertFalse(RainHeight.stops(List.of(), 0.5, 0.0, 0.5));
    }

    @Test void anImpactJustBelowTheGroundFaceStillRainsLikeTheFaceItself() {
        // Floating point can put a ground hit a hair below the face: its block is then the ground itself.
        double hY = Math.nextDown(FLOOR);
        RainHeight.Column open = groundAnd();
        double ceiling = RainHeight.centreCeiling(hY, hY + RainHeight.PROBE_BACKOFF, HEIGHT, MIN_HEIGHT, MIN_Y, MAX_Y, open);
        assertSpawn(hY + 20, spawn(hY, ceiling, open));
        assertSpawn(69.5, spawn(hY, RainHeight.centreCeiling(hY, hY + RainHeight.PROBE_BACKOFF, HEIGHT, MIN_HEIGHT,
                MIN_Y, MAX_Y, caveWithCeilingAt(70)), caveWithCeilingAt(70)));
    }

    @Test void onlyBlocksBetweenTheImpactAndTheLimitAreRead() {
        java.util.List<Integer> read = new java.util.ArrayList<>();
        RainHeight.spawnY(FLOOR, HEIGHT, MIN_HEIGHT, 84.5, MIN_Y, MAX_Y, y -> { read.add(y); return y <= 63; });
        assertEquals(IntStream.rangeClosed(64, 84).boxed().toList(), read);
    }
}
