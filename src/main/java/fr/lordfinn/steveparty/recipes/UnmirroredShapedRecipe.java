package fr.lordfinn.steveparty.recipes;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.mixin.ShapedRecipeAccessor;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.world.World;

import java.util.List;

/**
 * A shaped recipe that does not also match its mirror image. Same JSON as {@code minecraft:crafting_shaped}, with
 * {@code "type": "steveparty:shaped_unmirrored"}.
 * <p>
 * The Polished Tiles checker « AB / BA » needs it: mirrored, the pattern of (A, B) is the pattern of (B, A), so a
 * vanilla shaped recipe made both recipes match the same grid and the colour order of the tiles was whichever one the
 * recipe manager found first. Unmirrored, the colour in the top-left corner is always the first one.
 */
public class UnmirroredShapedRecipe extends ShapedRecipe {
    public UnmirroredShapedRecipe(String group, CraftingRecipeCategory category, RawShapedRecipe raw, ItemStack result, boolean showNotification) {
        super(group, category, raw, result, showNotification);
    }

    private static UnmirroredShapedRecipe of(ShapedRecipe recipe) {
        ShapedRecipeAccessor accessor = (ShapedRecipeAccessor) recipe;
        return new UnmirroredShapedRecipe(accessor.getGroup(), accessor.getCategory(), accessor.getRaw(), accessor.getResult(),
                accessor.getShowNotification());
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        // The vanilla check (size, item count, either orientation), then the grid as it is drawn
        return super.matches(input, world) && matchesAsDrawn(input);
    }

    private boolean matchesAsDrawn(CraftingRecipeInput input) {
        RawShapedRecipe raw = ((ShapedRecipeAccessor) this).getRaw();
        int width = raw.getWidth(), height = raw.getHeight();
        if (input.getWidth() != width || input.getHeight() != height) return false;
        List<Ingredient> ingredients = raw.getIngredients();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                Ingredient ingredient = ingredients.get(x + y * width);
                ItemStack stack = input.getStackInSlot(x, y);
                if (!ingredient.isEmpty() ? !ingredient.test(stack) : !stack.isEmpty()) return false;
            }
        }
        return true;
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return ModRecipes.SHAPED_UNMIRRORED;
    }

    public static class Serializer implements RecipeSerializer<UnmirroredShapedRecipe> {
        private static final MapCodec<UnmirroredShapedRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(UnmirroredShapedRecipe::of, recipe -> recipe);
        private static final PacketCodec<RegistryByteBuf, UnmirroredShapedRecipe> PACKET_CODEC =
                ShapedRecipe.Serializer.PACKET_CODEC.xmap(UnmirroredShapedRecipe::of, recipe -> recipe);

        @Override
        public MapCodec<UnmirroredShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, UnmirroredShapedRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
