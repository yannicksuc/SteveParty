package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.custom.SaveStencilPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilMakerScreenHandler;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import fr.lordfinn.steveparty.stencil.StencilShape;
import fr.lordfinn.steveparty.stencil.StencilLibrary;
import fr.lordfinn.steveparty.payloads.custom.StencilMakerActionPayload;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * Stencil editor of the stencil maker: a 16x16 grid (left click cuts a pixel, right click fills it back, dragging
 * draws a continuous line), the player's stencil library on the left, tools on the right (clear, fill, invert,
 * mirrors, rotation, undo / redo with Ctrl+Z / Ctrl+Y, save to / remove from the library, take the stencil out).
 * <p>
 * The library ({@link StencilLibrary}) holds the patterns the player found, and the ones they saved; favourites
 * (right click) come first, framed in gold. In creative mode the built-in patterns are listed too.
 * The stencil is saved with the Save button and when the screen is closed.
 */
public class StencilMakerScreen extends HandledScreen<StencilMakerScreenHandler> {
    private static final Identifier BACKGROUND_TEXTURE = Steveparty.id("textures/gui/stencil_maker.png");
    private static final Identifier STENCIL_TEXTURE = Steveparty.id("textures/item/stencil.png");
    private static final Identifier BUTTONS = Steveparty.id("textures/gui/stencil_maker_buttons.png");
    private static final Identifier ICONS = Steveparty.id("textures/gui/stencil_maker_icons.png");
    private static final int ICON_SIZE = 12, ICON_COUNT = 12;
    private static final int ICON_CLEAR = 0, ICON_FILL = 1, ICON_INVERT = 2, ICON_MIRROR_H = 3, ICON_MIRROR_V = 4, ICON_ROTATE = 5,
            ICON_UNDO = 6, ICON_REDO = 7, ICON_SAVE = 8, ICON_DELETE = 9, ICON_TAKE_OUT = 10, ICON_FAVORITE = 11;
    private static final int FAVORITE_FRAME = 0xFFFFC21E;

    private static final int PIXEL_SIZE = 8;
    private static final int WIDTH_UNIT = 32;
    private static final int FRAME = PIXEL_SIZE * WIDTH_UNIT;
    private static final int CANVAS = FRAME / 2;
    private static final int HISTORY = 64;
    /** Library thumbnails: 16x16 at one screen pixel per stencil pixel, in cells of this size. */
    private static final int CELL = 20;
    private static final int TOOL_WIDTH = 104;
    private static final long SOUND_INTERVAL_MS = 45;

    private int stencilX, stencilY; // Position of the stencil
    private int bgX, bgY;
    private int libraryX, libraryY, libraryColumns, libraryRows, libraryScroll;

    private byte[] shape = StencilShape.blank();
    private byte[] savedShape = StencilShape.blank();
    private final Deque<byte[]> undo = new ArrayDeque<>();
    private final Deque<byte[]> redo = new ArrayDeque<>();
    /** Mouse button painting on the grid (-1: none), and the last pixel painted, to draw lines while dragging. */
    private int paintingButton = -1;
    private int lastPixelX, lastPixelY;
    private long lastSoundTime;
    private long savedMessageUntil;
    private IconButton libraryButton;

    /** A pattern shown in the library. */
    private record LibraryItem(byte[] shape, boolean favorite, boolean own, Text name) {
    }

    public StencilMakerScreen(StencilMakerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = FRAME;
        this.backgroundHeight = FRAME;
    }

