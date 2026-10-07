package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.utils.StencilResourceManager;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.custom.SaveStencilPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.StencilMakerScreenHandler;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import fr.lordfinn.steveparty.stencil.StencilShape;
import fr.lordfinn.steveparty.stencil.StencilLibrary;
import fr.lordfinn.steveparty.payloads.custom.StencilMakerActionPayload;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Formatting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Stencil editor of the stencil maker: a 16x16 grid (left click cuts a pixel, right click fills it back, dragging
 * draws a continuous line), the player's stencil library on the left, tools on the right (clear, fill, invert,
 * mirrors, rotation, undo / redo with Ctrl+Z / Ctrl+Y, save to / remove from the library, take the stencil out).
 * <p>
 * The library ({@link StencilLibrary}) holds the patterns the player found, and the ones they saved; favourites
 * (right click) come first, framed in gold. In creative mode the built-in patterns are listed too.
 * The stencil is saved with the Save button and when the screen is closed.
 * <p>
 * Drawn in the mod's GUI style ({@link PartyGui}): one light panel under a steel title plate, the library and the
 * plate in sunken boxes, the tools as {@link PartyButton}s (the shape tools as icons, named in their tooltip).
 */
public class StencilMakerScreen extends HandledScreen<StencilMakerScreenHandler> {
    private static final Identifier STENCIL_TEXTURE = Steveparty.id("textures/item/stencil.png");
    private static final Identifier ICONS = Steveparty.id("textures/gui/stencil_maker_icons.png");
    private static final ItemStack TITLE_ICON = new ItemStack(ModBlocks.STENCIL_MAKER);
    private static final int ICON_SIZE = 12, ICON_COUNT = 12;
    private static final int ICON_CLEAR = 0, ICON_FILL = 1, ICON_INVERT = 2, ICON_MIRROR_H = 3, ICON_MIRROR_V = 4, ICON_ROTATE = 5,
            ICON_UNDO = 6, ICON_REDO = 7, ICON_SAVE = 8, ICON_DELETE = 9, ICON_TAKE_OUT = 10;
    private static final int FAVORITE_FRAME = 0xFFFFC21E;
    /** Sunken boxes' body: the dark slate of the mod's fields; the library's thumbnails on it. */
    private static final int BOX_BODY = 0xFF3B4247;
    private static final int THUMB_BODY = 0xFF2B3237, THUMB_HOVER = 0xFF56636C, THUMB_PIXEL = 0xFFE8E8E8;

    private static final int PIXEL_SIZE = 8;
    private static final int HISTORY = 64;
    /** Library thumbnails: 16x16 at one screen pixel per stencil pixel, in cells of this size. */
    private static final int CELL = 20;
    /** Panel padding, space between its columns, top of the columns (under the title plate and the labels). */
    private static final int PAD = 8, GAP = 8, TOP = 28;
    private static final int TOOLS_WIDTH = 92, BUTTON_HEIGHT = 20;
    /** Shape tools (2 rows of 3 icons), undo / redo, library and take out, save: 6 rows and their gaps. */
    private static final int TOOLS_HEIGHT = 6 * BUTTON_HEIGHT + 4 + 8 + 8 + 4 + 8;
    /** The library box around the thumbnails: its 2 px border, and room for the scroll bar on its right. */
    private static final int LIBRARY_EXTRA = 2 + 6;
    private static final long SOUND_INTERVAL_MS = 45;

    private int stencilX, stencilY; // Position of the stencil
    private int canvasBoxX, canvasBoxY, canvasBox, contentHeight;
    private int libraryBoxX, libraryBoxY, libraryBoxWidth;
    private int libraryX, libraryY, libraryColumns, libraryRows, libraryScroll;
    private List<OrderedText> help = List.of();

    private byte[] shape = StencilShape.blank();
    private byte[] savedShape = StencilShape.blank();
    private final Deque<byte[]> undo = new ArrayDeque<>();
    private final Deque<byte[]> redo = new ArrayDeque<>();
    /** Mouse button painting on the grid (-1: none), and the last pixel painted, to draw lines while dragging. */
    private int paintingButton = -1;
    private int lastPixelX, lastPixelY;
    /** The shape before the stroke being drawn: one undo step per stroke, and none (redo kept) if it changed nothing. */
    private byte[] strokeStart;
    private long lastSoundTime;
    private long savedMessageUntil;
    private PartyButton libraryButton, undoButton, redoButton, saveButton;
    private int libraryIcon = ICON_SAVE;
    /** {@link #libraryItems()} is asked for several times a frame: rebuilt only when the library or the game mode changes. */
    private StencilLibrary cachedLibrary;
    private boolean cachedCreative;
    private List<LibraryItem> cachedItems = List.of();

