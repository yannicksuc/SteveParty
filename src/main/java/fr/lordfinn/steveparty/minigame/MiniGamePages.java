package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.components.MiniGamePageRef;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * The mini-game pages of a server: the one place to read and change what a page says.
 * <p>
 * A page item only carries a page id ({@link MiniGamePageRef}); its content ({@link MiniGamePageData}) is kept here,
 * saved with the world ({@link MiniGamePagesState}, pictures in {@link MiniGamePageImageStore}). Items with the same
 * id are linked copies: a change made through any of them shows on all of them.
 * <ul>
 *     <li>read: {@link #get}, {@link #find}, {@link #of(MinecraftServer, ItemStack)}, {@link #all};</li>
 *     <li>change: {@link #update} (and {@link #setImage}, {@link #clearImage}, {@link #duplicate});</li>
 *     <li>be told: {@link #CHANGED}, called after every change. The clients are told too.</li>
 * </ul>
 * Server thread only.
 */
public final class MiniGamePages {
    /** Called on the server thread after a page changed (written, picture set or removed, created by a copy). */
    public static final Event<Changed> CHANGED = EventFactory.createArrayBacked(Changed.class, listeners -> (server, page) -> {
        for (Changed listener : listeners) listener.onPageChanged(server, page);
    });

    @FunctionalInterface
    public interface Changed {
        void onPageChanged(MinecraftServer server, MiniGamePageData page);
    }

    public enum ImageResult {
        SAVED,
        /** More than {@link MiniGamePageImages#MAX_BYTES} bytes. */
        TOO_HEAVY,
        /** Wider or higher than {@link MiniGamePageImages#MAX_WIDTH} × {@link MiniGamePageImages#MAX_HEIGHT}. */
        TOO_LARGE,
        /** Neither a PNG nor a JPEG. */
        UNREADABLE,
        /** The file could not be written. */
        FAILED
    }

    private MiniGamePages() {
    }

    // ------------------------------------------------------------------ content

    /** @return the content of a page; a blank one when nothing was written on it yet. Never null. */
    public static MiniGamePageData get(MinecraftServer server, UUID id) {
        MiniGamePageData data = MiniGamePagesState.get(server).get(id);
        return data != null ? data : MiniGamePageData.empty(id);
    }

    /** @return the content of a page, empty when nothing was written on it yet. */
    public static Optional<MiniGamePageData> find(MinecraftServer server, UUID id) {
        return Optional.ofNullable(MiniGamePagesState.get(server).get(id));
    }

    /** Every page something was written on. */
    public static Collection<MiniGamePageData> all(MinecraftServer server) {
        return MiniGamePagesState.get(server).all();
    }

    /**
     * Writes a page (the whole content, under {@code data.id()}), then tells the listeners of {@link #CHANGED} and
     * every client. Nothing happens when the content is the same as before.
     */
    public static void update(MinecraftServer server, MiniGamePageData data) {
        MiniGamePagesState state = MiniGamePagesState.get(server);
        MiniGamePageData before = state.get(data.id());
        if (data.equals(before) || (before == null && data.isBlank())) return;
        state.put(data);
        // A picture no page shows any more is deleted
        if (before != null && before.image() != null
                && (data.image() == null || !data.image().hash().equals(before.image().hash()))
                && !state.isImageUsed(before.image().hash(), null)) {
            MiniGamePageImageStore.delete(server, before.image().hash());
        }
        CHANGED.invoker().onPageChanged(server, data);
        MiniGamePagePayloads.Data payload = new MiniGamePagePayloads.Data(data);
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (ServerPlayNetworking.canSend(player, MiniGamePagePayloads.Data.ID)) ServerPlayNetworking.send(player, payload);
        }
    }

    /**
     * Gives a page its picture, from the bytes of a PNG or JPEG within the limits of {@link MiniGamePageImages}.
     *
     * @param uploader   name of the player who sends it, kept with the picture
     * @param uploaderId that player, null when unknown
     */
    public static ImageResult setImage(MinecraftServer server, UUID id, byte[] bytes, String uploader, @Nullable UUID uploaderId) {
        if (bytes == null || bytes.length == 0) return ImageResult.UNREADABLE;
        if (bytes.length > MiniGamePageImages.MAX_BYTES) return ImageResult.TOO_HEAVY;
        MiniGamePageImages.Info info = MiniGamePageImages.inspect(bytes);
        if (info == null) return ImageResult.UNREADABLE;
        if (info.width() > MiniGamePageImages.MAX_WIDTH || info.height() > MiniGamePageImages.MAX_HEIGHT) return ImageResult.TOO_LARGE;
        String hash = MiniGamePageImages.hash(bytes);
        if (!MiniGamePageImageStore.write(server, hash, bytes)) return ImageResult.FAILED;
        update(server, get(server, id).withImage(new MiniGamePageImage(hash, info.width(), info.height(), bytes.length,
                uploader == null ? "" : uploader, uploaderId)));
        return ImageResult.SAVED;
    }

    /** Removes the picture of a page. @return false if it had none */
    public static boolean clearImage(MinecraftServer server, UUID id) {
        MiniGamePageData data = get(server, id);
        if (data.image() == null) return false;
        update(server, data.withImage(null));
        return true;
    }

    /** @return the bytes of a picture (PNG or JPEG), null if no page shows it. */
    public static byte @Nullable [] imageBytes(MinecraftServer server, String hash) {
        if (!MiniGamePageImage.isHash(hash) || !MiniGamePagesState.get(server).isImageUsed(hash, null)) return null;
        return MiniGamePageImageStore.read(server, hash);
    }

    /** Copies the content of a page under a new id (a page of its own, no longer linked). @return the new page */
    public static MiniGamePageData duplicate(MinecraftServer server, UUID id) {
        MiniGamePageData copy = get(server, id).withId(UUID.randomUUID());
        update(server, copy);
        return copy;
    }

    // ------------------------------------------------------------------ rights

    /** Writing on a page takes the right to build: not in adventure, not as a spectator. */
    public static boolean canEdit(PlayerEntity player) {
        return player != null && !player.isSpectator() && player.getAbilities().allowModifyWorld;
    }

    // ------------------------------------------------------------------ items

    public static boolean isPage(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof MiniGamePageItem;
    }

    /** @return the id of the page this item is, null when it has none yet (never opened). */
    public static @Nullable UUID idOf(ItemStack stack) {
        if (!isPage(stack)) return null;
        MiniGamePageRef ref = stack.get(ModComponents.MINI_GAME_PAGE);
        return ref == null ? null : ref.id();
    }

    /** Gives the item a page id if it has none. @return its id */
    public static UUID ensureId(ItemStack stack) {
        MiniGamePageRef ref = stack.get(ModComponents.MINI_GAME_PAGE);
        if (ref == null) {
            ref = new MiniGamePageRef(UUID.randomUUID(), "", false);
            stack.set(ModComponents.MINI_GAME_PAGE, ref);
        }
        return ref.id();
    }

    /** @return the content of the page this item is, null when it has no id yet. */
    public static @Nullable MiniGamePageData of(MinecraftServer server, ItemStack stack) {
        UUID id = idOf(stack);
        return id == null ? null : get(server, id);
    }

    /** The name of a page for the players: its title, or the item's name while it has none. Refreshes the item. */
    public static Text displayName(MinecraftServer server, ItemStack stack) {
        refresh(server, stack);
        return stack.getName();
    }

    /** Brings the title kept on the item up to date with the page. @return true if the item changed */
    public static boolean refresh(MinecraftServer server, ItemStack stack) {
        if (!isPage(stack)) return false;
        MiniGamePageRef ref = stack.get(ModComponents.MINI_GAME_PAGE);
        if (ref == null) return false;
        String title = get(server, ref.id()).title();
        if (title.equals(ref.title())) return false;
        stack.set(ModComponents.MINI_GAME_PAGE, ref.withTitle(title));
        return true;
    }

    /**
     * Makes linked copies of a page item: same id (given now if it had none), marked as linked.
     * The item copied is marked as linked too.
     */
    public static ItemStack linkedCopy(ItemStack page, int count) {
        ensureId(page);
        MiniGamePageRef ref = page.get(ModComponents.MINI_GAME_PAGE);
        if (ref != null && !ref.linked()) page.set(ModComponents.MINI_GAME_PAGE, ref.withLinked(true));
        return page.copyWithCount(count);
    }

    /**
     * Unlinks a page item: it gets an id of its own, with a copy of the content (text, picture, settings).
     *
     * @return the new page, null if the item has no id
     */
    public static @Nullable MiniGamePageData unlink(MinecraftServer server, ItemStack stack) {
        UUID id = idOf(stack);
        if (id == null) return null;
        MiniGamePageData copy = duplicate(server, id);
        stack.set(ModComponents.MINI_GAME_PAGE, new MiniGamePageRef(copy.id(), copy.title(), false));
        return copy;
    }
}
