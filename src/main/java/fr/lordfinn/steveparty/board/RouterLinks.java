package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tile Linker Brush's links from an Aiguilleur (router) to the board spaces it drives, kept by the router itself
 * (no cartridge): painted from the router to a space; painted again, removed. Drawn by the brush overlay in redstone
 * red dots, like the party links (they control, nobody walks them: chevrons are the pawns' paths).
 */
public final class RouterLinks {
    /** The colours of the dots, from the router to its board space. */
    public static final int NEAR = 0xFF5A48, FAR = CartridgeLinks.ROUTER_COLOR;

    static final BrushLinks.Provider PROVIDER = (world, holder, held, out) -> {
        if (holder instanceof BoardSpaceRedstoneRouterBlockEntity router && held.slot() == 0) out.add(new Tiles(router));
    };

    private RouterLinks() {
    }

    /** The colour (rgb) of the dot {@code t} of the way from the router (0) to its board space (1). */
    public static int gradient(double t) {
        return Argb.lerp(NEAR, FAR, (float) Math.clamp(t, 0, 1)) & 0xFFFFFF;
    }

    /** A router's board spaces. */
    public record Tiles(BoardSpaceRedstoneRouterBlockEntity router) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return router.getPos();
        }

        @Override
        public int color() {
            return CartridgeLinks.ROUTER_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return world.getBlockEntity(BoardSpaces.resolve(world, target)) instanceof BoardSpaceBlockEntity;
        }

        @Override
        public List<BlockPos> targets(World world) {
            return router.getTiles();
        }

        @Override
        public boolean linked(World world, BlockPos target) {
            return router.getTiles().contains(BoardSpaces.resolve(world, target));
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            BlockPos tile = BoardSpaces.resolve(world, target).toImmutable();
            if (!accepts(world, tile)) return false;
            List<BlockPos> tiles = new ArrayList<>(router.getTiles());
            if (tiles.contains(tile)) return true;
            tiles.add(tile);
            set(player, tiles);
            WrenchActions.say(player, Text.translatable("message.steveparty.tile_linker_brush.linked", BoardText.pos(holder()), BoardText.pos(tile)), WrenchActions.linkWord(true));
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.45f, 1.2f);
            BoardLinks.trail(world, holder(), tile, RouterLinks::gradient);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            BlockPos tile = BoardSpaces.resolve(world, target);
            List<BlockPos> tiles = new ArrayList<>(router.getTiles());
            if (!tiles.remove(tile)) return false;
            set(player, tiles);
            TileLinkerBrush.erased(player, world, holder(), tile);
            BoardLinks.trail(world, holder(), tile, BoardLinks.CUT_COLOR);
            return true;
        }

        private void set(ServerPlayerEntity player, List<BlockPos> tiles) {
            LinkHistory.record(player, new LinkHistory.RouterLinksChange(holder(), List.copyOf(router.getTiles()), List.copyOf(tiles)));
            router.setTiles(tiles);
        }
    }
}
