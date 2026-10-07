package fr.lordfinn.steveparty.recipes;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileContents;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.mixin.ShapedRecipeAccessor;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.RawShapedRecipe;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * The recipes of the Tile and of the Advanced Tile: shaped recipes whose cartridges ({@code steveparty:cartridges})
 * end up in the tile crafted, as they are (colour, links, settings). Same JSON as {@code minecraft:crafting_shaped},
 * with {@code "type": "steveparty:tile_shaped"}; its result is the tile shown by the recipe book and REI.
 * <ul>
 *     <li>Tile: its cartridge is the one it holds.</li>
 *     <li>Advanced Tile: its two cartridges fill it in {@link TileCartridgeRecipe#FILL_ORDER} (slots 0 and 15), left
 *     then right; the row under the pattern may hold more cartridges (optional), which come next in the same order,
 *     left to right. Only with the pattern on the top two rows of the crafting table, then.</li>
 * </ul>
 */
public class TileShapedRecipe extends ShapedRecipe {
    public TileShapedRecipe(String group, CraftingRecipeCategory category, RawShapedRecipe raw, ItemStack result, boolean showNotification) {
        super(group, category, raw, result, showNotification);
    }

    private static TileShapedRecipe of(ShapedRecipe recipe) {
        ShapedRecipeAccessor accessor = (ShapedRecipeAccessor) recipe;
        return new TileShapedRecipe(accessor.getGroup(), accessor.getCategory(), accessor.getRaw(), accessor.getResult(),
                accessor.getShowNotification());
    }

    private ItemStack shownResult() {
        return ((ShapedRecipeAccessor) this).getResult();
    }

    /** An Advanced Tile: it takes optional cartridges in the row under its pattern. */
    private boolean takesExtraCartridges() {
        return shownResult().isOf(ModBlocks.ADVANCED_TILE.asItem());
    }

    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        if (super.matches(input, world)) return true;
        RawShapedRecipe raw = ((ShapedRecipeAccessor) this).getRaw();
        // The pattern on top, a row of loose cartridges under it
        if (!takesExtraCartridges() || input.getWidth() != raw.getWidth() || input.getHeight() != raw.getHeight() + 1) return false;
        List<ItemStack> top = new ArrayList<>();
        for (int y = 0; y < raw.getHeight(); y++) for (int x = 0; x < raw.getWidth(); x++) top.add(input.getStackInSlot(x, y));
        if (!super.matches(CraftingRecipeInput.create(raw.getWidth(), raw.getHeight(), top), world)) return false;
        for (int x = 0; x < input.getWidth(); x++) {
            ItemStack stack = input.getStackInSlot(x, raw.getHeight());
            if (!stack.isEmpty() && !(stack.getItem() instanceof CartridgeItem)) return false;
        }
        return true;
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        // The cartridges of the grid, row by row: the pattern's (left, right), then the optional ones
        List<ItemStack> cartridges = new ArrayList<>();
        for (int i = 0; i < input.getSize(); i++) {
            ItemStack stack = input.getStackInSlot(i);
            if (stack.getItem() instanceof CartridgeItem) cartridges.add(stack.copyWithCount(1));
        }
        ItemStack tile = shownResult().copyWithCount(1);
        tile.remove(DataComponentTypes.CONTAINER);
        if (cartridges.isEmpty()) return tile;
        if (takesExtraCartridges()) {
            ItemStack filled = TileCartridgeRecipe.fill(tile, cartridges);
            return filled == null ? ItemStack.EMPTY : filled;
        }
        return TileContents.holding(tile, cartridges.getFirst());
    }

    @Override
    public RecipeSerializer<? extends ShapedRecipe> getSerializer() {
        return ModRecipes.TILE_SHAPED;
    }

    public static class Serializer implements RecipeSerializer<TileShapedRecipe> {
        private static final MapCodec<TileShapedRecipe> CODEC = ShapedRecipe.Serializer.CODEC.xmap(TileShapedRecipe::of, recipe -> recipe);
        private static final PacketCodec<RegistryByteBuf, TileShapedRecipe> PACKET_CODEC =
                ShapedRecipe.Serializer.PACKET_CODEC.xmap(TileShapedRecipe::of, recipe -> recipe);

        @Override
        public MapCodec<TileShapedRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, TileShapedRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}
