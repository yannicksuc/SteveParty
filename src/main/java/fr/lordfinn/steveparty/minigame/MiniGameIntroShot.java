package fr.lordfinn.steveparty.minigame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A shot of a mini-game's introduction (see {@link MiniGamePageData#intro()}): where the camera stands and looks, for
 * how long, and what is said meanwhile. The shots of a page are shown in order before the players leave for their
 * pipes.
 *
 * @param pos   where the camera stands
 * @param ticks how long the shot lasts
 * @param text  said in the chat during the shot (the markup of {@link MiniGameText}), empty for nothing
 */
public record MiniGameIntroShot(RegistryKey<World> dimension, Vec3d pos, float yaw, float pitch, int ticks, String text) {
    public static final int MAX_TICKS = 20 * 30;
    public static final int MAX_TEXT_LENGTH = 200;

    public MiniGameIntroShot {
        yaw = MathHelper.wrapDegrees(yaw);
        pitch = MathHelper.clamp(pitch, -90, 90);
        ticks = MathHelper.clamp(ticks, 1, MAX_TICKS);
        text = text == null ? "" : text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH) : text;
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Dimension", dimension.getValue().toString());
        nbt.putDouble("X", pos.x);
        nbt.putDouble("Y", pos.y);
        nbt.putDouble("Z", pos.z);
        nbt.putFloat("Yaw", yaw);
        nbt.putFloat("Pitch", pitch);
        nbt.putInt("Ticks", ticks);
        if (!text.isEmpty()) nbt.putString("Text", text);
        return nbt;
    }

    public static @Nullable MiniGameIntroShot fromNbt(NbtCompound nbt) {
        Identifier dimension = Identifier.tryParse(nbt.getString("Dimension"));
        if (dimension == null) return null;
        return new MiniGameIntroShot(RegistryKey.of(RegistryKeys.WORLD, dimension), new Vec3d(nbt.getDouble("X"), nbt.getDouble("Y"), nbt.getDouble("Z")),
                nbt.getFloat("Yaw"), nbt.getFloat("Pitch"), nbt.getInt("Ticks"), nbt.getString("Text"));
    }

    public void write(PacketByteBuf buf) {
        buf.writeIdentifier(dimension.getValue());
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeVarInt(ticks);
        buf.writeString(text, MAX_TEXT_LENGTH);
    }

    public static MiniGameIntroShot read(PacketByteBuf buf) {
        return new MiniGameIntroShot(RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()),
                new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readFloat(), buf.readFloat(), buf.readVarInt(),
                buf.readString(MAX_TEXT_LENGTH));
    }
}
