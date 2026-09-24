package fr.lordfinn.steveparty.payloads;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.custom.*;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleBaseScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.GoalPoleScreenHandler;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilMakerScreenHandler;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.CustomPayload;
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
    public static final Identifier HERE_WE_GO_BOOK_PAYLOAD = Steveparty.id("here-we-go-book-payload");
    public static final Identifier HERE_WE_COME_BOOK_PAYLOAD = Steveparty.id("here-we-come-book-payload");
    public static final Identifier SAVE_STENCIL_PAYLOAD = Steveparty.id("save_stencil");
    public static final Identifier GOAL_POLE_BASE_PAYLOAD = Steveparty.id("goal-pole-base-payload");
    public static final Identifier GOAL_POLE_PAYLOAD = Steveparty.id("goal-pole-payload");
    public static final Identifier FLOATING_TEXT_PAYLOAD = Steveparty.id("floating-text-payload");
    public static final Identifier CARTRIDGE_SLOT_SCROLL_PAYLOAD = Steveparty.id("cartridge-slot-scroll-payload");
    public static final Identifier SWITCHABLE_BLOCKS_PAYLOAD = Steveparty.id("switchable-blocks-payload");
    /** Max length accepted for the goal pole base selector / goal strings. */
    private static final int MAX_GOAL_POLE_STRING_LENGTH = 256;

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ArrowParticlesPayload.ID, ArrowParticlesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(TokenPayload.ID, TokenPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EnchantedCircularParticlePayload.ID, EnchantedCircularParticlePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateColoredTilePayload.ID, UpdateColoredTilePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartyDataPayload.ID, PartyDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SelectionStatePayload.ID, SelectionStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectionStatePayload.ID, SelectionStatePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HereWeGoBookPayload.ID, HereWeGoBookPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(HereWeComeBookPayload.ID, HereWeComeBookPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveStencilPayload.ID, SaveStencilPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StencilGunScrollPayload.ID, StencilGunScrollPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StencilMakerActionPayload.ID, StencilMakerActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GoalPoleBasePayload.ID, GoalPoleBasePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GoalPolePayload.ID, GoalPolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FloatingTextPayload.ID, FloatingTextPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CartridgeSlotScrollPayload.ID, CartridgeSlotScrollPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SwitchableBlocksPayload.ID, SwitchableBlocksPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SquishAnimationPayload.ID, SquishAnimationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenTokenSpellPayload.ID, OpenTokenSpellPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TokenSpellPayload.ID, TokenSpellPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TokenSpellPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> payload.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(CartridgeSlotScrollPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> CartridgeSlotScrollPayload.handle(payload, player));
        });

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
                if (shape == null || shape.length != SaveStencilPayload.SHAPE_SIZE) return;
                // The stencil maker screen for this block must be open and the block in reach
                if (!(player.currentScreenHandler instanceof StencilMakerScreenHandler handler)
                        || handler.getBlockEntity() == null
                        || !pos.equals(handler.getBlockEntity().getPos())
                        || !ScreenHandlerChecks.isInReach(player, pos)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof StencilMakerBlockEntity blockEntity
                        && blockEntity == handler.getBlockEntity()) {
                    ItemStack stencil = blockEntity.getStencil();
                    if (stencil.isEmpty() || !(stencil.getItem() instanceof StencilItem)) return;
                    blockEntity.setStencilShape(shape);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(StencilMakerActionPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> payload.handle(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(StencilGunScrollPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> payload.handle(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(GoalPoleBasePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();

            // Schedule on server thread
            player.server.execute(() -> {
                BlockPos pos = payload.pos();
                if (payload.selector() == null || payload.goal() == null
                        || payload.selector().length() > MAX_GOAL_POLE_STRING_LENGTH
                        || payload.goal().length() > MAX_GOAL_POLE_STRING_LENGTH) return;
                // The goal pole base screen for this block must be open and the block in reach
                if (!(player.currentScreenHandler instanceof GoalPoleBaseScreenHandler handler)
                        || !pos.equals(handler.getPos())
                        || !ScreenHandlerChecks.isInReach(player, pos)) return;

                // Check the BlockEntity type
                if (player.getWorld().getBlockEntity(pos) instanceof GoalPoleBaseBlockEntity blockEntity) {
                    // Update the BlockEntity fields
                    blockEntity.update(payload.selector(), payload.goal());
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(GoalPolePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            player.server.execute(() -> {
                BlockPos pos = payload.pos();
                if (payload.comparator() == null) return;
                // The goal pole screen for this block must be open and the block in reach
                if (!(player.currentScreenHandler instanceof GoalPoleScreenHandler handler)
                        || !pos.equals(handler.getPos())
                        || !ScreenHandlerChecks.isInReach(player, pos)) return;
                if (player.getWorld().getBlockEntity(pos) instanceof GoalPoleBlockEntity blockEntity) {
                    blockEntity.update(payload.comparator(), payload.value());
                }
            });
        });
    }

}
