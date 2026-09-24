package fr.lordfinn.steveparty.items.custom;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * The goal pole's flag. It can be dyed: on the pole (a dye on the flag), or in the crafting grid (flag + dyes, mixed
 * like leather armour). The colour is the vanilla {@link DataComponentTypes#DYED_COLOR} component, hidden from the
 * vanilla tooltip ("Dyed") because this item names the colour itself. No component: the original red flag.
 */
public class FlagItem extends Item {
    /** "No colour": the original red flag. */
    public static final int NO_COLOR = -1;

    public FlagItem(Settings settings) {
        super(settings);
    }

    /** @return the flag's colour (0xRRGGBB), or {@link #NO_COLOR}. */
    public static int getColor(ItemStack stack) {
        DyedColorComponent dyed = stack.get(DataComponentTypes.DYED_COLOR);
        return dyed == null ? NO_COLOR : dyed.rgb() & 0xFFFFFF;
    }

    /** Sets (or clears, with {@link #NO_COLOR}) the colour of a flag stack. */
    public static ItemStack withColor(ItemStack stack, int color) {
        if (color == NO_COLOR) stack.remove(DataComponentTypes.DYED_COLOR);
        else stack.set(DataComponentTypes.DYED_COLOR, new DyedColorComponent(color & 0xFFFFFF, false));
        return stack;
    }

    /** Colour of a single dye on a flag, the same as the crafting grid gives for one dye. */
    public static int dyeColor(DyeColor dye) {
        return dye.getEntityColor() & 0xFFFFFF;
    }

    /**
     * Mixes dyes into a flag's colour exactly like vanilla leather armour dyeing: the average of the current colour
     * (if dyed) and the dyes, brightened back to the average brightness of the inputs.
     */
    public static int mix(int current, List<DyeItem> dyes) {
        int red = 0, green = 0, blue = 0, brightness = 0, count = 0;
        if (current != NO_COLOR) {
            int r = current >> 16 & 0xFF, g = current >> 8 & 0xFF, b = current & 0xFF;
            brightness += Math.max(r, Math.max(g, b));
            red += r;
            green += g;
            blue += b;
            count++;
        }
        for (DyeItem dye : dyes) {
            int color = dye.getColor().getEntityColor();
            int r = color >> 16 & 0xFF, g = color >> 8 & 0xFF, b = color & 0xFF;
            brightness += Math.max(r, Math.max(g, b));
            red += r;
            green += g;
            blue += b;
            count++;
        }
        if (count == 0) return current;
        red /= count;
        green /= count;
        blue /= count;
        float average = (float) brightness / count;
        float max = Math.max(red, Math.max(green, blue));
        if (max > 0) {
            red = (int) (red * average / max);
            green = (int) (green * average / max);
            blue = (int) (blue * average / max);
        }
        return red << 16 | green << 8 | blue;
    }

    /** @return the dye whose colour this is exactly, or null for a mixed colour. */
    @Nullable
    public static DyeColor matchingDye(int color) {
        for (DyeColor dye : DyeColor.values()) {
            if (dyeColor(dye) == color) return dye;
        }
        return null;
    }

    /** The colour's name: a dye's name ("Blue"), or its code for a mix ("#4A7BC0"). */
    public static Text colorName(int color) {
        DyeColor dye = matchingDye(color);
        return dye != null ? Text.translatable("color.minecraft." + dye.getName())
                : Text.literal(String.format(Locale.ROOT, "#%06X", color));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        int color = getColor(stack);
        if (color == NO_COLOR) return;
        tooltip.add(Text.translatable("tooltip.steveparty.flag.color", colorName(color).copy().withColor(color))
                .formatted(Formatting.GRAY));
    }
}
