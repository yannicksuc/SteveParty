package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.client.gui.cartridge.CartridgePanel;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.screen_handlers.custom.BoardSpaceScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;

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
    private final CartridgePanel panel;

    public BoardSpaceScreen(BoardSpaceScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, TILE_H);
        isSingle = this.handler.isSingle();
        BoardSpaceBlockEntity boardSpace = handler.boardSpace();
        BlockPos pos = boardSpace == null ? null : boardSpace.getPos();
        this.panel = new CartridgePanel(MinecraftClient.getInstance(), handler::selectedStack, () -> pos, handler::storage, () -> handler.syncId,
                () -> pos != null && CartridgeRef.slot(pos, handler.getSelectedSlot()).mayEdit(inventory.player),
                CartridgeLayout.MAX_CONTENT_BESIDE_TILE, 0, TILE_H);
    }

    /**
     * The menu takes the window's width left by the tile part (its columns shrink in a narrow window) and its height
     * under the tile's top (its modules scroll when they are taller).
     */
    @Override
    protected void init() {
        int top = (height - backgroundHeight) / 2;
        panel.setAvailable(width - BoardSpaceScreenHandler.MENU_X - CartridgeScreen.MARGIN, height - top - CartridgeScreen.MARGIN);
        backgroundWidth = BoardSpaceScreenHandler.MENU_X + panel.width();
        super.init();
        panel.setOrigin(x + BoardSpaceScreenHandler.MENU_X, y);
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        panel.setSlotLabel(isSingle ? null : Text.translatable(CartridgeItem.MENU_KEY + "slot", handler.getSelectedSlot() + 1));
        // Another cartridge, or texts on more lines: the shell's size may change
        if (panel.tick() && client != null) init(client, this.width, this.height);
    }

    /** The menu may be taller than the tile part: a click on it is not a click outside (it would drop the item held). */
    @Override
    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        return super.isClickOutsideBounds(mouseX, mouseY, left, top, button) && !panel.isMouseOver(mouseX, mouseY);
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
        // The cable from the cartridge shown to its menu
        int selected = isSingle ? 0 : handler.getSelectedSlot();
        if (selected < handler.slots.size()) wire(context, y + handler.slots.get(selected).y + 7);
        panel.render(context, mouseX, mouseY);
    }

    private static final int WIRE_OUTLINE = 0xFF3A2410, WIRE_CORE = 0xFFFFC52E, WIRE_GLOW = 0xFFFFF6C8;
    /** The tile's square ends at 135 in tile.png, the menu starts at MENU_X; the cable climbs halfway between. */
    private static final int WIRE_FROM = 136, WIRE_HEADER = 12;

    /**
     * A cable, 2 px of gold in a dark sheath, from the right of the tile's square at the height of the cartridge
     * shown ({@code fromY}) to the menu's title, in right angles; a light runs along it, toward the menu.
     */
    private void wire(DrawContext context, int fromY) {
        int x0 = x + WIRE_FROM, x2 = x + BoardSpaceScreenHandler.MENU_X, x1 = (x0 + x2) / 2, toY = y + WIRE_HEADER;
        int[][] path = {{x0, fromY, x1, fromY}, {x1, fromY, x1, toY}, {x1, toY, x2, toY}};
        for (int[] seg : path) segment(context, seg, WIRE_OUTLINE, 2);
        for (int[] seg : path) segment(context, seg, WIRE_CORE, 1);
        // The light: a bright spot travelling the cable's length
        int length = Math.abs(x1 - x0) + Math.abs(toY - fromY) + Math.abs(x2 - x1);
        if (length <= 0) return;
        int at = (int) (Util.getMeasuringTimeMs() / 25 % (length + 20)) - 10;
        for (int[] seg : path) {
            int len = Math.abs(seg[2] - seg[0]) + Math.abs(seg[3] - seg[1]);
            if (at >= 0 && at <= len) {
                int px = seg[0] + Integer.signum(seg[2] - seg[0]) * at, py = seg[1] + Integer.signum(seg[3] - seg[1]) * at;
                context.fill(px - 1, py - 1, px + 1, py + 1, WIRE_GLOW);
            }
            at -= len;
        }
    }

    /** A straight line from (seg[0], seg[1]) to (seg[2], seg[3]), {@code half} px on each side of it. */
    private static void segment(DrawContext context, int[] seg, int color, int half) {
        int ax = Math.min(seg[0], seg[2]), bx = Math.max(seg[0], seg[2]), ay = Math.min(seg[1], seg[3]), by = Math.max(seg[1], seg[3]);
        context.fill(ax - half, ay - half, bx + half, by + half, color);
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