    @Override
    protected void init() {
        super.init();
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        bgX = centerX - FRAME / 2;
        bgY = centerY - FRAME / 2;
        stencilX = bgX + FRAME / 4;
        stencilY = bgY + FRAME / 4;
        if (undo.isEmpty() && redo.isEmpty()) {
            shape = StencilItem.getShape(handler.getBlockEntity().getStencil());
            savedShape = shape.clone();
        }

        // Library on the left of the frame, as many columns as fit (5 at most)
        libraryColumns = Math.max(1, Math.min(5, (bgX - 8) / CELL));
        libraryX = bgX - 6 - libraryColumns * CELL;
        libraryY = Math.max(bgY + 18, 14);
        // Rows that fit on screen; the mouse wheel scrolls the rest
        libraryRows = Math.max(1, (this.height - libraryY - 4) / CELL);
        libraryScroll = Math.clamp(libraryScroll, 0, maxLibraryScroll());

        // Tools on the right of the frame
        int toolX = Math.min(bgX + FRAME + 6, this.width - TOOL_WIDTH - 2);
        int toolY = Math.max(bgY + 18, 4);
        addTool(toolX, toolY, "clear", ICON_CLEAR, s -> StencilShape.blank());
        addTool(toolX, toolY += 21, "fill", ICON_FILL, s -> StencilShape.full());
        addTool(toolX, toolY += 21, "invert", ICON_INVERT, StencilShape::invert);
        addTool(toolX, toolY += 21, "mirror_horizontal", ICON_MIRROR_H, StencilShape::mirrorHorizontal);
        addTool(toolX, toolY += 21, "mirror_vertical", ICON_MIRROR_V, StencilShape::mirrorVertical);
        addTool(toolX, toolY += 21, "rotate", ICON_ROTATE, StencilShape::rotateClockwise);
        addDrawableChild(new IconButton(toolX, toolY += 27, Text.translatable("gui.steveparty.stencil_maker.undo"), ICON_UNDO, b -> undo()));
        addDrawableChild(new IconButton(toolX, toolY += 21, Text.translatable("gui.steveparty.stencil_maker.redo"), ICON_REDO, b -> redo()));
        libraryButton = addDrawableChild(new IconButton(toolX, toolY += 27, Text.empty(), ICON_SAVE, b -> toggleInLibrary()));
        addDrawableChild(new IconButton(toolX, toolY += 21, Text.translatable("gui.steveparty.stencil_maker.take_out"), ICON_TAKE_OUT,
                b -> send(StencilMakerActionPayload.Action.TAKE_OUT, shape)));
        updateLibraryButton();
    }

    private void addTool(int x, int y, String name, int icon, UnaryOperator<byte[]> tool) {
        addDrawableChild(new IconButton(x, y, Text.translatable("gui.steveparty.stencil_maker." + name), icon, b -> apply(tool.apply(shape))));
    }

    /** Button with an icon on its left (all the editor's tools share the same look). */
    private static final class IconButton extends ButtonWidget {
        private int icon;

        IconButton(int x, int y, Text message, int icon, PressAction onPress) {
            super(x, y, TOOL_WIDTH, 20, message, onPress, DEFAULT_NARRATION_SUPPLIER);
            this.icon = icon;
        }

        void setIcon(int icon) {
            this.icon = icon;
        }

        @Override
        public void drawMessage(DrawContext context, TextRenderer textRenderer, int color) {
            context.drawTexture(RenderLayer::getGuiTextured, ICONS, getX() + 4, getY() + (height - ICON_SIZE) / 2,
                    icon * ICON_SIZE, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE * ICON_COUNT, ICON_SIZE);
            drawScrollableText(context, textRenderer, getMessage(), getX() + 20, getY(), getX() + getWidth() - 3, getY() + getHeight(), color);
        }
    }

    // ---------------------------------------------------------------- library

    /** Favourites first, then the rest of the player's library, then (creative) the built-in patterns. */
    private List<LibraryItem> libraryItems() {
        StencilLibrary library = client == null || client.player == null ? StencilLibrary.EMPTY : StencilLibrary.of(client.player);
        List<LibraryItem> items = new ArrayList<>();
        for (boolean favorites : new boolean[]{true, false}) {
            for (StencilLibrary.Entry entry : library.entries()) {
                if (entry.favorite() == favorites) items.add(item(entry.shapeArray(), entry.favorite(), true));
            }
        }
        if (client != null && client.player != null && client.player.isCreative()) {
            for (StencilPatterns.Pattern pattern : StencilPatterns.all()) {
                if (!library.contains(pattern.shape())) items.add(item(pattern.shape(), false, false));
            }
        }
        return items;
    }

