package fr.lordfinn.steveparty.client.screens.pageeditor;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import fr.lordfinn.steveparty.client.gui.ConsolePaint;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.paint.Ramp;
import fr.lordfinn.steveparty.client.renderer.DestinationsRenderer;
import fr.lordfinn.steveparty.minigame.MiniGameFormat;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePipeLink;
import fr.lordfinn.steveparty.minigame.MiniGamePipeRole;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static fr.lordfinn.steveparty.client.screens.pageeditor.PageEditorStyle.*;

/**
 * The « Pipes » tab, a switchboard: what goes in by a colour comes out by that colour.
 * <ul>
 *     <li>On the left, the <b>entrances</b>: the Mini-game Pipes the page is printed on (the way in, out of a party),
 *     each joined to the <b>trunk</b> (the pipes joined to them). A black mouth on each: its own mouth leads to the
 *     Entry pipes. Click: where it is; right click (or its ×, twice): unlinked, its page wiped off.</li>
 *     <li>From the trunk, one <b>mouth and arrow per role</b>, in the colour of its pipes, into its <b>lane</b>: the
 *     pipes linked to the page with that role, as cards. The Exit lane's arrow points back: the way out.</li>
 *     <li>Hovering a lane lights its path, from the entrances to its pipes (back for the exit); dragging a card to
 *     another lane gives it that role; right click on a card unlinks it, a click shows where it is. The small button
 *     of a lane says how its players are sent to its pipes: each in turn, or at random.</li>
 *     <li>The first line says what the formats ticked still miss (the colours of the missing pipes), and how many
 *     pipes the page has.</li>
 * </ul>
 */
public final class PipesTab {
    /** The lanes, top to bottom. */
    private static final MiniGamePipeRole[] LANES = {MiniGamePipeRole.PLAYERS, MiniGamePipeRole.TEAM_A, MiniGamePipeRole.TEAM_B,
            MiniGamePipeRole.TEAM_C, MiniGamePipeRole.TEAM_D, MiniGamePipeRole.SPECTATORS, MiniGamePipeRole.ENTRY, MiniGamePipeRole.EXIT};
    // The grid (from the page's corner)
    private static final int TOP = 24, PITCH = 20, LANE_H = 18;
    private static final int ENTRANCE_X = 12, ENTRANCE_W = 76, ENTRANCE_H = 18, ENTRANCE_ROWS = 7, ENTRANCE_SHIFT = PITCH / 2;
    private static final int STUB_X = 88, TRUNK_X = 92, TRUNK_W = 4, MOUTH_X = 90, MOUTH = 8, ARROW_X = 100, ARROW_W = 8;
    private static final int LANE_X = 110, LANE_W = 202, LABEL_W = 72, SLOT1 = 76, SLOT2 = 132, CARD_W = 54, CARD_H = 14, TOGGLE = 188, TOGGLE_SIZE = 12;
    private static final int BOTTOM = TOP + LANES.length * PITCH - 2;
    /** The overlay of a lane with more pipes than its two slots. */
    private static final int OVERLAY_COLS = 2, OVERLAY_PITCH = 16;
    /** How long an armed unlink of an entrance waits for its second click (ms). */
    private static final long ARM_MS = 2000;

    private final PageEditor editor;
    /** The mini-game pipes printed with the page, as the server last said. */
    private MiniGamePagePayloads.Entrances entrances = new MiniGamePagePayloads.Entrances(new UUID(0, 0), List.of(), List.of());
    /** The first card shown in each lane (the wheel cycles them), the first entrance shown. */
    private final int[] offset = new int[LANES.length];
    private int entranceScroll;
    /** The card held by the mouse, and whether it moved (a drag) since it was pressed. */
    private @Nullable MiniGamePipeLink held;
    private boolean dragged;
    private double pressX, pressY;
    /** The lane whose overflow is open over the tab, -1 for none. */
    private int expanded = -1;
    /** The entrance whose unlink waits for a second click, and since when. */
    private @Nullable GlobalPos armed;
    private long armedAt;

    public PipesTab(PageEditor editor) {
        this.editor = editor;
    }

    public void setEntrances(MiniGamePagePayloads.Entrances entrances) {
        this.entrances = entrances;
        entranceScroll = Math.max(0, Math.min(entranceScroll, entrances.pipes().size() - ENTRANCE_ROWS));
    }

    private TextRenderer font() {
        return editor.font();
    }

    /** The card held is let go (another tab is shown). */
    public void drop() {
        held = null;
        expanded = -1;
        armed = null;
    }

    // ------------------------------------------------------------------ geometry

    private int x(int px) {
        return editor.left() + px;
    }

    private int laneY(int lane) {
        return editor.top() + TOP + lane * PITCH;
    }

    private int laneAt(double mouseX, double mouseY) {
        for (int lane = 0; lane < LANES.length; lane++) {
            if (HitArea.contains(mouseX, mouseY, x(MOUTH_X), laneY(lane), LANE_X + LANE_W - MOUTH_X, LANE_H)) return lane;
        }
        return -1;
    }

    private static int laneOf(MiniGamePipeRole role) {
        for (int lane = 0; lane < LANES.length; lane++) if (LANES[lane] == role) return lane;
        return -1;
    }

    /** The cards a collapsed lane shows: one or two pipes, the second slot a stack when there are more than two. */
    private List<MiniGamePipeLink> shown(List<MiniGamePipeLink> pipes, int lane) {
        if (pipes.size() <= 2) return pipes;
        int first = Math.floorMod(offset[lane], pipes.size());
        return List.of(pipes.get(first));
    }

