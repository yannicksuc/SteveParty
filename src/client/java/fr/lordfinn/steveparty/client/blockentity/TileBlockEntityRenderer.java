package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.SimpleTileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSupport;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileSize;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.util.math.MathHelper;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors.InventoryInteractorTileBehavior;
import fr.lordfinn.steveparty.client.utils.BoardSpaceClientUtils;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourceReloadListenerKeys;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.ROTATION_8;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SUPPORT;
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.SIZE;
import static fr.lordfinn.steveparty.components.ModComponents.*;
import static java.lang.Math.PI;

public class TileBlockEntityRenderer implements BlockEntityRenderer<BoardSpaceBlockEntity> {

    private final SkullEntityModel model;
    private static final int MAX_CACHED_OWNERS = 256;
    private static final Map<String, Optional<UUID>> OWNER_UUIDS = new HashMap<>();
    private static final Map<Identifier, Sprite> SPRITES = new ConcurrentHashMap<>();
    // Scratch quaternion, render thread only
    private static final Quaternionf ROTATION = new Quaternionf();
    private static final Identifier textureBad = Steveparty.id("block/tile_overlay_angry");
    private static final Identifier textureNeutral = Steveparty.id("block/tile_overlay_neutral");
    private static final Identifier textureExcited = Steveparty.id("block/tile_overlay_excited");
    private static final int WHITE = 0xFFFFFF;
    private static final Identifier textureBlow = Steveparty.id("block/tile_overlay_blow");
    private static final Identifier textureAdvancedBase = Steveparty.id("block/tile");
    private static final Identifier textureSimpleBase = Steveparty.id("block/simple_tile");


