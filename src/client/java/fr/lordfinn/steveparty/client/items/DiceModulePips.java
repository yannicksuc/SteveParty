package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.dice.DiceModule;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.dice.DiceModulesComponent;
import fr.lordfinn.steveparty.items.custom.DefaultDiceItem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The tiny pictograms of the modules of a die, drawn over its icon in the inventories: one 5 px pictogram per module
 * (textures/gui/dice_module/&lt;id&gt;.png), along the top of the slot then down its left side (the die itself sits in
 * the middle, the stack count at the bottom right). More modules than {@link #MAX} end with a "more" pictogram.
 */
public final class DiceModulePips {
    private static final int SIZE = 5;
    /** Top-left corner of each pictogram in the slot. */
    private static final int[][] POSITIONS = {{0, 0}, {5, 0}, {11, 0}, {0, 5}, {0, 11}};
    public static final int MAX = POSITIONS.length;
    private static final Identifier MORE = Steveparty.id("textures/gui/dice_module/more.png");
    private static final Map<String, Identifier> TEXTURES = new ConcurrentHashMap<>();

    private DiceModulePips() {
    }

    public static void draw(DrawContext context, ItemStack stack, int x, int y) {
        if (stack.isEmpty() || !(stack.getItem() instanceof DefaultDiceItem) || !stack.contains(DiceModulesComponent.TYPE)) return;
        List<DiceModule> modules = new ArrayList<>(DiceModules.of(stack).keySet());
        if (modules.isEmpty()) return;
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 200); // over the item, like the stack count
        for (int i = 0; i < Math.min(modules.size(), MAX); i++) {
            Identifier texture = i == MAX - 1 && modules.size() > MAX ? MORE
                    : TEXTURES.computeIfAbsent(modules.get(i).id(), id -> Steveparty.id("textures/gui/dice_module/" + id + ".png"));
            context.drawTexture(RenderLayer::getGuiTextured, texture, x + POSITIONS[i][0], y + POSITIONS[i][1], 0, 0, SIZE, SIZE, SIZE, SIZE);
        }
        context.getMatrices().pop();
    }
}
