package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;

/**
 * No phantom pawn: a pawn that is gone for good (killed, picked up as an item, removed by a command; not merely
 * unloaded) is unbound from the start tiles bound to it, so a new pawn can take its place and no party counts it again,
 * and a running party it plays excludes it (its turns would wait for a pawn that will never come back).
 */
public final class PartyPawnGuard {
    private PartyPawnGuard() {
    }

    public static void initialize() {
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (!(entity instanceof MobEntity mob) || !((TokenizedEntityInterface) mob).steveparty$isTokenized()) return;
            Entity.RemovalReason reason = entity.getRemovalReason();
            if (reason == null || !reason.shouldDestroy()) return;
            UUID token = entity.getUuid();
            BlockPos at = entity.getBlockPos();
            // Not while the world removes it: the party moves on (its next step) on the next tick
            SCHEDULER.schedule(UUID.randomUUID(), 1, () -> gone(world, token, at));
        });
    }

    /** The pawn {@code token}, last seen at {@code at}, is gone for good. */
    static void gone(ServerWorld world, UUID token, BlockPos at) {
        if (world.getEntity(token) != null) return; // back (a pawn moved to another dimension keeps its UUID)
        Set<BlockPos> starts = new LinkedHashSet<>();
        BlockPos under = BoardSpaces.boardSpacePosAt(world, at);
        if (under != null) starts.add(under);
        BlockPos below = BoardSpaces.boardSpacePosAt(world, at.down());
        if (below != null) starts.add(below);
        for (PartyControllerEntity controller : PartyControllers.all()) {
            if (controller.getWorld() != world) continue;
            BlockPos start = controller.getStartTile(token);
            if (start != null) starts.add(start);
            starts.addAll(controller.getStartLinks());
        }
        for (BlockPos start : starts) unbind(world, start, token);
        PartyControllerEntity.getRunningPartyOf(token).ifPresent(party -> party.excludeToken(token));
    }

    /** The start tile at {@code pos} forgets {@code token} if it is bound to it. @return true if it was */
    public static boolean unbind(ServerWorld world, BlockPos pos, UUID token) {
        if (!world.isChunkLoaded(pos) || !PartyBoard.isStart(world, pos)
                || !(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tile)) return false;
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        if (cartridge.isEmpty() || !token.toString().equals(cartridge.get(ModComponents.TB_START_BOUND_ENTITY))) return false;
        cartridge.remove(ModComponents.TB_START_BOUND_ENTITY);
        tile.update();
        return true;
    }

    /** The pawn bound to the start tile at {@code pos}, null for none. */
    public static @Nullable UUID boundTo(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity tile)) return null;
        String bound = tile.getActiveCartridgeItemStack().get(ModComponents.TB_START_BOUND_ENTITY);
        try {
            return bound == null ? null : UUID.fromString(bound);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
