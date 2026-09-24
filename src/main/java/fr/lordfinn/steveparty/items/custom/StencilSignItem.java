package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.signs.AbstractStencilSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.PlasticRoadSignBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.SignMaterial;
import fr.lordfinn.steveparty.blocks.custom.signs.WoodenCutoutPanelBlock;
import fr.lordfinn.steveparty.blocks.custom.signs.WoodenPanelBlock;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.StencilCanvasComponent;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import net.minecraft.block.Block;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Item of a stencil sign: named after what it is made of ("Traffic Sign (Birch Planks)", "Plastic Road Sign (Red)")
 * and telling what is painted on it.
 */
public class StencilSignItem extends BlockItem {
    public StencilSignItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public Text getName(ItemStack stack) {
        Text base = super.getName(stack);
        if (getBlock() instanceof AbstractStencilSignBlock sign && sign.getMaterialKind() != null) {
            SignMaterial kind = sign.getMaterialKind();
            Block material = kind.resolveBlock(stack.get(ModComponents.SIGN_MATERIAL));
            return Text.translatable("item.steveparty.material_sign", base, material.getName());
        }
        if (getBlock() instanceof PlasticRoadSignBlock) {
            DyeColor color = stack.getOrDefault(DataComponentTypes.BASE_COLOR, DyeColor.WHITE);
            return Text.translatable("item.steveparty.material_sign", base, Text.translatable("color.minecraft." + color.getName()));
        }
        return base;
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        if (getBlock() instanceof PlasticRoadSignBlock) {
            PlasticRoadSignBlock.Plate plate = stack.getOrDefault(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT)
                    .getValue(PlasticRoadSignBlock.PLATE);
            if (plate == null) plate = PlasticRoadSignBlock.Plate.ROUND;
            tooltip.add(Text.translatable("tooltip.steveparty.plastic_road_sign.plate." + plate.asString()).formatted(Formatting.GRAY));
        }
        if (getBlock() instanceof WoodenPanelBlock || getBlock() instanceof WoodenCutoutPanelBlock || getBlock() instanceof PlasticRoadSignBlock) {
            tooltip.add(Text.translatable("tooltip.steveparty.sign_post.usage").formatted(Formatting.DARK_GRAY));
        }
        if (getBlock() instanceof WoodenCutoutPanelBlock) {
            tooltip.add(Text.translatable("tooltip.steveparty.cutout_panel.usage").formatted(Formatting.DARK_GRAY));
        }
        StencilCanvasComponent canvas = stack.get(ModComponents.STENCIL_CANVAS);
        if (canvas != null) {
            byte[] shape = canvas.shapeArray();
            StencilPatterns.Pattern pattern = shape == null ? null : StencilPatterns.byShape(shape);
            Text symbol = pattern != null ? pattern.name() : Text.translatable("tooltip.steveparty.stencil.custom");
            Text finish = canvas.color()
                    .<Text>map(color -> Text.translatable("color.minecraft." + color.getName()))
                    .orElse(Text.translatable("tooltip.steveparty.stencil_sign.engraved"));
            tooltip.add(Text.translatable("tooltip.steveparty.stencil_sign.symbol", symbol, finish).formatted(Formatting.GRAY));
            if (canvas.glowing()) tooltip.add(Text.translatable("tooltip.steveparty.stencil_sign.glowing").formatted(Formatting.AQUA));
        }
    }
}
