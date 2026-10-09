package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.hud.HudShapes;
import fr.lordfinn.steveparty.hud.HudTexts;
import fr.lordfinn.steveparty.hud.StandingsLayout;
import fr.lordfinn.steveparty.hud.TurnStripLayout;
import fr.lordfinn.steveparty.hud.TurnStripLayout.El;
import fr.lordfinn.steveparty.hud.TurnStripLayout.Kind;
import fr.lordfinn.steveparty.hud.TurnStripLayout.Step;
import fr.lordfinn.steveparty.hud.TurnStripLayout.Type;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The party HUDs' layouts (the approved mock-ups « 7E v4 » and « S5 »), pure geometry: the strip's rules (the next
 * mini-game always visible, « toi » under my chips, « toi dans N » under « +N », the big chip's fixed name width, one
 * axis, no overlap) and the standings' (the collapse past eight players, ties, the names' column).
 */
public class PartyHudLayoutGameTests implements FabricGameTest {
    /** A font of 5 px glyphs and 1 px apart: « Tom » is 17 px, like the game's. */
    private static final HudTexts TEXTS = new HudTexts() {
        @Override
        public int width(String text) {
            return text.isEmpty() ? 0 : 6 * text.codePointCount(0, text.length()) - 1;
        }

        @Override
        public String step(Kind kind) {
            return switch (kind) {
                case START -> "Départ";
                case MINI_GAME -> "Mini-jeu";
                case EVENT -> "Événement";
                default -> "Étape";
            };
        }

        @Override
        public String pinned(int steps) {
            return "dans " + steps;
        }

        @Override
        public String toi() {
            return "toi";
        }

        @Override
        public String toiNow() {
            return "à toi !";
        }

        @Override
        public String toiIn(int steps) {
            return "toi dans " + steps;
        }
    };

    private static final List<String> NAMES = List.of("Nora", "Tom", "Lina", "Max", "Iris", "Léo", "Sam", "Zoé");

    /** A party of {@code players} from {@code round} on, the turn of {@code from} first: turns then a mini-game per round. */
    private static List<Step> party(int players, int round, int rounds, int from) {
        List<Step> steps = new ArrayList<>();
        int key = 10000;
        for (int r = round; r <= rounds; r++) {
            for (int p = r == round ? from : 0; p < players; p++) steps.add(new Step(Kind.TURN, p, r, key--));
            steps.add(new Step(Kind.MINI_GAME, -1, r, key--));
        }
        steps.add(new Step(Kind.END, -1, 0, key));
        return steps;
    }

    private static TurnStripLayout.Result layout(List<Step> steps, int rounds, int me, List<String> names, int width) {
        return TurnStripLayout.layout(new TurnStripLayout.Input(steps, rounds, me, names, width), TEXTS);
    }

    private static List<El> strip(TurnStripLayout.Result result) {
        return result.elements().stream().filter(el -> el.type != Type.BUBBLE && el.type != Type.MARKER).toList();
    }

    private static boolean shows(TurnStripLayout.Result result, Step step) {
        return result.get("s" + step.key()) != null;
    }

    /** The strip's shapes are centred on one axis and never come closer than three empty pixels. */
    private static void checkGeometry(TestContext context, TurnStripLayout.Result result, String label) {
        El prev = null;
        for (El el : strip(result)) {
            int top = el.y + el.top(), bottom = el.y + el.bottom();
            context.assertEquals(top + bottom, 2 * TurnStripLayout.AXIS - 1, label + ": " + el.key + " centred on the axis");
            // (a round's medallion leads its round: 2 empty pixels after it, as in the mock-up)
            int tighter = prev != null && prev.type == Type.ROUND_DISC ? 1 : 0;
            if (prev != null)
                context.assertTrue(TurnStripLayout.gapX(prev, el, el.y, Integer.MIN_VALUE) - tighter <= el.x, label + ": " + el.key + " clear of " + prev.key);
            prev = el;
        }
        El before = null;
        for (El el : result.elements()) {
            if (el.type != Type.BUBBLE) continue;
            if (before != null) context.assertTrue(TurnStripLayout.gapX(before, el, el.y, Integer.MIN_VALUE) <= el.x, label + ": bubbles apart");
            for (El shape : strip(result))
                context.assertTrue(el.y + TurnStripLayout.POINTER + HudShapes.PAD > shape.y + shape.bottom(), label + ": " + el.key + " under the strip");
            before = el;
        }
    }

