package fr.lordfinn.steveparty.client.pawn;

import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

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

    @Override
    protected void scale(PlayerPawnEntity pawn, MatrixStack matrices, float amount) {
        matrices.scale(STATUE_SCALE, STATUE_SCALE, STATUE_SCALE);
    }
}
