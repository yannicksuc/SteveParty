package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.passive.ChickenEntity;
import net.minecraft.item.ItemConvertible;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A token is a game piece: it does not produce items on its own. A token chicken lays no egg; its timer still
 * resets, so nothing is stored up for later. The other periodic "gifts" (armadillo scutes, panda sneezes, sniffer
 * seeds, cat and villager gifts) come from the AI, which a token does not run; the turtle scute is in
 * {@link TokenTurtleMixin}.
 */
@Mixin(ChickenEntity.class)
public abstract class TokenChickenMixin {
    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/entity/passive/ChickenEntity;dropItem(Lnet/minecraft/item/ItemConvertible;)Lnet/minecraft/entity/ItemEntity;"))
    private ItemEntity steveparty$noEggForTokens(ChickenEntity chicken, ItemConvertible item, Operation<ItemEntity> original) {
        if (chicken instanceof TokenizedEntityInterface token && token.steveparty$isTokenized()) return null;
        return original.call(chicken, item);
    }
}