    /** B1: the next mini-game always shows: in its place when it fits, else pinned before « +N », « dans N ». */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void nextMiniGameIsAlwaysVisible(TestContext context) {
        List<Step> steps = party(8, 5, 15, 4);                     // Iris plays, then Léo, Sam, Zoé, the mini-game
        Step mini = steps.get(4);
        TurnStripLayout.Result narrow = layout(steps, 15, 6, NAMES, 150);
        context.assertTrue(!shows(narrow, mini), "narrow: the mini-game is not in its place");
        context.assertEquals(narrow.pinnedIn(), 3, "pinned: in 3 steps");
        El pinned = narrow.get("pinned"), more = narrow.get("more");
        context.assertTrue(pinned != null && pinned.label.equals("dans 3"), "« dans 3 »");
        context.assertTrue(more != null && pinned.x < more.x, "before « +N »");
        context.assertEquals(narrow.more(), 10, "« +N »: the 10 rounds left hidden");
        checkGeometry(context, narrow, "narrow");

        TurnStripLayout.Result wide = layout(steps, 15, 6, NAMES, 400);
        context.assertTrue(shows(wide, mini) && wide.get("pinned") == null && wide.pinnedIn() == -1, "wide: in its place, not pinned");
        context.assertTrue(strip(wide).size() <= TurnStripLayout.MAX_STEPS + 3, "at most seven steps (and their round medallions, « +N »)");
        checkGeometry(context, wide, "wide");
        context.complete();
    }

