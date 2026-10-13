package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.BoardText;
import fr.lordfinn.steveparty.board.Pipette;
import fr.lordfinn.steveparty.client.gui.HandCursor;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.cartridge.CartridgePanel;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeRef;
import fr.lordfinn.steveparty.payloads.custom.PipettePayload;
import fr.lordfinn.steveparty.screen_handlers.custom.CartridgeContainerScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The screens of the cartridge blocks. Those of the board spaces and routers ({@link #hasPipette}) have the
 * <b>pipette</b> (see {@link Pipette}): its button turns it on (the cursor becomes a pipette), the first cartridge
 * clicked is copied (its slot outlined), each next one gets its destinations; right click, Escape or the button again:
 * off. While on, a box by the cursor shows the destinations copied.
 * <p>
 * Those whose handler shows a cartridge's menu ({@link CartridgeContainerScreenHandler#hasCartridgeMenu}: board spaces,
 * Piggy Bank, Looting Box) have it on the right of the block's part, linked to it by a gold cable: the menu takes the
 * window's width left (its columns shrink in a narrow window) and its height under the top (its modules scroll).
 */
public abstract class CartridgeContainerScreen<T extends CartridgeContainerScreenHandler> extends HandledScreen<T> {
    private static final Identifier PIPETTE_ICON = Steveparty.id("textures/gui/pipette.png");
    private static final int PIPETTE_BUTTON = 18;
    /** Coordinates listed in the box at most. */
    private static final int SHOWN_LINKS = 6;
    private static final int COPY_COLOR = 0xFF4CFF4C;
    /** The titles' colour (vanilla's), and their margin before the panel's right edge or the pipette button. */
    private static final int TITLE_COLOR = 0x404040, TITLE_MARGIN = 8, PIPETTE_GAP = 4;

    private boolean pipetteOn;
    /** The slot copied (-1: none yet) and its destinations (as the client sees them). */
    private int pipetteSource = -1;
    private @Nullable List<BlockPos> pipetteLinks;
    private ItemStack pipetteCartridge = ItemStack.EMPTY;
    /** The selected cartridge's menu, on the right of the block's part; null if the handler has none. */
    protected final @Nullable CartridgePanel panel;

    public CartridgeContainerScreen(T handler, PlayerInventory inventory, Text title, int backgroundHeight) {
        super(handler, inventory, title);
        this.backgroundHeight = backgroundHeight;
        this.panel = handler.hasCartridgeMenu() ? new CartridgePanel(MinecraftClient.getInstance(), handler::selectedStack,
                handler::menuPos, handler::storage, () -> handler.syncId,
                () -> handler.menuPos() != null && CartridgeRef.slot(handler.menuPos(), handler.getSelectedSlot()).mayEdit(inventory.player),
                CartridgeLayout.MAX_CONTENT_BESIDE_TILE, 0, backgroundHeight) : null;
        if (panel != null) panel.setStock(handler::shopStock);
    }

    /** The block's part: its texture, the cable to the cartridge's menu and the menu. */
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(getTexture(), x, y, 0f, 0f, CartridgeContainerScreenHandler.BODY_W, backgroundHeight, 256, 256);
        drawCartridgeMenu(context, mouseX, mouseY);
    }

    /** The cable from the cartridge shown to its menu, and the menu (nothing without one). */
    protected void drawCartridgeMenu(DrawContext context, int mouseX, int mouseY) {
        if (panel == null) return;
        int selected = handler.getSelectedSlot();
        if (selected < handler.slots.size()) wire(context, x + wireFromX(), y + handler.slots.get(selected).y + 7);
        panel.render(context, mouseX, mouseY);
    }

    public abstract Identifier getTexture();

    /** The width of the part the titles are in (the block's part). */
    protected int titlesWidth() {
        return CartridgeContainerScreenHandler.BODY_W;
    }

    // ---------------------------------------------------------------- the cartridge's menu

    private static final int WIRE_OUTLINE = 0xFF3A2410, WIRE_CORE = 0xFFFFC52E, WIRE_GLOW = 0xFFFFF6C8;
    /** The height of the menu's title, where the cable ends. */
    private static final int WIRE_HEADER = 12;

    /** Where the cable to the menu starts, from the interface's corner: the right edge of the part holding the slot. */
    protected int wireFromX() {
        return CartridgeContainerScreenHandler.BODY_W;
    }

    /**
     * A cable, 2 px of gold in a dark sheath, from {@code (fromX, fromY)} (the cartridge shown's height) to the menu's
     * title, in right angles; a light runs along it, toward the menu.
     */
    private void wire(DrawContext context, int fromX, int fromY) {
        int x2 = x + CartridgeContainerScreenHandler.MENU_X, x1 = (fromX + x2) / 2, toY = y + WIRE_HEADER;
        int[][] path = {{fromX, fromY, x1, fromY}, {x1, fromY, x1, toY}, {x1, toY, x2, toY}};
        for (int[] seg : path) segment(context, seg, WIRE_OUTLINE, 2);
        for (int[] seg : path) segment(context, seg, WIRE_CORE, 1);
        // The light: a bright spot travelling the cable's length
        int length = Math.abs(x1 - fromX) + Math.abs(toY - fromY) + Math.abs(x2 - x1);
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
    protected void handledScreenTick() {
        super.handledScreenTick();
        // Another cartridge, or texts on more lines: the shell's size may change
        if (panel != null && panel.tick() && client != null) init(client, this.width, this.height);
    }

    /** The menu may be taller than the block's part: a click on it is not a click outside (it would drop the item held). */
    @Override
    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        return super.isClickOutsideBounds(mouseX, mouseY, left, top, button) && (panel == null || !panel.isMouseOver(mouseX, mouseY));
    }

    /** The menu's tooltip, else the slots' (a screen calls it last, over what it draws). */
    protected void drawTooltips(DrawContext context, int mouseX, int mouseY) {
        if (panel == null || !panel.renderTooltip(context, mouseX, mouseY)) drawMouseoverTooltip(context, mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (panel != null) {
            double amount = verticalAmount + horizontalAmount;
            if (CartridgeScreen.scrollGhost(handler.slots, handler.syncId, x, y, mouseX, mouseY, amount)) return true;
            if (panel.mouseScrolled(mouseX, mouseY, amount)) return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    /** The titles as vanilla places them, each on one line in its room (too long, it scrolls in it). */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        UiText.line(context, textRenderer, title, titleX, titleY, titleRoom(titleX, titleY), TITLE_COLOR, false);
        UiText.line(context, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY,
                titleRoom(playerInventoryTitleX, playerInventoryTitleY), TITLE_COLOR, false);
    }

    /**
     * The room of a title at ({@code tx}, {@code ty}) (from the interface's corner): up to 8 px before the panel's right
     * edge, or 4 px before the pipette button when it is on its row, on its right.
     */
    private int titleRoom(int tx, int ty) {
        int right = titlesWidth() - TITLE_MARGIN;
        int px = pipetteX() - x, py = pipetteY() - y;
        if (hasPipette() && px > tx && py < ty + UiText.LINE_H && py + PIPETTE_BUTTON > ty) right = Math.min(right, px - PIPETTE_GAP);
        return right - tx;
    }

    /**
     * With a cartridge's menu: it takes the window's width left by the block's part (its columns shrink in a narrow
     * window) and its height under the top (its modules scroll when they are taller); the whole is centred.
     */
    @Override
    protected void init() {
        if (panel != null) {
            int top = (height - backgroundHeight) / 2;
            panel.setAvailable(width - CartridgeContainerScreenHandler.MENU_X - CartridgeScreen.MARGIN, height - top - CartridgeScreen.MARGIN);
            backgroundWidth = CartridgeContainerScreenHandler.MENU_X + panel.width();
        }
        super.init();
        this.playerInventoryTitleY = this.backgroundHeight - 93;
        if (panel != null) panel.setOrigin(x + CartridgeContainerScreenHandler.MENU_X, y);
    }

    // ---------------------------------------------------------------- the pipette

    /** Whether this screen has the pipette (board spaces and routers). */
    protected boolean hasPipette() {
        return false;
    }

    /** The right edge of the part the pipette button sits in, top right (the block's part by default). */
    protected int pipetteRight() {
        return x + CartridgeContainerScreenHandler.BODY_W;
    }

    protected int pipetteX() {
        return pipetteRight() - PIPETTE_BUTTON - 4;
    }

    protected int pipetteY() {
        return y + 4;
    }

    private boolean overPipetteButton(double mouseX, double mouseY) {
        return hasPipette() && HitArea.contains(mouseX, mouseY, pipetteX(), pipetteY(), PIPETTE_BUTTON, PIPETTE_BUTTON);
    }

    public boolean pipetteOn() {
        return pipetteOn;
    }

    private void setPipette(boolean on) {
        pipetteOn = on;
        pipetteSource = -1;
        pipetteLinks = null;
        pipetteCartridge = ItemStack.EMPTY;
        HandCursor.pipette(on);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (overPipetteButton(mouseX, mouseY) && button == 0) {
            setPipette(!pipetteOn);
            return true;
        }
        if (!pipetteOn) {
            if (panel != null && panel.mouseClicked(mouseX, mouseY, button)) return true;
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (button == 1) {
            setPipette(false);
            return true;
        }
        if (button == 0) pipetteClick(focusedSlot);
        return true; // nothing else happens while the pipette is on
    }

    private void pipetteClick(@Nullable Slot slot) {
        if (slot == null || !Pipette.isItemSlot(handler, slot.id) || client == null || client.player == null) return;
        ItemStack stack = slot.getStack();
        if (pipetteSource < 0 || slot.id == pipetteSource) {
            List<BlockPos> links = Pipette.copyOf(stack);
            if (links == null) {
                client.player.sendMessage(Text.translatable("message.steveparty.pipette.no_source"), true);
                return;
            }
            pipetteSource = slot.id;
            pipetteLinks = links;
            pipetteCartridge = Pipette.cartridgeOf(stack).copy();
            client.player.sendMessage(Text.translatable("message.steveparty.pipette.copied", pipetteCartridge.getName(), links.size()), true);
            return;
        }
        if (Pipette.pasted(stack, pipetteLinks, client.player.getWorld()) == null) {
            client.player.sendMessage(Text.translatable("message.steveparty.pipette.no_target"), true);
            return;
        }
        ClientPlayNetworking.send(new PipettePayload(handler.syncId, pipetteSource, slot.id));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return pipetteOn || super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return pipetteOn || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pipetteOn && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            setPipette(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The pipette's box replaces the item tooltips while it is on. */
    @Override
    protected void drawMouseoverTooltip(DrawContext context, int x, int y) {
        if (!pipetteOn) super.drawMouseoverTooltip(context, x, y);
    }

    /** What its cartridge slot (slot 0) takes, ghosted in it while it is empty; null: nothing shown. */
    protected @Nullable ItemStack emptySlotGhost() {
        return null;
    }

    /** The veil over that ghost (ARGB): its slot's own colour, see-through. */
    protected int emptySlotVeil() {
        return PartyGui.GHOST_VEIL;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        ItemStack ghost = emptySlotGhost();
        if (ghost != null && !handler.slots.isEmpty() && !handler.slots.getFirst().hasStack()) {
            Slot slot = handler.slots.getFirst();
            PartyGui.ghostItem(context, ghost, x + slot.x, y + slot.y, null, emptySlotVeil());
        }
        if (panel != null) CartridgePanel.drawGhostMarks(context, handler.slots, x, y);
        if (!hasPipette()) return;
        drawPipetteButton(context, mouseX, mouseY);
        if (pipetteOn) {
            drawPipetteSource(context);
            drawPipetteBox(context, mouseX, mouseY);
        } else if (overPipetteButton(mouseX, mouseY)) {
            context.drawTooltip(textRenderer, List.of(Text.translatable("gui.steveparty.pipette"),
                    Text.translatable("gui.steveparty.pipette.hint").formatted(Formatting.GRAY)), mouseX, mouseY);
        }
    }

    private void drawPipetteButton(DrawContext context, int mouseX, int mouseY) {
        int bx = pipetteX(), by = pipetteY();
        boolean hover = overPipetteButton(mouseX, mouseY);
        // A button of the mod (PartyGui): gold and pushed in while the pipette is on
        PartyGui.Theme theme = pipetteOn ? PartyGui.BUTTON_SELECTED : hover ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON;
        PartyGui.button(context, bx, by, PIPETTE_BUTTON, PIPETTE_BUTTON, theme, pipetteOn);
        if (hover) context.drawBorder(bx, by, PIPETTE_BUTTON, PIPETTE_BUTTON, 0xFFFFFFFF);
        int push = pipetteOn ? 1 : 0;
        context.drawTexture(PIPETTE_ICON, bx + 1 + push, by + 1 + push, 0, 0, 16, 16, 16, 16);
    }

    /** The slot copied: outlined in the link colour. */
    private void drawPipetteSource(DrawContext context) {
        if (pipetteSource < 0 || pipetteSource >= handler.slots.size()) return;
        Slot slot = handler.slots.get(pipetteSource);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.drawBorder(x + slot.x - 1, y + slot.y - 1, 18, 18, COPY_COLOR);
        context.drawBorder(x + slot.x - 2, y + slot.y - 2, 20, 20, COPY_COLOR);
        context.getMatrices().pop();
    }

    /** By the cursor: what is copied (the cartridge and its destinations), or what to click first. */
    private void drawPipetteBox(DrawContext context, int mouseX, int mouseY) {
        List<Text> lines = new ArrayList<>();
        if (pipetteLinks == null) {
            lines.add(Text.translatable("gui.steveparty.pipette.pick").formatted(Formatting.YELLOW));
        } else {
            lines.add(Text.translatable("gui.steveparty.pipette.copy", pipetteCartridge.getName()).formatted(Formatting.GREEN));
            lines.add(Text.translatable("gui.steveparty.pipette.count", pipetteLinks.size()).formatted(Formatting.WHITE));
            for (int i = 0; i < Math.min(SHOWN_LINKS, pipetteLinks.size()); i++) {
                lines.add(Text.literal("  → ").append(BoardText.pos(pipetteLinks.get(i))).formatted(Formatting.GRAY));
            }
            if (pipetteLinks.size() > SHOWN_LINKS) {
                lines.add(Text.translatable("gui.steveparty.pipette.more", pipetteLinks.size() - SHOWN_LINKS).formatted(Formatting.DARK_GRAY));
            }
            lines.add(Text.translatable("gui.steveparty.pipette.paste").formatted(Formatting.YELLOW));
        }
        lines.add(Text.translatable("gui.steveparty.pipette.exit").formatted(Formatting.DARK_GRAY));
        context.drawTooltip(textRenderer, lines, mouseX + 8, mouseY + 8);
    }

    @Override
    public void removed() {
        super.removed();
        pipetteOn = false;
        HandCursor.reset();
    }
}
