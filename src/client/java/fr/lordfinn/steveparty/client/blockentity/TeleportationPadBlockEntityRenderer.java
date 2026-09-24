package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.TeleportationPadBlockEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import java.util.Map;
import java.util.WeakHashMap;

import static fr.lordfinn.steveparty.particles.ModParticles.ENCHANTED_CIRCULAR_PARTICLE;

public class TeleportationPadBlockEntityRenderer extends GeoBlockRenderer<TeleportationPadBlockEntity> {

    /** ~0.1 per frame at 60 FPS, expressed per tick. */
    private static final float SMOOTHING_PER_TICK = 0.27F;
    private static final Map<TeleportationPadBlockEntity, PadState> STATES = new WeakHashMap<>();

    public TeleportationPadBlockEntityRenderer(BlockEntityRendererFactory.Context ignoredCtx) {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("big_book")));
    }

    @Override
    public @Nullable RenderLayer getRenderType(TeleportationPadBlockEntity animatable, Identifier texture,
                                               @Nullable VertexConsumerProvider bufferSource,
                                               float partialTick) {
        return RenderLayer.getEntityTranslucent(getTextureLocation(animatable));
    }

    @Override
    public void preRender(MatrixStack poseStack, TeleportationPadBlockEntity animatable, BakedGeoModel model, @Nullable VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, renderColor);
        World world = animatable.getWorld();
        PadState state = STATES.computeIfAbsent(animatable, k -> new PadState());
        if (world != null && state.lastTick != world.getTime()) {
            // Player lookup, smoothing and particles once per tick instead of once per frame
            state.lastTick = world.getTime();
            tickPad(animatable, world, state);
        }
        // The baked model is shared by every pad: always write this pad's own angle
        float yaw = state.initialized ? MathHelper.lerpAngleDegrees(partialTick, state.prevYaw, state.yaw) : 0.0F;
        model.getBone("bone6").ifPresent(bone -> bone.setRotY((float) Math.toRadians(-yaw)));
    }

    private void tickPad(TeleportationPadBlockEntity animatable, World world, PadState state) {
        state.prevYaw = state.yaw;
        PlayerEntity player = world.getClosestPlayer(animatable.getPos().getX(), animatable.getPos().getY(), animatable.getPos().getZ(), 6, false);
        if (player == null) return;

        float targetYaw = getFinalNewYaw(animatable, player);
        if (!state.initialized) {
            state.yaw = state.prevYaw = targetYaw;
            state.initialized = true;
        } else {
            // Shortest path (handles the -180/180 wrap)
            state.yaw = MathHelper.lerpAngleDegrees(SMOOTHING_PER_TICK, state.yaw, targetYaw);
        }
        animatable.currentYaw = state.yaw;

        if (!animatable.book.isEmpty() && System.currentTimeMillis() > animatable.lastTime + 300) {
            double distance = player.getPos().distanceTo(animatable.getPos().toCenterPos());
            double particleSpeed1 = 0.01 + (Math.max(0, 6 - distance) / 6) * (0.4 - 0.01);

            int darkColor = 0xb16714;
            int mediumColor = 0xc59138;
            int lightColor = 0xdec253;

            Vec3d center = animatable.getPos().toCenterPos();
            summonParticle(center.add(0,-0.25,0), world, 0.8, lightColor, particleSpeed1);
            summonParticle(center.add(0,0,0), world, 0.8, lightColor, particleSpeed1);
            summonParticle(center.add(0,0.25,0), world, 0.8, lightColor, particleSpeed1);
            summonParticle(center.add(0,0.5,0), world, 0.8, mediumColor, particleSpeed1);
            summonParticle(center.add(0,0.75,0), world, 0.8, mediumColor, particleSpeed1);
            summonParticle(center.add(0,1,0), world, 0.8, darkColor, particleSpeed1);
            summonParticle(center.add(0,1.25,0), world, 1.2, mediumColor, particleSpeed1);
            summonParticle(center.add(0,1.5,0), world, 1.2, lightColor, particleSpeed1);
            summonParticle(center.add(0,1.75,0), world, 1.2, lightColor, particleSpeed1);
            if (distance < 3) {
                summonParticle(center.add(0,1.25,0), world, 1.2, mediumColor, particleSpeed1);
                summonParticle(center.add(0,1.5,0), world, 1.2, lightColor, particleSpeed1);
                summonParticle(center.add(0,1.75,0), world, 1.2, lightColor, particleSpeed1);
            }
            animatable.lastTime = System.currentTimeMillis();
        }
    }

    /** Client-only animation state, one per pad (the renderer and its baked model are shared). */
    private static final class PadState {
        long lastTick = Long.MIN_VALUE;
        boolean initialized;
        float prevYaw;
        float yaw;
    }

    private void summonParticle(Vec3d position, World world, double distance, double color, double angularVelocity) {

        world.addParticle(ENCHANTED_CIRCULAR_PARTICLE, position.x, position.y, position.z,
                distance, color, angularVelocity);
    }

    private static float getFinalNewYaw(TeleportationPadBlockEntity animatable, PlayerEntity player) {
        Vec3d playerPos = player.getPos();
        Vec3d blockCenter = animatable.getPos().toCenterPos(); // Ensure center position is used
        double xDiff = playerPos.getX() - blockCenter.getX();
        double zDiff = playerPos.getZ() - blockCenter.getZ();
        return (float) ((float) Math.atan2(zDiff, xDiff) / Math.PI * 180);
    }
}
