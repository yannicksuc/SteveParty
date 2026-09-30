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
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * A cartridge's menu, drawn as its shell (the dark cartridge of the Inventory Cartridge's menu): its label on top
 * (the cartridge's colour, icon and name), its modules laid out by {@link CartridgeLayout}, and its contacts at the
 * bottom. Shared by the cartridge's own screen and the tile's interface.
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

    private final MinecraftClient client;
    private final TextRenderer textRenderer;
    private final Supplier<ItemStack> stack;
    private final Supplier<BlockPos> pos;
    private final IntSupplier syncId;
    private final BooleanSupplier canEdit;
    private final int maxContent;
    private final int minHeight;

    private @Nullable Item item;
    private List<CartridgeModule> modules = List.of();
    private CartridgeLayout layout = CartridgeLayout.of(List.of(), 0);
    private int x, y;
    private @Nullable Text slotLabel;

    // State per module (sized when the cartridge changes)
    private int[] pending = new int[0];
    private int[] pendingTicks = new int[0];
    private long[] sparkStart = new long[0];
    private long[] flashStart = new long[0];
    private List<List<OrderedText>> infoLines = new ArrayList<>();
    private int[][] infoColors = new int[0][];
    private int infoTicks;
    // Traces: per module, 5 points (x, y) from the contacts to the module, and the lengths
    private int[][] paths = new int[0][];
    private int[] pathLength = new int[0];
    private long openedAt;
    private int pointX, pointY;

    /**
     * @param maxContent the content height before a second column (see {@link CartridgeLayout})
     * @param minHeight  the shell's minimum height (e.g. beside a tile, the tile's height)
     */
    public CartridgePanel(MinecraftClient client, Supplier<ItemStack> stack, Supplier<BlockPos> pos, IntSupplier syncId,
                          BooleanSupplier canEdit, int maxContent, int minHeight) {
        this.client = client;
        this.textRenderer = client.textRenderer;
        this.stack = stack;
        this.pos = pos;
        this.syncId = syncId;
        this.canEdit = canEdit;
        this.maxContent = maxContent;
        this.minHeight = minHeight;
        refresh();
    }

    public void setOrigin(int x, int y) {
        this.x = x;
        this.y = y;
        buildPaths();
    }

    /** A note in the label (e.g. the slot of an Advanced Tile). */
    public void setSlotLabel(@Nullable Text slotLabel) {
        this.slotLabel = slotLabel;
    }

    public int width() {
        return layout.width();
    }

    public int height() {
        return Math.max(minHeight, layout.height());
    }

    public List<CartridgeModule> modules() {
        return modules;
    }

    public CartridgeLayout layout() {
        return layout;
    }

    /** Rebuilds the layout when the cartridge shown changed (another slot selected, a cartridge inserted...). */
    public boolean refresh() {
        ItemStack current = stack.get();
        Item now = CartridgeMenus.cartridge(current) == null ? null : current.getItem();
        if (now == item && (now != null || modules.isEmpty()) && pending.length == modules.size()) return false;
        item = now;
        modules = now == null ? List.of() : CartridgeMenus.modules(current);
        layout = CartridgeLayout.of(modules, maxContent);
        int n = modules.size();
        pending = new int[n];
        java.util.Arrays.fill(pending, NO_PENDING);
        pendingTicks = new int[n];
        sparkStart = new long[n];
        flashStart = new long[n];
        infoLines = new ArrayList<>(n);
        for (int i = 0; i < n; i++) infoLines.add(List.of());
        infoColors = new int[n][];
        infoTicks = 0;
        openedAt = Util.getMeasuringTimeMs();
        buildPaths();
        return true;
    }

    /** Once per client tick: the pending values, the info lines. */
    public void tick() {
        refresh();
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            if (pending[i] == NO_PENDING) continue;
            if (modules.get(i).get(current) == pending[i] || ++pendingTicks[i] > PENDING_TICKS) pending[i] = NO_PENDING;
        }
        if (infoTicks-- <= 0) {
            infoTicks = INFO_REFRESH_TICKS;
            updateInfo(current);
        }
    }

    private void updateInfo(ItemStack current) {
        if (client.world == null) return;
        InfoModule.Context context = new InfoModule.Context(current, client.world, pos.get());
        for (int i = 0; i < modules.size(); i++) {
            if (!(modules.get(i) instanceof InfoModule info)) continue;
            int width = CartridgeLayout.COLUMN_W - (info.hasIcon() ? InfoModule.ICON + 4 : 0);
            List<OrderedText> lines = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();
            for (InfoModule.Line line : info.content(context)) {
                for (OrderedText wrapped : textRenderer.wrapLines(line.text(), width)) {
                    if (lines.size() >= info.lines()) break;
                    lines.add(wrapped);
                    colors.add(switch (line.tone()) {
                        case NORMAL -> TONE_NORMAL;
                        case SOFT -> TONE_SOFT;
                        case GOOD -> TONE_GOOD;
                        case BAD -> TONE_BAD;
                    });
                }
            }
            infoLines.set(i, lines);
            infoColors[i] = colors.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private int value(ItemStack current, int i) {
        return pending[i] != NO_PENDING ? pending[i] : modules.get(i).get(current);
    }

    // ------------------------------------------------------------------ traces

    private void buildPaths() {
        int n = modules.size();
        paths = new int[n][];
        pathLength = new int[n];
        int h = height();
        int cx = x + layout.width() / 2, bottom = y + h - 5, bus = y + h - 9;
        for (int i = 0; i < n; i++) {
            int gutter = x + layout.x(i) - 5, target = y + layout.y(i) + 4, end = x + layout.x(i) - 2;
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
        int w = layout.width(), h = height();
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

        if (modules.isEmpty()) {
            Text empty = Text.translatable(CartridgeItem.MENU_KEY + "empty.hint");
            List<OrderedText> lines = textRenderer.wrapLines(empty, w - 2 * CartridgeLayout.PAD_X);
            for (int l = 0; l < lines.size() && l < 2; l++) {
                context.drawText(textRenderer, lines.get(l), x + CartridgeLayout.PAD_X, y + CartridgeLayout.TOP + l * 10, TONE_SOFT, false);
            }
        }
        for (int i = 0; i < modules.size(); i++) {
            float flash = animate && flashStart[i] != 0 ? 1F - (now - flashStart[i]) / (float) FLASH_MS : 0F;
            drawModule(context, current, i, x + layout.x(i), y + layout.y(i), mouseX, mouseY, Math.clamp(flash, 0F, 1F));
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
        Text name = cartridge != null ? current.getName() : Text.translatable(CartridgeItem.MENU_KEY + "empty");
        int nameWidth = sw - (textX - sx) - TOGGLE - 8 - (slotLabel == null ? 0 : textRenderer.getWidth(slotLabel) + 4);
        context.drawText(textRenderer, textRenderer.trimToWidth(name, Math.max(10, nameWidth)).getString(), textX, sy + 7, textColor, !dark);
        int toggleX = sx + sw - TOGGLE - 4, toggleY = sy + 6;
        if (slotLabel != null) {
            context.drawText(textRenderer, slotLabel, toggleX - 4 - textRenderer.getWidth(slotLabel), sy + 7, dark ? 0xFF4A4A4A : 0xFFE8E8E8, false);
        }
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
        if (module.labelKey() != null) {
            String number = (i + 1 < 10 ? "0" : "") + (i + 1);
            int chipColor = flash > 0 ? mix(CHIP, 0xFFFFC52E, flash) : CHIP;
            int numberWidth = textRenderer.getWidth(number);
            context.fill(mx - 1, my, mx + numberWidth + 2, my + 9, chipColor);
            context.drawText(textRenderer, number, mx + 1, my + 1, flash > 0.5F ? 0xFF3B2600 : (enabled ? CHIP_TEXT : LABEL_OFF), false);
            Text label = Text.translatable(module.labelKey());
            if (module instanceof ChoiceModule choice && choice.swatches()) {
                int value = value(current, i);
                if (value >= 0 && value < choice.options().size()) {
                    ChoiceModule.Option option = choice.options().get(value);
                    label = label.copy().append(": ").append(Text.translatable(option.key()).withColor(lighten(0xFF000000 | option.color(), 0.3F)));
                }
            }
            context.drawText(textRenderer, label, mx + numberWidth + 5, my + 1, enabled ? LABEL : LABEL_OFF, false);
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
            boolean hovered = active && inside(mouseX, mouseY, bx, top, bw, bh);
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
                String text = textRenderer.trimToWidth(Text.translatable(option.key()), bw - 4).getString();
                int color = !choice.enabled(current) ? 0xFF7A7A7A : selected ? 0xFF3B2600 : PartyGui.TEXT_DARK;
                context.drawText(textRenderer, text, bx + (bw - textRenderer.getWidth(text)) / 2 + push, top + 4 + push, color, false);
            }
        }
    }

    private static int choiceW(ChoiceModule choice) {
        int n = choice.options().size();
        int w = (CartridgeLayout.COLUMN_W - (n - 1) * 3) / n;
        return choice.swatches() ? Math.min(34, w) : w;
    }

    private static int choiceX(ChoiceModule choice, int mx, int option) {
        return mx + option * (choiceW(choice) + 3);
    }

    // Number: [-] figure [+] lamps
    private static final int MINUS_X = 0, FIGURE_X = 18, FIGURE_W = 26, PLUS_X = 46, LAMPS_X = 66, STEP_W = 16;

    private int lampWidth(NumberModule number) {
        int count = number.max() - number.min() + 1;
        return Math.min(12, (CartridgeLayout.COLUMN_W - LAMPS_X) / count - 2);
    }

    private boolean showsLamps(NumberModule number) {
        return number.max() - number.min() + 1 <= 9;
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
        context.getMatrices().push();
        context.getMatrices().translate(mx + FIGURE_X + FIGURE_W / 2F - textRenderer.getWidth(figure), top + 2, 0);
        context.getMatrices().scale(2, 2, 1);
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
            if (lw >= 8) {
                String digit = Integer.toString(v);
                context.drawText(textRenderer, digit, lx + (lw - textRenderer.getWidth(digit)) / 2 + 1, top + 5, on ? 0xFFFFFFFF : 0xFF6A6A6A, false);
            }
        }
    }

    private void stepButton(DrawContext context, int bx, int top, String sign, boolean active, boolean hovered) {
        PartyGui.Theme theme = active ? (hovered ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON) : PartyGui.BUTTON_DISABLED;
        PartyGui.button(context, bx, top, STEP_W, NumberModule.ROW_H, theme, false);
        context.drawText(textRenderer, sign, bx + (STEP_W - textRenderer.getWidth(sign)) / 2, top + 5, active ? PartyGui.TEXT_DARK : 0xFF7A7A7A, false);
    }

    private static int colorX(int mx, int value) {
        return mx + (value % ColorModule.PER_ROW) * (ColorModule.SWATCH + ColorModule.GAP);
    }

    private static int colorY(int top, int value) {
        return top + (value / ColorModule.PER_ROW) * (ColorModule.SWATCH + ColorModule.GAP);
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
        int tx = mx + GhostSlotsModule.COLUMNS * GhostSlotsModule.SLOT + 6, ty = my + CartridgeModule.LABEL_H + 2;
        String k = CartridgeItem.MENU_KEY + "inventory.";
        context.fill(tx, ty + 1, tx + 5, ty + 6, 0xFF46AE2E);
        context.drawText(textRenderer, Text.translatable(k + "give"), tx + 8, ty, TONE_NORMAL, false);
        context.fill(tx, ty + 13, tx + 5, ty + 18, 0xFFD9283B);
        context.drawText(textRenderer, Text.translatable(k + "take"), tx + 8, ty + 12, TONE_NORMAL, false);
        List<OrderedText> help = textRenderer.wrapLines(Text.translatable(k + "wheel"), CartridgeLayout.COLUMN_W - (tx - mx));
        for (int l = 0; l < help.size() && l < 3; l++) context.drawText(textRenderer, help.get(l), tx, ty + 26 + l * 10, TONE_SOFT, false);
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
        int sx = x + 6, sw = layout.width() - 12;
        int toggleX = sx + sw - TOGGLE - 4, toggleY = y + 11;
        if (inside(mouseX, mouseY, toggleX - 1, toggleY - 1, TOGGLE + 2, TOGGLE + 2)) {
            context.drawTooltip(textRenderer, Text.translatable(CartridgeItem.MENU_KEY
                    + (CartridgeGuiConfig.currentAnimation() ? "animation.on" : "animation.off")), mouseX, mouseY);
            return true;
        }
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            CartridgeModule module = modules.get(i);
            int mx = x + layout.x(i), top = y + layout.y(i) + (module.labelKey() != null ? CartridgeModule.LABEL_H : 0);
            if (module instanceof ChoiceModule choice) {
                int bh = choice.swatches() ? ChoiceModule.SWATCH_H : ChoiceModule.BUTTON_H;
                for (int o = 0; o < choice.options().size(); o++) {
                    if (!inside(mouseX, mouseY, choiceX(choice, mx, o), top, choiceW(choice), bh)) continue;
                    ChoiceModule.Option option = choice.options().get(o);
                    List<Text> lines = new ArrayList<>();
                    lines.add(Text.translatable(option.key()));
                    if (option.tooltipKey() != null) lines.add(Text.translatable(option.tooltipKey()).formatted(net.minecraft.util.Formatting.GRAY));
                    if (!choice.enabled(current)) lines.add(Text.translatable(CartridgeItem.MENU_KEY + "unpowered").formatted(net.minecraft.util.Formatting.DARK_GRAY));
                    context.drawTooltip(textRenderer, wrap(lines), mouseX, mouseY);
                    return true;
                }
            } else if (module instanceof ColorModule color) {
                for (int v = 0; v <= ColorModule.DEFAULT; v++) {
                    if (!inside(mouseX, mouseY, colorX(mx, v), colorY(top, v), ColorModule.SWATCH, ColorModule.SWATCH)) continue;
                    context.drawTooltip(textRenderer, v == ColorModule.DEFAULT ? Text.translatable(CartridgeItem.MENU_KEY + "color.default")
                            : Text.translatable("color.minecraft." + DyeColor.byId(v).getName()), mouseX, mouseY);
                    return true;
                }
            }
        }
        if (!canEdit.getAsBoolean() && !modules.isEmpty() && inside(mouseX, mouseY, x, y, layout.width(), height())) {
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
        int sx = x + 6, sw = layout.width() - 12;
        int toggleX = sx + sw - TOGGLE - 4, toggleY = y + 11;
        if (inside(mouseX, mouseY, toggleX - 1, toggleY - 1, TOGGLE + 2, TOGGLE + 2)) {
            CartridgeGuiConfig.setCurrentAnimation(!CartridgeGuiConfig.currentAnimation());
            click();
            return true;
        }
        if (!canEdit.getAsBoolean()) return inside(mouseX, mouseY, x, y, layout.width(), height());
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            CartridgeModule module = modules.get(i);
            if (!module.editable() || !module.enabled(current)) continue;
            int mx = x + layout.x(i), top = y + layout.y(i) + (module.labelKey() != null ? CartridgeModule.LABEL_H : 0);
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

    /** The wheel over a number: one more / one less. */
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount == 0 || !canEdit.getAsBoolean()) return false;
        ItemStack current = stack.get();
        for (int i = 0; i < modules.size(); i++) {
            if (!(modules.get(i) instanceof NumberModule number) || !number.enabled(current)) continue;
            if (!inside(mouseX, mouseY, x + layout.x(i), y + layout.y(i), CartridgeLayout.COLUMN_W, number.height())) continue;
            int value = value(current, i);
            return change(i, Math.clamp(value + (amount > 0 ? 1 : -1), number.min(), number.max()), value);
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
        return inside(mouseX, mouseY, x, y, layout.width(), height());
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
