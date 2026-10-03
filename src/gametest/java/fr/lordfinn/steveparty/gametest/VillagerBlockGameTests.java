package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.VillagerBlock;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEvents;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockUse;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerMode;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerReaction;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BellBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.entity.JukeboxBlockEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.event.GameEvent;

/**
 * The villager block's reactions, server side: what triggers which reaction, the priority and cooldown rules, sleep,
 * dance, and that it still falls and is still pushed by pistons.
 * <p>
 * Checks made in the same tick as the actions are exact; the ones that need the block to look around (players,
 * mobs) wait until the reaction has started at least once ({@link VillagerBlockEntity#timesStarted}), since other
 * tests' players may make it react to something else in between.
 */
public class VillagerBlockGameTests implements FabricGameTest {
    private static final BlockPos POS = new BlockPos(3, 2, 3);

    private static VillagerBlockEntity place(TestContext context) {
        return place(context, POS);
    }

    private static VillagerBlockEntity place(TestContext context, BlockPos pos) {
        context.setBlockState(pos.down(), Blocks.STONE);
        context.setBlockState(pos, ModBlocks.VILLAGER_BLOCK);
        VillagerBlockEntity villager = context.getBlockEntity(pos);
        context.assertTrue(villager != null, "the villager block has its block entity");
        return villager;
    }

