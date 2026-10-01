package fr.lordfinn.steveparty.client.gui.cartridge;

import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeLayout;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeMenus;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ColorModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.GhostSlotsModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import fr.lordfinn.steveparty.payloads.custom.CartridgeSettingPayload;
import fr.lordfinn.steveparty.screen_handlers.custom.GhostSlot;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A cartridge's menu, drawn as its shell (the dark cartridge of the Inventory Cartridge's menu): its label on top
 * (the cartridge's colour, icon and name), its modules laid out by {@link CartridgeLayout}, and its contacts at the
 * bottom. Shared by the cartridge's own screen and the tile's interface.
 * <p>
 * Texts always fit: the texts of several lines (descriptions, infos, hints) wrap on as many lines as they need and
 * their module takes that height; a one-line text too long for its box (the name, a module's title, a button) is cut
 * with « … » and scrolls back and forth while the mouse is over it. In a narrow window the columns shrink; when the
 * modules are taller than the place the shell has, they scroll (mouse wheel, a thin bar on the right).
 * <p>
 * « Current »: copper traces run from the contacts to each module. When the menu opens, a pulse runs from the contacts
 * along every trace and lights them up, each module's number flashing as it arrives; a change sends a spark along its
 * module's trace; a module that can't be changed now gets no current (dim trace). Everything is drawn with fills from
 * precomputed paths (no allocation per frame), and nothing moves when the animation is off (the lightning button of
 * the label, or the game's « Hide Lightning Flashes »).
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
    private static final int TOGGLE = 9;
    /** A text too long for its box, under the mouse: pixels per second, and the pause at each end. */
    private static final float MARQUEE_SPEED = 28F;
    private static final long MARQUEE_PAUSE_MS = 700;
    private static final String ELLIPSIS = "…";
    private static final int SCROLL_STEP = 14;

    private final MinecraftClient client;
    private final TextRenderer textRenderer;
    private final Supplier<ItemStack> stack;
    private final Supplier<BlockPos> pos;
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
    private @Nullable Text slotLabel;

    // State per module (sized when the cartridge changes)
    private int[] heights = new int[0];
    private int[] pending = new int[0];
    private int[] pendingTicks = new int[0];
    private long[] sparkStart = new long[0];
    private long[] flashStart = new long[0];
    private String[] labels = new String[0];
    private String[][] optionTexts = new String[0][];
    private List<List<OrderedText>> infoLines = new ArrayList<>();
    private int[][] infoColors = new int[0][];
    private List<OrderedText> ghostHelp = List.of(), emptyLines = List.of();
    private String giveText = "", takeText = "";
    private int infoTicks;
    /** Texts cut with « … », by text and width (cleared with the layout). */
    private final Map<String, String> truncated = new HashMap<>();
    private long marqueeStart;
    private @Nullable String marqueeText;
    // Traces: per module, 5 points (x, y) from the contacts to the module, and the lengths
    private int[][] paths = new int[0][];
    private int[] pathLength = new int[0];
    private long openedAt;
    private int pointX, pointY;

    /**
     * @param maxContent the content height before another column (see {@link CartridgeLayout})
     * @param minWidth   the shell's minimum width (a shell of a fixed size: with the player's inventory)
     * @param minHeight  the shell's minimum height (e.g. beside a tile, the tile's height)
     */
    public CartridgePanel(MinecraftClient client, Supplier<ItemStack> stack, Supplier<BlockPos> pos, IntSupplier syncId,
                          BooleanSupplier canEdit, int maxContent, int minWidth, int minHeight) {
        this.client = client;
        this.textRenderer = client.textRenderer;
        this.stack = stack;
        this.pos = pos;
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

    /** A note in the label (e.g. the slot of an Advanced Tile). */
    public void setSlotLabel(@Nullable Text slotLabel) {
        this.slotLabel = slotLabel;
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
        java.util.Arrays.fill(pending, NO_PENDING);
        pendingTicks = new int[n];
        sparkStart = new long[n];
        flashStart = new long[n];
        scroll = 0;
        infoTicks = INFO_REFRESH_TICKS;
        openedAt = Util.getMeasuringTimeMs();
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
        if (infoTicks-- <= 0) {
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
        truncated.clear();
        // The widest column the place leaves room for
        columnW = Math.clamp(Math.max(minWidth, maxWidth) - 2 * CartridgeLayout.PAD_X, CartridgeLayout.MIN_COLUMN_W, CartridgeLayout.COLUMN_W);
        int maxColumns = Math.max(1, (Math.max(minWidth, maxWidth) - 2 * CartridgeLayout.PAD_X + CartridgeLayout.COLUMN_GAP)
                / (columnW + CartridgeLayout.COLUMN_GAP));
        int n = modules.size();
        heights = new int[n];
        labels = new String[n];
        optionTexts = new String[n][];
        infoLines = new ArrayList<>(n);
        infoColors = new int[n][];
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
                case ColorModule color -> {
                    int rows = (ColorModule.DEFAULT + colorsPerRow()) / colorsPerRow();
                    yield rows * ColorModule.SWATCH + (rows - 1) * ColorModule.GAP;
                }
                case GhostSlotsModule ghosts -> {
                    String k = CartridgeItem.MENU_KEY + "inventory.";
                    giveText = I18n.translate(k + "give");
                    takeText = I18n.translate(k + "take");
                    ghostHelp = textRenderer.wrapLines(Text.translatable(k + "wheel"), Math.max(40, columnW - ghostHelpX()));
                    yield Math.max((GhostSlotsModule.COUNT / GhostSlotsModule.COLUMNS) * GhostSlotsModule.SLOT, 26 + ghostHelp.size() * InfoModule.LINE_H);
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
            heights[i] = (module.labelKey() == null ? 0 : CartridgeModule.LABEL_H) + body;
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

    // ------------------------------------------------------------------ one-line texts that may not fit

    /**
     * Draws {@code text} in a box {@code maxWidth} wide: as is when it fits; else cut with « … », and scrolling back
     * and forth (clipped to the box) while {@code hovered}.
     */
    private void drawFitted(DrawContext context, String text, int tx, int ty, int maxWidth, int color, boolean shadow, boolean hovered) {
        int width = textRenderer.getWidth(text);
        if (width <= maxWidth) {
            context.drawText(textRenderer, text, tx, ty, color, shadow);
            return;
        }
        if (!hovered) {
            String key = maxWidth + "|" + text;
            String cut = truncated.get(key);
            if (cut == null) {
                cut = textRenderer.trimToWidth(text, Math.max(0, maxWidth - textRenderer.getWidth(ELLIPSIS))).stripTrailing() + ELLIPSIS;
                truncated.put(key, cut);
            }
            context.drawText(textRenderer, cut, tx, ty, color, shadow);
            return;
        }
        long now = Util.getMeasuringTimeMs();
        if (!text.equals(marqueeText)) {
            marqueeText = text;
            marqueeStart = now;
        }
        int travel = width - maxWidth;
        long moveMs = (long) (travel / MARQUEE_SPEED * 1000F);
        long cycle = 2 * (MARQUEE_PAUSE_MS + moveMs);
        long t = (now - marqueeStart) % cycle;
        float offset;
        if (t < MARQUEE_PAUSE_MS) offset = 0;
        else if (t < MARQUEE_PAUSE_MS + moveMs) offset = (t - MARQUEE_PAUSE_MS) / (float) moveMs * travel;
        else if (t < 2 * MARQUEE_PAUSE_MS + moveMs) offset = travel;
        else offset = travel - (t - 2 * MARQUEE_PAUSE_MS - moveMs) / (float) moveMs * travel;
        context.enableScissor(tx, ty - 1, tx + maxWidth, ty + 10);
        context.getMatrices().push();
        context.getMatrices().translate(-offset, 0, 0);
        context.drawText(textRenderer, text, tx, ty, color, shadow);
        context.getMatrices().pop();
        context.disableScissor();
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
            paths[i] = new int[]{cx, bottom, cx, bus, gutter, bus, gutter, target, end, target};
            int length = 0;
            for (int p = 0; p < 8; p += 2) length += Math.abs(paths[i][p + 2] - paths[i][p]) + Math.abs(paths[i][p + 3] - paths[i][p + 1]);
            pathLength[i] = length;
        }
    }

    /** Sets {@link #pointX}/{@link #pointY} to the point {@code distance} pixels along path {@code i}. */
    private void pointAt(int i, int distance) {
        int[] path = paths[i];
        int left = Math.max(0, distance);
        for (int p = 0; p < 8; p += 2) {
            int dx = path[p + 2] - path[p], dy = path[p + 3] - path[p + 1];
            int length = Math.abs(dx) + Math.abs(dy);
            if (left <= length) {
                pointX = path[p] + Integer.signum(dx) * Math.min(left, Math.abs(dx));
                pointY = path[p + 1] + Integer.signum(dy) * Math.min(left, Math.abs(dy));
                return;
            }
            left -= length;
        }
        pointX = path[8];
        pointY = path[9];
    }

    /** Draws path {@code i}: lit from the contacts up to {@code lit} pixels, dim after. */
    private void drawTrace(DrawContext context, int i, int lit) {
        int[] path = paths[i];
        int walked = 0;
        for (int p = 0; p < 8; p += 2) {
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
            context.drawText(textRenderer, emptyLines.get(l), x + CartridgeLayout.PAD_X, y + CartridgeLayout.TOP + l * InfoModule.LINE_H, TONE_SOFT, false);
        }
        boolean clipped = scrollMax > 0;
        if (clipped) context.enableScissor(x + 4, contentTop(), x + w - 4, contentBottom());
        boolean mouseInContent = inContent(mouseY);
        for (int i = 0; i < modules.size(); i++) {
            int my = moduleY(i);
            if (clipped && (my + heights[i] < contentTop() || my > contentBottom())) continue;
            float flash = animate && flashStart[i] != 0 ? 1F - (now - flashStart[i]) / (float) FLASH_MS : 0F;
            drawModule(context, current, i, x + layout.x(i), my, mouseInContent ? mouseX : -1, mouseInContent ? mouseY : -1, Math.clamp(flash, 0F, 1F));
        }
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
        if (textRenderer.getWidth(name) <= room || current.contains(net.minecraft.component.DataComponentTypes.CUSTOM_NAME)) return name;
        // The icon already says it is a cartridge: « Rejouer » rather than « Cartouche Rejouer »
        String key = CartridgeItem.MENU_KEY + "name." + Registries.ITEM.getId(current.getItem()).getPath();
        return I18n.hasTranslation(key) ? I18n.translate(key) : name;
    }

    private void drawLabel(DrawContext context, ItemStack current, int w, int mouseX, int mouseY) {
        int sx = x + 6, sy = y + 5, sw = w - 12, sh = 20;
        CartridgeItem cartridge = CartridgeMenus.cartridge(current);
        int color = 0xFF000000 | (cartridge == null ? 0x8A8A8A : cartridge.menuColor(current));
        context.fill(sx, sy, sx + sw, sy + sh, STICKER_EDGE);
        context.fill(sx + 1, sy + 1, sx + sw - 1, sy + 3, STRIP);
        context.fill(sx + 1, sy + 3, sx + sw - 1, sy + sh - 1, color);
        context.fill(sx + 1, sy + 3, sx + sw - 1, sy + 4, lighten(color, 0.35F));
        context.fill(sx + 1, sy + sh - 2, sx + sw - 1, sy + sh - 1, darken(color, 0.35F));
        boolean dark = luminance(color) > 165;
        int textColor = dark ? 0xFF2A2A2A : 0xFFFFFFFF;
        int textX = sx + 4;
        if (cartridge != null) {
            context.drawItem(current, sx + 3, sy + 3);
            textX = sx + 22;
        }
        // The lightning button and the slot's number keep their place; the name takes what is left
        int toggleX = sx + sw - TOGGLE - 4, toggleY = sy + 6;
        int right = toggleX - 4;
        if (slotLabel != null) {
            int slotWidth = textRenderer.getWidth(slotLabel);
            context.drawText(textRenderer, slotLabel, right - slotWidth, sy + 7, dark ? 0xFF4A4A4A : 0xFFE8E8E8, false);
            right -= slotWidth + 4;
        }
        int room = Math.max(10, right - textX);
        boolean hovered = inside(mouseX, mouseY, textX, sy, room, sh);
        drawFitted(context, labelName(current, cartridge, room), textX, sy + 7, room, textColor, !dark, hovered);
        drawToggle(context, toggleX, toggleY, CartridgeGuiConfig.currentAnimation(), inside(mouseX, mouseY, toggleX - 1, toggleY - 1, TOGGLE + 2, TOGGLE + 2));
    }

    /** The lightning button: the current animation on (yellow) or off (grey). */
    private static final String[] BOLT = {"....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##....", ".#.....", "......."};

    private static void drawToggle(DrawContext context, int x, int y, boolean on, boolean hovered) {
        context.fill(x - 1, y - 1, x + TOGGLE + 1, y + TOGGLE + 1, hovered ? 0xFFFFFFFF : OUTLINE);
        context.fill(x, y, x + TOGGLE, y + TOGGLE, 0xFF2B2B2B);
        for (int row = 0; row < BOLT.length; row++) {
            String line = BOLT[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) == '#') PartyGui.pixel(context, x + 1 + col, y + row, on ? 0xFFFFD34A : 0xFF6A6A6A);
            }
        }
    }

    private void drawModule(DrawContext context, ItemStack current, int i, int mx, int my, int mouseX, int mouseY, float flash) {
        CartridgeModule module = modules.get(i);
        boolean enabled = module.enabled(current);
        int top = my;
        if (labels[i] != null) {
            String number = (i + 1 < 10 ? "0" : "") + (i + 1);
            int chipColor = flash > 0 ? mix(CHIP, 0xFFFFC52E, flash) : CHIP;
            int numberWidth = textRenderer.getWidth(number);
            context.fill(mx - 1, my, mx + numberWidth + 2, my + 9, chipColor);
            context.drawText(textRenderer, number, mx + 1, my + 1, flash > 0.5F ? 0xFF3B2600 : (enabled ? CHIP_TEXT : LABEL_OFF), false);
            int lx = mx + numberWidth + 5, room = columnW - (lx - mx);
            boolean hovered = inside(mouseX, mouseY, mx, my, columnW, CartridgeModule.LABEL_H);
            // A choice of colours: its title names the one chosen (« Réseau : violet »)
            String chosen = null;
            int chosenColor = 0;
            if (module instanceof ChoiceModule choice && choice.swatches()) {
                int value = value(current, i);
                if (value >= 0 && value < choice.options().size()) {
                    chosen = optionTexts[i][value];
                    chosenColor = lighten(0xFF000000 | choice.options().get(value).color(), 0.3F);
                }
            }
            if (chosen == null) {
                drawFitted(context, labels[i], lx, my + 1, room, enabled ? LABEL : LABEL_OFF, false, hovered);
            } else {
                int chosenWidth = Math.min(textRenderer.getWidth(chosen), room / 2);
                int titleRoom = room - chosenWidth - textRenderer.getWidth(": ");
                int titleWidth = Math.min(textRenderer.getWidth(labels[i]), titleRoom);
                drawFitted(context, labels[i], lx, my + 1, titleRoom, enabled ? LABEL : LABEL_OFF, false, hovered);
                context.drawText(textRenderer, ": ", lx + titleWidth, my + 1, enabled ? LABEL : LABEL_OFF, false);
                drawFitted(context, chosen, lx + titleWidth + textRenderer.getWidth(": "), my + 1, chosenWidth, chosenColor, false, hovered);
            }
            top += CartridgeModule.LABEL_H;
        }
        boolean editable = canEdit.getAsBoolean();
        switch (module) {
            case ChoiceModule choice -> drawChoice(context, current, i, choice, mx, top, mouseX, mouseY, enabled && editable);
            case NumberModule number -> drawNumber(context, current, i, number, mx, top, mouseX, mouseY, editable);
            case ColorModule color -> drawColors(context, current, i, color, mx, top, mouseX, mouseY, editable);
            case GhostSlotsModule ghosts -> drawGhostSlots(context, ghosts, mx, my);
            case InfoModule info -> drawInfo(context, current, i, info, mx, top);
            default -> {
            }
        }
    }

    private void drawChoice(DrawContext context, ItemStack current, int i, ChoiceModule choice, int mx, int top,
                            int mouseX, int mouseY, boolean active) {
        int n = choice.options().size();
        int value = value(current, i);
        boolean swatches = choice.swatches();
        for (int o = 0; o < n; o++) {
            int bx = choiceX(choice, mx, o), bw = choiceW(choice), bh = swatches ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
            boolean selected = o == value && choice.enabled(current);
            boolean over = inside(mouseX, mouseY, bx, top, bw, bh);
            boolean hovered = active && over;
            PartyGui.Theme theme = !choice.enabled(current) ? PartyGui.BUTTON_DISABLED : selected ? PartyGui.BUTTON_SELECTED : PartyGui.BUTTON;
            if (hovered && !selected) theme = theme.brighter();
            PartyGui.button(context, bx, top, bw, bh, theme, selected);
            int push = selected ? 1 : 0;
            ChoiceModule.Option option = choice.options().get(o);
            if (swatches) {
                int c = 0xFF000000 | option.color();
                PartyGui.button(context, bx + 4 + push, top + 4 + push, bw - 8, bh - 8,
                        new PartyGui.Theme(darken(c, 0.6F), lighten(c, 0.4F), c, darken(c, 0.35F)), false);
            } else {
                String text = optionTexts[i][o];
                int room = bw - 6;
                int width = Math.min(textRenderer.getWidth(text), room);
                int color = !choice.enabled(current) ? 0xFF7A7A7A : selected ? 0xFF3B2600 : PartyGui.TEXT_DARK;
                drawFitted(context, text, bx + (bw - width) / 2 + push, top + 4 + push, room, color, false, over);
            }
        }
    }

    private int choiceW(ChoiceModule choice) {
        int n = choice.options().size();
        int w = (columnW - (n - 1) * 3) / n;
        return choice.swatches() ? Math.min(34, w) : w;
    }

    private int choiceX(ChoiceModule choice, int mx, int option) {
        return mx + option * (choiceW(choice) + 3);
    }

    // Number: [-] figure [+] lamps
    private static final int MINUS_X = 0, FIGURE_X = 18, FIGURE_W = 26, PLUS_X = 46, LAMPS_X = 66, STEP_W = 16;

    private int lampWidth(NumberModule number) {
        int count = number.max() - number.min() + 1;
        return Math.min(12, (columnW - LAMPS_X) / count - 2);
    }

    private boolean showsLamps(NumberModule number) {
        return number.max() - number.min() + 1 <= 9 && lampWidth(number) >= 3;
    }

    private void drawNumber(DrawContext context, ItemStack current, int i, NumberModule number, int mx, int top,
                            int mouseX, int mouseY, boolean active) {
        int value = value(current, i);
        int color = 0xFF000000 | number.color(current);
        stepButton(context, mx + MINUS_X, top, "-", active && value > number.min(), inside(mouseX, mouseY, mx + MINUS_X, top, STEP_W, NumberModule.ROW_H));
        stepButton(context, mx + PLUS_X, top, "+", active && value < number.max(), inside(mouseX, mouseY, mx + PLUS_X, top, STEP_W, NumberModule.ROW_H));
        context.fill(mx + FIGURE_X, top, mx + FIGURE_X + FIGURE_W, top + NumberModule.ROW_H, OUTLINE);
        context.fill(mx + FIGURE_X + 1, top + 1, mx + FIGURE_X + FIGURE_W - 1, top + NumberModule.ROW_H - 1, 0xFF161A14);
        String figure = Integer.toString(value);
        // Twice the size while it fits (one or two figures), else the plain size
        int scale = textRenderer.getWidth(figure) * 2 <= FIGURE_W - 2 ? 2 : 1;
        context.getMatrices().push();
        context.getMatrices().translate(mx + FIGURE_X + (FIGURE_W - textRenderer.getWidth(figure) * scale) / 2F + (scale == 2 ? 1 : 0),
                top + (scale == 2 ? 2 : 5), 0);
        context.getMatrices().scale(scale, scale, 1);
        context.drawText(textRenderer, figure, 0, 0, lighten(color, 0.3F), false);
        context.getMatrices().pop();
        if (!showsLamps(number)) return;
        int lw = lampWidth(number);
        for (int v = number.min(); v <= number.max(); v++) {
            int lx = mx + LAMPS_X + (v - number.min()) * (lw + 2);
            boolean on = v <= value;
            PartyGui.button(context, lx, top + 3, lw, 12, on
                    ? new PartyGui.Theme(darken(color, 0.6F), lighten(color, 0.4F), color, darken(color, 0.35F))
                    : new PartyGui.Theme(OUTLINE, 0xFF3A3A3A, 0xFF2B2B2B, 0xFF1E1E1E), !on);
            String digit = Integer.toString(v);
            if (lw >= textRenderer.getWidth(digit) + 3) {
                context.drawText(textRenderer, digit, lx + (lw - textRenderer.getWidth(digit)) / 2 + 1, top + 5, on ? 0xFFFFFFFF : 0xFF6A6A6A, false);
            }
        }
    }

    private void stepButton(DrawContext context, int bx, int top, String sign, boolean active, boolean hovered) {
        PartyGui.Theme theme = active ? (hovered ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON) : PartyGui.BUTTON_DISABLED;
        PartyGui.button(context, bx, top, STEP_W, NumberModule.ROW_H, theme, false);
        context.drawText(textRenderer, sign, bx + (STEP_W - textRenderer.getWidth(sign)) / 2, top + 5, active ? PartyGui.TEXT_DARK : 0xFF7A7A7A, false);
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
        for (int v = 0; v <= ColorModule.DEFAULT; v++) {
            int sx = colorX(mx, v), sy = colorY(top, v);
            int c = 0xFF000000 | module.colorOf(v);
            boolean hovered = active && inside(mouseX, mouseY, sx, sy, ColorModule.SWATCH, ColorModule.SWATCH);
            if (v == value) context.fill(sx - 1, sy - 1, sx + ColorModule.SWATCH + 1, sy + ColorModule.SWATCH + 1, 0xFFFFC52E);
            PartyGui.button(context, sx, sy, ColorModule.SWATCH, ColorModule.SWATCH,
                    new PartyGui.Theme(hovered ? 0xFFFFFFFF : darken(c, 0.6F), lighten(c, 0.4F), c, darken(c, 0.35F)), v == value);
            if (v == ColorModule.DEFAULT) {
                // The cartridge's own colour: a small cartridge mark
                int mark = luminance(c) > 140 ? 0xFF2A2A2A : 0xFFFFFFFF;
                context.fill(sx + 4, sy + 3, sx + 9, sy + 10, mark);
                context.fill(sx + 5, sy + 4, sx + 8, sy + 6, STRIP);
            }
        }
    }

    private void drawGhostSlots(DrawContext context, GhostSlotsModule ghosts, int mx, int my) {
        for (int s = 0; s < GhostSlotsModule.COUNT; s++) {
            int sx = mx + ghosts.slotX(s) - 1, sy = my + ghosts.slotY(s) - 1;
            context.fill(sx, sy, sx + 18, sy + 18, 0xFF2B2B2B);
            context.fill(sx + 1, sy + 1, sx + 18, sy + 18, 0xFF5A5A5A);
            context.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF3A3A3A);
        }
        // On the right of the slots: green gives, red takes, then how to set the quantity
        int tx = mx + ghostHelpX(), ty = my + CartridgeModule.LABEL_H + 2, room = columnW - ghostHelpX() - 8;
        context.fill(tx, ty + 1, tx + 5, ty + 6, 0xFF46AE2E);
        drawFitted(context, giveText, tx + 8, ty, room, TONE_NORMAL, false, false);
        context.fill(tx, ty + 12, tx + 5, ty + 17, 0xFFD9283B);
        drawFitted(context, takeText, tx + 8, ty + 11, room, TONE_NORMAL, false, false);
        for (int l = 0; l < ghostHelp.size(); l++) context.drawText(textRenderer, ghostHelp.get(l), tx, ty + 24 + l * InfoModule.LINE_H, TONE_SOFT, false);
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
            context.drawText(textRenderer, lines.get(l), tx, top + 1 + l * InfoModule.LINE_H, colors != null && l < colors.length ? colors[l] : TONE_NORMAL, false);
        }
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
        int sx = x + 6, sw = width() - 12;
        int toggleX = sx + sw - TOGGLE - 4, toggleY = y + 11;
        if (inside(mouseX, mouseY, toggleX - 1, toggleY - 1, TOGGLE + 2, TOGGLE + 2)) {
            context.drawTooltip(textRenderer, Text.translatable(CartridgeItem.MENU_KEY
                    + (CartridgeGuiConfig.currentAnimation() ? "animation.on" : "animation.off")), mouseX, mouseY);
            return true;
        }
        if (!inContent(mouseY)) return false;
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            CartridgeModule module = modules.get(i);
            int mx = x + layout.x(i), top = moduleY(i) + (module.labelKey() != null ? CartridgeModule.LABEL_H : 0);
            if (module instanceof ChoiceModule choice) {
                int bh = choice.swatches() ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
                for (int o = 0; o < choice.options().size(); o++) {
                    if (!inside(mouseX, mouseY, choiceX(choice, mx, o), top, choiceW(choice), bh)) continue;
                    ChoiceModule.Option option = choice.options().get(o);
                    List<Text> lines = new ArrayList<>();
                    lines.add(Text.translatable(option.key()));
                    if (option.tooltipKey() != null) lines.add(Text.translatable(option.tooltipKey()).formatted(Formatting.GRAY));
                    if (!choice.enabled(current)) lines.add(Text.translatable(CartridgeItem.MENU_KEY + "unpowered").formatted(Formatting.DARK_GRAY));
                    context.drawTooltip(textRenderer, wrap(lines), mouseX, mouseY);
                    return true;
                }
            } else if (module instanceof ColorModule) {
                for (int v = 0; v <= ColorModule.DEFAULT; v++) {
                    if (!inside(mouseX, mouseY, colorX(mx, v), colorY(top, v), ColorModule.SWATCH, ColorModule.SWATCH)) continue;
                    context.drawTooltip(textRenderer, v == ColorModule.DEFAULT ? Text.translatable(CartridgeItem.MENU_KEY + "color.default")
                            : Text.translatable("color.minecraft." + DyeColor.byId(v).getName()), mouseX, mouseY);
                    return true;
                }
            }
        }
        if (!canEdit.getAsBoolean() && !modules.isEmpty() && inside(mouseX, mouseY, x, y, width(), shellHeight)) {
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
        if (button != 0) return false;
        int sx = x + 6, sw = width() - 12;
        int toggleX = sx + sw - TOGGLE - 4, toggleY = y + 11;
        if (inside(mouseX, mouseY, toggleX - 1, toggleY - 1, TOGGLE + 2, TOGGLE + 2)) {
            CartridgeGuiConfig.setCurrentAnimation(!CartridgeGuiConfig.currentAnimation());
            click();
            return true;
        }
        if (!canEdit.getAsBoolean() || !inContent(mouseY)) return false;
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            CartridgeModule module = modules.get(i);
            if (!module.editable() || !module.enabled(current)) continue;
            int mx = x + layout.x(i), top = moduleY(i) + (module.labelKey() != null ? CartridgeModule.LABEL_H : 0);
            int value = value(current, i);
            switch (module) {
                case ChoiceModule choice -> {
                    int bh = choice.swatches() ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
                    for (int o = 0; o < choice.options().size(); o++) {
                        if (inside(mouseX, mouseY, choiceX(choice, mx, o), top, choiceW(choice), bh)) return change(i, o, value);
                    }
                }
                case NumberModule number -> {
                    if (inside(mouseX, mouseY, mx + MINUS_X, top, STEP_W, NumberModule.ROW_H)) return change(i, Math.max(number.min(), value - 1), value);
                    if (inside(mouseX, mouseY, mx + PLUS_X, top, STEP_W, NumberModule.ROW_H)) return change(i, Math.min(number.max(), value + 1), value);
                    if (showsLamps(number)) {
                        int lw = lampWidth(number);
                        for (int v = number.min(); v <= number.max(); v++) {
                            if (inside(mouseX, mouseY, mx + LAMPS_X + (v - number.min()) * (lw + 2), top + 3, lw, 12)) return change(i, v, value);
                        }
                    }
                }
                case ColorModule color -> {
                    for (int v = 0; v <= ColorModule.DEFAULT; v++) {
                        if (inside(mouseX, mouseY, colorX(mx, v), colorY(top, v), ColorModule.SWATCH, ColorModule.SWATCH)) return change(i, v, value);
                    }
                }
                default -> {
                }
            }
        }
        return false;
    }

    /** The wheel over a number: one more / one less; elsewhere on a shell too low for its modules: scrolls them. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount == 0 || !inside(mouseX, mouseY, x, y, width(), shellHeight)) return false;
        ItemStack current = stack.get();
        if (canEdit.getAsBoolean() && inContent(mouseY)) {
            for (int i = 0; i < modules.size(); i++) {
                if (!(modules.get(i) instanceof NumberModule number) || !number.enabled(current)) continue;
                if (!inside(mouseX, mouseY, x + layout.x(i), moduleY(i), columnW, heights[i])) continue;
                int value = value(current, i);
                return change(i, Math.clamp(value + (amount > 0 ? 1 : -1), number.min(), number.max()), value);
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
        return inside(mouseX, mouseY, x, y, width(), shellHeight);
    }

    // ------------------------------------------------------------------ helpers

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    private static int lighten(int argb, float amount) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r += (int) ((255 - r) * amount);
        g += (int) ((255 - g) * amount);
        b += (int) ((255 - b) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int darken(int argb, float amount) {
        int r = (int) (((argb >> 16) & 0xFF) * (1 - amount)), g = (int) (((argb >> 8) & 0xFF) * (1 - amount)), b = (int) ((argb & 0xFF) * (1 - amount));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }
}
