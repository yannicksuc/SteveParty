package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Inside a pawn bigger than a player (the token spell makes them up to 9 blocks), first person: the view is a
 * player's own, at a player's eye height over the pawn's feet, not up in the giant's eyes. A smaller pawn's eyes are
 * kept (seen from down there). Third person is left as vanilla (standing back with the pawn's size).
 */
@Mixin(Camera.class)
public abstract class CameraPawnViewMixin {
    /** A player's standing eye height (blocks). */
    @Unique
    private static final double PLAYER_EYES = 1.62;

    @WrapOperation(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V"))
    private void steveparty$pawnEyes(Camera camera, double x, double y, double z, Operation<Void> original,
                                     @Local(argsOnly = true) Entity focused, @Local(argsOnly = true, ordinal = 0) boolean thirdPerson,
                                     @Local(argsOnly = true) float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!thirdPerson && focused instanceof PlayerPawnEntity pawn && client.player != null
                && client.player.getUuid().equals(pawn.getPossessor())) {
            y = Math.min(y, pawn.getLerpedPos(tickDelta).y + PLAYER_EYES);
        }
        original.call(camera, x, y, z);
    }
}
