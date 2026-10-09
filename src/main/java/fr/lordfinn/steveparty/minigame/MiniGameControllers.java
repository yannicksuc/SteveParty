package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The Mini-game Controllers of a server, by the page they hold (saved with the overworld): the controller holding a
 * page is the home of that page's mini-game, and a page has one home at most. Kept here rather than asked to the
 * blocks, so that a party knows a page has a controller (and so a practice round) without its arena being loaded.
 * The zone of the mini-game is its page's ({@link #zoneOf}); a zone saved here by an earlier version (the Zone Cartridge
 * of the controller) is given to its page when the server starts, if the page has none ({@link #initialize}).
 * <p>
 * A controller claims its page when it gets it and again every few seconds ({@code MiniGameControllerBlockEntity});
 * a home whose controller is gone, or holds another page, gives way to the next claim once its chunk is loaded.
 */
public final class MiniGameControllers extends PersistentState {
    private static final String ID = "steveparty_mini_game_controllers";
    private static final Type<MiniGameControllers> TYPE = new Type<>(MiniGameControllers::new, MiniGameControllers::fromNbt, null);

    private final Map<UUID, GlobalPos> homes = new LinkedHashMap<>();
    /** The zones saved with the homes before the pages had theirs: given to the pages when the server starts. */
    private final Map<UUID, PageZone> oldZones = new LinkedHashMap<>();
    /** The pages whose controller had its « adventure mode » option on, saved before the pages had it: given to them when the server starts. */
    private final Set<UUID> oldAdventure = new LinkedHashSet<>();

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> get(server).adoptOldZones(server));
    }

    /**
     * The zones and « adventure mode » options saved here by an earlier version go to their pages (a zone only to a page
     * without one), and are forgotten.
     */
    public void adoptOldZones(MinecraftServer server) {
        if (oldZones.isEmpty() && oldAdventure.isEmpty()) return;
        oldZones.forEach((page, zone) -> adoptZone(server, page, zone));
        oldAdventure.forEach(page -> adoptAdventure(server, page));
        oldZones.clear();
        oldAdventure.clear();
        markDirty();
    }

    /** The « adventure mode » a controller had before the pages had it goes to its page. */
    public static void adoptAdventure(MinecraftServer server, UUID page) {
        MiniGamePageData data = MiniGamePages.get(server, page);
        if (!data.adventure()) MiniGamePages.update(server, data.withAdventure(true));
    }

    /**
     * Gives the page a zone found where zones were kept before (a controller's Zone Cartridge), if it has none. Such a
     * zone was always played in a bubble: the page's « Remettre l'arène en état » is on.
     *
     * @return true if the page took it
     */
    public static boolean adoptZone(MinecraftServer server, UUID page, @Nullable PageZone zone) {
        if (zone == null || zone.tooBig()) return false;
        MiniGamePageData data = MiniGamePages.get(server, page);
        if (data.zone() != null) return false;
        MiniGamePages.update(server, data.withZone(zone).withRestore(true));
        return true;
    }

    public static MiniGameControllers get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(TYPE, ID);
    }

    /** Where the controller holding the page is, empty when no controller holds it. */
    public static Optional<GlobalPos> of(MinecraftServer server, UUID page) {
        return get(server).home(page);
    }

    public Optional<GlobalPos> home(UUID page) {
        return Optional.ofNullable(homes.get(page));
    }

    /** @return true if a Mini-game Controller holds the page. */
    public static boolean has(MinecraftServer server, UUID page) {
        return get(server).homes.containsKey(page);
    }

    /** The zone of the mini-game of a page: its page's, controller or not. Empty when the page has none. */
    public static Optional<PageZone> zoneOf(MinecraftServer server, UUID page) {
        return MiniGamePages.find(server, page).map(MiniGamePageData::zone);
    }

    /** @return true if the controller at {@code pos} may hold the page: no other controller is its home. */
    public static boolean isFreeFor(MinecraftServer server, UUID page, GlobalPos pos) {
        GlobalPos home = get(server).homes.get(page);
        return home == null || home.equals(pos) || !holds(server, home, page);
    }

    /**
     * The controller at {@code pos} holds (or is about to hold) the page: it becomes its home unless another one is.
     *
     * @return false if another controller is the home of the page
     */
    public static boolean claim(MinecraftServer server, UUID page, GlobalPos pos) {
        MiniGameControllers controllers = get(server);
        GlobalPos home = controllers.homes.get(page);
        if (!pos.equals(home)) {
            if (home != null && holds(server, home, page)) return false;
            controllers.homes.put(page, pos);
            controllers.markDirty();
        }
        return true;
    }

    /** The controller at {@code pos} no longer holds the page. */
    public static void release(MinecraftServer server, UUID page, GlobalPos pos) {
        MiniGameControllers controllers = get(server);
        if (!controllers.homes.remove(page, pos)) return;
        controllers.markDirty();
    }

    /** Whether the home still holds the page; a home that is not loaded is trusted. */
    private static boolean holds(MinecraftServer server, GlobalPos home, UUID page) {
        ServerWorld world = server.getWorld(home.dimension());
        if (world == null) return false;
        ChunkPos chunk = new ChunkPos(home.pos());
        if (!world.isChunkLoaded(chunk.x, chunk.z)) return true;
        return world.getBlockEntity(home.pos()) instanceof MiniGameControllerBlockEntity controller && page.equals(controller.getPageId());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList list = new NbtList();
        homes.forEach((page, pos) -> {
            NbtCompound home = new NbtCompound();
            home.putUuid("Page", page);
            home.putString("Dimension", pos.dimension().getValue().toString());
            home.putLong("Pos", pos.pos().asLong());
            list.add(home);
        });
        nbt.put("Homes", list);
        return nbt;
    }

    public static MiniGameControllers fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        MiniGameControllers controllers = new MiniGameControllers();
        for (NbtElement element : nbt.getList("Homes", NbtElement.COMPOUND_TYPE)) {
            NbtCompound home = (NbtCompound) element;
            Identifier dimension = Identifier.tryParse(home.getString("Dimension"));
            if (dimension == null || !home.containsUuid("Page")) continue;
            RegistryKey<World> world = RegistryKey.of(RegistryKeys.WORLD, dimension);
            controllers.homes.put(home.getUuid("Page"), GlobalPos.create(world, BlockPos.fromLong(home.getLong("Pos"))));
            if (home.contains("Zone")) PageZone.CODEC.parse(NbtOps.INSTANCE, home.get("Zone")).result()
                    .ifPresent(zone -> controllers.oldZones.put(home.getUuid("Page"), zone));
            if (home.getBoolean("Adventure")) controllers.oldAdventure.add(home.getUuid("Page"));
        }
        return controllers;
    }
}
