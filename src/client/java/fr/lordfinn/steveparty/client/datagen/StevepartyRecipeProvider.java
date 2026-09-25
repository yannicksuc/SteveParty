package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.data.server.recipe.CookingRecipeJsonBuilder;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.RecipeGenerator;
import net.minecraft.data.server.recipe.StonecuttingRecipeJsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.data.family.BlockFamily;
import net.minecraft.item.DyeItem;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.SmeltingRecipe;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.tag.ItemTags;
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
            /**
             * Full recipe id for a named recipe. A bare name would be parsed in the minecraft namespace: Fabric fixes
             * the file path, but not the unlock advancement, which would then reward a recipe that doesn't exist.
             */
            private static String id(String name) {
                return Steveparty.id(name).toString();
            }

            // The vanilla helpers below name their recipes with a bare name: same recipes, namespaced ids

            @Override
            public void offerStonecuttingRecipe(RecipeCategory category, ItemConvertible output, ItemConvertible input, int count) {
                StonecuttingRecipeJsonBuilder.createStonecutting(Ingredient.ofItem(input), category, output, count)
                        .criterion(hasItem(input), conditionsFromItem(input))
                        .offerTo(exporter, id(convertBetween(output, input) + "_stonecutting"));
            }

            @Override
            public void offerReversibleCompactingRecipes(RecipeCategory reverseCategory, ItemConvertible baseItem,
                                                         RecipeCategory compactingCategory, ItemConvertible compactItem) {
                offerReversibleCompactingRecipes(reverseCategory, baseItem, compactingCategory, compactItem,
                        id(getItemPath(compactItem)), null, id(getItemPath(baseItem)), null);
            }

            @Override
            public void offerSmelting(List<ItemConvertible> inputs, RecipeCategory category, ItemConvertible output,
                                      float experience, int cookingTime, String group) {
                for (ItemConvertible input : inputs) {
                    CookingRecipeJsonBuilder.create(Ingredient.ofItem(input), category, output, experience, cookingTime,
                                    RecipeSerializer.SMELTING, SmeltingRecipe::new)
                            .group(group)
                            .criterion(hasItem(input), conditionsFromItem(input))
                            .offerTo(exporter, id(getItemPath(output) + "_from_smelting_" + getItemPath(input)));
                }
            }

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
                        .offerTo(recipeExporter, id("blank_dice_face_from_crafting"));

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
                        .offerTo(recipeExporter, id("power_star_from_fragments"));
                createShapeless(RecipeCategory.MISC, ModItems.POWER_STAR, 1)
                        .input(ModItems.BLACK_STAR_FRAGMENT, 5)
                        .criterion(hasItem(ModItems.BLACK_STAR_FRAGMENT), conditionsFromItem(ModItems.BLACK_STAR_FRAGMENT))
                        .offerTo(recipeExporter, id("power_star_from_black_fragments"));

                generatePolishedConcrete();
                generatePolishedTerracotta();
                generatePlasticBlocks();
                generateSurvivalRecipes();
            }

            /**
             * Items that used to be creative-only. The villager block has no recipe on purpose: it is made by pushing
             * a villager down with a piston.
             */
            private void generateSurvivalRecipes() {
                // End-game: the forge needs a power star and netherite, its gravity core a heavy core (trial vaults)
                // wrapped in black star fragments, the fragments that also weigh the core down in the forge
                createShaped(RecipeCategory.DECORATIONS, ModBlocks.DICE_FORGE)
                        .pattern(" P ")
                        .pattern("OBO")
                        .pattern("ONO")
                        .input('P', ModItems.POWER_STAR)
                        .input('O', Items.OBSIDIAN)
                        .input('B', Items.BLAST_FURNACE)
                        .input('N', Items.NETHERITE_INGOT)
                        .criterion(hasItem(ModItems.POWER_STAR), conditionsFromItem(ModItems.POWER_STAR))
                        .offerTo(recipeExporter);
                createShaped(RecipeCategory.DECORATIONS, ModBlocks.GRAVITY_CORE)
                        .pattern("CFC")
                        .pattern("FHF")
                        .pattern("CFC")
                        .input('C', Items.CRYING_OBSIDIAN)
                        .input('F', ModItems.BLACK_STAR_FRAGMENT)
                        .input('H', Items.HEAVY_CORE)
                        .criterion(hasItem(Items.HEAVY_CORE), conditionsFromItem(Items.HEAVY_CORE))
                        .criterion(hasItem(ModItems.BLACK_STAR_FRAGMENT), conditionsFromItem(ModItems.BLACK_STAR_FRAGMENT))
                        .offerTo(recipeExporter);

                // Shop: a register of gold and iron (buttons for keys, a chest for the drawer), and the shopkeeper's
                // key, a gold key with a star fragment of any colour for its bow
                createShaped(RecipeCategory.DECORATIONS, ModBlocks.CASH_REGISTER)
                        .pattern("BBB")
                        .pattern("GCG")
                        .pattern("III")
                        .input('B', Items.STONE_BUTTON)
                        .input('G', Items.GOLD_INGOT)
                        .input('C', Items.CHEST)
                        .input('I', Items.IRON_INGOT)
                        .criterion(hasItem(ModBlocks.TRADING_STALL), conditionsFromItem(ModBlocks.TRADING_STALL))
                        .criterion(hasItem(Items.GOLD_INGOT), conditionsFromItem(Items.GOLD_INGOT))
                        .offerTo(recipeExporter);
                createShaped(RecipeCategory.TOOLS, ModItems.SHOPKEEPER_KEY)
                        .pattern("F")
                        .pattern("G")
                        .pattern("N")
                        .input('F', Ingredient.ofItems(ModItems.BLUE_STAR_FRAGMENT, ModItems.PURPLE_STAR_FRAGMENT,
                                ModItems.RED_STAR_FRAGMENT, ModItems.YELLOW_STAR_FRAGMENT, ModItems.GREEN_STAR_FRAGMENT,
                                ModItems.BLACK_STAR_FRAGMENT))
                        .input('G', Items.GOLD_INGOT)
                        .input('N', Items.GOLD_NUGGET)
                        .criterion(hasItem(ModBlocks.TRADING_STALL), conditionsFromItem(ModBlocks.TRADING_STALL))
                        .criterion(hasItem(ModBlocks.CASH_REGISTER), conditionsFromItem(ModBlocks.CASH_REGISTER))
                        .offerTo(recipeExporter);

                // Teleportation books: an ender pearl bound in a book, with a compass to go somewhere, a lead to
                // bring the others along
                createShapeless(RecipeCategory.TOOLS, ModItems.HERE_WE_GO_BOOK)
                        .input(Items.BOOK)
                        .input(Items.ENDER_PEARL)
                        .input(Items.COMPASS)
                        .criterion(hasItem(Items.ENDER_PEARL), conditionsFromItem(Items.ENDER_PEARL))
                        .offerTo(recipeExporter);
                createShapeless(RecipeCategory.TOOLS, ModItems.HERE_WE_COME_BOOK)
                        .input(Items.BOOK)
                        .input(Items.ENDER_PEARL)
                        .input(Items.LEAD)
                        .criterion(hasItem(Items.ENDER_PEARL), conditionsFromItem(Items.ENDER_PEARL))
                        .offerTo(recipeExporter);

                createShapeless(RecipeCategory.COMBAT, ModItems.TRIPLE_JUMP_SHOES)
                        .input(Items.LEATHER_BOOTS)
                        .input(Items.RABBIT_FOOT)
                        .input(Items.SLIME_BALL, 2)
                        .criterion(hasItem(Items.RABBIT_FOOT), conditionsFromItem(Items.RABBIT_FOOT))
                        .offerTo(recipeExporter);

                // Special cartridges: a plain cartridge given its role (green to start, red to pause, a chest for the inventory)
                offerCartridge(ModItems.TILE_BEHAVIOR_START, Items.LIME_DYE);
                offerCartridge(ModItems.BOARD_SPACE_BEHAVIOR_STOP, Items.RED_DYE);
                offerCartridge(ModItems.INVENTORY_CARTRIDGE, Items.CHEST);

                // A single-cartridge tile: the tile's carpets on one pressure plate, without its chest
                createShaped(RecipeCategory.REDSTONE, ModBlocks.SIMPLE_TILE)
                        .pattern("WWW")
                        .pattern(" P ")
                        .input('W', ItemTags.WOOL_CARPETS)
                        .input('P', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                        .criterion(hasItem(Items.LIGHT_WEIGHTED_PRESSURE_PLATE), conditionsFromItem(Items.LIGHT_WEIGHTED_PRESSURE_PLATE))
                        .criterion(hasItem(ModBlocks.TILE), conditionsFromItem(ModBlocks.TILE))
                        .offerTo(recipeExporter);

                // A page for the catalogue: paper dyed like the page
                createShapeless(RecipeCategory.MISC, ModItems.MINI_GAME_PAGE)
                        .input(Items.PAPER)
                        .input(Items.CYAN_DYE)
                        .criterion(hasItem(ModItems.MINI_GAMES_CATALOGUE), conditionsFromItem(ModItems.MINI_GAMES_CATALOGUE))
                        .offerTo(recipeExporter);
            }

            private void offerCartridge(Item cartridge, Item role) {
                createShapeless(RecipeCategory.MISC, cartridge)
                        .input(ModItems.BOARD_SPACE_BEHAVIOR)
                        .input(role)
                        .group("cartridge")
                        .criterion(hasItem(ModItems.BOARD_SPACE_BEHAVIOR), conditionsFromItem(ModItems.BOARD_SPACE_BEHAVIOR))
                        .offerTo(recipeExporter);
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
                            .offerTo(recipeExporter, id(getItemPath(plasticBlock) + "_from_dyeing"));
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
                            .offerTo(recipeExporter, id(getItemPath(plasticBlock) + "_from_plastic_studs"));
                    createShaped(RecipeCategory.DECORATIONS, stud, 8)
                            .pattern("###")
                            .pattern("#X#")
                            .pattern("###")
                            .input('#', anyStud)
                            .input('X', dye)
                            .group("dyed_plastic_stud")
                            .criterion(hasItem(dye), conditionsFromItem(dye))
                            .offerTo(recipeExporter, id(getItemPath(stud) + "_from_dyeing"));
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

            // The same chain for the polished terracotta, from the vanilla terracotta of each colour ("default" = plain terracotta)
            private void generatePolishedTerracotta() {
                RecipeCategory building = RecipeCategory.BUILDING_BLOCKS;
                for (int i = 0; i < ModBlocks.COLORS_WITH_DEFAULT.length; i++) {
                    String color = ModBlocks.COLORS_WITH_DEFAULT[i];
                    Block terracotta = color.equals("default") ? Blocks.TERRACOTTA
                            : Registries.BLOCK.get(Identifier.ofVanilla(color + "_terracotta"));
                    Block polished = ModBlocks.POLISHED_TERRACOTTA_BLOCKS[i];
                    Block bricks = ModBlocks.POLISHED_TERRACOTTA_BRICKS_BLOCKS[i];
                    Block[] polishedVariants = {ModBlocks.POLISHED_TERRACOTTA_STAIRS[i], ModBlocks.POLISHED_TERRACOTTA_SLABS[i], ModBlocks.POLISHED_TERRACOTTA_WALLS[i]};
                    Block[] bricksVariants = {ModBlocks.POLISHED_TERRACOTTA_BRICKS_STAIRS[i], ModBlocks.POLISHED_TERRACOTTA_BRICKS_SLABS[i], ModBlocks.POLISHED_TERRACOTTA_BRICKS_WALLS[i]};

                    offerPolishedStoneRecipe(building, polished, terracotta);
                    offerPolishedStoneRecipe(building, bricks, polished);
                    generateFamily(new BlockFamily.Builder(polished)
                            .stairs(polishedVariants[0]).slab(polishedVariants[1]).wall(polishedVariants[2]).build(), FeatureFlags.VANILLA_FEATURES);
                    generateFamily(new BlockFamily.Builder(bricks)
                            .stairs(bricksVariants[0]).slab(bricksVariants[1]).wall(bricksVariants[2]).build(), FeatureFlags.VANILLA_FEATURES);

                    offerStonecuttingRecipe(building, polished, terracotta);
                    offerStonecuttingRecipe(building, bricks, terracotta);
                    offerStonecuttingRecipe(building, bricks, polished);
                    for (Block input : new Block[]{terracotta, polished})
                        offerStonecuttingVariants(polishedVariants, input);
                    for (Block input : new Block[]{terracotta, polished, bricks})
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
