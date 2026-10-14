package fr.lordfinn.steveparty.screen_handlers;

import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * A block's screen opened from afar (the Remote): the screen stays usable however far the player is, in whatever
 * dimension, as long as it is the one opened that way ({@link ScreenHandlerChecks#canUseBlockEntity} asks here when
 * the player is out of reach). The block's chunk is kept loaded while the screen is open.
 */
public final class RemoteAccess {
    /** Keeps the chunk of a block whose screen is open from afar: renewed while the screen is checked. */
    private static final ChunkTicketType<ChunkPos> TICKET = ChunkTicketType.create("steveparty_remote", Comparator.comparingLong(ChunkPos::toLong), 40);
    /** The ticket's radius: the block entity ticks (its screen follows it). */
    private static final int TICKET_RADIUS = 2;

    /** A screen opened from afar: whose block, which screen (its sync id). */
    private record Grant(GlobalPos block, int syncId) {
    }

    private static final Map<UUID, Grant> GRANTS = ServerMemory.forgetOnStop(new HashMap<>());

    private RemoteAccess() {
    }

    /**
     * Opens the screen of {@code blockEntity} for {@code player}, wherever he is.
     *
     * @return false if it has no screen, or it did not open
     */
    public static boolean open(ServerPlayerEntity player, BlockEntity blockEntity) {
        if (!(blockEntity instanceof NamedScreenHandlerFactory factory) || !(blockEntity.getWorld() instanceof ServerWorld world)) return false;
        keepLoaded(world, blockEntity);
        OptionalInt syncId = player.openHandledScreen(factory);
        if (syncId.isEmpty()) return false;
        GRANTS.put(player.getUuid(), new Grant(GlobalPos.create(world.getRegistryKey(), blockEntity.getPos().toImmutable()), syncId.getAsInt()));
        return true;
    }

    /** Whether {@code player} uses the screen of {@code blockEntity} he opened from afar (its chunk kept loaded). */
    public static boolean allows(PlayerEntity player, BlockEntity blockEntity) {
        Grant grant = GRANTS.get(player.getUuid());
        if (grant == null || !(blockEntity.getWorld() instanceof ServerWorld world)) return false;
        if (player.currentScreenHandler == null || player.currentScreenHandler.syncId != grant.syncId()
                || player.currentScreenHandler == player.playerScreenHandler
                || !grant.block().equals(GlobalPos.create(world.getRegistryKey(), blockEntity.getPos()))) return false;
        keepLoaded(world, blockEntity);
        return true;
    }

    private static void keepLoaded(ServerWorld world, BlockEntity blockEntity) {
        ChunkPos chunk = new ChunkPos(blockEntity.getPos());
        world.getChunkManager().addTicket(TICKET, chunk, TICKET_RADIUS, chunk);
    }
}
