package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.TrichaudronTileBehavior;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronPartEntity;
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
import net.minecraft.util.Hand;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestAsserts.count;
import static fr.lordfinn.steveparty.gametest.kit.TestWait.when;

/**
 * The Trichaudron space: one head per prize on offer (1 to 5), each head a distinct prize, shuffled in unseen (a blind
 * pick) or shown at its mouth (a true choice); its player picks a head by hitting or clicking its hit box; the chosen
 * head spits (harmlessly, whoever stands at the token) and its prize is taken out of the chests (never from nothing):
 * as many as set but never more than they hold; a prize won is no longer offered for the rest of the party unless the
 * cartridge always offers the same; its menu's items filter them, without any the first items found; without chest
 * or with nothing to offer it sleeps; the Trichaudron is a board actor removed at the end (or when the party ends in
 * the middle), its hit boxes with it; the recipe. And its hit boxes on a wild one: a blow on a head or a neck goes to
 * its body, a head's harder.
 */
public class TrichaudronSpaceGameTests implements FabricGameTest {
    private static final String BATCH = "trichaudron_space";
    private static final BlockPos TILE = new BlockPos(3, 1, 3), CHEST = new BlockPos(5, 1, 3);
    private static final int WHOLE = TrichaudronPrizes.WHOLE + 20;
    /** Until its heads are held out (rise, dive, emerge), with some margin. */
    private static final int TO_CHOICE = TrichaudronPrizes.RISE_TICKS + TrichaudronPrizes.DIVE_TICKS + TrichaudronPrizes.EMERGE_TICKS + 20;

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

