package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.DyeColor;
import net.minecraft.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

/**
 * The network of a Teleport tile, by colour (like warp pipes): a token landing on a Teleport tile is sent to another
 * Teleport tile of the same colour on the same board. The tile's face and its cartridge's icon take the colour.
 */
public enum TeleportNetwork implements StringIdentifiable {
    VIOLET("violet", 0x8E4BFF, DyeColor.PURPLE, DyeColor.MAGENTA),
    GREEN("green", 0x34C759, DyeColor.LIME, DyeColor.GREEN),
    ORANGE("orange", 0xFF8A1F, DyeColor.ORANGE),
    BLUE("blue", 0x2E9BFF, DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.CYAN);

    public static final Codec<TeleportNetwork> CODEC = StringIdentifiable.createCodec(TeleportNetwork::values);

    private final String id;
    private final int color;
    private final DyeColor[] dyes;

    TeleportNetwork(String id, int color, DyeColor... dyes) {
        this.id = id;
        this.color = color;
        this.dyes = dyes;
    }

    @Override
    public String asString() {
        return id;
    }

    /** The colour of the network's tiles (face, sides, warp sparkles, board view arcs). */
    public int color() {
        return color;
    }

    /** The network a dye of this colour stands for, or null (a dye that matches none of them). */
    public static @Nullable TeleportNetwork ofDye(DyeColor dye) {
        for (TeleportNetwork network : values()) {
            for (DyeColor d : network.dyes) if (d == dye) return network;
        }
        return null;
    }

    public static TeleportNetwork byIndex(int index) {
        TeleportNetwork[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    /** « violet », « vert »... in the network's colour. */
    public MutableText displayName() {
        return Text.translatable("teleport_network.steveparty." + id).styled(style -> style.withColor(TextColor.fromRgb(color)));
    }
}
