package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * How a thrown die gets its result, from the throw to the final faces. One sequence drives a whole throw: the die, or
 * the dice of a Double / Triple Dice together (it lives on the first one, the others follow). Server side, not saved:
 * a die reloaded in the middle of its roll simply rolls again.
 * <p>
 * The sequence, following the modules of the die ({@link DiceModules}):
 * <ol>
 *     <li>the dice roll until a player hits one (Slow: only their roller, and each die stops on the face it shows;
 *     left alone, a Slow die stops by itself after {@link #SLOW_TIMEOUT_TICKS});</li>
 *     <li>Lucky ×N: N more rolls are made (Slow: each one stopped by hand), and the roller keeps one of the results
 *     (no answer: the highest number);</li>
 *     <li>Reroll ×N: the roller keeps the result or rolls everything again, up to N times (no answer: kept);</li>
 *     <li>the result is final: the dice show it and {@link DiceEntity#onRollFinished} announces it.</li>
 * </ol>
 * Choice replaces all of it: the roller picks the face of each die, one after the other (no answer: a face at random).
 */
public final class DiceRollSequence {
    public enum Phase { ROLLING, PICKING, DECIDING, DONE }

    /** Slow: ticks each face is shown. */
    public static final int SLOW_FACE_TICKS = 8;
    /** Slow: the die stops by itself on the face shown after 10 seconds. */
    public static final int SLOW_TIMEOUT_TICKS = 200;
    /** A reroll of a die that is not Slow stops by itself after a second. */
    public static final int REROLL_SPIN_TICKS = 20;
    /** Slow: the longest cycle of faces (heavier dice are scaled down to it). */
    public static final int MAX_CYCLE = 48;

    private final DiceEntity lead;
    private Phase phase = Phase.ROLLING;
    private boolean started;
    /** The results rolled so far for the current attempt: one face per die of the throw. */
    private final List<List<DiceFace>> results = new ArrayList<>();
    /** Choice: the faces picked so far. */
    private final List<DiceFace> chosen = new ArrayList<>();
    private int rerollsUsed;
    private int age;
    private int autoStopAt = -1;
    private int promptTimeout = DicePrompts.TIMEOUT_TICKS;
    private @Nullable DicePrompts.Prompt prompt;
    /** Slow: the faces each die goes through, and where it is. */
    private final Map<UUID, List<DiceFace>> cycles = new HashMap<>();
    private final Map<UUID, Integer> cycleIndexes = new HashMap<>();

    public DiceRollSequence(DiceEntity lead) {
        this.lead = lead;
    }

    public Phase phase() {
        return phase;
    }

    /** The prompt the roller has to answer (Choice, Lucky, Reroll), null if none. */
    public @Nullable DicePrompts.Prompt prompt() {
        return prompt == null || prompt.isDone() ? null : prompt;
    }

    /** The results rolled so far (Lucky: one per roll). */
    public List<List<DiceFace>> results() {
        return List.copyOf(results);
    }

    public int rerollsLeft() {
        return Math.max(0, rerolls() - rerollsUsed);
    }

    /** Time given to answer the prompts of this roll (tests shorten it). */
    public void setPromptTimeout(int ticks) {
        this.promptTimeout = Math.max(1, ticks);
    }

    // ------------------------------------------------------------------ modules

    private Map<DiceModule, Integer> modules() {
        return DiceModules.of(lead.getDieStack());
    }

    private boolean chooses() {
        return modules().keySet().stream().anyMatch(DiceModule::rollerChooses);
    }

    private boolean byHand() {
        return !chooses() && modules().keySet().stream().anyMatch(DiceModule::stoppedByHand);
    }

    private int extraRolls() {
        if (chooses()) return 0;
        return modules().entrySet().stream().mapToInt(entry -> entry.getKey().extraRolls(entry.getValue())).sum();
    }

    private int rerolls() {
        if (chooses()) return 0;
        return modules().entrySet().stream().mapToInt(entry -> entry.getKey().rerolls(entry.getValue())).sum();
    }

    private boolean isRoller(ServerPlayerEntity player) {
        return lead.getOwner().map(owner -> owner.equals(player.getUuid())).orElse(true);
    }

    // ------------------------------------------------------------------ the sequence

    /** The dice were just thrown. */
    public void start() {
        if (started) return;
        started = true;
        modules().forEach((module, count) -> module.onThrown(lead, count));
        begin();
    }

    /** A roll (the first one, or a reroll) begins: the dice spin. */
    private void begin() {
        phase = Phase.ROLLING;
        age = 0;
        results.clear();
        chosen.clear();
        cycles.clear();
        cycleIndexes.clear();
        boolean slow = byHand();
        for (DiceEntity die : lead.group()) {
            die.setSpinning(true);
            die.setFaceShown(slow);
            if (slow) {
                List<DiceFace> cycle = cycle(DiceFacesComponent.facesOf(die.getDieStack()));
                int index = lead.getRandom().nextInt(cycle.size());
                cycles.put(die.getUuid(), cycle);
                cycleIndexes.put(die.getUuid(), index);
                die.showFace(cycle.get(index));
            }
        }
        if (chooses()) askChoice();
    }

    public void tick() {
        if (!started) start();
        if (phase != Phase.ROLLING) return;
        age++;
        if (chooses()) {
            if (prompt() == null) askChoice(); // lost (reload): asked again
            return;
        }
        if (byHand()) {
            if (cycles.isEmpty()) {
                begin();
                return;
            }
            if (age % SLOW_FACE_TICKS == 0) advanceCycles();
            if (age >= SLOW_TIMEOUT_TICKS) stop();
        } else if (autoStopAt >= 0 && age >= autoStopAt) {
            stop();
        }
    }

    private void advanceCycles() {
        for (DiceEntity die : lead.group()) {
            List<DiceFace> cycle = cycles.get(die.getUuid());
            if (cycle == null) continue;
            int index = (cycleIndexes.getOrDefault(die.getUuid(), 0) + 1) % cycle.size();
            cycleIndexes.put(die.getUuid(), index);
            die.showFace(cycle.get(index));
        }
        lead.getWorld().playSound(null, lead.getX(), lead.getY(), lead.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(),
                SoundCategory.PLAYERS, 0.35f, 1.4f);
    }

    /** A player (not sneaking) hit one of the dice. */
    public void hit(ServerPlayerEntity player) {
        if (!started) start();
        switch (phase) {
            case ROLLING -> {
                if (chooses()) {
                    if (isRoller(player)) DicePrompts.resend(player);
                    return;
                }
                if (byHand() && !isRoller(player)) {
                    MessageUtils.sendToPlayer(player, Text.translatable("message.steveparty.dice.slow.not_yours")
                            .formatted(Formatting.GRAY), MessageUtils.MessageType.ACTION_BAR);
                    return;
                }
                stop();
            }
            case PICKING, DECIDING -> {
                if (isRoller(player)) DicePrompts.resend(player);
            }
            case DONE -> {
            }
        }
    }

    /** The rolling dice stop: a result is rolled (Slow: the faces shown). */
    public void stop() {
        if (!started) start();
        if (phase != Phase.ROLLING || chooses()) return;
        boolean slow = byHand();
        int needed = 1 + extraRolls();
        results.add(rollSet(slow));
        if (results.size() < needed) {
            if (slow) {
                // The next roll is stopped by hand too: the dice go on turning
                age = 0;
                ServerPlayerEntity roller = lead.getOnlineOwner();
                if (roller != null) {
                    MessageUtils.sendToPlayer(roller, Text.translatable("message.steveparty.dice.lucky.progress",
                                    results.size(), needed, DiceOutcome.of(results.getLast()).describe()).formatted(Formatting.GOLD),
                            MessageUtils.MessageType.ACTION_BAR);
                }
                lead.getWorld().playSound(null, lead.getX(), lead.getY(), lead.getZ(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                        SoundCategory.PLAYERS, 0.8f, 1.2f);
                return;
            }
            while (results.size() < needed) results.add(rollSet(false));
        }
        for (DiceEntity die : lead.group()) {
            die.setSpinning(false);
            die.setFaceShown(false);
        }
        showSet(results.getLast());
        if (needed > 1) askLucky();
        else picked(results.getFirst());
    }

    private List<DiceFace> rollSet(boolean slow) {
        List<DiceFace> set = new ArrayList<>();
        for (DiceEntity die : lead.group()) {
            List<DiceFace> cycle = slow ? cycles.get(die.getUuid()) : null;
            if (cycle != null) set.add(cycle.get(cycleIndexes.getOrDefault(die.getUuid(), 0) % cycle.size()));
            else set.add(DiceFacesComponent.roll(die.getDieStack(), lead.getRandom()));
        }
        return set;
    }

    private void showSet(List<DiceFace> set) {
        List<DiceEntity> group = lead.group();
        for (int i = 0; i < group.size() && i < set.size(); i++) group.get(i).showFace(set.get(i));
    }

    /** Lucky: the roller keeps one of the results (no answer: the best one, see {@link #best}). */
    private void askLucky() {
        phase = Phase.PICKING;
        List<List<DiceFace>> sets = List.copyOf(results);
        List<DicePrompts.Option> options = new ArrayList<>();
        for (List<DiceFace> set : sets) options.add(optionOf(set));
        prompt = DicePrompts.ask(lead.getOnlineOwner(), Text.translatable("gui.steveparty.dice_prompt.lucky"),
                DicePrompts.Layout.GRID, options, promptTimeout, best(sets), index -> {
                    if (lead.isRemoved() || phase != Phase.PICKING) return;
                    picked(sets.get(index));
                });
    }

    /** The result kept when the roller doesn't answer: the highest number, else the most coins, else the first. */
    public static int best(List<List<DiceFace>> sets) {
        int best = 0;
        for (int i = 1; i < sets.size(); i++) {
            DiceOutcome candidate = DiceOutcome.of(sets.get(i)), current = DiceOutcome.of(sets.get(best));
            if (candidate.steps() > current.steps()
                    || (candidate.steps() == current.steps() && candidate.coins() > current.coins())) best = i;
        }
        return best;
    }

    /** A result is held: Reroll lets the roller throw it away (no answer: kept). */
    private void picked(List<DiceFace> set) {
        showSet(set);
        int left = rerollsLeft();
        if (left <= 0) {
            finish(set);
            return;
        }
        phase = Phase.DECIDING;
        List<DicePrompts.Option> options = List.of(
                new DicePrompts.Option(optionOf(set).icon(), Text.translatable("gui.steveparty.dice_prompt.reroll.keep",
                        DiceOutcome.of(set).describe())),
                new DicePrompts.Option(new ItemStack(DiceModules.REROLL.item()),
                        Text.translatable("gui.steveparty.dice_prompt.reroll.again", left)));
        prompt = DicePrompts.ask(lead.getOnlineOwner(), Text.translatable("gui.steveparty.dice_prompt.reroll"),
                DicePrompts.Layout.LIST, options, promptTimeout, 0, index -> {
                    if (lead.isRemoved() || phase != Phase.DECIDING) return;
                    if (index == 0) {
                        finish(set);
                        return;
                    }
                    rerollsUsed++;
                    autoStopAt = REROLL_SPIN_TICKS;
                    begin();
                });
    }

    /** Choice: the roller picks the face of the next die (no answer: one at random, by weight). */
    private void askChoice() {
        List<DiceEntity> group = lead.group();
        int dieIndex = chosen.size();
        if (dieIndex >= group.size()) {
            finish(List.copyOf(chosen));
            return;
        }
        DiceEntity die = group.get(dieIndex);
        List<DiceFace> faces = DiceFacesComponent.facesOf(die.getDieStack());
        List<DicePrompts.Option> options = new ArrayList<>();
        for (DiceFace face : faces) options.add(optionOf(List.of(face)));
        int random = Math.max(0, faces.indexOf(DiceFacesComponent.roll(die.getDieStack(), lead.getRandom())));
        Text title = group.size() > 1
                ? Text.translatable("gui.steveparty.dice_prompt.choice.die", dieIndex + 1, group.size())
                : Text.translatable("gui.steveparty.dice_prompt.choice");
        prompt = DicePrompts.ask(lead.getOnlineOwner(), title, DicePrompts.Layout.GRID, options, promptTimeout, random, index -> {
            if (lead.isRemoved() || phase != Phase.ROLLING || chosen.size() != dieIndex) return;
            DiceFace face = faces.get(index);
            chosen.add(face);
            die.setSpinning(false);
            die.showFace(face);
            askChoice();
        });
    }

    private static DicePrompts.Option optionOf(List<DiceFace> set) {
        DiceFace first = set.getFirst();
        ItemStack icon = new ItemStack(first.toItem());
        Text label = set.size() == 1 ? (icon.isEmpty() ? first.asText() : icon.getName()) : DiceOutcome.of(set).describe();
        return new DicePrompts.Option(icon, label);
    }

    private void finish(List<DiceFace> set) {
        phase = Phase.DONE;
        for (DiceEntity die : lead.group()) {
            die.setSpinning(false);
            die.setFaceShown(false);
        }
        showSet(set);
        lead.onRollFinished(set);
    }

    /** The dice are gone: nothing is asked any more. */
    public void cancel() {
        DicePrompts.cancel(prompt);
        prompt = null;
    }

    // ------------------------------------------------------------------ Slow: the cycle of the faces

    /**
     * The faces a Slow die shows one after the other, then again: each face comes up as often as its weight, spread
     * as evenly as possible (smooth weighted round-robin), so stopping the die at random is as fair as rolling it.
     * Weights are first reduced by their common divisor, then scaled down so that the cycle stays within
     * {@link #MAX_CYCLE} faces (every face keeps at least one place).
     */
    public static List<DiceFace> cycle(List<DiceFace> faces) {
        if (faces.isEmpty()) return List.of(new DiceFace(DiceFacesComponent.Kind.BLANK, 0));
        int[] weights = new int[faces.size()];
        int divisor = 0, total = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = Math.max(1, faces.get(i).weight());
            divisor = gcd(divisor, weights[i]);
        }
        for (int i = 0; i < weights.length; i++) {
            weights[i] /= divisor;
            total += weights[i];
        }
        if (total > MAX_CYCLE) {
            float scale = MAX_CYCLE / (float) total;
            total = 0;
            for (int i = 0; i < weights.length; i++) {
                weights[i] = Math.max(1, Math.round(weights[i] * scale));
                total += weights[i];
            }
        }
        List<DiceFace> cycle = new ArrayList<>(total);
        int[] current = new int[weights.length];
        for (int n = 0; n < total; n++) {
            int pick = 0;
            for (int i = 0; i < weights.length; i++) {
                current[i] += weights[i];
                if (current[i] > current[pick]) pick = i;
            }
            current[pick] -= total;
            cycle.add(faces.get(pick));
        }
        return cycle;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}
