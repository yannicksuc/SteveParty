package fr.lordfinn.steveparty.blocks;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import static fr.lordfinn.steveparty.Steveparty.MOD_ID;

public class ModBlockEntities {
    public static final BlockEntityType<TileEntity> TILE_ENTITY = Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of (MOD_ID, "tile_entity"),
            FabricBlockEntityTypeBuilder.create(TileEntity::new, ModBlocks.TILE).build(null)
    );

    public static void initialize() {
    }
}