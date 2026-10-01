package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.pipe.PipeBlock;
import fr.lordfinn.steveparty.blocks.custom.pipe.PipeKind;
import net.minecraft.block.BlockState;
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
 * @param kind    the kind of pipe it was when it was linked (how its card looks)
 * @param color   its colour then (index in {@code ModBlocks.COLORS}), {@link #UNKNOWN_COLOR} when not known
 */
public record MiniGamePipeLink(GlobalPos mouth, Direction opening, MiniGamePipeRole role, PipeKind kind, int color) {
    public static final int UNKNOWN_COLOR = -1;

    public MiniGamePipeLink {
        if (kind == null) kind = PipeKind.OPAQUE;
        if (color < 0 || color > 15) color = UNKNOWN_COLOR;
    }

    /** A link whose pipe is not known (its card takes the colour of its role). */
    public MiniGamePipeLink(GlobalPos mouth, Direction opening, MiniGamePipeRole role) {
        this(mouth, opening, role, PipeKind.OPAQUE, UNKNOWN_COLOR);
    }

    /** The link to the pipe {@code state} at {@code mouth}, with the role of its colour. */
    public static MiniGamePipeLink of(GlobalPos mouth, Direction opening, BlockState state) {
        PipeBlock pipe = state.getBlock() instanceof PipeBlock block ? block : null;
        return new MiniGamePipeLink(mouth, opening, MiniGamePipeRole.ofPipe(state), pipe == null ? PipeKind.OPAQUE : pipe.kind(),
                pipe == null ? UNKNOWN_COLOR : pipe.color());
    }

    public MiniGamePipeLink withRole(MiniGamePipeRole newRole) {
        return new MiniGamePipeLink(mouth, opening, newRole, kind, color);
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
        nbt.putString("Kind", kind.name());
        nbt.putInt("Color", color);
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
        PipeKind kind = PipeKind.OPAQUE;
        try {
            if (nbt.contains("Kind")) kind = PipeKind.valueOf(nbt.getString("Kind"));
        } catch (IllegalArgumentException ignored) {
        }
        return new MiniGamePipeLink(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), BlockPos.fromLong(nbt.getLong("Pos"))),
                Direction.byId(nbt.getInt("Opening")), role, kind, nbt.contains("Color") ? nbt.getInt("Color") : UNKNOWN_COLOR);
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(mouth.dimension().getValue());
        buf.writeBlockPos(mouth.pos());
        buf.writeEnumConstant(opening);
        buf.writeEnumConstant(role);
        buf.writeEnumConstant(kind);
        buf.writeByte(color);
    }

    public static MiniGamePipeLink read(PacketByteBuf buf) {
        Identifier dimension = buf.readIdentifier();
        BlockPos pos = buf.readBlockPos();
        return new MiniGamePipeLink(GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, dimension), pos),
                buf.readEnumConstant(Direction.class), buf.readEnumConstant(MiniGamePipeRole.class), buf.readEnumConstant(PipeKind.class), buf.readByte());
    }
}
