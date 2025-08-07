package fr.lordfinn.steveparty.recipes;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.components.CarpetColorComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.utils.WoolColorsUtils;
import net.minecraft.item.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.recipe.*;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.recipe.display.RecipeDisplay;
import net.minecraft.recipe.input.CraftingRecipeInput;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.List;
import java.util.Optional;

public class TradingStallRecipe implements CraftingRecipe {
    private final Identifier id;
    private final String group;

    public TradingStallRecipe(Identifier id, String group) {
        this.id = id;
        this.group = group;
    }
    @Override
    public boolean matches(CraftingRecipeInput input, World world) {
        if (input.size() < 4) return false;

        ItemStack topLeft = input.getStackInSlot(0);
        ItemStack topRight = input.getStackInSlot(1);
        ItemStack bottomLeft = input.getStackInSlot(2);
        ItemStack bottomRight = input.getStackInSlot(3);

        return WoolColorsUtils.isCarpet(topLeft) &&
                WoolColorsUtils.isCarpet(topRight) &&
                bottomLeft.isOf(Items.BARREL) &&
                bottomRight.isOf(Items.BARREL);
    }

    @Override
    public ItemStack craft(CraftingRecipeInput input, RegistryWrapper.WrapperLookup registries) {
        ItemStack result = new ItemStack(ModBlocks.TRADING_STALL);
        DyeColor color1 = WoolColorsUtils.getCarpetColor(input.getStackInSlot(0));
        DyeColor color2 = WoolColorsUtils.getCarpetColor(input.getStackInSlot(1));

        if (color1 != null && color2 != null) {
            result.set(ModComponents.CARPET_COLORS, new CarpetColorComponent(color1, color2));
        }

        return result;
    }

    @Override
    public List<RecipeDisplay> getDisplays() {
        return CraftingRecipe.super.getDisplays();
    }

    @Override
    public RecipeSerializer<? extends CraftingRecipe> getSerializer() {
        return ModRecipeSerializers.TRADING_STALL_RECIPE_SERIALIZER;
    }

    Ingredient carpet = Ingredient.ofItems(
            Items.WHITE_CARPET,
            Items.ORANGE_CARPET,
            Items.MAGENTA_CARPET,
            Items.LIGHT_BLUE_CARPET,
            Items.YELLOW_CARPET,
            Items.LIME_CARPET,
            Items.PINK_CARPET,
            Items.GRAY_CARPET,
            Items.LIGHT_GRAY_CARPET,
            Items.CYAN_CARPET,
            Items.PURPLE_CARPET,
            Items.BLUE_CARPET,
            Items.BROWN_CARPET,
            Items.GREEN_CARPET,
            Items.RED_CARPET,
            Items.BLACK_CARPET
    );
    Ingredient barrel = Ingredient.ofItems(Items.BARREL);
    @Override
    public IngredientPlacement getIngredientPlacement() {
        return IngredientPlacement.forMultipleSlots(List.of(
                Optional.of(carpet),  // haut-gauche (C)
                Optional.of(carpet),  // haut-droit  (C)
                Optional.of(barrel),  // bas-gauche  (B)
                Optional.of(barrel)   // bas-droit   (B)
        ));
    }

    @Override
    public CraftingRecipeCategory getCategory() {
        return CraftingRecipeCategory.REDSTONE;
    }

    @Override
    public String getGroup() {
        return group;
    }

    public Identifier getId() {
        return id;
    }

    public static class Serializer implements RecipeSerializer<TradingStallRecipe> {
        public static final Serializer INSTANCE = new Serializer();

        public static final MapCodec<TradingStallRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        Identifier.CODEC.fieldOf("id").forGetter(recipe -> recipe.id),
                        Codec.STRING.fieldOf("group").forGetter(recipe -> recipe.group)
                ).apply(instance, TradingStallRecipe::new)
        );

        public static final PacketCodec<RegistryByteBuf, TradingStallRecipe> PACKET_CODEC = PacketCodec.of(
                (recipe, buf) -> {
                    buf.writeIdentifier(recipe.id);
                    buf.writeString(recipe.group);
                },
                buf -> {
                    Identifier id = buf.readIdentifier();
                    String group = buf.readString();
                    return new TradingStallRecipe(id, group);
                }
        );

        @Override
        public MapCodec<TradingStallRecipe> codec() {
            return CODEC;
        }

        @Override
        public PacketCodec<RegistryByteBuf, TradingStallRecipe> packetCodec() {
            return PACKET_CODEC;
        }
    }
}

