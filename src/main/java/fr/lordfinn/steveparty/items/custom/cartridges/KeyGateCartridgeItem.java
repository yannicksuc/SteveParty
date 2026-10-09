package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.NumberModule;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.List;

/**
 * The Key gate Cartridge: a gate on some exits of its board space (each side, north, east, south or west, locked or
 * not: all of them by default). A token leaving the space by a locked exit needs a Gate Key (an item of the
 * {@code steveparty:gate_keys} tag), used up to open the gate; without one its player may wait there, replay the move
 * from where it started, or go back to the start (see KeyGates). The gate may stay open after a key opened it: for
 * some rounds, or for good. Its tile and its gates' veil are teal; a dye on it changes both.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@code lock_<side>} (0: that exit is free, 1 locked; locked
 * by default), {@link #WITHOUT_KEY} (what is offered without a key besides waiting: replaying the move, going back to the start; both by default),
 * {@link #STAY_OPEN} (rounds the gate stays open after a key opened it: 0 it closes behind, {@link #FOREVER} for good).
 * Remembered ({@link ModComponents#BOARD_CARTRIDGE_STATE}): {@link #OPENED} (the round it was opened at, -1 for good).
 */
public class KeyGateCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's and its gates' teal. */
    public static final int COLOR = 0x109090;
    public static final String WITHOUT_KEY = "without_key", STAY_OPEN = "stay_open", OPENED = "opened";
    /** {@link #WITHOUT_KEY}: what is offered besides waiting, 1 replaying the move, 2 going back to the start (both: 3). */
    public static final int REPLAY = 1, BACK_TO_START = 2;
    /** {@link #STAY_OPEN}: open for good. */
    public static final int FOREVER = 10;
    /** The sides, in the order of the menu's buttons. */
    public static final List<Direction> SIDES = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final String EDITED = "side";

    private static final String K = MENU_KEY + "key_gate.";
    private static final List<CartridgeModule> MODULES = List.of(
            description("key_gate_cartridge", 2),
            new ChoiceModule("side", K + "side", SIDES.stream().map(side -> new ChoiceModule.Option(K + "side." + side.asString(),
                    -1, K + "side." + side.asString() + ".tooltip")).toList(),
                    stack -> setting(stack, EDITED, 0, 0, SIDES.size() - 1), (edit, value) -> putSetting(edit.stack(), EDITED, value)),
            new ChoiceModule("locked", K + "locked", List.of(new ChoiceModule.Option(K + "free"), new ChoiceModule.Option(K + "locked_option")),
                    stack -> isLocked(stack, SIDES.get(setting(stack, EDITED, 0, 0, SIDES.size() - 1))) ? 1 : 0,
                    (edit, value) -> putSetting(edit.stack(), "lock_" + SIDES.get(setting(edit.stack(), EDITED, 0, 0, SIDES.size() - 1)).asString(), value)),
            new ChoiceModule("without_key", K + "without_key", List.of(new ChoiceModule.Option(K + "without_key.wait", -1, K + "without_key.wait.tooltip"),
                    new ChoiceModule.Option(K + "without_key.replay", -1, K + "without_key.replay.tooltip"),
                    new ChoiceModule.Option(K + "without_key.start", -1, K + "without_key.start.tooltip"),
                    new ChoiceModule.Option(K + "without_key.all", -1, K + "without_key.all.tooltip")),
                    stack -> setting(stack, WITHOUT_KEY, 3, 0, 3), (edit, value) -> putSetting(edit.stack(), WITHOUT_KEY, value)),
            new NumberModule("stay_open", K + "stay_open", 0, FOREVER, KeyGateCartridgeItem::stayOpen,
                    (edit, value) -> putSetting(edit.stack(), STAY_OPEN, value), stack -> COLOR));

    public KeyGateCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_KEY_GATE;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    /** The exit on {@code side} is locked (all of them by default). */
    public static boolean isLocked(ItemStack stack, Direction side) {
        return setting(stack, "lock_" + side.asString(), 1, 0, 1) == 1;
    }

    public static void setLocked(ItemStack stack, Direction side, boolean locked) {
        putSetting(stack, "lock_" + side.asString(), locked ? 1 : 0);
    }

    /** The side of {@code from} the board space at {@code to} leaves by: the axis it is furthest along. */
    public static Direction sideOf(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        if (Math.abs(dx) >= Math.abs(dz)) return dx >= 0 ? Direction.EAST : Direction.WEST;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** The exit from {@code from} to {@code to} is locked. */
    public static boolean locks(ItemStack stack, BlockPos from, BlockPos to) {
        return isLocked(stack, sideOf(from, to));
    }

    public static boolean allowsCancel(ItemStack stack) {
        return (setting(stack, WITHOUT_KEY, 3, 0, 3) & REPLAY) != 0;
    }

    public static boolean allowsStart(ItemStack stack) {
        return (setting(stack, WITHOUT_KEY, 3, 0, 3) & BACK_TO_START) != 0;
    }

    /** Rounds the gate stays open once a key opened it: 0 it closes behind the token, {@link #FOREVER} for good. */
    public static int stayOpen(ItemStack stack) {
        return setting(stack, STAY_OPEN, 0, 0, FOREVER);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        MutableText sides = Text.empty();
        for (Direction side : SIDES) {
            if (!isLocked(stack, side)) continue;
            if (!sides.getSiblings().isEmpty()) sides.append(", ");
            sides.append(Text.translatable(K + "side." + side.asString() + ".tooltip"));
        }
        tooltip.add(Text.translatable("tooltip.steveparty.key_gate_cartridge.locked", sides)
                .styled(style -> style.withColor(TextColor.fromRgb(0x6FD6C8))));
        int open = stayOpen(stack);
        if (open > 0) tooltip.add(Text.translatable(open >= FOREVER ? "tooltip.steveparty.key_gate_cartridge.forever"
                : "tooltip.steveparty.key_gate_cartridge.rounds", open).styled(style -> style.withColor(TextColor.fromRgb(0x6FD6C8))));
        super.appendTooltip(stack, context, tooltip, type);
    }
}
