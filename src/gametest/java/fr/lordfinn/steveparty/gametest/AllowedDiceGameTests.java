package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.dice.AllowedDice;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

import java.util.List;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/**
 * The party's « Restrict dice » setting ({@link AllowedDice}): off by default, the Default Die listed; the same die is
 * the same item with the same faces and modules; on, in a party listing dice, any other die is refused (nothing thrown,
 * nothing spent), an empty list allows every die; off or outside a party, every die is thrown. Its ghost slots list a copy of the die clicked, an empty hand takes it off.
 */
public class AllowedDiceGameTests implements FabricGameTest {
    private static final String BATCH = "allowed_dice";

    /** A party of {@code player}'s single token, running, at their turn. */
    private static PartyControllerEntity partyOf(TestContext context, ServerPlayerEntity player) {
        path(context, 1, -1, null);
        PigEntity pig = token(context, PATH.get(0), player.getUuid());
        return party(context, player.getUuid(), pig);
    }

    /** The dice {@code player} threw. */
    private static List<DiceEntity> diceOf(TestContext context, ServerPlayerEntity player) {
        return context.getWorld().getEntitiesByClass(DiceEntity.class, player.getBoundingBox().expand(24),
                dice -> dice.getOwner().map(owner -> owner.equals(player.getUuid())).orElse(false));
    }