    /** B2, B3: « toi » under my next chip, « à toi ! » on my turn (no marker), « toi dans N » under « +N » when hidden. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void myBubbles(TestContext context) {
        List<Step> steps = party(4, 3, 12, 1);                     // Tom plays, Lina next
        TurnStripLayout.Result next = layout(steps, 12, 2, NAMES.subList(0, 4), 236);
        context.assertTrue(next.get("toi" + "s" + steps.get(1).key()) != null, "« toi » under Lina's chip");
        context.assertTrue(next.get("marker") != null && next.get("toi_now") == null && !next.mineNow(), "Tom plays: the marker over him");
        El toi = next.get("toi" + "s" + steps.get(1).key()), lina = next.get("s" + steps.get(1).key());
        context.assertEquals(toi.x + toi.pictureWidth() / 2, lina.x + lina.pictureWidth() / 2, "centred under her chip");
        checkGeometry(context, next, "next");

        // My turn, on a narrow bar: Lina, Max, then the mini-game pinned, « +N »
        TurnStripLayout.Result mine = layout(steps.subList(1, steps.size()), 12, 2, NAMES.subList(0, 4), 120);
        context.assertTrue(mine.mineNow() && mine.get("toi_now") != null && mine.get("marker") == null, "my turn: « à toi ! », no marker");
        context.assertTrue(mine.get("toi_now").label.equals("à toi !"), "its words");
        // My next turn is in the next round, hidden in « +N »: after Max, the mini-game, Nora and Tom
        context.assertEquals(mine.toiIn(), 4, "my next turn: in 4 steps");
        context.assertTrue(mine.get("toi_more") != null && mine.get("toi_more").label.equals("toi dans 4"), "« toi dans 4 » under « +N »");
        checkGeometry(context, mine, "mine");

        TurnStripLayout.Result spectator = layout(steps, 12, -1, NAMES.subList(0, 4), 236);
        context.assertTrue(spectator.elements().stream().noneMatch(el -> el.type == Type.BUBBLE), "a spectator: no bubble");
        context.complete();
    }

    /** I1, I5: the big chip's name has the width of the party's longest name (48 px at most, « … »): nothing shifts. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void bigChipHasAFixedNameWidth(TestContext context) {
        List<String> names = List.of("Al", "Bob", "Lina", "xX_Steve_Xx");
        context.assertEquals(TurnStripLayout.nameWidth(names, TEXTS), TurnStripLayout.NAME_MAX, "« xX_Steve_Xx » is wider than 48 px: 48");
        context.assertEquals(TurnStripLayout.nameWidth(List.of("Al", "Bob"), TEXTS), TEXTS.width("Bob"), "else the longest name");
        List<Step> steps = party(4, 1, 3, 0);
        El al = layout(steps, 3, -1, names, 300).elements().getFirst();
        El bob = layout(steps.subList(1, steps.size()), 3, -1, names, 300).elements().getFirst();
        El steve = layout(steps.subList(3, steps.size()), 3, -1, names, 300).elements().getFirst();
        context.assertTrue(al.w == bob.w && bob.w == steve.w, "the same width for every player");
        context.assertEquals(steve.label, TEXTS.fit("xX_Steve_Xx", 48), "a long name cut with « … »");
        context.assertTrue(steve.label.endsWith("…") && TEXTS.width(steve.label) <= 48, "within 48 px");
        // The next chip: its name (32 px at most), or its initial when narrow
        El nextWide = layout(steps, 3, -1, names, 300).elements().get(1);
        El nextNarrow = layout(steps, 3, -1, names, 172).elements().get(1);
        context.assertTrue(nextWide.label.equals("Bob") && nextNarrow.label.equals("B"), "« Bob », or « B » when narrow");
        context.complete();
    }

    /**
     * One « toi » only, under my next turn (not under each of my turns shown); none at all when every token is mine
     * (alone, or playing them all): only « à toi ! » on my turn.
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void oneToiBubbleAtMost(TestContext context) {
        List<Step> two = party(2, 1, 6, 0);                         // Nora, Tom, the mini-game, Nora, Tom...
        TurnStripLayout.Result wide = layout(two, 6, 1, NAMES.subList(0, 2), 600);
        long tois = wide.elements().stream().filter(el -> el.type == Type.BUBBLE && el.key.startsWith("toi") && !el.key.equals("toi_more")).count();
        long tomShown = wide.elements().stream().filter(el -> el.type == Type.PLAYER && el.step.player() == 1).count();
        context.assertTrue(tomShown >= 2, "several of my turns on the strip: " + tomShown);
        context.assertEquals(tois, 1L, "one « toi »");
        context.assertTrue(wide.get("toi" + "s" + two.get(1).key()) != null, "under my next turn");

        // Solo: every token is mine
        List<Step> solo = party(1, 1, 10, 0);
        TurnStripLayout.Result alone = layout(solo, 10, 0, NAMES.subList(0, 1), 400);
        context.assertTrue(alone.mineNow() && alone.get("toi_now") != null, "my turn: « à toi ! »");
        context.assertTrue(alone.elements().stream().noneMatch(el -> el.type == Type.BUBBLE && !el.key.equals("toi_now")), "no other bubble");
        TurnStripLayout.Result aloneMini = layout(solo.subList(1, solo.size()), 10, 0, NAMES.subList(0, 1), 120);
        context.assertTrue(aloneMini.elements().stream().noneMatch(el -> el.type == Type.BUBBLE), "the mini-game being played: no bubble at all");
        // Playing all the tokens of the party
        TurnStripLayout.Result all = TurnStripLayout.layout(new TurnStripLayout.Input(two.subList(1, two.size()), 6, Set.of(0, 1),
                NAMES.subList(0, 2), 600), TEXTS);
        context.assertTrue(all.elements().stream().noneMatch(el -> el.type == Type.BUBBLE && !el.key.equals("toi_now")), "all mine: « à toi ! » only");
        context.complete();
    }

    /** The bar's frame hugs what it draws: the marker's row only when it is shown, the bubbles' only when there are some. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void barHugsWhatItDraws(TestContext context) {
        List<Step> steps = party(4, 3, 12, 1);                     // Tom plays
        TurnStripLayout.Result spectator = layout(steps, 12, -1, NAMES.subList(0, 4), 236);
        El marker = spectator.get("marker");
        int[] drawn = spectator.drawn();
        context.assertEquals(drawn[1], marker.y + HudShapes.PAD, "the top: the marker's first row");
        int stripBottom = Integer.MIN_VALUE;
        for (El el : strip(spectator)) stripBottom = Math.max(stripBottom, el.y + el.bottom());
        context.assertEquals(drawn[3], stripBottom + 2, "no bubble: the bottom is the strip's drop shadow");

        TurnStripLayout.Result mine = layout(steps.subList(1, steps.size()), 12, 2, NAMES.subList(0, 4), 236);
        El big = mine.elements().getFirst();
        context.assertEquals(mine.drawn()[1], big.y + big.top() - 1, "my turn, no marker: the top is the big chip's halo");
        El bubble = mine.get("toi_now");
        context.assertEquals(mine.drawn()[3], bubble.y + bubble.bottom() + 2, "« à toi ! »: down to its drop shadow");
        context.assertTrue(mine.drawn()[1] > drawn[1], "less room over the strip without the marker");
        context.complete();
    }

    /** Before the turn order: « Départ » big, then the rounds as capsules, « +N » for the others. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void startShowsTheRounds(TestContext context) {
        List<Step> steps = new ArrayList<>();
        steps.add(new Step(Kind.START, -1, 0, 1));
        for (int r = 1; r <= 17; r++) steps.add(new Step(Kind.CAPSULE, -1, r, -r));
        TurnStripLayout.Result result = layout(steps, 17, 1, NAMES.subList(0, 4), 236);
        El first = result.elements().getFirst();
        context.assertTrue(first.type == Type.BIG_STEP && first.label.equals("Départ") && first.halo, "« Départ », big, its halo");
        long capsules = result.elements().stream().filter(el -> el.type == Type.CAPSULE).count();
        context.assertTrue(capsules >= 2, "rounds as capsules: " + capsules);
        context.assertEquals(result.more(), 17 - (int) capsules, "« +N »: the rounds not shown");
        checkGeometry(context, result, "start");
        context.complete();
    }

    private static List<StandingsLayout.Entry> ranked(int n, int me) {
        List<StandingsLayout.Entry> entries = new ArrayList<>();
        for (int i = 0; i < n; i++) entries.add(new StandingsLayout.Entry(i, i + 1, "P" + i, n - i, 0, 0, i == me));
        return entries;
    }

    private static List<Integer> shown(List<StandingsLayout.Row> rows) {
        List<Integer> out = new ArrayList<>();
        for (StandingsLayout.Row row : rows) out.add(row.gap() ? -row.hidden() : row.entry().index());
        return out;
    }

    /** Past 8 players: the first three, « … N », me between my neighbours, « … N », the last one. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void standingsCollapsePastEight(TestContext context) {
        context.assertEquals(shown(StandingsLayout.collapse(ranked(8, 6))), List.of(0, 1, 2, 3, 4, 5, 6, 7), "8 players: all of them");
        context.assertEquals(shown(StandingsLayout.collapse(ranked(16, 8))), List.of(0, 1, 2, -4, 7, 8, 9, -5, 15),
                "16 players, me 9th: top 3, « … 4 », 8th to 10th, « … 5 », the last");
        context.assertEquals(shown(StandingsLayout.collapse(ranked(16, 3))), List.of(0, 1, 2, 3, 4, -10, 15), "me 4th: next to the top 3");
        context.assertEquals(shown(StandingsLayout.collapse(ranked(16, 15))), List.of(0, 1, 2, -11, 14, 15), "me last");
        context.assertEquals(shown(StandingsLayout.collapse(ranked(16, -1))), List.of(0, 1, 2, -12, 15), "a spectator: top 3 and the last");
        context.complete();
    }

    /**
     * The names' column is as wide as the longest name shown (not of the hidden ones), 64 px at most; ties share their
     * rank (1, 1, 3: the coins break the stars' ties).
     */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void standingsColumnsAndTies(TestContext context) {
        List<StandingsLayout.Entry> entries = new ArrayList<>(ranked(16, 8));
        // A long name in a hidden row: it does not widen the column
        entries.set(5, new StandingsLayout.Entry(5, 6, "Un-nom-vraiment-très-long", 0, 0, 0, false));
        StandingsLayout.Layout layout = StandingsLayout.layout(entries, TEXTS);
        context.assertEquals(layout.nameWidth(), TEXTS.width("P10"), "the longest name shown");
        entries.set(8, new StandingsLayout.Entry(8, 9, "Un-nom-vraiment-très-long", 0, 0, 0, true));
        context.assertEquals(StandingsLayout.layout(entries, TEXTS).nameWidth(), StandingsLayout.NAME_CAP, "a long name shown: 64 px at most");
        context.assertTrue(!layout.bonuses(), "nobody has bonuses: no column");
        entries.set(0, new StandingsLayout.Entry(0, 1, "P0", 3, 42, 2, false));
        StandingsLayout.Layout withBonuses = StandingsLayout.layout(entries, TEXTS);
        context.assertTrue(withBonuses.bonuses() && withBonuses.plateWidth() > layout.plateWidth(), "someone has bonuses: the column shows");
        context.assertEquals(layout.coinColumn() - layout.starColumn(), TEXTS.width("888") + 6, "three-digit columns");
        // One axis per row: rank medallion, head, digits (8 rows with their shadow) and slots centred on the row
        int h = StandingsLayout.ROW_H;
        context.assertEquals(1 + StandingsLayout.BADGE / 2f, h / 2f, "the rank medallion centred");
        context.assertEquals((h - StandingsLayout.HEAD) / 2 + StandingsLayout.HEAD / 2f, h / 2f, "the head centred");
        context.assertEquals((h - 8) / 2 + 4f, h / 2f, "the digits centred");
        context.assertEquals((h - StandingsLayout.SLOT) / 2 + StandingsLayout.SLOT / 2f, h / 2f, "the bonus slots centred");

        List<PartyLiveData.Standing> tie = new ArrayList<>();
        int[][] values = {{3, 42}, {3, 25}, {3, 42}, {0, 31}};
        for (int[] v : values) tie.add(new PartyLiveData.Standing(UUID.randomUUID(), "", Optional.empty(), "", -1, true, v[0], v[1], List.of()));
        context.assertTrue(Arrays.equals(PartyLiveData.ranks(tie), new int[]{1, 3, 1, 4}), "1, 1, 3: the coins break the stars' ties");
        context.complete();
    }
}
