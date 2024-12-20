package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.BigBookEntity;
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

    public BigBookRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
        super(new DefaultedBlockGeoModel<>(Identifier.of(Steveparty.MOD_ID, "big_book")));
    }

    @Override
    public @Nullable RenderLayer getRenderType(BigBookEntity animatable, Identifier texture,
                                               @Nullable VertexConsumerProvider bufferSource,
                                               float partialTick) {
        return RenderLayer.getEntityTranslucent(getTextureLocation(animatable));
    }

    @Override
    public void preRender(MatrixStack poseStack, BigBookEntity animatable, BakedGeoModel model, @Nullable VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, renderColor);
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
    }

    private void summonParticle(Vec3d position, World world, double distance, double color, double angularVelocity) {

        world.addParticle(ENCHANTED_CIRCULAR_PARTICLE, position.x, position.y, position.z,
                distance, color, angularVelocity);
    }

    private static float getFinalNewYaw(BigBookEntity animatable, PlayerEntity player) {
        Vec3d playerPos = player.getPos();
        Vec3d blockCenter = animatable.getPos().toCenterPos(); // Ensure center position is used
        double xDiff = playerPos.getX() - blockCenter.getX();
        double zDiff = playerPos.getZ() - blockCenter.getZ();
        return (float) ((float) Math.atan2(zDiff, xDiff) / Math.PI * 180);
    }
}
