package fr.lordfinn.steveparty.board.copy;

import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The settings of a kind of block, as the Wrench copies and pastes them (see {@link WrenchCopy}; every kind:
 * {@link BlockSettingsKinds}). Settings only: never links or positions, never the state of a party (a waiting antenna, a
 * registered player), never real items in survival. Items it holds may be written along (a cartridge, a page): only a
 * creative paste places copies of them, in what the target has empty.
 */
public interface BlockSettings {
    /** Blocks sharing it paste on each other. */
    String family();

    /** Whether {@code state} is of this kind. Both sides: the client predicts the click from it. */
    boolean handles(BlockState state);

    /** Where the settings of the block clicked at {@code pos} live (a large tile's main block, a podium column's bottom). */
    default BlockPos settingsPos(World world, BlockPos pos) {
        return pos;
    }

    /** Who may paste on it: the same players as its screen. */
    default boolean mayEdit(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        return ScreenHandlerChecks.canBuildAt(player, pos);
    }

    /** Its settings, null when it has nothing to copy ({@code creative}: its items count). */
    @Nullable NbtCompound write(ServerWorld world, BlockPos pos, RegistryWrapper.WrapperLookup registries, boolean creative);

    /** Pastes {@code paste}'s settings on the block at {@code paste.pos()}. */
    Pasted paste(Paste paste);

    /**
     * A paste.
     *
     * @param source   the block the settings were copied from (block-only settings paste on the same block only)
     * @param creative whether copies of the source's items may fill what the target has empty
     */
    record Paste(ServerPlayerEntity player, ServerWorld world, BlockPos pos, Identifier source, NbtCompound settings,
                 boolean creative) {
        public RegistryWrapper.WrapperLookup registries() {
            return world.getRegistryManager();
        }
    }

    /**
     * What a paste did.
     *
     * @param changed whether something changed
     * @param missing the items the target lacks for the rest (« Put a Shop Cartridge in first »)
     * @param refusal why nothing could be pasted (the whole message), or null
     */
    record Pasted(boolean changed, List<Text> missing, @Nullable Text refusal) {
        public static Pasted of(boolean changed) {
            return new Pasted(changed, List.of(), null);
        }

        public static Pasted refused(Text why) {
            return new Pasted(false, List.of(), why);
        }
    }
}
