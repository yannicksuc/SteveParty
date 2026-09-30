package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.client.gui.cartridge.CartridgePanel;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * A board space's interface: the tile part (its slot, or the 16 slots of an Advanced Tile with the active one lit)
 * with the player's inventory, and on its right the menu of the selected slot's cartridge (a right click on a slot of
 * an Advanced Tile selects it, framed in gold).
 */
public class BoardSpaceScreen extends CartridgeContainerScreen<BoardSpaceScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/advanced_tile.png");
    private static final List<Identifier> TEXTURES_OVERLAY = List.of(
            Steveparty.id("textures/gui/tile-overlay-0.png"),
            Steveparty.id("textures/gui/tile-overlay-1.png"),
            Steveparty.id("textures/gui/tile-overlay-2.png"),
            Steveparty.id("textures/gui/tile-overlay-3.png"),
            Steveparty.id("textures/gui/tile-overlay-4.png"),
            Steveparty.id("textures/gui/tile-overlay-5.png"),
            Steveparty.id("textures/gui/tile-overlay-6.png"),
            Steveparty.id("textures/gui/tile-overlay-7.png"),
            Steveparty.id("textures/gui/tile-overlay-8.png"),
            Steveparty.id("textures/gui/tile-overlay-9.png"),
            Steveparty.id("textures/gui/tile-overlay-10.png"),
            Steveparty.id("textures/gui/tile-overlay-11.png"),
            Steveparty.id("textures/gui/tile-overlay-12.png"),
            Steveparty.id("textures/gui/tile-overlay-13.png"),
            Steveparty.id("textures/gui/tile-overlay-14.png"),
            Steveparty.id("textures/gui/tile-overlay-15.png")
    );
    private static final Identifier SIMPLE_TEXTURE = Steveparty.id("textures/gui/tile.png");
    private static final int TILE_H = 183;

    private final boolean isSingle;
    private final CartridgePanel panel;

    public BoardSpaceScreen(BoardSpaceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, TILE_H);
        isSingle = this.handler.isSingle();
        BoardSpaceBlockEntity boardSpace = handler.boardSpace();
        BlockPos pos = boardSpace == null ? null : boardSpace.getPos();
        this.panel = new CartridgePanel(MinecraftClient.getInstance(), handler::selectedStack, () -> pos, () -> handler.syncId,
                () -> pos != null && CartridgeRef.slot(pos, handler.getSelectedSlot()).mayEdit(inventory.player),
                CartridgeLayout.MAX_CONTENT_BESIDE_TILE, TILE_H);
        this.backgroundWidth = BoardSpaceScreenHandler.MENU_X + panel.width();
    }

    @Override
    protected void init() {
        super.init();
        panel.setOrigin(x + BoardSpaceScreenHandler.MENU_X, y);
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        panel.setSlotLabel(isSingle ? null : Text.translatable(CartridgeItem.MENU_KEY + "slot", handler.getSelectedSlot() + 1));
        if (panel.refresh()) {
            // Another cartridge: its layout (the width may change)
            int width = BoardSpaceScreenHandler.MENU_X + panel.width();
            if (width != backgroundWidth) {
                backgroundWidth = width;
                init(client, this.width, this.height);
            } else {
                panel.setOrigin(x + BoardSpaceScreenHandler.MENU_X, y);
            }
        }
        panel.tick();
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground, getTexture(), x, y, 0f, 0f,
                BoardSpaceScreenHandler.TILE_W, backgroundHeight, 256, 256);
        if (!isSingle) {
            int activeSlot = this.handler.getActiveSlot();
            if (activeSlot >= 0 && activeSlot < TEXTURES_OVERLAY.size()) {
                context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURES_OVERLAY.get(activeSlot), x, y, 0, 0,
                        BoardSpaceScreenHandler.TILE_W, backgroundHeight, 256, 256);
            }
            // The selected slot (its cartridge's menu is on the right): a gold frame
            int selected = handler.getSelectedSlot();
            if (selected < handler.slots.size()) {
                Slot slot = handler.slots.get(selected);
                int sx = x + slot.x - 1, sy = y + slot.y - 1;
                context.fill(sx - 1, sy - 1, sx + 19, sy, 0xFFFFC52E);
                context.fill(sx - 1, sy + 18, sx + 19, sy + 19, 0xFFFFC52E);
                context.fill(sx - 1, sy, sx, sy + 18, 0xFFFFC52E);
                context.fill(sx + 18, sy, sx + 19, sy + 18, 0xFFFFC52E);
            }
        }
        panel.render(context, mouseX, mouseY);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        CartridgePanel.drawGhostMarks(context, handler.slots, x, y);
        if (!panel.renderTooltip(context, mouseX, mouseY)) drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /** On an Advanced Tile, a cartridge's tooltip says how to show its menu. */
    @Override
    protected List<Text> getTooltipFromItem(ItemStack stack) {
        List<Text> tooltip = super.getTooltipFromItem(stack);
        if (!isSingle && focusedSlot != null && focusedSlot.id < handler.getInventorySize() && stack.getItem() instanceof CartridgeItem) {
            tooltip = new java.util.ArrayList<>(tooltip);
            tooltip.add(1, Text.translatable(CartridgeItem.MENU_KEY + "select_hint").formatted(Formatting.YELLOW));
        }
        return tooltip;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (panel.mouseClicked(mouseX, mouseY, button)) return true;
        // Advanced Tile: a right click on a cartridge (empty cursor) shows its menu instead of taking it
        if (!isSingle && button == 1 && handler.getCursorStack().isEmpty() && client != null && client.interactionManager != null) {
            for (int i = 0; i < handler.getInventorySize() && i < handler.slots.size(); i++) {
                Slot slot = handler.slots.get(i);
                if (!slot.hasStack() || !isPointWithinBounds(slot.x, slot.y, 16, 16, mouseX, mouseY)) continue;
                client.interactionManager.clickButton(handler.syncId, i);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double amount = verticalAmount + horizontalAmount;
        if (CartridgeScreen.scrollGhost(handler.slots, handler.syncId, x, y, mouseX, mouseY, amount)) return true;
        if (panel.mouseScrolled(mouseX, mouseY, amount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public Identifier getTexture() {
        return isSingle ? SIMPLE_TEXTURE : TEXTURE;
    }
}
