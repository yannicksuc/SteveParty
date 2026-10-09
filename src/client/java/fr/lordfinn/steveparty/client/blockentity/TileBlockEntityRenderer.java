package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.AdvancedTileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileStamping;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSupport;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.client.utils.BoardSpaceClientUtils;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import fr.lordfinn.steveparty.client.utils.TileColors;
import fr.lordfinn.steveparty.client.utils.TileStampTextures;
import fr.lordfinn.steveparty.components.TileStampComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.ROTATION_8;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SIZE;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SUPPORT;
import static fr.lordfinn.steveparty.components.ModComponents.*;

/**
 * Draws what a tile shows above its block model: its face (coloured with vivid {@link TileColors} ramps, or its
 * stamped look), the head of a start tile's player; and, for the tiles that are not level and standard sized (not
 * baked in the chunk, see the blockstate file), the tile itself: its level model moved onto its support (lowered, or
 * tilted 45 degrees on stairs, face kept square) and brought to its size, with the base filling the hollows under a
 * sloped tile ({@link TileFillRenderer}).
 */
public class TileBlockEntityRenderer implements BlockEntityRenderer<BoardSpaceBlockEntity> {

    private final SkullEntityModel model;
    private static final int MAX_CACHED_OWNERS = 256;
    private static final Map<String, Optional<UUID>> OWNER_UUIDS = new HashMap<>();
    private static final Map<Identifier, Sprite> SPRITES = new ConcurrentHashMap<>();
    private static final Random RANDOM = Random.create();
    private static final Identifier textureBad = Steveparty.id("block/tile_overlay_angry");
    private static final Identifier textureNeutral = Steveparty.id("block/tile_overlay_neutral");
    private static final Identifier textureExcited = Steveparty.id("block/tile_overlay_excited");
    private static final Identifier textureBlow = Steveparty.id("block/tile_overlay_blow");
    private static final Identifier textureAdvancedFill = Steveparty.id("block/advanced_tile_fill");
    private static final Identifier textureSimpleFill = Steveparty.id("block/tile_fill");
    /** Height of the top of a tile's face above the tile's floor, and of its bottom (its picture is 1 px thick). */
    private static final float FACE_TOP = 2 / 16f, FACE_BOTTOM = 1 / 16f;

