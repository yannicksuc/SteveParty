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


public class ModPayloads {
    public static final Identifier ARROW_PARTICLES_PAYLOAD = Steveparty.id("arrow-particles");
    public static final Identifier ENCHANTED_CIRCULAR_PAYLOAD = Steveparty.id("enchanted-circular-particles-payload");
    public static final Identifier UPDATE_COLORED_TILE_PAYLOAD = Steveparty.id("update-colored-tile-payload");
    public static final Identifier PARTY_DATA_PAYLOAD = Steveparty.id("party-data");
    public static final Identifier PARTY_LIVE_PAYLOAD = Steveparty.id("party-live");
    public static final Identifier PARTY_DASHBOARD_PAYLOAD = Steveparty.id("party-dashboard");
    public static final Identifier SAVE_STENCIL_PAYLOAD = Steveparty.id("save_stencil");
    public static final Identifier GOAL_POLE_BASE_PAYLOAD = Steveparty.id("goal-pole-base-payload");
    public static final Identifier GOAL_POLE_PAYLOAD = Steveparty.id("goal-pole-payload");
    public static final Identifier FLOATING_TEXT_PAYLOAD = Steveparty.id("floating-text-payload");
    public static final Identifier CARTRIDGE_SLOT_SCROLL_PAYLOAD = Steveparty.id("cartridge-slot-scroll-payload");
    public static final Identifier SWITCHABLE_BLOCKS_PAYLOAD = Steveparty.id("switchable-blocks-payload");
    public static final Identifier VILLAGER_BLOCK_PUNCH_PAYLOAD = Steveparty.id("villager-block-punch");

    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(ArrowParticlesPayload.ID, ArrowParticlesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EnchantedCircularParticlePayload.ID, EnchantedCircularParticlePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(UpdateColoredTilePayload.ID, UpdateColoredTilePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartyDataPayload.ID, PartyDataPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartyLivePayload.ID, PartyLivePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(PartyDashboardPayload.ID, PartyDashboardPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SaveStencilPayload.ID, SaveStencilPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ToolWheelPayload.ID, ToolWheelPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ShopCartridgeScrollPayload.ID, ShopCartridgeScrollPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(PageZonePayload.ID, PageZonePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(AdvanceBackScrollPayload.ID, AdvanceBackScrollPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(StencilMakerActionPayload.ID, StencilMakerActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GoalPoleBasePayload.ID, GoalPoleBasePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(GoalPolePayload.ID, GoalPolePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FloatingTextPayload.ID, FloatingTextPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CartridgeSlotScrollPayload.ID, CartridgeSlotScrollPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SwitchableBlocksPayload.ID, SwitchableBlocksPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StarSpacesPayload.ID, StarSpacesPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(SquishAnimationPayload.ID, SquishAnimationPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StencilHammerStrikePayload.ID, StencilHammerStrikePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(OpenTokenSpellPayload.ID, OpenTokenSpellPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(TokenSpellPayload.ID, TokenSpellPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(VillagerBlockPunchPayload.ID, VillagerBlockPunchPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(CartridgeSettingPayload.ID, CartridgeSettingPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DicePromptPayload.ID, DicePromptPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(DicePromptAnswerPayload.ID, DicePromptAnswerPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DicePromptAnswerPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> payload.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(CartridgeSettingPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> payload.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(VillagerBlockPunchPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () ->
                    fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEvents.onPunchRequest(player, payload.pos()));
        });

        ServerPlayNetworking.registerGlobalReceiver(TokenSpellPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> payload.handle(player));
        });

        ServerPlayNetworking.registerGlobalReceiver(CartridgeSlotScrollPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> CartridgeSlotScrollPayload.handle(payload, player));
        });

        ServerPlayNetworking.registerGlobalReceiver(SaveStencilPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> {
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
            runInPacketOrder(player, () -> payload.handle(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ToolWheelPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> payload.handle(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(PageZonePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> payload.handle(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(ShopCartridgeScrollPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> payload.handle(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(AdvanceBackScrollPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> payload.handle(player));
        });
        ServerPlayNetworking.registerGlobalReceiver(GoalPoleBasePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();

            // In packet order (see runInPacketOrder): the screen closes right after this payload is sent
            runInPacketOrder(player, () -> applyGoalPoleBase(player, payload));
        });

        ServerPlayNetworking.registerGlobalReceiver(GoalPolePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            runInPacketOrder(player, () -> applyGoalPole(player, payload));
        });
    }

    /**
     * The settings of a goal pole base sent back by its screen. The screen opens for anyone holding a Wrench (to read
     * them), but only a player who may build there changes them: they reset the points of a party going on and pick
     * the players it counts.
     */
    public static void applyGoalPoleBase(ServerPlayerEntity player, GoalPoleBasePayload payload) {
        BlockPos pos = payload.pos();
        if (payload.settings() == null) return;
        // The goal pole base screen for this block must be open and the block in reach
        if (!(player.currentScreenHandler instanceof GoalPoleBaseScreenHandler handler)
                || !pos.equals(handler.getPos())
                || !ScreenHandlerChecks.isInReach(player, pos)
                || !ScreenHandlerChecks.canBuildAt(player, pos)) return;

        // Check the BlockEntity type
        if (player.getWorld().getBlockEntity(pos) instanceof GoalPoleBaseBlockEntity blockEntity) {
            // Each setting is checked by the base (known values, string lengths)
            blockEntity.applySettings(payload.settings());
        }
    }

    /** The goal of a goal pole sent back by its screen: like the base, read by anyone, changed by a builder. */
    public static void applyGoalPole(ServerPlayerEntity player, GoalPolePayload payload) {
        BlockPos pos = payload.pos();
        if (payload.comparator() == null) return;
        // The goal pole screen for this block must be open and the block in reach
        if (!(player.currentScreenHandler instanceof GoalPoleScreenHandler handler)
                || !pos.equals(handler.getPos())
                || !ScreenHandlerChecks.isInReach(player, pos)
                || !ScreenHandlerChecks.canBuildAt(player, pos)) return;
        if (player.getWorld().getBlockEntity(pos) instanceof GoalPoleBlockEntity blockEntity) {
            blockEntity.applyGoal(payload.comparator(), payload.value(), payload.perSegment());
            blockEntity.applyFlagSteps(payload.flagSteps());
            blockEntity.applyPerPlayer(payload.perPlayer());
        }
    }

    /**
     * Runs a C2S payload's action right away when Fabric already calls the receiver on the server thread (it does for
     * play payloads), so that it is handled in packet order. Deferring it with {@code server.execute} queued it behind
     * the packets received with it: a screen that sends its settings then closes had its close packet handled first,
     * and the "screen must be open" check then dropped the settings (the goal pole screens often did not save).
     */
    public static void runInPacketOrder(ServerPlayerEntity player, Runnable action) {
        if (player.server.isOnThread()) action.run();
        else player.server.execute(action);
    }
}
