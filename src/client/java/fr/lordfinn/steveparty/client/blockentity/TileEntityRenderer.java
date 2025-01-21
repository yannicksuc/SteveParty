package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import fr.lordfinn.steveparty.components.PersistentInventoryComponent;
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
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpace.TILE_TYPE;
import static fr.lordfinn.steveparty.components.ModComponents.*;
import static java.lang.Math.PI;

public class TileEntityRenderer implements BlockEntityRenderer<BoardSpaceEntity> {

    private final SkullEntityModel model;
    private final Identifier textureBad = Identifier.of(Steveparty.MOD_ID, "block/tile_overlay_angry");
    private final Identifier textureNeutral = Identifier.of(Steveparty.MOD_ID, "block/tile_overlay_neutral");
    private final Identifier textureExcited = Identifier.of(Steveparty.MOD_ID, "block/tile_overlay_excited");
    private final Identifier textureBlow = Identifier.of(Steveparty.MOD_ID, "block/tile_overlay_blow");


    public TileEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new SkullEntityModel(ctx.getLayerRenderDispatcher().getModelPart(EntityModelLayers.PLAYER_HEAD));
    }

    @Override
    public void render(BoardSpaceEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BoardSpaceType tileType = entity.getCachedState().get(TILE_TYPE);
        Direction direction = entity.getCachedState().get(Properties.HORIZONTAL_FACING);
        ItemStack stack = entity.getActiveTileBehaviorItemStack();
        switch (tileType) {
            case TILE_START -> renderTileStart(entity, matrices, vertexConsumers, light, stack);
            case TILE_INVENTORY_INTERACTOR -> renderInventoryInteractor(entity, matrices, vertexConsumers, light, overlay, stack, direction);
            default -> {        renderPicture(matrices, vertexConsumers, light, overlay, textureNeutral, direction);}
        }
    }

    private void renderInventoryInteractor(BoardSpaceEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, ItemStack stack, Direction direction) {
        Identifier texture = textureBlow;
        PersistentInventoryComponent inventory = stack.get(INVENTORY_CARTRIDGE_COMPONENT);
        if (inventory != null) {
            ItemStack item = inventory.getStack(0);
            if (item != null) {
                boolean isNegative = item.getOrDefault(IS_NEGATIVE, false);
                texture = isNegative ? textureBad : textureExcited;
            }
        }
        renderPicture(matrices, vertexConsumers, light, overlay, texture, direction);
    }

    private void renderTileStart(BoardSpaceEntity entity, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, ItemStack stack) {
        // Validate UUID
        String owner = stack.get(TB_START_OWNER);
        if (owner == null || owner.isEmpty()) return;
        UUID ownerUUID;
        try {
            ownerUUID = UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            //Steveparty.LOGGER.error("Invalid UUID: {}", owner);
            return;
        }

        Identifier texture = SkinUtils.getPlayerSkin(ownerUUID);

        if (texture == null) {
            //Steveparty.LOGGER.error("Failed to fetch texture for UUID: {}", ownerUUID);
            return;
        }

        matrices.push();
        Direction dir = entity.getCachedState().get(Properties.HORIZONTAL_FACING);
        Vector3f translate = (new Vector3f(9f/16, 0, 9f/16)).mul(dir.getUnitVector()).add(0,-1f/16,0);
        matrices.translate(translate.x, translate.y, translate.z);
        matrices.scale(1.0F, 1.0F, 1.0F);
        RenderLayer renderLayer = RenderLayer.getEntityTranslucent(texture);
        SkullBlockEntityRenderer.renderSkull(
                Direction.DOWN,
                180 + dir.asRotation(),
                0.0F,
                matrices,
                vertexConsumers,
                light,
                this.model,
                renderLayer);
        matrices.pop();
    }


    private void renderPicture(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay, Identifier texture, Direction direction) {
        float angle = (float) (direction.asRotation() / 180f * PI);
        matrices.push();
        matrices.translate(0.5, 0, 0.5);

        // Bind the texture
        Sprite sprite = MinecraftClient.getInstance().getSpriteAtlas(PlayerScreenHandler.BLOCK_ATLAS_TEXTURE).apply(texture);

        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getCutout());

        // Define your cube's geometry and UVs
        // Here is an example of rendering a simple quad
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float side = 1f;
        matrix.rotate(angle, 0, -1, 0);
        vertexConsumer.vertex(matrix, -side, 2/16f, side).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, side, 2/16f, side).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, side, 2/16f, -side).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        vertexConsumer.vertex(matrix, -side, 2/16f, -side).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, -1, 0);
        float unitUV = (sprite.getMaxV() - sprite.getMinV()) / 32f;
        matrix.rotate((float) (PI/2f), 1, 0, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, -1/16f).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMaxV() - unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, -1/16f).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMaxV() - unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, -2/16f).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMinV()  + unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, -2/16f).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMinV()  + unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        matrix.rotate((float) -PI, 1, 0, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, 2/16f).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMaxV() - unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, 2/16f).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMaxV() - unitUV * 29).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, side, 14/16f, 1/16f).color(255, 255, 255, 255).texture(sprite.getMaxU(), sprite.getMinV()  + unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -side, 14/16f, 1/16f).color(255, 255, 255, 255).texture(sprite.getMinU(), sprite.getMinV()  + unitUV * 2).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        matrix.rotate((float) PI/2, 1, 0, 0);
        matrix.rotate((float) PI/2,  0, 0, 1);
        vertexConsumer.vertex(matrix, 1/16f, 14/16f, side).color(255, 255, 255, 255).texture(sprite.getMinU()  + unitUV * 2, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 2/16f, 14/16f, side).color(255, 255, 255, 255).texture(sprite.getMaxU() - unitUV * 29, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 2/16f, 14/16f, -side).color(255, 255, 255, 255).texture(sprite.getMaxU() - unitUV * 29, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, 1/16f, 14/16f, -side).color(255, 255, 255, 255).texture(sprite.getMinU()  + unitUV * 2, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        matrix.rotate((float) -PI,  0, 0, 1);
        vertexConsumer.vertex(matrix, -2/16f, 14/16f, side).color(255, 255, 255, 255).texture(sprite.getMinU()  + unitUV * 29, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -1/16f, 14/16f, side).color(255, 255, 255, 255).texture(sprite.getMaxU() - unitUV * 2, sprite.getMaxV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -1/16f, 14/16f, -side).color(255, 255, 255, 255).texture(sprite.getMaxU() - unitUV * 2, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);
        vertexConsumer.vertex(matrix, -2/16f, 14/16f, -side).color(255, 255, 255, 255).texture(sprite.getMinU()  + unitUV * 29, sprite.getMinV()).light(light).overlay(overlay).normal(entry, 0, 1, 0);

        matrices.pop();
    }
}
