package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenuHost;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/**
 * Client → server: in the cartridge menu open in screen {@code syncId}, module {@code module} takes {@code value}.
 * The server applies it to the cartridge that screen edits, after its checks (see {@link CartridgeMenus#apply}).
 */
public record CartridgeSettingPayload(int syncId, String module, int value) implements CustomPayload {
    public static final Id<CartridgeSettingPayload> ID = new Id<>(Steveparty.id("cartridge_setting"));
    public static final PacketCodec<RegistryByteBuf, CartridgeSettingPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, CartridgeSettingPayload::syncId,
            PacketCodecs.string(64), CartridgeSettingPayload::module,
            PacketCodecs.VAR_INT, CartridgeSettingPayload::value,
            CartridgeSettingPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void handle(ServerPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof CartridgeMenuHost host) || player.currentScreenHandler.syncId != syncId) return;
        CartridgeRef ref = host.editedCartridge(player);
        if (ref == null) return;
        if (CartridgeMenus.apply(player, ref, module, value)) {
            player.playSoundToPlayer(SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.3F, 1.4F);
        }
    }
}
