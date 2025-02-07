package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceDestination;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity.getDestinationsStatus;
import static fr.lordfinn.steveparty.components.ModComponents.STENCIL_PIXELS;

public class StencilItem extends Item {
    private static byte[] shape = new byte[]{
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };
    public StencilItem(Settings settings) {
        super(settings);
    }

    public static byte[] getShape(ItemStack stack) {
        if (stack.contains(STENCIL_PIXELS)) {
            List<Byte> byteList = stack.get(STENCIL_PIXELS);
            if (byteList != null) {
                byte[] byteArray = new byte[byteList.size()];
                for (int i = 0; i < byteList.size(); i++) {
                    byteArray[i] = byteList.get(i);
                }
                return byteArray;
            }
            return shape.clone();
        }
        return shape.clone();
    }

    public static void setShape(byte[] shape, ItemStack stack) {
        List<Byte> byteList = new ArrayList<>(shape.length);
        for (byte b : shape) {
            byteList.add(b);
        }
        stack.set(STENCIL_PIXELS, byteList);
    }

    @Environment(EnvType.CLIENT)
    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        byte[] shape = getShape(stack);
        if (shape == null || shape.length != 256) return;

        for (int i = 0; i < 16; i++) {
            StringBuilder line = new StringBuilder();
            for (int j = 0; j < 16; j++) {
                line.append((getShape(stack)[j * 16 + i] == 1) ? "⬛" : "⬜");
            }
            tooltip.add(Text.literal(line.toString()));
        }
    }
}