    /** A player standing {@code offset} from the villager block, looking away from it. */
    private static ServerPlayerEntity playerAt(TestContext context, Vec3d offset, GameMode gameMode) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        player.changeGameMode(gameMode);
        BlockPos abs = context.getAbsolutePos(POS);
        player.refreshPositionAndAngles(abs.getX() + 0.5 + offset.x, abs.getY() + offset.y, abs.getZ() + 0.5 + offset.z, 0, -90);
        return player;
    }

    private static void disconnect(TestContext context, ServerPlayerEntity player) {
        context.getWorld().getServer().getPlayerManager().remove(player);
    }

    private static BlockHitResult hit(TestContext context) {
        BlockPos abs = context.getAbsolutePos(POS);
        return new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
    }

    // ---------------------------------------------------------------- right-clicks

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void itemsGetTheirOwnReaction(TestContext context) {
        context.assertEquals(VillagerBlockUse.reactionTo(ItemStack.EMPTY), VillagerReaction.POKED, "empty hand");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.EMERALD)), VillagerReaction.TRADE_HAPPY, "emerald");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.EMERALD_BLOCK)), VillagerReaction.JACKPOT, "emerald block");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.DIAMOND)), VillagerReaction.GREEDY, "diamond");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.IRON_SWORD)), VillagerReaction.WEAPON_SCARED, "sword");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.CROSSBOW)), VillagerReaction.WEAPON_SCARED, "crossbow");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.BREAD)), VillagerReaction.FED_LOVE, "bread");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.COOKED_BEEF)), VillagerReaction.FED, "steak");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.ROTTEN_FLESH)), VillagerReaction.YUCK, "rotten flesh");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.POPPY)), VillagerReaction.SNIFF_FLOWER, "poppy");
        context.assertEquals(VillagerBlockUse.reactionTo(new ItemStack(Items.STICK)), VillagerReaction.REFUSE, "stick");
        context.assertTrue(VillagerBlockUse.reactionTo(new ItemStack(Items.STONE)) == null, "a block is placed, no reaction");
        context.assertFalse(VillagerBlockUse.takesUse(new ItemStack(Items.WATER_BUCKET)), "a water bucket keeps its use");
        context.assertFalse(VillagerBlockUse.takesUse(new ItemStack(Items.STONE)), "a block is placed");
        context.assertTrue(VillagerBlockUse.takesUse(new ItemStack(Items.EMERALD)), "the emerald is admired");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rightClickResultsKeepPlacingBlocks(TestContext context) {
        VillagerBlockEntity villager = place(context);
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 0, 20), GameMode.SURVIVAL);
        BlockState state = context.getWorld().getBlockState(context.getAbsolutePos(POS));
        try {
            context.assertTrue(state.onUseWithItem(ItemStack.EMPTY, context.getWorld(), player, Hand.MAIN_HAND, hit(context))
                    == ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION, "empty hand: the block's own use (a poke)");
            context.assertTrue(state.onUseWithItem(new ItemStack(Items.STONE), context.getWorld(), player, Hand.MAIN_HAND, hit(context))
                    == ActionResult.PASS, "a block in hand is placed");
            context.assertTrue(state.onUseWithItem(new ItemStack(Items.WATER_BUCKET), context.getWorld(), player, Hand.MAIN_HAND, hit(context))
                    == ActionResult.PASS, "an unwanted item keeps its use");
            context.assertEquals(villager.getReaction(), VillagerReaction.REFUSE, "but it shakes its head");
            context.assertTrue(state.onUseWithItem(new ItemStack(Items.EMERALD), context.getWorld(), player, Hand.MAIN_HAND, hit(context))
                    .isAccepted(), "the emerald is taken (admired, not consumed)");
            context.assertEquals(villager.getReaction(), VillagerReaction.TRADE_HAPPY, "delighted by the emerald");
            context.useBlock(POS, player);
            context.assertEquals(villager.getTotalPokes(), 3, "three right-clicks counted (the block in hand was not one)");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pokeSpamAnnoysThenDizzies(TestContext context) {
        VillagerBlockEntity villager = place(context);
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 0, 20), GameMode.SURVIVAL);
        try {
            for (int i = 1; i <= 4; i++) villager.onRightClick(player, VillagerReaction.POKED);
            context.assertEquals(villager.getReaction(), VillagerReaction.POKED, "a few pokes: hmm?");
            villager.onRightClick(player, VillagerReaction.POKED);
            context.assertEquals(villager.getReaction(), VillagerReaction.ANNOYED, "5 pokes: annoyed");
            for (int i = 6; i <= 9; i++) villager.onRightClick(player, VillagerReaction.POKED);
            context.assertEquals(villager.getReaction(), VillagerReaction.ANNOYED, "still annoyed, pokes don't interrupt it");
            villager.onRightClick(player, VillagerReaction.POKED);
            context.assertEquals(villager.getReaction(), VillagerReaction.DIZZY, "10 pokes: dizzy");
            villager.onRightClick(player, VillagerReaction.POKED);
            context.assertEquals(villager.getReaction(), VillagerReaction.DIZZY, "a poke doesn't stop the dizziness");
            context.assertEquals(villager.getTotalPokes(), 11, "every click counted");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aStackOfPokesIsAnEasterEgg(TestContext context) {
        VillagerBlockEntity villager = place(context);
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 0, 20), GameMode.SURVIVAL);
        try {
            for (int i = 0; i < VillagerBlockEntity.POKES_PER_STACK; i++) villager.onRightClick(player, VillagerReaction.POKED);
            context.assertEquals(villager.getReaction(), VillagerReaction.STACK_OF_POKES, "the 64th poke");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void everyLineAndSubtitleIsTranslated(TestContext context) {
        for (String code : new String[]{"en_us", "fr_fr"}) {
            com.google.gson.JsonObject lang;
            try (java.io.InputStream in = VillagerBlockGameTests.class.getResourceAsStream("/assets/steveparty/lang/" + code + ".json")) {
                lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (java.io.IOException e) {
                throw new AssertionError(e);
            }
            for (VillagerReaction reaction : VillagerReaction.values()) {
                if (reaction.hasMessage()) {
                    context.assertTrue(lang.has(reaction.messageKey()), code + " has " + reaction.messageKey());
                }
            }
            for (net.minecraft.util.Identifier id : net.minecraft.registry.Registries.SOUND_EVENT.getIds()) {
                if (id.getNamespace().equals("steveparty") && id.getPath().startsWith("villager_block.")) {
                    context.assertTrue(lang.has("subtitles.steveparty." + id.getPath()), code + " has the subtitle of " + id);
                }
            }
        }
        context.complete();
    }

    // ---------------------------------------------------------------- priority, cooldown

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 300)
    public void priorityAndCooldownRules(TestContext context) {
        VillagerBlockEntity villager = place(context);
        context.assertTrue(villager.react(VillagerReaction.GREEDY, null), "greedy starts");
        context.assertFalse(villager.react(VillagerReaction.GREET, null), "a lower priority doesn't interrupt");
        context.assertFalse(villager.react(VillagerReaction.CURIOUS_POTION, null), "nor the same priority");
        context.assertFalse(villager.react(VillagerReaction.GREEDY, null), "greedy doesn't restart");
        context.assertTrue(villager.react(VillagerReaction.FAINT, null), "a higher priority interrupts");
        context.assertEquals(villager.getReaction(), VillagerReaction.FAINT, "fainting");
        context.assertFalse(villager.react(VillagerReaction.DIZZY, null), "nothing interrupts a faint but drama");
        context.assertTrue(villager.isCoolingDown(VillagerReaction.GREEDY), "greedy cooling down");

        VillagerBlockEntity poked = place(context, POS.east(2));
        context.assertTrue(poked.react(VillagerReaction.POKED, null), "poke");
        context.assertTrue(poked.react(VillagerReaction.POKED, null), "a poke restarts (no cooldown)");

        long greedyCooldown = VillagerReaction.GREEDY.cooldown;
        context.waitAndRun(greedyCooldown - 20, () ->
                context.assertTrue(villager.isCoolingDown(VillagerReaction.GREEDY), "greedy still cooling down"));
        context.waitAndRun(greedyCooldown + 1, () -> {
            context.assertFalse(villager.isCoolingDown(VillagerReaction.GREEDY), "greedy can play again");
            context.assertTrue(villager.getReaction() != VillagerReaction.FAINT, "the faint is over");
            context.complete();
        });
    }

    // ---------------------------------------------------------------- hits, steps

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void punchesEscalateToAFaint(TestContext context) {
        VillagerBlockEntity villager = place(context);
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 0, 20), GameMode.ADVENTURE);
        BlockPos abs = context.getAbsolutePos(POS);
        player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 2.5, 180, 30);
        context.assertTrue(VillagerBlockEvents.onPunchRequest(player, abs), "punching it in adventure mode is handled");
        context.assertEquals(villager.getReaction(), VillagerReaction.OUCH, "ouch");
        villager.onPunched(player);
        context.assertEquals(villager.timesStarted(VillagerReaction.OUCH), 1, "punches right after each other count once");
        for (int i = 1; i <= 5; i++) {
            int n = i;
            context.runAtTick(VillagerBlockEntity.PUNCH_RATE * i + 1L, () -> {
                villager.onPunched(player);
                if (n == 2) context.assertEquals(villager.getReaction(), VillagerReaction.GRUMPY, "3 punches: grumpy");
            });
        }
        context.runAtTick(VillagerBlockEntity.PUNCH_RATE * 5 + 2L, () -> {
            try {
                context.assertEquals(villager.getReaction(), VillagerReaction.FAINT, "6 punches: faints");
                ServerPlayerEntity builder = playerAt(context, new Vec3d(0, 0, 2.5), GameMode.SURVIVAL);
                context.assertFalse(VillagerBlockEvents.onPunchRequest(builder, abs), "where it can be broken, a punch breaks it as before");
                player.refreshPositionAndAngles(abs.getX() + 0.5, abs.getY(), abs.getZ() + 20, 0, 0);
                context.assertFalse(VillagerBlockEvents.onPunchRequest(player, abs), "out of reach");
                disconnect(context, builder);
            } finally {
                disconnect(context, player);
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void steppedOnAndBellyFlop(TestContext context) {
        VillagerBlockEntity villager = place(context);
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 1, 0), GameMode.SURVIVAL);
        BlockPos abs = context.getAbsolutePos(POS);
        BlockState state = context.getWorld().getBlockState(abs);
        try {
            state.getBlock().onSteppedOn(context.getWorld(), abs, state, player);
            context.assertEquals(villager.getReaction(), VillagerReaction.SQUISHED, "stepped on: squished");
            state.getBlock().onSteppedOn(context.getWorld(), abs, state, player);
            context.assertEquals(villager.timesStarted(VillagerReaction.SQUISHED), 1, "walking on it squishes it once");
            state.getBlock().onLandedUpon(context.getWorld(), state, abs, player, VillagerBlock.BELLY_FLOP_FALL + 1);
            context.assertEquals(villager.getReaction(), VillagerReaction.BELLY_FLOP, "landed on from high: belly flop");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    // ---------------------------------------------------------------- sleep

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sleepsAtNightAndWakesUp(TestContext context) {
        context.assertFalse(VillagerBlockEntity.isSleepTime(1000), "morning");
        context.assertTrue(VillagerBlockEntity.isSleepTime(13000), "night");
        context.assertTrue(VillagerBlockEntity.isSleepTime(18000 + 24000L * 7), "midnight, a week later");
        context.assertFalse(VillagerBlockEntity.isSleepTime(23500), "dawn");

        VillagerBlockEntity villager = place(context);
        villager.setMode(VillagerMode.SLEEP);
        context.assertFalse(villager.react(VillagerReaction.GREET, null), "asleep: no greeting");
        context.assertTrue(villager.react(VillagerReaction.POKED, null), "a poke wakes it up");
        context.assertEquals(villager.getReaction(), VillagerReaction.WAKE_STARTLED, "startled awake");
        context.assertEquals(villager.getMode(), VillagerMode.NONE, "awake");

        VillagerBlockEntity other = place(context, POS.east(2));
        other.setMode(VillagerMode.SLEEP);
        context.assertTrue(other.react(VillagerReaction.THUNDER_PANIC, null), "thunder wakes it up");
        context.assertEquals(other.getReaction(), VillagerReaction.THUNDER_PANIC, "panicking");
        context.assertEquals(other.getMode(), VillagerMode.NONE, "awake");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void idleBitsAreMostlyCommon(TestContext context) {
        context.assertEquals(VillagerBlockEntity.pickIdle(0, false), VillagerReaction.IDLE_BREAKDANCE, "1 %: breakdance");
        context.assertEquals(VillagerBlockEntity.pickIdle(15, false), VillagerReaction.IDLE_DISGUISE, "1 %: disguise");
        context.assertEquals(VillagerBlockEntity.pickIdle(30, false), VillagerReaction.IDLE_SPIN, "3 %: spin");
        context.assertEquals(VillagerBlockEntity.pickIdle(100, true), VillagerReaction.YAWN, "yawns at night");
        context.assertTrue(VillagerBlockEntity.pickIdle(500, false).priority == VillagerReaction.P.IDLE, "a common idle bit");
        context.complete();
    }

    // ---------------------------------------------------------------- game events

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noteBlocksBellsAndBuilding(TestContext context) {
        VillagerBlockEntity villager = place(context);
        BlockPos abs = context.getAbsolutePos(POS);
        context.getWorld().emitGameEvent(GameEvent.NOTE_BLOCK_PLAY, abs.west(3), GameEvent.Emitter.of(Blocks.NOTE_BLOCK.getDefaultState()));
        context.assertEquals(villager.getReaction(), VillagerReaction.NOTE_BOB, "bobs to the note block");

        context.setBlockState(POS.west(3), Blocks.BELL);
        ((BellBlock) Blocks.BELL).ring(context.getWorld(), context.getAbsolutePos(POS.west(3)), Direction.NORTH);
        context.assertEquals(villager.getReaction(), VillagerReaction.BELL_ALARM, "the bell: raid reflex");

        VillagerBlockEntity builderWatcher = place(context, POS.north(3));
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 0, 20), GameMode.SURVIVAL);
        try {
            BlockPos watcherAbs = context.getAbsolutePos(POS.north(3));
            context.getWorld().emitGameEvent(player, GameEvent.BLOCK_PLACE, watcherAbs.east());
            context.assertEquals(builderWatcher.getReaction(), VillagerReaction.PERSONAL_SPACE, "a block right against it");
        } finally {
            disconnect(context, player);
        }
        context.complete();
    }

    /** Played by day (see {@link #pinDay}): at night the villager block falls asleep instead of dancing. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "villager_block_day")
    public void dancesWhileTheJukeboxPlays(TestContext context) {
        pinDay(context);
        VillagerBlockEntity villager = place(context);
        context.setBlockState(POS.east(3), Blocks.JUKEBOX);
        JukeboxBlockEntity jukebox = context.getBlockEntity(POS.east(3));
        jukebox.setStack(new ItemStack(Items.MUSIC_DISC_PIGSTEP));
        // the jukebox tells the listeners around every second while it plays
        context.waitAndRun(30, () -> {
            try {
                context.assertEquals(villager.getMode(), VillagerMode.DANCE_FUNKY, "Pigstep: funky dance");
                jukebox.setStack(ItemStack.EMPTY);
                context.assertEquals(villager.getMode(), VillagerMode.NONE, "the music stops, so does the dance");
            } finally {
                unpinDay(context);
            }
            context.complete();
        });
    }

    // ---------------------------------------------------------------- senses (ticks)

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void greetsAPlayerComingClose(TestContext context) {
        VillagerBlockEntity villager = place(context);
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 0, 2.5), GameMode.SURVIVAL);
        context.addInstantFinalTask(() -> {
            context.assertTrue(villager.timesStarted(VillagerReaction.GREET) > 0, "greeted the player");
            disconnect(context, player);
        });
    }

    // ---------------------------------------------------------------- awake: by day, whatever the hour of the suite

    private static int dayTests;
    private static long hourBefore;

    /** A villager block sleeps at night: the tests of what it does awake are played by day, in a batch of their own. */
    private static void pinDay(TestContext context) {
        var world = context.getWorld();
        if (dayTests++ == 0) hourBefore = world.getTimeOfDay();
        world.setTimeOfDay(hourBefore - Math.floorMod(hourBefore, 24000L) + 1000);
    }

    /** The hour is put back once the last of those tests is over. */
    private static void unpinDay(TestContext context) {
        if (--dayTests == 0) context.getWorld().setTimeOfDay(hourBefore);
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "villager_block_day")
    public void answersHmmInTheChat(TestContext context) {
        pinDay(context);
        context.assertTrue(VillagerBlockEvents.isVillagerTalk("Hmmm?"), "hmm");
        context.assertTrue(VillagerBlockEvents.isVillagerTalk("bonjour !"), "bonjour");
        context.assertFalse(VillagerBlockEvents.isVillagerTalk("where is the shop"), "not villager talk");
        VillagerBlockEntity villager = place(context);
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 0, 6), GameMode.SURVIVAL);
        context.runAtTick(10, () -> VillagerBlockEvents.recordVillagerTalk(player));
        context.addInstantFinalTask(() -> {
            context.assertTrue(villager.timesStarted(VillagerReaction.CHAT_HMM) > 0, "answered hmm");
            unpinDay(context);
            disconnect(context, player);
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100, batchId = "villager_block_day")
    public void panicsWhenAZombieComes(TestContext context) {
        pinDay(context);
        VillagerBlockEntity villager = place(context);
        ServerPlayerEntity player = playerAt(context, new Vec3d(0, 0, 10), GameMode.CREATIVE);
        ZombieEntity zombie = context.spawnEntity(EntityType.ZOMBIE, POS.west(3));
        zombie.setAiDisabled(true);
        context.addInstantFinalTask(() -> {
            context.assertTrue(villager.timesStarted(VillagerReaction.ZOMBIE_PANIC) > 0, "panicked");
            unpinDay(context);
            zombie.discard();
            disconnect(context, player);
        });
    }

    // ---------------------------------------------------------------- still a falling block, still pushed

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void stillFallsLikeSand(TestContext context) {
        context.setBlockState(POS.down(), Blocks.STONE);
        context.setBlockState(POS.up(3), ModBlocks.VILLAGER_BLOCK);
        context.addInstantFinalTask(() -> {
            context.expectBlock(ModBlocks.VILLAGER_BLOCK, POS);
            context.assertTrue(context.getWorld().getBlockEntity(context.getAbsolutePos(POS)) instanceof VillagerBlockEntity,
                    "alive again where it landed");
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void pistonsStillPushIt(TestContext context) {
        for (int x = 0; x <= 5; x++) context.setBlockState(new BlockPos(x, 1, 3), Blocks.STONE);
        context.setBlockState(new BlockPos(1, 2, 3), Blocks.PISTON.getDefaultState().with(PistonBlock.FACING, Direction.EAST));
        context.setBlockState(new BlockPos(2, 2, 3), ModBlocks.VILLAGER_BLOCK);
        context.setBlockState(new BlockPos(0, 2, 3), Blocks.REDSTONE_BLOCK);
        context.addInstantFinalTask(() -> {
            context.expectBlock(ModBlocks.VILLAGER_BLOCK, new BlockPos(3, 2, 3));
            context.assertTrue(context.getWorld().getBlockEntity(context.getAbsolutePos(new BlockPos(3, 2, 3))) instanceof VillagerBlockEntity,
                    "alive again where it was pushed");
        });
    }
}
