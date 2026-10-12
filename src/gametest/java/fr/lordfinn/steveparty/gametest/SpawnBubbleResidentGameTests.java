package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.service.BoardActors;
import fr.lordfinn.steveparty.service.MarkerMobs;
import fr.lordfinn.steveparty.service.MarkerResidents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

import java.util.UUID;

import static fr.lordfinn.steveparty.gametest.DiceTestKit.*;
import static fr.lordfinn.steveparty.gametest.kit.TestCleanup.atEnd;

/**
 * A Spawn Bubble set to « always visible » shows its space's mob all the party long, whatever the order things were
 * done in and however its cartridge came in its space, without linking it again: linked before or after the party
 * starts, reloaded in the middle of a party, its cartridge moved to another space or replaced, its mode changed during
 * the party, a show whose actors vanished. Meanwhile its bubble is told a party runs (no miniature in it).
 */
public class SpawnBubbleResidentGameTests implements SteveGameTest {
    private static final BlockPos TILE = new BlockPos(3, 1, 3);
    private static final BlockPos OTHER = new BlockPos(3, 1, 7);
    private static final BlockPos MARKER = new BlockPos(6, 1, 5);

    private static BlockPos marker(TestContext context) {
        context.setBlockState(MARKER.down(), Blocks.STONE);
        context.setBlockState(MARKER, ModBlocks.SPAWN_MARKER.getDefaultState());
        BlockPos marker = context.getAbsolutePos(MARKER);
        atEnd(context, () -> MarkerResidents.remove(context.getWorld(), marker));
        return marker;
    }

    private static SpawnMarkerBlockEntity bubble(TestContext context, BlockPos marker) {
        return (SpawnMarkerBlockEntity) context.getWorld().getBlockEntity(marker);
    }

    /** As the brush links it: the cartridge of {@code tile} linked to the bubble, the bubble owned by the space. */
    private static void link(TestContext context, BoardSpaceBlockEntity tile, BlockPos marker) {
        CartridgeSpawnMarker.set(tile.getStack(0), context.getWorld(), marker);
        CartridgeSpawnMarker.own(context.getWorld(), marker, tile.getPos());
    }

    /** A cartridge {@code kind} already linked to the bubble at {@code marker} (moved, copied, from a chest...). */
    private static ItemStack linked(TestContext context, Item kind, BlockPos marker) {
        ItemStack stack = new ItemStack(kind);
        stack.set(ModComponents.SPAWN_MARKER, GlobalPos.create(context.getWorld().getRegistryKey(), marker));
        return stack;
    }

    private static PartyControllerEntity party(TestContext context) {
        ServerPlayerEntity a = player(context), b = player(context);
        return DiceTestKit.party(context, a.getUuid(), token(context, new BlockPos(1, 1, 1), a.getUuid()),
                token(context, new BlockPos(3, 1, 1), b.getUuid()));
    }

    /** The bubble looked at as every second: its links and party flag, its resident. */
    private static void tick(TestContext context, BlockPos marker) {
        MarkerMobs.refresh(context.getWorld(), marker);
        MarkerResidents.update(context.getWorld().getServer());
    }

    /** Its {@code type} mob lives on the bubble, and the bubble knows a party runs (empty: no miniature). */
    private static MobEntity lives(TestContext context, BlockPos marker, Class<? extends MobEntity> type, String what) {
        MobEntity resident = MarkerResidents.resident(context.getWorld(), marker);
        context.assertTrue(type.isInstance(resident), what + ": its mob lives on the bubble (" + resident + ")");
        SpawnMarkerBlockEntity bubble = bubble(context, marker);
        context.assertTrue(bubble.isPartyRunning() && bubble.isResident() && !bubble.getLinkedMobs().isEmpty(),
                what + ": the bubble knows a party runs (no miniature)");
        return resident;
    }

