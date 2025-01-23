package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.BigBookEntity;
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

    public static final BlockEntityType<BigBookEntity> BIG_BOOK_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "big_book_entity"),
            FabricBlockEntityTypeBuilder.create(BigBookEntity::new, ModBlocks.BIG_BOOK).build(null)
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

    @SuppressWarnings("EmptyMethod")
    public static void initialize() {
    }
}