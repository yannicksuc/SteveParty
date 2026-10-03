package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.persistent_state.ShopProtection;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No automation with a merchant's containers (its trading stalls, cash registers and the stock containers linked to
 * it): see {@link ShopProtection#blocksAutomation}.
 * <ul>
 *     <li>a hopper or a hopper minecart pulls nothing out of one ({@code extract});</li>
 *     <li>a hopper pushes nothing into one ({@code insert});</li>
 *     <li>whatever else asks the vanilla look-up for the container at a position (a dropper, a crafter...) finds
 *     none there ({@code getInventoryAt}).</li>
 * </ul>
 * The hopper moves are cancelled before their container is resolved: Fabric's transfer API falls back to the
 * block's item storage when the vanilla look-up finds nothing, so hiding the inventory alone would not be enough.
 * The checks cost one hash look-up, nothing at all on a server without shop.
 */
@Mixin(HopperBlockEntity.class)
public abstract class HopperBlockEntityMixin {

    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
    private static void steveparty$noPullFromMerchants(World world, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        BlockPos above = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY() + 1.0, hopper.getHopperZ());
        if (ShopProtection.blocksAutomation(world, above)) cir.setReturnValue(false);
    }

    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private static void steveparty$noPushIntoMerchants(World world, BlockPos pos, HopperBlockEntity blockEntity, CallbackInfoReturnable<Boolean> cir) {
        if (world.isClient) return;
        var state = world.getBlockState(pos);
        if (!state.contains(HopperBlock.FACING)) return;
        if (ShopProtection.blocksAutomation(world, pos.offset(state.get(HopperBlock.FACING)))) cir.setReturnValue(false);
    }

    @Inject(method = "getInventoryAt(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/inventory/Inventory;",
            at = @At("HEAD"), cancellable = true)
    private static void steveparty$noMerchantInventory(World world, BlockPos pos, CallbackInfoReturnable<Inventory> cir) {
        if (!world.isClient && ShopProtection.blocksAutomation(world, pos)) cir.setReturnValue(null);
    }
}
