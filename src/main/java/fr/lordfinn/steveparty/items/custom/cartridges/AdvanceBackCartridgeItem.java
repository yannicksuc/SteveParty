package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.SneakScrollItem;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * The Move Forward / Back cartridge: a token ending its move on its tile moves on {@link #steps} more spaces, forward
 * (1..6) or back (-1..-6). Its menu has the direction and the number; sneak + mouse wheel with it in the main hand
 * changes them too.
 */
public class AdvanceBackCartridgeItem extends CartridgeItem implements SneakScrollItem {
    public static final int MAX_STEPS = 6;
    public static final int DEFAULT_STEPS = 3;
    /** The tile's colours (face, sides, landing particles): green forward, pink-magenta back. */
    public static final int FORWARD_COLOR = 0x2DB84C;
    public static final int BACK_COLOR = 0xE23C9A;

    private static final String K = MENU_KEY + "advance.";
    private static final List<CartridgeModule> MODULES = List.of(
            new ChoiceModule("direction", K + "direction",
                    List.of(new ChoiceModule.Option(K + "back"), new ChoiceModule.Option(K + "forward")),
                    stack -> steps(stack) < 0 ? 0 : 1,
                    (edit, value) -> edit.stack().set(ModComponents.ADVANCE_BACK_STEPS,
                            (value == 0 ? -1 : 1) * Math.abs(steps(edit.stack())))),
            new NumberModule("steps", K + "steps", 1, MAX_STEPS,
                    stack -> Math.abs(steps(stack)),
                    (edit, value) -> edit.stack().set(ModComponents.ADVANCE_BACK_STEPS,
                            (steps(edit.stack()) < 0 ? -1 : 1) * value),
                    stack -> color(steps(stack))),
            InfoModule.hint("hint", K + "hint", 1));

    public AdvanceBackCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_ADVANCE_BACK;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int menuColor(ItemStack stack) {
        return color(steps(stack));
    }

    /** Spaces to move: 1..6 forward, -1..-6 back (never 0: a cartridge without setting moves 3 forward). */
    public static int steps(ItemStack stack) {
        int steps = stack == null ? 0 : stack.getOrDefault(ModComponents.ADVANCE_BACK_STEPS, 0);
        if (steps == 0) return DEFAULT_STEPS;
        return Math.max(-MAX_STEPS, Math.min(MAX_STEPS, steps));
    }

    public static int color(int steps) {
        return steps < 0 ? BACK_COLOR : FORWARD_COLOR;
    }

    /** A new cartridge moving {@code steps} spaces. */
    public static ItemStack withSteps(int steps) {
        ItemStack stack = new ItemStack(ModItems.ADVANCE_BACK_CARTRIDGE);
        stack.set(ModComponents.ADVANCE_BACK_STEPS, steps);
        return stack;
    }

    /**
     * The next setting on the wheel: -6 .. -1, +1 .. +6 (0 skipped), stopping at both ends.
     *
     * @param direction +1 toward forward, -1 toward back
     */
    public static int scrolled(int steps, int direction) {
        if (direction == 0) return steps;
        int next = steps + Integer.signum(direction);
        if (next == 0) next += Integer.signum(direction);
        return Math.max(-MAX_STEPS, Math.min(MAX_STEPS, next));
    }

    /** Changes the setting of the cartridge by one notch of the wheel. Server side. */
    public static void scroll(ItemStack stack, int direction) {
        stack.set(ModComponents.ADVANCE_BACK_STEPS, scrolled(steps(stack), direction));
    }

    /** Sneak + wheel: the new setting, shown in the action bar with a click pitched by the number of spaces. */
    @Override
    public void onSneakScroll(ServerPlayerEntity player, ItemStack stack, int direction) {
        scroll(stack, Integer.signum(direction));
        int steps = steps(stack);
        player.sendMessage(settingText(steps), true);
        player.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.4F,
                (float) Math.pow(2, steps / 12.0));
    }

    /** "⏩ Moves forward: 3" in green, or "⏪ Moves back: 2" in purple-red. */
    public static MutableText settingText(int steps) {
        return Text.translatable(steps < 0 ? "tooltip.steveparty.advance_back.back" : "tooltip.steveparty.advance_back.forward",
                        Math.abs(steps))
                .styled(headline(steps < 0 ? 0xF07ABB : 0x6FE38A));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(settingText(steps(stack)));
        super.appendTooltip(stack, context, tooltip, type);
        addWrapped(tooltip, Text.translatable("tooltip.steveparty.advance_back.controls"), Formatting.DARK_GRAY);
    }
}
