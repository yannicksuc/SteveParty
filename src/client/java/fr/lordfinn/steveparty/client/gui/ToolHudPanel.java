package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.client.gui.wheel.ToolWheel;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * The HUD of a tool held in hand (Tile Linker Brush, Stencil Hammer...), described by the tool and laid out here, in the
 * tools' look (see {@link ToolHud}), from the bottom up:
 * <ol>
 *     <li>a row of framed boxes (an icon each), right above the hotbar or the status rows, where the held item's name
 *     would be (it is hidden meanwhile);</li>
 *     <li>the state: small text plates;</li>
 *     <li>the see-through hint (the controls).</li>
 * </ol>
 * Every part is optional, and a clear gap separates them. The action bar goes up above the whole panel (see
 * {@link ToolHud#occupy}). A tool plugs in with {@link #register}:
 * <pre>{@code
 * ToolHudPanel.register(stack -> stack.isOf(ModItems.MY_TOOL), (panel, held, client) -> panel
 *         .item(someStack)
 *         .state(Text.literal("Mode"), ToolHud.Plate.GREEN)
 *         .hint(Text.translatable("hud.steveparty.my_tool.hint")));
 * }</pre>
 */
public final class ToolHudPanel {
    /** Size of a framed box (a 16 pixel icon inside). */
    public static final int BOX = 22;
    /** Where a box's 16 pixel icon starts inside it. */
    public static final int INSET = (BOX - 16) / 2;
    /** Height of a state plate. */
    public static final int STATE = 16;
    /** Between two elements of a row, and between two rows of the same kind. */
    private static final int GAP = 4;
    /** Clear room between two parts (boxes, state, hint): nothing touches. */
    private static final int PART_GAP = 4;
    /** A hint line's height (8 pixel text and its shadow). */
    private static final int LINE = 10;

    /** What a box shows: the 16 pixel icon at (x, y). */
    @FunctionalInterface
    public interface Icon {
        void draw(DrawContext context, int x, int y);
    }

    /** What a tool shows while held: fills the panel. */
    @FunctionalInterface
    public interface Describer {
        void describe(ToolHudPanel panel, ItemStack held, MinecraftClient client);
    }

    private record Box(ToolHud.Plate frame, Icon icon) {
    }

    private record State(Text text, ToolHud.Plate plate) {
    }

    private final List<Box> boxes = new ArrayList<>();
    private final List<State> states = new ArrayList<>();
    private Text hint;

    private ToolHudPanel() {
    }

    /**
     * Shows the panel {@code describer} fills while the main hand holds what {@code holds} accepts (not with the HUD
     * hidden, a screen or a tool wheel open).
     */
    public static void register(Predicate<ItemStack> holds, Describer describer) {
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.options.hudHidden || client.currentScreen != null || client.player == null || client.world == null
                    || ToolWheel.isOpen()) return;
            ItemStack held = client.player.getMainHandStack();
            if (!holds.test(held)) return;
            ToolHudPanel panel = new ToolHudPanel();
            describer.describe(panel, held, client);
            panel.draw(context);
        });
    }

    // ---------------------------------------------------------------- description

    /** A teal box drawing {@code icon}. */
    public ToolHudPanel box(Icon icon) {
        return box(ToolHud.Plate.TEAL, icon);
    }

    /** A box framed by {@code frame} (gold: the active one, red: something missing) drawing {@code icon}. */
    public ToolHudPanel box(ToolHud.Plate frame, Icon icon) {
        boxes.add(new Box(frame, icon));
        return this;
    }

    /** A teal box showing an item, with its count like a hotbar slot. */
    public ToolHudPanel item(ItemStack stack) {
        return box((context, x, y) -> {
            context.drawItem(stack, x, y);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x, y);
        });
    }

    /** A teal box showing an item, with {@code count} written like a hotbar stack's (none when null). */
    public ToolHudPanel item(ItemStack stack, String count) {
        return box((context, x, y) -> {
            context.drawItem(stack, x, y);
            if (count != null) context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x, y, count);
        });
    }

    /** A state plate: what the tool does now, a warning... */
    public ToolHudPanel state(Text text, ToolHud.Plate plate) {
        states.add(new State(text, plate));
        return this;
    }

    /** The see-through hint above everything, wrapped when wider than the screen. */
    public ToolHudPanel hint(Text hint) {
        this.hint = hint;
        return this;
    }

    // ---------------------------------------------------------------- drawing

    /** A state plate's width: its text's, but no wider than a row (a longer text scrolls in it). */
    private static int stateWidth(TextRenderer textRenderer, Text text, int available) {
        return Math.min(textRenderer.getWidth(text) + 10, available);
    }

    private void draw(DrawContext context) {
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int centerX = context.getScaledWindowWidth() / 2;
        int available = ToolHud.available(context);
        int bottom = ToolHud.bottom(context);

        // Boxes, the first row the lowest
        List<List<Box>> boxRows = wrap(boxes, box -> BOX, available);
        for (int i = 0; i < boxRows.size(); i++) {
            List<Box> row = boxRows.get(i);
            int y = bottom - BOX - i * (BOX + GAP);
            int x = centerX - rowWidth(row, box -> BOX) / 2;
            for (Box box : row) {
                ToolHud.plate(context, x, y, BOX, BOX, box.frame());
                int ix = x + INSET, iy = y + INSET;
                HudDepth.item(context, () -> box.icon().draw(context, ix, iy));
                x += BOX + GAP;
            }
        }
        if (!boxRows.isEmpty()) bottom -= boxRows.size() * (BOX + GAP) - GAP + PART_GAP;

        // State plates
        List<List<State>> stateRows = wrap(states, state -> stateWidth(textRenderer, state.text(), available), available);
        for (int i = 0; i < stateRows.size(); i++) {
            List<State> row = stateRows.get(i);
            int y = bottom - STATE - i * (STATE + GAP);
            int x = centerX - rowWidth(row, state -> stateWidth(textRenderer, state.text(), available)) / 2;
            for (State state : row) {
                int width = stateWidth(textRenderer, state.text(), available);
                ToolHud.plate(context, x, y, width, STATE, state.plate());
                int tx = x + 5;
                HudDepth.onTop(context, () -> UiText.line(context, textRenderer, state.text(), tx, y + (STATE - 8) / 2, width - 10, ToolHud.TEXT, false));
                x += width + GAP;
            }
        }
        if (!stateRows.isEmpty()) bottom -= stateRows.size() * (STATE + GAP) - GAP + PART_GAP;

        // Hint
        if (hint != null) {
            var lines = textRenderer.wrapLines(hint, available);
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                int y = bottom - LINE * (lines.size() - i) + 1;
                HudDepth.onTop(context, () -> ToolHud.centeredLine(context, textRenderer, line, centerX, y, available, ToolHud.HINT));
            }
            bottom -= LINE * lines.size();
        }
        ToolHud.occupy(bottom);
    }

    private static <T> List<List<T>> wrap(List<T> elements, ToIntFunction<T> width, int available) {
        List<List<T>> rows = new ArrayList<>();
        List<T> row = new ArrayList<>();
        int used = 0;
        for (T element : elements) {
            int w = width.applyAsInt(element);
            if (!row.isEmpty() && used + GAP + w > available) {
                rows.add(row);
                row = new ArrayList<>();
                used = 0;
            }
            used += (row.isEmpty() ? 0 : GAP) + w;
            row.add(element);
        }
        if (!row.isEmpty()) rows.add(row);
        return rows;
    }

    private static <T> int rowWidth(List<T> row, ToIntFunction<T> width) {
        int total = 0;
        for (T element : row) total += width.applyAsInt(element);
        return total + Math.max(0, row.size() - 1) * GAP;
    }
}
