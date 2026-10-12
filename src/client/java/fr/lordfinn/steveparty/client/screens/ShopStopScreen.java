package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStopScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

import java.util.Map;

/**
 * The merchant's screen at a shop stop ({@link CardboardTradeScreen}). Over the counter, as wide as the inventory, the
 * time left: a clock, a shrinking bar and its seconds. Under the payment row, centred like it, a shopping bag per purchase
 * allowed, filled as they are bought. Under the offers, level with the hotbar and as wide as their column, « Buy
 * nothing » (« Done », green, once something was bought). Closing the screen ends nothing: it opens again.
 */
public class ShopStopScreen extends CardboardTradeScreen {
    /** Offers shown: one row less than a merchant's, for the button under them (the last card ends with the inventory's rows). */
    private static final int CARDS = 6;
    /** The button: the offers' column wide, level with the hotbar's slots (their top edge included). */
    private static final int BUTTON_Y = 141, BUTTON_H = 17;
    /** The time row: from the inventory's left edge to its right one, at the top of the content. */
    private static final int CLOCK_W = 9, BAR_H = 7;
    /** The bags' row: centred under the payment row, in the gap above the inventory. */
    private static final int BAGS_Y = 64, BAG_W = 12, BAG_H = 13, BAG_GAP = 3, MAX_BAGS = 8;
    private static final int GOLD = 0xFFFFC52E, BAR_BACK = 0xFF6B3F1C, TIME_LOW = 0xFFE0303E, TIME_LOW_TEXT = 0xFFB3202A;
    private static final int CLOCK_FACE = 0xFFFFF2D6;
    /** An empty bag: pressed into the cardboard. */
    private static final int BAG_EMPTY_LINE = 0xFFA8692F, BAG_EMPTY_BODY = 0xFFE6B270, BAG_EMPTY_LIGHT = 0xFFEFC27E;
    private static final String[] BAG = {
            "...######...",
            "..#......#..",
            "..#......#..",
            "############",
            "#hhhhhhhhhh#",
            "#hbbbbbbbbs#",
            "#hbbbbbbbbs#",
            "#hbbbbbbbbs#",
            "#hbbbbbbbbs#",
            "#hbbbbbbbbs#",
            "#hbbbbbbbbs#",
            "#ssssssssss#",
            "############"};
    private static final String[] CLOCK = {
            "..#####..",
            ".#fffff#.",
            "#ffff#ff#",
            "#ffff#ff#",
            "#ffff##f#",
            "#fffffff#",
            "#fffffff#",
            ".#fffff#.",
            "..#####.."};

    private final ShopStopScreenHandler shopHandler;
    private PartyButton doneButton;
    /** Where the bags and the time were drawn (their tooltips). */
    private int bagsX, bagsW;

