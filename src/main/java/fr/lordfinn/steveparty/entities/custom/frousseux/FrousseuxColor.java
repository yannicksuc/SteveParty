package fr.lordfinn.steveparty.entities.custom.frousseux;

import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;

/**
 * The 17 Frousseux, one per candle: the plain candle and the 16 dye colours. One model and one set of textures for
 * all (textures/entity/frousseux*.png): the flame and the pool of wax on its top are greyscale there, tinted when drawn
 * with {@link #flame} and {@link #accent} (its wax colour, faded into the ivory of its body). Born in a cave, its
 * colour is drawn at random by {@link #weight}: the plain and earthy candles are common, the bright ones rare.
 *
 * @param flame  its flame's colour (RGB), drawn glowing
 * @param wax    its candle's wax colour (RGB)
 * @param weight how likely a wild one is this colour, against the others' weights
 */
public enum FrousseuxColor {
    PLAIN("plain", Items.CANDLE, 0xFFAA3C, 0xF0E2BA, 30),
    WHITE("white", Items.WHITE_CANDLE, 0xFFECC0, 0xF6F6F6, 12),
    ORANGE("orange", Items.ORANGE_CANDLE, 0xFF8C28, 0xF08228, 8),
    MAGENTA("magenta", Items.MAGENTA_CANDLE, 0xFF6EE6, 0xC450BE, 2),
    LIGHT_BLUE("light_blue", Items.LIGHT_BLUE_CANDLE, 0x82D2FF, 0x64AAE1, 4),
    YELLOW("yellow", Items.YELLOW_CANDLE, 0xFFE646, 0xF5CD3C, 8),
    LIME("lime", Items.LIME_CANDLE, 0xAAFF5A, 0x82C828, 3),
    PINK("pink", Items.PINK_CANDLE, 0xFF96C8, 0xF096B4, 2),
    GRAY("gray", Items.GRAY_CANDLE, 0xC8C8D7, 0x6E737D, 8),
    LIGHT_GRAY("light_gray", Items.LIGHT_GRAY_CANDLE, 0xE1E1EB, 0xAAAAAA, 8),
    CYAN("cyan", Items.CYAN_CANDLE, 0x5AF0F0, 0x2896A0, 3),
    PURPLE("purple", Items.PURPLE_CANDLE, 0xBE78FF, 0x823CBE, 2),
    BLUE("blue", Items.BLUE_CANDLE, 0x648CFF, 0x3C50BE, 3),
    BROWN("brown", Items.BROWN_CANDLE, 0xFFAF6E, 0x825532, 8),
    GREEN("green", Items.GREEN_CANDLE, 0x78DC5A, 0x5A7828, 4),
    RED("red", Items.RED_CANDLE, 0xFF5A46, 0xBE322D, 4),
    BLACK("black", Items.BLACK_CANDLE, 0xAA8CFF, 0x322D37, 2);

    /** The ivory of its wax body, and how much of the candle's colour shows over it in the pool on its top. */
    private static final int IVORY = 0xF8E8C8;
    private static final float ACCENT_STRENGTH = 0.25f;
    /** How much of the candle's colour its fringe (a lock of wax over one eye) shows over ivory. */
    private static final float FRINGE_STRENGTH = 0.7f;
    private static final FrousseuxColor[] VALUES = values();
    private static final int TOTAL_WEIGHT;

    static {
        int total = 0;
        for (FrousseuxColor color : VALUES) total += color.weight;
        TOTAL_WEIGHT = total;
    }

    private final String name;
    /** The candle it may drop (see the loot table entities/frousseux). */
    public final Item candle;
    public final int flame;
    public final int wax;
    /** The tint of the pool on its top: a hint of {@link #wax} over ivory, never the full dye. */
    public final int accent;
    /** The tint of its fringe: mostly {@link #wax}, so it reads as the candle's colour. */
    public final int fringe;
    public final int weight;

    FrousseuxColor(String name, Item candle, int flame, int wax, int weight) {
        this.name = name;
        this.candle = candle;
        this.flame = flame;
        this.wax = wax;
        this.accent = mix(IVORY, wax, ACCENT_STRENGTH);
        this.fringe = mix(IVORY, wax, FRINGE_STRENGTH);
        this.weight = weight;
    }

    private static int mix(int from, int to, float amount) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = (from >> shift) & 0xFF, b = (to >> shift) & 0xFF;
            rgb |= Math.round(a + (b - a) * amount) << shift;
        }
        return rgb;
    }

    /** Its name in its entity data ("Color") and its loot table conditions. */
    public String asString() {
        return name;
    }

    public static FrousseuxColor byId(int id) {
        return VALUES[Math.floorMod(id, VALUES.length)];
    }

    public static FrousseuxColor byName(String name) {
        for (FrousseuxColor color : VALUES) {
            if (color.name.equals(name)) return color;
        }
        return PLAIN;
    }

    /** A wild one's colour, drawn by weight. */
    public static FrousseuxColor random(Random random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        for (FrousseuxColor color : VALUES) {
            roll -= color.weight;
            if (roll < 0) return color;
        }
        return PLAIN;
    }
}
