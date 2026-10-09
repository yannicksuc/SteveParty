package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** A tamed Trichaudron's screen, a horse's look: its saddle slot, itself where a horse shows itself, and how full its tank is. */
public class TrichaudronScreen extends HandledScreen<TrichaudronScreenHandler> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/horse.png");
    private static final Identifier SADDLE_SLOT = Identifier.ofVanilla("container/horse/saddle_slot");
    /** Its preview's scale (pixels a block: it is huge) and the lava gauge's width. */
    private static final int PREVIEW_SIZE = 6, GAUGE_WIDTH = 5;

    public TrichaudronScreen(TrichaudronScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2, y = (height - backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
        context.drawGuiTexture(SADDLE_SLOT, x + 7, y + 35 - 18, 18, 18);
        TrichaudronEntity trichaudron = handler.trichaudron();
        if (trichaudron == null) return;
        // itself in the horse's preview box, small enough for its necks, turning after the mouse
        int left = x + 26, top = y + 18, right = x + 78, bottom = y + 70;
        InventoryScreen.drawEntity(context, left, top, right - GAUGE_WIDTH - 1, bottom, PREVIEW_SIZE, 0.0625f,
                mouseX, mouseY, trichaudron);
        // its tank, a lava gauge along the box's right edge, and the count under it
        int tank = trichaudron.getTank();
        int gaugeLeft = right - GAUGE_WIDTH, gaugeTop = top + 1, gaugeBottom = bottom - 11;
        context.fill(gaugeLeft, gaugeTop, right - 1, gaugeBottom, 0xFF2A1A14);
        int filled = Math.round((gaugeBottom - gaugeTop) * tank / (float) TrichaudronEntity.TANK_MAX);
        context.fill(gaugeLeft, gaugeBottom - filled, right - 1, gaugeBottom, 0xFFE0601C);
        context.fill(left, bottom - 10, right, bottom, 0xA0000000);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.steveparty.trichaudron.tank", tank,
                TrichaudronEntity.TANK_MAX), (left + right) / 2, bottom - 9, 0xFFFFFFFF);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
