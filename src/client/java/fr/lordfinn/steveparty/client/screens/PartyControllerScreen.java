package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.screen_handlers.custom.PartyControllerScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Party controller settings: coin and star items, then the party program cards. */
public class PartyControllerScreen extends HandledScreen<PartyControllerScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/party_controller.png");
    private static final int LABEL_COLOR = 0xFFFFFF;
    private static final int TOOLTIP_WIDTH = 150;

    public PartyControllerScreen(PartyControllerScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 168;
        this.playerInventoryTitleY = this.backgroundHeight - 94;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, 256, 256);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.title, this.titleX, this.titleY, LABEL_COLOR, true);
        context.drawText(this.textRenderer, Text.translatable("screen.steveparty.party_controller.labels"), 48, 23, LABEL_COLOR, true);
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX, this.playerInventoryTitleY, 0x404040, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
        // Empty settings slot: what goes there
        Slot slot = this.focusedSlot;
        if (slot != null && !slot.hasStack() && slot.id < PartyControllerEntity.SETTINGS_SIZE) {
            String key = slot.id == PartyControllerEntity.SLOT_COIN ? "coin"
                    : slot.id == PartyControllerEntity.SLOT_STAR ? "star" : "program";
            // Long hints are wrapped so the tooltip does not hide the slots
            List<OrderedText> lines = new ArrayList<>();
            lines.add(Text.translatable("screen.steveparty.party_controller." + key).asOrderedText());
            lines.addAll(this.textRenderer.wrapLines(
                    Text.translatable("screen.steveparty.party_controller." + key + ".hint").withColor(0xAAAAAA), TOOLTIP_WIDTH));
            context.drawOrderedTooltip(this.textRenderer, lines, mouseX, mouseY);
        }
    }
}
