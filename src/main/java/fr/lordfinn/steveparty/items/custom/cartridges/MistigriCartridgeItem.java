package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import fr.lordfinn.steveparty.service.MistigriSentences.Sentence;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Mistigri Cartridge: a token stopping on its tile meets the Mistigri, who leaps onto the space and rolls his giant
 * loaded die: it draws a sentence for the token's player (see MistigriTileBehavior and MistigriSentences). Its menu sets
 * how likely each sentence is (a weight, 0 to {@link #MAX_WEIGHT}: 0 never drawn) and the amounts: the small and the
 * big fine, what everyone pays, how far back. Its tile is witch plum; a dye on it changes that.
 * <p>
 * The settings live in one component ({@link ModComponents#MISTIGRI_SETTINGS}), by name: {@code w_<sentence>} for the
 * weights, then {@link #COINS_SMALL}, {@link #COINS_BIG}, {@link #EVERYONE}, {@link #BACK}; a missing one is its default.
 */
public class MistigriCartridgeItem extends CartridgeItem {
    /** Its tile's witch plum. */
    public static final int COLOR = 0x8A2A6E;
    public static final int MAX_WEIGHT = 9, MAX_COINS = 99, MAX_BACK = 6;
    public static final String COINS_SMALL = "coins_small", COINS_BIG = "coins_big", EVERYONE = "everyone", BACK = "back";
    private static final Map<String, Integer> DEFAULTS = Map.of(COINS_SMALL, 10, COINS_BIG, 20, EVERYONE, 5, BACK, 3);

    private static final String K = MENU_KEY + "mistigri.";
    /** The sentences' swatches in the menu: gold, orange, rust, star yellow, red, rewind pink, cursed violet, pity green. */
    private static final int[] SWATCHES = {0xE8C547, 0xE07B2A, 0xA0522D, 0xFFD83D, 0xD94040, 0xE23C9A, 0x912CD6, 0x6CBF4A};
    private static final List<CartridgeModule> MODULES = modules0();

    /**
     * One column beside a tile: the sentence picked in a row of swatches, then its weight, its amount (greyed out for
     * the sentences without one) and how often it is drawn.
     */
    private static List<CartridgeModule> modules0() {
        List<ChoiceModule.Option> options = new ArrayList<>();
        for (Sentence sentence : Sentence.values()) {
            options.add(new ChoiceModule.Option(K + "sentence." + sentence.id, SWATCHES[sentence.ordinal()],
                    "message.steveparty.mistigri_space.sentence." + sentence.id));
        }
        return List.of(
                description("mistigri_cartridge", 3),
                new ChoiceModule("sentence", K + "sentence", options, stack -> selected(stack).ordinal(),
                        (edit, value) -> put(edit.stack(), EDITED, value)),
                new NumberModule("weight", K + "weight", 0, MAX_WEIGHT, stack -> weight(stack, selected(stack)),
                        (edit, value) -> put(edit.stack(), "w_" + selected(edit.stack()).id, value), stack -> COLOR),
                new NumberModule("amount", K + "amount", 1, MAX_COINS, stack -> amountOf(stack, selected(stack)),
                        (edit, value) -> {
                            String key = amountKey(selected(edit.stack()));
                            if (key != null) put(edit.stack(), key, value);
                        }, stack -> COLOR, stack -> amountKey(selected(stack)) != null),
                new InfoModule("chance", null, 1, context -> List.of(new InfoModule.Line(
                        Text.translatable(K + "chance", chance(context.stack(), selected(context.stack()))), InfoModule.Tone.SOFT))));
    }

    /** The settings key of the sentence being edited in the menu. */
    private static final String EDITED = "edited";

    /** The sentence the menu edits now. */
    public static Sentence selected(ItemStack stack) {
        int index = settings(stack).getOrDefault(EDITED, 0);
        return Sentence.values()[Math.clamp(index, 0, Sentence.values().length - 1)];
    }

    /** The amount setting of {@code sentence}, null if it has none. */
    public static String amountKey(Sentence sentence) {
        return switch (sentence) {
            case COINS_SMALL -> COINS_SMALL;
            case COINS_BIG -> COINS_BIG;
            case EVERYONE -> EVERYONE;
            case BACK -> BACK;
            default -> null;
        };
    }

    private static int amountOf(ItemStack stack, Sentence sentence) {
        String key = amountKey(sentence);
        return key == null ? sentence.amount(stack) : amount(stack, key);
    }

    /** How often {@code sentence} is drawn, in percent. */
    public static int chance(ItemStack stack, Sentence sentence) {
        int total = 0;
        for (Sentence each : Sentence.values()) total += weight(stack, each);
        return total == 0 ? 0 : Math.round(100f * weight(stack, sentence) / total);
    }

    public MistigriCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_MISTIGRI;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    private static Map<String, Integer> settings(ItemStack stack) {
        return IntMaps.of(stack, ModComponents.MISTIGRI_SETTINGS);
    }

    private static void put(ItemStack stack, String key, int value) {
        IntMaps.put(stack, ModComponents.MISTIGRI_SETTINGS, key, value);
    }

    /** How likely {@code sentence} is: 0 (never) to {@link #MAX_WEIGHT}. */
    public static int weight(ItemStack stack, Sentence sentence) {
        return Math.clamp(settings(stack).getOrDefault("w_" + sentence.id, sentence.defaultWeight), 0, MAX_WEIGHT);
    }

    /** One of the amounts ({@link #COINS_SMALL}, {@link #COINS_BIG}, {@link #EVERYONE}, {@link #BACK}). */
    public static int amount(ItemStack stack, String key) {
        int max = BACK.equals(key) ? MAX_BACK : MAX_COINS;
        return Math.clamp(settings(stack).getOrDefault(key, DEFAULTS.getOrDefault(key, 1)), 1, max);
    }

    /** Sets a sentence's weight (tests, commands). */
    public static void setWeight(ItemStack stack, Sentence sentence, int weight) {
        put(stack, "w_" + sentence.id, Math.clamp(weight, 0, MAX_WEIGHT));
    }

    /** Sets an amount (tests, commands). */
    public static void setAmount(ItemStack stack, String key, int amount) {
        put(stack, key, amount);
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        int total = 0;
        for (Sentence sentence : Sentence.values()) total += weight(stack, sentence);
        if (total == 0) tips.warn(Text.translatable("tooltip.steveparty.mistigri_cartridge.none"));
    }
}
