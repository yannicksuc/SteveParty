package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.block.Block;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.RecipeGenerator;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.data.family.BlockFamily;
import net.minecraft.item.DyeItem;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.util.DyeColor;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.util.Identifier;
import java.util.List;
import net.minecraft.registry.RegistryWrapper;
import java.util.concurrent.CompletableFuture;

public class StevepartyRecipeProvider extends FabricRecipeProvider {
    public StevepartyRecipeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected RecipeGenerator getRecipeGenerator(RegistryWrapper.WrapperLookup wrapperLookup, RecipeExporter recipeExporter) {
        return new RecipeGenerator(wrapperLookup, recipeExporter) {
            @Override
            public void generate() {
                //TagKey<Item> diceTag = StevepartyReferenceItemTagProvider.DICE_FACES_TAG;
                List<Item> allDice = ModItems.DICE_FACES;
                for (Item dice : allDice) {
                    if (dice == ModItems.DICE_FACES.get(0))
                        continue;
                    offerStonecuttingRecipe(
                            RecipeCategory.MISC,
                            dice,
                            ModItems.DICE_FACES.get(0)
                    );
                    offerStonecuttingRecipe(
                            RecipeCategory.MISC,
                            ModItems.DICE_FACES.get(0),
                            dice
                    );
                }
                createShaped(RecipeCategory.MISC, ModItems.DICE_FACES.get(0), 4) // output 4 blank dice faces
                        .pattern("IQ")
                        .pattern("QI")
                        .input('I', Items.IRON_INGOT)
                        .input('Q', Items.QUARTZ)
                        .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT))
                        .criterion(hasItem(Items.QUARTZ), conditionsFromItem(Items.QUARTZ))
                        .offerTo(recipeExporter, "blank_dice_face_from_crafting");

                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.BLACK_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.BLACK_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.PURPLE_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.PURPLE_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.RED_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.RED_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.YELLOW_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.YELLOW_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.GREEN_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.GREEN_STAR_FRAGMENTS_BLOCK
                );
                offerReversibleCompactingRecipes(
                        RecipeCategory.MISC,
                        ModItems.BLUE_STAR_FRAGMENT,
                        RecipeCategory.BUILDING_BLOCKS,
                        ModBlocks.BLUE_STAR_FRAGMENTS_BLOCK
                );
                createShapeless(RecipeCategory.MISC, ModItems.POWER_STAR, 1)
                        .input(ModItems.BLUE_STAR_FRAGMENT)
                        .input(ModItems.GREEN_STAR_FRAGMENT)
                        .input(ModItems.YELLOW_STAR_FRAGMENT)
                        .input(ModItems.RED_STAR_FRAGMENT)
                        .input(ModItems.PURPLE_STAR_FRAGMENT)
                        .criterion(hasItem(ModItems.BLUE_STAR_FRAGMENT), conditionsFromItem(ModItems.BLUE_STAR_FRAGMENT))
                        .criterion(hasItem(ModItems.GREEN_STAR_FRAGMENT), conditionsFromItem(ModItems.GREEN_STAR_FRAGMENT))
                        .criterion(hasItem(ModItems.YELLOW_STAR_FRAGMENT), conditionsFromItem(ModItems.YELLOW_STAR_FRAGMENT))
                        .criterion(hasItem(ModItems.RED_STAR_FRAGMENT), conditionsFromItem(ModItems.RED_STAR_FRAGMENT))
                        .criterion(hasItem(ModItems.PURPLE_STAR_FRAGMENT), conditionsFromItem(ModItems.PURPLE_STAR_FRAGMENT))
                        .offerTo(recipeExporter, "power_star_from_fragments");
                createShapeless(RecipeCategory.MISC, ModItems.POWER_STAR, 1)
                        .input(ModItems.BLACK_STAR_FRAGMENT, 5)
                        .criterion(hasItem(ModItems.BLACK_STAR_FRAGMENT), conditionsFromItem(ModItems.BLACK_STAR_FRAGMENT))
                        .offerTo(recipeExporter, "power_star_from_black_fragments");

