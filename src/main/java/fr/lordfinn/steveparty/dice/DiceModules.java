package fr.lordfinn.steveparty.dice;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.items.custom.DiceModuleItem;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry of the dice modules ({@link DiceModule}). Registering a module here is all it takes for its item to
 * exist ({@code ModItems} registers one {@code dice_module_<id>} per module), for the Dice Forge and the crafting
 * table to put it on dice, and for the dice to apply it.
 * <p>
 * How they combine on one die:
 * <ul>
 *     <li><b>Choice</b> decides everything: the roller picks the face (of each die thrown together); Slow, Lucky and
 *     Reroll do nothing on that die;</li>
 *     <li><b>Slow</b> applies to every roll: the roller stops the die by hand each time;</li>
 *     <li><b>Lucky ×N</b>: N + 1 rolls, the roller keeps the result they prefer;</li>
 *     <li><b>Reroll ×N</b>: once the result is known (after the Lucky pick), keep it or roll everything again, up to
 *     N times; the last roll is final;</li>
 *     <li><b>Reversed</b> applies to the final result; <b>Power-up</b>, <b>Skeleton Key</b> and <b>Homing</b> are
 *     independent of the others, and so is <b>Firecracker</b> (the die's burst hurts and knocks back).</li>
 * </ul>
 */
public final class DiceModules {
    private static final Map<String, DiceModule> REGISTRY = new LinkedHashMap<>();

    /** The thrown die turns slowly, showing its faces one after the other: its roller hits it to stop it on one. */
    public static final DiceModule SLOW = register(new DiceModule("slow", 1, false) {
        @Override
        public boolean stoppedByHand() {
            return true;
        }
    });
    /** The roller picks the face the die stops on. */
    public static final DiceModule CHOICE = register(new DiceModule("choice", 1, false) {
        @Override
        public boolean rollerChooses() {
            return true;
        }
    });
    /**
     * The die is a power-up: spent once rolled (any other die goes back to its roller); in a party it is rolled in
     * place of the player's die, counts in the « Max power-ups » and is the power-up of the turn.
     */
    public static final DiceModule POWER_UP = register(new DiceModule("power_up", 1, false) {
        @Override
        public boolean makesPowerUp() {
            return true;
        }

        @Override
        public Text itemDescription() {
            return Text.translatable("dice_module.steveparty.power_up.item_desc");
        }
    });
    /** One more roll per module: the roller keeps the result they prefer. */
    public static final DiceModule LUCKY = register(new DiceModule("lucky", 5, false) {
        @Override
        public int extraRolls(int count) {
            return count;
        }

        @Override
        protected int descriptionValue(int count) {
            return count + 1; // "rolled %s times"
        }
    });
    /** Stop or go on: one more possible reroll per module. */
    public static final DiceModule REROLL = register(new DiceModule("reroll", 5, false) {
        @Override
        public int rerolls(int count) {
            return count;
        }
    });
    /** The result is inverted: the token walks backward, the coin faces take instead of giving (and the reverse). */
    public static final DiceModule REVERSED = register(new DiceModule("reversed", 1, true) {
        @Override
        public DiceOutcome modifyOutcome(DiceOutcome outcome, int count) {
            return outcome.reversed();
        }
    });
    /** During this move the token ignores the Stop spaces and the shop check points. */
    public static final DiceModule SKELETON_KEY = register(new DiceModule("skeleton_key", 1, false) {
        @Override
        public boolean ignoresStops() {
            return true;
        }
    });
    /**
     * At a fork the token takes a branch by itself: a random one for now. A real objective (the nearest shop, the
     * star...) plugs in here: return the branch leading to it instead.
     */
    public static final DiceModule HOMING = register(new DiceModule("homing", 1, false) {
        @Override
        public @Nullable BoardSpaceDestination chooseFork(MobEntity token, List<BoardSpaceDestination> forks, Random random) {
            return forks.isEmpty() ? null : forks.get(random.nextInt(forks.size()));
        }
    });

    /**
     * The die bursts for real when it goes away: its blast hurts the players and mobs around it (three times what a
     * plain die's firework would) and throws them back hard (see DiceEntity). A plain die's burst hurts no one.
     */
    public static final DiceModule FIRECRACKER = register(new DiceModule("firecracker", 1, false));

    private DiceModules() {
    }

    public static <T extends DiceModule> T register(T module) {
        if (REGISTRY.putIfAbsent(module.id(), module) != null)
            throw new IllegalStateException("Die module registered twice: " + module.id());
        return module;
    }

    public static @Nullable DiceModule get(String id) {
        return REGISTRY.get(id);
    }

    /** Every module, in registration order. */
    public static Collection<DiceModule> all() {
        return List.copyOf(REGISTRY.values());
    }

    /** @return the module a module item stands for, null for any other item. */
    public static @Nullable DiceModule fromItem(@Nullable ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof DiceModuleItem item ? item.module() : null;
    }

    public static boolean isModuleItem(@Nullable ItemStack stack) {
        return fromItem(stack) != null;
    }

    /** The modules a die carries and how many of each, in registration order. */
    public static Map<DiceModule, Integer> of(@Nullable ItemStack die) {
        Map<DiceModule, Integer> modules = new LinkedHashMap<>();
        DiceModulesComponent component = die == null || die.isEmpty() ? null : die.get(DiceModulesComponent.TYPE);
        if (component == null) return modules;
        for (DiceModule module : REGISTRY.values()) {
            Integer count = component.counts().get(module.id());
            if (count != null && count > 0) modules.put(module, Math.min(count, module.maxCount()));
        }
        return modules;
    }

    public static int count(@Nullable ItemStack die, DiceModule module) {
        return of(die).getOrDefault(module, 0);
    }

    public static boolean has(@Nullable ItemStack die, DiceModule module) {
        return count(die, module) > 0;
    }

    /** True if the die is a power-up, spent once rolled (a module says so: Power-up). */
    public static boolean isPowerUp(@Nullable ItemStack die) {
        for (DiceModule module : of(die).keySet()) {
            if (module.makesPowerUp()) return true;
        }
        return false;
    }

    /** True if the die goes back to its roller once rolled: any die but a power-up one. */
    public static boolean returnsToRoller(@Nullable ItemStack die) {
        return die != null && !die.isEmpty() && !isPowerUp(die);
    }

    /** Puts these modules on a die, replacing what it carried (none: removes the component). Counts are capped. */
    public static ItemStack set(ItemStack die, Map<DiceModule, Integer> modules) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        modules.forEach((module, count) -> {
            if (count != null && count > 0) counts.put(module.id(), Math.min(count, module.maxCount()));
        });
        if (counts.isEmpty()) die.remove(DiceModulesComponent.TYPE);
        else die.set(DiceModulesComponent.TYPE, new DiceModulesComponent(counts));
        return die;
    }

    /** Adds {@code count} of a module to what {@code modules} holds, up to its maximum. */
    public static void add(Map<DiceModule, Integer> modules, DiceModule module, int count) {
        modules.merge(module, count, (a, b) -> Math.min(module.maxCount(), a + b));
        modules.computeIfPresent(module, (key, value) -> Math.min(module.maxCount(), value));
    }

    /** The union of two sets of modules: for each module, the highest of the two counts. */
    public static Map<DiceModule, Integer> union(Map<DiceModule, Integer> a, Map<DiceModule, Integer> b) {
        Map<DiceModule, Integer> union = new LinkedHashMap<>();
        for (DiceModule module : REGISTRY.values()) {
            int count = Math.max(a.getOrDefault(module, 0), b.getOrDefault(module, 0));
            if (count > 0) union.put(module, count);
        }
        return union;
    }

    /** One tooltip line per module of a die: its name, its count if several, and what it does. */
    public static List<Text> tooltip(@Nullable ItemStack die) {
        List<Text> lines = new ArrayList<>();
        of(die).forEach((module, count) -> lines.add(line(module, count)));
        return lines;
    }

    /** "◆ Lucky ×2: rolled 3 times, keep the result you prefer"; all red for a negative module. */
    public static Text line(DiceModule module, int count) {
        MutableText line = Text.literal("◆ ").formatted(module.color()).append(module.name().copy().formatted(module.color()));
        if (count > 1) line.append(Text.literal(" ×" + count).formatted(module.color()));
        line.append(Text.literal(": ").formatted(module.negative() ? Formatting.RED : Formatting.DARK_GRAY));
        line.append(module.description(count).copy().formatted(module.negative() ? Formatting.RED : Formatting.GRAY));
        return line;
    }
}
