package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.parachute.ParachuteItem;
import fr.lordfinn.steveparty.items.custom.parachute.ParachuteState;
import fr.lordfinn.steveparty.items.custom.parachute.Parachutes;
import net.minecraft.block.Blocks;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.GameMode;

import java.util.List;

/**
 * The Parachute: held, it opens after a fall and folds on landing (worn by one an opening), a use in the air opens or
 * folds it (folded by hand, it stays so until landing), an elytra wins; on a mob, packed until it falls, open it
 * brakes the fall to a slow descent, the mob lands unhurt and the parachute drops worn by one; a removed mob drops it;
 * never on a boss nor on a pawn (one found there drops); dyed stripes; repaired with wool or string.
 */
public class ParachuteGameTests implements SteveGameTest {
    /** A survival test player in the air at {@code at} (relative), holding a parachute. */
    private static ServerPlayerEntity falling(TestContext context, BlockPos at) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        BlockPos abs = context.getAbsolutePos(at);
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PARACHUTE));
        player.setOnGround(false);
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void heldItOpensAfterAFallAndFoldsOnLanding(TestContext context) {
        ServerPlayerEntity player = falling(context, new BlockPos(2, 6, 2));
        player.fallDistance = Parachutes.AUTO_OPEN_FALL - 1;
        Parachutes.tick(player);
        context.assertFalse(Parachutes.isOpen(player), "a short drop: still folded");
        player.fallDistance = Parachutes.AUTO_OPEN_FALL;
        Parachutes.tick(player);
        context.assertTrue(Parachutes.isOpen(player), "a real fall: open");
        context.assertTrue(player.getMainHandStack().getDamage() == 1, "an opening wears it by one");
        player.fallDistance = 12;
        Parachutes.tick(player);
        context.assertTrue(player.fallDistance == 0, "open: no fall builds up");
        player.setOnGround(true);
        Parachutes.tick(player);
        context.assertFalse(Parachutes.isOpen(player), "landed: folded");
        TestPlayers.remove(context, player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aUseInTheAirOpensOrFoldsItFoldedItWaitsForTheGround(TestContext context) {
        ServerPlayerEntity player = falling(context, new BlockPos(2, 6, 2));
        context.assertTrue(Parachutes.toggle(player, Hand.MAIN_HAND), "a use in the air");
        context.assertTrue(Parachutes.isOpen(player), "open at once");
        context.assertTrue(Parachutes.toggle(player, Hand.MAIN_HAND), "used again");
        context.assertFalse(Parachutes.isOpen(player), "folded");
        player.fallDistance = 10;
        Parachutes.tick(player);
        context.assertFalse(Parachutes.isOpen(player), "folded by hand: never opens by itself again in this fall");
        player.setOnGround(true);
        Parachutes.tick(player);
        context.assertFalse(Parachutes.toggle(player, Hand.MAIN_HAND), "on the ground: a use does nothing");
        player.setOnGround(false);
        player.fallDistance = 10;
        Parachutes.tick(player);
        context.assertTrue(Parachutes.isOpen(player), "the next fall: it opens by itself again");
        TestPlayers.remove(context, player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itFoldsWhenLetGoOrWhenAnElytraGlides(TestContext context) {
        ServerPlayerEntity player = falling(context, new BlockPos(2, 6, 2));
        Parachutes.toggle(player, Hand.MAIN_HAND);
        player.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(ModItems.PARACHUTE));
        Parachutes.tick(player);
        context.assertTrue(Parachutes.isOpen(player), "held in the other hand: still open");
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        Parachutes.tick(player);
        context.assertFalse(Parachutes.isOpen(player), "let go: folded");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.PARACHUTE));
        Parachutes.toggle(player, Hand.MAIN_HAND);
        player.startFallFlying();
        Parachutes.tick(player);
        context.assertFalse(Parachutes.isOpen(player), "gliding with an elytra: the elytra wins");
        TestPlayers.remove(context, player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wornOutItNoLongerOpens(TestContext context) {
        ServerPlayerEntity player = falling(context, new BlockPos(2, 6, 2));
        player.getMainHandStack().setDamage(Parachutes.MAX_DAMAGE - 1);
        context.assertFalse(Parachutes.toggle(player, Hand.MAIN_HAND), "at its last use: it doesn't open");
        context.assertFalse(Parachutes.isOpen(player), "folded");
        TestPlayers.remove(context, player);
        context.complete();
    }

    /** A cow 12 blocks up, a parachute on its back: open after a short drop, slow, it lands unhurt and the parachute drops. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "parachute_mob", tickLimit = 240)
    public void onAMobItOpensWhenItFallsAndDropsOnceItLands(TestContext context) {
        for (int y = 1; y <= 14; y++) {
            for (int x = 1; x <= 5; x++) for (int z = 1; z <= 5; z++) context.setBlockState(new BlockPos(x, y, z), Blocks.AIR);
        }
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        ItemStack held = new ItemStack(ModItems.PARACHUTE);
        player.setStackInHand(Hand.MAIN_HAND, held);
        CowEntity cow = context.spawnEntity(EntityType.COW, new BlockPos(3, 1, 3));
        context.assertTrue(Parachutes.attach(player, cow, held), "put on the cow");
        context.assertTrue(player.getMainHandStack().isEmpty(), "taken from the hand");
        ParachuteState packed = Parachutes.get(cow);
        context.assertTrue(packed != null && !packed.open(), "packed on its back");
        context.assertFalse(Parachutes.attach(player, cow, new ItemStack(ModItems.PARACHUTE)), "one at a time");
        float health = cow.getHealth();
        BlockPos high = context.getAbsolutePos(new BlockPos(3, 13, 3));
        cow.refreshPositionAndAngles(high.getX() + 0.5, high.getY(), high.getZ() + 0.5, 0, 0);
        context.waitAndRun(15, () -> {
            context.assertTrue(Parachutes.isOpen(cow), "falling: open");
            context.waitAndRun(10, () -> context.assertTrue(cow.getVelocity().y >= -Parachutes.DESCENT - 0.04,
                    "a slow descent: " + cow.getVelocity().y));
        });
        context.waitAndRun(200, () -> {
            context.assertTrue(cow.isOnGround(), "landed");
            context.assertTrue(Parachutes.get(cow) == null, "no parachute left on it");
            context.assertTrue(cow.getHealth() == health, "unhurt: " + cow.getHealth());
            List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class, cow.getBoundingBox().expand(8),
                    item -> item.getStack().isOf(ModItems.PARACHUTE));
            context.assertTrue(drops.size() == 1 && drops.getFirst().getStack().getDamage() == 1, "the parachute drops, worn by one");
            drops.forEach(Entity::discard);
            cow.discard();
            TestPlayers.remove(context, player);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "parachute_removed")
    public void aRemovedMobDropsItNeverOnABoss(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, new BlockPos(2, 1, 2));
        zombie.setAiDisabled(true);
        context.assertTrue(Parachutes.attach(player, zombie, new ItemStack(ModItems.PARACHUTE)), "put on the zombie");
        Box around = zombie.getBoundingBox().expand(3);
        zombie.discard(); // a board space's mob at the end of its sequence, say
        List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class, around, item -> item.getStack().isOf(ModItems.PARACHUTE));
        context.assertTrue(drops.size() == 1 && drops.getFirst().getStack().getDamage() == 0, "removed: it drops, as it was");
        drops.forEach(Entity::discard);
        WitherEntity wither = context.spawnEntity(EntityType.WITHER, new BlockPos(2, 1, 2));
        wither.setAiDisabled(true);
        context.assertFalse(Parachutes.canCarry(wither), "never on a boss");
        context.assertFalse(Parachutes.canCarry(player), "a player holds his own");
        wither.discard();
        TestPlayers.remove(context, player);
        context.complete();
    }

    /** A pawn never takes one (the use does nothing); one found on a pawn (an old save) drops as it was. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "parachute_pawn")
    public void neverOnAPawnOneFoundThereDrops(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context, GameMode.SURVIVAL);
        ItemStack held = new ItemStack(ModItems.PARACHUTE);
        player.setStackInHand(Hand.MAIN_HAND, held);
        PigEntity pawn = context.spawnEntity(EntityType.PIG, new BlockPos(2, 1, 2));
        ((TokenizedEntityInterface) pawn).steveparty$setTokenized(true);
        context.assertTrue(Parachutes.isPawn(pawn) && !Parachutes.canCarry(pawn), "a pawn: never");
        context.assertTrue(held.useOnEntity(player, pawn, Hand.MAIN_HAND) == ActionResult.FAIL, "the use does nothing");
        context.assertTrue(Parachutes.get(pawn) == null && held.getCount() == 1, "not put on it, still in hand");
        pawn.setAttached(Parachutes.TYPE, new ParachuteState(new ItemStack(ModItems.PARACHUTE), 0, false));
        Parachutes.tick(pawn);
        context.assertTrue(Parachutes.get(pawn) == null, "one found on a pawn: off");
        List<ItemEntity> drops = context.getWorld().getEntitiesByClass(ItemEntity.class, pawn.getBoundingBox().expand(3),
                item -> item.getStack().isOf(ModItems.PARACHUTE));
        context.assertTrue(drops.size() == 1 && drops.getFirst().getStack().getDamage() == 0, "dropped as it was");
        drops.forEach(Entity::discard);
        pawn.discard();
        TestPlayers.remove(context, player);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dyedStripesRepairedWithWoolOrString(TestContext context) {
        ItemStack parachute = new ItemStack(ModItems.PARACHUTE);
        context.assertTrue(ParachuteItem.color(parachute) == ParachuteItem.DEFAULT_COLOR, "undyed: sky blue");
        parachute.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(0xB02E26, true));
        context.assertTrue((ParachuteItem.color(parachute) & 0xFFFFFF) == 0xB02E26, "dyed red");
        context.assertTrue(parachute.isIn(ItemTags.DYEABLE), "dyeable like leather");
        context.assertTrue(parachute.getItem().canRepair(parachute, new ItemStack(Items.RED_WOOL)), "repaired with wool");
        context.assertTrue(parachute.getItem().canRepair(parachute, new ItemStack(Items.STRING)), "or string");
        context.assertFalse(parachute.getItem().canRepair(parachute, new ItemStack(Items.LEATHER)), "not leather");
        context.complete();
    }
}
