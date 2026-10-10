package fr.lordfinn.steveparty.client.config;

import fr.lordfinn.steveparty.board.DestinationSwap;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.client.gui.cartridge.CartridgeGuiConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The mod's client options (opened from Mod Menu): what a cartridge clicked on another one does with their
 * destinations, and the electric animation of the cartridge menus.
 */
public class ClientOptionsScreen extends Screen {
    private final @Nullable Screen parent;

    public ClientOptionsScreen(@Nullable Screen parent) {
        super(Text.translatable("options.steveparty.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = width / 2 - 155, y = height / 6 + 24;
        addDrawableChild(CyclingButtonWidget.<DestinationSwap.Preference>builder(value -> Text.translatable(
                        "options.steveparty.destination_swap." + value.name().toLowerCase(Locale.ROOT)))
                .values(List.of(DestinationSwap.Preference.ON, DestinationSwap.Preference.OFF, DestinationSwap.Preference.UNSET))
                .initially(ClientOptions.destinationSwap())
                .tooltip(value -> Tooltip.of(Text.translatable("options.steveparty.destination_swap.tooltip")))
                .build(x, y, 310, 20, Text.translatable("options.steveparty.destination_swap"), (button, value) ->
                        ClientOptions.setDestinationSwap(value == DestinationSwap.Preference.UNSET ? null : value == DestinationSwap.Preference.ON)));
        addDrawableChild(CyclingButtonWidget.onOffBuilder(CartridgeGuiConfig.currentAnimation())
                .tooltip(value -> Tooltip.of(Text.translatable("options.steveparty.cartridge_animation.tooltip")))
                .build(x, y + 24, 310, 20, Text.translatable("options.steveparty.cartridge_animation"),
                        (button, value) -> CartridgeGuiConfig.setCurrentAnimation(value)));
        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, button -> close()).dimensions(width / 2 - 100, height - 27, 200, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        // The screen's width, a margin on each side (odd: centered on the pixel drawCenteredText used)
        int room = (width - 20 - 1) | 1;
        UiText.centered(context, textRenderer, title, width / 2 - room / 2, 15, room, 0xFFFFFF, true);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
