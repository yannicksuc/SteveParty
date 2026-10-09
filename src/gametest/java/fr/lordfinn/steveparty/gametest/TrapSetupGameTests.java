package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TrapSetupComponent;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.custom.TrapPowerUpItem;
import fr.lordfinn.steveparty.payloads.custom.TrapSetupPayloads;
import fr.lordfinn.steveparty.powerups.PowerUps;
import fr.lordfinn.steveparty.powerups.effects.TrapKind;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Trap item, like a book and quill: unsigned it steals 10 coins; signed (the setup screen's payload), it does what
 * its signer chose, for good. Traps alike stack; a signed Trap is copied in the crafting grid with unsigned ones.
 */
public class TrapSetupGameTests implements FabricGameTest {
    private static ItemStack trap() {
        return new ItemStack(PowerUps.TRAP.item());
    }

    private static ItemStack signed(TrapKind kind, int amount, String signer, UUID id) {
        ItemStack stack = trap();
        stack.set(ModComponents.TRAP_SETUP, new TrapSetupComponent(kind, amount, signer, id));
        return stack;
    }

    /** An unsigned Trap is the coin trap; its item is the Trap's own. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anUnsignedTrapStealsCoins(TestContext context) {
        context.assertTrue(trap().getItem() instanceof TrapPowerUpItem, "the Trap's item");
        context.assertFalse(TrapSetupComponent.isSigned(trap()), "unsigned");
        context.assertEquals(TrapSetupComponent.effectOf(trap()), new TrapSetupComponent.Effect(TrapKind.COINS, 10), "10 coins");
        context.complete();
    }

    /** Signing sets its effect (amount within bounds) and signer; a signed Trap can't be signed again. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void signingLocksTheSetup(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            player.setStackInHand(Hand.MAIN_HAND, trap().copyWithCount(3));
            new TrapSetupPayloads.Sign(Hand.MAIN_HAND, TrapKind.BACK.ordinal(), 99).handle(player);
            ItemStack held = player.getMainHandStack();
            TrapSetupComponent setup = held.get(ModComponents.TRAP_SETUP);
            context.assertTrue(setup != null, "signed");
            context.assertEquals(setup.effect(), new TrapSetupComponent.Effect(TrapKind.BACK, TrapKind.BACK.maxAmount), "back, at most 10");
            context.assertEquals(setup.signerId(), player.getUuid(), "its signer");
            context.assertEquals(held.getCount(), 3, "the whole stack signed");

            new TrapSetupPayloads.Sign(Hand.MAIN_HAND, TrapKind.COINS.ordinal(), 5).handle(player);
            context.assertEquals(player.getMainHandStack().get(ModComponents.TRAP_SETUP), setup, "can't be changed any more");

            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BOOK));
            new TrapSetupPayloads.Sign(Hand.MAIN_HAND, TrapKind.COINS.ordinal(), 5).handle(player);
            context.assertFalse(player.getMainHandStack().contains(ModComponents.TRAP_SETUP), "only a Trap is signed");
        } finally {
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /** Traps alike stack (unsigned ones, signed ones with the same setup); a signed one never with an unsigned one. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void trapsAlikeStack(TestContext context) {
        UUID id = UUID.randomUUID();
        context.assertTrue(trap().getMaxCount() > 1, "Traps stack");
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(trap(), trap()), "two unsigned Traps");
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(signed(TrapKind.BACK, 3, "a", id), signed(TrapKind.BACK, 3, "a", id)),
                "two Traps signed alike");
        context.assertFalse(ItemStack.areItemsAndComponentsEqual(signed(TrapKind.BACK, 3, "a", id), trap()), "signed and unsigned");
        context.assertFalse(ItemStack.areItemsAndComponentsEqual(signed(TrapKind.BACK, 3, "a", id), signed(TrapKind.BACK, 4, "a", id)),
                "other amount");
        context.complete();
    }

    /** A signed Trap + unsigned ones: as many signed copies, the signed one left in the grid. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aSignedTrapIsCopied(TestContext context) {
        UUID id = UUID.randomUUID();
        ItemStack original = signed(TrapKind.SKIP_TURN, 0, "Steve", id);
        CraftingRecipeInput input = CraftingRecipeInput.create(3, 1, List.of(trap(), original, trap()));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        context.assertTrue(recipe.isPresent(), "a recipe");
        ItemStack result = recipe.get().value().craft(input, context.getWorld().getRegistryManager());
        context.assertEquals(result.getCount(), 2, "two copies");
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(result, original), "the same setup and signer");
        DefaultedList<ItemStack> remainder = recipe.get().value().getRemainder(input);
        context.assertTrue(ItemStack.areItemsAndComponentsEqual(remainder.get(1), original), "the signed Trap stays in the grid");

        // No copy without a signed Trap, nor with two different signed ones
        context.assertTrue(match(context, trap(), trap()).isEmpty(), "two unsigned Traps: nothing");
        context.assertTrue(match(context, original, signed(TrapKind.BACK, 2, "Alex", UUID.randomUUID()), trap()).isEmpty(),
                "two signed Traps: nothing");
        context.assertTrue(match(context, original).isEmpty(), "a signed Trap alone: nothing");
        context.complete();
    }

    private static Optional<RecipeEntry<CraftingRecipe>> match(TestContext context, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(grid.length, 1, List.of(grid));
        return context.getWorld().getServer().getRecipeManager().getFirstMatch(RecipeType.CRAFTING, input, context.getWorld())
                .filter(entry -> !entry.value().craft(input, context.getWorld().getRegistryManager()).isEmpty());
    }
}
