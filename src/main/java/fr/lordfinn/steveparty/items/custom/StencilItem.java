package fr.lordfinn.steveparty.items.custom;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

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
}
