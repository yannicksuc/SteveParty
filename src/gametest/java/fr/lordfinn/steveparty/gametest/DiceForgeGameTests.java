package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlock;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.entities.custom.ForgeCoreEntity;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.DiceForgeScreenHandler;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.random.Random;

import java.util.Arrays;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;

public class DiceForgeGameTests implements SteveGameTest {
    private static final BlockPos FORGE_POS = new BlockPos(1, 1, 1);
    private static final int TICK_LIMIT = 400;

    private static Item face(int value) {
        return Registries.ITEM.get(Steveparty.id("dice_face_" + value));
    }

    private static ItemStack blanks(int count) {
        return new ItemStack(Registries.ITEM.get(Steveparty.id("blank_dice_face")), count);
    }

    /** Places an activated forge (core inserted through the center slot) and returns it. */
    private static DiceForgeBlockEntity placeActivatedForge(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        forge.setStack(CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
        context.assertTrue(forge.isActivated(), "core inserted in the center slot activates the forge");
        context.assertTrue(forge.isCoreInPlace(), "the insertion is known: the core is drawn and animated");
        context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "the core goes into the forge, not the output slot");
        return forge;
    }

    /**
     * A craft outputs a die carrying the faces with their weights (the slot counts). Faces stay, one blank face per
     * face slot is consumed; a black fragment is never consumed, other colours are.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void craftProducesDieWithFacesAndKeepsBlackFragment(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(0, new ItemStack(face(1), 3));
        forge.setStack(5, new ItemStack(face(2), 3));
        forge.setStack(FIRST_FRAGMENT_SLOT, new ItemStack(ModItems.BLACK_STAR_FRAGMENT, 1));
        forge.setStack(FIRST_FRAGMENT_SLOT + 1, new ItemStack(ModItems.RED_STAR_FRAGMENT, 3));
        forge.setStack(FIRST_FRAGMENT_SLOT + 2, new ItemStack(ModItems.BLUE_STAR_FRAGMENT, 3));
        forge.setStack(FIRST_FRAGMENT_SLOT + 3, new ItemStack(ModItems.GREEN_STAR_FRAGMENT, 3));
        forge.setStack(FIRST_FRAGMENT_SLOT + 4, new ItemStack(ModItems.YELLOW_STAR_FRAGMENT, 3));
        forge.setStack(BLANK_SLOT, blanks(10));
        context.assertTrue(forge.start(), "production starts");

        context.waitAndRun(CRAFT_TIME + 10, () -> {
            ItemStack output = forge.getStack(OUTPUT_SLOT);
            context.assertTrue(output.isOf(ModItems.DEFAULT_DICE), "a die was forged: " + output);
            context.assertEquals(output.getCount(), 1, "one craft done");
            DiceFacesComponent faces = output.get(DiceFacesComponent.TYPE);
            context.assertTrue(faces != null, "the die carries its faces");
            context.assertEquals(faces.faces(),
                    List.of(new DiceFace(Kind.NORMAL, 1, 3), new DiceFace(Kind.NORMAL, 2, 3)), "faces and weights");
            context.assertEquals(forge.getStack(FIRST_FRAGMENT_SLOT).getCount(), 1, "black fragment not consumed");
            context.assertEquals(forge.getStack(FIRST_FRAGMENT_SLOT + 1).getCount(), 2, "red fragment consumed");
            context.assertEquals(forge.getStack(0).getCount(), 3, "faces are not consumed");
            context.assertEquals(forge.getStack(BLANK_SLOT).getCount(), 8, "one blank face per face slot consumed");
            context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "nothing in the center: it is the button");
            context.assertTrue(forge.isCrafting(), "still looping while items remain");
            context.assertTrue(forge.canExtract(OUTPUT_SLOT, output, Direction.DOWN), "output extractable from below");
            context.assertTrue(!forge.canExtract(0, forge.getStack(0), Direction.DOWN), "faces not extractable");
            forge.toggleProduction();
            context.assertTrue(!forge.isCrafting(), "toggle stops production");
            GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
        });
    }

    /** Non-black colours must differ between fragment slots; black may be repeated. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void duplicateNonBlackFragmentsAreRejected(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(0, new ItemStack(face(3)));
        forge.setStack(1, new ItemStack(face(4)));
        forge.setStack(FIRST_FRAGMENT_SLOT, new ItemStack(ModItems.RED_STAR_FRAGMENT));
        context.assertTrue(!forge.isValid(FIRST_FRAGMENT_SLOT + 1, new ItemStack(ModItems.RED_STAR_FRAGMENT)),
                "a second red slot is refused");
        context.assertTrue(!forge.canInsert(FIRST_FRAGMENT_SLOT + 1, new ItemStack(ModItems.RED_STAR_FRAGMENT), Direction.UP),
                "hoppers can't duplicate a colour either");
        context.assertTrue(forge.isValid(FIRST_FRAGMENT_SLOT + 1, new ItemStack(ModItems.YELLOW_STAR_FRAGMENT)),
                "another colour is accepted");
        forge.setStack(FIRST_FRAGMENT_SLOT + 1, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        context.assertTrue(forge.isValid(FIRST_FRAGMENT_SLOT + 2, new ItemStack(ModItems.BLACK_STAR_FRAGMENT)),
                "black can be repeated");
        context.assertTrue(!forge.isValid(FIRST_FRAGMENT_SLOT + 2, new ItemStack(face(1))), "faces don't go in fragment slots");

        // Forced duplicates (e.g. old data) are still refused by the craft itself
        forge.setStack(FIRST_FRAGMENT_SLOT + 2, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        forge.setStack(FIRST_FRAGMENT_SLOT + 3, new ItemStack(ModItems.RED_STAR_FRAGMENT));
        context.assertEquals(forge.getStatus(), Status.DUPLICATE_FRAGMENT, "status");
        context.assertTrue(!forge.start(), "craft refused with two red slots");

        forge.setStack(FIRST_FRAGMENT_SLOT + 3, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        context.assertEquals(forge.getStatus(), Status.MISSING_FRAGMENT, "the 5 fragment slots are all needed");
        forge.setStack(FIRST_FRAGMENT_SLOT + 4, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        context.assertEquals(forge.getStatus(), Status.NOT_ENOUGH_BLANK_FACES, "no blank faces yet");
        forge.setStack(BLANK_SLOT, blanks(2));
        context.assertEquals(forge.getStatus(), Status.OK, "1 red + 4 black is valid");
        GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
    }

    /** The loop stops as soon as the blank faces run out (no unwanted die), and remembers what is missing. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void loopStopsWhenBlankFacesRunOut(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(0, new ItemStack(face(1), 1));
        forge.setStack(1, new ItemStack(face(6), 5));
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        }
        forge.setStack(BLANK_SLOT, blanks(3)); // 2 per die: one die, then 1 left
        context.assertTrue(forge.start(), "production starts with 4 black fragments");

        context.waitAndRun(2 * CRAFT_TIME + 20, () -> {
            context.assertTrue(!forge.isCrafting(), "loop stopped");
            context.assertEquals(forge.getStack(OUTPUT_SLOT).getCount(), 1, "only one die forged");
            context.assertEquals(forge.getStack(BLANK_SLOT).getCount(), 1, "one blank face left");
            context.assertEquals(forge.getStack(0).getCount(), 1, "faces kept");
            context.assertEquals(forge.getStack(1).getCount(), 5, "faces kept");
            context.assertEquals(forge.getStatus(), Status.NOT_ENOUGH_BLANK_FACES, "status");
            for (int i = 0; i < FRAGMENT_SLOTS; i++) {
                context.assertEquals(forge.getStack(FIRST_FRAGMENT_SLOT + i).getCount(), 1, "black fragments kept");
            }
            // Automation refill: only blank faces go in the blank faces slot
            context.assertTrue(!forge.canInsert(BLANK_SLOT, new ItemStack(face(9)), Direction.UP), "wrong item refused");
            context.assertTrue(forge.canInsert(BLANK_SLOT, blanks(1), Direction.UP), "blank faces accepted");
            context.assertTrue(!forge.canInsert(OUTPUT_SLOT, blanks(1), Direction.UP), "nothing goes in the output");
            GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
        });
    }

    /** Hoppers fill it like any container; blank faces only go to their own slot, never on the ring. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void hoppersFillItLikeAContainer(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(0, new ItemStack(face(2)));
        context.assertTrue(forge.canInsert(0, new ItemStack(face(2)), Direction.UP), "same face stacks up");
        context.assertTrue(forge.canInsert(1, new ItemStack(face(5)), Direction.UP), "any face in a free slot");
        context.assertTrue(!forge.canInsert(1, blanks(1), Direction.UP), "blank faces never go on the ring");
        context.assertTrue(forge.canInsert(BLANK_SLOT, blanks(1), Direction.UP), "blank faces go to their slot");
        context.assertTrue(forge.canInsert(FIRST_FRAGMENT_SLOT, new ItemStack(ModItems.RED_STAR_FRAGMENT), Direction.UP),
                "fragments go to the fragment slots");
        context.assertTrue(!forge.canInsert(FIRST_FRAGMENT_SLOT, new ItemStack(face(5)), Direction.UP), "faces do not");
        // Hoppers below only take the forged dice out
        for (int slot = 0; slot < SIZE; slot++) {
            context.assertTrue(forge.canExtract(slot, new ItemStack(face(2)), Direction.DOWN) == (slot == OUTPUT_SLOT),
                    "only the output can be emptied, slot " + slot);
        }
        context.assertTrue(Arrays.equals(forge.getAvailableSlots(Direction.DOWN), new int[]{OUTPUT_SLOT}),
                "a hopper below only sees the output");
        context.complete();
    }

    /** Faces are not remembered: changing one during a loop does not stop it, the next die carries the new face. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void changingAFaceDuringALoopChangesTheNextDie(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(0, new ItemStack(face(1)));
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        }
        forge.setStack(BLANK_SLOT, blanks(10));
        context.assertTrue(forge.start(), "production starts");
        forge.setStack(0, new ItemStack(face(4)));
        context.waitAndRun(CRAFT_TIME + 10, () -> {
            context.assertTrue(forge.isCrafting(), "still running");
            ItemStack die = forge.getStack(OUTPUT_SLOT);
            context.assertTrue(!die.isEmpty(), "a die was forged");
            context.assertEquals(DiceFacesComponent.rollFace(die, Random.create(1)), 4, "with the new face");
            GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
        });
    }

    /** Forged dice roll only their faces; plain dice keep the 1..10 range. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rollFaceUsesForgedFaces(TestContext context) {
        ItemStack die = DiceFacesComponent.createDie(List.of(new ItemStack(face(3)), new ItemStack(face(7))));
        Random random = Random.create(42);
        for (int i = 0; i < 200; i++) {
            int value = DiceFacesComponent.rollFace(die, random);
            context.assertTrue(value == 3 || value == 7, "forged roll " + value);
            int plain = DiceFacesComponent.rollFace(new ItemStack(ModItems.DEFAULT_DICE), random);
            context.assertTrue(plain >= 1 && plain <= 10, "plain roll " + plain);
        }
        context.complete();
    }

    /** A single face is enough: the die always rolls that face. No face at all is still refused. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void singleFaceDieIsForgedAndAlwaysRollsThatFace(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        }
        context.assertEquals(forge.getStatus(), Status.NOT_ENOUGH_FACES, "no face at all");
        forge.setStack(BLANK_SLOT, blanks(4));
        forge.setStack(7, new ItemStack(face(4), 2));
        context.assertEquals(forge.getStatus(), Status.OK, "one face is enough");
        context.assertTrue(forge.start(), "production starts with a single face");

        context.waitAndRun(CRAFT_TIME + 10, () -> {
            ItemStack output = forge.getStack(OUTPUT_SLOT);
            context.assertTrue(output.isOf(ModItems.DEFAULT_DICE), "a die was forged: " + output);
            DiceFacesComponent faces = output.get(DiceFacesComponent.TYPE);
            context.assertTrue(faces != null, "the die carries its face");
            context.assertEquals(faces.faces(), List.of(new DiceFace(Kind.NORMAL, 4, 2)), "faces");
            context.assertEquals(forge.getStack(BLANK_SLOT).getCount(), 3, "one blank face used");
            Random random = Random.create(7);
            for (int i = 0; i < 100; i++) {
                context.assertEquals(DiceFacesComponent.rollFace(output, random), 4, "single-face roll");
            }
            forge.toggleProduction();
            GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
        });
    }

    /**
     * Sneak + right-click (empty hand) takes the core back: the forge is deactivated, the running craft stops
     * without consuming anything, the core goes to the player (the forged dice stay in the output slot), and
     * re-inserting replays the insertion. Refused while the insertion animation plays.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void sneakUseRemovesTheCoreAndDeactivates(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        ItemStack forged = DiceFacesComponent.createDie(List.of(new ItemStack(face(2))));
        forge.setStack(OUTPUT_SLOT, forged.copyWithCount(3));
        forge.setStack(BLANK_SLOT, blanks(5));
        forge.setStack(0, new ItemStack(face(5), 4));
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        }
        forge.setStack(FIRST_FRAGMENT_SLOT, new ItemStack(ModItems.RED_STAR_FRAGMENT, 2));

        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        player.getInventory().clear();
        player.setSneaking(true);
        BlockPos abs = context.getAbsolutePos(FORGE_POS);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);

        context.assertTrue(!forge.canRemoveCore(), "not removable during the insertion animation");
        context.getBlockState(FORGE_POS).onUse(context.getWorld(), player, hit);
        context.assertTrue(forge.isActivated(), "still activated while the core settles");

        context.waitAndRun(CORE_INSERT_TICKS + 5, () -> {
            try {
                // The output holds another die: the loop runs but waits at 100% (OUTPUT_BLOCKED)
                context.assertTrue(forge.start(), "production starts");
                context.assertTrue(forge.isCrafting(), "crafting before removal");

                context.getBlockState(FORGE_POS).onUse(context.getWorld(), player, hit);

                context.assertTrue(!context.getBlockState(FORGE_POS).get(DiceForgeBlock.ACTIVATED), "block state deactivated");
                context.assertTrue(!forge.isActivated(), "forge deactivated");
                context.assertTrue(!forge.isCrafting(), "craft stopped");
                context.assertEquals(forge.getStatus(), Status.NOT_ACTIVATED, "status");
                context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "center slot freed for the core");
                context.assertEquals(player.getInventory().count(ModBlocks.GRAVITY_CORE.asItem()), 1, "core given back");
                context.assertEquals(forge.getStack(OUTPUT_SLOT).getCount(), 3, "forged dice stay in the output slot");
                context.assertEquals(forge.getStack(BLANK_SLOT).getCount(), 5, "blank faces not consumed");
                context.assertEquals(forge.getStack(0).getCount(), 4, "faces not consumed");
                context.assertEquals(forge.getStack(FIRST_FRAGMENT_SLOT).getCount(), 2, "fragments not consumed");
                context.assertTrue(forge.getExtraDrops().isEmpty(), "no core left to drop when broken");

                // Saved state: no activation time left over
                DiceForgeBlockEntity reloaded = new DiceForgeBlockEntity(abs, context.getBlockState(FORGE_POS));
                reloaded.read(forge.createNbt(context.getWorld().getRegistryManager()), context.getWorld().getRegistryManager());
                context.assertTrue(!reloaded.isCrafting(), "not crafting after reload");
                context.assertEquals(reloaded.getActivationTime(), forge.getActivationTime(), "activation time saved");

                // Re-inserting plays the insertion again
                forge.setStack(CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
                context.assertTrue(forge.isActivated(), "re-activated");
                context.assertEquals(forge.getActivationTime(), context.getWorld().getTime(), "new activation time");
                context.assertTrue(forge.isInsertingCore(0f), "insertion animation replays");
            } finally {
                TestPlayers.remove(context, player);
            }
            GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
        });
    }

    /** Old forges kept a party star in slot 12: it is given back (dropped), never deleted. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyPartyStarIsDropped(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        NbtCompound nbt = new NbtCompound();
        NbtList items = new NbtList();
        NbtCompound star = (NbtCompound) new ItemStack(ModItems.PARTY_STAR).encode(context.getWorld().getRegistryManager());
        star.putByte("Slot", (byte) 12);
        items.add(star);
        nbt.put("Items", items);
        forge.read(nbt, context.getWorld().getRegistryManager());
        context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "star removed from the output slot");
        context.waitAndRun(5, () -> {
            context.expectItemAt(ModItems.PARTY_STAR, FORGE_POS.up(), 2.0);
            context.complete();
        });
    }

    /** A face placed 10 times comes up about 10 times more often than a face placed once. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void weightsBiasTheRoll(TestContext context) {
        ItemStack die = DiceFacesComponent.createDie(List.of(new ItemStack(face(10), 10), new ItemStack(face(1), 1)));
        Random random = Random.create(1234);
        int tens = 0, ones = 0;
        for (int i = 0; i < 22000; i++) {
            int value = DiceFacesComponent.rollFace(die, random);
            if (value == 10) tens++;
            else if (value == 1) ones++;
            else context.throwGameTestException("unexpected face " + value);
        }
        double ratio = (double) tens / ones;
        context.assertTrue(ratio > 8.5 && ratio < 11.5, "about 10 times more tens, ratio " + ratio);
        context.complete();
    }

    /** The same face in two slots adds up its weights; each slot still costs one blank face. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sameFaceInTwoSlotsAddsUpWeights(TestContext context) {
        ItemStack die = DiceFacesComponent.createDie(List.of(new ItemStack(face(6), 5), new ItemStack(face(6), 3)));
        DiceFacesComponent faces = die.get(DiceFacesComponent.TYPE);
        context.assertEquals(faces.faces(), List.of(new DiceFace(Kind.NORMAL, 6, 8)), "one face of weight 8");

        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(2, new ItemStack(face(6), 5));
        forge.setStack(9, new ItemStack(face(6), 3));
        context.assertEquals(countFaces(forge), 2, "two blank faces per die");
        context.complete();
    }

    /** Dice forged before weights existed keep working: every face weighs 1. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void diceForgedBeforeWeightsReadWeightOne(TestContext context) {
        NbtCompound face = new NbtCompound();
        face.putString("kind", "normal");
        face.putInt("value", 5);
        NbtList list = new NbtList();
        list.add(face);
        DiceFacesComponent faces = DiceFacesComponent.CODEC.parse(NbtOps.INSTANCE, list).getOrThrow();
        context.assertEquals(faces.faces(), List.of(new DiceFace(Kind.NORMAL, 5, 1)), "weight 1 by default");
        context.complete();
    }

    /** Forges saved when the die waited in the center slot move it to the output slot. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void centerDieMovesToTheOutputSlot(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        ItemStack forged = DiceFacesComponent.createDie(List.of(new ItemStack(face(3))));
        NbtCompound nbt = new NbtCompound();
        NbtList items = new NbtList();
        NbtCompound die = (NbtCompound) forged.copyWithCount(2).encode(context.getWorld().getRegistryManager());
        die.putByte("Slot", (byte) CENTER_SLOT);
        items.add(die);
        nbt.put("Items", items);
        nbt.putInt("ForgeVersion", 2);
        forge.read(nbt, context.getWorld().getRegistryManager());
        context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "center slot emptied");
        context.assertEquals(forge.getStack(OUTPUT_SLOT).getCount(), 2, "dice moved to the output slot");
        context.complete();
    }

    /**
     * Forges saved with 4 fragment slots (version 3): the blank faces, output and module slots move up by one, the
     * 5th fragment slot is empty, and the remembered blank faces follow their slot.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void version3SlotsMoveUpForTheFifthFragment(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        var registries = context.getWorld().getRegistryManager();
        ItemStack forged = DiceFacesComponent.createDie(List.of(new ItemStack(face(3))));
        NbtList items = new NbtList();
        // Version 3: fragments 13..16, blank faces 17, output 18, modules 19..22
        Object[][] saved = {{16, new ItemStack(ModItems.RED_STAR_FRAGMENT, 5)}, {17, blanks(7)},
                {18, forged.copyWithCount(2)}, {19, new ItemStack(DiceModules.LUCKY.item(), 2)},
                {22, new ItemStack(DiceModules.SLOW.item())}};
        for (Object[] entry : saved) {
            NbtCompound item = (NbtCompound) ((ItemStack) entry[1]).encode(registries);
            item.putByte("Slot", (byte) (int) (Integer) entry[0]);
            items.add(item);
        }
        NbtList layout = new NbtList();
        for (int i = 0; i < 17; i++) {
            layout.add(NbtString.of(i == 15 ? "steveparty:red_star_fragment" : i == 16 ? "steveparty:blank_dice_face" : ""));
        }
        NbtCompound nbt = new NbtCompound();
        nbt.put("Items", items);
        nbt.put("Layout", layout);
        nbt.putInt("ForgeVersion", 3);
        forge.read(nbt, registries);

        context.assertEquals(forge.getStack(FIRST_FRAGMENT_SLOT + 3).getCount(), 5, "4th fragment slot unchanged");
        context.assertTrue(forge.getStack(FIRST_FRAGMENT_SLOT + 4).isEmpty(), "the 5th fragment slot is new, empty");
        context.assertEquals(forge.getStack(BLANK_SLOT).getCount(), 7, "blank faces moved to their new slot");
        context.assertEquals(forge.getStack(OUTPUT_SLOT).getCount(), 2, "dice moved to the new output slot");
        context.assertEquals(forge.getStack(FIRST_MODULE_SLOT).getCount(), 2, "first module moved");
        context.assertTrue(forge.getStack(FIRST_MODULE_SLOT + 3).isOf(DiceModules.SLOW.item()), "last old module moved");
        context.assertTrue(forge.getStack(FIRST_MODULE_SLOT + 4).isEmpty(), "the 5th module slot is new, empty");
        DiceForgeScreenHandler handler = new DiceForgeScreenHandler(1, DiceTestKit.player(context).getInventory(), forge, forge.getProperties());
        context.assertTrue(handler.getGhost(FIRST_FRAGMENT_SLOT + 3) == ModItems.RED_STAR_FRAGMENT, "fragment ghost kept");
        context.assertTrue(handler.getGhost(FIRST_FRAGMENT_SLOT + 4) == null, "no ghost on the new fragment slot");
        context.assertTrue(handler.getGhost(BLANK_SLOT) == ModItems.blankDiceFace(), "blank faces ghost follows its slot");
        context.complete();
    }

    /** Core altitude: every fragment counts, a black one as a full stack, 320 fragments (5 stacks) = 16 blocks (max). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void coreAltitudeFollowsTheFragments(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(FIRST_FRAGMENT_SLOT, new ItemStack(ModItems.RED_STAR_FRAGMENT, 64));
        forge.setStack(FIRST_FRAGMENT_SLOT + 1, new ItemStack(ModItems.BLUE_STAR_FRAGMENT, 40));
        forge.setStack(FIRST_FRAGMENT_SLOT + 2, new ItemStack(ModItems.YELLOW_STAR_FRAGMENT, 20));
        forge.setStack(FIRST_FRAGMENT_SLOT + 3, new ItemStack(ModItems.BLACK_STAR_FRAGMENT, 1));
        forge.setStack(FIRST_FRAGMENT_SLOT + 4, new ItemStack(ModItems.GREEN_STAR_FRAGMENT, 52));
        context.assertEquals(countAltitudeFragments(forge), 240, "64 + 40 + 20 + black (64) + 52");
        context.assertTrue(Math.abs(getTargetAltitude(forge) - 12f) < 1e-4, "240 / 320 x 16 blocks");
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        }
        context.assertTrue(getTargetAltitude(forge) == MAX_CORE_ALTITUDE, "5 black fragments: 16 blocks");
        GravityGameTests.removeAndComplete(context, FORGE_POS); // its core has risen
    }

    /** A loaded forge: faces, blank faces, one fragment of each colour (a low core), a forged die in the output. */
    private static DiceForgeBlockEntity loadedForge(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(0, new ItemStack(face(1), 3));
        forge.setStack(5, new ItemStack(face(6), 2));
        forge.setStack(FIRST_FRAGMENT_SLOT, new ItemStack(ModItems.RED_STAR_FRAGMENT));
        forge.setStack(FIRST_FRAGMENT_SLOT + 1, new ItemStack(ModItems.BLUE_STAR_FRAGMENT));
        forge.setStack(FIRST_FRAGMENT_SLOT + 2, new ItemStack(ModItems.GREEN_STAR_FRAGMENT));
        forge.setStack(FIRST_FRAGMENT_SLOT + 3, new ItemStack(ModItems.YELLOW_STAR_FRAGMENT));
        forge.setStack(FIRST_FRAGMENT_SLOT + 4, new ItemStack(ModItems.PURPLE_STAR_FRAGMENT));
        forge.setStack(BLANK_SLOT, blanks(7));
        forge.setStack(OUTPUT_SLOT, DiceFacesComponent.createDie(List.of(new ItemStack(face(2)))));
        return forge;
    }

