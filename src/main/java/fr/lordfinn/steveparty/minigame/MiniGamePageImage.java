package fr.lordfinn.steveparty.minigame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The picture of a mini-game page: a reference to its bytes, kept by the server ({@link MiniGamePageImageStore}).
 * The hash is the one of the bytes: a new picture is a new hash, so whatever is cached under a hash never changes.
 *
 * @param hash       {@link MiniGamePageImages#hash} of the stored bytes (PNG or JPEG)
 * @param size       number of bytes stored
 * @param uploader   name of the player who sent it (moderation)
 * @param uploaderId that player, null when unknown
 */
public record MiniGamePageImage(String hash, int width, int height, int size, String uploader, @Nullable UUID uploaderId) {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{32}");

    public static boolean isHash(@Nullable String hash) {
        return hash != null && HASH.matcher(hash).matches();
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("Hash", hash);
        nbt.putInt("Width", width);
        nbt.putInt("Height", height);
        nbt.putInt("Size", size);
        nbt.putString("Uploader", uploader);
        if (uploaderId != null) nbt.putUuid("UploaderId", uploaderId);
        return nbt;
    }

    public static @Nullable MiniGamePageImage fromNbt(NbtCompound nbt) {
        String hash = nbt.getString("Hash");
        if (!isHash(hash)) return null;
        return new MiniGamePageImage(hash, nbt.getInt("Width"), nbt.getInt("Height"), nbt.getInt("Size"),
                nbt.getString("Uploader"), nbt.containsUuid("UploaderId") ? nbt.getUuid("UploaderId") : null);
    }

    public void write(PacketByteBuf buf) {
        buf.writeString(hash, 32);
        buf.writeVarInt(width);
        buf.writeVarInt(height);
        buf.writeVarInt(size);
        buf.writeString(uploader, 64);
        buf.writeBoolean(uploaderId != null);
        if (uploaderId != null) buf.writeUuid(uploaderId);
    }

    public static MiniGamePageImage read(PacketByteBuf buf) {
        String hash = buf.readString(32);
        int width = buf.readVarInt(), height = buf.readVarInt(), size = buf.readVarInt();
        String uploader = buf.readString(64);
        UUID uploaderId = buf.readBoolean() ? buf.readUuid() : null;
        return new MiniGamePageImage(hash, width, height, size, uploader, uploaderId);
    }
}
