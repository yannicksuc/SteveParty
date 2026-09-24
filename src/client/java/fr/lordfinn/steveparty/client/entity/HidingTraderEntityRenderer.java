package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.HidingTraderEntity;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import static fr.lordfinn.steveparty.client.entity.HidingTraderEntityRenderLayer.CUBE_BONE_ID;


public class HidingTraderEntityRenderer extends GeoEntityRenderer<HidingTraderEntity> {
    /** Bald merchant: his bandana was stolen with shears. */
    private static final Identifier BALD_TEXTURE = Steveparty.id("textures/entity/hiding_trader_bald.png");
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

    public HidingTraderEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(Steveparty.id("hiding_trader")));
        addRenderLayer(new HidingTraderEntityRenderLayer(this));
    }

    @Override
    public Identifier getTextureLocation(HidingTraderEntity animatable) {
        if (!animatable.hasBandana()) return BALD_TEXTURE;
        int color = animatable.getBandanaColor();
        return color >= 0 && color < BANDANA_TEXTURES.length ? BANDANA_TEXTURES[color] : BASE_TEXTURE;
    }

    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int renderColor) {
        if (bone.getName().startsWith(CUBE_BONE_ID)) return;
        super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, renderColor);
    }
}