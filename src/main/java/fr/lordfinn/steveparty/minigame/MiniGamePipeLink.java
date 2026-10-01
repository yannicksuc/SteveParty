package fr.lordfinn.steveparty.minigame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * A pipe mouth linked to a mini-game page, with what it is for.
 *
 * @param mouth   the pipe block, with its dimension
 * @param opening the side the mouth opens on
 * @param role    what the mouth does in the mini-game
 */
public record MiniGamePipeLink(GlobalPos mouth, Direction opening, MiniGamePipeRole role) {
    public MiniGamePipeLink withRole(MiniGamePipeRole newRole) {
        return new MiniGamePipeLink(mouth, opening, newRole);
    }

    /** @return true if this link is the mouth of the pipe at {@code pos} (whatever side it opens on). */
    public boolean isAt(GlobalPos pos) {
        return mouth.equals(pos);
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Dimension", mouth.dimension().getValue().toString());
        nbt.putLong("Pos", mouth.pos().asLong());
        nbt.putInt("Opening", opening.getId());
        nbt.putString("Role", role.name());
        return nbt;
    }

    public static @Nullable MiniGamePipeLink fromNbt(NbtCompound nbt) {
        Identifier dimension = Identifier.tryParse(nbt.getString("Dimension"));
        if (dimension == null) return null;
        MiniGamePipeRole role;
        try {
            role = MiniGamePipeRole.valueOf(nbt.getString("Role"));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return new MiniGamePipeLink(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(nbt.getLong("Pos"))),
                Direction.byId(nbt.getInt("Opening")), role);
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(mouth.dimension().getValue());
        buf.writeBlockPos(mouth.pos());
        buf.writeEnumConstant(opening);
        buf.writeEnumConstant(role);
    }

    public static MiniGamePipeLink read(PacketByteBuf buf) {
        Identifier dimension = buf.readIdentifier();
        BlockPos pos = buf.readBlockPos();
        return new MiniGamePipeLink(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), pos),
                buf.readEnumConstant(Direction.class), buf.readEnumConstant(MiniGamePipeRole.class));
    }
}
