package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilMakerScreenHandler;
import fr.lordfinn.steveparty.stencil.StencilLibrary;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Client → server, from the stencil maker editor: take the stencil out, or save / remove / (un)favourite a pattern
 * in the player's stencil library.
 */
public record StencilMakerActionPayload(Action action, byte[] shape) implements CustomPayload {
    public static final Id<StencilMakerActionPayload> ID = new Id<>(Steveparty.id("stencil_maker_action"));

    public enum Action { TAKE_OUT, SAVE, DELETE, FAVORITE }

    public static final PacketCodec<RegistryByteBuf, StencilMakerActionPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.indexed(i -> Action.values()[Math.clamp(i, 0, Action.values().length - 1)], Action::ordinal), StencilMakerActionPayload::action,
            PacketCodecs.byteArray(StencilShape.SIZE), StencilMakerActionPayload::shape,
            StencilMakerActionPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Server side: only while the player has a stencil maker open and in reach. */
    public void handle(ServerPlayerEntity player) {
        if (!(player.currentScreenHandler instanceof StencilMakerScreenHandler handler)) return;
        StencilMakerBlockEntity maker = handler.getBlockEntity();
        if (maker == null || !ScreenHandlerChecks.isInReach(player, maker.getPos())) return;
        if (action == Action.TAKE_OUT) {
            maker.takeOutStencil(player);
            player.closeHandledScreen();
            return;
        }
        if (!StencilShape.isValid(shape)) return;
        StencilLibrary library = StencilLibrary.of(player);
        StencilLibrary changed = switch (action) {
            case SAVE -> library.with(shape);
            case DELETE -> library.without(shape);
            case FAVORITE -> library.toggleFavorite(shape);
            case TAKE_OUT -> library;
        };
        if (changed != library) StencilLibrary.set(player, changed);
    }
}
