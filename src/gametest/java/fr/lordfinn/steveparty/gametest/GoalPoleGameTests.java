package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleFlags;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleNetwork;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleSearch;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EndPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.criteria.ModScoreboardCriteria;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import net.minecraft.text.Text;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBaseBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlock;
import fr.lordfinn.steveparty.blocks.custom.GoalPoleBlockEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.GoalPoleBasePayload;
import fr.lordfinn.steveparty.payloads.custom.GoalPolePayload;
import net.minecraft.nbt.NbtCompound;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import fr.lordfinn.steveparty.recipes.FlagDyeRecipe;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.util.DyeColor;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Block;
import net.minecraft.block.ComparatorBlock;
import net.minecraft.block.ComposterBlock;
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
        world.updateNeighbor(abs, Blocks.AIR, abs.down());
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

    /** The extra-life golden heart never takes absorption away. */
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

    /**
     * A signal of 1 to 14 into the base pauses it, its points kept; 15 pauses it and puts the points back to 0; no
     * signal any more: it counts again. Here a comparator reading a composter (level 4) gives 4, a redstone block 15.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void aSignalPausesTheBaseAndAFullOneResetsIt(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.assertTrue(base.isActive(), "counts when placed");
        context.assertTrue(base.credit("Alex", 2, null), "point counted");
        // The comparator first: the composter put behind it then wakes it up
        context.setBlockState(BASE.south().down(), Blocks.STONE);
        context.setBlockState(BASE.south(), Blocks.COMPARATOR.getDefaultState().with(ComparatorBlock.FACING, Direction.SOUTH));
        context.setBlockState(BASE.south(2), Blocks.COMPOSTER.getDefaultState().with(ComposterBlock.LEVEL, 4));
        context.waitAndRun(4, () -> {
            context.assertTrue(base.getInputPower() == 4 && !base.isActive(), "signal 4: paused, got " + base.getInputPower());
            context.assertTrue(!base.credit("Alex", 5, null), "nothing counted while paused");
            context.assertTrue(base.getTotal() == 2 && mirrorScore(context, "Alex") == 2, "1-14: points kept, got " + base.getTotal());
            // A full signal, here on another side: paused, and back to 0
            context.setBlockState(BASE.east(), Blocks.REDSTONE_BLOCK);
            context.assertTrue(base.getInputPower() == 15 && !base.isActive(), "signal 15: paused");
            context.assertTrue(base.getTotal() == 0 && mirrorScore(context, "Alex") == 0, "signal 15: points back to 0");
            context.assertTrue(!base.credit("Alex", 1, null), "nothing counted at 15");
            context.setBlockState(BASE.east(), Blocks.AIR);
            context.assertTrue(base.getInputPower() == 4 && !base.isActive() && base.getTotal() == 0, "back to 4: still paused");
            context.setBlockState(BASE.south(), Blocks.AIR);
            context.assertTrue(base.getInputPower() == 0 && base.isActive(), "no signal: counts again");
            context.assertTrue(base.credit("Alex", 1, null) && base.getTotal() == 1, "counted again");
            context.assertTrue(objective(context) != null, "objective kept");
            removeBase(context);
            context.assertTrue(objective(context) == null, "objective removed with the base");
            context.complete();
        });
    }

    /** A full signal resets the points whatever the side; the screen's old redstone settings are not saved any more. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aFullSignalOnAnySideResets(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            base.credit("Alex", 3, null);
            context.assertTrue(base.getTotal() == 3, "points before the signal");
            context.setBlockState(BASE.offset(side), Blocks.REDSTONE_BLOCK);
            context.assertTrue(base.getTotal() == 0 && !base.isActive(), "a full signal from the " + side + ": reset and paused");
            context.setBlockState(BASE.offset(side), Blocks.AIR);
            context.assertTrue(base.isActive() && base.getTotal() == 0, "resumes at 0");
        }
        var saved = base.createNbt(context.getWorld().getRegistryManager());
        context.assertTrue(!saved.contains("RedstoneMode") && !saved.contains("OutputMode"), "no redstone mode saved");
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

    /** The poles above get the total pushed at once: no waiting for a tick (a pole counting everyone's total). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void polesFollowTheTotalAtOnce(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, false));
        context.setBlockState(BASE.up(2), pole(false, true));
        GoalPoleNetwork.processPending();
        GoalPoleBlockEntity low = poleEntity(context, BASE.up()), high = poleEntity(context, BASE.up(2));
        low.applyCount(GoalPoleBlockEntity.Count.TOTAL);
        low.update(GoalPoleBlockEntity.Comparator.EQUAL, 3);
        high.update(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 2);
        base.credit("Alex", 2, null);
        context.assertTrue(low.getRedstoneOutput() == 10 && high.getRedstoneOutput() == 15,
                "2 points: 2 of 3 (10), >= 2 reached (15), got " + low.getRedstoneOutput() + ", " + high.getRedstoneOutput());
        base.credit("Alex", 1, null);
        context.assertTrue(low.getRedstoneOutput() == 15 && low.isGoalMet(), "3 points: = 3 is reached");
        context.assertTrue(low.getTotal() == 3 && high.getTotal() == 3, "poles know the total");
        base.credit("Alex", 1, null);
        context.assertTrue(low.getRedstoneOutput() == 14 && !low.isGoalMet() && high.getRedstoneOutput() == 15,
                "4 points: = 3 is no longer reached, so never 15, got " + low.getRedstoneOutput());
        removeBase(context);
        context.assertTrue(poleEntity(context, BASE.up()).getRedstoneOutput() == 0, "no base: no signal");
        context.complete();
    }

    /** A full signal that stays resets once: a neighbour changing meanwhile resets nothing more (the mirror included). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aSteadyFullSignalResetsOnce(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        base.credit("Sam", 3, null);
        context.setBlockState(BASE.east(), Blocks.REDSTONE_BLOCK);
        context.assertTrue(base.getTotal() == 0 && mirrorScore(context, "Sam") == 0, "reset");
        // Points set by a command while paused stay, even when a neighbour changes
        var scoreboard = context.getWorld().getScoreboard();
        scoreboard.getOrCreateScore(net.minecraft.scoreboard.ScoreHolder.fromName("Sam"), objective(context)).setScore(4);
        context.setBlockState(BASE.west(), Blocks.STONE);
        context.assertTrue(base.getTotal() == 4, "a steady signal resets once, got " + base.getTotal());
        context.setBlockState(BASE.west(), Blocks.AIR);
        context.setBlockState(BASE.east(), Blocks.AIR);
        removeBase(context);
        context.complete();
    }

    /** A base saved with the removed "ResetPort" setting loads fine, and a full signal on any side resets it. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oldResetPortSettingIsIgnored(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        var registries = context.getWorld().getRegistryManager();
        net.minecraft.nbt.NbtCompound saved = base.createNbt(registries);
        saved.putString("ResetPort", "MARKED_SIDE");
        net.minecraft.nbt.NbtCompound points = new net.minecraft.nbt.NbtCompound();
        points.putInt("Alex", 3);
        saved.put("Points", points);
        base.read(saved, registries);
        context.assertTrue(base.getTotal() == 3, "points loaded, got " + base.getTotal());
        context.assertFalse(base.createNbt(registries).contains("ResetPort"), "the setting is not saved any more");
        net.minecraft.nbt.NbtCompound settings = base.writeSettings();
        settings.putString("ResetPort", "MARKED_SIDE");
        base.applySettings(settings);
        BlockPos west = BASE.offset(Direction.WEST);
        context.setBlockState(west, Blocks.REDSTONE_BLOCK);
        context.assertTrue(base.getTotal() == 0, "the left side (not the old marked one) resets");
        context.setBlockState(west, Blocks.AIR);
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
     * An objective of the server typed as the goal (playtest #73, offered as you type): the base follows it as it is
     * (no objective of its own), each increase for a followed player is a point, and the screen is told the objectives.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "goal_pole_player")
    public void anObjectiveOfTheServerCanBeTheGoal(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        String name = player.getGameProfile().getName();
        var scoreboard = context.getWorld().getScoreboard();
        String objectiveName = "jumps" + context.getAbsolutePos(BASE).getX();
        ScoreboardObjective jumps = scoreboard.addObjective(objectiveName, net.minecraft.scoreboard.ScoreboardCriterion.DUMMY, Text.literal("Jumps"),
                net.minecraft.scoreboard.ScoreboardCriterion.RenderType.INTEGER, true, null);
        try {
            GoalPoleBaseBlockEntity base = placeBase(context, base());
            base.setPlayers(GoalPoleBaseBlockEntity.Players.SELECTOR, 16);
            base.setSelector(name);
            scoreboard.getOrCreateScore(player, jumps).setScore(4);
            base.setSource(GoalPoleBaseBlockEntity.Source.CRITERION, objectiveName);
            context.assertTrue(!base.isSourceInvalid() && sourceObjective(context) == null, "followed as it is: no objective of its own");
            context.assertTrue(base.getPoints(name) == 0, "the score it had is no point");
            scoreboard.getOrCreateScore(player, jumps).setScore(7);
            context.assertTrue(base.getPoints(name) == 3, "+3 counted, got " + base.getPoints(name));
            boolean listed = false;
            for (net.minecraft.nbt.NbtElement element : base.writeSettings().getList("Objectives", net.minecraft.nbt.NbtElement.COMPOUND_TYPE)) {
                net.minecraft.nbt.NbtCompound entry = (net.minecraft.nbt.NbtCompound) element;
                listed |= entry.getString("Name").equals(objectiveName) && entry.getString("Criterion").equals("dummy");
                context.assertTrue(!entry.getString("Name").startsWith("steveparty_"), "the bases' own objectives are not offered");
            }
            context.assertTrue(listed, "the screen is told the objectives of the server");
            scoreboard.removeObjective(jumps);
            GoalPoleNetwork.processPending();
            context.assertTrue(base.isSourceInvalid(), "the objective removed: the goal is unknown");
            removeBase(context);
        } finally {
            if (scoreboard.getNullableObjective(objectiveName) != null) scoreboard.removeObjective(scoreboard.getNullableObjective(objectiveName));
            TestPlayers.remove(context, player);
        }
        context.complete();
    }

    /**
     * A base saved before the rewrite: it counted while powered, reset on any side, and its objective counted the
     * criterion. It keeps its criterion and players, the objective's scores become its points, and its back port
     * now pauses it (counting only while powered is gone).
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
        context.assertTrue(!base.isActive(), "powered: paused");
        context.assertTrue(base.getSource() == GoalPoleBaseBlockEntity.Source.CRITERION && base.getCriterion().equals("deathCount"), "same criterion");
        context.assertTrue(base.getSelector().equals("@a") && base.getPlayers() == GoalPoleBaseBlockEntity.Players.SELECTOR,
                "same selector, as the advanced choice");
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

    /** A base placed against a powered block starts powered, so paused (it used to wait for a neighbor change). */
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
        ServerPlayerEntity player = TestPlayers.mock(context);
        String name = player.getGameProfile().getName();
        GoalPoleBaseBlockEntity base;
        try {
            base = placeBase(context, base());
            base.setPlayers(GoalPoleBaseBlockEntity.Players.SELECTOR, 16);
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
            context.assertTrue(base.getPoints(name) == 0, "a full signal: back to 0 and paused, not counted");
            context.setBlockState(BASE.south(), Blocks.AIR);
            base.setSelector("SomeoneElse");
            scoreboard.getOrCreateScore(player, src).setScore(11);
            context.assertTrue(base.getPoints(name) == 0, "a player the base does not follow does not count");

            context.setBlockState(BASE.up(), pole(true, true));
            GoalPoleNetwork.processPending();
            GoalPoleBlockEntity pole = poleEntity(context, BASE.up());
            context.assertTrue(pole.onPlayerTouch(player), "first touch: a landing");
            context.assertTrue(!pole.onPlayerTouch(player), "still standing: not a new landing");
        } catch (RuntimeException e) {
            TestPlayers.remove(context, player);
            throw e;
        }
        GoalPoleBaseBlockEntity finalBase = base;
        context.waitAndRun(45, () -> {
            boolean again = poleEntity(context, BASE.up()).onPlayerTouch(player);
            TestPlayers.remove(context, player);
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
        ServerPlayerEntity player = TestPlayers.mock(context);
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
            b.setSource(GoalPoleBaseBlockEntity.Source.CRITERION, ModScoreboardCriteria.LANDED_ON_POLE_ID);
            poleA.onPlayerArrive(player, context.getWorld(), poleA.getPos());
            context.assertTrue(a.getPoints(name) == 2, "A: its own landing, got " + a.getPoints(name));
            context.assertTrue(b.getPoints(name) == 2, "B: the global criterion counts a landing anywhere, got " + b.getPoints(name));

            // Paused: nothing counted
            context.setBlockState(BASE.south(), Blocks.REDSTONE_BLOCK);
            GoalPoleNetwork.onLanding(poleA, player);
            context.assertTrue(a.getPoints(name) == 0, "a full signal: back to 0 and paused, not counted");
        } finally {
            TestPlayers.remove(context, player);
            context.setBlockState(otherBase, Blocks.AIR);
            removeBase(context);
        }
        context.complete();
    }

    /**
     * A comparator on the base gets a pulse per point; one against any segment of the pole reads the progress towards
     * the goal, 0 to 15, 15 only once it is reached.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, tickLimit = 40)
    public void basePulsesPerPointAndThePoleGivesTheProgress(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, false));
        context.setBlockState(BASE.up(2), pole(false, true).with(GoalPoleBlock.FLAG, true));
        GoalPoleNetwork.processPending();
        poleEntity(context, BASE.up()).applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 4, false);
        ServerWorld world = context.getWorld();
        BlockPos low = context.getAbsolutePos(BASE.up()), high = context.getAbsolutePos(BASE.up(2)), abs = context.getAbsolutePos(BASE);
        java.util.function.IntPredicate both = level -> world.getBlockState(low).getComparatorOutput(world, low) == level
                && world.getBlockState(high).getComparatorOutput(world, high) == level;
        context.assertTrue(both.test(0), "nothing yet: 0 on every segment");
        base.credit("Alex", 1, null);
        context.assertTrue(world.getBlockState(abs).getComparatorOutput(world, abs) == 15, "a pulse right after a point");
        context.assertTrue(both.test(3), "1 of 4: 15 * 1 / 4 = 3 on every segment");
        base.credit("Alex", 2, null);
        context.assertTrue(both.test(11), "3 of 4: 11, not 15 yet");
        context.waitAndRun(4, () -> {
            context.assertTrue(world.getBlockState(abs).getComparatorOutput(world, abs) == 0, "the pulse is over");
            base.credit("Alex", 1, null);
            context.assertTrue(both.test(15) && poleEntity(context, BASE.up(2)).isGoalMet(), "goal reached: 15");
            removeBase(context);
            context.complete();
        });
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
     * Poles saved with several flags: one flag is kept, the highest, moved to the top with its colour and facing; the
     * others drop as items (with their colour).
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void severalFlagsBecomeOneAtTheTop(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        // As loaded from the save: no neighbour update between the segments
        for (int y = 1; y <= 3; y++) {
            context.getWorld().setBlockState(context.getAbsolutePos(BASE.up(y)), pole(false, y == 3).with(GoalPoleBlock.FLAG, y < 3)
                    .with(GoalPoleBlock.FACING, y == 2 ? Direction.EAST : Direction.NORTH), Block.NOTIFY_LISTENERS);
            NbtCompound saved = new NbtCompound();
            saved.putInt("Version", GoalPoleBlockEntity.VERSION);
            if (y < 3) saved.putInt("FlagColor", FlagItem.dyeColor(y == 1 ? DyeColor.RED : DyeColor.BLUE));
            poleEntity(context, BASE.up(y)).read(saved, registries);
        }
        GoalPoleNetwork.processPending();
        BlockState top = context.getBlockState(BASE.up(3));
        context.assertTrue(top.get(GoalPoleBlock.FLAG) && top.get(GoalPoleBlock.FACING) == Direction.EAST, "the highest flag is now at the top, same facing");
        context.assertTrue(poleEntity(context, BASE.up(3)).getFlagColor() == FlagItem.dyeColor(DyeColor.BLUE), "with its colour");
        context.assertTrue(!context.getBlockState(BASE.up()).get(GoalPoleBlock.FLAG) && !context.getBlockState(BASE.up(2)).get(GoalPoleBlock.FLAG),
                "no flag lower down");
        context.waitAndRun(1, () -> {
            List<ItemEntity> flags = context.getWorld().getEntitiesByClass(ItemEntity.class,
                    new Box(context.getAbsolutePos(BASE.up(2))).expand(3), e -> e.getStack().isOf(ModItems.FLAG));
            context.assertTrue(flags.size() == 1 && FlagItem.getColor(flags.getFirst().getStack()) == FlagItem.dyeColor(DyeColor.RED),
                    "the other flag dropped, red, got " + flags.size());
            context.complete();
        });
    }

    /**
     * A segment put on a flagged top takes the flag up (colour and facing); joining two flagged poles keeps the
     * higher flag, the lower one drops.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theFlagStaysAtTheTop(TestContext context) {
        context.setBlockState(BASE.up(), pole(false, true).with(GoalPoleBlock.FLAG, true).with(GoalPoleBlock.FACING, Direction.WEST));
        poleEntity(context, BASE.up()).setFlagColor(FlagItem.dyeColor(DyeColor.LIME));
        context.setBlockState(BASE.up(2), pole(false, true));
        BlockState top = context.getBlockState(BASE.up(2));
        context.assertTrue(top.get(GoalPoleBlock.FLAG) && top.get(GoalPoleBlock.FACING) == Direction.WEST, "the flag went up");
        context.assertTrue(poleEntity(context, BASE.up(2)).getFlagColor() == FlagItem.dyeColor(DyeColor.LIME), "with its colour");
        context.assertTrue(!context.getBlockState(BASE.up()).get(GoalPoleBlock.FLAG)
                && poleEntity(context, BASE.up()).getFlagColor() == FlagItem.NO_COLOR, "nothing left below");
        // Another flagged pole above a gap, then the gap filled: one pole, the higher flag kept
        context.setBlockState(BASE.up(4), pole(false, true).with(GoalPoleBlock.FLAG, true));
        context.setBlockState(BASE.up(3), pole(false, false));
        context.assertTrue(context.getBlockState(BASE.up(4)).get(GoalPoleBlock.FLAG), "the top keeps its flag");
        for (int y = 1; y <= 3; y++) context.assertTrue(!context.getBlockState(BASE.up(y)).get(GoalPoleBlock.FLAG), "one flag only, segment " + y);
        context.waitAndRun(1, () -> {
            List<ItemEntity> flags = context.getWorld().getEntitiesByClass(ItemEntity.class,
                    new Box(context.getAbsolutePos(BASE.up(2))).expand(3), e -> e.getStack().isOf(ModItems.FLAG));
            context.assertTrue(flags.size() == 1 && FlagItem.getColor(flags.getFirst().getStack()) == FlagItem.dyeColor(DyeColor.LIME),
                    "the lower flag dropped, got " + flags.size());
            context.complete();
        });
    }

    /**
     * Any segment works on the pole's flag: dye colours it, shears take it off (it drops), a flag in hand hangs it back
     * at the top.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anySegmentDyesShearsAndHangsTheFlag(TestContext context) {
        for (int y = 1; y <= 3; y++) context.setBlockState(BASE.up(y), pole(false, y == 3).with(GoalPoleBlock.FLAG, y == 3));
        ServerWorld world = context.getWorld();
        BlockPos low = context.getAbsolutePos(BASE.up());
        BlockHitResult side = new BlockHitResult(Vec3d.ofCenter(low).add(0, 0, -0.1), Direction.NORTH, low, false);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.ORANGE_DYE));
        context.assertTrue(world.getBlockState(low).onUse(world, player, side).isAccepted(), "dye on the low segment");
        context.assertTrue(poleEntity(context, BASE.up(3)).getFlagColor() == FlagItem.dyeColor(DyeColor.ORANGE), "the flag at the top is orange");
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.SHEARS));
        context.assertTrue(world.getBlockState(low).onUse(world, player, side).isAccepted(), "shears on the low segment");
        context.assertTrue(!GoalPoleBlock.hasFlag(world, low), "the pole has no flag any more");
        context.assertTrue(!world.getBlockState(low).onUse(world, player, side).isAccepted(), "no flag: the shears do nothing");
        context.waitAndRun(1, () -> {
            List<ItemEntity> flags = world.getEntitiesByClass(ItemEntity.class, new Box(context.getAbsolutePos(BASE.up(2))).expand(3),
                    e -> e.getStack().isOf(ModItems.FLAG));
            context.assertTrue(flags.size() == 1, "one flag dropped, got " + flags.size());
            ItemStack flag = flags.getFirst().getStack().copy();
            context.assertTrue(FlagItem.getColor(flag) == FlagItem.dyeColor(DyeColor.ORANGE), "orange");
            flags.getFirst().discard();
            player.setStackInHand(Hand.MAIN_HAND, flag);
            context.assertTrue(world.getBlockState(low).onUse(world, player, side).isAccepted(), "the flag on the low segment");
            context.assertTrue(context.getBlockState(BASE.up(3)).get(GoalPoleBlock.FLAG) && !context.getBlockState(BASE.up()).get(GoalPoleBlock.FLAG),
                    "hung back at the top");
            context.assertTrue(poleEntity(context, BASE.up(3)).getFlagColor() == FlagItem.dyeColor(DyeColor.ORANGE) && player.getMainHandStack().isEmpty(),
                    "with its colour, the item used");
            context.complete();
        });
    }

    /**
     * An empty hand on a side turns the flag towards it; on the side it already faces, on the top, sneaking, or on a
     * pole without a flag: the goal. A Wrench always opens the goal; a player who may not build changes nothing.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anEmptyHandTurnsTheFlagOrOpensTheGoal(TestContext context) {
        context.setBlockState(BASE.up(), pole(false, false));
        context.setBlockState(BASE.up(2), pole(false, true).with(GoalPoleBlock.FLAG, true).with(GoalPoleBlock.FACING, Direction.NORTH));
        ServerWorld world = context.getWorld();
        BlockPos low = context.getAbsolutePos(BASE.up()), top = context.getAbsolutePos(BASE.up(2));
        BlockHitResult east = new BlockHitResult(Vec3d.ofCenter(low).add(0.1, 0, 0), Direction.EAST, low, false);
        BlockHitResult up = new BlockHitResult(Vec3d.ofCenter(top).add(0, 0.5, 0), Direction.UP, top, false);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        context.assertTrue(world.getBlockState(low).onUse(world, player, east).isAccepted(), "accepted");
        Direction turned = context.getBlockState(BASE.up(2)).get(GoalPoleBlock.FACING);
        context.assertTrue(turned == GoalPoleBlock.flagFacing(east, player) && turned != Direction.NORTH, "turned towards the east side, got " + turned);
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, ItemStack.EMPTY, east) == GoalPoleBlock.Use.GOAL, "the side it faces: the goal");
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, ItemStack.EMPTY, up) == GoalPoleBlock.Use.GOAL, "the top: the goal");
        BlockHitResult west = new BlockHitResult(Vec3d.ofCenter(low).add(-0.1, 0, 0), Direction.WEST, low, false);
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, ItemStack.EMPTY, west) == GoalPoleBlock.Use.TURN, "another side: turns");
        player.setSneaking(true);
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, ItemStack.EMPTY, west) == GoalPoleBlock.Use.GOAL, "sneaking: the goal");
        player.setSneaking(false);
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, new ItemStack(ModItems.WRENCH), west) == GoalPoleBlock.Use.GOAL, "a Wrench: the goal");
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, new ItemStack(Items.STONE), west) == GoalPoleBlock.Use.NONE, "a block: building");
        player.getAbilities().allowModifyWorld = false;
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, ItemStack.EMPTY, west) == GoalPoleBlock.Use.NONE, "may not build: nothing");
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, new ItemStack(Items.SHEARS), west) == GoalPoleBlock.Use.NONE, "may not build: no shears");
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, new ItemStack(ModItems.WRENCH), west) == GoalPoleBlock.Use.GOAL, "may not build: the Wrench still shows the goal");
        player.getAbilities().allowModifyWorld = true;
        context.setBlockState(BASE.up(2), pole(false, true));
        context.assertTrue(GoalPoleBlock.useOf(world, top, player, ItemStack.EMPTY, west) == GoalPoleBlock.Use.GOAL, "no flag: the goal");
        context.complete();
    }

    /** No flag, no goal: nothing shows above the top of a pole without a flag; with it, the goal shows there only. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void noFlagNoGoalShown(TestContext context) {
        context.setBlockState(BASE.up(), pole(false, false));
        context.setBlockState(BASE.up(2), pole(false, true));
        context.assertTrue(!GoalPoleBlock.showsGoal(context.getBlockState(BASE.up(2))), "no flag: nothing shown");
        context.setBlockState(BASE.up(2), pole(false, true).with(GoalPoleBlock.FLAG, true));
        context.assertTrue(GoalPoleBlock.showsGoal(context.getBlockState(BASE.up(2))), "a flag: the goal shows at the top");
        context.assertTrue(!GoalPoleBlock.showsGoal(context.getBlockState(BASE.up())), "never lower down");
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

    /** Reaching the goal is sent to clients (met, and when), and rings one chime for the whole pole (counting the total). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void reachingTheGoalIsSyncedAndRingsOnce(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        for (int y = 1; y <= 3; y++) context.setBlockState(BASE.up(y), pole(y == 1, y == 3).with(GoalPoleBlock.FLAG, y == 3));
        GoalPoleNetwork.processPending();
        poleEntity(context, BASE.up()).applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 2, false);
        poleEntity(context, BASE.up()).applyCount(GoalPoleBlockEntity.Count.TOTAL);
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

    /**
     * Which players a base follows, in plain words: everyone (new bases), the players near the base, an advanced
     * selector; the party's players when no party is running: nobody.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "goal_pole_player_modes", tickLimit = 40)
    public void playersInPlainWords(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        String name = player.getGameProfile().getName();
        try {
            GoalPoleBaseBlockEntity base = placeBase(context, base());
            context.assertTrue(base.getPlayers() == GoalPoleBaseBlockEntity.Players.ALL && base.follows(player), "new base: everyone");
            Vec3d center = Vec3d.ofCenter(context.getAbsolutePos(BASE));
            base.setPlayers(GoalPoleBaseBlockEntity.Players.RADIUS, 4);
            player.refreshPositionAndAngles(center.x + 2, center.y, center.z, 0, 0);
            context.assertTrue(base.follows(player), "2 blocks away: within 4");
            player.refreshPositionAndAngles(center.x + 20, center.y, center.z, 0, 0);
            context.assertTrue(!base.follows(player), "20 blocks away: not within 4");
            base.setPlayers(GoalPoleBaseBlockEntity.Players.RADIUS, 1000);
            context.assertTrue(base.getRadius() == GoalPoleBaseBlockEntity.MAX_RADIUS, "distance capped");
            base.setPlayers(GoalPoleBaseBlockEntity.Players.SELECTOR, 16);
            // (by selector: other tests' mock players may share this player's name)
            base.setSelector("@a");
            context.assertTrue(base.follows(player), "advanced: @a");
            base.setSelector("@a[tag=steveparty_nobody]");
            context.assertTrue(!base.follows(player), "advanced: a selector without this player");
            base.setPlayers(GoalPoleBaseBlockEntity.Players.PARTY, 16);
            context.assertTrue(base.linkedParty() == null && !base.follows(player), "party players without a party: nobody");
            // Settings from the screen
            net.minecraft.nbt.NbtCompound settings = base.writeSettings();
            settings.putString("Players", "RADIUS");
            settings.putInt("Radius", 7);
            base.applySettings(settings);
            context.assertTrue(base.getPlayers() == GoalPoleBaseBlockEntity.Players.RADIUS && base.getRadius() == 7, "from the screen");
            removeBase(context);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /**
     * The party link: placed near a party controller, a base follows the party's players; when that party starts,
     * its points go back to 0 (a base following everyone keeps its points).
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "goal_pole_player_party", tickLimit = 40)
    public void partyLinkFollowsThePartyAndResetsAtItsStart(TestContext context) {
        ServerPlayerEntity player = TestPlayers.mock(context);
        BlockPos controllerPos = BASE.east(4);
        BlockPos otherBase = BASE.west(2);
        try {
            context.setBlockState(controllerPos.down(), Blocks.STONE);
            context.setBlockState(controllerPos, ModBlocks.PARTY_CONTROLLER);
            PartyControllerEntity controller = context.getBlockEntity(controllerPos);
            GoalPoleBaseBlockEntity base = placeBase(context, base());
            base.onPlacedByPlayer();
            context.assertTrue(base.getPlayers() == GoalPoleBaseBlockEntity.Players.PARTY && base.linkedParty() == controller,
                    "placed near a party controller: the party's players");
            context.setBlockState(otherBase, base());
            GoalPoleNetwork.processPending();
            GoalPoleBaseBlockEntity everyone = (GoalPoleBaseBlockEntity) context.getWorld().getBlockEntity(context.getAbsolutePos(otherBase));
            context.assertTrue(everyone.getPlayers() == GoalPoleBaseBlockEntity.Players.ALL, "set by a command: everyone");

            context.assertTrue(!base.follows(player), "no party running: nobody");
            PartyData data = new PartyData();
            data.addStep(new PartyStep());
            data.addStep(new EndPartyStep(new java.util.ArrayList<>()));
            controller.setPartyData(data);
            controller.nextStep();
            context.assertTrue(data.isStarted(), "party running");
            context.assertTrue(!base.follows(player), "not in the party");
            controller.addInterestedPlayer(player);
            context.assertTrue(base.follows(player), "in the party");

            base.credit("Alex", 3, null);
            everyone.credit("Alex", 3, null);
            GoalPoleNetwork.onPartyStarted(controller);
            context.assertTrue(base.getTotal() == 0, "linked base: back to 0 when the party starts");
            context.assertTrue(everyone.getTotal() == 3, "base following everyone: points kept");
            context.setBlockState(otherBase, Blocks.AIR);
            removeBase(context);
            context.complete();
        } finally {
            TestPlayers.remove(context, player);
        }
    }

    /**
     * One notch per point: the flag goes down the share of its goal reached (a setting of the whole pole, saved, sent
     * to the screen); by default it waits for the goal.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void flagStepsDownOneNotchPerPoint(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        for (int y = 1; y <= 3; y++) context.setBlockState(BASE.up(y), pole(y == 1, y == 3).with(GoalPoleBlock.FLAG, y == 3));
        GoalPoleNetwork.processPending();
        GoalPoleBlockEntity top = poleEntity(context, BASE.up(3));
        top.applyGoal(GoalPoleBlockEntity.Comparator.GREATER_OR_EQUAL, 4, false);
        BlockPos.Mutable scratch = new BlockPos.Mutable();
        BlockPos flag = context.getAbsolutePos(BASE.up(3));
        ServerWorld world = context.getWorld();
        base.credit("Alex", 2, null);
        context.assertTrue(GoalPoleFlags.drop(world, flag, scratch) == 0f, "default: up until the goal is reached");
        poleEntity(context, BASE.up()).applyFlagSteps(true);
        for (int y = 1; y <= 3; y++) context.assertTrue(poleEntity(context, BASE.up(y)).isFlagSteps(), "the whole pole, segment " + y);
        float half = GoalPoleFlags.drop(world, flag, scratch);
        context.assertTrue(half == -16f, "2 of 4: halfway down (-16 of -32), got " + half);
        base.credit("Alex", 1, null);
        float threeQuarters = GoalPoleFlags.drop(world, flag, scratch);
        context.assertTrue(threeQuarters == -24f, "3 of 4: -24, got " + threeQuarters);
        base.credit("Alex", 1, null);
        context.assertTrue(GoalPoleFlags.drop(world, flag, scratch) == -32f && top.isGoalMet(), "goal reached: at the bottom");
        context.assertTrue(top.createNbt(registries).getBoolean("FlagSteps") && top.getScreenOpeningData(null).flagSteps(),
                "saved and sent to the screen");
        context.setBlockState(BASE.up(4), pole(false, true));
        GoalPoleNetwork.processPending();
        context.assertTrue(poleEntity(context, BASE.up(4)).isFlagSteps(), "a new segment takes the pole's setting");
        removeBase(context);
        context.complete();
    }

    /**
     * The screens of a base and of a pole open for anyone holding a Wrench, but what they send back only changes
     * them for a player who may build there: an adventure player (a party's players) neither resets the points nor
     * picks the players counted, nor changes the goal.
     */
    @GameTest(templateName = EMPTY_STRUCTURE, batchId = "goal_pole_player_rights")
    public void onlyABuilderChangesTheSettingsFromTheScreens(TestContext context) {
        GoalPoleBaseBlockEntity base = placeBase(context, base());
        context.setBlockState(BASE.up(), pole(true, true));
        GoalPoleNetwork.processPending();
        GoalPoleBlockEntity pole = poleEntity(context, BASE.up());
        BlockPos basePos = context.getAbsolutePos(BASE), polePos = context.getAbsolutePos(BASE.up());
        ServerPlayerEntity player = TestPlayers.mock(context);
        try {
            Vec3d near = basePos.toCenterPos().add(1.5, 0, 0);
            player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
            base.setSelector("@p");
            base.credit("Alex", 3, null);
            NbtCompound settings = base.writeSettings();
            settings.putString("Selector", "Someone");
            settings.putBoolean("Reset", true);
            GoalPolePayload goal = new GoalPolePayload(polePos, GoalPoleBlockEntity.Comparator.EQUAL, 7, false, false, GoalPoleBlockEntity.Count.SIDES);

            player.changeGameMode(GameMode.ADVENTURE);
            base.openScreen(player);
            new GoalPoleBasePayload(basePos, settings).handle(player);
            context.assertTrue(base.getSelector().equals("@p") && base.getPoints("Alex") == 3,
                    "adventure: the base keeps its selector and its points");
            pole.openScreen(player);
            goal.handle(player);
            context.assertTrue(pole.getValue() != 7, "adventure: the pole keeps its goal");

            player.changeGameMode(GameMode.SURVIVAL);
            base.openScreen(player);
            new GoalPoleBasePayload(basePos, settings).handle(player);
            context.assertTrue(base.getSelector().equals("Someone") && base.getPoints("Alex") == 0,
                    "survival: the settings are applied");
            pole.openScreen(player);
            goal.handle(player);
            context.assertTrue(pole.getValue() == 7, "survival: the goal is applied");
        } finally {
            player.closeHandledScreen();
            TestPlayers.remove(context, player);
        }
        removeBase(context);
        context.complete();
    }

    /** The goal search ignores case, accents, the minecraft namespace and the separators. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void goalSearchNormalizes(TestContext context) {
        context.assertTrue(GoalPoleSearch.normalize("minecraft.custom:minecraft.jump").equals("custom jump"),
                "namespace and separators: " + GoalPoleSearch.normalize("minecraft.custom:minecraft.jump"));
        context.assertTrue(GoalPoleSearch.normalize("  Blocs CASSÉS_Pierre ").equals("blocs casses pierre"),
                "case and accents: " + GoalPoleSearch.normalize("  Blocs CASSÉS_Pierre "));
        context.complete();
    }

    /** Objectives first, then the common goals, then the other statistics; the best label match first in a group. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void goalSearchRanksObjectivesThenStats(TestContext context) {
        List<GoalPoleSearch.Entry<Void>> goals = List.of(
                new GoalPoleSearch.Entry<>("sauts_equipe", "Sauts de l'équipe", "", GoalPoleSearch.GROUP_OBJECTIVE, null),
                new GoalPoleSearch.Entry<>("minecraft.custom:minecraft.jump", "Sauts", "saut sauter", GoalPoleSearch.GROUP_COMMON, null),
                new GoalPoleSearch.Entry<>("deathCount", "Morts", "mort morts", GoalPoleSearch.GROUP_COMMON, null),
                new GoalPoleSearch.Entry<>("minecraft.mined:minecraft.stone", "Pierre (minage)", "miné cassés blocs", GoalPoleSearch.GROUP_STAT, null),
                new GoalPoleSearch.Entry<>("minecraft.mined:minecraft.stone_bricks", "Pierres taillées (minage)", "miné cassés blocs", GoalPoleSearch.GROUP_STAT, null));
        List<GoalPoleSearch.Entry<Void>> found = GoalPoleSearch.search("SAUT", goals);
        context.assertTrue(found.size() == 2 && found.get(0).value().equals("sauts_equipe")
                && found.get(1).value().equals("minecraft.custom:minecraft.jump"), "objective, then stat: " + values(found));
        // In the id only (English), and through the namespace
        found = GoalPoleSearch.search("jump", goals);
        context.assertTrue(found.size() == 1 && found.get(0).label().equals("Sauts"), "by id: " + values(found));
        found = GoalPoleSearch.search("minecraft.custom:minecraft.jump", goals);
        context.assertTrue(found.size() == 1 && found.get(0).label().equals("Sauts"), "raw id: " + values(found));
        // Several words, accents and plurals: every word somewhere, the shorter label first
        found = GoalPoleSearch.search("blocs cassés pierre", goals);
        context.assertTrue(found.size() == 2 && found.get(0).label().equals("Pierre (minage)"), "words: " + values(found));
        context.assertTrue(GoalPoleSearch.search("pierre zombie", goals).isEmpty(), "a missing word finds nothing");
        // Nothing typed: objectives and common goals, in order
        found = GoalPoleSearch.search("", goals);
        context.assertTrue(found.size() == 3 && found.get(0).group() == GoalPoleSearch.GROUP_OBJECTIVE, "empty: " + values(found));
        // A label typed by hand (any case, no accents) stands for its goal; so does a raw value
        context.assertTrue(GoalPoleSearch.resolve("morts", goals).value().equals("deathCount"), "label resolves");
        context.assertTrue(GoalPoleSearch.resolve("minecraft.mined:minecraft.stone", goals).label().equals("Pierre (minage)"), "value resolves");
        context.assertTrue(GoalPoleSearch.resolve("pier", goals) == null, "a part of a label does not resolve");
        context.complete();
    }

    private static List<String> values(List<? extends GoalPoleSearch.Entry<?>> entries) {
        return entries.stream().map(GoalPoleSearch.Entry::value).toList();
    }
}
