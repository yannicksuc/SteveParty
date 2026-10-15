package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlock;
import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.payloads.custom.AntennaSettingsPayload;
import fr.lordfinn.steveparty.utils.Argb;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import fr.lordfinn.steveparty.client.gui.NotLinkedMark;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The Party Antenna's screen (right-click with the Wrench): every moment at once, one row per family (its colour on
 * the left and in its buttons, the moment's item in each), one click chooses. Under them: the last moment heard, then
 * the mode (pulse / wait), the value filter and a test pulse. No help text: one tooltip per control. Linked to no party
 * (it hears nothing then): the Tile Linker Brush crossed out in red at the end of the last-heard row, where its
 * hourglass shows when it holds a party ({@link NotLinkedMark}), what it means on hover.
 * <p>
 * It reads the antenna the client already has (moment in the block state, the rest in the synced block entity) and
 * sends every change at once ({@link AntennaSettingsPayload}). Buttons are pressed in while held: the face darkens and
 * its bevels swap, the icon stays on its pixels.
 */
public class AntennaScreen extends Screen {
    private static final int W = 176, H = 186;
    private static final int CELL = 22, BUTTON = 20, GRID_X = 16, GRID_Y = 18;
    private static final int CONTROLS_Y = 158, HEARD_Y = 136, SEPARATOR_Y = 130;
    private static final int SLOT = 0xFF8D8D8D;

    private final BlockPos pos;
    private int left, top;
    /** The control held down by the mouse (pressed in until released), null for none. */
    private @Nullable String held;

    /** A control: its id, place and size (GUI pixels, from the panel's corner). */
    private record Control(String id, int x, int y, int w, int h) {
        boolean contains(double mx, double my, int left, int top) {
            return mx >= left + x && mx < left + x + w && my >= top + y && my < top + y + h;
        }
    }

    private static final List<Control> CONTROLS = List.of(
            new Control("pulse", 8, CONTROLS_Y, BUTTON, BUTTON),
            new Control("wait", 30, CONTROLS_Y, BUTTON, BUTTON),
            new Control("filter", 56, CONTROLS_Y, BUTTON, BUTTON),
            new Control("minus", 78, CONTROLS_Y, BUTTON, BUTTON),
            new Control("value", 100, CONTROLS_Y, BUTTON, BUTTON),
            new Control("plus", 122, CONTROLS_Y, BUTTON, BUTTON),
            new Control("test", 148, CONTROLS_Y, BUTTON, BUTTON));

