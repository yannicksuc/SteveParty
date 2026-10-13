package fr.lordfinn.steveparty.client.compat.rei;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import me.shedaniel.math.Point;
import me.shedaniel.math.Rectangle;
import me.shedaniel.rei.api.client.gui.Renderer;
import me.shedaniel.rei.api.client.gui.widgets.Widget;
import me.shedaniel.rei.api.client.gui.widgets.Widgets;
import me.shedaniel.rei.api.client.registry.display.DisplayCategory;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.util.EntryStacks;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * « Dice Forge »: the core waking the forge and the 5 fragments on top, then faces + blank faces + modules -> forged
 * dice, and how to run it.
 */
public class DiceForgeCategory implements DisplayCategory<DiceForgeDisplay> {
    private static final int WIDTH = 160, HEIGHT = 112, LINE = 10, GREY = 0xFF404040, LIGHT = 0xFFBBBBBB;

    @Override
    public CategoryIdentifier<? extends DiceForgeDisplay> getCategoryIdentifier() {
        return SteveReiPlugin.DICE_FORGE;
    }

    @Override
    public Text getTitle() {
        return Text.translatable("rei.steveparty.dice_forge");
    }

    @Override
    public Renderer getIcon() {
        return EntryStacks.of(ModBlocks.DICE_FORGE);
    }

    @Override
    public int getDisplayWidth(DiceForgeDisplay display) {
        return WIDTH;
    }

    /** Tall enough for the whole explanation in the current language (it was cut after 5 lines in French). */
    @Override
    public int getDisplayHeight() {
        return Math.max(HEIGHT, TEXT_TOP + howLines().size() * LINE + 4);
    }

    /** Where the explanation starts, from the top of the display. */
    private static final int TEXT_TOP = 54;
    private String howFor;
    private List<Text> howLines = List.of();

    /** The explanation wrapped to the display's width, kept until the language changes. */
    private List<Text> howLines() {
        Text how = Text.translatable("rei.steveparty.dice_forge.how");
        String key = how.getString();
        if (!key.equals(howFor)) {
            List<Text> lines = new ArrayList<>();
            CartridgeApplicationCategory.wrap(how, WIDTH - 10, lines);
            howLines = lines;
            howFor = key;
        }
        return howLines;
    }

    @Override
    public List<Widget> setupDisplay(DiceForgeDisplay display, Rectangle bounds) {
        List<Widget> widgets = new ArrayList<>();
        widgets.add(Widgets.createRecipeBase(bounds));
        int x = bounds.getCenterX() - 72, y = bounds.y + 6;
        // Core | 5 fragments
        widgets.add(Widgets.createSlot(new Point(x, y)).entries(display.core()).markInput());
        List<EntryIngredient> fragments = display.fragments();
        for (int i = 0; i < fragments.size(); i++) {
            widgets.add(Widgets.createSlot(new Point(x + 36 + i * 18, y)).entries(fragments.get(i)).markInput());
        }
        // Faces + blank faces + modules -> dice
        int row = y + 24;
        widgets.add(Widgets.createSlot(new Point(x, row)).entries(display.faces()).markInput());
        widgets.add(Widgets.createLabel(new Point(x + 22, row + 5), Text.literal("+")).noShadow().color(GREY, LIGHT));
        widgets.add(Widgets.createSlot(new Point(x + 28, row)).entries(display.blankFaces()).markInput());
        widgets.add(Widgets.createLabel(new Point(x + 50, row + 5), Text.literal("+")).noShadow().color(GREY, LIGHT));
        widgets.add(Widgets.createSlot(new Point(x + 56, row)).entries(display.modules()).markInput());
        widgets.add(Widgets.createArrow(new Point(x + 82, row)));
        widgets.add(Widgets.createResultSlotBackground(new Point(x + 118, row)));
        widgets.add(Widgets.createSlot(new Point(x + 118, row)).entries(display.dice()).disableBackground().markOutput());
        // How
        int textY = bounds.y + TEXT_TOP;
        for (Text line : howLines()) {
            widgets.add(Widgets.createLabel(new Point(bounds.x + 5, textY), line).leftAligned().noShadow().color(GREY, LIGHT));
            textY += LINE;
        }
        return widgets;
    }
}
