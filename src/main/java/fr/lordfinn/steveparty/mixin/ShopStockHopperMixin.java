package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.persistent_state.ShopProtection;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hoppers and hopper minecarts never pull from the stock of a shop: a hopper under a stock chest would empty the shop.
 * They may still fill it (pushing into it is left alone).
 */
@Mixin(HopperBlockEntity.class)
public abstract class ShopStockHopperMixin {

    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
    private static void steveparty$noPullFromShopStock(World world, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        BlockPos above = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        if (ShopProtection.isShopStock(world, above)) cir.setReturnValue(false);
    }
}
