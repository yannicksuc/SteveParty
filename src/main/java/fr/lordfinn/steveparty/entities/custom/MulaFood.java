package fr.lordfinn.steveparty.entities.custom;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * What a Mula eats: something both of ITS COLOUR and EDIBLE.
 * <ul>
 *   <li><b>Food</b> (an item with the vanilla {@code food} component) listed for its colour below; the satiety it gives
 *   is the food's nutrition (the hunger points it gives a player).</li>
 *   <li><b>Potions</b> (drinkable ones) whose liquid colour is closest to the Mula's colour; they give, summed over
 *   their effects: {@code minutes x (level x 2)}, each resulting minute counting as 1 point (Strength II for 3:00 =
 *   3 x (2 x 2) = 12). Instant effects count as one minute. The empty bottle goes back to the player.</li>
 * </ul>
 * Anything else (a dye, an ore, a potion of another colour, water...) is gently refused.
 * <p>
 * "Its colour" for foods (vanilla food items, grouped by what they look like):
 * blue = the cold-sea fish (cod, tropical fish); red = apple, sweet berries, salmon, raw beef and mutton;
 * green = dried kelp, melon slice, poisonous potato; yellow = golden apples and carrot, honey, baked potato, bread,
 * glow berries; purple = chorus fruit, beetroot and its soup; black = the dark foods (cookie, mushroom and rabbit
 * stews, steak, cooked mutton).
 */
public final class MulaFood {
    private MulaFood() {
    }

    private static final Map<MulaEntity.MulaVariant, Set<Item>> FOODS = new EnumMap<>(MulaEntity.MulaVariant.class);

    static {
        FOODS.put(MulaEntity.MulaVariant.BLUE, Set.of(Items.COD, Items.COOKED_COD, Items.TROPICAL_FISH));
        FOODS.put(MulaEntity.MulaVariant.RED, Set.of(Items.APPLE, Items.SWEET_BERRIES, Items.SALMON, Items.COOKED_SALMON,
                Items.BEEF, Items.MUTTON));
        FOODS.put(MulaEntity.MulaVariant.GREEN, Set.of(Items.DRIED_KELP, Items.MELON_SLICE, Items.POISONOUS_POTATO));
        FOODS.put(MulaEntity.MulaVariant.YELLOW, Set.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE,
                Items.GOLDEN_CARROT, Items.HONEY_BOTTLE, Items.BAKED_POTATO, Items.BREAD, Items.GLOW_BERRIES));
        FOODS.put(MulaEntity.MulaVariant.PURPLE, Set.of(Items.CHORUS_FRUIT, Items.BEETROOT, Items.BEETROOT_SOUP));
        FOODS.put(MulaEntity.MulaVariant.BLACK, Set.of(Items.COOKIE, Items.MUSHROOM_STEW, Items.RABBIT_STEW,
                Items.COOKED_BEEF, Items.COOKED_MUTTON));
    }

    /** The foods of this colour (for tests, the tooltip and the test scene). */
    public static Set<Item> foodsOf(MulaEntity.MulaVariant variant) {
        return FOODS.get(variant);
    }

    /** Satiety this stack gives a Mula of this colour, 0 if it doesn't eat it. */
    public static int value(MulaEntity.MulaVariant variant, ItemStack stack) {
        if (stack.isEmpty()) return 0;
        if (stack.isOf(Items.POTION)) {
            PotionContentsComponent potion = stack.get(DataComponentTypes.POTION_CONTENTS);
            if (potion == null || colourOf(potion.getColor()) != variant) return 0;
            return potionValue(potion.getEffects());
        }
        FoodComponent food = stack.get(DataComponentTypes.FOOD);
        if (food == null || !FOODS.get(variant).contains(stack.getItem())) return 0;
        return Math.max(1, food.nutrition());
    }

    /** Sum over the effects of minutes x (level x 2); an instant effect counts as one minute. */
    public static int potionValue(Iterable<StatusEffectInstance> effects) {
        int total = 0;
        for (StatusEffectInstance effect : effects) {
            int minutes = effect.getEffectType().value().isInstant() ? 1 : Math.max(1, effect.getDuration() / 1200);
            total += minutes * (effect.getAmplifier() + 1) * 2;
        }
        return total;
    }

    /** "Minutes x (level x 2)" spelled out for the feedback message, e.g. "3 min x (2 x 2)". */
    public static String potionFormula(Iterable<StatusEffectInstance> effects) {
        StringBuilder text = new StringBuilder();
        for (StatusEffectInstance effect : effects) {
            int minutes = effect.getEffectType().value().isInstant() ? 1 : Math.max(1, effect.getDuration() / 1200);
            if (!text.isEmpty()) text.append(" + ");
            text.append(minutes).append(" min x (").append(effect.getAmplifier() + 1).append(" x 2)");
        }
        return text.toString();
    }

    /** The Mula colour closest to a liquid colour (RGB): dark ones are black's, otherwise the nearest hue. */
    public static MulaEntity.MulaVariant colourOf(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        if (max < 90) return MulaEntity.MulaVariant.BLACK;
        MulaEntity.MulaVariant best = MulaEntity.MulaVariant.BLUE;
        double bestDistance = Double.MAX_VALUE;
        for (MulaEntity.MulaVariant v : MulaEntity.MulaVariant.values()) {
            if (v == MulaEntity.MulaVariant.BLACK) continue;
            double d = hueDistance(hue(r, g, b, max, min), hueOf(v.getColor()));
            if (d < bestDistance) {
                bestDistance = d;
                best = v;
            }
        }
        return best;
    }

    private static double hueOf(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return hue(r, g, b, Math.max(r, Math.max(g, b)), Math.min(r, Math.min(g, b)));
    }

    private static double hue(int r, int g, int b, int max, int min) {
        if (max == min) return 0;
        double d = max - min, h;
        if (max == r) h = ((g - b) / d) % 6;
        else if (max == g) h = (b - r) / d + 2;
        else h = (r - g) / d + 4;
        h *= 60;
        return h < 0 ? h + 360 : h;
    }

    private static double hueDistance(double a, double b) {
        double d = Math.abs(a - b) % 360;
        return Math.min(d, 360 - d);
    }
}