                generatePolishedConcrete();
                generatePlasticBlocks();
            }

            // Plastic like the real thing: sugar cane (green polyethylene) is turned into pellets in a furnace,
            // then pellets are moulded with a dye and an amethyst shard.
            private void generatePlasticBlocks() {
                offerSmelting(List.of(Items.SUGAR_CANE), RecipeCategory.MISC, ModItems.PLASTIC_PELLETS, 0.1f, 200, "plastic_pellets");
                Ingredient anyPlasticBlock = Ingredient.ofItems(ModBlocks.PLASTIC_BLOCKS);
                for (int i = 0; i < ModBlocks.COLORS.length; i++) {
                    Block plasticBlock = ModBlocks.PLASTIC_BLOCKS[i];
                    Item dye = DyeItem.byColor(DyeColor.byName(ModBlocks.COLORS[i], DyeColor.WHITE));
                    createShapeless(RecipeCategory.BUILDING_BLOCKS, plasticBlock, 4)
                            .input(ModItems.PLASTIC_PELLETS, 4)
                            .input(dye)
                            .input(Items.AMETHYST_SHARD)
                            .group("plastic_block")
                            .criterion(hasItem(ModItems.PLASTIC_PELLETS), conditionsFromItem(ModItems.PLASTIC_PELLETS))
                            .offerTo(recipeExporter);
                    // Re-dye 8 plastic blocks of any colour, like the vanilla terracotta
                    createShaped(RecipeCategory.BUILDING_BLOCKS, plasticBlock, 8)
                            .pattern("###")
                            .pattern("#X#")
                            .pattern("###")
                            .input('#', anyPlasticBlock)
                            .input('X', dye)
                            .group("dyed_plastic_block")
                            .criterion(hasItem(dye), conditionsFromItem(dye))
                            .offerTo(recipeExporter, getItemPath(plasticBlock) + "_from_dyeing");
                }
                generatePlasticStuds();
                generatePlasticShapes();
            }

            // Slabs, stairs and walls like the vanilla stone ones (crafting table and stonecutter); plastic sticks
            // from 2 plastic blocks, like wooden sticks from 2 planks
            private void generatePlasticShapes() {
                Ingredient anyPlasticBlock = Ingredient.ofItems(ModBlocks.PLASTIC_BLOCKS);
                createShaped(RecipeCategory.MISC, ModItems.PLASTIC_STICK, 4)
                        .pattern("#")
                        .pattern("#")
                        .input('#', anyPlasticBlock)
                        .group("plastic_stick")
                        .criterion(hasItem(ModItems.PLASTIC_PELLETS), conditionsFromItem(ModItems.PLASTIC_PELLETS))
                        .offerTo(recipeExporter);
                for (int i = 0; i < ModBlocks.COLORS.length; i++) {
                    Block plasticBlock = ModBlocks.PLASTIC_BLOCKS[i];
                    Block[] shapes = {ModBlocks.PLASTIC_STAIRS[i], ModBlocks.PLASTIC_SLABS[i], ModBlocks.PLASTIC_WALLS[i]};
                    generateFamily(new BlockFamily.Builder(plasticBlock)
                            .stairs(shapes[0]).slab(shapes[1]).wall(shapes[2]).build(), FeatureFlags.VANILLA_FEATURES);
                    offerStonecuttingVariants(shapes, plasticBlock);
                }
            }

            // A plastic block splits into 4 studs and 4 studs make it back; studs re-dye like plastic blocks
            private void generatePlasticStuds() {
                Ingredient anyStud = Ingredient.ofItems(ModBlocks.PLASTIC_STUDS);
                for (int i = 0; i < ModBlocks.COLORS.length; i++) {
                    Block plasticBlock = ModBlocks.PLASTIC_BLOCKS[i];
                    Block stud = ModBlocks.PLASTIC_STUDS[i];
                    Item dye = DyeItem.byColor(DyeColor.byName(ModBlocks.COLORS[i], DyeColor.WHITE));
                    createShapeless(RecipeCategory.DECORATIONS, stud, 4)
                            .input(plasticBlock)
                            .group("plastic_stud")
                            .criterion(hasItem(plasticBlock), conditionsFromItem(plasticBlock))
                            .offerTo(recipeExporter);
                    createShaped(RecipeCategory.BUILDING_BLOCKS, plasticBlock)
                            .pattern("##")
                            .pattern("##")
                            .input('#', stud)
                            .group("plastic_block")
                            .criterion(hasItem(stud), conditionsFromItem(stud))
                            .offerTo(recipeExporter, getItemPath(plasticBlock) + "_from_plastic_studs");
                    createShaped(RecipeCategory.DECORATIONS, stud, 8)
                            .pattern("###")
                            .pattern("#X#")
                            .pattern("###")
                            .input('#', anyStud)
                            .input('X', dye)
                            .group("dyed_plastic_stud")
                            .criterion(hasItem(dye), conditionsFromItem(dye))
                            .offerTo(recipeExporter, getItemPath(stud) + "_from_dyeing");
                }
            }

            // Same chain as the vanilla polished stones: concrete -> polished (2x2) -> bricks (2x2),
            // stairs / slabs / walls at the crafting table, everything at the stonecutter.
            private void generatePolishedConcrete() {
                RecipeCategory building = RecipeCategory.BUILDING_BLOCKS;
                for (int i = 0; i < ModBlocks.COLORS.length; i++) {
                    Block concrete = Registries.BLOCK.get(Identifier.ofVanilla(ModBlocks.COLORS[i] + "_concrete"));
                    Block polished = ModBlocks.POLISHED_CONCRETE_BLOCKS[i];
                    Block bricks = ModBlocks.POLISHED_CONCRETE_BRICKS_BLOCKS[i];
                    Block[] polishedVariants = {ModBlocks.POLISHED_CONCRETE_STAIRS[i], ModBlocks.POLISHED_CONCRETE_SLABS[i], ModBlocks.POLISHED_CONCRETE_WALLS[i]};
                    Block[] bricksVariants = {ModBlocks.POLISHED_CONCRETE_BRICKS_STAIRS[i], ModBlocks.POLISHED_CONCRETE_BRICKS_SLABS[i], ModBlocks.POLISHED_CONCRETE_BRICKS_WALLS[i]};

                    offerPolishedStoneRecipe(building, polished, concrete);
                    offerPolishedStoneRecipe(building, bricks, polished);
                    generateFamily(new BlockFamily.Builder(polished)
                            .stairs(polishedVariants[0]).slab(polishedVariants[1]).wall(polishedVariants[2]).build(), FeatureFlags.VANILLA_FEATURES);
                    generateFamily(new BlockFamily.Builder(bricks)
                            .stairs(bricksVariants[0]).slab(bricksVariants[1]).wall(bricksVariants[2]).build(), FeatureFlags.VANILLA_FEATURES);

                    offerStonecuttingRecipe(building, polished, concrete);
                    offerStonecuttingRecipe(building, bricks, concrete);
                    offerStonecuttingRecipe(building, bricks, polished);
                    for (Block input : new Block[]{concrete, polished})
                        offerStonecuttingVariants(polishedVariants, input);
                    for (Block input : new Block[]{concrete, polished, bricks})
                        offerStonecuttingVariants(bricksVariants, input);
                }
            }

            // {stairs, slab, wall}: a slab is half a block, so the stonecutter gives two
            private void offerStonecuttingVariants(Block[] variants, Block input) {
                offerStonecuttingRecipe(RecipeCategory.BUILDING_BLOCKS, variants[0], input);
                offerStonecuttingRecipe(RecipeCategory.BUILDING_BLOCKS, variants[1], input, 2);
                offerStonecuttingRecipe(RecipeCategory.BUILDING_BLOCKS, variants[2], input);
            }
        };
    }

    @Override
    public String getName() {
        return "Steveparty Dice Recipe Provider";
    }
}
