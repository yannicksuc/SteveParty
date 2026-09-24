package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.registry.tag.ItemTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;

import java.util.concurrent.CompletableFuture;

public class StevepartyReferenceItemTagProvider  extends FabricTagProvider<Item> {

    public static final TagKey<Item> DICE_FACES_TAG =
            TagKey.of(RegistryKeys.ITEM, Steveparty.id("dice_faces"));

    public StevepartyReferenceItemTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, RegistryKeys.ITEM, registriesFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        for (Item diceFace : ModItems.DICE_FACES)
            getOrCreateTagBuilder(DICE_FACES_TAG).add(diceFace);
        for (net.minecraft.block.Block fence : fr.lordfinn.steveparty.blocks.ModBlocks.PLASTIC_FENCES)
            getOrCreateTagBuilder(net.minecraft.registry.tag.ItemTags.FENCES).add(fence.asItem());
        for (int i = 0; i < fr.lordfinn.steveparty.blocks.ModBlocks.COLORS.length; i++) {
            getOrCreateTagBuilder(net.minecraft.registry.tag.ItemTags.SLABS).add(fr.lordfinn.steveparty.blocks.ModBlocks.PLASTIC_SLABS[i].asItem());
            getOrCreateTagBuilder(net.minecraft.registry.tag.ItemTags.STAIRS).add(fr.lordfinn.steveparty.blocks.ModBlocks.PLASTIC_STAIRS[i].asItem());
            getOrCreateTagBuilder(net.minecraft.registry.tag.ItemTags.WALLS).add(fr.lordfinn.steveparty.blocks.ModBlocks.PLASTIC_WALLS[i].asItem());
        }
        // Polished concrete and terracotta shapes, like their block tags
        addShapes(ModBlocks.POLISHED_CONCRETE_SLABS, ModBlocks.POLISHED_CONCRETE_STAIRS, ModBlocks.POLISHED_CONCRETE_WALLS);
        addShapes(ModBlocks.POLISHED_CONCRETE_BRICKS_SLABS, ModBlocks.POLISHED_CONCRETE_BRICKS_STAIRS, ModBlocks.POLISHED_CONCRETE_BRICKS_WALLS);
        addShapes(ModBlocks.POLISHED_TERRACOTTA_SLABS, ModBlocks.POLISHED_TERRACOTTA_STAIRS, ModBlocks.POLISHED_TERRACOTTA_WALLS);
        addShapes(ModBlocks.POLISHED_TERRACOTTA_BRICKS_SLABS, ModBlocks.POLISHED_TERRACOTTA_BRICKS_STAIRS, ModBlocks.POLISHED_TERRACOTTA_BRICKS_WALLS);
    }

    private void addShapes(Block[] slabs, Block[] stairs, Block[] walls) {
        for (Block slab : slabs) getOrCreateTagBuilder(ItemTags.SLABS).add(slab.asItem());
        for (Block stair : stairs) getOrCreateTagBuilder(ItemTags.STAIRS).add(stair.asItem());
        for (Block wall : walls) getOrCreateTagBuilder(ItemTags.WALLS).add(wall.asItem());
    }
}
