package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;

import java.util.UUID;

/**
 * The payloads of the mini-game pages. The content of a page lives on the server: the clients ask for it
 * ({@link Request}, {@link ImageRequest}), keep it, and are told when it changes ({@link Data}, sent to everyone).
 * Pictures travel in pieces of {@link #CHUNK_SIZE} bytes at most (a client → server payload can't hold more).
 */
public final class MiniGamePagePayloads {
    /** Bytes of a picture per payload. */
    public static final int CHUNK_SIZE = 30_000;
    /** The most pieces a picture can be cut in. */
    public static final int MAX_CHUNKS = (MiniGamePageImages.MAX_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE;

    private MiniGamePagePayloads() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(Open.ID, Open.CODEC);
        PayloadTypeRegistry.playS2C().register(Data.ID, Data.CODEC);
        PayloadTypeRegistry.playS2C().register(ImageChunk.ID, ImageChunk.CODEC);
        PayloadTypeRegistry.playS2C().register(Status.ID, Status.CODEC);
        PayloadTypeRegistry.playS2C().register(Preview.ID, Preview.CODEC);
        PayloadTypeRegistry.playC2S().register(Edit.ID, Edit.CODEC);
        PayloadTypeRegistry.playC2S().register(Action.ID, Action.CODEC);
        PayloadTypeRegistry.playC2S().register(Upload.ID, Upload.CODEC);
        PayloadTypeRegistry.playC2S().register(Request.ID, Request.CODEC);
        PayloadTypeRegistry.playC2S().register(ImageRequest.ID, ImageRequest.CODEC);
    }

    private static <T extends CustomPayload> CustomPayload.Id<T> id(String name) {
        return new CustomPayload.Id<>(Steveparty.id("minigame_page_" + name));
    }

    // ------------------------------------------------------------------ server → client

