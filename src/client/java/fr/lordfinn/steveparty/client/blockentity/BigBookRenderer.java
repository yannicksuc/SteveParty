package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.BigBookEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import static fr.lordfinn.steveparty.particles.ModParticles.ENCHANTED_CIRCULAR_PARTICLE;

public class BigBookRenderer extends GeoBlockRenderer<BigBookEntity> {

    public BigBookRenderer(BlockEntityRendererFactory.Context ctx) {
        super(new DefaultedBlockGeoModel<>(Identifier.of(Steveparty.MOD_ID, "big_book")));
    }

    @Override
    public @Nullable RenderLayer getRenderType(BigBookEntity animatable, Identifier texture,
                                               @Nullable VertexConsumerProvider bufferSource,
                                               float partialTick) {
        return RenderLayer.getEntityTranslucent(getTextureLocation(animatable));
    }

    @Override
    public void renderFinal(MatrixStack poseStack, BigBookEntity animatable, BakedGeoModel model, VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        World world = animatable.getWorld();
        PlayerEntity player = null;
        if (world != null)
            player = world.getClosestPlayer(animatable.getPos().getX(), animatable.getPos().getY(), animatable.getPos().getZ(), 6, false);
        if (player != null) {
            float finalNewYaw = getFinalNewYaw(animatable, player);
            model.getBone("bone").ifPresent(bone -> bone.setRotY((float) Math.toRadians(-finalNewYaw)));
            if (!animatable.catalogue.isEmpty() && System.currentTimeMillis()> animatable.lastTime + 150) {
                double distance = player.getPos().distanceTo(animatable.getPos().toCenterPos());
                double particleSpeed1 = 0.01 + (Math.max(0, 6 - distance) / 6) * (0.25 - 0.01); // Range: 0.05 to 0.5
                double particleSpeed2 = 0.01 + (Math.max(0, 6 - distance) / 6) * (0.35 - 0.01); // Range: 0.05 to 0.6
                double particleSpeed3 = 0.01 + (Math.max(0, 6 - distance) / 6) * (0.2 - 0.01); // Range: 0.05 to 0.6
                int interpolatedColor = getInterpolatedColor(distance);

                summonParticle(animatable.getPos().toCenterPos().add(0,1.4,0), world, 1.7, interpolatedColor, particleSpeed1);
                if (distance < 4)
                    summonParticle(animatable.getPos().toCenterPos(), world, 1.2, interpolatedColor, particleSpeed2);
                if (distance < 2)
                    summonParticle(animatable.getPos().toCenterPos().add(0,0.7,0), world, 2.5, interpolatedColor,  particleSpeed3);
                animatable.lastTime = System.currentTimeMillis();
            }
        }
        super.renderFinal(poseStack, animatable, model, bufferSource, buffer, partialTick, packedLight, packedOverlay, renderColor);
    }

    private static int getInterpolatedColor(double distance) {
        float factor = (float) Math.max(0, Math.min(1, (6 - distance) / 6));

        // Define your colors
        int[] colors = {0x4CBAE7, 0xC080FB, 0xFF82E6, 0xFF1087};

        // Determine the current color range
        int colorIndex = (int) (factor * (colors.length - 1));
        float subFactor = factor * (colors.length - 1) - colorIndex;

        // Interpolate between the two closest colors
        return interpolateColor(colors[colorIndex], colors[Math.min(colorIndex + 1, colors.length - 1)], subFactor);
    }

    private void summonParticle(Vec3d position, World world, double distance, double color, double angularVelocity) {

        world.addImportantParticle(ENCHANTED_CIRCULAR_PARTICLE, position.x, position.y, position.z,
                distance, color, angularVelocity);
    }

    public static int interpolateColor(int color1, int color2, float factor) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;

        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;

        int r = (int) (r1 + (r2 - r1) * factor);
        int g = (int) (g1 + (g2 - g1) * factor);
        int b = (int) (b1 + (b2 - b1) * factor);

        return (r << 16) | (g << 8) | b;
    }

    private static float getFinalNewYaw(BigBookEntity animatable, PlayerEntity player) {
        Vec3d playerPos = player.getPos();
        double xDiff = playerPos.getX() - (animatable.getPos().getX() + 0.5);
        double zDiff = playerPos.getZ() - (animatable.getPos().getZ() + 0.5);
        return (float) ((float) Math.atan2(zDiff, xDiff) / Math.PI * 180);
    }
}
