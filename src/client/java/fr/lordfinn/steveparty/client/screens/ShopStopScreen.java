package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStopScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;

/**
 * The merchant's screen at a shop stop ({@link CardboardTradeScreen}): on the counter, the purchases allowed as shopping
 * bags (filled as they are bought) and the time left as a shrinking bar; under the offers, « Buy nothing » (« Done »,
 * green, once something was bought). Closing the screen ends nothing: it opens again.
 */
public class ShopStopScreen extends CardboardTradeScreen {
    /** Offers shown: one row less than a merchant's, for the button under them. */
    private static final int CARDS = 6;
    private static final int BUTTON_X = 5, BUTTON_Y = 18 + CARDS * 20 + 3, BUTTON_W = 95, BUTTON_H = 18;
    /** The counter's row over the payment slots: bags on the left, the clock on the right. */
    private static final int ROW_Y = 20, ROW_LEFT = 108, ROW_RIGHT = 270;
    private static final int BAG_H = 12, BAG_STEP = 13, MAX_BAGS = 8;
    private static final int GOLD = 0xFFFFC52E, BAR_BACK = 0xFF6B3F1C, BAG_EMPTY = 0xFFF3E4C4;
    private static final int LIME = 0xFF000000 | ShopCartridgeItem.COLOR;
    private static final String[] BAG = {
            "...#####...",
            "..#.....#..",
            "..#.....#..",
            "###########",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "###########"};
    private static final String[] CLOCK = {
            "..#####..",
            ".#.....#.",
            "#...#...#",
            "#...#...#",
            "#...##..#",
            "#.......#",
            "#.......#",
            ".#.....#.",
            "..#####.."};

    private final ShopStopScreenHandler shopHandler;
    private PartyButton doneButton;
    /** Where the counter's row was drawn: the bags' width, the clock's left edge (their tooltips). */
    private int bagsW, timerX;

    public ShopStopScreen(ShopStopScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, CARDS);
        this.shopHandler = handler;
    }

    @Override
    protected void init() {
        super.init();
        doneButton = new PartyButton(this.x + BUTTON_X, this.y + BUTTON_Y, BUTTON_W, BUTTON_H, label(), button -> buyNothing());
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
        int rowY = this.y + ROW_Y;
        if (mouseY >= rowY && mouseY < rowY + BAG_H && mouseX >= this.x + ROW_LEFT && mouseX < this.x + ROW_LEFT + bagsW) {
            context.drawTooltip(textRenderer, Text.translatable("gui.steveparty.shop_stop.purchases",
                    shopHandler.getPurchases(), limit()), mouseX, mouseY);
        } else if (mouseY >= rowY && mouseY < rowY + BAG_H && mouseX >= timerX && mouseX < this.x + ROW_RIGHT) {
            context.drawTooltip(textRenderer, Text.translatable("gui.steveparty.shop_stop.time_left",
                    shopHandler.getSecondsLeft()), mouseX, mouseY);
        }
    }

    /** The counter's row, under the slots and the tooltips. */
    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        super.drawBackground(context, delta, mouseX, mouseY);
        bagsW = drawBags(context);
        timerX = drawTimer(context);
    }

    private int limit() {
        return Math.max(1, shopHandler.getLimit());
    }

    /** A bag per purchase allowed, filled once bought (past {@link #MAX_BAGS}: one bag and « 3/12 »). @return the width taken */
    private int drawBags(DrawContext context) {
        int x = this.x + ROW_LEFT, y = this.y + ROW_Y;
        int bought = shopHandler.getPurchases(), limit = limit();
        if (limit > MAX_BAGS) {
            drawIcon(context, BAG, x, y, bought >= limit ? LIME : BAG_EMPTY);
            String text = bought + "/" + limit;
            UiText.line(context, textRenderer, text, x + BAG_STEP + 1, y + 1, textRenderer.getWidth(text), INK, false);
            return BAG_STEP + 1 + textRenderer.getWidth(text);
        }
        for (int i = 0; i < limit; i++) drawIcon(context, BAG, x + i * BAG_STEP, y, i < bought ? LIME : BAG_EMPTY);
        return limit * BAG_STEP;
    }

    /** The clock, the bar of the time left and its seconds, right-aligned on the counter. @return its left edge */
    private int drawTimer(DrawContext context) {
        int seconds = shopHandler.getSecondsLeft(), total = Math.max(1, shopHandler.getTotalSeconds());
        int color = seconds <= 10 ? 0xFFE0303E : seconds <= 20 ? GOLD : LIME;
        String text = seconds + " s";
        int right = this.x + ROW_RIGHT, y = this.y + ROW_Y;
        int textX = right - textRenderer.getWidth(text);
        UiText.line(context, textRenderer, text, textX, y + 1, textRenderer.getWidth(text), seconds <= 10 ? 0xFFB3202A : INK, false);
        int barW = 60, barX = textX - 4 - barW;
        bar(context, barX, y + 2, barW, 6, Math.min(1f, seconds / (float) total), color);
        int clockX = barX - 12;
        drawIcon(context, CLOCK, clockX, y, 0xFFFFF2D6);
        return clockX;
    }

    /** A pixel icon: '#' in ink, '.' inside it in {@code fill}. */
    private static void drawIcon(DrawContext context, String[] rows, int x, int y, int fill) {
        for (int row = 0; row < rows.length; row++) {
            String line = rows[row];
            int first = line.indexOf('#'), last = line.lastIndexOf('#');
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == '#') PartyGui.pixel(context, x + col, y + row, INK);
                else if (c == '.' && col > first && col < last && row >= bodyTop(rows)) PartyGui.pixel(context, x + col, y + row, fill);
            }
        }
    }

    /** The first row inside an icon's body: under its first full row (a bag's handle stays hollow), else its second row. */
    private static int bodyTop(String[] rows) {
        for (int row = 0; row < rows.length; row++) if (rows[row].indexOf('.') < 0) return row + 1;
        return 1;
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
