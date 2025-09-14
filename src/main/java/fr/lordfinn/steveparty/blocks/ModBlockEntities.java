package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.blocks.custom.*;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.Steveparty.MOD_ID;

public class ModBlockEntities {
    public static final BlockEntityType<BoardSpaceBlockEntity> TILE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "tile_entity"),
            FabricBlockEntityTypeBuilder.create(BoardSpaceBlockEntity::new, ModBlocks.TILE).build(null)
    );

    public static final BlockEntityType<BoardSpaceBlockEntity> CHECK_POINT_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "check_point_entity"),
            FabricBlockEntityTypeBuilder.create(BoardSpaceBlockEntity::new, ModBlocks.CHECK_POINT).build(null)
    );

    public static final BlockEntityType<BoardSpaceRedstoneRouterBlockEntity> BOARD_SPACE_REDSTONE_ROUTER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "board_space_redstone_router_entity"),
            FabricBlockEntityTypeBuilder.create(BoardSpaceRedstoneRouterBlockEntity::new, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER).build(null)
    );

    public static final BlockEntityType<TeleportationPadBlockEntity> BIG_BOOK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "big_book_entity"),
            FabricBlockEntityTypeBuilder.create(TeleportationPadBlockEntity::new, ModBlocks.TELEPORTATION_PAD).build(null)
    );

    public static final BlockEntityType<PartyControllerEntity> PARTY_CONTROLLER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "party_controller_entity"),
            FabricBlockEntityTypeBuilder.create(PartyControllerEntity::new, ModBlocks.PARTY_CONTROLLER).build(null)
    );

    public static final BlockEntityType<StepControllerBlockEntity> STEP_CONTROLLER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "step_controller"),
            FabricBlockEntityTypeBuilder.create(StepControllerBlockEntity::new, ModBlocks.STEP_CONTROLLER).build(null)
    );
    public static final BlockEntityType<TrafficSignBlockEntity> TRAFFIC_SIGN_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "traffic_sign"),
            FabricBlockEntityTypeBuilder.create(TrafficSignBlockEntity::new, ModBlocks.SPRUCE_TRAFFIC_SIGN, ModBlocks.JUNGLE_TRAFFIC_SIGN, ModBlocks.OAK_TRAFFIC_SIGN, ModBlocks.DARK_OAK_TRAFFIC_SIGN, ModBlocks.CRIMSON_TRAFFIC_SIGN, ModBlocks.WARPED_TRAFFIC_SIGN, ModBlocks.BIRCH_TRAFFIC_SIGN, ModBlocks.ACACIA_TRAFFIC_SIGN, ModBlocks.MANGROVE_TRAFFIC_SIGN, ModBlocks.CHERRY_TRAFFIC_SIGN).build(null)
    );
    public static final BlockEntityType<StencilMakerBlockEntity> STENCIL_MAKER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "stencil_maker"),
            FabricBlockEntityTypeBuilder.create(StencilMakerBlockEntity::new, ModBlocks.STENCIL_MAKER).build(null)
    );
    public static final BlockEntityType<TradingStallBlockEntity> TRADING_STALL = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "trading_stall"),
            FabricBlockEntityTypeBuilder.create(TradingStallBlockEntity::new, ModBlocks.TRADING_STALL).build(null)
    );
    public static final BlockEntityType<CashRegisterBlockEntity> CASH_REGISTER = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "cash_register"),
            FabricBlockEntityTypeBuilder.create(CashRegisterBlockEntity::new, ModBlocks.CASH_REGISTER).build(null)
    );
    public static final BlockEntityType<HopSwitchBlockEntity> HOP_SWITCH_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "hop_switch_entity"),
            FabricBlockEntityTypeBuilder.create(HopSwitchBlockEntity::new, ModBlocks.HOP_SWITCH).build(null)
    );

    public static final BlockEntityType<GoalPoleBaseBlockEntity> GOAL_POLE_BASE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "goal_pole_base_entity"),
            FabricBlockEntityTypeBuilder.create(GoalPoleBaseBlockEntity::new, ModBlocks.GOAL_POLE_BASE).build(null)
    );
    public static final BlockEntityType<?> GOAL_POLE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "goal_pole_entity"),
            FabricBlockEntityTypeBuilder.create(GoalPoleBlockEntity::new, ModBlocks.GOAL_POLE).build(null)
    );
    public static final BlockEntityType<LootingBoxBlockEntity> LOOTING_BOX_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "looting_box_entity"),
            FabricBlockEntityTypeBuilder.create(LootingBoxBlockEntity::new, ModBlocks.LOOTING_BOX).build(null)
    );

    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }
}