    private static LibraryItem item(byte[] shape, boolean favorite, boolean own) {
        StencilPatterns.Pattern pattern = StencilPatterns.byShape(shape);
        return new LibraryItem(shape, favorite, own, pattern != null ? pattern.name() : Text.translatable("tooltip.steveparty.stencil.custom"));
    }

    private boolean inLibrary() {
        return client != null && client.player != null && StencilLibrary.of(client.player).contains(shape);
    }

    private void toggleInLibrary() {
        send(inLibrary() ? StencilMakerActionPayload.Action.DELETE : StencilMakerActionPayload.Action.SAVE, shape);
        playEditSound();
    }

    private void updateLibraryButton() {
        if (libraryButton == null) return;
        boolean in = inLibrary();
        libraryButton.setIcon(in ? ICON_DELETE : ICON_SAVE);
        libraryButton.setMessage(Text.translatable(in ? "gui.steveparty.stencil_maker.library_remove" : "gui.steveparty.stencil_maker.library_save"));
        libraryButton.active = in || !StencilShape.isBlank(shape);
    }

    private static void send(StencilMakerActionPayload.Action action, byte[] shape) {
        ClientPlayNetworking.send(new StencilMakerActionPayload(action, shape.clone()));
    }

    // ---------------------------------------------------------------- editing

    /** Replaces the whole shape, undoable. */
    private void apply(byte[] next) {
        if (Arrays.equals(next, shape)) return;
        pushUndo();
        shape = next;
        playEditSound();
    }

    private void pushUndo() {
        undo.push(shape.clone());
        while (undo.size() > HISTORY) undo.removeLast();
        redo.clear();
    }

    private void undo() {
        if (undo.isEmpty()) return;
        redo.push(shape);
        shape = undo.pop();
        playEditSound();
    }

    private void redo() {
        if (redo.isEmpty()) return;
        undo.push(shape);
        shape = redo.pop();
        playEditSound();
    }

    private boolean setPixel(int x, int y, byte value) {
        if (x < 0 || x >= 16 || y < 0 || y >= 16) return false;
        int index = StencilShape.index(x, y);
        if (shape[index] == value) return false;
        shape[index] = value;
        return true;
    }

    /** Paints from the last painted pixel to (x, y) so that fast strokes leave no gap. */
    private void paintLine(int x, int y, byte value) {
        int x0 = lastPixelX, y0 = lastPixelY;
        int dx = Math.abs(x - x0), dy = -Math.abs(y - y0);
        int sx = x0 < x ? 1 : -1, sy = y0 < y ? 1 : -1;
        int error = dx + dy;
        boolean changed = false;
        while (true) {
            changed |= setPixel(x0, y0, value);
            if (x0 == x && y0 == y) break;
            int e2 = 2 * error;
            if (e2 >= dy) {
                error += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                error += dx;
                y0 += sy;
            }
        }
        lastPixelX = x;
        lastPixelY = y;
        if (changed) playPixelSound();
    }

