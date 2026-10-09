package fr.lordfinn.steveparty.client.screens.partycontroller;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyDashboardData.Phase;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.BasicGameGeneratorStep;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.PartyCardItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.client.screens.partycontroller.DashboardStyle.*;
import static fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler.*;

/**
 * The Program tab: the catalogue slot (its mini-games in its tooltip), what the party will be made of, the party's
 * program (2 rows of 12 card slots, the default party as ghost cards while it is empty, see
 * {@link BasicGameGeneratorStep#defaultProgram}) and, under them, the timeline of what it will play.
 */
public final class ProgramPage {
    /** The summary line. */
    private static final int SUMMARY_Y = CY + 21;

    private final Dashboard dashboard;
    private final DashboardPainter paint;
    private final DashboardTimeline timeline;

    public ProgramPage(Dashboard dashboard, DashboardPainter paint, DashboardTimeline timeline) {
        this.dashboard = dashboard;
        this.paint = paint;
        this.timeline = timeline;
    }

    /** What is wrong with the mini-games: no catalogue, an empty one, mini-games without their pipes. Null: nothing. */
    public static DashboardTabs.@Nullable Badge catalogueBadge(PartyDashboardData data) {
        if (!data.hasCatalogue()) return new DashboardTabs.Badge(true, Text.translatable(KEY + "badge.no_catalogue"));
        if (data.pages().isEmpty()) return new DashboardTabs.Badge(true, Text.translatable(KEY + "badge.empty_catalogue"));
        long notPlayable = data.pages().stream().filter(p -> p.playable() == 0).count();
        if (notPlayable > 0) return new DashboardTabs.Badge(false, Text.translatable(KEY + "badge.no_pipes", notPlayable));
        return null;
    }

    /** The real cards of the program, in reading order. */
    private List<ItemStack> cards() {
        List<ItemStack> cards = new ArrayList<>();
        for (int i = 0; i < PartyControllerEntity.PROGRAM_SLOTS; i++) {
            ItemStack stack = dashboard.handler().getSlot(PROGRAM_FIRST_SLOT + i).getStack();
            if (!stack.isEmpty()) cards.add(stack);
        }
        return cards;
    }

    private static Item cardItem(PartyCardItem.CardType type) {
        return switch (type) {
            case TURNS -> ModItems.PARTY_CARD_TURNS;
            case MINIGAME -> ModItems.PARTY_CARD_MINIGAME;
            case EVENT -> ModItems.PARTY_CARD_EVENT;
            case REPEAT -> ModItems.PARTY_CARD_REPEAT;
            case SEQUENCE_START -> ModItems.PARTY_CARD_SEQUENCE_START;
        };
    }

    /**
     * The ghost cards of the program: with no real card, the default party as cards (never items: only drawn), in
     * the first slots; empty as soon as a real card is placed (the program is then the real cards only).
     */
    private List<ItemStack> ghosts(PartyDashboardData data) {
        if (!cards().isEmpty()) return List.of();
        List<ItemStack> ghosts = new ArrayList<>();
        for (BasicGameGeneratorStep.ExpandedCard card : BasicGameGeneratorStep.defaultProgram(data.roundsSetting()))
            ghosts.add(new ItemStack(cardItem(card.type()), card.count()));
        return ghosts;
    }

