package fr.lordfinn.steveparty.board;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

/**
 * Texts shared by the board tools, and their colours. The meaningful parts (numbers, dead ends, the slot's state...) are coloured
 * by the code, never by the lang strings: two palettes, one dark for the light grey plates of the HUD (enough
 * contrast on the mod's GUI grey), one light for the action bar over the world.
 */
public final class BoardText {
    private BoardText() {
    }

    /** Colours on a HUD plate (the light grey of the mod's screens): dark, saturated, like the chevrons' dark shades. */
    public enum Plate {
        NUMBER(0x1C4FA8), OK(0x1D7A1D), DEAD_END(0xB01E1E), UNREACHABLE(0xA35200),
        POWERED(0xB01E1E), CHOSEN(0x7030A0), MUTED(0x707070);

        public final int rgb;

        Plate(int rgb) {
            this.rgb = rgb;
        }

        public MutableText of(Object value) {
            return (value instanceof Text text ? text.copy() : Text.literal(String.valueOf(value))).withColor(rgb);
        }
    }

    /** A position, "(x, y, z)". */
    public static MutableText pos(BlockPos pos) {
        return Text.translatable("message.steveparty.wrench.pos", pos.getX(), pos.getY(), pos.getZ());
    }

    /** A number in an action bar message: stands out over the world. */
    public static MutableText num(int value) {
        return Text.literal(Integer.toString(value)).formatted(Formatting.YELLOW);
    }
}
