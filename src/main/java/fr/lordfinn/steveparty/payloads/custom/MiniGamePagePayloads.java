package fr.lordfinn.steveparty.payloads.custom;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePageImages;
import fr.lordfinn.steveparty.payloads.Payloads;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Hand;
import net.minecraft.util.math.GlobalPos;

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

    /** The server → client payloads (the client → server ones are registered with their handlers by MiniGamePageNetworking). */
    public static void register() {
        Payloads.s2c(Open.ID, Open.CODEC);
        Payloads.s2c(Data.ID, Data.CODEC);
        Payloads.s2c(ImageChunk.ID, ImageChunk.CODEC);
        Payloads.s2c(Status.ID, Status.CODEC);
        Payloads.s2c(Preview.ID, Preview.CODEC);
        Payloads.s2c(Results.ID, Results.CODEC);
        Payloads.s2c(TestLabel.ID, TestLabel.CODEC);
        Payloads.s2c(TestStatus.ID, TestStatus.CODEC);
        Payloads.s2c(Practice.ID, Practice.CODEC);
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
     * @param format    the index of the page's format it will be played in, -1 when not known
     * @param countdown seconds before the departure, 0 while it has not started
     */
    public record Preview(boolean show, MiniGamePageData data, int format, int countdown) implements CustomPayload {
        public static final Id<Preview> ID = id("preview");
        public static final PacketCodec<PacketByteBuf, Preview> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeBoolean(payload.show);
            MiniGamePageData.PACKET_CODEC.encode(buf, payload.data);
            buf.writeByte(payload.format);
            buf.writeByte(payload.countdown);
        }, buf -> new Preview(buf.readBoolean(), MiniGamePageData.PACKET_CODEC.decode(buf), buf.readByte(), buf.readByte()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** The results of the mini-game that just ended: the places, and what the party paid. */
    public record Results(fr.lordfinn.steveparty.minigame.MiniGameResults results) implements CustomPayload {
        public static final Id<Results> ID = id("results");
        public static final PacketCodec<net.minecraft.network.RegistryByteBuf, Results> CODEC = PacketCodec.of(
                (payload, buf) -> fr.lordfinn.steveparty.minigame.MiniGameResults.PACKET_CODEC.encode(buf, payload.results),
                buf -> new Results(fr.lordfinn.steveparty.minigame.MiniGameResults.PACKET_CODEC.decode(buf)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * The practice round of a party's mini-game, for its players and its audience: the chip « Practice — [key] Ready
     * 2/4 » and who is ready. Sent again each time a vote changes.
     *
     * @param show   false: the practice round is over (the real round starts, or the mini-game is called off)
     * @param title  the page's title, empty if it has none
     * @param voters the connected players of the mini-game, in the play order
     */
    public record Practice(boolean show, String title, java.util.List<Voter> voters) implements CustomPayload {
        public static final int MAX_VOTERS = 64;

        /** A player of the practice round, and whether he said he is ready. */
        public record Voter(String name, boolean ready) {
        }

        public static final Id<Practice> ID = id("practice");
        public static final PacketCodec<PacketByteBuf, Practice> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeBoolean(payload.show);
            buf.writeString(payload.title, MiniGamePageData.MAX_TITLE_LENGTH);
            java.util.List<Voter> voters = payload.voters.size() > MAX_VOTERS ? payload.voters.subList(0, MAX_VOTERS) : payload.voters;
            buf.writeVarInt(voters.size());
            for (Voter voter : voters) {
                buf.writeString(voter.name, 64);
                buf.writeBoolean(voter.ready);
            }
        }, buf -> {
            boolean show = buf.readBoolean();
            String title = buf.readString(MiniGamePageData.MAX_TITLE_LENGTH);
            int count = Math.min(buf.readVarInt(), MAX_VOTERS);
            java.util.List<Voter> voters = new java.util.ArrayList<>(count);
            for (int i = 0; i < count; i++) voters.add(new Voter(buf.readString(64), buf.readBoolean()));
            return new Practice(show, title, voters);
        });

        public long readyCount() {
            return voters.stream().filter(Voter::ready).count();
        }

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * The label of a mini-game played out of any party (« Out of a party: ... »), for its players, spectators and
     * observer.
     *
     * @param show  false: it is over
     * @param title the page's title, empty if it has none
     */
    public record TestLabel(boolean show, String title) implements CustomPayload {
        public static final Id<TestLabel> ID = id("test_label");
        public static final PacketCodec<PacketByteBuf, TestLabel> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeBoolean(payload.show);
            buf.writeString(payload.title, MiniGamePageData.MAX_TITLE_LENGTH);
        }, buf -> new TestLabel(buf.readBoolean(), buf.readString(MiniGamePageData.MAX_TITLE_LENGTH)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * Whether a page can be tested now (the answer to a {@link TestQuery}, and to a refused {@link TestAction}).
     *
     * @param status  ordinal of the {@code MiniGameTest.Status}
     * @param players the players who would play
     * @param mode    ordinal of the way it would be played, -1 for none
     */
    /**
     * Whether a page can be tested now: its {@link fr.lordfinn.steveparty.minigame.MiniGameTest.Status}, with how many
     * players and in which of its formats ({@code format}, -1 when it can't); when nobody fits, the closest format and
     * what it misses ({@code shortfall}: format, role, count, min, max, see
     * {@link fr.lordfinn.steveparty.minigame.MiniGameTest.Shortfall}; empty otherwise).
     */
    public record TestStatus(UUID page, int status, int players, int format, int[] shortfall) implements CustomPayload {
        public static final Id<TestStatus> ID = id("test_status");
        public static final PacketCodec<PacketByteBuf, TestStatus> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeUuid(payload.page);
            buf.writeByte(payload.status);
            buf.writeByte(payload.players);
            buf.writeByte(payload.format);
            buf.writeVarInt(payload.shortfall.length);
            for (int value : payload.shortfall) buf.writeVarInt(value);
        }, buf -> {
            TestStatus head = new TestStatus(buf.readUuid(), buf.readByte(), buf.readByte(), buf.readByte(), new int[0]);
            int[] shortfall = new int[Math.min(buf.readVarInt(), 5)];
            for (int i = 0; i < shortfall.length; i++) shortfall[i] = buf.readVarInt();
            return new TestStatus(head.page, head.status, head.players, head.format, shortfall);
        });

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    // ------------------------------------------------------------------ client → server

    /** The player says he is ready for the real round of his party's mini-game, or no longer is (the practice round's vote). */
    public record Ready() implements CustomPayload {
        public static final Id<Ready> ID = id("ready");
        public static final PacketCodec<PacketByteBuf, Ready> CODEC = PacketCodec.unit(new Ready());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Asks whether the page {@code page} held in {@code hand} can be tested now (answered by {@link TestStatus}). */
    public record TestQuery(Hand hand, UUID page) implements CustomPayload {
        public static final Id<TestQuery> ID = id("test_query");
        public static final PacketCodec<PacketByteBuf, TestQuery> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
        }, buf -> new TestQuery(buf.readEnumConstant(Hand.class), buf.readUuid()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** The « Test » button of the editor, for the page {@code page} held in {@code hand}: starts its test, or stops it. */
    public record TestAction(Hand hand, UUID page, boolean start) implements CustomPayload {
        public static final Id<TestAction> ID = id("test_action");
        public static final PacketCodec<PacketByteBuf, TestAction> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
            buf.writeBoolean(payload.start);
        }, buf -> new TestAction(buf.readEnumConstant(Hand.class), buf.readUuid(), buf.readBoolean()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** A podium (or a goal pole base) linked to the page {@code page} held in {@code hand}, unlinked in the editor. */
    public record PodiumUnlink(Hand hand, UUID page, GlobalPos pos) implements CustomPayload {
        public static final Id<PodiumUnlink> ID = id("podium_unlink");
        public static final PacketCodec<PacketByteBuf, PodiumUnlink> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
            buf.writeIdentifier(payload.pos.dimension().getValue());
            buf.writeBlockPos(payload.pos.pos());
        }, buf -> new PodiumUnlink(buf.readEnumConstant(Hand.class), buf.readUuid(),
                GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()), buf.readBlockPos())));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** The texts and settings written in the editor, for the page {@code page} held in {@code hand}. */
    public record Edit(Hand hand, UUID page, String title, String description, java.util.List<fr.lordfinn.steveparty.minigame.MiniGameFormat> formats)
            implements CustomPayload {
        public static final Id<Edit> ID = id("edit");
        public static final PacketCodec<PacketByteBuf, Edit> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
            buf.writeString(payload.title, MiniGamePageData.MAX_TITLE_LENGTH);
            buf.writeString(payload.description, MiniGamePageData.MAX_DESCRIPTION_STORED);
            fr.lordfinn.steveparty.minigame.MiniGameFormat.writeList(buf, payload.formats);
        }, buf -> new Edit(buf.readEnumConstant(Hand.class), buf.readUuid(), buf.readString(MiniGamePageData.MAX_TITLE_LENGTH),
                buf.readString(MiniGamePageData.MAX_DESCRIPTION_STORED), fr.lordfinn.steveparty.minigame.MiniGameFormat.readList(buf)));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** A button of the editor, for the page {@code page} held in {@code hand}. */
    public record Action(Hand hand, UUID page, Kind kind) implements CustomPayload {
        /** RESTORE, ADVENTURE: the option is switched (« Remettre l'arène en état » only with a zone). */
        public enum Kind { COPY, UNLINK, CLEAR_IMAGE, DRAW_ZONE, CLEAR_ZONE, RESTORE, ADVENTURE }

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

    /**
     * The role of a pipe linked to the page {@code page} held in {@code hand}, changed in the editor.
     *
     * @param role ordinal of its new {@link fr.lordfinn.steveparty.minigame.MiniGamePipeRole}, -1 to unlink the pipe
     */
    public record PipeRole(Hand hand, UUID page, GlobalPos mouth, int role) implements CustomPayload {
        public static final Id<PipeRole> ID = id("pipe_role");
        public static final PacketCodec<PacketByteBuf, PipeRole> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
            buf.writeIdentifier(payload.mouth.dimension().getValue());
            buf.writeBlockPos(payload.mouth.pos());
            buf.writeByte(payload.role);
        }, buf -> new PipeRole(buf.readEnumConstant(Hand.class), buf.readUuid(),
                GlobalPos.create(RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()), buf.readBlockPos()), buf.readByte()));

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /**
     * How the players of a role are sent to its pipes, for the page {@code page} held in {@code hand}.
     *
     * @param role   ordinal of the {@link fr.lordfinn.steveparty.minigame.MiniGamePipeRole}
     * @param random true: a pipe picked at random; false: each pipe in turn
     */
    public record PipeOrder(Hand hand, UUID page, int role, boolean random) implements CustomPayload {
        public static final Id<PipeOrder> ID = id("pipe_order");
        public static final PacketCodec<PacketByteBuf, PipeOrder> CODEC = PacketCodec.of((payload, buf) -> {
            buf.writeEnumConstant(payload.hand);
            buf.writeUuid(payload.page);
            buf.writeByte(payload.role);
            buf.writeBoolean(payload.random);
        }, buf -> new PipeOrder(buf.readEnumConstant(Hand.class), buf.readUuid(), buf.readByte(), buf.readBoolean()));

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
