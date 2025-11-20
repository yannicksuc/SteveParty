package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.block.Block;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.TexturedModel;
import net.minecraft.data.family.BlockFamily;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.ModBlocks.COLORS_WITH_DEFAULT;

public class StevepartyBlockProvider extends FabricModelProvider {
    public static final List<BlockFamily> POLISHED_TERRACOTTA_FAMILY_LIST = new ArrayList<>();
    public static final List<BlockFamily> POLISHED_BRICKS_TERRACOTTA_FAMILY_LIST = new ArrayList<>();

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
        blockStateModelGenerator.registerSingleton(ModBlocks.VILLAGER_BLOCK, TexturedModel.CUBE_BOTTOM_TOP);
    }

    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {
    }
}
