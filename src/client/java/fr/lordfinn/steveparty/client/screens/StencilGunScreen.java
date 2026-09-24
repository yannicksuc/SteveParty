package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.components.StencilGunSelection;
import fr.lordfinn.steveparty.items.custom.StencilGunItem;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilGunScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Loading screen of the stencil gun: stencils on the top row, dyes on the second one. The stencil and the colour
 * the gun sprays are framed.
 */
public class StencilGunScreen extends HandledScreen<StencilGunScreenHandler> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/generic_54.png");
    private static final int ROWS = 2;
    private static final int SELECTED_STENCIL = 0xFFFFD83D;
    private static final int SELECTED_DYE = 0xFF5FD3FF;

    public StencilGunScreen(StencilGunScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 114 + ROWS * 18;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0.0F, 0.0F, this.backgroundWidth, ROWS * 18 + 17, 256, 256);
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y + ROWS * 18 + 17, 0.0F, 126.0F, this.backgroundWidth, 96, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawSelection(context);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /** Frames the selected stencil and dye (from the gun in hand, updated as the player loads it). */
    private void drawSelection(DrawContext context) {
        if (client == null || client.player == null) return;
        ItemStack gun = client.player.getMainHandStack();
        if (!(gun.getItem() instanceof StencilGunItem)) gun = client.player.getOffHandStack();
        if (!(gun.getItem() instanceof StencilGunItem)) return;
        List<ItemStack> contents = StencilGunItem.contents(gun);
        StencilGunSelection selection = StencilGunItem.validSelection(contents, StencilGunItem.selection(gun));
        if (!contents.get(selection.stencil()).isEmpty()) frame(context, handler.slots.get(selection.stencil()), SELECTED_STENCIL);
        if (selection.dye() != StencilGunSelection.ENGRAVE) {
            frame(context, handler.slots.get(StencilGunItem.STENCIL_SLOTS + selection.dye()), SELECTED_DYE);
        }
    }

    private void frame(DrawContext context, Slot slot, int color) {
        context.drawBorder(this.x + slot.x - 1, this.y + slot.y - 1, 18, 18, color);
    }
}
