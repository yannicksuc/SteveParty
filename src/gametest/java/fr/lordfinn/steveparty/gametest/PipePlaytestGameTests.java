package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeSolid;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeTravel;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.function.BooleanSupplier;

import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * Pipes after the playtest of 2026-10-06: the speed inside is capped (#76): however fast it goes in, a traveller
 * rides at most {@link PipeTravel#MAX_SPEED}; thrown out of a pipe straight into another one (a loop of pipes without a
 * way out), it gains nothing from the throw. A pipe placed over items lying there does not swallow them (#77).
 */
public class PipePlaytestGameTests implements FabricGameTest {
    private static Block pipe() {
        return ModBlocks.PIPES[PipeKind.OPAQUE.ordinal()][5];
    }

    /** A lone pipe standing on stone: a mouth on top, capped into the stone (a warp). */
    private static BlockPos lone(TestContext context, int x, int z) {
        context.setBlockState(new BlockPos(x, 1, z), Blocks.STONE);
        BlockPos pos = new BlockPos(x, 2, z);
        context.setBlockState(pos, pipe().getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        return pos;
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 200)
    public void speedInsidePipesIsCapped(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos first = lone(context, 1, 1), second = lone(context, 5, 1), third = lone(context, 1, 5);
        PigEntity fast = context.spawnEntity(EntityType.PIG, new BlockPos(1, 3, 5));
        fast.setAiDisabled(true);
        context.assertTrue(PipeTravel.enter(world, context.getAbsolutePos(third), Direction.UP, fast, 10), "the fast one goes in");
        PigEntity looper = context.spawnEntity(EntityType.PIG, new BlockPos(1, 3, 1));
        looper.setAiDisabled(true);
        context.assertTrue(PipeTravel.enter(world, context.getAbsolutePos(first), Direction.UP, looper, 0), "the looper goes in");
        context.waitAndRun(1, () -> {
            context.assertTrue(fast.getVehicle() instanceof PipeCarrierEntity carrier && carrier.speed() <= PipeTravel.MAX_SPEED,
                    "however fast it went in, no faster than the cap inside");
            // The looper warps to the second pipe and is thrown out of it: straight back into the first one, fast
            when(context, () -> !looper.hasVehicle() && looper.getY() > context.getAbsolutePos(second).getY() + 0.9
                    && Math.abs(looper.getX() - (context.getAbsolutePos(second).getX() + 0.5)) < 0.6, 60, "never thrown out of the second pipe", () -> {
                context.assertTrue(PipeTravel.enter(world, context.getAbsolutePos(first), Direction.UP, looper, PipeTravel.MAX_SPEED),
                        "thrown out, it goes into the first pipe again");
                context.waitAndRun(1, () -> {
                    context.assertTrue(looper.getVehicle() instanceof PipeCarrierEntity carrier && carrier.speed() <= PipeTravel.BASE_SPEED + 1.0E-6,
                            "no speed gained from being thrown out of a pipe");
                    context.complete();
                });
            });
        });
    }

    /** A pipe broken and placed again over what it dropped: the items stay; one dropped into it afterwards goes in. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void aPipePlacedOverItemsDoesNotSwallowThem(TestContext context) {
        ServerWorld world = context.getWorld();
        context.setBlockState(new BlockPos(2, 1, 2), Blocks.STONE);
        Vec3d at = context.getAbsolute(new Vec3d(2.5, 2.05, 2.5));
        ItemEntity lying = new ItemEntity(world, at.x, at.y, at.z, new ItemStack(Items.DIAMOND));
        lying.setVelocity(Vec3d.ZERO);
        world.spawnEntity(lying);
        BlockPos pos = new BlockPos(2, 2, 2);
        context.setBlockState(pos, pipe().getDefaultState().with(PipeBlock.SOLID, PipeSolid.DOWN));
        context.waitAndRun(10, () -> {
            context.assertTrue(lying.isAlive() && !PipeTravel.isTravelling(lying), "the item the pipe was placed over stays");
            ItemEntity dropped = new ItemEntity(world, at.x, at.y + 1.25, at.z, new ItemStack(Items.EMERALD));
            dropped.setVelocity(Vec3d.ZERO);
            world.spawnEntity(dropped);
            when(context, () -> PipeTravel.isTravelling(dropped) || !dropped.isAlive(), 40, "an item dropped into it afterwards never went in", () -> {
                lying.discard();
                context.complete();
            });
        });
    }
}
