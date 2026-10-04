package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.payloads.ModPayloads;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.Action;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.Edit;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.ImageChunk;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.Status;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads.Upload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of the mini-game page editor: opens it, and applies what the clients ask, after its checks. Every
 * request names the page and the hand holding it: the player must hold that page, and be allowed to build
 * ({@link MiniGamePages#canEdit}). Reading (content, pictures) is open to everyone.
 */
public final class MiniGamePageNetworking {
    /** The picture a player is sending, piece by piece. */
    private static final class PendingUpload {
        final UUID page;
        final int total;
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int next;

        PendingUpload(UUID page, int total) {
            this.page = page;
            this.total = total;
        }
    }

    private static final Map<UUID, PendingUpload> UPLOADS = new HashMap<>();

    private MiniGamePageNetworking() {
    }

    public static void initialize() {
        MiniGamePagePayloads.register();
        ServerPlayNetworking.registerGlobalReceiver(Edit.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> edit(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Action.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> action(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(Upload.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> upload(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.PipeRole.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> pipeRole(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.PipeOrder.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> pipeOrder(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.PodiumUnlink.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> podiumUnlink(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.TestQuery.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> testQuery(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.TestAction.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> testAction(context.player(), payload)));
        ServerPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.Ready.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () ->
                        fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep.toggleReady(context.player())));
        ServerPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.Request.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () ->
                        send(context.player(), new MiniGamePagePayloads.Data(MiniGamePages.get(context.player().server, payload.page())))));
        ServerPlayNetworking.registerGlobalReceiver(MiniGamePagePayloads.ImageRequest.ID, (payload, context) ->
                ModPayloads.runInPacketOrder(context.player(), () -> sendImage(context.player(), payload.hash())));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> UPLOADS.remove(handler.getPlayer().getUuid()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            UPLOADS.clear();
            MiniGamePageImageStore.clearCache();
        });
    }

    private static void send(ServerPlayerEntity player, CustomPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.getId())) ServerPlayNetworking.send(player, payload);
    }

    // ------------------------------------------------------------------ opening

    /**
     * Opens the page held in {@code hand}. A page opened for the first time by someone who may write on it gets its
     * id; of a stack of new pages, only one becomes that page (the others go back to the inventory).
     */
    public static void open(ServerPlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!MiniGamePages.isPage(stack)) return;
        boolean canEdit = MiniGamePages.canEdit(player);
        if (MiniGamePages.idOf(stack) == null) {
            if (!canEdit) {
                send(player, new MiniGamePagePayloads.Open(hand, MiniGamePageData.empty(MiniGamePageData.NO_ID), false, false));
                return;
            }
            ensureSinglePage(player, hand);
        }
        MiniGamePages.refresh(player.server, stack);
        // Opening the editor ends the zone mode
        PageZoneTool.end(null, stack);
        MiniGamePageRef ref = stack.get(ModComponents.MINI_GAME_PAGE);
        send(player, new MiniGamePagePayloads.Open(hand, MiniGamePages.get(player.server, ref.id()), canEdit, ref.linked()));
    }

    /**
     * Gives the page held in {@code hand} its id if it has none. Of a stack of new pages only one becomes that page:
     * it stays in hand, the others go back to the inventory.
     *
     * @return the page's id
     */
    public static UUID ensureSinglePage(ServerPlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (MiniGamePages.idOf(stack) == null && stack.getCount() > 1) {
            ItemStack others = stack.split(stack.getCount() - 1);
            UUID id = MiniGamePages.ensureId(stack);
            player.getInventory().offerOrDrop(others);
            return id;
        }
        return MiniGamePages.ensureId(stack);
    }

    // ------------------------------------------------------------------ checks

    /** @return the page item held in {@code hand} if it is the page {@code page} and the player may write on it. */
    private static @Nullable ItemStack editable(ServerPlayerEntity player, Hand hand, UUID page) {
        ItemStack stack = player.getStackInHand(hand);
        if (!page.equals(MiniGamePages.idOf(stack))) return null;
        if (!MiniGamePages.canEdit(player)) {
            send(player, new Status(page, Status.Code.NOT_ALLOWED));
            return null;
        }
        return stack;
    }

    // ------------------------------------------------------------------ requests

    /** Writes the texts and settings of the editor on the page. @return true if the request was accepted */
    public static boolean edit(ServerPlayerEntity player, Edit payload) {
        ItemStack stack = editable(player, payload.hand(), payload.page());
        if (stack == null) return false;
        MiniGamePageData data = MiniGamePages.get(player.server, payload.page())
                .withTexts(payload.title(), payload.description())
                .withFormats(payload.formats());
        MiniGamePages.update(player.server, data);
        MiniGamePages.refresh(player.server, stack);
        return true;
    }

    /** A linked pipe moved to another role in the editor, or unlinked there. @return true if it was done */
    public static boolean pipeRole(ServerPlayerEntity player, MiniGamePagePayloads.PipeRole payload) {
        if (editable(player, payload.hand(), payload.page()) == null) return false;
        if (payload.role() < 0) return MiniGamePages.removeLink(player.server, payload.page(), payload.mouth());
        MiniGamePipeRole role = MiniGamePipeRole.byOrdinal(payload.role());
        return role != null && MiniGamePages.setLinkRole(player.server, payload.page(), payload.mouth(), role);
    }

    /** A linked podium (or goal pole base) unlinked in the editor. @return true if it was done */
    public static boolean podiumUnlink(ServerPlayerEntity player, MiniGamePagePayloads.PodiumUnlink payload) {
        if (editable(player, payload.hand(), payload.page()) == null) return false;
        return MiniGamePages.removePodiumLinks(player.server, payload.page(), java.util.List.of(payload.pos()));
    }

    private static MiniGamePagePayloads.TestStatus testStatus(UUID page, MiniGameTest.Plan plan) {
        MiniGameTest.Shortfall shortfall = plan.shortfall();
        int[] missing = shortfall == null ? new int[0]
                : new int[]{shortfall.format(), shortfall.role(), shortfall.count(), shortfall.min(), Math.min(shortfall.max(), 255)};
        return new MiniGamePagePayloads.TestStatus(page, plan.status().ordinal(), plan.players().size(), plan.format(), missing);
    }

    /** The editor asks whether its page can be tested now (the state of its « Test » button). */
    public static void testQuery(ServerPlayerEntity player, MiniGamePagePayloads.TestQuery payload) {
        if (!payload.page().equals(MiniGamePages.idOf(player.getStackInHand(payload.hand())))) return;
        send(player, testStatus(payload.page(), MiniGameTest.check(player.server, payload.page())));
    }

    /**
     * The « Test » button of the editor: starts the test of the page with those near its pipes, or stops it.
     *
     * @return what became of it: {@code READY} when a test started, {@code RUNNING} when one was stopped, else why
     * nothing started; null if the player may not (not holding the page, not allowed to build)
     */
    public static MiniGameTest.@Nullable Status testAction(ServerPlayerEntity player, MiniGamePagePayloads.TestAction payload) {
        if (editable(player, payload.hand(), payload.page()) == null) return null;
        if (!payload.start()) {
            MiniGameTest.stop(payload.page());
            return MiniGameTest.Status.RUNNING;
        }
        MiniGameTest.Status status = MiniGameTest.start(player.server, payload.page(), player, MiniGameTest.COUNTDOWN_SECONDS);
        if (status != MiniGameTest.Status.READY) send(player, testStatus(payload.page(), MiniGameTest.check(player.server, payload.page())));
        return status;
    }

    /** A role's players sent in turn or at random, chosen in the editor. @return true if it was done */
    public static boolean pipeOrder(ServerPlayerEntity player, MiniGamePagePayloads.PipeOrder payload) {
        MiniGamePipeRole role = MiniGamePipeRole.byOrdinal(payload.role());
        if (role == null || !role.hasOrder() || editable(player, payload.hand(), payload.page()) == null) return false;
        MiniGamePages.setRandom(player.server, payload.page(), role, payload.random());
        return true;
    }

    /** A button of the editor. @return true if it was done */
    public static boolean action(ServerPlayerEntity player, Action payload) {
        ItemStack stack = editable(player, payload.hand(), payload.page());
        if (stack == null) return false;
        MinecraftServer server = player.server;
        switch (payload.kind()) {
            case COPY -> {
                // A copy is written on a sheet of paper (free in creative)
                if (!player.getAbilities().creativeMode) {
                    int paper = player.getInventory().getSlotWithStack(new ItemStack(Items.PAPER));
                    if (paper < 0) {
                        send(player, new Status(payload.page(), Status.Code.COPY_NEEDS_PAPER));
                        return false;
                    }
                    player.getInventory().getStack(paper).decrement(1);
                }
                MiniGamePages.refresh(server, stack);
                ItemStack copy = MiniGamePages.linkedCopy(stack, 1);
                player.getInventory().offerOrDrop(copy);
                player.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8F, 1.1F);
                send(player, new Status(payload.page(), Status.Code.COPIED));
                // The item is now a linked copy: the editor is opened again on it
                open(player, payload.hand());
            }
            case UNLINK -> {
                if (MiniGamePages.unlink(server, stack) == null) return false;
                player.playSoundToPlayer(SoundEvents.ENTITY_SHEEP_SHEAR, SoundCategory.PLAYERS, 0.6F, 1.3F);
                // The item is another page now: the editor is opened again on it
                open(player, payload.hand());
            }
            case CLEAR_IMAGE -> {
                if (!MiniGamePages.clearImage(server, payload.page())) return false;
                send(player, new Status(payload.page(), Status.Code.IMAGE_CLEARED));
            }
            // The editor closes itself: the page in hand draws the zone
            case DRAW_ZONE -> {
                return PageZoneTool.start(player, payload.hand());
            }
            case RESTORE -> {
                MiniGamePageData data = MiniGamePages.get(server, payload.page());
                if (data.zone() == null) return false;
                MiniGamePages.update(server, data.withRestore(!data.restore()));
            }
            case ADVENTURE -> {
                MiniGamePageData data = MiniGamePages.get(server, payload.page());
                MiniGamePages.update(server, data.withAdventure(!data.adventure()));
            }
            case CLEAR_ZONE -> {
                MiniGamePageData data = MiniGamePages.get(server, payload.page());
                if (data.zone() == null) return false;
                MiniGamePages.update(server, data.withZone(null));
            }
        }
        return true;
    }

    /** A piece of a picture: once the last one is there, the picture is checked and given to the page. */
    public static void upload(ServerPlayerEntity player, Upload payload) {
        if (editable(player, payload.hand(), payload.page()) == null) {
            UPLOADS.remove(player.getUuid());
            return;
        }
        MiniGamePages.ImageResult result = receive(player.getUuid(), payload);
        if (result == null) return; // more to come
        if (result == MiniGamePages.ImageResult.SAVED) {
            // receive() only assembled: store now, under the sender's name
            PendingUpload done = UPLOADS.remove(player.getUuid());
            result = done == null ? MiniGamePages.ImageResult.FAILED : MiniGamePages.setImage(player.server, payload.page(),
                    done.bytes.toByteArray(), player.getGameProfile().getName(), player.getUuid());
        }
        send(player, new Status(payload.page(), result == MiniGamePages.ImageResult.SAVED ? Status.Code.IMAGE_SAVED : Status.Code.IMAGE_REFUSED));
    }

    /**
     * Adds a piece to the sender's picture.
     *
     * @return null while pieces are missing, {@code SAVED} when the picture is whole (still in {@link #UPLOADS}),
     * another result when the upload is refused (and forgotten)
     */
    private static @Nullable MiniGamePages.ImageResult receive(UUID sender, Upload payload) {
        int total = payload.total(), index = payload.index();
        byte[] chunk = payload.bytes();
        if (total < 1 || total > MiniGamePagePayloads.MAX_CHUNKS || index < 0 || index >= total
                || chunk == null || chunk.length == 0 || chunk.length > MiniGamePagePayloads.CHUNK_SIZE) {
            UPLOADS.remove(sender);
            return MiniGamePages.ImageResult.UNREADABLE;
        }
        PendingUpload upload = UPLOADS.get(sender);
        if (index == 0) {
            upload = new PendingUpload(payload.page(), total);
            UPLOADS.put(sender, upload);
        }
        if (upload == null || !upload.page.equals(payload.page()) || upload.total != total || upload.next != index) {
            UPLOADS.remove(sender);
            return MiniGamePages.ImageResult.UNREADABLE;
        }
        if (upload.bytes.size() + chunk.length > MiniGamePageImages.MAX_BYTES) {
            UPLOADS.remove(sender);
            return MiniGamePages.ImageResult.TOO_HEAVY;
        }
        upload.bytes.write(chunk, 0, chunk.length);
        upload.next++;
        return upload.next == total ? MiniGamePages.ImageResult.SAVED : null;
    }

    /** Sends a picture to a player, in pieces (one empty piece when the server does not have it). */
    public static void sendImage(ServerPlayerEntity player, String hash) {
        byte[] bytes = MiniGamePages.imageBytes(player.server, hash);
        if (bytes == null || bytes.length == 0) {
            if (MiniGamePageImage.isHash(hash)) send(player, new ImageChunk(hash, 0, 0, new byte[0]));
            return;
        }
        int total = (bytes.length + MiniGamePagePayloads.CHUNK_SIZE - 1) / MiniGamePagePayloads.CHUNK_SIZE;
        for (int index = 0; index < total; index++) {
            int from = index * MiniGamePagePayloads.CHUNK_SIZE;
            int to = Math.min(bytes.length, from + MiniGamePagePayloads.CHUNK_SIZE);
            send(player, new ImageChunk(hash, index, total, java.util.Arrays.copyOfRange(bytes, from, to)));
        }
    }
}
