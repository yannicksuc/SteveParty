package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import fr.lordfinn.steveparty.recipes.FlagDyeRecipe;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.util.DyeColor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;

import java.util.List;
import java.util.UUID;

public class GoalPoleGameTests implements FabricGameTest {
    private static final BlockPos BASE = new BlockPos(2, 1, 2);

    private static BlockState pole(boolean onBase, boolean top) {
        return ModBlocks.GOAL_POLE.getDefaultState().with(GoalPoleBlock.ON_BASE, onBase).with(GoalPoleBlock.TOP, top)
                .with(GoalPoleBlock.FLAG, false).with(GoalPoleBlock.FACING, Direction.NORTH);
    }

    /** Base facing north: its back (the counting input) is south. */
    private static BlockState base() {
        return ModBlocks.GOAL_POLE_BASE.getDefaultState().with(GoalPoleBaseBlock.FACING, Direction.NORTH);
    }

    private static GoalPoleBaseBlockEntity baseEntity(TestContext context) {
        return (GoalPoleBaseBlockEntity) context.getWorld().getBlockEntity(context.getAbsolutePos(BASE));
    }

    private static GoalPoleBlockEntity poleEntity(TestContext context, BlockPos pos) {
        return (GoalPoleBlockEntity) context.getWorld().getBlockEntity(context.getAbsolutePos(pos));
    }

    private static ScoreboardObjective objective(TestContext context) {
        BlockPos abs = context.getAbsolutePos(BASE);
        return context.getWorld().getScoreboard().getNullableObjective(GoalPoleBaseBlockEntity.getObjectiveName(context.getWorld(), abs));
    }

    /** Removes the base through the normal path, so that its objective does not outlive the test. */
    private static void removeBase(TestContext context) {
        context.setBlockState(BASE, Blocks.AIR);
    }

    // ------------------------------------------------------------------ structure

