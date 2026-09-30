package fr.lordfinn.steveparty.client.compat.rei;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** « Cartridge application »: a tile and a cartridge give the tile holding it, with how to do it. */
public class CartridgeApplicationCategory implements DisplayCategory<CartridgeApplicationDisplay> {
    private static final int WIDTH = 160, HEIGHT = 92, LINE = 10;

    @Override
    public CategoryIdentifier<? extends CartridgeApplicationDisplay> getCategoryIdentifier() {
        return SteveReiPlugin.CARTRIDGE_APPLICATION;
    }

    @Override
    public Text getTitle() {
        return Text.translatable("rei.steveparty.cartridge_application");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ModBlocks.TILE);
    }

    @Override
    public int getDisplayWidth(CartridgeApplicationDisplay display) {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public List<Widget> setupDisplay(CartridgeApplicationDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        // tile + cartridge -> tile holding it
        int y = bounds.y + 6;
        int x = bounds.getCenterX() - 58;
        widgets.add(Widgets.createSlot(new Point(x, y)).entries(display.tiles()).markInput());
        widgets.add(Widgets.createLabel(new Point(x + 27, y + 5), Text.literal("+")).noShadow().color(0xFF404040, 0xFFBBBBBB));
        widgets.add(Widgets.createSlot(new Point(x + 36, y)).entries(display.cartridge()).markInput());
        widgets.add(Widgets.createArrow(new Point(x + 62, y)));
        widgets.add(Widgets.createResultSlotBackground(new Point(x + 98, y)));
        widgets.add(Widgets.createSlot(new Point(x + 98, y)).entries(display.results()).disableBackground().markOutput());
        // How
        List<Text> lines = new ArrayList<>();
        wrap(Text.translatable("rei.steveparty.cartridge_application.how"), WIDTH - 10, lines);
        if (display.application().advanced()) wrap(Text.translatable("rei.steveparty.cartridge_application.advanced"), WIDTH - 10, lines);
        int textY = y + 26;
        for (Text line : lines) {
            if (textY + LINE > bounds.getMaxY()) break;
            widgets.add(Widgets.createLabel(new Point(bounds.x + 5, textY), line).leftAligned().noShadow().color(0xFF404040, 0xFFBBBBBB));
            textY += LINE;
        }
        return widgets;
    }

    /** Adds {@code text} as lines of at most {@code width} pixels (a REI label doesn't wrap). */
    private static void wrap(Text text, int width, List<Text> lines) {
        TextRenderer font = MinecraftClient.getInstance().textRenderer;
        StringBuilder line = new StringBuilder();
        for (String word : text.getString().split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getWidth(candidate) > width) {
                lines.add(Text.literal(line.toString()));
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (!line.isEmpty()) lines.add(Text.literal(line.toString()));
    }
}