    private void save() {
        StencilItem.setShape(shape, handler.getBlockEntity().getStencil());
        ClientPlayNetworking.send(new SaveStencilPayload(shape.clone(), handler.getBlockEntity().getPos()));
        savedShape = shape.clone();
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInsideStencil(mouseX, mouseY) && (button == 0 || button == 1)) {
            pushUndo();
            paintingButton = button;
            lastPixelX = pixelX(mouseX);
            lastPixelY = pixelY(mouseY);
            paintLine(lastPixelX, lastPixelY, (byte) (button == 0 ? 1 : 0));
            return true;
        }
        LibraryItem item = itemAt(mouseX, mouseY);
        if (item != null && button == 0) {
            apply(item.shape());
            return true;
        }
        if (item != null && button == 1) {
            send(StencilMakerActionPayload.Action.FAVORITE, item.shape());
            playEditSound();
            return true;
        }
        if (isSaveButtonHovered(mouseX, mouseY)) {
            save();
            savedMessageUntil = Util.getMeasuringTimeMs() + 1500;
            playClickSound();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (paintingButton == button) {
            int x = Math.clamp(pixelX(mouseX), 0, 15);
            int y = Math.clamp(pixelY(mouseY), 0, 15);
            paintLine(x, y, (byte) (button == 0 ? 1 : 0));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (paintingButton == button) {
            paintingButton = -1;
            // A click that changed nothing leaves no undo step
            if (!undo.isEmpty() && Arrays.equals(undo.peek(), shape)) undo.pop();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown()) {
            if (keyCode == GLFW.GLFW_KEY_Z && !Screen.hasShiftDown()) {
                undo();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_Y || (keyCode == GLFW.GLFW_KEY_Z && Screen.hasShiftDown())) {
                redo();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Nothing drawn is lost: closing the editor (Esc, inventory key) saves the stencil. Sent before the screen
     * closes: the server only accepts it while the stencil maker is open.
     */
    @Override
    public void close() {
        if (!Arrays.equals(shape, savedShape) && handler.getBlockEntity() != null && !handler.getBlockEntity().getStencil().isEmpty()) {
            save();
        }
        super.close();
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(RenderLayer::getGuiTexturedOverlay, BACKGROUND_TEXTURE, bgX, bgY, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                if (shape[i * 16 + j] == 1) continue;
                context.drawTexture(RenderLayer::getGuiTexturedOverlay, STENCIL_TEXTURE, (stencilX + i * PIXEL_SIZE), (stencilY + j * PIXEL_SIZE), i * PIXEL_SIZE, j * PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE, backgroundWidth / 2, backgroundHeight / 2);
            }
        }
        if (isInsideStencil(mouseX, mouseY)) {
            int x = stencilX + pixelX(mouseX) * PIXEL_SIZE, y = stencilY + pixelY(mouseY) * PIXEL_SIZE;
            context.fill(x, y, x + PIXEL_SIZE, y + PIXEL_SIZE, 0x60FFFFFF);
        }

        int buttonX = this.width / 2 - 32;
        int buttonY = saveButtonY();
        boolean isHovering = isSaveButtonHovered(mouseX, mouseY);
        context.drawTexture(RenderLayer::getGuiTexturedOverlay, BUTTONS, buttonX, buttonY, 0, isHovering ? 16 : 0, 64, 16, 64, 64);
        boolean justSaved = Util.getMeasuringTimeMs() < savedMessageUntil;
        Text text = Text.translatable(justSaved ? "gui.steveparty.stencil_maker.saved" : "gui.steveparty.stencil_save");
        int textWidth = textRenderer.getWidth(text);
        context.drawText(textRenderer, text, this.width / 2 - textWidth / 2, buttonY + 4, 0xFFFFFFFF, true);

        drawLibrary(context, mouseX, mouseY);
    }

    private int maxLibraryScroll() {
        int rows = (libraryItems().size() + libraryColumns - 1) / libraryColumns;
        return Math.max(0, rows - libraryRows);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= libraryX && mouseX < libraryX + libraryColumns * CELL && mouseY >= libraryY) {
            libraryScroll = Math.clamp(libraryScroll - (int) Math.signum(verticalAmount), 0, maxLibraryScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawLibrary(DrawContext context, int mouseX, int mouseY) {
        List<LibraryItem> items = libraryItems();
        libraryScroll = Math.clamp(libraryScroll, 0, maxLibraryScroll());
        context.drawText(textRenderer, Text.translatable("gui.steveparty.stencil_maker.library"), libraryX, libraryY - 11, 0xFFFFFFFF, true);
        if (items.isEmpty()) {
            context.drawTextWrapped(textRenderer, Text.translatable("gui.steveparty.stencil_maker.library_empty"), libraryX, libraryY,
                    libraryColumns * CELL - 2, 0xFFB0B0B0);
        }
        int first = libraryScroll * libraryColumns;
        int last = Math.min(items.size(), first + libraryRows * libraryColumns);
        if (maxLibraryScroll() > 0) {
            // Scroll bar on the left of the thumbnails
            int barHeight = libraryRows * CELL;
            int thumb = Math.max(8, barHeight * libraryRows / (libraryRows + maxLibraryScroll()));
            int thumbY = libraryY + (barHeight - thumb) * libraryScroll / maxLibraryScroll();
            context.fill(libraryX - 4, libraryY, libraryX - 2, libraryY + barHeight, 0xFF2A2A2A);
            context.fill(libraryX - 4, thumbY, libraryX - 2, thumbY + thumb, 0xFFB0B0B0);
        }
        for (int i = first; i < last; i++) {
            int x = libraryX + (i % libraryColumns) * CELL;
            int y = libraryY + (i / libraryColumns - libraryScroll) * CELL;
            boolean hovered = mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
            LibraryItem item = items.get(i);
            context.fill(x, y, x + CELL - 2, y + CELL - 2, hovered ? 0xFF6A6A6A : 0xFF3A3A3A);
            if (item.favorite()) context.drawBorder(x - 1, y - 1, CELL, CELL, FAVORITE_FRAME);
            byte[] pattern = item.shape();
            for (int px = 0; px < 16; px++) {
                for (int py = 0; py < 16; py++) {
                    if (StencilShape.get(pattern, px, py)) context.fill(x + 1 + px, y + 1 + py, x + 2 + px, y + 2 + py, 0xFFE8E8E8);
                }
            }
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // No inventory here: only the title and how to draw, inside the frame
        context.drawCenteredTextWithShadow(textRenderer, this.title, FRAME / 2, 28, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.steveparty.stencil_maker.help_draw"), FRAME / 2, 42, 0xFFE0E0E0);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateLibraryButton();
        super.render(context, mouseX, mouseY, delta);
        LibraryItem item = itemAt(mouseX, mouseY);
        if (item != null) {
            context.drawTooltip(textRenderer, List.of(item.name(), Text.translatable(item.favorite()
                    ? "gui.steveparty.stencil_maker.favorite_remove" : "gui.steveparty.stencil_maker.favorite_add").formatted(Formatting.GRAY)), mouseX, mouseY);
        }
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- layout helpers

    private LibraryItem itemAt(double mouseX, double mouseY) {
        if (mouseX < libraryX || mouseY < libraryY) return null;
        int column = (int) ((mouseX - libraryX) / CELL);
        int row = (int) ((mouseY - libraryY) / CELL);
        if (column >= libraryColumns || row >= libraryRows) return null;
        int index = (row + libraryScroll) * libraryColumns + column;
        List<LibraryItem> items = libraryItems();
        return index >= 0 && index < items.size() ? items.get(index) : null;
    }

    private int saveButtonY() {
        return bgY + (FRAME / 4) * 3 + PIXEL_SIZE * 3;
    }

    private boolean isSaveButtonHovered(double mouseX, double mouseY) {
        int buttonX = this.width / 2 - 32;
        int buttonY = saveButtonY();
        return mouseX >= buttonX && mouseX < buttonX + 64 && mouseY >= buttonY && mouseY < buttonY + 16;
    }

    private int pixelX(double mouseX) {
        return (int) Math.floor((mouseX - stencilX) * 16 / CANVAS);
    }

    private int pixelY(double mouseY) {
        return (int) Math.floor((mouseY - stencilY) * 16 / CANVAS);
    }

    private boolean isInsideStencil(double mouseX, double mouseY) {
        return mouseX >= stencilX && mouseX < stencilX + CANVAS && mouseY >= stencilY && mouseY < stencilY + CANVAS;
    }

    // ---------------------------------------------------------------- sounds

    /** Soft tick while drawing (the anvil sound was deafening when dragging). */
    private void playPixelSound() {
        long now = Util.getMeasuringTimeMs();
        if (client == null || now - lastSoundTime < SOUND_INTERVAL_MS) return;
        lastSoundTime = now;
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_METAL_HIT, 1.6F, 0.25F));
    }

    private void playEditSound() {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_METAL_HIT, 1.2F, 0.35F));
    }

    private void playClickSound() {
        if (client != null)
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_ANVIL_USE, 1.0F, 1f));
    }
}
