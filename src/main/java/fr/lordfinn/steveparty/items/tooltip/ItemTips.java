package fr.lordfinn.steveparty.items.tooltip;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The tooltips of the items that have no class of their own (plain items, block items of blocks without a tooltip):
 * the same layout as the others ({@link Tooltips}), added by the client's tooltip callback.
 */
public final class ItemTips {
    private static final String KEY = "tooltip.steveparty.";
    private static Map<Item, BiConsumer<ItemStack, Tooltips>> tips;

    private ItemTips() {
    }

    /** Adds the tooltip of {@code stack}'s item, if it is one of these; false otherwise. */
    public static boolean append(ItemStack stack, List<Text> lines) {
        BiConsumer<ItemStack, Tooltips> tip = all().get(stack.getItem());
        if (tip == null) return false;
        tip.accept(stack, Tooltips.of(lines));
        return true;
    }

    /** Every item given a tooltip here (tests). */
    public static Map<Item, BiConsumer<ItemStack, Tooltips>> all() {
        if (tips == null) tips = build();
        return tips;
    }

    private static Item item(String id) {
        Item item = Registries.ITEM.get(Steveparty.id(id));
        if (item == Items.AIR) throw new IllegalStateException("no item steveparty:" + id);
        return item;
    }

    /** A summary and, behind Shift, what to do with it ({@code tooltip.steveparty.<id>.summary} / {@code .use}...). */
    private static BiConsumer<ItemStack, Tooltips> simple(String id, Tooltips.Tag... tags) {
        return (stack, tips) -> tips.tags(tags).summary(KEY + id + ".summary");
    }

    private static Map<Item, BiConsumer<ItemStack, Tooltips>> build() {
        Map<Item, BiConsumer<ItemStack, Tooltips>> map = new LinkedHashMap<>();
        // --- Items
        map.put(item("coin"), simple("coin"));
        map.put(item("party_star"), simple("party_star"));
        map.put(item("plunger"), (stack, tips) -> tips
                .summary(KEY + "plunger.summary")
                .more(more -> more.use(Tooltips.Keys.use(), KEY + "plunger.use")));
        map.put(item("triple_jump_shoes"), (stack, tips) -> tips.summary(KEY + "triple_jump_shoes.summary"));
        map.put(item("mini_games_catalogue"), (stack, tips) -> tips
                .summary(KEY + "mini_games_catalogue.summary")
                .more(more -> more
                        .use(Tooltips.Keys.use(), KEY + "mini_games_catalogue.use")
                        .note(KEY + "mini_games_catalogue.controller")));
        map.put(item("acorn"), (stack, tips) -> tips
                .summary(KEY + "acorn.summary")
                .more(more -> more.use(Tooltips.Keys.use(), KEY + "acorn.use")));
        // --- Dice faces: only for the Dice Forge
        BiConsumer<ItemStack, Tooltips> face = (stack, tips) -> tips.summary(KEY + "dice_face.summary");
        map.put(item("blank_dice_face"), face);
        for (int i = 1; i <= 10; i++) {
            map.put(item("dice_face_" + i), face);
            map.put(item("premium_dice_face_" + i), face);
        }
        for (int i = 1; i <= 3; i++) map.put(item("cursed_dice_face_" + i), face);
        map.put(item("dice_face_0"), (stack, tips) -> tips.summary(KEY + "dice_face_0.summary"));
        for (int i = 1; i <= 10; i++) {
            int value = i;
            map.put(item("coin_dice_face_" + i), (stack, tips) -> tips
                    .summary(KEY + "coin_dice_face.summary", Tooltips.coins(value))
                    .more(more -> more.note(KEY + "dice_face.no_move")));
            map.put(item("debt_dice_face_" + i), (stack, tips) -> tips
                    .summary(KEY + "debt_dice_face.summary", Tooltips.bad(value))
                    .more(more -> more.note(KEY + "dice_face.no_move")));
        }
        map.put(item("swap_dice_face"), (stack, tips) -> tips
                .summary(KEY + "swap_dice_face.summary")
                .more(more -> more.note(KEY + "dice_face.no_move")));
        // --- Blocks
        map.put(item("hop_switch"), (stack, tips) -> tips
                .summary(KEY + "hop_switch.summary")
                .more(more -> more
                        .use(Tooltips.Keys.use(), KEY + "hop_switch.use")
                        .note(KEY + "hop_switch.brush")));
        map.put(item("piggy_bank"), (stack, tips) -> tips
                .summary(KEY + "piggy_bank.summary")
                .more(more -> more.use(Tooltips.Keys.use(), KEY + "piggy_bank.use")));
        map.put(item("party_bell"), (stack, tips) -> tips
                .summary(KEY + "party_bell.summary")
                .more(more -> more.use(Tooltips.Keys.use(), KEY + "party_bell.use")));
        map.put(item("party_controller"), (stack, tips) -> tips
                .summary(KEY + "party_controller.summary")
                .more(more -> more.use(Tooltips.Keys.use(), KEY + "party_controller.use")));
        map.put(item("step_controller"), (stack, tips) -> tips
                .summary(KEY + "step_controller.summary")
                .more(more -> more.use(Tooltips.Keys.use(), KEY + "step_controller.use")));
        map.put(item("goal_pole_base"), (stack, tips) -> tips
                .summary(KEY + "goal_pole_base.summary")
                .more(more -> more.use(Tooltips.Keys.use(), KEY + "goal_pole_base.use")));
        map.put(item("dice_forge"), simple("dice_forge"));
        map.put(item("looting_box"), (stack, tips) -> tips
                .summary(KEY + "looting_box.summary")
                .more(more -> more
                        .use(Tooltips.Keys.use(), KEY + "looting_box.use")
                        .note(KEY + "looting_box.rules")));
        map.put(item("magpie_nest"), (stack, tips) -> tips
                .summary(KEY + "magpie_nest.summary")
                .more(more -> more.use(Tooltips.Keys.use(), KEY + "magpie_nest.use").note(KEY + "magpie_nest.pot")));
        map.put(item("stencil_maker"), simple("stencil_maker"));
        map.put(item("check_point"), (stack, tips) -> tips.summary(KEY + "check_point.summary"));
        return map;
    }
}
