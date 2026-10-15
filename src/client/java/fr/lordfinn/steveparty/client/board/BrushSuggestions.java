package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBoard;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.board.BoardGraph;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.board.BoardRevision;
import fr.lordfinn.steveparty.board.BrushAim;
import fr.lordfinn.steveparty.board.BrushLinkable;
import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.CartridgeLinks;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.client.blockentity.SpawnBubbleRenderer;
import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import fr.lordfinn.steveparty.client.utils.GhostModels;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.MagpieNestBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * What the Tile Linker Brush suggests, hovering (no stroke held) over what it can link from: grey see-through ghost
 * links (arrows) toward the nearest targets of the right kind, nothing linked yet, nothing done until painted.
 * <ul>
 *     <li>a board space (a start tile too) linking nothing at the brush's level: to the nearest tile no link leads to
 *     (never a start tile: nothing leads into one);</li>
 *     <li>the Party Controller: to the start tiles around not linked to it; and, its bank linking no container, to the
 *     nearest chest, or to a ghost chest 1 to 2 blocks away when there is none;</li>
 *     <li>a mob space whose cartridge links no Spawn Bubble: a ghost bubble where its mob appears by default (beside the
 *     space, on the right of the way its token goes);</li>
 *     <li>a Common pot space whose cartridge links no Magpie Nest: a ghost nest 2 blocks (else 1) beside it, facing
 *     it.</li>
 * </ul>
 * Worked out when the aimed block or the board changes (or once a second), never per frame. Drawn by
 * {@link BrushOverlay} in its pass (LAST, entity buffers drawn at the end of the hook), like the brush's own ghost
 * tiles: thin see-through arrows writing no depth that is relied on. The ghost chest and bubble are models, asked for
 * from GhostModels (nearest faces only, seen through water and glass: see the minecraft-rendering skill), the ghost
 * nest too (its block model; its pile and long twig, drawn by its block entity, left out).
 */
final class BrushSuggestions {
    /** The grey of the suggestions, and how see-through their arrows are. */
    private static final int GREY = 0xC8C8C8, ARROW_ALPHA = 0xD0;
    /** A tile suggests the nearest tile this far at most. */
    private static final double TILE_RANGE = 12;
    /** The Party Controller suggests start tiles this far at most, and chests this far. */
    private static final double START_RANGE = PartyControllerEntity.START_TILES_SEARCH_RADIUS, CHEST_RANGE = 8;
    private static final int REFRESH_TICKS = 20;
    /** The ghost bubble stands this far beside its tile (blocks, centre to centre): a full block clear of it. */
    private static final int BUBBLE_SIDE = 2;

    /** A ghost link from a block to another, and the ghost blocks drawn at their end. */
    private record Suggestion(List<Vec3d[]> links, @Nullable BlockPos chest, Direction chestFacing, @Nullable Vec3d bubble, @Nullable Vec3d bubbleFrom,
                              @Nullable BlockPos nest, Direction nestFacing) {
        static final Suggestion NONE = new Suggestion(List.of(), null, Direction.SOUTH, null, null, null, Direction.NORTH);

        Suggestion(List<Vec3d[]> links, @Nullable BlockPos chest, Direction chestFacing, @Nullable Vec3d bubble, @Nullable Vec3d bubbleFrom) {
            this(links, chest, chestFacing, bubble, bubbleFrom, null, Direction.NORTH);
        }
    }

    private static Suggestion current = Suggestion.NONE;
    private static @Nullable BlockPos builtFor;
    private static long builtRevision = -1;
    private static int age;

    private BrushSuggestions() {
    }

