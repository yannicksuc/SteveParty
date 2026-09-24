package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.screen_handlers.custom.TradingStallScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

public class TradingStallScreen extends HandledScreen<TradingStallScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/trading_stall.png");
    // Sale mode button (relative to the GUI), under the trader key slot; drawn with the vanilla button sprites
    private static final int MODE_BUTTON_X = 181;
    private static final int MODE_BUTTON_Y = 82;
    private static final int MODE_BUTTON_SIZE = 18;
    private static final Identifier BUTTON_SPRITE = Identifier.ofVanilla("widget/button");
    private static final Identifier BUTTON_HIGHLIGHTED_SPRITE = Identifier.ofVanilla("widget/button_highlighted");
    private static final int TOOLTIP_WIDTH = 200;

    public TradingStallScreen(TradingStallScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 184;
        this.backgroundHeight = 187;
        this.playerInventoryTitleY = this.backgroundHeight - 93;
        this.playerInventoryTitleX += 4;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (this.width - this.backgroundWidth) / 2; //12 is the size of the additional hiding trader slot
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground,
                TEXTURE, x, y, 0,0,
                this.backgroundWidth, this.backgroundHeight, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawSaleModeButton(context, mouseX, mouseY);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
    }

    private boolean isOverModeButton(double mouseX, double mouseY) {
        return isPointWithinBounds(MODE_BUTTON_X, MODE_BUTTON_Y, MODE_BUTTON_SIZE, MODE_BUTTON_SIZE, mouseX, mouseY);
    }

    /** Icon of the current mode: emerald = free shop, redstone torch = sale available, redstone dust = waiting. */
    private ItemStack getModeIcon() {
        if (handler.getSaleMode() == TradingStallBlockEntity.SaleMode.FREE) return new ItemStack(Items.EMERALD);
        return new ItemStack(handler.hasSaleCredit() ? Items.REDSTONE_TORCH : Items.REDSTONE);
    }

    private void drawSaleModeButton(DrawContext context, int mouseX, int mouseY) {
        int screenX = this.x + MODE_BUTTON_X;
        int screenY = this.y + MODE_BUTTON_Y;
        boolean hovered = isOverModeButton(mouseX, mouseY);
        context.drawGuiTexture(RenderLayer::getGuiTextured, hovered ? BUTTON_HIGHLIGHTED_SPRITE : BUTTON_SPRITE,
                screenX, screenY, MODE_BUTTON_SIZE, MODE_BUTTON_SIZE);
        context.drawItem(getModeIcon(), screenX + 1, screenY + 1);
        if (hovered) {
            context.drawOrderedTooltip(this.textRenderer, getModeTooltip(), mouseX, mouseY);
        }
    }

    private List<OrderedText> getModeTooltip() {
        TradingStallBlockEntity.SaleMode mode = handler.getSaleMode();
        List<OrderedText> lines = new ArrayList<>();
        lines.add(modeName(mode).formatted(Formatting.GOLD).asOrderedText());
        lines.addAll(this.textRenderer.wrapLines(modeDescription(mode).formatted(Formatting.GRAY), TOOLTIP_WIDTH));
        if (mode == TradingStallBlockEntity.SaleMode.ONE_SALE_PER_SIGNAL) {
            lines.add((handler.hasSaleCredit()
                    ? Text.translatableWithFallback("gui.steveparty.trading_stall.credit.available", "Sale available").formatted(Formatting.GREEN)
                    : Text.translatableWithFallback("gui.steveparty.trading_stall.credit.waiting", "Waiting for a redstone signal").formatted(Formatting.RED))
                    .asOrderedText());
        }
        lines.addAll(this.textRenderer.wrapLines(Text.translatableWithFallback("gui.steveparty.trading_stall.mode.click",
                "Click to change the sale mode").formatted(Formatting.DARK_GRAY, Formatting.ITALIC), TOOLTIP_WIDTH));
        return lines;
    }

    private static MutableText modeName(TradingStallBlockEntity.SaleMode mode) {
        return switch (mode) {
            case FREE -> Text.translatableWithFallback("gui.steveparty.trading_stall.mode.free", "Free shop");
            case ONE_SALE_PER_SIGNAL -> Text.translatableWithFallback("gui.steveparty.trading_stall.mode.one_sale_per_signal", "One sale per signal");
        };
    }

    private static MutableText modeDescription(TradingStallBlockEntity.SaleMode mode) {
        return switch (mode) {
            case FREE -> Text.translatableWithFallback("gui.steveparty.trading_stall.mode.free.description",
                    "Customers buy as much as they want while there is stock.");
            case ONE_SALE_PER_SIGNAL -> Text.translatableWithFallback("gui.steveparty.trading_stall.mode.one_sale_per_signal.description",
                    "Each redstone pulse on the stall allows a single sale, then the stall locks again.");
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverModeButton(mouseX, mouseY) && this.client != null && this.client.interactionManager != null) {
            this.client.interactionManager.clickButton(handler.syncId, TradingStallScreenHandler.SALE_MODE_BUTTON_ID);
            this.client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        //context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0xfff1f1f1, false);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 4210752, false);
    }
}
