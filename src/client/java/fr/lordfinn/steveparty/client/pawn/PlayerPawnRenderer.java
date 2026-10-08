package fr.lordfinn.steveparty.client.pawn;

import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnPose;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * A player pawn: the statue of its player (their skin, wide or slim arms) on its token base, holding its item. The
 * base, the stillness of a pawn and the squish come with every living entity renderer (TokenBase mixins).
 */
public class PlayerPawnRenderer extends LivingEntityRenderer<PlayerPawnEntity, PlayerPawnModel> {
    /**
     * The statue is drawn smaller than a player (32 pixels high) so that, with its big head, it stands as high as its
     * hitbox (1.8 blocks at scale 1).
     */
    private static final float STATUE_SCALE = PlayerPawnEntity.HEIGHT * 16.0F / (24.0F + 8.0F * PlayerPawnModel.HEAD_SCALE);

    private final PlayerPawnModel wide;
    private final PlayerPawnModel slim;

    public PlayerPawnRenderer(EntityRendererFactory.Context context) {
        super(context, new PlayerPawnModel(context.getPart(EntityModelLayers.PLAYER), false), 0.4F);
        this.wide = this.model;
        this.slim = new PlayerPawnModel(context.getPart(EntityModelLayers.PLAYER_SLIM), true);
        this.addFeature(new HeldItemFeatureRenderer<>(this, context.getHeldItemRenderer()));
    }

    @Override
    public void render(PlayerPawnEntity pawn, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        this.model = PawnSkins.of(pawn).model() == SkinTextures.Model.SLIM ? this.slim : this.wide;
        super.render(pawn, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(PlayerPawnEntity pawn) {
        return PawnSkins.of(pawn).texture();
    }

    /**
     * The statue's size, then the pose's moves of the whole figure. Here the model is upside down (y down, vanilla's
     * flip) and its feet are still {@value #FEET} blocks below this origin (the renderer moves it up right after).
     */
    @Override
    protected void scale(PlayerPawnEntity pawn, MatrixStack matrices, float amount) {
        matrices.scale(STATUE_SCALE, STATUE_SCALE, STATUE_SCALE);
        PlayerPawnPose pose = pawn.getStatuePose();
        if (pose.lift != 0 || pose.forward != 0) matrices.translate(0, -pose.lift / 16.0F, -pose.forward / 16.0F);
        if (pose.tilt != 0) {
            // Around the hips (12 pixels above the feet)
            float hips = 12.0F / 16.0F - FEET;
            matrices.translate(0, hips, 0);
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-pose.tilt));
            matrices.translate(0, -hips, 0);
        }
        if (pose.upsideDown) {
            // Turned over, the hands of the raised arms on the base: shoulders 2 pixels below the neck, arms 12 long
            float arm = 12.0F * MathHelper.cos((180.0F - Math.abs(pose.rightArmRoll)) * MathHelper.RADIANS_PER_DEGREE);
            float hands = (24.0F - 2.0F + arm) / 16.0F;
            matrices.translate(0, -hands, 0);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));
        }
    }

    /** Where the feet are below the origin of {@link #scale} (vanilla's 1.501). */
    private static final float FEET = 1.501F;
}
