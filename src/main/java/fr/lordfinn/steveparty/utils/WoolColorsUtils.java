package fr.lordfinn.steveparty.utils;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.DyeColor;

import java.util.HashMap;
import java.util.Map;

public class WoolColorsUtils {

    private static final Map<Block, DyeColor> CARPET_COLOR_MAP = new HashMap<>();
    private static final int[] DYE_COLOR_TO_ARGB = new int[16]; // Indexed by DyeColor.ordinal()

    static {
        // Initialisation des blocs de tapis vers DyeColor
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

        // DyeColor ordinal → Couleurs ARGB personnalisées
        DYE_COLOR_TO_ARGB[DyeColor.WHITE.ordinal()] = WoolColors.WHITE;
        DYE_COLOR_TO_ARGB[DyeColor.ORANGE.ordinal()] = WoolColors.ORANGE;
        DYE_COLOR_TO_ARGB[DyeColor.MAGENTA.ordinal()] = WoolColors.MAGENTA;
        DYE_COLOR_TO_ARGB[DyeColor.LIGHT_BLUE.ordinal()] = WoolColors.LIGHT_BLUE;
        DYE_COLOR_TO_ARGB[DyeColor.YELLOW.ordinal()] = WoolColors.YELLOW;
        DYE_COLOR_TO_ARGB[DyeColor.LIME.ordinal()] = WoolColors.LIME;
        DYE_COLOR_TO_ARGB[DyeColor.PINK.ordinal()] = WoolColors.PINK;
        DYE_COLOR_TO_ARGB[DyeColor.GRAY.ordinal()] = WoolColors.GRAY;
        DYE_COLOR_TO_ARGB[DyeColor.LIGHT_GRAY.ordinal()] = WoolColors.LIGHT_GRAY;
        DYE_COLOR_TO_ARGB[DyeColor.CYAN.ordinal()] = WoolColors.CYAN;
        DYE_COLOR_TO_ARGB[DyeColor.PURPLE.ordinal()] = WoolColors.PURPLE;
        DYE_COLOR_TO_ARGB[DyeColor.BLUE.ordinal()] = WoolColors.BLUE;
        DYE_COLOR_TO_ARGB[DyeColor.BROWN.ordinal()] = WoolColors.BROWN;
        DYE_COLOR_TO_ARGB[DyeColor.GREEN.ordinal()] = WoolColors.GREEN;
        DYE_COLOR_TO_ARGB[DyeColor.RED.ordinal()] = WoolColors.RED;
        DYE_COLOR_TO_ARGB[DyeColor.BLACK.ordinal()] = WoolColors.BLACK;
    }

    // Vérifie si un itemstack est un tapis
    public static boolean isCarpet(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return false;
        return CARPET_COLOR_MAP.containsKey(bi.getBlock());
    }

    // Récupère la couleur de teinture d'un tapis
    public static DyeColor getCarpetColor(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        return CARPET_COLOR_MAP.get(bi.getBlock());
    }

    // Convertit un DyeColor en couleur ARGB
    public static int getARGBFromDyeColor(DyeColor dyeColor) {
        return DYE_COLOR_TO_ARGB[dyeColor.ordinal()];
    }

    // Optionnel : Convertir ARGB (index 0-15) en DyeColor
    public static DyeColor getDyeColorFromIndex(int index) {
        return DyeColor.byId(index); // index 0–15
    }

    // Optionnel : Renvoie la couleur ARGB à partir de l'index 0–15
    public static int getARGBFromColorIndex(int index) {
        if (index < 0 || index >= DYE_COLOR_TO_ARGB.length) return 0xFFFFFFFF;
        return DYE_COLOR_TO_ARGB[index];
    }
}
