package fr.lordfinn.steveparty.client.gui.cartridge;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrencyText;
import fr.lordfinn.steveparty.client.gui.HitArea;
import net.minecraft.client.render.DiffuseLighting;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.components.MistigriOutcomes;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Kind;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Outcome;
import fr.lordfinn.steveparty.components.MistigriOutcomes.Target;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.OutcomeListModule;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * The Mistigri's sentences in his cartridge's menu ({@link OutcomeListModule}): a box of rows that scrolls, then the
 * line under it (add a sentence; how many go on the roulette).
 * <ul>
 *     <li>A row reads at a glance: its kind's icon (a badge: minus they lose, plus they get, arrows the common pot),
 *     who it falls on (a pictogram), its amount, its chance at a landing, and × to delete it.</li>
 *     <li>A click on a row opens it (one at a time): under it, the kinds and the targets as palettes of icons (the one
 *     chosen pushed in gold), its amount with « # » / « % » (a number, or a share of what they hold) and its weight.
 *     The mouse wheel over a figure changes it.</li>
 *     <li>× turns the row into « Delete? Yes / No » (on two lines when it doesn't fit on one).</li>
 *     <li>The full sentence, each icon's meaning: in tooltips.</li>
 * </ul>
 * The rows shown are the menu's own copy of the list: an action changes it at once and is sent to the server
 * ({@link OutcomeListModule#apply} on both sides); the cartridge's list replaces it as soon as the server's answer
 * matches it, or a moment after the last action (a change refused).
 */
final class OutcomeListWidget {
    private static final int ROW_H = OutcomeListModule.ROW_H;
    /**
     * The open row's grid, in the rows' column grid ({@link #gridW}): every line of it the same width, the kinds as one
     * segmented bar (buttons {@link #KIND_B} high sharing their 1-pixel edges), the targets as touching buttons
     * ({@link #TARGET_B} high), both spread evenly over the width, the figures (amount and #/% on the left edge, the
     * weight on the right edge). A column narrower than {@link #GRID_W} wraps them at their least sizes.
     * {@link #ED_GAP} between the lines, above the first and under the last.
     */
    private static final int GRID_W = 120, KIND_PITCH = 17, ED_GAP = 4, SIGN_GAP = 2, GROUP_GAP = 4;
    /**
     * A row: {@link #PAD} on each side (the gold bar of an open row in it), the kind's icon, {@link #ICON_GAP}, who,
     * {@link #TEXT_GAP}, the amount; on the right the chance, {@link #TEXT_GAP}, the delete button. Everything centred
     * on the row's height: the 21 pixels centre the 7-pixel capitals, the 11-pixel pictogram and button exactly; the
     * 16-pixel item sits 2 above, 3 under (an item's drawing keeps its own margin).
     */
    private static final int PAD = 4, ICON_GAP = 4, TEXT_GAP = 6, LIST_PAD = OutcomeListModule.LIST_PAD;
    private static final int KIND_B = 18, KIND_GAP = KIND_PITCH - KIND_B, TARGET_B = 17, TARGET_MIN_W = 15, GAP = 0, NUM_H = 13, STEP_W = 11, VALUE_W = 25, MODE_W = 11, WEIGHT_W = 15, DRAWS_W = 27;
    private static final int DELETE_W = 11, SCROLLBAR = 4, SCROLL_STEP = 7;
    private static final long SETTLE_MS = 1500;
    // Colours: the shell's
    private static final int OUTLINE = 0xFF0E0E0E, BOX = 0xFF2C2C2C, ROW_A = 0xFF363636, ROW_B = 0xFF323232,
            ROW_HOVER = 0xFF414141, ROW_OPEN = 0xFF3D3A33, EDITOR = 0xFF2F2D29, GOLD = 0xFFFFC52E, CONFIRM = 0xFF4A1E22;
    private static final int TEXT = 0xFFE0E0E0, SOFT = 0xFF9C9C9C, OFF = 0xFF6A6A6A;
    private static final PartyGui.Theme DARK = new PartyGui.Theme(OUTLINE, 0xFF5E5E5E, 0xFF3E3E3E, 0xFF262626);
    private static final String K = CartridgeItem.MENU_KEY + "mistigri.";

    /** What a hit box is. */
    enum Part { ROW, DELETE, YES, NO, KIND, TARGET, AMOUNT_DOWN, AMOUNT, AMOUNT_UP, NUMBER, PERCENT, WEIGHT_DOWN, WEIGHT, WEIGHT_UP, ADD, DRAWS_DOWN, DRAWS, DRAWS_UP }

    /** A hit box of the current frame (screen coordinates): what, which row, which value, whether it may be clicked. */
    record Hit(int x, int y, int w, int h, Part part, int row, int value, boolean active) {
        boolean contains(double mouseX, double mouseY) {
            return HitArea.contains(mouseX, mouseY, x, y, w, h);
        }
    }

    /** A row's block on screen (its header and, open, its editor). */
    private record Block(int row, int y, int h) {
    }

    private final TextRenderer font;
    private final IntConsumer sender;
    private @Nullable MistigriOutcomes shown, seen, chancesOf;
    private double[] chances = new double[0];
    private long editedAt;
    private int open = -1, confirm = -1, reveal = -1;
    private int scroll, scrollMax;
    // The current frame
    private int bx, by, bw;
    private boolean editable;
    private final List<Hit> hits = new ArrayList<>();
    private final List<Block> blocks = new ArrayList<>();

    /** @param sender sends an action ({@link OutcomeListModule#action}) to the server, with the menu's spark and click */
    OutcomeListWidget(TextRenderer font, IntConsumer sender) {
        this.font = font;
        this.sender = sender;
    }

    // ------------------------------------------------------------------ state

    /** Follows the cartridge's list (see the class). */
    private MistigriOutcomes sync(ItemStack stack) {
        MistigriOutcomes now = MistigriOutcomes.of(stack);
        long time = Util.getMeasuringTimeMs();
        boolean settled = time - editedAt > SETTLE_MS;
        if (shown == null || (!now.equals(seen) && (now.equals(shown) || settled)) || (settled && !now.equals(shown))) shown = now;
        seen = now;
        if (open >= shown.size()) open = -1;
        if (confirm >= shown.size()) confirm = -1;
        if (!shown.equals(chancesOf)) {
            chancesOf = shown;
            chances = shown.chances();
        }
        return shown;
    }

    private void act(int action) {
        if (shown == null) return;
        MistigriOutcomes next = OutcomeListModule.apply(shown, action);
        if (next == null) return;
        int op = OutcomeListModule.op(action), row = OutcomeListModule.row(action);
        if (op == OutcomeListModule.ADD) {
            open = next.size() - 1;
            reveal = open;
            confirm = -1;
        } else if (op == OutcomeListModule.DELETE) {
            confirm = -1;
            if (open == row) open = -1;
            else if (open > row) open--;
        }
        shown = next;
        editedAt = Util.getMeasuringTimeMs();
        sender.accept(action);
    }

    // ------------------------------------------------------------------ layout

    private int innerW(boolean bar) {
        return bw - 2 - (bar ? SCROLLBAR : 0);
    }

    /**
     * The open row's grid width in a row {@code iw} wide: from the rows' content (where the kind's icon starts, 3 pixels
     * in) to the delete button's right edge (2 pixels from the row's end). Its lines are stretched to it.
     */
    private static int gridW(int iw) {
        return iw - 2 * PAD;
    }

    /** A row's editor height (its row {@code iw} wide): the palettes (wrapped when narrow), the figures, the gaps. */
    private int editorH(Outcome outcome, int iw) {
        int w = gridW(iw);
        int kinds = rows(Kind.values().length, KIND_B, KIND_GAP, w), targets = rows(Target.values().length, TARGET_MIN_W, GAP, w);
        return ED_GAP + kinds * KIND_B + (kinds - 1) * (ED_GAP) + ED_GAP + targets * TARGET_B + (targets - 1) * ED_GAP + ED_GAP
                + (figuresWrap(outcome, w) ? 2 * NUM_H + ED_GAP : NUM_H) + ED_GAP;
    }

    private static int perRow(int size, int gap, int room) {
        return Math.max(1, (room + gap) / (size + gap));
    }

    private static int rows(int count, int size, int gap, int room) {
        int perRow = perRow(size, gap, room);
        return (count + perRow - 1) / perRow;
    }

    private static int amountGroupW(Outcome outcome) {
        if (!outcome.kind().hasAmount()) return 0;
        return 2 * STEP_W + VALUE_W + (outcome.kind().percent ? SIGN_GAP + 2 * MODE_W : 0);
    }

    private static final int WEIGHT_GROUP_W = 9 + 2 * STEP_W + WEIGHT_W;

    private static boolean figuresWrap(Outcome outcome, int room) {
        int amount = amountGroupW(outcome);
        return amount > 0 && amount + GROUP_GAP + WEIGHT_GROUP_W > room;
    }

    /** The « Delete? » row: whether its buttons go under the question. */
    private boolean confirmWraps(int w) {
        return PAD + font.getWidth(Text.translatable(K + "delete.confirm")) + TEXT_GAP + buttonW(K + "yes") + 2 + buttonW(K + "no") + PAD > w;
    }

    /** A text button: its ink and 5 pixels each side (the same room left and right). */
    private int buttonW(String key) {
        return font.getWidth(Text.translatable(key)) - 1 + 10;
    }

    /** The add button with its word: the sign, a space, the word, 5 pixels each side. */
    private int addWords() {
        return 5 + 3 + font.getWidth(Text.translatable(K + "add")) - 1 + 10;
    }

    private int blockH(MistigriOutcomes outcomes, int row, int w) {
        if (row == confirm) return confirmWraps(w) ? ROW_H + NUM_H : ROW_H;
        return ROW_H + (row == open ? editorH(outcomes.get(row), w) : 0);
    }

    private int contentH(MistigriOutcomes outcomes, int w) {
        int h = 0;
        for (int row = 0; row < outcomes.size(); row++) h += blockH(outcomes, row, w);
        return h;
    }

    /** Lays the rows, the palettes and the line under the box out for this frame: {@link #hits}, {@link #blocks}. */
    private void layout(MistigriOutcomes outcomes, int mx, int top, int w) {
        bx = mx;
        by = top;
        bw = w;
        hits.clear();
        blocks.clear();
        int view = OutcomeListModule.LIST_H - 2 - 2 * LIST_PAD;
        boolean bar = contentH(outcomes, innerW(false)) > view;
        int iw = innerW(bar), content = contentH(outcomes, iw);
        scrollMax = Math.max(0, content - view);
        if (reveal >= 0 && reveal < outcomes.size()) {
            int at = 0;
            for (int row = 0; row < reveal; row++) at += blockH(outcomes, row, iw);
            int h = blockH(outcomes, reveal, iw);
            if (at < scroll) scroll = at;
            else if (at + Math.min(h, view) > scroll + view) scroll = at + Math.min(h, view) - view;
            reveal = -1;
        }
        scroll = Math.clamp(scroll, 0, scrollMax);

        int x0 = bx + 1, y = by + 1 + LIST_PAD - scroll;
        for (int row = 0; row < outcomes.size(); row++) {
            Outcome outcome = outcomes.get(row);
            int h = blockH(outcomes, row, iw);
            blocks.add(new Block(row, y, h));
            if (row == confirm) {
                int yes = buttonW(K + "yes"), no = buttonW(K + "no");
                int line = confirmWraps(iw) ? y + ROW_H - 2 : y + (ROW_H - NUM_H) / 2;
                hits.add(new Hit(x0 + iw - PAD - no, line, no, NUM_H, Part.NO, row, 0, editable));
                hits.add(new Hit(x0 + iw - PAD - no - 2 - yes, line, yes, NUM_H, Part.YES, row, 0, editable));
            } else {
                hits.add(new Hit(x0 + iw - PAD - DELETE_W, y + (ROW_H - DELETE_W) / 2, DELETE_W, DELETE_W, Part.DELETE, row, 0, editable));
                hits.add(new Hit(x0, y, iw, ROW_H, Part.ROW, row, 0, editable));
            }
            if (row == open && row != confirm) {
                // Centred between the gold bar (2 pixels) and the row's right end (the delete button's right edge)
                // In the rows' column grid: under the kind's icon, to the delete button's right edge
                layoutEditor(outcome, row, x0 + PAD, y + ROW_H + ED_GAP, gridW(iw));
            }
            y += h;
        }

        // Under the box: add (left), the draws (right)
        int fy = by + OutcomeListModule.LIST_H + OutcomeListModule.FOOTER_GAP;
        int m = outcomes.eligible().size(), k = outcomes.drawCount();
        int dx = bx + bw - STEP_W;
        hits.add(new Hit(dx, fy + 1, STEP_W, NUM_H, Part.DRAWS_UP, -1, 0, editable && k < m));
        dx -= DRAWS_W;
        hits.add(new Hit(dx, fy + 1, DRAWS_W, NUM_H, Part.DRAWS, -1, 0, editable && m > 0));
        dx -= STEP_W;
        hits.add(new Hit(dx, fy + 1, STEP_W, NUM_H, Part.DRAWS_DOWN, -1, 0, editable && k > 1));
        int addW = Math.min(addWords(), dx - GROUP_GAP - MistigriIcons.CARDS_W - 6 - bx);
        hits.add(new Hit(bx, fy, Math.max(16, addW), OutcomeListModule.FOOTER_H, Part.ADD, -1, 0,
                editable && outcomes.size() < MistigriOutcomes.MAX));
    }

    private void layoutEditor(Outcome outcome, int row, int x, int y, int w) {
        int perRow = perRow(KIND_B, KIND_GAP, w);
        int kinds = Kind.values().length, targets = Target.values().length;
        for (Kind kind : Kind.values()) {
            int i = kind.ordinal();
            if (perRow >= kinds) {
                // One segmented bar across the whole width: the edges shared, the widths spread evenly (a pixel apart at most)
                int from = x + Math.round(i * (w - 1) / (float) kinds), to = x + Math.round((i + 1) * (w - 1) / (float) kinds);
                hits.add(new Hit(from, y, to - from + 1, KIND_B, Part.KIND, row, i, editable));
            } else {
                hits.add(new Hit(x + i % perRow * KIND_PITCH, y + i / perRow * (KIND_B + ED_GAP), KIND_B, KIND_B, Part.KIND, row, i, editable));
            }
        }
        y += rows(Kind.values().length, KIND_B, KIND_GAP, w) * (KIND_B + ED_GAP);
        perRow = perRow(TARGET_MIN_W, GAP, w);
        for (Target target : Target.values()) {
            int i = target.ordinal();
            boolean allowed = outcome.kind().anyTarget || target == Target.LANDED;
            if (perRow >= targets) {
                // Touching buttons across the whole width, the widths spread evenly
                int from = x + Math.round(i * w / (float) targets), to = x + Math.round((i + 1) * w / (float) targets);
                hits.add(new Hit(from, y, to - from, TARGET_B, Part.TARGET, row, i, editable && allowed));
            } else {
                hits.add(new Hit(x + i % perRow * TARGET_MIN_W, y + i / perRow * (TARGET_B + ED_GAP), TARGET_MIN_W, TARGET_B, Part.TARGET, row, i, editable && allowed));
            }
        }
        y += rows(Target.values().length, TARGET_MIN_W, GAP, w) * (TARGET_B + ED_GAP);
        Kind kind = outcome.kind();
        if (kind.hasAmount()) {
            int min = outcome.percent() ? 5 : kind.min, max = outcome.percent() ? 100 : kind.max;
            hits.add(new Hit(x, y, STEP_W, NUM_H, Part.AMOUNT_DOWN, row, 0, editable && outcome.amount() > min));
            hits.add(new Hit(x + STEP_W, y, VALUE_W, NUM_H, Part.AMOUNT, row, 0, editable));
            hits.add(new Hit(x + STEP_W + VALUE_W, y, STEP_W, NUM_H, Part.AMOUNT_UP, row, 0, editable && outcome.amount() < max));
            if (kind.percent) {
                int px = x + 2 * STEP_W + VALUE_W + SIGN_GAP;
                hits.add(new Hit(px, y, MODE_W, NUM_H, Part.NUMBER, row, 0, editable));
                hits.add(new Hit(px + MODE_W, y, MODE_W, NUM_H, Part.PERCENT, row, 1, editable));
            }
        }
        // The weight on the grid's right edge, under the last kind and the last target
        int wy = figuresWrap(outcome, w) ? y + NUM_H + ED_GAP : y;
        int wx = x + w - WEIGHT_GROUP_W + 9;
        hits.add(new Hit(wx, wy, STEP_W, NUM_H, Part.WEIGHT_DOWN, row, 0, editable && outcome.weight() > 0));
        hits.add(new Hit(wx + STEP_W, wy, WEIGHT_W, NUM_H, Part.WEIGHT, row, 0, editable));
        hits.add(new Hit(wx + STEP_W + WEIGHT_W, wy, STEP_W, NUM_H, Part.WEIGHT_UP, row, 0, editable && outcome.weight() < MistigriOutcomes.MAX_WEIGHT));
    }

    // ------------------------------------------------------------------ drawing

    /** Draws the module under its title: the box at ({@code mx}, {@code top}), {@code w} wide, then the line under it. */
    void render(DrawContext context, ItemStack stack, int mx, int top, int w, int mouseX, int mouseY, boolean editable) {
        this.editable = editable;
        MistigriOutcomes outcomes = sync(stack);
        layout(outcomes, mx, top, w);
        int bh = OutcomeListModule.LIST_H;
        context.fill(bx, by, bx + bw, by + bh, OUTLINE);
        context.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, BOX);
        context.enableScissor(bx + 1, by + 1 + LIST_PAD, bx + bw - 1, by + bh - 1 - LIST_PAD);
        boolean inBox = HitArea.contains(mouseX, mouseY, bx + 1, by + 1 + LIST_PAD, bw - 2, bh - 2 - 2 * LIST_PAD);
        int iw = innerW(scrollMax > 0);
        for (Block block : blocks) {
            if (block.y + block.h < by || block.y > by + bh) continue;
            drawRow(context, outcomes, block, iw, inBox ? mouseX : -1, inBox ? mouseY : -1);
        }
        if (outcomes.size() == 0) {
            UiText.centered(context, font, Text.translatable(K + "empty"), bx + 4, by + bh / 2 - 4, bw - 8, SOFT, false);
        }
        Hit chosenKind = null;
        for (Hit hit : hits) {
            if (hit.row < 0 || hit.part == Part.ROW || hit.y + hit.h < by || hit.y > by + bh) continue;
            if (hit.part == Part.KIND && hit.row < outcomes.size() && outcomes.get(hit.row).kind().ordinal() == hit.value) {
                chosenKind = hit; // the kinds share their edges: the chosen one's gold edges over its neighbours'
                continue;
            }
            drawHit(context, outcomes, hit, inBox && hit.contains(mouseX, mouseY));
        }
        if (chosenKind != null) drawHit(context, outcomes, chosenKind, inBox && chosenKind.contains(mouseX, mouseY));
        context.disableScissor();
        if (scrollMax > 0) {
            int track = bh - 4, thumb = Math.max(8, track * track / (track + scrollMax));
            int ty = by + 2 + (track - thumb) * scroll / scrollMax;
            context.fill(bx + bw - 4, by + 2, bx + bw - 2, by + bh - 2, 0xFF222222);
            context.fill(bx + bw - 4, ty, bx + bw - 2, ty + thumb, 0xFFC9A227);
        }
        for (Hit hit : hits) if (hit.row < 0) drawHit(context, outcomes, hit, hit.contains(mouseX, mouseY));
    }

    private void drawRow(DrawContext context, MistigriOutcomes outcomes, Block block, int iw, int mouseX, int mouseY) {
        int x0 = bx + 1, y = block.y, row = block.row;
        Outcome outcome = outcomes.get(row);
        if (row == confirm) {
            context.fill(x0, y, x0 + iw, y + block.h, CONFIRM);
            context.fill(x0, y, x0 + 2, y + block.h, 0xFFD9283B);
            Text question = Text.translatable(K + "delete.confirm");
            UiText.line(context, font, question, x0 + PAD, y + (ROW_H - ButtonGlyphs.CAP_H) / 2, iw - 2 * PAD, 0xFFFFD0D0, false);
            return;
        }
        boolean hovered = editable && HitArea.contains(mouseX, mouseY, x0, y, iw, ROW_H);
        int bg = row == open ? ROW_OPEN : hovered ? ROW_HOVER : row % 2 == 0 ? ROW_A : ROW_B;
        context.fill(x0, y, x0 + iw, y + ROW_H, bg);
        if (row == open) {
            context.fill(x0, y, x0 + 2, y + block.h, GOLD);
            context.fill(x0 + 2, y + ROW_H, x0 + iw, y + block.h, EDITOR);
        }
        boolean off = outcome.weight() == 0;
        // Its kind, who, how much
        int iy = y + (ROW_H - 16) / 2;
        flatItem(context, icon(outcome.kind()), x0 + PAD, iy);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        MistigriIcons.badge(context, outcome.kind(), x0 + PAD, iy);
        if (off) context.fill(x0 + PAD, iy, x0 + PAD + 16, iy + 16, 0x9A2C2C2C);
        context.getMatrices().pop();
        int whoX = x0 + PAD + 16 + ICON_GAP;
        MistigriIcons.target(context, outcome.target(), whoX, y + (ROW_H - MistigriIcons.SIZE) / 2, off);
        int chanceW = font.getWidth("100%");
        int textX = whoX + MistigriIcons.SIZE + TEXT_GAP, room = x0 + iw - PAD - DELETE_W - TEXT_GAP - chanceW - TEXT_GAP - textX;
        UiText.line(context, font, amount(outcome), textX, y + (ROW_H - ButtonGlyphs.CAP_H) / 2, room, off ? OFF : amountColor(outcome.kind()), false);
        // Its chance at a landing
        String chance = off ? "–" : Math.round(100 * (row < chances.length ? chances[row] : 0)) + "%";
        int cx = x0 + iw - PAD - DELETE_W - TEXT_GAP - font.getWidth(chance);
        UiText.line(context, font, chance, cx, y + (ROW_H - ButtonGlyphs.CAP_H) / 2, font.getWidth(chance), off ? OFF : SOFT, false);
    }

    /** A row's amount, short: « −10 », « −50% », « +2 », « 1-3 »... */
    private static String amount(Outcome outcome) {
        String figure = outcome.amount() + (outcome.percent() ? "%" : "");
        return switch (outcome.kind()) {
            case LOSE_COINS, LOSE_STARS, MOVE_BACK -> "−" + figure;
            case PITY -> "+" + figure;
            case SHARE_COINS, SHARE_STARS -> figure;
            case CURSED_DIE -> "1-3";
        };
    }

    private static int amountColor(Kind kind) {
        return switch (kind) {
            case LOSE_COINS, LOSE_STARS -> 0xFFFF8A80;
            case PITY -> 0xFF8CE07A;
            case SHARE_COINS, SHARE_STARS -> 0xFF7FD3EA;
            case MOVE_BACK -> 0xFFF29BC9;
            case CURSED_DIE -> 0xFFC79BF2;
        };
    }

    private void drawHit(DrawContext context, MistigriOutcomes outcomes, Hit hit, boolean hovered) {
        boolean hot = hovered && hit.active;
        Outcome outcome = hit.row >= 0 && hit.row < outcomes.size() ? outcomes.get(hit.row) : null;
        switch (hit.part) {
            case DELETE -> {
                if (!editable) return;
                PartyGui.button(context, hit.x, hit.y, hit.w, hit.h, hot ? PartyGui.FLAG_RED : DARK, false);
                ButtonGlyphs.cross(context, hit.x, hit.y, hit.w, hit.h, hot ? 0xFFFFFFFF : SOFT);
            }
            case YES -> textButton(context, hit, K + "yes", hot ? PartyGui.FLAG_RED.brighter() : PartyGui.FLAG_RED, 0xFFFFFFFF);
            case NO -> textButton(context, hit, K + "no", hot ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON, PartyGui.TEXT_DARK);
            case KIND -> {
                Kind kind = Kind.values()[hit.value];
                boolean chosen = outcome != null && outcome.kind() == kind;
                if (chosen) {
                    // A bright gold ring (its edges over its neighbours'), a dark inside: the item stays readable
                    context.fill(hit.x, hit.y, hit.x + hit.w, hit.y + hit.h, GOLD);
                    context.fill(hit.x + 1, hit.y + 1, hit.x + hit.w - 1, hit.y + hit.h - 1, 0xFF5A4410);
                } else {
                    PartyGui.button(context, hit.x, hit.y, hit.w, hit.h, hot ? DARK.brighter() : DARK, false);
                }
                int ix = hit.x + (hit.w - 16) / 2, iy = hit.y + (hit.h - 16) / 2;
                flatItem(context, icon(kind), ix, iy);
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200);
                MistigriIcons.badge(context, kind, ix, iy);
                context.getMatrices().pop();
            }
            case TARGET -> {
                Target target = Target.values()[hit.value];
                boolean chosen = outcome != null && outcome.target() == target;
                PartyGui.Theme theme = chosen ? PartyGui.BUTTON_SELECTED : !hit.active ? DIM : hot ? DARK.brighter() : DARK;
                PartyGui.button(context, hit.x, hit.y, hit.w, hit.h, theme, chosen);
                if (chosen) {
                    // A bright gold ring (the dark gold one hardly showed on the grey), the white figures on a dark inside
                    context.fill(hit.x + 1, hit.y + 1, hit.x + hit.w - 1, hit.y + hit.h - 1, GOLD);
                    context.fill(hit.x + 2, hit.y + 2, hit.x + hit.w - 2, hit.y + hit.h - 2, 0xFF5A4410);
                }
                MistigriIcons.target(context, target, hit.x + (hit.w - MistigriIcons.SIZE) / 2, hit.y + (hit.h - MistigriIcons.SIZE) / 2, !hit.active && !chosen);
            }
            case AMOUNT_DOWN, WEIGHT_DOWN, DRAWS_DOWN -> step(context, hit, "-", hot);
            case AMOUNT_UP, WEIGHT_UP, DRAWS_UP -> step(context, hit, "+", hot);
            case AMOUNT -> figure(context, hit, outcome == null ? "" : outcome.amount() + (outcome.percent() ? "%" : ""),
                    outcome == null ? TEXT : amountColor(outcome.kind()), hot);
            case WEIGHT -> {
                MistigriIcons.weight(context, hit.x - STEP_W - 9, hit.y + (hit.h - 7) / 2, outcome == null || outcome.weight() == 0);
                figure(context, hit, outcome == null ? "" : Integer.toString(outcome.weight()), outcome != null && outcome.weight() > 0 ? TEXT : OFF, hot);
            }
            case NUMBER, PERCENT -> {
                boolean chosen = outcome != null && outcome.percent() == (hit.part == Part.PERCENT);
                PartyGui.button(context, hit.x, hit.y, hit.w, hit.h, chosen ? PartyGui.BUTTON_SELECTED : hot ? DARK.brighter() : DARK, chosen);
                ButtonGlyphs.text(context, font, hit.part == Part.PERCENT ? "%" : "#", hit.x, hit.y, hit.w, hit.h, chosen ? 0xFF3B2600 : TEXT);
            }
            case ADD -> {
                PartyGui.Theme theme = !hit.active ? OFF_BUTTON : hot ? PartyGui.BUTTON_PRIMARY.brighter() : PartyGui.BUTTON_PRIMARY;
                if (!editable) return;
                PartyGui.button(context, hit.x, hit.y, hit.w, hit.h, theme, false);
                Text label = Text.translatable(K + "add");
                int color = hit.active ? 0xFFFFFFFF : 0xFF7A7A7A;
                if (hit.w >= addWords()) {
                    // The + sign (5 pixels), a space, the word: centred as one
                    int ink = 5 + 3 + font.getWidth(label) - 1, tx = hit.x + (hit.w - ink) / 2;
                    ButtonGlyphs.plus(context, tx, hit.y, 5, hit.h, color);
                    UiText.line(context, font, label, tx + 8, hit.y + (hit.h - ButtonGlyphs.CAP_H) / 2, font.getWidth(label), color, false);
                } else {
                    ButtonGlyphs.plus(context, hit.x, hit.y, hit.w, hit.h, color);
                }
            }
            case DRAWS -> {
                int m = outcomes.eligible().size();
                int fy = by + OutcomeListModule.LIST_H + OutcomeListModule.FOOTER_GAP;
                MistigriIcons.cards(context, hit.x - STEP_W - GROUP_GAP - MistigriIcons.CARDS_W,
                        fy + (OutcomeListModule.FOOTER_H - MistigriIcons.CARDS_H) / 2);
                figure(context, hit, outcomes.drawCount() + "/" + m, outcomes.drawCount() < m ? GOLD : TEXT, hot);
            }
            default -> {
            }
        }
    }

    private static final ItemStack[] ICONS = new ItemStack[Kind.values().length];

    /**
     * An item lit flat, like a sprite: a 3D model (the Party Star) is otherwise lit from the side as in a slot, its
     * faces dulled to grey next to the flat coin.
     */
    private static void flatItem(DrawContext context, ItemStack stack, int x, int y) {
        context.draw();
        DiffuseLighting.disableGuiDepthLighting();
        context.drawItem(stack, x, y);
        context.draw();
        DiffuseLighting.enableGuiDepthLighting();
    }

    /** A kind's icon in the default currencies (the cartridge is not tied to a party here). */
    private static ItemStack icon(Kind kind) {
        if (ICONS[kind.ordinal()] == null) ICONS[kind.ordinal()] = kind.icon(PartyCurrency.COIN.defaultStack(), PartyCurrency.STAR.defaultStack());
        return ICONS[kind.ordinal()];
    }

    /**
     * A button that can't be pressed now: the same black edge and size as the others (it stays on the grid, edges
     * aligned), a dim grey body, a dark sign: still a button, clearly off.
     */
    private static final PartyGui.Theme OFF_BUTTON = new PartyGui.Theme(OUTLINE, 0xFF6E6E6E, 0xFF555555, 0xFF3C3C3C);
    private static final int OFF_SIGN = 0xFF2E2E2E;

    private static final PartyGui.Theme DIM = new PartyGui.Theme(0xFF1E1E1E, 0xFF333333, 0xFF2A2A2A, 0xFF222222);

    private void textButton(DrawContext context, Hit hit, String key, PartyGui.Theme theme, int color) {
        PartyGui.button(context, hit.x, hit.y, hit.w, hit.h, theme, false);
        ButtonGlyphs.text(context, font, Text.translatable(key), hit.x, hit.y, hit.w, hit.h, color, false);
    }

    private void step(DrawContext context, Hit hit, String sign, boolean hot) {
        if (!editable) return;
        PartyGui.Theme theme = hit.active ? (hot ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON) : OFF_BUTTON;
        PartyGui.button(context, hit.x, hit.y, hit.w, hit.h, theme, false);
        int color = hit.active ? PartyGui.TEXT_DARK : OFF_SIGN;
        if (sign.equals("+")) ButtonGlyphs.plus(context, hit.x, hit.y, hit.w, hit.h, color);
        else ButtonGlyphs.minus(context, hit.x, hit.y, hit.w, hit.h, color);
    }

    private void figure(DrawContext context, Hit hit, String text, int color, boolean hot) {
        context.fill(hit.x, hit.y, hit.x + hit.w, hit.y + hit.h, hot ? 0xFF6A5418 : OUTLINE);
        context.fill(hit.x + 1, hit.y + 1, hit.x + hit.w - 1, hit.y + hit.h - 1, 0xFF161A14);
        ButtonGlyphs.text(context, font, text, hit.x, hit.y, hit.w, hit.h, Argb.lighten(color, 0.15F));
    }

    // ------------------------------------------------------------------ tooltips

    /** The tooltip under the mouse, null for none. */
    @Nullable List<Text> tooltip(int mouseX, int mouseY) {
        if (shown == null) return null;
        Hit hit = hitAt(mouseX, mouseY);
        if (hit == null) return null;
        Outcome outcome = hit.row >= 0 && hit.row < shown.size() ? shown.get(hit.row) : null;
        List<Text> lines = new ArrayList<>();
        switch (hit.part) {
            case ROW -> {
                if (outcome == null) return null;
                // The sentence as the roulette writes it, its target in its colour; then its chance and the click
                lines.add(outcome.text());
                MutableText second = outcome.weight() == 0 ? Text.translatable(K + "never").formatted(Formatting.GRAY)
                        : Text.translatable(K + "chance", Math.round(100 * chances[hit.row])).formatted(Formatting.GRAY);
                if (editable) second.append(Text.literal(" · ").formatted(Formatting.DARK_GRAY))
                        .append(Text.translatable(open == hit.row ? K + "row.close" : K + "row.open").formatted(Formatting.YELLOW));
                lines.add(second);
            }
            case DELETE -> lines.add(Text.translatable(K + "delete"));
            case KIND -> {
                String id = Kind.values()[hit.value].id;
                lines.add(PartyCurrencyText.menuText(K + "kind." + id));
                lines.add(PartyCurrencyText.menuText(K + "kind." + id + ".tooltip").formatted(Formatting.GRAY));
            }
            case TARGET -> {
                String id = Target.values()[hit.value].id;
                lines.add(PartyCurrencyText.menuText(K + "target." + id).styled(style -> style.withColor(Target.values()[hit.value].color)));
                lines.add(PartyCurrencyText.menuText(K + "target." + id + ".tooltip").formatted(Formatting.GRAY));
                if (!hit.active && editable) lines.add(Text.translatable(K + "only_landed").formatted(Formatting.DARK_GRAY));
            }
            case AMOUNT, AMOUNT_DOWN, AMOUNT_UP -> {
                if (outcome == null) return null;
                lines.add(Text.translatable(K + "amount"));
                lines.add(outcome.text().formatted(Formatting.GRAY));
            }
            case YES -> lines.add(Text.translatable(K + "delete"));
            case NUMBER -> lines.add(Text.translatable(K + "number"));
            case PERCENT -> lines.add(Text.translatable(K + "percent"));
            case WEIGHT, WEIGHT_DOWN, WEIGHT_UP -> {
                lines.add(Text.translatable(K + "weight"));
                lines.add(Text.translatable(K + "weight.tooltip").formatted(Formatting.GRAY));
            }
            case ADD -> lines.add(Text.translatable(hit.active ? K + "add.tooltip" : K + "add.full", MistigriOutcomes.MAX));
            case DRAWS, DRAWS_DOWN, DRAWS_UP -> {
                lines.add(Text.translatable(K + "draws", shown.drawCount(), shown.eligible().size()));
                lines.add(Text.translatable(K + "draws.tooltip").formatted(Formatting.GRAY));
            }
            default -> {
                return null;
            }
        }
        return lines;
    }

    // ------------------------------------------------------------------ input

    /** The hit box under the mouse: in the box only where it is shown. */
    private @Nullable Hit hitAt(double mouseX, double mouseY) {
        boolean inBox = HitArea.contains(mouseX, mouseY, bx + 1, by + 1 + LIST_PAD, bw - 2, OutcomeListModule.LIST_H - 2 - 2 * LIST_PAD);
        // The buttons before the row under them (added after them)
        for (Hit hit : hits) {
            if (hit.part == Part.ROW) continue;
            if ((hit.row < 0 || inBox) && hit.contains(mouseX, mouseY)) return hit;
        }
        for (Hit hit : hits) {
            if (hit.part == Part.ROW && inBox && hit.contains(mouseX, mouseY)) return hit;
        }
        return null;
    }

    boolean mouseClicked(double mouseX, double mouseY) {
        if (shown == null || !editable) return false;
        Hit hit = hitAt(mouseX, mouseY);
        if (hit == null) {
            // In the box, beside every row: nothing, but the click stays here
            return HitArea.contains(mouseX, mouseY, bx, by, bw, OutcomeListModule.LIST_H);
        }
        if (!hit.active) return true;
        Outcome outcome = hit.row >= 0 ? shown.get(hit.row) : null;
        switch (hit.part) {
            case ROW -> {
                open = open == hit.row ? -1 : hit.row;
                confirm = -1;
                reveal = open;
                click.run();
            }
            case DELETE -> {
                confirm = hit.row;
                click.run();
            }
            case NO -> {
                confirm = -1;
                click.run();
            }
            case YES -> act(OutcomeListModule.action(OutcomeListModule.DELETE, hit.row, 0));
            case KIND -> act(OutcomeListModule.action(OutcomeListModule.KIND, hit.row, hit.value));
            case TARGET -> act(OutcomeListModule.action(OutcomeListModule.TARGET, hit.row, hit.value));
            case NUMBER, PERCENT -> act(OutcomeListModule.action(OutcomeListModule.PERCENT, hit.row, hit.value));
            case AMOUNT_DOWN -> amount(hit.row, outcome, -1);
            case AMOUNT_UP -> amount(hit.row, outcome, 1);
            case WEIGHT_DOWN -> weight(hit.row, outcome, -1);
            case WEIGHT_UP -> weight(hit.row, outcome, 1);
            case ADD -> act(OutcomeListModule.action(OutcomeListModule.ADD, 0, 0));
            case DRAWS_DOWN -> draws(-1);
            case DRAWS_UP -> draws(1);
            default -> {
            }
        }
        return true;
    }

    /** The menu's click sound (a palette, a row opened: no change sent). */
    private Runnable click = () -> {
    };

    void onClick(Runnable click) {
        this.click = click;
    }

    private void amount(int row, Outcome outcome, int direction) {
        int value = outcome.amount(), next;
        if (outcome.percent()) {
            // By fives (ten with Shift)
            int step = Screen.hasShiftDown() ? 10 : 5;
            next = direction > 0 ? value - value % step + step : (value % step == 0 ? value - step : value - value % step);
            next = Math.clamp(next, 5, 100);
        } else {
            next = Math.clamp(value + direction * (Screen.hasShiftDown() ? 10 : 1), outcome.kind().min, outcome.kind().max);
        }
        if (next != value) act(OutcomeListModule.action(OutcomeListModule.AMOUNT, row, next));
    }

    private void weight(int row, Outcome outcome, int direction) {
        int next = Math.clamp(outcome.weight() + direction, 0, MistigriOutcomes.MAX_WEIGHT);
        if (next != outcome.weight()) act(OutcomeListModule.action(OutcomeListModule.WEIGHT, row, next));
    }

    /** One more / one less on the roulette; as many as there are: all of them (0, it follows the list). */
    private void draws(int direction) {
        int m = shown.eligible().size(), k = shown.drawCount();
        int next = Math.clamp(k + direction, Math.min(1, m), m);
        if (next == k) return;
        act(OutcomeListModule.action(OutcomeListModule.DRAWS, 0, next >= m ? 0 : next));
    }

    /** The wheel: over a figure it changes it, elsewhere in the box it scrolls the rows. */
    boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (shown == null || amount == 0) return false;
        int direction = amount > 0 ? 1 : -1;
        Hit hit = hitAt(mouseX, mouseY);
        if (hit != null && editable) {
            Outcome outcome = hit.row >= 0 && hit.row < shown.size() ? shown.get(hit.row) : null;
            switch (hit.part) {
                case AMOUNT -> {
                    amount(hit.row, outcome, direction);
                    return true;
                }
                case WEIGHT -> {
                    weight(hit.row, outcome, direction);
                    return true;
                }
                case DRAWS -> {
                    draws(direction);
                    return true;
                }
                default -> {
                }
            }
        }
        if (HitArea.contains(mouseX, mouseY, bx, by, bw, OutcomeListModule.LIST_H) && scrollMax > 0) {
            scroll = Math.clamp(scroll - direction * SCROLL_STEP, 0, scrollMax);
            return true;
        }
        return false;
    }
}
