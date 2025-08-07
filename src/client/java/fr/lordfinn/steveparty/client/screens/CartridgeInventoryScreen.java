package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.utils.DrawContextUtils;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.SelectionStatePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeInventoryScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_POS;

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
        drawInventoryOverlay(context, mouseX, mouseY);

        drawButtonOverlay(context, mouseX, mouseY);

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
        overlayY = isPositive ? 18 : 36;

        // Adjust for screen position
        int screenX = x + this.x;
        int screenY = y + this.y;

        // Draw the texture
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE_OVERLAY, screenX, screenY, overlayX, overlayY, 16, 16, 256, 256);
    }

    private void drawInventoryOverlay(DrawContext context, int mouseX, int mouseY) {
        ItemStack stack = client != null ? client.player != null ? client.player.getMainHandStack() : null : null;
        int overlayX = 61;
        int overlayY = 71;
        int overlayWidth = 16;
        int overlayHeight = 16;
        BlockPos pos = stack != null ? stack.getOrDefault(INVENTORY_POS, null) : null;

        if (pos == null) {
            if (isPointWithinBounds(overlayX, overlayY, overlayWidth, overlayHeight, mouseX, mouseY)) {
                List<Text> lines = new ArrayList<>();
                lines.add(Text.translatable("message.steveparty.no_inventory_connected.1"));
                lines.add(Text.translatable("message.steveparty.no_inventory_connected.2"));
                lines.add(Text.translatable("message.steveparty.no_inventory_connected.3"));
                context.drawTooltip(client.textRenderer, lines, mouseX, mouseY);
            }
            return;
        }
        DrawContextUtils.drawTextureWithGlint(context, TEXTURE_OVERLAY, overlayX + this.x, overlayY + this.y, 128, 0, 16, 16, 256, 256);
        if (isPointWithinBounds(overlayX, overlayY, overlayWidth, overlayHeight, mouseX, mouseY)) {
            context.drawTooltip(client.textRenderer, Text.translatable("message.steveparty.inventory_connected_at_pos", pos.getX(), pos.getY(), pos.getZ()), mouseX, mouseY);
        }
    }

    private void drawButtonOverlay(DrawContext context, int mouseX, int mouseY) {
        ItemStack stack = client != null ? client.player != null ? client.player.getMainHandStack() : null : null;
        int overlayX = 79;
        int overlayY = 71;
        int overlayWidth = 36;
        int overlayHeight = 16;
        int buttonState = stack != null ? InventoryCartridgeItem.getSelectionState(stack) : 0;

        // Sélectionne la texture à afficher selon l'état
        int overlayXTexture = 146;
        int overlayYTexture = switch (buttonState) {
            case 0 -> 0;  // Random
            case 1 -> 18; // All
            case 2 -> 36; // Cycle
            default -> 0; // Par défaut, Random
        };

        // Dessine l'overlay du bouton
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, TEXTURE_OVERLAY, overlayX + this.x, overlayY + this.y, overlayXTexture, overlayYTexture, 36, 16, 256, 256);

        // Affiche le tooltip basé sur l'état
        if (isPointWithinBounds(overlayX, overlayY, overlayWidth, overlayHeight, mouseX, mouseY)) {
            List<Text> lines = new ArrayList<>();
            switch (buttonState) {
                case 0 -> lines.add(Text.translatable("message.steveparty.button_state.random"));
                case 1 -> lines.add(Text.translatable("message.steveparty.button_state.all"));
                case 2 -> lines.add(Text.translatable("message.steveparty.button_state.cycle"));
            }
            context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE_OVERLAY, overlayX + this.x, overlayY + this.y, 183, 0, 36, 16, 256, 256);
            context.drawTooltip(client.textRenderer, lines, mouseX, mouseY);
        }
    }

    @Override
    public void close() {
        super.close();
        if (client != null && client.player != null) {
            sendSelectionStateToServer(InventoryCartridgeItem.getSelectionState(client.player.getMainHandStack()));
        }
    }

    public void sendSelectionStateToServer(int state) {
        SelectionStatePayload payload = new SelectionStatePayload(state);
        ClientPlayNetworking.send(payload);
    }



    @Override
    protected void init() {
        super.init();
        this.playerInventoryTitleY = this.backgroundHeight - 93;
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // Left click
            if (isPointWithinBounds(79, 71, 36, 16, mouseX, mouseY)) {
                ItemStack stack = client != null ? client.player != null ? client.player.getMainHandStack() : null : null;
                if (stack != null) {
                    // Cycle through button states
                    int currentState =  InventoryCartridgeItem.getSelectionState(stack);
                    int nextState = (currentState + 1) % 3; // Cycle through 0, 1, 2
                    InventoryCartridgeItem.setSelectionState(stack, nextState);
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
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
