package fr.lordfinn.steveparty.gametest;

import com.mojang.serialization.DataResult;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.switchable.SwitchedOffBlockEntity;
import fr.lordfinn.steveparty.blocks.switchable.Switchables;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.WallMountedBlock;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryOps;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;

import java.util.List;
import java.util.Set;

public class SwitchableGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(1, 2, 1);

    private static Block redPlastic() {
        return ModBlocks.PLASTIC_BLOCKS[14]; // COLORS[14] = red
    }

    private static List<ItemEntity> itemsAround(ServerWorld world, BlockPos pos) {
        return world.getEntitiesByClass(ItemEntity.class, new Box(pos).expand(2), e -> true);
    }

    /** Plastic blocks and studs are switchable out of the box, vanilla blocks are not. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plasticIsSwitchableByDefault(TestContext context) {
        context.assertTrue(Switchables.isSwitchable(redPlastic().getDefaultState()), "plastic block switchable");
        context.assertTrue(Switchables.isSwitchable(ModBlocks.PLASTIC_STUDS[0].getDefaultState()), "stud switchable");
        context.assertTrue(!Switchables.isSwitchable(Blocks.STONE.getDefaultState()), "stone not switchable by default");
        context.complete();
    }

    /** Switching off then on gives back the exact same state (here an east-facing wall stud). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void switchingOffThenOnRestoresTheExactState(TestContext context) {
        BlockState stud = ModBlocks.PLASTIC_STUDS[3].getDefaultState()
                .with(WallMountedBlock.FACE, BlockFace.WALL).with(WallMountedBlock.FACING, Direction.EAST);
        context.setBlockState(POS, stud);
        BlockPos abs = context.getAbsolutePos(POS);
        ServerWorld world = context.getWorld();

        Switchables.switchOff(world, abs);
        context.assertTrue(world.getBlockState(abs).isOf(ModBlocks.SWITCHED_OFF_BLOCK), "switched off");
        context.assertTrue(world.getBlockEntity(abs) instanceof SwitchedOffBlockEntity be && be.getStoredState() == stud,
                "stored state is the stud");
        Switchables.switchOn(world, abs);
        context.assertTrue(world.getBlockState(abs) == stud, "same stud state back");
        context.complete();
    }

    /** Switching off twice must not wrap the placeholder in another placeholder. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void switchingOffTwiceDoesNotNest(TestContext context) {
        context.setBlockState(POS, redPlastic());
        BlockPos abs = context.getAbsolutePos(POS);
        ServerWorld world = context.getWorld();
        Switchables.switchOff(world, abs);
        Switchables.switchOff(world, abs);
        Switchables.switchOn(world, abs);
        context.assertTrue(world.getBlockState(abs).isOf(redPlastic()), "plastic block back after one switch on");
        context.complete();
    }

    /** Blocks with a block entity are never switched, even when listed by the server config. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blocksWithABlockEntityAreNeverSwitched(TestContext context) {
        Set<Block> previous = Switchables.getConfigBlocks();
        try {
            Switchables.setConfigBlocks(Set.of(Blocks.CHEST));
            context.setBlockState(POS, Blocks.CHEST);
            BlockPos abs = context.getAbsolutePos(POS);
            Switchables.switchOff(context.getWorld(), abs);
            context.assertTrue(context.getWorld().getBlockState(abs).isOf(Blocks.CHEST), "chest untouched");
        } finally {
            Switchables.setConfigBlocks(previous);
        }
        context.complete();
    }

    /** Breaking a switched off block drops the original item once; switching the spot on afterwards gives nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingASwitchedOffBlockDropsTheOriginalOnce(TestContext context) {
        context.setBlockState(POS, redPlastic());
        BlockPos abs = context.getAbsolutePos(POS);
        ServerWorld world = context.getWorld();
        Switchables.switchOff(world, abs);
        world.breakBlock(abs, true);
        Switchables.switchOn(world, abs);
        context.assertTrue(world.getBlockState(abs).isAir(), "nothing comes back");
        context.waitAndRun(2, () -> {
            List<ItemEntity> items = itemsAround(world, abs);
            int count = items.stream().mapToInt(e -> e.getStack().getCount()).sum();
            context.assertTrue(count == 1, "exactly one item dropped, got " + count);
            context.assertTrue(items.stream().allMatch(e -> e.getStack().isOf(redPlastic().asItem())), "the plastic block");
            context.complete();
        });
    }

    /** A switched off stone broken without a pickaxe drops nothing, like stone itself. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noDropsWithoutTheToolTheOriginalNeeds(TestContext context) {
        Set<Block> previous = Switchables.getConfigBlocks();
        try {
            Switchables.setConfigBlocks(Set.of(Blocks.STONE));
            context.setBlockState(POS, Blocks.STONE);
            BlockPos abs = context.getAbsolutePos(POS);
            Switchables.switchOff(context.getWorld(), abs);
            context.assertTrue(context.getWorld().getBlockState(abs).isOf(ModBlocks.SWITCHED_OFF_BLOCK), "stone switched off");
            context.getWorld().breakBlock(abs, true);
        } finally {
            Switchables.setConfigBlocks(previous);
        }
        BlockPos abs = context.getAbsolutePos(POS);
        context.waitAndRun(2, () -> {
            context.assertTrue(itemsAround(context.getWorld(), abs).isEmpty(), "no drop without a pickaxe");
            context.complete();
        });
    }

    /** The placeholder cannot be blown up or pushed, so it cannot be used to farm drops. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placeholderIsExplosionAndPistonProof(TestContext context) {
        BlockState placeholder = ModBlocks.SWITCHED_OFF_BLOCK.getDefaultState();
        context.assertTrue(ModBlocks.SWITCHED_OFF_BLOCK.getBlastResistance() >= 1200f, "explosion proof");
        context.assertTrue(placeholder.getPistonBehavior() == PistonBehavior.BLOCK, "piston proof");
        context.assertTrue(placeholder.blocksMovement(), "fluids cannot wash it away");
        context.complete();
    }

    /** Studs need no support and stay when their support disappears. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void studsNeedNoSupport(TestContext context) {
        context.setBlockState(POS.down(), redPlastic());
        context.setBlockState(POS, ModBlocks.PLASTIC_STUDS[0].getDefaultState());
        context.setBlockState(POS.down(), Blocks.AIR);
        context.waitAndRun(2, () -> {
            context.expectBlock(ModBlocks.PLASTIC_STUDS[0], POS);
            context.complete();
        });
    }

    /** Saves from before the rename: old switcher block ids load as plastic blocks (placed blocks and items). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldSwitcherBlockIdsLoadAsPlasticBlocks(TestContext context) {
        context.assertTrue(Registries.BLOCK.get(Steveparty.id("red_switcher_block")) == redPlastic(), "block id alias");

        NbtCompound blockNbt = new NbtCompound();
        blockNbt.putString("Name", "steveparty:red_switcher_block");
        NbtCompound properties = new NbtCompound();
        properties.put("solid", NbtString.of("false"));
        blockNbt.put("Properties", properties);
        DataResult<BlockState> state = BlockState.CODEC.parse(NbtOps.INSTANCE, blockNbt);
        context.assertTrue(state.result().map(s -> s.isOf(redPlastic())).orElse(false), "chunk palette entry migrated");

        NbtCompound itemNbt = new NbtCompound();
        itemNbt.putString("id", "steveparty:red_switcher_block");
        itemNbt.putInt("count", 3);
        RegistryOps<net.minecraft.nbt.NbtElement> ops = context.getWorld().getRegistryManager().getOps(NbtOps.INSTANCE);
        DataResult<ItemStack> stack = ItemStack.CODEC.parse(ops, itemNbt);
        context.assertTrue(stack.result().map(s -> s.isOf(redPlastic().asItem()) && s.getCount() == 3).orElse(false),
                "inventory item migrated");
        context.complete();
    }
}
