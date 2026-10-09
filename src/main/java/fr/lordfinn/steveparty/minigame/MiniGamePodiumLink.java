package fr.lordfinn.steveparty.minigame;

import java.util.Locale;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * A block linked to a mini-game page to end it and tell who won: a podium (its whole column, and the columns touching
 * it, are the places of the mini-game), a goal pole base (a counter whose reached goals fill those places) or a step
 * controller (a redstone pulse into it ends the mini-game of the page, wherever its party is).
 *
 * @param pos  the block clicked with the page (the bottom block of the podium's column, the goal pole base, the step
 *             controller), with its dimension
 * @param kind what it was when it was linked
 */
public record MiniGamePodiumLink(GlobalPos pos, Kind kind) {
    public enum Kind {
        /** A podium column: a place of the mini-game. */
        PODIUM,
        /** A goal pole base: its points go back to 0 with the podiums, its per-player goals give the places. */
        COUNTER,
        /** A step controller: a pulse into it acts on the party playing the page's mini-game, however far. */
        STEP_CONTROLLER;

        /** Its name in the translation keys. */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public MiniGamePodiumLink {
        if (kind == null) kind = Kind.PODIUM;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Dimension", pos.dimension().getValue().toString());
        nbt.putLong("Pos", pos.pos().asLong());
        nbt.putString("Kind", kind.name());
        return nbt;
    }

    public static @Nullable MiniGamePodiumLink fromNbt(NbtCompound nbt) {
        Identifier dimension = Identifier.tryParse(nbt.getString("Dimension"));
        if (dimension == null) return null;
        Kind kind = Kind.PODIUM;
        try {
            if (nbt.contains("Kind")) kind = Kind.valueOf(nbt.getString("Kind"));
        } catch (IllegalArgumentException ignored) {
        }
        return new MiniGamePodiumLink(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(nbt.getLong("Pos"))), kind);
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(pos.dimension().getValue());
        buf.writeBlockPos(pos.pos());
        buf.writeEnumConstant(kind);
    }

    public static MiniGamePodiumLink read(PacketByteBuf buf) {
        Identifier dimension = buf.readIdentifier();
        BlockPos pos = buf.readBlockPos();
        return new MiniGamePodiumLink(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), pos), buf.readEnumConstant(Kind.class));
    }
}
