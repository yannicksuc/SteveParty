package fr.lordfinn.steveparty.client.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock;
import net.minecraft.block.Block;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.CopyStateLootFunction;
import net.minecraft.loot.function.SetComponentsLootFunction;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlock;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;


public class ModBlockLootTableProvider extends FabricBlockLootTableProvider {

    protected ModBlockLootTableProvider(FabricDataOutput dataOutput, CompletableFuture<RegistryWrapper.WrapperLookup> registryLookup) {
        super(dataOutput, registryLookup);
    }

    @Override
    public void generate() {
        // Make blocks drop themselves
        addDrop(ModBlocks.HOP_SWITCH);
        for (Block plasticBlock : ModBlocks.PLASTIC_BLOCKS)
            addDrop(plasticBlock);
        for (Block stud : ModBlocks.PLASTIC_STUDS)
            addDrop(stud);
        for (Block fence : ModBlocks.PLASTIC_FENCES)
            addDrop(fence);
        for (int i = 0; i < ModBlocks.COLORS.length; i++) {
            addDrop(ModBlocks.PLASTIC_SLABS[i], slabDrops(ModBlocks.PLASTIC_SLABS[i]));
            addDrop(ModBlocks.PLASTIC_STAIRS[i]);
            addDrop(ModBlocks.PLASTIC_WALLS[i]);
        }
        addDrop(ModBlocks.GOAL_POLE_BASE);
        // A broken segment comes back without its flag (the flag drops beside it): see GoalPoleBlock#isFlagless
        addDrop(ModBlocks.GOAL_POLE, LootTable.builder().pool(addSurvivesExplosionCondition(ModBlocks.GOAL_POLE,
                LootPool.builder().rolls(ConstantLootNumberProvider.create(1)).with(ItemEntry.builder(ModBlocks.GOAL_POLE)
                        .apply(SetComponentsLootFunction.builder(DataComponentTypes.BLOCK_STATE,
                                BlockStateComponent.DEFAULT.with(GoalPoleBlock.FLAG, false)))))));
        addDrop(ModBlocks.LOOTING_BOX);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BLOCKS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_BLOCKS)
            addDrop(block);
        // Slabs: a double slab must drop 2 items
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_SLABS)
            addDrop(block, slabDrops(block));
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_STAIRS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_WALLS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_SLABS)
            addDrop(block, slabDrops(block));
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_STAIRS)
            addDrop(block);
        for (Block block : ModBlocks.POLISHED_TERRACOTTA_BRICKS_WALLS)
            addDrop(block);
        for (Block[] blocks : new Block[][]{
                ModBlocks.POLISHED_CONCRETE_BLOCKS, ModBlocks.POLISHED_CONCRETE_BRICKS_BLOCKS,
                ModBlocks.POLISHED_CONCRETE_STAIRS, ModBlocks.POLISHED_CONCRETE_WALLS,
                ModBlocks.POLISHED_CONCRETE_BRICKS_STAIRS, ModBlocks.POLISHED_CONCRETE_BRICKS_WALLS})
            for (Block block : blocks)
                addDrop(block);
        for (Block[] slabs : new Block[][]{ModBlocks.POLISHED_CONCRETE_SLABS, ModBlocks.POLISHED_CONCRETE_BRICKS_SLABS})
            for (Block block : slabs)
                addDrop(block, slabDrops(block));
        // Polished tiles: the item keeps the two colours of the block (its block_state component)
        for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES)
            addDrop(tiles, LootTable.builder().pool(addSurvivesExplosionCondition(tiles, LootPool.builder()
                    .rolls(ConstantLootNumberProvider.create(1))
                    .with(ItemEntry.builder(tiles).apply(CopyStateLootFunction.builder(tiles)
                            .addProperty(tiles.colorA()).addProperty(tiles.colorB()))))));
    }
}