    public AntennaScreen(BlockPos pos) {
        super(ModBlocks.PARTY_BELL.getName());
        this.pos = pos;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private @Nullable PartyBellBlockEntity antenna() {
        return client != null && client.world != null && client.world.getBlockEntity(pos) instanceof PartyBellBlockEntity antenna ? antenna : null;
    }

    @Override
    public void tick() {
        // Broken, or left behind: nothing to set any more
        if (antenna() == null || client == null || client.player == null || client.player.squaredDistanceTo(pos.toCenterPos()) > 64) close();
    }

    // ---------------------------------------------------------------- layout

    private static List<PartyMoment> row(PartyMoment.Family family) {
        List<PartyMoment> row = new ArrayList<>();
        for (PartyMoment moment : PartyMoment.values()) if (moment.family() == family) row.add(moment);
        return row;
    }

    private int momentX(int column) {
        return left + GRID_X + CELL * column;
    }

    private int momentY(int row) {
        return top + GRID_Y + CELL * row;
    }

    private @Nullable PartyMoment momentAt(double mx, double my) {
        PartyMoment.Family[] families = PartyMoment.Family.values();
        for (int r = 0; r < families.length; r++) {
            List<PartyMoment> moments = row(families[r]);
            for (int c = 0; c < moments.size(); c++) {
                int x = momentX(c), y = momentY(r);
                if (mx >= x && mx < x + BUTTON && my >= y && my < y + BUTTON) return moments.get(c);
            }
        }
        return null;
    }

    private @Nullable PartyMoment.Family familyAt(double mx, double my) {
        PartyMoment.Family[] families = PartyMoment.Family.values();
        for (int r = 0; r < families.length; r++) {
            int y = momentY(r);
            if (mx >= left + 8 && mx < left + 12 && my >= y && my < y + BUTTON) return families[r];
        }
        return null;
    }

    private @Nullable Control controlAt(double mx, double my) {
        for (Control control : CONTROLS) if (control.contains(mx, my, left, top)) return control;
        return null;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        PartyBellBlockEntity antenna = antenna();
        if (antenna == null) return;
        BlockState state = antenna.getCachedState();
        PartyMoment current = state.get(PartyBellBlock.MOMENT);
        boolean waiting = state.get(PartyBellBlock.WAITING) && current.canWait();
        PartyBellBlockEntity.Filter filter = antenna.getFilter();

        PartyGui.panel(context, left, top, W, H, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, left + W / 2, top - 11, 0, title, PartyGui.STEEL);

        // The palette: one row per family
        PartyMoment hovered = momentAt(mouseX, mouseY);
        PartyMoment.Family[] families = PartyMoment.Family.values();
        for (int r = 0; r < families.length; r++) {
            int y = momentY(r), colour = AntennaIcons.colour(families[r]);
            context.fill(left + 8, y, left + 12, y + BUTTON, colour);
            context.fill(left + 8, y, left + 12, y + 1, Argb.lighten(colour, 0.4f));
            context.fill(left + 8, y + BUTTON - 1, left + 12, y + BUTTON, Argb.darken(colour, 0.35f));
            List<PartyMoment> moments = row(families[r]);
            for (int c = 0; c < moments.size(); c++) {
                PartyMoment moment = moments.get(c);
                int x = momentX(c);
                int body = mix(SLOT, colour, 0.28f);
                boolean pressed = moment.name().equals(held);
                face(context, x, y, BUTTON, BUTTON, body, pressed, moment == hovered, false);
                AntennaIcons.drawMoment(context, moment, x + 2, y + 2);
                if (moment == current) selected(context, x, y, BUTTON, BUTTON);
            }
        }

        // The separator, then the last moment heard
        context.fill(left + 8, top + SEPARATOR_Y, left + W - 8, top + SEPARATOR_Y + 1, 0xFF8D8D8D);
        context.fill(left + 8, top + SEPARATOR_Y + 1, left + W - 8, top + SEPARATOR_Y + 2, 0xFFFFFFFF);
        drawHeard(context, antenna, current);

        // The mode, the filter, the test
        Control hoveredControl = controlAt(mouseX, mouseY);
        boolean canWait = current.canWait();
        boolean testable = !antenna.inPartyOnClient();
        button(context, control("pulse"), AntennaIcons.PULSE, !waiting, true, hoveredControl);
        button(context, control("wait"), AntennaIcons.WAIT, waiting, canWait, hoveredControl);
        button(context, control("filter"), switch (filter) {
            case ANY -> AntennaIcons.FILTER_ANY;
            case EQUAL -> AntennaIcons.FILTER_EQUAL;
            case AT_LEAST -> AntennaIcons.FILTER_AT_LEAST;
            case AT_MOST -> AntennaIcons.FILTER_AT_MOST;
        }, false, true, hoveredControl);
        boolean valued = filter != PartyBellBlockEntity.Filter.ANY;
        button(context, control("minus"), AntennaIcons.MINUS, false, valued && antenna.getFilterValue() > 0, hoveredControl);
        button(context, control("plus"), AntennaIcons.PLUS, false, valued && antenna.getFilterValue() < 15, hoveredControl);
        button(context, control("test"), AntennaIcons.TEST, false, testable, hoveredControl);
        Control value = control("value");
        face(context, left + value.x(), top + value.y(), value.w(), value.h(), valued ? 0xFF3A3A3A : 0xFF6A6A6A, false, false, !valued);
        if (valued) {
            UiText.centered(context, textRenderer, Text.literal(Integer.toString(antenna.getFilterValue())), left + value.x() + 1,
                    top + value.y() + 6, value.w(), 0xFFFFFFFF, true);
        }

        drawTooltip(context, antenna, current, hovered, hoveredControl, mouseX, mouseY, canWait, testable);
    }

    /** The corner of the last-heard row's right end: its hourglass while it holds a party, its crossed brush unlinked. */
    private int heardEndX() {
        return left + W - 8 - 16;
    }

    private void drawHeard(DrawContext context, PartyBellBlockEntity antenna, PartyMoment current) {
        if (!antenna.isPartyLinked()) NotLinkedMark.screen(context, heardEndX(), top + HEARD_Y);
        if (antenna.isHolding()) {
            int x = left + W - 8 - 16;
            AntennaIcons.cell(context, AntennaIcons.WAIT, x, top + HEARD_Y);
        }
        int x = left + 8, y = top + HEARD_Y;
        if (antenna.getHeardAt() < 0 || client == null || client.world == null) {
            PartyGui.slot(context, x, y, 0xFF7A7A7A, 0xFF3A4048);
            return;
        }
        AntennaIcons.drawMoment(context, current, x, y);
        Text value = antenna.heardPassed() ? Text.literal(Integer.toString(antenna.getHeardValue())).formatted(Formatting.BOLD)
                : Text.literal(Integer.toString(antenna.getHeardValue()));
        int valueX = x + 22, textY = y + 4, valueW = textRenderer.getWidth(value);
        UiText.line(context, textRenderer, value, valueX, textY, valueW, antenna.heardPassed() ? 0xFFFFFFFF : 0xFF8A8A8A, antenna.heardPassed());
        if (!antenna.heardPassed()) context.fill(valueX - 1, textY + 3, valueX + valueW + 1, textY + 4, 0xFF5A5A5A);
        long seconds = Math.max(0, (client.world.getTime() - antenna.getHeardAt()) / 20);
        int agoX = valueX + valueW + 8;
        UiText.line(context, textRenderer, Text.translatable("gui.steveparty.antenna.heard", seconds), agoX, textY,
                left + W - 8 - 18 - agoX, PartyGui.TEXT_SOFT, false);
    }

    private void drawTooltip(DrawContext context, PartyBellBlockEntity antenna, PartyMoment current, @Nullable PartyMoment hovered,
                             @Nullable Control control, int mouseX, int mouseY, boolean canWait, boolean testable) {
        List<Text> lines = new ArrayList<>();
        if (hovered != null) {
            lines.add(hovered.getText().copy().withColor(AntennaIcons.textColour(hovered.family())));
            lines.add(Text.translatable("gui.steveparty.antenna.comparator", Text.translatable(hovered.valueDescriptionKey()))
                    .formatted(Formatting.GRAY));
        } else if (familyAt(mouseX, mouseY) instanceof PartyMoment.Family family) {
            lines.add(family.getText().copy().withColor(AntennaIcons.textColour(family)));
        } else if (control != null) {
            int filterValue = antenna.getFilterValue();
            switch (control.id()) {
                case "pulse" -> lines.add(Text.translatable("gui.steveparty.antenna.pulse"));
                case "wait" -> lines.add(Text.translatable(canWait ? "gui.steveparty.antenna.wait" : "gui.steveparty.antenna.wait.never"));
                case "filter", "value" -> lines.add(switch (antenna.getFilter()) {
                    case ANY -> Text.translatable("gui.steveparty.antenna.filter.any");
                    case EQUAL -> Text.translatable("gui.steveparty.antenna.filter.equal", filterValue);
                    case AT_LEAST -> Text.translatable("gui.steveparty.antenna.filter.at_least", filterValue);
                    case AT_MOST -> Text.translatable("gui.steveparty.antenna.filter.at_most", filterValue);
                });
                case "minus" -> lines.add(Text.translatable("gui.steveparty.antenna.minus"));
                case "plus" -> lines.add(Text.translatable("gui.steveparty.antenna.plus"));
                case "test" -> lines.add(Text.translatable(testable ? "gui.steveparty.antenna.test" : "gui.steveparty.antenna.test.party"));
                default -> {
                }
            }
        } else if (!antenna.isPartyLinked() && NotLinkedMark.hovered(mouseX, mouseY, heardEndX(), top + HEARD_Y)) {
            lines.add(NotLinkedMark.tooltip(antenna));
        } else if (antenna.isHolding() && mouseX >= left + W - 24 && mouseX < left + W - 8 && mouseY >= top + HEARD_Y && mouseY < top + HEARD_Y + 16) {
            lines.add(Text.translatable("gui.steveparty.antenna.holding"));
        } else if (antenna.getHeardAt() < 0 && mouseX >= left + 8 && mouseX < left + 24
                && mouseY >= top + HEARD_Y && mouseY < top + HEARD_Y + 16) {
            lines.add(Text.translatable("gui.steveparty.antenna.heard.none"));
        } else if (antenna.getHeardAt() >= 0 && !antenna.heardPassed() && mouseX >= left + 8 && mouseX < left + 60
                && mouseY >= top + HEARD_Y && mouseY < top + HEARD_Y + 16) {
            lines.add(Text.translatable("gui.steveparty.antenna.heard.filtered"));
        }
        if (lines.isEmpty()) return;
        // Two lines at most: a long one is wrapped
        List<net.minecraft.text.OrderedText> wrapped = new ArrayList<>();
        for (Text line : lines) wrapped.addAll(textRenderer.wrapLines(line, 200));
        context.drawOrderedTooltip(textRenderer, wrapped, mouseX, mouseY);
    }

    private Control control(String id) {
        for (Control control : CONTROLS) if (control.id().equals(id)) return control;
        throw new IllegalArgumentException(id);
    }

    /** A control button: its glyph, pressed in while held or when it is the chosen toggle; dimmed when it can't be used. */
    private void button(DrawContext context, Control control, int glyph, boolean chosen, boolean enabled, @Nullable Control hovered) {
        int x = left + control.x(), y = top + control.y();
        boolean pressed = enabled && control.id().equals(held);
        face(context, x, y, control.w(), control.h(), enabled ? 0xFFD6D6D6 : 0xFFA3A3A3, pressed || chosen,
                enabled && hovered == control, !enabled);
        AntennaIcons.cell(context, glyph, x + (control.w() - 16) / 2, y + 2);
        if (!enabled) context.fill(x + 2, y + 2, x + control.w() - 2, y + control.h() - 2, 0x80A3A3A3);
        if (chosen) selected(context, x, y, control.w(), control.h());
    }

    /**
     * A button's face in the mod's style (1 px rounded outline, bevels). Pressed in: darker and its bevels swapped;
     * what is drawn on it stays where it is. Hovered: brighter.
     */
    private static void face(DrawContext context, int x, int y, int w, int h, int body, boolean pressed, boolean hovered, boolean disabled) {
        int colour = pressed ? Argb.darken(body, 0.14f) : hovered ? Argb.lighten(body, 0.35f) : body;
        PartyGui.Theme theme = new PartyGui.Theme(0xFF000000, Argb.lighten(colour, disabled ? 0.2f : 0.45f), colour,
                Argb.darken(colour, disabled ? 0.2f : 0.38f));
        PartyGui.button(context, x, y, w, h, theme, pressed);
    }

    /**
     * The chosen one: a 1 px white ring just inside its black outline, in the button's own pixels, stopping before the
     * cut corners as the outline does.
     */
    private static void selected(DrawContext context, int x, int y, int w, int h) {
        context.fill(x + 2, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        context.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0xFFFFFFFF);
        context.fill(x + 1, y + 2, x + 2, y + h - 2, 0xFFFFFFFF);
        context.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, 0xFFFFFFFF);
    }

