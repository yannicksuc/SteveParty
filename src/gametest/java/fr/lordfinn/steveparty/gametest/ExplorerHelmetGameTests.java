package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.world.GameMode;

import java.util.List;
import java.util.Optional;

/** The Explorer's Helmet: its recipe, worn on the head without armour, and who sees the board view. */
public class ExplorerHelmetGameTests implements SteveGameTest {

    private static ItemStack craft(TestContext context, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 2, List.of(grid));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        return recipe.map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    /** Leather round a glowstone lamp and a copper ingot; not without the glowstone. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void helmetIsMadeOfLeatherGlowstoneAndCopper(TestContext context) {
        ItemStack l = new ItemStack(Items.LEATHER), c = new ItemStack(Items.COPPER_INGOT);
        ItemStack helmet = craft(context, l, new ItemStack(Items.GLOWSTONE_DUST), l, l, c, l);
        context.assertTrue(helmet.isOf(ModItems.EXPLORER_HELMET) && helmet.getCount() == 1, "an Explorer's Helmet, got " + helmet);
        context.assertTrue(ExplorerHelmet.lit(helmet), "a new helmet has its lamp lit");
        ItemStack without = craft(context, l, ItemStack.EMPTY, l, l, c, l);
        context.assertTrue(!without.isOf(ModItems.EXPLORER_HELMET), "no helmet without the glowstone");
        context.complete();
    }

    /** A right click puts it on the head, like any helmet, and it gives no armour. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void helmetIsWornOnTheHeadWithoutArmour(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.EXPLORER_HELMET));
        player.getMainHandStack().use(context.getWorld(), player, Hand.MAIN_HAND);
        context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.EXPLORER_HELMET), "the helmet is on the head");
        context.assertTrue(player.getMainHandStack().isEmpty(), "the hand is empty once it is worn");
        context.assertTrue(ExplorerHelmet.wears(player), "worn");
        context.assertTrue(player.getArmor() == 0, "no armour points, got " + player.getArmor());
        context.complete();
    }

    /** The board view: a board tool in hand shows it, a lit helmet shows it with the details, the lamp off hides it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boardViewFollowsTheHelmetLampAndTheTools(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        context.assertTrue(ExplorerHelmet.view(player) == ExplorerHelmet.View.NONE, "nothing in hand, no helmet: no board view");

        player.equipStack(EquipmentSlot.HEAD, new ItemStack(ModItems.EXPLORER_HELMET));
        context.assertTrue(ExplorerHelmet.view(player) == ExplorerHelmet.View.HELMET, "a lit helmet: the board view with details");
        context.assertTrue(ExplorerHelmet.view(player).shown() && ExplorerHelmet.view(player).details(), "shown, with details");

        ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
        context.assertTrue(!ExplorerHelmet.switchLamp(helmet), "the key puts the lamp out");
        context.assertTrue(ExplorerHelmet.view(player) == ExplorerHelmet.View.NONE, "lamp off: no board view");

        player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.WRENCH));
        context.assertTrue(ExplorerHelmet.view(player) == ExplorerHelmet.View.NONE, "lamp off, the Wrench in hand: the Wrench shows no board view");
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.TILE_LINKER_BRUSH));
        context.assertTrue(ExplorerHelmet.view(player) == ExplorerHelmet.View.TOOL, "lamp off but the brush in hand: the board view, no details");

        context.assertTrue(ExplorerHelmet.switchLamp(helmet), "the key lights the lamp again");
        context.assertTrue(ExplorerHelmet.view(player) == ExplorerHelmet.View.HELMET, "lit again, with the brush: the details");

        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.TILE_LINKER_BRUSH));
        player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        context.assertTrue(ExplorerHelmet.view(player) == ExplorerHelmet.View.TOOL, "another helmet, the brush in hand: the board view only");
        context.assertTrue(!ExplorerHelmet.lit(new ItemStack(Items.LEATHER_HELMET)), "only the Explorer's Helmet has a lamp");
        context.complete();
    }
}