    /** {@code player} throws {@code die} from their main hand: true if a die flew (it is taken away at once). */
    private static boolean throwDie(TestContext context, ServerPlayerEntity player, ItemStack die) {
        player.setStackInHand(Hand.MAIN_HAND, die);
        die.use(context.getWorld(), player, Hand.MAIN_HAND);
        List<DiceEntity> thrown = diceOf(context, player);
        thrown.forEach(DiceEntity::discard);
        return !thrown.isEmpty();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theSameDieIsItsItemFacesAndModules(TestContext context) {
        ItemStack plain = new ItemStack(ModItems.DEFAULT_DICE);
        ItemStack named = new ItemStack(ModItems.DEFAULT_DICE, 5);
        named.set(DataComponentTypes.CUSTOM_NAME, Text.literal("Lucky me"));
        context.assertTrue(AllowedDice.sameDie(plain, named), "count and name don't matter");
        context.assertTrue(!AllowedDice.sameDie(plain, new ItemStack(ModItems.DOUBLE_DICE)), "a Double Dice is another die");
        ItemStack forged = die("dice_face_1", "dice_face_2");
        context.assertTrue(AllowedDice.sameDie(forged, die("dice_face_1", "dice_face_2")), "the same faces: the same die");
        context.assertTrue(!AllowedDice.sameDie(forged, die("dice_face_1", "dice_face_3")), "other faces: another die");
        context.assertTrue(!AllowedDice.sameDie(forged, plain), "a forged die is not the plain one");
        context.assertTrue(!AllowedDice.sameDie(forged, with(forged.copy(), DiceModules.SLOW, 1)), "another module: another die");
        context.assertTrue(!AllowedDice.sameDie(with(forged.copy(), DiceModules.LUCKY, 1), with(forged.copy(), DiceModules.LUCKY, 2)),
                "another count of a module: another die");
        context.assertTrue(!AllowedDice.sameDie(new ItemStack(Items.DIAMOND), new ItemStack(Items.DIAMOND)), "not a die");
        context.assertTrue(AllowedDice.allows(List.of(), forged) && AllowedDice.allows(List.of(), plain), "an empty list allows every die");
        context.assertTrue(AllowedDice.allows(List.of(plain, forged), named), "listed");
        context.assertTrue(!AllowedDice.allows(List.of(forged), plain), "not listed");
        context.complete();
    }

    /** Off by default, the list holding the Default Die; both saved, an emptied list too (only a missing one gets the default). */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theSettingIsSavedWithTheParty(TestContext context) {
        ServerPlayerEntity player = player(context);
        PartyControllerEntity controller = partyOf(context, player);
        context.assertTrue(!controller.isRestrictDice(), "off by default: every die");
        context.assertEquals(controller.getAllowedDice().size(), AllowedDice.defaults().size(), "the default list");
        context.assertTrue(AllowedDice.sameDie(controller.getAllowedDice().getFirst(), new ItemStack(ModItems.DEFAULT_DICE)), "the Default Die first");
        ItemStack forged = with(die("dice_face_1", "dice_face_2"), DiceModules.SLOW, 1);
        context.assertTrue(!controller.setAllowedDie(5, new ItemStack(ModItems.DEFAULT_DICE, 3)), "the same die twice is refused");
        context.assertTrue(controller.setAllowedDie(5, forged), "a forged die listed, after the last one");
        context.assertTrue(!controller.setAllowedDie(2, new ItemStack(Items.DIAMOND)), "not a die: refused");
        context.assertEquals(controller.getAllowedDice().getLast().getCount(), 1, "one of each");
        int listed = controller.getAllowedDice().size();
        controller.setRestrictDice(true);
        var registries = context.getWorld().getRegistryManager();
        NbtCompound nbt = controller.createNbt(registries);
        controller.setRestrictDice(false);
        while (controller.removeAllowedDie(0)) ;
        controller.read(nbt, registries);
        context.assertTrue(controller.isRestrictDice(), "the switch saved");
        context.assertEquals(controller.getAllowedDice().size(), listed, "the list saved");
        context.assertTrue(AllowedDice.sameDie(controller.getAllowedDice().getLast(), forged), "its faces and modules too");
        while (controller.removeAllowedDie(0)) ;
        controller.read(controller.createNbt(registries), registries);
        context.assertTrue(controller.getAllowedDice().isEmpty(), "an emptied list stays empty");
        NbtCompound old = controller.createNbt(registries);
        old.remove("AllowedDice");
        old.remove("RestrictDice");
        controller.read(old, registries);
        context.assertTrue(!controller.isRestrictDice() && controller.getAllowedDice().size() == AllowedDice.defaults().size(),
                "a controller saved without the setting: off, the default list");
        context.complete();
    }

    /**
     * Off, every die is thrown. On, a party listing a forged die refuses the others (nothing thrown, nothing spent) and
     * throws the listed one; an empty list allows every die.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void aDieNotListedIsRefusedOnceRestricted(TestContext context) {
        ServerPlayerEntity player = player(context);
        PartyControllerEntity controller = partyOf(context, player);
        while (controller.removeAllowedDie(0)) ;
        ItemStack forged = die("dice_face_1", "dice_face_2");
        controller.setAllowedDie(0, forged);
        player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
        context.assertTrue(throwDie(context, player, new ItemStack(ModItems.TRIPLE_DICE)), "off: an unlisted die is thrown");
        controller.setRestrictDice(true);
        ItemStack plain = new ItemStack(ModItems.DEFAULT_DICE, 2);
        context.assertTrue(!throwDie(context, player, plain), "on: the plain die is refused");
        context.assertEquals(plain.getCount(), 2, "nothing spent");
        context.assertTrue(!throwDie(context, player, new ItemStack(ModItems.TRIPLE_DICE)), "a Triple Dice is refused");
        context.assertTrue(!throwDie(context, player, with(forged.copy(), DiceModules.SLOW, 1)), "the listed die with a module more is refused");
        context.assertTrue(throwDie(context, player, forged.copy()), "the listed die is thrown");
        controller.removeAllowedDie(0);
        context.assertTrue(throwDie(context, player, new ItemStack(ModItems.DEFAULT_DICE)), "on but emptied: every die");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void outsideAPartyEveryDieIsThrown(TestContext context) {
        ServerPlayerEntity player = player(context);
        context.assertTrue(throwDie(context, player, new ItemStack(ModItems.DEFAULT_DICE)), "no party: thrown");
        context.complete();
    }

    /**
     * The ghost slots: a die on the cursor lists a copy of it (the cursor keeps it), after the last one; an empty hand
     * takes it off; anything else is refused; nothing changes while the dice are not restricted. The row and the panel
     * show the same list; the places after the first free one are not usable.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theGhostSlotsListACopyOfTheDieClicked(TestContext context) {
        ServerPlayerEntity player = player(context);
        context.setBlockState(CONTROLLER.down(), net.minecraft.block.Blocks.STONE);
        context.setBlockState(CONTROLLER, fr.lordfinn.steveparty.blocks.ModBlocks.PARTY_CONTROLLER);
        PartyControllerEntity controller = context.getBlockEntity(CONTROLLER);
        atEnd(context, () -> context.removeBlock(CONTROLLER));
        PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), controller);
        while (controller.removeAllowedDie(0)) ;
        handler.setCursorStack(new ItemStack(ModItems.DEFAULT_DICE, 4));
        handler.onSlotClick(DICE_FIRST_SLOT, 0, SlotActionType.PICKUP, player);
        context.assertTrue(controller.getAllowedDice().isEmpty() && !handler.isDiceSlotUsable(DICE_FIRST_SLOT), "off: the slots don't change");
        handler.onButtonClick(player, BUTTON_RESTRICT_DICE);
        context.assertTrue(controller.isRestrictDice(), "the switch turned on");
        context.assertTrue(handler.isDiceSlotUsable(DICE_FIRST_SLOT) && !handler.isDiceSlotUsable(DICE_FIRST_SLOT + 1), "empty: the first place only");
        handler.onSlotClick(DICE_PANEL_FIRST_SLOT + 7, 0, SlotActionType.PICKUP, player);
        context.assertEquals(controller.getAllowedDice().size(), 1, "listed (in the first free place)");
        context.assertTrue(handler.isDiceSlotUsable(DICE_FIRST_SLOT + 1) && !handler.isDiceSlotUsable(DICE_FIRST_SLOT + 2), "one die: the next place opens");
        context.assertTrue(handler.getCursorStack().isOf(ModItems.DEFAULT_DICE) && handler.getCursorStack().getCount() == 4, "nothing taken from the cursor");
        context.assertTrue(handler.getSlot(DICE_FIRST_SLOT).getStack().isOf(ModItems.DEFAULT_DICE)
                && handler.getSlot(DICE_PANEL_FIRST_SLOT).getStack().isOf(ModItems.DEFAULT_DICE), "shown in the row and in the panel");
        handler.setCursorStack(new ItemStack(ModItems.DOUBLE_DICE));
        handler.onSlotClick(DICE_FIRST_SLOT + 1, 0, SlotActionType.PICKUP, player);
        handler.setCursorStack(new ItemStack(Items.DIAMOND));
        handler.onSlotClick(DICE_FIRST_SLOT + 2, 0, SlotActionType.PICKUP, player);
        context.assertEquals(controller.getAllowedDice().size(), 2, "a diamond is refused");
        handler.setCursorStack(new ItemStack(ModItems.TRIPLE_DICE));
        handler.onSlotClick(DICE_FIRST_SLOT, 0, SlotActionType.PICKUP, player);
        context.assertTrue(controller.getAllowedDice().getFirst().isOf(ModItems.TRIPLE_DICE), "a die clicked on a listed one replaces it");
        handler.quickMove(player, DICE_FIRST_SLOT);
        context.assertTrue(!handler.getSlot(DICE_FIRST_SLOT).canTakeItems(player) && !handler.getSlot(DICE_FIRST_SLOT).canInsert(new ItemStack(ModItems.DEFAULT_DICE)),
                "a ghost slot is never filled or emptied");
        handler.setCursorStack(ItemStack.EMPTY);
        handler.onSlotClick(DICE_PANEL_FIRST_SLOT, 0, SlotActionType.PICKUP, player);
        context.assertEquals(controller.getAllowedDice().size(), 1, "an empty hand takes it off");
        context.assertTrue(controller.getAllowedDice().getFirst().isOf(ModItems.DOUBLE_DICE), "the next one moves up");
        context.assertTrue(player.getInventory().count(ModItems.TRIPLE_DICE) == 0, "nothing given back");
        player.changeGameMode(net.minecraft.world.GameMode.ADVENTURE);
        handler.onSlotClick(DICE_PANEL_FIRST_SLOT, 0, SlotActionType.PICKUP, player);
        context.assertEquals(controller.getAllowedDice().size(), 1, "Adventure: read only");
        context.complete();
    }

    /** The client's handler: the row on the Settings page, the panel instead while open; inside the page, no overlap. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void theRowAndThePanelFitTheSettingsPage(TestContext context) {
        ServerPlayerEntity player = player(context);
        PartyControllerScreenHandler handler = new PartyControllerScreenHandler(1, player.getInventory(), new BlockPosPayload(BlockPos.ORIGIN));
        handler.setPage(PartyControllerScreenHandler.Page.GAINS);
        context.assertTrue(!handler.getSlot(DICE_FIRST_SLOT).isEnabled() && !handler.getSlot(DICE_PANEL_FIRST_SLOT).isEnabled(), "Gains: no dice slots");
        handler.setPage(PartyControllerScreenHandler.Page.SETTINGS);
        for (boolean open : new boolean[]{false, true}) {
            handler.setDiceOpen(open);
            context.assertTrue(handler.getSlot(DICE_FIRST_SLOT).isEnabled() != open && handler.getSlot(DICE_PANEL_FIRST_SLOT).isEnabled() == open,
                    "open " + open + ": the row or the panel");
            List<Slot> shown = handler.slots.stream().filter(slot -> slot.isEnabled() && slot.inventory != player.getInventory()).toList();
            for (Slot slot : shown) {
                context.assertTrue(slot.x >= 4 && slot.x + 16 <= WIDTH - 4 && slot.y >= 4 && slot.y + 16 <= PANEL_HEIGHT - 4, "slot " + slot.id + " is inside the page");
                for (Slot other : shown)
                    context.assertTrue(other == slot || Math.abs(other.x - slot.x) >= 18 || Math.abs(other.y - slot.y) >= 18, "slots " + slot.id + " and " + other.id + " overlap");
            }
        }
        context.complete();
    }
}
