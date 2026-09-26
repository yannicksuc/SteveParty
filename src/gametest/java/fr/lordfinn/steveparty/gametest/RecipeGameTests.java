package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
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
import net.minecraft.recipe.ServerRecipeManager;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.loot.LootTable;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.display.SlotDisplayContexts;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.context.ContextParameterMap;
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
        ServerRecipeManager recipes = context.getWorld().getServer().getRecipeManager();
        Set<RegistryKey<Recipe<?>>> unlocked = new HashSet<>();
        for (AdvancementEntry advancement : context.getWorld().getServer().getAdvancementLoader().getAdvancements()) {
            for (RegistryKey<Recipe<?>> key : advancement.value().rewards().recipes()) {
                // An advancement rewarding a recipe id that doesn't exist never shows that recipe in the book
                if (key.getValue().getNamespace().equals(Steveparty.MOD_ID) || advancement.id().getNamespace().equals(Steveparty.MOD_ID))
                    context.assertTrue(recipes.get(key).isPresent(), advancement.id() + " unlocks a missing recipe " + key.getValue());
                unlocked.add(key);
            }
        }
        for (RecipeEntry<?> recipe : recipes.values()) {
            if (!recipe.id().getValue().getNamespace().equals(Steveparty.MOD_ID) || recipe.value().isIgnoredInRecipeBook()) continue;
            context.assertTrue(unlocked.contains(recipe.id()), "no advancement unlocks " + recipe.id().getValue());
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
            Map.entry("bandana", "shorn off a Hiding Trader"),
            Map.entry("villager_block", "a villager pushed down by a piston"),
            Map.entry("mula_spawn_egg", "spawn eggs are creative-only, like vanilla ones"),
            // The 10 fixed-wood easel signs are kept for the worlds that have them: the material easel sign replaced them
            Map.entry("oak_easel_sign", "legacy"), Map.entry("spruce_easel_sign", "legacy"),
            Map.entry("birch_easel_sign", "legacy"), Map.entry("jungle_easel_sign", "legacy"),
            Map.entry("acacia_easel_sign", "legacy"), Map.entry("dark_oak_easel_sign", "legacy"),
            Map.entry("mangrove_easel_sign", "legacy"), Map.entry("crimson_easel_sign", "legacy"),
            Map.entry("warped_easel_sign", "legacy"), Map.entry("cherry_easel_sign", "legacy"));

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyItemHasASurvivalRoute(TestContext context) {
        ContextParameterMap displayContext = SlotDisplayContexts.createParameters(context.getWorld());
        Set<Item> crafted = new HashSet<>();
        for (RecipeEntry<?> recipe : context.getWorld().getServer().getRecipeManager().values()) {
            for (RecipeDisplay display : recipe.value().getDisplays()) {
                display.result().getStacks(displayContext).forEach(stack -> crafted.add(stack.getItem()));
            }
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
                boolean dropsSomething = block.getLootTableKey()
                        .map(key -> context.getWorld().getServer().getReloadableRegistries().getLootTable(key) != LootTable.EMPTY)
                        .orElse(false);
                if (!dropsSomething) notMinable.add(path);
            }
        }
        context.assertTrue(missing.isEmpty(), "creative-only items: " + missing);
        context.assertTrue(notMinable.isEmpty(), "blocks without a loot table: " + notMinable);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void formerlyCreativeOnlyItemsAreCraftable(TestContext context) {
        ItemStack e = ItemStack.EMPTY;
        ItemStack obsidian = new ItemStack(Items.OBSIDIAN);
        ItemStack forge = result(context, 3, 3,
                e, new ItemStack(ModItems.POWER_STAR), e,
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

        ItemStack carpet = new ItemStack(Items.RED_CARPET);
        ItemStack pellets = new ItemStack(ModItems.PLASTIC_PELLETS);
        ItemStack tile = result(context, 3, 2, carpet, new ItemStack(Items.BLUE_CARPET), carpet,
                pellets, new ItemStack(Items.LIGHT_WEIGHTED_PRESSURE_PLATE), pellets);
        context.assertTrue(tile.isOf(ModBlocks.TILE.asItem()) && tile.getCount() == 2, "two tiles, got " + tile);
        ItemStack gold = new ItemStack(Items.GOLD_INGOT);
        ItemStack advanced = result(context, 3, 3, e, new ItemStack(Items.COMPARATOR), e,
                gold, new ItemStack(ModBlocks.TILE), gold, gold, new ItemStack(Items.CHEST), gold);
        context.assertTrue(advanced.isOf(ModBlocks.ADVANCED_TILE.asItem()), "advanced tile, got " + advanced);

        ItemStack book = new ItemStack(Items.BOOK);
        ItemStack pearl = new ItemStack(Items.ENDER_PEARL);
        context.assertTrue(result(context, 3, 1, book, pearl, new ItemStack(Items.COMPASS)).isOf(ModItems.HERE_WE_GO_BOOK), "here we go book");
        context.assertTrue(result(context, 3, 1, pearl, new ItemStack(Items.LEAD), book).isOf(ModItems.HERE_WE_COME_BOOK), "here we come book");
        context.assertTrue(result(context, 2, 1, new ItemStack(Items.PAPER), new ItemStack(Items.CYAN_DYE)).isOf(ModItems.MINI_GAME_PAGE), "mini game page");
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
}
