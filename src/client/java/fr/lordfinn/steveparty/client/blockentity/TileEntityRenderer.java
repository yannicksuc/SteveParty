package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.render.block.entity.SkullBlockEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.entity.model.SkullEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import org.joml.Vector3f;

import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpace.TILE_TYPE;
import static fr.lordfinn.steveparty.components.ModComponents.TB_START_OWNER;

public class TileEntityRenderer implements BlockEntityRenderer<BoardSpaceEntity> {

    private final SkullEntityModel model;

    public TileEntityRenderer(BlockEntityRendererFactory.Context ctx) {
        this.model = new SkullEntityModel(ctx.getLayerRenderDispatcher().getModelPart(EntityModelLayers.PLAYER_HEAD));
    }



    @Override
    public void render(BoardSpaceEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BoardSpaceType tileType = entity.getCachedState().get(TILE_TYPE);
        ItemStack stack = entity.getActiveTileBehaviorItemStack();
        String owner = stack.get(TB_START_OWNER);
        if (tileType != BoardSpaceType.TILE_START || owner == null) return;

        // Validate UUID
        UUID ownerUUID;
        try {
            ownerUUID = UUID.fromString(owner);
        } catch (IllegalArgumentException e) {
            Steveparty.LOGGER.error("Invalid UUID: {}", owner);
            return;
        }

        Identifier texture = SkinUtils.getPlayerSkin(ownerUUID);

        if (texture == null) {
            Steveparty.LOGGER.error("Failed to fetch texture for UUID: {}", ownerUUID);
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
}
