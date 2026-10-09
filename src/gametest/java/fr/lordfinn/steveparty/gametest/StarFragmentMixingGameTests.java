package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.goals.MulaBrain;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.minecraft.util.DyeColor.*;

/**
 * The 16 colours of star fragments: 6 are dropped by the Mulas, the 10 others are mixed from them like dyes (as many
 * fragments out as in), and each colour packs into its block.
 */
public class StarFragmentMixingGameTests implements FabricGameTest {

    private record Mix(DyeColor result, DyeColor... inputs) {
        /** The ingredients whatever their order. */
        List<DyeColor> key() {
            List<DyeColor> sorted = new ArrayList<>(Arrays.asList(inputs));
            sorted.sort(null);
            return sorted;
        }
    }

    /** The mixing table (the wiki's): result, ingredients. */
    private static final List<Mix> MIXES = List.of(
            new Mix(WHITE, RED, BLUE, YELLOW),
            new Mix(ORANGE, RED, YELLOW),
            new Mix(GREEN, BLUE, YELLOW),
            new Mix(PURPLE, RED, BLUE),
            new Mix(CYAN, BLUE, GREEN),
            new Mix(LIGHT_BLUE, BLUE, WHITE),
            new Mix(LIME, GREEN, WHITE),
            new Mix(PINK, RED, WHITE),
            new Mix(MAGENTA, PURPLE, PINK),
            new Mix(MAGENTA, BLUE, RED, PINK),
            new Mix(MAGENTA, BLUE, RED, RED, WHITE),
            new Mix(GRAY, BLACK, WHITE),
            new Mix(LIGHT_GRAY, GRAY, WHITE),
            new Mix(LIGHT_GRAY, BLACK, WHITE, WHITE),
            new Mix(BROWN, ORANGE, BLACK),
            new Mix(BROWN, RED, GREEN));

    private static List<ItemStack> fragments(List<DyeColor> colours) {
        List<ItemStack> stacks = new ArrayList<>();
        for (DyeColor colour : colours) stacks.add(new ItemStack(ModItems.starFragment(colour)));
        return stacks;
    }

    private static List<RecipeEntry<CraftingRecipe>> craftingRecipes(TestContext context) {
        List<RecipeEntry<CraftingRecipe>> crafting = new ArrayList<>();
        for (RecipeEntry<?> entry : context.getWorld().getServer().getRecipeManager().values()) {
            if (entry.value() instanceof CraftingRecipe) {
                @SuppressWarnings("unchecked")
                RecipeEntry<CraftingRecipe> cast = (RecipeEntry<CraftingRecipe>) entry;
                crafting.add(cast);
            }
        }
        return crafting;
    }

    /** Every recipe (vanilla ones included) these stacks match in a crafting grid, in any arrangement. */
    private static List<RecipeEntry<CraftingRecipe>> matches(TestContext context, List<RecipeEntry<CraftingRecipe>> recipes,
                                                             List<ItemStack> stacks) {
        List<ItemStack> grid = new ArrayList<>(stacks);
        int width = stacks.size() <= 3 ? stacks.size() : stacks.size() == 4 ? 2 : 3;
        int height = (stacks.size() + width - 1) / width;
        while (grid.size() < width * height) grid.add(ItemStack.EMPTY);
        CraftingRecipeInput input = CraftingRecipeInput.create(width, height, grid);
        List<RecipeEntry<CraftingRecipe>> found = new ArrayList<>();
        for (RecipeEntry<CraftingRecipe> recipe : recipes) {
            if (recipe.value().matches(input, context.getWorld())) found.add(recipe);
        }
        return found;
    }

