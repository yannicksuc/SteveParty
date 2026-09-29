package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.screen_handlers.custom.RouterScreenHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

public class RouterScreen extends CartridgeContainerScreen<RouterScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/router.png");
    private static final Text COMPARATOR_HELP = Text.translatable("gui.steveparty.router.comparator");
    private static final int HELP_PADDING = 5;
    /** The comparator help, wrapped once per layout (not per frame). */
    private List<OrderedText> help = List.of();

    public RouterScreen(RouterScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title, 136);
    }

    @Override
    protected void init() {
        super.init();
        help = textRenderer.wrapLines(COMPARATOR_HELP, backgroundWidth - 2 * HELP_PADDING);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        drawComparatorHelp(context);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }

    /** Above the interface (under it if there is no room): what a comparator reading the router outputs. */
    private void drawComparatorHelp(DrawContext context) {
        if (help.isEmpty()) return;
        int x = (width - backgroundWidth) / 2;
        int top = (height - backgroundHeight) / 2;
        int h = help.size() * (textRenderer.fontHeight + 1) + 2 * HELP_PADDING - 1;
        int y = top - h - 2 >= 0 ? top - h - 2 : top + backgroundHeight + 2;
        PartyGui.panel(context, x, y, backgroundWidth, h, PartyGui.PANEL);
        for (int i = 0; i < help.size(); i++) {
            context.drawText(textRenderer, help.get(i), x + HELP_PADDING, y + HELP_PADDING + i * (textRenderer.fontHeight + 1),
                    PartyGui.TEXT_DARK, false);
        }
    }

    @Override
    public Identifier getTexture() {
        return TEXTURE;
    }

}