    private int slotX(int slot) {
        return x(LANE_X + (slot == 0 ? SLOT1 : SLOT2));
    }

    /** The card under the mouse in a collapsed lane, null for none. */
    private @Nullable MiniGamePipeLink cardAt(double mouseX, double mouseY) {
        MiniGamePipeLink inOverlay = overlayCardAt(mouseX, mouseY);
        if (inOverlay != null || overOverlay(mouseX, mouseY)) return inOverlay;
        int lane = laneAt(mouseX, mouseY);
        if (lane < 0) return null;
        List<MiniGamePipeLink> cards = shown(editor.current().pipes(LANES[lane]), lane);
        for (int slot = 0; slot < cards.size(); slot++) {
            if (HitArea.contains(mouseX, mouseY, slotX(slot), laneY(lane) + 2, CARD_W, CARD_H)) return cards.get(slot);
        }
        return null;
    }

    /** Whether the mouse is on the stack card of a lane with more than two pipes; its lane, else -1. */
    private int stackAt(double mouseX, double mouseY) {
        int lane = laneAt(mouseX, mouseY);
        if (lane < 0 || editor.current().pipes(LANES[lane]).size() <= 2) return -1;
        return HitArea.contains(mouseX, mouseY, slotX(1), laneY(lane) + 2, CARD_W, CARD_H) ? lane : -1;
    }

    private int toggleAt(double mouseX, double mouseY) {
        int lane = laneAt(mouseX, mouseY);
        if (lane < 0 || !LANES[lane].hasOrder()) return -1;
        return HitArea.contains(mouseX, mouseY, x(LANE_X + TOGGLE), laneY(lane) + 3, TOGGLE_SIZE, TOGGLE_SIZE) ? lane : -1;
    }

    /** The × of the hovered card (unlinks it). */
    private boolean onCardCross(double mouseX, double mouseY, int cardX, int cardY) {
        return HitArea.contains(mouseX, mouseY, cardX + CARD_W - 9, cardY, 9, 9);
    }

    // ---- the overflow overlay

    private int overlayRows(int lane) {
        int count = editor.current().pipes(LANES[lane]).size();
        return (count + OVERLAY_COLS - 1) / OVERLAY_COLS;
    }

    private int overlayTop(int lane) {
        int rows = overlayRows(lane);
        int top = laneY(lane);
        // Grows down, up to the bottom of the lanes: lifted when it would go past
        int bottom = editor.top() + BOTTOM;
        return Math.max(editor.top() + TOP, Math.min(top, bottom - (rows * OVERLAY_PITCH + 4)));
    }

    private boolean overOverlay(double mouseX, double mouseY) {
        if (expanded < 0) return false;
        int rows = overlayRows(expanded);
        return HitArea.contains(mouseX, mouseY, slotX(0) - 2, overlayTop(expanded), 2 * CARD_W + 6, rows * OVERLAY_PITCH + 4);
    }

    private @Nullable MiniGamePipeLink overlayCardAt(double mouseX, double mouseY) {
        if (expanded < 0) return null;
        List<MiniGamePipeLink> pipes = editor.current().pipes(LANES[expanded]);
        int top = overlayTop(expanded) + 2;
        for (int i = 0; i < pipes.size(); i++) {
            int cx = slotX(0) + (i % OVERLAY_COLS) * (CARD_W + 2), cy = top + (i / OVERLAY_COLS) * OVERLAY_PITCH;
            if (HitArea.contains(mouseX, mouseY, cx, cy, CARD_W, CARD_H)) return pipes.get(i);
        }
        return null;
    }

    // ---- the entrances

    private int entranceY(int row) {
        return editor.top() + TOP + ENTRANCE_SHIFT + row * PITCH;
    }

    private int visibleEntrances() {
        return Math.min(ENTRANCE_ROWS, entrances.pipes().size() - entranceScroll);
    }

    private int entranceAt(double mouseX, double mouseY) {
        for (int row = 0; row < visibleEntrances(); row++) {
            if (HitArea.contains(mouseX, mouseY, x(ENTRANCE_X), entranceY(row), ENTRANCE_W, ENTRANCE_H)) return row + entranceScroll;
        }
        return -1;
    }

    private boolean onEntranceCross(double mouseX, double mouseY, int index) {
        int row = index - entranceScroll;
        return HitArea.contains(mouseX, mouseY, x(ENTRANCE_X + 66), entranceY(row) + 4, 10, 10);
    }

    private boolean onEmptyEntrance(double mouseX, double mouseY) {
        return entrances.pipes().isEmpty() && HitArea.contains(mouseX, mouseY, x(ENTRANCE_X), entranceY(0), ENTRANCE_W, ENTRANCE_H);
    }

    private static Block entranceBlock(int tier) {
        return switch (tier) {
            case 2 -> ModBlocks.GOLDEN_MINIGAME_PIPE;
            case 1 -> ModBlocks.IRON_MINIGAME_PIPE;
            default -> ModBlocks.COPPER_MINIGAME_PIPE;
        };
    }

    // ------------------------------------------------------------------ what it says

    private static Text xz(BlockPos pos) {
        return Text.literal(pos.getX() + " " + pos.getZ());
    }

