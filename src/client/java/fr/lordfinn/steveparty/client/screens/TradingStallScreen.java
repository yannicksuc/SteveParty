package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.screen_handlers.custom.TradingStallScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * The offers of a shop, one per column: the item sold, its price and an optional second price. Every slot of the
 * stall says what it is for (empty, or added to the item's tooltip): the items here are models the customer sees in
 * the trader's screen, a sale takes the item from the shop's stock containers.
 */
public class TradingStallScreen extends HandledScreen<TradingStallScreenHandler> {
    private static final String KEY = "gui.steveparty.trading_stall.slot.";
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/trading_stall.png");

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
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0,0,
                this.backgroundWidth, this.backgroundHeight, 256, 256);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);
        // An empty slot of the stall says what it takes
        if (focusedSlot != null && !focusedSlot.hasStack() && handler.getCursorStack().isEmpty()
                && focusedSlot.id < TradingStallBlockEntity.SIZE) {
            context.drawTooltip(textRenderer, slotHelp(focusedSlot.id), mouseX, mouseY);
        }
    }

    @Override
    protected List<Text> getTooltipFromItem(ItemStack stack) {
        List<Text> tooltip = super.getTooltipFromItem(stack);
        if (focusedSlot == null || focusedSlot.id >= TradingStallBlockEntity.SIZE) return tooltip;
        List<Text> lines = new ArrayList<>(tooltip);
        lines.addAll(slotHelp(focusedSlot.id));
        return lines;
    }

    /** What a slot of the stall is (its index: 0-8 the price, 9-17 the second price, 18-26 the item sold). */
    private static List<Text> slotHelp(int slot) {
        String role = switch (slot / 9) {
            case 0 -> "price";
            case 1 -> "second_price";
            default -> "sold";
        };
        return List.of(Text.translatable(KEY + role).formatted(Formatting.GOLD),
                Text.translatable(KEY + role + ".help").formatted(Formatting.GRAY),
                Text.translatable(KEY + "models").formatted(Formatting.DARK_GRAY, Formatting.ITALIC));
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        //context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, 0xfff1f1f1, false);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 4210752, false);
    }
}
