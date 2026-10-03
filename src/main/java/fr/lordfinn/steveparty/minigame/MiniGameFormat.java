package fr.lordfinn.steveparty.minigame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A way a mini-game can be played, a page having one or more ({@link MiniGamePageData#formats()}):
 * <ul>
 *     <li>{@link Kind#TEAMS}: 2 to 4 teams, each « from {@code min} to {@code max} » players ({@code max} may be
 *     {@link Side#INFINITE}), and optionally « same size » (every team as big as the others);</li>
 *     <li>{@link Kind#FREE_FOR_ALL}: everyone for themselves, from {@code min} to {@code max} players;</li>
 *     <li>{@link Kind#ALL_TOGETHER}: everyone on the same side (against the mini-game), from {@code min} to {@code max}.</li>
 * </ul>
 * Its name (« 2 contre 2 », « Duel », « 1 contre tous », « Chacun pour soi · 2 à 8 »...) and its pawn pictogram are
 * generated from it: a format written by hand looks as finished as a ready-made one.
 * <p>
 * <b>Its sides are ordered</b>: side 1 is team A (its players come out of the blue team A pipes), side 2 team B (red),
 * side 3 team C (purple), side 4 team D (orange). « 1 contre 3 » is 1 player out of the A pipes, 3 out of the B pipes.
 * <p>
 * <b>Matching a party</b> ({@link #matches}, {@link #assignment}): the board makes groups (the positive tiles' players,
 * the negative ones'...); they fit a format in any order, then are given to its sides by their sizes: board 3 positive
 * / 1 negative and « 1 contre 3 »: the negative player plays side A (blue pipes), the 3 positive ones side B. When the
 * board's order (A positive, B negative...) already fits it is kept; otherwise the first order that fits, trying the
 * groups for side A first, then B... in the board's order (deterministic). A composition with a single group (everyone
 * on the same kind of tile, or free for all) fits the free-for-all and all-together formats; one with 2 to 4 groups
 * fits the team formats with as many sides. When several formats of a page fit, the most specific is the one played
 * ({@link #specificity}: the narrower its ranges, the more specific; the first in the page's list when equal).
 * <p>
 * <b>Out of a party</b> (« Jouer » / « Tester ») the players near each colour of pipe are already in order: they must
 * fit in order ({@link #matchesInOrder}).
 */
public record MiniGameFormat(Kind kind, List<Side> sides, boolean sameSize) {
    public static final int MIN_SIDES = 2, MAX_SIDES = 4;
    /** The most players a side counts before « or more » ({@link Side#INFINITE}). */
    public static final int MAX_COUNT = 16;

    public enum Kind {
        TEAMS, FREE_FOR_ALL, ALL_TOGETHER;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A team, or the players of a free-for-all: from {@code min} to {@code max} players. */
    public record Side(int min, int max) {
        public static final int INFINITE = Integer.MAX_VALUE;

        public Side {
            min = Math.max(1, Math.min(MAX_COUNT, min));
            max = max == INFINITE || max > MAX_COUNT ? INFINITE : Math.max(min, max);
        }

        public static Side exactly(int count) {
            return new Side(count, count);
        }

        public static Side atLeast(int count) {
            return new Side(count, INFINITE);
        }

        public boolean infinite() {
            return max == INFINITE;
        }

        public boolean contains(int count) {
            return count >= min && count <= max;
        }

        /** How far {@code count} is from the range (0 inside). */
        public int distance(int count) {
            return count < min ? min - count : count > max ? count - max : 0;
        }

        /** « 2 », « 2+ », « 2-4 ». */
        public String label() {
            return min == max ? Integer.toString(min) : infinite() ? min + "+" : min + "-" + max;
        }

        /** « 2 », « 2 à 4 », « 2 ou plus » / « 2 or more ». */
        public Text rangeText() {
            if (min == max) return Text.literal(Integer.toString(min));
            if (infinite()) return Text.translatable("format.steveparty.range.more", min);
            return Text.translatable("format.steveparty.range", min, max);
        }
    }

    /** Cleans whatever it is given: 2 to 4 teams, a single side without teams. */
    public MiniGameFormat {
        if (kind == null) kind = Kind.TEAMS;
        List<Side> clean = new ArrayList<>(sides == null ? List.of() : sides);
        clean.removeIf(java.util.Objects::isNull);
        if (kind == Kind.TEAMS) {
            while (clean.size() < MIN_SIDES) clean.add(Side.atLeast(1));
            while (clean.size() > MAX_SIDES) clean.removeLast();
        } else {
            Side side = clean.isEmpty() ? Side.atLeast(1) : clean.getFirst();
            clean = List.of(side);
            sameSize = false;
        }
        sides = List.copyOf(clean);
    }

    // ------------------------------------------------------------------ ready-made formats

    public static MiniGameFormat teams(boolean sameSize, Side... sides) {
        return new MiniGameFormat(Kind.TEAMS, List.of(sides), sameSize);
    }

    public static MiniGameFormat freeForAll(int min, int max) {
        return new MiniGameFormat(Kind.FREE_FOR_ALL, List.of(new Side(min, max)), false);
    }

    public static MiniGameFormat allTogether(int min, int max) {
        return new MiniGameFormat(Kind.ALL_TOGETHER, List.of(new Side(min, max)), false);
    }

    /** « Format vide »: two teams of any size. */
    public static MiniGameFormat blank() {
        return teams(false, Side.atLeast(1), Side.atLeast(1));
    }

    /** The gallery of the editor's « + »: the formats most mini-games use, then {@link #blank}. */
    public static final List<MiniGameFormat> GALLERY = List.of(
            teams(false, Side.exactly(1), Side.exactly(1)),
            teams(false, Side.exactly(1), Side.exactly(3)),
            teams(false, Side.exactly(2), Side.exactly(2)),
            teams(false, Side.exactly(1), Side.atLeast(1)),
            teams(true, Side.atLeast(2), Side.atLeast(2)),
            teams(false, Side.atLeast(1), Side.atLeast(1), Side.atLeast(1)),
            teams(false, Side.atLeast(1), Side.atLeast(1), Side.atLeast(1), Side.atLeast(1)),
            freeForAll(2, 8),
            allTogether(2, 4));

    // ------------------------------------------------------------------ editing

    public MiniGameFormat withKind(Kind newKind) {
        if (newKind == kind) return this;
        if (newKind == Kind.TEAMS) return blank();
        return new MiniGameFormat(newKind, List.of(kind == Kind.TEAMS ? Side.atLeast(2) : sides.getFirst()), false);
    }

    public MiniGameFormat withSide(int index, Side side) {
        List<Side> list = new ArrayList<>(sides);
        list.set(index, side);
        return new MiniGameFormat(kind, list, sameSize);
    }

    public MiniGameFormat withoutSide(int index) {
        if (kind != Kind.TEAMS || sides.size() <= MIN_SIDES) return this;
        List<Side> list = new ArrayList<>(sides);
        list.remove(index);
        return new MiniGameFormat(kind, list, sameSize);
    }

    public MiniGameFormat withAddedSide() {
        if (kind != Kind.TEAMS || sides.size() >= MAX_SIDES) return this;
        List<Side> list = new ArrayList<>(sides);
        list.add(Side.atLeast(1));
        return new MiniGameFormat(kind, list, sameSize);
    }

    public MiniGameFormat withSameSize(boolean same) {
        return new MiniGameFormat(kind, sides, same);
    }

    // ------------------------------------------------------------------ matching

    /** The teams this format plays with: 2 to 4, or 1 (everyone on one side) without teams. */
    public int teams() {
        return kind == Kind.TEAMS ? sides.size() : 1;
    }

    /**
     * @param counts the players of each group of a composition (its non-empty teams; a single count without teams)
     * @return true if that composition can play this format, its groups given to the sides in some order
     */
    public boolean matches(List<Integer> counts) {
        return assignment(counts) != null;
    }

    /** @return true if the groups fit the sides in their order: group 1 side 1 (team A), group 2 side 2... */
    public boolean matchesInOrder(List<Integer> counts) {
        if (counts.isEmpty() || counts.size() != teams()) return false;
        if (sameSize && counts.stream().distinct().count() > 1) return false;
        for (int i = 0; i < counts.size(); i++) if (!sides.get(i).contains(counts.get(i))) return false;
        return true;
    }

    /**
     * Which group plays which side: {@code order[side]} is the index of its group; null when the groups fit no order.
     * Their own order when it fits, else the first order that fits (side A given the first group that can play it, in
     * the groups' order, then B...).
     */
    public int @Nullable [] assignment(List<Integer> counts) {
        if (counts.isEmpty() || counts.size() != teams()) return null;
        if (sameSize && counts.stream().distinct().count() > 1) return null;
        int[] order = new int[counts.size()];
        if (matchesInOrder(counts)) {
            for (int i = 0; i < order.length; i++) order[i] = i;
            return order;
        }
        return assign(counts, new boolean[counts.size()], 0, order) ? order : null;
    }

    private boolean assign(List<Integer> counts, boolean[] used, int side, int[] order) {
        if (side == counts.size()) return true;
        for (int i = 0; i < counts.size(); i++) {
            if (used[i] || !sides.get(side).contains(counts.get(i))) continue;
            used[i] = true;
            order[side] = i;
            if (assign(counts, used, side + 1, order)) return true;
            used[i] = false;
        }
        return false;
    }

    /** How far groups in order are from fitting: the players missing or too many, side by side (0 when they fit). */
    public int distanceInOrder(List<Integer> counts) {
        if (counts.size() != teams()) return Integer.MAX_VALUE / 2;
        int total = 0;
        for (int i = 0; i < counts.size(); i++) total += sides.get(i).distance(counts.get(i));
        return total;
    }

    /** The lower, the more specific: the sum of its ranges' widths (« or more » counts as wide). */
    public int specificity() {
        int width = 0;
        for (Side side : sides) width += side.infinite() ? 100 : side.max() - side.min();
        return width + (sameSize ? 0 : 1);
    }

    /** The roles of the pipes its players come out of: the team pipes A to D, or the players pipes without teams. */
    public List<MiniGamePipeRole> neededRoles() {
        if (kind != Kind.TEAMS) return List.of(MiniGamePipeRole.PLAYERS);
        List<MiniGamePipeRole> roles = new ArrayList<>();
        for (int team = 0; team < sides.size(); team++) roles.add(MiniGamePipeRole.ofTeam(team));
        return roles;
    }

    /** The smallest and the largest number of players it can take (the largest {@link Side#INFINITE} when unbounded). */
    public int minPlayers() {
        int total = 0;
        for (Side side : sides) total += side.min();
        return total;
    }

    // ------------------------------------------------------------------ its name

    /** « 2 contre 2 », « Duel », « 1 contre tous », « Équipes égales 2+ », « 3 équipes », « Chacun pour soi · 2 à 8 »... */
    public MutableText name() {
        if (kind != Kind.TEAMS) {
            MutableText base = Text.translatable("format.steveparty." + kind.key());
            Side side = sides.getFirst();
            if (side.min() == 1 && side.infinite()) return base;
            return Text.translatable("format.steveparty.with_players", base, side.rangeText());
        }
        int k = sides.size();
        Side first = sides.getFirst(), second = sides.get(1);
        if (k == 2 && first.equals(Side.exactly(1)) && second.equals(Side.exactly(1))) return Text.translatable("format.steveparty.duel");
        if (k == 2 && (first.equals(Side.exactly(1)) && second.equals(Side.atLeast(1)) || second.equals(Side.exactly(1)) && first.equals(Side.atLeast(1))))
            return Text.translatable("format.steveparty.one_vs_all");
        boolean allSame = sides.stream().distinct().count() == 1;
        if (allSame && first.infinite()) {
            MutableText base = sameSize
                    ? (k == 2 ? Text.translatable("format.steveparty.equal") : Text.translatable("format.steveparty.equal.n", k))
                    : Text.translatable("format.steveparty.teams", k);
            return first.min() == 1 ? base : Text.translatable("format.steveparty.at_least", base, first.min());
        }
        MutableText text = Text.literal(first.label());
        for (int i = 1; i < k; i++) text = Text.translatable("format.steveparty.vs", text, sides.get(i).label());
        if (sameSize && !(allSame && first.min() == first.max())) text = Text.translatable("format.steveparty.same_size", text);
        return text;
    }

    /** What it means at the draw: « au tirage : si la partie a 1 joueur d'un côté et 3 de l'autre »... */
    public MutableText meaning() {
        if (kind != Kind.TEAMS) return Text.translatable("format.steveparty.meaning." + kind.key(), sides.getFirst().rangeText());
        MutableText list = Text.empty();
        for (int i = 0; i < sides.size(); i++) {
            if (i > 0) list.append(i == sides.size() - 1 ? Text.translatable("format.steveparty.meaning.and") : Text.literal(", "));
            list.append(sides.get(i).rangeText());
        }
        return Text.translatable(sameSize ? "format.steveparty.meaning.teams.same" : "format.steveparty.meaning.teams", sides.size(), list);
    }

    // ------------------------------------------------------------------ saving

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Kind", kind.key());
        NbtList list = new NbtList();
        for (Side side : sides) {
            NbtCompound s = new NbtCompound();
            s.putInt("Min", side.min());
            s.putInt("Max", side.infinite() ? -1 : side.max());
            list.add(s);
        }
        nbt.put("Sides", list);
        if (sameSize) nbt.putBoolean("SameSize", true);
        return nbt;
    }

    public static MiniGameFormat fromNbt(NbtCompound nbt) {
        Kind kind;
        try {
            kind = Kind.valueOf(nbt.getString("Kind").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            kind = Kind.TEAMS;
        }
        List<Side> sides = new ArrayList<>();
        NbtList list = nbt.getList("Sides", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound s = list.getCompound(i);
            int max = s.getInt("Max");
            sides.add(new Side(s.getInt("Min"), max < 0 ? Side.INFINITE : max));
        }
        return new MiniGameFormat(kind, sides, nbt.getBoolean("SameSize"));
    }

    public void write(PacketByteBuf buf) {
        buf.writeByte(kind.ordinal());
        buf.writeBoolean(sameSize);
        buf.writeByte(sides.size());
        for (Side side : sides) {
            buf.writeByte(side.min());
            buf.writeByte(side.infinite() ? 0 : side.max());
        }
    }

    public static MiniGameFormat read(PacketByteBuf buf) {
        Kind[] kinds = Kind.values();
        Kind kind = kinds[Math.floorMod(buf.readByte(), kinds.length)];
        boolean same = buf.readBoolean();
        int count = Math.min(buf.readByte(), MAX_SIDES);
        List<Side> sides = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int min = buf.readByte(), max = buf.readByte();
            sides.add(new Side(min, max == 0 ? Side.INFINITE : max));
        }
        return new MiniGameFormat(kind, sides, same);
    }

    public static void writeList(PacketByteBuf buf, List<MiniGameFormat> formats) {
        buf.writeVarInt(formats.size());
        formats.forEach(format -> format.write(buf));
    }

    public static List<MiniGameFormat> readList(PacketByteBuf buf) {
        int count = Math.min(buf.readVarInt(), MiniGamePageData.MAX_FORMATS);
        List<MiniGameFormat> formats = new ArrayList<>(count);
        for (int i = 0; i < count; i++) formats.add(read(buf));
        return formats;
    }

    // ------------------------------------------------------------------ the old ways to play

    /**
     * The formats of a page saved before formats (its ticked ways to play and its players range): free for all keeps
     * the range ({@link MiniGamePageData#MAX_PLAYERS_OLD}, the old « any », becomes « or more »); 2, 3 and 4 teams become
     * as many teams of « 1 or more », each capped so that the teams together don't exceed the old most players.
     *
     * @param modeMask the old ways ticked: bit 0 free for all, bits 1 to 3 two to four teams
     */
    public static List<MiniGameFormat> migrate(int modeMask, int minPlayers, int maxPlayers) {
        List<MiniGameFormat> formats = new ArrayList<>();
        boolean anyMax = maxPlayers <= 0 || maxPlayers >= MiniGamePageData.MAX_PLAYERS_OLD;
        if ((modeMask & 1) != 0 || modeMask == 0) {
            formats.add(freeForAll(Math.max(1, minPlayers), anyMax ? Side.INFINITE : maxPlayers));
        }
        for (int teams = 2; teams <= 4; teams++) {
            if ((modeMask & (1 << (teams - 1))) == 0) continue;
            int cap = anyMax ? Side.INFINITE : Math.max(1, maxPlayers - (teams - 1));
            List<Side> sides = new ArrayList<>();
            for (int i = 0; i < teams; i++) sides.add(new Side(1, cap));
            formats.add(new MiniGameFormat(Kind.TEAMS, sides, false));
        }
        return formats;
    }
}
