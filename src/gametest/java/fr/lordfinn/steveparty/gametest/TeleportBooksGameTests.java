package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.TeleportationPadBlockEntity;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.teleportation_books.HereWeGoBookItem;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.DESTINATIONS_COMPONENT;
import static fr.lordfinn.steveparty.components.ModComponents.STATE;

public class TeleportBooksGameTests implements FabricGameTest {
    /** The pad waits 10 ticks before teleporting. */
    private static final int TELEPORT_DELAY = 10;
    private static final double EPSILON = 1.0E-4;

    // ------------------------------------------------------------------ helpers

    /** A teleportation pad on stone, holding {@code book} (may be empty). */
    static TeleportationPadBlockEntity placePad(TestContext context, BlockPos pos, ItemStack book) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.TELEPORTATION_PAD);
        TeleportationPadBlockEntity pad = context.getBlockEntity(pos);
        if (!book.isEmpty()) pad.setBook(book);
        return pad;
    }

    static ItemStack hereWeGo(HereWeGoBookItem.State state, BlockPos... absoluteDestinations) {
        ItemStack book = new ItemStack(ModItems.HERE_WE_GO_BOOK);
        book.set(STATE, state.getValue());
        if (absoluteDestinations.length > 0)
            book.set(DESTINATIONS_COMPONENT, new DestinationsComponent(new ArrayList<>(List.of(absoluteDestinations)), ""));
        return book;
    }

    static ServerPlayerEntity playerAt(TestContext context, BlockPos relativePos, double feetOffset) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(GameMode.SURVIVAL);
        moveTo(context, player, relativePos, feetOffset);
        return player;
    }

    static void moveTo(TestContext context, ServerPlayerEntity player, BlockPos relativePos, double feetOffset) {
        BlockPos abs = context.getAbsolutePos(relativePos);
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY() + feetOffset, abs.getZ() + 0.5, 0, 0);
    }

    /** What the pad does every tick a player touches it. */
    static void stepOn(TestContext context, BlockPos padPos, ServerPlayerEntity player) {
        context.getBlockState(padPos).onEntityCollision(context.getWorld(), context.getAbsolutePos(padPos), player);
    }

    static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    static void assertFeetAt(TestContext context, ServerPlayerEntity player, BlockPos relativeBlock, double feetOffset, String what) {
        BlockPos abs = context.getAbsolutePos(relativeBlock);
        context.assertTrue(Math.abs(player.getX() - (abs.getX() + 0.5)) < EPSILON
                        && Math.abs(player.getZ() - (abs.getZ() + 0.5)) < EPSILON
                        && Math.abs(player.getY() - (abs.getY() + feetOffset)) < EPSILON,
                what + ": expected feet at " + abs + " +" + feetOffset + ", got " + player.getPos());
        context.assertTrue(context.getWorld().isSpaceEmpty(player, player.getBoundingBox().contract(1.0E-7)),
                what + ": the player is not inside a block");
    }

    // ------------------------------------------------------------------ saved positions land on top of the block

    /** A saved position is the clicked block: the player lands standing on it, not inside it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedPositionLandsOnTopOfTheClickedBlock(TestContext context) {
        BlockPos padPos = new BlockPos(1, 1, 1);
        BlockPos clicked = new BlockPos(4, 1, 4);
        context.setBlockState(clicked, Blocks.STONE);
        placePad(context, padPos, hereWeGo(HereWeGoBookItem.State.TP_REGISTERED_POS, context.getAbsolutePos(clicked)));
        ServerPlayerEntity player = playerAt(context, padPos, 0.25);
        stepOn(context, padPos, player);
        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, clicked, 1.0, "on top of the stone");
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    /** Something right above the clicked block: the player lands in the nearest free space above. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedPositionUnderABlockLandsInTheFreeSpaceAbove(TestContext context) {
        BlockPos padPos = new BlockPos(1, 1, 1);
        BlockPos clicked = new BlockPos(4, 1, 4);
        context.setBlockState(clicked, Blocks.STONE);
        context.setBlockState(clicked.up(), Blocks.OAK_SLAB); // bottom slab: top at +0.5
        placePad(context, padPos, hereWeGo(HereWeGoBookItem.State.TP_REGISTERED_POS, context.getAbsolutePos(clicked)));
        ServerPlayerEntity player = playerAt(context, padPos, 0.25);
        stepOn(context, padPos, player);
        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, clicked.up(), 0.5, "on the slab above the clicked block");
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    /** A saved teleportation pad: the player stands on the pad (4 pixels high), not in it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void savedPadPositionLandsOnThePad(TestContext context) {
        BlockPos padPos = new BlockPos(1, 1, 1);
        BlockPos targetPad = new BlockPos(5, 1, 5);
        placePad(context, targetPad, ItemStack.EMPTY);
        placePad(context, padPos, hereWeGo(HereWeGoBookItem.State.TP_REGISTERED_POS, context.getAbsolutePos(targetPad)));
        ServerPlayerEntity player = playerAt(context, padPos, 0.25);
        stepOn(context, padPos, player);
        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, targetPad, 0.25, "on the target pad");
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    // ------------------------------------------------------------------ "just arrived" guard

    /**
     * « Last used » brings the player back onto a pad that has a « Here we go » book: he stays there until he steps
     * off (no immediate bounce), then that pad works again.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void playerSentBackOntoAPadIsNotBouncedUntilHeStepsOff(TestContext context) {
        BlockPos lastUsedPad = new BlockPos(1, 1, 1);
        BlockPos previousPad = new BlockPos(5, 1, 5);
        BlockPos previousPadTarget = new BlockPos(1, 1, 5);
        context.setBlockState(previousPadTarget, Blocks.STONE);
        placePad(context, previousPad, hereWeGo(HereWeGoBookItem.State.TP_REGISTERED_POS, context.getAbsolutePos(previousPadTarget)));
        placePad(context, lastUsedPad, hereWeGo(HereWeGoBookItem.State.TP_BACK_LAST_USED_TP_PAD));

        ServerPlayerEntity player = playerAt(context, lastUsedPad, 0.25);
        // He used the previous pad before
        TeleportationPadStorageManager.getTeleportationHistoryStorage(context.getWorld())
                .addTeleportation(player.getUuid(), context.getAbsolutePos(previousPad), context.getAbsolutePos(previousPadTarget));
        stepOn(context, lastUsedPad, player);

        context.waitAndRun(TELEPORT_DELAY + 2, () -> {
            try {
                assertFeetAt(context, player, previousPad, 0.25, "back on the previous pad");
                // Touching the pad he just landed on (every tick) doesn't send him away
                for (int i = 0; i < 3; i++) stepOn(context, previousPad, player);
            } catch (RuntimeException e) {
                disconnect(context, player);
                throw e;
            }
            context.waitAndRun(TELEPORT_DELAY + 2, () -> {
                try {
                    assertFeetAt(context, player, previousPad, 0.25, "still on the previous pad");
                    // Steps off, then back on: the pad teleports him again
                    moveTo(context, player, previousPad.north(2), 0);
                } catch (RuntimeException e) {
                    disconnect(context, player);
                    throw e;
                }
                context.waitAndRun(2, () -> {
                    try {
                        moveTo(context, player, previousPad, 0.25);
                        stepOn(context, previousPad, player);
                    } catch (RuntimeException e) {
                        disconnect(context, player);
                        throw e;
                    }
                    context.waitAndRun(TELEPORT_DELAY + 2, () -> {
                        try {
                            assertFeetAt(context, player, previousPadTarget, 1.0, "sent by the previous pad once he came back on it");
                        } finally {
                            disconnect(context, player);
                        }
                        context.complete();
                    });
                });
            });
        });
    }
}
