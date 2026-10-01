package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.client.minigame.MiniGamePageClient;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.minigame.MiniGameMode;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
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
 * A mini-game page in a tooltip (inventory, catalogue, party controller): its picture, how it is played, and the
 * start of its description. Empty until the page's content is known (it is asked to the server).
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

    /** « Free for all · 2 teams » : the ways a mini-game can be played. */
    public static MutableText modesText(MiniGamePageData data) {
        MutableText text = Text.empty();
        boolean first = true;
        for (MiniGameMode mode : data.modes()) {
            if (!first) text.append(" · ");
            text.append(mode.text());
            first = false;
        }
        return text;
    }

    /** « 2 to 4 players », « 4 players ». */
    public static Text playersText(MiniGamePageData data) {
        return data.minPlayers() == data.maxPlayers()
                ? Text.translatable("tooltip.steveparty.mini_game_page.players.exact", data.minPlayers())
                : Text.translatable("tooltip.steveparty.mini_game_page.players", data.minPlayers(), data.maxPlayers());
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
                line = StringVisitable.concat(textRenderer.trimToWidth(line, Math.max(0, width - textRenderer.getWidth("…"))), StringVisitable.plain("…"));
            }
            lines.add(Language.getInstance().reorder(line));
        }
        return lines;
    }

    @Override
    public int getHeight(TextRenderer textRenderer) {
        MiniGamePageData data = MiniGamePageClient.page(page);
        if (data == null || data.isBlank()) return 0;
        int height = data.image() != null ? PICTURE_HEIGHT + 3 : 0;
        height += 10 * lines(textRenderer, modesText(data), 2).size() + 10;
        if (!data.description().isEmpty()) height += 2 + 10 * lines(textRenderer, Text.literal(data.description()), MAX_DESCRIPTION_LINES).size();
        return height + 2;
    }

    @Override
    public int getWidth(TextRenderer textRenderer) {
        MiniGamePageData data = MiniGamePageClient.page(page);
        return data == null || data.isBlank() ? 0 : WIDTH;
    }

    @Override
    public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext context) {
        MiniGamePageData data = MiniGamePageClient.page(page);
        if (data == null || data.isBlank()) return;
        int top = y;
        if (data.image() != null) {
            context.fill(x, top, x + WIDTH, top + PICTURE_HEIGHT, 0xFF14181B);
            MiniGamePageClient.Picture picture = MiniGamePageClient.picture(data.image(), WIDTH, PICTURE_HEIGHT);
            if (picture != null) picture.draw(context, x, top, WIDTH, PICTURE_HEIGHT, 0xFFFFFFFF);
            top += PICTURE_HEIGHT + 3;
        }
        for (OrderedText line : lines(textRenderer, modesText(data), 2)) {
            context.drawText(textRenderer, line, x, top, COLOR_TYPE, true);
            top += 10;
        }
        context.drawText(textRenderer, playersText(data), x, top, COLOR_DESCRIPTION, true);
        top += 10;
        if (!data.description().isEmpty()) {
            top += 2;
            for (OrderedText line : lines(textRenderer, Text.literal(data.description()), MAX_DESCRIPTION_LINES)) {
                context.drawText(textRenderer, line, x, top, COLOR_DESCRIPTION, true);
                top += 10;
            }
        }
    }
}
