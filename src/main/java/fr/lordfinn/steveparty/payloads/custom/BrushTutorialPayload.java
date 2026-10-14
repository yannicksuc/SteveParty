package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.BrushTutorialSteps;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * S2C: the player just did a step of the Tile Linker Brush's tutorial ({@link BrushTutorialSteps.Step}, its ordinal):
 * the client's toasts count it.
 */
public record BrushTutorialPayload(int step) implements CustomPayload {
    public static final CustomPayload.Id<BrushTutorialPayload> ID = new CustomPayload.Id<>(Steveparty.id("brush_tutorial"));
    public static final PacketCodec<ByteBuf, BrushTutorialPayload> CODEC =
            PacketCodecs.VAR_INT.xmap(BrushTutorialPayload::new, BrushTutorialPayload::step);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