    public ShopStopScreen(ShopStopScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, CARDS);
        this.shopHandler = handler;
    }

    @Override
    protected void init() {
        super.init();
        doneButton = new PartyButton(this.x + LEFT, this.y + BUTTON_Y, COLUMN_END - LEFT, BUTTON_H, label(), button -> buyNothing());
        doneButton.setTooltip(Tooltip.of(Text.translatable("gui.steveparty.shop_stop.buy_nothing.hint")));
        addDrawableChild(doneButton);
    }

    private Text label() {
        return shopHandler.getPurchases() > 0
                ? Text.translatable("gui.steveparty.shop_stop.done")
                : Text.translatable("gui.steveparty.shop_stop.buy_nothing");
    }

    private void buyNothing() {
        if (this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(shopHandler.syncId, ShopStopScreenHandler.BUY_NOTHING_BUTTON_ID);
        }
        close();
    }

    @Override
    protected boolean canBuyMore() {
        return !shopHandler.isLimitReached();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (doneButton != null) {
            doneButton.setMessage(label());
            doneButton.style(shopHandler.getPurchases() > 0 ? PartyButton.Style.PRIMARY : PartyButton.Style.NORMAL);
        }
        super.render(context, mouseX, mouseY, delta);
        if (!handler.getCursorStack().isEmpty()) return;
        int bagsY = this.y + BAGS_Y, timeY = this.y + TOP;
        if (mouseY >= bagsY && mouseY < bagsY + BAG_H && mouseX >= bagsX && mouseX < bagsX + bagsW) {
            context.drawTooltip(textRenderer, Text.translatable("gui.steveparty.shop_stop.purchases",
                    shopHandler.getPurchases(), limit()), mouseX, mouseY);
        } else if (mouseY >= timeY && mouseY < timeY + CLOCK_W && mouseX >= this.x + COUNTER_X && mouseX < this.x + COUNTER_END) {
            context.drawTooltip(textRenderer, Text.translatable("gui.steveparty.shop_stop.time_left",
                    shopHandler.getSecondsLeft()), mouseX, mouseY);
        }
    }

    /** The time and the bags, under the slots and the tooltips. */
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        super.drawBackground(context, delta, mouseX, mouseY);
        drawTime(context);
        drawBags(context);
    }

    private int limit() {
        return Math.max(1, shopHandler.getLimit());
    }

    /** The clock, the bar of the time left and its seconds, from the inventory's left edge to its right one. */
    private void drawTime(DrawContext context) {
        int seconds = shopHandler.getSecondsLeft(), total = Math.max(1, shopHandler.getTotalSeconds());
        int left = this.x + COUNTER_X, right = this.x + COUNTER_END, y = this.y + TOP;
        drawIcon(context, CLOCK, left, y, Map.of('#', INK, 'f', CLOCK_FACE));
        // The seconds right-aligned in the room of the longest ones (the whole time): the bar never moves as they go down
        String text = seconds + " s";
        int textRoom = Math.max(textRenderer.getWidth(total + " s"), textRenderer.getWidth(text));
        UiText.line(context, textRenderer, text, right - textRenderer.getWidth(text), y + 1, textRenderer.getWidth(text),
                seconds <= 10 ? TIME_LOW_TEXT : INK, false);
        int barX = left + CLOCK_W + 4, barW = right - textRoom - 4 - barX;
        int color = seconds <= 10 ? TIME_LOW : seconds <= 20 ? GOLD : LIME;
        bar(context, barX, y + 1, barW, BAR_H, Math.min(1f, seconds / (float) total), color);
    }

    /** A bag per purchase allowed, filled once bought (past {@link #MAX_BAGS}: one bag and « 3/12 »), centred under the payment row. */
    private void drawBags(DrawContext context) {
        int bought = shopHandler.getPurchases(), limit = limit();
        int y = this.y + BAGS_Y, centre = this.x + (COUNTER_X + COUNTER_END) / 2;
        if (limit > MAX_BAGS) {
            String text = bought + "/" + limit;
            bagsW = BAG_W + BAG_GAP + textRenderer.getWidth(text);
            bagsX = centre - bagsW / 2;
            drawBag(context, bagsX, y, bought >= limit);
            UiText.line(context, textRenderer, text, bagsX + BAG_W + BAG_GAP, y + 4, textRenderer.getWidth(text), INK, false);
            return;
        }
        bagsW = limit * BAG_W + (limit - 1) * BAG_GAP;
        bagsX = centre - bagsW / 2;
        for (int i = 0; i < limit; i++) drawBag(context, bagsX + i * (BAG_W + BAG_GAP), y, i < bought);
    }

    /** A shopping bag: lime once bought, else only pressed into the cardboard. */
    private static void drawBag(DrawContext context, int x, int y, boolean bought) {
        drawIcon(context, BAG, x, y, bought
                ? Map.of('#', INK, 'h', LIME_LIGHT, 'b', LIME, 's', LIME_DARK)
                : Map.of('#', BAG_EMPTY_LINE, 'h', BAG_EMPTY_BODY, 'b', BAG_EMPTY_BODY, 's', BAG_EMPTY_LIGHT));
    }

    /** A pixel icon from rows of characters, each one a colour of {@code colours} (others: nothing). */
    private static void drawIcon(DrawContext context, String[] rows, int x, int y, Map<Character, Integer> colours) {
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < rows[row].length(); col++) {
                Integer colour = colours.get(rows[row].charAt(col));
                if (colour != null) PartyGui.pixel(context, x + col, y + row, colour);
            }
        }
    }

    /** The time bar: a dark groove, filled from the left, its top pixel row lighter. */
    private static void bar(DrawContext context, int x, int y, int w, int h, float fraction, int color) {
        context.fill(x, y, x + w, y + h, INK);
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, BAR_BACK);
        int filled = Math.round((w - 2) * fraction);
        if (filled <= 0) return;
        context.fill(x + 1, y + 1, x + 1 + filled, y + h - 1, color);
        context.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0x66FFFFFF);
        context.fill(x + 1, y + h - 2, x + 1 + filled, y + h - 1, 0x33000000);
    }
}
