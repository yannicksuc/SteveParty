package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesColor;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.BlockStateVariant;
import net.minecraft.data.client.BlockStateVariantMap;
import net.minecraft.data.client.Models;
import net.minecraft.data.client.TextureMap;
import net.minecraft.data.client.VariantSettings;
import net.minecraft.data.client.VariantsBlockStateSupplier;
import net.minecraft.util.Identifier;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.TexturedModel;
import net.minecraft.data.family.BlockFamily;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.ModBlocks.COLORS;
import static fr.lordfinn.steveparty.blocks.ModBlocks.COLORS_WITH_DEFAULT;

public class StevepartyBlockProvider extends FabricModelProvider {
    public static final List<BlockFamily> POLISHED_TERRACOTTA_FAMILY_LIST = new ArrayList<>();
    public static final List<BlockFamily> POLISHED_BRICKS_TERRACOTTA_FAMILY_LIST = new ArrayList<>();
    public static final List<BlockFamily> POLISHED_CONCRETE_FAMILY_LIST = new ArrayList<>();
    public static final List<BlockFamily> POLISHED_BRICKS_CONCRETE_FAMILY_LIST = new ArrayList<>();

    public StevepartyBlockProvider(FabricDataOutput output) {
        super(output);
        for (int i = 0; i < COLORS_WITH_DEFAULT.length; i++) {
            BlockFamily family = new BlockFamily.Builder(ModBlocks.POLISHED_TERRACOTTA_BLOCKS[i])
                    .stairs(ModBlocks.POLISHED_TERRACOTTA_STAIRS[i])
                    .slab(ModBlocks.POLISHED_TERRACOTTA_SLABS[i])
                    .wall(ModBlocks.POLISHED_TERRACOTTA_WALLS[i])
                    .build();
            POLISHED_TERRACOTTA_FAMILY_LIST.add(family);

            family = new BlockFamily.Builder(ModBlocks.POLISHED_TERRACOTTA_BRICKS_BLOCKS[i])
                    .stairs(ModBlocks.POLISHED_TERRACOTTA_BRICKS_STAIRS[i])
                    .slab(ModBlocks.POLISHED_TERRACOTTA_BRICKS_SLABS[i])
                    .wall(ModBlocks.POLISHED_TERRACOTTA_BRICKS_WALLS[i])
                    .build();
            POLISHED_BRICKS_TERRACOTTA_FAMILY_LIST.add(family);
        }
        for (int i = 0; i < COLORS.length; i++) {
            POLISHED_CONCRETE_FAMILY_LIST.add(new BlockFamily.Builder(ModBlocks.POLISHED_CONCRETE_BLOCKS[i])
                    .stairs(ModBlocks.POLISHED_CONCRETE_STAIRS[i])
                    .slab(ModBlocks.POLISHED_CONCRETE_SLABS[i])
                    .wall(ModBlocks.POLISHED_CONCRETE_WALLS[i])
                    .build());
            POLISHED_BRICKS_CONCRETE_FAMILY_LIST.add(new BlockFamily.Builder(ModBlocks.POLISHED_CONCRETE_BRICKS_BLOCKS[i])
                    .stairs(ModBlocks.POLISHED_CONCRETE_BRICKS_STAIRS[i])
                    .slab(ModBlocks.POLISHED_CONCRETE_BRICKS_SLABS[i])
                    .wall(ModBlocks.POLISHED_CONCRETE_BRICKS_WALLS[i])
                    .build());
        }
    }
    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {
        for (int i = 0; i < COLORS_WITH_DEFAULT.length; i++) {
            blockStateModelGenerator
                    .registerCubeAllModelTexturePool(ModBlocks.POLISHED_TERRACOTTA_BLOCKS[i])
                    .family(POLISHED_TERRACOTTA_FAMILY_LIST.get(i));
            blockStateModelGenerator
                    .registerCubeAllModelTexturePool(ModBlocks.POLISHED_TERRACOTTA_BRICKS_BLOCKS[i])
                    .family(POLISHED_BRICKS_TERRACOTTA_FAMILY_LIST.get(i));
        }
        for (int i = 0; i < COLORS.length; i++) {
            blockStateModelGenerator
                    .registerCubeAllModelTexturePool(ModBlocks.POLISHED_CONCRETE_BLOCKS[i])
                    .family(POLISHED_CONCRETE_FAMILY_LIST.get(i));
            blockStateModelGenerator
                    .registerCubeAllModelTexturePool(ModBlocks.POLISHED_CONCRETE_BRICKS_BLOCKS[i])
                    .family(POLISHED_BRICKS_CONCRETE_FAMILY_LIST.get(i));
        }
        blockStateModelGenerator.registerSingleton(ModBlocks.VILLAGER_BLOCK, TexturedModel.CUBE_BOTTOM_TOP);
        for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) registerPolishedTiles(blockStateModelGenerator, tiles);
    }

    /**
     * Polished tiles: one plain cube model per pair of colours, block/polished_&lt;material&gt;_tiles/&lt;a&gt;_&lt;b&gt;, over
     * the texture of the same name (the art sources).
     */
    private static void registerPolishedTiles(BlockStateModelGenerator generator, PolishedTilesBlock tiles) {
        generator.blockStateCollector.accept(VariantsBlockStateSupplier.create(tiles)
                .coordinate(BlockStateVariantMap.create(tiles.colorA(), tiles.colorB()).register((a, b) -> {
                    Identifier id = polishedTilesModel(tiles, a, b);
                    Models.CUBE_ALL.upload(id, TextureMap.all(id), generator.modelCollector);
                    return BlockStateVariant.create().put(VariantSettings.MODEL, id);
                })));
        // The item draws the model of its colours (PolishedTilesItemModel): this one only gives the way a block is held
        PolishedTilesColor first = tiles.colors().getFirst();
        generator.registerParentedItemModel(tiles, polishedTilesModel(tiles, first, first));
    }

    private static Identifier polishedTilesModel(PolishedTilesBlock tiles, PolishedTilesColor a, PolishedTilesColor b) {
        return Steveparty.id("block/polished_" + tiles.material() + "_tiles/" + a.asString() + "_" + b.asString());
    }

    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {
    }
}
