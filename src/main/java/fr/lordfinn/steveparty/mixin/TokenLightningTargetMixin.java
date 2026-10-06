package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.entity.LivingEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * In a thunderstorm, lightning strikes a living entity under the open sky near where it falls: never a token. A board
 * left outside would draw every bolt, and the fire around the struck pawn would burn the board and its neighbours.
 */
@Mixin(ServerWorld.class)
public abstract class TokenLightningTargetMixin {
    @ModifyExpressionValue(method = "getLightningPos", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/world/ServerWorld;getEntitiesByClass(Ljava/lang/Class;Lnet/minecraft/util/math/Box;Ljava/util/function/Predicate;)Ljava/util/List;"))
    private List<LivingEntity> steveparty$tokensDrawNoLightning(List<LivingEntity> targets) {
        if (targets.stream().noneMatch(TokenBase::isToken)) return targets;
        return targets.stream().filter(entity -> !TokenBase.isToken(entity)).toList();
    }
}
