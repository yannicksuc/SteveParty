package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.components.CarpetColorComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.utils.WoolColorsUtils;
import net.minecraft.item.*;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;

public class TradingStallRecipe extends ShapedRecipe {
    private final ItemStack result;
    private final RawShapedRecipe raw;

    public TradingStallRecipe(String group, CraftingRecipeCategory category, RawShapedRecipe raw, ItemStack result, boolean showNotification) {
        super(group, category, raw, result, showNotification);
        this.result = result;
        this.raw = raw;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup wrapperLookup) {
        ItemStack result = super.craft(input, wrapperLookup);

        DyeColor color1 = null;
        DyeColor color2 = null;
        int found = 0;

        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (!stack.isEmpty() && WoolColorsUtils.isCarpet(stack)) {
                DyeColor color = WoolColorsUtils.getCarpetColor(stack);
                if (color != null) {
                    if (found == 0) {
                        color1 = color;
                        found++;
                    } else if (found == 1) {
                        color2 = color;
                        break;  // found both carpets, stop looping
                    }
                }
            }
        }

        if (color1 != null && color2 != null) {
            result.set(ModComponents.CARPET_COLORS, new CarpetColorComponent(color1, color2));
        }

        return result;
    }

    public RawShapedRecipe getRaw() {
        return raw;
    }

    public ItemStack getResult() {
        return result;
    }
}

