package fr.lordfinn.steveparty.items.custom.cartridges;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.CartridgeModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.ChoiceModule;
import fr.lordfinn.steveparty.items.custom.cartridges.menu.InfoModule;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The End Cartridge: the finish of the board. A pawn reaching its space stops there (as on a Stop) and the party heads
 * for its end, as its settings say ({@link Ending}): the round is played out, it stops right away, everyone else gets
 * one more turn, or the pawns that arrived wait on it, set aside, until every pawn has arrived. The party is then
 * ranked ({@link Ranking}): by order of arrival (the first one wins, the others after, by stars) or by stars as always.
 * Linked to the Party Controller with the Tile Linker Brush, its space is also where the pawns gather once the party is
 * over (see PartyEnding). Its tile is raspberry with a checkered flag; a dye on it changes that.
 * <p>
 * Settings ({@link ModComponents#BOARD_CARTRIDGE_SETTINGS}): {@link #ENDING} (an {@link Ending}'s index, the round
 * played out by default) and {@link #RANKING} (a {@link Ranking}'s index, by arrival by default).
 */
public class EndCartridgeItem extends BoardRuleCartridgeItem {
    /** Its tile's raspberry. */
    public static final int COLOR = 0xD94F70;
    public static final String ENDING = "ending", RANKING = "ranking";

    /** What happens once a pawn arrived. */
    public enum Ending {
        /** The turns of the round still to play are played, then the party ends. */
        FINISH_ROUND,
        /** The party ends right after this turn. */
        AT_ONCE,
        /** The round is played out, then everyone who has not arrived plays one more turn. */
        ONE_MORE_TURN,
        /** The pawns that arrived wait, set aside on the End space, until every pawn has arrived. */
        WAIT_FOR_ALL;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** How the party is ranked once over. */
    public enum Ranking {
        /** The pawns that arrived first, in their order of arrival; the others after them, by stars and coins. */
        ARRIVAL,
        /** By stars, then coins, as a party without End space. */
        STARS;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final String K = MENU_KEY + "end.";
    private static final Setting ENDING_SETTING = new Setting(ENDING, 0, 0, Ending.values().length - 1);
    private static final Setting RANKING_SETTING = new Setting(RANKING, 0, 0, Ranking.values().length - 1);
    private static final List<CartridgeModule> MODULES = modules0();

    private static List<CartridgeModule> modules0() {
        List<ChoiceModule.Option> endings = new ArrayList<>();
        for (Ending ending : Ending.values()) endings.add(ChoiceModule.Option.tipped(K + "ending." + ending.id()));
        List<ChoiceModule.Option> rankings = new ArrayList<>();
        for (Ranking ranking : Ranking.values()) rankings.add(ChoiceModule.Option.tipped(K + "ranking." + ranking.id()));
        return List.of(
                // One line: what the chosen ending does
                new InfoModule("description", null, 3, context -> List.of(InfoModule.Line.of(
                        Text.translatable(MENU_KEY + "desc.end_cartridge." + ending(context.stack()).id())))),
                ENDING_SETTING.choice("ending", K + "ending", endings),
                RANKING_SETTING.choice("ranking", K + "ranking", rankings),
                colorModule(COLOR));
    }

    public EndCartridgeItem(Settings settings) {
        super(settings);
    }

    @Override
    public BoardSpaceType getBoardSpaceType() {
        return BoardSpaceType.TILE_END;
    }

    @Override
    public List<CartridgeModule> modules() {
        return MODULES;
    }

    @Override
    public int tileColor() {
        return COLOR;
    }

    public static Ending ending(ItemStack stack) {
        return Ending.values()[ENDING_SETTING.get(stack)];
    }

    public static Ranking ranking(ItemStack stack) {
        return Ranking.values()[RANKING_SETTING.get(stack)];
    }

    /** A cartridge with these settings (tests, commands). */
    public static ItemStack with(Item item, Ending ending, Ranking ranking) {
        ItemStack stack = new ItemStack(item);
        putSetting(stack, ENDING, ending.ordinal());
        putSetting(stack, RANKING, ranking.ordinal());
        return stack;
    }

    @Override
    protected void appendState(ItemStack stack, Tooltips tips) {
        tips.state("tooltip.steveparty.end_cartridge.ending", Tooltips.setting(Text.translatable(K + "ending." + ending(stack).id())));
        tips.state("tooltip.steveparty.end_cartridge.ranking", Tooltips.setting(Text.translatable(K + "ranking." + ranking(stack).id())));
    }

    @Override
    protected void appendMore(ItemStack stack, Tooltips.More more) {
        more.note("tooltip.steveparty.end_cartridge.arrivals");
    }
}