    /**
     * A neighbor update that changes both ON_BASE and TOP must keep both: the two properties used to be written by
     * two separate updates from the same old state, the second undoing the first.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void onBaseAndTopAreUpdatedTogether(TestContext context) {
        ServerWorld world = context.getWorld();
        context.setBlockState(BASE, base());
        BlockPos abs = context.getAbsolutePos(BASE.up());
        world.setBlockState(abs, pole(false, false), Block.NOTIFY_LISTENERS);
        world.updateNeighbor(abs, Blocks.AIR, null);
        BlockState state = world.getBlockState(abs);
        context.assertTrue(state.get(GoalPoleBlock.ON_BASE), "on base");
        context.assertTrue(state.get(GoalPoleBlock.TOP), "top");
        removeBase(context);
        context.complete();
    }

    /** Stacking poles: only the highest is the top; removing it makes the one below the top again. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stackedPolesKnowWhichOneIsTheTop(TestContext context) {
        context.setBlockState(BASE, base());
        context.setBlockState(BASE.up(), pole(true, true));
        context.setBlockState(BASE.up(2), pole(false, true));
        context.assertTrue(!context.getBlockState(BASE.up()).get(GoalPoleBlock.TOP), "lower pole no longer the top");
        context.assertTrue(context.getBlockState(BASE.up(2)).get(GoalPoleBlock.TOP), "upper pole is the top");
        context.setBlockState(BASE.up(2), Blocks.AIR);
        context.assertTrue(context.getBlockState(BASE.up()).get(GoalPoleBlock.TOP), "lower pole is the top again");
        context.assertTrue(context.getBlockState(BASE.up()).get(GoalPoleBlock.ON_BASE), "still on the base");
        removeBase(context);
        context.complete();
    }

    /** Breaking a middle segment disconnects the poles above from the base; putting it back reconnects them. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void polesAboveAGapLoseTheirBase(TestContext context) {
        context.setBlockState(BASE, base());
        context.setBlockState(BASE.up(), pole(true, false));
        context.setBlockState(BASE.up(2), pole(false, false));
        context.setBlockState(BASE.up(3), pole(false, true));
        GoalPoleBaseBlockEntity base = baseEntity(context);
        context.assertTrue(poleEntity(context, BASE.up(3)).getCachedBase() == base, "top pole finds the base");
        context.setBlockState(BASE.up(2), Blocks.AIR);
        context.assertTrue(poleEntity(context, BASE.up(3)).getCachedBase() == null, "no base above the gap");
        context.setBlockState(BASE.up(2), pole(false, false));
        context.assertTrue(poleEntity(context, BASE.up(3)).getCachedBase() == base, "base found again once the gap is filled");
        removeBase(context);
        context.complete();
    }

    /** Breaking a pole with a flag drops the pole and the flag. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void breakingAFlaggedPoleDropsTheFlag(TestContext context) {
        BlockPos pos = new BlockPos(2, 2, 2);
        context.setBlockState(pos, pole(false, true).with(GoalPoleBlock.FLAG, true));
        BlockPos abs = context.getAbsolutePos(pos);
        context.getWorld().breakBlock(abs, true);
        context.waitAndRun(2, () -> {
            List<ItemEntity> items = context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(abs).expand(2), e -> true);
            context.assertTrue(items.stream().anyMatch(e -> e.getStack().isOf(ModItems.FLAG)), "flag dropped");
            context.assertTrue(items.stream().anyMatch(e -> e.getStack().isOf(ModBlocks.GOAL_POLE.asItem())), "pole dropped");
            context.complete();
        });
    }

    // ------------------------------------------------------------------ flag

    /**
     * Using a flag on the top face of a pole (its ball) used to throw (UP has no horizontal rotation): the flag now
     * faces the player, like when clicking the side facing them. Placing and turning both go through it.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flagOnTheTopFaceFacesThePlayer(TestContext context) {
        BlockPos pos = new BlockPos(2, 2, 2);
        BlockPos abs = context.getAbsolutePos(pos);
        ServerWorld world = context.getWorld();
        context.setBlockState(pos, pole(false, true).with(GoalPoleBlock.FACING, Direction.SOUTH));
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setYaw(-90f); // looking east
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.FLAG, 2));
        BlockHitResult top = new BlockHitResult(Vec3d.ofCenter(abs).add(0, 0.5, 0), Direction.UP, abs, false);

        // Place: the side facing a player looking east is the west side, and the flag turns clockwise from it
        world.getBlockState(abs).onUse(world, player, top);
        BlockState placed = world.getBlockState(abs);
        context.assertTrue(placed.get(GoalPoleBlock.FLAG), "flag placed from the top face");
        context.assertTrue(placed.get(GoalPoleBlock.FACING) == Direction.NORTH, "facing north, got " + placed.get(GoalPoleBlock.FACING));
        context.assertTrue(player.getMainHandStack().getCount() == 1, "one flag used");

        // Turn: from the top again, looking north this time
        player.setYaw(180f);
        world.getBlockState(abs).onUse(world, player, top);
        context.assertTrue(world.getBlockState(abs).get(GoalPoleBlock.FACING) == Direction.WEST,
                "turned to face the player, got " + world.getBlockState(abs).get(GoalPoleBlock.FACING));
        context.complete();
    }

    // ------------------------------------------------------------------ flag colour

    private static final BlockPos FLAG_POLE = new BlockPos(2, 2, 2);

    private static BlockHitResult sideHit(TestContext context) {
        BlockPos abs = context.getAbsolutePos(FLAG_POLE);
        return new BlockHitResult(Vec3d.ofCenter(abs).add(0, 0, -0.1), Direction.NORTH, abs, false);
    }

    private static net.minecraft.util.ActionResult use(TestContext context, PlayerEntity player) {
        BlockPos abs = context.getAbsolutePos(FLAG_POLE);
        return context.getWorld().getBlockState(abs).onUse(context.getWorld(), player, sideHit(context));
    }

    private static List<ItemEntity> flagsAround(TestContext context) {
        BlockPos abs = context.getAbsolutePos(FLAG_POLE);
        return context.getWorld().getEntitiesByClass(ItemEntity.class, new Box(abs).expand(2), e -> e.getStack().isOf(ModItems.FLAG));
    }

    /** A dye on the flag colours it and is used up; the same dye again changes nothing and is not used. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dyeColoursTheFlagOnce(TestContext context) {
        context.setBlockState(FLAG_POLE, pole(false, true).with(GoalPoleBlock.FLAG, true));
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BLUE_DYE, 2));
        context.assertTrue(use(context, player).isAccepted(), "blue dye accepted");
        GoalPoleBlockEntity pole = poleEntity(context, FLAG_POLE);
        context.assertTrue(pole.getFlagColor() == FlagItem.dyeColor(DyeColor.BLUE), "flag is blue");
        context.assertTrue(player.getMainHandStack().getCount() == 1, "one dye used");

        context.assertTrue(!use(context, player).isAccepted(), "same colour: nothing happens");
        context.assertTrue(player.getMainHandStack().getCount() == 1, "no dye used for the same colour");

        // Without a flag, a dye does nothing either
        context.setBlockState(FLAG_POLE, pole(false, true));
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        context.assertTrue(!use(context, player).isAccepted(), "no flag: dye refused");
        context.assertTrue(player.getMainHandStack().getCount() == 1, "no dye used without a flag");

        // Creative players keep their dye
        context.setBlockState(FLAG_POLE, pole(false, true).with(GoalPoleBlock.FLAG, true));
        PlayerEntity creative = context.createMockPlayer(GameMode.CREATIVE);
        creative.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.LIME_DYE));
        use(context, creative);
        context.assertTrue(creative.getMainHandStack().getCount() == 1, "creative keeps the dye");
        context.complete();
    }

    /** Shears drop the flag with its colour, and the pole forgets it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shearedFlagKeepsItsColour(TestContext context) {
        context.setBlockState(FLAG_POLE, pole(false, true).with(GoalPoleBlock.FLAG, true));
        poleEntity(context, FLAG_POLE).setFlagColor(FlagItem.dyeColor(DyeColor.YELLOW));
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
        use(context, player);
        context.assertTrue(!context.getBlockState(FLAG_POLE).get(GoalPoleBlock.FLAG), "flag removed");
        context.assertTrue(poleEntity(context, FLAG_POLE).getFlagColor() == FlagItem.NO_COLOR, "pole forgot the colour");
        context.waitAndRun(1, () -> {
            List<ItemEntity> flags = flagsAround(context);
            context.assertTrue(flags.size() == 1, "one flag dropped, got " + flags.size());
            context.assertTrue(FlagItem.getColor(flags.getFirst().getStack()) == FlagItem.dyeColor(DyeColor.YELLOW), "dropped flag is yellow");
            context.complete();
        });
    }

    /** Breaking the pole drops the flag with its colour. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void brokenPoleDropsTheColouredFlag(TestContext context) {
        context.setBlockState(FLAG_POLE, pole(false, true).with(GoalPoleBlock.FLAG, true));
        poleEntity(context, FLAG_POLE).setFlagColor(FlagItem.dyeColor(DyeColor.PURPLE));
        context.getWorld().breakBlock(context.getAbsolutePos(FLAG_POLE), true);
        context.waitAndRun(2, () -> {
            List<ItemEntity> flags = flagsAround(context);
            context.assertTrue(flags.size() == 1, "one flag dropped, got " + flags.size());
            context.assertTrue(FlagItem.getColor(flags.getFirst().getStack()) == FlagItem.dyeColor(DyeColor.PURPLE), "dropped flag is purple");
            context.complete();
        });
    }

    /** Putting a coloured flag back on a pole gives the pole its colour; an undyed flag clears it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void placedFlagBringsItsColour(TestContext context) {
        context.setBlockState(FLAG_POLE, pole(false, true));
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, FlagItem.withColor(new ItemStack(ModItems.FLAG), 0x4A7BC0));
        use(context, player);
        context.assertTrue(context.getBlockState(FLAG_POLE).get(GoalPoleBlock.FLAG), "flag placed");
        context.assertTrue(poleEntity(context, FLAG_POLE).getFlagColor() == 0x4A7BC0, "colour restored");
        context.assertTrue(player.getMainHandStack().isEmpty(), "flag used");

        // Turning the flag keeps the colour
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.FLAG));
        player.setYaw(90f);
        context.getWorld().getBlockState(context.getAbsolutePos(FLAG_POLE)).onUse(context.getWorld(), player,
                new BlockHitResult(Vec3d.ofCenter(context.getAbsolutePos(FLAG_POLE)), Direction.EAST, context.getAbsolutePos(FLAG_POLE), false));
        context.assertTrue(poleEntity(context, FLAG_POLE).getFlagColor() == 0x4A7BC0, "colour kept when turning");

        // An undyed flag on a bare pole: the original red
        context.setBlockState(FLAG_POLE.east(2), pole(false, true));
        poleEntity(context, FLAG_POLE.east(2)).setFlagColor(0x123456);
        BlockPos other = context.getAbsolutePos(FLAG_POLE.east(2));
        context.getWorld().getBlockState(other).onUse(context.getWorld(), player,
                new BlockHitResult(Vec3d.ofCenter(other), Direction.NORTH, other, false));
        context.assertTrue(poleEntity(context, FLAG_POLE.east(2)).getFlagColor() == FlagItem.NO_COLOR, "undyed flag: no colour");
        context.complete();
    }

    /** The colour is saved with the pole. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flagColourIsSaved(TestContext context) {
        context.setBlockState(FLAG_POLE, pole(false, true).with(GoalPoleBlock.FLAG, true));
        GoalPoleBlockEntity pole = poleEntity(context, FLAG_POLE);
        pole.setFlagColor(FlagItem.dyeColor(DyeColor.CYAN));
        var registries = context.getWorld().getRegistryManager();
        GoalPoleBlockEntity copy = new GoalPoleBlockEntity(pole.getPos(), pole.getCachedState());
        copy.read(pole.createNbt(registries), registries);
        context.assertTrue(copy.getFlagColor() == FlagItem.dyeColor(DyeColor.CYAN), "colour read back");
        context.assertTrue(pole.toInitialChunkDataNbt(registries).getInt("FlagColor") == FlagItem.dyeColor(DyeColor.CYAN), "colour sent to clients");
        context.complete();
    }

    /** Flag + dyes in the crafting grid: one dye gives exactly the colour the pole gives, several are mixed. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void craftingDyesTheFlag(TestContext context) {
        FlagDyeRecipe recipe = new FlagDyeRecipe(CraftingRecipeCategory.MISC);
        var registries = context.getWorld().getRegistryManager();
        CraftingRecipeInput one = CraftingRecipeInput.create(2, 1, List.of(new ItemStack(ModItems.FLAG), new ItemStack(Items.GREEN_DYE)));
        context.assertTrue(recipe.matches(one, context.getWorld()), "flag + dye matches");
        ItemStack green = recipe.craft(one, registries);
        context.assertTrue(FlagItem.getColor(green) == FlagItem.dyeColor(DyeColor.GREEN), "green flag");
        CraftingRecipeInput mixed = CraftingRecipeInput.create(3, 1, List.of(new ItemStack(ModItems.FLAG), new ItemStack(Items.RED_DYE), new ItemStack(Items.YELLOW_DYE)));
        int mix = FlagItem.getColor(recipe.craft(mixed, registries));
        context.assertTrue(mix != FlagItem.NO_COLOR && FlagItem.matchingDye(mix) == null, "red + yellow is a mix, got " + Integer.toHexString(mix));
        CraftingRecipeInput noDye = CraftingRecipeInput.create(1, 1, List.of(new ItemStack(ModItems.FLAG)));
        context.assertTrue(!recipe.matches(noDye, context.getWorld()), "a flag alone does not match");
        CraftingRecipeInput twoFlags = CraftingRecipeInput.create(3, 1, List.of(new ItemStack(ModItems.FLAG), new ItemStack(ModItems.FLAG), new ItemStack(Items.RED_DYE)));
        context.assertTrue(!recipe.matches(twoFlags, context.getWorld()), "two flags do not match");
        context.complete();
    }

    // ------------------------------------------------------------------ landing reward

    /** The "1up" golden heart never takes absorption away. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void goldenHeartKeepsTheAbsorptionAlreadyThere(TestContext context) {
        PlayerEntity fresh = context.createMockPlayer(GameMode.SURVIVAL);
        GoalPoleBlockEntity.grantGoldenHeart(fresh);
        context.assertTrue(fresh.getAbsorptionAmount() == 2f, "one golden heart, got " + fresh.getAbsorptionAmount());
        // Landing again while the heart is still there: still one heart (used to drop to 0)
        GoalPoleBlockEntity.grantGoldenHeart(fresh);
        context.assertTrue(fresh.getAbsorptionAmount() == 2f, "still one golden heart, got " + fresh.getAbsorptionAmount());

        PlayerEntity golden = context.createMockPlayer(GameMode.SURVIVAL);
        golden.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 2400, 0));
        context.assertTrue(golden.getAbsorptionAmount() == 4f, "golden apple: two hearts");
        GoalPoleBlockEntity.grantGoldenHeart(golden);
        context.assertTrue(golden.getAbsorptionAmount() == 4f, "golden apple hearts kept, got " + golden.getAbsorptionAmount());
        context.complete();
    }

    // ------------------------------------------------------------------ scoreboard

    /** Remembered scores follow the score down too, and -1 is a score like any other. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void rememberedScoresFollowTheScoreDown(TestContext context) {
        context.setBlockState(BASE, base());
        GoalPoleBaseBlockEntity base = baseEntity(context);
        UUID player = UUID.randomUUID();
        context.assertTrue(base.trackScore(player, 3) == 0, "first score is the baseline");
        context.assertTrue(base.trackScore(player, 5) == 2, "gained 2");
        context.assertTrue(base.trackScore(player, 0) == 0, "reset from outside: no pulse");
        context.assertTrue(base.trackScore(player, 1) == 1, "counts again right after a reset (was blocked until > 5)");
        UUID other = UUID.randomUUID();
        context.assertTrue(base.trackScore(other, -1) == 0, "baseline -1");
        context.assertTrue(base.trackScore(other, 0) == 1, "-1 is a real score, not 'never seen'");
        removeBase(context);
        context.complete();
    }

    /**
     * Unpowered = paused: the objective is removed and must stay removed (the tick used to recreate it at once),
     * then comes back when the back is powered again.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void pausedBaseDoesNotRecreateItsObjective(TestContext context) {
        context.setBlockState(BASE, base());
        context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
        context.assertTrue(context.getBlockState(BASE).get(GoalPoleBaseBlock.POWERED), "powered from the back");
        context.assertTrue(objective(context) != null, "objective while counting");
        context.setBlockState(BASE.south(), Blocks.AIR);
        context.assertTrue(!context.getBlockState(BASE).get(GoalPoleBaseBlock.POWERED), "unpowered");
        context.assertTrue(objective(context) == null, "objective removed by the pause");
        context.waitAndRun(30, () -> {
            context.assertTrue(objective(context) == null, "still no objective while paused");
            context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
            context.assertTrue(objective(context) != null, "objective back on resume");
            removeBase(context);
            context.assertTrue(objective(context) == null, "objective removed with the base");
            context.complete();
        });
    }

    /** Changing the goal changes the objective's criterion; an unknown goal is ignored without errors. */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 100)
    public void goalChangesTheObjectiveCriterion(TestContext context) {
        context.setBlockState(BASE, base());
        context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
        GoalPoleBaseBlockEntity base = baseEntity(context);
        base.setGoal("dummy");
        context.assertTrue(objective(context) != null && objective(context).getCriterion().getName().equals("dummy"), "dummy objective");
        base.setGoal("not a criterion!");
        context.assertTrue(objective(context) == null, "no objective for an unknown goal");
        context.waitAndRun(30, () -> {
            context.assertTrue(objective(context) == null, "still none, and no error while retrying");
            base.setGoal("deathCount");
            context.assertTrue(objective(context) != null && objective(context).getCriterion().getName().equals("deathCount"), "deathCount objective");
            removeBase(context);
            context.complete();
        });
    }

    /** Two bases at the same coordinates in two dimensions get two objectives (overworld names are unchanged). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void objectiveNamesDependOnTheDimension(TestContext context) {
        BlockPos pos = new BlockPos(10, -5, 20);
        ServerWorld nether = context.getWorld().getServer().getWorld(World.NETHER);
        String overworldName = GoalPoleBaseBlockEntity.getObjectiveName(context.getWorld().getServer().getOverworld(), pos);
        context.assertTrue(overworldName.equals("steveparty_10_-5_20"), "overworld name kept, got " + overworldName);
        if (nether != null) {
            String netherName = GoalPoleBaseBlockEntity.getObjectiveName(nether, pos);
            context.assertTrue(!netherName.equals(overworldName), "nether name differs, got " + netherName);
            context.assertTrue(netherName.matches("[A-Za-z0-9_.+-]+"), "usable in commands, got " + netherName);
        }
        context.complete();
    }

    /** A base placed with its back against a powered block starts powered (it used to wait for a neighbor change). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void baseStartsPoweredWhenPlacedAgainstPower(TestContext context) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setYaw(180f); // looking north: the base faces south, its back is north
        context.setBlockState(BASE.north(), Blocks.REDSTONE_BLOCK);
        BlockPos abs = context.getAbsolutePos(BASE);
        ItemPlacementContext placement = new ItemPlacementContext(player, Hand.MAIN_HAND, new ItemStack(ModBlocks.GOAL_POLE_BASE),
                new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false));
        BlockState state = ModBlocks.GOAL_POLE_BASE.getPlacementState(placement);
        context.assertTrue(state != null && state.get(GoalPoleBaseBlock.FACING) == Direction.SOUTH, "facing south");
        context.assertTrue(state.get(GoalPoleBaseBlock.POWERED), "powered from the start");
        context.complete();
    }

    /** Comparisons of the pole. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void poleComparisons(TestContext context) {
        context.assertTrue(GoalPoleBlockEntity.compare(GoalPoleBlockEntity.Comparator.LESS, 2, 3), "2 < 3");
        context.assertTrue(!GoalPoleBlockEntity.compare(GoalPoleBlockEntity.Comparator.LESS, 3, 3), "!(3 < 3)");
        context.assertTrue(GoalPoleBlockEntity.compare(GoalPoleBlockEntity.Comparator.LESS_OR_EQUAL, 3, 3), "3 <= 3");
        context.assertTrue(GoalPoleBlockEntity.compare(GoalPoleBlockEntity.Comparator.EQUAL, 3, 3), "3 == 3");
        context.assertTrue(GoalPoleBlockEntity.compare(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 3, 3), "3 >= 3");
        context.assertTrue(!GoalPoleBlockEntity.compare(GoalPoleBlockEntity.Comparator.GREATER, 3, 3), "!(3 > 3)");
        context.assertTrue(GoalPoleBlockEntity.compare(GoalPoleBlockEntity.Comparator.GREATER, 4, 3), "4 > 3");
        context.complete();
    }

    /**
     * End to end: a pole above a counting base lights its comparator output when the tracked player's score matches.
     * Uses a server player joined to the server: in its own batch so that no other test runs alongside.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "goal_pole_player", tickLimit = 100)
    public void poleOutputFollowsTheTrackedScore(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            context.setBlockState(BASE, base());
            context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
            context.setBlockState(BASE.up(), pole(true, true));
            GoalPoleBaseBlockEntity base = baseEntity(context);
            base.update(player.getGameProfile().getName(), "dummy");
            GoalPoleBlockEntity pole = poleEntity(context, BASE.up());
            pole.update(GoalPoleBlockEntity.Comparator.EQUAL, 3);
            ScoreboardObjective objective = objective(context);
            context.assertTrue(objective != null, "objective");
            context.getWorld().getScoreboard().getOrCreateScore(player, objective).setScore(3);
        } catch (RuntimeException e) {
            context.getWorld().getServer().getPlayerManager().remove(player);
            throw e;
        }
        context.waitAndRun(3, () -> {
            GoalPoleBlockEntity pole = poleEntity(context, BASE.up());
            boolean on = pole.getRedstoneOutput() == 15;
            context.getWorld().getScoreboard().getOrCreateScore(player, objective(context)).setScore(4);
            context.waitAndRun(3, () -> {
                boolean off = poleEntity(context, BASE.up()).getRedstoneOutput() == 0;
                context.getWorld().getServer().getPlayerManager().remove(player);
                removeBase(context);
                context.assertTrue(on, "signal when the total equals 3");
                context.assertTrue(off, "no signal at 4");
                context.complete();
            });
        });
    }
}
