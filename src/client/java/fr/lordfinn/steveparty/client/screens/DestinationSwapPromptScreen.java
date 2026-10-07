package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.config.ClientOptions;
import net.minecraft.client.font.MultilineText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

/**
 * Asked the first time a cartridge is clicked on another one (see DestinationSwap): what will happen, and whether to
 * keep it that way. « Yes » is put forward; the answer is the client option (ClientOptions), changed later in the
 * options screen. Escape: nothing chosen, asked again next time. Back to the screen it was opened over.
 */
public class DestinationSwapPromptScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int GOLD = 0xFFFFC52E;

    private final @Nullable Screen parent;
    private MultilineText body = MultilineText.EMPTY;
    private int panelTop;
    private int panelHeight;
    private ButtonWidget yes;

    public DestinationSwapPromptScreen(@Nullable Screen parent) {
        super(Text.translatable("screen.steveparty.destination_swap.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (parent != null) parent.resize(client, width, height);
        body = MultilineText.create(textRenderer, Text.translatable("screen.steveparty.destination_swap.body"), PANEL_W - 24);
        int textHeight = body.count() * textRenderer.fontHeight;
        panelHeight = 24 + textHeight + 12 + 20 + 6 + 20 + 12;
        panelTop = (height - panelHeight) / 2;
        int buttonsTop = panelTop + 24 + textHeight + 12;
        int left = (width - (PANEL_W - 24)) / 2;
        yes = addDrawableChild(ButtonWidget.builder(Text.translatable("screen.steveparty.destination_swap.yes").formatted(Formatting.GOLD, Formatting.BOLD),
                button -> choose(true)).dimensions(left, buttonsTop, PANEL_W - 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.steveparty.destination_swap.no"),
                button -> choose(false)).dimensions(left, buttonsTop + 26, PANEL_W - 24, 20).build());
        setInitialFocus(yes);
    }

    private void choose(boolean swap) {
        ClientOptions.setDestinationSwap(swap);
        if (client != null && client.player != null) {
            client.player.sendMessage(Text.translatable(swap ? "screen.steveparty.destination_swap.chosen_yes"
                    : "screen.steveparty.destination_swap.chosen_no"), true);
        }
        close();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // The screen it was opened over, dimmed, then the panel
        if (parent != null) parent.render(context, -1, -1, delta);
        context.fill(0, 0, width, height, 0xA0000000);
        int left = (width - PANEL_W) / 2;
        context.fill(left, panelTop, left + PANEL_W, panelTop + panelHeight, 0xF0201810);
        context.drawBorder(left, panelTop, PANEL_W, panelHeight, GOLD);
        context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD), width / 2, panelTop + 9, 0xFFFFFF);
        body.drawCenterWithShadow(context, width / 2, panelTop + 24);
        // The call to action: a gold glow around « Yes »
        context.drawBorder(yes.getX() - 2, yes.getY() - 2, yes.getWidth() + 4, yes.getHeight() + 4, GOLD);
        for (var child : children()) {
            if (child instanceof ButtonWidget button) button.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Drawn in render (the parent screen behind)
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
