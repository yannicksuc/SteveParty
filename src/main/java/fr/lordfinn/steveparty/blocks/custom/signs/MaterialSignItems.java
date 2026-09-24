package fr.lordfinn.steveparty.blocks.custom.signs;

import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

/** Helpers for the items of the stencil signs. */
public final class MaterialSignItems {
    private MaterialSignItems() {
    }

    /** Copies what the sign is made of (material, plate colour) onto {@code stack}, not what is painted on it. */
    public static void copyLook(StencilCanvasBlockEntity canvas, ItemStack stack) {
        if (canvas.getMaterial() != null) stack.set(ModComponents.SIGN_MATERIAL, canvas.getMaterial());
        if (canvas.getPlateColor() != null) stack.set(DataComponentTypes.BASE_COLOR, canvas.getPlateColor());
    }

    /** @return a sign item made of {@code material}. */
    public static ItemStack withMaterial(Block sign, Block material) {
        ItemStack stack = new ItemStack(sign);
        stack.set(ModComponents.SIGN_MATERIAL, Registries.BLOCK.getId(material));
        return stack;
    }

    public static ItemStack withPlateColor(Block sign, DyeColor color) {
        ItemStack stack = new ItemStack(sign);
        stack.set(DataComponentTypes.BASE_COLOR, color);
        return stack;
    }

    /** @return the material of a sign item, resolved to a registered block of the right kind. */
    public static Identifier materialOf(ItemStack stack, SignMaterial kind) {
        return kind.resolve(stack.get(ModComponents.SIGN_MATERIAL));
    }
}
