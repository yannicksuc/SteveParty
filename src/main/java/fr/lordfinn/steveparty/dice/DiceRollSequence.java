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
 *     <li>the result is final: it is revealed ({@link #REVEAL_STEP_TICKS}): the dice stop one after the other, a
 *     double / triple shows as soon as they make it, then the total ({@link DiceReveal});</li>
 *     <li>then {@link DiceEntity#onRollFinished} announces it ({@link fr.lordfinn.steveparty.events.DiceThrowRevealed},
 *     then the move).</li>
 * </ol>
 * Choice replaces the rolls: the roller picks the face of each die, one after the other (no answer: a face at random);
 * the faces picked are then revealed all at once (they were chosen: no suspense). A single die is revealed at once too.
 */
public final class DiceRollSequence {
    public enum Phase { ROLLING, PICKING, DECIDING, REVEALING, DONE }

    /** Slow: ticks each face is shown. */
    public static final int SLOW_FACE_TICKS = 8;
    /** Slow: the die stops by itself on the face shown after 10 seconds. */
    public static final int SLOW_TIMEOUT_TICKS = 200;
    /** A reroll of a die that is not Slow stops by itself after a second. */
    public static final int REROLL_SPIN_TICKS = 20;
    /** Slow: the longest cycle of faces (heavier dice are scaled down to it). */
    public static final int MAX_CYCLE = 48;
    /** The reveal: ticks between two dice stopping (the first one stops at once). */
    public static final int REVEAL_STEP_TICKS = 14;
    /** The reveal: ticks between the last die stopping and the total. */
    public static final int REVEAL_TOTAL_TICKS = 16;

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
    /** The reveal: the faces of the throw, how many are shown, ticks since it began, the double / triple shown. */
    private List<DiceFace> revealing = List.of();
    private int revealed, revealAge, comboShown;

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

    /** The modules of the die: read once when the roll starts (the thrown die no longer changes). */
    private @Nullable Map<DiceModule, Integer> modules;
    private boolean chooses, byHand;

    private Map<DiceModule, Integer> modules() {
        return modules != null ? modules : DiceModules.of(lead.getDieStack());
    }

    private void readModules() {
        modules = DiceModules.of(lead.getDieStack());
        chooses = modules.keySet().stream().anyMatch(DiceModule::rollerChooses);
        byHand = !chooses && modules.keySet().stream().anyMatch(DiceModule::stoppedByHand);
    }

    private boolean chooses() {
        if (modules == null) readModules();
        return chooses;
    }

    private boolean byHand() {
        if (modules == null) readModules();
        return byHand;
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

    /** The roller's roll is cursed (CursedRolls): only the cursed 1, 2 and 3; read when the roll starts. */
    private boolean cursed;

    /** The faces a die of this throw may stop on: its own, or the cursed 1 to 3 of a cursed roll. */
    private List<DiceFace> faces(DiceEntity die) {
        return cursed ? CursedRolls.FACES : DiceFacesComponent.facesOf(die.getDieStack());
    }

    /** One face of a die of this throw, by weight (a cursed roll: 1, 2 or 3 as likely). */
    private DiceFace roll(DiceEntity die) {
        return cursed ? CursedRolls.FACES.get(lead.getRandom().nextInt(CursedRolls.FACES.size()))
                : DiceFacesComponent.roll(die.getDieStack(), lead.getRandom());
    }

    /** The dice were just thrown. */
    public void start() {
        if (started) return;
        started = true;
        cursed = CursedRolls.isCursed(lead.getWorld().getServer(), lead.getOwner().orElse(null));
        ServerPlayerEntity roller = cursed ? lead.getOnlineOwner() : null;
        if (roller != null) MessageUtils.sendToPlayer(roller, Text.translatable("message.steveparty.dice.cursed")
                .formatted(Formatting.DARK_PURPLE), MessageUtils.MessageType.ACTION_BAR);
        readModules();
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
            die.setGlowing(false); // the double of an earlier roll
            die.setFaceShown(slow);
            if (slow) {
                List<DiceFace> cycle = cycle(faces(die));
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
        if (phase == Phase.REVEALING) {
            tickReveal();
            return;
        }
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
            case REVEALING, DONE -> {
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
        if (needed > 1) {
            stopAll();
            showSet(results.getLast());
            askLucky();
        } else {
            picked(results.getFirst()); // nothing asked: the dice go on turning into the reveal
        }
    }

    private void stopAll() {
        for (DiceEntity die : lead.group()) {
            die.setSpinning(false);
            die.setFaceShown(false);
        }
    }

    private List<DiceFace> rollSet(boolean slow) {
        List<DiceFace> set = new ArrayList<>();
        for (DiceEntity die : lead.group()) {
            List<DiceFace> cycle = slow ? cycles.get(die.getUuid()) : null;
            if (cycle != null) set.add(cycle.get(cycleIndexes.getOrDefault(die.getUuid(), 0) % cycle.size()));
            else set.add(roll(die));
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
        int left = rerollsLeft();
        if (left <= 0) {
            finish(set);
            return;
        }
        stopAll();
        showSet(set);
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
        List<DiceFace> faces = faces(die);
        List<DicePrompts.Option> options = new ArrayList<>();
        for (DiceFace face : faces) options.add(optionOf(List.of(face)));
        int random = Math.max(0, faces.indexOf(roll(die)));
        // One prompt per die, shown as one section per die of the throw (the faces already picked above)
        Text title = Text.translatable(group.size() > 1 ? "gui.steveparty.dice_prompt.choice.dice" : "gui.steveparty.dice_prompt.choice");
        List<DicePrompts.Option> picked = new ArrayList<>();
        for (DiceFace face : chosen) picked.add(optionOf(List.of(face)));
        prompt = DicePrompts.askStep(lead.getOnlineOwner(), title, DicePrompts.Layout.GRID, options, promptTimeout, random,
                picked, group.size(), index -> {
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

    /** The result is final: it is revealed, die after die (see {@link #REVEAL_STEP_TICKS}). */
    private void finish(List<DiceFace> set) {
        phase = Phase.REVEALING;
        revealing = List.copyOf(set);
        revealed = 0;
        revealAge = 0;
        comboShown = 0;
        // Dice stopped for a prompt (Lucky, Reroll) turn again to land one by one; a Slow die keeps the face it was stopped on
        for (DiceEntity die : lead.group()) {
            if (!die.isRolling()) {
                die.setSpinning(true);
                die.setFaceShown(false);
            }
        }
        if (revealing.size() <= 1 || chooses()) {
            // One die, or faces the roller picked: all at once
            while (revealed < revealing.size()) revealNext();
            complete();
            return;
        }
        revealNext(); // the first die lands at once
    }

    private void tickReveal() {
        revealAge++;
        if (revealed < revealing.size()) {
            if (revealAge >= revealed * REVEAL_STEP_TICKS) revealNext();
        } else if (revealAge >= (revealing.size() - 1) * REVEAL_STEP_TICKS + REVEAL_TOTAL_TICKS) {
            complete();
        }
    }

    /** The next die lands on its face; a double / triple shows as soon as the dice make it. */
    private void revealNext() {
        List<DiceEntity> group = lead.group();
        int index = revealed++;
        DiceFace face = revealing.get(index);
        if (index < group.size()) {
            DiceEntity die = group.get(index);
            die.setSpinning(false);
            die.setFaceShown(false);
            die.showFace(face);
            DiceReveal.dieLands(die, index, revealing.size());
        }
        List<DiceFace> shown = revealing.subList(0, revealed);
        List<Integer> numbers = DiceThrow.numbers(shown);
        int same = DiceThrow.sameCount(numbers);
        if (same >= 2 && same > comboShown) {
            comboShown = Math.min(3, same);
            int number = DiceThrow.sameNumber(numbers);
            List<DiceEntity> matching = new ArrayList<>();
            for (int i = 0; i < shown.size() && i < group.size(); i++) {
                if (shown.get(i).steps() == number) matching.add(group.get(i));
            }
            DiceReveal.combo(matching, same);
        }
        if (revealed < revealing.size()) DiceReveal.send(lead, revealing.size(), shown, null);
    }

    /** All revealed: the total, then the roll is announced (and the token moves). */
    private void complete() {
        phase = Phase.DONE;
        stopAll();
        showSet(revealing);
        DiceReveal.total(lead, revealing.size());
        if (cursed) CursedRolls.lift(lead.getWorld().getServer(), lead.getOwner().orElse(null)); // spent
        lead.onRollFinished(revealing);
    }

    /** The reveal: the dice stopped so far (all of them once the roll is final). */
    public int revealed() {
        return phase == Phase.DONE ? revealing.size() : phase == Phase.REVEALING ? revealed : 0;
    }

    /** The reveal: the double (2) or triple (3) shown so far, 0 if none. */
    public int comboShown() {
        return comboShown;
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
