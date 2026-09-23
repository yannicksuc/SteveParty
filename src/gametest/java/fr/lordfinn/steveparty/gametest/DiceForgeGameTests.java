package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlock;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.util.math.random.Random;

import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;

public class DiceForgeGameTests implements FabricGameTest {
    private static final BlockPos FORGE_POS = new BlockPos(1, 1, 1);
    private static final int TICK_LIMIT = 400;

    private static Item face(int value) {
        return Registries.ITEM.get(Steveparty.id("dice_face_" + value));
    }

    /** Places an activated forge (core inserted through the center slot) and returns it. */
    private static DiceForgeBlockEntity placeActivatedForge(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        forge.setStack(CENTER_SLOT, new ItemStack(ModBlocks.GRAVITY_CORE));
        context.assertTrue(forge.isActivated(), "core inserted in the center slot activates the forge");
        context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "the core goes into the forge, not the output slot");
        return forge;
    }

    /** A craft outputs a die carrying the faces; a black fragment is never consumed, other colours are. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void craftProducesDieWithFacesAndKeepsBlackFragment(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(0, new ItemStack(face(1), 3));
        forge.setStack(5, new ItemStack(face(2), 3));
        forge.setStack(FIRST_FRAGMENT_SLOT, new ItemStack(ModItems.BLACK_STAR_FRAGMENT, 1));
        forge.setStack(FIRST_FRAGMENT_SLOT + 1, new ItemStack(ModItems.RED_STAR_FRAGMENT, 3));
        forge.setStack(FIRST_FRAGMENT_SLOT + 2, new ItemStack(ModItems.BLUE_STAR_FRAGMENT, 3));
        forge.setStack(FIRST_FRAGMENT_SLOT + 3, new ItemStack(ModItems.GREEN_STAR_FRAGMENT, 3));
        context.assertTrue(forge.start(), "production starts");

        context.waitAndRun(CRAFT_TIME + 10, () -> {
            ItemStack output = forge.getStack(CENTER_SLOT);
            context.assertTrue(output.isOf(ModItems.DEFAULT_DICE), "a die was forged: " + output);
            context.assertEquals(output.getCount(), 1, "one craft done");
            DiceFacesComponent faces = output.get(DiceFacesComponent.TYPE);
            context.assertTrue(faces != null, "the die carries its faces");
            context.assertEquals(faces.faces(),
                    List.of(new DiceFace(Kind.NORMAL, 1), new DiceFace(Kind.NORMAL, 2)), "faces");
            context.assertEquals(forge.getStack(FIRST_FRAGMENT_SLOT).getCount(), 1, "black fragment not consumed");
            context.assertEquals(forge.getStack(FIRST_FRAGMENT_SLOT + 1).getCount(), 2, "red fragment consumed");
            context.assertEquals(forge.getStack(0).getCount(), 2, "face consumed");
            context.assertTrue(forge.isCrafting(), "still looping while items remain");
            context.assertTrue(forge.canExtract(CENTER_SLOT, output, Direction.DOWN), "output extractable from below");
            context.assertTrue(!forge.canExtract(0, forge.getStack(0), Direction.DOWN), "faces not extractable");
            forge.toggleProduction();
            context.assertTrue(!forge.isCrafting(), "toggle stops production");
            context.complete();
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
        context.assertEquals(forge.getStatus(false), Status.DUPLICATE_FRAGMENT, "status");
        context.assertTrue(!forge.start(), "craft refused with two red slots");

        forge.setStack(FIRST_FRAGMENT_SLOT + 3, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        context.assertEquals(forge.getStatus(false), Status.OK, "1 red + 3 black is valid");
        context.complete();
    }

    /** The loop stops as soon as a face slot runs out (no unwanted die), and remembers the missing face. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void loopStopsWhenASlotRunsOut(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        forge.setStack(0, new ItemStack(face(1), 1));
        forge.setStack(1, new ItemStack(face(6), 5));
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        }
        context.assertTrue(forge.start(), "production starts with 4 black fragments");

        context.waitAndRun(2 * CRAFT_TIME + 20, () -> {
            context.assertTrue(!forge.isCrafting(), "loop stopped");
            context.assertEquals(forge.getStack(CENTER_SLOT).getCount(), 1, "only one die forged");
            context.assertEquals(forge.getStack(1).getCount(), 4, "the other face was not consumed further");
            context.assertTrue(forge.getLayoutItem(0) == face(1), "missing face remembered (ghost)");
            for (int i = 0; i < FRAGMENT_SLOTS; i++) {
                context.assertEquals(forge.getStack(FIRST_FRAGMENT_SLOT + i).getCount(), 1, "black fragments kept");
            }
            // Automation refill: only the remembered face is accepted in the empty slot
            context.assertTrue(!forge.canInsert(0, new ItemStack(face(9)), Direction.UP), "wrong face refused");
            context.assertTrue(forge.canInsert(0, new ItemStack(face(1)), Direction.UP), "remembered face accepted");
            context.complete();
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
        context.assertEquals(forge.getStatus(false), Status.NOT_ENOUGH_FACES, "no face at all");
        forge.setStack(7, new ItemStack(face(4), 2));
        context.assertEquals(forge.getStatus(false), Status.OK, "one face is enough");
        context.assertTrue(forge.start(), "production starts with a single face");

        context.waitAndRun(CRAFT_TIME + 10, () -> {
            ItemStack output = forge.getStack(CENTER_SLOT);
            context.assertTrue(output.isOf(ModItems.DEFAULT_DICE), "a die was forged: " + output);
            DiceFacesComponent faces = output.get(DiceFacesComponent.TYPE);
            context.assertTrue(faces != null, "the die carries its face");
            context.assertEquals(faces.faces(), List.of(new DiceFace(Kind.NORMAL, 4)), "faces");
            Random random = Random.create(7);
            for (int i = 0; i < 100; i++) {
                context.assertEquals(DiceFacesComponent.rollFace(output, random), 4, "single-face roll");
            }
            forge.toggleProduction();
            context.complete();
        });
    }

    /**
     * Sneak + right-click (empty hand) takes the core back: the forge is deactivated, the running craft stops
     * without consuming anything, the core and the forged dice go to the player, and re-inserting replays the
     * insertion. Refused while the insertion animation plays.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = TICK_LIMIT)
    public void sneakUseRemovesTheCoreAndDeactivates(TestContext context) {
        DiceForgeBlockEntity forge = placeActivatedForge(context);
        ItemStack forged = DiceFacesComponent.createDie(List.of(new ItemStack(face(2))));
        forge.setStack(CENTER_SLOT, forged.copyWithCount(3));
        forge.setStack(0, new ItemStack(face(5), 4));
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            forge.setStack(FIRST_FRAGMENT_SLOT + i, new ItemStack(ModItems.BLACK_STAR_FRAGMENT));
        }
        forge.setStack(FIRST_FRAGMENT_SLOT, new ItemStack(ModItems.RED_STAR_FRAGMENT, 2));

        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
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
                context.assertEquals(forge.getStatus(false), Status.NOT_ACTIVATED, "status");
                context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "center slot freed for the core");
                context.assertEquals(player.getInventory().count(ModBlocks.GRAVITY_CORE.asItem()), 1, "core given back");
                context.assertEquals(player.getInventory().count(ModItems.DEFAULT_DICE), 3, "forged dice given back");
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
                context.getWorld().getServer().getPlayerManager().remove(player);
            }
            context.complete();
        });
    }

    /** Old forges kept a power star in slot 12: it is given back (dropped), never deleted. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyPowerStarIsDropped(TestContext context) {
        context.setBlockState(FORGE_POS, ModBlocks.DICE_FORGE.getDefaultState());
        DiceForgeBlockEntity forge = context.getBlockEntity(FORGE_POS);
        NbtCompound nbt = new NbtCompound();
        NbtList items = new NbtList();
        NbtCompound star = (NbtCompound) new ItemStack(ModItems.POWER_STAR).toNbt(context.getWorld().getRegistryManager());
        star.putByte("Slot", (byte) 12);
        items.add(star);
        nbt.put("Items", items);
        forge.read(nbt, context.getWorld().getRegistryManager());
        context.assertTrue(forge.getStack(CENTER_SLOT).isEmpty(), "star removed from the output slot");
        context.waitAndRun(5, () -> {
            context.expectItemAt(ModItems.POWER_STAR, FORGE_POS.up(), 2.0);
            context.complete();
        });
    }
}
