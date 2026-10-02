package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.entity.Hopper;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hoppers and hopper minecarts move nothing across the border of a mini-game zone in session: they neither pull
 * from above it, nor push through it, nor suck up an item lying on its other side.
 */
@Mixin(HopperBlockEntity.class)
public abstract class ZoneBubbleHopperMixin {

    @Inject(method = "extract(Lnet/minecraft/world/World;Lnet/minecraft/block/entity/Hopper;)Z", at = @At("HEAD"), cancellable = true)
    private static void steveparty$noPullAcrossZoneBorder(World world, Hopper hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!ZoneBorder.ACTIVE) return;
        BlockPos pos = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY(), hopper.getHopperZ());
        if (ZoneBorder.across(world, pos, pos.up())) cir.setReturnValue(false);
    }

    @Inject(method = "extract(Lnet/minecraft/inventory/Inventory;Lnet/minecraft/entity/ItemEntity;)Z", at = @At("HEAD"), cancellable = true)
    private static void steveparty$noItemAcrossZoneBorder(Inventory inventory, ItemEntity item, CallbackInfoReturnable<Boolean> cir) {
        if (!ZoneBorder.ACTIVE || !(inventory instanceof Hopper hopper)) return;
        BlockPos pos = BlockPos.ofFloored(hopper.getHopperX(), hopper.getHopperY(), hopper.getHopperZ());
        if (ZoneBorder.across(item.getWorld(), pos, item.getBlockPos())) cir.setReturnValue(false);
    }

    @Inject(method = "insert(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/HopperBlockEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private static void steveparty$noPushAcrossZoneBorder(World world, BlockPos pos, HopperBlockEntity hopper, CallbackInfoReturnable<Boolean> cir) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.across(world, pos, pos.offset(hopper.getCachedState().get(HopperBlock.FACING)))) cir.setReturnValue(false);
    }
}
