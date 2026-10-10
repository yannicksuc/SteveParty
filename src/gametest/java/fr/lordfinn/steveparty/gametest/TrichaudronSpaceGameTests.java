package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.TrichaudronTileBehavior;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.service.TileInfos;
import fr.lordfinn.steveparty.service.TrichaudronPrizes;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
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
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The Trichaudron space: its prizes come out of its linked chests (never from nothing): the chosen head's prize is taken
 * out of them, as many as set but never more than they hold; its menu's items filter them, without any the first items
 * found; without chest or with nothing to give it sleeps; the Trichaudron and its die are board actors removed at the
 * end (or when the party ends in the middle); the recipe.
 */
public class TrichaudronSpaceGameTests implements FabricGameTest {
    private static final String BATCH = "trichaudron_space";
    private static final BlockPos TILE = new BlockPos(3, 1, 3), CHEST = new BlockPos(5, 1, 3);
    private static final int WHOLE = TrichaudronPrizes.WHOLE + 20;

    private record Show(ServerPlayerEntity player, MobEntity token, PartyControllerEntity party, BoardSpaceBlockEntity tile,
                        boolean[] done, ChestBlockEntity chest) {
        ItemStack cartridge() {
            return tile.getActiveCartridgeItemStack();
        }

        int inChest(Item item) {
            int count = 0;
            for (int i = 0; i < chest.size(); i++) if (chest.getStack(i).isOf(item)) count += chest.getStack(i).getCount();
            return count;
        }
    }

    /** A Trichaudron Cartridge whose menu sets these prizes (none: whatever its chests hold). */
    private static ItemStack cartridge(ItemStack... prizes) {
        ItemStack cartridge = new ItemStack(ModItems.TRICHAUDRON_CARTRIDGE);
        TrichaudronCartridgeItem.setFilters(cartridge, List.of(prizes));
        return cartridge;
    }

    private static ItemStack[] chest(ItemStack... content) {
        return content;
    }