    public TileBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new SkullEntityModel(ctx.getLayerRenderDispatcher().getModelPart(EntityModelLayers.PLAYER_HEAD));
    }

    @Override
    public void render(BoardSpaceBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = entity.getCachedState();
        if (!state.contains(SUPPORT)) return;
        BoardSpaceType tileType = state.get(TILE_TYPE);
        Integer direction = state.get(ROTATION_8);
        TileSupport support = state.get(SUPPORT);
        TileSize size = state.get(SIZE);
        ItemStack stack = BoardSpaceClientUtils.getDisplayedCartridge(entity);
        int color = stack.isEmpty() ? WHITE : stack.getOrDefault(COLOR, WHITE);

        matrices.push();
        if (!support.isFlat() || size != TileSize.STANDARD) {
            // Not baked in the chunk (see the blockstate file): the level standard model, moved onto the support's
            // surface and brought to its size
            if (support.isSloped()) renderSkirt(state, support, matrices, vertexConsumers, light);
            matrices.multiplyPositionMatrix(support.transform());
            applySize(matrices, size);
            renderLevelModel(state, matrices, vertexConsumers, light, overlay, color);
        }
        switch (tileType) {
            case TILE_START -> {
                matrices.pop();
                renderTileStart(entity, support, size, matrices, vertexConsumers, light, stack);
                return;
            }
            case TILE_INVENTORY_INTERACTOR -> renderInventoryInteractor(entity, matrices, vertexConsumers, light, overlay, stack, direction);
            // The neutral face covers the tinted top of the model: it takes the cartridge colour (dyes), white by default
            default -> renderPicture(matrices, vertexConsumers, light, overlay, textureNeutral, direction, color);
        }
        matrices.pop();
    }

    /**
     * From the standard size (2 blocks wide, centred on the block) to {@code size}: a small tile is shrunk so that its
     * picture covers its block; a large one is moved to the middle of its 2x2 blocks (its block is the north-west one).
     */
    private static void applySize(MatrixStack matrices, TileSize size) {
        switch (size) {
            case SMALL -> {
                matrices.translate(0.5, 0, 0.5);
                matrices.scale(TileSize.SMALL_SCALE, 1, TileSize.SMALL_SCALE);
                matrices.translate(-0.5, 0, -0.5);
            }
            case LARGE -> matrices.translate(0.5, 0, 0.5);
            default -> {
            }
        }
    }

    /** The tile's own block model (as if it were level), drawn with the current transformation. */
    private static void renderLevelModel(BlockState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         int light, int overlay, int color) {
        BlockRenderManager manager = MinecraftClient.getInstance().getBlockRenderManager();
        BlockState level = ATileBlock.levelState(state);
        BakedModel model = manager.getModel(level);
        float r = ((color >> 16) & 0xFF) / 255f, g = ((color >> 8) & 0xFF) / 255f, b = (color & 0xFF) / 255f;
        manager.getModelRenderer().render(matrices.peek(), vertexConsumers.getBuffer(RenderLayers.getBlockLayer(level)),
                level, model, r, g, b, light, overlay);
    }

    /**
     * Fills the hollows between a sloped tile and the stairs under it: the sides of the wedge of tile base between the
     * steps and the slope, on the edges of the cell (the tile covers its top).
     */
    private static void renderSkirt(BlockState state, TileSupport support, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        Sprite sprite = getSprite(state.getBlock() instanceof SimpleTileBlock ? textureSimpleBase : textureAdvancedBase);
        // The underside of the tile's base: the lower left quarter of its texture
        float u0 = MathHelper.lerp(0.0f, sprite.getMinU(), sprite.getMaxU()), u1 = MathHelper.lerp(0.5f, sprite.getMinU(), sprite.getMaxU());
        float v0 = MathHelper.lerp(0.5f, sprite.getMinV(), sprite.getMaxV()), v1 = MathHelper.lerp(1.0f, sprite.getMinV(), sprite.getMaxV());
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getSolid());
        MatrixStack.Entry entry = matrices.peek();
        // Each edge of the cell, in two halves (one per quarter of the support under it)
        double[][] edges = {{0, 0, 1, 0}, {1, 0, 1, 1}, {1, 1, 0, 1}, {0, 1, 0, 0}};
        for (double[] edge : edges) {
            for (int half = 0; half < 2; half++) {
                double ax = MathHelper.lerp(half * 0.5, edge[0], edge[2]), az = MathHelper.lerp(half * 0.5, edge[1], edge[3]);
                double bx = MathHelper.lerp(half * 0.5 + 0.5, edge[0], edge[2]), bz = MathHelper.lerp(half * 0.5 + 0.5, edge[1], edge[3]);
                double mx = (ax + bx) / 2, mz = (az + bz) / 2;
                int quarter = (mx > 0.5 ? 1 : 0) + (mz > 0.5 ? 2 : 0);
                float bottom = (float) support.supportTop(quarter);
                float topA = (float) Math.max(bottom, support.surfaceY(ax, az));
                float topB = (float) Math.max(bottom, support.surfaceY(bx, bz));
                if (topA - bottom < 1.0E-3 && topB - bottom < 1.0E-3) continue;
                float nx = (float) (bz - az), nz = (float) (ax - bx);
                float[][] quad = {{(float) ax, bottom, (float) az}, {(float) bx, bottom, (float) bz},
                        {(float) bx, topB, (float) bz}, {(float) ax, topA, (float) az}};
                float[][] uv = {{u0, v1}, {u1, v1}, {u1, MathHelper.lerp((topB - bottom) * 2, v1, v0)}, {u0, MathHelper.lerp((topA - bottom) * 2, v1, v0)}};
                // Both sides: seen from outside the cell and from the hollow of a missing neighbour
                for (int side = 0; side < 2; side++) {
                    for (int k = 0; k < 4; k++) {
                        int i = side == 0 ? k : 3 - k;
                        consumer.vertex(entry.getPositionMatrix(), quad[i][0], quad[i][1], quad[i][2]).color(255, 255, 255, 255)
                                .texture(uv[i][0], uv[i][1]).overlay(OverlayTexture.DEFAULT_UV).light(light)
                                .normal(entry, side == 0 ? nx : -nx, 0, side == 0 ? nz : -nz);
                    }
                }
            }
        }
    }

    private void renderInventoryInteractor(BoardSpaceBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, ItemStack stack, int direction) {
        Identifier texture = textureBlow;
        int color = stack.getOrDefault(COLOR, 0);
        if (color == InventoryInteractorTileBehavior.GOOD_COLOR)
            texture = textureExcited;
        else if (color == InventoryInteractorTileBehavior.BAD_COLOR)
            texture = textureBad;
        renderPicture(matrices, vertexConsumers, light, overlay, texture, direction, WHITE);
    }

    private void renderTileStart(BoardSpaceBlockEntity entity, TileSupport support, TileSize size, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ItemStack stack) {
        // Validate UUID
        String owner = stack.get(TB_START_OWNER);
        if (owner == null || owner.isEmpty()) return;
        UUID ownerUUID = parseOwner(owner);
        if (ownerUUID == null) return;

        Identifier texture = SkinUtils.getPlayerSkin(ownerUUID);

        if (texture == null) {
            return;
        }

        matrices.push();
        Integer dir = entity.getCachedState().get(ROTATION_8);
        // The head sits on the pedestal of the model, on the front side (toward the player who placed the tile).
        // The model only has 4 orientations: diagonals keep the pedestal of the previous side, the head still looks diagonally.
        Vector3f front = frontVector(dir);
        Vector3f translate = (new Vector3f(9f/16, 0, 9f/16)).mul(front).add(0,-1f/16,0);
        // Where the pedestal is for this size
        if (size == TileSize.SMALL) translate.mul(TileSize.SMALL_SCALE, 1, TileSize.SMALL_SCALE);
        else if (size == TileSize.LARGE) translate.add(0.5f, 0, 0.5f);
        // On the surface of the support (lowered or sloped), the head itself stays upright
        translate.add(0, (float) support.surfaceY(0.5 + translate.x, 0.5 + translate.z), 0);
        matrices.translate(translate.x, translate.y, translate.z);
        matrices.scale(1.0F, 1.0F, 1.0F);
        RenderLayer renderLayer = RenderLayer.getEntityTranslucent(texture);
        SkullBlockEntityRenderer.renderSkull(
                Direction.DOWN,
                180 + dir * 45,
                0.0F,
                matrices,
                vertexConsumers,
                light,
                this.model,
                renderLayer);
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
    }

    private static void rotate(MatrixStack matrices, float angle, float x, float y, float z) {
        // Through the MatrixStack so that the normal matrix is rotated as well
        matrices.multiply(ROTATION.rotationAxis(angle, x, y, z));
    }

    /**
     * Side the tile faces, snapped to the 4 orientations of the start tile model: rotation 0 = placed while looking
     * north, faces south; the rotation turns clockwise (2 = west, 4 = north, 6 = east), like the blockstate "y".
     */
    private static Vector3f frontVector(int rot) {
        return switch ((rot >> 1) & 3) {
            case 0 -> new Vector3f(0, 0, 1);
            case 1 -> new Vector3f(-1, 0, 0);
            case 2 -> new Vector3f(0, 0, -1);
            default -> new Vector3f(1, 0, 0);
        };
    }


    private void renderPicture(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Identifier texture, int direction, int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        float angle = (float) ((direction * 45) / 180f * PI);
        matrices.push();
        matrices.translate(0.5, 0, 0.5);

        Sprite sprite = getSprite(texture);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getCutout());

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float side = 1f;
        rotate(matrices, angle, 0, -1, 0);
        vertexConsumer.vertex(matrix, -side, 2/16f, side).color(r, g, b, 255).texture(sprite.getMinU(), sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, side, 2/16f, side).color(r, g, b, 255).texture(sprite.getMaxU(), sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, side, 2/16f, -side).color(r, g, b, 255).texture(sprite.getMaxU(), sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, -side, 2/16f, -side).color(r, g, b, 255).texture(sprite.getMinU(), sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        float unitUV = (sprite.getMaxV() - sprite.getMinV()) / 32f;
        rotate(matrices, (float) (PI/2f), 1, 0, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, -1/16f).color(r, g, b, 255).texture(sprite.getMinU(), sprite.getMaxV() - unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, -1/16f).color(r, g, b, 255).texture(sprite.getMaxU(), sprite.getMaxV() - unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, -2/16f).color(r, g, b, 255).texture(sprite.getMaxU(), sprite.getMinV()  + unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, -2/16f).color(r, g, b, 255).texture(sprite.getMinU(), sprite.getMinV()  + unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        rotate(matrices, (float) -PI, 1, 0, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, 2/16f).color(r, g, b, 255).texture(sprite.getMinU(), sprite.getMaxV() - unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, 2/16f).color(r, g, b, 255).texture(sprite.getMaxU(), sprite.getMaxV() - unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, 1/16f).color(r, g, b, 255).texture(sprite.getMaxU(), sprite.getMinV()  + unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, 1/16f).color(r, g, b, 255).texture(sprite.getMinU(), sprite.getMinV()  + unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        rotate(matrices, (float) PI/2, 1, 0, 0);
        rotate(matrices, (float) PI/2, 0, 0, 1);
        vertexConsumer.vertex(matrix, 1/16f, 14/16f, side).color(r, g, b, 255).texture(sprite.getMinU()  + unitUV * 2, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 2/16f, 14/16f, side).color(r, g, b, 255).texture(sprite.getMaxU() - unitUV * 29, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 2/16f, 14/16f, -side).color(r, g, b, 255).texture(sprite.getMaxU() - unitUV * 29, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 1/16f, 14/16f, -side).color(r, g, b, 255).texture(sprite.getMinU()  + unitUV * 2, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        rotate(matrices, (float) -PI, 0, 0, 1);
        vertexConsumer.vertex(matrix, -2/16f, 14/16f, side).color(r, g, b, 255).texture(sprite.getMinU()  + unitUV * 29, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -1/16f, 14/16f, side).color(r, g, b, 255).texture(sprite.getMaxU() - unitUV * 2, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -1/16f, 14/16f, -side).color(r, g, b, 255).texture(sprite.getMaxU() - unitUV * 2, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -2/16f, 14/16f, -side).color(r, g, b, 255).texture(sprite.getMinU()  + unitUV * 29, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);

        matrices.pop();
    }
}
