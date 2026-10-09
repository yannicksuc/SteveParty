package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGameText;
import net.fabricmc.fabric.api.client.rendering.v1.TooltipComponentCallback;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A mini-game page in a tooltip (inventory, catalogue, party controller): its picture, its formats (pawn chips), and
 * the start of its description. Empty until the page's content is known (it is asked to the server).
 */
public class MiniGamePageTooltipComponent implements TooltipComponent {
    private static final int WIDTH = 144, PICTURE_HEIGHT = 81;
    private static final int MAX_DESCRIPTION_LINES = 4;
    private static final int COLOR_TYPE = 0xFFFFC52E, COLOR_DESCRIPTION = 0xFFBFBFBF;
    private final UUID page;

    public MiniGamePageTooltipComponent(UUID page) {
        this.page = page;
    }

    public static void register() {
        TooltipComponentCallback.EVENT.register(data -> data instanceof MiniGamePageItem.PageTooltip tooltip
                ? new MiniGamePageTooltipComponent(tooltip.page()) : null);
    }

    /** « 2 contre 2 · 1 contre 3+ » : the formats of a mini-game, by name. */
    public static MutableText formatsText(MiniGamePageData data) {
        MutableText text = Text.empty();
        for (int i = 0; i < data.formats().size(); i++) {
            if (i > 0) text.append(" · ");
            text.append(data.formats().get(i).name());
        }
        return text;
    }

    private static FormatChips.Look look(MiniGamePageData data, int index) {
        return new FormatChips.Look(false, false, !data.hasPipesFor(data.formats().get(index)), false, 13);
    }

    private static List<OrderedText> lines(TextRenderer textRenderer, Text text, int max) {
        return wrap(textRenderer, text, WIDTH, max);
    }

    /** The text wrapped to {@code width}, on {@code max} lines at most: when it is longer, the last line ends with « … ». */
    public static List<OrderedText> wrap(TextRenderer textRenderer, Text text, int width, int max) {
        List<StringVisitable> wrapped = textRenderer.getTextHandler().wrapLines(text, width, Style.EMPTY);
        List<OrderedText> lines = new ArrayList<>(Math.min(max, wrapped.size()));
        for (int i = 0; i < wrapped.size() && i < max; i++) {
            StringVisitable line = wrapped.get(i);
            if (i == max - 1 && wrapped.size() > max) {
                line = GuiText.cut(textRenderer, line, width);
            }
            lines.add(Language.getInstance().reorder(line));
        }
        return lines;
    }

    @Override
    public int getHeight() {
        TextRenderer textRenderer = net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        MiniGamePageData data = MiniGamePageClient.page(page);
        if (data == null || data.isBlank()) return 0;
        int height = data.image() != null ? PICTURE_HEIGHT + 3 : 0;
        java.util.List<int[]> chips = FormatChips.flow(textRenderer, data.formats(), i -> look(data, i), WIDTH, 3);
        height += chips.getLast()[1] + 13 + 3;
        if (!data.isPlayable()) height += 10;
        if (!data.description().isEmpty()) height += 2 + 10 * lines(textRenderer, MiniGameText.parse(data.description()), MAX_DESCRIPTION_LINES).size();
        return height + 2;
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        MiniGamePageData data = MiniGamePageClient.page(page);
        return data == null || data.isBlank() ? 0 : WIDTH;
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext context) {
        MiniGamePageData data = MiniGamePageClient.page(page);
        if (data == null || data.isBlank()) return;
        int top = y;
        if (data.image() != null) {
            context.fill(x, top, x + WIDTH, top + PICTURE_HEIGHT, 0xFF14181B);
            MiniGamePageClient.Picture picture = MiniGamePageClient.picture(data.image(), WIDTH, PICTURE_HEIGHT);
            if (picture != null) picture.draw(context, x, top, WIDTH, PICTURE_HEIGHT, 0xFFFFFFFF);
            top += PICTURE_HEIGHT + 3;
        }
        top += FormatChips.drawFlow(context, textRenderer, data.formats(), i -> look(data, i), x, top, WIDTH, 3) + 3;
        if (!data.isPlayable()) {
            context.drawText(textRenderer, Text.translatable("tooltip.steveparty.mini_game_page.not_playable"), x, top, 0xFFFF7A7A, true);
            top += 10;
        }
        if (!data.description().isEmpty()) {
            top += 2;
            for (OrderedText line : lines(textRenderer, MiniGameText.parse(data.description()), MAX_DESCRIPTION_LINES)) {
                context.drawText(textRenderer, line, x, top, COLOR_DESCRIPTION, true);
                top += 10;
            }
        }
    }
}
