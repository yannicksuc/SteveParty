package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.items.custom.MiniGameRemoteItem;
import fr.lordfinn.steveparty.items.custom.ShopkeeperKeyItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.podium.Podiums;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The blocks something can be linked to by a click or a brush stroke (a target, never the holder of the link), each
 * answer taken from the code that makes that link:
 * <ul>
 *     <li>a storage, for a cartridge's or the Party Controller's chests and a trader's stock
 *     ({@link CartridgeContainers#accepts(BlockState, BlockEntity)});</li>
 *     <li>a board space, the end of a board path ({@link CartridgeLinks.BoardPaths});</li>
 *     <li>a Spawn Bubble, where a mob space's mob appears ({@link CartridgeSpawnMarker#accepts(BlockState)});</li>
 *     <li>a pipe, a podium, a goal pole base, a step controller, for a Mini-game Page ({@link MiniGamePipes#linkable},
 *     {@link Podiums#linkable});</li>
 *     <li>a trading stall, a cash register, for a Shopkeeper Key ({@link ShopkeeperKeyItem#linkable});</li>
 *     <li>a Mini-game Controller, for a Mini-game Remote ({@link MiniGameRemoteItem#linkable}).</li>
 * </ul>
 * The blocks a Hop Switch switches are left out: they are tagged « [Switchable] » already.
 */
public final class LinkTargets {
    /**
     * Per block, a sample of its block entity (empty: none), made once; the answer itself is not kept, the block tags it
     * reads may change (a reload, joining a server).
     */
    private static final Map<Block, Optional<BlockEntity>> SAMPLES = new ConcurrentHashMap<>();

    private LinkTargets() {
    }

    /** Whether the block at {@code pos} can be linked to. */
    public static boolean accepts(World world, BlockPos pos) {
        return accepts(world.getBlockState(pos), world.getBlockEntity(pos));
    }

    /** Whether a block in {@code state} with {@code blockEntity} (placed, or a sample) can be linked to. */
    public static boolean accepts(BlockState state, @Nullable BlockEntity blockEntity) {
        return CartridgeContainers.accepts(state, blockEntity)
                || blockEntity instanceof BoardSpaceBlockEntity
                || CartridgeSpawnMarker.accepts(state)
                || MiniGamePipes.linkable(state) || Podiums.linkable(state)
                || ShopkeeperKeyItem.linkable(blockEntity)
                || MiniGameRemoteItem.linkable(blockEntity);
    }

    /**
     * Whether {@code block}, placed in its default state, can be linked to (its item's tooltip says so): judged on a
     * sample of its block entity. A block entity that can't be made apart from a world counts as none.
     */
    public static boolean isTarget(Block block) {
        return accepts(block.getDefaultState(), SAMPLES.computeIfAbsent(block, LinkTargets::sample).orElse(null));
    }

    private static Optional<BlockEntity> sample(Block block) {
        if (!(block instanceof BlockEntityProvider provider)) return Optional.empty();
        try {
            return Optional.ofNullable(provider.createBlockEntity(BlockPos.ORIGIN, block.getDefaultState()));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
