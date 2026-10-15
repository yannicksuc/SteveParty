package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.HandCursor;
import fr.lordfinn.steveparty.client.gui.ButtonIcons;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.StallArt;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStallScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * A Shop Cartridge's offers on a Trading Stall: the stall block's screen ({@link StallArt}), its slots holding ghost
 * copies. Per column, the item sold over the awning, its price and second price under it, quantities with the mouse
 * wheel; on each column's arrow, how many times the shop's storage can still sell it. The ◀ button beside the awning
 * goes back to the menu it was opened from.
 */
public class ShopStallScreen extends HandledScreen<ShopStallScreenHandler> {
    private static final String KEY = "gui.steveparty.shop_stall.";
    private static final int WIDTH = 184, HEIGHT = 187, BACK_SIZE = 18, BACK_GAP = 4;
    /** The arrows' band, where a column's stock is (its tooltip). */
    private static final int ARROWS_Y = 33, ARROWS_H = 18;

    public ShopStallScreen(ShopStallScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = WIDTH;
        this.backgroundHeight = HEIGHT;
        this.playerInventoryTitleY = HEIGHT - 93;
        this.playerInventoryTitleX += 4;
    }

    private int backX() {
        return x - BACK_SIZE - BACK_GAP;
    }

    private int backY() {
        return y + 17;
    }

    private boolean overBack(double mouseX, double mouseY) {
        return HitArea.contains(mouseX, mouseY, backX(), backY(), BACK_SIZE, BACK_SIZE);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(StallArt.TEXTURE, x, y, 0, 0, WIDTH, HEIGHT, 256, 256);
    }

    @Override
    protected void init() {
        super.init();
        // Back to the menu it was opened from: a button of the mod's, as on its other screens
        PartyButton back = new PartyButton(backX(), backY(), BACK_SIZE, BACK_SIZE, Text.literal("◀"), button -> {
            if (client != null && client.interactionManager != null)
                client.interactionManager.clickButton(handler.syncId, ShopStallScreenHandler.BACK);
        });
        back.icon(ButtonIcons.Icon.BACK).setTooltip(Tooltip.of(Text.translatable(KEY + "back")));
        addDrawableChild(back);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        // No title (the awning says it); the inventory's up to its panel's right padding, as on the stall
        UiText.line(context, textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY,
                180 - 8 - playerInventoryTitleX, 4210752, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        for (int column = 0; column < ShopCartridgeItem.OFFERS; column++) {
            Slot price = handler.slots.get(column);
            StallArt.drawBasePrice(context, textRenderer, price.getStack(), handler.slots.get(column + ShopCartridgeItem.OFFERS).getStack(),
                    handler.slots.get(column + 2 * ShopCartridgeItem.OFFERS).getStack(), x + price.x, y + price.y);
            StallArt.drawStock(context, textRenderer, x, y, column, handler.stock(column));
        }
        boolean back = overBack(mouseX, mouseY);
        HandCursor.update(back);
        if (back) return;
        // An empty slot says what it takes; an arrow, its column's stock
        if (focusedSlot != null && !focusedSlot.hasStack() && handler.getCursorStack().isEmpty()
                && focusedSlot.id < ShopCartridgeItem.SLOTS) {
            context.drawTooltip(textRenderer, StallArt.slotHelp(focusedSlot.id), mouseX, mouseY);
            return;
        }
        List<Text> stock = stockUnder(mouseX, mouseY);
        if (stock != null) {
            context.drawTooltip(textRenderer, stock, mouseX, mouseY);
            return;
        }
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /** The stock tooltip of the arrow under the mouse, or null. */
    private List<Text> stockUnder(int mouseX, int mouseY) {
        for (int column = 0; column < ShopCartridgeItem.OFFERS; column++) {
            int ax = x + StallArt.arrowX(column) - ShopStallScreenHandler.SLOT / 2;
            if (HitArea.contains(mouseX, mouseY, ax, y + ARROWS_Y, ShopStallScreenHandler.SLOT, ARROWS_H)) {
                return StallArt.stockHelp(handler.stock(column));
            }
        }
        return null;
    }

    @Override
    protected List<Text> getTooltipFromItem(ItemStack stack) {
        List<Text> tooltip = super.getTooltipFromItem(stack);
        if (focusedSlot == null || focusedSlot.id >= ShopCartridgeItem.SLOTS) return tooltip;
        List<Text> lines = new ArrayList<>(tooltip);
        lines.addAll(StallArt.slotHelp(focusedSlot.id));
        return lines;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean isClickOutsideBounds(double mouseX, double mouseY, int left, int top, int button) {
        return !overBack(mouseX, mouseY) && super.isClickOutsideBounds(mouseX, mouseY, left, top, button);
    }

    /** The wheel over an offer: its quantity (predicted here, applied by the server). */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (CartridgeScreen.scrollGhost(handler.slots, handler.syncId, x, y, mouseX, mouseY, verticalAmount + horizontalAmount)) return true;
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        super.removed();
        HandCursor.reset();
    }
}
