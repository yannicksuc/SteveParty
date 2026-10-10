package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.payloads.Payloads;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.service.MarkerResidents;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

/**
 * Client → server: the settings of a Spawn Marker chosen in its menu (when its mob shows, how high it appears). Taken
 * from a player who may build there, close enough to it.
 *
 * @param lift in half blocks (clamped, see {@link SpawnMarkerBlockEntity#setLiftSteps})
 */
public record SpawnMarkerSettings(BlockPos pos, boolean resident, int lift) implements CustomPayload {
    public static final Id<SpawnMarkerSettings> ID = new Id<>(Steveparty.id("spawn_marker_settings"));
    public static final PacketCodec<RegistryByteBuf, SpawnMarkerSettings> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, SpawnMarkerSettings::pos,
            PacketCodecs.BOOL, SpawnMarkerSettings::resident,
            PacketCodecs.VAR_INT, SpawnMarkerSettings::lift,
            SpawnMarkerSettings::new);
    /** How far from the marker its menu is used. */
    private static final double REACH = 8;

    public static void initialize() {
        Payloads.c2s(ID, CODEC, SpawnMarkerSettings::apply);
    }

    /** The settings applied to the marker, if {@code player} may. @return whether they were */
    public static boolean apply(ServerPlayerEntity player, SpawnMarkerSettings settings) {
        ServerWorld world = player.getServerWorld();
        BlockPos pos = settings.pos();
        if (player.squaredDistanceTo(pos.toCenterPos()) > REACH * REACH || !ScreenHandlerChecks.canBuildAt(player, pos)) return false;
        if (!(world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker)) return false;
        boolean changed = marker.isResident() != settings.resident() || marker.getLiftSteps() != settings.lift();
        marker.setResident(settings.resident());
        marker.setLiftSteps(settings.lift());
        if (changed) {
            world.playSound(null, pos, SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.BLOCKS, 0.4f, settings.resident() ? 1.3f : 0.9f);
            MarkerResidents.refresh(world, pos);
        }
        return true;
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