    /**
     * The catalogue slot and what it brings (its mini-games are in its tooltip), what the party will be made of, the
     * card slots (the default party as ghost cards while no card is placed), the timeline of what they will play.
     */
    public void draw(DrawContext context, PartyDashboardData data, int mx, int my) {
        int tx = CX + 22, room = INFO_X - 4 - tx;
        if (!data.canEdit()) room -= paint.readOnly(context, CY + 5) + 6;
        // Next to the catalogue: its mini-games, or what is wrong with them
        DashboardTabs.Badge problem = catalogueBadge(data);
        long played = data.pages().stream().filter(p -> p.played() > 0).count();
        Text catalogue = problem != null ? problem.reason() : Text.translatable(KEY + "program.catalogue.summary", data.pages().size(), played);
        paint.fitted(context, catalogue, tx, CY + 5, room, problem == null ? WHITE : problem.error() ? INK_RED : INK_WARN,
                HitArea.contains(mx, my, tx, CY + 4, room, 10));
        // What the party will be made of
        List<ItemStack> cards = cards();
        BasicGameGeneratorStep.Summary made = BasicGameGeneratorStep.summary(cards, data.roundsSetting());
        Text summary = data.phase() == Phase.RUNNING ? Text.translatable(KEY + "program.running")
                : Text.translatable(cards.isEmpty() ? KEY + "program.default" : KEY + "program.summary", made.turns(), made.miniGames(), made.events());
        paint.fitted(context, summary, CX, SUMMARY_Y, CW, INK_SOFT, HitArea.contains(mx, my, CX, SUMMARY_Y - 1, CW, 10));
        // The default party, as ghost cards
        List<ItemStack> ghosts = ghosts(data);
        for (int i = 0; i < ghosts.size() && i < PartyControllerEntity.PROGRAM_SLOTS; i++) {
            paint.ghost(context, ghosts.get(i), PROGRAM_X + (i % PROGRAM_COLUMNS) * 18, PROGRAM_Y + (i / PROGRAM_COLUMNS) * 18, true);
        }
        // Under the cards, on their columns: what the program will play, its loops unrolled
        timeline.drawProgram(context, data, mx, my);
    }

    // ------------------------------------------------------------------ tooltips

    /** The catalogue (its slot, its line): what it is, its mini-games; null when the mouse is elsewhere. */
    public @Nullable List<Text> catalogueTooltip(PartyDashboardData data, @Nullable Slot focused, int mx, int my) {
        if (!((focused != null && focused.id == SLOT_CATALOGUE) || HitArea.contains(mx, my, CX + 20, CY, INFO_X - 4 - CX - 20, 18))) return null;
        ItemStack catalogue = dashboard.handler().getSlot(SLOT_CATALOGUE).getStack();
        if (catalogue.isEmpty()) {
            return List.of(Text.translatable(KEY + "program.catalogue").formatted(Formatting.GOLD),
                    Text.translatable(KEY + "program.catalogue.hint").formatted(Formatting.GRAY));
        }
        // Its name, its count, then a line per mini-game (played, without its pipes, current)
        List<Text> lines = new ArrayList<>();
        lines.add(catalogue.getName().copy().formatted(Formatting.GOLD));
        long played = data.pages().stream().filter(p -> p.played() > 0).count();
        lines.add(Text.translatable(KEY + "program.catalogue.summary", data.pages().size(), played).formatted(Formatting.GRAY));
        for (PartyDashboardData.Page page : data.pages()) {
            MutableText line = Text.literal("• ").append(page.page().getName());
            if (page.slot() == data.currentPage()) line.append(" · ").append(Text.translatable(KEY + "mini_games.page.current"));
            else if (page.playable() == 0) line.append(" · ").append(Text.translatable(page.pipes() == 0 ? KEY + "mini_games.page.no_pipe" : KEY + "mini_games.page.not_playable"));
            else if (page.played() > 0) line.append(" · ").append(Text.translatable(KEY + "mini_games.page.played", page.played()));
            lines.add(line.formatted(page.slot() == data.currentPage() ? Formatting.GOLD : page.playable() == 0 ? Formatting.RED
                    : page.played() > 0 ? Formatting.GREEN : Formatting.WHITE));
        }
        if (data.catalogueLocked()) lines.add(Text.translatable(KEY + "program.catalogue.locked").formatted(Formatting.RED));
        return lines;
    }

    /** An empty card slot: the ghost card it shows, or what it takes. */
    public List<Text> slotTooltip(PartyDashboardData data, Slot slot) {
        List<ItemStack> ghosts = ghosts(data);
        int index = slot.id - PROGRAM_FIRST_SLOT;
        List<Text> lines = new ArrayList<>();
        if (index < ghosts.size()) {
            ItemStack ghost = ghosts.get(index);
            Text name = ghost.getCount() > 1 ? Text.translatable(KEY + "program.ghost.count", ghost.getName(), ghost.getCount()) : ghost.getName();
            lines.add(Text.translatable(KEY + "program.ghost", name).formatted(Formatting.GOLD));
            lines.add(Text.translatable(KEY + "program.ghost.hint").formatted(Formatting.GRAY));
        } else {
            lines.add(Text.translatable(KEY + "program.slot").formatted(Formatting.GRAY));
        }
        return lines;
    }
}
