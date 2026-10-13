package fr.lordfinn.steveparty.client.gui.cartridge;

import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.LinksHelp;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.BlockSlotModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ColorModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ContainersModule;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.OutcomeListModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.StallOffersModule;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopStock;
import fr.lordfinn.steveparty.client.gui.HandCursor;
import fr.lordfinn.steveparty.client.gui.StallArt;
import fr.lordfinn.steveparty.components.InventoryComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.payloads.custom.CartridgeSettingPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlot;
import fr.lordfinn.steveparty.utils.Argb;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;

/**
 * A cartridge's menu, drawn as its shell (the dark cartridge of the Inventory Cartridge's menu): its label on top
 * (the cartridge's colour, icon and name), its modules laid out by {@link CartridgeLayout}, and its contacts at the
 * bottom. Shared by the cartridge's own screen and the tile's interface.
 * <p>
 * Texts always fit: the texts of several lines (descriptions, infos, hints) wrap on as many lines as they need and
 * their module takes that height; a one-line text too long for its box (the name, a module's title, a button) scrolls
 * in it in the reading direction, whole in a tooltip under the mouse ({@link UiText#line}). In a narrow window the columns shrink; when the
 * modules are taller than the place the shell has, they scroll (mouse wheel, a thin bar on the right).
 * <p>
 * « Current »: copper traces run from the contacts to each module. When the menu opens, a pulse runs from the contacts
 * along every trace and lights them up, each module's number flashing as it arrives; a change sends a spark along its
 * module's trace; a module that can't be changed now gets no current (dim trace). Everything is drawn with fills from
 * precomputed paths (no allocation per frame), and nothing moves under the game's « Hide Lightning Flashes ».
 */
public final class CartridgePanel {
    // The cartridge shell (colours of cartridge.png)
    private static final int OUTLINE = 0xFF0E0E0E, BODY = 0xFF272727, BEVEL = 0xFF5E5E5E, SCREEN = 0xFF454545;
    private static final int STICKER_EDGE = 0xFF232322, STRIP = 0xFFFF6052;
    private static final int LABEL = 0xFFD6D6D6, LABEL_OFF = 0xFF7A7A7A, CHIP = 0xFF2B2B2B, CHIP_TEXT = 0xFFFFC52E;
    private static final int GOLD = 0xFFC9A227, GOLD_LIGHT = 0xFFFFE27A;
    private static final int TRACE_OFF = 0xFF3C3522, TRACE_ON = 0xFF9A7430;
    private static final int PULSE_HEAD = 0xFFFFF6C0, PULSE_MID = 0xFFFFD34A, PULSE_TAIL = 0xFFC08A2A;
    private static final int TONE_NORMAL = 0xFFE0E0E0, TONE_SOFT = 0xFF9C9C9C, TONE_GOOD = 0xFF7CE06A, TONE_BAD = 0xFFFF7A7A;
    /** Pixels per millisecond: the opening pulse, a change's spark. */
    private static final float OPEN_SPEED = 0.32F, SPARK_SPEED = 0.45F;
    private static final long FLASH_MS = 260;
    private static final int NO_PENDING = Integer.MIN_VALUE, PENDING_TICKS = 40, INFO_REFRESH_TICKS = 10;
    private static final int SCROLL_STEP = 14;

    private final MinecraftClient client;
    private final TextRenderer textRenderer;
    private final Supplier<ItemStack> stack;
    private final Supplier<BlockPos> pos;
    private final Supplier<CartridgeContainers.Storage> storage;
    private final IntSupplier syncId;
    private final BooleanSupplier canEdit;
    private final int maxContent;
    private final int minWidth, minHeight;

    private @Nullable Item item;
    private List<CartridgeModule> modules = List.of();
    private CartridgeLayout layout = CartridgeLayout.of(List.of(), 0);
    private int x, y;
    /** The place the shell may take (the window, less what is beside it). */
    private int maxWidth = CartridgeLayout.width(1, CartridgeLayout.COLUMN_W), maxHeight = 240;
    /** The width of a column, the shell's height (less than the layout's when it scrolls) and how far it scrolls. */
    private int columnW = CartridgeLayout.COLUMN_W;
    private int shellHeight;
    private int scroll, scrollMax;
    /** The redstone power selecting the cartridge shown (a tile holding several), -1 for none. */
    private int slotPower = -1;
    private static final ItemStack REDSTONE = new ItemStack(net.minecraft.item.Items.REDSTONE);

    // State per module (sized when the cartridge changes)
    private int[] heights = new int[0];
    private int[] pending = new int[0];
    private int[] pendingTicks = new int[0];
    private long[] sparkStart = new long[0];
    private long[] flashStart = new long[0];
    private String[] labels = new String[0];
    /** The number of each module's chip (« 01 »...): the inline modules have none, the next ones follow on. */
    private int[] numbers = new int[0];
    private String[][] optionTexts = new String[0][];
    private List<List<OrderedText>> infoLines = new ArrayList<>();
    private int[][] infoColors = new int[0][];
    private List<OrderedText> ghostHelp = List.of(), emptyLines = List.of();
    /** The width the ghost slots' help is wrapped at. */
    private int ghostHelpW;
    private String giveText = "", takeText = "";
    /** The storage the containers' module was laid out for, null without such a module. */
    private @Nullable CartridgeContainers.Storage shownStorage;
    private int infoTicks;
    /** A Shop Cartridge's stock per offer, as the screen's handler last got it (see {@link #setStock}). */
    private IntUnaryOperator stock = column -> ShopStock.NO_OFFER;
    /** The small stall's offers ({@link ShopCartridgeItem#stallSlots}), read again only when they change. */
    private List<ItemStack> stallSlots = List.of();
    private @Nullable InventoryComponent stallFrom;
    private @Nullable Item stallItem;
    // Traces: per module, 5 points (x, y) from the contacts to the module, and the lengths
    private int[][] paths = new int[0][];
    private int[] pathLength = new int[0];
    private long openedAt;
    private int pointX, pointY;
    /** The Mistigri's sentences (its own state: the rows open, the scroll), null without such a module. */
    private @Nullable OutcomeListWidget outcomeList;

    /**
     * @param maxContent the content height before another column (see {@link CartridgeLayout})
     * @param minWidth   the shell's minimum width (a shell of a fixed size: with the player's inventory)
     * @param minHeight  the shell's minimum height (e.g. beside a tile, the tile's height)
     */
    public CartridgePanel(MinecraftClient client, Supplier<ItemStack> stack, Supplier<BlockPos> pos,
                          Supplier<CartridgeContainers.Storage> storage, IntSupplier syncId,
                          BooleanSupplier canEdit, int maxContent, int minWidth, int minHeight) {
        this.client = client;
        this.textRenderer = client.textRenderer;
        this.stack = stack;
        this.pos = pos;
        this.storage = storage;
        this.syncId = syncId;
        this.canEdit = canEdit;
        this.maxContent = maxContent;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        refresh();
    }

    public void setOrigin(int x, int y) {
        if (x == this.x && y == this.y) return;
        this.x = x;
        this.y = y;
        buildPaths();
    }

    /**
     * The place the shell may take: narrower than a column and the columns shrink, lower than the modules and they
     * scroll.
     *
     * @return true if its size changed
     */
    public boolean setAvailable(int maxWidth, int maxHeight) {
        if (maxWidth == this.maxWidth && maxHeight == this.maxHeight) return false;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        int w = width(), h = height();
        relayout(stack.get());
        return w != width() || h != height();
    }

    /** Where a Shop Cartridge's stock comes from: the screen handler's (sent by the server). */
    public void setStock(IntUnaryOperator stock) {
        this.stock = stock;
    }

    /** The redstone power selecting the cartridge shown, in the label (a tile holding several); -1 for none. */
    public void setSlotPower(int slotPower) {
        this.slotPower = slotPower;
    }

    public int width() {
        return Math.max(minWidth, layout.width());
    }

    public int height() {
        return shellHeight;
    }

    /** Rebuilds the modules when the cartridge shown changed (another slot selected, a cartridge inserted...). */
    public boolean refresh() {
        ItemStack current = stack.get();
        Item now = CartridgeMenus.cartridge(current) == null ? null : current.getItem();
        if (now == item && pending.length == modules.size() && shellHeight > 0) return false;
        item = now;
        modules = now == null ? List.of() : CartridgeMenus.modules(current);
        int n = modules.size();
        pending = new int[n];
        Arrays.fill(pending, NO_PENDING);
        pendingTicks = new int[n];
        sparkStart = new long[n];
        flashStart = new long[n];
        scroll = 0;
        infoTicks = INFO_REFRESH_TICKS;
        openedAt = Util.getMeasuringTimeMs();
        outcomeList = null;
        int list = CartridgeLayout.indexOf(modules, OutcomeListModule.class);
        if (list >= 0) {
            outcomeList = new OutcomeListWidget(textRenderer, action -> {
                // An action, not a value: sent as is (the widget shows it at once)
                sparkStart[list] = Util.getMeasuringTimeMs();
                ClientPlayNetworking.send(new CartridgeSettingPayload(syncId.getAsInt(), modules.get(list).id(), action));
                click();
            });
            outcomeList.onClick(this::click);
        }
        relayout(current);
        return true;
    }

