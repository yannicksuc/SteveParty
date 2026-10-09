package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** A tamed Trichaudron's screen, a horse's look: its saddle slot, and how full its tank is where a horse shows itself. */
public class TrichaudronScreen extends HandledScreen<TrichaudronScreenHandler> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/horse.png");
    private static final Identifier SADDLE_SLOT = Identifier.ofVanilla("container/horse/saddle_slot");

    public TrichaudronScreen(TrichaudronScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2, y = (height - backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
        context.drawGuiTexture(SADDLE_SLOT, x + 7, y + 35 - 18, 18, 18);
        TrichaudronEntity trichaudron = handler.trichaudron();
        if (trichaudron != null) {
            int tank = trichaudron.getTank();
            // the tank, in the horse's preview box: a lava gauge
            int left = x + 26, top = y + 18, w = 52, h = 52;
            int filled = Math.round(h * tank / (float) TrichaudronEntity.TANK_MAX);
            context.fill(left, top + h - filled, left + w, top + h, 0xFFE0601C);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.steveparty.trichaudron.tank", tank, TrichaudronEntity.TANK_MAX),
                    left + w / 2, top + h / 2 - 4, 0xFFFFFFFF);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
