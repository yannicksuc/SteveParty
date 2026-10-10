package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.blocks.custom.*;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.MiniGamePipeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import fr.lordfinn.steveparty.blocks.switchable.SwitchedOffBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CheckPointBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.AdvancedTileBlockEntity;
import java.util.Arrays;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.Steveparty.MOD_ID;

public class ModBlockEntities {
    /**
     * The Advanced Tile's. It also accepts the plain Tile block: an Advanced Tile from a world saved before the tiles
     * were renamed loads as a Tile holding this block entity, and is turned back into an Advanced Tile (see
     * {@link fr.lordfinn.steveparty.blocks.custom.boardspaces.TileMigration}).
     */
    public static final BlockEntityType<AdvancedTileBlockEntity> ADVANCED_TILE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "advanced_tile"),
            FabricBlockEntityTypeBuilder.create(AdvancedTileBlockEntity::new, ModBlocks.ADVANCED_TILE, ModBlocks.TILE).build(null)
    );

    public static final BlockEntityType<SwitchedOffBlockEntity> SWITCHED_OFF_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "switched_off_block_entity"),
            FabricBlockEntityTypeBuilder.create(SwitchedOffBlockEntity::new, ModBlocks.SWITCHED_OFF_BLOCK).build(null)
    );

    public static final BlockEntityType<CheckPointBlockEntity> CHECK_POINT_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "check_point_entity"),
            FabricBlockEntityTypeBuilder.create(CheckPointBlockEntity::new, ModBlocks.CHECK_POINT).build(null)
    );

    public static final BlockEntityType<TileBlockEntity> TILE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "tile"),
            FabricBlockEntityTypeBuilder.create(TileBlockEntity::new, ModBlocks.TILE).build(null)
    );

    public static final BlockEntityType<BoardSpaceRedstoneRouterBlockEntity> BOARD_SPACE_REDSTONE_ROUTER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "board_space_redstone_router_entity"),
            FabricBlockEntityTypeBuilder.create(BoardSpaceRedstoneRouterBlockEntity::new, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER).build(null)
    );

    public static final BlockEntityType<PartyControllerEntity> PARTY_CONTROLLER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "party_controller_entity"),
            FabricBlockEntityTypeBuilder.create(PartyControllerEntity::new, ModBlocks.PARTY_CONTROLLER).build(null)
    );

    public static final BlockEntityType<MiniGameControllerBlockEntity> MINI_GAME_CONTROLLER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "mini_game_controller"),
            FabricBlockEntityTypeBuilder.create(MiniGameControllerBlockEntity::new, ModBlocks.MINI_GAME_CONTROLLER).build(null)
    );

    public static final BlockEntityType<StepControllerBlockEntity> STEP_CONTROLLER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "step_controller"),
            FabricBlockEntityTypeBuilder.create(StepControllerBlockEntity::new, ModBlocks.STEP_CONTROLLER).build(null)
    );
    public static final BlockEntityType<PartyBellBlockEntity> PARTY_BELL_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "party_bell"),
            FabricBlockEntityTypeBuilder.create(PartyBellBlockEntity::new, ModBlocks.PARTY_BELL).build(null)
    );
    /** Every travel pipe's (the cartridge slot of its mouths). */
    public static final BlockEntityType<PipeBlockEntity> PIPE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "pipe"),
            FabricBlockEntityTypeBuilder.create(PipeBlockEntity::new,
                    Arrays.stream(ModBlocks.PIPES).flatMap(Arrays::stream).toArray(Block[]::new)).build(null)
    );
    /** The mini-game pipe's: it shows the page it holds. */
    public static final BlockEntityType<MiniGamePipeBlockEntity> MINIGAME_PIPE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "minigame_pipe"),
            FabricBlockEntityTypeBuilder.create(MiniGamePipeBlockEntity::new,
                    ModBlocks.COPPER_MINIGAME_PIPE, ModBlocks.IRON_MINIGAME_PIPE, ModBlocks.GOLDEN_MINIGAME_PIPE).build(null)
    );
    public static final BlockEntityType<PodiumBlockEntity> PODIUM_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "podium"),
            FabricBlockEntityTypeBuilder.create(PodiumBlockEntity::new, ModBlocks.PODIUM, ModBlocks.GOLD_PODIUM, ModBlocks.SILVER_PODIUM, ModBlocks.BRONZE_PODIUM).build(null)
    );
    public static final BlockEntityType<PiggyBankBlockEntity> PIGGY_BANK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "piggy_bank"),
            FabricBlockEntityTypeBuilder.create(PiggyBankBlockEntity::new, ModBlocks.PIGGY_BANK).build(null)
    );
    public static final BlockEntityType<EaselSignBlockEntity> EASEL_SIGN_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "easel_sign"),
            FabricBlockEntityTypeBuilder.create(EaselSignBlockEntity::new, ModBlocks.SPRUCE_EASEL_SIGN, ModBlocks.JUNGLE_EASEL_SIGN, ModBlocks.OAK_EASEL_SIGN, ModBlocks.DARK_OAK_EASEL_SIGN, ModBlocks.CRIMSON_EASEL_SIGN, ModBlocks.WARPED_EASEL_SIGN, ModBlocks.BIRCH_EASEL_SIGN, ModBlocks.ACACIA_EASEL_SIGN, ModBlocks.MANGROVE_EASEL_SIGN, ModBlocks.CHERRY_EASEL_SIGN, ModBlocks.EASEL_SIGN).build(null)
    );
    /** Every other block taking stencils: panels, rock signs, plastic road signs, sprayed paint. */
    public static final BlockEntityType<StencilCanvasBlockEntity> STENCIL_CANVAS = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "stencil_canvas"),
            FabricBlockEntityTypeBuilder.create(StencilCanvasBlockEntity::new, ModBlocks.WOODEN_PANEL, ModBlocks.WOODEN_CUTOUT_PANEL,
                    ModBlocks.ROCK_SIGN, ModBlocks.PLASTIC_ROAD_SIGN, ModBlocks.STENCIL_PAINT).build(null)
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
    public static final BlockEntityType<GoalPoleBlockEntity> GOAL_POLE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "goal_pole_entity"),
            FabricBlockEntityTypeBuilder.create(GoalPoleBlockEntity::new, ModBlocks.GOAL_POLE).build(null)
    );
    public static final BlockEntityType<LootingBoxBlockEntity> LOOTING_BOX_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "looting_box_entity"),
            FabricBlockEntityTypeBuilder.create(LootingBoxBlockEntity::new, ModBlocks.LOOTING_BOX).build(null)
    );
    public static final BlockEntityType<VillagerBlockEntity> VILLAGER_BLOCK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "villager_block"),
            FabricBlockEntityTypeBuilder.create(VillagerBlockEntity::new, ModBlocks.VILLAGER_BLOCK).build(null)
    );
    public static final BlockEntityType<FrousseuxCandleHolderBlockEntity> FROUSSEUX_CANDLE_HOLDER = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "frousseux_candle_holder"),
            FabricBlockEntityTypeBuilder.create(FrousseuxCandleHolderBlockEntity::new,
                    ModBlocks.FROUSSEUX_CANDLE_HOLDER).build(null)
    );
    public static final BlockEntityType<GravityCoreBlockEntity> GRAVITY_CORE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "gravity_core"),
            FabricBlockEntityTypeBuilder.create(GravityCoreBlockEntity::new, ModBlocks.GRAVITY_CORE).build(null)
    );

    public static final BlockEntityType<DiceForgeBlockEntity> DICE_FORGE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "dice_forge_entity"),
            FabricBlockEntityTypeBuilder.create(DiceForgeBlockEntity::new, ModBlocks.DICE_FORGE).build(null)
    );

    public static final BlockEntityType<TelescopeBlockEntity> TELESCOPE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "telescope"),
            FabricBlockEntityTypeBuilder.create(TelescopeBlockEntity::new, ModBlocks.TELESCOPE).build(null)
    );

    /** A Spawn Marker's settings: when its mob shows, the space linked to it. */
    public static final BlockEntityType<SpawnMarkerBlockEntity> SPAWN_MARKER_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "spawn_marker"),
            FabricBlockEntityTypeBuilder.create(SpawnMarkerBlockEntity::new, ModBlocks.SPAWN_MARKER).build(null)
    );

    /** What a Magpie Nest holds: coins (its pot's when linked) and shiny things. */
    public static final BlockEntityType<MagpieNestBlockEntity> MAGPIE_NEST_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of(MOD_ID, "magpie_nest"),
            FabricBlockEntityTypeBuilder.create(MagpieNestBlockEntity::new, ModBlocks.MAGPIE_NEST).build(null)
    );

    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }
}