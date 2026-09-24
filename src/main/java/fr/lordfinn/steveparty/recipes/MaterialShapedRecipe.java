package fr.lordfinn.steveparty.recipes;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignMaterial;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.mixin.ShapedRecipeAccessor;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.BlockItem;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Shaped recipe of a sign made of a material: the ingredient given as a tag (any planks, any rock, any plastic block)
 * becomes what the sign is made of. All the material ingredients must be the same block (no half-oak half-birch
 * sign). Same JSON as {@code minecraft:crafting_shaped}, with {@code "type": "steveparty:material_shaped"}.
 */
public class MaterialShapedRecipe extends ShapedRecipe {
    public MaterialShapedRecipe(String group, CraftingRecipeCategory category, RawShapedRecipe raw, ItemStack result, boolean showNotification) {
        super(group, category, raw, result, showNotification);
    }

    private static MaterialShapedRecipe of(ShapedRecipe recipe) {
        ShapedRecipeAccessor accessor = (ShapedRecipeAccessor) recipe;
        return new MaterialShapedRecipe(accessor.getGroup(), accessor.getCategory(), accessor.getRaw(), accessor.getResult(),
                accessor.getShowNotification());
    }

    private ItemStack resultTemplate() {
        return ((ShapedRecipeAccessor) this).getResult();
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        return super.matches(input, world) && material(input) != null;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack result = super.craft(input, registries);
        Block material = material(input);
        if (material == null) return result;
        if (isPlastic(result)) {
            DyeColor color = plasticColor(material);
            if (color != null) result.set(DataComponentTypes.BASE_COLOR, color);
        } else {
            result.set(ModComponents.SIGN_MATERIAL, Registries.BLOCK.getId(material));
        }
        return result;
    }

    /** @return the one material block used in the grid, or null if there is none or they differ. */
    private @Nullable Block material(CraftingRecipeInput input) {
        ItemStack template = resultTemplate();
        Block found = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            Block block = materialOf(template, stack);
            if (block == null) continue;
            if (found != null && found != block) return null;
            found = block;
        }
        return found;
    }

    private static @Nullable Block materialOf(ItemStack result, ItemStack ingredient) {
        if (!(ingredient.getItem() instanceof BlockItem blockItem)) return null;
        Block block = blockItem.getBlock();
        if (isPlastic(result)) return plasticColor(block) != null ? block : null;
        if (result.getItem() instanceof BlockItem signItem && signItem.getBlock() instanceof AbstractStencilSignBlock sign) {
            SignMaterial kind = sign.getMaterialKind();
            return kind != null && kind.accepts(block) ? block : null;
        }
        return null;
    }

    private static boolean isPlastic(ItemStack result) {
        return result.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof PlasticRoadSignBlock;
    }

    private static @Nullable DyeColor plasticColor(Block block) {
        for (int i = 0; i < ModBlocks.PLASTIC_BLOCKS.length; i++) {
            if (ModBlocks.PLASTIC_BLOCKS[i] == block) return DyeColor.byName(ModBlocks.COLORS[i], null);
        }
        return null;
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return ModRecipes.MATERIAL_SHAPED;
    }

    public static class Serializer implements RecipeSerializer<MaterialShapedRecipe> {
        private static final MapCodec<MaterialShapedRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(MaterialShapedRecipe::of, recipe -> recipe);
        private static final PacketCodec<RegistryByteBuf, MaterialShapedRecipe> PACKET_CODEC =
                ShapedRecipe.Serializer.PACKET_CODEC.xmap(MaterialShapedRecipe::of, recipe -> recipe);

        @Override
        public MapCodec<MaterialShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, MaterialShapedRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