    /**
     * A party of one player, its token on a Trichaudron tile holding {@code cartridge}, linked to a chest holding
     * {@code content} (null: no chest linked); the show started if it can.
     */
    private static Show show(TestContext context, ItemStack cartridge, ItemStack @Nullable [] content, TrichaudronPrizes.Start expected) {
        ServerPlayerEntity player = player(context);
        context.setBlockState(CHEST.down(), Blocks.STONE);
        context.setBlockState(CHEST, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(CHEST);
        if (content != null) {
            for (int i = 0; i < content.length; i++) chest.setStack(i, content[i].copy());
            CartridgeContainers.set(cartridge, List.of(GlobalPos.create(context.getWorld().getRegistryKey(), context.getAbsolutePos(CHEST))));
        }
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
        return new Show(player, token, party, tile, done, chest);
    }

    /** The face whose head holds {@code item} (null: an empty head), -1 for none. */
    private static int faceOf(List<ItemStack> heads, @Nullable Item item) {
        for (int i = 0; i < heads.size(); i++) {
            ItemStack head = heads.get(i);
            if (item == null ? head.isEmpty() : head.isOf(item)) return i + 1;
        }
        return -1;
    }

    private static boolean has(TileInfo info, String key) {
        return info.lines().stream().anyMatch(line -> line.text().getContent() instanceof TranslatableTextContent content
                && content.getKey().equals("hud.steveparty.tile_info." + key));
    }

    /** Its player stops the slow die by hitting it: the head of that face gives its prize, taken out of the chest. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theChosenHeadGivesItsPrizeFromTheChest(TestContext context) {
        Show show = show(context, cartridge(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.EMERALD, 5),
                        new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.APPLE, 1)),
                chest(new ItemStack(Items.DIAMOND, 10), new ItemStack(Items.EMERALD, 10), new ItemStack(Items.GOLD_INGOT, 10),
                        new ItemStack(Items.APPLE, 10)), TrichaudronPrizes.Start.STARTED);
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
                    context.assertEquals(show.inChest(prize.getItem()), 10 - prize.getCount(), "taken out of the chest");
                    for (ItemStack other : heads) {
                        if (other.getItem() != prize.getItem()) context.assertEquals(show.inChest(other.getItem()), 10, "the others stay in the chest");
                    }
                    context.assertTrue(actor.isRemoved() && die.isRemoved(), "the Trichaudron and its die are gone");
                    context.assertFalse(TrichaudronPrizes.isRunning(token), "over");
                    context.complete();
                });
            });
        });
    }

    /** Its menu filters the chest: only the items set are prizes; fewer than three, the other heads come out empty. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theFilterIsRespected(TestContext context) {
        Show show = show(context, cartridge(new ItemStack(Items.DIAMOND, 4)),
                chest(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIAMOND, 6), new ItemStack(Items.COBBLESTONE, 64)),
                TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        List<ItemStack> heads = TrichaudronPrizes.heads(token);
        context.assertEquals((int) heads.stream().filter(ItemStack::isEmpty).count(), 2, "one item set: two empty heads");
        context.assertTrue(heads.stream().noneMatch(head -> head.isOf(Items.DIRT) || head.isOf(Items.COBBLESTONE)), "only the items set");
        int emptyFace = faceOf(heads, null);
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, WHOLE, "the choice", () -> {
            TrichaudronPrizes.pick(token, emptyFace);
            when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(show.player(), Items.DIAMOND), 0, "an empty head: nothing");
                context.assertEquals(show.inChest(Items.DIAMOND), 6, "the chest is untouched");
                context.complete();
            });
        });
    }

    /** More set than the chest holds: the prize is what it holds; then nothing is left and the space sleeps. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theQuantityIsCappedThenItSleeps(TestContext context) {
        Show show = show(context, cartridge(new ItemStack(Items.EMERALD, 10)), chest(new ItemStack(Items.EMERALD, 3)),
                TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        List<ItemStack> heads = TrichaudronPrizes.heads(token);
        int prizeFace = faceOf(heads, Items.EMERALD);
        context.assertEquals(heads.get(prizeFace - 1).getCount(), 3, "as many as the chest holds");
        TileInfo info = TileInfos.of(show.tile());
        context.assertTrue(info.ring().size() == 1 && info.ring().getFirst().getCount() == 3, "what is really there circles over the space");
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, WHOLE, "the choice", () -> {
            TrichaudronPrizes.pick(token, prizeFace);
            when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(show.player(), Items.EMERALD), 3, "3 emeralds, no more");
                context.assertEquals(show.inChest(Items.EMERALD), 0, "the chest is empty");
                context.assertTrue(TrichaudronCartridgeItem.isAsleep(show.cartridge()), "it sleeps (its face dimmed)");
                context.assertEquals(TileFeedback.landingOf(show.tile()), TileFeedback.Landing.DEFAULT, "a plain landing");
                TileInfo asleep = TileInfos.of(show.tile());
                context.assertTrue(asleep.ring().isEmpty() && has(asleep, "trichaudron.empty"), "its panel: empty");
                boolean[] again = {false};
                context.assertTrue(TrichaudronPrizes.start(context.getWorld(), show.tile().getPos(), token, show.party(),
                        () -> again[0] = true) == TrichaudronPrizes.Start.EMPTY, "no show any more");
                context.assertTrue(TrichaudronPrizes.actor(token) == null && !again[0], "no Trichaudron");
                // Refilled: awake again
                show.chest().setStack(0, new ItemStack(Items.EMERALD, 2));
                TrichaudronTileBehavior.refreshSleep(context.getWorld(), show.tile());
                context.assertFalse(TrichaudronCartridgeItem.isAsleep(show.cartridge()), "refilled: awake");
                context.complete();
            });
        });
    }

    /** Nothing set in its menu: the first items found in its chests, as many as they hold. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void noFilterTakesTheFirstItemsFound(TestContext context) {
        Show show = show(context, cartridge(), chest(new ItemStack(Items.DIRT, 5), new ItemStack(Items.DIRT, 7),
                new ItemStack(Items.APPLE, 2), new ItemStack(Items.BREAD, 1), new ItemStack(Items.STICK, 3),
                new ItemStack(Items.STONE, 4), new ItemStack(Items.FEATHER, 9)), TrichaudronPrizes.Start.STARTED);
        List<ItemStack> available = TrichaudronCartridgeItem.available(show.cartridge(), context.getWorld(), null);
        context.assertEquals(available.size(), TrichaudronCartridgeItem.PRIZES, "the first 5 different items");
        context.assertTrue(available.getFirst().isOf(Items.DIRT) && available.getFirst().getCount() == 12, "all the dirt there is");
        context.assertTrue(available.stream().noneMatch(item -> item.isOf(Items.FEATHER)), "not the 6th");
        context.complete();
    }

    /** No chest linked (a cartridge set before chests, too): the space sleeps, « no chest ». */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void noChestSleeps(TestContext context) {
        Show show = show(context, cartridge(new ItemStack(Items.DIAMOND, 2)), null, TrichaudronPrizes.Start.EMPTY);
        context.assertTrue(TrichaudronPrizes.actor(show.token()) == null && !show.done()[0], "no Trichaudron, nothing to wait for");
        context.assertFalse(show.tile().getBoardSpaceBehavior().keepsTurn(show.token()), "the turn goes on");
        context.assertTrue(TrichaudronCartridgeItem.isAsleep(show.cartridge()), "asleep");
        context.assertTrue(has(TileInfos.of(show.tile()), "trichaudron.no_chest"), "its panel: no chest");
        context.complete();
    }

    /** The party ending in the middle: the show stops, its actors are removed, nothing leaves the chest. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void partyEndingRemovesIt(TestContext context) {
        Show show = show(context, cartridge(), chest(new ItemStack(Items.DIAMOND)), TrichaudronPrizes.Start.STARTED);
        TrichaudronEntity actor = TrichaudronPrizes.actor(show.token());
        context.assertTrue(show.tile().getBoardSpaceBehavior().keepsTurn(show.token()), "it holds the turn");
        when(context, () -> TrichaudronPrizes.die(show.token()) != null, WHOLE, "its die", () -> {
            DiceEntity die = TrichaudronPrizes.die(show.token());
            context.removeBlock(DiceTestKit.CONTROLLER);
            context.waitAndRun(2, () -> {
                context.assertTrue(show.done()[0] && !TrichaudronPrizes.isRunning(show.token()), "stopped");
                context.assertTrue(actor.isRemoved() && die.isRemoved(), "the Trichaudron and its die are gone");
                context.assertEquals(show.inChest(Items.DIAMOND), 1, "nothing taken");
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
        context.assertTrue(TrichaudronCartridgeItem.filters(result).isEmpty() && !TrichaudronCartridgeItem.hasChests(result), "nothing set, no chest");
        context.complete();
    }

    /**
     * No chest linked, in a party: its prizes come out of the Party Controller's bank (its own inventory, then its
     * linked chests), shown over the space as « Party Controller's bank »; the bank empty, it sleeps.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = "trichaudron_party_bank")
    public void noChestTakesItsPrizesFromThePartyBank(TestContext context) {
        ServerPlayerEntity player = player(context);
        BoardSpaceBlockEntity tile = tile(context, TILE, cartridge(new ItemStack(Items.DIAMOND, 2)));
        MobEntity token = token(context, TILE, player.getUuid());
        PartyControllerEntity party = DiceTestKit.party(context, player.getUuid(), token);
        party.getBankItems().setStack(3, new ItemStack(Items.DIAMOND, 5));
        TrichaudronTileBehavior.refreshSleep(context.getWorld(), tile);
        context.assertFalse(TrichaudronCartridgeItem.isAsleep(tile.getActiveCartridgeItemStack()), "awake: the bank holds diamonds");
        TileInfo info = TileInfos.of(tile);
        context.assertTrue(info.ring().size() == 1 && info.ring().getFirst().isOf(Items.DIAMOND) && has(info, "party_bank"),
                "the bank's diamonds circle over it, « Party Controller's bank »");
        boolean[] done = {false};
        context.assertTrue(TrichaudronPrizes.start(context.getWorld(), tile.getPos(), token, party, () -> done[0] = true)
                == TrichaudronPrizes.Start.STARTED, "the show starts");
        List<ItemStack> heads = TrichaudronPrizes.heads(token);
        int face = faceOf(heads, Items.DIAMOND);
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, WHOLE, "the choice", () -> {
            TrichaudronPrizes.pick(token, face);
            when(context, () -> done[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(player, Items.DIAMOND), 2, "the prize");
                context.assertEquals(party.getBankItems().getStack(3).getCount(), 3, "taken out of the Party Controller's bank");
                party.getBankItems().clear();
                TrichaudronTileBehavior.refreshSleep(context.getWorld(), tile);
                context.assertTrue(TrichaudronCartridgeItem.isAsleep(tile.getActiveCartridgeItemStack()), "the bank empty: it sleeps");
                context.complete();
            });
        });
    }
}
