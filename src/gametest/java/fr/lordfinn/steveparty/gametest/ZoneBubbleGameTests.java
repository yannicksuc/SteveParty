package fr.lordfinn.steveparty.gametest;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.minigame.zone.MiniGameZone;
import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubble;
import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.Portal;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.CrafterBlockEntity;
import net.minecraft.block.enums.Orientation;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.ChestType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.PigEntity;
import net.minecraft.entity.vehicle.ChestMinecartEntity;
import net.minecraft.entity.vehicle.HopperMinecartEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The mini-game bubble: a zone in session is given back as it was (blocks, containers, entities), its players
 * hold a session inventory and get theirs back whatever the way the session ends (end, leaving, crash, server
 * stop), and nothing crosses its border, either way.
 * <p>
 * Every test plays in a zone of 4x5x4 blocks, from (1,1,1) to (4,5,4), on a stone floor: x = 5 and beyond is
 * the world outside. The tests that touch the whole server (crash, stop, settings) each have a batch of their own.
 */
public class ZoneBubbleGameTests implements FabricGameTest {
    private static final String BATCH = "zone_bubble";
    private static final String STASH_TAG = "steveparty.zone_bubble";
    private static final AtomicInteger SERIAL = new AtomicInteger();
    /** The time of day each running test began at. */
    private static final Map<TestContext, Long> STARTED_AT = new IdentityHashMap<>();

    // ------------------------------------------------------------------ helpers

    /** A test that fails ends its session all the same: a zone left in session would spoil the tests that follow. */
    @Override
    public void invokeTestMethod(TestContext context, Method method) {
        STARTED_AT.put(context, context.getWorld().getTimeOfDay());
        try {
            FabricGameTest.super.invokeTestMethod(context, method);
        } catch (RuntimeException | Error e) {
            endLeftSession(context);
            throw e;
        }
    }

    /**
     * A test is over. The hour of the world is put back where it was when the test began: tests of other suites
     * count on the daylight of the hour they have always run at, which these tests would push back otherwise.
     */
    private static void done(TestContext context) {
        Long startedAt = STARTED_AT.remove(context);
        if (startedAt != null) context.getWorld().setTimeOfDay(startedAt);
        context.complete();
    }

    private static void endLeftSession(TestContext context) {
        ZoneBubble left = ZoneBubbles.of(context.getWorld(), context.getAbsolutePos(at(1, 1, 1)));
        if (left != null) left.endNow();
    }

    private static BlockPos at(int x, int y, int z) {
        return new BlockPos(x, y, z);
    }

    private static MiniGameZone zone(TestContext context) {
        return MiniGameZone.of(context.getWorld().getRegistryKey(), context.getAbsolutePos(at(1, 1, 1)), context.getAbsolutePos(at(4, 5, 4)));
    }