    /** A pattern shown in the library. */
    private record LibraryItem(byte[] shape, boolean favorite, boolean own, Text name) {
    }

    public StencilMakerScreen(StencilMakerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void init() {
        if (undo.isEmpty() && redo.isEmpty()) {
            shape = StencilItem.getShape(handler.getBlockEntity().getStencil());
            savedShape = shape.clone();
        }
        // The plate (the 16x16 canvas and its frame) in a sunken box; the columns as tall as the tallest of them
        canvasBox = StencilResourceManager.Kind.METAL.size() * PIXEL_SIZE + 4;
        contentHeight = Math.max(canvasBox, TOOLS_HEIGHT);
        int fixedWidth = PAD + GAP + canvasBox + GAP + TOOLS_WIDTH + PAD;
        // Library on the left, as many columns as fit (5 at most)
        libraryColumns = Math.clamp((this.width - 8 - fixedWidth - LIBRARY_EXTRA) / CELL, 1, 5);
        libraryBoxWidth = libraryColumns * CELL + LIBRARY_EXTRA;
        backgroundWidth = fixedWidth + libraryBoxWidth;
        help = textRenderer.wrapLines(Text.translatable("gui.steveparty.stencil_maker.help_draw"), backgroundWidth - 2 * PAD);
        backgroundHeight = TOP + contentHeight + 6 + help.size() * 10 + 4;
        super.init();
        // Centred with the title plate, which sticks out 11 px above the panel
        y = Math.max(12, (this.height - backgroundHeight + 11) / 2);

        libraryBoxX = x + PAD;
        libraryBoxY = y + TOP;
        libraryX = libraryBoxX + 2;
        libraryY = libraryBoxY + 2;
        libraryRows = Math.max(1, (contentHeight - 2) / CELL);
        libraryScroll = Math.clamp(libraryScroll, 0, maxLibraryScroll());

        canvasBoxX = libraryBoxX + libraryBoxWidth + GAP;
        canvasBoxY = y + TOP + (contentHeight - canvasBox) / 2;
        int margin = StencilResourceManager.Kind.METAL.margin();
        stencilX = canvasBoxX + 2 + margin * PIXEL_SIZE;
        stencilY = canvasBoxY + 2 + margin * PIXEL_SIZE;

        // Tools on the right: the shape tools as icons, three a row
        int toolX = canvasBoxX + canvasBox + GAP;
        int toolY = y + TOP;
        int small = (TOOLS_WIDTH - 8) / 3;
        addTool(toolX, toolY, small, "clear", ICON_CLEAR, s -> StencilShape.blank());
        addTool(toolX + small + 4, toolY, small, "fill", ICON_FILL, s -> StencilShape.full());
        addTool(toolX + 2 * (small + 4), toolY, small, "invert", ICON_INVERT, StencilShape::invert);
        toolY += BUTTON_HEIGHT + 4;
        addTool(toolX, toolY, small, "mirror_horizontal", ICON_MIRROR_H, StencilShape::mirrorHorizontal);
        addTool(toolX + small + 4, toolY, small, "mirror_vertical", ICON_MIRROR_V, StencilShape::mirrorVertical);
        addTool(toolX + 2 * (small + 4), toolY, small, "rotate", ICON_ROTATE, StencilShape::rotateClockwise);
        toolY += BUTTON_HEIGHT + 8;
        int half = (TOOLS_WIDTH - 4) / 2;
        undoButton = addDrawableChild(iconButton(toolX, toolY, half, ICON_UNDO, b -> undo()));
        undoButton.setTooltip(Tooltip.of(shortcut("undo", "Ctrl+Z")));
        redoButton = addDrawableChild(iconButton(toolX + half + 4, toolY, half, ICON_REDO, b -> redo()));
        redoButton.setTooltip(Tooltip.of(shortcut("redo", "Ctrl+Y")));
        toolY += BUTTON_HEIGHT + 8;
        libraryButton = addDrawableChild(labelledButton(toolX, toolY, Text.empty(), -1, b -> toggleInLibrary()));
        toolY += BUTTON_HEIGHT + 4;
        addDrawableChild(labelledButton(toolX, toolY, Text.translatable("gui.steveparty.stencil_maker.take_out"), ICON_TAKE_OUT,
                b -> takeOut()));
        toolY += BUTTON_HEIGHT + 8;
        saveButton = addDrawableChild(new PartyButton(toolX, toolY, TOOLS_WIDTH, BUTTON_HEIGHT,
                Text.translatable("gui.steveparty.stencil_save"), b -> {
            save();
            savedMessageUntil = Util.getMeasuringTimeMs() + 1500;
            playClickSound();
        }).style(PartyButton.Style.PRIMARY));
        updateButtons();
    }

    private static Text shortcut(String name, String keys) {
        return Text.translatable("gui.steveparty.stencil_maker." + name).append(Text.literal("  " + keys).formatted(Formatting.GRAY));
    }

    private void addTool(int x, int y, int width, String name, int icon, UnaryOperator<byte[]> tool) {
        addDrawableChild(iconButton(x, y, width, icon, b -> apply(tool.apply(shape))))
                .setTooltip(Tooltip.of(Text.translatable("gui.steveparty.stencil_maker." + name)));
    }

    /** A button showing only its icon (its name in the tooltip). */
    private static PartyButton iconButton(int x, int y, int width, int icon, Consumer<PartyButton> onPress) {
        return new PartyButton(x, y, width, BUTTON_HEIGHT, Text.empty(), onPress)
                .content((context, textRenderer, centerX, centerY, color) ->
                        drawIcon(context, icon, centerX - ICON_SIZE / 2, centerY - ICON_SIZE / 2, color));
    }

    /** A full width button: its icon (-1: the library button's current one), then its text, cut with « … » if too long. */
    private PartyButton labelledButton(int x, int y, Text message, int icon, Consumer<PartyButton> onPress) {
        PartyButton button = new PartyButton(x, y, TOOLS_WIDTH, BUTTON_HEIGHT, message, onPress);
        return button.content((context, textRenderer, centerX, centerY, color) -> {
            String text = button.getMessage().getString();
            int room = TOOLS_WIDTH - 8 - ICON_SIZE - 4;
            if (textRenderer.getWidth(text) > room) text = textRenderer.trimToWidth(text, room - textRenderer.getWidth("…")).stripTrailing() + "…";
            int left = centerX - (ICON_SIZE + 4 + textRenderer.getWidth(text)) / 2;
            drawIcon(context, icon < 0 ? libraryIcon : icon, left, centerY - ICON_SIZE / 2, color);
            context.drawText(textRenderer, text, left + ICON_SIZE + 4, centerY - 4, color, false);
        });
    }

    /** One of the editor's icons (white in the texture), tinted with the button's text colour. */
    private static void drawIcon(DrawContext context, int icon, int x, int y, int color) {
        RenderSystem.enableBlend();
        context.setShaderColor(((color >> 16) & 0xFF) / 255F, ((color >> 8) & 0xFF) / 255F, (color & 0xFF) / 255F, 1F);
        context.drawTexture(ICONS, x, y, icon * ICON_SIZE, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE * ICON_COUNT, ICON_SIZE);
        context.setShaderColor(1F, 1F, 1F, 1F);
        RenderSystem.disableBlend();
    }

    // ---------------------------------------------------------------- library

    /** Favourites first, then the rest of the player's library, then (creative) the built-in patterns. */
    private List<LibraryItem> libraryItems() {
        StencilLibrary library = client == null || client.player == null ? StencilLibrary.EMPTY : StencilLibrary.of(client.player);
        boolean creative = client != null && client.player != null && client.player.isCreative();
        // The library is immutable: a change is a new instance
        if (library == cachedLibrary && creative == cachedCreative) return cachedItems;
        List<LibraryItem> items = new ArrayList<>();
        for (boolean favorites : new boolean[]{true, false}) {
            for (StencilLibrary.Entry entry : library.entries()) {
                if (entry.favorite() == favorites) items.add(item(entry.shapeArray(), entry.favorite(), true));
            }
        }
        if (creative) {
            for (StencilPatterns.Pattern pattern : StencilPatterns.all()) {
                if (!library.contains(pattern.shape())) items.add(item(pattern.shape(), false, false));
            }
        }
        cachedLibrary = library;
        cachedCreative = creative;
        cachedItems = List.copyOf(items);
        return cachedItems;
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

    private void updateButtons() {
        if (libraryButton == null) return;
        boolean in = inLibrary();
        libraryIcon = in ? ICON_DELETE : ICON_SAVE;
        libraryButton.setMessage(Text.translatable(in ? "gui.steveparty.stencil_maker.library_remove" : "gui.steveparty.stencil_maker.library_save"));
        libraryButton.active = in || !StencilShape.isBlank(shape);
        undoButton.active = !undo.isEmpty();
        redoButton.active = !redo.isEmpty();
        boolean justSaved = Util.getMeasuringTimeMs() < savedMessageUntil;
        saveButton.setMessage(Text.translatable(justSaved ? "gui.steveparty.stencil_maker.saved" : "gui.steveparty.stencil_save"));
    }

    private static void send(StencilMakerActionPayload.Action action, byte[] shape) {
        ClientPlayNetworking.send(new StencilMakerActionPayload(action, shape.clone()));
    }

    // ---------------------------------------------------------------- editing

    /** Replaces the whole shape, undoable. */
    private void apply(byte[] next) {
        if (Arrays.equals(next, shape)) return;
        pushUndo();
        // A copy: drawing changes the shape in place, and a library thumbnail must not change with it
        shape = next.clone();
        playEditSound();
    }

    private void pushUndo() {
        pushUndo(shape.clone());
    }

    private void pushUndo(byte[] previous) {
        undo.push(previous);
        while (undo.size() > HISTORY) undo.removeLast();
        redo.clear();
    }

    private void undo() {
        if (undo.isEmpty() || paintingButton != -1) return;
        redo.push(shape);
        shape = undo.pop();
        playEditSound();
    }

    private void redo() {
        if (redo.isEmpty() || paintingButton != -1) return;
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
            strokeStart = shape.clone();
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
            if (strokeStart != null && !Arrays.equals(strokeStart, shape)) pushUndo(strokeStart);
            strokeStart = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Screen.hasControlDown()) {
            boolean z = isLetter(keyCode, scanCode, "z", GLFW.GLFW_KEY_Z);
            if (z && !Screen.hasShiftDown()) {
                undo();
                return true;
            }
            if (isLetter(keyCode, scanCode, "y", GLFW.GLFW_KEY_Y) || (z && Screen.hasShiftDown())) {
                redo();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Whether the key pressed is the one printed {@code letter} on the player's keyboard layout (Ctrl+Z is the Z key on
     * AZERTY too, not the key where a QWERTY Z is); {@code fallback} (a QWERTY position) when the layout has no name
     * for it.
     */
    private static boolean isLetter(int keyCode, int scanCode, String letter, int fallback) {
        String name = GLFW.glfwGetKeyName(keyCode, scanCode);
        return name != null ? name.equalsIgnoreCase(letter) : keyCode == fallback;
    }

    /**
     * Nothing drawn is lost: closing the editor (Esc, inventory key) saves the stencil. Sent before the screen
     * closes: the server only accepts it while the stencil maker is open.
     */
    @Override
    public void close() {
        saveIfChanged();
        super.close();
    }

    /**
     * Take out button: the drawing is saved on the stencil first (the server handles both packets in order, and
     * closes the screen itself, without going through {@link #close()}).
     */
    private void takeOut() {
        saveIfChanged();
        send(StencilMakerActionPayload.Action.TAKE_OUT, shape);
    }

    private void saveIfChanged() {
        if (!Arrays.equals(shape, savedShape) && handler.getBlockEntity() != null && !handler.getBlockEntity().getStencil().isEmpty()) {
            save();
        }
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        PartyGui.panel(context, x, y, backgroundWidth, backgroundHeight, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, x + backgroundWidth / 2, y - 11, 18, title, PartyGui.STEEL);
        context.drawItem(TITLE_ICON, PartyGui.titlePlateIconX(textRenderer, x + backgroundWidth / 2, 18, title), y - 8);

        // The plate: its frame around the canvas, the shape cut out of the 16x16 inside
        PartyGui.inset(context, canvasBoxX, canvasBoxY, canvasBox, canvasBox, BOX_BODY, false, false);
        RenderSystem.enableBlend();
        int plate = StencilResourceManager.Kind.METAL.size() * PIXEL_SIZE, margin = StencilResourceManager.Kind.METAL.margin();
        for (int i = -margin; i < 16 + margin; i++) {
            for (int j = -margin; j < 16 + margin; j++) {
                if (i >= 0 && i < 16 && j >= 0 && j < 16 && shape[i * 16 + j] == 1) continue;
                context.drawTexture(STENCIL_TEXTURE, stencilX + i * PIXEL_SIZE, stencilY + j * PIXEL_SIZE,
                        (i + margin) * PIXEL_SIZE, (j + margin) * PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE, plate, plate);
            }
        }
        RenderSystem.disableBlend();
        if (isInsideStencil(mouseX, mouseY)) {
            int px = stencilX + pixelX(mouseX) * PIXEL_SIZE, py = stencilY + pixelY(mouseY) * PIXEL_SIZE;
            context.fill(px, py, px + PIXEL_SIZE, py + PIXEL_SIZE, 0x60FFFFFF);
            context.drawBorder(px, py, PIXEL_SIZE, PIXEL_SIZE, 0xFFFFC52E);
        }

        drawLibrary(context, mouseX, mouseY);

        // How to draw, along the bottom of the panel
        int helpY = y + TOP + contentHeight + 6;
        for (OrderedText line : help) {
            context.drawText(textRenderer, line, x + (backgroundWidth - textRenderer.getWidth(line)) / 2, helpY, PartyGui.TEXT_SOFT, false);
            helpY += 10;
        }
    }

    private int maxLibraryScroll() {
        int rows = (libraryItems().size() + libraryColumns - 1) / libraryColumns;
        return Math.max(0, rows - libraryRows);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= libraryBoxX && mouseX < libraryBoxX + libraryBoxWidth && mouseY >= libraryBoxY && mouseY < libraryBoxY + contentHeight) {
            libraryScroll = Math.clamp(libraryScroll - (int) Math.signum(verticalAmount), 0, maxLibraryScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void drawLibrary(DrawContext context, int mouseX, int mouseY) {
        List<LibraryItem> items = libraryItems();
        libraryScroll = Math.clamp(libraryScroll, 0, maxLibraryScroll());
        context.drawText(textRenderer, Text.translatable("gui.steveparty.stencil_maker.library"), libraryBoxX + 1, libraryBoxY - 11,
                PartyGui.TEXT_DARK, false);
        PartyGui.inset(context, libraryBoxX, libraryBoxY, libraryBoxWidth, contentHeight, BOX_BODY, false, false);
        if (items.isEmpty()) {
            context.drawTextWrapped(textRenderer, Text.translatable("gui.steveparty.stencil_maker.library_empty"), libraryX + 2, libraryY + 2,
                    libraryBoxWidth - 8, 0xFFB8C2C8);
        }
        int first = libraryScroll * libraryColumns;
        int last = Math.min(items.size(), first + libraryRows * libraryColumns);
        if (maxLibraryScroll() > 0) {
            // The scroll bar on the right of the thumbnails, like the cartridges'
            int top = libraryY + 1, bottom = libraryBoxY + contentHeight - 3, track = bottom - top;
            int thumb = Math.max(8, track * libraryRows / (libraryRows + maxLibraryScroll()));
            int thumbY = top + (track - thumb) * libraryScroll / maxLibraryScroll();
            int barX = libraryBoxX + libraryBoxWidth - 5;
            context.fill(barX, top, barX + 2, bottom, 0xFF2B2B2B);
            context.fill(barX, thumbY, barX + 2, thumbY + thumb, 0xFFC9A227);
        }
        for (int i = first; i < last; i++) {
            int cellX = libraryX + (i % libraryColumns) * CELL, cellY = libraryY + (i / libraryColumns - libraryScroll) * CELL;
            boolean hovered = mouseX >= cellX && mouseX < cellX + CELL && mouseY >= cellY && mouseY < cellY + CELL;
            LibraryItem item = items.get(i);
            int cx = cellX + 1, cy = cellY + 1;
            context.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, hovered ? THUMB_HOVER : THUMB_BODY);
            if (item.favorite()) context.drawBorder(cellX, cellY, CELL, CELL, FAVORITE_FRAME);
            else if (hovered) context.drawBorder(cellX, cellY, CELL, CELL, 0xFFFFFFFF);
            byte[] pattern = item.shape();
            for (int px = 0; px < 16; px++) {
                for (int py = 0; py < 16; py++) {
                    if (StencilShape.get(pattern, px, py)) context.fill(cx + 1 + px, cy + 1 + py, cx + 2 + px, cy + 2 + py, THUMB_PIXEL);
                }
            }
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // No inventory here: the title is on its plate, the labels are drawn with the background
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        updateButtons();
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

    private int pixelX(double mouseX) {
        return (int) Math.floor((mouseX - stencilX) / PIXEL_SIZE);
    }

    private int pixelY(double mouseY) {
        return (int) Math.floor((mouseY - stencilY) / PIXEL_SIZE);
    }

    private boolean isInsideStencil(double mouseX, double mouseY) {
        return mouseX >= stencilX && mouseX < stencilX + 16 * PIXEL_SIZE && mouseY >= stencilY && mouseY < stencilY + 16 * PIXEL_SIZE;
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
