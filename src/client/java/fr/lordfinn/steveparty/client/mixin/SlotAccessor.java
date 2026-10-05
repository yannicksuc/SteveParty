package fr.lordfinn.steveparty.client.mixin;

import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * A slot's position on screen, final in vanilla: the dice forge moves its face and fragment slots with the turning
 * galaxy, client side only (drawing and clicks both read these fields, the server never does).
 */
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void steveparty$setX(int x);

    @Mutable
    @Accessor("y")
    void steveparty$setY(int y);
}
