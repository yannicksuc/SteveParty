package fr.lordfinn.steveparty.minigame;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The content of every mini-game page of the world, by page id (saved with the overworld). The pictures themselves
 * are files beside it ({@link MiniGamePageImageStore}): only their reference is kept here.
 * Go through {@link MiniGamePages} to read and change pages.
 */
public class MiniGamePagesState extends PersistentState {
    private static final String ID = "steveparty_minigame_pages";
    private static final Type<MiniGamePagesState> TYPE = new Type<>(MiniGamePagesState::new,
            (nbt, registries) -> fromNbt(nbt), null);

    private final Map<UUID, MiniGamePageData> pages = new LinkedHashMap<>();

    public static MiniGamePagesState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    public @Nullable MiniGamePageData get(UUID id) {
        return pages.get(id);
    }

    public Collection<MiniGamePageData> all() {
        return Collections.unmodifiableCollection(pages.values());
    }

    public void put(MiniGamePageData data) {
        if (data.equals(pages.put(data.id(), data))) return;
        markDirty();
    }

    /** @return true if a page other than {@code except} shows the picture {@code hash}. */
    public boolean isImageUsed(String hash, @Nullable UUID except) {
        for (MiniGamePageData page : pages.values()) {
            if (page.image() != null && page.image().hash().equals(hash) && !page.id().equals(except)) return true;
        }
        return false;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        pages.values().forEach(page -> list.add(page.toNbt()));
        nbt.put("Pages", list);
        return nbt;
    }

    public static MiniGamePagesState fromNbt(NbtCompound nbt) {
        MiniGamePagesState state = new MiniGamePagesState();
        NbtList list = nbt.getList("Pages", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            MiniGamePageData page = MiniGamePageData.fromNbt(list.getCompound(i));
            if (page != null) state.pages.put(page.id(), page);
        }
        return state;
    }
}
