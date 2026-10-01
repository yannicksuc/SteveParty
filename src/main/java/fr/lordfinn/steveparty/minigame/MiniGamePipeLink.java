package fr.lordfinn.steveparty.minigame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * A pipe mouth linked to a mini-game page, with what it is for.
 *
 * @param mouth the pipe mouth, with its dimension
 * @param role  what the mouth does in the mini-game
 * @param team  the team it is for (1..4), {@link #ANY_TEAM} for everyone
 */
public record MiniGamePipeLink(GlobalPos mouth, Role role, int team) {
    public static final int ANY_TEAM = 0;

    public enum Role {
        /** The players go into the mini-game through this mouth. */
        ENTRY,
        /** The players come out of this mouth in the mini-game. */
        ARRIVAL
    }

    public MiniGamePipeLink {
        team = Math.max(ANY_TEAM, Math.min(4, team));
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Dimension", mouth.dimension().getValue().toString());
        nbt.putLong("Pos", mouth.pos().asLong());
        nbt.putString("Role", role.name());
        nbt.putInt("Team", team);
        return nbt;
    }

    public static @Nullable MiniGamePipeLink fromNbt(NbtCompound nbt) {
        Identifier dimension = Identifier.tryParse(nbt.getString("Dimension"));
        if (dimension == null) return null;
        Role role;
        try {
            role = Role.valueOf(nbt.getString("Role"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new MiniGamePipeLink(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(nbt.getLong("Pos"))),
                role, nbt.getInt("Team"));
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(mouth.dimension().getValue());
        buf.writeBlockPos(mouth.pos());
        buf.writeEnumConstant(role);
        buf.writeByte(team);
    }

    public static MiniGamePipeLink read(PacketByteBuf buf) {
        Identifier dimension = buf.readIdentifier();
        BlockPos pos = buf.readBlockPos();
        return new MiniGamePipeLink(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), pos),
                buf.readEnumConstant(Role.class), buf.readByte());
    }
}
