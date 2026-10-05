package fr.lordfinn.steveparty.recipes;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.custom.FlagItem;
import fr.lordfinn.steveparty.mixin.ShapedRecipeAccessor;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The flag's shaped recipe (3 wool): the colour of the wool is the colour of the flag. Three wools of one colour give
 * a flag of that dye (drawn in that wool's colours); wools of several colours are mixed like dyes on leather armour.
 * Red wool only gives the classic goal-flag red (the undyed flag). Same JSON as {@code minecraft:crafting_shaped},
 * with {@code "type": "steveparty:flag_from_wool"}: it stays in the recipe book, showing the classic red flag.
 */
public class FlagFromWoolRecipe extends ShapedRecipe {
    public FlagFromWoolRecipe(String group, CraftingRecipeCategory category, RawShapedRecipe raw, ItemStack result, boolean showNotification) {
        super(group, category, raw, result, showNotification);
    }

    private static FlagFromWoolRecipe of(ShapedRecipe recipe) {
        ShapedRecipeAccessor accessor = (ShapedRecipeAccessor) recipe;
        return new FlagFromWoolRecipe(accessor.getGroup(), accessor.getCategory(), accessor.getRaw(), accessor.getResult(),
                accessor.getShowNotification());
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack result = super.craft(input, registries);
        List<DyeItem> dyes = new ArrayList<>();
        boolean allRed = true;
        for (int i = 0; i < input.getSize(); i++) {
            DyeColor color = woolColor(input.getStackInSlot(i));
            if (color == null) continue;
            dyes.add(DyeItem.byColor(color));
            allRed &= color == DyeColor.RED;
        }
        if (dyes.isEmpty() || allRed) return result;
        return FlagItem.withColor(result, FlagItem.mix(FlagItem.NO_COLOR, dyes));
    }

    /** @return the dye colour of a vanilla wool ({@code minecraft:<colour>_wool}), null for anything else. */
    @Nullable
    public static DyeColor woolColor(ItemStack stack) {
        if (stack.isEmpty()) return null;
        Identifier id = Registries.ITEM.getId(stack.getItem());
        if (!id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) || !id.getPath().endsWith("_wool")) return null;
        return DyeColor.byName(id.getPath().substring(0, id.getPath().length() - "_wool".length()), null);
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return ModRecipes.FLAG_FROM_WOOL;
    }

    public static class Serializer implements RecipeSerializer<FlagFromWoolRecipe> {
        private static final MapCodec<FlagFromWoolRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(FlagFromWoolRecipe::of, recipe -> recipe);
        private static final PacketCodec<RegistryByteBuf, FlagFromWoolRecipe> PACKET_CODEC =
                ShapedRecipe.Serializer.PACKET_CODEC.xmap(FlagFromWoolRecipe::of, recipe -> recipe);

        @Override
        public MapCodec<FlagFromWoolRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, FlagFromWoolRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
