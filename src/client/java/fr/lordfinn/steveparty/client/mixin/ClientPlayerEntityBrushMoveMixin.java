package fr.lordfinn.steveparty.client.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The Tile Linker Brush is held in use while it paints (see TileLinkerBrushItem): unlike a bow or food, it does not
 * slow its player down nor stop their sprint, a board is painted walking along it.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityBrushMoveMixin {
    @ModifyExpressionValue(method = "tickMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean steveparty$brushDoesNotSlowDown(boolean using) {
        return using && !steveparty$paints();
    }

    @ModifyExpressionValue(method = "canStartSprinting", require = 0, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean steveparty$brushLetsSprint(boolean using) {
        return using && !steveparty$paints();
    }

    @org.spongepowered.asm.mixin.Unique
    private boolean steveparty$paints() {
        return TileLinkerBrush.isBrush(((ClientPlayerEntity) (Object) this).getActiveItem());
    }
}
