package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileStamping;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.items.custom.StencilHammerStrike;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stamping a look on a tile: on the tile while it holds no cartridge, else on its cartridge (which carries it away). */
public class TileStampGameTests implements FabricGameTest {
    private static final BlockPos TILE = new BlockPos(2, 2, 2);

    private static BoardSpaceBlockEntity tile(TestContext context) {
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, ModBlocks.TILE);
        return context.getBlockEntity(TILE);
    }

    private static byte[] pattern(String id) {
        return StencilPatterns.byId(id).shape();
    }

    private static ItemActionResult use(TestContext context, PlayerEntity player, BlockPos pos) {
        BlockPos abs = context.getAbsolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(abs), Direction.UP, abs, false);
        return context.getBlockState(pos).onUseWithItem(player.getMainHandStack(), context.getWorld(), player, Hand.MAIN_HAND, hit);
    }

    private static PlayerEntity stencilAndDye(TestContext context, String patternId, net.minecraft.item.Item dye, int dyes) {
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, StencilItem.of(StencilPatterns.byId(patternId)));
        player.setStackInHand(Hand.OFF_HAND, new ItemStack(dye, dyes));
        return player;
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilAndDyeStampTheEmptyTile(TestContext context) {
        BoardSpaceBlockEntity tile = tile(context);
        PlayerEntity player = stencilAndDye(context, "coin", Items.RED_DYE, 3);
        context.assertEquals(use(context, player, TILE), ItemActionResult.SUCCESS, "stamped");
        TileStampComponent stamp = tile.getStamp();
        context.assertTrue(stamp != null && stamp.sameAs(pattern("coin"), DyeColor.RED), "a red coin on the tile: " + stamp);
        context.assertEquals(player.getOffHandStack().getCount(), 2, "one dye used");
        // The same look again: nothing changes, nothing is used up
        use(context, player, TILE);
        context.assertEquals(player.getOffHandStack().getCount(), 2, "no dye used for the same look");
        // Kept when saved and loaded (and sent to the clients the same way)
        var registries = context.getWorld().getRegistryManager();
        NbtCompound nbt = tile.createNbtWithIdentifyingData(registries);
        BoardSpaceBlockEntity copy = (BoardSpaceBlockEntity) net.minecraft.block.entity.BlockEntity.createFromNbt(tile.getPos(), tile.getCachedState(), nbt, registries);
        context.assertTrue(copy != null && stamp.equals(copy.getStamp()), "saved with the tile");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void theCartridgePrevailsAndCarriesTheLook(TestContext context) {
        BoardSpaceBlockEntity tile = tile(context);
        use(context, stencilAndDye(context, "coin", Items.RED_DYE, 1), TILE);
        tile.setStack(0, new ItemStack(ModItems.BOARD_SPACE_BEHAVIOR));
        // With a cartridge: the cartridge's plain face shows, and a new stamp goes onto the cartridge
        context.assertTrue(TileStamping.displayedStamp(tile, tile.getActiveCartridgeItemStack()) == null, "the cartridge prevails");
        use(context, stencilAndDye(context, "star", Items.BLUE_DYE, 1), TILE);
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        TileStampComponent onCartridge = cartridge.get(ModComponents.TILE_STAMP);
        context.assertTrue(onCartridge != null && onCartridge.sameAs(pattern("star"), DyeColor.BLUE), "a blue star on the cartridge");
        context.assertTrue(tile.getStamp().sameAs(pattern("coin"), DyeColor.RED), "the tile keeps its own look");
        context.assertTrue(TileStamping.displayedStamp(tile, cartridge) == onCartridge, "the tile shows its cartridge's look");
        // The cartridge shows its look in its tooltip
        Optional<TooltipData> data = cartridge.getItem().getTooltipData(cartridge);
        context.assertTrue(data.isPresent() && data.get() == onCartridge, "the stamp is the cartridge's tooltip data");
        // Taken out: the look goes with it, the tile shows its own again
        ItemStack removed = tile.removeStack(0);
        context.assertTrue(removed.get(ModComponents.TILE_STAMP) != null, "the look travels with the cartridge");
        context.assertTrue(TileStamping.displayedStamp(tile, tile.getActiveCartridgeItemStack()).sameAs(pattern("coin"), DyeColor.RED),
                "the tile's own look again");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void wetSpongeWashesTheShownLookOff(TestContext context) {
        BoardSpaceBlockEntity tile = tile(context);
        use(context, stencilAndDye(context, "coin", Items.RED_DYE, 1), TILE);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        player.setStackInHand(Hand.MAIN_HAND, new ItemStack(Items.WET_SPONGE));
        context.assertEquals(use(context, player, TILE), ItemActionResult.SUCCESS, "washed");
        context.assertTrue(tile.getStamp() == null, "no look any more");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stencilHammerStampsTheTileWithItsStrike(TestContext context) {
        BoardSpaceBlockEntity tile = tile(context);
        PlayerEntity player = context.createMockPlayer(GameMode.SURVIVAL);
        ItemStack gun = new ItemStack(ModItems.STENCIL_GUN);
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < StencilGunItem.SIZE; i++) contents.add(ItemStack.EMPTY);
        contents.set(0, StencilItem.of(StencilPatterns.byId("coin")));
        contents.set(StencilGunItem.STENCIL_SLOTS, new ItemStack(Items.LIME_DYE, 2));
        StencilGunItem.setContents(gun, contents);
        player.setStackInHand(Hand.MAIN_HAND, gun);
        context.assertEquals(use(context, player, TILE), ItemActionResult.CONSUME, "the strike plays its own swing");
        context.assertTrue(tile.getStamp() != null && tile.getStamp().sameAs(pattern("coin"), DyeColor.LIME), "a lime coin stamped");
        context.assertEquals(StencilGunItem.contents(player.getMainHandStack()).get(StencilGunItem.STENCIL_SLOTS).getCount(), 1, "one lime dye used");
        context.assertTrue(StencilHammerStrike.isCoolingDown(player, Hand.MAIN_HAND), "the hammer swung");
        context.complete();
    }

    @GameTest(templateName = EMPTY_STRUCTURE)
    public void aLargeTileIsStampedFromAnyOfItsBlocks(TestContext context) {
        context.setBlockState(TILE.down(), Blocks.STONE);
        context.setBlockState(TILE, ModBlocks.ADVANCED_TILE.getDefaultState().with(fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SIZE,
                fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout.LARGE_SOUTH_EAST));
        BoardSpaceBlockEntity tile = context.getBlockEntity(TILE);
        use(context, stencilAndDye(context, "coin", Items.ORANGE_DYE, 1), TILE.add(1, 0, 1));
        context.assertTrue(tile.getStamp() != null && tile.getStamp().sameAs(pattern("coin"), DyeColor.ORANGE), "stamped through a part");
        context.complete();
    }
}
