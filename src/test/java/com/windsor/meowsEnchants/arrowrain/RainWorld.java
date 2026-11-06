package com.windsor.meowsEnchants.arrowrain;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.bukkit.util.VoxelShape;

/** Block layouts for listener tests: which blocks of a mocked world stop arrows, and their collision boxes. */
final class RainWorld {
    /** True when block (x, y, z) stops arrows (Block.isPassable() is false); such blocks are full cubes. */
    @FunctionalInterface
    interface Blocks {
        boolean blocks(int x, int y, int z);
    }

    /** Collision boxes (block-relative) of block (x, y, z); an empty list is a passable block. */
    @FunctionalInterface
    interface Shapes {
        List<BoundingBox> at(int x, int y, int z);
    }

    static final List<BoundingBox> AIR = List.of();
    static final List<BoundingBox> FULL = List.of(new BoundingBox(0, 0, 0, 1, 1, 1));
    static final List<BoundingBox> BOTTOM_SLAB = List.of(new BoundingBox(0, 0, 0, 1, 0.5, 1));
    static final List<BoundingBox> TOP_SLAB = List.of(new BoundingBox(0, 0.5, 0, 1, 1, 1));

    private RainWorld() {}

    /** Every block is passable: open sky, the rain spawns at the full height. */
    static void openSky(World world) {
        blocks(world, (x, y, z) -> false);
    }

    static void blocks(World world, Blocks layout) {
        shapes(world, (x, y, z) -> layout.blocks(x, y, z) ? FULL : AIR);
    }

    static void shapes(World world, Shapes layout) {
        // Mocks are made here, not inside the answer: stubbing a new mock while answering another call is fragile.
        Map<List<BoundingBox>, Block> blocks = new ConcurrentHashMap<>();
        for (List<BoundingBox> boxes : List.of(AIR, FULL, BOTTOM_SLAB, TOP_SLAB)) blocks.put(boxes, block(boxes));
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> blocks.computeIfAbsent(layout.at(
                invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)), RainWorld::block));
    }

    /** A source arrow that landed flying straight down, as a plain ground hit does. */
    static void fallingOnto(AbstractArrow source) {
        when(source.getVelocity()).thenReturn(new Vector(0, -3, 0));
    }

    private static Block block(List<BoundingBox> boxes) {
        Block block = mock(Block.class);
        when(block.isPassable()).thenReturn(boxes.isEmpty());
        VoxelShape shape = mock(VoxelShape.class);
        when(shape.getBoundingBoxes()).thenReturn(boxes);
        when(block.getCollisionShape()).thenReturn(shape);
        return block;
    }
}
