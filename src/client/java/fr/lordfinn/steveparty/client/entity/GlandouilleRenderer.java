package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleTowers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.joml.Quaternionf;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Draws a Glandouille at its variant's size. A tower sways as a whole while its bottom one walks: every one above the
 * bottom is turned about the bottom one's feet, by an angle that grows with the tower's height and its bottom one's
 * pace (the young one's towers sway the most), so the top moves the most. Only a look: the riders stay where riding
 * puts them.
 */
public class GlandouilleRenderer extends GeoEntityRenderer<GlandouilleEntity> {
    private final Quaternionf rotation = new Quaternionf();

    public GlandouilleRenderer(EntityRendererFactory.Context context) {
        super(context, new GlandouilleModel());
        this.shadowRadius = 0.35f;
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, MatrixStack poseStack, GlandouilleEntity glandouille,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = glandouille.getScaleFactor();
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, glandouille, model, isReRender,
                partialTick, packedLight, packedOverlay);
    }

    @Override
    public void render(GlandouilleEntity glandouille, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        GlandouilleEntity bottom = glandouille.getVehicle() instanceof GlandouilleEntity ? GlandouilleTowers.bottom(glandouille) : null;
        if (bottom == null) {
            super.render(glandouille, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        float sway = MathHelper.lerp(partialTick, bottom.prevSway, bottom.sway);
        int height = Math.min(GlandouilleTowers.height(bottom), 24);
        // degrees: a little more per Glandouille up there (each one's lean adds to the ones below it)
        float amplitude = sway * (2.0f + 0.45f * height);
        float time = bottom.age + partialTick;
        float roll = amplitude * MathHelper.sin(time * 0.32f) * MathHelper.RADIANS_PER_DEGREE;
        float pitch = 0.35f * amplitude * MathHelper.cos(time * 0.21f) * MathHelper.RADIANS_PER_DEGREE;
        if (Math.abs(roll) + Math.abs(pitch) < 1.0E-4f) {
            super.render(glandouille, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        double dx = MathHelper.lerp(partialTick, glandouille.prevX, glandouille.getX()) - MathHelper.lerp(partialTick, bottom.prevX, bottom.getX());
        double dy = MathHelper.lerp(partialTick, glandouille.prevY, glandouille.getY()) - MathHelper.lerp(partialTick, bottom.prevY, bottom.getY());
        double dz = MathHelper.lerp(partialTick, glandouille.prevZ, glandouille.getZ()) - MathHelper.lerp(partialTick, bottom.prevZ, bottom.getZ());
        float yaw = MathHelper.lerpAngleDegrees(partialTick, bottom.prevBodyYaw, bottom.bodyYaw) * MathHelper.RADIANS_PER_DEGREE;
        // the bottom one's forward (roll about it: side to side) and side (pitch about it: back and forth)
        float fx = -MathHelper.sin(yaw), fz = MathHelper.cos(yaw);
        poseStack.push();
        poseStack.translate(-dx, -dy, -dz);
        rotation.identity().rotateAxis(roll, fx, 0, fz).rotateAxis(pitch, fz, 0, -fx);
        poseStack.multiply(rotation);
        poseStack.translate(dx, dy, dz);
        super.render(glandouille, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
    }
}
