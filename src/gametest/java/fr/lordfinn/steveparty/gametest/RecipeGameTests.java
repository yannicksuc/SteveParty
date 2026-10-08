package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.Recipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.RecipeManager;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.loot.LootTable;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Recipes: every one reachable from the recipe book, and the crafts that used to be missing or lossy. */
public class RecipeGameTests implements FabricGameTest {

    /** @return what the crafting grid gives (empty if no recipe matches). */
    private static ItemStack result(TestContext context, int width, int height, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(width, height, List.of(grid));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        return recipe.map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyRecipeIsUnlockedByAnAdvancement(TestContext context) {
        RecipeManager recipes = context.getWorld().getServer().getRecipeManager();
        Set<Identifier> unlocked = new HashSet<>();
        for (AdvancementEntry advancement : context.getWorld().getServer().getAdvancementLoader().getAdvancements()) {
            for (Identifier key : advancement.value().rewards().recipes()) {
                // An advancement rewarding a recipe id that doesn't exist never shows that recipe in the book
                if (key.getNamespace().equals(Steveparty.MOD_ID) || advancement.id().getNamespace().equals(Steveparty.MOD_ID))
                    context.assertTrue(recipes.get(key).isPresent(), advancement.id() + " unlocks a missing recipe " + key);
                unlocked.add(key);
            }
        }
        for (RecipeEntry<?> recipe : recipes.values()) {
            if (!recipe.id().getNamespace().equals(Steveparty.MOD_ID) || recipe.value().isIgnoredInRecipeBook()) continue;
            context.assertTrue(unlocked.contains(recipe.id()), "no advancement unlocks " + recipe.id());
        }
        context.complete();
    }

    /**
     * Items obtained in survival without a recipe, and how. Anything else must be the result of a recipe: an item
     * missing from both was only obtainable in creative.
     */
    private static final Map<String, String> OTHER_SURVIVAL_ROUTES = Map.ofEntries(
            Map.entry("blue_star_fragment", "dropped by the blue Mula"),
            Map.entry("purple_star_fragment", "dropped by the purple Mula"),
            Map.entry("red_star_fragment", "dropped by the red Mula"),
            Map.entry("yellow_star_fragment", "dropped by the yellow Mula"),
            Map.entry("green_star_fragment", "dropped by the green Mula"),
            Map.entry("black_star_fragment", "dropped by the black Mula"),
            Map.entry("bandana", "shorn off a Boxed Trader"),
            Map.entry("box_costume", "shorn off a Boxed Trader who lost his bandana"),
            Map.entry("villager_block", "a villager pushed down by a piston"),
            Map.entry("mula_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            Map.entry("boxed_trader_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            Map.entry("acorn", "dropped by the Glandouille (and by bone meal on it)"),
            Map.entry("acorn_hat", "flies off a Glandouille's head in a crash"),
            Map.entry("glandouille_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            Map.entry("young_glandouille_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            Map.entry("mossy_glandouille_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            Map.entry("frosty_glandouille_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            Map.entry("boomcart_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            Map.entry("frousseux_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            Map.entry("frousseux_candle_holder", "a tamed Frousseux put to sleep by its owner"),
            // The 10 fixed-wood easel signs are kept for the worlds that have them: the material easel sign replaced them
            Map.entry("oak_easel_sign", "legacy"), Map.entry("spruce_easel_sign", "legacy"),
            Map.entry("birch_easel_sign", "legacy"), Map.entry("jungle_easel_sign", "legacy"),
            Map.entry("acacia_easel_sign", "legacy"), Map.entry("dark_oak_easel_sign", "legacy"),
            Map.entry("mangrove_easel_sign", "legacy"), Map.entry("crimson_easel_sign", "legacy"),
            Map.entry("warped_easel_sign", "legacy"), Map.entry("cherry_easel_sign", "legacy"));

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyItemHasASurvivalRoute(TestContext context) {
        Set<Item> crafted = new HashSet<>();
        for (RecipeEntry<?> recipe : context.getWorld().getServer().getRecipeManager().values()) {
            ItemStack output = recipe.value().getResult(context.getWorld().getRegistryManager());
            if (!output.isEmpty()) crafted.add(output.getItem());
        }
        List<String> missing = new ArrayList<>();
        List<String> notMinable = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            String path = Registries.ITEM.getId(item).getPath();
            if (!Registries.ITEM.getId(item).getNamespace().equals(Steveparty.MOD_ID)) continue;
            if (!crafted.contains(item) && !OTHER_SURVIVAL_ROUTES.containsKey(path)) missing.add(path);
            // A placed block must give something back when mined
            if (item instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                boolean dropsSomething = context.getWorld().getServer().getReloadableRegistries()
                        .getLootTable(block.getLootTableKey()) != LootTable.EMPTY;
                if (!dropsSomething) notMinable.add(path);
            }
        }
        context.assertTrue(missing.isEmpty(), "creative-only items: " + missing);
        context.assertTrue(notMinable.isEmpty(), "blocks without a loot table: " + notMinable);
        context.complete();
    }

    /** The Telescope: a spyglass on a copper ingot on three sticks; not without the spyglass. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void telescopeIsMadeFromASpyglass(TestContext context) {
        ItemStack e = ItemStack.EMPTY;
        ItemStack telescope = result(context, 3, 3,
                e, new ItemStack(Items.SPYGLASS), e,
                e, new ItemStack(Items.COPPER_INGOT), e,
                new ItemStack(Items.STICK), new ItemStack(Items.STICK), new ItemStack(Items.STICK));
        context.assertTrue(telescope.isOf(ModBlocks.TELESCOPE.asItem()) && telescope.getCount() == 1, "telescope, got " + telescope);
        ItemStack without = result(context, 3, 3,
                e, new ItemStack(Items.AMETHYST_SHARD), e,
                e, new ItemStack(Items.COPPER_INGOT), e,
                new ItemStack(Items.STICK), new ItemStack(Items.STICK), new ItemStack(Items.STICK));
        context.assertTrue(!without.isOf(ModBlocks.TELESCOPE.asItem()), "no telescope without a spyglass");
        context.complete();
    }

    /** Plastic blocks: 4 pellets and a dye, nothing else (no amethyst shard). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plasticBlocksAreMouldedFromPelletsAndADye(TestContext context) {
        ItemStack pellets = new ItemStack(ModItems.PLASTIC_PELLETS);
        ItemStack blocks = result(context, 3, 2, pellets, pellets, pellets, pellets, new ItemStack(Items.RED_DYE), ItemStack.EMPTY);
        context.assertTrue(blocks.isOf(ModBlocks.PLASTIC_BLOCKS[java.util.Arrays.asList(ModBlocks.COLORS).indexOf("red")].asItem())
                && blocks.getCount() == 4, "4 red plastic blocks, got " + blocks);
        context.complete();
    }

    /** The Gravity Core takes star fragments of any colour, mixed: not only the rare black ones. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theGravityCoreTakesAnyStarFragments(TestContext context) {
        ItemStack c = new ItemStack(Items.CRYING_OBSIDIAN);
        ItemStack core = result(context, 3, 3,
                c, new ItemStack(ModItems.BLUE_STAR_FRAGMENT), c,
                new ItemStack(ModItems.RED_STAR_FRAGMENT), new ItemStack(Items.HEAVY_CORE), new ItemStack(ModItems.YELLOW_STAR_FRAGMENT),
                c, new ItemStack(ModItems.BLACK_STAR_FRAGMENT), c);
        context.assertTrue(core.isOf(ModBlocks.GRAVITY_CORE.asItem()), "a gravity core from mixed fragments, got " + core);
        context.complete();
    }

    /** A gold nugget mints one coin (never the other way round), and that coin is what a fresh party counts as Pièce. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aGoldNuggetMintsTheCoinOfAFreshParty(TestContext context) {
        ItemStack coin = result(context, 1, 1, new ItemStack(Items.GOLD_NUGGET));
        context.assertTrue(coin.isOf(ModItems.COIN) && coin.getCount() == 1, "1 gold nugget: 1 coin, got " + coin);
        context.assertTrue(result(context, 1, 1, new ItemStack(ModItems.COIN)).isEmpty(), "a coin is not turned back into a nugget");

        BlockPos pos = new BlockPos(1, 1, 1);
        context.setBlockState(pos, ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(pos);
        ItemStack partyCoin = controller.getCurrency(PartyCurrency.COIN);
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(partyCoin, coin), "a fresh party's coin is the crafted coin, got " + partyCoin);
        context.assertTrue(controller.setCurrency(PartyCurrency.COIN, new ItemStack(Items.EMERALD)), "another item may still be picked");
        PartyControllerEntity loaded = new PartyControllerEntity(controller.getPos(), controller.getCachedState());
        loaded.read(controller.createNbt(context.getWorld().getRegistryManager()), context.getWorld().getRegistryManager());
        context.assertTrue(loaded.getCurrency(PartyCurrency.COIN).isOf(Items.EMERALD), "a party with its own coin item keeps it");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void formerlyCreativeOnlyItemsAreCraftable(TestContext context) {
        ItemStack e = ItemStack.EMPTY;
        ItemStack obsidian = new ItemStack(Items.OBSIDIAN);
        ItemStack forge = result(context, 3, 3,
                e, new ItemStack(ModItems.PARTY_STAR), e,
                obsidian, new ItemStack(Items.BLAST_FURNACE), obsidian,
                obsidian, new ItemStack(Items.NETHERITE_INGOT), obsidian);
        context.assertTrue(forge.isOf(ModBlocks.DICE_FORGE.asItem()), "dice forge, got " + forge);

        ItemStack c = new ItemStack(Items.CRYING_OBSIDIAN);
        ItemStack f = new ItemStack(ModItems.BLACK_STAR_FRAGMENT);
        ItemStack core = result(context, 3, 3, c, f, c, f, new ItemStack(Items.HEAVY_CORE), f, c, f, c);
        context.assertTrue(core.isOf(ModBlocks.GRAVITY_CORE.asItem()), "gravity core, got " + core);

        // Any star fragment colour makes the key
        ItemStack key = result(context, 1, 3, new ItemStack(ModItems.GREEN_STAR_FRAGMENT),
                new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.GOLD_NUGGET));
        context.assertTrue(key.isOf(ModItems.SHOPKEEPER_KEY), "shopkeeper key, got " + key);

        ItemStack shoes = result(context, 2, 2, new ItemStack(Items.SLIME_BALL), new ItemStack(Items.LEATHER_BOOTS),
                new ItemStack(Items.RABBIT_FOOT), new ItemStack(Items.SLIME_BALL));
        context.assertTrue(shoes.isOf(ModItems.TRIPLE_JUMP_SHOES), "triple jump shoes, got " + shoes);

        ItemStack cartridge = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(Items.LIME_DYE)).isOf(ModItems.TILE_BEHAVIOR_START), "start cartridge");
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(Items.RED_DYE)).isOf(ModItems.BOARD_SPACE_BEHAVIOR_STOP), "stop cartridge");
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(Items.CHEST)).isOf(ModItems.INVENTORY_CARTRIDGE), "inventory cartridge");
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(Items.PISTON)).isOf(ModItems.ADVANCE_BACK_CARTRIDGE), "move forward / back cartridge");
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(Items.REPEATER)).isOf(ModItems.REPLAY_CARTRIDGE), "roll again cartridge");
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(Items.ENDER_PEARL)).isOf(ModItems.TELEPORT_CARTRIDGE), "teleport cartridge");
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(ModItems.PARTY_STAR)).isOf(ModItems.STAR_CARTRIDGE), "star cartridge");
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(ModItems.ACORN)).isOf(ModItems.GLANDOUILLE_CARTRIDGE), "glandouille cartridge");
        context.assertTrue(result(context, 2, 1, cartridge, new ItemStack(Items.CANDLE)).isOf(ModItems.FROUSSEUX_CARTRIDGE), "frousseux cartridge");

        // The Tile and the Advanced Tile (their cartridges loaded: TileCartridgeGameTests)
        ItemStack white = new ItemStack(ModBlocks.PLASTIC_SLABS[net.minecraft.util.DyeColor.WHITE.getId()]);
        ItemStack tile = result(context, 3, 2, white, new ItemStack(Items.HEAVY_WEIGHTED_PRESSURE_PLATE), white, white, cartridge, white);
        context.assertTrue(tile.isOf(ModBlocks.TILE.asItem()) && tile.getCount() == 1
                && fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents.cartridges(tile).size() == 1, "an equipped tile, got " + tile);
        ItemStack yellow = new ItemStack(ModBlocks.PLASTIC_SLABS[net.minecraft.util.DyeColor.YELLOW.getId()]);
        ItemStack advanced = result(context, 3, 2, yellow, new ItemStack(Items.LIGHT_WEIGHTED_PRESSURE_PLATE), yellow,
                cartridge, new ItemStack(Items.TRAPPED_CHEST), cartridge);
        context.assertTrue(advanced.isOf(ModBlocks.ADVANCED_TILE.asItem()), "advanced tile, got " + advanced);

        // Mini-game pages: paper around a cartridge (a page is linked to pipes like a cartridge to tiles)
        ItemStack paper = new ItemStack(Items.PAPER), none = ItemStack.EMPTY;
        ItemStack pages = result(context, 3, 3, none, paper, none, paper, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), paper, none, paper, none);
        context.assertTrue(pages.isOf(ModItems.MINI_GAME_PAGE) && pages.getCount() == 4, "4 mini game pages, got " + pages);
        context.assertTrue(result(context, 2, 1, new ItemStack(Items.PAPER), new ItemStack(Items.CYAN_DYE)).isEmpty(), "no longer from paper and dye");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void polishedTerracottaIsCraftable(TestContext context) {
        ItemStack red = new ItemStack(Items.RED_TERRACOTTA);
        ItemStack polished = result(context, 2, 2, red, red, red, red);
        context.assertTrue(polished.isOf(ModBlocks.POLISHED_TERRACOTTA_BLOCKS[15].asItem()) && polished.getCount() == 4,
                "4 polished red terracotta, got " + polished);
        ItemStack plain = new ItemStack(Items.TERRACOTTA);
        ItemStack polishedPlain = result(context, 2, 2, plain, plain, plain, plain);
        context.assertTrue(polishedPlain.isOf(ModBlocks.POLISHED_TERRACOTTA_BLOCKS[0].asItem()) && polishedPlain.getCount() == 4,
                "4 polished terracotta, got " + polishedPlain);
        ItemStack p = new ItemStack(ModBlocks.POLISHED_TERRACOTTA_BLOCKS[15]);
        ItemStack bricks = result(context, 2, 2, p, p, p, p);
        context.assertTrue(bricks.isOf(ModBlocks.POLISHED_TERRACOTTA_BRICKS_BLOCKS[15].asItem()), "red terracotta bricks, got " + bricks);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void forgedDiceAreNotMergedIntoPlainOnes(TestContext context) {
        ItemStack plain = new ItemStack(ModItems.DEFAULT_DICE);
        context.assertTrue(result(context, 2, 1, plain, plain.copy()).isOf(ModItems.DOUBLE_DICE), "2 dice make a double dice");
        // Merging would silently drop the forged faces
        ItemStack forged = DiceFacesComponent.createDie(List.of(new ItemStack(ModItems.DICE_FACES.get(1))));
        context.assertTrue(forged.contains(DiceFacesComponent.TYPE), "forged die has faces");
        context.assertTrue(result(context, 2, 1, forged, plain).isEmpty(), "a forged die is not merged");
        context.assertTrue(result(context, 3, 1, forged, plain, plain.copy()).isEmpty(), "a forged die is not merged into a triple");
        context.assertTrue(result(context, 2, 1, new ItemStack(ModItems.DOUBLE_DICE), forged).isEmpty(),
                "a forged die is not added to a double");
        context.complete();
    }

    /** A party card: the Plastic Stud of its colour, paper and redstone, in any order; four cards a craft. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void partyCardsAreMadeOfAStudOfTheirColour(TestContext context) {
        java.util.Map<String, net.minecraft.item.Item> cards = new java.util.LinkedHashMap<>();
        cards.put("blue", ModItems.PARTY_CARD_TURNS);
        cards.put("lime", ModItems.PARTY_CARD_MINIGAME);
        cards.put("orange", ModItems.PARTY_CARD_EVENT);
        cards.put("purple", ModItems.PARTY_CARD_REPEAT);
        cards.put("pink", ModItems.PARTY_CARD_SEQUENCE_START);
        cards.forEach((colour, card) -> {
            ItemStack stud = new ItemStack(net.minecraft.registry.Registries.ITEM.get(Steveparty.id(colour + "_plastic_stud")));
            context.assertTrue(!stud.isEmpty(), "there is a " + colour + " plastic stud");
            ItemStack made = result(context, 3, 1, new ItemStack(Items.REDSTONE), stud, new ItemStack(Items.PAPER));
            context.assertTrue(made.isOf(card) && made.getCount() == 4, colour + " stud + paper + redstone: four cards");
        });
        ItemStack red = new ItemStack(net.minecraft.registry.Registries.ITEM.get(Steveparty.id("red_plastic_stud")));
        context.assertTrue(result(context, 3, 1, red, new ItemStack(Items.PAPER), new ItemStack(Items.REDSTONE)).isEmpty(), "a stud of another colour makes no card");
        context.assertTrue(result(context, 3, 1, new ItemStack(Items.PAPER), new ItemStack(Items.PAPER), new ItemStack(Items.CLOCK)).isEmpty(),
                "the old recipes are gone");
        context.complete();
    }
}
