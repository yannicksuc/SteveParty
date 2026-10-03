package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyStrip;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import fr.lordfinn.steveparty.client.gui.party.PartyHudModel.StepKind;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * The turn bar, at the top of the screen by default: one plate with
 * <ul>
 * <li>a first row, the strip of steps, read from the left: the step being played, then the ones to come, each on an
 * arrow banner fitting in the one before it (now → next → later). The turn being played is gold, with its player's
 * face and name and a small gold marker bobbing under it; the next turns are white banners with the player's head,
 * rank, stars and coins; the other steps are coloured banners with their icon (a mini-game green, an event orange, the
 * turn order rolls and the preparation purple, the end red), and their name when there is room; a small « T4 » stands
 * before the first step of a round. As many steps as the bar's width allows: the next ones in full, the following
 * ones small (the head or the icon only) and fading, then « +N » (see {@link PartyStrip}). During the turn order
 * rolls, the players follow the current step, each with its roll;</li>
 * <li>a second row: the round (« Tour 3/10 »), then what is happening now (« À toi de lancer le dé ! », «
 * Déplacement » and the steps left...), with the Rejoue tag during a replay turn and the number (steps left, roll,
 * seconds) in a gold box.</li>
 * </ul>
 * Animated: when the step changes, the next banner slides into the first place, grows and turns gold while the others
 * follow, and pops; banners glide, grow, shrink and fade when the room, the scale or the steps change; the stars, coins
 * and rank of a banner pop when they change; the action line cross-fades when it changes, its number pops when it
 * changes. The layout is worked out when the model or the room changes, never per frame.
 */
final class TurnBarHud {
    private static final int PAD = 4;
    private static final int ROW_A = 20;
    private static final int ROW_B = 13;
    private static final int ROW_GAP = 2;
    static final int HEIGHT = PAD + ROW_A + ROW_GAP + ROW_B + PAD;
    private static final int MIN_WIDTH = 120;
    private static final int MAX_WIDTH = 360;
    private static final int NAME_MAX = 72;
    private static final float TURN_POP_TICKS = 9;
    private static final float CROSSFADE_TICKS = 6;
    private static final float BADGE_POP_TICKS = 7;
    private static final float BUMP_TICKS = 8;
    /** Per tick: how fast the banners and the bar reach their place and size. */
    private static final float GLIDE = 0.45f;

    // The banners: the big ones (the current step, the steps in full) and the small ones
    private static final Identifier ARROW_BIG = Steveparty.id("party_hud/arrow_20");
    private static final Identifier ARROW_SMALL = Steveparty.id("party_hud/arrow_16");
    private static final int BIG = 20, SMALL = 16;
    /** How deep the point of a banner goes into the notch of the one before it. */
    private static final int BIG_DEPTH = 5, SMALL_DEPTH = 4;
    private static final int FACE = 16, HEAD = 12;
    /** Widths of a banner with a face or a head only, and with an icon only. */
    private static final int CURRENT_TURN = 28, SMALL_TURN = 22, BIG_ICON = 22, SMALL_ICON = 20;
    /** Room kept at the end of the strip for the notch of its last banner. */
    private static final int TAIL = 4;
    /** Room a name takes besides its width: clear of the notch (a turn's name, a step's name). */
    private static final int NAME_ROOM = 4, CAPTION_ROOM = 3;
    private static final int SMALL_MODE = 0, FULL = 1, CURRENT = 2;

    private static final int TINT_TURN = 0xFFFFFFFF;
    private static final int TINT_MINE = 0xFFD8F7C8;
    private static final int TINT_CURRENT = 0xFFFFD23A;
    private static final int TINT_MINI_GAME = 0xFF8FE07A;
    private static final int TINT_EVENT = 0xFFFFB04A;
    private static final int TINT_START = 0xFFCDA6F5;
    private static final int TINT_END = 0xFFF59A8A;
    private static final int ROUND_TEXT = 0xFF6A3FA0;
    private static final int[] RANK_COLORS = {0xFFFFC900, 0xFF9EDFD6, 0xFFFFA860, 0xFFD3B3F2};

