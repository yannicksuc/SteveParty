package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
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
import static fr.lordfinn.steveparty.blocks.custom.boardspaces.TileBlock.ROTATION_8;
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
    private static final Identifier textureBlow = Steveparty.id("block/tile_overlay_blow");


    public TileBlockEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new SkullEntityModel(ctx.getLayerRenderDispatcher().getModelPart(EntityModelLayers.PLAYER_HEAD));
    }

    @Override
    public void render(BoardSpaceBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BoardSpaceType tileType = entity.getCachedState().get(TILE_TYPE);
        Integer direction = entity.getCachedState().get(ROTATION_8);
        ItemStack stack = BoardSpaceClientUtils.getDisplayedCartridge(entity);
        switch (tileType) {
            case TILE_START -> renderTileStart(entity, matrices, vertexConsumers, light, stack);
            case TILE_INVENTORY_INTERACTOR -> renderInventoryInteractor(entity, matrices, vertexConsumers, light, overlay, stack, direction);
            default -> {        renderPicture(matrices, vertexConsumers, light, overlay, textureNeutral, direction);}
        }
    }

    private void renderInventoryInteractor(BoardSpaceBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, ItemStack stack, int direction) {
        Identifier texture = textureBlow;
        int color = stack.getOrDefault(COLOR, 0);
        if (color == InventoryInteractorTileBehavior.GOOD_COLOR)
            texture = textureExcited;
        else if (color == InventoryInteractorTileBehavior.BAD_COLOR)
            texture = textureBad;
        renderPicture(matrices, vertexConsumers, light, overlay, texture, direction);
    }

    private void renderTileStart(BoardSpaceBlockEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ItemStack stack) {
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
        Vector3f translate = (new Vector3f(9f/16, 0, 9f/16)).mul(frontVector(dir)).add(0,-1f/16,0);
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


    private void renderPicture(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Identifier texture, int direction) {
        float angle = (float) ((direction * 45) / 180f * PI);
        matrices.push();
        matrices.translate(0.5, 0, 0.5);

        Sprite sprite = getSprite(texture);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getCutout());

        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float side = 1f;
        rotate(matrices, angle, 0, -1, 0);
        vertexConsumer.vertex(matrix, -side, 2/16f, side).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, side, 2/16f, side).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, side, 2/16f, -side).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, -side, 2/16f, -side).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        float unitUV = (sprite.getMaxV() - sprite.getMinV()) / 32f;
        rotate(matrices, (float) (PI/2f), 1, 0, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, -1/16f).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMaxV() - unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, -1/16f).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMaxV() - unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, -2/16f).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMinV()  + unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, -2/16f).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMinV()  + unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        rotate(matrices, (float) -PI, 1, 0, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, 2/16f).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMaxV() - unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, 2/16f).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMaxV() - unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, 1/16f).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMinV()  + unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, 1/16f).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMinV()  + unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        rotate(matrices, (float) PI/2, 1, 0, 0);
        rotate(matrices, (float) PI/2, 0, 0, 1);
        vertexConsumer.vertex(matrix, 1/16f, 14/16f, side).color(255, 255, 255, 255).texture(sprite.getMinU()  + unitUV * 2, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 2/16f, 14/16f, side).color(255, 255, 255, 255).texture(sprite.getMaxU() - unitUV * 29, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 2/16f, 14/16f, -side).color(255, 255, 255, 255).texture(sprite.getMaxU() - unitUV * 29, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 1/16f, 14/16f, -side).color(255, 255, 255, 255).texture(sprite.getMinU()  + unitUV * 2, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        rotate(matrices, (float) -PI, 0, 0, 1);
        vertexConsumer.vertex(matrix, -2/16f, 14/16f, side).color(255, 255, 255, 255).texture(sprite.getMinU()  + unitUV * 29, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -1/16f, 14/16f, side).color(255, 255, 255, 255).texture(sprite.getMaxU() - unitUV * 2, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -1/16f, 14/16f, -side).color(255, 255, 255, 255).texture(sprite.getMaxU() - unitUV * 2, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -2/16f, 14/16f, -side).color(255, 255, 255, 255).texture(sprite.getMinU()  + unitUV * 29, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);

        matrices.pop();
    }
}
