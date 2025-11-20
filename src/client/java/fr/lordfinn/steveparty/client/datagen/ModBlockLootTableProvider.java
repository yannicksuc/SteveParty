package fr.lordfinn.steveparty.client.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

import static fr.lordfinn.steveparty.blocks.ModBlocks.SWITCHER_BLOCKS;

public class ModBlockLootTableProvider extends FabricBlockLootTableProvider {

    protected ModBlockLootTableProvider(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generate() {
        // Make blocks drop themselves
        addDrop(ModBlocks.HOP_SWITCH);
        for (Block switcher : SWITCHER_BLOCKS)
            addDrop(switcher);
        addDrop(ModBlocks.GOAL_POLE_BASE);
        addDrop(ModBlocks.GOAL_POLE);
        addDrop(ModBlocks.LOOTING_BOX);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BLOCKS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_BLOCKS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_SLABS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_STAIRS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_WALLS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_SLABS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_STAIRS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_WALLS)
            addDrop(block);
    }
}
