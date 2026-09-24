package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleFlags;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import net.minecraft.text.Text;
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

    /** What the crafting grid gives (empty if no recipe matches). */
    private static ItemStack craft(TestContext context, int width, int height, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(width, height, List.of(grid));
        return context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(net.minecraft.recipe.RecipeType.CRAFTING, input, context.getWorld())
                .map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    private static ItemStack flagFromWool(TestContext context, net.minecraft.item.Item a, net.minecraft.item.Item b, net.minecraft.item.Item c) {
        return craft(context, 2, 2, new ItemStack(a), ItemStack.EMPTY, new ItemStack(b), new ItemStack(c));
    }

    /** Three wools of one colour: a flag of that dye. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void woolOfOneColourGivesThatFlag(TestContext context) {
        ItemStack flag = flagFromWool(context, Items.ORANGE_WOOL, Items.ORANGE_WOOL, Items.ORANGE_WOOL);
        context.assertTrue(flag.isOf(ModItems.FLAG), "a flag");
        context.assertTrue(FlagItem.getColor(flag) == FlagItem.dyeColor(DyeColor.ORANGE), "orange, got " + Integer.toHexString(FlagItem.getColor(flag)));
        context.complete();
    }

    /** Wools of several colours are mixed like dyes on leather armour. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void mixedWoolsMixTheirColours(TestContext context) {
        ItemStack flag = flagFromWool(context, Items.WHITE_WOOL, Items.WHITE_WOOL, Items.BLUE_WOOL);
        int expected = FlagItem.mix(FlagItem.NO_COLOR, List.of((net.minecraft.item.DyeItem) Items.WHITE_DYE,
                (net.minecraft.item.DyeItem) Items.WHITE_DYE, (net.minecraft.item.DyeItem) Items.BLUE_DYE));
        context.assertTrue(FlagItem.getColor(flag) == expected, "white, white and blue mixed, got " + Integer.toHexString(FlagItem.getColor(flag)));
        context.assertTrue(FlagItem.matchingDye(expected) == null, "a mix, not a dye colour");
        context.complete();
    }

    /** Red wool gives the classic goal-flag red (the undyed flag). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redWoolGivesTheClassicFlag(TestContext context) {
        ItemStack flag = flagFromWool(context, Items.RED_WOOL, Items.RED_WOOL, Items.RED_WOOL);
        context.assertTrue(flag.isOf(ModItems.FLAG) && FlagItem.getColor(flag) == FlagItem.NO_COLOR, "the classic red flag");
        context.complete();
    }

    /** Dyeing a flag made of wool mixes the dye with the wool's colour (not with the classic red). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dyeingAWoolFlagMixesWithItsColour(TestContext context) {
        ItemStack orange = flagFromWool(context, Items.ORANGE_WOOL, Items.ORANGE_WOOL, Items.ORANGE_WOOL);
        ItemStack dyed = craft(context, 2, 1, orange, new ItemStack(Items.BLUE_DYE));
        int expected = FlagItem.mix(FlagItem.dyeColor(DyeColor.ORANGE), List.of((net.minecraft.item.DyeItem) Items.BLUE_DYE));
        context.assertTrue(FlagItem.getColor(dyed) == expected, "orange + blue, got " + Integer.toHexString(FlagItem.getColor(dyed)));
        context.assertTrue(expected != FlagItem.dyeColor(DyeColor.BLUE), "not simply blue");
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

    // ------------------------------------------------------------------ scoreboard (event driven)

    /** Places a base (and runs its end-of-tick setup now, so the test can use it right away). */
    private static GoalPoleBaseBlockEntity placeBase(TestContext context, BlockState state) {
        context.setBlockState(BASE, state);
        GoalPoleNetwork.processPending();
        return baseEntity(context);
    }

    private static ScoreboardObjective sourceObjective(TestContext context) {
        return context.getWorld().getScoreboard().getNullableObjective(baseEntity(context).getSourceObjectiveName());
    }

    private static int mirrorScore(TestContext context, String holder) {
        var score = context.getWorld().getScoreboard().getScore(net.minecraft.scoreboard.ScoreHolder.fromName(holder), objective(context));
        return score == null ? 0 : score.getScore();
    }

    /** Neither the base nor the pole ticks: they only work when something happens. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void basesAndPolesDoNotTick(TestContext context) {
        ServerWorld world = context.getWorld();
        context.assertTrue(base().getBlockEntityTicker(world, ModBlockEntities.GOAL_POLE_BASE_ENTITY) == null, "base has no ticker");
        context.assertTrue(pole(true, true).getBlockEntityTicker(world, ModBlockEntities.GOAL_POLE_ENTITY) == null, "pole has no ticker");
        context.complete();
    }

    /** A new base counts as soon as it is placed; a signal at its back pauses it (points and objective kept). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newBaseCountsAndPausesWhenPowered(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.assertTrue(base.getRedstoneMode() == GoalPoleBaseBlockEntity.RedstoneMode.PAUSE_WHEN_POWERED, "new base: the signal pauses");
        context.assertTrue(base.isActive(), "counts when placed");
        context.assertTrue(base.credit("Alex", 2, null), "point counted");
        context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
        context.assertTrue(!base.isActive(), "paused by the signal at the back");
        context.assertTrue(!base.credit("Alex", 5, null), "nothing counted while paused");
        context.assertTrue(base.getTotal() == 2, "points kept, got " + base.getTotal());
        context.assertTrue(objective(context) != null && mirrorScore(context, "Alex") == 2, "objective kept while paused");
        context.setBlockState(BASE.south(), Blocks.AIR);
        context.assertTrue(base.credit("Alex", 1, null) && base.getTotal() == 3, "counts again");
        removeBase(context);
        context.assertTrue(objective(context) == null, "objective removed with the base");
        context.complete();
    }

    /** Run-when-powered counts only with a signal at the back; ignore counts always. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void redstoneModesDecideWhenTheBaseCounts(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        base.setRedstoneMode(GoalPoleBaseBlockEntity.RedstoneMode.RUN_WHEN_POWERED);
        context.assertTrue(!base.isActive(), "run mode: idle without a signal");
        context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
        context.assertTrue(base.isActive(), "run mode: counts with a signal");
        base.setRedstoneMode(GoalPoleBaseBlockEntity.RedstoneMode.IGNORE);
        context.assertTrue(base.isActive(), "ignore: counts with a signal");
        context.setBlockState(BASE.south(), Blocks.AIR);
        context.assertTrue(base.isActive(), "ignore: counts without a signal");
        removeBase(context);
        context.complete();
    }

    /** Points are mirrored in the scoreboard, and a command changing the mirror changes the points. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void pointsAreMirroredInTheScoreboard(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        ScoreboardObjective mirror = objective(context);
        context.assertTrue(mirror != null && mirror.getCriterion() == net.minecraft.scoreboard.ScoreboardCriterion.DUMMY, "dummy mirror");
        base.credit("Alex", 2, null);
        context.assertTrue(mirrorScore(context, "Alex") == 2, "mirrored");
        var scoreboard = context.getWorld().getScoreboard();
        scoreboard.getOrCreateScore(net.minecraft.scoreboard.ScoreHolder.fromName("Alex"), mirror).setScore(7);
        context.assertTrue(base.getPoints("Alex") == 7 && base.getTotal() == 7, "command sets the points, got " + base.getTotal());
        scoreboard.getOrCreateScore(net.minecraft.scoreboard.ScoreHolder.fromName("Sam"), mirror).setScore(1);
        context.assertTrue(base.getTotal() == 8, "another holder added by a command");
        scoreboard.removeScore(net.minecraft.scoreboard.ScoreHolder.fromName("Alex"), mirror);
        context.assertTrue(base.getTotal() == 1, "removed score = 0 points, got " + base.getTotal());
        removeBase(context);
        context.complete();
    }

    /** The poles above get the total pushed at once: no waiting for a tick. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void polesFollowTheTotalAtOnce(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, false));
        context.setBlockState(BASE.up(2), pole(false, true));
        GoalPoleNetwork.processPending();
        GoalPoleBlockEntity low = poleEntity(context, BASE.up()), high = poleEntity(context, BASE.up(2));
        low.update(GoalPoleBlockEntity.Comparator.EQUAL, 3);
        high.update(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 2);
        base.credit("Alex", 2, null);
        context.assertTrue(low.getRedstoneOutput() == 0 && high.getRedstoneOutput() == 15, "2 points: only >= 2 is on");
        base.credit("Alex", 1, null);
        context.assertTrue(low.getRedstoneOutput() == 15 && low.isGoalMet(), "3 points: = 3 is on");
        context.assertTrue(low.getTotal() == 3 && high.getTotal() == 3, "poles know the total");
        base.credit("Alex", 1, null);
        context.assertTrue(low.getRedstoneOutput() == 0 && high.getRedstoneOutput() == 15, "4 points: = 3 is off again");
        removeBase(context);
        context.assertTrue(poleEntity(context, BASE.up()).getRedstoneOutput() == 0, "no base: no signal");
        context.complete();
    }

    /** Reset: the marked port (right side seen from the front) and not the others; legacy bases: any side. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void resetPortClearsThePoints(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        base.credit("Alex", 2, null);
        base.credit("Sam", 3, null);
        BlockPos other = BASE.offset(Direction.EAST);
        context.setBlockState(other, Blocks.REDSTONE_BLOCK);
        context.assertTrue(base.getTotal() == 5, "another side does not reset");
        context.setBlockState(other, Blocks.AIR);
        BlockPos marked = BASE.offset(GoalPoleBaseBlockEntity.resetSide(context.getBlockState(BASE)));
        context.setBlockState(marked, Blocks.REDSTONE_BLOCK);
        context.assertTrue(base.getTotal() == 0 && mirrorScore(context, "Sam") == 0, "the marked port resets everything");
        context.setBlockState(marked, Blocks.AIR);
        base.credit("Alex", 1, null);
        base.setResetPort(GoalPoleBaseBlockEntity.ResetPort.ANY_SIDE);
        context.setBlockState(other, Blocks.REDSTONE_BLOCK);
        context.assertTrue(base.getTotal() == 0, "any side resets in the legacy mode");
        context.setBlockState(other, Blocks.AIR);
        removeBase(context);
        context.complete();
    }

    /** A criterion source: the source objective exists, and an unknown criterion is flagged without errors. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void criterionSourceObjective(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        base.setSource(GoalPoleBaseBlockEntity.Source.CRITERION, "deathCount");
        context.assertTrue(sourceObjective(context) != null && sourceObjective(context).getCriterion().getName().equals("deathCount"), "deathCount source");
        base.setSource(GoalPoleBaseBlockEntity.Source.CRITERION, "not a criterion!");
        context.assertTrue(sourceObjective(context) == null && base.isSourceInvalid(), "unknown criterion: no source, flagged");
        base.setSource(GoalPoleBaseBlockEntity.Source.LANDINGS_HERE, "");
        context.assertTrue(sourceObjective(context) == null && !base.isSourceInvalid(), "landings: no source objective");
        context.assertTrue(objective(context) != null, "the mirror stays");
        removeBase(context);
        context.complete();
    }

    /**
     * A base saved before the rewrite: it counted while powered, reset on any side, and its objective counted the
     * criterion. It keeps working the same way, and the objective's scores become its points.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyBaseIsMigrated(TestContext context) {
        var scoreboard = context.getWorld().getScoreboard();
        String name = GoalPoleBaseBlockEntity.getObjectiveName(context.getWorld(), context.getAbsolutePos(BASE));
        ScoreboardObjective old = scoreboard.addObjective(name, net.minecraft.scoreboard.ScoreboardCriterion.getOrCreateStatCriterion("deathCount").orElseThrow(),
                Text.literal("old"), net.minecraft.scoreboard.ScoreboardCriterion.RenderType.INTEGER, true, null);
        scoreboard.getOrCreateScore(net.minecraft.scoreboard.ScoreHolder.fromName("Bob"), old).setScore(4);
        net.minecraft.nbt.NbtCompound legacy = new net.minecraft.nbt.NbtCompound();
        legacy.putString("Selector", "@a");
        legacy.putString("Goal", "deathCount");
        legacy.putBoolean("ResetSidePowered", false);
        legacy.put("LastScores", new net.minecraft.nbt.NbtCompound());
        context.setBlockState(BASE, base().with(GoalPoleBaseBlock.POWERED, true));
        GoalPoleBaseBlockEntity base = baseEntity(context);
        base.read(legacy, context.getWorld().getRegistryManager());
        GoalPoleNetwork.processPending();
        context.assertTrue(base.getRedstoneMode() == GoalPoleBaseBlockEntity.RedstoneMode.RUN_WHEN_POWERED, "counts while powered, like before");
        context.assertTrue(base.getResetPort() == GoalPoleBaseBlockEntity.ResetPort.ANY_SIDE, "resets on any side, like before");
        context.assertTrue(base.getSource() == GoalPoleBaseBlockEntity.Source.CRITERION && base.getCriterion().equals("deathCount"), "same criterion");
        context.assertTrue(base.getSelector().equals("@a"), "same selector");
        context.assertTrue(base.getPoints("Bob") == 4 && base.getTotal() == 4, "old scores become points, got " + base.getTotal());
        ScoreboardObjective mirror = objective(context);
        context.assertTrue(mirror != null && mirror.getCriterion() == net.minecraft.scoreboard.ScoreboardCriterion.DUMMY, "the objective is now the dummy mirror");
        context.assertTrue(mirrorScore(context, "Bob") == 4, "mirror keeps the score");
        context.assertTrue(sourceObjective(context) != null, "source objective created");
        var saved = base.createNbt(context.getWorld().getRegistryManager());
        context.assertTrue(saved.getInt("Version") == GoalPoleBaseBlockEntity.VERSION, "saved in the new format");
        removeBase(context);
        context.complete();
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
     * With a real player: a criterion source counts each increase of the followed player's score (not decreases,
     * not while paused), and a landing is recognised once (standing on the pole or jumping on the spot is not a new
     * landing). In its own batch so that no other test runs alongside.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "goal_pole_player", tickLimit = 120)
    public void criterionIncreasesAndLandingsWithAPlayer(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        String name = player.getGameProfile().getName();
        GoalPoleBaseBlockEntity base;
        try {
            base = placeBase(context, base());
            base.setSelector(name);
            base.setSource(GoalPoleBaseBlockEntity.Source.CRITERION, "dummy");
            var scoreboard = context.getWorld().getScoreboard();
            ScoreboardObjective src = sourceObjective(context);
            scoreboard.getOrCreateScore(player, src).setScore(2);
            context.assertTrue(base.getPoints(name) == 2, "+2 counted, got " + base.getPoints(name));
            scoreboard.getOrCreateScore(player, src).setScore(5);
            context.assertTrue(base.getPoints(name) == 5, "+3 counted");
            scoreboard.getOrCreateScore(player, src).setScore(1);
            context.assertTrue(base.getPoints(name) == 5, "a decrease is not taken off");
            scoreboard.getOrCreateScore(player, src).setScore(3);
            context.assertTrue(base.getPoints(name) == 7, "+2 from the new low, got " + base.getPoints(name));
            context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
            scoreboard.getOrCreateScore(player, src).setScore(10);
            context.assertTrue(base.getPoints(name) == 7, "paused: not counted");
            context.setBlockState(BASE.south(), Blocks.AIR);
            base.setSelector("SomeoneElse");
            scoreboard.getOrCreateScore(player, src).setScore(11);
            context.assertTrue(base.getPoints(name) == 7, "a player the base does not follow does not count");

            context.setBlockState(BASE.up(), pole(true, true));
            GoalPoleNetwork.processPending();
            GoalPoleBlockEntity pole = poleEntity(context, BASE.up());
            context.assertTrue(pole.onPlayerTouch(player), "first touch: a landing");
            context.assertTrue(!pole.onPlayerTouch(player), "still standing: not a new landing");
        } catch (RuntimeException e) {
            context.getWorld().getServer().getPlayerManager().remove(player);
            throw e;
        }
        GoalPoleBaseBlockEntity finalBase = base;
        context.waitAndRun(45, () -> {
            boolean again = poleEntity(context, BASE.up()).onPlayerTouch(player);
            context.getWorld().getServer().getPlayerManager().remove(player);
            removeBase(context);
            context.assertTrue(again, "back after 2 s away: a new landing");
            context.assertTrue(finalBase.isRemoved(), "base removed");
            context.complete();
        });
    }

    /**
     * Each base counts the landings on its own poles only (two boards in one world no longer share landings); a base
     * set to the old global criterion still counts landings on any pole; a paused base counts nothing.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "goal_pole_player_boards", tickLimit = 60)
    public void eachBaseCountsLandingsOnItsOwnPoles(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        String name = player.getGameProfile().getName();
        BlockPos otherBase = BASE.east(3);
        try {
            GoalPoleBaseBlockEntity a = placeBase(context, base());
            context.setBlockState(otherBase, base());
            context.setBlockState(BASE.up(), pole(true, true));
            context.setBlockState(otherBase.up(), pole(true, true));
            GoalPoleNetwork.processPending();
            GoalPoleBaseBlockEntity b = (GoalPoleBaseBlockEntity) context.getWorld().getBlockEntity(context.getAbsolutePos(otherBase));
            a.setSelector("@a");
            b.setSelector("@a");
            context.assertTrue(a.getSource() == GoalPoleBaseBlockEntity.Source.LANDINGS_HERE, "new bases count their own landings");
            GoalPoleBlockEntity poleA = poleEntity(context, BASE.up()), poleB = poleEntity(context, otherBase.up());

            GoalPoleNetwork.onLanding(poleA, player);
            context.assertTrue(a.getPoints(name) == 1 && b.getPoints(name) == 0, "landing on A counts for A only");
            GoalPoleNetwork.onLanding(poleB, player);
            context.assertTrue(a.getPoints(name) == 1 && b.getPoints(name) == 1, "landing on B counts for B only");

            // B follows the old global criterion: a landing on A counts for both
            b.setSource(GoalPoleBaseBlockEntity.Source.CRITERION, fr.lordfinn.steveparty.criteria.ModScoreboardCriteria.LANDED_ON_POLE_ID);
            poleA.onPlayerArrive(player, context.getWorld(), poleA.getPos());
            context.assertTrue(a.getPoints(name) == 2, "A: its own landing, got " + a.getPoints(name));
            context.assertTrue(b.getPoints(name) == 2, "B: the global criterion counts a landing anywhere, got " + b.getPoints(name));

            // Paused: nothing counted
            context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
            GoalPoleNetwork.onLanding(poleA, player);
            context.assertTrue(a.getPoints(name) == 2, "paused: not counted");
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
            context.setBlockState(otherBase, Blocks.AIR);
            removeBase(context);
        }
        context.complete();
    }

    /** The base's comparator: a pulse per point by default, or the progress towards the pole's goal (0 to 15). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void baseComparatorGivesPulsesOrProgress(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, true));
        GoalPoleNetwork.processPending();
        poleEntity(context, BASE.up()).update(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 4);
        base.credit("Alex", 1, null);
        context.assertTrue(base.getComparatorOutput() == 15, "pulse mode: 15 right after a point");
        base.setOutputMode(GoalPoleBaseBlockEntity.OutputMode.PROGRESS);
        context.assertTrue(base.getComparatorOutput() == 3, "1 of 4: 15 * 1 / 4 = 3, got " + base.getComparatorOutput());
        base.credit("Alex", 1, null);
        context.assertTrue(base.getComparatorOutput() == 7, "2 of 4: 7, got " + base.getComparatorOutput());
        base.credit("Alex", 2, null);
        context.assertTrue(base.getComparatorOutput() == 15, "goal met: 15");
        removeBase(context);
        context.complete();
    }

    /** A new pole's goal is "at least 1": not reached before the first point. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newPoleGoalIsAtLeastOne(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, true));
        GoalPoleNetwork.processPending();
        GoalPoleBlockEntity pole = poleEntity(context, BASE.up());
        context.assertTrue(pole.getComparator() == GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL && pole.getValue() == 1, "at least 1");
        context.assertTrue(!pole.isGoalMet() && pole.getRedstoneOutput() == 0, "not reached at 0");
        base.credit("Alex", 1, null);
        context.assertTrue(pole.isGoalMet() && pole.getRedstoneOutput() == 15, "reached at 1");
        removeBase(context);
        context.complete();
    }

    /** One goal for the whole pole: set on any segment, every segment gets it; a new segment joins it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oneGoalForTheWholePole(TestContext context) {
        placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, false));
        context.setBlockState(BASE.up(2), pole(false, false));
        context.setBlockState(BASE.up(3), pole(false, true));
        GoalPoleNetwork.processPending();
        poleEntity(context, BASE.up(2)).applyGoal(GoalPoleBlockEntity.Comparator.EQUAL, 3, false);
        for (int y = 1; y <= 3; y++) {
            GoalPoleBlockEntity segment = poleEntity(context, BASE.up(y));
            context.assertTrue(segment.getComparator() == GoalPoleBlockEntity.Comparator.EQUAL && segment.getValue() == 3
                    && !segment.isPerSegment(), "segment " + y + " has the pole's goal");
        }
        context.setBlockState(BASE.up(4), pole(false, true));
        GoalPoleNetwork.processPending();
        GoalPoleBlockEntity added = poleEntity(context, BASE.up(4));
        context.assertTrue(added.getComparator() == GoalPoleBlockEntity.Comparator.EQUAL && added.getValue() == 3, "a new segment takes the pole's goal");
        removeBase(context);
        context.complete();
    }

    /** A goal per segment (advanced): only the edited segment changes, and the whole column is in that mode. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aGoalPerSegment(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, false));
        context.setBlockState(BASE.up(2), pole(false, true));
        GoalPoleNetwork.processPending();
        poleEntity(context, BASE.up(2)).applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 3, true);
        GoalPoleBlockEntity low = poleEntity(context, BASE.up()), high = poleEntity(context, BASE.up(2));
        context.assertTrue(low.getValue() == 1 && high.getValue() == 3, "each segment its own goal");
        context.assertTrue(low.isPerSegment() && high.isPerSegment(), "the column is in per segment mode");
        base.credit("Alex", 2, null);
        context.assertTrue(low.isGoalMet() && !high.isGoalMet(), "2 points: the low segment only");
        removeBase(context);
        context.complete();
    }

    /** Poles saved before the column setting: same goals everywhere become one goal, different goals stay per segment. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacyPoleGoalsAreMigrated(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        BlockPos[] columns = {BASE, BASE.east(3)};
        int[][] values = {{2, 2}, {2, 5}};
        for (int c = 0; c < 2; c++) {
            context.setBlockState(columns[c].up(), pole(false, false));
            context.setBlockState(columns[c].up(2), pole(false, true));
            for (int s = 0; s < 2; s++) {
                net.minecraft.nbt.NbtCompound old = new net.minecraft.nbt.NbtCompound();
                old.putInt("Comparator", GoalPoleBlockEntity.Comparator.EQUAL.ordinal());
                old.putInt("Value", values[c][s]);
                poleEntity(context, columns[c].up(s + 1)).read(old, registries);
            }
        }
        GoalPoleNetwork.processPending();
        context.assertTrue(!poleEntity(context, BASE.up()).isPerSegment() && !poleEntity(context, BASE.up(2)).isPerSegment(),
                "same old goals: one goal for the pole");
        context.assertTrue(poleEntity(context, BASE.east(3).up()).isPerSegment() && poleEntity(context, BASE.east(3).up(2)).isPerSegment(),
                "different old goals: per segment");
        context.assertTrue(poleEntity(context, BASE.east(3).up(2)).getValue() == 5, "old goals kept");
        context.assertTrue(poleEntity(context, BASE.up()).createNbt(registries).getInt("Version") == GoalPoleBlockEntity.VERSION, "saved in the new format");
        context.complete();
    }

    /** The progress above the ball needs to know whether a base is under the pole: synced, and saved. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void polesKnowWhetherABaseIsUnderThem(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, false));
        context.setBlockState(BASE.up(2), pole(false, true));
        GoalPoleNetwork.processPending();
        GoalPoleBlockEntity top = poleEntity(context, BASE.up(2));
        context.assertTrue(top.isLinked() && top.toInitialChunkDataNbt(registries).getBoolean("Linked"), "linked to the base");
        base.credit("Alex", 3, null);
        context.assertTrue(base.getPointsView().get("Alex") == 3 && top.getTotal() == 3, "points and total");
        context.assertTrue(base.toInitialChunkDataNbt(registries).getCompound("Points").getInt("Alex") == 3,
                "the points are sent to clients (wrench details)");
        removeBase(context);
        GoalPoleNetwork.processPending();
        context.assertTrue(!top.isLinked() && top.getTotal() == 0, "no base any more");
        context.complete();
    }

    /**
     * Flags of met goals rest at the bottom of the pole, stacked (11 pixels high, 1 apart), each on the flag below it
     * wherever that one is; a flag never rests above its own place.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void metFlagsRestStackedAtTheBottom(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        for (int y = 1; y <= 3; y++) context.setBlockState(BASE.up(y), pole(y == 1, y == 3).with(GoalPoleBlock.FLAG, true));
        GoalPoleNetwork.processPending();
        poleEntity(context, BASE.up()).applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 1, true);
        poleEntity(context, BASE.up(2)).applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 2, true);
        poleEntity(context, BASE.up(3)).applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 3, true);
        base.credit("Alex", 3, null);
        BlockPos.Mutable scratch = new BlockPos.Mutable();
        ServerWorld world = context.getWorld();
        float low = GoalPoleFlags.restingDrop(world, context.getAbsolutePos(BASE.up()), scratch);
        float middle = GoalPoleFlags.restingDrop(world, context.getAbsolutePos(BASE.up(2)), scratch);
        float high = GoalPoleFlags.restingDrop(world, context.getAbsolutePos(BASE.up(3)), scratch);
        context.assertTrue(low == 0f && middle == -4f && high == -8f, "stacked: 0, -4, -8 pixels, got " + low + ", " + middle + ", " + high);
        // Only the top flag down: it rests on the middle flag, which stays at its place
        base.reset();
        poleEntity(context, BASE.up(3)).applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 0, true);
        context.assertTrue(poleEntity(context, BASE.up(3)).isGoalMet() && !poleEntity(context, BASE.up(2)).isGoalMet(), "only the top met");
        high = GoalPoleFlags.restingDrop(world, context.getAbsolutePos(BASE.up(3)), scratch);
        context.assertTrue(high == -4f, "on the middle flag at its place: -4, got " + high);
        removeBase(context);
        context.complete();
    }

    /** A flag alone at the top of a tall pole slides all the way down to the bottom segment. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aLoneFlagSlidesToTheBottom(TestContext context) {
        placeBase(context, base());
        for (int y = 1; y <= 3; y++) context.setBlockState(BASE.up(y), pole(y == 1, y == 3).with(GoalPoleBlock.FLAG, y == 3));
        GoalPoleNetwork.processPending();
        float drop = GoalPoleFlags.restingDrop(context.getWorld(), context.getAbsolutePos(BASE.up(3)), new BlockPos.Mutable());
        context.assertTrue(drop == -32f, "two segments down: -32 pixels, got " + drop);
        removeBase(context);
        context.complete();
    }

    /** Reaching the goal is sent to clients (met, and when), and rings one chime for the whole pole. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reachingTheGoalIsSyncedAndRingsOnce(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        for (int y = 1; y <= 3; y++) context.setBlockState(BASE.up(y), pole(y == 1, y == 3).with(GoalPoleBlock.FLAG, true));
        GoalPoleNetwork.processPending();
        poleEntity(context, BASE.up()).applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 2, false);
        int chimes = base.getGoalChimes();
        base.credit("Alex", 1, null);
        context.assertTrue(base.getGoalChimes() == chimes, "not reached: no chime");
        base.credit("Alex", 1, null);
        context.assertTrue(base.getGoalChimes() == chimes + 1, "reached: one chime for three segments");
        GoalPoleBlockEntity top = poleEntity(context, BASE.up(3));
        var nbt = top.toInitialChunkDataNbt(registries);
        context.assertTrue(nbt.getBoolean("GoalMet") && nbt.getLong("GoalMetTick") == context.getWorld().getTime(),
                "met, and when, sent to clients");
        base.credit("Alex", 1, null);
        context.assertTrue(base.getGoalChimes() == chimes + 1, "still met: no new chime");
        base.reset();
        context.assertTrue(!top.isGoalMet() && !top.toInitialChunkDataNbt(registries).getBoolean("GoalMet"), "reset: not met");
        base.credit("Alex", 2, null);
        context.assertTrue(base.getGoalChimes() == chimes + 2, "reached again: a new chime");
        removeBase(context);
        context.complete();
    }
}
