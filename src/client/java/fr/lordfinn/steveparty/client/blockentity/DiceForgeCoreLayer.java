package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Renders the gravity (heavy) core seated in the dice forge hole, following the model animation.
 * <p>
 * If the model provides a {@value #CORE_BONE} bone, the core is drawn centered on that bone's pivot and fully
 * driven by the animations. Otherwise it is attached to the {@value #ROOT_BONE} bone: it rests in the plate hole,
 * its fall into the hole during the "core_insert" animation is computed here, then it rises to the altitude given
 * by the star fragments ({@link DiceForgeBlockEntity#getCoreAltitude}).
 * <p>
 * The core has no rotation of its own: it turns with the root bone, so the core and the forge ("observatory")
 * always share the same speed and direction.
 */
public class DiceForgeCoreLayer extends GeoRenderLayer<DiceForgeBlockEntity> {
    public static final String CORE_BONE = "core";
    public static final String ROOT_BONE = "root";
    /** Core center in root bone space (px): the 8px core sits on the plate recess bottom (y = 12). */
    private static final float CORE_REST_Y = 16f / 16f;
    /** Height (blocks) from which the core falls into the hole. */
    private static final float CORE_DROP_HEIGHT = 1.25f;
    /** Part of the insertion animation during which the core falls (the forge rises afterwards). */
    private static final float CORE_FALL_TICKS = DiceForgeBlockEntity.CORE_INSERT_TICKS / 2f;

    private final BlockState coreState = ModBlocks.GRAVITY_CORE.getDefaultState();

    public DiceForgeCoreLayer(GeoRenderer<DiceForgeBlockEntity> renderer) {
        super(renderer);
    }

    @Override
    public void renderForBone(MatrixStack poseStack, DiceForgeBlockEntity animatable, GeoBone bone, RenderLayer renderType,
                              VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick,
                              int packedLight, int packedOverlay, int renderColor) {
        if (!animatable.isActivated()) return;
        boolean hasCoreBone = getGeoModel().getBone(CORE_BONE).isPresent();
        if (!bone.getName().equals(hasCoreBone ? CORE_BONE : ROOT_BONE)) return;

        poseStack.push();
        // The bone transforms are already applied (translated back from its pivot): move to the pivot
        poseStack.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
        if (!hasCoreBone) {
            poseStack.translate(0, getCoreHeight(animatable, partialTick), 0);
        }

        // gravity_core block model: 8x8x8 cube spanning x/z 4..12, y 8..16 → center it on the origin
        poseStack.translate(-0.5f, -0.75f, -0.5f);
        MinecraftClient.getInstance().getBlockRenderManager().renderBlockAsEntity(coreState, poseStack, bufferSource,
                LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
        poseStack.pop();

        // Give GeckoLib its buffer back (see GeoRenderLayer#renderForBone)
        bufferSource.getBuffer(renderType);
    }

    /** @return height of the core center above the block (blocks): resting in the hole, falling, or floating up */
    public static float getCoreHeight(DiceForgeBlockEntity animatable, float partialTick) {
        return CORE_REST_Y + getFallOffset(animatable, partialTick) + animatable.getCoreAltitude(partialTick);
    }

    /** Falling core during the first half of the insertion animation (ease-in, like a heavy object). */
    private static float getFallOffset(DiceForgeBlockEntity animatable, float partialTick) {
        if (animatable.getWorld() == null) return 0f;
        float elapsed = (animatable.getWorld().getTime() - animatable.getActivationTime()) + partialTick;
        if (elapsed >= CORE_FALL_TICKS || elapsed < 0) return 0f;
        float t = MathHelper.clamp(elapsed / CORE_FALL_TICKS, 0f, 1f);
        return CORE_DROP_HEIGHT * (1f - t * t);
    }
}
