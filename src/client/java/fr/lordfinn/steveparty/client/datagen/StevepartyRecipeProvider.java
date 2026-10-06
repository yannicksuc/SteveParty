package fr.lordfinn.steveparty.client.datagen;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.recipes.DiceModuleRecipe;
import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesBlock;
import fr.lordfinn.steveparty.blocks.custom.tiles.PolishedTilesColor;
import net.minecraft.advancement.AdvancementRequirements;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.criterion.RecipeUnlockedCriterion;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import java.util.Map;
import net.minecraft.data.server.recipe.CookingRecipeJsonBuilder;
import net.minecraft.data.server.recipe.RecipeExporter;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.data.server.recipe.ShapelessRecipeJsonBuilder;
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

    /** The exporter of the current {@link #generate} run, for the helpers below. */
    private RecipeExporter exporter;

    /**
     * Full recipe id for a named recipe. A bare name would be parsed in the minecraft namespace: Fabric fixes
     * the file path, but not the unlock advancement, which would then reward a recipe that doesn't exist.
     */
    private static Identifier id(String name) {
        return Steveparty.id(name);
    }

    // The vanilla helpers below name their recipes with a bare name: same recipes, namespaced ids

    private void offerStonecuttingRecipe(RecipeCategory category, ItemConvertible output, ItemConvertible input) {
        offerStonecuttingRecipe(category, output, input, 1);
    }

    private void offerStonecuttingRecipe(RecipeCategory category, ItemConvertible output, ItemConvertible input, int count) {
        StonecuttingRecipeJsonBuilder.createStonecutting(Ingredient.ofItems(input), category, output, count)
                .criterion(hasItem(input), conditionsFromItem(input))
                .offerTo(exporter, id(convertBetween(output, input) + "_stonecutting"));
    }

    /** 9 of {@code baseItem} make {@code compactItem}, and back (the vanilla recipes, ids in the mod's namespace). */
    private void offerReversibleCompactingRecipes(RecipeCategory reverseCategory, ItemConvertible baseItem,
                                                  RecipeCategory compactingCategory, ItemConvertible compactItem) {
        ShapelessRecipeJsonBuilder.create(reverseCategory, baseItem, 9)
                .input(compactItem)
                .criterion(hasItem(compactItem), conditionsFromItem(compactItem))
                .offerTo(exporter, id(getItemPath(baseItem)));
        ShapedRecipeJsonBuilder.create(compactingCategory, compactItem)
                .input('#', baseItem)
                .pattern("###")
                .pattern("###")
                .pattern("###")
                .criterion(hasItem(baseItem), conditionsFromItem(baseItem))
                .offerTo(exporter, id(getItemPath(compactItem)));
    }

    private void offerSmelting(List<ItemConvertible> inputs, RecipeCategory category, ItemConvertible output,
                               float experience, int cookingTime, String group) {
        for (ItemConvertible input : inputs) {
            CookingRecipeJsonBuilder.create(Ingredient.ofItems(input), category, output, experience, cookingTime,
                            RecipeSerializer.SMELTING, SmeltingRecipe::new)
                    .group(group)
                    .criterion(hasItem(input), conditionsFromItem(input))
                    .offerTo(exporter, id(getItemPath(output) + "_from_smelting_" + getItemPath(input)));
        }
    }

    /** A special dice face: a blank face and its ingredient, anywhere in the grid. */
    private void offerSpecialFace(String face, Item blank, Item ingredient) {
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, Registries.ITEM.get(Steveparty.id(face)), 1)
                .input(blank)
                .input(ingredient)
                .criterion(hasItem(blank), conditionsFromItem(blank))
                .offerTo(exporter, id(face + "_from_crafting"));
    }

    /** Coin / debt faces: any face of the family is cut into any value of it at the stonecutter. */
    private void offerFaceValues(String prefix) {
        List<Item> family = new java.util.ArrayList<>();
        for (int value = 1; value <= DiceFacesComponent.DiceFace.MAX_COINS; value++)
            family.add(Registries.ITEM.get(Steveparty.id(prefix + value)));
        for (Item output : family) {
            StonecuttingRecipeJsonBuilder.createStonecutting(Ingredient.ofItems(family.stream().filter(item -> item != output).toArray(ItemConvertible[]::new)),
                            RecipeCategory.MISC, output, 1)
                    .criterion(hasItem(family.getFirst()), conditionsFromItem(family.getFirst()))
                    .offerTo(exporter, id(getItemPath(output) + "_from_its_family_stonecutting"));
        }
    }

    /**
     * A dice module: its item (a blank module ringed with seven star fragments of the module's colour, its
     * ingredient on top), and the craft that puts it on a die (see DiceModuleRecipe), unlocked by the module item.
     */
    private void offerModule(DiceModule module, Item fragment, Item ingredient) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, module.item(), 1)
                .pattern("FIF")
                .pattern("FMF")
                .pattern("FFF")
                .input('F', fragment)
                .input('I', ingredient)
                .input('M', ModItems.BLANK_DICE_MODULE)
                .criterion(hasItem(ModItems.BLANK_DICE_MODULE), conditionsFromItem(ModItems.BLANK_DICE_MODULE))
                .offerTo(exporter); // named after the item: steveparty:dice_module_<id>
        Identifier onDie = Steveparty.id("dice_with_module_" + module.id());
        exporter.accept(onDie, new DiceModuleRecipe(module), exporter.getAdvancementBuilder()
                .criterion("has_the_recipe", RecipeUnlockedCriterion.create(onDie))
                .criterion(hasItem(module.item()), conditionsFromItem(module.item()))
                .rewards(AdvancementRewards.Builder.recipe(onDie))
                .criteriaMerger(AdvancementRequirements.CriterionMerger.OR)
                .build(onDie.withPrefixedPath("recipes/tools/")));
    }

    @Override
    public void generate(RecipeExporter exporter) {
        this.exporter = exporter;
        // Dice faces. The numbers (0 included, premium, cursed) are cut from a blank face at the stonecutter,
        // and any face is cut back into a blank one. The special faces are crafted from a blank face and an
        // ingredient (their value 1 for the coin and debt faces), then cut into the value wanted.
        Item blank = ModItems.blankDiceFace();
        for (Item dice : ModItems.DICE_FACES) {
            if (dice == blank) continue;
            DiceFacesComponent.Kind kind = DiceFacesComponent.DiceFace.fromItem(dice).orElseThrow().kind();
            if (kind.isNumeric()) offerStonecuttingRecipe(RecipeCategory.MISC, dice, blank);
            offerStonecuttingRecipe(RecipeCategory.MISC, blank, dice);
        }
        offerSpecialFace("coin_dice_face_1", blank, ModItems.COIN);
        offerSpecialFace("debt_dice_face_1", blank, Items.SPIDER_EYE);
        offerSpecialFace("swap_dice_face", blank, Items.ENDER_PEARL);
        offerFaceValues("coin_dice_face_");
        offerFaceValues("debt_dice_face_");

        // Dice modules: a blank module (a blank face set in gold), then seven star fragments of the module's
        // colour (its icon's ring) around it and what the module is about
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.BLANK_DICE_MODULE, 1)
                .pattern(" G ")
                .pattern("GBG")
                .pattern(" G ")
                .input('G', Items.GOLD_NUGGET)
                .input('B', blank)
                .criterion(hasItem(blank), conditionsFromItem(blank))
                .offerTo(exporter);
        offerModule(DiceModules.SLOW, ModItems.LIGHT_BLUE_STAR_FRAGMENT, Items.CLOCK);
        offerModule(DiceModules.CHOICE, ModItems.BLUE_STAR_FRAGMENT, Items.COMPASS);
        offerModule(DiceModules.POWER_UP, ModItems.PURPLE_STAR_FRAGMENT, Items.ECHO_SHARD);
        offerModule(DiceModules.LUCKY, ModItems.GREEN_STAR_FRAGMENT, Items.RABBIT_FOOT);
        offerModule(DiceModules.REROLL, ModItems.ORANGE_STAR_FRAGMENT, Items.WIND_CHARGE);
        offerModule(DiceModules.REVERSED, ModItems.RED_STAR_FRAGMENT, Items.FERMENTED_SPIDER_EYE);
        offerModule(DiceModules.SKELETON_KEY, ModItems.YELLOW_STAR_FRAGMENT, Items.TRIPWIRE_HOOK);
        offerModule(DiceModules.HOMING, ModItems.MAGENTA_STAR_FRAGMENT, Items.ENDER_EYE);

        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.DICE_FACES.get(0), 4) // output 4 blank dice faces
                .pattern("IQ")
                .pattern("QI")
                .input('I', Items.IRON_INGOT)
                .input('Q', Items.QUARTZ)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT))
                .criterion(hasItem(Items.QUARTZ), conditionsFromItem(Items.QUARTZ))
                .offerTo(exporter, id("blank_dice_face_from_crafting"));

        // 9 fragments <-> their block, for the 16 colours
        for (DyeColor dye : DyeColor.values()) {
            offerReversibleCompactingRecipes(RecipeCategory.MISC, ModItems.starFragment(dye),
                    RecipeCategory.BUILDING_BLOCKS, ModBlocks.starFragmentsBlock(dye));
        }
        generateStarFragmentMixing();
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.POWER_STAR, 1)
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
                .offerTo(exporter, id("power_star_from_fragments"));
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.POWER_STAR, 1)
                .input(ModItems.BLACK_STAR_FRAGMENT, 5)
                .criterion(hasItem(ModItems.BLACK_STAR_FRAGMENT), conditionsFromItem(ModItems.BLACK_STAR_FRAGMENT))
                .offerTo(exporter, id("power_star_from_black_fragments"));
        // The coin (the default Pièce currency of a party): minted from a gold nugget, one for one
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.COIN, 1)
                .input(Items.GOLD_NUGGET)
                .criterion(hasItem(Items.GOLD_NUGGET), conditionsFromItem(Items.GOLD_NUGGET))
                .offerTo(exporter, id("coin"));

        generatePolishedConcrete();
        generatePolishedTerracotta();
        generatePolishedTiles();
        generatePlasticBlocks();
        generateSurvivalRecipes();
    }

    /**
     * Star fragments mix like dyes: as many fragments out as in, so nothing is lost. The Mulas drop blue,
     * purple, red, yellow, green and black; every other colour is mixed from those (white from the three
     * primaries). Vanilla's dye mixes where they exist; no two recipes share the same ingredients.
     */
    private void generateStarFragmentMixing() {
        offerMix(DyeColor.WHITE, DyeColor.RED, DyeColor.BLUE, DyeColor.YELLOW);
        offerMix(DyeColor.ORANGE, DyeColor.RED, DyeColor.YELLOW);
        offerMix(DyeColor.GREEN, DyeColor.BLUE, DyeColor.YELLOW);
        offerMix(DyeColor.PURPLE, DyeColor.RED, DyeColor.BLUE);
        offerMix(DyeColor.CYAN, DyeColor.BLUE, DyeColor.GREEN);
        offerMix(DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.WHITE);
        offerMix(DyeColor.LIME, DyeColor.GREEN, DyeColor.WHITE);
        offerMix(DyeColor.PINK, DyeColor.RED, DyeColor.WHITE);
        offerMix(DyeColor.MAGENTA, DyeColor.PURPLE, DyeColor.PINK);
        offerMix(DyeColor.MAGENTA, DyeColor.BLUE, DyeColor.RED, DyeColor.PINK);
        offerMix(DyeColor.MAGENTA, DyeColor.BLUE, DyeColor.RED, DyeColor.RED, DyeColor.WHITE);
        offerMix(DyeColor.GRAY, DyeColor.BLACK, DyeColor.WHITE);
        offerMix(DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.WHITE);
        offerMix(DyeColor.LIGHT_GRAY, DyeColor.BLACK, DyeColor.WHITE, DyeColor.WHITE);
        offerMix(DyeColor.BROWN, DyeColor.ORANGE, DyeColor.BLACK);
        offerMix(DyeColor.BROWN, DyeColor.RED, DyeColor.GREEN);
    }

    /** Shapeless mix: one fragment of each input colour gives as many fragments of the result colour. */
    private void offerMix(DyeColor result, DyeColor... inputs) {
        Item output = ModItems.starFragment(result);
        var builder = ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, output, inputs.length)
                .group(result.getName() + "_star_fragment");
        StringBuilder name = new StringBuilder(result.getName()).append("_star_fragment_from");
        for (DyeColor input : inputs) {
            Item fragment = ModItems.starFragment(input);
            builder.input(fragment).criterion(hasItem(fragment), conditionsFromItem(fragment));
            name.append('_').append(input.getName());
        }
        builder.offerTo(exporter, id(name.toString()));
    }

    /**
     * Items that used to be creative-only. The villager block has no recipe on purpose: it is made by pushing
     * a villager down with a piston.
     */
    private void generateSurvivalRecipes() {
        // End-game: the forge needs a power star and netherite, its gravity core a heavy core (trial vaults)
        // wrapped in black star fragments, the fragments that also weigh the core down in the forge
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.DICE_FORGE)
                .pattern(" P ")
                .pattern("OBO")
                .pattern("ONO")
                .input('P', ModItems.POWER_STAR)
                .input('O', Items.OBSIDIAN)
                .input('B', Items.BLAST_FURNACE)
                .input('N', Items.NETHERITE_INGOT)
                .criterion(hasItem(ModItems.POWER_STAR), conditionsFromItem(ModItems.POWER_STAR))
                .offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.GRAVITY_CORE)
                .pattern("CFC")
                .pattern("FHF")
                .pattern("CFC")
                .input('C', Items.CRYING_OBSIDIAN)
                .input('F', ModItems.BLACK_STAR_FRAGMENT)
                .input('H', Items.HEAVY_CORE)
                .criterion(hasItem(Items.HEAVY_CORE), conditionsFromItem(Items.HEAVY_CORE))
                .criterion(hasItem(ModItems.BLACK_STAR_FRAGMENT), conditionsFromItem(ModItems.BLACK_STAR_FRAGMENT))
                .offerTo(exporter);

        // The Telescope: a spyglass on a copper mount and a tripod of sticks
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.TELESCOPE)
                .pattern(" S ")
                .pattern(" C ")
                .pattern("TTT")
                .input('S', Items.SPYGLASS)
                .input('C', Items.COPPER_INGOT)
                .input('T', Items.STICK)
                .criterion(hasItem(Items.SPYGLASS), conditionsFromItem(Items.SPYGLASS))
                .offerTo(exporter);

        // Shop: a register of gold and iron (buttons for keys, a chest for the drawer), and the shopkeeper's
        // key, a gold key with a star fragment of any colour for its bow
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.CASH_REGISTER)
                .pattern("BBB")
                .pattern("GCG")
                .pattern("III")
                .input('B', Items.STONE_BUTTON)
                .input('G', Items.GOLD_INGOT)
                .input('C', Items.CHEST)
                .input('I', Items.IRON_INGOT)
                .criterion(hasItem(ModBlocks.TRADING_STALL), conditionsFromItem(ModBlocks.TRADING_STALL))
                .criterion(hasItem(Items.GOLD_INGOT), conditionsFromItem(Items.GOLD_INGOT))
                .offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.TOOLS, ModItems.SHOPKEEPER_KEY)
                .pattern("F")
                .pattern("G")
                .pattern("N")
                .input('F', StevepartyReferenceItemTagProvider.STAR_FRAGMENTS_TAG)
                .input('G', Items.GOLD_INGOT)
                .input('N', Items.GOLD_NUGGET)
                .criterion(hasItem(ModBlocks.TRADING_STALL), conditionsFromItem(ModBlocks.TRADING_STALL))
                .criterion(hasItem(ModBlocks.CASH_REGISTER), conditionsFromItem(ModBlocks.CASH_REGISTER))
                .offerTo(exporter);

        ShapelessRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.TRIPLE_JUMP_SHOES)
                .input(Items.LEATHER_BOOTS)
                .input(Items.RABBIT_FOOT)
                .input(Items.SLIME_BALL, 2)
                .criterion(hasItem(Items.RABBIT_FOOT), conditionsFromItem(Items.RABBIT_FOOT))
                .offerTo(exporter);

        // Special cartridges: a plain cartridge given its role (green to start, red to stop, a chest for the inventory)
        offerCartridge(ModItems.TILE_BEHAVIOR_START, Items.LIME_DYE);
        offerCartridge(ModItems.BOARD_SPACE_BEHAVIOR_STOP, Items.RED_DYE);
        offerCartridge(ModItems.INVENTORY_CARTRIDGE, Items.CHEST);
        offerCartridge(ModItems.ADVANCE_BACK_CARTRIDGE, Items.PISTON); // pushes the token on, or pulls it back
        // Roll Again: a repeater, to play the turn again
        offerCartridge(ModItems.REPLAY_CARTRIDGE, Items.REPEATER);
        // An ender pearl: the warp of the Teleport tile
        offerCartridge(ModItems.TELEPORT_CARTRIDGE, Items.ENDER_PEARL);
        // A Power Star: the star the space sells
        offerCartridge(ModItems.STAR_CARTRIDGE, ModItems.POWER_STAR);

        // The Tile: a plastic board (pellets) under a cloth face (carpets), on the pressure plate that feels
        // the tokens landing on it; two at a time
        ShapedRecipeJsonBuilder.create(RecipeCategory.REDSTONE, ModBlocks.TILE, 2)
                .pattern("WWW")
                .pattern("CPC")
                .input('W', ItemTags.WOOL_CARPETS)
                .input('C', ModItems.PLASTIC_PELLETS)
                .input('P', Items.LIGHT_WEIGHTED_PRESSURE_PLATE)
                .criterion(hasItem(ModItems.PLASTIC_PELLETS), conditionsFromItem(ModItems.PLASTIC_PELLETS))
                .criterion(hasItem(Items.LIGHT_WEIGHTED_PRESSURE_PLATE), conditionsFromItem(Items.LIGHT_WEIGHTED_PRESSURE_PLATE))
                .offerTo(exporter);

        // The Advanced Tile: a Tile in a gold frame, a chest for its 16 cartridges and a comparator reading
        // the redstone power that picks the active one
        ShapedRecipeJsonBuilder.create(RecipeCategory.REDSTONE, ModBlocks.ADVANCED_TILE)
                .pattern(" R ")
                .pattern("GTG")
                .pattern("GCG")
                .input('R', Items.COMPARATOR)
                .input('G', Items.GOLD_INGOT)
                .input('T', ModBlocks.TILE)
                .input('C', Items.CHEST)
                .criterion(hasItem(ModBlocks.TILE), conditionsFromItem(ModBlocks.TILE))
                .offerTo(exporter);

        // Pages for the catalogue: paper around a cartridge (a page is linked to pipes like a cartridge to tiles)
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.MINI_GAME_PAGE, 4)
                .pattern(" P ")
                .pattern("PCP")
                .pattern(" P ")
                .input('P', Items.PAPER)
                .input('C', ModItems.BOARD_SPACE_BEHAVIOR)
                .criterion(hasItem(ModItems.BOARD_SPACE_BEHAVIOR), conditionsFromItem(ModItems.BOARD_SPACE_BEHAVIOR))
                .offerTo(exporter);
    }

    private void offerCartridge(Item cartridge, Item role) {
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, cartridge)
                .input(ModItems.BOARD_SPACE_BEHAVIOR)
                .input(role)
                .group("cartridge")
                .criterion(hasItem(ModItems.BOARD_SPACE_BEHAVIOR), conditionsFromItem(ModItems.BOARD_SPACE_BEHAVIOR))
                .offerTo(exporter);
    }

    // Plastic like the real thing: sugar cane (green polyethylene) is turned into pellets in a furnace,
    // then pellets are moulded with a dye and an amethyst shard.
    private void generatePlasticBlocks() {
        offerSmelting(List.of(Items.SUGAR_CANE), RecipeCategory.MISC, ModItems.PLASTIC_PELLETS, 0.1f, 200, "plastic_pellets");
        Ingredient anyPlasticBlock = Ingredient.ofItems(ModBlocks.PLASTIC_BLOCKS);
        for (int i = 0; i < ModBlocks.COLORS.length; i++) {
            Block plasticBlock = ModBlocks.PLASTIC_BLOCKS[i];
            Item dye = DyeItem.byColor(DyeColor.byName(ModBlocks.COLORS[i], DyeColor.WHITE));
            ShapelessRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, plasticBlock, 4)
                    .input(ModItems.PLASTIC_PELLETS, 4)
                    .input(dye)
                    .input(Items.AMETHYST_SHARD)
                    .group("plastic_block")
                    .criterion(hasItem(ModItems.PLASTIC_PELLETS), conditionsFromItem(ModItems.PLASTIC_PELLETS))
                    .offerTo(exporter);
            // Re-dye 8 plastic blocks of any colour, like the vanilla terracotta
            ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, plasticBlock, 8)
                    .pattern("###")
                    .pattern("#X#")
                    .pattern("###")
                    .input('#', anyPlasticBlock)
                    .input('X', dye)
                    .group("dyed_plastic_block")
                    .criterion(hasItem(dye), conditionsFromItem(dye))
                    .offerTo(exporter, id(getItemPath(plasticBlock) + "_from_dyeing"));
        }
        generatePlasticStuds();
        generatePlasticShapes();
    }

    // Slabs, stairs and walls like the vanilla stone ones (crafting table and stonecutter); plastic sticks
    // from 2 plastic blocks, like wooden sticks from 2 planks
    private void generatePlasticShapes() {
        Ingredient anyPlasticBlock = Ingredient.ofItems(ModBlocks.PLASTIC_BLOCKS);
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.PLASTIC_STICK, 4)
                .pattern("#")
                .pattern("#")
                .input('#', anyPlasticBlock)
                .group("plastic_stick")
                .criterion(hasItem(ModItems.PLASTIC_PELLETS), conditionsFromItem(ModItems.PLASTIC_PELLETS))
                .offerTo(exporter);
        for (int i = 0; i < ModBlocks.COLORS.length; i++) {
            Block plasticBlock = ModBlocks.PLASTIC_BLOCKS[i];
            Block[] shapes = {ModBlocks.PLASTIC_STAIRS[i], ModBlocks.PLASTIC_SLABS[i], ModBlocks.PLASTIC_WALLS[i]};
            generateFamily(exporter, new BlockFamily.Builder(plasticBlock)
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
            ShapelessRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, stud, 4)
                    .input(plasticBlock)
                    .group("plastic_stud")
                    .criterion(hasItem(plasticBlock), conditionsFromItem(plasticBlock))
                    .offerTo(exporter);
            ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, plasticBlock)
                    .pattern("##")
                    .pattern("##")
                    .input('#', stud)
                    .group("plastic_block")
                    .criterion(hasItem(stud), conditionsFromItem(stud))
                    .offerTo(exporter, id(getItemPath(plasticBlock) + "_from_plastic_studs"));
            ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, stud, 8)
                    .pattern("###")
                    .pattern("#X#")
                    .pattern("###")
                    .input('#', anyStud)
                    .input('X', dye)
                    .group("dyed_plastic_stud")
                    .criterion(hasItem(dye), conditionsFromItem(dye))
                    .offerTo(exporter, id(getItemPath(stud) + "_from_dyeing"));
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

            offerPolishedStoneRecipe(exporter, building, polished, concrete);
            offerPolishedStoneRecipe(exporter, building, bricks, polished);
            generateFamily(exporter, new BlockFamily.Builder(polished)
                    .stairs(polishedVariants[0]).slab(polishedVariants[1]).wall(polishedVariants[2]).build(), FeatureFlags.VANILLA_FEATURES);
            generateFamily(exporter, new BlockFamily.Builder(bricks)
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

            offerPolishedStoneRecipe(exporter, building, polished, terracotta);
            offerPolishedStoneRecipe(exporter, building, bricks, polished);
            generateFamily(exporter, new BlockFamily.Builder(polished)
                    .stairs(polishedVariants[0]).slab(polishedVariants[1]).wall(polishedVariants[2]).build(), FeatureFlags.VANILLA_FEATURES);
            generateFamily(exporter, new BlockFamily.Builder(bricks)
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

    /**
     * Polished tiles, for each material: the 2x2 checker « A B / B A » of two different polished blocks gives
     * 4 tiles in colours (A, B) (A top-left: (B, A) is the other recipe), and the stonecutter cuts a polished
     * block into the single-colour tiles (4 same polished blocks in a square are the bricks). One unlock
     * advancement per colour, on its stonecutter recipe: having the polished block of a colour shows every
     * recipe using it.
     */
    private void generatePolishedTiles() {
        for (PolishedTilesBlock tiles : ModBlocks.POLISHED_TILES) {
            String name = Registries.BLOCK.getId(tiles).getPath();
            for (PolishedTilesColor a : tiles.colors()) {
                Block polished = tiles.polished(a);
                Identifier cutting = polishedTilesRecipe(name + "_" + a.asString() + "_stonecutting");
                AdvancementRewards.Builder unlocked = AdvancementRewards.Builder.recipe(cutting);
                for (PolishedTilesColor b : tiles.colors()) {
                    if (a == b) continue;
                    Identifier checker = polishedTilesRecipe(name + "_" + a.asString() + "_" + b.asString());
                    exporter.accept(checker, new fr.lordfinn.steveparty.recipes.UnmirroredShapedRecipe(name + "_" + a.asString(), CraftingRecipeCategory.BUILDING,
                            RawShapedRecipe.create(Map.of('A', Ingredient.ofItems(polished), 'B', Ingredient.ofItems(tiles.polished(b))), "AB", "BA"),
                            tiles.stack(a, b, 4), true), null);
                    unlocked.addRecipe(checker).addRecipe(polishedTilesRecipe(name + "_" + b.asString() + "_" + a.asString()));
                }
                exporter.accept(cutting, new StonecuttingRecipe(name, Ingredient.ofItems(polished), tiles.stack(a, a, 1)),
                        exporter.getAdvancementBuilder()
                                .criterion("has_the_recipe", RecipeUnlockedCriterion.create(cutting))
                                .criterion(hasItem(polished), conditionsFromItem(polished))
                                .rewards(unlocked)
                                .criteriaMerger(AdvancementRequirements.CriterionMerger.OR)
                                .build(cutting.withPrefixedPath("recipes/building_blocks/")));
            }
        }
    }

    private static Identifier polishedTilesRecipe(String name) {
        return Steveparty.id(name);
    }

    // {stairs, slab, wall}: a slab is half a block, so the stonecutter gives two
    private void offerStonecuttingVariants(Block[] variants, Block input) {
        offerStonecuttingRecipe(RecipeCategory.BUILDING_BLOCKS, variants[0], input);
        offerStonecuttingRecipe(RecipeCategory.BUILDING_BLOCKS, variants[1], input, 2);
        offerStonecuttingRecipe(RecipeCategory.BUILDING_BLOCKS, variants[2], input);
    }
}
