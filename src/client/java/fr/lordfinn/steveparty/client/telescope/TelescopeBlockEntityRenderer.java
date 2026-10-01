package fr.lordfinn.steveparty.client.telescope;

import fr.lordfinn.steveparty.blocks.custom.TelescopeBlock;
import fr.lordfinn.steveparty.blocks.custom.TelescopeBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationPropertyHelper;

/**
 * Draws the Telescope: tripod and tube the way it was placed. On the client of a player looking through it, its tube
 * follows his eyes (nothing is sent: the others keep seeing it at rest).
 */
public class TelescopeBlockEntityRenderer implements BlockEntityRenderer<TelescopeBlockEntity> {
    private final TelescopeModel model = new TelescopeModel();

    public TelescopeBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(TelescopeBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay) {
        float yaw = RotationPropertyHelper.toDegrees(entity.getCachedState().get(TelescopeBlock.ROTATION));
        float tubeYaw = yaw, tubePitch = TelescopeModel.REST_PITCH;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && TelescopeClient.isWatching(entity.getPos())) {
            tubeYaw = client.player.getYaw(tickDelta);
            tubePitch = client.player.getPitch(tickDelta);
        }
        matrices.push();
        matrices.translate(0.5f, 0f, 0.5f);
        model.render(matrices, vertexConsumers, light, overlay, yaw, tubeYaw, tubePitch);
        matrices.pop();
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
