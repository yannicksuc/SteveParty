package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import static fr.lordfinn.steveparty.client.entity.HidingTraderEntityRenderLayer.CUBE_BONE_ID;


public class HidingTraderEntityRenderer extends GeoEntityRenderer<HidingTraderEntity> {
    /** Bald merchant (his bandana was stolen with shears), per colour: his chin scarf keeps his colour. */
    private static final Identifier[] BALD_TEXTURES = {
            Steveparty.id("textures/entity/hiding_trader_bald_teal.png"),
            Steveparty.id("textures/entity/hiding_trader_bald_blue.png"),
            Steveparty.id("textures/entity/hiding_trader_bald_pink.png"),
            Steveparty.id("textures/entity/hiding_trader_bald_orange.png"),
            Steveparty.id("textures/entity/hiding_trader_bald_yellow.png"),
    };
    /** Base texture (gold bandana), used until the bandana colour is synced. */
    private static final Identifier BASE_TEXTURE = Steveparty.id("textures/entity/hiding_trader.png");
    /** One texture per bandana colour, in HidingTraderEntity's BandanaColor order (the art sources). */
    private static final Identifier[] BANDANA_TEXTURES = {
            Steveparty.id("textures/entity/hiding_trader_teal.png"),
            Steveparty.id("textures/entity/hiding_trader_blue.png"),
            Steveparty.id("textures/entity/hiding_trader_pink.png"),
            Steveparty.id("textures/entity/hiding_trader_orange.png"),
            Steveparty.id("textures/entity/hiding_trader_yellow.png"),
    };

    /** Root of the box: its walls, flaps, arm holes, peeking eyes and shadow. */
    public static final String BOX_BONE = "cube_box";

    public HidingTraderEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Steveparty.id("hiding_trader")));
        addRenderLayer(new HidingTraderEntityRenderLayer(this));
    }

    @Override
    public Identifier getTextureLocation(HidingTraderEntity animatable) {
        int color = animatable.getBandanaColor();
        if (color < 0 || color >= BANDANA_TEXTURES.length) return BASE_TEXTURE;
        return animatable.hasBandana() ? BANDANA_TEXTURES[color] : BALD_TEXTURES[color];
    }

    /** Without his box (taken with shears), the whole box with its flaps, holes and shadow is gone. */
    @Override
    public void renderRecursively(MatrixStack poseStack, HidingTraderEntity animatable, GeoBone bone, RenderLayer renderType, VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int renderColor) {
        if (BOX_BONE.equals(bone.getName()) && !animatable.hasBox()) return;
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, renderColor);
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int renderColor) {
        if (bone.getName().startsWith(CUBE_BONE_ID)) return;
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, renderColor);
    }
}