package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.gametest.kit.TestBoards;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.minigame.zone.MiniGameZone;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubble;
import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import fr.lordfinn.steveparty.minigame.zone.ZoneForbidden;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a server forbids in a mini-game zone ({@link ZoneForbidden}): a zone holding a forbidden block or entity
 * starts no session, none appears in a zone in session, and the members of a session get no forbidden item. Each
 * kind is forbidden by its tag {@code steveparty:zone_forbidden} (here: the lodestone, the heart of the sea, the
 * allay, added by the tests' own data) and by the lists of the settings (an id, a tag, a whole mod).
 * <p>
 * The zone of every test goes from (1, 1, 1) to (4, 5, 4). Each test has a batch of its own: the settings are the
 * server's.
 */
public class ZoneForbiddenGameTests implements FabricGameTest {
    private long hour;

    // ------------------------------------------------------------------ helpers

    /** A test that fails ends its session and forbids nothing more all the same. */
    @Override
    public void invokeTestMethod(TestContext context, Method method) {
        hour = context.getWorld().getTimeOfDay();
        try {
            FabricGameTest.super.invokeTestMethod(context, method);
        } finally {
            for (ZoneBubble left : ZoneBubbles.all()) left.endNow();
            forbid(List.of(), List.of(), List.of());
            context.getWorld().setTimeOfDay(hour);
        }
    }

    /** What the settings forbid from now on. */
    private static void forbid(List<String> blocks, List<String> items, List<String> entities) {
        ServerConfig config = ServerConfig.get();
        config.miniGameBubbleForbiddenBlocks = new ArrayList<>(blocks);
        config.miniGameBubbleForbiddenItems = new ArrayList<>(items);
        config.miniGameBubbleForbiddenEntities = new ArrayList<>(entities);
        ZoneForbidden.resolve();
    }

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    private static MiniGameZone zone(TestContext context) {
        return MiniGameZone.of(context.getWorld().getRegistryKey(), context.getAbsolutePos(at(1, 1, 1)), context.getAbsolutePos(at(4, 5, 4)));
    }

    private static ZoneBubble begin(TestContext context, ServerPlayerEntity... participants) {
        return ZoneBubbles.begin(context.getWorld().getServer(), UUID.randomUUID(), zone(context), List.of(participants), List.of(), ZoneBubble.Options.DEFAULT);
    }

    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        return TestPlayers.joined(context, "f", name, GameMode.SURVIVAL, x, y, z);
    }

    /** A zone holding {@code block} at {@code pos} starts no session, and says which block and where; without it, it does. */
    private static void expectRefused(TestContext context, Block block, BlockPos pos, String how) {
        MinecraftServer server = context.getWorld().getServer();
        context.setBlockState(pos, block);
        context.assertEquals(ZoneBubbles.check(server, zone(context)), ZoneBubble.Refusal.FORBIDDEN_BLOCK, how + ": seen before the session");
        ZoneForbidden.FoundBlock found = ZoneBubbles.forbiddenBlock(server, zone(context));
        context.assertTrue(found != null && found.block() == block && found.pos().equals(context.getAbsolutePos(pos)), how + ": the block and its place are known");
        ZoneBubble refused = begin(context);
        context.assertTrue(!refused.isActive() && refused.refusal() == ZoneBubble.Refusal.FORBIDDEN_BLOCK, how + ": no session");
        BlockPos abs = context.getAbsolutePos(pos);
        List<Object> said = said(refused);
        context.assertTrue(said.size() == 4 && said.get(1).equals(abs.getX()) && said.get(2).equals(abs.getY()) && said.get(3).equals(abs.getZ()),
                how + ": the refusal says where");
        context.assertTrue(said.get(0) instanceof Text name && name.getString().equals(block.getName().getString()), how + ": and which block");
        context.setBlockState(pos, Blocks.AIR);
        context.assertEquals(ZoneBubbles.check(server, zone(context)), ZoneBubble.Refusal.NONE, how + ": without it the zone is fine");
    }

    /** What the refusal of a bubble says: the name and the place of what is forbidden. */
    private static List<Object> said(ZoneBubble refused) {
        return refused.refusalText() != null && refused.refusalText().getContent() instanceof TranslatableTextContent text ? List.of(text.getArgs()) : List.of();
    }

    // ------------------------------------------------------------------ blocks

    /**
     * A block forbidden by the tag, or by an id, a tag or a whole mod of the settings, in the zone: no session.
     * During a session, none can be put in the zone.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_forbidden_blocks")
    public void forbiddenBlocks(TestContext context) {
        TestBoards.floor(context, 8);
        ServerWorld world = context.getWorld();
        context.assertTrue(Blocks.LODESTONE.getDefaultState().isIn(ZoneForbidden.BLOCKS), "the tests' data put the lodestone in the tag");
        expectRefused(context, Blocks.LODESTONE, at(2, 3, 2), "tag");

        context.setBlockState(at(2, 1, 2), Blocks.SEA_LANTERN);
        context.setBlockState(at(3, 1, 2), Blocks.WHITE_WOOL);
        context.setBlockState(at(5, 1, 2), Blocks.LODESTONE);
        context.assertEquals(ZoneBubbles.check(world.getServer(), zone(context)), ZoneBubble.Refusal.NONE, "what is not forbidden, and what is out of the zone, hold nothing up");
        context.setBlockState(at(2, 1, 2), Blocks.AIR);
        context.setBlockState(at(3, 1, 2), Blocks.AIR);

        forbid(List.of("minecraft:sea_lantern", "#minecraft:wool", "steveparty:*", "nomod:nothing", "othermod:*"), List.of(), List.of());
        expectRefused(context, Blocks.SEA_LANTERN, at(4, 5, 4), "an id of the settings");
        expectRefused(context, Blocks.WHITE_WOOL, at(1, 1, 1), "a tag of the settings");
        expectRefused(context, ModBlocks.GOLD_PODIUM, at(3, 2, 3), "a whole mod of the settings");

        // During a session: nothing forbidden appears in the zone
        ZoneBubble bubble = begin(context);
        context.assertTrue(bubble.isActive(), "a clean zone: the session begins");
        context.setBlockState(at(2, 2, 2), Blocks.LODESTONE);
        context.setBlockState(at(2, 2, 3), Blocks.SEA_LANTERN);
        context.setBlockState(at(2, 2, 4), Blocks.STONE);
        context.setBlockState(at(6, 1, 2), Blocks.SEA_LANTERN);
        context.expectBlock(Blocks.AIR, at(2, 2, 2));
        context.expectBlock(Blocks.AIR, at(2, 2, 3));
        context.expectBlock(Blocks.STONE, at(2, 2, 4));
        context.expectBlock(Blocks.SEA_LANTERN, at(6, 1, 2));
        bubble.end();
        context.setBlockState(at(2, 2, 2), Blocks.LODESTONE);
        context.expectBlock(Blocks.LODESTONE, at(2, 2, 2));
        context.complete();
    }

    /**
     * The look by sections misses no block: wherever a forbidden block stands in a zone that spreads over several
     * chunks and sections (its corners, the edges of its sections), it is found, and one just out of the zone is not.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_forbidden_sections")
    public void noSectionIsMissed(TestContext context) {
        ServerWorld world = context.getWorld();
        BlockPos min = context.getAbsolutePos(at(0, 10, 0)), max = min.add(40, 45, 40);
        MiniGameZone big = MiniGameZone.of(world.getRegistryKey(), min, max);
        context.assertTrue(ZoneForbidden.findBlock(world, big, true) == null, "an empty zone");
        List<BlockPos> inside = new ArrayList<>();
        for (int x : new int[]{min.getX(), max.getX()}) for (int y : new int[]{min.getY(), max.getY()}) for (int z : new int[]{min.getZ(), max.getZ()}) inside.add(new BlockPos(x, y, z));
        // around every edge between two sections the zone covers, along each axis
        for (int x = min.getX(); x <= max.getX(); x++) {
            if ((x & 15) == 0 || (x & 15) == 15) inside.add(new BlockPos(x, min.getY() + 7, min.getZ() + 9));
        }
        for (int y = min.getY(); y <= max.getY(); y++) {
            if ((y & 15) == 0 || (y & 15) == 15) inside.add(new BlockPos(min.getX() + 21, y, min.getZ() + 3));
        }
        for (int z = min.getZ(); z <= max.getZ(); z++) {
            if ((z & 15) == 0 || (z & 15) == 15) inside.add(new BlockPos(max.getX() - 2, max.getY() - 5, z));
        }
        for (BlockPos pos : inside) {
            world.setBlockState(pos, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            ZoneForbidden.FoundBlock found = ZoneForbidden.findBlock(world, big, true);
            context.assertTrue(found != null && found.pos().equals(pos), "found at " + pos + ": " + found);
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        for (BlockPos out : List.of(min.add(-1, 0, 0), min.add(0, -1, 0), min.add(0, 0, -1), max.add(1, 0, 0), max.add(0, 1, 0), max.add(0, 0, 1))) {
            world.setBlockState(out, Blocks.LODESTONE.getDefaultState(), Block.NOTIFY_LISTENERS);
            context.assertTrue(ZoneForbidden.findBlock(world, big, true) == null, "one block out of the zone is not of it: " + out);
            world.setBlockState(out, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }

        // What it costs: the zone filled with a few kinds of blocks, none forbidden (the usual case: every palette is read, no section walked)
        Block[] kinds = {Blocks.STONE, Blocks.DIRT, Blocks.OAK_PLANKS, Blocks.GLASS};
        int placed = 0;
        for (int x = min.getX(); x <= max.getX(); x += 3) for (int y = min.getY(); y <= max.getY(); y += 3) for (int z = min.getZ(); z <= max.getZ(); z += 3) {
            world.setBlockState(new BlockPos(x, y, z), kinds[placed++ % kinds.length].getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        double best = Double.MAX_VALUE;
        for (int round = 0; round < 50; round++) {
            long t = System.nanoTime();
            context.assertTrue(ZoneForbidden.findBlock(world, big, false) == null, "nothing forbidden");
            best = Math.min(best, (System.nanoTime() - t) / 1000.0);
        }
        int sections = (big.maxChunkX() - big.minChunkX() + 1) * (big.maxChunkZ() - big.minChunkZ() + 1) * ((max.getY() >> 4) - (min.getY() >> 4) + 1);
        Steveparty.LOGGER.info("[zone-bubble-bench] forbidden blocks: a clean zone of 41x46x41 over {} sections is checked in {} µs ({} µs a section)",
                sections, String.format("%.1f", best), String.format("%.2f", best / sections));
        for (int x = min.getX(); x <= max.getX(); x += 3) for (int y = min.getY(); y <= max.getY(); y += 3) for (int z = min.getZ(); z <= max.getZ(); z += 3) {
            world.setBlockState(new BlockPos(x, y, z), Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ items

    /** A member of a session gets no forbidden item: not picked up, not used, not taken from a container. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_forbidden_items")
    public void forbiddenItems(TestContext context) {
        TestBoards.floor(context, 8);
        ServerWorld world = context.getWorld();
        ServerPlayerEntity member = player(context, "in", 2.5, 1, 2.5), outsider = player(context, "out", 6.5, 1, 6.5);
        try {
            forbid(List.of(), List.of("minecraft:nether_star"), List.of());
            context.setBlockState(at(3, 1, 3), Blocks.CHEST);
            ChestBlockEntity chest = context.getBlockEntity(at(3, 1, 3));
            chest.setStack(0, new ItemStack(Items.HEART_OF_THE_SEA));
            chest.setStack(1, new ItemStack(Items.NETHER_STAR));
            chest.setStack(2, new ItemStack(Items.DIAMOND));
            ZoneBubble bubble = begin(context, member);
            context.assertTrue(bubble.isActive(), "forbidden items in a chest of the zone hold nothing up");

            // on the ground
            for (ItemStack lying : List.of(new ItemStack(Items.HEART_OF_THE_SEA), new ItemStack(Items.NETHER_STAR), new ItemStack(Items.EMERALD))) {
                ItemEntity item = context.spawnItem(lying.getItem(), new Vec3d(2.5, 1, 3.5));
                item.setPickupDelay(0);
                item.onPlayerCollision(member);
                boolean taken = member.getInventory().count(lying.getItem()) == 1;
                context.assertTrue(taken == lying.isOf(Items.EMERALD) && taken == item.isRemoved(), "only what is not forbidden is picked up: " + lying.getItem());
                item.discard();
            }
            // in a container
            GenericContainerScreenHandler screen = GenericContainerScreenHandler.createGeneric9x3(1, member.getInventory(), chest);
            for (int slot = 0; slot < 3; slot++) screen.onSlotClick(slot, 0, SlotActionType.QUICK_MOVE, member);
            context.assertTrue(chest.getStack(0).isOf(Items.HEART_OF_THE_SEA) && chest.getStack(1).isOf(Items.NETHER_STAR), "the forbidden items stay in the chest");
            context.assertTrue(chest.getStack(2).isEmpty() && member.getInventory().count(Items.DIAMOND) == 1, "the diamond is taken");
            screen.onSlotClick(0, 0, SlotActionType.PICKUP, member);
            screen.onSlotClick(1, 0, SlotActionType.SWAP, member);
            context.assertTrue(screen.getCursorStack().isEmpty() && member.getInventory().count(Items.NETHER_STAR) == 0, "by no kind of click");
            // in the hand (however it got there)
            member.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.HEART_OF_THE_SEA));
            context.assertEquals(member.interactionManager.interactItem(member, world, member.getMainHandStack(), Hand.MAIN_HAND), ActionResult.FAIL, "a forbidden item is not used");
            member.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.STICK));
            context.assertTrue(member.interactionManager.interactItem(member, world, member.getMainHandStack(), Hand.MAIN_HAND) != ActionResult.FAIL, "another one is");

            // who is of no session is not concerned
            GenericContainerScreenHandler other = GenericContainerScreenHandler.createGeneric9x3(2, outsider.getInventory(), chest);
            other.onSlotClick(1, 0, SlotActionType.QUICK_MOVE, outsider);
            context.assertTrue(outsider.getInventory().count(Items.NETHER_STAR) == 1, "the rule is for the members of a session");
            bubble.end();
        } finally {
            TestPlayers.remove(context, member);
            TestPlayers.remove(context, outsider);
        }
        context.complete();
    }

    // ------------------------------------------------------------------ entities

    /** A forbidden entity in the zone: no session, and the refusal names it; during a session none spawns in the zone. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_forbidden_entities")
    public void forbiddenEntities(TestContext context) {
        TestBoards.floor(context, 8);
        ServerWorld world = context.getWorld();
        context.assertTrue(EntityType.ALLAY.isIn(ZoneForbidden.ENTITIES), "the tests' data put the allay in the tag");
        forbid(List.of(), List.of(), List.of("minecraft:bat"));
        for (EntityType<?> type : List.of(EntityType.ALLAY, EntityType.BAT)) {
            Entity there = spawn(context, type, new Vec3d(2.5, 1, 2.5));
            context.assertTrue(there.isAlive() && !there.isRemoved() && world.getEntity(there.getUuid()) == there, "no session: anything spawns");
            ZoneBubble refused = begin(context);
            context.assertTrue(!refused.isActive() && refused.refusal() == ZoneBubble.Refusal.FORBIDDEN_ENTITY, "a forbidden entity in the zone: no session");
            context.assertTrue(said(refused).size() == 4 && said(refused).get(0) instanceof Text name && name.getString().equals(type.getName().getString())
                    && said(refused).get(1).equals(there.getBlockX()), "the refusal names it, and says where");
            there.discard();
        }
        Entity outside = spawn(context, EntityType.ALLAY, new Vec3d(6.5, 1, 6.5));
        Entity pig = spawn(context, EntityType.PIG, new Vec3d(3.5, 1, 3.5));
        ZoneBubble bubble = begin(context);
        context.assertTrue(bubble.isActive(), "a forbidden entity out of the zone, another one in it: the session begins");

        Entity allay = spawn(context, EntityType.ALLAY, new Vec3d(2.5, 1, 2.5)), bat = spawn(context, EntityType.BAT, new Vec3d(2.5, 2, 2.5));
        context.assertTrue(world.getEntity(allay.getUuid()) == null && world.getEntity(bat.getUuid()) == null, "during a session no forbidden entity spawns in the zone");
        Entity cow = spawn(context, EntityType.COW, new Vec3d(2.5, 1, 3.5)), far = spawn(context, EntityType.BAT, new Vec3d(6.5, 2, 2.5));
        context.assertTrue(world.getEntity(cow.getUuid()) == cow && world.getEntity(far.getUuid()) == far, "another entity does, and a forbidden one out of the zone too");
        Vec3d in = context.getAbsolute(new Vec3d(2.5, 1, 2.5));
        outside.setPosition(in);
        context.assertTrue(!zone(context).contains(outside.getBlockPos()), "and none comes in");
        bubble.end();
        outside.discard();
        far.discard();
        pig.discard();
        context.complete();
    }

    /** An entity of a type, made and added to the world at a relative position (whether the world takes it or not). */
    private static Entity spawn(TestContext context, EntityType<?> type, Vec3d relative) {
        Entity entity = type.create(context.getWorld());
        Vec3d abs = context.getAbsolute(relative);
        entity.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
        if (entity instanceof MobEntity mob) mob.setAiDisabled(true);
        context.getWorld().spawnEntity(entity);
        return entity;
    }
}
