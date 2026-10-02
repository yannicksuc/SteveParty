package fr.lordfinn.steveparty.client.telescope;

import fr.lordfinn.steveparty.blocks.custom.TelescopeBlock;
import fr.lordfinn.steveparty.blocks.custom.TelescopeBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationPropertyHelper;

/**
 * Draws the Telescope: tripod and tube the way it was placed. While a player looks through it, its tube follows his
 * eyes (the look every client knows of him), turning to them and back to rest as he comes and goes (TelescopePoses).
 */
public class TelescopeBlockEntityRenderer implements BlockEntityRenderer<TelescopeBlockEntity> {
    private final TelescopeModel model = new TelescopeModel();
    private final float[] ease = new float[1];

    public TelescopeBlockEntityRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(TelescopeBlockEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                       int light, int overlay) {
        float yaw = RotationPropertyHelper.toDegrees(entity.getCachedState().get(TelescopeBlock.ROTATION));
        float tubeYaw = yaw, tubePitch = TelescopeModel.REST_PITCH;
        PlayerEntity watcher = TelescopePoses.watcherOf(entity.getPos(), ease, tickDelta);
        if (watcher != null) {
            tubeYaw = yaw + MathHelper.wrapDegrees(watcher.getYaw(tickDelta) - yaw) * ease[0];
            tubePitch = MathHelper.lerp(ease[0], tubePitch, watcher.getPitch(tickDelta));
        }
        matrices.push();
        matrices.translate(0.5f, 0f, 0.5f);
        // His own eye is in the tube: the player looking through it in first person is not shown it
        MinecraftClient client = MinecraftClient.getInstance();
        boolean inside = TelescopeClient.isWatching(entity.getPos()) && client.options.getPerspective().isFirstPerson();
        model.render(matrices, vertexConsumers, light, overlay, yaw, tubeYaw, tubePitch, !inside);
        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(TelescopeBlockEntity entity) {
        return true;
    }

    @Override
    public int getRenderDistance() {
        return 96;
    }
}
