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
import static org.joml.Math.lerp;

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
    public void render(BigBookEntity animatable, float partialTick, MatrixStack poseStack, VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        World world = animatable.getWorld();
        PlayerEntity player = null;
        if (world != null)
            player = world.getClosestPlayer(animatable.getPos().getX(), animatable.getPos().getY(), animatable.getPos().getZ(), 6, false);
        if (player != null) {
            float targetYaw = getFinalNewYaw(animatable, player);

            // Smooth transition for the yaw
            if (animatable.currentYaw == null) {
                animatable.currentYaw = targetYaw; // Initialize if null
            } else {
                float smoothingFactor = 0.1f; // Adjust for smoother/slower transition
                animatable.currentYaw = lerp(animatable.currentYaw, targetYaw, smoothingFactor);
            }

            model.getBone("bone6").ifPresent(bone -> bone.setRotY((float) Math.toRadians(-animatable.currentYaw)));
            if (!animatable.catalogue.isEmpty() && System.currentTimeMillis()> animatable.lastTime + 300) {
                double distance = player.getPos().distanceTo(animatable.getPos().toCenterPos());
                double particleSpeed1 = 0.01 + (Math.max(0, 6 - distance) / 6) * (0.4 - 0.01);

                int darkColor = 0xb16714;
                int mediumColor = 0xc59138;
                int lightColor = 0xdec253;

                summonParticle(animatable.getPos().toCenterPos().add(0,-0.25,0), world, 0.8, lightColor, particleSpeed1);
                summonParticle(animatable.getPos().toCenterPos().add(0,0,0), world, 0.8, lightColor, particleSpeed1);
                summonParticle(animatable.getPos().toCenterPos().add(0,0.25,0), world, 0.8, lightColor, particleSpeed1);
                summonParticle(animatable.getPos().toCenterPos().add(0,0.5,0), world, 0.8, mediumColor, particleSpeed1);
                summonParticle(animatable.getPos().toCenterPos().add(0,0.75,0), world, 0.8, mediumColor, particleSpeed1);
                summonParticle(animatable.getPos().toCenterPos().add(0,1,0), world, 0.8, darkColor, particleSpeed1);
                summonParticle(animatable.getPos().toCenterPos().add(0,1.25,0), world, 1.2, mediumColor, particleSpeed1);
                summonParticle(animatable.getPos().toCenterPos().add(0,1.5,0), world, 1.2, lightColor, particleSpeed1);
                summonParticle(animatable.getPos().toCenterPos().add(0,1.75,0), world, 1.2, lightColor, particleSpeed1);
                if (distance < 3) {
                    summonParticle(animatable.getPos().toCenterPos().add(0,1.25,0), world, 1.2, mediumColor, particleSpeed1);
                    summonParticle(animatable.getPos().toCenterPos().add(0,1.5,0), world, 1.2, lightColor, particleSpeed1);
                    summonParticle(animatable.getPos().toCenterPos().add(0,1.75,0), world, 1.2, lightColor, particleSpeed1);
                }
                animatable.lastTime = System.currentTimeMillis();
            }
        }
        super.render(animatable, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private static int getInterpolatedColor(double distance) {
        float factor = (float) Math.max(0, Math.min(1, (6 - distance) / 6));

        // Define your colors
        //int[] colors = {0x4CBAE7, 0xC080FB, 0xFF82E6, 0xFF1087};
        int[] colors = {0xede553, 0xd9c230, 0xe8b600};

        // Determine the current color range
        int colorIndex = (int) (factor * (colors.length - 1));
        float subFactor = factor * (colors.length - 1) - colorIndex;

        // Interpolate between the two closest colors
        return interpolateColor(colors[colorIndex], colors[Math.min(colorIndex + 1, colors.length - 1)], subFactor);
    }

    private void summonParticle(Vec3d position, World world, double distance, double color, double angularVelocity) {

        world.addParticle(ENCHANTED_CIRCULAR_PARTICLE, position.x, position.y, position.z,
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
        Vec3d blockCenter = animatable.getPos().toCenterPos(); // Ensure center position is used
        double xDiff = playerPos.getX() - blockCenter.getX();
        double zDiff = playerPos.getZ() - blockCenter.getZ();
        return (float) ((float) Math.atan2(zDiff, xDiff) / Math.PI * 180);
    }
}
