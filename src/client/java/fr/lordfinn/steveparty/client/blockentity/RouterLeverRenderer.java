package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlock;
import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
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
 * The Aiguilleur's lever (its plate, space, housing and torch are a plain block model): it leans with the redstone
 * power the block receives, +90 degrees at 0 to -90 at 15, and swings there quickly when the power changes.
 * Opaque, cutout: no see-through pass (minecraft-rendering).
 */
public class RouterLeverRenderer extends GeoBlockRenderer<BoardSpaceRedstoneRouterBlockEntity> {
    private static final String LEVER = "levier";
    /** The lever's angle at power 0 and its step per power level (degrees). */
    private static final float AT_ZERO = 90, PER_LEVEL = 12;
    /** How much of the way left the lever covers per tick (a quick swing that slows down as it arrives). */
    private static final float SWING = 0.45f;
    /** Its item: the lever a little to the side, so it reads as a lever. */
    private static final float ITEM_ANGLE = 30;

    /** This frame's lever angle (degrees). */
    private float angle;

    public RouterLeverRenderer() {
        super(new DefaultedBlockGeoModel<>(Steveparty.id("board_space_redstone_router")));
    }

    @Override
    public void render(BoardSpaceRedstoneRouterBlockEntity entity, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider bufferSource, int packedLight, int packedOverlay) {
        angle = entity.getWorld() == null ? ITEM_ANGLE : swing(entity, partialTick);
        super.render(entity, partialTick, matrices, bufferSource, packedLight, packedOverlay);
    }

    /** Moves the drawn lever toward the angle of its power, by the time passed since it was last drawn. */
    private static float swing(BoardSpaceRedstoneRouterBlockEntity entity, float partialTick) {
        BlockState state = entity.getCachedState();
        int power = state.contains(BoardSpaceRedstoneRouterBlock.POWER) ? state.get(BoardSpaceRedstoneRouterBlock.POWER) : 0;
        float target = AT_ZERO - PER_LEVEL * power;
        float now = entity.getWorld().getTime() + partialTick;
        if (Float.isNaN(entity.leverAngle)) {
            entity.leverAngle = target;
        } else {
            float ticks = MathHelper.clamp(now - entity.leverTime, 0, 20);
            float left = 1 - (float) Math.pow(1 - SWING, ticks);
            entity.leverAngle += (target - entity.leverAngle) * left;
        }
        entity.leverTime = now;
        return entity.leverAngle;
    }

    /** Not oriented: the lever always swings across the same axis. */
    @Override
    protected void rotateBlock(Direction facing, MatrixStack poseStack) {
    }

    @Override
    public @Nullable RenderLayer getRenderType(BoardSpaceRedstoneRouterBlockEntity animatable, Identifier texture,
                                               @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityCutout(texture);
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, BoardSpaceRedstoneRouterBlockEntity animatable, GeoBone bone,
                                  RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer,
                                  boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour) {
        if (LEVER.equals(bone.getName())) bone.setRotZ(angle * MathHelper.RADIANS_PER_DEGREE);
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, colour);
    }

    /** Lying flat, the lever reaches past the block. */
    @Override
    public boolean rendersOutsideBoundingBox(BoardSpaceRedstoneRouterBlockEntity blockEntity) {
        return true;
    }

    private static RouterLeverRenderer itemRenderer;
    private static BoardSpaceRedstoneRouterBlockEntity itemRouter;

    /** Its item: the block model, torch off, and the lever. */
    public static final BuiltinItemRendererRegistry.DynamicItemRenderer ITEM = (stack, mode, matrices, vertexConsumers, light, overlay) -> {
        BlockState state = ModBlocks.BOARD_SPACE_REDSTONE_ROUTER.getDefaultState();
        BlockRenderManager blocks = MinecraftClient.getInstance().getBlockRenderManager();
        blocks.getModelRenderer().render(matrices.peek(), vertexConsumers.getBuffer(TexturedRenderLayers.getEntityCutout()), state,
                blocks.getModel(state), 1, 1, 1, light, overlay);
        if (itemRenderer == null) {
            itemRenderer = new RouterLeverRenderer();
            itemRouter = new BoardSpaceRedstoneRouterBlockEntity(BlockPos.ORIGIN, state);
        }
        itemRenderer.render(itemRouter, 0, matrices, vertexConsumers, light, overlay);
    };
}