    private static Text xyz(BlockPos pos) {
        return Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    /** The roles the formats ticked need a pipe of. */
    private static Set<MiniGamePipeRole> needed(MiniGamePageData data) {
        Set<MiniGamePipeRole> needed = EnumSet.noneOf(MiniGamePipeRole.class);
        for (MiniGameFormat format : data.formats()) needed.addAll(MiniGamePipeRole.needed(format));
        return needed;
    }

    /** The roles some ticked format misses a pipe of, and the first such format. */
    private record Missing(Set<MiniGamePipeRole> roles, @Nullable MiniGameFormat format) {
    }

    private static Missing missing(MiniGamePageData data) {
        Set<MiniGamePipeRole> roles = new LinkedHashSet<>();
        MiniGameFormat first = null;
        for (MiniGameFormat format : data.formats()) {
            List<MiniGamePipeRole> lacking = data.missing(format);
            if (!lacking.isEmpty() && first == null) first = format;
            roles.addAll(lacking);
        }
        return new Missing(roles, first);
    }

    private static String dyeName(String dye) {
        return Text.translatable("color.minecraft." + dye).getString().toLowerCase(Locale.ROOT);
    }

    /** The colour word of a role's pipes (« vert »), « en verre » / « glass » for the spectators. */
    private static Text colourWord(MiniGamePipeRole role) {
        if (role == MiniGamePipeRole.SPECTATORS) return Text.translatable(KEY + "pipes.colour.glass");
        return Text.literal(role.pipeColors().isEmpty() ? "" : dyeName(role.pipeColors().getFirst()));
    }

    /** The colour of the pipe a card stands for (0xRRGGBB): the pipe's, whatever its role; its role's when not known. */
    private static int pipeColor(MiniGamePipeLink link) {
        if (link.kind() == PipeKind.GLASS) return 0xD6ECF2;
        if (link.color() == MiniGamePipeLink.UNKNOWN_COLOR) return link.role().color();
        String name = ModBlocks.COLORS[link.color()];
        for (MiniGamePipeRole role : MiniGamePipeRole.values()) if (!role.pipeColors().isEmpty() && role.pipeColors().getFirst().equals(name)) return role.color();
        DyeColor dye = DyeColor.byName(name, null);
        return dye == null ? link.role().color() : Argb.lerp(Argb.opaque(dye.getEntityColor()), WHITE, 0.2f) & 0xFFFFFF;
    }

    /** The colour of a lane's background: its role's at a fifth over the paper (a grey for the black, the paper for the white). */
    private static int laneTint(MiniGamePipeRole role) {
        if (role == MiniGamePipeRole.ENTRY) return 0xFFC9D0D1;
        if (role == MiniGamePipeRole.SPECTATORS) return PAPER;
        return Argb.lerp(PAPER, Argb.opaque(role.color()), 0.22f);
    }

    /** {@code argb} faded halfway to the paper. */
    private static int faded(int argb) {
        return Argb.lerp(Argb.opaque(argb), PAPER, 0.55f);
    }

    // ------------------------------------------------------------------ drawing

    public void draw(DrawContext context, MiniGamePageData data, int mouseX, int mouseY) {
        boolean dragging = held != null && dragged;
        int hoveredLane = expanded >= 0 ? expanded : laneAt(mouseX, mouseY);
        int hoveredEntrance = held == null ? entranceAt(mouseX, mouseY) : -1;
        if (armed != null && (Util.getMeasuringTimeMs() - armedAt > ARM_MS || hoveredEntrance < 0
                || !entrances.pipes().get(hoveredEntrance).equals(armed))) armed = null;
        Missing missing = missing(data);
        Set<MiniGamePipeRole> needed = needed(data);
        boolean overStatus = HitArea.contains(mouseX, mouseY, x(8), editor.top() + 8, 286, 12);

        drawStatus(context, data, missing);
        // The path lit: the hovered lane's (or the dragged card's target), or every path from the hovered entrance
        int lit = dragging ? laneAt(mouseX, mouseY) : hoveredLane;
        drawEntrances(context, hoveredEntrance, lit >= 0 && hoveredEntrance < 0 ? LANES[lit] : null);
        drawTrunk(context, lit >= 0 ? LANES[lit] : null, lit, hoveredEntrance >= 0);
        for (int lane = 0; lane < LANES.length; lane++) {
            MiniGamePipeRole role = LANES[lane];
            boolean dim = lit >= 0 && lit != lane;
            boolean optional = !needed.contains(role) && role.isArrival() && role != MiniGamePipeRole.SPECTATORS;
            boolean lacking = missing.roles().contains(role);
            drawMouthAndArrow(context, lane, dim, lacking);
            drawLane(context, data, lane, optional, lacking || (overStatus && lacking), dragging, lit == lane, mouseX, mouseY);
        }
    }

    /** The first line: a red « ! », the colours of the missing pipes and the format missing them; else « Ready ». The pipe count on the right. */
    private void drawStatus(DrawContext context, MiniGamePageData data, Missing missing) {
        int left = x(8), top = editor.top() + 10;
        if (missing.format() == null) {
            PartyGui.statusIcon(context, left, top - 1, true);
            UiText.line(context, font(), Text.translatable(KEY + "pipes.ready"), left + 12, top, 200, GREEN2, false);
        } else {
            PartyGui.statusIcon(context, left, top - 1, false);
            int sx = left + 12;
            for (MiniGamePipeRole role : missing.roles()) {
                context.fill(sx, top, sx + 8, top + 8, INK);
                context.fill(sx + 1, top + 1, sx + 7, top + 7, Argb.opaque(role.color()));
                sx += 10;
            }
            UiText.line(context, font(), missing.format().name(), sx + 2, top, 286 - (sx + 2 - left), RED, false);
        }
        Text count = Text.literal(data.pipeLinks().size() + "/" + MiniGamePageData.MAX_PIPE_LINKS);
        int full = data.pipeLinks().size() >= MiniGamePageData.MAX_PIPE_LINKS ? RED : INK2;
        UiText.line(context, font(), count, x(294) - font().getWidth(count), top, font().getWidth(count) + 1, full, false);
    }

    private void drawEntrances(DrawContext context, int hovered, @Nullable MiniGamePipeRole lit) {
        if (entrances.pipes().isEmpty()) {
            // A dashed card: a faded copper pipe and a « + »
            int left = x(ENTRANCE_X), top = entranceY(0);
            dashedBox(context, left, top, ENTRANCE_W, ENTRANCE_H, INK3);
            PartyGui.ghostItem(context, new ItemStack(ModBlocks.COPPER_MINIGAME_PIPE), left + 1, top + 1, null);
            UiText.line(context, font(), "+", left + 22, top + 5, 8, INK3, false);
            return;
        }
        for (int row = 0; row < visibleEntrances(); row++) {
            int index = row + entranceScroll;
            GlobalPos pipe = entrances.pipes().get(index);
            int left = x(ENTRANCE_X), top = entranceY(row);
            boolean over = index == hovered, isArmed = pipe.equals(armed);
            ConsolePaint.box(context, left, top, ENTRANCE_W, ENTRANCE_H, isArmed ? Ramp.of(0x7e9192, 0xffffff, 0xffd6d6, 0xf0b0b0) : CARD, 1, 1);
            if (over) ConsolePaint.highlight(context, left, top, ENTRANCE_W, ENTRANCE_H, 1, TEAL2, 0);
            context.drawItem(new ItemStack(entranceBlock(entrances.tiers().get(index))), left + 1, top + 1);
            Text text = xz(pipe.pos());
            UiText.line(context, font(), text, left + 20, top + 5, 46, INK, false);
            // Its own mouth, black: it leads to the Entry pipes. Hovered: its unlink cross
            int mx = left + 68, my = top + 5;
            if (over && editor.canEdit()) {
                if (isArmed) drawBin(context, mx, my);
                else drawCross(context, mx + 2, my + 2, RED);
            } else {
                // Its own mouth: a pipe end, black like the Entry pipes it leads to
                context.fill(mx + 2, my + 1, mx + 8, my + 7, INK);
                context.fill(mx + 3, my + 2, mx + 8, my + 6, Argb.opaque(MiniGamePipeRole.ENTRY.color()));
            }
            // Its stub to the trunk
            int stubColour = over ? TEAL : lit != null ? Argb.opaque(lit.color()) : EDGE;
            int cy = top + ENTRANCE_H / 2;
            context.fill(x(STUB_X), cy - 2, x(TRUNK_X), cy + 2, INK);
            context.fill(x(STUB_X), cy - 1, x(TRUNK_X), cy + 1, stubColour);
        }
        if (entranceScroll > 0) context.fill(x(ENTRANCE_X), entranceY(0) - 2, x(ENTRANCE_X + ENTRANCE_W), entranceY(0) - 1, INK3);
        if (entranceScroll + ENTRANCE_ROWS < entrances.pipes().size())
            context.fill(x(ENTRANCE_X), editor.top() + BOTTOM + 1, x(ENTRANCE_X + ENTRANCE_W), editor.top() + BOTTOM + 2, INK3);
    }

    /**
     * The trunk (the pipes joined to the entrances): neutral; lit in a role's colour with dashes marching to its lane
     * (up from the bottom for the exit), teal when an entrance is hovered; dashed when there is no entrance yet.
     */
    private void drawTrunk(DrawContext context, @Nullable MiniGamePipeRole lit, int litLane, boolean entranceHovered) {
        int left = x(TRUNK_X), top = editor.top() + TOP, bottom = editor.top() + BOTTOM;
        if (entrances.pipes().isEmpty()) {
            for (int py = top; py < bottom; py += 4) context.fill(left + 1, py, left + TRUNK_W - 1, Math.min(py + 2, bottom), INK3);
            return;
        }
        context.fill(left, top, left + TRUNK_W, bottom, INK);
        context.fill(left + 1, top + 1, left + TRUNK_W - 1, bottom - 1, entranceHovered ? TEAL : EDGE);
        if (lit != null && !entranceHovered) {
            // Lit down to the hovered mouth only
            bottom = laneY(litLane) + LANE_H / 2 + 2;
            context.fill(left + 1, top + 1, left + TRUNK_W - 1, bottom - 1, Argb.opaque(lit.color()));
            // Marching dashes: down to the lanes, up for the way out
            int phase = (int) (Util.getMeasuringTimeMs() / 90 % 4);
            if (lit == MiniGamePipeRole.EXIT) phase = 3 - phase;
            int dash = Argb.lerp(Argb.opaque(lit.color()), WHITE, 0.5f);
            for (int py = top + 1 + phase; py < bottom - 1; py += 4) context.fill(left + 1, py, left + TRUNK_W - 1, Math.min(py + 2, bottom - 1), dash);
        }
    }

    private void drawMouthAndArrow(DrawContext context, int lane, boolean dim, boolean lacking) {
        MiniGamePipeRole role = LANES[lane];
        int colour = Argb.opaque(role.color()), ink = INK;
        if (dim) {
            colour = faded(colour);
            ink = faded(INK);
        }
        int cy = laneY(lane) + LANE_H / 2;
        // The mouth on the trunk
        int mx = x(MOUTH_X), my = cy - 4;
        context.fill(mx, my, mx + MOUTH, my + MOUTH, ink);
        context.fill(mx + 1, my + 1, mx + MOUTH - 1, my + MOUTH - 1, colour);
        if (lacking) {
            // A red « ! » in the mouth of a missing pipe
            context.fill(mx + 3, my + 1, mx + 5, my + 5, RED);
            context.fill(mx + 3, my + 6, mx + 5, my + 7, RED);
        }
        // The arrow a little darker than the mouth, so that the white and the yellow read on the paper
        drawArrow(context, x(ARROW_X), cy - 2, role == MiniGamePipeRole.EXIT, Argb.lerp(colour, ink, 0.35f), ink);
    }

    /** An 8 x 5 arrow, outlined, pointing right (or left for the way out). */
    private static void drawArrow(DrawContext context, int left, int top, boolean back, int colour, int ink) {
        // Rows of the arrow, '#' outline, 'o' body (pointing right)
        String[] rows = {"....#...", "....##..", "#######.", "....##..", "....#..."};
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < 8; col++) {
                char c = rows[row].charAt(back ? 7 - col : col);
                if (c == '#') PartyGui.pixel(context, left + col, top + row, colour);
            }
        }
    }

    private void drawLane(DrawContext context, MiniGamePageData data, int lane, boolean optional, boolean lacking, boolean dragging,
                          boolean lit, int mouseX, int mouseY) {
        MiniGamePipeRole role = LANES[lane];
        int left = x(LANE_X), top = laneY(lane), right = left + LANE_W, bottom = top + LANE_H;
        int tint = laneTint(role);
        if (lit && dragging) tint = Argb.lerp(tint, Argb.opaque(role.color()), 0.3f);
        if (optional && !lit) tint = Argb.lerp(tint, PAPER, 0.5f);
        int edge = lacking ? RED : dragging ? TEAL : EDGE;
        context.fill(left, top, right, bottom, edge);
        context.fill(left + 1, top + 1, right - 1, bottom - 1, tint);
        // Its stripe and its name
        int stripe = Argb.opaque(role.color());
        context.fill(left + 1, top + 1, left + 5, bottom - 1, role == MiniGamePipeRole.SPECTATORS ? 0xFFC8D4D8 : stripe);
        if (role == MiniGamePipeRole.SPECTATORS) context.fill(left + 5, top + 1, left + 6, bottom - 1, 0xFF8A9AA0);
        UiText.line(context, font(), role.text(), left + 8, top + 5, LABEL_W - 10, INK, false);

        List<MiniGamePipeLink> pipes = data.pipes(role);
        if (pipes.isEmpty()) {
            drawEmptySlots(context, role, slotX(0), top + 2);
        } else {
            List<MiniGamePipeLink> cards = shown(pipes, lane);
            for (int slot = 0; slot < cards.size(); slot++) {
                MiniGamePipeLink link = cards.get(slot);
                int cx = slotX(slot), cy = top + 2;
                if (link.equals(held) && dragged) {
                    dashedBox(context, cx, cy, CARD_W, CARD_H, INK3);
                    continue;
                }
                boolean over = held == null && expanded < 0 && HitArea.contains(mouseX, mouseY, cx, cy, CARD_W, CARD_H);
                drawCard(context, link, cx, cy, over, null);
            }
            if (pipes.size() > 2) drawStack(context, slotX(1), top + 2, pipes.size() - 1,
                    held == null && expanded < 0 && HitArea.contains(mouseX, mouseY, slotX(1), top + 2, CARD_W, CARD_H));
        }
        if (role.hasOrder()) {
            boolean over = HitArea.contains(mouseX, mouseY, left + TOGGLE, top + 3, TOGGLE_SIZE, TOGGLE_SIZE);
            drawOrderButton(context, left + TOGGLE, top + 3, data.isRandom(role), over);
        } else {
            // The way out sends nobody anywhere: its toggle's place, greyed, keeps the column
            ConsolePaint.box(context, left + TOGGLE, top + 3, TOGGLE_SIZE, TOGGLE_SIZE, KEYCAP_OFF, 1, 1);
        }
    }

    /** No pipe of this role: a dashed box over both slots, « + green pipe ». */
    private void drawEmptySlots(DrawContext context, MiniGamePipeRole role, int left, int top) {
        int width = SLOT2 + CARD_W - SLOT1;
        dashedBox(context, left, top, width, CARD_H, INK3);
        context.fill(left + 3, top + 2, left + 5, top + 12, Argb.opaque(role == MiniGamePipeRole.SPECTATORS ? 0xD6ECF2 : role.color()));
        UiText.line(context, font(), Text.translatable(KEY + "pipes.empty", colourWord(role)), left + 8, top + 3, width - 10, INK3, false);
    }

    private static void dashedBox(DrawContext context, int left, int top, int width, int height, int colour) {
        int right = left + width, bottom = top + height;
        for (int px = left; px < right; px += 3) {
            context.fill(px, top, Math.min(px + 2, right), top + 1, colour);
            context.fill(px, bottom - 1, Math.min(px + 2, right), bottom, colour);
        }
        for (int py = top; py < bottom; py += 3) {
            context.fill(left, py, left + 1, Math.min(py + 2, bottom), colour);
            context.fill(right - 1, py, right, Math.min(py + 2, bottom), colour);
        }
    }

    /** A pipe card: the mark of its pipe, « x z » (a « ← » before it for the way out); hovered, its × to unlink. */
    private void drawCard(DrawContext context, MiniGamePipeLink link, int left, int top, boolean hovered, @Nullable MiniGamePipeRole preview) {
        ConsolePaint.box(context, left, top, CARD_W, CARD_H, CARD, 1, 1);
        if (hovered) ConsolePaint.highlight(context, left, top, CARD_W, CARD_H, 1, TEAL2, 0);
        int color = Argb.opaque(preview != null ? preview.color() : pipeColor(link));
        int markLeft = left + 2, markTop = top + 2, markRight = left + 4, markBottom = top + 12;
        context.fill(markLeft, markTop, markRight, markBottom, color);
        if (preview == null && link.kind() == PipeKind.WINDOWED) context.fill(markLeft, markTop + 3, markRight, markBottom - 3, 0xFFEAF6FA);
        int textLeft = left + 6;
        if (link.role() == MiniGamePipeRole.EXIT) {
            // ← : the way out (5 x 5)
            context.fill(left + 6, top + 6, left + 11, top + 7, INK2);
            context.fill(left + 7, top + 5, left + 8, top + 8, INK2);
            context.fill(left + 8, top + 4, left + 9, top + 5, INK2);
            context.fill(left + 8, top + 8, left + 9, top + 9, INK2);
            textLeft = left + 13;
        }
        int room = left + CARD_W - 2 - textLeft - (hovered && editor.canEdit() ? 8 : 0);
        UiText.line(context, font(), xz(link.mouth().pos()), textLeft, top + 3, room, INK, false);
        if (hovered && editor.canEdit()) drawCross(context, left + CARD_W - 7, top + 2, RED);
    }

    /** The second slot of a lane with more than two pipes: a stack of cards, « +N ». */
    private void drawStack(DrawContext context, int left, int top, int more, boolean hovered) {
        // Two cards behind, 2 px up and right each
        ConsolePaint.box(context, left + 4, top - 2, CARD_W - 4, CARD_H - 2, CARD, 1, 1);
        ConsolePaint.box(context, left + 2, top, CARD_W - 4, CARD_H - 2, CARD, 1, 1);
        ConsolePaint.box(context, left, top + 2, CARD_W - 4, CARD_H - 2, CARD, 1, 1);
        if (hovered) ConsolePaint.highlight(context, left, top + 2, CARD_W - 4, CARD_H - 2, 1, TEAL2, 0);
        UiText.centered(context, font(), Text.literal("+" + more), left, top + 4, CARD_W - 4, INK, false);
    }

    /** A 5 x 5 cross. */
    private static void drawCross(DrawContext context, int left, int top, int colour) {
        for (int i = 0; i < 5; i++) {
            PartyGui.pixel(context, left + i, top + i, colour);
            PartyGui.pixel(context, left + 4 - i, top + i, colour);
        }
    }

    /** A 8 x 8 bin: the armed unlink of an entrance (a second click wipes its page off). */
    private static void drawBin(DrawContext context, int left, int top) {
        context.fill(left + 1, top, left + 7, top + 1, RED);
        context.fill(left + 3, top - 1, left + 5, top, RED);
        context.fill(left + 1, top + 2, left + 7, top + 8, RED);
        context.fill(left + 2, top + 3, left + 3, top + 7, 0xFFFFD6D6);
        context.fill(left + 5, top + 3, left + 6, top + 7, 0xFFFFD6D6);
    }

    /** The 12 px toggle of a lane: two arrows going round for « each in turn », a dice face for « at random ». */
    private void drawOrderButton(DrawContext context, int left, int top, boolean random, boolean hovered) {
        ConsolePaint.box(context, left, top, TOGGLE_SIZE, TOGGLE_SIZE, hovered && editor.canEdit() ? KEYCAP : CARD, 1, 1);
        int ink = INK;
        if (random) {
            for (int[] dot : new int[][]{{3, 3}, {7, 3}, {5, 5}, {3, 7}, {7, 7}}) context.fill(left + dot[0], top + dot[1], left + dot[0] + 2, top + dot[1] + 2, ink);
        } else {
            // Two arrows round: → on top, ← below
            context.fill(left + 3, top + 4, left + 8, top + 5, ink);
            PartyGui.pixel(context, left + 7, top + 3, ink);
            PartyGui.pixel(context, left + 8, top + 4, ink);
            PartyGui.pixel(context, left + 7, top + 5, ink);
            context.fill(left + 4, top + 7, left + 9, top + 8, ink);
            PartyGui.pixel(context, left + 4, top + 6, ink);
            PartyGui.pixel(context, left + 3, top + 7, ink);
            PartyGui.pixel(context, left + 4, top + 8, ink);
        }
    }

    /** Over everything: the open overflow of a lane, the card being dragged, or the tooltip of what is under the mouse. */
    public void drawOverlay(DrawContext context, int mouseX, int mouseY) {
        if (expanded >= 0) {
            if (!overOverlay(mouseX, mouseY) && held == null) expanded = -1;
            else drawExpanded(context, mouseX, mouseY);
        }
        if (held != null && dragged) {
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 200);
            int lane = laneAt(mouseX, mouseY);
            context.fill(mouseX - CARD_W / 2 + 1, mouseY - CARD_H / 2 + 1, mouseX + CARD_W / 2 + 1, mouseY + CARD_H / 2 + 1, INK);
            drawCard(context, held, mouseX - CARD_W / 2, mouseY - CARD_H / 2, true, lane >= 0 ? LANES[lane] : null);
            context.getMatrices().pop();
            return;
        }
        List<Text> lines = tooltip(mouseX, mouseY);
        if (lines == null) return;
        int lane = laneAt(mouseX, mouseY);
        // Over a lane: under it (vanilla draws a tooltip 12 px above the point it is given)
        int anchorY = lane >= 0 && expanded < 0 ? laneY(lane) + LANE_H + 2 + 12 : mouseY;
        if (lane >= 0 && lane >= LANES.length - 2 && expanded < 0) anchorY = laneY(lane) - 4 - 10 * lines.size() + 12;
        PagePaint.tooltip(context, font(), lines, mouseX, anchorY);
    }

    private void drawExpanded(DrawContext context, int mouseX, int mouseY) {
        List<MiniGamePipeLink> pipes = editor.current().pipes(LANES[expanded]);
        int rows = overlayRows(expanded);
        int left = slotX(0) - 2, top = overlayTop(expanded), width = 2 * CARD_W + 6, height = rows * OVERLAY_PITCH + 4;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 150);
        context.fill(left + 1, top + 1, left + width + 1, top + height + 1, INK);
        context.fill(left, top, left + width, top + height, EDGE);
        context.fill(left + 1, top + 1, left + width - 1, top + height - 1, laneTint(LANES[expanded]));
        for (int i = 0; i < pipes.size(); i++) {
            MiniGamePipeLink link = pipes.get(i);
            int cx = slotX(0) + (i % OVERLAY_COLS) * (CARD_W + 2), cy = top + 2 + (i / OVERLAY_COLS) * OVERLAY_PITCH;
            if (link.equals(held) && dragged) {
                dashedBox(context, cx, cy, CARD_W, CARD_H, INK3);
                continue;
            }
            drawCard(context, link, cx, cy, held == null && HitArea.contains(mouseX, mouseY, cx, cy, CARD_W, CARD_H), null);
        }
        context.getMatrices().pop();
    }

    private @Nullable List<Text> tooltip(int mouseX, int mouseY) {
        // The first line: what is missing, in full
        if (HitArea.contains(mouseX, mouseY, x(8), editor.top() + 8, 286, 12)) {
            List<Text> lines = new ArrayList<>();
            MiniGamePageData data = editor.current();
            for (MiniGameFormat format : data.formats()) {
                List<MiniGamePipeRole> lacking = data.missing(format);
                if (!lacking.isEmpty()) lines.add(Text.translatable(KEY + "formats.chip.missing", format.name(), PagePaint.missingText(lacking)).formatted(Formatting.RED));
            }
            return lines.isEmpty() ? null : lines;
        }
        if (onEmptyEntrance(mouseX, mouseY)) return List.of(Text.translatable(KEY + "pipes.entrance.empty").formatted(Formatting.GRAY));
        int entrance = entranceAt(mouseX, mouseY);
        if (entrance >= 0) {
            GlobalPos pipe = entrances.pipes().get(entrance);
            if (editor.canEdit() && onEntranceCross(mouseX, mouseY, entrance)) {
                return List.of(Text.translatable(KEY + (pipe.equals(armed) ? "pipes.entrance.confirm" : "pipes.entrance.unlink")).formatted(Formatting.RED));
            }
            Text name = entranceBlock(entrances.tiers().get(entrance)).getName();
            return List.of(Text.empty().append(name).append(" · ").append(xyz(pipe.pos())).formatted(Formatting.GOLD),
                    Text.translatable(KEY + (editor.canEdit() ? "pipes.entrance.hint" : "pipes.card.hint.read_only")).formatted(Formatting.GRAY));
        }
        int toggle = toggleAt(mouseX, mouseY);
        if (toggle >= 0) {
            boolean random = editor.current().isRandom(LANES[toggle]);
            return List.of(Text.translatable(KEY + (random ? "pipes.order.random" : "pipes.order.in_turn")).formatted(Formatting.GOLD),
                    Text.translatable(KEY + (random ? "pipes.order.random.hint" : "pipes.order.in_turn.hint")).formatted(Formatting.GRAY));
        }
        int stack = stackAt(mouseX, mouseY);
        if (stack >= 0 && expanded < 0) return List.of(Text.translatable(KEY + "pipes.stack", editor.current().pipes(LANES[stack]).size()).formatted(Formatting.GRAY));
        MiniGamePipeLink link = cardAt(mouseX, mouseY);
        if (link != null) {
            return List.of(Text.empty().append(xyz(link.mouth().pos())).append(" · " + link.mouth().dimension().getValue().getPath()).formatted(Formatting.GOLD),
                    Text.translatable(KEY + (editor.canEdit() ? "pipes.card.hint" : "pipes.card.hint.read_only")).formatted(Formatting.GRAY));
        }
        int lane = laneAt(mouseX, mouseY);
        if (lane >= 0) {
            MiniGamePipeRole role = LANES[lane];
            Text first = role == MiniGamePipeRole.EXIT ? Text.translatable(KEY + "pipes.lane.exit")
                    : role == MiniGamePipeRole.ENTRY ? Text.translatable(KEY + "pipes.lane.entry")
                    : Text.translatable(KEY + "pipes.lane.in", colourWord(role));
            Text name = Text.empty().append(role.text()).styled(style -> style.withColor(role == MiniGamePipeRole.ENTRY ? 0xB8B8B8 : role.color()));
            List<Text> lines = new ArrayList<>();
            lines.add(name);
            lines.add(first.copy().formatted(Formatting.GRAY));
            if (editor.current().pipes(role).isEmpty()) lines.add(Text.translatable(KEY + "pipes.lane.add", colourWord(role)).formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
            return lines;
        }
        return null;
    }

    // ------------------------------------------------------------------ input

    /** Shows where a pipe is: it blinks in the world for a few seconds (seen through the menu, and once it is closed). */
    private void locate(GlobalPos pipe) {
        DestinationsRenderer.locate(pipe, LOCATE_TICKS);
        MinecraftClient client = editor.client();
        if (client != null && client.player != null) client.player.playSound(ModSounds.SELECT_SOUND_EVENT, 1.0F, 1.4F);
        boolean here = client != null && client.world != null && client.world.getRegistryKey().equals(pipe.dimension());
        editor.setStatus(Text.translatable(KEY + (here ? "status.located" : "status.located_elsewhere"), xyz(pipe.pos())), false);
    }

    private void setRole(MiniGamePipeLink link, @Nullable MiniGamePipeRole role) {
        if (!editor.canEdit() || role == link.role()) return;
        editor.send(new MiniGamePagePayloads.PipeRole(editor.hand(), editor.pageId(), link.mouth(), role == null ? -1 : role.ordinal()));
        editor.playClick();
    }

    private void unlinkEntrance(GlobalPos pipe) {
        armed = null;
        editor.send(new MiniGamePagePayloads.EntranceUnlink(editor.hand(), editor.pageId(), pipe));
        MinecraftClient client = editor.client();
        if (client != null && client.player != null) client.player.playSound(ModSounds.CANCEL_SOUND_EVENT, 1.0F, 1.0F);
    }

    /** An entrance armed by a first click (its × or a right click) is unlinked by a second one within two seconds. */
    private void armOrUnlink(GlobalPos pipe) {
        if (pipe.equals(armed) && Util.getMeasuringTimeMs() - armedAt <= ARM_MS) unlinkEntrance(pipe);
        else {
            armed = pipe;
            armedAt = Util.getMeasuringTimeMs();
            editor.playClick();
        }
    }

    public boolean click(double mouseX, double mouseY, int button) {
        int entrance = entranceAt(mouseX, mouseY);
        if (entrance >= 0) {
            GlobalPos pipe = entrances.pipes().get(entrance);
            if (editor.canEdit() && (button == 1 || onEntranceCross(mouseX, mouseY, entrance))) armOrUnlink(pipe);
            else if (button == 0) locate(pipe);
            return true;
        }
        int toggle = toggleAt(mouseX, mouseY);
        if (toggle >= 0 && button == 0 && expanded < 0) {
            if (editor.canEdit()) {
                MiniGamePipeRole role = LANES[toggle];
                editor.send(new MiniGamePagePayloads.PipeOrder(editor.hand(), editor.pageId(), role.ordinal(), !editor.current().isRandom(role)));
                editor.playClick();
            }
            return true;
        }
        int stack = stackAt(mouseX, mouseY);
        if (stack >= 0 && expanded < 0 && button == 0) {
            expanded = stack;
            editor.playClick();
            return true;
        }
        MiniGamePipeLink link = cardAt(mouseX, mouseY);
        if (link != null && button == 1) {
            setRole(link, null);
            return true;
        }
        if (link != null && button == 0) {
            if (editor.canEdit() && onCardCross(mouseX, mouseY, cardLeft(link, mouseX, mouseY), cardTop(link, mouseX, mouseY))) {
                setRole(link, null);
                return true;
            }
            held = link;
            dragged = false;
            pressX = mouseX;
            pressY = mouseY;
            return true;
        }
        return expanded >= 0 && overOverlay(mouseX, mouseY);
    }

    /** The left of the card {@code link} under the mouse (in its lane or in the open overflow). */
    private int cardLeft(MiniGamePipeLink link, double mouseX, double mouseY) {
        if (overOverlay(mouseX, mouseY)) {
            int i = editor.current().pipes(LANES[expanded]).indexOf(link);
            return slotX(0) + (i % OVERLAY_COLS) * (CARD_W + 2);
        }
        return HitArea.contains(mouseX, mouseY, slotX(1), 0, CARD_W, 10000) ? slotX(1) : slotX(0);
    }

    private int cardTop(MiniGamePipeLink link, double mouseX, double mouseY) {
        if (overOverlay(mouseX, mouseY)) {
            int i = editor.current().pipes(LANES[expanded]).indexOf(link);
            return overlayTop(expanded) + 2 + (i / OVERLAY_COLS) * OVERLAY_PITCH;
        }
        return laneY(laneAt(mouseX, mouseY)) + 2;
    }

    /** @return true while a card is held (the drag is the tab's) */
    public boolean drag(double mouseX, double mouseY) {
        if (held != null && (Math.abs(mouseX - pressX) > 3 || Math.abs(mouseY - pressY) > 3)) {
            if (!dragged) expanded = -1;
            dragged = true;
        }
        return held != null;
    }

    /** The card held let go: dropped on a lane, that role; not moved, where its pipe is. @return true if one was held */
    public boolean release(double mouseX, double mouseY, int button) {
        if (held == null || button != 0) return false;
        MiniGamePipeLink link = held;
        held = null;
        if (dragged) {
            int lane = laneAt(mouseX, mouseY);
            if (lane >= 0) setRole(link, LANES[lane]);
        } else {
            locate(link.mouth());
        }
        dragged = false;
        return true;
    }

    /** The wheel over a lane cycles its cards, over the entrances scrolls them. @return true if it was over one */
    public boolean scroll(double mouseX, double mouseY, double verticalAmount) {
        if (HitArea.contains(mouseX, mouseY, x(ENTRANCE_X), editor.top() + TOP, ENTRANCE_W, BOTTOM - TOP)) {
            entranceScroll = Math.max(0, Math.min(entrances.pipes().size() - ENTRANCE_ROWS, entranceScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        int lane = laneAt(mouseX, mouseY);
        if (lane < 0 || expanded >= 0) return lane >= 0;
        offset[lane] -= (int) Math.signum(verticalAmount);
        return true;
    }
}
