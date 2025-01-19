package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.screens.CartridgeInventoryScreenHandler;
import fr.lordfinn.steveparty.screens.TileScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CartridgeInventoryScreen extends HandledScreen<CartridgeInventoryScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of("steveparty", "textures/gui/cartridge.png");
    private static final Identifier TEXTURE_OVERLAY = Identifier.of("steveparty", "textures/gui/cartridge-overlay-inventory-interactor.png");

    public CartridgeInventoryScreen(CartridgeInventoryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundHeight = 187;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE, x, y, 0f, 0f, backgroundWidth, backgroundHeight, 256, 256);
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE_OVERLAY, x, y, 0f, 0f, 125, 97, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render the default screen elements
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        for (Slot slot : this.handler.slots) {
            if (slot.hasStack() && slot instanceof CartridgeInventoryScreenHandler.CustomSlot customSlot) {
                drawSlotOverlay(context, slot.x, slot.y, customSlot.isPositive(slot.getStack()));
            }
        }

        // Render tooltips
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /**
     * Draws the overlay for a slot.
     *
     * @param context    the rendering context
     * @param x          the x-coordinate of the slot
     * @param y          the y-coordinate of the slot
     * @param isPositive whether the slot is positive
     */
    private void drawSlotOverlay(DrawContext context, int x, int y, boolean isPositive) {
        int overlayX, overlayY;
        overlayX = 128;
        overlayY = isPositive ? 34 : 52;

        // Adjust for screen position
        int screenX = x + this.x;
        int screenY = y + this.y;

        // Draw the texture
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE_OVERLAY, screenX, screenY, overlayX, overlayY, 16, 16, 256, 256);
    }

    @Override
    protected void init() {
        super.init();
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Check if the mouse is over a slot
        Slot slot = this.getSlotAt(mouseX, mouseY);
        if (slot instanceof CartridgeInventoryScreenHandler.CustomSlot customSlot) {
            customSlot.onScroll(verticalAmount + horizontalAmount); // Call the scroll method
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);

    }

    private Slot getSlotAt(double mouseX, double mouseY) {
        for (Slot slot : this.handler.slots) {
            if (isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                return slot;
            }
        }
        return null;
    }

}
