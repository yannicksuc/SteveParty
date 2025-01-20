package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.utils.DrawContextUtils;
import fr.lordfinn.steveparty.components.PersistentInventoryComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem;
import fr.lordfinn.steveparty.screens.CartridgeInventoryScreenHandler;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static fr.lordfinn.steveparty.components.ModComponents.INVENTORY_POS;

public class CartridgeInventoryScreen extends HandledScreen<CartridgeInventoryScreenHandler> {
    private static final Identifier TEXTURE = Identifier.of("steveparty", "textures/gui/cartridge.png");
    private static final Identifier TEXTURE_OVERLAY = Identifier.of("steveparty", "textures/gui/cartridge-overlay-inventory-interactor.png");
    private PersistentInventoryComponent inventory;

    public CartridgeInventoryScreen(CartridgeInventoryScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundHeight = 187;
        this.inventory = handler.getPersistentInventory();
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

    private void drawInventoryOverlay(DrawContext context, int mouseX, int mouseY) {
        ItemStack stack = client != null ? client.player != null ? client.player.getMainHandStack() : null : null;
        int overlayX = 80;
        int overlayY = 53;
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
        DrawContextUtils.drawTextureWithGlint(context, TEXTURE_OVERLAY, overlayX + this.x, overlayY + this.y, 128, 17, 16, 16, 256, 256);
        if (isPointWithinBounds(overlayX, overlayY, overlayWidth, overlayHeight, mouseX, mouseY)) {
            context.drawTooltip(client.textRenderer, Text.translatable("message.steveparty.inventory_connected_at_pos", pos.getX(), pos.getY(), pos.getZ()), mouseX, mouseY);
        }
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
