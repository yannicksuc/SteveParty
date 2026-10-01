package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * The payloads of the Telescope. All of it concerns one player only: the server answers his click with the past
 * nights he can replay there ({@link Open}), he tells it when he followed a star to the end ({@link Found}), and it
 * tells him his guide stars ({@link Guides}). Nothing is ever sent to the others.
 */
public final class TelescopePayloads {
    /** The most nights and guide stars one payload carries. */
    public static final int MAX_NIGHTS = 8, MAX_GUIDES = 64, MAX_COLOURS = 8;

    private TelescopePayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
        PayloadTypeRegistry.playS2C().register(Guides.ID, Guides.CODEC);
        PayloadTypeRegistry.playC2S().register(Found.ID, Found.CODEC);
    }

    private static <T extends CustomPayload> CustomPayload.Id<T> id(String name) {
        return new CustomPayload.Id<>(Steveparty.id("telescope_" + name));
    }

    /**
     * A past night to replay: the site's id, the day of its night, the way its stars went (unit, towards the site: its
     * place itself is not told before it is found) and the colours of its Mulas (variant ids).
     */
    public record Night(int id, long day, float dirX, float dirZ, int[] colours) {
    }

    /** A guide star: the site it stands over. */
    public record Guide(int id, int x, int z) {
    }

    /** Server → the player who clicked: look through this telescope; today, and the nights to replay (latest first). */
    public record Open(BlockPos pos, long day, List<Night> nights) implements CustomPayload {
        public static final Id<Open> ID = id("open");
        public static final PacketCodec<PacketByteBuf, Open> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeBlockPos(payload.pos);
            buf.writeVarLong(payload.day);
            buf.writeVarInt(payload.nights.size());
            for (Night night : payload.nights) {
                buf.writeVarInt(night.id);
                buf.writeVarLong(night.day);
                buf.writeFloat(night.dirX);
                buf.writeFloat(night.dirZ);
                buf.writeIntArray(night.colours);
            }
        }, buf -> {
            BlockPos pos = buf.readBlockPos();
            long day = buf.readVarLong();
            int n = Math.min(buf.readVarInt(), MAX_NIGHTS);
            List<Night> nights = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                nights.add(new Night(buf.readVarInt(), buf.readVarLong(), buf.readFloat(), buf.readFloat(),
                        buf.readIntArray(MAX_COLOURS)));
            }
            return new Open(pos, day, nights);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Server → one player: his guide stars in the dimension he is in (the whole list, each time it changes). */
    public record Guides(List<Guide> guides) implements CustomPayload {
        public static final Id<Guides> ID = id("guides");
        public static final PacketCodec<PacketByteBuf, Guides> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeVarInt(payload.guides.size());
            for (Guide guide : payload.guides) {
                buf.writeVarInt(guide.id);
                buf.writeInt(guide.x);
                buf.writeInt(guide.z);
            }
        }, buf -> {
            int n = Math.min(buf.readVarInt(), MAX_GUIDES);
            List<Guide> guides = new ArrayList<>(n);
            for (int i = 0; i < n; i++) guides.add(new Guide(buf.readVarInt(), buf.readInt(), buf.readInt()));
            return new Guides(guides);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Client → server: the player followed a star of that night to the end. */
    public record Found(int site) implements CustomPayload {
        public static final Id<Found> ID = TelescopePayloads.id("found");
        public static final PacketCodec<PacketByteBuf, Found> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeVarInt(payload.site), buf -> new Found(buf.readVarInt()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
