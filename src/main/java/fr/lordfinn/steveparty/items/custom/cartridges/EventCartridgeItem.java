package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.PartyController.EndPipe;
import fr.lordfinn.steveparty.blocks.custom.PartyController.EventTimeOut;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Event Cartridge: a time out of the party when a pawn stops on its space (see EventTimeOut). The map maker's
 * redstone and commands take over: the "event" party bells of its channel ring (a comparator on a Router of the space
 * reads the landing too), the players come out of the pipes it is linked to with the Tile Linker Brush, if any. The
 * event ends when its hourglass runs out, when a waiting bell receives redstone, or on a Step Controller's pulse; the
 * players then come back and the party goes on. Its tile is violet with an exclamation mark; a dye on it changes that.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #DURATION} (an index of
 * {@link EventTimeOut#DURATIONS}, a minute by default) and {@link #CHANNEL} (the value the bells ring with, 1 to 15).
 * Its pipes: {@link ModComponents#EVENT_PIPES}.
 */
public class EventCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's violet. */
    public static final int COLOR = 0x9F238E;
    public static final String DURATION = "duration", CHANNEL = "channel";

    private static final String K = MENU_KEY + "event.";
    private static final Setting DURATION_SETTING = new Setting(DURATION, 2, 0, EventTimeOut.DURATIONS.length - 1);
    private static final Setting CHANNEL_SETTING = new Setting(CHANNEL, 1, 1, 15);
    private static final List<CartridgeModule> MODULES = modules0();

    private static List<CartridgeModule> modules0() {
        List<ChoiceModule.Option> durations = new ArrayList<>();
        for (int seconds : EventTimeOut.DURATIONS)
            durations.add(seconds == 0 ? ChoiceModule.Option.tipped(K + "duration.0") : new ChoiceModule.Option(K + "duration." + seconds));
        return List.of(
                description("event_cartridge", 2),
                DURATION_SETTING.choice("duration", K + "duration", durations).fitted(),
                CHANNEL_SETTING.number("channel", K + "channel", stack -> COLOR),
                colorModule(COLOR));
    }

    public EventCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_EVENT;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    /** Its hourglass, in seconds (0: none). */
    public static int seconds(ItemStack stack) {
        return EventTimeOut.DURATIONS[DURATION_SETTING.get(stack)];
    }

    public static int channel(ItemStack stack) {
        return CHANNEL_SETTING.get(stack);
    }

    public static List<EndPipe> pipes(ItemStack stack) {
        return EventTimeOut.pipes(stack);
    }

    /** A cartridge with this hourglass ({@link EventTimeOut#DURATIONS} index) and channel (tests, commands). */
    public static ItemStack with(Item item, int durationIndex, int channel) {
        ItemStack stack = new ItemStack(item);
        putSetting(stack, DURATION, durationIndex);
        putSetting(stack, CHANNEL, channel);
        return stack;
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        tips.state("tooltip.steveparty.event.hourglass", Tooltips.setting(EventTimeOut.durationText(seconds(stack))));
        tips.state("tooltip.steveparty.event.pipes", Tooltips.value(pipes(stack).size()));
        if (channel(stack) != 1) tips.state("tooltip.steveparty.event.channel", Tooltips.setting(channel(stack)));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        more.note("tooltip.steveparty.event_cartridge.pipes");
        more.note("tooltip.steveparty.event.ends");
    }
}
