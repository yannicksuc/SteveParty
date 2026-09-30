package fr.lordfinn.steveparty.board;

import com.mojang.serialization.Codec;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.StringIdentifiable;

/**
 * What a right click on a board space does with the Wrench (« Clé »). Switched with the mode key (R by default) or
 * sneak + mouse wheel, shown in the HUD, the item name and the tooltip.
 */
public enum WrenchMode implements StringIdentifiable {
    /** Links in a chain: each clicked board space is linked from the previous one and becomes the new origin. */
    TRACE("trace", Formatting.GREEN),
    /** The former behaviour: a fixed origin, each click adds or removes one of its links (forks, routers). */
    EDIT("edit", Formatting.AQUA),
    /** A click on a board space removes all its outgoing links. */
    CUT("cut", Formatting.RED);

    public static final Codec<WrenchMode> CODEC = StringIdentifiable.createCodec(WrenchMode::values);

    private final String id;
    private final Formatting color;

    WrenchMode(String id, Formatting color) {
        this.id = id;
        this.color = color;
    }

    @Override
    public String asString() {
        return id;
    }

    public Formatting color() {
        return color;
    }

    public WrenchMode cycle(int direction) {
        WrenchMode[] values = values();
        return values[Math.floorMod(ordinal() + Integer.signum(direction == 0 ? 1 : direction), values.length)];
    }

    public Text displayName() {
        return Text.translatable("wrench.steveparty.mode." + id).formatted(color);
    }
}
