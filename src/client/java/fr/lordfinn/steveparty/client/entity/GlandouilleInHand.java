package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleTowers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/**
 * A carried Glandouille stack is drawn in the carrier's main hand, not where the entities stand (the server keeps them
 * at {@link GlandouilleTowers#heldPos}): in first person like a held item (see HeldItemRendererGlandouilleCarryMixin),
 * seen from outside on the arm ({@link Feature}). The entities themselves are not drawn while carried
 * ({@link GlandouilleRenderer#shouldRender}).
 */
public final class GlandouilleInHand {
    /** Set while a stack is drawn in a hand: the renderer then draws it, facing {@link #yaw}, without its name. */
    static boolean drawing;
    /** The yaw the renderer turns the model by while {@link #drawing} (180: the way the matrices face). */
    static float yaw;

    private GlandouilleInHand() {
    }

    /** {@code glandouille} is carried by a player, alone or in a stack. */
    public static boolean carried(GlandouilleEntity glandouille) {
        return GlandouilleTowers.bottom(glandouille).getVehicle() instanceof PlayerEntity;
    }

    /**
     * Draws the stack {@code bottom} is the bottom one of, its feet at the matrices' origin (Y up), turned by
     * {@code turn} degrees from facing -Z.
     */
    public static void render(GlandouilleEntity bottom, float turn, float tickDelta, MatrixStack matrices,
                              VertexConsumerProvider vertexConsumers, int light) {
        EntityRenderer<? super GlandouilleEntity> renderer = MinecraftClient.getInstance().getEntityRenderDispatcher().getRenderer(bottom);
        double bottomY = MathHelper.lerp(tickDelta, bottom.prevY, bottom.getY());
        boolean was = drawing;
        drawing = true;
        yaw = 180f - turn;
        try {
            for (GlandouilleEntity one : GlandouilleTowers.members(bottom)) {
                matrices.push();
                // the ones above stand where riding puts them, straight above it
                matrices.translate(0, MathHelper.lerp(tickDelta, one.prevY, one.getY()) - bottomY, 0);
                renderer.render(one, 0f, tickDelta, matrices, vertexConsumers, light);
                matrices.pop();
            }
        } finally {
            drawing = was;
        }
    }

    /**
     * Seen from outside: the stack stands upright on the main hand (or the left one of a left-handed player), carried
     * along by the arm (raised by PlayerEntityModelGlandouilleCarryMixin), its walking swing and its throw.
     */
    public static final class Feature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
        /** From the shoulder pivot to the top of the hand, along the arm (model pixels). */
        private static final float HAND_DOWN = 11f, HAND_UP = 1.5f;
        /** The stack's size in the hand, against its real one. */
        private static final float SCALE = 0.55f;

        public Feature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> context) {
            super(context);
        }

        @Override
        public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, AbstractClientPlayerEntity player,
                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
            GlandouilleEntity bottom = GlandouilleTowers.carried(player);
            if (bottom == null) return;
            Arm arm = player.getMainArm();
            PlayerEntityModel<AbstractClientPlayerEntity> model = getContextModel();
            float pitch = (arm == Arm.RIGHT ? model.rightArm : model.leftArm).pitch;
            matrices.push();
            model.setArmAngle(arm, matrices);
            // down the arm to the hand, then upright again (the arm's own turn toward the head kept)
            matrices.translate((arm == Arm.RIGHT ? -1f : 1f) / 16f, HAND_DOWN / 16f, 0f);
            matrices.multiply(RotationAxis.POSITIVE_X.rotation(-pitch));
            matrices.translate(0f, -HAND_UP / 16f, 0f);
            // the model's space is upside down
            matrices.scale(-1f, -1f, 1f);
            matrices.scale(SCALE, SCALE, SCALE);
            GlandouilleInHand.render(bottom, 0f, tickDelta, matrices, vertexConsumers, light);
            matrices.pop();
        }
    }
}
