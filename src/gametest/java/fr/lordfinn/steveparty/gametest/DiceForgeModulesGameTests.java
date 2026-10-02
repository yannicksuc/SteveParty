package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.DiceForgeScreenHandler;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.Map;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;
import static fr.lordfinn.steveparty.gametest.DiceTestKit.face;

/** The module slots of the Dice Forge: what they accept, what the forged dice carry, and that they are saved. */
public class DiceForgeModulesGameTests implements FabricGameTest {
    private static final BlockPos FORGE_POS = new BlockPos(1, 1, 1);

    private static DiceForgeBlockEntity placeActivatedForge(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        forge.setStack(CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
        context.assertTrue(forge.isActivated(), "core in");
        return forge;
    }

    private static void fill(DiceForgeBlockEntity forge) {
        forge.setStack(0, new ItemStack(face("dice_face_2"), 2));
        forge.setStack(1, new ItemStack(face("coin_dice_face_5")));
        for (int i = 0; i < FRAGMENT_SLOTS; i++) forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        forge.setStack(BLANK_SLOT, new ItemStack(ModItems.blankDiceFace(), 10));
    }

    private static ItemStack module(DiceModule module, int count) {
        return new ItemStack(module.item(), count);
    }

    /** Every die forged carries the modules of the slots, with their counts; the modules are not consumed. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 400)
    public void forgedDiceCarryTheModulesOfTheSlots(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        fill(forge);
        forge.setStack(FIRST_MODULE_SLOT, module(DiceModules.LUCKY, 2));
        forge.setStack(FIRST_MODULE_SLOT + 1, module(DiceModules.INFINITY, 1));
        forge.setStack(FIRST_MODULE_SLOT + 2, module(DiceModules.LUCKY, 1));
        forge.setStack(FIRST_MODULE_SLOT + 3, module(DiceModules.INFINITY, 1));
        context.assertEquals(modulesOf(forge), Map.of(DiceModules.LUCKY, 3, DiceModules.INFINITY, 1), "Lucky adds up, Infinity counts once");
        context.assertEquals(forge.getStatus(), Status.OK, "ready");
        context.assertTrue(forge.start(), "production starts");

        context.waitAndRun(2 * CRAFT_TIME + 10, () -> {
            ItemStack output = forge.getStack(OUTPUT_SLOT);
            context.assertTrue(output.isOf(ModItems.DEFAULT_DICE) && output.getCount() == 2, "two dice forged, stacked: " + output);
            context.assertEquals(DiceModules.of(output), Map.of(DiceModules.LUCKY, 3, DiceModules.INFINITY, 1), "they carry the modules");
            context.assertTrue(output.get(DiceFacesComponent.TYPE).faces().size() == 2, "and their faces");
            context.assertEquals(forge.getStack(FIRST_MODULE_SLOT).getCount(), 2, "the modules are not consumed");
            context.assertEquals(forge.getStack(FIRST_MODULE_SLOT + 1).getCount(), 1, "the modules are not consumed");
            context.assertEquals(forge.getStack(BLANK_SLOT).getCount(), 6, "blank faces: one per face slot, none for the modules");

            // Taking a module out changes the next die
            forge.setStack(FIRST_MODULE_SLOT + 1, ItemStack.EMPTY);
            forge.setStack(FIRST_MODULE_SLOT + 3, ItemStack.EMPTY);
            context.assertTrue(!DiceModules.has(forge.createDie(), DiceModules.INFINITY), "the next die has no Infinity");
            forge.toggleProduction();
            GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
        });
    }

    /** Module slots take module items only, as many as a die may carry; a die still needs at least one face. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void moduleSlotsTakeModulesOnly(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        int slot = FIRST_MODULE_SLOT;
        context.assertEquals(MODULE_SLOTS, 4, "four module slots");
        context.assertEquals(SIZE, FIRST_MODULE_SLOT + MODULE_SLOTS, "they come last");
        context.assertTrue(forge.isValid(slot, module(DiceModules.SLOW, 1)), "a module goes in a module slot");
        context.assertTrue(!forge.isValid(slot, new ItemStack(face("dice_face_2"))), "a face does not");
        context.assertTrue(!forge.isValid(slot, new ItemStack(ModItems.BLACK_STAR_FRAGMENT)), "a fragment does not");
        context.assertTrue(!forge.isValid(0, module(DiceModules.SLOW, 1)), "a module is not a face");
        context.assertTrue(!forge.isValid(FIRST_FRAGMENT_SLOT, module(DiceModules.SLOW, 1)) && !forge.isValid(BLANK_SLOT, module(DiceModules.SLOW, 1)),
                "nor a fragment, nor a blank face");

        // Hoppers: a module slot never holds more of a module than a die may carry
        context.assertTrue(forge.canInsert(slot, module(DiceModules.SLOW, 1), Direction.UP), "into an empty slot");
        forge.setStack(slot, module(DiceModules.SLOW, 1));
        context.assertTrue(!forge.canInsert(slot, module(DiceModules.SLOW, 1), Direction.UP), "Slow doesn't stack");
        forge.setStack(slot + 1, module(DiceModules.LUCKY, DiceModules.LUCKY.maxCount() - 1));
        context.assertTrue(forge.canInsert(slot + 1, module(DiceModules.LUCKY, 1), Direction.UP), "Lucky stacks");
        forge.setStack(slot + 1, module(DiceModules.LUCKY, DiceModules.LUCKY.maxCount()));
        context.assertTrue(!forge.canInsert(slot + 1, module(DiceModules.LUCKY, 1), Direction.UP), "up to its maximum");
        context.assertTrue(!forge.canExtract(slot, forge.getStack(slot), Direction.DOWN), "modules are not taken out from below");

        // The GUI slots follow the same rules
        ServerPlayerEntity player = DiceTestKit.player(context);
        DiceForgeScreenHandler handler = new DiceForgeScreenHandler(1, player.getInventory(), forge, forge.getProperties());
        Slot guiSlot = handler.getSlot(slot);
        context.assertTrue(guiSlot.canInsert(module(DiceModules.REROLL, 1)) && !guiSlot.canInsert(new ItemStack(face("dice_face_2"))), "module slot in the GUI");
        context.assertEquals(guiSlot.getMaxItemCount(module(DiceModules.REROLL, 1)), DiceModules.REROLL.maxCount(), "as many as a die may carry");
        context.assertEquals(guiSlot.getMaxItemCount(module(DiceModules.CHOICE, 1)), 1, "one of a module that doesn't stack");
        context.assertEquals(DiceForgeScreenHandler.MODULE_POSITIONS.length, MODULE_SLOTS, "one position per slot");

        // Modules alone forge nothing: a die needs at least one face (a plain die gets its modules at the crafting table)
        for (int i = 0; i < FRAGMENT_SLOTS; i++) forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        forge.setStack(BLANK_SLOT, new ItemStack(ModItems.blankDiceFace(), 10));
        context.assertEquals(forge.getStatus(), Status.NOT_ENOUGH_FACES, "no face: no die");
        context.assertTrue(forge.createDie().isEmpty(), "nothing to forge");
        GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
    }

    /** The module slots are saved with the forge. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void moduleSlotsAreSaved(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        fill(forge);
        forge.setStack(FIRST_MODULE_SLOT, module(DiceModules.REROLL, 3));
        forge.setStack(FIRST_MODULE_SLOT + 3, module(DiceModules.REVERSED, 1));
        NbtCompound nbt = forge.createNbt(context.getWorld().getRegistryManager());

        DiceForgeBlockEntity loaded = new DiceForgeBlockEntity(forge.getPos(), forge.getCachedState());
        loaded.read(nbt, context.getWorld().getRegistryManager());
        context.assertTrue(ItemStack.areEqual(loaded.getStack(FIRST_MODULE_SLOT), module(DiceModules.REROLL, 3)), "first module slot");
        context.assertTrue(loaded.getStack(FIRST_MODULE_SLOT + 1).isEmpty() && loaded.getStack(FIRST_MODULE_SLOT + 2).isEmpty(), "empty ones stay empty");
        context.assertTrue(ItemStack.areEqual(loaded.getStack(FIRST_MODULE_SLOT + 3), module(DiceModules.REVERSED, 1)), "last module slot");
        context.assertEquals(DiceModules.of(DiceForgeBlockEntity.createDie(loaded)), Map.of(DiceModules.REROLL, 3, DiceModules.REVERSED, 1),
                "the die it forges carries them");
        context.assertTrue(ItemStack.areEqual(loaded.getStack(0), forge.getStack(0)) && ItemStack.areEqual(loaded.getStack(BLANK_SLOT), forge.getStack(BLANK_SLOT)),
                "with the rest of the forge");
        GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
    }
}
