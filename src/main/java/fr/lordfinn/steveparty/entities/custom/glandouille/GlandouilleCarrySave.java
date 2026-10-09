package fr.lordfinn.steveparty.entities.custom.glandouille;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.List;

/**
 * A player leaving with a stack in hand takes it with him: vanilla would drop his passengers where he stood, on top of
 * his head (where a dismounted passenger goes), and nothing would put them back in his hand. The stack is saved with
 * the player instead (its bottom one with the ones above as its passengers) and taken out of the world; it is back in
 * his hand when he comes back.
 */
public final class GlandouilleCarrySave {
    public static final AttachmentType<NbtCompound> TYPE = AttachmentRegistry.<NbtCompound>builder()
            .persistent(NbtCompound.CODEC)
            .buildAndRegister(Steveparty.id("carried_glandouilles"));

    private GlandouilleCarrySave() {
    }

    public static void initialize() {
        // before the player is saved and taken out of the world
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> stash(handler.player));
        // once he is in his world again
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> restore(handler.player));
    }

    /** {@code player} leaves: the stack he carries goes with his data and out of the world. False if he carries none. */
    public static boolean stash(ServerPlayerEntity player) {
        GlandouilleEntity carried = GlandouilleTowers.carried(player);
        if (carried == null) return false;
        NbtCompound nbt = new NbtCompound();
        if (!carried.saveSelfNbt(nbt)) return false;
        player.setAttached(TYPE, nbt);
        // top first, so that none of them is dismounted on its way out
        List<GlandouilleEntity> members = GlandouilleTowers.members(carried);
        for (int i = members.size() - 1; i >= 0; i--) members.get(i).discard();
        return true;
    }

    /** {@code player} is back: the stack he left with is in his hand again. False if he left with none. */
    public static boolean restore(ServerPlayerEntity player) {
        NbtCompound nbt = player.getAttached(TYPE);
        if (nbt == null) return false;
        player.removeAttached(TYPE);
        ServerWorld world = player.getServerWorld();
        Entity bottom = EntityType.loadEntityWithPassengers(nbt, world, entity -> {
            entity.refreshPositionAndAngles(player.getX(), player.getY(), player.getZ(), player.getYaw(), 0f);
            return entity;
        });
        if (bottom == null || !world.spawnNewEntityAndPassengers(bottom)) return false;
        // in his hand again; if he can't carry it now, it stands at his feet
        if (bottom instanceof GlandouilleEntity glandouille && GlandouilleTowers.carried(player) == null) {
            glandouille.startRiding(player, true);
        }
        return true;
    }
}