        /** The show again (another landing of the same token), a fresh {@code done}. */
        TrichaudronPrizes.Start again(TestContext context, boolean[] done) {
            return TrichaudronPrizes.start(context.getWorld(), tile.getPos(), token, party, () -> done[0] = true);
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

    /** The head holding {@code item}, -1 for none. */
    private static int headOf(List<ItemStack> heads, Item item) {
        for (int i = 0; i < heads.size(); i++) if (heads.get(i).isOf(item)) return i;
        return -1;
    }

    /** The prizes in the heads it shows, checked: one per head shown, none in the others, all different. */
    private static List<ItemStack> checkHeads(TestContext context, MobEntity token, int count) {
        TrichaudronEntity actor = TrichaudronPrizes.actor(token);
        List<ItemStack> heads = TrichaudronPrizes.heads(token);
        context.assertEquals(actor.getHeadCount(), count, "one head per prize on offer");
        List<ItemStack> prizes = new ArrayList<>();
        for (int head = 0; head < TrichaudronEntity.MAX_HEADS; head++) {
            boolean shown = actor.showsHead(head);
            context.assertTrue(shown != heads.get(head).isEmpty(), "head " + head + ": a prize if and only if shown");
            if (shown) prizes.add(heads.get(head));
        }
        Set<Item> items = new HashSet<>();
        for (ItemStack prize : prizes) items.add(prize.getItem());
        context.assertEquals(items.size(), count, "each head a different prize");
        return prizes;
    }

    private static boolean has(TileInfo info, String key) {
        return info.lines().stream().anyMatch(line -> line.text().getContent() instanceof TranslatableTextContent content
                && content.getKey().equals("hud.steveparty.tile_info." + key));
    }

    /** 1 to 5 heads: the centre one, the side ones, three, side and outer, all; never the same head twice. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theHeadsShownForEachCount(TestContext context) {
        for (int count = 1; count <= TrichaudronEntity.MAX_HEADS; count++) {
            int[] shown = TrichaudronEntity.headsShown(count);
            context.assertEquals(shown.length, count, count + " heads shown");
            context.assertEquals((int) java.util.Arrays.stream(shown).distinct().count(), count, "distinct heads");
        }
        context.assertTrue(TrichaudronEntity.headsShown(1)[0] == 0, "one: the centre head");
        context.assertTrue(java.util.Arrays.equals(TrichaudronEntity.headsShown(3), new int[]{0, 1, 2}), "three: a wild one's");
        context.complete();
    }

    /**
     * A blind pick (default): 4 prizes, 4 heads, each a different prize, nothing shown at their mouths; its player
     * clicks a head's hit box: that head's prize, taken out of the chest; standing at the token, the spit splashes him
     * harmlessly; at the end the Trichaudron and its hit boxes are gone.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void clickingAHeadGivesItsPrizeFromTheChest(TestContext context) {
        Show show = show(context, cartridge(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.EMERALD, 5),
                        new ItemStack(Items.GOLD_INGOT, 3), new ItemStack(Items.APPLE, 1)),
                chest(new ItemStack(Items.DIAMOND, 10), new ItemStack(Items.EMERALD, 10), new ItemStack(Items.GOLD_INGOT, 10),
                        new ItemStack(Items.APPLE, 10)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        TrichaudronEntity actor = TrichaudronPrizes.actor(token);
        List<ItemStack> prizes = checkHeads(context, token, 4);
        context.assertFalse(actor.showsHead(0), "four heads: the centre one hidden");
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, TO_CHOICE, "its heads held out", () -> {
            for (int head : actor.shownHeads()) {
                context.assertTrue(actor.getHeldPrize(head).isEmpty(), "a blind pick: nothing shown at its mouth");
                context.assertTrue(actor.getParts().of(head, false) != null && actor.getParts().of(head, true) != null,
                        "a hit box on head " + head + " and its neck");
            }
            context.assertTrue(actor.getParts().of(0, false) == null, "none on the hidden head");
            int head = actor.shownHeads()[context.getWorld().getRandom().nextInt(4)];
            ItemStack prize = TrichaudronPrizes.heads(token).get(head);
            // he stands at the token: the spit splashes him too
            Vec3d at = token.getPos();
            show.player().refreshPositionAndAngles(at.x + 0.5, at.y, at.z, 0, 0);
            float health = show.player().getHealth();
            TrichaudronPartEntity part = actor.getParts().of(head, false);
            // a hologram the crosshair goes through, but its heads' hit boxes are aimed at
            context.assertFalse(actor.canHit(), "the Trichaudron itself is not aimed at");
            context.assertTrue(part.canHit(), "its heads are");
            part.interact(show.player(), Hand.MAIN_HAND);
            context.assertEquals(TrichaudronPrizes.picked(token), head, "the head clicked is picked");
            when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(show.player(), prize.getItem()), prize.getCount(), "the clicked head's prize");
                context.assertEquals(show.inChest(prize.getItem()), 10 - prize.getCount(), "taken out of the chest");
                for (ItemStack other : prizes) {
                    if (other.getItem() != prize.getItem()) context.assertEquals(show.inChest(other.getItem()), 10, "the others stay in the chest");
                }
                context.assertEquals(show.player().getHealth(), health, "the spit hurts no one");
                context.assertTrue(show.player().isAlive(), "alive");
                context.assertTrue(actor.isRemoved() && part.isRemoved(), "the Trichaudron and its hit boxes are gone");
                context.assertFalse(TrichaudronPrizes.isRunning(token), "over");
                context.complete();
            });
        });
    }

    /** A true choice: each head shows its own prize at its mouth; hitting a head (attack) picks it too. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void aTrueChoiceShowsEachHeadsPrize(TestContext context) {
        ItemStack cartridge = cartridge(new ItemStack(Items.DIAMOND, 1), new ItemStack(Items.EMERALD, 1), new ItemStack(Items.APPLE, 1));
        TrichaudronCartridgeItem.setTrueChoice(cartridge, true);
        Show show = show(context, cartridge, chest(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.APPLE, 5)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        TrichaudronEntity actor = TrichaudronPrizes.actor(token);
        checkHeads(context, token, 3);
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, TO_CHOICE, "its heads held out", () -> {
            List<ItemStack> heads = TrichaudronPrizes.heads(token);
            for (int head : actor.shownHeads()) {
                context.assertTrue(ItemStack.areItemsEqual(actor.getHeldPrize(head), heads.get(head)), "head " + head + " shows its prize");
            }
            int emerald = headOf(heads, Items.EMERALD);
            actor.getParts().of(emerald, false).damage(context.getWorld().getDamageSources().playerAttack(show.player()), 5);
            context.assertEquals(TrichaudronPrizes.picked(token), emerald, "the head hit is picked");
            context.assertEquals(actor.getHealth(), actor.getMaxHealth(), "a board actor takes no harm");
            when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(show.player(), Items.EMERALD), 1, "the emerald he chose");
                context.assertEquals(count(show.player(), Items.DIAMOND) + count(show.player(), Items.APPLE), 0, "nothing else");
                context.complete();
            });
        });
    }

    /**
     * Prizes consumed (default): a prize won is no longer offered at that space for the rest of the party, one head
     * less the next time; the last one won, it sleeps though its chest still holds them. Always the same prizes: they
     * stay on offer.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 3 * WHOLE, batchId = BATCH)
    public void aPrizeWonIsConsumedForTheParty(TestContext context) {
        Show show = show(context, cartridge(new ItemStack(Items.DIAMOND, 1), new ItemStack(Items.EMERALD, 1)),
                chest(new ItemStack(Items.DIAMOND, 10), new ItemStack(Items.EMERALD, 10)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        checkHeads(context, token, 2);
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, TO_CHOICE, "the choice", () -> {
            TrichaudronPrizes.pick(token, headOf(TrichaudronPrizes.heads(token), Items.DIAMOND));
            when(context, () -> show.done()[0], WHOLE, "the first show ends", () -> {
                context.assertEquals(count(show.player(), Items.DIAMOND), 1, "a diamond won");
                List<ItemStack> offered = TrichaudronTileBehavior.offered(context.getWorld(), show.tile(), show.cartridge());
                context.assertTrue(offered.size() == 1 && offered.getFirst().isOf(Items.EMERALD), "only the emerald still on offer");
                boolean[] again = {false};
                context.assertTrue(show.again(context, again) == TrichaudronPrizes.Start.STARTED, "it rises again");
                List<ItemStack> heads = checkHeads(context, token, 1);
                context.assertTrue(heads.getFirst().isOf(Items.EMERALD), "its one head: the emerald");
                when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, TO_CHOICE, "the second choice", () -> {
                    TrichaudronPrizes.pick(token, 0);
                    when(context, () -> again[0], WHOLE, "the second show ends", () -> {
                        context.assertEquals(count(show.player(), Items.EMERALD), 1, "the emerald won");
                        context.assertTrue(show.inChest(Items.DIAMOND) == 9 && show.inChest(Items.EMERALD) == 9, "the chest still holds both");
                        context.assertTrue(TrichaudronCartridgeItem.isAsleep(show.cartridge()), "everything won: it sleeps");
                        context.assertTrue(show.again(context, new boolean[1]) == TrichaudronPrizes.Start.EMPTY, "no show any more");
                        // Always the same prizes: both on offer again
                        TrichaudronCartridgeItem.setSamePrizes(show.cartridge(), true);
                        context.assertEquals(TrichaudronTileBehavior.offered(context.getWorld(), show.tile(), show.cartridge()).size(), 2,
                                "always the same prizes: both on offer");
                        TrichaudronTileBehavior.refreshSleep(context.getWorld(), show.tile());
                        context.assertFalse(TrichaudronCartridgeItem.isAsleep(show.cartridge()), "awake again");
                        context.complete();
                    });
                });
            });
        });
    }

    /** Each time it rises, the prizes are shuffled into its heads anew (a head's place never tells its prize). */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40, batchId = BATCH)
    public void thePrizesAreShuffledEachTime(TestContext context) {
        ItemStack cartridge = cartridge(new ItemStack(Items.DIAMOND, 1), new ItemStack(Items.EMERALD, 1),
                new ItemStack(Items.APPLE, 1), new ItemStack(Items.BREAD, 1), new ItemStack(Items.STICK, 1));
        TrichaudronCartridgeItem.setSamePrizes(cartridge, true);
        Show show = show(context, cartridge, chest(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.EMERALD, 5),
                new ItemStack(Items.APPLE, 5), new ItemStack(Items.BREAD, 5), new ItemStack(Items.STICK, 5)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            if (i > 0) context.assertTrue(show.again(context, new boolean[1]) == TrichaudronPrizes.Start.STARTED, "it rises again");
            checkHeads(context, token, 5);
            seen.add(headOf(TrichaudronPrizes.heads(token), Items.DIAMOND));
            TrichaudronPrizes.stop(token);
            context.assertFalse(TrichaudronPrizes.isRunning(token), "stopped");
        }
        context.assertTrue(seen.size() > 1, "the diamond in different heads, got " + seen);
        context.complete();
    }

