package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.TurtleEntity;
import net.minecraft.item.ItemConvertible;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A baby turtle token that grows up does not drop its scute: tokens do not produce items on their own
 * (the other periodic drops, like chicken eggs, are blocked in {@link TokenEntityMixin}).
 */
@Mixin(TurtleEntity.class)
public abstract class TokenTurtleMixin {
    @WrapOperation(method = "onGrowUp", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/passive/TurtleEntity;dropItem(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/item/ItemConvertible;I)Lnet/minecraft/entity/ItemEntity;"))
    private ItemEntity steveparty$noScuteForTokens(TurtleEntity turtle, ServerWorld world, ItemConvertible item, int yOffset,
                                                  Operation<ItemEntity> original) {
        if (turtle instanceof TokenizedEntityInterface token && token.steveparty$isTokenized()) return null;
        return original.call(turtle, world, item, yOffset);
    }
}
