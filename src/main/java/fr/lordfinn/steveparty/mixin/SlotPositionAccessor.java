package fr.lordfinn.steveparty.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Moves a slot on its screen: the Party Controller's storage scrolls its rows (see {@code PartyControllerScreenHandler}). */
@Mixin(Slot.class)
public interface SlotPositionAccessor {
    @Mutable
    @Accessor("y")
    void steveparty$setY(int y);
}