    private static ItemStack craft(TestContext context, List<RecipeEntry<CraftingRecipe>> recipes, List<ItemStack> stacks) {
        List<RecipeEntry<CraftingRecipe>> found = matches(context, recipes, stacks);
        if (found.size() != 1) return ItemStack.EMPTY;
        int width = stacks.size() <= 3 ? stacks.size() : stacks.size() == 4 ? 2 : 3;
        List<ItemStack> grid = new ArrayList<>(stacks);
        while (grid.size() % width != 0) grid.add(ItemStack.EMPTY);
        return found.get(0).value().craft(CraftingRecipeInput.create(width, grid.size() / width, grid),
                context.getWorld().getRegistryManager());
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyMixGivesAsManyFragmentsOfTheMixedColour(TestContext context) {
        List<RecipeEntry<CraftingRecipe>> recipes = craftingRecipes(context);
        for (Mix mix : MIXES) {
            List<DyeColor> inputs = new ArrayList<>(Arrays.asList(mix.inputs()));
            for (int turn = 0; turn < 2; turn++) {          // shapeless: in the table's order, then reversed
                ItemStack result = craft(context, recipes, fragments(inputs));
                context.assertTrue(result.isOf(ModItems.starFragment(mix.result())) && result.getCount() == inputs.size(),
                        inputs + " should give " + inputs.size() + " " + mix.result().getName() + " fragments, got " + result);
                Collections.reverse(inputs);
            }
        }
        // White is the three primaries, three fragments in, three out
        ItemStack white = craft(context, recipes, fragments(List.of(YELLOW, RED, BLUE)));
        context.assertTrue(white.isOf(ModItems.WHITE_STAR_FRAGMENT) && white.getCount() == 3, "3 white fragments, got " + white);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sixteenColoursAreReachableFromTheMulaDrops(TestContext context) {
        List<RecipeEntry<CraftingRecipe>> recipes = craftingRecipes(context);
        Set<DyeColor> reachable = EnumSet.noneOf(DyeColor.class);
        for (MulaEntity.MulaVariant variant : MulaEntity.MulaVariant.values()) {
            int index = ModItems.STAR_FRAGMENTS.indexOf(variant.getFragmentItem());
            context.assertTrue(index >= 0, variant + " drops a star fragment");
            reachable.add(DyeColor.byId(index));
        }
        context.assertTrue(reachable.equals(EnumSet.of(BLUE, PURPLE, RED, YELLOW, GREEN, BLACK)),
                "the Mulas drop blue, purple, red, yellow, green and black, got " + reachable);
        boolean grown = true;
        while (grown) {
            grown = false;
            for (Mix mix : MIXES) {
                if (reachable.contains(mix.result()) || !reachable.containsAll(Arrays.asList(mix.inputs()))) continue;
                ItemStack result = craft(context, recipes, fragments(Arrays.asList(mix.inputs())));
                if (result.isOf(ModItems.starFragment(mix.result()))) {
                    reachable.add(mix.result());
                    grown = true;
                }
            }
        }
        context.assertTrue(reachable.size() == 16, "every colour is made from the Mulas' drops, missing "
                + EnumSet.complementOf(EnumSet.copyOf(reachable)));
        // Without the rare black Mula: everything but gray and light gray
        Set<DyeColor> common = EnumSet.of(BLUE, PURPLE, RED, YELLOW, GREEN);
        grown = true;
        while (grown) {
            grown = false;
            for (Mix mix : MIXES) {
                if (!common.contains(mix.result()) && common.containsAll(Arrays.asList(mix.inputs()))) {
                    common.add(mix.result());
                    grown = true;
                }
            }
        }
        context.assertTrue(EnumSet.complementOf(EnumSet.copyOf(common)).equals(EnumSet.of(BLACK, GRAY, LIGHT_GRAY)),
                "only gray and light gray need a black fragment, got " + common);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyColourPacksIntoItsBlockAndBack(TestContext context) {
        List<RecipeEntry<CraftingRecipe>> recipes = craftingRecipes(context);
        context.assertTrue(ModItems.STAR_FRAGMENTS.size() == 16 && ModBlocks.STAR_FRAGMENTS_BLOCKS.size() == 16
                && new HashSet<>(ModItems.STAR_FRAGMENTS).size() == 16 && new HashSet<>(ModBlocks.STAR_FRAGMENTS_BLOCKS).size() == 16,
                "16 fragments and 16 blocks");
        for (DyeColor dye : DyeColor.values()) {
            Item fragment = ModItems.starFragment(dye);
            Item block = ModBlocks.starFragmentsBlock(dye).asItem();
            String name = dye.getName();
            context.assertTrue(Registries.ITEM.getId(fragment).equals(Steveparty.id(name + "_star_fragment")),
                    name + " fragment is at its dye's index");
            context.assertTrue(Registries.ITEM.getId(block).equals(Steveparty.id(name + "_star_fragments_block")),
                    name + " block is at its dye's index");
            List<ItemStack> nine = new ArrayList<>();
            for (int i = 0; i < 9; i++) nine.add(new ItemStack(fragment));
            ItemStack packed = craft(context, recipes, nine);
            context.assertTrue(packed.isOf(block) && packed.getCount() == 1, "9 " + name + " fragments make their block, got " + packed);
            ItemStack unpacked = craft(context, recipes, List.of(new ItemStack(block)));
            context.assertTrue(unpacked.isOf(fragment) && unpacked.getCount() == 9, "a " + name + " block gives 9 fragments back, got " + unpacked);
            // 8 are not enough, and a block of another colour doesn't come out of a mixed grid
            context.assertTrue(matches(context, recipes, nine.subList(0, 8)).isEmpty(), "8 " + name + " fragments make nothing");
        }
        context.complete();
    }

    /** Every handful of 1 to 4 fragments matches one recipe at most, and exactly the table's. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noTwoRecipesShareTheSameFragments(TestContext context) {
        List<RecipeEntry<CraftingRecipe>> recipes = craftingRecipes(context);
        Map<List<DyeColor>, DyeColor> table = new HashMap<>();
        for (Mix mix : MIXES) {
            context.assertTrue(table.put(mix.key(), mix.result()) == null, "two mixes with the same ingredients: " + mix.key());
        }
        DyeColor[] dyes = DyeColor.values();
        int checked = 0;
        List<DyeColor> hand = new ArrayList<>();
        int[] index = new int[4];
        for (int size = 1; size <= 4; size++) {
            Arrays.fill(index, 0);
            while (true) {
                hand.clear();
                for (int i = 0; i < size; i++) hand.add(dyes[index[i]]);
                List<RecipeEntry<CraftingRecipe>> found = matches(context, recipes, fragments(hand));
                DyeColor expected = table.get(hand);
                context.assertTrue(found.size() == (expected == null ? 0 : 1),
                        hand + " matches " + found.stream().map(r -> r.id().toString()).toList()
                                + ", expected " + (expected == null ? "nothing" : expected.getName()));
                checked++;
                // next multiset (non-decreasing indexes)
                int p = size - 1;
                while (p >= 0 && index[p] == dyes.length - 1) p--;
                if (p < 0) break;
                int v = index[p] + 1;
                for (int i = p; i < size; i++) index[i] = v;
            }
        }
        context.assertTrue(checked == 16 + 136 + 816 + 3876, "every handful checked, got " + checked);
        context.complete();
    }

    /** The mixed colours are star fragments everywhere the dropped ones are. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mixedColoursAreStarFragmentsEverywhere(TestContext context) {
        TagKey<Item> tag = TagKey.of(RegistryKeys.ITEM, Steveparty.id("star_fragments"));
        var brewing = context.getWorld().getBrewingRecipeRegistry();
        ItemStack awkward = PotionContentsComponent.createStack(Items.POTION, Potions.AWKWARD);
        for (Item fragment : ModItems.STAR_FRAGMENTS) {
            ItemStack stack = new ItemStack(fragment);
            context.assertTrue(stack.isIn(tag), fragment + " is in #steveparty:star_fragments");
            context.assertTrue(DiceForgeBlockEntity.isStarFragment(stack), fragment + " feeds the Dice Forge");
            context.assertTrue(MulaBrain.isStarFragment(fragment), fragment + " catches a Mula's eye");
            context.assertTrue(DiceForgeBlockEntity.isInfiniteFragment(stack) == (fragment == ModItems.BLACK_STAR_FRAGMENT),
                    "only black is the endless fragment: " + fragment);
            PotionContentsComponent potion = brewing.craft(stack, awkward).get(DataComponentTypes.POTION_CONTENTS);
            context.assertTrue(potion != null && potion.matches(Potions.LUCK), fragment + " brews Luck");
        }
        // Dice Forge: 4 mixed colours make 4 different colours; twice the same mixed colour is refused
        SimpleInventory forge = new SimpleInventory(DiceForgeBlockEntity.FIRST_FRAGMENT_SLOT + DiceForgeBlockEntity.FRAGMENT_SLOTS);
        Item[] mixed = {ModItems.WHITE_STAR_FRAGMENT, ModItems.ORANGE_STAR_FRAGMENT, ModItems.CYAN_STAR_FRAGMENT, ModItems.PINK_STAR_FRAGMENT};
        for (int i = 0; i < 4; i++) {
            int slot = DiceForgeBlockEntity.FIRST_FRAGMENT_SLOT + i;
            context.assertTrue(DiceForgeBlockEntity.isValidForSlot(forge, slot, new ItemStack(mixed[i]), true), mixed[i] + " fits a fragment slot");
            forge.setStack(slot, new ItemStack(mixed[i]));
        }
        context.assertTrue(!DiceForgeBlockEntity.isValidForSlot(forge, DiceForgeBlockEntity.FIRST_FRAGMENT_SLOT + 1,
                new ItemStack(ModItems.WHITE_STAR_FRAGMENT), true), "white is already in another slot");
        context.assertTrue(DiceForgeBlockEntity.countAltitudeFragments(forge) == 4, "a mixed fragment lifts the core like a common one");

        // The Shopkeeper Key takes any colour
        List<RecipeEntry<CraftingRecipe>> recipes = craftingRecipes(context);
        CraftingRecipeInput key = CraftingRecipeInput.create(1, 3, List.of(new ItemStack(ModItems.WHITE_STAR_FRAGMENT),
                new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.GOLD_NUGGET)));
        boolean keyed = recipes.stream().anyMatch(r -> r.value().matches(key, context.getWorld())
                && r.value().craft(key, context.getWorld().getRegistryManager()).isOf(ModItems.SHOPKEEPER_KEY));
        context.assertTrue(keyed, "a white fragment makes the Shopkeeper Key");
        context.complete();
    }
}
