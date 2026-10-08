package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.board.WrenchActions;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A block placed with the Tile Linker Brush in the off hand: linked from the brush's anchor when its cartridge links
 * such a block (a chest to an Inventory Cartridge, a switchable block to a Hop Switch...), see
 * {@link WrenchActions#onBlockPlaced}. Board spaces have their own hook (they are linked as tiles).
 */
@Mixin(BlockItem.class)
public abstract class BrushOffHandPlaceMixin {
    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void steveparty$linkFromTheBrush(ItemPlacementContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (!cir.getReturnValue().isAccepted()) return;
        WrenchActions.onBlockPlaced(context.getWorld(), context.getBlockPos(), context.getPlayer());
    }
}
