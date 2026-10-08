package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlock;
import fr.lordfinn.steveparty.blocks.custom.frousseux.FrousseuxCandleHolderBlockEntity;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.texture.AnimatableTexture;

/**
 * The candle holder, drawn whole here as skulls and banners are (it faces 16 ways, a block model turns by quarters
 * only): its saucer if it has one (the Candle Saucer's model, square to the world), its candle's block model (the
 * candle and its fists; FrousseuxCandleHolderBlock) turned its way, its pool tinted its candle's colour, and its flame,
 * the only thing of it that moves. The Frousseux's own flame: two crossed planes of the animated flame texture, tinted its
 * candle's flame colour (made more saturated), with its heart, a small soft glow of a paler shade; its size and brightness from the stage it kept ({@code flame} block
 * state), swaying a little about its foot; unshaded and full bright, see-through. Its item ({@link #ITEM}) draws the
 * block model and this flame alike, from the colour, flame and saucer its item keeps.
 */
public class FrousseuxCandleHolderRenderer implements BlockEntityRenderer<FrousseuxCandleHolderBlockEntity> {
    private static final Identifier FLAME = Steveparty.id("textures/entity/frousseux_flame.png");
    private static final Identifier HEART = Steveparty.id("textures/entity/frousseux_flame_heart.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    /** The flame's and the heart's places in their textures (the Frousseux's 64x64 layout). */
    private static final float FLAME_U0 = 40 / 64f, FLAME_V0 = 8 / 64f, FLAME_U1 = 46 / 64f, FLAME_V1 = 16 / 64f;
    private static final float HEART_U0 = 48 / 64f, HEART_V0 = 8 / 64f, HEART_U1 = 50 / 64f, HEART_V1 = 11 / 64f;

    public FrousseuxCandleHolderRenderer(BlockEntityRendererFactory.Context context) {
    }

    @Override
    public void render(FrousseuxCandleHolderBlockEntity holder, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        if (holder.getWorld() == null) return;
        float time = holder.getWorld().getTime() + tickDelta + (holder.getPos().hashCode() & 0xFF);
        BlockState state = holder.getCachedState();
        if (state.get(FrousseuxCandleHolderBlock.SAUCER)) drawModel(SAUCER, matrices, vertexConsumers, light, overlay);
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-FrousseuxCandleHolderBlock.yawOf(state) + 180));
        matrices.translate(-0.5, 0, -0.5);
        drawModel(state, matrices, vertexConsumers, light, overlay);
        matrices.pop();
        drawFlame(state, time, matrices, vertexConsumers);
    }

    /** The saucer under the candle: the Candle Saucer block's own model, square to the world. */
    private static final BlockState SAUCER = ModBlocks.CANDLE_SAUCER.getDefaultState();

    /**
     * A block model: the candle (its fists, its pool tinted its candle's colour), standing on the ground or one pixel
     * up for its saucer; or the saucer.
     */
    static void drawModel(BlockState state, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockRenderManager blocks = MinecraftClient.getInstance().getBlockRenderManager();
        BakedModel model = blocks.getModel(state);
        int accent = state.contains(FrousseuxCandleHolderBlock.COLOR) ? state.get(FrousseuxCandleHolderBlock.COLOR).accent : 0xFFFFFF;
        blocks.getModelRenderer().render(matrices.peek(), vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()),
                state, model, ((accent >> 16) & 0xFF) / 255f, ((accent >> 8) & 0xFF) / 255f, (accent & 0xFF) / 255f,
                light, overlay);
    }

    /** Its flame above the candle, by its block state (colour, stage, on its saucer or not). */
    static void drawFlame(BlockState state, float time, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        FrousseuxColor color = state.get(FrousseuxCandleHolderBlock.COLOR);
        FrousseuxEntity.Flame flame = FrousseuxEntity.Flame.values()[state.get(FrousseuxCandleHolderBlock.FLAME)];
        float lift = state.get(FrousseuxCandleHolderBlock.SAUCER) ? 1 : 0;
        matrices.push();
        // its foot: the top of the wick (as the mob's flame bone, 9.5 px over the candle's foot)
        matrices.translate(0.5, (lift + 9.5) / 16f, 0.5);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotation(0.06f * MathHelper.sin(time * 0.31f)
                + 0.03f * MathHelper.sin(time * 0.77f + 1.1f)));
        matrices.multiply(RotationAxis.POSITIVE_X.rotation(0.05f * MathHelper.sin(time * 0.27f + 2.0f)
                + 0.025f * MathHelper.sin(time * 0.83f + 0.4f)));
        float flicker = 0.05f * MathHelper.sin(time * 0.9f) + 0.03f * MathHelper.sin(time * 2.3f + 1.7f);
        matrices.scale(flame.size * (1 - flicker * 0.5f), flame.size * (1 + flicker), flame.size * (1 - flicker * 0.5f));

        int tint = color.flameEdge;
        float b = flame.brightness;
        int argb = 0xFF000000 | (int) (((tint >> 16) & 0xFF) * b) << 16 | (int) (((tint >> 8) & 0xFF) * b) << 8
                | (int) ((tint & 0xFF) * b);
        AnimatableTexture.setAndUpdate(FLAME); // its frames (frousseux_flame.png.mcmeta)
        VertexConsumer flameBuffer = vertexConsumers.getBuffer(RenderLayer.getBeaconBeam(FLAME, true));
        for (float angle : new float[]{45, -45}) {
            plane(matrices, flameBuffer, angle, 3 / 16f, -0.5f / 16, 7.5f / 16, FLAME_U0, FLAME_V0, FLAME_U1, FLAME_V1, argb);
        }
        int pale = color.flameHeart;
        int white = 0xFF000000 | (int) (((pale >> 16) & 0xFF) * b) << 16 | (int) (((pale >> 8) & 0xFF) * b) << 8
                | (int) ((pale & 0xFF) * b);
        VertexConsumer heartBuffer = vertexConsumers.getBuffer(RenderLayer.getBeaconBeam(HEART, true));
        for (float angle : new float[]{0, 90}) {
            plane(matrices, heartBuffer, angle, 0.7f / 16, 0.1f / 16, 2.5f / 16, HEART_U0, HEART_V0, HEART_U1, HEART_V1, white);
        }
        matrices.pop();
    }

    /** A vertical plane turned {@code angle} degrees about the flame's axis, seen from both sides. */
    private static void plane(MatrixStack matrices, VertexConsumer buffer, float angle, float halfWidth, float y0, float y1,
                              float u0, float v0, float u1, float v1, int argb) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f m = entry.getPositionMatrix();
        vertex(buffer, entry, m, -halfWidth, y1, u0, v0, argb);
        vertex(buffer, entry, m, -halfWidth, y0, u0, v1, argb);
        vertex(buffer, entry, m, halfWidth, y0, u1, v1, argb);
        vertex(buffer, entry, m, halfWidth, y1, u1, v0, argb);
        vertex(buffer, entry, m, halfWidth, y1, u1, v0, argb);
        vertex(buffer, entry, m, halfWidth, y0, u1, v1, argb);
        vertex(buffer, entry, m, -halfWidth, y0, u0, v1, argb);
        vertex(buffer, entry, m, -halfWidth, y1, u0, v0, argb);
        matrices.pop();
    }

    private static void vertex(VertexConsumer buffer, MatrixStack.Entry entry, Matrix4f m, float x, float y, float u, float v,
                               int argb) {
        buffer.vertex(m, x, y, 0).color(argb).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(FULL_BRIGHT)
                .normal(entry, 0, 1, 0);
    }

    /** The flame reaches above its block. */
    @Override
    public boolean rendersOutsideBoundingBox(FrousseuxCandleHolderBlockEntity blockEntity) {
        return true;
    }

    /** Its item: its block model (the colour, flame and saucer it keeps) and its flame. */
    public static final BuiltinItemRendererRegistry.DynamicItemRenderer ITEM = (stack, mode, matrices, vertexConsumers, light, overlay) -> {
        BlockState state = stateOf(stack);
        MinecraftClient client = MinecraftClient.getInstance();
        if (state.get(FrousseuxCandleHolderBlock.SAUCER)) drawModel(SAUCER, matrices, vertexConsumers, light, overlay);
        drawModel(state, matrices, vertexConsumers, light, overlay);
        float time = client.world == null ? 0 : client.world.getTime() + client.getRenderTickCounter().getTickDelta(false);
        drawFlame(state, time, matrices, vertexConsumers);
    };

    /** The block state its item would be placed as (its model's front: north). */
    static BlockState stateOf(ItemStack stack) {
        var kept = FrousseuxCandleHolderBlock.keptIn(stack);
        return ModBlocks.FROUSSEUX_CANDLE_HOLDER.getDefaultState()
                .with(FrousseuxCandleHolderBlock.COLOR, FrousseuxCandleHolderBlockEntity.colorOf(kept))
                .with(FrousseuxCandleHolderBlock.FLAME, FrousseuxCandleHolderBlockEntity.flameOf(kept).ordinal())
                .with(FrousseuxCandleHolderBlock.SAUCER, FrousseuxCandleHolderBlock.isOnSaucer(stack));
    }
}
