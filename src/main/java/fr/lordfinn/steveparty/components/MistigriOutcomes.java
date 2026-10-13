package fr.lordfinn.steveparty.components;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrencyText;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrency;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.hud.OutcomeRoulette;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * The Mistigri Cartridge's sentences: a list the players write themselves, each row one sentence his loaded die may
 * pass (what happens, to whom, how much, how likely), and how many of them are put on his roulette at each landing.
 * <p>
 * At each landing, {@link #draws} rows are picked among those of a weight above 0, each as likely (a uniform pick, the
 * weights play no part in it), and listed on the roulette; the weights then draw the one that falls among them. All of
 * them ({@code draws} 0) by default. {@link #chances()} gives each row's exact chance of falling, both steps included.
 *
 * @param outcomes the rows, in the menu's order (at most {@link #MAX})
 * @param draws    how many rows are picked for each landing, 0 for all of them
 */
public record MistigriOutcomes(List<Outcome> outcomes, int draws) {
    /** The most rows: as many as a roulette lists. */
    public static final int MAX = OutcomeRoulette.MAX_LINES;
    public static final int MAX_WEIGHT = 9;

    /** What a sentence does. */
    public enum Kind implements StringIdentifiable {
        /** Each one aimed at loses coins (a number, or a share of theirs), into the party's bank. */
        LOSE_COINS("lose_coins", 1, 99, 10, true, true),
        /** Each one aimed at loses stars (a number, or a share of theirs), into the party's bank. */
        LOSE_STARS("lose_stars", 1, 9, 1, true, true),
        /**
         * The common pot of coins: each one aimed at puts coins in (a number, or a share of theirs), and the pot is dealt
         * out coin by coin to the players who put nothing in (everyone when all did), the poorest first. Nothing goes to
         * the bank, nothing comes out of it.
         */
        SHARE_COINS("share_coins", 1, 99, 10, true, true),
        /** The common pot of stars: the same with stars, dealt out to the fewest stars first. */
        SHARE_STARS("share_stars", 1, 9, 1, true, true),
        /** The player who landed moves back this many spaces (only them: the others are not moving). */
        MOVE_BACK("move_back", 1, 9, 3, false, false),
        /** The next roll of each one aimed at is cursed: 1 to 3 only (CursedRolls). */
        CURSED_DIE("cursed_die", 0, 0, 0, false, true),
        /** He takes pity: each one aimed at gets coins, out of the party's bank (as many as it still holds). */
        PITY("pity", 1, 99, 2, false, true);

        public static final Codec<Kind> CODEC = StringIdentifiable.createCodec(Kind::values);

        public final String id;
        /** Its number's range (min 0: it has none) and default. */
        public final int min, max, defaultAmount;
        /** Whether it may be a share of what they hold (a percentage) rather than a number. */
        public final boolean percent;
        /** Whether it may aim at anyone, or only at the player who landed. */
        public final boolean anyTarget;

        Kind(String id, int min, int max, int defaultAmount, boolean percent, boolean anyTarget) {
            this.id = id;
            this.min = min;
            this.max = max;
            this.defaultAmount = defaultAmount;
            this.percent = percent;
            this.anyTarget = anyTarget;
        }

        public boolean hasAmount() {
            return max > 0;
        }

        /** A loss (red on the roulette, he hisses), or a kind one (green, he yawns), or neither (the pots). */
        public int tone() {
            return switch (this) {
                case PITY -> 1;
                case SHARE_COINS, SHARE_STARS -> 0;
                default -> -1;
            };
        }

        /** The currency it moves, null if none. */
        public @org.jetbrains.annotations.Nullable PartyCurrency currency() {
            return switch (this) {
                case LOSE_COINS, SHARE_COINS, PITY -> PartyCurrency.COIN;
                case LOSE_STARS, SHARE_STARS -> PartyCurrency.STAR;
                default -> null;
            };
        }

        /** Its icon (the roulette, the menu), in these currencies. */
        public ItemStack icon(ItemStack coin, ItemStack star) {
            return (switch (this) {
                case LOSE_COINS, SHARE_COINS -> coin;
                case LOSE_STARS, SHARE_STARS -> star;
                case MOVE_BACK -> new ItemStack(Items.ARROW);
                case CURSED_DIE -> new ItemStack(Registries.ITEM.get(Steveparty.id("cursed_dice_face_3")));
                case PITY -> new ItemStack(Items.COD);
            }).copyWithCount(1);
        }

        @Override
        public String asString() {
            return id;
        }
    }

    /** Who a sentence falls on. Ranks, coins and stars are the ones held when it falls; a tie aims at all the tied. */
    public enum Target implements StringIdentifiable {
        /** The player whose pawn landed on the space. */
        LANDED("landed", 0x8E3FC8),
        /** The first place: the most stars, then the most coins. */
        FIRST("first", 0xB8860B),
        /** The last place: the fewest stars, then the fewest coins. */
        LAST("last", 0x7D8C1C),
        /** Every player of the party. */
        ALL("all", 0xD2691E),
        /** Every player of the party but the one who landed. */
        OTHERS("others", 0xC0407A),
        /** The most coins. */
        RICHEST("richest", 0x1E8C7E),
        /** The fewest coins. */
        POOREST("poorest", 0x8B5A2B),
        /** One player of the party, at random. */
        RANDOM("random", 0x2A7BD4);

        public static final Codec<Target> CODEC = StringIdentifiable.createCodec(Target::values);

        public final String id;
        /**
         * Its colour (opaque RGB), the same wherever it is named: the roulette's lines (panel and card), the chat, the
         * menu's pictograms and tooltips. Mid-tones, readable on the light rows of the roulette and on the dark menu.
         */
        public final int color;

        Target(String id, int color) {
            this.id = id;
            this.color = color;
        }

        @Override
        public String asString() {
            return id;
        }
    }

    /**
     * One sentence.
     *
     * @param amount  coins, stars or spaces ({@link Kind#min} to {@link Kind#max}), or a percentage (1 to 100)
     * @param percent whether {@code amount} is a share of what they hold
     * @param weight  how likely it falls once on the roulette: 0 (never picked) to {@link #MAX_WEIGHT}
     */
    public record Outcome(Kind kind, Target target, int amount, boolean percent, int weight) {
        public static final Codec<Outcome> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Kind.CODEC.fieldOf("kind").forGetter(Outcome::kind),
                Target.CODEC.optionalFieldOf("target", Target.LANDED).forGetter(Outcome::target),
                Codec.INT.optionalFieldOf("amount", 0).forGetter(Outcome::amount),
                Codec.BOOL.optionalFieldOf("percent", false).forGetter(Outcome::percent),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Outcome::weight)
        ).apply(instance, Outcome::new));

        /** Kept within the ranges of its kind. */
        public Outcome {
            if (!kind.anyTarget) target = Target.LANDED;
            percent = percent && kind.percent;
            amount = !kind.hasAmount() ? 0 : percent ? Math.clamp(amount, 1, 100) : Math.clamp(amount, kind.min, kind.max);
            weight = Math.clamp(weight, 0, MAX_WEIGHT);
        }

        /** A new sentence of {@code kind}, its default amount, aimed at the player who landed. */
        public static Outcome of(Kind kind, int weight) {
            return new Outcome(kind, Target.LANDED, kind.defaultAmount, false, weight);
        }

        /** Another kind: its default amount (a number), the same target when that kind may aim at it. */
        public Outcome withKind(Kind next) {
            return next == kind ? this : new Outcome(next, target, next.defaultAmount, false, weight);
        }

        public Outcome withTarget(Target next) {
            return new Outcome(kind, next, amount, percent, weight);
        }

        public Outcome withAmount(int next) {
            return new Outcome(kind, target, next, percent, weight);
        }

        /** A number or a percentage: half of what they hold, or the kind's default number. */
        public Outcome withPercent(boolean next) {
            return next == percent ? this : new Outcome(kind, target, next ? 50 : kind.defaultAmount, next, weight);
        }

        public Outcome withWeight(int next) {
            return new Outcome(kind, target, amount, percent, next);
        }

        /** How many {@code held} it takes: the number, or the share of {@code held} (rounded down). */
        public int count(int held) {
            return percent ? held * amount / 100 : amount;
        }

        /** {@link #effect(ItemStack, ItemStack)} where no party is at hand (the menu): see {@link PartyCurrencyText#contextual}. */
        public MutableText effect() {
            return effect(PartyCurrencyText.contextual(PartyCurrency.COIN), PartyCurrencyText.contextual(PartyCurrency.STAR));
        }

        /** Its effect alone, in the party's {@code coin} and {@code star}: « −10 × Coin », « −50% (Party Star) », « Back 3 spaces »... */
        public MutableText effect(ItemStack coin, ItemStack star) {
            String key = "hud.steveparty.mistigri.effect." + kind.id;
            if (!kind.hasAmount()) return Text.translatable(key);
            PartyCurrency currency = kind.currency();
            if (currency == null) return amount == 1 ? Text.translatable(key + ".one") : Text.translatable(key, amount);
            ItemStack item = currency == PartyCurrency.STAR ? star : coin;
            if (percent) return Text.translatable(key + ".percent", amount, PartyCurrencyText.name(item));
            return Text.translatable(key, PartyCurrencyText.amount(item, amount));
        }

        /** Who it falls on, short and in its target's colour: « 1st place », « Everyone »... */
        public MutableText who() {
            return Text.translatable("hud.steveparty.mistigri.who." + target.id).styled(style -> style.withColor(target.color));
        }

        /** Who then what, the target in its colour: « 1st place: −10 coins », « Who landed: Back 3 spaces ». */
        public MutableText text() {
            return text(null);
        }

        /**
         * The same, the player who landed named by {@code lander} (the roulette: « LordFinn: −10 coins »), in the
         * colour of their target.
         */
        public MutableText text(@org.jetbrains.annotations.Nullable Text lander) {
            return text(lander, PartyCurrencyText.contextual(PartyCurrency.COIN), PartyCurrencyText.contextual(PartyCurrency.STAR));
        }

        /** The same, in the party's {@code coin} and {@code star}. */
        public MutableText text(@org.jetbrains.annotations.Nullable Text lander, ItemStack coin, ItemStack star) {
            Text who = target == Target.LANDED && lander != null ? lander.copy().styled(style -> style.withColor(target.color)) : who();
            return Text.translatable("hud.steveparty.mistigri.line", who, effect(coin, star));
        }
    }

    public static final Codec<MistigriOutcomes> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Outcome.CODEC.sizeLimitedListOf(MAX).fieldOf("outcomes").forGetter(MistigriOutcomes::outcomes),
            Codec.INT.optionalFieldOf("draws", 0).forGetter(MistigriOutcomes::draws)
    ).apply(instance, MistigriOutcomes::new));

    /**
     * A new cartridge's: mostly fines for the one who landed, a few harsher and rarer ones, the whole party sharing a
     * fine, the first place feeding the common pot, a move back, a cursed die and a little pity. All on the roulette.
     */
    public static final MistigriOutcomes DEFAULT = new MistigriOutcomes(List.of(
            new Outcome(Kind.LOSE_COINS, Target.LANDED, 10, false, 4),
            new Outcome(Kind.LOSE_COINS, Target.LANDED, 50, true, 1),
            new Outcome(Kind.LOSE_STARS, Target.LANDED, 1, false, 1),
            new Outcome(Kind.LOSE_COINS, Target.ALL, 5, false, 2),
            new Outcome(Kind.SHARE_COINS, Target.FIRST, 20, true, 2),
            new Outcome(Kind.MOVE_BACK, Target.LANDED, 3, false, 3),
            new Outcome(Kind.CURSED_DIE, Target.LANDED, 0, false, 2),
            new Outcome(Kind.PITY, Target.LANDED, 2, false, 2)), 0);

    /** A row added from the menu: a small fine for the one who landed. */
    public static final Outcome ADDED = new Outcome(Kind.LOSE_COINS, Target.LANDED, 5, false, 2);

    public MistigriOutcomes {
        outcomes = List.copyOf(outcomes.size() > MAX ? outcomes.subList(0, MAX) : outcomes);
        draws = Math.clamp(draws, 0, MAX);
    }

    /** The sentences of {@code stack}: its own, or the default ones. */
    public static MistigriOutcomes of(ItemStack stack) {
        return stack.getOrDefault(ModComponents.MISTIGRI_OUTCOMES, DEFAULT);
    }

    /** Writes {@code outcomes} in {@code stack} (the default ones: nothing stored). */
    public static void set(ItemStack stack, MistigriOutcomes outcomes) {
        if (outcomes.equals(DEFAULT)) stack.remove(ModComponents.MISTIGRI_OUTCOMES);
        else stack.set(ModComponents.MISTIGRI_OUTCOMES, outcomes);
    }

    public int size() {
        return outcomes.size();
    }

    public Outcome get(int row) {
        return outcomes.get(row);
    }

    /** The rows that may be picked: a weight above 0. */
    public List<Integer> eligible() {
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < outcomes.size(); i++) if (outcomes.get(i).weight() > 0) rows.add(i);
        return rows;
    }

    /** How many rows a landing puts on the roulette: {@link #draws}, at most the rows that may be picked. */
    public int drawCount() {
        int eligible = eligible().size();
        return draws == 0 ? eligible : Math.min(draws, eligible);
    }

    /** The rows put on the roulette for one landing, in the list's order: {@link #drawCount} of them, each as likely. */
    public List<Integer> pick(Random random) {
        List<Integer> pool = new ArrayList<>(eligible());
        int count = drawCount();
        List<Integer> picked = new ArrayList<>(count);
        for (int k = 0; k < count; k++) picked.add(pool.remove(random.nextInt(pool.size())));
        picked.sort(null);
        return picked;
    }

    /** One of {@code rows} drawn by their weights, -1 if none may fall. */
    public int draw(List<Integer> rows, Random random) {
        int total = 0;
        for (int row : rows) total += outcomes.get(row).weight();
        if (total <= 0) return -1;
        int roll = random.nextInt(total);
        for (int row : rows) {
            roll -= outcomes.get(row).weight();
            if (roll < 0) return row;
        }
        return -1;
    }

    /** Row {@code row}'s chance among {@code rows} (those on the roulette), in percent. */
    public int chanceAmong(List<Integer> rows, int row) {
        int total = 0;
        for (int each : rows) total += outcomes.get(each).weight();
        return total == 0 ? 0 : Math.round(100f * outcomes.get(row).weight() / total);
    }

    /**
     * Each row's exact chance of falling at a landing (0 to 1), both steps included: being picked (uniform), then drawn
     * by the weights among the rows picked. Every set of rows that may be picked is weighed (at most 924).
     */
    public double[] chances() {
        double[] chances = new double[outcomes.size()];
        List<Integer> eligible = eligible();
        int m = eligible.size(), k = drawCount();
        if (k == 0) return chances;
        int sets = 0;
        for (int mask = 0; mask < 1 << m; mask++) {
            if (Integer.bitCount(mask) != k) continue;
            sets++;
            int total = 0;
            for (int b = 0; b < m; b++) if ((mask & 1 << b) != 0) total += outcomes.get(eligible.get(b)).weight();
            for (int b = 0; b < m; b++) {
                if ((mask & 1 << b) != 0) chances[eligible.get(b)] += outcomes.get(eligible.get(b)).weight() / (double) total;
            }
        }
        for (int i = 0; i < chances.length; i++) chances[i] /= sets;
        return chances;
    }

    /** Row {@code row}'s chance of falling at a landing, in whole percent. */
    public int chance(int row) {
        return (int) Math.round(100 * chances()[row]);
    }

    public MistigriOutcomes with(int row, Outcome outcome) {
        List<Outcome> next = new ArrayList<>(outcomes);
        next.set(row, outcome);
        return new MistigriOutcomes(next, draws);
    }

    public MistigriOutcomes add(Outcome outcome) {
        if (outcomes.size() >= MAX) return this;
        List<Outcome> next = new ArrayList<>(outcomes);
        next.add(outcome);
        return new MistigriOutcomes(next, draws);
    }

    /** Without row {@code row} (a count of draws then above the rows left means all of them). */
    public MistigriOutcomes remove(int row) {
        List<Outcome> next = new ArrayList<>(outcomes);
        next.remove(row);
        return new MistigriOutcomes(next, draws);
    }

    public MistigriOutcomes withDraws(int next) {
        return new MistigriOutcomes(outcomes, next);
    }
}
