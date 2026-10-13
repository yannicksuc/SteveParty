package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.utils.InfiniteMark;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A Party Controller whose storage is infinite says so at a glance: the gold « ∞ » of the mod (see {@link InfiniteMark})
 * floating slowly over it with its shadow, facing the camera, within {@link #DISTANCE} blocks. Opaque text that writes
 * depth, drawn in the block entity pass like the other labels ({@link WorldLabels}): nothing see-through.
 */
public class PartyControllerInfinityRenderer implements BlockEntityRenderer<PartyControllerEntity> {
    /** Seen from this close (blocks). */
    private static final double DISTANCE = 24;
    /** Its size: a block per this many text pixels. */
    private static final float SCALE = 1f / 22f;
    private final BlockEntityRenderDispatcher dispatcher;

    public PartyControllerInfinityRenderer(BlockEntityRendererFactory.Context context) {
        this.dispatcher = context.getRenderDispatcher();
    }

    @Override
    public void render(PartyControllerEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers,
                       int light, int overlay) {
        if (!entity.isInfiniteBank() || entity.getWorld() == null) return;
        Vec3d camera = dispatcher.camera.getPos();
        if (camera.squaredDistanceTo(entity.getPos().toCenterPos()) > DISTANCE * DISTANCE) return;
        float time = entity.getWorld().getTime() + tickDelta;
        double bob = 0.06 * MathHelper.sin(time * 0.05f);
        // Its shadow keeps it readable on leaves and sky
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        Text glyph = Text.literal(InfiniteMark.GLYPH);
        matrices.push();
        matrices.translate(0.5, 1.9 + bob, 0.5);
        matrices.multiply(dispatcher.camera.getRotation());
        matrices.scale(SCALE, -SCALE, SCALE);
        font.draw(glyph, -font.getWidth(glyph) / 2f, -4.5f, InfiniteMark.COLOR, true, matrices.peek().getPositionMatrix(),
                consumers, TextRenderer.TextLayerType.NORMAL, 0, LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    @Override
    public boolean rendersOutsideBoundingBox(PartyControllerEntity entity) {
        return true;
    }
}
