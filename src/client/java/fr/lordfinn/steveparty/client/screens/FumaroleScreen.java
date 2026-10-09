package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleEntity;
import fr.lordfinn.steveparty.entities.custom.fumarole.FumaroleScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** A tamed Fumarole's screen, a horse's look: its saddle slot, and how full its tank is where a horse shows itself. */
public class FumaroleScreen extends HandledScreen<FumaroleScreenHandler> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/gui/container/horse.png");
    private static final Identifier SADDLE_SLOT = Identifier.ofVanilla("container/horse/saddle_slot");

    public FumaroleScreen(FumaroleScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (width - backgroundWidth) / 2, y = (height - backgroundHeight) / 2;
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
        context.drawGuiTexture(SADDLE_SLOT, x + 7, y + 35 - 18, 18, 18);
        FumaroleEntity fumarole = handler.fumarole();
        if (fumarole != null) {
            int tank = fumarole.getTank();
            // the tank, in the horse's preview box: a lava gauge
            int left = x + 26, top = y + 18, w = 52, h = 52;
            int filled = Math.round(h * tank / (float) FumaroleEntity.TANK_MAX);
            context.fill(left, top + h - filled, left + w, top + h, 0xFFE0601C);
            context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.steveparty.fumarole.tank", tank, FumaroleEntity.TANK_MAX),
                    left + w / 2, top + h / 2 - 4, 0xFFFFFFFF);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
