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
 * Hoppers and hopper minecarts don't pull items out of a block of an owned shop (trading stall, cash register,
 * stock container): see {@link ShopProtection}. Pushing items into them is unchanged.
 * <p>
 * Cancelled before the input inventory is resolved: Fabric's transfer API falls back to the block's item storage
 * when the vanilla inventory lookup finds nothing, so hiding the inventory alone would not be enough. A hopper
 * under a container never picks up item entities anyway, so nothing else is lost.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
    private static void steveparty$protectShopInventories(World world, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        BlockPos above = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        if (ShopProtection.isContainerProtected(world, above)) cir.setReturnValue(false);
    }
}