    /**
     * Opens the page held in {@code hand}.
     *
     * @param canEdit false: the player may only read it (adventure, spectator)
     * @param linked  the item is a linked copy
     */
    public record Open(Hand hand, MiniGamePageData data, boolean canEdit, boolean linked) implements CustomPayload {
        public static final Id<Open> ID = id("open");
        public static final PacketCodec<PacketByteBuf, Open> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            MiniGamePageData.PACKET_CODEC.encode(buf, payload.data);
            buf.writeBoolean(payload.canEdit);
            buf.writeBoolean(payload.linked);
        }, buf -> new Open(buf.readEnumConstant(Hand.class), MiniGamePageData.PACKET_CODEC.decode(buf), buf.readBoolean(), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** The content of a page: the answer to a {@link Request}, and sent to everyone when a page changes. */
    public record Data(MiniGamePageData data) implements CustomPayload {
        public static final Id<Data> ID = id("data");
        public static final PacketCodec<PacketByteBuf, Data> CODEC = PacketCodec.of(
                (payload, buf) -> MiniGamePageData.PACKET_CODEC.encode(buf, payload.data),
                buf -> new Data(MiniGamePageData.PACKET_CODEC.decode(buf)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * A piece of the picture {@code hash}, the answer to an {@link ImageRequest}.
     *
     * @param total number of pieces, 0 when the server does not have the picture
     */
    public record ImageChunk(String hash, int index, int total, byte[] bytes) implements CustomPayload {
        public static final Id<ImageChunk> ID = id("image_chunk");
        public static final PacketCodec<PacketByteBuf, ImageChunk> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeString(payload.hash, 32);
            buf.writeVarInt(payload.index);
            buf.writeVarInt(payload.total);
            buf.writeByteArray(payload.bytes);
        }, buf -> new ImageChunk(buf.readString(32), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_SIZE)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** What became of the player's last request on a page (shown in the editor). */
    public record Status(UUID page, Code code) implements CustomPayload {
        public enum Code { IMAGE_SAVED, IMAGE_REFUSED, IMAGE_CLEARED, COPIED, COPY_NEEDS_PAPER, NOT_ALLOWED }

        public static final Id<Status> ID = id("status");
        public static final PacketCodec<PacketByteBuf, Status> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.page);
            buf.writeEnumConstant(payload.code);
        }, buf -> new Status(buf.readUuid(), buf.readEnumConstant(Code.class)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * The card of the mini-game the party controller drew.
     *
     * @param show      false: the card goes away (the players leave for the mini-game, or it is called off)
     * @param data      the page drawn
     * @param mode      ordinal of the {@link fr.lordfinn.steveparty.minigame.MiniGameMode} it will be played in
     * @param countdown seconds before the departure, 0 while it has not started
     */
    public record Preview(boolean show, MiniGamePageData data, int mode, int countdown) implements CustomPayload {
        public static final Id<Preview> ID = id("preview");
        public static final PacketCodec<PacketByteBuf, Preview> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeBoolean(payload.show);
            MiniGamePageData.PACKET_CODEC.encode(buf, payload.data);
            buf.writeByte(payload.mode);
            buf.writeByte(payload.countdown);
        }, buf -> new Preview(buf.readBoolean(), MiniGamePageData.PACKET_CODEC.decode(buf), buf.readByte(), buf.readByte()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ------------------------------------------------------------------ client → server

    /** The texts and settings written in the editor, for the page {@code page} held in {@code hand}. */
    public record Edit(Hand hand, UUID page, String title, String description, int modes, int minPlayers, int maxPlayers) implements CustomPayload {
        public static final Id<Edit> ID = id("edit");
        public static final PacketCodec<PacketByteBuf, Edit> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
            buf.writeString(payload.title, MiniGamePageData.MAX_TITLE_LENGTH);
            buf.writeString(payload.description, MiniGamePageData.MAX_DESCRIPTION_LENGTH);
            buf.writeByte(payload.modes);
            buf.writeByte(payload.minPlayers);
            buf.writeByte(payload.maxPlayers);
        }, buf -> new Edit(buf.readEnumConstant(Hand.class), buf.readUuid(), buf.readString(MiniGamePageData.MAX_TITLE_LENGTH),
                buf.readString(MiniGamePageData.MAX_DESCRIPTION_LENGTH), buf.readByte(), buf.readByte(), buf.readByte()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** A button of the editor, for the page {@code page} held in {@code hand}. */
    public record Action(Hand hand, UUID page, Kind kind) implements CustomPayload {
        public enum Kind { COPY, UNLINK, CLEAR_IMAGE }

        public static final Id<Action> ID = id("action");
        public static final PacketCodec<PacketByteBuf, Action> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
            buf.writeEnumConstant(payload.kind);
        }, buf -> new Action(buf.readEnumConstant(Hand.class), buf.readUuid(), buf.readEnumConstant(Kind.class)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** A piece of the new picture of the page {@code page} held in {@code hand}; pieces are sent in order. */
    public record Upload(Hand hand, UUID page, int index, int total, byte[] bytes) implements CustomPayload {
        public static final Id<Upload> ID = id("upload");
        public static final PacketCodec<PacketByteBuf, Upload> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
            buf.writeVarInt(payload.index);
            buf.writeVarInt(payload.total);
            buf.writeByteArray(payload.bytes);
        }, buf -> new Upload(buf.readEnumConstant(Hand.class), buf.readUuid(), buf.readVarInt(), buf.readVarInt(), buf.readByteArray(CHUNK_SIZE)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Asks for the content of a page (answered by {@link Data}). */
    public record Request(UUID page) implements CustomPayload {
        public static final Id<Request> ID = id("request");
        public static final PacketCodec<PacketByteBuf, Request> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeUuid(payload.page), buf -> new Request(buf.readUuid()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Asks for a picture (answered by {@link ImageChunk}s). */
    public record ImageRequest(String hash) implements CustomPayload {
        public static final Id<ImageRequest> ID = id("image_request");
        public static final PacketCodec<PacketByteBuf, ImageRequest> CODEC = PacketCodec.of(
                (payload, buf) -> buf.writeString(payload.hash, 32), buf -> new ImageRequest(buf.readString(32)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }
}
