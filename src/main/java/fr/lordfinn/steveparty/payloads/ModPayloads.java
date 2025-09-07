package fr.lordfinn.steveparty.payloads;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.custom.*;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import static fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeComeBookItem.handleHereWeComeBookPayload;
import static fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeGoBookItem.handleHereWeGoBookPayload;

public class ModPayloads {
    public static final Identifier ARROW_PARTICLES_PAYLOAD = Steveparty.id("arrow-particles");
    public static final Identifier TOKENS_PAYLOAD = Steveparty.id("tokens-payload");
    public static final Identifier ENCHANTED_CIRCULAR_PAYLOAD = Steveparty.id("enchanted-circular-particles-payload");
    public static final Identifier UPDATE_COLORED_TILE_PAYLOAD = Steveparty.id("update-colored-tile-payload");
    public static final Identifier PARTY_DATA_PAYLOAD = Steveparty.id("party-data");
    public static final Identifier SELECTION_STATE_PAYLOAD = Steveparty.id("selection-state-payload");
    public static final Identifier BLOCK_POSES_MAP_PAYLOAD = Steveparty.id("block-poses-map-payload");
    public static final Identifier HERE_WE_GO_BOOK_PAYLOAD = Steveparty.id("here-we-go-book-payload");
    public static final Identifier HERE_WE_COME_BOOK_PAYLOAD = Steveparty.id("here-we-come-book-payload");
    public static final Identifier SAVE_STENCIL_PAYLOAD = Steveparty.id("save_stencil");
    public static final Identifier HOP_SWITCH_PAYLOAD = Steveparty.id("hop-switch-payload");
    public static final Identifier GOAL_POLE_BASE_PAYLOAD = Steveparty.id("goal-pole-base-payload");


    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ArrowParticlesPayload.ID, ArrowParticlesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TokenPayload.ID, TokenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EnchantedCircularParticlePayload.ID, EnchantedCircularParticlePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateColoredTilePayload.ID, UpdateColoredTilePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartyDataPayload.ID, PartyDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionStatePayload.ID, SelectionStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectionStatePayload.ID, SelectionStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(BlockPosesMapPayload.ID, BlockPosesMapPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HereWeGoBookPayload.ID, HereWeGoBookPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HereWeComeBookPayload.ID, HereWeComeBookPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveStencilPayload.ID, SaveStencilPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GoalPoleBasePayload.ID, GoalPoleBasePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(HereWeGoBookPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> handleHereWeGoBookPayload(player, payload.state()));
        });

        ServerPlayNetworking.registerGlobalReceiver(HereWeComeBookPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> handleHereWeComeBookPayload(player, payload.teleportingTargets()));
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveStencilPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> {
                BlockPos pos = payload.pos();
                byte[] shape = payload.shape();
                if (player.getWorld().getBlockEntity(pos) instanceof StencilMakerBlockEntity blockEntity) {
                    StencilItem.setShape(shape, blockEntity.getStencil());
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(GoalPoleBasePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();

            // Schedule on server thread
            player.server.execute(() -> {
                BlockPos pos = payload.pos();

                // Check the BlockEntity type
                if (player.getWorld().getBlockEntity(pos) instanceof GoalPoleBaseBlockEntity blockEntity) {
                    // Update the BlockEntity fields
                    blockEntity.setSelector(payload.selector());
                    blockEntity.setGoal(payload.goal());
                }
            });
        });
    }

}
