package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.block.Block;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;

import java.util.concurrent.CompletableFuture;

public class StevepartyReferenceItemTagProvider  extends FabricTagProvider<Item> {

    public static final TagKey<Item> DICE_FACES_TAG =
            TagKey.of(RegistryKeys.ITEM, Steveparty.id("dice_faces"));
    /** The star fragments of the 16 colours (the Shopkeeper Key takes any of them). */
    public static final TagKey<Item> STAR_FRAGMENTS_TAG =
            TagKey.of(RegistryKeys.ITEM, Steveparty.id("star_fragments"));

    /** Every cartridge (the C of the Tile and Advanced Tile recipes). */
    public static final TagKey<Item> CARTRIDGES_TAG =
            TagKey.of(RegistryKeys.ITEM, Steveparty.id("cartridges"));

    /** Every pressure plate, wooden or not (the plate of the Tile recipe): vanilla has no item tag for them all. */
    public static final TagKey<Item> PRESSURE_PLATES_TAG =
            TagKey.of(RegistryKeys.ITEM, Steveparty.id("pressure_plates"));

    public StevepartyReferenceItemTagProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, RegistryKeys.ITEM, registriesFuture);
    }

    @Override
    protected void configure(RegistryWrapper.WrapperLookup wrapperLookup) {
        for (Item diceFace : ModItems.DICE_FACES)
            getOrCreateTagBuilder(DICE_FACES_TAG).add(diceFace);
        for (Item item : Registries.ITEM) {
            if (item instanceof CartridgeItem
                    && Steveparty.MOD_ID.equals(Registries.ITEM.getId(item).getNamespace()))
                getOrCreateTagBuilder(CARTRIDGES_TAG).add(item);
        }
        for (Item fragment : ModItems.STAR_FRAGMENTS)
            getOrCreateTagBuilder(STAR_FRAGMENTS_TAG).add(fragment);
        getOrCreateTagBuilder(PRESSURE_PLATES_TAG).forceAddTag(ItemTags.WOODEN_PRESSURE_PLATES)
                .add(Items.STONE_PRESSURE_PLATE, Items.POLISHED_BLACKSTONE_PRESSURE_PLATE,
                        Items.LIGHT_WEIGHTED_PRESSURE_PLATE, Items.HEAVY_WEIGHTED_PRESSURE_PLATE);
        for (Block fence : ModBlocks.PLASTIC_FENCES)
            getOrCreateTagBuilder(ItemTags.FENCES).add(fence.asItem());
        for (int i = 0; i < ModBlocks.COLORS.length; i++) {
            getOrCreateTagBuilder(ItemTags.SLABS).add(ModBlocks.PLASTIC_SLABS[i].asItem());
            getOrCreateTagBuilder(ItemTags.STAIRS).add(ModBlocks.PLASTIC_STAIRS[i].asItem());
            getOrCreateTagBuilder(ItemTags.WALLS).add(ModBlocks.PLASTIC_WALLS[i].asItem());
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
