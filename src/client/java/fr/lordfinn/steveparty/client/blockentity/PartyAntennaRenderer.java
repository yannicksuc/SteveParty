package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedBlockGeoModel;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * The Party Antenna's head (its plate, box and mast are a plain block model): LordFinn's model, cutout with back faces
 * culled. Its head turns as {@link PartyBellBlockEntity} says (yaw, a little pitch). Its receiver is dark red at rest;
 * when the antenna catches a moment, it lights (full light, as the Party Controller's torch: the red head around it,
 * six planes facing in, so only the far ones show behind the tip) and its dish gives one quick pulse.
 * <p>
 * Everything is opaque and writes depth: no see-through pass to order (minecraft-rendering).
 */
public class PartyAntennaRenderer extends GeoBlockRenderer<PartyBellBlockEntity> {
    private static final String HEAD = "tete", DISH = "parabole", RECEIVER = "recepteur", GLOW = "recepteur_lueur";
    /** The receiver at rest: its light red tip darkened. */
    private static final int DARK = 0xFF642020;
    /** The dish's pulse: how much it grows, for how long (ticks). */
    private static final float PULSE = 0.12f, PULSE_TICKS = 6;
    /** The red head shows for this long after a catch (ticks); the tip then fades back to dark. */
    private static final float GLOW_TICKS = 18;

    /** This frame's state, set before the bones are drawn. */
    private float yaw, pitch, age = Float.MAX_VALUE;

    public PartyAntennaRenderer() {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("party_antenna")));
    }

    @Override
    public void render(PartyBellBlockEntity entity, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        yaw = lerpAngle(partialTick, entity.lastYaw, entity.yaw);
        pitch = MathHelper.lerp(partialTick, entity.lastPitch, entity.pitch);
        age = entity.ringTicks > 0 ? PartyBellBlockEntity.RING_TICKS - entity.ringTicks + partialTick : Float.MAX_VALUE;
        super.render(entity, partialTick, matrices, bufferSource, packedLight, packedOverlay);
    }

    private static float lerpAngle(float delta, float start, float end) {
        return start + delta * MathHelper.wrapDegrees((end - start) * MathHelper.DEGREES_PER_RADIAN) * MathHelper.RADIANS_PER_DEGREE;
    }

    /** Its head is turned by {@link #yaw}, never by a facing. */
    @Override
    protected void rotateBlock(Direction facing, MatrixStack poseStack) {
    }

    @Override
    public @Nullable RenderLayer getRenderType(PartyBellBlockEntity animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityCutout(texture);
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, PartyBellBlockEntity animatable, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        switch (bone.getName()) {
            case HEAD -> {
                bone.setRotY(yaw);
                bone.setRotX(pitch);
            }
            case DISH -> {
                float scale = age < PULSE_TICKS ? 1 + PULSE * MathHelper.sin(MathHelper.PI * age / PULSE_TICKS) : 1;
                bone.setScaleX(scale);
                bone.setScaleY(scale);
                bone.setScaleZ(scale);
            }
            case RECEIVER -> {
                if (age == Float.MAX_VALUE) colour = DARK;
                else {
                    packedLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
                    float fade = MathHelper.clamp((age - GLOW_TICKS) / (PartyBellBlockEntity.RING_TICKS - GLOW_TICKS), 0, 1);
                    colour = lerpColour(fade, 0xFFFFFFFF, DARK);
                }
            }
            case GLOW -> {
                if (age >= GLOW_TICKS) return;
                packedLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
                colour = glowColour(animatable.getMoment());
            }
            default -> {
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    /** The tint of the lit head for the moment it listens to: none yet, the texture's own red for every moment. */
    private static int glowColour(PartyMoment moment) {
        return 0xFFFFFFFF;
    }

    private static int lerpColour(float delta, int from, int to) {
        int r = (int) MathHelper.lerp(delta, from >> 16 & 0xFF, to >> 16 & 0xFF);
        int g = (int) MathHelper.lerp(delta, from >> 8 & 0xFF, to >> 8 & 0xFF);
        int b = (int) MathHelper.lerp(delta, from & 0xFF, to & 0xFF);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** The head reaches above the block. */
    @Override
    public boolean rendersOutsideBoundingBox(PartyBellBlockEntity blockEntity) {
        return true;
    }

    /**
     * The head's yaw in its item: three quarters, its lit outer cloth toward the inventory's light and its red receiver
     * at the side (seen from the front, the dish's shaded inside reads as a dark square).
     */
    private static final float ITEM_YAW = 0;
    private static PartyAntennaRenderer itemRenderer;
    private static PartyBellBlockEntity itemAntenna;

    /** Its item: the block model and the head at rest, its receiver dark. */
    public static final BuiltinItemRendererRegistry.DynamicItemRenderer ITEM = (stack, mode, matrices, vertexConsumers, light, overlay) -> {
        BlockState state = ModBlocks.PARTY_BELL.getDefaultState();
        BlockRenderManager blocks = MinecraftClient.getInstance().getBlockRenderManager();
        blocks.getModelRenderer().render(matrices.peek(), vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()), state,
                blocks.getModel(state), 1, 1, 1, light, overlay);
        if (itemRenderer == null) {
            itemRenderer = new PartyAntennaRenderer();
            itemAntenna = new PartyBellBlockEntity(BlockPos.ORIGIN, state);
            itemAntenna.yaw = itemAntenna.lastYaw = ITEM_YAW;
        }
        itemRenderer.render(itemAntenna, 0, matrices, vertexConsumers, light, overlay);
    };
}
