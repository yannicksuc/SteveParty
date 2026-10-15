package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyMoment;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;

import java.util.EnumMap;
import java.util.Map;

/**
 * What shows a Party Antenna's moments: an item of the game for each (the item says what, the button's colour says the
 * family), with a small badge where the item alone does not say it (a gain, a loss, a stop, done). The badges and the
 * screen's controls come from {@code antenna_icons.png} (16 x 16 cells: badges 0-3, controls from 32).
 */
public final class AntennaIcons {
    public static final Identifier SHEET = Steveparty.id("textures/gui/antenna_icons.png");
    public static final int BADGE_PLUS = 0, BADGE_MINUS = 1, BADGE_STOP = 2, BADGE_CHECK = 3;
    public static final int PULSE = 32, WAIT = 33, FILTER_ANY = 34, FILTER_EQUAL = 35, FILTER_AT_LEAST = 36, FILTER_AT_MOST = 37,
            MINUS = 38, PLUS = 39, TEST = 40, LINKED = 41, UNLINKED = 42;

    private record Icon(ItemConvertible item, int badge) {
    }

    private static final Map<PartyMoment, Icon> ICONS = new EnumMap<>(PartyMoment.class);

    static {
        put(PartyMoment.PARTY_START, ModBlocks.PARTY_CONTROLLER, -1);
        put(PartyMoment.FINAL_ROUNDS, Items.CLOCK, -1);
        put(PartyMoment.PARTY_END, ModItems.END_CARTRIDGE, -1);
        put(PartyMoment.REWIND, ModBlocks.STEP_CONTROLLER, -1);
        put(PartyMoment.ROUND_START, ModItems.PARTY_CARD_TURNS, -1);
        put(PartyMoment.TURN_START, ModItems.FLAG, -1);
        put(PartyMoment.DICE_ROLLED, ModItems.DEFAULT_DICE, -1);
        put(PartyMoment.CROSSROADS, ModBlocks.BOARD_SPACE_REDSTONE_ROUTER, -1);
        put(PartyMoment.PATH_CHOSEN, Items.ARROW, -1);
        put(PartyMoment.TOKEN_LANDS, ModBlocks.TILE, -1);
        put(PartyMoment.TURN_END, ModItems.FLAG, BADGE_STOP);
        put(PartyMoment.COINS_WON, ModItems.COIN, BADGE_PLUS);
        put(PartyMoment.COINS_LOST, ModItems.COIN, BADGE_MINUS);
        put(PartyMoment.STAR_WON, ModItems.PARTY_STAR, BADGE_PLUS);
        put(PartyMoment.STAR_MOVES, ModItems.STAR_CARTRIDGE, -1);
        put(PartyMoment.NEW_LEADER, Items.GOLDEN_HELMET, -1);
        put(PartyMoment.EVENT, ModItems.EVENT_CARTRIDGE, -1);
        put(PartyMoment.EVENT_OVER, ModItems.EVENT_CARTRIDGE, BADGE_CHECK);
        put(PartyMoment.MINIGAME_CHOSEN, ModItems.MINI_GAME_PAGE, -1);
        put(PartyMoment.PRACTICE_ROUND, Items.TARGET, -1);
        put(PartyMoment.MINIGAME_START, ModItems.MINIGAME_CARTRIDGE, -1);
        put(PartyMoment.MINIGAME_END, ModBlocks.GOLD_PODIUM, -1);
    }

    private static void put(PartyMoment moment, ItemConvertible item, int badge) {
        ICONS.put(moment, new Icon(item, badge));
    }

    private AntennaIcons() {
    }

    public static ItemStack item(PartyMoment moment) {
        return new ItemStack(ICONS.get(moment).item());
    }

    /** Its badge's cell in the sheet, -1 for none. */
    public static int badge(PartyMoment moment) {
        return ICONS.get(moment).badge();
    }

    /** The moment's item at ({@code x}, {@code y}), and its badge above it. */
    public static void drawMoment(DrawContext context, PartyMoment moment, int x, int y) {
        context.drawItem(item(moment), x, y);
        int badge = ICONS.get(moment).badge();
        if (badge < 0) return;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200);
        cell(context, badge, x, y);
        context.getMatrices().pop();
    }

    /** A 16 x 16 cell of the sheet at ({@code x}, {@code y}). */
    public static void cell(DrawContext context, int cell, int x, int y) {
        context.drawTexture(SHEET, x, y, (cell % 16) * 16, (cell / 16) * 16, 16, 16, 256, 64);
    }

    /** The family's colour, ARGB. */
    public static int colour(PartyMoment.Family family) {
        return 0xFF000000 | family.colour();
    }

    /** The family's colour as text on a dark tooltip: the dark green lightened a little, the others as they are. */
    public static int textColour(PartyMoment.Family family) {
        int colour = colour(family);
        return Argb.luminance(colour) < 110 ? Argb.lighten(colour, 0.35f) : colour;
    }
}
