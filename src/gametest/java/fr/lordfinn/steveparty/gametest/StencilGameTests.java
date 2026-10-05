package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.EaselSignBlockEntity;
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
import fr.lordfinn.steveparty.items.custom.StencilHammerStrike;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilGunScreenHandler;
import fr.lordfinn.steveparty.stencil.StencilLibrary;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import net.minecraft.server.network.ServerPlayerEntity;
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
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.Items;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootContextParameterSet;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
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
    public void easelSignTakesAndGivesBackWater(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.OAK_EASEL_SIGN);
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
        context.setBlockState(SIGN, ModBlocks.EASEL_SIGN);
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, stencil("heart"));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.RED_DYE, 3));
        BlockState state = context.getBlockState(SIGN);
        state.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        EaselSignBlockEntity sign = at(context, SIGN);
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
        context.setBlockState(SIGN, ModBlocks.EASEL_SIGN);
        EaselSignBlockEntity sign = at(context, SIGN);
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
        context.setBlockState(other, ModBlocks.EASEL_SIGN);
        EaselSignBlockEntity placed = at(context, other);
        placed.readComponents(drop);
        context.assertTrue(birch.equals(placed.getMaterial()) && Arrays.equals(placed.getShape(), pattern("mushroom"))
                && placed.getColor() == DyeColor.RED && placed.isGlowing(), "same sign placed again");
        context.complete();
    }

    /** Worlds saved before the rename: the easel signs were "traffic_sign" and "<wood>_traffic_sign". */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void easelSignsSavedUnderTheirOldNamesLoad(TestContext context) {
        var registries = context.getWorld().getRegistryManager();
        Identifier old = Identifier.of("steveparty", "traffic_sign");
        context.assertTrue(Registries.BLOCK.get(old) == ModBlocks.EASEL_SIGN, "old block id");
        context.assertTrue(Registries.ITEM.get(old) == ModBlocks.EASEL_SIGN.asItem(), "old item id");
        context.assertTrue(Registries.BLOCK_ENTITY_TYPE.get(old) == fr.lordfinn.steveparty.blocks.ModBlockEntities.EASEL_SIGN_ENTITY, "old block entity id");
        context.assertTrue(Registries.BLOCK.get(Identifier.of("steveparty", "cherry_traffic_sign")) == ModBlocks.CHERRY_EASEL_SIGN, "old wood block id");
        context.assertTrue(Registries.ITEM.get(Identifier.of("steveparty", "warped_traffic_sign")) == ModBlocks.WARPED_EASEL_SIGN.asItem(), "old wood item id");
        // A chunk palette entry
        net.minecraft.nbt.NbtCompound stateNbt = new net.minecraft.nbt.NbtCompound();
        stateNbt.putString("Name", "steveparty:oak_traffic_sign");
        BlockState state = BlockState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, stateNbt).getOrThrow();
        context.assertTrue(state.isOf(ModBlocks.OAK_EASEL_SIGN), "old block state loads, got " + state);
        // A saved stack
        net.minecraft.nbt.NbtCompound stackNbt = new net.minecraft.nbt.NbtCompound();
        stackNbt.putString("id", "steveparty:traffic_sign");
        stackNbt.putInt("count", 3);
        ItemStack stack = ItemStack.fromNbt(registries, stackNbt).orElse(ItemStack.EMPTY);
        context.assertTrue(stack.isOf(ModBlocks.EASEL_SIGN.asItem()) && stack.getCount() == 3, "old stack loads, got " + stack);
        // A saved block entity, with its symbol
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.EASEL_SIGN);
        EaselSignBlockEntity sign = at(context, SIGN);
        sign.setSymbol(pattern("heart"), DyeColor.RED);
        net.minecraft.nbt.NbtCompound saved = sign.createNbtWithIdentifyingData(registries);
        saved.putString("id", "steveparty:traffic_sign");
        BlockPos abs = context.getAbsolutePos(SIGN);
        var loaded = net.minecraft.block.entity.BlockEntity.createFromNbt(abs, context.getWorld().getBlockState(abs), saved, registries);
        context.assertTrue(loaded instanceof EaselSignBlockEntity easel && Arrays.equals(easel.getShape(), pattern("heart"))
                && easel.getColor() == DyeColor.RED, "old block entity loads with its symbol");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void legacySignsStillReadTheirOldData(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.OAK_EASEL_SIGN);
        EaselSignBlockEntity sign = at(context, SIGN);
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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void cutOutPanelsAreCutWithAnAxe(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.OAK_FENCE);
        context.setBlockState(SIGN, ModBlocks.WOODEN_CUTOUT_PANEL);
        PlayerEntity player = survivalPlayer(context);
        StencilCanvasBlockEntity panel = at(context, SIGN);
        // A dye does not cut
        player.setStackInHand(Hand.MAIN_HAND, stencil("power_star"));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.RED_DYE));
        context.getBlockState(SIGN).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertTrue(!panel.hasShape(), "not cut with a dye");
        // Stencil + axe cuts, using the axe
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.IRON_AXE));
        context.getBlockState(SIGN).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertTrue(Arrays.equals(panel.getShape(), pattern("power_star")), "cut as a star");
        context.assertEquals(player.getOffHandStack().getDamage(), 1, "axe used");
        // A wet sponge gives the whole board back
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WET_SPONGE));
        context.getBlockState(SIGN).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertTrue(!panel.hasShape(), "whole board again");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void postSignsStandOnFencesAndWalls(TestContext context) {
        BlockPos abs = context.getAbsolutePos(SIGN);
        for (var sign : List.of(ModBlocks.WOODEN_PANEL, ModBlocks.WOODEN_CUTOUT_PANEL, ModBlocks.PLASTIC_ROAD_SIGN)) {
            context.setBlockState(SIGN.down(), Blocks.STONE);
            context.assertTrue(!sign.getDefaultState().canPlaceAt(context.getWorld(), abs), sign + " not on stone");
            context.setBlockState(SIGN.down(), Blocks.SPRUCE_FENCE);
            context.assertTrue(sign.getDefaultState().canPlaceAt(context.getWorld(), abs), sign + " on a fence");
            context.setBlockState(SIGN.down(), Blocks.COBBLESTONE_WALL);
            context.assertTrue(sign.getDefaultState().canPlaceAt(context.getWorld(), abs), sign + " on a wall");
            context.setBlockState(SIGN.down(), ModBlocks.PLASTIC_FENCES[3]);
            context.assertTrue(sign.getDefaultState().canPlaceAt(context.getWorld(), abs), sign + " on a plastic fence");
        }
        context.assertTrue(ModBlocks.PLASTIC_FENCES[3].getDefaultState().isIn(net.minecraft.registry.tag.BlockTags.FENCES), "plastic fences are fences");
        context.complete();
    }

    /** Uses the item in the player's main hand on the {@code side} face of the block at {@code against}. */
    private static void useOn(TestContext context, PlayerEntity player, BlockPos against, Direction side) {
        BlockPos abs = context.getAbsolutePos(against);
        Vec3d face = Vec3d.ofCenter(abs).add(Vec3d.of(side.getVector()).multiply(0.5));
        player.getMainHandStack().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND, new BlockHitResult(face, side, abs, false)));
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void signsGoAgainstFencesAndWalls(TestContext context) {
        PlayerEntity player = survivalPlayer(context);
        player.setPosition(Vec3d.of(context.getAbsolutePos(SIGN)).add(0, 0, -6));
        BlockPos front = SIGN.south();

        // Post signs hang on the side of a fence, facing away from it, whatever lies below
        for (var sign : List.of(ModBlocks.WOODEN_PANEL, ModBlocks.WOODEN_CUTOUT_PANEL, ModBlocks.PLASTIC_ROAD_SIGN)) {
            context.setBlockState(SIGN, Blocks.OAK_FENCE);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(sign));
            useOn(context, player, SIGN, Direction.SOUTH);
            BlockState hung = context.getBlockState(front);
            context.assertTrue(hung.isOf(sign) && hung.get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.HUNG
                    && AbstractStencilSignBlock.hungFacing(hung) == Direction.SOUTH, sign + " hung on the fence, facing south");
            // It falls when its post goes
            context.setBlockState(SIGN, Blocks.AIR);
            context.expectBlock(Blocks.AIR, front);
        }

        // On the side of a wall too
        context.setBlockState(SIGN, Blocks.COBBLESTONE_WALL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.WOODEN_PANEL));
        useOn(context, player, SIGN, Direction.SOUTH);
        context.assertTrue(AbstractStencilSignBlock.hungFacing(context.getBlockState(front)) == Direction.SOUTH, "panel hung on a wall");
        context.setBlockState(front, Blocks.AIR);

        // Signs standing on the ground stand in front of it, their back against it
        context.setBlockState(front.down(), Blocks.STONE);
        for (var sign : List.of(ModBlocks.ROCK_SIGN, ModBlocks.EASEL_SIGN)) {
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(sign));
            useOn(context, player, SIGN, Direction.SOUTH);
            BlockState leaning = context.getBlockState(front);
            context.assertTrue(leaning.isOf(sign) && leaning.get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.POST
                    && leaning.get(AbstractStencilSignBlock.ROTATION) == 0, sign + " against the wall, facing south");
            context.setBlockState(front, Blocks.AIR);
        }

        // Every sign also stands on top of a fence or a wall
        BlockPos abs = context.getAbsolutePos(front);
        context.setBlockState(front.down(), Blocks.BIRCH_FENCE);
        for (var sign : List.of(ModBlocks.ROCK_SIGN, ModBlocks.EASEL_SIGN)) {
            context.assertTrue(sign.getDefaultState().canPlaceAt(context.getWorld(), abs), sign + " on a fence");
        }
        context.setBlockState(front.down(), Blocks.MOSSY_STONE_BRICK_WALL);
        for (var sign : List.of(ModBlocks.ROCK_SIGN, ModBlocks.EASEL_SIGN)) {
            context.assertTrue(sign.getDefaultState().canPlaceAt(context.getWorld(), abs), sign + " on a wall");
        }
        context.complete();
    }

    private static double shift(TestContext context, BlockPos pos) {
        BlockState state = context.getBlockState(pos);
        return ((AbstractStencilSignBlock) state.getBlock()).boardShift(context.getWorld(), context.getAbsolutePos(pos), state);
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void postSignsGoFlatOnWallsFloorsAndCeilings(TestContext context) {
        PlayerEntity player = survivalPlayer(context);
        player.setPosition(Vec3d.of(context.getAbsolutePos(SIGN)).add(0, 0, -6));
        for (var sign : List.of(ModBlocks.WOODEN_PANEL, ModBlocks.WOODEN_CUTOUT_PANEL, ModBlocks.PLASTIC_ROAD_SIGN)) {
            // Against the south face of a stone block: flat on it, facing south; falls with it
            context.setBlockState(SIGN, Blocks.STONE);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(sign));
            useOn(context, player, SIGN, Direction.SOUTH);
            BlockState wall = context.getBlockState(SIGN.south());
            context.assertTrue(wall.isOf(sign) && wall.get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.WALL
                    && AbstractStencilSignBlock.facing(wall.get(AbstractStencilSignBlock.ROTATION)) == Direction.SOUTH, sign + " on the wall: " + wall);
            context.setBlockState(SIGN, Blocks.AIR);
            // Plastic needs nothing to hold it (it floats, like the studs); the wooden ones fall
            if (sign == ModBlocks.PLASTIC_ROAD_SIGN) context.expectBlock(sign, SIGN.south());
            else context.expectBlock(Blocks.AIR, SIGN.south());
            context.setBlockState(SIGN.south(), Blocks.AIR);
            // On the top face: lying on the floor
            context.setBlockState(SIGN, Blocks.STONE);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(sign));
            useOn(context, player, SIGN, Direction.UP);
            context.assertTrue(context.getBlockState(SIGN.up()).get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.FLOOR,
                    sign + " on the floor");
            context.setBlockState(SIGN.up(), Blocks.AIR);
            // Under it: on the ceiling, falling when the ceiling goes
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(sign));
            useOn(context, player, SIGN, Direction.DOWN);
            context.assertTrue(context.getBlockState(SIGN.down()).get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.CEILING,
                    sign + " on the ceiling");
            context.setBlockState(SIGN, Blocks.AIR);
            if (sign == ModBlocks.PLASTIC_ROAD_SIGN) context.expectBlock(sign, SIGN.down());
            else context.expectBlock(Blocks.AIR, SIGN.down());
            context.setBlockState(SIGN.down(), Blocks.AIR);
        }
        // On top of a fence: flat on it; sneaking: standing on it as on a post
        context.setBlockState(SIGN, Blocks.OAK_FENCE);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.WOODEN_PANEL));
        useOn(context, player, SIGN, Direction.UP);
        context.assertTrue(context.getBlockState(SIGN.up()).get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.FLOOR,
                "flat on the fence");
        context.setBlockState(SIGN.up(), Blocks.AIR);
        player.setSneaking(true);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.WOODEN_PANEL));
        useOn(context, player, SIGN, Direction.UP);
        context.assertTrue(context.getBlockState(SIGN.up()).get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.POST,
                "standing on the fence when sneaking");
        player.setSneaking(false);
        context.setBlockState(SIGN.up(), Blocks.AIR);
        // Signs standing on the ground do not go flat
        context.setBlockState(SIGN, Blocks.STONE);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.ROCK_SIGN));
        useOn(context, player, SIGN, Direction.UP);
        context.assertTrue(context.getBlockState(SIGN.up()).get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.POST,
                "rock sign stands");
        context.complete();
    }

    // ---------------------------------------------------------------- floating plastic

    private static final int TUBE_X = 3, TUBE_Z = 3, TUBE_BOTTOM = 1, TUBE_TOP = 5;

    /** A glass tube of still water from TUBE_BOTTOM to TUBE_TOP, open above. */
    private static void waterTube(TestContext context) {
        for (int y = TUBE_BOTTOM; y <= TUBE_TOP + 1; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) context.setBlockState(new BlockPos(TUBE_X + dx, y, TUBE_Z + dz), Blocks.GLASS);
            }
        }
        for (int y = TUBE_BOTTOM; y <= TUBE_TOP; y++) context.setBlockState(new BlockPos(TUBE_X, y, TUBE_Z), Blocks.WATER);
        context.setBlockState(new BlockPos(TUBE_X, TUBE_TOP + 1, TUBE_Z), Blocks.AIR);
    }

    private static int risingTicks(int blocks) {
        return blocks * fr.lordfinn.steveparty.blocks.custom.PlasticBlock.RISE_DELAY + 10;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plasticSignsFloatUpAndLieOnTheSurface(TestContext context) {
        waterTube(context);
        BlockPos start = new BlockPos(TUBE_X, TUBE_BOTTOM, TUBE_Z);
        context.setBlockState(start, ModBlocks.PLASTIC_ROAD_SIGN.getDefaultState().with(AbstractStencilSignBlock.WATERLOGGED, true)
                .with(AbstractStencilSignBlock.MOUNT, AbstractStencilSignBlock.Mount.WALL).with(PlasticRoadSignBlock.PLATE, PlasticRoadSignBlock.Plate.STAR));
        StencilCanvasBlockEntity sign = at(context, start);
        sign.setSymbol(pattern("skull"), DyeColor.RED);
        context.waitAndRun(risingTicks(TUBE_TOP - TUBE_BOTTOM + 1), () -> {
            BlockPos surface = new BlockPos(TUBE_X, TUBE_TOP + 1, TUBE_Z);
            BlockState state = context.getBlockState(surface);
            context.assertTrue(state.isOf(ModBlocks.PLASTIC_ROAD_SIGN), "at the surface: " + state);
            context.assertTrue(!state.get(AbstractStencilSignBlock.WATERLOGGED)
                    && state.get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.FLOOR, "lying flat on the water");
            context.assertTrue(state.get(PlasticRoadSignBlock.PLATE) == PlasticRoadSignBlock.Plate.STAR, "still a star");
            StencilCanvasBlockEntity moved = at(context, surface);
            context.assertTrue(Arrays.equals(moved.getShape(), pattern("skull")) && moved.getColor() == DyeColor.RED, "symbol kept");
            for (int y = TUBE_BOTTOM; y <= TUBE_TOP; y++) context.expectBlock(Blocks.WATER, new BlockPos(TUBE_X, y, TUBE_Z));
            context.complete();
        });
    }

    /** Rising in still water, plastic carries a player at its own pace (a block every few ticks), not flung up. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stillWaterDoesNotFlingRiders(TestContext context) {
        waterTube(context);
        BlockPos start = new BlockPos(TUBE_X, TUBE_BOTTOM, TUBE_Z);
        context.setBlockState(start, ModBlocks.PLASTIC_BLOCKS[0]);
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        Vec3d feet = Vec3d.ofBottomCenter(context.getAbsolutePos(start.up()));
        player.refreshPositionAndAngles(feet.x, feet.y, feet.z, 0, 0);
        context.runAtTick(fr.lordfinn.steveparty.blocks.custom.PlasticBlock.RISE_DELAY + 3, () -> {
            try {
                context.expectBlock(ModBlocks.PLASTIC_BLOCKS[0], start.up());
                // A block per RISE_DELAY ticks through the water's drag (about 0.35), far from the column's 1.4
                context.assertTrue(player.getVelocity().y <= fr.lordfinn.steveparty.blocks.custom.PlasticBlock.RIDE_STILL_SPEED + 1e-3,
                        "carried gently, not flung: " + player.getVelocity().y);
            } finally {
                context.getWorld().getServer().getPlayerManager().remove(player);
            }
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plasticSignsStopFlatUnderABlockAndChainsHoldThem(TestContext context) {
        waterTube(context);
        context.setBlockState(new BlockPos(TUBE_X, TUBE_BOTTOM + 3, TUBE_Z), Blocks.STONE);
        BlockPos start = new BlockPos(TUBE_X, TUBE_BOTTOM, TUBE_Z);
        context.setBlockState(start, ModBlocks.PLASTIC_ROAD_SIGN.getDefaultState().with(AbstractStencilSignBlock.WATERLOGGED, true)
                .with(AbstractStencilSignBlock.MOUNT, AbstractStencilSignBlock.Mount.FLOOR));
        // A chained one, beside, in its own water
        BlockPos chained = new BlockPos(1, 2, 1);
        context.setBlockState(chained.down(), Blocks.CHAIN);
        context.setBlockState(chained.up(), Blocks.WATER);
        context.setBlockState(chained, ModBlocks.PLASTIC_ROAD_SIGN.getDefaultState().with(AbstractStencilSignBlock.WATERLOGGED, true)
                .with(AbstractStencilSignBlock.MOUNT, AbstractStencilSignBlock.Mount.FLOOR));
        context.waitAndRun(risingTicks(3), () -> {
            BlockState under = context.getBlockState(new BlockPos(TUBE_X, TUBE_BOTTOM + 2, TUBE_Z));
            context.assertTrue(under.isOf(ModBlocks.PLASTIC_ROAD_SIGN)
                    && under.get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.CEILING, "flat under the stone: " + under);
            context.expectBlock(ModBlocks.PLASTIC_ROAD_SIGN, chained);
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void plasticFencesFloatButSignsOnPostsStay(TestContext context) {
        waterTube(context);
        context.setBlockState(new BlockPos(TUBE_X, TUBE_BOTTOM, TUBE_Z), ModBlocks.PLASTIC_FENCES[4].getDefaultState().with(net.minecraft.block.FenceBlock.WATERLOGGED, true));
        context.assertTrue(fr.lordfinn.steveparty.blocks.custom.PlasticBlock.isPlastic(ModBlocks.PLASTIC_FENCES[4].getDefaultState())
                && fr.lordfinn.steveparty.blocks.custom.PlasticBlock.isPlastic(ModBlocks.PLASTIC_ROAD_SIGN.getDefaultState()), "made of plastic");
        // A sign standing on a (stone) wall under water: on its post, it stays
        BlockPos post = new BlockPos(1, 1, 1);
        context.setBlockState(post, Blocks.COBBLESTONE_WALL.getDefaultState().with(net.minecraft.block.WallBlock.WATERLOGGED, true));
        context.setBlockState(post.up(), ModBlocks.PLASTIC_ROAD_SIGN.getDefaultState().with(AbstractStencilSignBlock.WATERLOGGED, true));
        context.setBlockState(post.up(2), Blocks.WATER);
        context.waitAndRun(risingTicks(TUBE_TOP - TUBE_BOTTOM + 1), () -> {
            // Like the plastic block, it stays in the water, its top level with the surface
            BlockState fence = context.getBlockState(new BlockPos(TUBE_X, TUBE_TOP, TUBE_Z));
            context.assertTrue(fence.isOf(ModBlocks.PLASTIC_FENCES[4]) && fence.get(net.minecraft.block.FenceBlock.WATERLOGGED), "fence at the surface: " + fence);
            context.expectBlock(Blocks.AIR, new BlockPos(TUBE_X, TUBE_TOP + 1, TUBE_Z));
            context.expectBlock(ModBlocks.PLASTIC_ROAD_SIGN, post.up());
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void signsCanBeBumpedInto(TestContext context) {
        BlockPos abs = context.getAbsolutePos(SIGN);
        context.setBlockState(SIGN.down(), Blocks.OAK_FENCE);
        for (var sign : List.of(ModBlocks.EASEL_SIGN, ModBlocks.OAK_EASEL_SIGN, ModBlocks.WOODEN_PANEL, ModBlocks.WOODEN_CUTOUT_PANEL,
                ModBlocks.PLASTIC_ROAD_SIGN, ModBlocks.ROCK_SIGN)) {
            context.setBlockState(SIGN, sign);
            context.assertTrue(!context.getBlockState(SIGN).getCollisionShape(context.getWorld(), abs).isEmpty(), sign + " has a hitbox");
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void boardsRestAgainstTheirPost(TestContext context) {
        // Board back at model z 5, fence post front at 6, wall pillar front at 4
        context.setBlockState(SIGN.down(), Blocks.OAK_FENCE);
        context.setBlockState(SIGN, ModBlocks.WOODEN_PANEL);
        context.assertTrue(Math.abs(shift(context, SIGN) - 1) < 1e-6, "panel against a fence post: " + shift(context, SIGN));
        context.setBlockState(SIGN.down(), Blocks.COBBLESTONE_WALL);
        context.assertTrue(Math.abs(shift(context, SIGN) + 1) < 1e-6, "panel against a wall pillar: " + shift(context, SIGN));
        // Turned by 45 degrees, the corner of the post comes further
        context.setBlockState(SIGN.down(), Blocks.OAK_FENCE);
        context.setBlockState(SIGN, ModBlocks.WOODEN_PANEL.getDefaultState().with(AbstractStencilSignBlock.ROTATION, 2));
        context.assertTrue(Math.abs(shift(context, SIGN) - (3 - 2 * Math.sqrt(2))) < 1e-6, "turned panel against the corner of the post");
        // Plate back at z 4
        context.setBlockState(SIGN, ModBlocks.PLASTIC_ROAD_SIGN);
        context.assertTrue(Math.abs(shift(context, SIGN) - 2) < 1e-6, "plate against a fence post: " + shift(context, SIGN));
        // Hung on a fence
        context.setBlockState(SIGN.down(), Blocks.AIR);
        context.setBlockState(SIGN.north(), Blocks.OAK_FENCE);
        context.setBlockState(SIGN, ModBlocks.WOODEN_CUTOUT_PANEL.getDefaultState().with(AbstractStencilSignBlock.MOUNT, AbstractStencilSignBlock.Mount.HUNG));
        context.assertTrue(Math.abs(shift(context, SIGN) - 1) < 1e-6, "hung cut-out panel against the fence post: " + shift(context, SIGN));
        // Signs without a board on a post do not move
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.ROCK_SIGN);
        context.assertTrue(shift(context, SIGN) == 0, "rock sign does not move");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void brushFadesASignThenScrubsIt(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.ROCK_SIGN);
        StencilCanvasBlockEntity rock = at(context, SIGN);
        rock.setSymbol(pattern("skull"), DyeColor.RED);
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.BRUSH));
        for (int step = 0; step <= StencilCanvasBlockEntity.MAX_FADE; step++) {
            context.runAtTick(1 + step * (StencilCanvasBlockEntity.BRUSH_INTERVAL + 1), () -> context.getBlockState(SIGN)
                    .onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH)));
        }
        context.runAtTick(2, () -> context.assertTrue(rock.getFade() == 1, "faded once"));
        context.runAtTick(2 + (StencilCanvasBlockEntity.MAX_FADE + 1) * (StencilCanvasBlockEntity.BRUSH_INTERVAL + 1), () -> {
            context.assertTrue(!rock.hasShape(), "scrubbed off");
            context.assertTrue(player.getMainHandStack().getDamage() == StencilCanvasBlockEntity.MAX_FADE + 1, "one brush use per step");
            context.complete();
        });
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilMakerGivesItsStencilBack(TestContext context) {
        context.setBlockState(SIGN, ModBlocks.STENCIL_MAKER);
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, stencil("key"));
        StencilMakerBlockEntity maker = at(context, SIGN);
        maker.swapStencil(player);
        context.assertTrue(player.getMainHandStack().isEmpty(), "stencil in the maker");
        maker.takeOutStencil(player);
        context.assertTrue(maker.getStencil().isEmpty() && Arrays.equals(StencilItem.getShape(player.getMainHandStack()), pattern("key")),
                "stencil back in hand");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilLibraryLearnsSavesAndFavourites(TestContext context) {
        StencilLibrary library = StencilLibrary.EMPTY.with(pattern("coin")).with(pattern("coin")).with(pattern("boo"));
        context.assertEquals(library.entries().size(), 2, "no duplicates");
        library = library.toggleFavorite(pattern("boo"));
        context.assertTrue(library.isFavorite(pattern("boo")) && !library.isFavorite(pattern("coin")), "boo is a favourite");
        library = library.toggleFavorite(pattern("key"));
        context.assertTrue(library.contains(pattern("key")) && library.isFavorite(pattern("key")), "favouriting adds it");
        library = library.without(pattern("coin"));
        context.assertTrue(!library.contains(pattern("coin")), "removed");
        context.assertTrue(StencilLibrary.EMPTY.with(StencilShape.blank()).entries().isEmpty(), "blank stencils are not kept");
        context.assertTrue(library.toggleFavorite(StencilShape.blank()) == library, "nothing to favourite: same library, nothing synced");

        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            player.getInventory().insertStack(stencil("crown"));
            StencilLibrary.learnFromInventory(player);
            context.assertTrue(StencilLibrary.of(player).contains(pattern("crown")), "found stencils are learnt");
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
        context.complete();
    }

    // ---------------------------------------------------------------- recipes

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void tokensAreMadeOfPlasticPellets(TestContext context) {
        ItemStack pellets = new ItemStack(ModItems.PLASTIC_PELLETS), empty = ItemStack.EMPTY;
        ItemStack token = result(context, 3, 3,
                empty, pellets, empty,
                empty, pellets, empty,
                pellets, new ItemStack(Items.SHULKER_BOX), pellets);
        context.assertTrue(token.isOf(ModItems.TOKEN), "token from plastic pellets and a shulker box: " + token);
        ItemStack iron = new ItemStack(Items.IRON_INGOT);
        context.assertTrue(result(context, 3, 3, empty, iron, empty, empty, iron, empty, iron, new ItemStack(Items.SHULKER_BOX), iron).isEmpty(),
                "no more token from iron");
        context.complete();
    }

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
        context.assertTrue(sign.isOf(ModBlocks.EASEL_SIGN.asItem()) && sign.getCount() == 2, "2 easel signs");
        context.assertTrue(Registries.BLOCK.getId(Blocks.CHERRY_PLANKS).equals(sign.get(ModComponents.SIGN_MATERIAL)), "made of cherry");

        ItemStack b = new ItemStack(Items.BIRCH_PLANKS);
        ItemStack mixed = result(context, 3, 3, p, b, p, p, p, p, s, e, s);
        context.assertTrue(!mixed.isOf(ModBlocks.EASEL_SIGN.asItem()), "no half cherry half birch sign");

        ItemStack r = new ItemStack(Items.GRANITE);
        ItemStack rock = result(context, 3, 2, e, r, e, r, r, r);
        context.assertTrue(rock.isOf(ModBlocks.ROCK_SIGN.asItem())
                && Registries.BLOCK.getId(Blocks.GRANITE).equals(rock.get(ModComponents.SIGN_MATERIAL)), "granite rock sign");

        ItemStack plastic = new ItemStack(ModBlocks.PLASTIC_BLOCKS[14]); // red
        ItemStack road = result(context, 1, 2, plastic, new ItemStack(ModItems.PLASTIC_PELLETS));
        context.assertTrue(road.isOf(ModBlocks.PLASTIC_ROAD_SIGN.asItem()) && road.get(DataComponentTypes.BASE_COLOR) == DyeColor.RED
                && road.getCount() == 2, "red road signs");
        // Plastic sticks from 2 plastic blocks (like wooden sticks), and plastic fences from plastic sticks
        ItemStack sticks = result(context, 1, 2, plastic, plastic);
        context.assertTrue(sticks.isOf(ModItems.PLASTIC_STICK) && sticks.getCount() == 4, "4 plastic sticks");
        ItemStack fences = result(context, 3, 2, plastic, new ItemStack(ModItems.PLASTIC_STICK), plastic,
                plastic, new ItemStack(ModItems.PLASTIC_STICK), plastic);
        context.assertTrue(fences.isOf(ModBlocks.PLASTIC_FENCES[14].asItem()) && fences.getCount() == 3, "3 red plastic fences");
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
        // Held on it, the brush fades it step by step, then scrubs it off
        for (int step = 0; step <= StencilCanvasBlockEntity.MAX_FADE; step++) {
            int fade = step;
            context.runAtTick(1 + step * (StencilCanvasBlockEntity.BRUSH_INTERVAL + 1), () -> {
                context.getBlockState(paint).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit(context, paint, Direction.SOUTH));
                if (fade < StencilCanvasBlockEntity.MAX_FADE) {
                    StencilCanvasBlockEntity faded = at(context, paint);
                    context.assertEquals(faded.getFade(), fade + 1, "fade step " + (fade + 1));
                }
            });
        }
        context.runAtTick(2 + (StencilCanvasBlockEntity.MAX_FADE + 1) * (StencilCanvasBlockEntity.BRUSH_INTERVAL + 1), () -> {
            context.expectBlock(Blocks.AIR, paint);
            context.complete();
        });
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
    public void stencilHammerStampsAWallWithOneStrike(TestContext context) {
        BlockPos wall = new BlockPos(1, 1, 3);
        context.setBlockState(wall, Blocks.STONE);
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, loadedGun());
        BlockPos abs = context.getAbsolutePos(wall);
        Vec3d face = Vec3d.ofCenter(abs).add(0, 0, -0.5);
        ActionResult result = player.getMainHandStack().useOnBlock(new ItemUsageContext(player, Hand.MAIN_HAND,
                new BlockHitResult(face, Direction.NORTH, abs, false)));
        context.assertEquals(result, ActionResult.CONSUME, "the strike plays its own swing (no vanilla arm swing)");
        context.expectBlock(ModBlocks.STENCIL_PAINT, wall.north());
        StencilCanvasBlockEntity paint = at(context, wall.north());
        context.assertTrue(Arrays.equals(paint.getShape(), pattern("coin")) && paint.getColor() == DyeColor.RED, "red coin stamped on the wall");
        context.assertEquals(StencilGunItem.contents(player.getMainHandStack()).get(StencilGunItem.STENCIL_SLOTS).getCount(), 1, "one red dye used");
        context.assertTrue(StencilHammerStrike.isCoolingDown(player, Hand.MAIN_HAND), "the hammer cools down for the length of its swing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilHammerStrikesOneSignAtATime(TestContext context) {
        context.setBlockState(SIGN.down(), ModBlocks.PLASTIC_FENCES[0]);
        context.setBlockState(SIGN, ModBlocks.PLASTIC_ROAD_SIGN);
        PlayerEntity player = survivalPlayer(context);
        ItemStack gun = loadedGun();
        player.setStackInHand(Hand.MAIN_HAND, gun);
        ItemActionResult first = context.getBlockState(SIGN).onUseWithItem(gun, context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertEquals(first, ItemActionResult.CONSUME, "the strike plays its own swing");
        // Straight away, with the blue dye: the hammer is still swinging
        StencilGunItem.scroll(player.getMainHandStack(), true, 1);
        context.getBlockState(SIGN).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        StencilCanvasBlockEntity sign = at(context, SIGN);
        context.assertTrue(sign.getColor() == DyeColor.RED, "no second strike while the first one swings");
        context.assertEquals(StencilGunItem.contents(player.getMainHandStack()).get(StencilGunItem.STENCIL_SLOTS + 3).getCount(), 1, "no blue dye used");
        // Once it has cooled down, it strikes again
        player.getItemCooldownManager().remove((player.getMainHandStack().getItem()));
        context.getBlockState(SIGN).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        context.assertTrue(sign.getColor() == DyeColor.BLUE, "blue once the swing is over");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilGunPaintsSignsWithItsOwnDyes(TestContext context) {
        context.setBlockState(SIGN.down(), ModBlocks.PLASTIC_FENCES[0]);
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

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilGunLoaderOnlyFillsTheGunItWasOpenedOn(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        try {
            ItemStack gunA = loadedGun();
            ItemStack gunB = new ItemStack(ModItems.STENCIL_GUN);
            player.getInventory().selectedSlot = 0;
            player.setStackInHand(Hand.MAIN_HAND, gunA);
            player.setStackInHand(Hand.OFF_HAND, gunB);
            StencilGunScreenHandler handler = new StencilGunScreenHandler(1, player.getInventory(), 0);
            handler.slots.get(1).setStack(stencil("boo"));
            context.assertTrue(Arrays.equals(StencilItem.getShape(StencilGunItem.contents(gunA).get(1)), pattern("boo")), "loading fills the gun");

            // The guns swap hands while the loader is open (off-hand swap key, a modified client)
            player.setStackInHand(Hand.MAIN_HAND, gunB);
            player.setStackInHand(Hand.OFF_HAND, gunA);
            handler.slots.get(3).setStack(stencil("key"));
            handler.onClosed(player);
            context.assertTrue(StencilGunItem.contents(gunB).stream().allMatch(ItemStack::isEmpty), "the other gun gets nothing: " + StencilGunItem.contents(gunB));
            context.assertTrue(StencilGunItem.contents(gunA).get(3).isEmpty(), "the gun that went away is not changed either");
            context.assertTrue(!handler.canUse(player), "the loader closes");
        } finally {
            context.getWorld().getServer().getPlayerManager().remove(player);
        }
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void destroyedStencilGunSpillsItsContents(TestContext context) {
        ServerWorld world = context.getWorld();
        Vec3d at = Vec3d.ofCenter(context.getAbsolutePos(new BlockPos(1, 2, 1)));
        ItemEntity entity = new ItemEntity(world, at.x, at.y, at.z, loadedGun());
        world.spawnEntity(entity);
        entity.getStack().getItem().onItemEntityDestroyed(entity);
        List<ItemEntity> spilled = world.getEntitiesByClass(ItemEntity.class, new net.minecraft.util.math.Box(at, at).expand(2),
                item -> item != entity);
        int stencils = spilled.stream().filter(item -> item.getStack().isOf(ModItems.STENCIL)).mapToInt(item -> item.getStack().getCount()).sum();
        int dyes = spilled.stream().filter(item -> item.getStack().getItem() instanceof net.minecraft.item.DyeItem)
                .mapToInt(item -> item.getStack().getCount()).sum();
        context.assertEquals(stencils, 2, "both stencils spilled");
        context.assertEquals(dyes, 3, "every dye spilled");
        spilled.forEach(net.minecraft.entity.Entity::discard);
        entity.discard();
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void blankStencilsPaintNothing(TestContext context) {
        BlockPos wall = new BlockPos(1, 2, 1);
        context.setBlockState(wall, Blocks.STONE);
        ServerWorld world = context.getWorld();
        context.assertTrue(!StencilPaintBlock.spray(world, context.getAbsolutePos(wall), Direction.SOUTH, StencilShape.blank(), DyeColor.RED, Direction.NORTH),
                "no invisible paint");
        context.expectBlock(Blocks.AIR, wall.south());
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModItems.STENCIL));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.RED_DYE, 2));
        useOn(context, player, wall, Direction.SOUTH);
        context.assertEquals(player.getOffHandStack().getCount(), 2, "no dye used on a wall");

        // On a sign: nothing painted, nothing used
        context.setBlockState(SIGN.east(2).down(), Blocks.STONE);
        context.setBlockState(SIGN.east(2), ModBlocks.EASEL_SIGN);
        context.getBlockState(SIGN.east(2)).onUseWithItem(player.getMainHandStack(), world, player, Hand.MAIN_HAND, hit(context, SIGN.east(2), Direction.NORTH));
        StencilCanvasBlockEntity sign = at(context, SIGN.east(2));
        context.assertTrue(!sign.hasShape(), "no blank symbol on the sign");
        context.assertEquals(player.getOffHandStack().getCount(), 2, "no dye used on a sign");

        // A gun whose selected stencil is blank has no stencil
        ItemStack gun = loadedGun();
        List<ItemStack> contents = StencilGunItem.contents(gun);
        contents.set(0, new ItemStack(ModItems.STENCIL));
        StencilGunItem.setContents(gun, contents);
        context.assertTrue(StencilGunItem.selectedLoad(gun).shape() == null, "blank stencil in the gun sprays nothing");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sprayingTheSamePaintAgainUsesNoDye(TestContext context) {
        BlockPos wall = new BlockPos(1, 2, 1);
        context.setBlockState(wall, Blocks.STONE);
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, stencil("heart"));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.RED_DYE, 3));
        useOn(context, player, wall, Direction.SOUTH);
        context.expectBlock(ModBlocks.STENCIL_PAINT, wall.south());
        context.assertEquals(player.getOffHandStack().getCount(), 2, "one dye to spray");
        useOn(context, player, wall, Direction.SOUTH);
        context.assertEquals(player.getOffHandStack().getCount(), 2, "the same fresh paint again uses nothing");
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(Items.BLUE_DYE, 1));
        useOn(context, player, wall, Direction.SOUTH);
        StencilCanvasBlockEntity paint = at(context, wall.south());
        context.assertTrue(paint.getColor() == DyeColor.BLUE && player.getOffHandStack().isEmpty(), "another colour repaints it");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void sprayingAFaceLeavesOtherFacesPaintAlone(TestContext context) {
        ServerWorld world = context.getWorld();
        // Paint on the floor, right in front of the south face of a wall
        BlockPos floor = new BlockPos(1, 1, 2), wall = new BlockPos(1, 2, 1);
        context.setBlockState(floor, Blocks.STONE);
        context.setBlockState(wall, Blocks.STONE);
        StencilPaintBlock.spray(world, context.getAbsolutePos(floor), Direction.UP, pattern("coin"), DyeColor.YELLOW, Direction.NORTH);
        context.expectBlock(ModBlocks.STENCIL_PAINT, floor.up());
        context.assertTrue(!StencilPaintBlock.spray(world, context.getAbsolutePos(wall), Direction.SOUTH, pattern("heart"), DyeColor.RED, Direction.NORTH),
                "spraying the wall does not repaint the floor");
        StencilCanvasBlockEntity paint = at(context, floor.up());
        context.assertTrue(Arrays.equals(paint.getShape(), pattern("coin")) && paint.getColor() == DyeColor.YELLOW, "floor paint unchanged");
        // Sprayed on the paint itself, it is repainted
        context.assertTrue(StencilPaintBlock.spray(world, context.getAbsolutePos(floor.up()), Direction.UP, pattern("heart"), DyeColor.RED, Direction.NORTH),
                "repainted when aimed at");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void glowOnlySignKeepsNoSymbolThroughItsItem(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.EASEL_SIGN);
        StencilCanvasBlockEntity sign = at(context, SIGN);
        sign.setGlowing(true);
        ServerWorld world = context.getWorld();
        BlockPos abs = context.getAbsolutePos(SIGN);
        ItemStack drop = net.minecraft.block.Block.getDroppedStacks(world.getBlockState(abs), world, abs, sign).getFirst();
        BlockPos other = SIGN.east(2);
        context.setBlockState(other.down(), Blocks.STONE);
        context.setBlockState(other, ModBlocks.EASEL_SIGN);
        StencilCanvasBlockEntity placed = at(context, other);
        placed.readComponents(drop);
        context.assertTrue(!placed.hasShape() && placed.isGlowing(), "glowing, and still no symbol");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void glowInkOnlyGoesOnPaint(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.EASEL_SIGN);
        StencilCanvasBlockEntity sign = at(context, SIGN);
        PlayerEntity player = survivalPlayer(context);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.GLOW_INK_SAC, 3));
        Runnable use = () -> context.getBlockState(SIGN).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND,
                hit(context, SIGN, Direction.NORTH));
        use.run();
        context.assertTrue(!sign.isGlowing() && player.getMainHandStack().getCount() == 3, "no ink on a bare sign");
        sign.setSymbol(pattern("coin"), null);
        use.run();
        context.assertTrue(!sign.isGlowing() && player.getMainHandStack().getCount() == 3, "no ink on an engraving");
        sign.setColor(DyeColor.RED);
        use.run();
        context.assertTrue(sign.isGlowing() && player.getMainHandStack().getCount() == 2, "the paint glows");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilGunSpraysSignsFromTheOffHand(TestContext context) {
        context.setBlockState(SIGN.down(), Blocks.STONE);
        context.setBlockState(SIGN, ModBlocks.EASEL_SIGN);
        PlayerEntity player = survivalPlayer(context);
        ItemStack gun = loadedGun();
        player.setStackInHand(Hand.OFF_HAND, gun);
        BlockState state = context.getBlockState(SIGN);
        // The main hand pass leaves it to the off hand
        state.onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit(context, SIGN, Direction.NORTH));
        state.onUseWithItem(gun, context.getWorld(), player, Hand.OFF_HAND, hit(context, SIGN, Direction.NORTH));
        StencilCanvasBlockEntity sign = at(context, SIGN);
        context.assertTrue(Arrays.equals(sign.getShape(), pattern("coin")) && sign.getColor() == DyeColor.RED, "red coin sprayed");
        context.assertEquals(StencilGunItem.contents(player.getOffHandStack()).get(StencilGunItem.STENCIL_SLOTS).getCount(), 1, "one dye used from the gun");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void signsGoInPlaceOfGrass(TestContext context) {
        PlayerEntity player = survivalPlayer(context);
        player.setPosition(Vec3d.of(context.getAbsolutePos(SIGN)).add(0, 0, -6));
        for (var sign : List.of(ModBlocks.WOODEN_PANEL, ModBlocks.PLASTIC_ROAD_SIGN, ModBlocks.EASEL_SIGN)) {
            context.setBlockState(SIGN.down(), Blocks.DIRT);
            context.setBlockState(SIGN, Blocks.SHORT_GRASS);
            player.setStackInHand(Hand.MAIN_HAND, new ItemStack(sign));
            useOn(context, player, SIGN, Direction.NORTH);
            BlockState placed = context.getBlockState(SIGN);
            AbstractStencilSignBlock.Mount expected = sign == ModBlocks.EASEL_SIGN ? AbstractStencilSignBlock.Mount.POST : AbstractStencilSignBlock.Mount.FLOOR;
            context.assertTrue(placed.isOf(sign) && placed.get(AbstractStencilSignBlock.MOUNT) == expected, sign + " in place of grass: " + placed);
            context.setBlockState(SIGN, Blocks.AIR);
        }
        // Sneaking on a fence under a snow layer: standing on it as on a post
        context.setBlockState(SIGN.down(), Blocks.OAK_FENCE);
        context.setBlockState(SIGN, Blocks.AIR);
        player.setSneaking(true);
        context.getWorld().setBlockState(context.getAbsolutePos(SIGN), Blocks.SNOW.getDefaultState(), net.minecraft.block.Block.FORCE_STATE);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(ModBlocks.WOODEN_PANEL));
        useOn(context, player, SIGN, Direction.UP);
        BlockState onPost = context.getBlockState(SIGN);
        context.assertTrue(onPost.isOf(ModBlocks.WOODEN_PANEL) && onPost.get(AbstractStencilSignBlock.MOUNT) == AbstractStencilSignBlock.Mount.POST,
                "on the post under the snow: " + onPost);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void easelSignOutlineFollowsItsBoard(TestContext context) {
        BlockPos abs = context.getAbsolutePos(SIGN);
        context.setBlockState(SIGN.down(), Blocks.STONE);
        for (int rotation : new int[]{0, 4, 2}) {
            context.setBlockState(SIGN, ModBlocks.EASEL_SIGN.getDefaultState().with(AbstractStencilSignBlock.ROTATION, rotation));
            BlockState state = context.getBlockState(SIGN);
            net.minecraft.util.shape.VoxelShape outline = state.getOutlineShape(context.getWorld(), abs);
            net.minecraft.util.shape.VoxelShape collision = state.getCollisionShape(context.getWorld(), abs);
            // The 2 block wide board sticks out of the block, along x facing south / north, along z facing west / east
            net.minecraft.util.math.Direction.Axis across = rotation == 4 ? net.minecraft.util.math.Direction.Axis.Z : net.minecraft.util.math.Direction.Axis.X;
            context.assertTrue(outline.getMin(across) < -0.3 && outline.getMax(across) > 1.3, "outline along the board at rotation " + rotation + ": " + outline.getBoundingBox());
            context.assertTrue(!collision.isEmpty() && collision.getMin(across) >= 0 && collision.getMax(across) <= 1
                    && collision.getMax(net.minecraft.util.math.Direction.Axis.Y) <= 1, "collision in its own block at rotation " + rotation);
        }
        context.complete();
    }

    // ---------------------------------------------------------------- loot

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilsAreFoundInStructureChests(TestContext context) {
        ServerWorld world = context.getWorld();
        LootTable table = world.getServer().getReloadableRegistries().getLootTable(LootTables.VILLAGE_CARTOGRAPHER_CHEST);
        LootContextParameterSet loot = new LootContextParameterSet.Builder(world)
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

    /** Stencil paint can't be broken by hitting it outside creative (brush or water remove it). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilPaintOnlyBreaksInCreative(TestContext context) {
        context.assertTrue(fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock.canBreak(
                context.createMockPlayer(net.minecraft.world.GameMode.CREATIVE)), "creative can break it");
        net.minecraft.entity.player.PlayerEntity survival = context.createMockPlayer(net.minecraft.world.GameMode.SURVIVAL);
        survival.setStackInHand(net.minecraft.util.Hand.MAIN_HAND, new net.minecraft.item.ItemStack(net.minecraft.item.Items.DIAMOND_PICKAXE));
        context.assertFalse(fr.lordfinn.steveparty.blocks.custom.signs.StencilPaintBlock.canBreak(survival), "survival can't, even with a pickaxe");
        context.complete();
    }
}
