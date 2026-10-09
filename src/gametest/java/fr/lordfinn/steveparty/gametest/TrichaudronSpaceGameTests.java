package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.service.TileInfos;
import fr.lordfinn.steveparty.service.TrichaudronPrizes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Optional;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The Trichaudron space: the chosen head's prize goes to the player and leaves the stock, heads come out empty past
 * the stock, an empty stock sleeps, the Trichaudron and its die are board actors removed at the end (or when the party
 * ends in the middle), the panel lists the prizes, the recipe.
 */
public class TrichaudronSpaceGameTests implements FabricGameTest {
    private static final String BATCH = "trichaudron_space";
    private static final BlockPos TILE = new BlockPos(3, 1, 3);
    private static final int WHOLE = TrichaudronPrizes.WHOLE + 20;

    private record Show(ServerPlayerEntity player, MobEntity token, PartyControllerEntity party, BoardSpaceBlockEntity tile,
                        boolean[] done) {
        ItemStack cartridge() {
            return tile.getActiveCartridgeItemStack();
        }
    }

    private static ItemStack stocked(ItemStack... prizes) {
        ItemStack cartridge = new ItemStack(ModItems.TRICHAUDRON_CARTRIDGE);
        TrichaudronCartridgeItem.setPrizes(cartridge, List.of(prizes));
        return cartridge;
    }

