package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A member of a mini-game session takes no item the server forbids to sessions out of a container: a click (of any
 * kind) on a slot that holds one, other than a slot of its own inventory, does nothing ({@link ZoneBorder#blocksSlot}).
 */
@Mixin(ScreenHandler.class)
public abstract class ZoneBubbleScreenHandlerMixin {
    @Shadow
    @Final
    public DefaultedList<Slot> slots;

    @Inject(method = "onSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$noForbiddenItemFromContainer(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (!ZoneBorder.ACTIVE || slotIndex < 0 || slotIndex >= slots.size()) return;
        if (ZoneBorder.blocksSlot(player, slots.get(slotIndex))) ci.cancel();
    }
}
