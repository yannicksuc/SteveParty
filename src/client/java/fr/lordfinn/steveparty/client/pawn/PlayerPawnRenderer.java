package fr.lordfinn.steveparty.client.pawn;

import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.client.util.math.MatrixStack;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

/**
 * A player pawn: the statue of its player (their skin, wide or slim arms) on its token base, holding its item. The
 * base, the stillness of a pawn and the squish come with every living entity renderer (TokenBase mixins).
 */
public class PlayerPawnRenderer extends LivingEntityRenderer<PlayerPawnEntity, PlayerPawnModel> {
    /**
     * The statue is drawn smaller than a player (32 pixels high) so that, with its big head, it stands as high as its
     * hitbox (1.8 blocks at scale 1).
     */
    private static final float STATUE_SCALE = PlayerPawnEntity.HEIGHT * 16.0F / PlayerStatue.HEIGHT_PIXELS;

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
        this.model = skin(pawn).model() == SkinTextures.Model.SLIM ? this.slim : this.wide;
        super.render(pawn, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(PlayerPawnEntity pawn) {
        return skin(pawn).texture();
    }

    /** Its player's skin (online or not, see SkinUtils). */
    private static SkinTextures skin(PlayerPawnEntity pawn) {
        return SkinUtils.getSkinTextures(pawn.getSkinOwner() != null ? pawn.getSkinOwner() : Util.NIL_UUID);
    }

    /** The statue's size, then the pose's moves of the whole figure. */
    @Override
    protected void scale(PlayerPawnEntity pawn, MatrixStack matrices, float amount) {
        matrices.scale(STATUE_SCALE, STATUE_SCALE, STATUE_SCALE);
        PlayerStatue.transform(matrices, pawn.getStatuePose());
    }
}
