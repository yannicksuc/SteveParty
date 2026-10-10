package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.screen_handlers.custom.ShopStopScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * The merchant's screen at a shop stop: the merchant screen, the time left and the purchases allowed above it and,
 * under it, « Buy nothing » (« Done » once something was bought). Closing the screen does the same.
 */
public class ShopStopScreen extends MerchantScreen {
    private static final int BUTTON_WIDTH = 140, BUTTON_HEIGHT = 20, GAP = 4;
    private final ShopStopScreenHandler shopHandler;
    private ButtonWidget doneButton;

    public ShopStopScreen(ShopStopScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.shopHandler = handler;
    }

    @Override
    protected void init() {
        super.init();
        int buttonY = Math.min(this.y + this.backgroundHeight + GAP, this.height - BUTTON_HEIGHT - 2);
        doneButton = ButtonWidget.builder(label(), button -> buyNothing())
                .dimensions(this.x + (this.backgroundWidth - BUTTON_WIDTH) / 2, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT)
                .tooltip(Tooltip.of(Text.translatable("gui.steveparty.shop_stop.buy_nothing.hint")))
                .build();
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (doneButton != null) doneButton.setMessage(label());
        super.render(context, mouseX, mouseY, delta);
        int seconds = shopHandler.getSecondsLeft();
        Text line = Text.translatable("gui.steveparty.shop_stop.status",
                        Text.translatable("gui.steveparty.shop_stop.purchases", shopHandler.getPurchases(), Math.max(1, shopHandler.getLimit()))
                                .formatted(Formatting.YELLOW),
                        Text.translatable("gui.steveparty.shop_stop.time_left", seconds)
                                .formatted(seconds <= 10 ? Formatting.RED : Formatting.GOLD));
        int lineY = Math.max(2, this.y - 12);
        // Centred over the merchant screen, as wide as it
        UiText.centered(context, this.textRenderer, line, this.x, lineY, this.backgroundWidth, 0xFFFFFF, true);
    }
}
