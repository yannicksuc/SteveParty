package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileStamping;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.StopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ColorModule;
import fr.lordfinn.steveparty.gametest.kit.TestPlayers;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CheckPointBlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import fr.lordfinn.steveparty.stencil.StencilShape;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.TileDye.DYED;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.TileGlow.GLOWING;

/**
 * A dye on a tile or a check point colours its cartridge (a check point then shows it); a sponge, a wet sponge or a
 * brush takes it off again, after a stamped look.
 */
public class TileDyeGameTests implements SteveGameTest {
    private static final BlockPos SPACE = new BlockPos(2, 2, 2);
    private static final int RED = DyeColor.RED.getEntityColor();

    /** A tile or a check point holding a Stop cartridge (anthracite of its own). */
    private static BoardSpaceBlockEntity space(TestContext context, Block block) {
        context.setBlockState(SPACE.down(), Blocks.STONE);
        context.setBlockState(SPACE, block);
        BoardSpaceBlockEntity space = context.getBlockEntity(SPACE);
        space.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP));
        return space;
    }

    private static ItemActionResult use(TestContext context, PlayerEntity player) {
        BlockPos abs = context.getAbsolutePos(SPACE);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
        return context.getBlockState(SPACE).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit);
    }

    private static PlayerEntity holding(TestContext context, ItemStack stack) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, stack);
        return player;
    }

    private static Integer color(BoardSpaceBlockEntity space) {
        return space.getActiveCartridgeItemStack().get(ModComponents.COLOR);
    }

    /**
     * The base swatch of a plain cartridge's menu means its host's base colour: no colour stored, so that a check point
     * stays blue (not white) and a tile white; in a hand, the neutral white.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBaseSwatchKeepsTheHostsColour(TestContext context) {
        BoardSpaceBlockEntity checkPoint = space(context, ModBlocks.CHECK_POINT);
        checkPoint.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        BlockPos abs = context.getAbsolutePos(SPACE);
        ServerPlayerEntity player = TestPlayers.mock(context);
        Vec3d near = abs.toCenterPos().add(1.5, 0.5, 0);
        player.refreshPositionAndAngles(near.x, near.y, near.z, 0, 0);
        CartridgeRef onCheckPoint = CartridgeRef.slot(abs, 0);
        ColorModule module = (ColorModule) CartridgeMenus.module(checkPoint.getStack(0), "color");
        context.assertTrue(module != null, "a colour module");
        context.assertEquals(module.get(checkPoint.getStack(0)), ColorModule.DEFAULT, "a new cartridge: the base colour");
        context.assertEquals(module.baseColor(checkPoint.getStack(0), checkPoint), CheckPointBlockEntity.BASE_COLOR, "blue in a check point");

        context.assertTrue(CartridgeMenus.apply(player, onCheckPoint, "color", DyeColor.RED.getId()), "red");
        context.assertEquals(color(checkPoint), RED, "its cartridge is red");
        context.expectBlockProperty(SPACE, DYED, true);
        context.assertTrue(CartridgeMenus.apply(player, onCheckPoint, "color", ColorModule.DEFAULT), "base colour");
        context.assertTrue(color(checkPoint) == null, "no colour stored: the host's own");
        context.expectBlockProperty(SPACE, DYED, false);
        context.assertEquals(module.get(checkPoint.getStack(0)), ColorModule.DEFAULT, "the base swatch selected");

        // In a hand: the neutral colour, the dye taken off the same way
        ItemStack inHand = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR);
        inHand.set(ModComponents.COLOR, RED);
        player.setStackInHand(Hand.MAIN_HAND, inHand);
        context.assertEquals(module.baseColor(inHand, null), CartridgeItem.PLAIN_COLOR, "white in a hand");
        context.assertTrue(CartridgeMenus.apply(player, CartridgeRef.hand(Hand.MAIN_HAND), "color", ColorModule.DEFAULT), "base colour in hand");
        context.assertTrue(!inHand.contains(ModComponents.COLOR), "no colour stored in hand");

        // On a tile: white; a Stop cartridge keeps its own anthracite
        BlockPos tilePos = SPACE.east(2);
        context.setBlockState(tilePos.down(), Blocks.STONE);
        context.setBlockState(tilePos, ModBlocks.TILE);
        BoardSpaceBlockEntity tile = context.getBlockEntity(tilePos);
        context.assertEquals(module.baseColor(new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR), tile), CartridgeItem.PLAIN_COLOR, "white on a tile");
        ItemStack stop = new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR_STOP);
        context.assertEquals(module.baseColor(stop, checkPoint), StopCartridgeItem.COLOR, "a Stop cartridge's own colour");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aDyeColoursTheCheckPoint(TestContext context) {
        BoardSpaceBlockEntity checkPoint = space(context, ModBlocks.CHECK_POINT);
        context.expectBlockProperty(SPACE, DYED, false);
        PlayerEntity player = holding(context, new ItemStack(Items.RED_DYE, 3));
        context.assertEquals(use(context, player), ItemActionResult.SUCCESS, "dyed");
        context.assertEquals(color(checkPoint), RED, "its cartridge is red");
        context.expectBlockProperty(SPACE, DYED, true);
        context.assertEquals(player.getMainHandStack().getCount(), 2, "one dye used");
        use(context, player);
        context.assertEquals(player.getMainHandStack().getCount(), 2, "already red: no dye used");
        // The cartridge taken out: a plain check point again
        checkPoint.removeStack(0);
        context.expectBlockProperty(SPACE, DYED, false);
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void creativeUsesNoDye(TestContext context) {
        space(context, ModBlocks.CHECK_POINT);
        PlayerEntity player = context.createMockPlayer(GameMode.CREATIVE);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.RED_DYE));
        use(context, player);
        context.expectBlockProperty(SPACE, DYED, true);
        context.assertEquals(player.getMainHandStack().getCount(), 1, "nothing used in creative");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aDyeOnAnEmptySpaceIsNotUsed(TestContext context) {
        for (Block block : new Block[]{ModBlocks.TILE, ModBlocks.CHECK_POINT}) {
            context.setBlockState(SPACE.down(), Blocks.STONE);
            context.setBlockState(SPACE, block);
            PlayerEntity player = holding(context, new ItemStack(Items.RED_DYE));
            use(context, player);
            context.assertEquals(player.getMainHandStack().getCount(), 1, "no cartridge to colour: no dye used on " + block);
        }
        context.expectBlockProperty(SPACE, DYED, false);
        context.complete();
    }

    /** {@code tool} takes the dye off {@code block}, nothing used; then finds nothing to do. */
    private static void takesTheDyeOff(TestContext context, Block block, ItemStack tool) {
        BoardSpaceBlockEntity space = space(context, block);
        use(context, holding(context, new ItemStack(Items.RED_DYE)));
        context.assertEquals(color(space), RED, "red");
        PlayerEntity player = holding(context, tool);
        context.assertEquals(use(context, player), ItemActionResult.SUCCESS, tool + " takes the dye off");
        context.assertEquals(color(space), StopCartridgeItem.COLOR, "its own anthracite again");
        if (block == ModBlocks.CHECK_POINT) context.expectBlockProperty(SPACE, DYED, false);
        context.assertEquals(player.getMainHandStack().getCount(), 1, "nothing used");
        int wear = player.getMainHandStack().getDamage();
        context.assertTrue(use(context, player) != ItemActionResult.SUCCESS, "no dye any more: nothing to take off");
        context.assertEquals(player.getMainHandStack().getDamage(), wear, "not worn for nothing");
        context.assertEquals(color(space), StopCartridgeItem.COLOR, "still anthracite");
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aSpongeTakesTheDyeOffATile(TestContext context) {
        takesTheDyeOff(context, ModBlocks.TILE, new ItemStack(Items.SPONGE));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aSpongeTakesTheDyeOffACheckPoint(TestContext context) {
        takesTheDyeOff(context, ModBlocks.CHECK_POINT, new ItemStack(Items.SPONGE));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBrushTakesTheDyeOffATile(TestContext context) {
        takesTheDyeOff(context, ModBlocks.TILE, new ItemStack(Items.BRUSH));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBrushTakesTheDyeOffACheckPoint(TestContext context) {
        takesTheDyeOff(context, ModBlocks.CHECK_POINT, new ItemStack(Items.BRUSH));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theBrushWearsAsWhenItBrushes(TestContext context) {
        space(context, ModBlocks.CHECK_POINT);
        use(context, holding(context, new ItemStack(Items.RED_DYE)));
        PlayerEntity player = holding(context, new ItemStack(Items.BRUSH));
        use(context, player);
        context.assertEquals(player.getMainHandStack().getDamage(), 1, "one use of the brush");
        context.complete();
    }

    /** On a stamped, dyed tile, {@code tool} takes the look off first, the dye on the next click. */
    private static void lookFirstThenDye(TestContext context, ItemStack tool) {
        BoardSpaceBlockEntity tile = space(context, ModBlocks.TILE);
        use(context, holding(context, new ItemStack(Items.RED_DYE)));
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        TileStamping.apply(tile, cartridge, TileStampComponent.of(StencilShape.full(), DyeColor.BLUE));
        PlayerEntity player = holding(context, tool);
        use(context, player);
        context.assertFalse(tile.getActiveCartridgeItemStack().contains(ModComponents.TILE_STAMP), "the look first");
        context.assertEquals(color(tile), RED, "the dye stays");
        use(context, player);
        context.assertEquals(color(tile), StopCartridgeItem.COLOR, "then the dye");
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aWetSpongeWashesTheLookThenTheDye(TestContext context) {
        lookFirstThenDye(context, new ItemStack(Items.WET_SPONGE));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aBrushScrubsTheLookThenTheDye(TestContext context) {
        lookFirstThenDye(context, new ItemStack(Items.BRUSH));
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aGlowingDyedCheckPointKeepsBoth(TestContext context) {
        space(context, ModBlocks.CHECK_POINT);
        use(context, holding(context, new ItemStack(Items.RED_DYE)));
        use(context, holding(context, new ItemStack(Items.GLOW_INK_SAC)));
        context.expectBlockProperty(SPACE, DYED, true);
        context.expectBlockProperty(SPACE, GLOWING, true);
        use(context, holding(context, new ItemStack(Items.SPONGE)));
        context.expectBlockProperty(SPACE, DYED, false);
        context.expectBlockProperty(SPACE, GLOWING, true);
        context.complete();
    }
}
