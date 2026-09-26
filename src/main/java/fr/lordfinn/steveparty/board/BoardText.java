package fr.lordfinn.steveparty.board;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/** Texts shared by the board tools. */
public final class BoardText {
    private BoardText() {
    }

    /** A position, "(x, y, z)". */
    public static MutableText pos(BlockPos pos) {
        return Text.translatable("message.steveparty.wrench.pos", pos.getX(), pos.getY(), pos.getZ());
    }
}
