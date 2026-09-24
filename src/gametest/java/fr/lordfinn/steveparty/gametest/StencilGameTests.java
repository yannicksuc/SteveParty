package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.TrafficSignBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignMaterial;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilCanvasBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilCanvasComponent;
import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Waterloggable;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.RecipeEntry;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Stencils, stencil maker, stencil signs, sprayed paint, stencil gun. */
public class StencilGameTests implements FabricGameTest {
    private static final BlockPos SIGN = new BlockPos(1, 2, 1);

    private static byte[] pattern(String id) {
        return StencilPatterns.byId(id).shape();
    }

    private static ItemStack stencil(String id) {
        return StencilItem.of(StencilPatterns.byId(id));
    }

    /** A survival player (uses up dyes), not added to the world. */
    private static PlayerEntity survivalPlayer(TestContext context) {
        return context.createMockPlayer(GameMode.SURVIVAL);
    }

    private static BlockHitResult hit(TestContext context, BlockPos pos, Direction side) {
        BlockPos abs = context.getAbsolutePos(pos);
        return new BlockHitResult(Vec3d.ofCenter(abs), side, abs, false);
    }

    private static <T extends net.minecraft.block.entity.BlockEntity> T at(TestContext context, BlockPos pos) {
        return context.getBlockEntity(pos);
    }

