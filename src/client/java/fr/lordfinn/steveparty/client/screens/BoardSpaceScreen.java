package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
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

    public BoardSpaceScreen(BoardSpaceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, TILE_H);
        isSingle = this.handler.isSingle();
    }

    @Override
    protected void handledScreenTick() {
        if (panel != null) panel.setSlotPower(isSingle ? -1 : handler.getSelectedSlot());
        super.handledScreenTick();
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(getTexture(), x, y, 0f, 0f,
                BoardSpaceScreenHandler.TILE_W, backgroundHeight, 256, 256);
        if (!isSingle) {
            int activeSlot = this.handler.getActiveSlot();
            if (activeSlot >= 0 && activeSlot < TEXTURES_OVERLAY.size()) {
                RenderSystem.enableBlend();
                context.drawTexture(TEXTURES_OVERLAY.get(activeSlot), x, y, 0, 0,
                        BoardSpaceScreenHandler.TILE_W, backgroundHeight, 256, 256);
                RenderSystem.disableBlend();
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
        drawCartridgeMenu(context, mouseX, mouseY);
    }

    /** The tile's square ends at 135 in tile.png: the cable starts on its right. */
    @Override
    protected int wireFromX() {
        return 136;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawTooltips(context, mouseX, mouseY);
    }

    /** On an Advanced Tile, a cartridge's tooltip says how to show its menu. */
    @Override
    protected List<Text> getTooltipFromItem(ItemStack stack) {
        List<Text> tooltip = super.getTooltipFromItem(stack);
        if (!isSingle && focusedSlot != null && focusedSlot.id < handler.getInventorySize() && stack.getItem() instanceof CartridgeItem) {
            tooltip = new ArrayList<>(tooltip);
            tooltip.add(1, Text.translatable(CartridgeItem.MENU_KEY + "select_hint").formatted(Formatting.YELLOW));
        }
        return tooltip;
    }

    /** The titles are in the tile part (the cartridge's menu is on its right). */
    @Override
    protected int titlesWidth() {
        return BoardSpaceScreenHandler.TILE_W;
    }

    @Override
    protected boolean hasPipette() {
        return true;
    }

    /** The pipette's button: left of the tile's square, just above the inventory's tab. */
    @Override
    protected int pipetteX() {
        return x + PIPETTE_RIGHT - 18;
    }

    @Override
    protected int pipetteY() {
        return y + INVENTORY_TOP - 2 - 18;
    }

    /** In tile.png: 2 px left of the tile's square (it starts at 41), and the top of the inventory's tab. */
    private static final int PIPETTE_RIGHT = 39, INVENTORY_TOP = 86;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pipetteOn()) return super.mouseClicked(mouseX, mouseY, button);
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
    public Identifier getTexture() {
        return isSingle ? SIMPLE_TEXTURE : TEXTURE;
    }
}
