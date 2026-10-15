package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.PartyBellBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Divider;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Icon;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Label;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Panel;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Part;
import fr.lordfinn.steveparty.client.board.LinkedPanels.SheetIcon;
import fr.lordfinn.steveparty.client.board.LinkedPanels.Space;
import fr.lordfinn.steveparty.client.screens.AntennaIcons;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Party Antenna looked at listens to, on its {@link LinkedPanels} panel: the moment's item (and its badge),
 * then its filter (its sign and value: « ≥ 5 ») if it has one and an hourglass if it waits. Linked to no party (it hears
 * nothing then), the Tile Linker Brush crossed out in red comes first.
 */
public final class AntennaPanel {
    /** Above its head, clear of the dish whichever way it turns (blocks). */
    private static final double ABOVE = 1.55;

    private AntennaPanel() {
    }

    public static void initialize() {
        LinkedPanels.register(blockEntity -> blockEntity instanceof PartyBellBlockEntity antenna ? panel(antenna) : null);
    }

    private static Panel panel(PartyBellBlockEntity antenna) {
        List<Part> parts = new ArrayList<>();
        if (!antenna.isPartyLinked()) LinkedPanels.notLinked(parts);
        PartyMoment moment = antenna.getMoment();
        parts.add(new Icon(AntennaIcons.item(moment), AntennaIcons.scale(moment), false, AntennaIcons.badge(moment)));
        boolean filtered = antenna.getFilter() != PartyBellBlockEntity.Filter.ANY, waits = antenna.isWaiting();
        if (filtered || waits) parts.add(new Divider());
        if (filtered) {
            parts.add(new SheetIcon(switch (antenna.getFilter()) {
                case EQUAL -> AntennaIcons.FILTER_EQUAL;
                case AT_LEAST -> AntennaIcons.FILTER_AT_LEAST;
                default -> AntennaIcons.FILTER_AT_MOST;
            }));
            parts.add(new Space(LinkedPanels.ICON_TEXT));
            parts.add(new Label(Integer.toString(antenna.getFilterValue()), WorldDraw.PLATE_TEXT));
            if (waits) parts.add(new Space(LinkedPanels.ITEM_GAP));
        }
        if (waits) parts.add(new SheetIcon(AntennaIcons.WAIT));
        return new Panel(parts, ABOVE);
    }
}