    /**
     * Once per client tick: the pending values, the texts (their lines may change: the layout follows).
     *
     * @return true if the shell's size changed
     */
    public boolean tick() {
        boolean changed = refresh();
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            if (pending[i] == NO_PENDING) continue;
            if (modules.get(i).get(current) == pending[i] || ++pendingTicks[i] > PENDING_TICKS) pending[i] = NO_PENDING;
        }
        // Its storage changed (sent by the server): its hint at once
        if (infoTicks-- <= 0 || (shownStorage != null && shownStorage != storage(rows(current).size()))) {
            infoTicks = INFO_REFRESH_TICKS;
            int w = width(), h = height();
            relayout(current);
            changed |= w != width() || h != height();
        }
        return changed;
    }

    // ------------------------------------------------------------------ texts and layout

    private int colorsPerRow() {
        return Math.max(1, (columnW + ColorModule.GAP) / (ColorModule.SWATCH + ColorModule.GAP));
    }

    private int ghostHelpX() {
        return GhostSlotsModule.COLUMNS * GhostSlotsModule.SLOT + 6;
    }

    /** Measures every text with the font, gives each module its height, and lays the modules out in the place given. */
    private void relayout(ItemStack current) {
        // The widest column the place leaves room for
        columnW = Math.clamp(Math.max(minWidth, maxWidth) - 2 * CartridgeLayout.PAD_X, CartridgeLayout.MIN_COLUMN_W, CartridgeLayout.COLUMN_W);
        int maxColumns = Math.max(1, (Math.max(minWidth, maxWidth) - 2 * CartridgeLayout.PAD_X + CartridgeLayout.COLUMN_GAP)
                / (columnW + CartridgeLayout.COLUMN_GAP));
        int n = modules.size();
        heights = new int[n];
        labels = new String[n];
        numbers = new int[n];
        optionTexts = new String[n][];
        infoLines = new ArrayList<>(n);
        infoColors = new int[n][];
        shownStorage = null;
        InfoModule.Context context = client.world == null ? null : new InfoModule.Context(current, client.world, pos.get());
        for (int i = 0; i < n; i++) {
            CartridgeModule module = modules.get(i);
            infoLines.add(List.of());
            labels[i] = module.labelKey() == null ? null : I18n.translate(module.labelKey());
            int body = switch (module) {
                case ChoiceModule choice -> {
                    optionTexts[i] = new String[choice.options().size()];
                    for (int o = 0; o < optionTexts[i].length; o++) optionTexts[i][o] = I18n.translate(choice.options().get(o).key());
                    yield choice.swatches() ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
                }
                case NumberModule number -> NumberModule.ROW_H;
                case StallOffersModule stall -> StallOffersModule.PREVIEW_H;
                case OutcomeListModule list -> OutcomeListModule.BODY_H;
                case BlockSlotModule slot -> {
                    if (slot.inline()) yield 0;
                    // Beside the slot: the block's name (drawn each frame), then the hint, wrapped
                    List<OrderedText> hint = textRenderer.wrapLines(Text.translatable(slot.hintKey()), blockTextW());
                    infoLines.set(i, hint);
                    yield Math.max(BlockSlotModule.SLOT, (1 + hint.size()) * InfoModule.LINE_H);
                }
                case ContainersModule containers -> containersHeight(current);
                case ColorModule color -> {
                    int rows = (ColorModule.DEFAULT + colorsPerRow()) / colorsPerRow();
                    yield rows * ColorModule.SWATCH + (rows - 1) * ColorModule.GAP;
                }
                case GhostSlotsModule ghosts -> {
                    String k = CartridgeItem.MENU_KEY + "inventory.";
                    giveText = I18n.translate(k + "give");
                    takeText = I18n.translate(k + "take");
                    // Slots that only give (a Trichaudron's prizes): their own help, no give / take legend
                    ghostHelpW = Math.max(40, columnW - ghostHelpX());
                    ghostHelp = textRenderer.wrapLines(Text.translatable(ghosts.signed() ? k + "wheel" : ghosts.helpKey()), ghostHelpW);
                    int legend = ghosts.signed() ? 26 : 2;
                    yield Math.max(ghosts.rows() * GhostSlotsModule.SLOT, legend + ghostHelp.size() * InfoModule.LINE_H);
                }
                case InfoModule info -> {
                    int width = columnW - (info.hasIcon() ? InfoModule.ICON + 4 : 0);
                    List<OrderedText> lines = new ArrayList<>();
                    List<Integer> colors = new ArrayList<>();
                    if (context != null) {
                        for (InfoModule.Line line : info.content(context)) {
                            for (OrderedText wrapped : textRenderer.wrapLines(line.text(), width)) {
                                lines.add(wrapped);
                                colors.add(switch (line.tone()) {
                                    case NORMAL -> TONE_NORMAL;
                                    case SOFT -> TONE_SOFT;
                                    case GOOD -> TONE_GOOD;
                                    case BAD -> TONE_BAD;
                                });
                            }
                        }
                    }
                    infoLines.set(i, lines);
                    infoColors[i] = colors.stream().mapToInt(Integer::intValue).toArray();
                    yield Math.max(lines.size() * InfoModule.LINE_H, info.hasIcon() ? InfoModule.ICON + 2 : 0);
                }
                default -> module.height();
            };
            // An inline module sits in the previous one's box: no height, no number of its own
            heights[i] = module.inline() ? 0 : (module.labelKey() == null ? 0 : CartridgeModule.LABEL_H) + body;
            numbers[i] = (i == 0 ? 0 : numbers[i - 1]) + (module.inline() || inPanel(i) ? 0 : 1);
        }
        emptyLines = n == 0 ? textRenderer.wrapLines(Text.translatable(CartridgeItem.MENU_KEY + "empty.hint"), columnW) : List.of();

        int room = Math.max(60, Math.min(maxHeight, 4096) - CartridgeLayout.TOP - CartridgeLayout.BOTTOM);
        layout = CartridgeLayout.of(modules, Math.min(maxContent, room), heights, maxColumns, columnW);
        int needed = n == 0 ? CartridgeLayout.TOP + Math.max(CartridgeLayout.EMPTY_CONTENT, emptyLines.size() * InfoModule.LINE_H) + CartridgeLayout.BOTTOM
                : layout.height();
        // The ghost slots are real slots: a shell holding them never scrolls
        boolean canScroll = CartridgeLayout.indexOf(modules, GhostSlotsModule.class) < 0;
        shellHeight = Math.max(minHeight, canScroll ? Math.min(needed, Math.max(maxHeight, CartridgeLayout.TOP + CartridgeLayout.BOTTOM + 60)) : needed);
        scrollMax = canScroll ? Math.max(0, needed - shellHeight) : 0;
        scroll = Math.clamp(scroll, 0, scrollMax);
        buildPaths();
    }

    /** Whether module {@code i} is in the panel of a tab row before it (no number of its own). */
    private boolean inPanel(int i) {
        for (int k = 0; k < i; k++) if (k + modules.get(k).panelSize() >= i) return true;
        return false;
    }

    private int value(ItemStack current, int i) {
        return pending[i] != NO_PENDING ? pending[i] : modules.get(i).get(current);
    }

    /** The top of module {@code i} on screen (scrolled). */
    private int moduleY(int i) {
        return y + layout.y(i) - scroll;
    }

    private int contentTop() {
        return y + CartridgeLayout.TOP - 2;
    }

    private int contentBottom() {
        return y + shellHeight - CartridgeLayout.BOTTOM + 2;
    }

    private boolean inContent(double mouseY) {
        return scrollMax == 0 || (mouseY >= contentTop() && mouseY < contentBottom());
    }

    // ------------------------------------------------------------------ traces

    private void buildPaths() {
        int n = modules.size();
        paths = new int[n][];
        pathLength = new int[n];
        int h = shellHeight;
        int cx = x + width() / 2, bottom = y + h - 5, bus = y + h - 9;
        for (int i = 0; i < n; i++) {
            int gutter = x + layout.x(i) - 5, end = x + layout.x(i) - 2;
            int target = Math.clamp(moduleY(i) + 4, contentTop() + 2, Math.max(contentTop() + 2, contentBottom() - 4));
            if (layout.owner(i) >= 0) {
                // In a tab's panel: the current goes to its tab row
                paths[i] = paths[layout.owner(i)];
            } else if (layout.right(i)) {
                // The right half of a row: up the column's gutter to the gap above the row, along it, down beside the module
                int column = x + layout.x(i - 1) - 5, above = Math.clamp(moduleY(i) - 3, contentTop() + 1, Math.max(contentTop() + 1, contentBottom() - 4));
                int side = x + layout.x(i) - 4;
                paths[i] = new int[]{cx, bottom, cx, bus, column, bus, column, above, side, above, side, target, end, target};
            } else {
                paths[i] = new int[]{cx, bottom, cx, bus, gutter, bus, gutter, target, end, target};
            }
            int length = 0;
            for (int p = 0; p < paths[i].length - 2; p += 2) length += Math.abs(paths[i][p + 2] - paths[i][p]) + Math.abs(paths[i][p + 3] - paths[i][p + 1]);
            pathLength[i] = length;
        }
    }

    /** Sets {@link #pointX}/{@link #pointY} to the point {@code distance} pixels along path {@code i}. */
    private void pointAt(int i, int distance) {
        int[] path = paths[i];
        int left = Math.max(0, distance);
        for (int p = 0; p < path.length - 2; p += 2) {
            int dx = path[p + 2] - path[p], dy = path[p + 3] - path[p + 1];
            int length = Math.abs(dx) + Math.abs(dy);
            if (left <= length) {
                pointX = path[p] + Integer.signum(dx) * Math.min(left, Math.abs(dx));
                pointY = path[p + 1] + Integer.signum(dy) * Math.min(left, Math.abs(dy));
                return;
            }
            left -= length;
        }
        pointX = path[path.length - 2];
        pointY = path[path.length - 1];
    }

    /** Draws path {@code i}: lit from the contacts up to {@code lit} pixels, dim after. */
    private void drawTrace(DrawContext context, int i, int lit) {
        int[] path = paths[i];
        int walked = 0;
        for (int p = 0; p < path.length - 2; p += 2) {
            int x1 = path[p], y1 = path[p + 1], x2 = path[p + 2], y2 = path[p + 3];
            int length = Math.abs(x2 - x1) + Math.abs(y2 - y1);
            if (length == 0) continue;
            int litHere = Math.clamp(lit - walked, 0, length);
            int sx = Integer.signum(x2 - x1), sy = Integer.signum(y2 - y1);
            int mx = x1 + sx * litHere, my = y1 + sy * litHere;
            if (litHere > 0) line(context, x1, y1, mx, my, TRACE_ON);
            if (litHere < length) line(context, mx, my, x2, y2, TRACE_OFF);
            walked += length;
        }
    }

    private static void line(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        context.fill(Math.min(x1, x2), Math.min(y1, y2), Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
    }

    private void drawPulse(DrawContext context, int i, int head) {
        pointAt(i, head - 2);
        PartyGui.pixel(context, pointX, pointY, PULSE_TAIL);
        pointAt(i, head - 1);
        PartyGui.pixel(context, pointX, pointY, PULSE_MID);
        pointAt(i, head);
        context.fill(pointX - 1, pointY, pointX + 2, pointY + 1, 0x80FFD34A);
        context.fill(pointX, pointY - 1, pointX + 1, pointY + 2, 0x80FFD34A);
        PartyGui.pixel(context, pointX, pointY, PULSE_HEAD);
    }

    // ------------------------------------------------------------------ drawing

    /** The shell, the label, the traces and the modules (the ghost slots' items are drawn by the screen). */
    public void render(DrawContext context, int mouseX, int mouseY) {
        ItemStack current = stack.get();
        int w = width(), h = shellHeight;
        long now = Util.getMeasuringTimeMs();
        boolean animate = CartridgeGuiConfig.animate();
        drawShell(context, w, h);
        drawLabel(context, current, w, mouseX, mouseY);

        // Traces: the opening pulse, then the sparks of the changes
        int opened = animate ? (int) ((now - openedAt) * OPEN_SPEED) : Integer.MAX_VALUE;
        for (int i = 0; i < modules.size(); i++) {
            CartridgeModule module = modules.get(i);
            boolean powered = !module.editable() || module.enabled(current);
            drawTrace(context, i, powered ? Math.min(opened, pathLength[i]) : 0);
        }
        drawContacts(context, w, h);
        for (int i = 0; i < modules.size(); i++) {
            if (!animate) break;
            if (opened < pathLength[i]) {
                drawPulse(context, i, opened);
            } else if (flashStart[i] == 0) {
                flashStart[i] = openedAt + (long) (pathLength[i] / OPEN_SPEED);
            }
            if (sparkStart[i] != 0) {
                int spark = (int) ((now - sparkStart[i]) * SPARK_SPEED);
                if (spark < pathLength[i]) {
                    drawPulse(context, i, spark);
                } else {
                    flashStart[i] = sparkStart[i] + (long) (pathLength[i] / SPARK_SPEED);
                    sparkStart[i] = 0;
                }
            }
        }

        for (int l = 0; l < emptyLines.size(); l++) {
            UiText.line(context, textRenderer, emptyLines.get(l), x + CartridgeLayout.PAD_X, y + CartridgeLayout.TOP + l * InfoModule.LINE_H, columnW, TONE_SOFT, false);
        }
        boolean clipped = scrollMax > 0;
        if (clipped) context.enableScissor(x + 4, contentTop(), x + w - 4, contentBottom());
        boolean mouseInContent = inContent(mouseY);
        stallHovered = false;
        for (int i = 0; i < modules.size(); i++) {
            if (modules.get(i).panelSize() > 0) drawPanel(context, i);
        }
        for (int i = 0; i < modules.size(); i++) {
            int my = moduleY(i);
            if (clipped && (my + heights[i] < contentTop() || my > contentBottom())) continue;
            float flash = animate && flashStart[i] != 0 ? 1F - (now - flashStart[i]) / (float) FLASH_MS : 0F;
            drawModule(context, current, i, x + layout.x(i), my, mouseInContent ? mouseX : -1, mouseInContent ? mouseY : -1, Math.clamp(flash, 0F, 1F));
        }
        // A small stall to click: the hand over it (the screen resets the cursor when it closes)
        if (CartridgeLayout.indexOf(modules, StallOffersModule.class) >= 0) HandCursor.update(stallHovered);
        if (clipped) {
            context.disableScissor();
            // The scroll bar: a thin track on the right, its thumb where the modules shown are
            int top = contentTop() + 2, bottom = contentBottom() - 2, track = bottom - top;
            int total = track + scrollMax;
            int thumb = Math.max(8, track * track / total);
            int thumbY = top + (track - thumb) * scroll / scrollMax;
            context.fill(x + w - 7, top, x + w - 5, bottom, 0xFF2B2B2B);
            context.fill(x + w - 7, thumbY, x + w - 5, thumbY + thumb, 0xFFC9A227);
        }
    }

    private void drawShell(DrawContext context, int w, int h) {
        context.fill(x + 2, y, x + w - 2, y + h, OUTLINE);
        context.fill(x, y + 2, x + w, y + h - 2, OUTLINE);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, OUTLINE);
        context.fill(x + 2, y + 1, x + w - 2, y + h - 1, BODY);
        context.fill(x + 1, y + 2, x + w - 1, y + h - 2, BODY);
        context.fill(x + 3, y + 3, x + w - 3, y + h - 3, BEVEL);
        context.fill(x + 4, y + 4, x + w - 4, y + h - 4, SCREEN);
    }

    /** The edge connector at the bottom (where the current comes from). */
    private void drawContacts(DrawContext context, int w, int h) {
        int cx = x + w / 2;
        context.fill(cx - 17, y + h - 4, cx + 18, y + h - 1, OUTLINE);
        for (int p = -3; p <= 3; p++) {
            int px = cx + p * 5 - 1;
            context.fill(px, y + h - 4, px + 3, y + h - 1, GOLD);
            context.fill(px, y + h - 4, px + 3, y + h - 3, GOLD_LIGHT);
        }
    }

    /** The name in the label: the item's (or its custom name), or its short name when that one doesn't fit. */
    private String labelName(ItemStack current, CartridgeItem cartridge, int room) {
        if (cartridge == null) return I18n.translate(CartridgeItem.MENU_KEY + "empty");
        String name = current.getName().getString();
        if (textRenderer.getWidth(name) <= room || current.contains(DataComponentTypes.CUSTOM_NAME)) return name;
        // The icon already says it is a cartridge: « Rejouer » rather than « Cartouche Rejouer »
        String key = CartridgeItem.MENU_KEY + "name." + Registries.ITEM.getId(current.getItem()).getPath();
        return I18n.hasTranslation(key) ? I18n.translate(key) : name;
    }

    private void drawLabel(DrawContext context, ItemStack current, int w, int mouseX, int mouseY) {
        int sx = x + 6, sy = y + 5, sw = w - 12, sh = 20;
        CartridgeItem cartridge = CartridgeMenus.cartridge(current);
        int color = Argb.opaque(cartridge == null ? 0x8A8A8A : cartridge.menuColor(current));
        context.fill(sx, sy, sx + sw, sy + sh, STICKER_EDGE);
        context.fill(sx + 1, sy + 1, sx + sw - 1, sy + 3, STRIP);
        context.fill(sx + 1, sy + 3, sx + sw - 1, sy + sh - 1, color);
        context.fill(sx + 1, sy + 3, sx + sw - 1, sy + 4, Argb.lighten(color, 0.35F));
        context.fill(sx + 1, sy + sh - 2, sx + sw - 1, sy + sh - 1, Argb.darken(color, 0.35F));
        boolean dark = Argb.luminance(color) > 165;
        int textColor = dark ? 0xFF2A2A2A : 0xFFFFFFFF;
        int textX = sx + 4;
        if (cartridge != null) {
            context.drawItem(current, sx + 3, sy + 3);
            textX = sx + 22;
        }
        int right = sx + sw - 4;
        if (slotPower >= 0) {
            // The redstone power that selects this cartridge: the number, then a redstone dust
            String power = Integer.toString(slotPower);
            right -= 16;
            context.drawItem(REDSTONE, right, sy + 2);
            right -= textRenderer.getWidth(power);
            UiText.line(context, textRenderer, power, right, sy + 7, textRenderer.getWidth(power), textColor, !dark);
            right -= 4;
        }
        int room = Math.max(10, right - textX);
        UiText.line(context, textRenderer, labelName(current, cartridge, room), textX, sy + 7, room, textColor, !dark);
    }

    private void drawModule(DrawContext context, ItemStack current, int i, int mx, int my, int mouseX, int mouseY, float flash) {
        CartridgeModule module = modules.get(i);
        boolean enabled = module.enabled(current);
        int top = my;
        if (module.inline()) {
            // Its title on the right of the previous one's line, its widget under it
            if (labels[i] != null) {
                int w = inlineTitleW(i);
                UiText.line(context, textRenderer, labels[i], mx + columnW - w, my + 1, w, enabled ? LABEL : LABEL_OFF, false);
            }
            if (module instanceof BlockSlotModule slot) drawBlockSlot(context, current, i, slot, mx, my + CartridgeModule.LABEL_H, mouseX, mouseY, canEdit.getAsBoolean());
            return;
        }
        if (labels[i] != null && layout.owner(i) >= 0) {
            // In a tab's panel: its title alone, the tab row has the number
            UiText.line(context, textRenderer, labels[i], mx, my + 1, layout.w(i), enabled ? LABEL : LABEL_OFF, false);
            top += CartridgeModule.LABEL_H;
        } else if (labels[i] != null) {
            int shown = numbers[i];
            String number = (shown < 10 ? "0" : "") + shown;
            int chipColor = flash > 0 ? Argb.lerp(CHIP, 0xFFFFC52E, flash) : CHIP;
            int numberWidth = textRenderer.getWidth(number);
            context.fill(mx - 1, my, mx + numberWidth + 2, my + 9, chipColor);
            UiText.line(context, textRenderer, number, mx + 1, my + 1, numberWidth, flash > 0.5F ? 0xFF3B2600 : (enabled ? CHIP_TEXT : LABEL_OFF), false);
            int lx = mx + numberWidth + 5, room = layout.w(i) - (lx - mx);
            // An inline module next: its title takes the right of the line
            if (i + 1 < modules.size() && modules.get(i + 1).inline() && labels[i + 1] != null) room -= inlineTitleW(i + 1) + 6;
            // A choice of colours: its title names the one chosen (« Réseau : violet »)
            String chosen = null;
            int chosenColor = 0;
            if (module instanceof ChoiceModule choice && choice.swatches()) {
                int value = value(current, i);
                if (value >= 0 && value < choice.options().size()) {
                    chosen = optionTexts[i][value];
                    chosenColor = Argb.lighten(Argb.opaque(choice.options().get(value).color()), 0.3F);
                }
            }
            if (chosen == null) {
                UiText.line(context, textRenderer, labels[i], lx, my + 1, room, enabled ? LABEL : LABEL_OFF, false);
            } else {
                int chosenWidth = Math.min(textRenderer.getWidth(chosen), room / 2);
                int separatorWidth = textRenderer.getWidth(": ");
                int titleRoom = room - chosenWidth - separatorWidth;
                int titleWidth = Math.min(textRenderer.getWidth(labels[i]), titleRoom);
                UiText.line(context, textRenderer, labels[i], lx, my + 1, titleRoom, enabled ? LABEL : LABEL_OFF, false);
                UiText.line(context, textRenderer, ": ", lx + titleWidth, my + 1, separatorWidth, enabled ? LABEL : LABEL_OFF, false);
                UiText.line(context, textRenderer, chosen, lx + titleWidth + separatorWidth, my + 1, chosenWidth, chosenColor, false);
            }
            top += CartridgeModule.LABEL_H;
        }
        boolean editable = canEdit.getAsBoolean();
        switch (module) {
            case ChoiceModule choice -> drawChoice(context, current, i, choice, mx, top, mouseX, mouseY, enabled && editable);
            case NumberModule number -> drawNumber(context, current, i, number, mx, top, mouseX, mouseY, editable);
            case ColorModule color -> drawColors(context, current, i, color, mx, top, mouseX, mouseY, editable);
            case GhostSlotsModule ghosts -> drawGhostSlots(context, ghosts, mx, my);
            case StallOffersModule stall -> drawStall(context, current, mx, top, mouseX, mouseY, editable);
            case BlockSlotModule slot -> drawBlockSlot(context, current, i, slot, mx, top, mouseX, mouseY, editable);
            case InfoModule info -> drawInfo(context, current, i, info, mx, top);
            case ContainersModule containers -> drawContainers(context, current, mx, top, mouseX, mouseY, editable);
            case OutcomeListModule list -> {
                if (outcomeList != null) outcomeList.render(context, current, mx, top, layout.w(i), mouseX, mouseY, editable);
            }
            default -> {
            }
        }
    }

    // Tab rows: the panel of the chosen tab, the tabs on it (the chosen one open into it)
    private static final int PANEL_BG = 0xFF363636, TAB_OFF = 0xFF2A2A2A;
    private void drawPanel(DrawContext context, int i) {
        int px = x + layout.x(i), pw = layout.w(i);
        int top = moduleY(i) + heights[i] - 1, bottom = y + layout.panelBottom(i) - scroll;
        context.fill(px, top, px + pw, bottom, OUTLINE);
        context.fill(px + 1, top + 1, px + pw - 1, bottom - 1, PANEL_BG);
    }

    private void drawTabs(DrawContext context, ItemStack current, int i, ChoiceModule choice, int mx, int top,
                          int mouseX, int mouseY, boolean active) {
        int value = value(current, i);
        int bh = choice.swatches() ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
        for (int o = 0; o < choice.options().size(); o++) {
            int bx = choiceX(choice, i, mx, o), bw = choiceW(choice, i);
            boolean selected = o == value;
            boolean hovered = active && HitArea.contains(mouseX, mouseY, bx, top, bw, bh);
            // The chosen tab stands taller and opens into the panel (its bottom edge is the panel's top, gone); what is
            // on a tab stays at the same place whether chosen or not
            int tabTop = selected ? top : top + 2;
            context.fill(bx, tabTop, bx + bw, top + bh, OUTLINE);
            context.fill(bx + 1, tabTop + 1, bx + bw - 1, top + bh + (selected ? 1 : -1), selected ? PANEL_BG : hovered ? 0xFF3A3A3A : TAB_OFF);
            ChoiceModule.Option option = choice.options().get(o);
            if (option.color() >= 0) {
                int c = Argb.opaque(option.color());
                int sw = selected ? c : Argb.darken(c, hovered ? 0.25F : 0.5F);
                context.fill(bx + 3, top + 5, bx + bw - 3, top + bh - 3, sw);
            } else {
                buttonText(context, optionTexts[i][o], bx, top + 2, bw, bh - 2, selected ? LABEL : TONE_SOFT);
            }
        }
    }

    private void drawChoice(DrawContext context, ItemStack current, int i, ChoiceModule choice, int mx, int top,
                            int mouseX, int mouseY, boolean active) {
        if (choice.panelSize() > 0) {
            drawTabs(context, current, i, choice, mx, top, mouseX, mouseY, active);
            return;
        }
        int n = choice.options().size();
        int value = value(current, i);
        boolean swatches = choice.swatches();
        for (int o = 0; o < n; o++) {
            int bx = choiceX(choice, i, mx, o), bw = choiceW(choice, i), bh = swatches ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
            boolean selected = o == value && choice.enabled(current);
            boolean over = HitArea.contains(mouseX, mouseY, bx, top, bw, bh);
            boolean hovered = active && over;
            PartyGui.Theme theme = !choice.enabled(current) ? PartyGui.BUTTON_DISABLED : selected ? PartyGui.BUTTON_SELECTED : PartyGui.BUTTON;
            if (hovered && !selected) theme = theme.brighter();
            PartyGui.button(context, bx, top, bw, bh, theme, selected);
            ChoiceModule.Option option = choice.options().get(o);
            if (swatches) {
                int c = Argb.opaque(option.color());
                PartyGui.button(context, bx + 4, top + 4, bw - 8, bh - 8,
                        new PartyGui.Theme(Argb.darken(c, 0.6F), Argb.lighten(c, 0.4F), c, Argb.darken(c, 0.35F)), false);
            } else {
                String text = optionTexts[i][o];
                int color = !choice.enabled(current) ? 0xFF7A7A7A : selected ? 0xFF3B2600 : PartyGui.TEXT_DARK;
                buttonText(context, text, bx, top, bw, bh, color);
            }
        }
    }

    /**
     * A text in a button, centred on its ink, as much room above as below its capitals (see {@link ButtonGlyphs}); too
     * wide for it, it scrolls, 3 pixels from each side.
     */
    private void buttonText(DrawContext context, String text, int x, int y, int w, int h, int color) {
        if (textRenderer.getWidth(text) - 1 <= w - 6) ButtonGlyphs.text(context, textRenderer, text, x, y, w, h, color);
        else UiText.centered(context, textRenderer, text, x + 3, y + (h - ButtonGlyphs.CAP_H) / 2, w - 6, color, false);
    }

    private int choiceW(ChoiceModule choice, int i) {
        int n = choice.options().size();
        int w = (layout.w(i) - (n - 1) * 3) / n;
        return choice.swatches() ? Math.min(34, w) : w;
    }

    private int choiceX(ChoiceModule choice, int i, int mx, int option) {
        return mx + option * (choiceW(choice, i) + 3);
    }

    // Number: [-] figure [+] lamps
    private static final int MINUS_X = 0, FIGURE_X = 18, FIGURE_W = 26, PLUS_X = 46, LAMPS_X = 66, STEP_W = 16;

    private int lampWidth(NumberModule number, int i) {
        int count = number.max() - number.min() + 1;
        return Math.min(12, (layout.w(i) - LAMPS_X) / count - 2);
    }

    private boolean showsLamps(NumberModule number, int i) {
        return number.max() - number.min() + 1 <= 9 && lampWidth(number, i) >= 3;
    }

    private void drawNumber(DrawContext context, ItemStack current, int i, NumberModule number, int mx, int top,
                            int mouseX, int mouseY, boolean active) {
        int value = value(current, i);
        int color = Argb.opaque(number.color(current));
        stepButton(context, mx + MINUS_X, top, "-", active && value > number.min(), HitArea.contains(mouseX, mouseY, mx + MINUS_X, top, STEP_W, NumberModule.ROW_H));
        stepButton(context, mx + PLUS_X, top, "+", active && value < number.max(), HitArea.contains(mouseX, mouseY, mx + PLUS_X, top, STEP_W, NumberModule.ROW_H));
        context.fill(mx + FIGURE_X, top, mx + FIGURE_X + FIGURE_W, top + NumberModule.ROW_H, OUTLINE);
        context.fill(mx + FIGURE_X + 1, top + 1, mx + FIGURE_X + FIGURE_W - 1, top + NumberModule.ROW_H - 1, 0xFF161A14);
        String figure = Integer.toString(value);
        // Twice the size while it fits (one or two figures), else the plain size (scrolling in the screen if wider)
        int scale = textRenderer.getWidth(figure) * 2 <= FIGURE_W - 2 ? 2 : 1;
        int shown = Math.min(textRenderer.getWidth(figure) * scale, FIGURE_W - 2);
        context.getMatrices().push();
        context.getMatrices().translate(mx + FIGURE_X + (FIGURE_W - shown) / 2F + (scale == 2 ? 1 : 0),
                top + (scale == 2 ? 2 : 5), 0);
        context.getMatrices().scale(scale, scale, 1);
        UiText.line(context, textRenderer, figure, 0, 0, (FIGURE_W - 2) / scale, Argb.lighten(color, 0.3F), false);
        context.getMatrices().pop();
        if (!showsLamps(number, i)) return;
        int lw = lampWidth(number, i);
        for (int v = number.min(); v <= number.max(); v++) {
            int lx = mx + LAMPS_X + (v - number.min()) * (lw + 2);
            boolean on = v <= value;
            PartyGui.button(context, lx, top + 3, lw, 12, on
                    ? new PartyGui.Theme(Argb.darken(color, 0.6F), Argb.lighten(color, 0.4F), color, Argb.darken(color, 0.35F))
                    : new PartyGui.Theme(OUTLINE, 0xFF3A3A3A, 0xFF2B2B2B, 0xFF1E1E1E), !on);
            String digit = Integer.toString(v);
            if (lw >= textRenderer.getWidth(digit) + 3) {
                UiText.centered(context, textRenderer, digit, lx + 1, top + 5, lw, on ? 0xFFFFFFFF : 0xFF6A6A6A, false);
            }
        }
    }

    private void stepButton(DrawContext context, int bx, int top, String sign, boolean active, boolean hovered) {
        PartyGui.Theme theme = active ? (hovered ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON) : PartyGui.BUTTON_DISABLED;
        PartyGui.button(context, bx, top, STEP_W, NumberModule.ROW_H, theme, false);
        int color = active ? PartyGui.TEXT_DARK : 0xFF7A7A7A;
        if (sign.equals("+")) ButtonGlyphs.plus(context, bx, top, STEP_W, NumberModule.ROW_H, color);
        else ButtonGlyphs.minus(context, bx, top, STEP_W, NumberModule.ROW_H, color);
    }

    private int colorX(int mx, int value) {
        return mx + (value % colorsPerRow()) * (ColorModule.SWATCH + ColorModule.GAP);
    }

    private int colorY(int top, int value) {
        return top + (value / colorsPerRow()) * (ColorModule.SWATCH + ColorModule.GAP);
    }

    private void drawColors(DrawContext context, ItemStack current, int i, ColorModule module, int mx, int top,
                            int mouseX, int mouseY, boolean active) {
        int value = value(current, i);
        // The base swatch shows its host's base colour (a check point's blue), the neutral one in a hand
        BlockPos at = pos.get();
        int base = module.baseColor(current, client.world == null || at == null ? null : client.world.getBlockEntity(at));
        for (int v = 0; v <= ColorModule.DEFAULT; v++) {
            int sx = colorX(mx, v), sy = colorY(top, v);
            int c = Argb.opaque(v == ColorModule.DEFAULT ? base : module.colorOf(v));
            boolean hovered = active && HitArea.contains(mouseX, mouseY, sx, sy, ColorModule.SWATCH, ColorModule.SWATCH);
            if (v == value) context.fill(sx - 1, sy - 1, sx + ColorModule.SWATCH + 1, sy + ColorModule.SWATCH + 1, 0xFFFFC52E);
            PartyGui.button(context, sx, sy, ColorModule.SWATCH, ColorModule.SWATCH,
                    new PartyGui.Theme(hovered ? 0xFFFFFFFF : Argb.darken(c, 0.6F), Argb.lighten(c, 0.4F), c, Argb.darken(c, 0.35F)), v == value);
            if (v == ColorModule.DEFAULT) {
                // The base colour: a small cartridge mark
                int mark = Argb.luminance(c) > 140 ? 0xFF2A2A2A : 0xFFFFFFFF;
                context.fill(sx + 4, sy + 3, sx + 9, sy + 10, mark);
                context.fill(sx + 5, sy + 4, sx + 8, sy + 6, STRIP);
            }
        }
    }

    private void drawGhostSlots(DrawContext context, GhostSlotsModule ghosts, int mx, int my) {
        for (int s = 0; s < ghosts.count(); s++) PartyGui.slot(context, mx + ghosts.slotX(s), my + ghosts.slotY(s));
        // On the right of the slots: green gives, red takes, then how to set the quantity
        int tx = mx + ghostHelpX(), ty = my + CartridgeModule.LABEL_H + 2, room = columnW - ghostHelpX() - 8;
        if (!ghosts.signed()) {
            for (int l = 0; l < ghostHelp.size(); l++) UiText.line(context, textRenderer, ghostHelp.get(l), tx, ty + l * InfoModule.LINE_H, ghostHelpW, TONE_SOFT, false);
            return;
        }
        context.fill(tx, ty + 1, tx + 5, ty + 6, 0xFF46AE2E);
        UiText.line(context, textRenderer, giveText, tx + 8, ty, room, TONE_NORMAL, false);
        context.fill(tx, ty + 12, tx + 5, ty + 17, 0xFFD9283B);
        UiText.line(context, textRenderer, takeText, tx + 8, ty + 11, room, TONE_NORMAL, false);
        for (int l = 0; l < ghostHelp.size(); l++) UiText.line(context, textRenderer, ghostHelp.get(l), tx, ty + 24 + l * InfoModule.LINE_H, ghostHelpW, TONE_SOFT, false);
    }

    // ------------------------------------------------------------------ the Shop Cartridge's small stall

    /** The width the small stall may take: the column, less an inline module on its right (the Shop's disguise). */
    private int stallRoom() {
        int i = CartridgeLayout.indexOf(modules, StallOffersModule.class);
        boolean beside = i >= 0 && i + 1 < modules.size() && modules.get(i + 1).inline();
        return beside ? columnW - BlockSlotModule.SLOT - 4 : columnW;
    }

    /** The scale of the small stall: as large as the column allows, up to {@link StallOffersModule#SCALE}. */
    private float stallScale() {
        return Math.min(StallOffersModule.SCALE, (float) stallRoom() / StallOffersModule.STALL_W);
    }

    private int stallW() {
        return Math.round(StallOffersModule.STALL_W * stallScale());
    }

    private int stallH() {
        return Math.round(StallOffersModule.STALL_H * stallScale());
    }

    /** Its left edge: centred in its room. */
    private int stallX(int mx) {
        return mx + (stallRoom() - stallW()) / 2;
    }

    /** The offers of {@code current}, read again when they changed. */
    private List<ItemStack> stallSlots(ItemStack current) {
        InventoryComponent offers = current.get(ModComponents.INVENTORY_COMPONENT);
        if (offers != stallFrom || current.getItem() != stallItem || stallSlots.isEmpty()) {
            stallFrom = offers;
            stallItem = current.getItem();
            stallSlots = ShopCartridgeItem.stallSlots(current);
        }
        return stallSlots;
    }

    /**
     * The Trading Stall in small, drawn with its own texture: the items sold over the awning, the stock on the arrows,
     * the prices under them. Under the mouse (the player may edit): outlined, the hand cursor; a click opens it.
     */
    private void drawStall(DrawContext context, ItemStack current, int mx, int top, int mouseX, int mouseY, boolean editable) {
        int sx = stallX(mx), sy = top + 1;
        boolean over = editable && HitArea.contains(mouseX, mouseY, sx, sy, stallW(), stallH());
        stallHovered |= over;
        float scale = stallScale();
        context.getMatrices().push();
        context.getMatrices().translate(sx, sy, 0);
        context.getMatrices().scale(scale, scale, 1);
        StallArt.drawTop(context, 0, 0);
        StallArt.drawItems(context, textRenderer, stallSlots(current), 0, 0);
        for (int column = 0; column < ShopCartridgeItem.OFFERS; column++) StallArt.drawStock(context, textRenderer, 0, 0, column, stock.applyAsInt(column));
        context.getMatrices().pop();
        if (over) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            context.drawBorder(sx - 1, sy - 1, stallW() + 2, stallH() + 2, 0xFFFFFFFF);
            context.getMatrices().pop();
        }
    }

    /** Whether the mouse is over a small stall this frame (the hand cursor). */
    private boolean stallHovered;

    // ------------------------------------------------------------------ a block slot (the Shop Cartridge's disguise)

    private static final int BLOCK_TEXT_X = BlockSlotModule.SLOT + 5;

    /** The width of an inline module's title: its text, at most half the column. */
    private int inlineTitleW(int i) {
        return Math.min(textRenderer.getWidth(labels[i]), columnW / 2);
    }

    /** The left edge of a block slot: the column's start, or its end when inline (beside the small stall). */
    private int blockSlotX(BlockSlotModule slot, int mx) {
        return slot.inline() ? mx + columnW - BlockSlotModule.SLOT : mx;
    }

    /** The top of a block slot, {@code top} being its module's top under its title. */
    private static int blockSlotY(BlockSlotModule slot, int top) {
        return slot.inline() ? top + 1 : top;
    }

    /** The width of the texts beside a block slot. */
    private int blockTextW() {
        return Math.max(40, columnW - BLOCK_TEXT_X);
    }

    /** What the player holds on the cursor in this screen (an item picked from the inventory), empty if nothing. */
    private ItemStack cursor() {
        return client.player == null ? ItemStack.EMPTY : client.player.currentScreenHandler.getCursorStack();
    }

    /** The block shown in the slot (the pending one while the server writes it), null for none: the default. */
    private @Nullable BlockState shownBlock(ItemStack current, int i, BlockSlotModule slot) {
        int value = value(current, i);
        return value == BlockSlotModule.EMPTY ? null : slot.blockOf(Registries.ITEM.get(value));
    }

    /** The name shown for the slot's block: its own, or « Default: Gold Block ». */
    private static Text blockName(@Nullable BlockState block, BlockSlotModule slot) {
        return block == null ? Text.translatable(CartridgeItem.MENU_KEY + "block_slot.default", slot.defaultBlock().getBlock().getName())
                : block.getBlock().getName();
    }

    /**
     * The slot (as the ghost slots), its block in it or the default one faded; beside it the block's name and the hint.
     * Under the mouse with an item on the cursor: outlined green if that block can go there, red if not.
     */
    private void drawBlockSlot(DrawContext context, ItemStack current, int i, BlockSlotModule slot, int moduleX, int moduleTop,
                               int mouseX, int mouseY, boolean editable) {
        int size = BlockSlotModule.SLOT;
        int mx = blockSlotX(slot, moduleX), top = blockSlotY(slot, moduleTop);
        context.fill(mx, top, mx + size, top + size, 0xFF2B2B2B);
        context.fill(mx + 1, top + 1, mx + size, top + size, 0xFF5A5A5A);
        context.fill(mx + 1, top + 1, mx + size - 1, top + size - 1, 0xFF3A3A3A);
        BlockState block = shownBlock(current, i, slot);
        context.drawItem(new ItemStack((block == null ? slot.defaultBlock() : block).getBlock()), mx + 1, top + 1);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        // The default: faded, it is not set
        if (block == null) context.fill(mx + 1, top + 1, mx + size - 1, top + size - 1, 0xA03A3A3A);
        if (editable && HitArea.contains(mouseX, mouseY, mx, top, size, size)) {
            ItemStack cursor = cursor();
            if (cursor.isEmpty()) context.fill(mx + 1, top + 1, mx + size - 1, top + size - 1, 0x80FFFFFF);
            else context.drawBorder(mx, top, size, size, slot.blockOf(cursor.getItem()) != null ? 0xFF7CE06A : 0xFFFF5050);
        }
        context.getMatrices().pop();
        // Inline: the slot alone (its name and hint in its tooltip)
        if (slot.inline()) return;
        int tx = mx + BLOCK_TEXT_X, w = blockTextW();
        UiText.line(context, textRenderer, blockName(block, slot), tx, top + 1, w, block == null ? TONE_SOFT : TONE_NORMAL, false);
        List<OrderedText> hint = infoLines.get(i);
        for (int l = 0; l < hint.size(); l++) {
            UiText.line(context, textRenderer, hint.get(l), tx, top + 1 + (l + 1) * InfoModule.LINE_H, w, TONE_SOFT, false);
        }
    }

    private void drawInfo(DrawContext context, ItemStack current, int i, InfoModule info, int mx, int top) {
        int tx = mx;
        if (info.hasIcon()) {
            ItemStack icon = info.icon(current);
            if (icon != null && !icon.isEmpty()) context.drawItem(icon, mx, top);
            tx += InfoModule.ICON + 4;
        }
        List<OrderedText> lines = infoLines.get(i);
        int[] colors = infoColors[i];
        for (int l = 0; l < lines.size(); l++) {
            UiText.line(context, textRenderer, lines.get(l), tx, top + 1 + l * InfoModule.LINE_H, columnW - (tx - mx),
                    colors != null && l < colors.length ? colors[l] : TONE_NORMAL, false);
        }
    }

    // ------------------------------------------------------------------ the containers of an Inventory Cartridge

    private static final int ROW_BUTTON = 9;

    /** A container of the list as shown: its text and whether it is there. */
    private record ContainerRow(String name, String coords, int color, @Nullable String state) {
    }

    /** The cartridge's containers as rows, in order: « 1 Coffre 12 64 -3 », and « absent » / « non chargé ». */
    private List<ContainerRow> rows(ItemStack current) {
        List<ContainerRow> rows = new ArrayList<>();
        if (client.world == null) return rows;
        for (var target : CartridgeContainers.of(current, client.world.getRegistryKey())) {
            BlockPos at = target.pos();
            String coords = at.getX() + " " + at.getY() + " " + at.getZ();
            String state = null;
            String name;
            if (!target.dimension().equals(client.world.getRegistryKey())) {
                name = target.dimension().getValue().getPath();
                state = I18n.translate(CartridgeItem.MENU_KEY + "inventory.chests.elsewhere");
            } else if (!client.world.isChunkLoaded(at.getX() >> 4, at.getZ() >> 4)) {
                name = I18n.translate(CartridgeItem.MENU_KEY + "inventory.chests.container");
                state = I18n.translate(CartridgeItem.MENU_KEY + "inventory.chests.unloaded");
            } else if (!(client.world.getBlockEntity(at) instanceof Inventory)) {
                name = I18n.translate(CartridgeItem.MENU_KEY + "inventory.chests.container");
                state = I18n.translate(CartridgeItem.MENU_KEY + "inventory.chests.absent");
            } else {
                name = client.world.getBlockState(at).getBlock().getName().getString();
            }
            rows.add(new ContainerRow(name, coords, state == null ? TONE_NORMAL : TONE_BAD, state));
        }
        return rows;
    }

    /**
     * Where the items come from: its containers when it has some (at once, from the cartridge), else what the server
     * sent (the Party Controller of its board, or none).
     */
    private CartridgeContainers.Storage storage(int count) {
        if (count > 0) return CartridgeContainers.Storage.LINKED;
        CartridgeContainers.Storage sent = storage.get();
        // Its last container just removed: none until the server says otherwise
        return sent == CartridgeContainers.Storage.LINKED ? CartridgeContainers.Storage.NONE : sent;
    }

    /** The module's height: the storage, its containers two per row, then the hint (how to link: the command). */
    private int containersHeight(ItemStack current) {
        int count = rows(current).size();
        shownStorage = storage(count);
        return (1 + containersBody(count) + 1) * ContainersModule.ROW_H;
    }

    /** The rows of its containers, two per row. */
    private static int containersBody(int count) {
        return (count + ContainersModule.PER_ROW - 1) / ContainersModule.PER_ROW;
    }

    /** The y of the hint row, under the list: clicked, the chat explains how to link. */
    private static int containersHintY(int top, int count) {
        return top + (1 + containersBody(count)) * ContainersModule.ROW_H;
    }

    /** The hint: how to link (with the order of the list when there is one); a click runs the command. */
    private static Text containersHint(int count) {
        return count > 0 ? Text.translatable(CartridgeItem.MENU_KEY + "inventory.click") : LinksHelp.hint();
    }

    private int cellW() {
        return (columnW - 2) / ContainersModule.PER_ROW;
    }

    private int cellX(int mx, int index) {
        return mx + (index % ContainersModule.PER_ROW) * (cellW() + 2);
    }

    /** Under the storage's line. */
    private static int cellY(int top, int index) {
        return top + (1 + index / ContainersModule.PER_ROW) * ContainersModule.ROW_H;
    }

    /** The x of a cell's button: « earlier in the order », then remove at the cell's right end. */
    private int rowButtonX(int mx, int index, int op) {
        int right = cellX(mx, index) + cellW() - ROW_BUTTON;
        return op == ContainersModule.REMOVE ? right : right - ROW_BUTTON - 1;
    }

    private void drawContainers(DrawContext context, ItemStack current, int mx, int top, int mouseX, int mouseY, boolean editable) {
        List<ContainerRow> rows = rows(current);
        // First, where the items come from: green, it has a storage; red, none
        CartridgeContainers.Storage now = storage(rows.size());
        String title = switch (now) {
            case LINKED -> rows.size() == 1 ? I18n.translate(CartridgeItem.MENU_KEY + "storage.linked.one")
                    : I18n.translate(CartridgeItem.MENU_KEY + "storage.linked", rows.size());
            case PARTY_CONTROLLER -> I18n.translate(CartridgeItem.MENU_KEY + "storage.party");
            case NONE -> I18n.translate(CartridgeItem.MENU_KEY + "storage.none");
        };
        UiText.line(context, textRenderer, title, mx, top + 1, columnW, now == CartridgeContainers.Storage.NONE ? TONE_BAD : TONE_GOOD, false);
        int buttons = editable ? 2 * (ROW_BUTTON + 1) : 0;
        for (int r = 0; r < rows.size(); r++) {
            ContainerRow row = rows.get(r);
            int cx = cellX(mx, r), cy = cellY(top, r);
            String number = Integer.toString(r + 1);
            int numberWidth = textRenderer.getWidth(number);
            context.fill(cx, cy, cx + numberWidth + 3, cy + 9, CHIP);
            UiText.line(context, textRenderer, number, cx + 2, cy + 1, numberWidth, CHIP_TEXT, false);
            int tx = cx + numberWidth + 5, room = cellW() - (tx - cx) - buttons;
            UiText.line(context, textRenderer, row.coords(), tx, cy + 1, room, row.color(), false);
            if (!editable) continue;
            rowButton(context, rowButtonX(mx, r, ContainersModule.UP), cy, "◀", r > 0, mouseX, mouseY);
            rowButton(context, rowButtonX(mx, r, ContainersModule.REMOVE), cy, "×", true, mouseX, mouseY);
        }
        // How to link, under the list: a click shows it in the chat
        int hintY = containersHintY(top, rows.size());
        boolean hintHovered = HitArea.contains(mouseX, mouseY, mx, hintY, columnW, ContainersModule.ROW_H);
        UiText.line(context, textRenderer, containersHint(rows.size()), mx, hintY + 1, columnW, hintHovered ? LinksHelp.HOVER_COLOR : TONE_SOFT, false);
    }

    private void rowButton(DrawContext context, int bx, int ry, String sign, boolean active, int mouseX, int mouseY) {
        boolean hovered = active && HitArea.contains(mouseX, mouseY, bx, ry, ROW_BUTTON, ROW_BUTTON);
        PartyGui.Theme theme = active ? (hovered ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON) : PartyGui.BUTTON_DISABLED;
        PartyGui.button(context, bx, ry, ROW_BUTTON, ROW_BUTTON, theme, false);
        int color = active ? PartyGui.TEXT_DARK : 0xFF7A7A7A;
        if (sign.equals("×")) ButtonGlyphs.cross(context, bx, ry, ROW_BUTTON, ROW_BUTTON, color);
        else ButtonGlyphs.left(context, bx, ry, ROW_BUTTON, ROW_BUTTON, color);
    }

    /** The give / take marks of the ghost slots (a green or red frame), over their items. */
    public static void drawGhostMarks(DrawContext context, List<Slot> slots, int originX, int originY) {
        for (Slot slot : slots) {
            if (!(slot instanceof GhostSlot) || !slot.isEnabled() || !slot.hasStack()) continue;
            int color = GhostSlot.isPositive(slot.getStack()) ? 0xFF46AE2E : 0xFFD9283B;
            int sx = originX + slot.x, sy = originY + slot.y;
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 300);
            context.fill(sx - 1, sy - 1, sx + 17, sy, color);
            context.fill(sx - 1, sy + 16, sx + 17, sy + 17, color);
            context.fill(sx - 1, sy, sx, sy + 16, color);
            context.fill(sx + 16, sy, sx + 17, sy + 16, color);
            context.getMatrices().pop();
        }
    }

    // ------------------------------------------------------------------ tooltips

    public boolean renderTooltip(DrawContext context, int mouseX, int mouseY) {
        // Its own tooltip is the one shown, not the whole text of a scrolling line under it
        boolean shown = drawTooltip(context, mouseX, mouseY);
        if (shown) UiText.cancelTooltip();
        return shown;
    }

    private boolean drawTooltip(DrawContext context, int mouseX, int mouseY) {
        if (!inContent(mouseY)) return false;
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            CartridgeModule module = modules.get(i);
            int mx = x + layout.x(i), top = moduleY(i) + (module.labelKey() != null ? CartridgeModule.LABEL_H : 0);
            if (!module.tooltipKeys().isEmpty() && module.labelKey() != null
                    && HitArea.contains(mouseX, mouseY, mx, moduleY(i), layout.w(i), CartridgeModule.LABEL_H)) {
                List<Text> lines = new ArrayList<>();
                lines.add(Text.translatable(module.labelKey()));
                for (String key : module.tooltipKeys()) lines.add(Text.translatable(key).formatted(Formatting.GRAY));
                context.drawTooltip(textRenderer, wrap(lines), mouseX, mouseY);
                return true;
            }
            if (module instanceof ChoiceModule choice) {
                int bh = choice.swatches() ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
                for (int o = 0; o < choice.options().size(); o++) {
                    if (!HitArea.contains(mouseX, mouseY, choiceX(choice, i, mx, o), top, choiceW(choice, i), bh)) continue;
                    ChoiceModule.Option option = choice.options().get(o);
                    List<Text> lines = new ArrayList<>();
                    lines.add(Text.translatable(option.key()));
                    if (option.tooltipKey() != null) lines.add(Text.translatable(option.tooltipKey()).formatted(Formatting.GRAY));
                    if (!choice.enabled(current)) lines.add(Text.translatable(CartridgeItem.MENU_KEY + "unpowered").formatted(Formatting.DARK_GRAY));
                    context.drawTooltip(textRenderer, wrap(lines), mouseX, mouseY);
                    return true;
                }
            } else if (module instanceof ContainersModule) {
                List<ContainerRow> rows = rows(current);
                for (int r = 0; r < rows.size(); r++) {
                    int cy = cellY(top, r);
                    if (canEdit.getAsBoolean()) {
                        for (int op : new int[]{ContainersModule.UP, ContainersModule.REMOVE}) {
                            if (!HitArea.contains(mouseX, mouseY, rowButtonX(mx, r, op), cy, ROW_BUTTON, ROW_BUTTON)) continue;
                            context.drawTooltip(textRenderer, Text.translatable(CartridgeItem.MENU_KEY + "inventory.chests." + (op == ContainersModule.REMOVE ? "remove" : "up")), mouseX, mouseY);
                            return true;
                        }
                    }
                    // The container: its block, where, and whether it is there
                    if (HitArea.contains(mouseX, mouseY, cellX(mx, r), cy, cellW(), ContainersModule.ROW_H)) {
                        ContainerRow row = rows.get(r);
                        List<Text> lines = new ArrayList<>();
                        lines.add(Text.literal((r + 1) + ". " + row.name()));
                        lines.add(Text.literal(row.coords()).formatted(Formatting.GRAY));
                        if (row.state() != null) lines.add(Text.literal(row.state()).formatted(Formatting.RED));
                        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
                        return true;
                    }
                }
            } else if (module instanceof OutcomeListModule && outcomeList != null) {
                List<Text> lines = outcomeList.tooltip(mouseX, mouseY);
                if (lines != null) {
                    context.drawTooltip(textRenderer, wrap(lines), mouseX, mouseY);
                    return true;
                }
            } else if (module instanceof StallOffersModule) {
                if (HitArea.contains(mouseX, mouseY, stallX(mx), top + 1, stallW(), stallH())) {
                    int offers = ShopCartridgeItem.offers(current).size();
                    List<Text> lines = new ArrayList<>();
                    lines.add(Text.translatable(CartridgeItem.MENU_KEY + "shop.offers.count", offers, ShopCartridgeItem.OFFERS)
                            .formatted(offers == 0 ? Formatting.RED : Formatting.WHITE));
                    if (canEdit.getAsBoolean()) lines.add(Text.translatable(CartridgeItem.MENU_KEY + "shop.offers.open").formatted(Formatting.YELLOW));
                    context.drawTooltip(textRenderer, lines, mouseX, mouseY);
                    return true;
                }
            } else if (module instanceof BlockSlotModule slot) {
                if (HitArea.contains(mouseX, mouseY, blockSlotX(slot, mx), blockSlotY(slot, top), BlockSlotModule.SLOT, BlockSlotModule.SLOT)) {
                    // A slot reads as one: only what it is for, and why a block in hand doesn't go there
                    List<Text> lines = new ArrayList<>();
                    lines.add(Text.translatable(slot.hintKey()));
                    ItemStack cursor = cursor();
                    if (canEdit.getAsBoolean() && !cursor.isEmpty() && slot.blockOf(cursor.getItem()) == null) {
                        lines.add(Text.translatable(CartridgeItem.MENU_KEY + "block_slot.refused", cursor.getName()).formatted(Formatting.RED));
                    }
                    context.drawTooltip(textRenderer, wrap(lines), mouseX, mouseY);
                    return true;
                }
            } else if (module instanceof ColorModule) {
                for (int v = 0; v <= ColorModule.DEFAULT; v++) {
                    if (!HitArea.contains(mouseX, mouseY, colorX(mx, v), colorY(top, v), ColorModule.SWATCH, ColorModule.SWATCH)) continue;
                    context.drawTooltip(textRenderer, v == ColorModule.DEFAULT ? Text.translatable(CartridgeItem.MENU_KEY + "color.default")
                            : Text.translatable("color.minecraft." + DyeColor.byId(v).getName()), mouseX, mouseY);
                    return true;
                }
            }
        }
        if (!canEdit.getAsBoolean() && !modules.isEmpty() && HitArea.contains(mouseX, mouseY, x, y, width(), shellHeight)) {
            context.drawTooltip(textRenderer, Text.translatable(CartridgeItem.MENU_KEY + "read_only"), mouseX, mouseY);
            return true;
        }
        return false;
    }

    private List<Text> wrap(List<Text> lines) {
        List<Text> out = new ArrayList<>();
        for (Text line : lines) {
            if (textRenderer.getWidth(line) <= 200) {
                out.add(line);
                continue;
            }
            // A long line: split on words (a tooltip line doesn't wrap by itself)
            StringBuilder current = new StringBuilder();
            for (String word : line.getString().split(" ")) {
                if (!current.isEmpty() && textRenderer.getWidth(current + " " + word) > 200) {
                    out.add(Text.literal(current.toString()).setStyle(line.getStyle()));
                    current.setLength(0);
                }
                if (!current.isEmpty()) current.append(' ');
                current.append(word);
            }
            if (!current.isEmpty()) out.add(Text.literal(current.toString()).setStyle(line.getStyle()));
        }
        return out;
    }

    // ------------------------------------------------------------------ input

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) return rightClicked(mouseX, mouseY);
        if (button != 0) return false;
        if (!inContent(mouseY)) return false;
        ItemStack current = stack.get();
        // The containers' hint, for anyone who sees it: how to link, in the chat
        for (int i = 0; i < modules.size(); i++) {
            CartridgeModule module = modules.get(i);
            if (!(module instanceof ContainersModule) || !module.enabled(current)) continue;
            int mx = x + layout.x(i), top = moduleY(i) + (module.labelKey() != null ? CartridgeModule.LABEL_H : 0);
            if (HitArea.contains(mouseX, mouseY, mx, containersHintY(top, rows(current).size()), columnW, ContainersModule.ROW_H)) {
                click();
                LinksHelp.show();
                return true;
            }
        }
        if (!canEdit.getAsBoolean()) return false;
        for (int i = 0; i < modules.size(); i++) {
            CartridgeModule module = modules.get(i);
            if (!module.editable() || !module.enabled(current)) continue;
            int mx = x + layout.x(i), top = moduleY(i) + (module.labelKey() != null ? CartridgeModule.LABEL_H : 0);
            int value = value(current, i);
            switch (module) {
                case ChoiceModule choice -> {
                    int bh = choice.swatches() ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
                    for (int o = 0; o < choice.options().size(); o++) {
                        if (HitArea.contains(mouseX, mouseY, choiceX(choice, i, mx, o), top, choiceW(choice, i), bh)) return change(i, o, value);
                    }
                }
                case NumberModule number -> {
                    if (HitArea.contains(mouseX, mouseY, mx + MINUS_X, top, STEP_W, NumberModule.ROW_H)) return change(i, Math.max(number.min(), value - step(number)), value);
                    if (HitArea.contains(mouseX, mouseY, mx + PLUS_X, top, STEP_W, NumberModule.ROW_H)) return change(i, Math.min(number.max(), value + step(number)), value);
                    if (showsLamps(number, i)) {
                        int lw = lampWidth(number, i);
                        for (int v = number.min(); v <= number.max(); v++) {
                            if (HitArea.contains(mouseX, mouseY, mx + LAMPS_X + (v - number.min()) * (lw + 2), top + 3, lw, 12)) return change(i, v, value);
                        }
                    }
                }
                case ColorModule color -> {
                    for (int v = 0; v <= ColorModule.DEFAULT; v++) {
                        if (HitArea.contains(mouseX, mouseY, colorX(mx, v), colorY(top, v), ColorModule.SWATCH, ColorModule.SWATCH)) return change(i, v, value);
                    }
                }
                case StallOffersModule stall -> {
                    if (HitArea.contains(mouseX, mouseY, stallX(mx), top + 1, stallW(), stallH())) {
                        // Not a value: the server opens the stall (the spark runs while it opens)
                        sparkStart[i] = Util.getMeasuringTimeMs();
                        ClientPlayNetworking.send(new CartridgeSettingPayload(syncId.getAsInt(), stall.id(), StallOffersModule.OPEN));
                        click();
                        return true;
                    }
                }
                case BlockSlotModule slot -> {
                    if (HitArea.contains(mouseX, mouseY, blockSlotX(slot, mx), blockSlotY(slot, top), BlockSlotModule.SLOT, BlockSlotModule.SLOT)) {
                        int chosen = BlockSlotModule.valueOf(cursor());
                        // Not a block that can go there: refused at once, the slot unchanged
                        if (!slot.accepts(current, chosen)) {
                            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_VILLAGER_NO, 1.0F));
                            return true;
                        }
                        return change(i, chosen, value);
                    }
                }
                case OutcomeListModule list -> {
                    if (outcomeList != null && outcomeList.mouseClicked(mouseX, mouseY)) return true;
                }
                case ContainersModule containers -> {
                    int count = rows(current).size();
                    for (int r = 0; r < count; r++) {
                        int cy = cellY(top, r);
                        for (int op : new int[]{ContainersModule.UP, ContainersModule.REMOVE}) {
                            if (HitArea.contains(mouseX, mouseY, rowButtonX(mx, r, op), cy, ROW_BUTTON, ROW_BUTTON)) {
                                // An action, not a value: sent as is (the list shown follows the server's)
                                return change(i, ContainersModule.action(r, op), Integer.MIN_VALUE);
                            }
                        }
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    /** A right click on a block slot empties it (the default again). */
    private boolean rightClicked(double mouseX, double mouseY) {
        if (!canEdit.getAsBoolean() || !inContent(mouseY)) return false;
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            if (!(modules.get(i) instanceof BlockSlotModule slot) || !slot.enabled(current)) continue;
            int mx = x + layout.x(i), top = moduleY(i) + (slot.labelKey() != null ? CartridgeModule.LABEL_H : 0);
            if (HitArea.contains(mouseX, mouseY, blockSlotX(slot, mx), blockSlotY(slot, top), BlockSlotModule.SLOT, BlockSlotModule.SLOT)) {
                return change(i, BlockSlotModule.EMPTY, value(current, i));
            }
        }
        return false;
    }

    /** How much a click on − / + or a wheel notch changes a number: one, or ten with Shift on a wide range (a price). */
    private static int step(NumberModule number) {
        return Screen.hasShiftDown() && number.max() - number.min() >= 20 ? 10 : 1;
    }

    /** The wheel over a number: one more / one less (ten with Shift on a wide range); elsewhere on a shell too low for its modules: scrolls them. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount == 0 || !HitArea.contains(mouseX, mouseY, x, y, width(), shellHeight)) return false;
        ItemStack current = stack.get();
        // The sentences' box: its figures, its rows (for anyone: reading them too)
        if (outcomeList != null && inContent(mouseY) && outcomeList.mouseScrolled(mouseX, mouseY, amount)) return true;
        if (canEdit.getAsBoolean() && inContent(mouseY)) {
            for (int i = 0; i < modules.size(); i++) {
                if (!(modules.get(i) instanceof NumberModule number) || !number.enabled(current)) continue;
                if (!HitArea.contains(mouseX, mouseY, x + layout.x(i), moduleY(i), layout.w(i), heights[i])) continue;
                int value = value(current, i);
                return change(i, Math.clamp(value + (amount > 0 ? step(number) : -step(number)), number.min(), number.max()), value);
            }
        }
        if (scrollMax > 0) {
            int next = Math.clamp(scroll - (int) Math.signum(amount) * SCROLL_STEP, 0, scrollMax);
            if (next != scroll) {
                scroll = next;
                buildPaths();
            }
            return true;
        }
        return false;
    }

    private boolean change(int i, int value, int current) {
        if (value == current) return true;
        CartridgeModule module = modules.get(i);
        if (!module.accepts(stack.get(), value)) return true;
        pending[i] = value;
        pendingTicks[i] = 0;
        sparkStart[i] = Util.getMeasuringTimeMs();
        ClientPlayNetworking.send(new CartridgeSettingPayload(syncId.getAsInt(), module.id(), value));
        click();
        return true;
    }

    private void click() {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return HitArea.contains(mouseX, mouseY, x, y, width(), shellHeight);
    }
}
