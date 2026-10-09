package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole throw once revealed: the faces of its dice (one per die, in the order they stopped) and what it does.
 * Double and triple read the <b>numbers</b> shown (the faces that walk steps: normal, premium, cursed; not 0, blank,
 * coins, debt or swap), the same notion as the Threshold obstacle's « Double » and « Triple »: a premium 5★ and a 5 make
 * a double. A triple is a double too ({@link #isDouble} means « at least two alike »).
 *
 * @param faces   the faces of the dice, in the order they were revealed
 * @param outcome what the throw does, modules and power-up included (its {@link DiceOutcome#steps} is the total)
 */
public record DiceThrow(List<DiceFace> faces, DiceOutcome outcome) {
    public DiceThrow {
        faces = List.copyOf(faces);
    }

    /** The total walked (negative: backward). */
    public int total() {
        return outcome.steps();
    }

    /** The dice thrown together. */
    public int dice() {
        return faces.size();
    }

    /** The numbers shown, one per die showing a number (see {@link #numbers(List)}). */
    public List<Integer> numbers() {
        return numbers(faces);
    }

    /** At least two dice show the same number (a triple included). */
    public boolean isDouble() {
        return sameCount() >= 2;
    }

    /** At least three dice show the same number. */
    public boolean isTriple() {
        return sameCount() >= 3;
    }

    /** The most dice showing one same number (1 when all differ, 0 without a number). */
    public int sameCount() {
        return sameCount(numbers());
    }

    /** The number shown by the most dice (the first to get there on a tie), -1 without a number. */
    public int sameNumber() {
        return sameNumber(numbers());
    }

    /** The numbers of {@code faces} (the faces that walk steps), in order. */
    public static List<Integer> numbers(List<DiceFace> faces) {
        List<Integer> numbers = new ArrayList<>();
        for (DiceFace face : faces) {
            if (face.steps() > 0) numbers.add(face.steps());
        }
        return numbers;
    }

    /** At least {@code count} of {@code numbers} are the same (a pair: 2, three of a kind: 3). */
    public static boolean hasSame(List<Integer> numbers, int count) {
        return sameCount(numbers) >= count;
    }

    /** The most of {@code numbers} that are the same. */
    public static int sameCount(List<Integer> numbers) {
        Map<Integer, Integer> seen = new HashMap<>();
        int most = 0;
        for (int number : numbers) most = Math.max(most, seen.merge(number, 1, Integer::sum));
        return most;
    }

    /** The number the most of {@code numbers} are (the first to get there on a tie), -1 if none. */
    public static int sameNumber(List<Integer> numbers) {
        Map<Integer, Integer> seen = new HashMap<>();
        int most = 0, number = -1;
        for (int n : numbers) {
            int count = seen.merge(n, 1, Integer::sum);
            if (count > most) {
                most = count;
                number = n;
            }
        }
        return number;
    }
}