    // ---------------------------------------------------------------- library

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void libraryPatternsAreValidAndDistinct(TestContext context) {
        Set<ByteBuffer> shapes = new HashSet<>();
        for (StencilPatterns.Pattern pattern : StencilPatterns.all()) {
            byte[] shape = pattern.shape();
            context.assertTrue(StencilShape.isValid(shape), pattern.id() + " is 16x16");
            context.assertTrue(!StencilShape.isBlank(shape), pattern.id() + " is not blank");
            context.assertTrue(shapes.add(ByteBuffer.wrap(shape)), pattern.id() + " is not a copy of another pattern");
            context.assertTrue(StencilPatterns.byShape(shape) == pattern, pattern.id() + " is found back from its shape");
        }
        context.assertTrue(StencilPatterns.all().size() >= 40, "a big library");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void shapeTransformsRoundTrip(TestContext context) {
        byte[] arrow = pattern("up_right_arrow");
        byte[] turned = arrow;
        for (int i = 0; i < 4; i++) turned = StencilShape.rotateClockwise(turned);
        context.assertTrue(Arrays.equals(turned, arrow), "4 quarter turns = no turn");
        context.assertTrue(Arrays.equals(StencilShape.mirrorHorizontal(StencilShape.mirrorHorizontal(arrow)), arrow), "mirror twice");
        context.assertTrue(Arrays.equals(StencilShape.invert(StencilShape.invert(arrow)), arrow), "invert twice");
        context.assertTrue(Arrays.equals(StencilShape.rotateClockwise(pattern("up_arrow")), pattern("right_arrow")), "up arrow turned = right arrow");
        context.complete();
    }

    // ---------------------------------------------------------------- stencil maker

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilMakerTakesOneStencilOfAStack(TestContext context) {
        context.setBlockState(SIGN, ModBlocks.STENCIL_MAKER);
        PlayerEntity player = survivalPlayer(context);
        ItemStack stack = stencil("coin").copyWithCount(5);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        StencilMakerBlockEntity maker = at(context, SIGN);
        maker.swapStencil(player);
        context.assertEquals(player.getMainHandStack().getCount(), 4, "4 stencils left in hand");
        context.assertEquals(maker.getStencil().getCount(), 1, "one stencil in the maker");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilMakerDropsItsStencilHoweverItGoes(TestContext context) {
        context.setBlockState(SIGN, ModBlocks.STENCIL_MAKER);
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, stencil("coin"));
        StencilMakerBlockEntity maker = at(context, SIGN);
        maker.swapStencil(player);
        // Replaced by a command / explosion, not mined by a player
        context.setBlockState(SIGN, Blocks.AIR);
        ItemEntity drop = context.expectEntity(EntityType.ITEM, SIGN.getX(), SIGN.getY(), SIGN.getZ(), 2);
        context.assertTrue(drop.getStack().isOf(ModItems.STENCIL)
                && Arrays.equals(StencilItem.getShape(drop.getStack()), pattern("coin")), "the stencil is dropped");
        context.complete();
    }

    // ---------------------------------------------------------------- signs

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void trafficSignTakesAndGivesBackWater(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.OAK_TRAFFIC_SIGN);
        ServerWorld world = context.getWorld();
        BlockPos abs = context.getAbsolutePos(SIGN);
        BlockState state = world.getBlockState(abs);
        context.assertTrue(state.getBlock() instanceof Waterloggable, "the sign is waterloggable");
        context.assertTrue(((Waterloggable) state.getBlock()).tryFillWithFluid(world, abs, state, Fluids.WATER.getStill(false)), "bucket fills it");
        context.assertTrue(world.getBlockState(abs).get(AbstractStencilSignBlock.WATERLOGGED), "waterlogged");
        ItemStack bucket = ((Waterloggable) state.getBlock()).tryDrainFluid(null, world, abs, world.getBlockState(abs));
        context.assertTrue(bucket.isOf(Items.WATER_BUCKET) && !world.getBlockState(abs).get(AbstractStencilSignBlock.WATERLOGGED), "bucket drains it");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilAndDyePaintOnceAndDyeAloneRepaints(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.TRAFFIC_SIGN);
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, stencil("heart"));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.RED_DYE, 3));
        BlockState state = context.getBlockState(SIGN);
        state.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        TrafficSignBlockEntity sign = at(context, SIGN);
        context.assertTrue(Arrays.equals(sign.getShape(), pattern("heart")) && sign.getColor() == DyeColor.RED, "red heart painted");
        context.assertEquals(player.getOffHandStack().getCount(), 2, "one dye used");
        // Same symbol, same colour: nothing is used
        state.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertEquals(player.getOffHandStack().getCount(), 2, "no dye wasted");
        // Dye alone repaints
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BLUE_DYE, 2));
        player.setStackInHand(Hand.OFF_HAND, ItemStack.EMPTY);
        state.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertTrue(Arrays.equals(sign.getShape(), pattern("heart")) && sign.getColor() == DyeColor.BLUE, "repainted blue");
        // Stencil alone engraves
        player.setStackInHand(Hand.MAIN_HAND, stencil("coin"));
        state.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertTrue(Arrays.equals(sign.getShape(), pattern("coin")) && sign.isEngraved(), "coin engraved");
        // Wet sponge washes it off
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WET_SPONGE));
        state.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertTrue(!sign.hasShape(), "washed off");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void brokenSignKeepsItsMaterialAndSymbol(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.TRAFFIC_SIGN);
        TrafficSignBlockEntity sign = at(context, SIGN);
        Identifier birch = Registries.BLOCK.getId(Blocks.BIRCH_PLANKS);
        sign.setMaterial(birch);
        sign.setSymbol(pattern("mushroom"), DyeColor.RED);
        sign.setGlowing(true);
        ServerWorld world = context.getWorld();
        BlockPos abs = context.getAbsolutePos(SIGN);
        List<ItemStack> drops = net.minecraft.block.Block.getDroppedStacks(world.getBlockState(abs), world, abs, sign);
        context.assertEquals(drops.size(), 1, "one drop");
        ItemStack drop = drops.getFirst();
        context.assertTrue(birch.equals(drop.get(ModComponents.SIGN_MATERIAL)), "birch kept");
        StencilCanvasComponent canvas = drop.get(ModComponents.STENCIL_CANVAS);
        context.assertTrue(canvas != null && Arrays.equals(canvas.shapeArray(), pattern("mushroom"))
                && canvas.color().orElse(null) == DyeColor.RED && canvas.glowing(), "symbol kept");

        // Placing it again gives the same sign
        BlockPos other = SIGN.east(2);
        context.setBlockState(other.down(), Blocks.STONE);
        context.setBlockState(other, ModBlocks.TRAFFIC_SIGN);
        TrafficSignBlockEntity placed = at(context, other);
        placed.readComponents(drop);
        context.assertTrue(birch.equals(placed.getMaterial()) && Arrays.equals(placed.getShape(), pattern("mushroom"))
                && placed.getColor() == DyeColor.RED && placed.isGlowing(), "same sign placed again");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacySignsStillReadTheirOldData(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.OAK_TRAFFIC_SIGN);
        TrafficSignBlockEntity sign = at(context, SIGN);
        net.minecraft.nbt.NbtCompound old = new net.minecraft.nbt.NbtCompound();
        old.putByteArray("SymbolShape", pattern("left_arrow"));
        old.putString("Color", "lime");
        old.putBoolean("IsGlowing", true);
        sign.read(old, context.getWorld().getRegistryManager());
        context.assertTrue(Arrays.equals(sign.getShape(), pattern("left_arrow")) && sign.getColor() == DyeColor.LIME && sign.isGlowing(), "old data read");
        net.minecraft.nbt.NbtCompound noColor = new net.minecraft.nbt.NbtCompound();
        noColor.putByteArray("SymbolShape", pattern("left_arrow"));
        sign.read(noColor, context.getWorld().getRegistryManager());
        context.assertTrue(sign.getColor() == DyeColor.WHITE, "old signs without colour are white, as before");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void materialsComeFromTags(TestContext context) {
        context.assertTrue(SignMaterial.WOOD.accepts(Blocks.CHERRY_PLANKS) && SignMaterial.WOOD.accepts(Blocks.BAMBOO_PLANKS), "any planks");
        context.assertTrue(!SignMaterial.WOOD.accepts(Blocks.STONE), "not stone");
        context.assertTrue(SignMaterial.ROCK.accepts(Blocks.GRANITE) && SignMaterial.ROCK.accepts(Blocks.BLACKSTONE)
                && SignMaterial.ROCK.accepts(Blocks.MOSSY_COBBLESTONE), "rocks");
        context.assertTrue(!SignMaterial.ROCK.accepts(Blocks.OAK_PLANKS), "not planks");
        context.assertTrue(SignMaterial.WOOD.resolve(Identifier.of("some_removed_mod", "ghost_planks")).equals(SignMaterial.WOOD.defaultId()),
                "unknown material falls back to oak");
        context.complete();
    }

    // ---------------------------------------------------------------- recipes

    /** @return what the crafting grid gives (empty if no recipe matches). */
    private static ItemStack result(TestContext context, int width, int height, ItemStack... grid) {
        CraftingRecipeInput input = CraftingRecipeInput.create(width, height, List.of(grid));
        Optional<RecipeEntry<CraftingRecipe>> recipe = context.getWorld().getServer().getRecipeManager()
                .getFirstMatch(RecipeType.CRAFTING, input, context.getWorld());
        return recipe.map(entry -> entry.value().craft(input, context.getWorld().getRegistryManager())).orElse(ItemStack.EMPTY);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void signRecipesKeepTheirMaterial(TestContext context) {
        ItemStack p = new ItemStack(Items.CHERRY_PLANKS), s = new ItemStack(Items.STICK), e = ItemStack.EMPTY;
        ItemStack sign = result(context, 3, 3, p, p, p, p, p, p, s, e, s);
        context.assertTrue(sign.isOf(ModBlocks.TRAFFIC_SIGN.asItem()) && sign.getCount() == 2, "2 traffic signs");
        context.assertTrue(Registries.BLOCK.getId(Blocks.CHERRY_PLANKS).equals(sign.get(ModComponents.SIGN_MATERIAL)), "made of cherry");

        ItemStack b = new ItemStack(Items.BIRCH_PLANKS);
        ItemStack mixed = result(context, 3, 3, p, b, p, p, p, p, s, e, s);
        context.assertTrue(!mixed.isOf(ModBlocks.TRAFFIC_SIGN.asItem()), "no half cherry half birch sign");

        ItemStack r = new ItemStack(Items.GRANITE);
        ItemStack rock = result(context, 3, 2, e, r, e, r, r, r);
        context.assertTrue(rock.isOf(ModBlocks.ROCK_SIGN.asItem())
                && Registries.BLOCK.getId(Blocks.GRANITE).equals(rock.get(ModComponents.SIGN_MATERIAL)), "granite rock sign");

        ItemStack plastic = new ItemStack(ModBlocks.PLASTIC_BLOCKS[14]); // red
        ItemStack road = result(context, 1, 3, plastic, new ItemStack(Items.IRON_INGOT), new ItemStack(Items.IRON_INGOT));
        context.assertTrue(road.isOf(ModBlocks.PLASTIC_ROAD_SIGN.asItem()) && road.get(DataComponentTypes.BASE_COLOR) == DyeColor.RED, "red road sign");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilsAreCopiedLikeMaps(TestContext context) {
        ItemStack blank = new ItemStack(ModItems.STENCIL);
        ItemStack copies = result(context, 2, 2, stencil("boo"), blank, blank, ItemStack.EMPTY);
        context.assertTrue(copies.getCount() == 3 && Arrays.equals(StencilItem.getShape(copies), pattern("boo")), "3 boo stencils");
        ItemStack blanks = new ItemStack(Items.IRON_NUGGET);
        ItemStack made = result(context, 3, 3, ItemStack.EMPTY, blanks, ItemStack.EMPTY, blanks, new ItemStack(Items.PAPER), blanks,
                ItemStack.EMPTY, blanks, ItemStack.EMPTY);
        context.assertTrue(made.isOf(ModItems.STENCIL) && made.getCount() == 2, "blank stencils are craftable");
        context.complete();
    }

    // ---------------------------------------------------------------- sprayed paint

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sprayOnAWallThenBrushItOff(TestContext context) {
        BlockPos wall = new BlockPos(1, 2, 1);
        context.setBlockState(wall, Blocks.STONE);
        ServerWorld world = context.getWorld();
        BlockPos absWall = context.getAbsolutePos(wall);
        boolean sprayed = StencilPaintBlock.spray(world, absWall, Direction.SOUTH, pattern("creeper_face"), DyeColor.LIME, Direction.NORTH);
        context.assertTrue(sprayed, "sprayed on the wall");
        BlockPos paint = wall.south();
        context.expectBlock(ModBlocks.STENCIL_PAINT, paint);
        StencilCanvasBlockEntity canvas = at(context, paint);
        context.assertTrue(Arrays.equals(canvas.getShape(), pattern("creeper_face")) && canvas.getColor() == DyeColor.LIME, "lime creeper");
        // Nothing on a face that is not full, nor in the air
        context.assertTrue(!StencilPaintBlock.spray(world, context.getAbsolutePos(new BlockPos(3, 2, 3)), Direction.UP, pattern("coin"), DyeColor.RED, Direction.NORTH),
                "no paint in the air");

        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BRUSH));
        context.getBlockState(paint).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit(context, paint, Direction.SOUTH));
        context.expectBlock(Blocks.AIR, paint);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sprayedPaintGoesWithItsBlock(TestContext context) {
        BlockPos floor = new BlockPos(1, 1, 1);
        context.setBlockState(floor, Blocks.STONE);
        StencilPaintBlock.spray(context.getWorld(), context.getAbsolutePos(floor), Direction.UP, pattern("coin"), DyeColor.YELLOW, Direction.NORTH);
        context.expectBlock(ModBlocks.STENCIL_PAINT, floor.up());
        context.setBlockState(floor, Blocks.AIR);
        context.expectBlock(Blocks.AIR, floor.up());
        context.complete();
    }

    // ---------------------------------------------------------------- stencil gun

    private static ItemStack loadedGun() {
        ItemStack gun = new ItemStack(ModItems.STENCIL_GUN);
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < StencilGunItem.SIZE; i++) contents.add(ItemStack.EMPTY);
        contents.set(0, stencil("coin"));
        contents.set(2, stencil("power_star"));
        contents.set(StencilGunItem.STENCIL_SLOTS, new ItemStack(Items.RED_DYE, 2));
        contents.set(StencilGunItem.STENCIL_SLOTS + 3, new ItemStack(Items.BLUE_DYE, 1));
        StencilGunItem.setContents(gun, contents);
        return gun;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilGunWheelSkipsEmptySlots(TestContext context) {
        ItemStack gun = loadedGun();
        StencilGunItem.scroll(gun, false, 1);
        context.assertEquals(StencilGunItem.selection(gun).stencil(), 2, "next stencil skips the empty slot");
        StencilGunItem.scroll(gun, false, 1);
        context.assertEquals(StencilGunItem.selection(gun).stencil(), 0, "wraps around");
        StencilGunItem.scroll(gun, true, 1);
        context.assertEquals(StencilGunItem.selection(gun).dye(), 3, "next colour");
        StencilGunItem.scroll(gun, true, 1);
        context.assertEquals(StencilGunItem.selection(gun).dye(), StencilGunSelection.ENGRAVE, "then no paint");
        StencilGunItem.scroll(gun, true, 1);
        context.assertEquals(StencilGunItem.selection(gun).dye(), 0, "then back to the first colour");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilGunPaintsSignsWithItsOwnDyes(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.PLASTIC_ROAD_SIGN);
        context.setBlockState(SIGN, context.getBlockState(SIGN).with(PlasticRoadSignBlock.PLATE, PlasticRoadSignBlock.Plate.DIAMOND));
        PlayerEntity player = survivalPlayer(context);
        ItemStack gun = loadedGun();
        player.setStackInHand(Hand.MAIN_HAND, gun);
        context.getBlockState(SIGN).onUseWithItem(gun, context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        StencilCanvasBlockEntity sign = at(context, SIGN);
        context.assertTrue(Arrays.equals(sign.getShape(), pattern("coin")) && sign.getColor() == DyeColor.RED, "red coin sprayed");
        context.assertEquals(StencilGunItem.contents(player.getMainHandStack()).get(StencilGunItem.STENCIL_SLOTS).getCount(), 1, "one red dye used from the gun");
        context.complete();
    }

    // ---------------------------------------------------------------- loot

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilsAreFoundInStructureChests(TestContext context) {
        ServerWorld world = context.getWorld();
        LootTable table = world.getServer().getReloadableRegistries().getLootTable(LootTables.VILLAGE_CARTOGRAPHER_CHEST);
        LootWorldContext loot = new LootWorldContext.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(context.getAbsolutePos(BlockPos.ORIGIN)))
                .build(LootContextTypes.CHEST);
        int found = 0;
        for (long seed = 0; seed < 200; seed++) {
            for (ItemStack stack : table.generateLoot(loot, seed)) {
                if (!stack.isOf(ModItems.STENCIL)) continue;
                context.assertTrue(StencilPatterns.byShape(StencilItem.getShape(stack)) != null, "a library pattern");
                found++;
            }
        }
        context.assertTrue(found > 20, "stencils in cartographer chests (" + found + " in 200 chests)");
        context.complete();
    }
}
