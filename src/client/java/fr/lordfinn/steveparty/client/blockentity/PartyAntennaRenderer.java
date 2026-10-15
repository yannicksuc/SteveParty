package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.client.screens.AntennaIcons;
import fr.lordfinn.steveparty.utils.Argb;
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
    /** The receiver and its head in light greys, tinted with the colour of the moment's family. */
    private static final Identifier TINT = Steveparty.id("textures/block/party_antenna_tint.png");
    /** The receiver switched off (no Party Controller in range). */
    private static final int OFF = 0xFF4A4A4A;
    /** How long the receiver blinks on and off while the antenna holds the party (ticks). */
    private static final int HOLD_BLINK_TICKS = 20;
    /** The dish's pulse: how much it grows, for how long (ticks). */
    private static final float PULSE = 0.12f, PULSE_TICKS = 6;
    /** The red head shows for this long after a catch (ticks); the tip then fades back to dark. */
    private static final float GLOW_TICKS = 18;

    /** This frame's state, set before the bones are drawn. */
    private float yaw, pitch, age = Float.MAX_VALUE;
    private int receiverColour, receiverLight;

    public PartyAntennaRenderer() {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("party_antenna")));
    }

    @Override
    public void render(PartyBellBlockEntity entity, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        yaw = lerpAngle(partialTick, entity.lastYaw, entity.yaw);
        pitch = MathHelper.lerp(partialTick, entity.lastPitch, entity.pitch);
        age = entity.ringTicks > 0 ? PartyBellBlockEntity.RING_TICKS - entity.ringTicks + partialTick : Float.MAX_VALUE;
        receiver(entity, packedLight);
        super.render(entity, partialTick, matrices, bufferSource, packedLight, packedOverlay);
        if (entity.getWorld() != null && WorldLabels.holdingWrench()
                && dispatcher().camera.getPos().squaredDistanceTo(entity.getPos().toCenterPos()) <= BUBBLE_RANGE * BUBBLE_RANGE)
            AntennaBubble.draw(entity, matrices, bufferSource, dispatcher());
    }

    /** Players with the Wrench in hand see what each antenna listens to, this far (blocks). */
    private static final int BUBBLE_RANGE = 16;

    private static net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher dispatcher() {
        return MinecraftClient.getInstance().getBlockEntityRenderDispatcher();
    }

    /**
     * The receiver, a light in the colour of the family of its moment (lit: it reads at night and under shaders):
     * off without a Party Controller in range; a white flash fading to its colour when it catches its moment, and when
     * clicked without the Wrench; blinking slowly while it holds the party.
     */
    private void receiver(PartyBellBlockEntity entity, int packedLight) {
        int family = AntennaIcons.colour(entity.getMoment().family());
        boolean linked = entity.getFaced() != null || entity.getWorld() == null;
        receiverLight = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        if (age != Float.MAX_VALUE) {
            float fade = MathHelper.clamp((age - GLOW_TICKS) / (PartyBellBlockEntity.RING_TICKS - GLOW_TICKS), 0, 1);
            receiverColour = lerpColour(fade, 0xFFFFFFFF, family);
        } else if (entity.blinkTicks > 0) {
            receiverColour = entity.blinkTicks % 4 < 2 ? 0xFFFFFFFF : family;
        } else if (entity.getWorld() == null) {
            // Its item: the colour of its first moment, unlit
            receiverColour = family;
            receiverLight = packedLight;
        } else if (!linked) {
            receiverColour = OFF;
            receiverLight = packedLight;
        } else if (entity.isHolding() && (entity.getWorld().getTime() / HOLD_BLINK_TICKS) % 2 == 1) {
            receiverColour = Argb.darken(family, 0.6f);
        } else {
            receiverColour = family;
        }
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
            case RECEIVER, GLOW -> {
                // The red head around the tip only shows while it catches its moment
                if (bone.getName().equals(GLOW) && age >= GLOW_TICKS) return;
                // Drawn from the grey copy of the texture, in the receiver's colour (GeckoLib takes its own buffer
                // back for the next bones: GeoRenderer#checkAndRefreshBuffer)
                RenderLayer tinted = RenderLayer.getEntityCutout(TINT);
                super.renderRecursively(poseStack, animatable, bone, tinted, bufferSource, bufferSource.getBuffer(tinted), isReRender,
                        partialTick, receiverLight, packedOverlay, bone.getName().equals(GLOW) ? Argb.lighten(receiverColour, 0.2f) : receiverColour);
                return;
            }
            default -> {
            }
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
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
