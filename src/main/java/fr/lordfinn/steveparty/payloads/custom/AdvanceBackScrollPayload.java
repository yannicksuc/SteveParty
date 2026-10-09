package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.payloads.ServerboundPayload;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** Client → server: sneak + mouse wheel with a Move Forward / Back cartridge in the main hand changes its setting. */
public record AdvanceBackScrollPayload(int direction) implements ServerboundPayload {
    public static final Id<AdvanceBackScrollPayload> ID = new Id<>(Steveparty.id("advance_back_scroll"));
    public static final PacketCodec<RegistryByteBuf, AdvanceBackScrollPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, AdvanceBackScrollPayload::direction,
            AdvanceBackScrollPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Server side: the new setting, shown in the action bar with a click pitched by the number of spaces. */
    @Override
    public void handle(ServerPlayerEntity player) {
        ItemStack cartridge = player.getMainHandStack();
        if (!(cartridge.getItem() instanceof AdvanceBackCartridgeItem)) return;
        AdvanceBackCartridgeItem.scroll(cartridge, Integer.signum(direction));
        int steps = AdvanceBackCartridgeItem.steps(cartridge);
        player.sendMessage(AdvanceBackCartridgeItem.settingText(steps), true);
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.4F,
                (float) Math.pow(2, steps / 12.0));
    }
}
