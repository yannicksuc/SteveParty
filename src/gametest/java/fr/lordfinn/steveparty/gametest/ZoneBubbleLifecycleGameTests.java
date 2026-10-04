package fr.lordfinn.steveparty.gametest;

import com.mojang.authlib.GameProfile;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.config.ServerConfig;
import fr.lordfinn.steveparty.minigame.MiniGameArena;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.PageZone;
import fr.lordfinn.steveparty.minigame.zone.MiniGameZone;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubble;
import fr.lordfinn.steveparty.minigame.zone.ZoneBubbles;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.CommandBlockBlockEntity;
import net.minecraft.block.entity.LecternBlockEntity;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.entity.SignText;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockFace;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.decoration.GlowItemFrameEntity;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.decoration.LeashKnotEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.CowEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.passive.WolfEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.ChestBoatEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
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
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.EulerAngle;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

/**
 * The mini-game bubble whatever happens around it: the arena always comes back exactly as built (every kind of block,
 * block entity and entity, the ticks it was waiting for, the chunks not loaded yet when the session began), and every
 * player always gets exactly his things back (spawn point and status effects included), through crashes, restorations
 * over several ticks, zones side by side and rounds that follow visits.
 * <p>
 * The tests in the arena of the test play in a zone from (1,1,1) to (4,5,4) (the fidelity test: to (6,4,6)); those
 * that need a chunk loaded or not play far away, in a chunk of their own. Each test that touches the whole server
 * (crash, recovery) has a batch of its own.
 */
public class ZoneBubbleLifecycleGameTests implements FabricGameTest {
    private static final String BATCH = "zone_bubble_life";
    private static final AtomicInteger SERIAL = new AtomicInteger();

    // ------------------------------------------------------------------ helpers

    @Override
    public void invokeTestMethod(TestContext context, Method method) {
        try {
            FabricGameTest.super.invokeTestMethod(context, method);
        } catch (RuntimeException | Error e) {
            endLeftSession(context);
            throw e;
        }
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

    private static void floor(TestContext context) {
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) context.setBlockState(at(x, 0, z), Blocks.STONE);
    }

    private static ZoneBubble begin(TestContext context, MiniGameZone zone, ServerPlayerEntity... participants) {
        endLeftSession(context);
        return ZoneBubbles.begin(context.getWorld().getServer(), UUID.randomUUID(), zone, List.of(participants), List.of(), ZoneBubble.Options.DEFAULT);
    }

