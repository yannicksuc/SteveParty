package fr.lordfinn.steveparty.board;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * What a Wrench remembers besides its origin ({@link fr.lordfinn.steveparty.components.BlockOriginComponent}): lives
 * on the item, so every player has their own (multiplayer safe), and an old wrench without it gets the defaults.
 *
 * @param mode        what a click does
 * @param chainStart  first board space of the chain being traced (closing the loop on it ends the chain)
 * @param chainLength number of board spaces in the chain so far (the origin is the last one)
 * @param slot        cartridge slot edited on a 16-slot board space (Advanced Tile, check point), -1: the active one
 * @param autoLink    a board space placed while tracing is linked from the origin and becomes the new origin
 */
public record WrenchState(WrenchMode mode, Optional<BlockPos> chainStart, int chainLength, int slot, boolean autoLink) {
    public static final int ACTIVE_SLOT = -1;
    public static final WrenchState DEFAULT = new WrenchState(WrenchMode.TRACE, Optional.empty(), 0, ACTIVE_SLOT, true);

    public static final Codec<WrenchState> CODEC = RecordCodecBuilder.create(builder -> builder.group(
            WrenchMode.CODEC.optionalFieldOf("mode", WrenchMode.TRACE).forGetter(WrenchState::mode),
            BlockPos.CODEC.optionalFieldOf("chain_start").forGetter(WrenchState::chainStart),
            Codec.INT.optionalFieldOf("chain_length", 0).forGetter(WrenchState::chainLength),
            Codec.INT.optionalFieldOf("slot", ACTIVE_SLOT).forGetter(WrenchState::slot),
            Codec.BOOL.optionalFieldOf("auto_link", true).forGetter(WrenchState::autoLink)
    ).apply(builder, WrenchState::new));

    public static WrenchState of(ItemStack stack) {
        return stack.getOrDefault(ModComponents.WRENCH_STATE, DEFAULT);
    }

    public WrenchState withMode(WrenchMode mode) {
        return new WrenchState(mode, chainStart, chainLength, slot, autoLink);
    }

    public WrenchState withChain(Optional<BlockPos> start, int length) {
        return new WrenchState(mode, start, length, slot, autoLink);
    }

    public WrenchState withSlot(int slot) {
        return new WrenchState(mode, chainStart, chainLength, slot, autoLink);
    }

    public WrenchState withAutoLink(boolean autoLink) {
        return new WrenchState(mode, chainStart, chainLength, slot, autoLink);
    }
}
