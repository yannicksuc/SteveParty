package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.components.TeleportSettingsComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

/**
 * Client → server: a change made in a Teleport Cartridge's menu (all its settings), for the tile at {@code tile} or
 * (empty) the cartridge in hand. The server checks the player may edit it (see TeleportCartridgeItem#applyFromMenu).
 */
public record TeleportSettingsPayload(Optional<BlockPos> tile, TeleportSettingsComponent settings) implements CustomPayload {
    public static final Id<TeleportSettingsPayload> ID = new Id<>(Steveparty.id("teleport_settings"));
    public static final PacketCodec<RegistryByteBuf, TeleportSettingsPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.optional(BlockPos.PACKET_CODEC), TeleportSettingsPayload::tile,
            TeleportSettingsComponent.PACKET_CODEC, TeleportSettingsPayload::settings,
            TeleportSettingsPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void handle(ServerPlayerEntity player) {
        if (TeleportCartridgeItem.applyFromMenu(player, tile.orElse(null), settings)) {
            player.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.3F, 1.4F);
        }
    }
}