    private static ServerPlayerEntity player(TestContext context, String name, double x, double y, double z) {
        ServerWorld world = context.getWorld();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "l" + SERIAL.incrementAndGet() + name);
        ConnectedClientData data = ConnectedClientData.createDefault(profile, false);
        ServerPlayerEntity player = new ServerPlayerEntity(world.getServer(), world, profile, data.syncedOptions());
        ClientConnection connection = new ClientConnection(NetworkSide.SERVERBOUND);
        new EmbeddedChannel(connection);
        world.getServer().getPlayerManager().onPlayerConnect(connection, player, data);
        player.changeGameMode(GameMode.SURVIVAL);
        player.getInventory().clear();
        Vec3d abs = context.getAbsolute(new Vec3d(x, y, z));
        player.refreshPositionAndAngles(abs.x, abs.y, abs.z, 0, 0);
        return player;
    }

    private static void remove(TestContext context, ServerPlayerEntity... players) {
        for (ServerPlayerEntity player : players) context.getWorld().getServer().getPlayerManager().remove(player);
    }

    private static void later(TestContext context, long ticks, Runnable step) {
        context.waitAndRun(ticks, () -> {
            try {
                step.run();
            } catch (RuntimeException e) {
                Steveparty.LOGGER.error("[zone-bubble-life-test] a step failed: {}", e.getMessage());
                endLeftSession(context);
                Runnable cleanup = CLEANUPS.remove(context);
                if (cleanup != null) cleanup.run();
                throw e;
            }
        });
    }

    /** Runs {@code then} once {@code condition} holds, checked every tick; fails after {@code ticks}. */
    private static void when(TestContext context, BooleanSupplier condition, int ticks, String what, Runnable then) {
        if (condition.getAsBoolean()) then.run();
        else if (ticks <= 0) context.throwGameTestException(what);
        else later(context, 1, () -> when(context, condition, ticks - 1, what, then));
    }

    /** A corner far from the test (a chunk nothing else loads), 2 blocks into its chunk. */
    private static BlockPos far(TestContext context, int distance) {
        BlockPos base = context.getAbsolutePos(at(0, 1, 0));
        int cx = base.getX() >> 4, cz = (base.getZ() + distance) >> 4;
        return new BlockPos((cx << 4) + 2, base.getY(), (cz << 4) + 2);
    }

    /** What each test leaves going far away, ended if it fails (the tests after it count the sessions of the server). */
    private static final Map<TestContext, Runnable> CLEANUPS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * A far chunk loaded, its entities read and those other runs left there gone, a floor under the zones: then
     * {@code then}. The chunk is let go of, and the far sessions ended, when the test ends or fails.
     */
    private static void farArea(TestContext context, BlockPos far, Runnable then) {
        ServerWorld world = context.getWorld();
        ChunkPos chunk = new ChunkPos(far);
        world.setChunkForced(chunk.x, chunk.z, true);
        world.getChunk(chunk.x, chunk.z);
        Box area = new Box(chunk.getStartX(), far.getY() - 2, chunk.getStartZ(), chunk.getEndX() + 1, far.getY() + 8, chunk.getEndZ() + 1);
        CLEANUPS.put(context, () -> {
            for (ZoneBubble bubble : ZoneBubbles.all()) if (bubble.zone().bounds().intersects(area)) bubble.endNow();
            world.setChunkForced(chunk.x, chunk.z, false);
        });
        when(context, () -> world.isChunkLoaded(chunk.toLong()), 100, "the far chunk's entities were never read", () -> {
            for (Entity entity : world.getOtherEntities(null, area, entity -> !(entity instanceof PlayerEntity))) entity.discard();
            for (BlockPos pos : BlockPos.iterate(far.add(-2, -1, -2), far.add(11, -1, 5))) world.setBlockState(pos, Blocks.STONE.getDefaultState());
            for (BlockPos pos : BlockPos.iterate(far.add(-2, 0, -2), far.add(11, 5, 5))) world.setBlockState(pos, Blocks.AIR.getDefaultState());
            then.run();
        });
    }

    private static void farDone(TestContext context) {
        Runnable cleanup = CLEANUPS.remove(context);
        if (cleanup != null) cleanup.run();
        context.complete();
    }

    private static MiniGameZone zoneAt(ServerWorld world, BlockPos corner) {
        return MiniGameZone.of(world.getRegistryKey(), corner, corner.add(3, 4, 3));
    }

    private static ArmorStandEntity statue(ServerWorld world, BlockPos at) {
        ArmorStandEntity stand = new ArmorStandEntity(world, at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
        stand.setNoGravity(true);
        stand.setCustomName(Text.literal("statue"));
        world.spawnEntity(stand);
        return stand;
    }

    // ------------------------------------------------------------------ the arena comes back whole

    /**
     * Every kind of block, block entity and entity an arena may hold comes back exactly as it was (same states, same
     * data, same UUIDs), after a session that broke every block and killed every entity of the zone.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_life_fidelity")
    public void everyKindComesBackAsItWas(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        MiniGameZone zone = MiniGameZone.of(world.getRegistryKey(), context.getAbsolutePos(at(1, 1, 1)), context.getAbsolutePos(at(6, 4, 6)));
        Map<BlockPos, BlockState> states = new HashMap<>();
        int flags = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;
        java.util.function.BiConsumer<BlockPos, BlockState> put = (rel, state) -> world.setBlockState(context.getAbsolutePos(rel), state, flags);

        // two-block structures, attachments, redstone, fluids, plants
        put.accept(at(1, 1, 1), Blocks.OAK_DOOR.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        put.accept(at(1, 2, 1), Blocks.OAK_DOOR.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        put.accept(at(2, 1, 1), Blocks.RED_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH).with(Properties.BED_PART, BedPart.HEAD));
        put.accept(at(2, 1, 2), Blocks.RED_BED.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH).with(Properties.BED_PART, BedPart.FOOT));
        put.accept(at(3, 1, 1), Blocks.TALL_GRASS.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        put.accept(at(3, 2, 1), Blocks.TALL_GRASS.getDefaultState().with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
        put.accept(at(4, 1, 1), Blocks.STONE.getDefaultState());
        put.accept(at(4, 1, 2), Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
        put.accept(at(5, 1, 1), Blocks.LEVER.getDefaultState().with(Properties.BLOCK_FACE, BlockFace.FLOOR).with(Properties.POWERED, true));
        put.accept(at(5, 1, 2), Blocks.REDSTONE_WIRE.getDefaultState().with(Properties.POWER, 15));
        put.accept(at(6, 1, 1), Blocks.REPEATER.getDefaultState().with(Properties.DELAY, 3).with(Properties.POWERED, true));
        put.accept(at(1, 1, 3), Blocks.PISTON.getDefaultState().with(Properties.FACING, Direction.UP).with(Properties.EXTENDED, true));
        put.accept(at(1, 2, 3), Blocks.PISTON_HEAD.getDefaultState().with(Properties.FACING, Direction.UP));
        put.accept(at(2, 1, 3), Blocks.WATER.getDefaultState());
        put.accept(at(2, 1, 4), Blocks.WATER.getDefaultState().with(Properties.LEVEL_15, 1));
        put.accept(at(3, 1, 3), Blocks.SNOW.getDefaultState().with(Properties.LAYERS, 3));
        put.accept(at(4, 1, 3), Blocks.FARMLAND.getDefaultState().with(Properties.MOISTURE, 7));
        put.accept(at(4, 2, 3), Blocks.WHEAT.getDefaultState().with(Properties.AGE_7, 4));
        put.accept(at(5, 1, 3), Blocks.OAK_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
        // vanilla block entities
        put.accept(at(6, 1, 3), Blocks.OAK_SIGN.getDefaultState());
        SignBlockEntity sign = (SignBlockEntity) world.getBlockEntity(context.getAbsolutePos(at(6, 1, 3)));
        sign.setText(new SignText().withMessage(0, Text.literal("front")), true);
        sign.setText(new SignText().withMessage(1, Text.literal("back")), false);
        sign.setWaxed(true);
        put.accept(at(1, 1, 5), Blocks.WHITE_BANNER.getDefaultState());
        put.accept(at(2, 1, 5), Blocks.LECTERN.getDefaultState().with(Properties.HAS_BOOK, true));
        ((LecternBlockEntity) world.getBlockEntity(context.getAbsolutePos(at(2, 1, 5)))).setBook(new ItemStack(Items.WRITABLE_BOOK));
        put.accept(at(3, 1, 5), Blocks.DECORATED_POT.getDefaultState());
        put.accept(at(4, 1, 5), Blocks.CHISELED_BOOKSHELF.getDefaultState());
        ((Inventory) world.getBlockEntity(context.getAbsolutePos(at(4, 1, 5)))).setStack(0, new ItemStack(Items.BOOK));
        put.accept(at(5, 1, 5), Blocks.JUKEBOX.getDefaultState());
        ((Inventory) world.getBlockEntity(context.getAbsolutePos(at(5, 1, 5)))).setStack(0, new ItemStack(Items.MUSIC_DISC_CAT));
        put.accept(at(6, 1, 5), Blocks.BEEHIVE.getDefaultState().with(Properties.HONEY_LEVEL, 2));
        put.accept(at(1, 1, 6), Blocks.SPAWNER.getDefaultState());
        put.accept(at(2, 1, 6), Blocks.COMMAND_BLOCK.getDefaultState());
        ((CommandBlockBlockEntity) world.getBlockEntity(context.getAbsolutePos(at(2, 1, 6)))).getCommandExecutor().setCommand("say arena");
        put.accept(at(3, 1, 6), Blocks.SKELETON_SKULL.getDefaultState());
        put.accept(at(4, 1, 6), Blocks.CAMPFIRE.getDefaultState());
        put.accept(at(5, 1, 6), Blocks.BREWING_STAND.getDefaultState());
        ((Inventory) world.getBlockEntity(context.getAbsolutePos(at(5, 1, 6)))).setStack(3, new ItemStack(Items.NETHER_WART));
        put.accept(at(6, 1, 6), Blocks.FURNACE.getDefaultState());
        ((Inventory) world.getBlockEntity(context.getAbsolutePos(at(6, 1, 6)))).setStack(0, new ItemStack(Items.IRON_ORE, 3));
        // this mod's blocks
        Block[] mine = {ModBlocks.TRADING_STALL, ModBlocks.STENCIL_MAKER, ModBlocks.TILE, ModBlocks.CASH_REGISTER, ModBlocks.PODIUM,
                ModBlocks.PIGGY_BANK, ModBlocks.GOAL_POLE_BASE, ModBlocks.DICE_FORGE, ModBlocks.TELESCOPE, ModBlocks.MINI_GAME_CONTROLLER,
                ModBlocks.OAK_EASEL_SIGN, ModBlocks.PARTY_BELL, ModBlocks.LOOTING_BOX, ModBlocks.COPPER_MINIGAME_PIPE, ModBlocks.VILLAGER_BLOCK};
        for (int i = 0; i < mine.length; i++) put.accept(at(1 + i % 2 * 5, 3 + i / 12, 1 + i / 2 % 6), mine[i].getDefaultState());
        put.accept(at(6, 2, 4), Blocks.OAK_FENCE.getDefaultState());

        // entities
        Vec3d c = context.getAbsolute(new Vec3d(3.5, 3, 3.5));
        ItemFrameEntity frame = new ItemFrameEntity(world, BlockPos.ofFloored(c), Direction.UP);
        frame.setHeldItemStack(new ItemStack(Items.COMPASS));
        world.spawnEntity(frame);
        GlowItemFrameEntity glow = new GlowItemFrameEntity(world, BlockPos.ofFloored(c).east(), Direction.UP);
        glow.setHeldItemStack(new ItemStack(Items.CLOCK));
        world.spawnEntity(glow);
        ArmorStandEntity stand = context.spawnEntity(EntityType.ARMOR_STAND, at(3, 3, 4));
        stand.setHeadRotation(new EulerAngle(10, 20, 30));
        stand.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        ChestBoatEntity boat = context.spawnEntity(EntityType.OAK_CHEST_BOAT, at(4, 3, 4));
        boat.setStack(0, new ItemStack(Items.DIAMOND, 4));
        VillagerEntity villager = context.spawnEntity(EntityType.VILLAGER, at(5, 3, 2));
        villager.setCustomName(Text.literal("Bob"));
        villager.setAiDisabled(true);
        WolfEntity wolf = context.spawnEntity(EntityType.WOLF, at(5, 3, 4));
        wolf.setOwnerUuid(UUID.randomUUID());
        wolf.setTamed(true, false);
        SheepEntity sheep = context.spawnEntity(EntityType.SHEEP, at(5, 3, 5));
        sheep.setBaby(true);
        sheep.attachLeash(LeashKnotEntity.getOrCreate(world, context.getAbsolutePos(at(6, 2, 4))), true);
        SlimeEntity slime = context.spawnEntity(EntityType.SLIME, at(4, 3, 2));
        slime.setSize(3, true);
        context.spawnEntity(EntityType.END_CRYSTAL, at(3, 4, 2));
        context.spawnEntity(EntityType.TNT_MINECART, at(4, 4, 4));
        context.spawnEntity(EntityType.BLOCK_DISPLAY, at(5, 4, 5));
        context.spawnEntity(EntityType.INTERACTION, at(4, 4, 5));
        Vec3d orb = context.getAbsolute(new Vec3d(3.5, 4, 5.5));
        world.spawnEntity(new ExperienceOrbEntity(world, orb.x, orb.y, orb.z, 7));
        AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(world, orb.x, orb.y, orb.z - 1);
        cloud.setRadius(1.5f);
        world.spawnEntity(cloud);
        for (Entity entity : world.getOtherEntities(null, zone.bounds(), entity -> !(entity instanceof PlayerEntity))) {
            entity.setVelocity(Vec3d.ZERO);
            if (entity instanceof net.minecraft.entity.mob.MobEntity mob) mob.setPersistent();
        }

        // what the zone is now: blocks, block entities, entities
        Map<BlockPos, NbtCompound> blockEntities = new HashMap<>();
        Map<UUID, NbtCompound> entities = new HashMap<>();
        for (BlockPos pos : BlockPos.iterate(context.getAbsolutePos(at(1, 1, 1)), context.getAbsolutePos(at(6, 4, 6)))) {
            BlockPos at = pos.toImmutable();
            states.put(at, world.getBlockState(at));
            BlockEntity be = world.getBlockEntity(at);
            if (be == null) continue;
            // as a reload of the chunk would read it (a lectern's empty book has no page 0: -1 once read)
            BlockEntity read = BlockEntity.createFromNbt(at, be.getCachedState(), be.createNbtWithIdentifyingData(world.getRegistryManager()), world.getRegistryManager());
            blockEntities.put(at, (read != null ? read : be).createNbtWithIdentifyingData(world.getRegistryManager()));
        }
        // a leash knot is never saved: the mob it holds makes one again (another UUID) when it is loaded
        for (Entity entity : world.getOtherEntities(null, zone.bounds(), entity -> !(entity instanceof PlayerEntity) && !(entity instanceof LeashKnotEntity)
                && zone.contains(entity.getBlockPos()))) {
            NbtCompound nbt = entity.writeNbt(new NbtCompound());
            nbt.putString("id", EntityType.getId(entity.getType()).toString());
            entities.put(entity.getUuid(), nbt);
        }
        context.assertTrue(entities.size() >= 13, "the entities are there, got " + entities.size());

        ZoneBubble bubble = begin(context, zone);
        context.assertTrue(bubble.isActive(), "the session begins");
        // the session: every block broken (neighbours told), every entity killed, something of its own left behind
        for (BlockPos pos : states.keySet()) world.setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), Block.NOTIFY_ALL);
        for (Entity entity : world.getOtherEntities(null, zone.bounds().expand(1), entity -> !(entity instanceof PlayerEntity))) entity.kill(world);
        context.spawnEntity(EntityType.COW, at(3, 3, 3));
        bubble.endNow();

        List<String> wrong = new ArrayList<>();
        states.forEach((pos, state) -> {
            BlockState now = world.getBlockState(pos);
            if (now != state) wrong.add("block " + pos + " " + state + " -> " + now);
        });
        blockEntities.forEach((pos, nbt) -> {
            BlockEntity be = world.getBlockEntity(pos);
            NbtCompound now = be == null ? null : be.createNbtWithIdentifyingData(world.getRegistryManager());
            if (!nbt.equals(now)) wrong.add("block entity " + nbt.getString("id") + " " + nbt + " -> " + now);
        });
        entities.forEach((id, nbt) -> {
            Entity entity = world.getEntity(id);
            NbtCompound now = entity == null ? null : entity.writeNbt(new NbtCompound());
            if (now != null) now.putString("id", EntityType.getId(entity.getType()).toString());
            if (now == null) wrong.add("entity " + nbt.getString("id") + " missing");
            else if (!nbt.equals(now)) wrong.add("entity " + nbt.getString("id") + " " + nbt + " -> " + now);
        });
        List<Entity> extra = world.getOtherEntities(null, zone.bounds().expand(1), entity -> !(entity instanceof PlayerEntity) && !(entity instanceof LeashKnotEntity)
                && !entities.containsKey(entity.getUuid()));
        for (Entity entity : extra) wrong.add("entity of the session left: " + entity.getType());
        Runnable clear = () -> {
            for (Entity entity : world.getOtherEntities(null, zone.bounds().expand(1), entity -> !(entity instanceof PlayerEntity))) entity.discard();
            for (BlockPos pos : states.keySet()) world.setBlockState(pos, Blocks.AIR.getDefaultState(), flags);
        };
        if (!wrong.isEmpty()) clear.run();
        context.assertTrue(wrong.isEmpty(), wrong.size() + " differences: " + String.join(" | ", wrong));
        UUID sheepId = sheep.getUuid();
        later(context, 3, () -> {
            boolean leashed = world.getEntity(sheepId) instanceof SheepEntity back && back.isLeashed();
            clear.run();
            context.assertTrue(leashed, "the sheep is tied to its fence again");
            context.complete();
        });
    }

    /**
     * A button pressed when the session begins pops out once the zone is put back (its tick is asked again): it used
     * to stay pressed for ever, and so would a redstone clock of the arena stop for ever.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH, tickLimit = 80)
    public void ticksTheZoneWaitedForAreAskedAgain(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        BlockPos button = context.getAbsolutePos(at(2, 1, 2));
        world.setBlockState(button, Blocks.STONE_BUTTON.getDefaultState().with(Properties.BLOCK_FACE, BlockFace.FLOOR).with(Properties.POWERED, true), Block.NOTIFY_ALL);
        world.scheduleBlockTick(button, Blocks.STONE_BUTTON, 20);
        ZoneBubble bubble = begin(context, zone(context));
        // the session breaks it
        context.setBlockState(at(2, 1, 2), Blocks.AIR);
        bubble.end();
        context.assertTrue(world.getBlockState(button).isOf(Blocks.STONE_BUTTON) && world.getBlockState(button).get(Properties.POWERED), "the button is back, pressed as it was");
        later(context, 30, () -> {
            context.assertTrue(world.getBlockState(button).isOf(Blocks.STONE_BUTTON) && !world.getBlockState(button).get(Properties.POWERED), "and it popped out as it would have");
            context.setBlockState(at(2, 1, 2), Blocks.AIR);
            context.complete();
        });
    }

    // ------------------------------------------------------------------ the players

    /** A bed slept in during the round does not move the spawn point: the one the player had is given back. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void spawnPointIsGivenBack(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        ServerPlayerEntity player = player(context, "spawn", 2.5, 1, 2.5);
        BlockPos home = context.getAbsolutePos(at(7, 1, 7));
        player.setSpawnPoint(world.getRegistryKey(), home, 0, true, false);
        ZoneBubble bubble = begin(context, zone(context), player);
        player.setSpawnPoint(world.getRegistryKey(), context.getAbsolutePos(at(2, 1, 2)), 0, false, false);
        bubble.end();
        context.assertTrue(home.equals(player.getSpawnPointPosition()) && player.isSpawnForced(), "the spawn point is back, got " + player.getSpawnPointPosition());
        remove(context, player);
        context.complete();
    }

    /** Effects stay on their side: one had before the round is paused in it, one got in the round does not follow out. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = BATCH)
    public void effectsStayOnTheirSide(TestContext context) {
        floor(context);
        ServerPlayerEntity player = player(context, "fx", 2.5, 1, 2.5);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 2400, 1));
        ZoneBubble bubble = begin(context, zone(context), player);
        context.assertTrue(!player.hasStatusEffect(StatusEffects.SPEED), "the speed had before does not help in the round");
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 2400));
        bubble.end();
        StatusEffectInstance speed = player.getStatusEffect(StatusEffects.SPEED);
        context.assertTrue(speed != null && speed.getAmplifier() == 1 && speed.getDuration() > 2300, "the speed is back, as long as it was");
        context.assertTrue(!player.hasStatusEffect(StatusEffects.STRENGTH), "the strength of the round stays in it");
        remove(context, player);
        context.complete();
    }

    // ------------------------------------------------------------------ crashes and restarts

    /** A crash while the zone is put back over several ticks: what changed meanwhile is put back too when the server starts. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_life_crash")
    public void crashDuringTheRestorationIsRecovered(TestContext context) {
        floor(context);
        MinecraftServer server = context.getWorld().getServer();
        ServerConfig config = ServerConfig.get();
        int perTick = config.miniGameBubbleRestorePerTick;
        server.saveAll(true, false, false);
        try {
            config.miniGameBubbleRestorePerTick = 16;
            ZoneBubble bubble = begin(context, zone(context));
            for (int x = 1; x <= 4; x++) for (int y = 1; y <= 4; y++) for (int z = 1; z <= 4; z++) context.setBlockState(at(x, y, z), Blocks.STONE);
            bubble.end();
            context.assertTrue(bubble.isRestoring(), "put back over several ticks");
            // a change while it is put back (water flowing, a block falling, a block set by a command)
            context.setBlockState(at(4, 5, 4), Blocks.GOLD_BLOCK);
            ZoneBubbles.simulateCrash();
            ZoneBubbles.recover(server);
        } finally {
            config.miniGameBubbleRestorePerTick = perTick;
        }
        context.expectBlock(Blocks.AIR, at(4, 5, 4));
        for (int x = 1; x <= 4; x++) for (int y = 1; y <= 4; y++) for (int z = 1; z <= 4; z++) context.expectBlock(Blocks.AIR, at(x, y, z));
        context.complete();
    }

    /** The files of a session whose dimension is missing when the server starts stay: the zone is put back when it is there again. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_life_nowhere")
    public void aZoneInAMissingDimensionWaitsForIt(TestContext context) throws IOException {
        MinecraftServer server = context.getWorld().getServer();
        server.saveAll(true, false, false);
        UUID session = UUID.randomUUID();
        Path directory = server.getSavePath(WorldSavePath.ROOT).resolve("steveparty").resolve("zone_bubbles").resolve("sessions").resolve(session.toString());
        Files.createDirectories(directory);
        NbtCompound zone = new NbtCompound();
        zone.putString("Dimension", "steveparty:nowhere");
        zone.putIntArray("Box", new int[]{0, 0, 0, 3, 3, 3});
        NbtCompound nbt = new NbtCompound();
        nbt.putUuid("Session", session);
        nbt.put("Zone", zone);
        NbtIo.writeCompressed(nbt, directory.resolve("session.dat"));
        try {
            ZoneBubbles.recover(server);
            // the pending writes and deletions done
            server.saveAll(true, false, false);
            context.assertTrue(Files.exists(directory.resolve("session.dat")), "the session's files are kept");
        } finally {
            try (Stream<Path> files = Files.walk(directory)) {
                for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(file);
            }
        }
        context.complete();
    }

    // ------------------------------------------------------------------ chunks

    /**
     * A zone whose chunk is not loaded when the session begins (the players come from afar, by pipe): its entities,
     * read from disk a few ticks later, are remembered as they come; what the session spawns there before is not.
     * They used to be wiped for good at the end.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_life_far", tickLimit = 600)
    public void entitiesOfAChunkNotLoadedYetAreKept(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos far = far(context, 3000);
        ChunkPos chunk = new ChunkPos(far);
        farArea(context, far, () -> {
            UUID id = statue(world, far.add(1, 1, 1)).getUuid();
            world.setChunkForced(chunk.x, chunk.z, false);
            when(context, () -> world.getEntity(id) == null && !world.isChunkLoaded(chunk.toLong()), 300, "the far chunk never unloaded", () -> {
                ZoneBubble bubble = ZoneBubbles.begin(server, UUID.randomUUID(), zoneAt(world, far), List.of(), List.of(), ZoneBubble.Options.DEFAULT);
                context.assertTrue(bubble.isActive(), "the session begins");
                // spawned by the session before the chunk's entities are read
                CowEntity early = EntityType.COW.create(world, net.minecraft.entity.SpawnReason.COMMAND);
                early.refreshPositionAndAngles(far.getX() + 3.5, far.getY(), far.getZ() + 3.5, 0, 0);
                world.spawnEntity(early);
                when(context, () -> world.getEntity(id) != null, 100, "the entities of the zone never came", () -> later(context, 1, () -> {
                    world.getEntity(id).discard();
                    CowEntity cow = EntityType.COW.create(world, net.minecraft.entity.SpawnReason.COMMAND);
                    cow.refreshPositionAndAngles(far.getX() + 2.5, far.getY(), far.getZ() + 2.5, 0, 0);
                    world.spawnEntity(cow);
                    bubble.endNow();
                    context.assertTrue(world.getEntity(id) instanceof ArmorStandEntity back && "statue".equals(back.getCustomName().getString()),
                            "the statue of the arena is back");
                    context.assertTrue(cow.isRemoved() && early.isRemoved(), "the cows of the session are gone");
                    world.getEntity(id).discard();
                    farDone(context);
                }));
            });
        });
    }

    /**
     * The server crashes during a session far from everyone; when it starts again, the entities the session left are
     * only read from disk a few ticks after the zone is put back: they are waited for, and wiped. They used to stay
     * (what the session dropped became loot of the real world).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_life_far_crash", tickLimit = 900)
    public void aRecoveryWaitsForTheEntitiesOfItsZone(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos far = far(context, 3400);
        ChunkPos chunk = new ChunkPos(far);
        farArea(context, far, () -> {
            UUID statue = statue(world, far.add(1, 1, 1)).getUuid();
            server.saveAll(true, false, false);
            ZoneBubble bubble = ZoneBubbles.begin(server, UUID.randomUUID(), zoneAt(world, far), List.of(), List.of(), ZoneBubble.Options.DEFAULT);
            context.assertTrue(bubble.isActive(), "the session begins");
            world.setChunkForced(chunk.x, chunk.z, false);
            world.getEntity(statue).discard();
            CowEntity cow = EntityType.COW.create(world, net.minecraft.entity.SpawnReason.COMMAND);
            cow.refreshPositionAndAngles(far.getX() + 2.5, far.getY(), far.getZ() + 2.5, 0, 0);
            world.spawnEntity(cow);
            ItemEntity loot = new ItemEntity(world, far.getX() + 1.5, far.getY(), far.getZ() + 2.5, new ItemStack(Items.DIAMOND, 64));
            world.spawnEntity(loot);
            UUID cowId = cow.getUuid(), lootId = loot.getUuid();
            later(context, 2, () -> {
                ZoneBubbles.simulateCrash();
                when(context, () -> world.getEntity(cowId) == null && !world.isChunkLoaded(chunk.toLong()), 300, "the far chunk never unloaded", () -> {
                    ZoneBubbles.recover(server);
                    when(context, () -> ZoneBubbles.all().isEmpty(), 300, "the zone was never put back", () -> later(context, 40, () -> {
                        List<String> left = new ArrayList<>();
                        for (Entity entity : world.getOtherEntities(null, zoneAt(world, far).bounds(), entity -> true)) left.add(entity.getType() + " " + entity.getUuid());
                        context.assertTrue(world.getEntity(cowId) == null && world.getEntity(lootId) == null, "what the session left is gone, found " + left);
                        Entity back = world.getEntity(statue);
                        context.assertTrue(back instanceof ArmorStandEntity, "the statue of the arena is back, found " + left);
                        back.discard();
                        farDone(context);
                    }));
                });
            });
        });
    }

    /** Two zones side by side in one chunk: the end of one does not let go of the chunk the other plays in. */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_life_far_pair", tickLimit = 400)
    public void zonesSideBySideKeepTheirChunk(TestContext context) {
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        BlockPos far = far(context, 3800);
        ChunkPos chunk = new ChunkPos(far);
        farArea(context, far, () -> {
            UUID statue = statue(world, far.add(7, 1, 1)).getUuid();
            ZoneBubble first = ZoneBubbles.begin(server, UUID.randomUUID(), zoneAt(world, far), List.of(), List.of(), ZoneBubble.Options.DEFAULT);
            ZoneBubble second = ZoneBubbles.begin(server, UUID.randomUUID(), zoneAt(world, far.add(6, 0, 0)), List.of(), List.of(), ZoneBubble.Options.DEFAULT);
            context.assertTrue(first.isActive() && second.isActive(), "both sessions begin");
            world.setChunkForced(chunk.x, chunk.z, false);
            first.endNow();
            later(context, 150, () -> {
                boolean loaded = world.getEntity(statue) != null;
                second.endNow();
                Entity stand = world.getEntity(statue);
                if (stand != null) stand.discard();
                context.assertTrue(loaded, "the chunk of the second zone stayed loaded through its session");
                farDone(context);
            });
        });
    }

    // ------------------------------------------------------------------ rounds and visits

    /**
     * A round of a page begins while the visits of that page, ended a moment ago, are still being put back: it begins
     * in its zone all the same (the zone is whole first). It used to be refused (a test stopped, a party played
     * without protection).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "zone_bubble_life_visits")
    public void aRoundBeginsOverVisitsBeingPutBack(TestContext context) {
        floor(context);
        ServerWorld world = context.getWorld();
        MinecraftServer server = world.getServer();
        ServerConfig config = ServerConfig.get();
        int perTick = config.miniGameBubbleRestorePerTick;
        UUID page = UUID.randomUUID();
        MiniGameZone zone = zone(context);
        MiniGamePages.update(server, MiniGamePageData.empty(page).withTexts("Life " + SERIAL.incrementAndGet(), "")
                .withZone(new PageZone(world.getRegistryKey(), zone.box())));
        ServerPlayerEntity visitor = player(context, "visit", 2.5, 1, 2.5), player = player(context, "round", 3.5, 1, 3.5);
        visitor.getInventory().setStack(0, new ItemStack(Items.DIAMOND));
        MiniGameArena arena = new MiniGameArena();
        try {
            config.miniGameBubbleRestorePerTick = 16;
            context.assertTrue(MiniGameArena.visit(server, page, visitor) && ZoneBubbles.ofPlayer(visitor) != null, "the visitor plays in the zone");
            for (int x = 1; x <= 4; x++) for (int y = 1; y <= 4; y++) for (int z = 1; z <= 4; z++) context.setBlockState(at(x, y, z), Blocks.STONE);
            ZoneBubble.Refusal refusal = arena.begin(server, page, List.of(player), List.of(), () -> true);
            context.assertTrue(refusal == ZoneBubble.Refusal.NONE, "the round begins, got " + refusal);
            ZoneBubble round = ZoneBubbles.ofPlayer(player);
            context.assertTrue(round != null && round.isActive(), "in its zone");
            context.expectBlock(Blocks.AIR, at(2, 2, 2));
            context.assertTrue(visitor.getInventory().count(Items.DIAMOND) == 1, "the visitor has what it owns");
        } finally {
            config.miniGameBubbleRestorePerTick = perTick;
            arena.end();
            for (ZoneBubble left : ZoneBubbles.all()) if (left.zone().intersects(zone)) left.endNow();
            remove(context, visitor, player);
        }
        context.complete();
    }
}