    public TileBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new SkullEntityModel(ctx.getLayerRenderDispatcher().getModelPart(EntityModelLayers.PLAYER_HEAD));
    }

    @Override
    public void render(BoardSpaceBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!state.contains(SUPPORT)) return;
        BoardSpaceType tileType = state.get(TILE_TYPE);
        int direction = state.get(ROTATION_8);
        TileSupport support = state.get(SUPPORT);
        TileLayout layout = state.get(SIZE);
        TileSize size = layout.size();
        ItemStack stack = BoardSpaceClientUtils.getDisplayedCartridge(entity);
        int color = stack.isEmpty() ? TileColors.WHITE : stack.getOrDefault(COLOR, TileColors.WHITE);
        boolean small = size == TileSize.SMALL;
        // The middle of the tile, in its cell (a large tile's is the corner shared by its 4 blocks)
        double centreX = layout.centreX(), centreZ = layout.centreZ();

        matrices.push();
        if (support.isSloped() && entity.getWorld() != null) {
            Sprite fill = getSprite(state.getBlock() instanceof AdvancedTileBlock ? textureAdvancedFill : textureSimpleFill);
            // The tile's highest point: its face's corner, half a diagonal up the slope from its middle
            double half = small ? 0.5 * 18 / 16 : 1;
            double ceiling = support.surfaceY(centreX, centreZ) + half * Math.sqrt(2) * Math.sin(support.angle());
            TileFillRenderer.render(entity.getWorld(), entity.getPos(), support, layout.cells(), ceiling, fill, matrices, vertexConsumers, light);
        }
        applySupport(matrices, support, centreX, centreZ);
        matrices.translate(centreX - 0.5, 0, centreZ - 0.5);
        if (!support.isFlat() || size != TileSize.STANDARD) {
            // Not baked in the chunk: the level standard model, brought to its size
            matrices.push();
            if (small) {
                matrices.translate(0.5, 0, 0.5);
                matrices.scale(TileSize.SMALL_SCALE, 1, TileSize.SMALL_SCALE);
                matrices.translate(-0.5, 0, -0.5);
            }
            renderLevelModel(state, matrices, vertexConsumers, light, overlay, color);
            matrices.pop();
        }
        if (tileType == BoardSpaceType.TILE_START) {
            matrices.pop();
            Matrix4f onSupport = support.transform(centreX, centreZ).translate((float) (centreX - 0.5), 0, (float) (centreZ - 0.5));
            renderTileStart(entity, small, onSupport, matrices, vertexConsumers, light, stack);
            return;
        }
        // A stamped look replaces the face (the tile's own, or its cartridge's: see TileStamping)
        Identifier face = faceTexture(tileType, stack, TileStamping.displayedStamp(entity, stack), color, small);
        if (face != null) renderFace(matrices, vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(face)), light, small, direction, 1f);
        matrices.pop();
        // What the role shows over the space (a Threshold obstacle's condition...)
        BoardRuleOverlays.render(entity, tileType, stack, centreX, centreZ, tickDelta, matrices, vertexConsumers, light);
    }

    /**
     * The face a tile of role {@code tileType} shows for its (active) {@code cartridge} (empty: none) in {@code color}:
     * its stamped look ({@code stamp}, the tile's own or its cartridge's) or its role's pictogram. Also the face of the
     * tile items (TileItemFace).
     */
    public static @Nullable Identifier faceTexture(BoardSpaceType tileType, ItemStack stack,
                                                   @Nullable TileStampComponent stamp,
                                                   int color, boolean small) {
        Identifier face;
        if (stamp != null) face = TileStampTextures.get(stamp, small);
        // An item tile: excited (bonus), angry (malus) or blowing (nothing yet) face, in the cartridge's colour
        else if (tileType == BoardSpaceType.TILE_INVENTORY_INTERACTOR) face = TileStampTextures.face(inventoryFace(stack), color, small);
        // A Stop tile: a barred "no entry" disc in the cartridge's colour (anthracite by default)
        else if (tileType == BoardSpaceType.BOARD_SPACE_STOP) face = TileStampTextures.stopFace(color, small);
        // A Move Forward / Back tile: a double arrow and the number of spaces, green forward, pink-magenta back
        else if (tileType == BoardSpaceType.TILE_ADVANCE_BACK)
            face = TileStampTextures.advanceBack(AdvanceBackCartridgeItem.steps(stack), small);
        // A Replay tile: a circular arrow in the cartridge's colour (cyan by default)
        else if (tileType == BoardSpaceType.TILE_REPLAY) face = TileStampTextures.replayFace(color, small);
        // A Teleport tile: a portal (its rings drifting) in the colour of its network (violet by default)
        else if (tileType == BoardSpaceType.TILE_TELEPORT) face = TileStampTextures.teleportFace(color, small);
        // A shop, a star space, Glandouille, Frousseux, Mistigri, a Threshold obstacle, a Common pot, a Key gate:
        // its pictogram in the cartridge's colour
        else {
            face = TileStampTextures.pictogramFace(tileType, color, small);
            // The neutral face in the cartridge's colour (dyes), white by default
            if (face == null) face = TileStampTextures.face(textureNeutral, color, small);
        }
        return face;
    }

    /**
     * Moves a level tile onto its support: lowered, or turned 45 degrees about its middle ({@code x}, {@code z})
     * (through the matrix stack, so that the normals turn too).
     */
    private static void applySupport(MatrixStack matrices, TileSupport support, double x, double z) {
        if (!support.isSloped()) {
            matrices.translate(0, -support.drop() / 16.0, 0);
            return;
        }
        double length = Math.sqrt(support.gradientX() * support.gradientX() + support.gradientZ() * support.gradientZ());
        matrices.translate(x, support.surfaceY(x, z), z);
        matrices.multiply(new Quaternionf().rotationAxis(support.angle(),
                (float) (-support.gradientZ() / length), 0, (float) (support.gradientX() / length)));
        matrices.translate(-x, 0, -z);
    }

    private static Identifier inventoryFace(ItemStack stack) {
        int color = stack.getOrDefault(COLOR, 0);
        if (color == InventoryInteractorTileBehavior.GOOD_COLOR) return textureExcited;
        if (color == InventoryInteractorTileBehavior.BAD_COLOR) return textureBad;
        return textureBlow;
    }

    /**
     * The tile's own block model (as if it were level and standard sized), drawn with the current transformation, each
     * tinted part in its shade of the tile's colour (see {@link TileColors#tint}).
     */
    private static void renderLevelModel(BlockState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         int light, int overlay, int color) {
        BlockState level = ATileBlock.levelState(state);
        BakedModel bakedModel = MinecraftClient.getInstance().getBlockRenderManager().getModel(level);
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayers.getBlockLayer(level));
        MatrixStack.Entry entry = matrices.peek();
        for (Direction face : Direction.values()) renderQuads(entry, consumer, bakedModel.getQuads(level, face, RANDOM), color, light, overlay);
        renderQuads(entry, consumer, bakedModel.getQuads(level, null, RANDOM), color, light, overlay);
    }

    private static void renderQuads(MatrixStack.Entry entry, VertexConsumer consumer, List<BakedQuad> quads, int color, int light, int overlay) {
        for (BakedQuad quad : quads) {
            int rgb = quad.hasColor() ? TileColors.tint(color, quad.getColorIndex()) : TileColors.WHITE;
            consumer.quad(entry, quad, ((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f, light, overlay);
        }
    }

    /** @param onSupport the transformation of the level tile onto its support (where its pedestal is) */
    private void renderTileStart(BoardSpaceBlockEntity entity, boolean small, Matrix4f onSupport, MatrixStack matrices,
                                 VertexConsumerProvider vertexConsumers, int light, ItemStack stack) {
        String owner = stack.get(TB_START_OWNER);
        if (owner == null || owner.isEmpty()) return;
        UUID ownerUUID = parseOwner(owner);
        if (ownerUUID == null) return;
        Identifier texture = SkinUtils.getPlayerSkin(ownerUUID);
        if (texture == null) return;

        int dir = entity.getCachedState().get(ROTATION_8);
        // The head sits on the pedestal of the model, on the front side (toward the player who placed the tile),
        // diagonals included (tile_start_45)
        Vector3f front = frontVector(dir);
        Vector3f pedestal = new Vector3f(9f / 16, 0, 9f / 16).mul(front);
        if (small) pedestal.mul(TileSize.SMALL_SCALE, 1, TileSize.SMALL_SCALE);
        // Where the pedestal is once the tile lies on its support (tilted or lowered); the head itself stays upright
        Vector4f point = new Vector4f(0.5f + pedestal.x, -1f / 16, 0.5f + pedestal.z, 1).mul(onSupport);
        matrices.push();
        // renderSkull puts the head in the middle of the block it is given
        matrices.translate(point.x - 0.5, point.y, point.z - 0.5);
        SkullBlockEntityRenderer.renderSkull(Direction.DOWN, 180 + dir * 45, 0.0F, matrices, vertexConsumers, light, this.model,
                RenderLayer.getEntityTranslucent(texture));
        matrices.pop();
    }

    /** Parses (and caches) the owner UUID; an invalid value is logged once instead of every frame. */
    private static UUID parseOwner(String owner) {
        Optional<UUID> cached = OWNER_UUIDS.get(owner);
        if (cached == null) {
            UUID parsed = null;
            try {
                parsed = UUID.fromString(owner);
            } catch (IllegalArgumentException e) {
                Steveparty.LOGGER.error("Invalid UUID: {}", owner);
            }
            if (OWNER_UUIDS.size() >= MAX_CACHED_OWNERS) OWNER_UUIDS.clear();
            cached = Optional.ofNullable(parsed);
            OWNER_UUIDS.put(owner, cached);
        }
        return cached.orElse(null);
    }

    private static Sprite getSprite(Identifier texture) {
        return SPRITES.computeIfAbsent(texture,
                id -> MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(id));
    }

    /** Sprites change when the block atlas is re-stitched: drop the cache after the models reload. */
    public static void registerReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("tile_renderer_sprite_cache");
            }

            @Override
            public Collection<Identifier> getFabricDependencies() {
                return List.of(ResourceReloadListenerKeys.MODELS, ResourceReloadListenerKeys.TEXTURES);
            }

            @Override
            public void reload(ResourceManager manager) {
                SPRITES.clear();
            }
        });
        TileStampTextures.registerReloadListener();
    }

    /**
     * Side the tile faces, in 8 steps of 45°: rotation 0 = placed while looking north, faces south; the rotation turns
     * clockwise (2 = west, 4 = north, 6 = east), like the blockstate "y"; odd ones are the diagonals of tile_start_45.
     */
    private static Vector3f frontVector(int rot) {
        double angle = Math.toRadians(rot * 45);
        return new Vector3f((float) -Math.sin(angle), 0, (float) Math.cos(angle));
    }

    /**
     * A tile face on top of the tile, turned toward its facing (8 directions), 1 px thick: a 32x32 face over the 2
     * blocks of a standard tile (drawn part 28x28), or a 16x16 face over exactly the block of a small tile; both
     * {@code scale}d about the middle (the tile items draw smaller faces).
     */
    public static void renderFace(MatrixStack matrices, VertexConsumer consumer, int light, boolean small, int direction, float scale) {
        int side = small ? TileStampTextures.SMALL_SIDE : TileStampTextures.SIDE;
        int margin = small ? 0 : TileStampTextures.MARGIN;
        float half = (small ? 0.5f : 1f) * scale;
        float edge = half * (side - 2 * margin) / side;
        float texel = 1f / side, m = margin * texel;
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(new Quaternionf().rotationY((float) (-direction * Math.PI / 4)));
        MatrixStack.Entry entry = matrices.peek();
        // Top: the texture's rows run south (v grows with z), as the tile model's picture
        vertex(consumer, entry, -half, FACE_TOP, half, 0, 1, light, 0, 1, 0);
        vertex(consumer, entry, half, FACE_TOP, half, 1, 1, light, 0, 1, 0);
        vertex(consumer, entry, half, FACE_TOP, -half, 1, 0, light, 0, 1, 0);
        vertex(consumer, entry, -half, FACE_TOP, -half, 0, 0, light, 0, 1, 0);
        // Its 4 sides, 1 px high, with the outermost texels of the face
        float lo = m, hi = 1 - m;
        // north (z = -edge), row lo
        vertex(consumer, entry, -edge, FACE_BOTTOM, -edge, lo, lo, light, 0, 0, -1);
        vertex(consumer, entry, -edge, FACE_TOP, -edge, lo, lo + texel, light, 0, 0, -1);
        vertex(consumer, entry, edge, FACE_TOP, -edge, hi, lo + texel, light, 0, 0, -1);
        vertex(consumer, entry, edge, FACE_BOTTOM, -edge, hi, lo, light, 0, 0, -1);
        // south (z = +edge), row hi
        vertex(consumer, entry, edge, FACE_BOTTOM, edge, hi, hi, light, 0, 0, 1);
        vertex(consumer, entry, edge, FACE_TOP, edge, hi, hi - texel, light, 0, 0, 1);
        vertex(consumer, entry, -edge, FACE_TOP, edge, lo, hi - texel, light, 0, 0, 1);
        vertex(consumer, entry, -edge, FACE_BOTTOM, edge, lo, hi, light, 0, 0, 1);
        // west (x = -edge), column lo
        vertex(consumer, entry, -edge, FACE_BOTTOM, edge, lo, hi, light, -1, 0, 0);
        vertex(consumer, entry, -edge, FACE_TOP, edge, lo + texel, hi, light, -1, 0, 0);
        vertex(consumer, entry, -edge, FACE_TOP, -edge, lo + texel, lo, light, -1, 0, 0);
        vertex(consumer, entry, -edge, FACE_BOTTOM, -edge, lo, lo, light, -1, 0, 0);
        // east (x = +edge), column hi
        vertex(consumer, entry, edge, FACE_BOTTOM, -edge, hi, lo, light, 1, 0, 0);
        vertex(consumer, entry, edge, FACE_TOP, -edge, hi - texel, lo, light, 1, 0, 0);
        vertex(consumer, entry, edge, FACE_TOP, edge, hi - texel, hi, light, 1, 0, 0);
        vertex(consumer, entry, edge, FACE_BOTTOM, edge, hi, hi, light, 1, 0, 0);
        matrices.pop();
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, float u, float v,
                               int light, float nx, float ny, float nz) {
        consumer.vertex(entry.getPositionMatrix(), x, y, z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, nx, ny, nz);
    }
}