    /** The stone floor the zone and its surroundings stand on (under the zone: not part of it). */
    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(at(x, 0, z), Blocks.STONE);
    }

    private static ZoneBubble begin(TestContext context, ServerPlayerEntity... participants) {
        // a test that ran out of time left its session going where the next one plays
        endLeftSession(context);
        return ZoneBubbles.begin(context.getWorld().getServer(), UUID.randomUUID(), zone(context), List.of(participants), List.of(), ZoneBubble.Options.DEFAULT);
    }

    /** A connected survival player with a name of its own, standing at a relative position. */
    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        ServerPlayerEntity player = connect(context, new GameProfile(UUID.randomUUID(), "z" + SERIAL.incrementAndGet() + name));
        player.changeGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        move(context, player, x, y, z);
        return player;
    }

    /** A player logs in: as it was saved if it came before, new otherwise. */
    private static ServerPlayerEntity connect(TestContext context, GameProfile profile) {
        ServerWorld world = context.getWorld();
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        return player;
    }

    private static void move(TestContext context, ServerPlayerEntity player, double x, double y, double z) {
        Vec3d abs = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
    }

    private static void remove(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) TestPlayers.remove(context, player);
    }

    /** Runs a step of a test some ticks after its start; what makes it fail is also written to the log. */
    private static void later(TestContext context, long ticks, Runnable step) {
        context.waitAndRun(ticks, () -> {
            try {
                step.run();
            } catch (RuntimeException e) {
                Steveparty.LOGGER.error("[zone-bubble-test] a step failed at tick {}: {}", ticks, e.getMessage());
                endLeftSession(context);
                throw e;
            }
        });
    }

    private static boolean inZone(TestContext context, Entity entity) {
        return zone(context).contains(entity.getBlockPos());
    }

    private static ChestBlockEntity chest(TestContext context, BlockPos pos, ItemStack... stacks) {
        context.setBlockState(pos, Blocks.CHEST);
        ChestBlockEntity chest = context.getBlockEntity(pos);
        for (int i = 0; i < stacks.length; i++) chest.setStack(i, stacks[i]);
        return chest;
    }

    private static List<ItemEntity> itemsIn(TestContext context, MiniGameZone zone) {
        return context.getWorld().getEntitiesByClass(ItemEntity.class, zone.bounds().expand(2), Entity::isAlive);
    }

    private static int count(ServerPlayerEntity player, net.minecraft.item.Item item) {
        return player.getInventory().count(item);
    }

    // ------------------------------------------------------------------ the zone is given back

    /** Blocks placed, replaced and broken in the zone are back as they were, containers with what they held; nothing is dropped. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void blocksAreRestored(TestContext context) {
        floor(context);
        context.setBlockState(at(2, 1, 2), Blocks.STONE);
        chest(context, at(3, 1, 2), new ItemStack(Items.DIAMOND, 5));
        ZoneBubble bubble = begin(context);
        context.assertTrue(bubble.isActive(), "the session begins");
        ServerWorld world = context.getWorld();

        context.setBlockState(at(2, 1, 2), Blocks.GOLD_BLOCK);
        world.breakBlock(context.getAbsolutePos(at(3, 1, 2)), true);
        context.assertTrue(!itemsIn(context, zone(context)).isEmpty(), "the broken chest dropped what it held");
        chest(context, at(2, 2, 2), new ItemStack(Items.EMERALD, 3));
        context.setBlockState(at(3, 3, 3), Blocks.OAK_PLANKS);
        context.setBlockState(at(6, 1, 2), Blocks.GOLD_BLOCK);
        context.assertTrue(bubble.journalSize() == 4, "one entry per changed position of the zone, got " + bubble.journalSize());

        bubble.end();
        context.assertTrue(bubble.state() == ZoneBubble.State.ENDED, "a small zone is whole again when end() returns");
        context.expectBlock(Blocks.STONE, at(2, 1, 2));
        context.expectBlock(Blocks.CHEST, at(3, 1, 2));
        ChestBlockEntity chest = context.getBlockEntity(at(3, 1, 2));
        context.assertTrue(chest.getStack(0).isOf(Items.DIAMOND) && chest.getStack(0).getCount() == 5, "the chest holds its diamonds again");
        context.expectBlock(Blocks.AIR, at(2, 2, 2));
        context.expectBlock(Blocks.AIR, at(3, 3, 3));
        context.expectBlock(Blocks.GOLD_BLOCK, at(6, 1, 2));
        context.assertTrue(itemsIn(context, zone(context)).isEmpty(), "neither the session's drops nor the session's chest left an item");
        context.assertTrue(ZoneBubbles.of(world, context.getAbsolutePos(at(2, 1, 2))) == null, "the bubble is gone");
        done(context);
    }

    /** A container emptied, a sign rewritten, a hopper filled, without any block changing: all as they were. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void blockEntitiesAreRestored(TestContext context) {
        floor(context);
        ChestBlockEntity chest = chest(context, at(2, 1, 2), new ItemStack(Items.DIAMOND, 5), new ItemStack(Items.GOLD_INGOT, 9));
        context.setBlockState(at(3, 1, 2), Blocks.OAK_SIGN);
        SignBlockEntity sign = context.getBlockEntity(at(3, 1, 2));
        sign.setText(new SignText().withMessage(0, Text.literal("arena")), true);
        context.setBlockState(at(2, 1, 3), Blocks.HOPPER.getDefaultState().with(HopperBlock.ENABLED, false));
        ZoneBubble bubble = begin(context);

        chest.clear();
        chest.setStack(3, new ItemStack(Items.DIRT));
        sign.setText(new SignText().withMessage(0, Text.literal("mine")), true);
        HopperBlockEntity hopper = context.getBlockEntity(at(2, 1, 3));
        hopper.setStack(0, new ItemStack(Items.EMERALD, 64));
        context.assertTrue(bubble.journalSize() == 0, "no block changed");

        bubble.end();
        chest = context.getBlockEntity(at(2, 1, 2));
        context.assertTrue(chest.getStack(0).isOf(Items.DIAMOND) && chest.getStack(0).getCount() == 5 && chest.getStack(1).getCount() == 9, "the chest holds what it held");
        context.assertTrue(chest.getStack(3).isEmpty(), "what the session put in the chest is gone");
        sign = context.getBlockEntity(at(3, 1, 2));
        context.assertTrue(sign.getFrontText().getMessage(0, false).getString().equals("arena"), "the sign says what it said");
        hopper = context.getBlockEntity(at(2, 1, 3));
        context.assertTrue(hopper.isEmpty(), "the hopper is empty again");
        done(context);
    }

    /** The entities of the zone come back as they were, with their UUID; those of the session are gone, dropping nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void entitiesAreRestored(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        PigEntity pig = context.spawnMob(EntityType.PIG, at(2, 1, 2));
        pig.setAiDisabled(true);
        ArmorStandEntity stand = context.spawnEntity(EntityType.ARMOR_STAND, at(3, 1, 3));
        stand.setCustomName(Text.literal("statue"));
        stand.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
        ChestMinecartEntity cart = context.spawnEntity(EntityType.CHEST_MINECART, at(2, 1, 4));
        cart.setStack(0, new ItemStack(Items.DIAMOND, 7));
        ItemEntity item = context.spawnItem(Items.EMERALD, new Vec3d(4.5, 1, 4.5));
        item.setVelocity(Vec3d.ZERO);
        UUID pigId = pig.getUuid(), standId = stand.getUuid(), cartId = cart.getUuid(), itemId = item.getUuid();
        ZoneBubble bubble = begin(context);

        pig.discard();
        stand.equipStack(EquipmentSlot.HEAD, ItemStack.EMPTY);
        stand.setCustomName(Text.literal("other"));
        cart.setStack(0, new ItemStack(Items.DIRT));
        cart.setStack(1, new ItemStack(Items.GOLD_INGOT, 64));
        item.discard();
        Entity cow = context.spawnMob(EntityType.COW, at(3, 1, 2));
        ChestMinecartEntity loaded = context.spawnEntity(EntityType.CHEST_MINECART, at(4, 1, 2));
        loaded.setStack(0, new ItemStack(Items.NETHERITE_INGOT, 64));

        bubble.end();
        context.assertTrue(cow.isRemoved() && loaded.isRemoved(), "the entities of the session are gone");
        context.assertTrue(world.getEntity(pigId) instanceof PigEntity back && back.isAlive() && inZone(context, back), "the pig is back");
        context.assertTrue(world.getEntity(standId) instanceof ArmorStandEntity back && back.getCustomName().getString().equals("statue")
                && back.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.DIAMOND_HELMET), "the armour stand is as it was");
        context.assertTrue(world.getEntity(cartId) instanceof ChestMinecartEntity back && back.getStack(0).isOf(Items.DIAMOND)
                && back.getStack(0).getCount() == 7 && back.getStack(1).isEmpty(), "the chest minecart holds what it held");
        context.assertTrue(world.getEntity(itemId) instanceof ItemEntity back && back.getStack().isOf(Items.EMERALD), "the item lies there again");
        List<ItemEntity> items = itemsIn(context, zone(context));
        context.assertTrue(items.size() == 1, "nothing else lies in the zone (the session's minecart dropped nothing), got " + items.size());
        done(context);
    }

    /** A double chest across the border is two chests while the session lasts, and one again after it. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void doubleChestAcrossTheBorderIsSplit(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        BlockState left = Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, Direction.NORTH).with(ChestBlock.CHEST_TYPE, ChestType.LEFT);
        context.setBlockState(at(4, 1, 2), left);
        context.setBlockState(at(5, 1, 2), left.with(ChestBlock.CHEST_TYPE, ChestType.RIGHT));
        BlockPos in = context.getAbsolutePos(at(4, 1, 2)), out = context.getAbsolutePos(at(5, 1, 2));
        context.<ChestBlockEntity>getBlockEntity(at(4, 1, 2)).setStack(0, new ItemStack(Items.DIAMOND, 5));
        context.<ChestBlockEntity>getBlockEntity(at(5, 1, 2)).setStack(0, new ItemStack(Items.EMERALD, 5));
        context.assertTrue(ChestBlock.getInventory((ChestBlock) Blocks.CHEST, world.getBlockState(in), world, in, true).size() == 54, "one chest of 54 slots");
        ZoneBubble bubble = begin(context);

        context.assertTrue(ChestBlock.getInventory((ChestBlock) Blocks.CHEST, world.getBlockState(in), world, in, true).size() == 27, "the half of the zone is a chest of its own");
        context.assertTrue(ChestBlock.getInventory((ChestBlock) Blocks.CHEST, world.getBlockState(out), world, out, true).size() == 27, "and so is the half outside");
        context.<ChestBlockEntity>getBlockEntity(at(4, 1, 2)).clear();

        bubble.end();
        context.assertTrue(world.getBlockState(in).get(ChestBlock.CHEST_TYPE) == ChestType.LEFT, "the chest is double again");
        context.assertTrue(ChestBlock.getInventory((ChestBlock) Blocks.CHEST, world.getBlockState(in), world, in, true).size() == 54, "one chest of 54 slots again");
        context.assertTrue(context.<ChestBlockEntity>getBlockEntity(at(4, 1, 2)).getStack(0).getCount() == 5, "its half of the zone holds what it held");
        context.assertTrue(context.<ChestBlockEntity>getBlockEntity(at(5, 1, 2)).getStack(0).isOf(Items.EMERALD), "the half outside was left alone");
        done(context);
    }

    // ------------------------------------------------------------------ the players

    /** A participant leaves all it owns at the door and gets it back at the end; what it got in the session is destroyed. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void inventoryIsSwappedAndGivenBack(TestContext context) {
        floor(context);
        ServerPlayerEntity player = player(context, "inv", 2.5, 1, 2.5);
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
        player.getInventory().setStack(20, new ItemStack(Items.COBBLESTONE, 33));
        player.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.SHIELD));
        player.getEnderChestInventory().setStack(4, new ItemStack(Items.NETHER_STAR));
        player.getInventory().selectedSlot = 3;
        player.experienceLevel = 7;
        player.experienceProgress = 0.5f;
        player.currentScreenHandler.setCursorStack(new ItemStack(Items.GOLDEN_APPLE, 2));
        ZoneBubble bubble = ZoneBubbles.begin(context.getWorld().getServer(), UUID.randomUUID(), zone(context), List.of(player), List.of(), new ZoneBubble.Options(true));

        context.assertTrue(bubble.isParticipant(player.getUuid()) && ZoneBubbles.ofPlayer(player) == bubble, "the player is of the session");
        context.assertTrue(player.getInventory().isEmpty(), "the session inventory starts empty");
        context.assertTrue(player.getEnderChestInventory().isEmpty(), "the session ender chest starts empty");
        context.assertTrue(player.experienceLevel == 0 && player.experienceProgress == 0, "the session experience starts at 0");
        context.assertTrue(player.interactionManager.getGameMode() == GameMode.ADVENTURE, "participants play in adventure mode");
        context.assertTrue(player.getCommandTags().contains(STASH_TAG), "the player is marked as holding a session inventory");

        player.getInventory().setStack(0, new ItemStack(Items.NETHERITE_BLOCK, 64));
        player.getEnderChestInventory().setStack(0, new ItemStack(Items.NETHERITE_BLOCK, 64));
        player.experienceLevel = 30;
        player.currentScreenHandler.setCursorStack(new ItemStack(Items.NETHERITE_BLOCK, 64));

        bubble.end();
        context.assertTrue(player.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD), "the sword is back");
        context.assertTrue(count(player, Items.COBBLESTONE) == 33, "the cobblestone is back");
        context.assertTrue(player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.IRON_HELMET), "the helmet is back");
        context.assertTrue(player.getOffHandStack().isOf(Items.SHIELD), "the shield is back");
        context.assertTrue(count(player, Items.GOLDEN_APPLE) == 2, "what the cursor held was put in the inventory");
        context.assertTrue(player.getEnderChestInventory().getStack(4).isOf(Items.NETHER_STAR) && player.getEnderChestInventory().getStack(0).isEmpty(), "the ender chest is back");
        context.assertTrue(player.getInventory().selectedSlot == 3, "the selected slot is back");
        context.assertTrue(player.experienceLevel == 7 && player.experienceProgress == 0.5f, "the experience is back");
        context.assertTrue(player.interactionManager.getGameMode() == GameMode.SURVIVAL, "the game mode is back");
        context.assertTrue(count(player, Items.NETHERITE_BLOCK) == 0 && player.currentScreenHandler.getCursorStack().isEmpty(), "nothing of the session is left");
        context.assertTrue(!player.getCommandTags().contains(STASH_TAG) && ZoneBubbles.ofPlayer(player) == null, "the player is of no session any more");
        context.assertTrue(itemsIn(context, zone(context)).isEmpty(), "nothing was dropped");
        remove(context, player);
        done(context);
    }

    /** A player leaving the session gets its inventory back at once; one joining it late leaves its own at the door. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void playersComeAndGo(TestContext context) {
        floor(context);
        ServerPlayerEntity first = player(context, "first", 2.5, 1, 2.5);
        ServerPlayerEntity late = player(context, "late", 3.5, 1, 3.5);
        ServerPlayerEntity watcher = player(context, "watch", 6.5, 1, 6.5);
        first.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 3));
        late.getInventory().setStack(0, new ItemStack(Items.EMERALD, 4));
        watcher.getInventory().setStack(0, new ItemStack(Items.GOLD_INGOT, 5));
        ZoneBubble bubble = ZoneBubbles.begin(context.getWorld().getServer(), UUID.randomUUID(), zone(context), List.of(first), List.of(watcher), ZoneBubble.Options.DEFAULT);
        context.assertTrue(first.getInventory().isEmpty() && watcher.getInventory().isEmpty(), "participant and spectator hold session inventories");
        context.assertTrue(count(late, Items.EMERALD) == 4, "who is not of the session keeps its inventory");
        context.assertTrue(bubble.isSpectator(watcher.getUuid()), "the watcher is a spectator");

        bubble.addParticipant(late);
        context.assertTrue(late.getInventory().isEmpty() && bubble.isParticipant(late.getUuid()), "the late player joined");
        first.getInventory().setStack(5, new ItemStack(Items.NETHERITE_BLOCK));
        bubble.removePlayer(first);
        context.assertTrue(count(first, Items.DIAMOND) == 3 && count(first, Items.NETHERITE_BLOCK) == 0, "who leaves gets its inventory back, without the session's items");
        context.assertTrue(!bubble.isMember(first.getUuid()) && bubble.isActive(), "the session goes on without it");

        bubble.end();
        context.assertTrue(count(late, Items.EMERALD) == 4 && count(watcher, Items.GOLD_INGOT) == 5, "the others get theirs at the end");
        remove(context, first, late, watcher);
        done(context);
    }

    /**
     * A player gone from the server during its session without a word (it was saved holding its session inventory)
     * gets what it owns back when it comes again, even if the session ended meanwhile.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void playerComingBackGetsItsInventory(TestContext context) {
        floor(context);
        MinecraftServer server = context.getWorld().getServer();
        ServerPlayerEntity player = player(context, "back", 2.5, 1, 2.5);
        GameProfile profile = player.getGameProfile();
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 3));
        player.experienceLevel = 9;
        ZoneBubble bubble = begin(context, player);
        player.getInventory().setStack(0, new ItemStack(Items.NETHERITE_BLOCK, 64));
        // saved as it is, and gone
        server.getPlayerManager().remove(player);
        bubble.end();

        ServerPlayerEntity again = connect(context, profile);
        context.assertTrue(count(again, Items.DIAMOND) == 3 && again.experienceLevel == 9, "it holds what it owns again");
        context.assertTrue(count(again, Items.NETHERITE_BLOCK) == 0 && !again.getCommandTags().contains(STASH_TAG), "and nothing of the session");
        context.assertTrue(ZoneBubbles.ofPlayer(again) == null, "it is of no session");
        remove(context, again);
        done(context);
    }

    /** A participant dying loses nothing it owns: what it drops stays in the zone, and it is put back in the zone. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
    public void deathLeaksNothing(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(context, "dead", 2.5, 1, 2.5);
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND, 3));
        ZoneBubble bubble = begin(context, player);
        ServerPlayerEntity[] respawned = new ServerPlayerEntity[1];
        later(context, 3, () -> {
            player.getInventory().setStack(0, new ItemStack(Items.NETHERITE_BLOCK, 5));
            player.kill();
            context.assertTrue(player.isDead(), "the participant died");
            List<ItemEntity> drops = itemsIn(context, zone(context));
            context.assertTrue(!drops.isEmpty() && drops.stream().allMatch(item -> inZone(context, item) && item.getStack().isOf(Items.NETHERITE_BLOCK)), "it dropped its session items, in the zone");
            respawned[0] = world.getServer().getPlayerManager().respawnPlayer(player, false, Entity.RemovalReason.KILLED);
            // as the connection does when its player asks to respawn
            respawned[0].networkHandler.player = respawned[0];
            context.assertTrue(ZoneBubbles.ofPlayer(respawned[0]) == bubble && respawned[0].getInventory().isEmpty(), "it is still of the session");
        });
        later(context, 8, () -> {
            context.assertTrue(inZone(context, respawned[0]), "it is put back in the zone, wherever it respawned");
            bubble.end();
            context.assertTrue(count(respawned[0], Items.DIAMOND) == 3 && count(respawned[0], Items.NETHERITE_BLOCK) == 0, "it gets what it owns back");
            context.assertTrue(itemsIn(context, zone(context)).isEmpty(), "what it dropped went with the session");
            remove(context, respawned[0]);
            done(context);
        });
    }

    /** A participant can't walk or be teleported out; anyone else can't come in; the mod's own teleports pass. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
    public void playersStayOnTheirSide(TestContext context) {
        floor(context);
        context.setBlockState(at(6, 1, 2), Blocks.DIRT);
        context.setBlockState(at(3, 1, 3), Blocks.DIRT);
        ServerWorld world = context.getWorld();
        ServerPlayerEntity inside = player(context, "in", 2.5, 1, 2.5);
        ServerPlayerEntity outsider = player(context, "out", 3.5, 1, 3.5);
        ZoneBubble bubble = begin(context, inside);
        Vec3d out = context.getAbsolute(new Vec3d(6.5, 1, 6.5));

        later(context, 2, () -> {
            context.assertTrue(!inZone(context, outsider), "who is not of the session and stood in the zone is put just out of it");
            context.assertTrue(inZone(context, inside), "the participant stays");
            // an ender pearl, a command...: not out of the zone
            boolean teleported = inside.teleport(world, out.x, out.y, out.z, Set.of(), 0, 0);
            context.assertTrue(!teleported && inZone(context, inside), "a participant is not teleported out");
            Vec3d in = context.getAbsolute(new Vec3d(2.5, 1, 2.5));
            context.assertTrue(!outsider.teleport(world, in.x, in.y, in.z, Set.of(), 0, 0) && !inZone(context, outsider), "nobody else is teleported in");
            // walking (or anything else moving it): sent back
            move(context, inside, 6.5, 1, 2.5);
            move(context, outsider, 2.5, 1, 2.5);
        });
        later(context, 4, () -> {
            context.assertTrue(inZone(context, inside), "a participant out of the zone is put back in");
            context.assertTrue(!inZone(context, outsider), "an intruder is put back out");
            // the mod's own teleport: out with its leave, hands tied
            ZoneBubbles.allowTeleports(() -> inside.teleport(world, out.x, out.y, out.z, Set.of(), 0, 0));
            context.assertTrue(!inZone(context, inside), "the mod teleports a participant out");
        });
        later(context, 7, () -> {
            context.assertTrue(!inZone(context, inside), "it is not sent back");
            inside.getInventory().setStack(0, new ItemStack(Items.NETHERITE_BLOCK, 8));
            context.assertTrue(inside.dropItem(new ItemStack(Items.NETHERITE_BLOCK), false, true) == null, "out of its zone, what it drops is destroyed");
            context.assertTrue(!inside.interactionManager.tryBreakBlock(context.getAbsolutePos(at(6, 1, 2))), "out of its zone, it breaks nothing");
            context.assertTrue(!inside.interactionManager.tryBreakBlock(context.getAbsolutePos(at(3, 1, 3))), "not even in the zone");
            move(context, inside, 2.5, 1, 2.5);
        });
        later(context, 9, () -> {
            context.assertTrue(inside.interactionManager.tryBreakBlock(context.getAbsolutePos(at(3, 1, 3))), "back in, it plays again");
            bubble.end();
            context.expectBlock(Blocks.DIRT, at(3, 1, 3));
            remove(context, inside, outsider);
            done(context);
        });
    }

    /** What a player may touch: a participant only its zone, anyone else only the rest of the world, a spectator nothing. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void playersOnlyTouchTheirSide(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        BlockPos in = at(4, 1, 2), in2 = at(4, 1, 3), outPos = at(5, 1, 2), out2 = at(6, 1, 3);
        for (BlockPos pos : List.of(in, in2, outPos, out2)) context.setBlockState(pos, Blocks.DIRT);
        ServerPlayerEntity participant = player(context, "part", 2.5, 1, 2.5);
        ServerPlayerEntity outsider = player(context, "outs", 6.5, 1, 2.5);
        ServerPlayerEntity spectator = player(context, "spec", 6.5, 1, 6.5);
        ZoneBubble bubble = ZoneBubbles.begin(world.getServer(), UUID.randomUUID(), zone(context), List.of(participant), List.of(spectator), ZoneBubble.Options.DEFAULT);

        context.assertTrue(!participant.interactionManager.tryBreakBlock(context.getAbsolutePos(outPos)), "a participant breaks nothing out of its zone");
        context.assertTrue(!outsider.interactionManager.tryBreakBlock(context.getAbsolutePos(in)), "nobody else breaks a block of the zone");
        context.assertTrue(!spectator.interactionManager.tryBreakBlock(context.getAbsolutePos(in)) && !spectator.interactionManager.tryBreakBlock(context.getAbsolutePos(out2)), "a spectator breaks nothing");
        context.expectBlock(Blocks.DIRT, in);
        context.expectBlock(Blocks.DIRT, outPos);
        context.assertTrue(participant.interactionManager.tryBreakBlock(context.getAbsolutePos(in)), "a participant breaks blocks of its zone");
        context.assertTrue(outsider.interactionManager.tryBreakBlock(context.getAbsolutePos(out2)), "the others break blocks of the world");
        context.assertTrue(ZoneBorder.isIdle(), "an action refused half-way leaves no origin behind");

        // a block of the session placed against the border from inside: it would stand outside
        participant.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GOLD_BLOCK));
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(context.getAbsolutePos(in2)), Direction.EAST, context.getAbsolutePos(in2), false);
        participant.interactionManager.interactBlock(participant, world, participant.getMainHandStack(), Hand.MAIN_HAND, hit);
        context.expectBlock(Blocks.AIR, at(5, 1, 3));
        context.assertTrue(ZoneBorder.isIdle(), "a use refused leaves no origin behind");
        // and whatever the item, a player's action changes no block and spawns nothing on the other side
        ZoneBorder.enterPlayer(participant);
        boolean placed = world.setBlockState(context.getAbsolutePos(at(5, 1, 3)), Blocks.GOLD_BLOCK.getDefaultState());
        ItemEntity thrown = new ItemEntity(world, context.getAbsolute(new Vec3d(5.5, 1, 3.5)).x, context.getAbsolute(new Vec3d(5.5, 1, 3.5)).y, context.getAbsolute(new Vec3d(5.5, 1, 3.5)).z, new ItemStack(Items.GOLD_BLOCK));
        boolean spawned = world.spawnEntity(thrown);
        ZoneBorder.exit(world);
        context.assertTrue(!placed && !spawned, "no block and no entity across the border");
        ZoneBorder.enterPlayer(outsider);
        placed = world.setBlockState(context.getAbsolutePos(at(3, 2, 3)), Blocks.GOLD_BLOCK.getDefaultState());
        ZoneBorder.exit(world);
        context.assertTrue(!placed, "nor into the zone");

        // items lying by the border are only taken from their side
        ItemEntity lying = context.spawnItem(Items.DIAMOND, new Vec3d(4.8, 1, 4.5));
        lying.setVelocity(Vec3d.ZERO);
        lying.setPickupDelay(0);
        lying.onPlayerCollision(outsider);
        lying.onPlayerCollision(spectator);
        context.assertTrue(lying.isAlive() && count(outsider, Items.DIAMOND) == 0, "an item of the zone is not picked up from outside, nor by a spectator");
        lying.onPlayerCollision(participant);
        context.assertTrue(lying.isRemoved() && count(participant, Items.DIAMOND) == 1, "a participant picks it up");
        context.assertTrue(spectator.dropItem(new ItemStack(Items.DIAMOND), false, true) == null, "a spectator drops nothing");

        bubble.end();
        context.expectBlock(Blocks.DIRT, in);
        remove(context, participant, outsider, spectator);
        done(context);
    }

    // ------------------------------------------------------------------ the border

    /** Entities never cross the border, whatever moves them: thrown, teleported, or simply set elsewhere. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 80)
    public void entitiesStayOnTheirSide(TestContext context) {
        floor(context);
        PigEntity pig = context.spawnMob(EntityType.PIG, at(2, 1, 2));
        pig.setAiDisabled(true);
        PigEntity stranger = context.spawnMob(EntityType.PIG, at(6, 1, 6));
        stranger.setAiDisabled(true);
        ZoneBubble bubble = begin(context);
        ItemEntity thrownOut = context.spawnItem(Items.DIAMOND, new Vec3d(3.5, 1.5, 2.5));
        thrownOut.setVelocity(0.6, 0.2, 0);
        ItemEntity thrownIn = context.spawnItem(Items.EMERALD, new Vec3d(6.5, 1.5, 3.5));
        thrownIn.setVelocity(-0.6, 0.2, 0);
        Vec3d out = context.getAbsolute(new Vec3d(6.5, 1, 2.5)), in = context.getAbsolute(new Vec3d(2.5, 1, 3.5));
        pig.requestTeleport(out.x, out.y, out.z);
        context.assertTrue(inZone(context, pig), "an entity of the zone is not teleported out");
        pig.setPosition(out.x, out.y + 20, out.z);
        context.assertTrue(inZone(context, pig), "nor moved out any other way");
        stranger.setPosition(in);
        context.assertTrue(!inZone(context, stranger), "an entity from outside does not come in");

        later(context, 40, () -> {
            context.assertTrue(thrownOut.isAlive() && inZone(context, thrownOut), "an item thrown at the border stays in the zone");
            context.assertTrue(thrownIn.isAlive() && !inZone(context, thrownIn), "an item thrown at the zone stays out");
            bubble.end();
            context.assertTrue(thrownOut.isRemoved() && thrownIn.isAlive(), "the item of the session goes with it, the other stays");
            // no session: the border is gone
            stranger.setPosition(in);
            context.assertTrue(inZone(context, stranger), "once the session is over, anyone comes in");
            done(context);
        });
    }

    /** Hoppers neither pull from across the border nor push through it, and suck up no item lying on the other side. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100)
    public void hoppersMoveNothingAcross(TestContext context) {
        floor(context);
        // a chest of the zone over a hopper under the zone
        chest(context, at(2, 1, 2), new ItemStack(Items.DIAMOND, 5));
        context.setBlockState(at(2, 0, 2), Blocks.HOPPER);
        // a hopper of the zone pushing into a chest outside
        context.setBlockState(at(4, 1, 3), Blocks.HOPPER.getDefaultState().with(HopperBlock.FACING, Direction.EAST));
        context.<HopperBlockEntity>getBlockEntity(at(4, 1, 3)).setStack(0, new ItemStack(Items.EMERALD, 5));
        chest(context, at(5, 1, 3));
        // an item of the zone lying on a hopper under the zone
        context.setBlockState(at(3, 0, 4), Blocks.HOPPER);
        ZoneBubble bubble = begin(context);
        ItemEntity lying = context.spawnItem(Items.GOLD_INGOT, new Vec3d(3.5, 1, 4.5));
        lying.setVelocity(Vec3d.ZERO);

        later(context, 30, () -> {
            context.assertTrue(context.<ChestBlockEntity>getBlockEntity(at(2, 1, 2)).getStack(0).getCount() == 5, "nothing is pulled out of the zone");
            context.assertTrue(context.<HopperBlockEntity>getBlockEntity(at(2, 0, 2)).isEmpty(), "the hopper under the zone got nothing");
            context.assertTrue(context.<ChestBlockEntity>getBlockEntity(at(5, 1, 3)).isEmpty(), "nothing is pushed out of the zone");
            context.assertTrue(lying.isAlive() && context.<HopperBlockEntity>getBlockEntity(at(3, 0, 4)).isEmpty(), "the item of the zone is not sucked up");
            bubble.end();
        });
        // the same hoppers, once the session is over: they work (the guard is what held them)
        later(context, 60, () -> {
            context.assertTrue(context.<ChestBlockEntity>getBlockEntity(at(2, 1, 2)).getStack(0).getCount() < 5, "after the session the hopper pulls");
            context.assertTrue(!context.<ChestBlockEntity>getBlockEntity(at(5, 1, 3)).isEmpty(), "after the session the hopper pushes");
            done(context);
        });
    }

    /** A dropper or a dispenser facing the border keeps what it holds, from either side. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
    public void dispensersKeepTheirItems(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        context.setBlockState(at(4, 1, 2), Blocks.DROPPER.getDefaultState().with(DispenserBlock.FACING, Direction.EAST));
        context.<DispenserBlockEntity>getBlockEntity(at(4, 1, 2)).setStack(0, new ItemStack(Items.DIAMOND));
        context.setBlockState(at(5, 1, 3), Blocks.DISPENSER.getDefaultState().with(DispenserBlock.FACING, Direction.WEST));
        context.<DispenserBlockEntity>getBlockEntity(at(5, 1, 3)).setStack(0, new ItemStack(Items.WATER_BUCKET));
        // a dropper of the zone facing into it: works
        context.setBlockState(at(1, 1, 4), Blocks.DROPPER.getDefaultState().with(DispenserBlock.FACING, Direction.EAST));
        context.<DispenserBlockEntity>getBlockEntity(at(1, 1, 4)).setStack(0, new ItemStack(Items.EMERALD));
        ZoneBubble bubble = begin(context);
        world.scheduleBlockTick(context.getAbsolutePos(at(4, 1, 2)), Blocks.DROPPER, 1);
        world.scheduleBlockTick(context.getAbsolutePos(at(5, 1, 3)), Blocks.DISPENSER, 1);
        world.scheduleBlockTick(context.getAbsolutePos(at(1, 1, 4)), Blocks.DROPPER, 1);

        later(context, 10, () -> {
            context.assertTrue(context.<DispenserBlockEntity>getBlockEntity(at(4, 1, 2)).getStack(0).isOf(Items.DIAMOND), "the dropper of the zone keeps its diamond");
            context.assertTrue(context.<DispenserBlockEntity>getBlockEntity(at(5, 1, 3)).getStack(0).isOf(Items.WATER_BUCKET), "the dispenser outside keeps its water");
            context.expectBlock(Blocks.AIR, at(4, 1, 3));
            context.assertTrue(context.<DispenserBlockEntity>getBlockEntity(at(1, 1, 4)).isEmpty(), "a dropper facing into the zone works");
            List<ItemEntity> items = itemsIn(context, zone(context));
            context.assertTrue(items.size() == 1 && items.get(0).getStack().isOf(Items.EMERALD) && inZone(context, items.get(0)), "only the emerald was dropped, in the zone");
            bubble.end();
            done(context);
        });
    }

    /** A piston pushes nothing across the border; one that stays on its side works. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
    public void pistonsMoveNothingAcross(TestContext context) {
        floor(context);
        BlockState east = Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST);
        // would push the stone out of the zone
        context.setBlockState(at(3, 1, 2), east);
        context.setBlockState(at(4, 1, 2), Blocks.STONE);
        // stays in the zone
        context.setBlockState(at(1, 1, 4), east);
        context.setBlockState(at(2, 1, 4), Blocks.STONE);
        // would push the stone into the zone
        context.setBlockState(at(6, 3, 2), Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.WEST));
        context.setBlockState(at(5, 3, 2), Blocks.STONE);
        ZoneBubble bubble = begin(context);
        context.setBlockState(at(3, 2, 2), Blocks.REDSTONE_BLOCK);
        context.setBlockState(at(1, 2, 4), Blocks.REDSTONE_BLOCK);
        context.setBlockState(at(6, 4, 2), Blocks.REDSTONE_BLOCK);

        later(context, 10, () -> {
            context.expectBlock(Blocks.STONE, at(4, 1, 2));
            context.expectBlock(Blocks.AIR, at(5, 1, 2));
            context.expectBlock(Blocks.STONE, at(5, 3, 2));
            context.expectBlock(Blocks.AIR, at(4, 3, 2));
            context.expectBlock(Blocks.STONE, at(3, 1, 4));
            bubble.end();
            context.expectBlock(Blocks.STONE, at(2, 1, 4));
            context.expectBlock(Blocks.AIR, at(3, 1, 4));
            context.assertTrue(!context.getBlockState(at(1, 1, 4)).get(PistonBlock.EXTENDED), "the piston of the zone is back as it was");
            done(context);
        });
    }

    /** Water spreads on its side of the border only, from the zone and from outside. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100)
    public void fluidsStayOnTheirSide(TestContext context) {
        floor(context);
        // the water outside stays in a basin of two blocks, open on the zone only
        for (BlockPos wall : List.of(at(7, 1, 4), at(5, 1, 3), at(6, 1, 3), at(5, 1, 5), at(6, 1, 5))) context.setBlockState(wall, Blocks.STONE);
        // and a wall across the zone keeps its own water away from the place the one outside would flow to
        for (int x = 1; x <= 4; x++) context.setBlockState(at(x, 1, 3), Blocks.STONE);
        ZoneBubble bubble = begin(context);
        context.setBlockState(at(4, 1, 2), Blocks.WATER);
        context.setBlockState(at(5, 1, 4), Blocks.WATER);

        later(context, 40, () -> {
            context.assertTrue(!context.getBlockState(at(3, 1, 2)).getFluidState().isEmpty(), "the water of the zone spreads in the zone");
            context.assertTrue(context.getBlockState(at(5, 1, 2)).getFluidState().isEmpty(), "not out of it");
            context.assertTrue(!context.getBlockState(at(6, 1, 4)).getFluidState().isEmpty(), "the water outside spreads outside");
            context.assertTrue(context.getBlockState(at(4, 1, 4)).getFluidState().isEmpty(), "not into the zone");
            bubble.end();
            for (int x = 1; x <= 4; x++) {
                for (int z = 1; z <= 4; z++) context.assertTrue(context.getBlockState(at(x, 1, z)).getFluidState().isEmpty(), "no water is left in the zone");
            }
            // the border is gone: the water outside goes too, before it runs over the test
            context.setBlockState(at(5, 1, 4), Blocks.AIR);
            context.setBlockState(at(6, 1, 4), Blocks.AIR);
        });
        later(context, 60, () -> {
            context.assertTrue(context.getBlockState(at(2, 1, 2)).isAir(), "no water comes back after the session");
            done(context);
        });
    }

    /** What spreads on random ticks (here grass) stays on its side; so does anything started by a tick. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
    public void growthStaysOnItsSide(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        context.setBlockState(at(4, 1, 2), Blocks.GRASS_BLOCK);
        context.setBlockState(at(3, 1, 2), Blocks.DIRT);
        context.setBlockState(at(5, 1, 2), Blocks.DIRT);
        // light for the grass to spread, whatever the hour
        context.setBlockState(at(5, 3, 2), Blocks.GLOWSTONE);
        context.setBlockState(at(3, 3, 2), Blocks.GLOWSTONE);
        ZoneBubble bubble = begin(context);
        BlockPos grass = context.getAbsolutePos(at(4, 1, 2));
        // once the light of the glowstone is there
        later(context, 10, () -> {
            for (int i = 0; i < 600; i++) world.getBlockState(grass).randomTick(world, grass, world.random);
            context.expectBlock(Blocks.GRASS_BLOCK, at(3, 1, 2));
            context.expectBlock(Blocks.DIRT, at(5, 1, 2));

            // the tick of a block, of an entity: nothing across
            ZoneBorder.enter(world, grass);
            boolean across = world.setBlockState(context.getAbsolutePos(at(6, 1, 6)), Blocks.FIRE.getDefaultState());
            boolean within = world.setBlockState(context.getAbsolutePos(at(2, 3, 2)), Blocks.STONE.getDefaultState());
            ZoneBorder.exit(world);
            context.assertTrue(!across && within, "what starts in the zone changes blocks of the zone only");
            // unless the mod does it itself
            ZoneBorder.enter(world, grass);
            ZoneBubbles.unguarded(() -> context.setBlockState(at(6, 1, 6), Blocks.GOLD_BLOCK));
            ZoneBorder.exit(world);
            context.expectBlock(Blocks.GOLD_BLOCK, at(6, 1, 6));

            bubble.end();
            context.expectBlock(Blocks.DIRT, at(3, 1, 2));
            context.expectBlock(Blocks.AIR, at(2, 3, 2));
            done(context);
        });
    }

    /** Explosions only destroy blocks on their side of the border. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_boom")
    public void explosionsStayOnTheirSide(TestContext context) {
        // a floor no blast goes through, and blasts too weak to reach around it: the ground of the test world is
        // the one the other tests stand on
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(at(x, 0, z), Blocks.OBSIDIAN);
        ServerWorld world = context.getWorld();
        for (int x = 2; x <= 7; x++) context.setBlockState(at(x, 1, 2), Blocks.DIRT);
        for (int x = 2; x <= 7; x++) context.setBlockState(at(x, 1, 4), Blocks.DIRT);
        ZoneBubble bubble = begin(context);

        Vec3d inside = context.getAbsolute(new Vec3d(4.5, 2.2, 2.5));
        world.createExplosion(null, inside.x, inside.y, inside.z, 2f, World.ExplosionSourceType.TNT);
        context.expectBlock(Blocks.AIR, at(4, 1, 2));
        context.expectBlock(Blocks.DIRT, at(5, 1, 2));
        context.expectBlock(Blocks.DIRT, at(6, 1, 2));
        bubble.end();
        context.expectBlock(Blocks.DIRT, at(4, 1, 2));
        context.expectBlock(Blocks.DIRT, at(4, 1, 4));
        context.assertTrue(itemsIn(context, zone(context)).stream().noneMatch(item -> inZone(context, item)), "the drops of the zone are gone");

        // another session in the same zone: an explosion from outside
        bubble = begin(context);
        context.assertTrue(bubble.isActive(), "a zone given back can be played again");
        Vec3d outside = context.getAbsolute(new Vec3d(5.5, 2.2, 4.5));
        world.createExplosion(null, outside.x, outside.y, outside.z, 2f, World.ExplosionSourceType.TNT);
        context.expectBlock(Blocks.AIR, at(5, 1, 4));
        context.expectBlock(Blocks.DIRT, at(4, 1, 4));
        context.expectBlock(Blocks.DIRT, at(3, 1, 4));

        bubble.end();
        context.expectBlock(Blocks.DIRT, at(4, 1, 4));
        done(context);
    }

    /** The portals of the zone take nobody anywhere; the egg of the dragon never jumps over the border. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_egg")
    public void portalsAndDragonEgg(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(context, "egg", 6.5, 1, 6.5);
        ArmorStandEntity inside = context.spawnEntity(EntityType.ARMOR_STAND, at(2, 1, 2));
        ArmorStandEntity outside = context.spawnEntity(EntityType.ARMOR_STAND, at(6, 1, 2));
        context.setBlockState(at(2, 2, 3), Blocks.DRAGON_EGG);
        context.setBlockState(at(6, 3, 4), Blocks.DRAGON_EGG);
        ZoneBubble bubble = begin(context);

        inside.tryUsePortal((Portal) Blocks.NETHER_PORTAL, context.getAbsolutePos(at(2, 1, 2)));
        outside.tryUsePortal((Portal) Blocks.NETHER_PORTAL, context.getAbsolutePos(at(6, 1, 2)));
        context.assertTrue(inside.portalManager == null, "a portal of the zone takes nobody");
        context.assertTrue(outside.portalManager != null, "a portal outside works");
        outside.portalManager = null;

        MiniGameZone zone = zone(context);
        // the egg outside always jumps from the same place, next to the zone and well above the bottom of the world
        // (an egg that picks a place under it is lost: the game's own doing)
        BlockPos eggIn = context.getAbsolutePos(at(2, 2, 3)), start = context.getAbsolutePos(at(6, 3, 4));
        for (int i = 0; i < 40; i++) {
            world.getBlockState(eggIn).onBlockBreakStart(world, eggIn, player);
            eggIn = findEgg(world, zone, eggIn, true);
            context.assertTrue(eggIn != null, "the egg of the zone stays in the zone");
            world.getBlockState(start).onBlockBreakStart(world, start, player);
            BlockPos eggOut = findEgg(world, zone, start, false);
            context.assertTrue(eggOut != null && findEgg(world, zone, eggIn, true) != null, "the egg from outside stays out (try " + i + ", out: " + eggOut + ")");
            world.setBlockState(eggOut, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS);
            world.setBlockState(start, Blocks.DRAGON_EGG.getDefaultState(), Block.NOTIFY_LISTENERS);
        }
        world.setBlockState(start, Blocks.AIR.getDefaultState());

        bubble.end();
        context.expectBlock(Blocks.DRAGON_EGG, at(2, 2, 3));
        remove(context, player);
        done(context);
    }

    /**
     * The dragon egg on one side of the border (in the zone or out of it), within the reach of an egg that stood at
     * {@code around}; null unless there is exactly one.
     */
    private static BlockPos findEgg(ServerWorld world, MiniGameZone zone, BlockPos around, boolean inZone) {
        BlockPos found = null;
        for (BlockPos pos : BlockPos.iterate(around.add(-16, -8, -16), around.add(16, 8, 16))) {
            if (!world.getBlockState(pos).isOf(Blocks.DRAGON_EGG) || zone.contains(pos) != inZone) continue;
            if (found != null) return null;
            found = pos.toImmutable();
        }
        return found;
    }

    /** A hopper minecart by the border sucks up no item lying on the other side; once the session is over, it does. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 100)
    public void hopperMinecartsTakeNothingAcross(TestContext context) {
        floor(context);
        HopperMinecartEntity cart = context.spawnEntity(EntityType.HOPPER_MINECART, new Vec3d(5.5, 1, 2.5));
        // an item of the zone, within the reach of the minecart standing just outside
        ItemEntity lying = context.spawnItem(Items.GOLD_INGOT, new Vec3d(4.8, 1, 2.5));
        lying.setVelocity(Vec3d.ZERO);
        ZoneBubble bubble = begin(context);
        later(context, 30, () -> {
            context.assertTrue(lying.isAlive() && cart.isEmpty(), "the item of the zone is not sucked up from outside");
            // the item was there before the session: it is put back, and the border is gone
            bubble.end();
        });
        later(context, 70, () -> {
            context.assertTrue(!cart.isEmpty(), "after the session the minecart takes it (the guard is what held it)");
            cart.discard();
            done(context);
        });
    }

    /** A crafter facing the border crafts nothing across it; one facing into its own side works. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 60)
    public void craftersCraftNothingAcross(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        context.setBlockState(at(4, 1, 3), Blocks.CRAFTER.getDefaultState().with(Properties.ORIENTATION, Orientation.EAST_UP));
        context.<CrafterBlockEntity>getBlockEntity(at(4, 1, 3)).setStack(0, new ItemStack(Items.OAK_LOG));
        context.setBlockState(at(5, 1, 1), Blocks.CRAFTER.getDefaultState().with(Properties.ORIENTATION, Orientation.WEST_UP));
        context.<CrafterBlockEntity>getBlockEntity(at(5, 1, 1)).setStack(0, new ItemStack(Items.OAK_LOG));
        context.setBlockState(at(3, 1, 4), Blocks.CRAFTER.getDefaultState().with(Properties.ORIENTATION, Orientation.WEST_UP));
        context.<CrafterBlockEntity>getBlockEntity(at(3, 1, 4)).setStack(0, new ItemStack(Items.OAK_LOG));
        ZoneBubble bubble = begin(context);
        for (BlockPos crafter : List.of(at(4, 1, 3), at(5, 1, 1), at(3, 1, 4))) world.scheduleBlockTick(context.getAbsolutePos(crafter), Blocks.CRAFTER, 1);
        later(context, 10, () -> {
            context.assertTrue(context.<CrafterBlockEntity>getBlockEntity(at(4, 1, 3)).getStack(0).isOf(Items.OAK_LOG), "the crafter of the zone facing out keeps its log");
            context.assertTrue(context.<CrafterBlockEntity>getBlockEntity(at(5, 1, 1)).getStack(0).isOf(Items.OAK_LOG), "the crafter outside facing in keeps its log");
            context.assertTrue(context.<CrafterBlockEntity>getBlockEntity(at(3, 1, 4)).isEmpty(), "a crafter facing into the zone works");
            List<ItemEntity> items = itemsIn(context, zone(context));
            context.assertTrue(items.size() == 1 && items.get(0).getStack().isOf(Items.OAK_PLANKS) && inZone(context, items.get(0)), "only its planks came out, in the zone");
            bubble.end();
            done(context);
        });
    }

    /** Two stacks of the same item on either side of the border don't merge. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 80)
    public void itemsDontMergeAcross(TestContext context) {
        floor(context);
        ZoneBubble bubble = begin(context);
        ItemEntity in = context.spawnItem(Items.DIAMOND, new Vec3d(4.85, 1, 2.5));
        ItemEntity out = context.spawnItem(Items.DIAMOND, new Vec3d(5.15, 1, 2.5));
        in.setVelocity(Vec3d.ZERO);
        out.setVelocity(Vec3d.ZERO);
        later(context, 50, () -> {
            context.assertTrue(in.isAlive() && out.isAlive() && in.getStack().getCount() == 1 && out.getStack().getCount() == 1, "the two stacks stay apart");
            bubble.end();
            context.assertTrue(in.isRemoved() && out.isAlive(), "the one of the zone goes, the other stays");
            done(context);
        });
    }

    // ------------------------------------------------------------------ caps, switch, crash, stop

    /** A zone too big or over another one starts no session; a full journal lets no new block change. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_caps")
    public void capsAreHeld(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ServerConfig config = ServerConfig.get();
        int journal = config.miniGameBubbleMaxJournal, blockEntities = config.miniGameBubbleMaxBlockEntities;
        try {
            BlockPos corner = context.getAbsolutePos(at(1, 1, 1));
            ZoneBubble big = ZoneBubbles.begin(server, UUID.randomUUID(), MiniGameZone.of(world.getRegistryKey(), corner, corner.add(128, 3, 3)), List.of(), List.of(), ZoneBubble.Options.DEFAULT);
            context.assertTrue(!big.isActive() && big.refusal() == ZoneBubble.Refusal.TOO_BIG, "129 blocks long: too big");

            chest(context, at(2, 3, 2));
            chest(context, at(2, 3, 4));
            config.miniGameBubbleMaxBlockEntities = 1;
            ZoneBubble full = begin(context);
            context.assertTrue(full.refusal() == ZoneBubble.Refusal.TOO_MANY_BLOCK_ENTITIES, "more block entities than a zone may hold");
            config.miniGameBubbleMaxBlockEntities = blockEntities;

            config.miniGameBubbleMaxJournal = 3;
            ZoneBubble bubble = begin(context);
            context.assertTrue(bubble.isActive(), "the session begins");
            ZoneBubble over = ZoneBubbles.begin(server, UUID.randomUUID(), MiniGameZone.of(world.getRegistryKey(), corner.add(3, 0, 0), corner.add(9, 3, 3)), List.of(), List.of(), ZoneBubble.Options.DEFAULT);
            context.assertTrue(over.refusal() == ZoneBubble.Refusal.OVERLAP, "a zone over a zone in session starts nothing");
            over.end();

            for (int x = 1; x <= 3; x++) context.setBlockState(at(x, 1, 1), Blocks.STONE);
            context.assertTrue(!bubble.isJournalFull(), "three positions fit");
            context.setBlockState(at(4, 1, 1), Blocks.STONE);
            context.expectBlock(Blocks.AIR, at(4, 1, 1));
            context.assertTrue(bubble.isJournalFull() && bubble.journalSize() == 3, "the fourth position can't change");
            context.setBlockState(at(1, 1, 1), Blocks.GOLD_BLOCK);
            context.expectBlock(Blocks.GOLD_BLOCK, at(1, 1, 1));
            context.setBlockState(at(6, 1, 1), Blocks.STONE);
            context.expectBlock(Blocks.STONE, at(6, 1, 1));

            bubble.end();
            for (int x = 1; x <= 4; x++) context.expectBlock(Blocks.AIR, at(x, 1, 1));
        } finally {
            config.miniGameBubbleMaxJournal = journal;
            config.miniGameBubbleMaxBlockEntities = blockEntities;
        }
        done(context);
    }

    /** A big journal is restored over several ticks; nobody stays in the zone meanwhile. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_slow", tickLimit = 100)
    public void bigRestorationsTakeSeveralTicks(TestContext context) {
        floor(context);
        ServerConfig config = ServerConfig.get();
        int perTick = config.miniGameBubbleRestorePerTick;
        config.miniGameBubbleRestorePerTick = 16;
        ServerPlayerEntity player = player(context, "slow", 2.5, 5, 2.5);
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND));
        ZoneBubble bubble = begin(context, player);
        for (int x = 1; x <= 4; x++) for (int y = 1; y <= 4; y++) for (int z = 1; z <= 4; z++) context.setBlockState(at(x, y, z), Blocks.STONE);
        context.assertTrue(bubble.journalSize() == 64, "64 blocks changed");

        bubble.end();
        context.assertTrue(bubble.isRestoring(), "64 blocks at 16 a tick: not done at once");
        context.assertTrue(count(player, Items.DIAMOND) == 1, "the players get their inventory back at once");
        context.assertTrue(ZoneBubbles.of(context.getWorld(), context.getAbsolutePos(at(2, 2, 2))) == bubble, "the zone is still guarded");
        context.assertTrue(!player.interactionManager.tryBreakBlock(context.getAbsolutePos(at(1, 4, 1))), "nobody acts in a zone being put back");
        later(context, 2, () -> {
            context.assertTrue(bubble.isRestoring(), "still going");
            context.assertTrue(!inZone(context, player), "nobody stays in a zone being put back");
        });
        later(context, 40, () -> {
            config.miniGameBubbleRestorePerTick = perTick;
            context.assertTrue(bubble.state() == ZoneBubble.State.ENDED, "done after a few ticks");
            for (int x = 1; x <= 4; x++) for (int y = 1; y <= 4; y++) for (int z = 1; z <= 4; z++) context.expectBlock(Blocks.AIR, at(x, y, z));
            remove(context, player);
            done(context);
        });
    }

    /** The feature turned off: a session begins nothing, and nothing is swapped, journaled or guarded. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_off")
    public void switchTurnsEverythingOff(TestContext context) {
        floor(context);
        ServerConfig config = ServerConfig.get();
        config.miniGameBubble = false;
        try {
            ServerPlayerEntity player = player(context, "off", 2.5, 1, 2.5);
            player.getInventory().setStack(0, new ItemStack(Items.DIAMOND));
            ZoneBubble bubble = begin(context, player);
            context.assertTrue(!bubble.isActive() && bubble.refusal() == ZoneBubble.Refusal.DISABLED, "no bubble");
            context.assertTrue(!ZoneBorder.ACTIVE && ZoneBubbles.all().isEmpty(), "no hook is armed");
            context.assertTrue(count(player, Items.DIAMOND) == 1 && ZoneBubbles.ofPlayer(player) == null, "the inventory is not swapped");
            context.setBlockState(at(2, 1, 2), Blocks.GOLD_BLOCK);
            bubble.addParticipant(player);
            bubble.removePlayer(player);
            bubble.end();
            bubble.endNow();
            context.expectBlock(Blocks.GOLD_BLOCK, at(2, 1, 2));
            context.assertTrue(count(player, Items.DIAMOND) == 1, "nothing happened");
            remove(context, player);
        } finally {
            config.miniGameBubble = true;
        }
        done(context);
    }

    /**
     * The server crashes during a session (its memory is lost, its files stay): when it starts again the zone is put
     * back, and the players get their inventory back; one saved before its session began keeps the one it has.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_crash")
    public void crashIsRecovered(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        context.setBlockState(at(2, 1, 2), Blocks.STONE);
        chest(context, at(3, 1, 2), new ItemStack(Items.DIAMOND, 5));
        PigEntity pig = context.spawnMob(EntityType.PIG, at(2, 1, 4));
        pig.setAiDisabled(true);
        UUID pigId = pig.getUuid();
        // the files of the sessions already over go with a save: only this one is left unfinished
        server.saveAll(true, false, false);
        ServerPlayerEntity saved = player(context, "saved", 2.5, 2, 2.5);
        ServerPlayerEntity rolledBack = player(context, "rolled", 3.5, 2, 3.5);
        saved.getInventory().setStack(0, new ItemStack(Items.DIAMOND_SWORD));
        saved.experienceLevel = 12;
        rolledBack.getInventory().setStack(0, new ItemStack(Items.GOLDEN_SWORD));
        ZoneBubble bubble = begin(context, saved, rolledBack);

        context.setBlockState(at(2, 1, 2), Blocks.GOLD_BLOCK);
        context.setBlockState(at(4, 4, 4), Blocks.OAK_PLANKS);
        context.<ChestBlockEntity>getBlockEntity(at(3, 1, 2)).clear();
        world.breakBlock(context.getAbsolutePos(at(3, 1, 2)), false);
        pig.discard();
        context.spawnMob(EntityType.COW, at(3, 1, 3));
        saved.getInventory().setStack(0, new ItemStack(Items.NETHERITE_BLOCK, 64));
        // this one was last saved before the session began: it holds what it owns (and no mark of a session)
        rolledBack.getInventory().clear();
        rolledBack.getInventory().setStack(0, new ItemStack(Items.GOLDEN_SWORD));
        rolledBack.removeCommandTag(STASH_TAG);

        ZoneBubbles.simulateCrash();
        context.assertTrue(ZoneBubbles.all().isEmpty() && ZoneBubbles.ofPlayer(saved) == null && !ZoneBorder.ACTIVE, "the server remembers nothing");
        context.expectBlock(Blocks.GOLD_BLOCK, at(2, 1, 2));
        context.assertTrue(count(saved, Items.NETHERITE_BLOCK) == 64, "the player still holds its session inventory");

        ZoneBubbles.recover(server);
        context.assertTrue(bubble.state() == ZoneBubble.State.ENDED && ZoneBubbles.all().isEmpty(), "nothing is left to restore");
        context.expectBlock(Blocks.STONE, at(2, 1, 2));
        context.expectBlock(Blocks.AIR, at(4, 4, 4));
        context.expectBlock(Blocks.CHEST, at(3, 1, 2));
        context.assertTrue(context.<ChestBlockEntity>getBlockEntity(at(3, 1, 2)).getStack(0).getCount() == 5, "the chest holds its diamonds again");
        context.assertTrue(world.getEntity(pigId) instanceof PigEntity back && back.isAlive(), "the pig is back");
        context.assertTrue(world.getEntitiesByType(EntityType.COW, zone(context).bounds(), Entity::isAlive).isEmpty(), "the cow of the session is gone");
        context.assertTrue(saved.getInventory().getStack(0).isOf(Items.DIAMOND_SWORD) && count(saved, Items.NETHERITE_BLOCK) == 0 && saved.experienceLevel == 12, "the player saved during its session gets its inventory back");
        context.assertTrue(!saved.getCommandTags().contains(STASH_TAG), "and is marked no more");
        context.assertTrue(rolledBack.getInventory().getStack(0).isOf(Items.GOLDEN_SWORD) && count(rolledBack, Items.GOLDEN_SWORD) == 1, "the player saved before its session keeps what it has: nothing twice");

        // once saved, another start finds nothing to do
        server.saveAll(true, false, false);
        context.setBlockState(at(2, 1, 2), Blocks.EMERALD_BLOCK);
        ZoneBubbles.recover(server);
        context.expectBlock(Blocks.EMERALD_BLOCK, at(2, 1, 2));
        remove(context, saved, rolledBack);
        done(context);
    }

    /** The server stops during a session: the zone is whole and the inventories back before anything is saved. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_stop")
    public void serverStopEndsEverySession(TestContext context) {
        floor(context);
        context.setBlockState(at(2, 1, 2), Blocks.STONE);
        ServerPlayerEntity player = player(context, "stop", 2.5, 2, 2.5);
        player.getInventory().setStack(0, new ItemStack(Items.DIAMOND));
        ZoneBubble bubble = begin(context, player);
        for (int x = 1; x <= 4; x++) for (int z = 1; z <= 4; z++) context.setBlockState(at(x, 1, z), Blocks.GOLD_BLOCK);

        ZoneBubbles.endAll(context.getWorld().getServer());
        context.assertTrue(bubble.state() == ZoneBubble.State.ENDED && ZoneBubbles.all().isEmpty() && !ZoneBorder.ACTIVE, "every session is over");
        context.expectBlock(Blocks.STONE, at(2, 1, 2));
        context.expectBlock(Blocks.AIR, at(3, 1, 3));
        context.assertTrue(count(player, Items.DIAMOND) == 1 && !player.getCommandTags().contains(STASH_TAG), "the inventory is back");
        remove(context, player);
        done(context);
    }

    // ------------------------------------------------------------------ what it costs

    /**
     * Timed: what the hooks cost with no session, what journaling 10 648 block changes and putting them back costs,
     * what a session costs to start in a zone full of chests. The figures go to the log ({@code [zone-bubble-bench]});
     * the test only fails on a result an order of magnitude off.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_bench", tickLimit = 400)
    public void benchmark(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        // 22x22x22 blocks of air above the test
        BlockPos corner = context.getAbsolutePos(at(0, 12, 0));
        int side = 22;
        MiniGameZone arena = MiniGameZone.of(world.getRegistryKey(), corner, corner.add(side - 1, side - 1, side - 1));
        List<BlockPos> cells = new ArrayList<>(side * side * side);
        for (BlockPos pos : BlockPos.iterate(corner, corner.add(side - 1, side - 1, side - 1))) cells.add(pos.toImmutable());
        BlockState stone = Blocks.STONE.getDefaultState(), air = Blocks.AIR.getDefaultState();
        ArmorStandEntity walker = context.spawnEntity(EntityType.ARMOR_STAND, at(6, 1, 6));
        Vec3d start = walker.getPos();
        int moves = 1_000_000;

        // no session: the hooks read one boolean and leave (the best of several rounds: the machine does other things)
        double idleSet = Double.MAX_VALUE, idleMove = Double.MAX_VALUE, idleTick = Double.MAX_VALUE;
        // a block that does nothing on a random tick: what is timed is the call and its hooks
        BlockPos ticked = context.getAbsolutePos(at(0, 0, 0));
        for (int round = 0; round < 4; round++) {
            long t = System.nanoTime();
            for (BlockPos pos : cells) world.setBlockState(pos, stone, Block.NOTIFY_LISTENERS);
            for (BlockPos pos : cells) world.setBlockState(pos, air, Block.NOTIFY_LISTENERS);
            idleSet = Math.min(idleSet, (System.nanoTime() - t) / (2.0 * cells.size()));
            t = System.nanoTime();
            for (int i = 0; i < moves; i++) walker.setPos(start.x + (i & 1), start.y, start.z);
            idleMove = Math.min(idleMove, (System.nanoTime() - t) / (double) moves);
            t = System.nanoTime();
            for (int i = 0; i < moves; i++) stone.randomTick(world, ticked, world.random);
            idleTick = Math.min(idleTick, (System.nanoTime() - t) / (double) moves);
        }

        double sessionTick = Double.MAX_VALUE;
        double sessionMove = Double.MAX_VALUE, sessionSetOutside = Double.MAX_VALUE, journaled = Double.MAX_VALUE, again = Double.MAX_VALUE, restoreMs = Double.MAX_VALUE;
        BlockPos away = context.getAbsolutePos(at(1, 1, 1));
        for (int round = 0; round < 4; round++) {
            ZoneBubble bubble = ZoneBubbles.begin(server, UUID.randomUUID(), arena, List.of(), List.of(), ZoneBubble.Options.DEFAULT);
            context.assertTrue(bubble.isActive(), "the session begins");
            // a session elsewhere (the arena): the same calls, out of every zone; the walker steps into another block every time
            long t = System.nanoTime();
            for (int i = 0; i < moves; i++) walker.setPos(start.x + (i & 1), start.y, start.z);
            sessionMove = Math.min(sessionMove, (System.nanoTime() - t) / (double) moves);
            t = System.nanoTime();
            for (int i = 0; i < moves; i++) stone.randomTick(world, ticked, world.random);
            sessionTick = Math.min(sessionTick, (System.nanoTime() - t) / (double) moves);
            t = System.nanoTime();
            for (int i = 0; i < 10_000; i++) world.setBlockState(away, (i & 1) == 0 ? stone : air, Block.NOTIFY_LISTENERS);
            sessionSetOutside = Math.min(sessionSetOutside, (System.nanoTime() - t) / 10_000.0);

            // 10 648 first changes journaled, then changed again (already journaled), then put back
            t = System.nanoTime();
            for (BlockPos pos : cells) world.setBlockState(pos, stone, Block.NOTIFY_LISTENERS);
            journaled = Math.min(journaled, (System.nanoTime() - t) / (double) cells.size());
            context.assertTrue(bubble.journalSize() == cells.size(), "every change is journaled");
            t = System.nanoTime();
            for (BlockPos pos : cells) world.setBlockState(pos, air, Block.NOTIFY_LISTENERS);
            for (BlockPos pos : cells) world.setBlockState(pos, stone, Block.NOTIFY_LISTENERS);
            again = Math.min(again, (System.nanoTime() - t) / (2.0 * cells.size()));
            t = System.nanoTime();
            bubble.endNow();
            restoreMs = Math.min(restoreMs, (System.nanoTime() - t) / 1.0e6);
            context.assertTrue(bubble.state() == ZoneBubble.State.ENDED, "restored");
        }
        for (BlockPos pos : cells) context.assertTrue(world.getBlockState(pos).isAir(), "the arena is air again");
        world.setBlockState(away, air, Block.NOTIFY_LISTENERS);

        // starting a session in a zone holding 200 full chests and 100 entities
        List<BlockPos> chests = cells.subList(0, 200);
        for (BlockPos pos : chests) {
            world.setBlockState(pos, Blocks.CHEST.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            ChestBlockEntity chest = (ChestBlockEntity) world.getBlockEntity(pos);
            for (int slot = 0; slot < chest.size(); slot++) chest.setStack(slot, new ItemStack(Items.DIAMOND, 1 + slot));
        }
        for (int i = 0; i < 100; i++) {
            ArmorStandEntity stand = new ArmorStandEntity(world, corner.getX() + 10.5, corner.getY() + 15, corner.getZ() + 0.5 + i * 0.2);
            stand.setNoGravity(true);
            world.spawnEntity(stand);
        }
        double beginMs = Double.MAX_VALUE, endMs = Double.MAX_VALUE;
        for (int round = 0; round < 4; round++) {
            long t = System.nanoTime();
            ZoneBubble crowded = ZoneBubbles.begin(server, UUID.randomUUID(), arena, List.of(), List.of(), ZoneBubble.Options.DEFAULT);
            beginMs = Math.min(beginMs, (System.nanoTime() - t) / 1.0e6);
            for (BlockPos pos : chests) ((ChestBlockEntity) world.getBlockEntity(pos)).clear();
            t = System.nanoTime();
            crowded.endNow();
            endMs = Math.min(endMs, (System.nanoTime() - t) / 1.0e6);
        }
        context.assertTrue(((ChestBlockEntity) world.getBlockEntity(chests.get(0))).getStack(26).getCount() == 27, "the chests are full again");
        for (BlockPos pos : chests) {
            ((ChestBlockEntity) world.getBlockEntity(pos)).clear();
            world.setBlockState(pos, air, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
        for (Entity entity : world.getEntitiesByType(EntityType.ARMOR_STAND, arena.bounds(), e -> true)) entity.discard();
        walker.discard();

        Steveparty.LOGGER.info("[zone-bubble-bench] no session: setBlockState {} ns/call, Entity.setPos {} ns/call, random tick {} ns/call",
                Math.round(idleSet), String.format("%.1f", idleMove), String.format("%.1f", idleTick));
        Steveparty.LOGGER.info("[zone-bubble-bench] session elsewhere: setBlockState out of every zone {} ns/call, Entity.setPos {} ns/call, random tick {} ns/call",
                Math.round(sessionSetOutside), String.format("%.1f", sessionMove), String.format("%.1f", sessionTick));
        Steveparty.LOGGER.info("[zone-bubble-bench] in the zone: first change of a position {} ns/call, later change {} ns/call ({} positions)", Math.round(journaled), Math.round(again), cells.size());
        Steveparty.LOGGER.info("[zone-bubble-bench] restoring {} blocks: {} ms", cells.size(), String.format("%.1f", restoreMs));
        Steveparty.LOGGER.info("[zone-bubble-bench] begin with 200 full chests and 100 entities: {} ms; end (200 chests refilled, 100 entities made again): {} ms", String.format("%.2f", beginMs), String.format("%.2f", endMs));
        context.assertTrue(restoreMs < 2000, "restoring 10 648 blocks took " + restoreMs + " ms");
        context.assertTrue(beginMs < 500, "beginning took " + beginMs + " ms");
        done(context);
    }
}