    private static List<ForgeCoreEntity> coreHitboxes(TestContext context) {
        BlockPos abs = context.getAbsolutePos(FORGE_POS);
        return context.getWorld().getEntitiesByClass(ForgeCoreEntity.class, new Box(abs).expand(1, MAX_CORE_ALTITUDE + 3, 1), e -> true);
    }

    private static int dropped(TestContext context, Item item) {
        BlockPos abs = context.getAbsolutePos(FORGE_POS);
        return context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(abs).expand(3), e -> e.getStack().isOf(item))
                .stream().mapToInt(e -> e.getStack().getCount()).sum();
    }

    /**
     * Playtest #54: punching a loaded forge blew it up. Its core, low over the plate (few fragments), was in the way
     * of the punches: it has no hitbox there, so hitting the forge only breaks it, and everything in it drops.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void punchingALoadedForgeBreaksItWithoutBlowingItUp(TestContext context) {
        DiceForgeBlockEntity forge = loadedForge(context);
        context.waitAndRun(CORE_INSERT_TICKS + 40, () -> {
            context.assertTrue(forge.getCoreCenter().y - context.getAbsolutePos(FORGE_POS).getY() < CORE_REST_HEIGHT + CORE_HIT_ALTITUDE,
                    "a low core: " + forge.getCoreCenter());
            context.assertTrue(coreHitboxes(context).isEmpty(), "a low core can't be hit (it floats where the forge is punched)");
            ServerPlayerEntity player = TestPlayers.mock(context);
            try {
                player.changeGameMode(GameMode.SURVIVAL);
                player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
                context.assertTrue(player.interactionManager.tryBreakBlock(context.getAbsolutePos(FORGE_POS)), "the forge breaks");
            } finally {
                TestPlayers.remove(context, player);
            }
            context.waitAndRun(2, () -> {
                context.assertEquals(dropped(context, ModBlocks.DICE_FORGE.asItem()), 1, "the forge itself");
                context.assertEquals(dropped(context, ModBlocks.GRAVITY_CORE.asItem()), 1, "its gravity core");
                context.assertEquals(dropped(context, face(1)), 3, "its faces");
                context.assertEquals(dropped(context, face(6)), 2, "its faces");
                context.assertEquals(dropped(context, ModItems.PURPLE_STAR_FRAGMENT), 1, "its fragments");
                context.assertEquals(dropped(context, blanks(1).getItem()), 7, "its blank faces");
                context.assertEquals(dropped(context, ModItems.DEFAULT_DICE), 1, "its forged die");
                context.complete();
            });
        });
    }

    /** The core risen high (in the sky) can still be hit: it blows up, the forge keeps everything but its core. */
    // alone in its batch: the risen core pulls, and its blast flings, what other tests have around
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT, batchId = "dice_forge_core_hit")
    public void hittingTheRisenCoreKeepsTheForgeAndItsContents(TestContext context) {
        DiceForgeBlockEntity forge = loadedForge(context);
        for (int i = 0; i < FRAGMENT_SLOTS; i++) forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        context.waitAndRun(CORE_INSERT_TICKS + 40, () -> {
            List<ForgeCoreEntity> hitboxes = coreHitboxes(context);
            context.assertEquals(hitboxes.size(), 1, "the risen core has its hitbox");
            ServerPlayerEntity player = TestPlayers.mock(context);
            try {
                player.attack(hitboxes.getFirst());
            } finally {
                TestPlayers.remove(context, player);
            }
            context.assertTrue(context.getBlockState(FORGE_POS).isOf(ModBlocks.DICE_FORGE), "the forge stays");
            context.assertTrue(!forge.isActivated(), "its core is gone");
            context.assertEquals(forge.getStack(0).getCount(), 3, "faces kept");
            context.assertEquals(forge.getStack(BLANK_SLOT).getCount(), 7, "blank faces kept");
            context.assertTrue(forge.getStack(FIRST_FRAGMENT_SLOT).isOf(ModItems.BLACK_STAR_FRAGMENT), "fragments kept");
            context.assertTrue(forge.getStack(OUTPUT_SLOT).isOf(ModItems.DEFAULT_DICE), "forged die kept");
            context.complete();
        });
    }

    /** Gravity cores anywhere: in the player's inventory, on its cursor, in the forge. */
    private static int coresAround(DiceForgeBlockEntity forge, ServerPlayerEntity player, DiceForgeScreenHandler handler) {
        int cursor = handler.getCursorStack().isOf(ModBlocks.GRAVITY_CORE.asItem()) ? handler.getCursorStack().getCount() : 0;
        return player.getInventory().count(ModBlocks.GRAVITY_CORE.asItem()) + cursor + (forge.isActivated() ? 1 : 0);
    }

    /**
     * The core is taken back from the center slot of the screen like any item (click, shift-click, number key): the
     * forge goes to sleep; put back, it wakes up. Never during the insertion animation, and never lost nor duplicated.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 3 * CORE_INSERT_TICKS + 100)
    public void theCoreIsTakenBackFromItsSlot(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        ServerPlayerEntity player = DiceTestKit.player(context);
        player.changeGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        DiceForgeScreenHandler handler = new DiceForgeScreenHandler(1, player.getInventory(), forge, forge.getProperties());
        context.assertTrue(handler.getSlot(CENTER_SLOT).getStack().isOf(ModBlocks.GRAVITY_CORE.asItem()), "the slot shows the core");

        // Not while it is being inserted
        handler.onSlotClick(CENTER_SLOT, 0, SlotActionType.PICKUP, player);
        context.assertTrue(forge.isActivated() && handler.getCursorStack().isEmpty(), "kept during the insertion");
        context.assertEquals(coresAround(forge, player, handler), 1, "one core");

        context.waitAndRun(CORE_INSERT_TICKS + 5, () -> {
            // Click: on the cursor, the forge asleep
            handler.onSlotClick(CENTER_SLOT, 0, SlotActionType.PICKUP, player);
            context.assertTrue(handler.getCursorStack().isOf(ModBlocks.GRAVITY_CORE.asItem()), "core on the cursor");
            context.assertTrue(!forge.isActivated(), "forge asleep");
            context.assertEquals(forge.getStatus(), Status.NOT_ACTIVATED, "status");
            context.assertTrue(handler.getSlot(CENTER_SLOT).getStack().isEmpty(), "slot empty");
            context.assertEquals(coresAround(forge, player, handler), 1, "one core");
            // Put back: awake again
            handler.onSlotClick(CENTER_SLOT, 0, SlotActionType.PICKUP, player);
            context.assertTrue(forge.isActivated() && handler.getCursorStack().isEmpty(), "put back: awake");
            context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "the core is in the forge, not its inventory");
            context.assertEquals(coresAround(forge, player, handler), 1, "one core");

            context.waitAndRun(CORE_INSERT_TICKS + 5, () -> {
                // Shift-click: to the inventory
                handler.onSlotClick(CENTER_SLOT, 0, SlotActionType.QUICK_MOVE, player);
                context.assertTrue(!forge.isActivated(), "shift-clicked out: asleep");
                context.assertEquals(player.getInventory().count(ModBlocks.GRAVITY_CORE.asItem()), 1, "in the inventory");
                context.assertEquals(coresAround(forge, player, handler), 1, "one core");
                // Shift-click back from the inventory
                int from = -1;
                for (int i = SIZE; i < handler.slots.size(); i++) {
                    if (handler.getSlot(i).getStack().isOf(ModBlocks.GRAVITY_CORE.asItem())) from = i;
                }
                handler.onSlotClick(from, 0, SlotActionType.QUICK_MOVE, player);
                context.assertTrue(forge.isActivated(), "shift-clicked back: awake");
                context.assertEquals(coresAround(forge, player, handler), 1, "one core");

                context.waitAndRun(CORE_INSERT_TICKS + 5, () -> {
                    // Number key: straight to an empty hotbar slot
                    handler.onSlotClick(CENTER_SLOT, 3, SlotActionType.SWAP, player);
                    context.assertTrue(!forge.isActivated(), "swapped out: asleep");
                    context.assertTrue(player.getInventory().getStack(3).isOf(ModBlocks.GRAVITY_CORE.asItem()), "in hotbar slot 4");
                    context.assertEquals(coresAround(forge, player, handler), 1, "one core");
                    context.complete();
                });
            });
        });
    }
}
