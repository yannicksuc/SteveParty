package fr.lordfinn.steveparty.blocks.switchable;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.BlockPos;

import java.util.Set;

import static fr.lordfinn.steveparty.sounds.ModSounds.POP_SOUND_EVENT;

/**
 * Blocks the hop switch can make disappear and reappear: anything in the {@code steveparty:switchable} tag
 * (datapacks can extend it) or listed in the server config, except blocks with a block entity.
 * A switched off block is replaced by {@link ModBlocks#SWITCHED_OFF_BLOCK}, which remembers its exact state.
 */
public final class Switchables {
    public static final TagKey<Block> SWITCHABLE = TagKey.of(RegistryKeys.BLOCK, Steveparty.id("switchable"));
    /** Plastic blocks and studs: mined instantly by the wrench, faster with shears. */
    public static final TagKey<Block> PLASTIC = TagKey.of(RegistryKeys.BLOCK, Steveparty.id("plastic"));

    // Blocks added by the server config (see SwitchableConfig), synced to clients for the tooltip
    private static volatile Set<Block> configBlocks = Set.of();

    private Switchables() {
    }

    public static boolean isSwitchable(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) return false;
        return state.isIn(SWITCHABLE) || configBlocks.contains(state.getBlock());
    }

    public static Set<Block> getConfigBlocks() {
        return configBlocks;
    }

    public static void setConfigBlocks(Set<Block> blocks) {
        configBlocks = Set.copyOf(blocks);
    }

    public static boolean isSwitchedOff(BlockState state) {
        return state.isOf(ModBlocks.SWITCHED_OFF_BLOCK);
    }

    public static void toggle(ServerWorld world, BlockPos pos) {
        if (isSwitchedOff(world.getBlockState(pos))) switchOn(world, pos);
        else switchOff(world, pos);
    }

    public static void setOn(ServerWorld world, BlockPos pos, boolean on) {
        if (on) switchOn(world, pos);
        else switchOff(world, pos);
    }

    public static void switchOff(ServerWorld world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isSwitchable(state)) return;
        BlockState switchedOffState = ModBlocks.SWITCHED_OFF_BLOCK.getDefaultState();
        // Store the original state before any neighbour reacts (no shape/neighbour update yet), so the
        // placeholder is never empty, then let the neighbours react
        if (!world.setBlockState(pos, switchedOffState, Block.NOTIFY_LISTENERS | Block.FORCE_STATE)) return;
        if (!(world.getBlockEntity(pos) instanceof SwitchedOffBlockEntity switchedOff)) {
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            return;
        }
        switchedOff.setStoredState(state);
        switchedOffState.updateNeighbors(world, pos, Block.NOTIFY_ALL);
        world.updateNeighborsAlways(pos, switchedOffState.getBlock());
        playPop(world, pos);
    }

    public static void switchOn(ServerWorld world, BlockPos pos) {
        if (!isSwitchedOff(world.getBlockState(pos))) return;
        if (!(world.getBlockEntity(pos) instanceof SwitchedOffBlockEntity switchedOff)) return;
        // The placeholder is replaced by the stored block: one block in, one block out
        world.setBlockState(pos, switchedOff.getStoredState(), Block.NOTIFY_ALL);
        playPop(world, pos);
    }

    private static void playPop(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos, POP_SOUND_EVENT, SoundCategory.BLOCKS, 0.4f, 0.4f);
    }
}