    // ---------------------------------------------------------------- layout (on change only)
    private PartyHudModel model;
    private int layoutRevision = -1;
    private int roomWidth = -1;
    /** The banners shown, the current step first. */
    private List<Chip> chips = new ArrayList<>();
    private List<Chip> previousChips = new ArrayList<>();
    /** The banners of the steps that left the strip, while they fade out. */
    private final List<Chip> leaving = new ArrayList<>();
    private String more;
    private int moreX;
    private int[] fullWidths = new int[0];
    private int[] smallWidths = new int[0];
    private Text roundText = Text.empty();
    private int roundWidth;
    private int width = MIN_WIDTH;
    private final ActionLine line = new ActionLine();
    private final ActionLine previousLine = new ActionLine();

    // ---------------------------------------------------------------- animation state
    private float shownWidth = -1;
    private float shownMoreX = -1;
    private int lastStepIndex = Integer.MIN_VALUE;
    private double turnChangedAt = -1000;
    private int lastActionKey;
    private double actionChangedAt = -1000;
    private String lastBadge;
    private double badgeChangedAt = -1000;
    private double lastFrame;

    /** A banner of the strip: what it shows, where it goes, and where it is shown now. */
    private static final class Chip {
        int key;
        PartyHudModel.Step step;
        PartyHudModel.Player player;
        Identifier icon;
        int tint;
        // What it may show
        OrderedText text;
        int textWidth;
        String rank;
        int rankWidth;
        int rankColor;
        String stars = "", coins = "";
        int countersX;
        String label;
        int labelWidth;
        int before;
        int fullWidth, smallWidth;
        // Where it goes
        int mode;
        boolean captioned;
        int x, w;
        float fade = 1;
        // Where it is
        float shownX, shownW, shownAlpha, grow, gold;
        boolean placed;
        // Its numbers pop when they change
        boolean counted;
        int lastStars, lastCoins, lastRank;
        double bumpAt = -1000;

        boolean sprite() {
            return step.kind != StepKind.ROLL;
        }

        boolean head() {
            return player != null && (step.kind == StepKind.TURN || step.kind == StepKind.ROLL);
        }
    }

    /** One action line, laid out. */
    private static final class ActionLine {
        Identifier icon;
        OrderedText text;
        int textWidth;
        String badge;
        int badgeWidth;
        boolean replay;
        OrderedText replayText;
        int replayWidth;
        boolean warn;
        int width;

        void copyFrom(ActionLine other) {
            icon = other.icon;
            text = other.text;
            textWidth = other.textWidth;
            badge = other.badge;
            badgeWidth = other.badgeWidth;
            replay = other.replay;
            replayText = other.replayText;
            replayWidth = other.replayWidth;
            warn = other.warn;
            width = other.width;
        }
    }

    /** Width of the whole bar, unscaled (valid after {@link #update}). */
    int width() {
        return width;
    }

    int height() {
        return HEIGHT;
    }

    /**
     * Takes a new model or new room (the unscaled width the bar may use): lays out again only if one of them
     * changed, and starts the animations of what changed.
     */
    void update(PartyHudModel model, int room, double now) {
        if (model == this.model && room == roomWidth) return;
        boolean newModel = model != this.model;
        this.model = model;
        this.roomWidth = room;
        layout(room, now);

        if (newModel) {
            if (model.stepIndex != lastStepIndex) {
                if (lastStepIndex != Integer.MIN_VALUE) turnChangedAt = now;
                lastStepIndex = model.stepIndex;
            }
            if (model.actionKey != lastActionKey) {
                if (layoutRevision >= 0) {
                    actionChangedAt = now;
                    badgeChangedAt = -1000;
                }
                lastActionKey = model.actionKey;
            } else if (model.badge != null && !model.badge.equals(lastBadge)) {
                badgeChangedAt = now;
            }
            lastBadge = model.badge;
        }
        layoutRevision++;
    }

