package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import fr.lordfinn.steveparty.service.MistigriSentences.Sentence;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Mistigri Cartridge: a token stopping on its tile meets the Mistigri, who leaps onto the space and rolls his giant
 * loaded die: it draws a sentence for the token's player (see MistigriTileBehavior and MistigriSentences). Its menu sets
 * how likely each sentence is (a weight, 0 to {@link #MAX_WEIGHT}: 0 never drawn) and the amounts: the small and the
 * big fine, what everyone pays, how far back. Its tile is witch purple; a dye on it changes that.
 * <p>
 * The settings live in one component ({@link ModComponents#MISTIGRI_SETTINGS}), by name: {@code w_<sentence>} for the
 * weights, then {@link #COINS_SMALL}, {@link #COINS_BIG}, {@link #EVERYONE}, {@link #BACK}; a missing one is its default.
 */
public class MistigriCartridgeItem extends CartridgeItem {
    /** Its tile's witch purple. */
    public static final int COLOR = 0x6B2FA0;
    public static final int MAX_WEIGHT = 9, MAX_COINS = 99, MAX_BACK = 6;
    public static final String COINS_SMALL = "coins_small", COINS_BIG = "coins_big", EVERYONE = "everyone", BACK = "back";
    private static final Map<String, Integer> DEFAULTS = Map.of(COINS_SMALL, 10, COINS_BIG, 20, EVERYONE, 5, BACK, 3);

    private static final String K = MENU_KEY + "mistigri.";
    private static final List<CartridgeModule> MODULES = modules0();

    private static List<CartridgeModule> modules0() {
        List<CartridgeModule> modules = new ArrayList<>();
        modules.add(description("mistigri_cartridge", 3));
        for (Sentence sentence : Sentence.values()) {
            modules.add(new NumberModule("w_" + sentence.id, K + "weight." + sentence.id, 0, MAX_WEIGHT,
                    stack -> weight(stack, sentence), (edit, value) -> put(edit.stack(), "w_" + sentence.id, value),
                    stack -> COLOR));
            switch (sentence) {
                case COINS_SMALL -> modules.add(amount(COINS_SMALL, MAX_COINS, sentence));
                case COINS_BIG -> modules.add(amount(COINS_BIG, MAX_COINS, sentence));
                case EVERYONE -> modules.add(amount(EVERYONE, MAX_COINS, sentence));
                case BACK -> modules.add(amount(BACK, MAX_BACK, sentence));
                default -> {
                }
            }
        }
        modules.add(new InfoModule("hint", null, 2, context -> List.of(
                new InfoModule.Line(Text.translatable(K + "hint"), InfoModule.Tone.SOFT))));
        return List.copyOf(modules);
    }

    /** The amount of a sentence, greyed out while that sentence is never drawn. */
    private static NumberModule amount(String key, int max, Sentence sentence) {
        return new NumberModule(key, K + key, 1, max, stack -> amount(stack, key),
                (edit, value) -> put(edit.stack(), key, value), stack -> COLOR, stack -> weight(stack, sentence) > 0);
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
    public int menuColor(ItemStack stack) {
        return stack.getOrDefault(ModComponents.COLOR, COLOR) & 0xFFFFFF;
    }

    private static Map<String, Integer> settings(ItemStack stack) {
        return stack == null ? Map.of() : stack.getOrDefault(ModComponents.MISTIGRI_SETTINGS, Map.of());
    }

    private static void put(ItemStack stack, String key, int value) {
        Map<String, Integer> settings = new HashMap<>(settings(stack));
        settings.put(key, value);
        stack.set(ModComponents.MISTIGRI_SETTINGS, Map.copyOf(settings));
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
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int total = 0;
        for (Sentence sentence : Sentence.values()) total += weight(stack, sentence);
        for (Sentence sentence : Sentence.values()) {
            int weight = weight(stack, sentence);
            if (weight == 0) continue;
            tooltip.add(Text.translatable("tooltip.steveparty.mistigri_cartridge.sentence",
                            Text.translatable("message.steveparty.mistigri_space.sentence." + sentence.id, sentence.amount(stack)),
                            Math.round(100f * weight / Math.max(1, total)))
                    .styled(style -> style.withColor(TextColor.fromRgb(0xC9A2F0))));
        }
        if (total == 0) tooltip.add(Text.translatable("tooltip.steveparty.mistigri_cartridge.none").formatted(Formatting.GRAY));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
