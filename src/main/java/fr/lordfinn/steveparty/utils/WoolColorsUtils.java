package fr.lordfinn.steveparty.utils;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;

import java.util.HashMap;
import java.util.Map;

public class CarpetColorUtils {
    private static final Map<Block, DyeColor> CARPET_COLOR_MAP = new HashMap<>();

    static {
        CARPET_COLOR_MAP.put(Blocks.WHITE_CARPET, DyeColor.WHITE);
        CARPET_COLOR_MAP.put(Blocks.ORANGE_CARPET, DyeColor.ORANGE);
        CARPET_COLOR_MAP.put(Blocks.MAGENTA_CARPET, DyeColor.MAGENTA);
        CARPET_COLOR_MAP.put(Blocks.LIGHT_BLUE_CARPET, DyeColor.LIGHT_BLUE);
        CARPET_COLOR_MAP.put(Blocks.YELLOW_CARPET, DyeColor.YELLOW);
        CARPET_COLOR_MAP.put(Blocks.LIME_CARPET, DyeColor.LIME);
        CARPET_COLOR_MAP.put(Blocks.PINK_CARPET, DyeColor.PINK);
        CARPET_COLOR_MAP.put(Blocks.GRAY_CARPET, DyeColor.GRAY);
        CARPET_COLOR_MAP.put(Blocks.LIGHT_GRAY_CARPET, DyeColor.LIGHT_GRAY);
        CARPET_COLOR_MAP.put(Blocks.CYAN_CARPET, DyeColor.CYAN);
        CARPET_COLOR_MAP.put(Blocks.PURPLE_CARPET, DyeColor.PURPLE);
        CARPET_COLOR_MAP.put(Blocks.BLUE_CARPET, DyeColor.BLUE);
        CARPET_COLOR_MAP.put(Blocks.BROWN_CARPET, DyeColor.BROWN);
        CARPET_COLOR_MAP.put(Blocks.GREEN_CARPET, DyeColor.GREEN);
        CARPET_COLOR_MAP.put(Blocks.RED_CARPET, DyeColor.RED);
        CARPET_COLOR_MAP.put(Blocks.BLACK_CARPET, DyeColor.BLACK);
    }

    public static boolean isCarpet(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return false;
        return CARPET_COLOR_MAP.containsKey(bi.getBlock());
    }

    public static DyeColor getCarpetColor(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        return CARPET_COLOR_MAP.get(bi.getBlock());
    }
}
