package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.screen.MerchantScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The Boxed Trader's trade screen, in the mod's look: a cardboard box under a lime and white awning (the Shop's colour),
 * his name on a sign over it. On the left, his offers as cards (price → item): the one picked pressed in lime, a price
 * the player can't pay in red, a sold out one greyed with a red cross. On the right, the payment slots and the result
 * (the picked offer's items ghosted in them while empty), then the player's inventory. The slots stay where the merchant
 * screen handler puts them (shift-click, number keys, scroll and the keyboard work as on the vanilla screen).
 */
public class CardboardTradeScreen extends HandledScreen<MerchantScreenHandler> {
    protected static final int WIDTH = 276, HEIGHT = 166;
    /** The sign's part above the box. */
    protected static final int SIGN_OVER = 8;
    /**
     * The grid: an 8 px margin on the left, the right and the bottom (the inventory's slots, set by the merchant screen
     * handler, end 8 px from the right and the bottom edges); the content starts under the awning, at {@link #TOP}. The
     * offers' column (cards, then the scrollbar) runs from {@link #LEFT} to {@link #COLUMN_END}, 8 px before the
     * counter's column, which starts with the inventory at {@link #COUNTER_X}.
     */
    protected static final int LEFT = 8, TOP = 17, COLUMN_END = 100, COUNTER_X = 108, COUNTER_END = 268;
    /** The offers' cards ({@link #CARD_W} × {@link #CARD_H}, one every {@link #CARD_STEP}) and the scrollbar on their right. */
    protected static final int CARD_W = 84, CARD_STEP = 20, CARD_H = 19;
    private static final int BAR_X = 94, BAR_W = COLUMN_END - BAR_X, THUMB_H = 15;
    /** The merchant screen handler's slots (vanilla positions): payments and result. */
    protected static final int PAY_Y = 37, RESULT_X = 220, RESULT_Y = 37;
    /** The bottom of the payment row (the result's frame). */
    protected static final int COUNTER_ROW_END = RESULT_Y + 21;

    // Cardboard (the Boxed Trader's box), its darker shelf, the awning in the Shop's lime
    protected static final PartyGui.Theme CARDBOARD = new PartyGui.Theme(0xFF3C241D, 0xFFF2C988, 0xFFD9A15C, 0xFFA8692F);
    private static final PartyGui.Theme SIGN = new PartyGui.Theme(0xFF2A1810, 0xFF9C6A3F, 0xFF6E4527, 0xFF4A2C18);
    private static final PartyGui.Theme CARD = new PartyGui.Theme(0xFF3C241D, 0xFFFFFBEF, 0xFFF3E4C4, 0xFFC9AE7E);
    private static final PartyGui.Theme CARD_PICKED = new PartyGui.Theme(0xFF1E3A08, 0xFFE2FFB0,
            0xFF000000 | ShopCartridgeItem.COLOR, 0xFF5E8F17);
    private static final PartyGui.Theme CARD_SOLD_OUT = new PartyGui.Theme(0xFF4A3A33, 0xFFC9BBA8, 0xFFAD9F8C, 0xFF7E715F);
    private static final int GROOVE = 0xFFA8692F, GROOVE_EDGE = 0xFF6B3F1C;
    protected static final int LIME = 0xFF000000 | ShopCartridgeItem.COLOR, LIME_DARK = 0xFF5E8F17, LIME_LIGHT = 0xFFD4F58E;
    protected static final int INK = 0xFF3C241D, RED = 0xFFD8323F, RED_DARK = 0xFF5E0A12, PRICE_MISSING = 0xFFC0182A;
    /** The slots' kraft: a darker cardboard cell pressed into the box, a deep brown shadow on top; the ghosts' veil in it. */
    private static final int SLOT = 0xFF8E5A2E, SLOT_TOP = 0xFF4E2C14, GHOST_VEIL = 0x998E5A2E;
    /** The awning's stripes ({@code shop_awning.png}: a lime one, then a white one, {@link #STRIPE} × {@link #AWNING_H}). */
    private static final Identifier AWNING = Steveparty.id("textures/gui/shop_awning.png");
    private static final int STRIPE = 10, AWNING_H = 11;

    private final int visibleCards;
    private final OfferCard[] cards;
    /** The first offer shown, the one picked (-1: none). */
    private int scroll, picked = -1;
    private boolean draggingBar;

    public CardboardTradeScreen(MerchantScreenHandler handler, PlayerInventory inventory, Text title) {
        this(handler, inventory, title, 7);
    }

    /** {@code visibleCards}: how many offers are shown at once (at most 7, the column's height). */
    protected CardboardTradeScreen(MerchantScreenHandler handler, PlayerInventory inventory, Text title, int visibleCards) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = HEIGHT;
        this.visibleCards = visibleCards;
        this.cards = new OfferCard[visibleCards];
    }

    @Override
    protected void init() {
        super.init();
        // The sign over the box stays on screen when there is room for it
        this.y = Math.max(this.y, Math.min(SIGN_OVER, Math.max(0, this.height - HEIGHT)));
        for (int i = 0; i < visibleCards; i++) {
            cards[i] = addDrawableChild(new OfferCard(i, this.x + LEFT, this.y + TOP + i * CARD_STEP));
        }
    }

    // ---------------------------------------------------------------- offers

    protected TradeOfferList offers() {
        return handler.getRecipes();
    }

    private int maxScroll() {
        return Math.max(0, offers().size() - visibleCards);
    }

    private void pick(int index) {
        picked = index;
        handler.setRecipeIndex(index);
        handler.switchTo(index);
        if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendPacket(new SelectMerchantTradeC2SPacket(index));
        }
    }

    /** Whether the player carries the price of {@code offer} (inventory and payment slots). */
    private boolean canPay(TradeOffer offer) {
        ItemStack first = offer.getDisplayedFirstBuyItem(), second = offer.getDisplayedSecondBuyItem();
        TradedItem firstItem = offer.getFirstBuyItem();
        Optional<TradedItem> secondItem = offer.getSecondBuyItem();
        int haveFirst = carried(firstItem), haveSecond = secondItem.map(this::carried).orElse(0);
        if (second.isEmpty()) return haveFirst >= first.getCount();
        if (ItemStack.areItemsEqual(first, second)) return haveFirst >= first.getCount() + second.getCount();
        return haveFirst >= first.getCount() && haveSecond >= second.getCount();
    }

    private int carried(TradedItem item) {
        int count = 0;
        for (Slot slot : handler.slots) {
            if (slot.id == 2) continue; // the result
            ItemStack stack = slot.getStack();
            if (!stack.isEmpty() && item.matches(stack)) count += stack.getCount();
        }
        return count;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        PartyGui.panel(context, x, y, WIDTH, HEIGHT, CARDBOARD);
        drawAwning(context, x + 3, y + 3, WIDTH - 6);
        drawScrollbar(context, x + BAR_X, y + TOP, visibleCards * CARD_STEP - 1);
        drawCounter(context, x, y);
        // Every slot of the screen but the result (in its frame) in the mod's style, straight on the cardboard
        for (Slot slot : handler.slots) if (slot.id != 2) slot(context, x + slot.x, y + slot.y);
        drawSign(context, x + WIDTH / 2, y - SIGN_OVER);
    }

    /**
     * The awning: lime and white stripes ({@link #AWNING}, the Trading Stall's scalloped awning in the Shop's lime), as
     * many as fit whole, lime at both ends, centred on {@code w}.
     */
    private static void drawAwning(DrawContext context, int x, int y, int w) {
        int count = w / STRIPE;
        if (count % 2 == 0) count--;
        int left = x + (w - count * STRIPE) / 2;
        for (int i = 0; i < count; i++) {
            context.drawTexture(AWNING, left + i * STRIPE, y, (i % 2) * STRIPE, 0, STRIPE, AWNING_H, 2 * STRIPE, AWNING_H);
        }
    }

    /** The merchant's name on a wooden sign straddling the box's top. */
    private void drawSign(DrawContext context, int centerX, int top) {
        int textW = Math.min(textRenderer.getWidth(this.title), WIDTH - 60);
        int w = textW + 16, left = centerX - w / 2;
        PartyGui.panel(context, left, top, w, 16, SIGN);
        // Its two nails
        PartyGui.pixel(context, left + 4, top + 7, 0xFFC9B79A);
        PartyGui.pixel(context, left + w - 5, top + 7, 0xFFC9B79A);
        UiText.centered(context, textRenderer, this.title, left + 8, top + 4, textW, 0xFFFFF2D6, true);
    }

    /** The scrollbar: a groove in the cardboard (rounded, its top edge dark like the slots), its cream thumb. */
    private void drawScrollbar(DrawContext context, int x, int y, int h) {
        context.fill(x + 1, y, x + BAR_W - 1, y + h, GROOVE);
        context.fill(x, y + 1, x + BAR_W, y + h - 1, GROOVE);
        context.fill(x + 1, y, x + BAR_W - 1, y + 1, GROOVE_EDGE);
        int max = maxScroll();
        int thumbY = max == 0 ? y : y + Math.round((h - THUMB_H) * (scroll / (float) max));
        PartyGui.button(context, x, thumbY, BAR_W, THUMB_H, max == 0 ? CARD_SOLD_OUT : CARD, false);
        if (max > 0) for (int i = 0; i < 3; i++)
            context.fill(x + 2, thumbY + 5 + i * 2, x + BAR_W - 2, thumbY + 6 + i * 2, CARD.shadow());
    }

    /** « + » between the payment slots, the arrow and the result in its frame. */
    private void drawCounter(DrawContext context, int x, int y) {
        // « + »
        int px = x + 155, py = y + PAY_Y + 5;
        context.fill(px + 2, py, px + 3, py + 5, INK);
        context.fill(px, py + 2, px + 5, py + 3, INK);
        // The result's frame: lime when something can be taken
        boolean ready = !handler.getSlot(2).getStack().isEmpty();
        int fx = x + RESULT_X - 5, fy = y + RESULT_Y - 5;
        PartyGui.panel(context, fx, fy, 26, 26, ready ? CARD_PICKED : CARD);
        slot(context, x + RESULT_X, y + RESULT_Y);
        // The arrow, centred between the second payment slot and the frame
        TradeOffer offer = pickedOffer();
        boolean blocked = offer != null && (offer.isDisabled() || !canBuyMore());
        drawArrow(context, x + 182, y + PAY_Y + 4, 28, blocked ? RED : ready ? LIME_DARK : INK);
        if (blocked) drawCross(context, x + 191, y + PAY_Y + 3);
    }

    /** A right arrow, its shaft {@code w} long, 9 px tall, from ({@code x}, {@code y}). */
    private static void drawArrow(DrawContext context, int x, int y, int w, int color) {
        context.fill(x, y + 3, x + w - 4, y + 6, color);
        for (int i = 0; i < 5; i++) context.fill(x + w - 5 + i, y + i, x + w - 4 + i, y + 9 - i, color);
    }

    private static void drawCross(DrawContext context, int x, int y) {
        for (int i = 0; i < 9; i++) {
            PartyGui.pixel(context, x + i + 1, y + i + 1, RED_DARK);
            PartyGui.pixel(context, x + 9 - i, y + i + 1, RED_DARK);
            context.fill(x + i, y + i, x + i + 1, y + i + 1, RED);
            context.fill(x + 8 - i, y + i, x + 9 - i, y + i + 1, RED);
        }
    }

    /** The mod's rounded slot ({@link PartyGui#slot}) under the item drawn at ({@code itemX}, {@code itemY}), in kraft. */
    private static void slot(DrawContext context, int itemX, int itemY) {
        PartyGui.slot(context, itemX, itemY, SLOT, SLOT_TOP);
    }

    protected @Nullable TradeOffer pickedOffer() {
        TradeOfferList offers = offers();
        return picked >= 0 && picked < offers.size() ? offers.get(picked) : null;
    }

    /** Whether one more purchase is allowed here (a shop stop counts them). */
    protected boolean canBuyMore() {
        return true;
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // No titles: the sign names the merchant, the cards and the slots speak for themselves
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        scroll = MathHelper.clamp(scroll, 0, maxScroll());
        for (OfferCard card : cards) card.visible = card.offer() != null;
        super.render(context, mouseX, mouseY, delta);
        drawGhosts(context);
        drawOfferTooltip(context, mouseX, mouseY);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /** The picked offer's items, faded, in its empty payment slots and result. */
    private void drawGhosts(DrawContext context) {
        TradeOffer offer = pickedOffer();
        if (offer == null) return;
        ghost(context, 0, offer.getDisplayedFirstBuyItem());
        ghost(context, 1, offer.getDisplayedSecondBuyItem());
        ghost(context, 2, offer.getSellItem());
    }

    private void ghost(DrawContext context, int slot, ItemStack stack) {
        Slot s = handler.getSlot(slot);
        if (stack.isEmpty() || s.hasStack()) return;
        PartyGui.ghostItem(context, stack, this.x + s.x, this.y + s.y, textRenderer, GHOST_VEIL);
    }

    private void drawOfferTooltip(DrawContext context, int mouseX, int mouseY) {
        if (!handler.getCursorStack().isEmpty()) return;
        for (OfferCard card : cards) {
            if (!card.visible || !card.isMouseOver(mouseX, mouseY)) continue;
            TradeOffer offer = card.offer();
            if (offer == null) return;
            int dx = mouseX - card.getX();
            ItemStack stack = dx < OfferCard.PRICE2_X ? offer.getDisplayedFirstBuyItem()
                    : dx < OfferCard.ARROW_X - 1 ? offer.getDisplayedSecondBuyItem()
                    : dx >= OfferCard.SOLD_X - 1 && dx < OfferCard.SOLD_X + 17 ? offer.getSellItem() : ItemStack.EMPTY;
            if (stack.isEmpty()) return;
            List<Text> lines = new ArrayList<>(Screen.getTooltipFromItem(MinecraftClient.getInstance(), stack));
            if (offer.isDisabled()) lines.add(Text.translatable("message.steveparty.shop.out_of_stock").formatted(Formatting.RED));
            context.drawTooltip(textRenderer, lines, stack.getTooltipData(), mouseX, mouseY);
            return;
        }
    }

    // ---------------------------------------------------------------- mouse

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll() > 0) {
            scroll = MathHelper.clamp(scroll - (int) Math.signum(verticalAmount), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int barX = this.x + BAR_X, barY = this.y + TOP;
        if (button == 0 && maxScroll() > 0 && mouseX >= barX && mouseX < barX + BAR_W
                && mouseY >= barY && mouseY < barY + visibleCards * CARD_STEP) {
            draggingBar = true;
            dragBar(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (draggingBar) {
            dragBar(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingBar = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void dragBar(double mouseY) {
        float track = visibleCards * CARD_STEP - 1 - THUMB_H;
        float at = (float) (mouseY - (this.y + TOP) - THUMB_H / 2f) / track;
        scroll = MathHelper.clamp(Math.round(at * maxScroll()), 0, maxScroll());
    }

    // ---------------------------------------------------------------- the cards

    /** One offer of the column (the {@code row}-th shown): pick it with a click or Enter. */
    private class OfferCard extends PressableWidget {
        /** The card's content, 4 px from its edges: the prices, the arrow, the item sold. */
        static final int PRICE1_X = 4, PRICE2_X = 21, ARROW_X = 41, SOLD_X = 64;
        private final int row;

        OfferCard(int row, int x, int y) {
            super(x, y, CARD_W, CARD_H, Text.empty());
            this.row = row;
        }

        int index() {
            return scroll + row;
        }

        @Nullable TradeOffer offer() {
            TradeOfferList offers = offers();
            return index() < offers.size() ? offers.get(index()) : null;
        }

        @Override
        public void onPress() {
            if (offer() != null) pick(index());
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            TradeOffer offer = offer();
            if (offer == null) return;
            setMessage(offer.getSellItem().getName());
            boolean soldOut = offer.isDisabled();
            boolean isPicked = index() == picked;
            boolean keyboard = isFocused() && MinecraftClient.getInstance().getNavigationType().isKeyboard();
            boolean lit = isHovered() || keyboard;
            PartyGui.Theme theme = isPicked ? CARD_PICKED : soldOut ? CARD_SOLD_OUT : CARD;
            if (lit && !isPicked) theme = theme.brighter();
            int x = getX(), y = getY();
            PartyGui.button(context, x, y, width, height, theme, isPicked);
            if (lit) context.drawBorder(x, y, width, height, 0xFFFFFFFF);
            int dy = y + 1 + (isPicked ? 1 : 0);
            int priceColor = canPay(offer) ? INK : PRICE_MISSING;
            item(context, offer.getDisplayedFirstBuyItem(), x + PRICE1_X, dy, priceColor, soldOut);
            item(context, offer.getDisplayedSecondBuyItem(), x + PRICE2_X, dy, priceColor, soldOut);
            drawArrow(context, x + ARROW_X, dy + 4, 18, soldOut ? 0xFF7E715F : isPicked ? 0xFF1E3A08 : INK);
            item(context, offer.getSellItem(), x + SOLD_X, dy, INK, soldOut);
            if (soldOut) drawCross(context, x + ARROW_X + 4, dy + 3);
        }

        /** An item of the card with its count ({@code color}), faded when sold out. */
        private void item(DrawContext context, ItemStack stack, int x, int y, int color, boolean faded) {
            if (stack.isEmpty()) return;
            context.drawItem(stack, x, y);
            if (faded) {
                PartyGui.veil(context, x, y, 0x88AD9F8C);
                return;
            }
            context.drawItemInSlot(textRenderer, stack, x, y, "");
            if (stack.getCount() > 1) {
                String count = String.valueOf(stack.getCount());
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200);
                UiText.line(context, textRenderer, count, x + 19 - 2 - textRenderer.getWidth(count), y + 9, textRenderer.getWidth(count), color, false);
                context.getMatrices().pop();
            }
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}