    /** Each tick, the brush in hand: what it suggests from the block aimed at now. */
    static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        ItemStack brush = player == null ? ItemStack.EMPTY : player.getMainHandStack();
        if (world == null || !TileLinkerBrush.isBrush(brush) || BrushTrail.painting(player)) {
            current = Suggestion.NONE;
            builtFor = null;
            return;
        }
        BlockPos aimed = BrushAim.aimed(player, world, 1f, Set.of(), pos -> false);
        if (Objects.equals(aimed, builtFor) && builtRevision == BoardRevision.client() && ++age < REFRESH_TICKS) return;
        age = 0;
        builtFor = aimed;
        builtRevision = BoardRevision.client();
        current = aimed == null ? Suggestion.NONE : build(world, aimed, TileLinkerBrush.level(brush));
    }

    private static Suggestion build(ClientWorld world, BlockPos aimed, int level) {
        BlockEntity blockEntity = world.getBlockEntity(BoardSpaces.resolve(world, aimed));
        if (blockEntity instanceof PartyControllerEntity controller) return controller(world, controller);
        if (!(blockEntity instanceof BoardSpaceBlockEntity space)) return Suggestion.NONE;
        List<Vec3d[]> links = new ArrayList<>();
        Vec3d from = BrushOverlay.anchor(world, space.getPos());
        List<BrushLinkable> kinds = BrushLinks.of(world, space.getPos(), level);
        Vec3d bubble = null;
        BlockPos nest = null;
        // An empty tile has no cartridge yet (the brush gives it one): it links nothing either
        boolean linksNothing = true;
        for (BrushLinkable kind : kinds) {
            if (kind instanceof CartridgeLinks.BoardPaths paths && !paths.targets(world).isEmpty()) linksNothing = false;
            else if (kind instanceof CartridgeLinks.SpawnPoint spawn && spawn.targets(world).isEmpty()) bubble = defaultMobSpot(world, space);
            else if (kind instanceof CartridgeLinks.PotNest pot && pot.targets(world).isEmpty()) nest = ghostSpot(world, space.getPos());
        }
        if (linksNothing) {
            BlockPos tile = nearestFreeTile(world, space.getPos());
            if (tile != null) links.add(new Vec3d[]{from, BrushOverlay.anchor(world, tile)});
        }
        // The ghost nest faces its space, linked to it as the real one would be
        Direction nestFacing = Direction.NORTH;
        if (nest != null) {
            links.add(new Vec3d[]{from, Vec3d.ofCenter(nest)});
            nestFacing = Direction.getFacing(space.getPos().getX() - nest.getX(), 0, space.getPos().getZ() - nest.getZ());
        }
        return new Suggestion(links, null, Direction.SOUTH, bubble, bubble == null ? null : from, nest, nestFacing);
    }

    /**
     * The tile nearest to {@code from} (within {@link #TILE_RANGE}) no link leads to, never a start tile nor
     * {@code from} itself, from the board view's graph; null for none.
     */
    private static @Nullable BlockPos nearestFreeTile(ClientWorld world, BlockPos from) {
        BoardGraph graph = BoardView.graph();
        if (graph == null) return null;
        Set<BlockPos> linkedTo = new HashSet<>();
        for (BoardGraph.Node node : graph.nodes()) {
            for (BoardGraph.Edge edge : node.edges()) if (!edge.to().equals(node.pos())) linkedTo.add(edge.to());
        }
        BlockPos best = null;
        double bestDistance = TILE_RANGE * TILE_RANGE;
        for (BoardGraph.Node node : graph.nodes()) {
            BlockPos pos = node.pos();
            if (pos.equals(from) || node.start() || linkedTo.contains(pos)) continue;
            double distance = pos.getSquaredDistance(from);
            if (distance <= bestDistance) {
                best = pos;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** From the Party Controller: its start tiles to link, and a chest for its bank (a ghost one if none is around). */
    private static Suggestion controller(ClientWorld world, PartyControllerEntity controller) {
        BlockPos pos = controller.getPos();
        Vec3d from = BrushOverlay.anchor(world, pos);
        List<Vec3d[]> links = new ArrayList<>();
        BoardGraph graph = BoardView.graph();
        if (graph != null) {
            for (BoardGraph.Node node : graph.nodes()) {
                if (!node.start() || controller.getStartLinks().contains(node.pos())) continue;
                if (node.pos().getSquaredDistance(pos) > START_RANGE * START_RANGE || !PartyBoard.isStart(world, node.pos())) continue;
                Vec3d to = BrushOverlay.anchor(world, node.pos());
                links.add(new Vec3d[]{BrushOverlay.sideOf(pos, from.y, to), to});
            }
        }
        BlockPos ghostChest = null;
        if (bankWithoutContainer(world, pos)) {
            BlockPos chest = nearestChest(world, pos);
            BlockPos to = chest != null ? chest : (ghostChest = ghostSpot(world, pos));
            if (to != null) links.add(new Vec3d[]{BrushOverlay.sideOf(pos, from.y, Vec3d.ofCenter(to)), Vec3d.ofCenter(to)});
        }
        // The ghost chest opens toward the controller
        Direction facing = ghostChest == null ? Direction.SOUTH
                : Direction.getFacing(pos.getX() - ghostChest.getX(), 0, pos.getZ() - ghostChest.getZ());
        return new Suggestion(links, ghostChest, facing, null, null);
    }

    /** Whether the controller's bank (an Inventory Cartridge, as the clients see it) links no container. */
    private static boolean bankWithoutContainer(ClientWorld world, BlockPos controller) {
        for (BrushLinkable kind : BrushLinks.of(world, controller, TileLinkerBrush.POWERED)) {
            if (kind instanceof CartridgeLinks.Containers) return kind.targets(world).isEmpty();
        }
        return false;
    }

    /** The container a cartridge takes nearest to {@code center} (within {@link #CHEST_RANGE}), or null. */
    private static @Nullable BlockPos nearestChest(ClientWorld world, BlockPos center) {
        BlockPos best = null;
        double bestDistance = CHEST_RANGE * CHEST_RANGE;
        int min = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.getX() - CHEST_RANGE));
        int maxX = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.getX() + CHEST_RANGE));
        int minZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.getZ() - CHEST_RANGE));
        int maxZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.getZ() + CHEST_RANGE));
        for (int chunkX = min; chunkX <= maxX; chunkX++) {
            for (int chunkZ = minZ; chunkZ <= maxZ; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof Inventory)) continue;
                    double distance = blockEntity.getPos().getSquaredDistance(center);
                    if (distance <= bestDistance && CartridgeContainers.accepts(world, blockEntity.getPos())) {
                        best = blockEntity.getPos();
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** Where a ghost chest (or nest) is shown: a free cell on solid ground 2 blocks from the block, else 1. */
    private static @Nullable BlockPos ghostSpot(ClientWorld world, BlockPos from) {
        for (int distance : new int[]{2, 1}) {
            for (Direction side : Direction.Type.HORIZONTAL) {
                BlockPos spot = from.offset(side, distance);
                if (world.getBlockState(spot).isReplaceable() && world.getBlockState(spot.down()).isSolidBlock(world, spot.down())) return spot;
            }
        }
        return null;
    }

    /**
     * Where the mob of the space appears without a Spawn Bubble: beside it, on the right of the way a token goes on it
     * (along its first link, else from the first space linking to it), a block away.
     */
    private static Vec3d defaultMobSpot(ClientWorld world, BoardSpaceBlockEntity space) {
        BlockPos pos = space.getPos();
        Vec3d way = null;
        List<BlockPos> out = BoardLinks.links(space, space.getActiveSlot());
        for (BlockPos to : out) if (!to.equals(pos)) {
            way = Vec3d.of(to.subtract(pos));
            break;
        }
        BoardGraph graph = BoardView.graph();
        if (way == null && graph != null) {
            for (BoardGraph.Node node : graph.nodes()) {
                if (node.pos().equals(pos) || node.edges().stream().noneMatch(e -> e.to().equals(pos))) continue;
                way = Vec3d.of(pos.subtract(node.pos()));
                break;
            }
        }
        if (way == null || way.horizontalLengthSquared() < 1.0E-4) way = new Vec3d(0, 0, -1);
        Vec3d forward = new Vec3d(way.x, 0, way.z).normalize();
        // Facing the way it goes, its right hand: (-z, x), a block clear of the tile, standing on the ground of its cell
        Direction right = Direction.getFacing(-forward.z, 0, forward.x), ahead = Direction.getFacing(forward.x, 0, forward.z);
        // The right hand first, then the other sides: the first on solid ground (not over water), else the right
        for (Direction side : new Direction[]{right, right.getOpposite(), ahead, ahead.getOpposite()}) {
            BlockPos cell = pos.offset(side, BUBBLE_SIDE);
            if (world.getBlockState(cell).isReplaceable() && world.getBlockState(cell.down()).isSolidBlock(world, cell.down())) {
                return Vec3d.ofBottomCenter(cell);
            }
        }
        return Vec3d.ofBottomCenter(pos.offset(right, BUBBLE_SIDE));
    }

    // ---------------------------------------------------------------- drawing

    /** Draws the suggestions (called from the brush overlay's pass, nothing while a stroke is held). */
    static void render(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, ClientWorld world, float tickDelta) {
        Suggestion suggestion = current;
        if (suggestion == Suggestion.NONE) return;
        double phase = (world.getTime() + tickDelta) / 20.0 * 2.5;
        float pulse = 0.75f + 0.25f * (float) Math.sin((world.getTime() + tickDelta) / 6.0);
        int arrows = ((int) (ARROW_ALPHA * pulse) << 24) | GREY;
        for (Vec3d[] link : suggestion.links()) WorldDraw.path(matrices, consumers, camera, link[0], link[1], arrows, 0.4, 0.45, phase, 0.3, 0);
        // Linked from its tile to its middle, inside its film
        if (suggestion.bubble() != null) {
            WorldDraw.dotted(matrices, consumers, camera, suggestion.bubbleFrom(), suggestion.bubble().add(0, 0.45, 0), 0.6f * pulse, t -> GREY);
        }
    }

    /**
     * Asks for the ghost chest and bubble of this frame (AFTER_SETUP: they are drawn around the translucent terrain,
     * GhostModels), pulsing with the arrows.
     */
    static void requestGhosts(ClientWorld world, Camera camera, float tickDelta) {
        Suggestion suggestion = current;
        if (suggestion == Suggestion.NONE || ToolWheel.isOpen()) return;
        float pulse = 0.75f + 0.25f * (float) Math.sin((world.getTime() + tickDelta) / 6.0);
        if (suggestion.chest() != null) ghostChest(suggestion.chest(), suggestion.chestFacing(), GHOST_ALPHA * pulse);
        if (suggestion.nest() != null) ghostNest(suggestion.nest(), suggestion.nestFacing(), GHOST_ALPHA * pulse);
        if (suggestion.bubble() != null) {
            // A Spawn Bubble's ghost: the real one at rest, see-through, facing the camera (as one placed)
            Vec3d at = suggestion.bubble(), cam = camera.getPos();
            float yaw = (float) Math.toDegrees(Math.atan2(-(cam.x - at.x), cam.z - at.z));
            SpawnBubbleRenderer.ghost(at, yaw, GHOST_ALPHA * pulse, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        }
    }

    /** How see-through the ghost chest and the ghost bubble are (their models in their own colours, before the pulse). */
    private static final float GHOST_ALPHA = 0.5f;

    /** A ghost Magpie Nest: its block model at {@code pos}, see-through (nearest faces only), facing {@code facing}. */
    private static void ghostNest(BlockPos pos, Direction facing, float alpha) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockState state = ModBlocks.MAGPIE_NEST.getDefaultState().with(MagpieNestBlock.FACING, facing);
        GhostModels.request(Vec3d.of(pos), Vec3d.ofCenter(pos), alpha, true, (matrices, buffers) -> {
            BakedModel model = client.getBlockRenderManager().getModel(state);
            client.getBlockRenderManager().getModelRenderer().render(matrices.peek(),
                    buffers.getBuffer(RenderLayer.getEntityCutout(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE)), state, model, 1, 1, 1,
                    LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        }, null);
    }

    /**
     * A ghost chest: a chest's hologram (one cube and its latch, GhostModels#CHEST), see-through, at
     * {@code pos}, opening toward {@code facing}.
     */
    private static void ghostChest(BlockPos pos, Direction facing, float alpha) {
        GhostModels.request(Vec3d.of(pos), Vec3d.ofCenter(pos), alpha, true, (matrices, buffers) -> {
            matrices.push();
            matrices.translate(0.5, 0.5, 0.5);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-facing.asRotation()));
            matrices.translate(-0.5, -0.5, -0.5);
            GhostModels.CHEST.draw(matrices, buffers, 0xFFFFFF, LightmapTextureManager.MAX_LIGHT_COORDINATE);
            matrices.pop();
        }, null);
    }
}
