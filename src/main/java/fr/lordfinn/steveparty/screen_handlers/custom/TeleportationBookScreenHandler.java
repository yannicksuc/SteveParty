package fr.lordfinn.steveparty.screen_handlers.custom;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import org.jetbrains.annotations.Nullable;

public abstract class TeleportationBookScreenHandler extends ScreenHandler {

    protected TeleportationBookScreenHandler(@Nullable ScreenHandlerType<?> type, int syncId) {
        super(type, syncId);
    }

    @Override
    public void sendContentUpdates() {
        super.sendContentUpdates();
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        // The book screen is only opened from the main hand: close it if the book is no longer there
        return isValidBook(player.getMainHandStack());
    }

    /** @return true if {@code stack} is the kind of book this screen edits. */
    public abstract boolean isValidBook(ItemStack stack);
}