    private void layout(int room, double now) {
        TextRenderer font = HudDraw.font();
        int maxContent = MathHelper.clamp(room, MIN_WIDTH, MAX_WIDTH) - 2 * PAD;
        int stripEnd = layoutStrip(font, maxContent, now);

        // Row B: [round] [Rejoue] icon text [badge]; a round too long for the line is shortened
        roundText = Text.empty();
        roundWidth = 0;
        if (model.round > 0) {
            roundText = model.rounds > 0 ? Text.translatable("hud.steveparty.party.round", model.round, model.rounds)
                    : Text.translatable("hud.steveparty.party.round.only", model.round);
            roundWidth = font.getWidth(roundText) + 10;
            if (layoutLine(line, maxContent - roundWidth - 4)) {
                roundText = model.rounds > 0 ? Text.translatable("hud.steveparty.party.round.short", model.round, model.rounds)
                        : Text.literal(Integer.toString(model.round));
                roundWidth = font.getWidth(roundText) + 10;
            }
        }
        layoutLine(line, maxContent - (roundWidth > 0 ? roundWidth + 4 : 0));
        int rowB = (roundWidth > 0 ? roundWidth + 4 : 0) + line.width;
        width = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, Math.max(stripEnd, rowB) + 2 * PAD));
    }

    /**
     * Lays out the strip in {@code maxContent} pixels: the current step, then what {@link PartyStrip#fit} lets in.
     *
     * @return where the strip ends
     */
    private int layoutStrip(TextRenderer font, int maxContent, double now) {
        List<Chip> old = chips;
        List<Chip> next = previousChips;
        next.clear();
        boolean snap = old.isEmpty() && leaving.isEmpty();
        more = null;
        List<PartyHudModel.Step> steps = model.strip;
        if (steps.isEmpty()) {
            leave(old);
            chips = next;
            previousChips = old;
            return 0;
        }

        // The banners of the steps, kept from the layout before: the one of the step being played stays the first
        // while that step goes on, the others are found by their key
        Chip first = !old.isEmpty() && (model.stepIndex == lastStepIndex || old.getFirst().key == steps.getFirst().key)
                ? old.removeFirst() : take(old, steps.getFirst().key);
        measure(first, steps.getFirst(), font, true);
        next.add(first);
        int upcoming = steps.size() - 1;
        if (fullWidths.length < upcoming) {
            fullWidths = new int[upcoming];
            smallWidths = new int[upcoming];
        }
        boolean firstRoll = true;
        for (int i = 0; i < upcoming; i++) {
            PartyHudModel.Step step = steps.get(i + 1);
            Chip chip = take(old, step.key);
            measure(chip, step, font, false);
            if (step.kind == StepKind.ROLL) {
                // Faces, not banners: clear of the notch of the banner before the first one
                chip.before = firstRoll ? BIG_DEPTH + 1 : 0;
                firstRoll = false;
            }
            fullWidths[i] = chip.before + advance(chip, chip.fullWidth, true);
            smallWidths[i] = chip.before + advance(chip, chip.smallWidth, false);
            next.add(chip);
        }

        // The current step: its name (a turn: the token's) takes what the two next steps in full leave
        int base = first.head() ? CURRENT_TURN : BIG_ICON;
        int room = maxContent - (base - BIG_DEPTH + 1) - TAIL;
        int wanted = first.head() ? Math.min(first.textWidth, NAME_MAX) : first.textWidth;
        int two = (upcoming > 0 ? fullWidths[0] : 0) + (upcoming > 1 ? fullWidths[1] : 0);
        int textRoom = MathHelper.clamp(room - two - NAME_ROOM, 0, wanted);
        if (textRoom < wanted && (!first.head() || textRoom < 16)) textRoom = 0;
        if (first.head() && textRoom > 0 && textRoom < first.textWidth) {
            first.text = HudDraw.fit(Text.literal(first.player.name), textRoom);
            textRoom = font.getWidth(first.text);
        }
        first.captioned = textRoom > 0;
        first.mode = CURRENT;
        first.x = 0;
        first.w = base + (textRoom > 0 ? textRoom + (first.head() ? NAME_ROOM : CAPTION_ROOM) : 0);
        first.fade = 1;
        room -= first.w - base;

        // The steps to come: in full, then small, then « +N »
        int beyond = model.stripMore;
        PartyStrip.Fit fit = PartyStrip.fit(java.util.Arrays.copyOf(fullWidths, upcoming), java.util.Arrays.copyOf(smallWidths, upcoming),
                room, beyond, hidden -> moreWidth(font, hidden));
        int used = fit.hidden() > 0 ? moreWidth(font, fit.hidden()) : 0;
        for (int i = 0; i < fit.shown(); i++) used += i < fit.full() ? fullWidths[i] : smallWidths[i];
        int cursor = first.w - BIG_DEPTH + 1;
        int end = first.w;
        for (int i = 0; i < fit.shown(); i++) {
            Chip chip = next.get(i + 1);
            boolean full = i < fit.full();
            chip.mode = full ? FULL : SMALL_MODE;
            // The name of a step in full (« Mini-jeu »), while there is room left
            chip.captioned = full && !chip.head() && chip.text != null && used + chip.textWidth + CAPTION_ROOM <= room;
            if (chip.captioned) used += chip.textWidth + CAPTION_ROOM;
            chip.w = full ? chip.fullWidth + (chip.captioned ? chip.textWidth + CAPTION_ROOM : 0) : chip.smallWidth;
            chip.x = cursor + chip.before;
            // Small ones fade towards the end of the strip (the players rolling for the turn order: never)
            int small = fit.compact(), index = i - fit.full();
            chip.fade = full || chip.step.kind == StepKind.ROLL ? 1
                    : MathHelper.lerp(small > 1 ? index / (float) (small - 1) : 0, 0.9f, fit.hidden() > 0 ? 0.4f : 0.6f);
            cursor = chip.x + advance(chip, chip.w, full);
            end = chip.x + chip.w;
        }
        // The steps that do not fit keep no banner
        for (int i = next.size() - 1; i > fit.shown(); i--) old.add(next.remove(i));
        if (fit.hidden() > 0 && used <= room) {
            more = "+" + fit.hidden();
            moreX = cursor + 3;
            end = moreX + font.getWidth(more);
        }

        // New banners come in from the right, the others glide from where they are
        for (Chip chip : next) {
            if (!chip.placed) {
                chip.placed = true;
                chip.shownX = snap ? chip.x : chip.x + 8;
                chip.shownW = chip.w;
                chip.shownAlpha = snap ? chip.fade : 0;
                chip.grow = chip.mode >= FULL ? 1 : 0;
                chip.gold = chip.mode == CURRENT ? 1 : 0;
            }
            bump(chip, now);
        }
        leave(old);
        chips = next;
        previousChips = old;
        return end;
    }

    private static int moreWidth(TextRenderer font, int hidden) {
        return font.getWidth("+" + hidden) + 4;
    }

    /** How far the banner after this one starts: its point goes into this one's notch. */
    private static int advance(Chip chip, int width, boolean big) {
        return chip.sprite() ? width - (big ? BIG_DEPTH : SMALL_DEPTH) + 1 : width + 2;
    }

    /** The banner of a key among the ones of the layout before (taken out of them), else a new one. */
    private static Chip take(List<Chip> old, int key) {
        for (int i = 0; i < old.size(); i++) {
            if (old.get(i).key == key) return old.remove(i);
        }
        return new Chip();
    }

    /** The banners left over fade out where they are. */
    private void leave(List<Chip> old) {
        for (Chip chip : old) {
            if (chip.placed && chip.shownAlpha > 0.03f) leaving.add(chip);
        }
        old.clear();
    }

    /** What a banner shows, and the width it takes in full and small. */
    private void measure(Chip chip, PartyHudModel.Step step, TextRenderer font, boolean current) {
        chip.key = step.key;
        chip.step = step;
        chip.player = step.player >= 0 ? model.players.get(step.player) : null;
        chip.before = 0;
        chip.label = null;
        chip.text = null;
        chip.textWidth = 0;
        chip.rank = null;
        if (step.round > 0 && !current) {
            chip.label = Text.translatable("hud.steveparty.party.strip.round", step.round).getString();
            chip.labelWidth = font.getWidth(chip.label);
            chip.before = chip.labelWidth + 4;
        }
        PartyHudModel.Player player = chip.player;
        if (step.kind == StepKind.ROLL) {
            chip.fullWidth = FACE;
            chip.smallWidth = HEAD;
        } else if (chip.head()) {
            chip.tint = player.mine ? TINT_MINE : TINT_TURN;
            chip.textWidth = font.getWidth(player.name);
            chip.text = Text.literal(player.name).asOrderedText();
            if (model.hasStandings) {
                chip.rank = Integer.toString(player.rank);
                chip.rankWidth = font.getWidth(chip.rank) + 3;
                chip.rankColor = RANK_COLORS[Math.min(player.rank, RANK_COLORS.length) - 1];
            }
            chip.stars = Integer.toString(player.stars);
            chip.coins = Integer.toString(player.coins);
            chip.countersX = Math.max(18, chip.rank == null ? 0 : 14 + chip.rankWidth) + 2;
            chip.fullWidth = chip.countersX + 9 + Math.max(font.getWidth(chip.stars), font.getWidth(chip.coins)) - 1 + 7;
            chip.smallWidth = SMALL_TURN;
        } else {
            String caption = switch (step.kind) {
                case START_ROLLS -> "hud.steveparty.party.round.start";
                case PREPARING -> "hud.steveparty.party.strip.preparing";
                case TURN, TURNS -> "hud.steveparty.party.strip.turns";
                case MINI_GAME -> "hud.steveparty.party.strip.mini_game";
                case EVENT -> "hud.steveparty.party.strip.event";
                case END -> "hud.steveparty.party.strip.end";
                default -> "hud.steveparty.party.strip.other";
            };
            chip.icon = switch (step.kind) {
                case START_ROLLS -> HudDraw.ICON_DICE;
                case TURN, TURNS -> HudDraw.ICON_STEPS;
                case MINI_GAME -> HudDraw.ICON_MINI_GAME;
                case EVENT -> HudDraw.ICON_CLOCK;
                case END -> HudDraw.ICON_CROWN;
                default -> HudDraw.ICON_PREPARING;
            };
            chip.tint = switch (step.kind) {
                case START_ROLLS, PREPARING -> TINT_START;
                case MINI_GAME -> TINT_MINI_GAME;
                case EVENT -> TINT_EVENT;
                case END -> TINT_END;
                default -> TINT_TURN;
            };
            Text text = Text.translatable(caption);
            chip.text = text.asOrderedText();
            chip.textWidth = font.getWidth(text);
            chip.fullWidth = BIG_ICON;
            chip.smallWidth = SMALL_ICON;
        }
    }

    /** The stars, coins or rank of a banner changed: they pop. */
    private static void bump(Chip chip, double now) {
        PartyHudModel.Player player = chip.player;
        if (player == null) {
            chip.counted = false;
            return;
        }
        if (chip.counted && (player.stars != chip.lastStars || player.coins != chip.lastCoins || player.rank != chip.lastRank))
            chip.bumpAt = now;
        chip.counted = true;
        chip.lastStars = player.stars;
        chip.lastCoins = player.coins;
        chip.lastRank = player.rank;
    }

    /** @return whether the text had to be cut */
    private boolean layoutLine(ActionLine line, int maxContent) {
        TextRenderer font = HudDraw.font();
        line.icon = model.actionIcon;
        line.replay = model.replay && model.stepType == PartyStepType.TOKEN_TURN;
        line.replayText = Text.translatable("hud.steveparty.party.replay").asOrderedText();
        line.replayWidth = line.replay ? font.getWidth(line.replayText) + HudDraw.ICON + 9 : 0;
        line.badge = model.badge;
        line.badgeWidth = model.badge == null ? 0 : Math.max(ROW_B, font.getWidth(model.badge) + 7);
        line.warn = model.warn;
        int fixed = (line.replay ? line.replayWidth + 4 : 0) + HudDraw.ICON + 3 + (line.badge != null ? line.badgeWidth + 4 : 0);
        int textRoom = Math.max(20, maxContent - fixed);
        line.text = HudDraw.fit(model.actionText, textRoom);
        line.textWidth = font.getWidth(line.text);
        line.width = fixed + line.textWidth;
        return font.getWidth(model.actionText) > textRoom;
    }

    /** Draws the bar with its top-left corner at (0, 0) of the current matrices. */
    void draw(DrawContext context, float alpha, double now) {
        if (model == null || alpha <= 0.02f) return;
        float delta = (float) MathHelper.clamp(now - lastFrame, 0, 5);
        lastFrame = now;
        animate(delta);

        int w = Math.round(shownWidth);
        HudDraw.plate(context, Plate.TEAL, 0, 0, w, HEIGHT, alpha);

        // The strip: the steps that left, the ones to come, « +N », and the current one over them (it pops)
        for (int i = 0; i < leaving.size(); i++) drawChip(context, leaving.get(i), alpha, now);
        for (int i = chips.size() - 1; i > 0; i--) drawChip(context, chips.get(i), alpha, now);
        if (more != null) HudDraw.text(context, more, PAD + Math.round(shownMoreX), PAD + 6, HudDraw.TEXT_SOFT, alpha);
        if (!chips.isEmpty()) drawCurrent(context, chips.getFirst(), alpha, now);

        // Round
        int lineY = PAD + ROW_A + ROW_GAP;
        int lineLeft = PAD;
        if (roundWidth > 0) {
            HudDraw.plate(context, Plate.PURPLE, PAD, lineY, roundWidth, ROW_B, alpha);
            HudDraw.text(context, roundText, PAD + 5, lineY + 3, HudDraw.TEXT, alpha);
            lineLeft += roundWidth + 4;
        }

        // Action line (cross-fading when it changed)
        float t = (float) ((now - actionChangedAt) / CROSSFADE_TICKS);
        if (t < 1) {
            float out = 1 - HudDraw.easeOutCubic(t);
            drawLine(context, previousLine, lineLeft, w, lineY - Math.round((1 - out) * 4), alpha * out, now);
            drawLine(context, line, lineLeft, w, lineY + Math.round(out * 4), alpha * HudDraw.easeOutCubic(t), now);
        } else {
            previousLine.copyFrom(line);
            drawLine(context, line, lineLeft, w, lineY, alpha, now);
        }
    }

    /** The step being played: its banner pops when the step changes, a marker bobs under it. */
    private void drawCurrent(DrawContext context, Chip chip, float alpha, double now) {
        float x = PAD + chip.shownX;
        MatrixStack matrices = context.getMatrices();
        float pop = (float) ((now - turnChangedAt) / TURN_POP_TICKS);
        float scale = pop < 1 ? 1 + 0.3f * (1 - HudDraw.easeOutBack(pop)) : 1;
        matrices.push();
        matrices.translate(x + chip.shownW / 2, PAD + ROW_A / 2f, 0);
        matrices.scale(scale, scale, 1);
        matrices.translate(-(x + chip.shownW / 2), -(PAD + ROW_A / 2f), 0);
        drawChip(context, chip, alpha, now);
        matrices.pop();
        // The marker bobs under the banner: whole pixels, a slow pulse
        int bob = Math.round((float) Math.sin(now * Math.PI / 12) * 1.2f);
        float middle = chip.head() ? 5 + FACE / 2f : 6 + HudDraw.ICON / 2f;
        context.drawGuiTexture(RenderLayer::getGuiTextured, HudDraw.ICON_MARKER,
                Math.round(x + middle - 3.5f), PAD + ROW_A - 1 + bob, 7, 5, HudDraw.white(alpha * chip.shownAlpha));
    }

    private void drawChip(DrawContext context, Chip chip, float alpha, double now) {
        float a = alpha * chip.shownAlpha;
        if (a <= 0.02f) return;
        int x = PAD + Math.round(chip.shownX);
        float grow = chip.grow, gold = chip.gold;
        if (chip.label != null) HudDraw.text(context, chip.label, x - chip.before + 2, PAD + 6, ROUND_TEXT, a);
        PartyHudModel.Player player = chip.player;

        if (!chip.sprite()) {
            // A player rolling for the turn order: its face, and its roll once made
            int size = Math.round(HEAD + (FACE - HEAD) * grow);
            int y = PAD + (ROW_A - size) / 2;
            HudDraw.face(context, player.owner, player.name, player.color, x, y, size, a, false);
            if (player.startRoll > 0) rollTag(context, player.startRoll, x + size - 5, y + size - 5, a);
            return;
        }

        boolean big = grow > 0.5f;
        int height = big ? BIG : SMALL;
        int tint = gold > 0.01f && chip.head() ? ColorHelper.lerp(gold, chip.tint, TINT_CURRENT) : chip.tint;
        context.drawGuiTexture(RenderLayer::getGuiTextured, big ? ARROW_BIG : ARROW_SMALL, x, PAD + (ROW_A - height) / 2,
                Math.max(Math.round(chip.shownW), height), height, HudDraw.fade(tint, a));
        // What a step in full adds shows in the second half of its growth
        float full = HudDraw.clamp01(grow * 2 - 1);
        float current = HudDraw.clamp01(gold * 2 - 1);

        if (!chip.head()) {
            HudDraw.icon(context, chip.icon, x + (big ? 6 : 5), PAD + 5, a);
            if (chip.captioned && chip.text != null && full > 0.02f) HudDraw.text(context, chip.text, x + 17, PAD + 6, HudDraw.TEXT, a * full);
            return;
        }

        // A turn: the head (the face of the current turn), then its rank, stars and coins, or its name
        int size = Math.round(HEAD + (FACE - HEAD) * gold);
        int faceX = x + Math.round(4 + 2 * grow - gold);
        int faceY = PAD + Math.round(4 - 3 * grow + gold);
        HudDraw.face(context, player.owner, player.name, player.color, faceX, faceY, size, a, false);
        if (current > 0.02f && chip.captioned && chip.text != null)
            HudDraw.text(context, chip.text, x + 24, PAD + 6, player.mine ? HudDraw.TEXT_MINE : HudDraw.TEXT, a * current);
        float counters = a * full * (1 - current);
        if (counters <= 0.02f) return;
        MatrixStack matrices = context.getMatrices();
        float bump = (float) ((now - chip.bumpAt) / BUMP_TICKS);
        float scale = bump < 1 ? 1 + 0.5f * (1 - HudDraw.easeOutBack(bump)) : 1;
        if (chip.rank != null) {
            // The rank, on a medal over the corner of the head
            int medalX = x + 14, medalY = PAD + 9;
            matrices.push();
            // In front of the head
            matrices.translate(medalX + chip.rankWidth / 2f, medalY + 4.5f, 10);
            matrices.scale(scale, scale, 1);
            matrices.translate(-chip.rankWidth / 2f, -4.5f, 0);
            context.fill(0, 0, chip.rankWidth, 9, HudDraw.fade(HudDraw.OUTLINE, counters));
            context.fill(1, 1, chip.rankWidth - 1, 8, HudDraw.fade(chip.rankColor, counters));
            HudDraw.text(context, chip.rank, 2, 1, HudDraw.TEXT, counters);
            matrices.pop();
        }
        int countersX = x + chip.countersX;
        drawCounter(context, model.starItem, chip.stars, countersX, PAD + 2, counters, scale);
        drawCounter(context, model.coinItem, chip.coins, countersX, PAD + 10, counters, scale);
    }

    /** A currency's item (8 px) and its number, which pops when it changes. */
    private static void drawCounter(DrawContext context, ItemStack icon, String text, int x, int y, float alpha, float scale) {
        MatrixStack matrices = context.getMatrices();
        if (alpha > 0.6f && !icon.isEmpty()) {
            matrices.push();
            matrices.translate(x + 4, y + 4, 0);
            matrices.scale(0.5f, 0.5f, 1);
            context.drawItem(icon, -8, -8);
            matrices.pop();
        }
        if (scale == 1) {
            HudDraw.text(context, text, x + 9, y, HudDraw.TEXT, alpha);
            return;
        }
        int textWidth = HudDraw.font().getWidth(text);
        matrices.push();
        // In front of the item (drawn at a depth of its own)
        matrices.translate(x + 9 + textWidth / 2f, y + 4, 200);
        matrices.scale(scale, scale, 1);
        HudDraw.text(context, text, -textWidth / 2, -4, HudDraw.TEXT, alpha);
        matrices.pop();
    }

    private void rollTag(DrawContext context, int roll, int x, int y, float alpha) {
        String text = Integer.toString(roll);
        int width = HudDraw.font().getWidth(text) + 4;
        HudDraw.plate(context, Plate.GOLD, x, y, Math.max(8, width), 10, alpha);
        HudDraw.text(context, text, x + Math.max(8, width) / 2 - HudDraw.font().getWidth(text) / 2, y + 1, HudDraw.TEXT, alpha);
    }

    /** The action line, centred in the bar when the round leaves it the room, else after the round. */
    private void drawLine(DrawContext context, ActionLine line, int left, int width, int y, float alpha, double now) {
        if (line.text == null || alpha <= 0.02f) return;
        int x = Math.max(left, (width - line.width) / 2);
        if (line.replay) {
            HudDraw.plate(context, Plate.GREEN, x, y, line.replayWidth, ROW_B, alpha);
            HudDraw.icon(context, HudDraw.ICON_REPLAY, x + 3, y + 2, alpha);
            HudDraw.text(context, line.replayText, x + 5 + HudDraw.ICON, y + 3, HudDraw.TEXT, alpha);
            x += line.replayWidth + 4;
        }
        HudDraw.icon(context, line.icon, x, y + 2, alpha);
        x += HudDraw.ICON + 3;
        HudDraw.text(context, line.text, x, y + 3, line.warn ? HudDraw.TEXT_WARN : HudDraw.TEXT, alpha);
        x += line.textWidth + 4;
        if (line.badge != null) {
            MatrixStack matrices = context.getMatrices();
            float pop = line == this.line ? (float) ((now - badgeChangedAt) / BADGE_POP_TICKS) : 1;
            float scale = pop < 1 ? 1 + 0.45f * (1 - HudDraw.easeOutBack(pop)) : 1;
            matrices.push();
            matrices.translate(x + line.badgeWidth / 2f, y + ROW_B / 2f, 0);
            matrices.scale(scale, scale, 1);
            matrices.translate(-line.badgeWidth / 2f, -ROW_B / 2f, 0);
            HudDraw.plate(context, line.warn ? Plate.RED : Plate.GOLD, 0, 0, line.badgeWidth, ROW_B, alpha);
            HudDraw.text(context, line.badge, (line.badgeWidth - HudDraw.font().getWidth(line.badge)) / 2 + 1, 3, HudDraw.TEXT, alpha);
            matrices.pop();
        }
    }

    /**
     * Eases what is shown towards the layout: the banners glide to their place, grow, shrink, turn gold and fade, the
     * ones that left fade out, the bar grows or shrinks.
     */
    private void animate(float delta) {
        for (int i = 0; i < chips.size(); i++) {
            Chip chip = chips.get(i);
            chip.shownX = HudDraw.approach(chip.shownX, chip.x, GLIDE, delta);
            chip.shownW = HudDraw.approach(chip.shownW, chip.w, GLIDE, delta);
            chip.shownAlpha = HudDraw.approach(chip.shownAlpha, chip.fade, GLIDE, delta);
            chip.grow = HudDraw.approach(chip.grow, chip.mode >= FULL ? 1 : 0, GLIDE, delta);
            chip.gold = HudDraw.approach(chip.gold, chip.mode == CURRENT ? 1 : 0, GLIDE, delta);
        }
        for (int i = leaving.size() - 1; i >= 0; i--) {
            Chip chip = leaving.get(i);
            chip.shownAlpha = HudDraw.approach(chip.shownAlpha, 0, 0.7f, delta);
            if (chip.shownAlpha <= 0.03f) {
                chip.placed = false;
                leaving.remove(i);
            }
        }
        shownMoreX = shownMoreX < 0 ? moreX : HudDraw.approach(shownMoreX, moreX, GLIDE, delta);
        shownWidth = shownWidth < 0 ? width : HudDraw.approach(shownWidth, width, GLIDE, delta);
    }
}