    private static int mix(int a, int b, float t) {
        return Argb.lerp(a, b, t) | 0xFF000000;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        PartyBellBlockEntity antenna = antenna();
        if (button != 0 || antenna == null) return super.mouseClicked(mouseX, mouseY, button);
        BlockState state = antenna.getCachedState();
        PartyMoment current = state.get(PartyBellBlock.MOMENT);
        boolean waiting = state.get(PartyBellBlock.WAITING);
        PartyBellBlockEntity.Filter filter = antenna.getFilter();
        int value = antenna.getFilterValue();
        PartyMoment moment = momentAt(mouseX, mouseY);
        if (moment != null) {
            held = moment.name();
            click();
            send(moment, waiting, filter, value, false);
            return true;
        }
        Control control = controlAt(mouseX, mouseY);
        if (control == null) return super.mouseClicked(mouseX, mouseY, button);
        int step = hasShiftDown() ? 5 : 1;
        boolean valued = filter != PartyBellBlockEntity.Filter.ANY;
        boolean done = switch (control.id()) {
            case "pulse" -> {
                send(current, false, filter, value, false);
                yield true;
            }
            case "wait" -> {
                if (!current.canWait()) yield false;
                send(current, true, filter, value, false);
                yield true;
            }
            case "filter" -> {
                send(current, waiting, filter.next(), value, false);
                yield true;
            }
            case "minus" -> {
                if (!valued || value <= 0) yield false;
                send(current, waiting, filter, Math.max(0, value - step), false);
                yield true;
            }
            case "plus" -> {
                if (!valued || value >= 15) yield false;
                send(current, waiting, filter, Math.min(15, value + step), false);
                yield true;
            }
            case "test" -> {
                if (antenna.inPartyOnClient()) yield false;
                send(current, waiting, filter, value, true);
                yield true;
            }
            default -> false;
        };
        if (done) {
            held = control.id();
            click();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        held = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        PartyBellBlockEntity antenna = antenna();
        Control control = controlAt(mouseX, mouseY);
        if (antenna == null || control == null || !control.id().equals("value") || antenna.getFilter() == PartyBellBlockEntity.Filter.ANY)
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        int step = (hasShiftDown() ? 5 : 1) * (int) Math.signum(verticalAmount);
        BlockState state = antenna.getCachedState();
        send(state.get(PartyBellBlock.MOMENT), state.get(PartyBellBlock.WAITING), antenna.getFilter(),
                Math.clamp(antenna.getFilterValue() + step, 0, 15), false);
        return true;
    }

    /** Arrows: the chosen moment moves in the palette (left / right in its row, up / down to the next row). */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        PartyBellBlockEntity antenna = antenna();
        if (antenna != null && keyCode >= GLFW.GLFW_KEY_RIGHT && keyCode <= GLFW.GLFW_KEY_UP) {
            BlockState state = antenna.getCachedState();
            PartyMoment current = state.get(PartyBellBlock.MOMENT);
            PartyMoment.Family[] families = PartyMoment.Family.values();
            int r = current.family().ordinal(), c = row(current.family()).indexOf(current);
            switch (keyCode) {
                case GLFW.GLFW_KEY_RIGHT -> c++;
                case GLFW.GLFW_KEY_LEFT -> c--;
                case GLFW.GLFW_KEY_DOWN -> r = Math.min(families.length - 1, r + 1);
                default -> r = Math.max(0, r - 1);
            }
            List<PartyMoment> moments = row(families[r]);
            PartyMoment next = moments.get(Math.clamp(c, 0, moments.size() - 1));
            if (next != current) {
                click();
                send(next, state.get(PartyBellBlock.WAITING), antenna.getFilter(), antenna.getFilterValue(), false);
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void send(PartyMoment moment, boolean waiting, PartyBellBlockEntity.Filter filter, int value, boolean test) {
        ClientPlayNetworking.send(new AntennaSettingsPayload(pos, moment.ordinal(), waiting, filter.ordinal(), value, test));
    }

    private void click() {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0f));
    }
}