    /**
     * Left alone, it never picks by itself: past {@link TrichaudronPrizes#CHOOSE_TICKS} it still waits, and only then
     * may the button in the chat pick a head at random; its prize is given as if clicked.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 60, batchId = BATCH)
    public void leftAloneItWaitsForTheRandomButton(TestContext context) {
        Show show = show(context, cartridge(new ItemStack(Items.DIAMOND, 1), new ItemStack(Items.EMERALD, 1)),
                chest(new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.EMERALD, 5)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, TO_CHOICE, "its heads held out", () -> {
            context.assertFalse(TrichaudronPrizes.pickAtRandom(token.getUuid(), show.player()), "too early for the button");
            long from = context.getTick();
            when(context, () -> context.getTick() >= from + TrichaudronPrizes.CHOOSE_TICKS + 10, WHOLE, "a long wait", () -> {
                context.assertEquals(TrichaudronPrizes.phase(token), TrichaudronPrizes.Phase.CHOOSE, "still waiting");
                context.assertEquals(TrichaudronPrizes.picked(token), -1, "no head picked by itself");
                context.assertTrue(TrichaudronPrizes.pickAtRandom(token.getUuid(), show.player()), "the button picks one");
                context.assertTrue(TrichaudronPrizes.picked(token) >= 0, "a head picked");
                when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                    context.assertEquals(count(show.player(), Items.DIAMOND) + count(show.player(), Items.EMERALD), 1, "one prize");
                    context.complete();
                });
            });
        });
    }

    /** Its menu filters the chest: only the items set are prizes, one head each. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theFilterIsRespected(TestContext context) {
        Show show = show(context, cartridge(new ItemStack(Items.DIAMOND, 4)),
                chest(new ItemStack(Items.DIRT, 64), new ItemStack(Items.DIAMOND, 6), new ItemStack(Items.COBBLESTONE, 64)),
                TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        List<ItemStack> heads = checkHeads(context, token, 1);
        context.assertTrue(heads.getFirst().isOf(Items.DIAMOND) && heads.getFirst().getCount() == 4, "only the item set, as many as set");
        context.complete();
    }

    /** More set than the chest holds: the prize is what it holds; then nothing is left and the space sleeps. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void theQuantityIsCappedThenItSleeps(TestContext context) {
        ItemStack cartridge = cartridge(new ItemStack(Items.EMERALD, 10));
        TrichaudronCartridgeItem.setSamePrizes(cartridge, true); // so that, refilled, it is on offer again
        Show show = show(context, cartridge, chest(new ItemStack(Items.EMERALD, 3)), TrichaudronPrizes.Start.STARTED);
        MobEntity token = show.token();
        List<ItemStack> heads = TrichaudronPrizes.heads(token);
        int prizeHead = headOf(heads, Items.EMERALD);
        context.assertEquals(heads.get(prizeHead).getCount(), 3, "as many as the chest holds");
        TileInfo info = TileInfos.of(show.tile());
        context.assertTrue(info.ring().size() == 1 && info.ring().getFirst().getCount() == 3, "what is really there circles over the space");
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, WHOLE, "the choice", () -> {
            TrichaudronPrizes.pick(token, prizeHead);
            when(context, () -> show.done()[0], WHOLE, "the show ends", () -> {
                context.assertEquals(count(show.player(), Items.EMERALD), 3, "3 emeralds, no more");
                context.assertEquals(show.inChest(Items.EMERALD), 0, "the chest is empty");
                context.assertTrue(TrichaudronCartridgeItem.isAsleep(show.cartridge()), "it sleeps (its face dimmed)");
                context.assertEquals(TileFeedback.landingOf(show.tile()), TileFeedback.Landing.DEFAULT, "a plain landing");
                TileInfo asleep = TileInfos.of(show.tile());
                context.assertTrue(asleep.ring().isEmpty() && has(asleep, "trichaudron.empty"), "its panel: empty");
                boolean[] again = {false};
                context.assertTrue(show.again(context, again) == TrichaudronPrizes.Start.EMPTY, "no show any more");
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
        checkHeads(context, show.token(), 5);
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

    /** The party ending in the middle: the show stops, its actor and its hit boxes are removed, nothing leaves the chest. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = WHOLE + 20, batchId = BATCH)
    public void partyEndingRemovesIt(TestContext context) {
        Show show = show(context, cartridge(), chest(new ItemStack(Items.DIAMOND)), TrichaudronPrizes.Start.STARTED);
        TrichaudronEntity actor = TrichaudronPrizes.actor(show.token());
        context.assertTrue(show.tile().getBoardSpaceBehavior().keepsTurn(show.token()), "it holds the turn");
        when(context, () -> TrichaudronPrizes.phase(show.token()) == TrichaudronPrizes.Phase.CHOOSE, WHOLE, "its heads held out", () -> {
            List<TrichaudronPartEntity> parts = actor.getParts().all();
            context.assertEquals(parts.size(), 2, "one head (and its neck) for one prize");
            context.removeBlock(DiceTestKit.CONTROLLER);
            context.waitAndRun(2, () -> {
                context.assertTrue(show.done()[0] && !TrichaudronPrizes.isRunning(show.token()), "stopped");
                context.assertTrue(actor.isRemoved() && parts.stream().allMatch(TrichaudronPartEntity::isRemoved),
                        "the Trichaudron and its hit boxes are gone");
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
        context.assertFalse(TrichaudronCartridgeItem.samePrizes(result) || TrichaudronCartridgeItem.trueChoice(result),
                "by default: prizes consumed, a blind pick");
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
        int head = headOf(TrichaudronPrizes.heads(token), Items.DIAMOND);
        when(context, () -> TrichaudronPrizes.phase(token) == TrichaudronPrizes.Phase.CHOOSE, WHOLE, "the choice", () -> {
            TrichaudronPrizes.pick(token, head);
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

    /**
     * A wild Trichaudron: a hit box on each of its three heads and on each neck; a blow on a head or a neck goes to its
     * body (a head's harder than a neck's); its outer heads have none (not shown).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40, batchId = "trichaudron_parts")
    public void aBlowOnAHeadOrANeckHurtsItsBody(TestContext context) {
        ServerPlayerEntity player = player(context);
        TrichaudronEntity byHead = context.spawnEntity(ModEntities.TRICHAUDRON, new BlockPos(2, 1, 2));
        TrichaudronEntity byNeck = context.spawnEntity(ModEntities.TRICHAUDRON, new BlockPos(2, 1, 12));
        byHead.setAiDisabled(true);
        byNeck.setAiDisabled(true);
        context.waitAndRun(3, () -> {
            context.assertEquals(byHead.getParts().all().size(), 6, "three heads, three necks");
            context.assertTrue(byHead.getParts().of(3, false) == null && byHead.getParts().of(4, false) == null, "no outer head");
            TrichaudronPartEntity head = byHead.getParts().of(1, false);
            TrichaudronPartEntity neck = byNeck.getParts().of(1, true);
            context.assertTrue(head.getBoundingBox().getCenter().distanceTo(byHead.headCenter(1)) < 0.01, "on its head");
            float full = byHead.getHealth();
            context.assertTrue(head.damage(context.getWorld().getDamageSources().playerAttack(player), 6), "a head hit lands");
            context.assertTrue(neck.damage(context.getWorld().getDamageSources().playerAttack(player), 6), "a neck hit lands");
            float headLoss = full - byHead.getHealth(), neckLoss = full - byNeck.getHealth();
            context.assertTrue(neckLoss > 0, "a neck hit hurts its body");
            context.assertTrue(headLoss > neckLoss, "a head hit hurts more: " + headLoss + " vs " + neckLoss);
            byHead.discard();
            context.waitAndRun(1, () -> {
                context.assertTrue(head.isRemoved(), "its hit boxes go with it");
                context.complete();
            });
        });
    }
}
