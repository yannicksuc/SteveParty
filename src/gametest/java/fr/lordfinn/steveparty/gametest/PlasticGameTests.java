package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PlasticBlock;
import fr.lordfinn.steveparty.blocks.custom.PlotBlock;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChainBlock;
import net.minecraft.fluid.Fluids;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

public class PlasticGameTests implements FabricGameTest {
    private static final int X = 3, Z = 3, BOTTOM = 1, TOP = 5;
    /** Most ticks a bubble column takes to move a piece one block (the slowest is 0.6 blocks per tick, down). */
    private static final int COLUMN_TICKS = 2;

    private static Block plastic() {
        return ModBlocks.PLASTIC_BLOCKS[0];
    }

    /** A glass tube filled with still water from BOTTOM to TOP. */
    private static void waterColumn(TestContext context) {
        for (int y = BOTTOM; y <= TOP + 1; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    context.setBlockState(new BlockPos(X + dx, y, Z + dz), Blocks.GLASS);
                }
            }
        }
        for (int y = BOTTOM; y <= TOP; y++) context.setBlockState(new BlockPos(X, y, Z), Blocks.WATER);
        context.setBlockState(new BlockPos(X, TOP + 1, Z), Blocks.AIR);
    }

    private static int risingTicks(int blocks) {
        return blocks * PlasticBlock.RISE_DELAY + 10;
    }

    /** It rises to the surface one block at a time; the water it leaves behind stays water (none created or lost). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plasticRisesToTheSurface(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), plastic());
        context.waitAndRun(risingTicks(TOP - BOTTOM), () -> {
            context.expectBlock(plastic(), new BlockPos(X, TOP, Z));
            for (int y = BOTTOM; y < TOP; y++) {
                BlockPos pos = new BlockPos(X, y, Z);
                context.expectBlock(Blocks.WATER, pos);
                context.assertTrue(context.getBlockState(pos).getFluidState().isStill(), "still water left at y=" + y);
            }
            context.expectBlock(Blocks.AIR, new BlockPos(X, TOP + 1, Z));
            context.complete();
        });
    }

    /** A vertical chain below holds it down; breaking the chain lets it rise. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aChainKeepsItDown(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.Y));
        context.setBlockState(new BlockPos(X, BOTTOM + 1, Z), plastic());
        context.waitAndRun(risingTicks(TOP - BOTTOM), () -> {
            context.expectBlock(plastic(), new BlockPos(X, BOTTOM + 1, Z));
            context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.WATER);
            context.waitAndRun(risingTicks(TOP - BOTTOM), () -> {
                context.expectBlock(plastic(), new BlockPos(X, TOP, Z));
                context.complete();
            });
        });
    }

    /** It never replaces a solid block: it stops right under it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itStopsUnderASolidBlock(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM + 3, Z), Blocks.STONE);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), plastic());
        context.waitAndRun(risingTicks(TOP - BOTTOM), () -> {
            context.expectBlock(plastic(), new BlockPos(X, BOTTOM + 2, Z));
            context.expectBlock(Blocks.STONE, new BlockPos(X, BOTTOM + 3, Z));
            context.complete();
        });
    }

    /** Studs hold water like other waterloggable blocks. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void studsAreWaterloggable(TestContext context) {
        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.PLASTIC_STUDS[0].getDefaultState().with(PlotBlock.WATERLOGGED, true));
        context.assertTrue(context.getBlockState(pos).getFluidState().isOf(Fluids.WATER), "stud holds water");
        context.complete();
    }

    /** A stud rises too, then pops out onto the surface: lying flat, without water, the water left behind intact. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void studsFloatUpAndLieOnTheSurface(TestContext context) {
        waterColumn(context);
        BlockPos start = new BlockPos(X, BOTTOM, Z);
        context.setBlockState(start, ModBlocks.PLASTIC_STUDS[5].getDefaultState()
                .with(PlotBlock.WATERLOGGED, true)
                .with(PlotBlock.FACE, net.minecraft.block.enums.BlockFace.WALL)
                .with(PlotBlock.FACING, Direction.EAST));
        context.waitAndRun(risingTicks(TOP - BOTTOM + 1), () -> {
            BlockPos surface = new BlockPos(X, TOP + 1, Z);
            context.expectBlock(ModBlocks.PLASTIC_STUDS[5], surface);
            var state = context.getBlockState(surface);
            context.assertTrue(!state.get(PlotBlock.WATERLOGGED), "no water in it at the surface");
            context.assertTrue(state.get(PlotBlock.FACE) == net.minecraft.block.enums.BlockFace.FLOOR, "lying flat");
            for (int y = BOTTOM; y <= TOP; y++) {
                BlockPos pos = new BlockPos(X, y, Z);
                context.expectBlock(Blocks.WATER, pos);
                context.assertTrue(context.getBlockState(pos).getFluidState().isStill(), "still water at y=" + y);
            }
            context.complete();
        });
    }

    /** A dry stud on land never moves. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dryStudsStayPut(TestContext context) {
        BlockPos pos = new BlockPos(2, 2, 2);
        context.setBlockState(pos, ModBlocks.PLASTIC_STUDS[0].getDefaultState());
        context.waitAndRun(risingTicks(3), () -> {
            context.expectBlock(ModBlocks.PLASTIC_STUDS[0], pos);
            context.complete();
        });
    }

    /** Soul sand below: the upward bubble column carries it to the surface faster than still water. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void soulSandColumnPushesItUpFaster(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.SOUL_SAND);
        // Let the bubble column form, then drop the block at the bottom of it
        context.waitAndRun(25, () -> {
            context.setBlockState(new BlockPos(X, BOTTOM + 1, Z), plastic());
            int steps = TOP - BOTTOM - 1;
            // Still water would need steps x RISE_DELAY ticks: check well before that
            context.waitAndRun(steps * COLUMN_TICKS + 4, () -> {
                context.expectBlock(plastic(), new BlockPos(X, TOP, Z));
                context.complete();
            });
        });
    }

    /** Magma below: the whirlpool pulls it down until it rests on the magma block. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void magmaColumnPullsItDown(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.MAGMA_BLOCK);
        context.waitAndRun(25, () -> {
            context.setBlockState(new BlockPos(X, TOP, Z), plastic());
            context.waitAndRun((TOP - BOTTOM) * COLUMN_TICKS + 20, () -> {
                context.expectBlock(plastic(), new BlockPos(X, BOTTOM + 1, Z));
                context.expectBlock(Blocks.MAGMA_BLOCK, new BlockPos(X, BOTTOM, Z));
                for (int y = BOTTOM + 2; y <= TOP; y++) {
                    context.assertTrue(context.getBlockState(new BlockPos(X, y, Z)).getFluidState().isStill(),
                            "water (or its bubble column) left above at y=" + y);
                }
                context.complete();
            });
        });
    }

    /** Put above the surface (no water taken), it leaves air when the whirlpool pulls it down: no water created. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aDryBlockPulledDownLeavesAir(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.MAGMA_BLOCK);
        context.waitAndRun(25, () -> {
            BlockPos aboveSurface = new BlockPos(X, TOP + 1, Z);
            context.setBlockState(aboveSurface, plastic());
            context.assertTrue(!context.getBlockState(aboveSurface).get(PlasticBlock.WET), "dry above the surface");
            context.waitAndRun((TOP - BOTTOM + 1) * COLUMN_TICKS + 20, () -> {
                BlockPos bottom = new BlockPos(X, BOTTOM + 1, Z);
                context.expectBlock(plastic(), bottom);
                context.assertTrue(context.getBlockState(bottom).get(PlasticBlock.WET), "it holds the water it took");
                context.expectBlock(Blocks.AIR, aboveSurface);
                context.complete();
            });
        });
    }

    /** Placed into water it takes that water, and gives it back when it rises. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBlockPlacedInWaterIsWet(TestContext context) {
        waterColumn(context);
        BlockPos pos = new BlockPos(X, BOTTOM, Z);
        context.setBlockState(pos, plastic());
        context.assertTrue(context.getBlockState(pos).get(PlasticBlock.WET), "wet in water");
        context.complete();
    }

    /** Broken by a player (creative or survival), a block that took a water source gives it back; a dry one leaves air. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingAWetBlockGivesTheWaterBack(TestContext context) {
        waterColumn(context);
        BlockPos surface = new BlockPos(X, TOP, Z), above = new BlockPos(X, TOP + 1, Z);
        context.setBlockState(surface, plastic()); // wet, and it stays at the surface
        context.setBlockState(above, plastic()); // dry: above the water
        var player = context.createMockCreativeServerPlayerInWorld();
        player.interactionManager.tryBreakBlock(context.getAbsolutePos(above));
        context.expectBlock(Blocks.AIR, above);
        player.interactionManager.tryBreakBlock(context.getAbsolutePos(surface));
        context.expectBlock(Blocks.WATER, surface);
        context.assertTrue(context.getBlockState(surface).getFluidState().isStill(), "a water source");

        context.setBlockState(surface, plastic());
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        player.interactionManager.tryBreakBlock(context.getAbsolutePos(surface));
        context.expectBlock(Blocks.WATER, surface);
        context.complete();
    }

    /** Put in the middle of a whirlpool, it only ever goes down (never a first step up). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void inAWhirlpoolItNeverGoesUpFirst(TestContext context) {
        neverGoesUpFirst(context, false);
    }

    /**
     * Same right after soul sand was switched to magma: the column still points up for a while (magma turns it after
     * 20 ticks), but the block goes by the magma at once.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rightAfterSwitchingToMagmaItNeverGoesUpFirst(TestContext context) {
        neverGoesUpFirst(context, true);
    }

    private static void neverGoesUpFirst(TestContext context, boolean switched) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), switched ? Blocks.SOUL_SAND : Blocks.MAGMA_BLOCK);
        context.waitAndRun(25, () -> {
            if (switched) context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.MAGMA_BLOCK);
            BlockPos start = new BlockPos(X, BOTTOM + 3, Z);
            context.setBlockState(start, plastic());
            StringBuilder trace = new StringBuilder();
            for (int t = 1; t <= 12; t++) {
                int tick = t;
                context.waitAndRun(t, () -> {
                    int y = -1;
                    for (int dy = BOTTOM; dy <= TOP + 1; dy++) {
                        if (context.getBlockState(new BlockPos(X, dy, Z)).isOf(plastic())) y = dy;
                    }
                    trace.append(tick).append(':').append(y).append(' ');
                    context.assertTrue(y <= start.getY(), "went up: " + trace);
                    if (tick == 12) {
                        context.expectBlock(plastic(), new BlockPos(X, BOTTOM + 1, Z));
                        context.complete();
                    }
                });
            }
        });
    }

    /** Resting on magma, switching it to soul sand turns the whole column (above the block too) and sends it back up. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void switchingMagmaToSoulSandSendsItBackUp(TestContext context) {
        waterColumn(context);
        BlockPos source = new BlockPos(X, BOTTOM, Z);
        context.setBlockState(source, Blocks.MAGMA_BLOCK);
        context.setBlockState(new BlockPos(X, BOTTOM + 1, Z), plastic());
        context.waitAndRun(25, () -> {
            context.expectBlock(plastic(), new BlockPos(X, BOTTOM + 1, Z));
            context.setBlockState(source, Blocks.SOUL_SAND);
            context.waitAndRun((TOP - BOTTOM) * COLUMN_TICKS + 20, () -> {
                context.expectBlock(plastic(), new BlockPos(X, TOP, Z));
                context.complete();
            });
        });
    }

    /** Removing the magma under the block turns the column above it back into still water. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void removingTheSourceClearsTheColumnAbove(TestContext context) {
        waterColumn(context);
        BlockPos source = new BlockPos(X, BOTTOM, Z);
        context.setBlockState(source, Blocks.MAGMA_BLOCK);
        // Chained, so it stays at the bottom once the whirlpool is gone
        context.setBlockState(new BlockPos(X - 1, BOTTOM + 1, Z), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.X));
        context.setBlockState(new BlockPos(X, BOTTOM + 1, Z), plastic());
        context.waitAndRun(25, () -> {
            context.expectBlock(Blocks.BUBBLE_COLUMN, new BlockPos(X, BOTTOM + 2, Z));
            context.setBlockState(source, Blocks.STONE);
            context.waitAndRun(20, () -> {
                for (int y = BOTTOM + 2; y <= TOP; y++) context.expectBlock(Blocks.WATER, new BlockPos(X, y, Z));
                context.complete();
            });
        });
    }

    private static net.minecraft.block.BlockState wetStud() {
        return ModBlocks.PLASTIC_STUDS[3].getDefaultState().with(PlotBlock.WATERLOGGED, true);
    }

    /** A stud stopped by a block while rising sticks under it (ceiling face), keeping its water. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void studStoppedByABlockSticksUnderIt(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM + 3, Z), Blocks.STONE);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), wetStud());
        context.waitAndRun(risingTicks(TOP - BOTTOM), () -> {
            BlockPos pos = new BlockPos(X, BOTTOM + 2, Z);
            context.expectBlock(ModBlocks.PLASTIC_STUDS[3], pos);
            context.assertTrue(context.getBlockState(pos).get(PlotBlock.FACE) == net.minecraft.block.enums.BlockFace.CEILING, "under the stone");
            context.assertTrue(context.getBlockState(pos).get(PlotBlock.WATERLOGGED), "still full of water");
            context.complete();
        });
    }

    /** A stud pulled down by a magma column lies on the magma (floor face), keeping its water. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void studPulledDownLiesOnTheMagma(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.MAGMA_BLOCK);
        context.waitAndRun(25, () -> {
            context.setBlockState(new BlockPos(X, TOP, Z), wetStud().with(PlotBlock.FACE, net.minecraft.block.enums.BlockFace.CEILING));
            context.waitAndRun((TOP - BOTTOM) * COLUMN_TICKS + 20, () -> {
                BlockPos pos = new BlockPos(X, BOTTOM + 1, Z);
                context.expectBlock(ModBlocks.PLASTIC_STUDS[3], pos);
                context.assertTrue(context.getBlockState(pos).get(PlotBlock.FACE) == net.minecraft.block.enums.BlockFace.FLOOR, "lies on the magma");
                context.assertTrue(context.getBlockState(pos).get(PlotBlock.WATERLOGGED), "still full of water");
                context.complete();
            });
        });
    }

    /** A plastic block resting on magma does not cut the whirlpool: the column goes on above it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plasticDoesNotCutAMagmaColumn(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.MAGMA_BLOCK);
        context.setBlockState(new BlockPos(X, BOTTOM + 1, Z), plastic());
        context.waitAndRun(40, () -> {
            context.expectBlock(plastic(), new BlockPos(X, BOTTOM + 1, Z));
            for (int y = BOTTOM + 2; y <= TOP; y++) {
                var state = context.getBlockState(new BlockPos(X, y, Z));
                context.assertTrue(state.isOf(Blocks.BUBBLE_COLUMN) && state.get(net.minecraft.block.BubbleColumnBlock.DRAG),
                        "whirlpool above the plastic at y=" + y);
            }
            context.complete();
        });
    }

    /** A chained plastic block in a soul sand column does not cut it either. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void chainedPlasticDoesNotCutASoulSandColumn(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.SOUL_SAND);
        context.setBlockState(new BlockPos(X + 1, BOTTOM + 2, Z), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.X));
        context.setBlockState(new BlockPos(X, BOTTOM + 2, Z), plastic());
        context.waitAndRun(40, () -> {
            context.expectBlock(plastic(), new BlockPos(X, BOTTOM + 2, Z));
            for (int y = BOTTOM + 3; y <= TOP; y++) {
                var state = context.getBlockState(new BlockPos(X, y, Z));
                context.assertTrue(state.isOf(Blocks.BUBBLE_COLUMN) && !state.get(net.minecraft.block.BubbleColumnBlock.DRAG),
                        "upward column above the plastic at y=" + y);
            }
            context.complete();
        });
    }

    /** A mob standing on a rising plastic block rises with it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void entitiesStandingOnItRiseWithIt(TestContext context) {
        waterColumn(context);
        // A 1-block air pocket above the block keeps the mob out of the water while it rides up
        context.setBlockState(new BlockPos(X, BOTTOM + 1, Z), Blocks.AIR);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.STONE);
        var chicken = context.spawnMob(net.minecraft.entity.EntityType.CHICKEN, new BlockPos(X, BOTTOM + 1, Z));
        chicken.setAiDisabled(true);
        context.waitAndRun(5, () -> {
            context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.WATER);
            context.setBlockState(new BlockPos(X, BOTTOM + 1, Z), Blocks.WATER);
            // Put the block right under the chicken, in still water topped by water up to TOP
            context.setBlockState(new BlockPos(X, BOTTOM, Z), plastic());
            double startY = chicken.getY();
            context.waitAndRun(risingTicks(1) + 2, () -> {
                context.assertTrue(chicken.getY() >= startY + 0.9, "the chicken rose with the block: " + startY + " -> " + chicken.getY());
                context.complete();
            });
        });
    }

    /** A mob standing on a plastic block pulled down by magma goes down with it (water elevator). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void entitiesStandingOnItGoDownWithIt(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.MAGMA_BLOCK);
        // The block starts held by a chain, with a chicken standing on it just above the water
        context.setBlockState(new BlockPos(X + 1, TOP, Z), Blocks.CHAIN.getDefaultState().with(ChainBlock.AXIS, Direction.Axis.X));
        context.setBlockState(new BlockPos(X, TOP, Z), plastic());
        var chicken = context.spawnMob(net.minecraft.entity.EntityType.CHICKEN, new BlockPos(X, TOP + 1, Z));
        chicken.setAiDisabled(true);
        context.waitAndRun(25, () -> {
            double startY = chicken.getY();
            context.setBlockState(new BlockPos(X + 1, TOP, Z), Blocks.GLASS); // release it
            context.waitAndRun(2 * COLUMN_TICKS + 3, () -> {
                context.assertTrue(chicken.getY() <= startY - 1.5, "the chicken went down with the block: " + startY + " -> " + chicken.getY());
                context.complete();
            });
        });
    }

    private static net.minecraft.entity.decoration.ArmorStandEntity floatingStand(TestContext context, double y) {
        var stand = context.spawnEntity(net.minecraft.entity.EntityType.ARMOR_STAND,
                new net.minecraft.util.math.Vec3d(X + 0.5, y, Z + 0.5));
        stand.setNoGravity(true); // stays where it is in the water
        return stand;
    }

    /** Something floating just above a rising block (not touching it) is picked up and carried on top of it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itPicksUpWhatFloatsAboveIt(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), plastic());
        var stand = floatingStand(context, BOTTOM + 1.3);
        context.waitAndRun(risingTicks(TOP - BOTTOM), () -> {
            context.expectBlock(plastic(), new BlockPos(X, TOP, Z));
            double top = context.getAbsolutePos(new BlockPos(X, TOP, Z)).getY() + 1;
            context.assertTrue(Math.abs(stand.getY() - top) < 0.05, "carried on top: " + stand.getY() + " vs " + top);
            context.complete();
        });
    }

    /** It never crushes what it carries into a ceiling: it waits, and rises once the way is clear. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itWaitsRatherThanCrushingARider(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM + 3, Z), Blocks.STONE);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), plastic());
        var stand = floatingStand(context, BOTTOM + 1); // 2 blocks tall: no room to go up one more
        context.waitAndRun(risingTicks(2), () -> {
            context.expectBlock(plastic(), new BlockPos(X, BOTTOM, Z));
            context.assertTrue(!stand.isInsideWall(), "the rider is not pushed into the stone");
            stand.discard();
            context.waitAndRun(risingTicks(2), () -> {
                context.expectBlock(plastic(), new BlockPos(X, BOTTOM + 2, Z));
                context.complete();
            });
        });
    }

    private static final int SHAFT_TOP = 29;

    /** A 28-block water shaft (y 1 to SHAFT_TOP) over {@code source}, then {@code then} once its bubble column formed. */
    private static void shaft(TestContext context, Block source, Runnable then) {
        for (int y = 0; y <= SHAFT_TOP + 1; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) context.setBlockState(new BlockPos(X + dx, y, Z + dz), Blocks.GLASS);
            }
        }
        for (int y = 1; y <= SHAFT_TOP; y++) context.setBlockState(new BlockPos(X, y, Z), Blocks.WATER);
        context.setBlockState(new BlockPos(X, SHAFT_TOP + 1, Z), Blocks.AIR);
        context.setBlockState(new BlockPos(X, 0, Z), source);
        context.waitAndRun(40, then);
    }

    /** Over soul sand it rises at 1.4 blocks per tick, twice the 0.7 the column gives entities. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void itRisesTwiceAsFastAsASoulSandElevator(TestContext context) {
        shaft(context, Blocks.SOUL_SAND, () -> {
            context.setBlockState(new BlockPos(X, 1, Z), plastic());
            int blocks = SHAFT_TOP - 1; // 28 blocks: 20 ticks at 1.4, 40 at vanilla speed
            context.waitAndRun(16, () -> context.assertTrue(!context.getBlockState(new BlockPos(X, SHAFT_TOP, Z)).isOf(plastic()),
                    "not there yet after 16 ticks"));
            context.waitAndRun(blocks * 5 / 7 + 3, () -> {
                context.expectBlock(plastic(), new BlockPos(X, SHAFT_TOP, Z));
                context.complete();
            });
        });
    }

    /** Over magma it sinks at 0.6 blocks per tick, twice the 0.3 the whirlpool gives entities. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void itSinksTwiceAsFastAsAMagmaElevator(TestContext context) {
        shaft(context, Blocks.MAGMA_BLOCK, () -> {
            context.setBlockState(new BlockPos(X, SHAFT_TOP, Z), plastic());
            int blocks = SHAFT_TOP - 1; // 28 blocks: ~47 ticks at 0.6, 93 at vanilla speed
            context.waitAndRun(40, () -> context.assertTrue(!context.getBlockState(new BlockPos(X, 1, Z)).isOf(plastic()),
                    "not there yet after 40 ticks"));
            context.waitAndRun(blocks * 5 / 3 + 3, () -> {
                context.expectBlock(plastic(), new BlockPos(X, 1, Z));
                context.complete();
            });
        });
    }

    /** Sinking onto a mob, it pushes the mob down ahead of it, and stops right above it once the mob reached the bottom. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itPushesDownWhatIsInItsWay(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.MAGMA_BLOCK);
        // No AI: the whirlpool does not move it, only the block can
        var pig = context.spawnMob(net.minecraft.entity.EntityType.PIG, new BlockPos(X, BOTTOM + 2, Z));
        pig.setAiDisabled(true);
        context.waitAndRun(25, () -> {
            context.setBlockState(new BlockPos(X, TOP, Z), plastic());
            context.waitAndRun((TOP - BOTTOM) * COLUMN_TICKS + 10, () -> {
                // The pig (0.9 tall) was pushed down to the magma, the block cannot go lower without crushing it
                BlockPos rest = new BlockPos(X, BOTTOM + 2, Z);
                context.expectBlock(plastic(), rest);
                double under = context.getAbsolutePos(rest).getY();
                context.assertTrue(Math.abs(pig.getBoundingBox().maxY - under) < 0.05, "right under the block: " + pig.getBoundingBox().maxY);
                context.assertTrue(pig.getY() < under - 0.5, "pushed down from where it was");
                context.assertTrue(!pig.isInsideWall(), "not crushed");
                context.complete();
            });
        });
    }

    /** A stud resting on magma does not cut the whirlpool either. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void studsDoNotCutAMagmaColumn(TestContext context) {
        waterColumn(context);
        context.setBlockState(new BlockPos(X, BOTTOM, Z), Blocks.MAGMA_BLOCK);
        context.setBlockState(new BlockPos(X, BOTTOM + 1, Z), wetStud());
        context.waitAndRun(40, () -> {
            context.expectBlock(ModBlocks.PLASTIC_STUDS[3], new BlockPos(X, BOTTOM + 1, Z));
            for (int y = BOTTOM + 2; y <= TOP; y++) {
                var state = context.getBlockState(new BlockPos(X, y, Z));
                context.assertTrue(state.isOf(Blocks.BUBBLE_COLUMN) && state.get(net.minecraft.block.BubbleColumnBlock.DRAG),
                        "whirlpool above the stud at y=" + y);
            }
            context.complete();
        });
    }
}
