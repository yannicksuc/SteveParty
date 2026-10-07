package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.config.ClientOptions;
import fr.lordfinn.steveparty.client.gui.PartyButton;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Asked the first time a cartridge is clicked on another one (see DestinationSwap): what will happen, and whether to
 * keep it that way. « Yes » is put forward; the answer is the client option (ClientOptions), changed later in the
 * options screen. Escape: nothing chosen, asked again next time. Back to the screen it was opened over.
 */
public class DestinationSwapPromptScreen extends Screen {
    private static final int PANEL_W = 300;
    private static final int PAD = 12;
    /** Above everything the screen behind draws (its items and their counts). */
    private static final float Z = 500;

    private final @Nullable Screen parent;
    private List<OrderedText> body = List.of();
    private int panelTop;
    private int panelHeight;

    public DestinationSwapPromptScreen(@Nullable Screen parent) {
        super(Text.translatable("screen.steveparty.destination_swap.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        if (parent != null) parent.resize(client, width, height);
        body = textRenderer.wrapLines(Text.translatable("screen.steveparty.destination_swap.body"), PANEL_W - 2 * PAD);
        int textHeight = body.size() * textRenderer.fontHeight;
        panelHeight = 18 + textHeight + 10 + 20 + 4 + 20 + PAD;
        panelTop = (height - panelHeight) / 2;
        int buttonsTop = panelTop + 18 + textHeight + 10;
        int left = (width - PANEL_W) / 2 + PAD;
        PartyButton yes = addDrawableChild(new PartyButton(left, buttonsTop, PANEL_W - 2 * PAD, 20,
                Text.translatable("screen.steveparty.destination_swap.yes"), button -> choose(true))
                .style(PartyButton.Style.PRIMARY));
        addDrawableChild(new PartyButton(left, buttonsTop + 24, PANEL_W - 2 * PAD, 20,
                Text.translatable("screen.steveparty.destination_swap.no"), button -> choose(false)));
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
        // The screen it was opened over, dimmed, then the panel above all of it
        if (parent != null) parent.render(context, -1, -1, delta);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, Z);
        context.fill(0, 0, width, height, 0xA0000000);
        int left = (width - PANEL_W) / 2;
        PartyGui.panel(context, left, panelTop, PANEL_W, panelHeight, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, width / 2, panelTop - 11, 0, title, PartyGui.BUTTON_SELECTED);
        int y = panelTop + 18;
        for (OrderedText line : body) {
            context.drawText(textRenderer, line, width / 2 - textRenderer.getWidth(line) / 2, y, PartyGui.TEXT_DARK, false);
            y += textRenderer.fontHeight;
        }
        for (var child : children()) {
            if (child instanceof PartyButton button) button.render(context, mouseX, mouseY, delta);
        }
        context.getMatrices().pop();
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
