package fr.lordfinn.steveparty.client.mixin;

import fr.lordfinn.steveparty.client.entity.GlandouilleInHand;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleTowers;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * First person, carrying Glandouilles: the stack is drawn in the main hand like a held item (whatever the hand holds),
 * following the camera, the view bobbing and the hand's swing; the other hand is drawn as usual.
 */
@Mixin(HeldItemRenderer.class)
public class HeldItemRendererGlandouilleCarryMixin {
    /** The stack's size in the hand, against its real one. */
    @Unique
    private static final float STEVEPARTY$SCALE = 0.42f;
    /** From where an item is held to the stack's feet (screen units), and how far it turns its face to the middle. */
    @Unique
    private static final float STEVEPARTY$DOWN = 0.2f, STEVEPARTY$IN = 0.06f, STEVEPARTY$TURN = 30f;

    @Shadow
    private void applyEquipOffset(MatrixStack matrices, Arm arm, float equipProgress) {
        throw new AssertionError();
    }

    @Shadow
    private void applySwingOffset(MatrixStack matrices, Arm arm, float swingProgress) {
        throw new AssertionError();
    }

    @Inject(method = "renderFirstPersonItem", at = @At("HEAD"), cancellable = true)
    private void steveparty$carriedInHand(AbstractClientPlayerEntity player, float tickDelta, float pitch, Hand hand,
                                          float swingProgress, ItemStack item, float equipProgress, MatrixStack matrices,
                                          VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (hand != Hand.MAIN_HAND) return;
        GlandouilleEntity bottom = GlandouilleTowers.carried(player);
        if (bottom == null) return;
        ci.cancel();
        if (player.isInvisible()) return;
        Arm arm = player.getMainArm();
        float side = arm == Arm.RIGHT ? 1f : -1f;
        matrices.push();
        // the same moves as a held item's
        float swingRoot = MathHelper.sqrt(swingProgress);
        matrices.translate(side * -0.4f * MathHelper.sin(swingRoot * MathHelper.PI),
                0.2f * MathHelper.sin(swingRoot * MathHelper.TAU), -0.2f * MathHelper.sin(swingProgress * MathHelper.PI));
        applyEquipOffset(matrices, arm, equipProgress);
        applySwingOffset(matrices, arm, swingProgress);
        matrices.translate(-side * STEVEPARTY$IN, -STEVEPARTY$DOWN, 0f);
        matrices.scale(STEVEPARTY$SCALE, STEVEPARTY$SCALE, STEVEPARTY$SCALE);
        // its face to the player, turned a little toward the middle of the screen
        GlandouilleInHand.render(bottom, 180f - side * STEVEPARTY$TURN, tickDelta, matrices, vertexConsumers, light);
        matrices.pop();
    }
}