    private static EntityType<?> mob(Item cartridge) {
        return CartridgeSpawnMarker.mobOf(new ItemStack(cartridge));
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_resident_link_first")
    public void linkedBeforeThePartyStarts(TestContext context) {
        BoardSpaceBlockEntity shop = tile(context, TILE, new ItemStack(ModItems.SHOP_CARTRIDGE));
        BlockPos marker = marker(context);
        link(context, shop, marker);
        bubble(context, marker).setResident(true);
        party(context);
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "linked, then the party");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_resident_party_first")
    public void linkedOnceThePartyRuns(TestContext context) {
        BoardSpaceBlockEntity shop = tile(context, TILE, new ItemStack(ModItems.SHOP_CARTRIDGE));
        BlockPos marker = marker(context);
        bubble(context, marker).setResident(true);
        party(context);
        tick(context, marker);
        context.assertTrue(MarkerResidents.resident(context.getWorld(), marker) == null, "nothing linked: nobody");
        link(context, shop, marker);
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "the party, then linked");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_resident_reload")
    public void reloadedInTheMiddleOfAParty(TestContext context) {
        BoardSpaceBlockEntity shop = tile(context, TILE, new ItemStack(ModItems.SHOP_CARTRIDGE));
        BlockPos marker = marker(context);
        link(context, shop, marker);
        SpawnMarkerBlockEntity bubble = bubble(context, marker);
        bubble.setResident(true);
        party(context);
        tick(context, marker);
        MobEntity before = lives(context, marker, BoxedTraderEntity.class, "before the reload");
        // saved and loaded again (its chunk, or the server: nothing of it kept in memory, its mob never saved)
        NbtCompound saved = bubble.createNbt(context.getWorld().getRegistryManager());
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(bubble, context.getWorld());
        context.assertTrue(before.isRemoved(), "unloaded: its mob gone");
        bubble.read(saved, context.getWorld().getRegistryManager());
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(bubble, context.getWorld());
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "reloaded");
        // saved by a version that knew its spaces but not its owner: still found
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.invoker().onUnload(bubble, context.getWorld());
        saved.remove("Owner");
        bubble.read(saved, context.getWorld().getRegistryManager());
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.invoker().onLoad(bubble, context.getWorld());
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "reloaded without its owner");
        context.complete();
    }

    /** The bug seen in a party: the bubble's owner (its last brushed space) is not the space holding its cartridge. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_resident_moved")
    public void itsCartridgeMovedToAnotherSpace(TestContext context) {
        BoardSpaceBlockEntity first = tile(context, TILE, new ItemStack(ModItems.SHOP_CARTRIDGE));
        BoardSpaceBlockEntity second = tile(context, OTHER, plain());
        BlockPos marker = marker(context);
        link(context, first, marker);
        bubble(context, marker).setResident(true);
        party(context);
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "on its first space");
        // the linked cartridge taken to another space (its menu, a hopper...): no brush
        ItemStack moved = first.getStack(0).copy();
        first.setStack(0, plain());
        second.setStack(0, moved);
        context.assertEquals(bubble(context, marker).getOwner(), first.getPos(), "(its owner is still the first space)");
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "moved to another space");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_resident_replaced")
    public void itsCartridgeReplaced(TestContext context) {
        BlockPos marker = marker(context);
        // a space holding a cartridge linked before (never brushed: the bubble has no owner)
        BoardSpaceBlockEntity space = tile(context, TILE, linked(context, ModItems.SHOP_CARTRIDGE, marker));
        bubble(context, marker).setResident(true);
        party(context);
        tick(context, marker);
        context.assertTrue(bubble(context, marker).getOwner() == null, "(no owner)");
        lives(context, marker, BoxedTraderEntity.class, "a linked cartridge put in");
        // replaced by another linked cartridge of another kind: its mob
        space.setStack(0, linked(context, ModItems.FROUSSEUX_CARTRIDGE, marker));
        tick(context, marker);
        lives(context, marker, FrousseuxEntity.class, "replaced by a Frousseux Cartridge");
        context.assertEquals(bubble(context, marker).getLinkedMobs(), java.util.List.of(mob(ModItems.FROUSSEUX_CARTRIDGE)), "its bubble knows it");
        // and back to a shop
        space.setStack(0, linked(context, ModItems.SHOP_CARTRIDGE, marker));
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "a shop again");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "bubble_resident_toggled")
    public void setAlwaysVisibleDuringTheParty(TestContext context) {
        BoardSpaceBlockEntity shop = tile(context, TILE, new ItemStack(ModItems.SHOP_CARTRIDGE));
        BlockPos marker = marker(context);
        link(context, shop, marker);
        party(context);
        tick(context, marker);
        context.assertTrue(MarkerResidents.resident(context.getWorld(), marker) == null, "only on landing: nobody");
        bubble(context, marker).setResident(true);
        MarkerResidents.refresh(context.getWorld(), marker);
        tick(context, marker);
        MobEntity resident = lives(context, marker, BoxedTraderEntity.class, "always visible now");
        bubble(context, marker).setResident(false);
        MarkerResidents.refresh(context.getWorld(), marker);
        context.assertTrue(resident.isRemoved() && MarkerResidents.resident(context.getWorld(), marker) == null, "on landing again: gone");
        bubble(context, marker).setResident(true);
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "always visible again");
        context.complete();
    }

    /** A show whose actors vanished without it ending (a crash, a command) does not keep the mob away for good. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 3 * MarkerResidents.CHECK_INTERVAL + 20, batchId = "bubble_resident_stale_show")
    public void aShowWithNoActorLeftGivesItsMarkerBack(TestContext context) {
        BoardSpaceBlockEntity shop = tile(context, TILE, new ItemStack(ModItems.SHOP_CARTRIDGE));
        BlockPos marker = marker(context);
        link(context, shop, marker);
        bubble(context, marker).setResident(true);
        party(context);
        tick(context, marker);
        lives(context, marker, BoxedTraderEntity.class, "before the show");
        UUID show = UUID.randomUUID();
        MobEntity actor = BoardActors.join(show, (MobEntity) EntityType.PIG.create(context.getWorld()));
        actor.refreshPositionAndAngles(context.getAbsolutePos(TILE).up(), 0, 0);
        context.getWorld().spawnEntity(actor);
        atEnd(context, () -> BoardActors.end(show));
        MarkerResidents.showStarts(context.getWorld(), marker, show);
        tick(context, marker);
        context.assertTrue(MarkerResidents.resident(context.getWorld(), marker) == null, "out for the show");
        actor.discard(); // gone, its show never ended
        tick(context, marker);
        context.assertTrue(MarkerResidents.resident(context.getWorld(), marker) == null, "a moment: its show may bring another actor");
        context.waitAndRun(2 * MarkerResidents.CHECK_INTERVAL + 1, () -> {
            tick(context, marker);
            lives(context, marker, BoxedTraderEntity.class, "the empty show over");
            context.complete();
        });
    }
}
