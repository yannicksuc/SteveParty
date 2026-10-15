package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;

/**
 * Client → server, from the Party Antenna's screen: its whole setting (moment, waiting mode, filter), or a test pulse
 * ({@code test}: the setting is not changed then).
 */
public record AntennaSettingsPayload(BlockPos pos, int moment, boolean waiting, int filter, int filterValue, boolean test)
        implements ServerboundPayload {
    public static final CustomPayload.Id<AntennaSettingsPayload> ID = new CustomPayload.Id<>(Steveparty.id("antenna_settings"));
    public static final PacketCodec<RegistryByteBuf, AntennaSettingsPayload> CODEC = PacketCodec.tuple(
            BlockPos.PACKET_CODEC, AntennaSettingsPayload::pos,
            PacketCodecs.VAR_INT, AntennaSettingsPayload::moment,
            PacketCodecs.BOOL, AntennaSettingsPayload::waiting,
            PacketCodecs.VAR_INT, AntennaSettingsPayload::filter,
            PacketCodecs.VAR_INT, AntennaSettingsPayload::filterValue,
            PacketCodecs.BOOL, AntennaSettingsPayload::test,
            AntennaSettingsPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** The Wrench in hand, the antenna in reach, and the player allowed to set it ({@link PartyBellBlockEntity#maySet}). */
    @Override
    public void handle(ServerPlayerEntity player) {
        if (!(player.getMainHandStack().getItem() instanceof WrenchItem) && !(player.getOffHandStack().getItem() instanceof WrenchItem)) return;
        if (!ScreenHandlerChecks.isInReach(player, pos) || !ScreenHandlerChecks.canBuildAt(player, pos)) return;
        if (!(player.getWorld().getBlockEntity(pos) instanceof PartyBellBlockEntity antenna) || !antenna.maySet(player)) return;
        if (test) {
            if (!antenna.inParty()) antenna.test();
            return;
        }
        PartyMoment[] moments = PartyMoment.values();
        PartyBellBlockEntity.Filter[] filters = PartyBellBlockEntity.Filter.values();
        if (moment < 0 || moment >= moments.length || filter < 0 || filter >= filters.length) return;
        PartyBellBlock.apply(player.getWorld(), pos, moments[moment], waiting, filters[filter], filterValue);
    }
}