    /** A party of one player, its token on a Trichaudron tile holding {@code cartridge}; the show started if it can. */
    private static Show show(TestContext context, ItemStack cartridge, TrichaudronPrizes.Start expected) {
        ServerPlayerEntity player = player(context);
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge);
        MobEntity token = token(context, TILE, player.getUuid());
        PartyControllerEntity party = DiceTestKit.party(context, player.getUuid(), token);
        boolean[] done = {false};
        TrichaudronPrizes.Start start = TrichaudronPrizes.start(context.getWorld(), tile.getPos(), token, party, () -> done[0] = true);
        context.assertTrue(start == expected, "start: " + start + ", expected " + expected);
        if (start == TrichaudronPrizes.Start.STARTED) {
            TrichaudronEntity actor = TrichaudronPrizes.actor(token);
            context.assertTrue(actor != null && actor.isBoardActor() && actor.isInvulnerable() && !actor.shouldSave(),
                    "a board actor: invulnerable, never saved");
        }
        return new Show(player, token, party, tile, done);
    }

    /** Its player stops the slow die by hitting it: the head of that face gives its prize, which leaves the stock. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theChosenHeadGivesItsPrize(TestContext context) {
        Show show = show(context, stocked(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.APPLE, 1)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        TrichaudronEntity actor = TrichaudronPrizes.actor(token);
        List<ItemStack> heads = TrichaudronPrizes.heads(token);
        context.assertTrue(heads.size() == 3 && heads.stream().noneMatch(ItemStack::isEmpty), "4 prizes: three full heads");
        context.assertTrue(heads.stream().map(ItemStack::getItem).distinct().count() == 3, "three different prizes");
        when(context, () -> {
            DiceEntity die = TrichaudronPrizes.die(token);
            return die != null && die.age > DiceEntity.THROW_GRACE_TICKS + 2;
        }, WHOLE, "the slow die rolls under the heads", () -> {
            DiceEntity die = TrichaudronPrizes.die(token);
            context.assertTrue(die.isShowDie() && !die.shouldSave() && die.isInvulnerable(), "a board prop, never saved");
            hit(context, die, show.player());
            when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.GIVE, 60, "the die picks a head", () -> {
                int face = die.getRolledFaces().getFirst().value();
                context.assertTrue(face >= 1 && face <= 3, "a face 1 to 3");
                ItemStack prize = heads.get(face - 1);
                when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                    context.assertEquals(count(show.player(), prize.getItem()), prize.getCount(), "the chosen head's prize");
                    List<ItemStack> left = TrichaudronCartridgeItem.prizes(show.cartridge());
                    context.assertEquals(left.size(), 3, "one prize less in stock");
                    context.assertTrue(left.stream().noneMatch(item -> item.isOf(prize.getItem())), "the prize won left the stock");
                    context.assertTrue(actor.isRemoved() && die.isRemoved(), "the Trichaudron and its die are gone");
                    context.assertFalse(TrichaudronPrizes.isRunning(token), "over");
                    context.complete();
                });
            });
        });
    }

    /** Fewer than three prizes: the other heads come out empty; picking one gives nothing and keeps the stock. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void emptyHeadsPastTheStock(TestContext context) {
        Show show = show(context, stocked(new ItemStack(Items.DIAMOND, 4)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        List<ItemStack> heads = TrichaudronPrizes.heads(token);
        context.assertEquals((int) heads.stream().filter(ItemStack::isEmpty).count(), 2, "one prize: two empty heads");
        int emptyFace = heads.indexOf(heads.stream().filter(ItemStack::isEmpty).findFirst().orElseThrow()) + 1;
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, WHOLE, "the choice", () -> {
            TrichaudronPrizes.pick(token, emptyFace);
            when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(show.player(), Items.DIAMOND), 0, "an empty head: nothing");
                context.assertEquals(TrichaudronCartridgeItem.prizes(show.cartridge()).size(), 1, "the stock is untouched");
                context.complete();
            });
        });
    }

    /** The last prize won: the stock is empty, the space sleeps (no show, a plain landing, « empty » panel). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theLastPrizeThenItSleeps(TestContext context) {
        Show show = show(context, stocked(new ItemStack(Items.EMERALD, 7)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        List<ItemStack> heads = TrichaudronPrizes.heads(token);
        int prizeFace = heads.indexOf(heads.stream().filter(item -> !item.isEmpty()).findFirst().orElseThrow()) + 1;
        context.assertEquals(TileFeedback.landingOf(show.tile()), TileFeedback.Landing.TRICHAUDRON, "a prize space");
        TileInfo info = TileInfos.of(show.tile());
        context.assertEquals(info.ring().size(), 1, "its prize circles over the space");
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, WHOLE, "the choice", () -> {
            TrichaudronPrizes.pick(token, prizeFace);
            when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(show.player(), Items.EMERALD), 7, "the 7 emeralds won");
                context.assertTrue(TrichaudronCartridgeItem.isEmpty(show.cartridge()), "the stock is empty");
                context.assertEquals(TileFeedback.landingOf(show.tile()), TileFeedback.Landing.DEFAULT, "it sleeps: a plain landing");
                TileInfo asleep = TileInfos.of(show.tile());
                context.assertTrue(asleep.ring().isEmpty() && asleep.lines().stream().anyMatch(line ->
                        line.text().getContent() instanceof TranslatableTextContent content
                                && content.getKey().equals("hud.steveparty.tile_info.trichaudron.empty")), "its panel: empty");
                boolean[] again = {false};
                context.assertTrue(TrichaudronPrizes.start(context.getWorld(), show.tile().getPos(), token, show.party(),
                        () -> again[0] = true) == TrichaudronPrizes.Start.EMPTY, "no show any more");
                context.assertTrue(TrichaudronPrizes.actor(token) == null && !again[0], "no Trichaudron");
                context.complete();
            });
        });
    }

    /** An empty cartridge: the space sleeps from the start. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void anEmptyStockSleeps(TestContext context) {
        Show show = show(context, new ItemStack(ModItems.TRICHAUDRON_CARTRIDGE), TrichaudronPrizes.Start.EMPTY);
        context.assertTrue(TrichaudronPrizes.actor(show.token()) == null && !show.done()[0], "no Trichaudron, nothing to wait for");
        context.assertFalse(show.tile().getBoardSpaceBehavior().keepsTurn(show.token()), "the turn goes on");
        context.complete();
    }

    /** The party ending in the middle: the show stops, its actors are removed. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void partyEndingRemovesIt(TestContext context) {
        Show show = show(context, stocked(new ItemStack(Items.DIAMOND)), TrichaudronPrizes.Start.STARTED);
        TrichaudronEntity actor = TrichaudronPrizes.actor(show.token());
        context.assertTrue(show.tile().getBoardSpaceBehavior().keepsTurn(show.token()), "it holds the turn");
        when(context, () -> TrichaudronPrizes.die(show.token()) != null, WHOLE, "its die", () -> {
            DiceEntity die = TrichaudronPrizes.die(show.token());
            context.removeBlock(DiceTestKit.CONTROLLER);
            context.waitAndRun(2, () -> {
                context.assertTrue(show.done()[0] && !TrichaudronPrizes.isRunning(show.token()), "stopped");
                context.assertTrue(actor.isRemoved() && die.isRemoved(), "the Trichaudron and its die are gone");
                context.assertEquals(TrichaudronCartridgeItem.prizes(show.cartridge()).size(), 1, "nothing given, nothing taken");
                context.complete();
            });
        });
    }

    /** An Inventory Cartridge and a lava bucket make it (the bucket comes back). */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void itsRecipe(TestContext context) {
        CraftingRecipeInput input = CraftingRecipeInput.create(2, 1,
                List.of(new ItemStack(ModItems.INVENTORY_CARTRIDGE), new ItemStack(Items.LAVA_BUCKET)));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        context.assertTrue(recipe.isPresent(), "a recipe");
        ItemStack result = recipe.get().value().craft(input, context.getWorld().getRegistryManager());
        context.assertTrue(result.isOf(ModItems.TRICHAUDRON_CARTRIDGE), "the Trichaudron Cartridge, got " + result);
        DefaultedList<ItemStack> left = recipe.get().value().getRemainder(input);
        context.assertTrue(left.stream().anyMatch(stack -> stack.isOf(Items.BUCKET)), "the bucket comes back");
        context.assertEquals(TrichaudronCartridgeItem.prizes(result).size(), 0, "no prize yet");
        context.complete();
    }
}
