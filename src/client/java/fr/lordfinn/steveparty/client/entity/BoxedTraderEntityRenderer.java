package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.client.blockentity.TradingStallBlockEntityRenderer;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import static fr.lordfinn.steveparty.client.entity.BoxedTraderEntityRenderLayer.CUBE_BONE_ID;


public class BoxedTraderEntityRenderer extends GeoEntityRenderer<BoxedTraderEntity> {
    /** Bald merchant (his bandana was stolen with shears), per colour: his chin scarf keeps his colour. */
    private static final Identifier[] BALD_TEXTURES = {
            Steveparty.id("textures/entity/boxed_trader_bald_teal.png"),
            Steveparty.id("textures/entity/boxed_trader_bald_blue.png"),
            Steveparty.id("textures/entity/boxed_trader_bald_pink.png"),
            Steveparty.id("textures/entity/boxed_trader_bald_orange.png"),
            Steveparty.id("textures/entity/boxed_trader_bald_yellow.png"),
    };
    /** Base texture (gold bandana), used until the bandana colour is synced. */
    private static final Identifier BASE_TEXTURE = Steveparty.id("textures/entity/boxed_trader.png");
    /** One texture per bandana colour, in BoxedTraderEntity's BandanaColor order (the art sources). */
    private static final Identifier[] BANDANA_TEXTURES = {
            Steveparty.id("textures/entity/boxed_trader_teal.png"),
            Steveparty.id("textures/entity/boxed_trader_blue.png"),
            Steveparty.id("textures/entity/boxed_trader_pink.png"),
            Steveparty.id("textures/entity/boxed_trader_orange.png"),
            Steveparty.id("textures/entity/boxed_trader_yellow.png"),
    };

    private final ItemRenderer itemRenderer;
    private final BlockRenderManager blockRenderer;

    public BoxedTraderEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Steveparty.id("boxed_trader")));
        addRenderLayer(new BoxedTraderEntityRenderLayer(this));
        this.itemRenderer = ctx.getItemRenderer();
        this.blockRenderer = ctx.getBlockRenderManager();
    }

    @Override
    public void render(BoxedTraderEntity entity, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        if (entity.bringsOwnStall()) renderOwnStall(entity, poseStack, bufferSource, packedLight);
    }

    /**
     * A merchant summoned by a Shop space with no real stall in front: his own, drawn with him (not a block: nothing
     * to bump into, gone with him), a block ahead, facing the way he does, with what he sells on it.
     */
    private void renderOwnStall(BoxedTraderEntity entity, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float yaw = entity.getYaw();
        Vec3d ahead = Vec3d.fromPolar(0, yaw);
        matrices.push();
        matrices.translate(ahead.x, 0, ahead.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.push();
        matrices.translate(-0.5, 0, -0.5);
        blockRenderer.renderBlockAsEntity(STALL, matrices, consumers, light, OverlayTexture.DEFAULT_UV);
        matrices.pop();
        TradingStallBlockEntityRenderer.renderOffers(itemRenderer, entity.getShopItems(), matrices, consumers, light,
                OverlayTexture.DEFAULT_UV, entity.getWorld());
        matrices.pop();
    }

    /** The summoned merchant's own stall: a Trading Stall facing south (front toward +Z, his way once turned). */
    private static final BlockState STALL = ModBlocks.TRADING_STALL.getDefaultState().with(HorizontalFacingBlock.FACING, Direction.SOUTH);

    @Override
    public Identifier getTextureLocation(BoxedTraderEntity animatable) {
        int color = animatable.getBandanaColor();
        if (color < 0 || color >= BANDANA_TEXTURES.length) return BASE_TEXTURE;
        return animatable.hasBandana() ? BANDANA_TEXTURES[color] : BALD_TEXTURES[color];
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int renderColor) {
        if (bone.getName().startsWith(CUBE_BONE_ID)) return;
        if (this.animatable != null && !this.animatable.isBoxGlitched()
                && BlockTexturedBones.isHiddenInside(bone, this.animatable.getBlockState())) return;
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, renderColor);
    }
}