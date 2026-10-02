package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
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
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Mini-game Controllers of a server, by the page they hold (saved with the overworld): the controller holding a
 * page is the home of that page's mini-game, and a page has one home at most. Kept here rather than asked to the
 * blocks, so that a party knows a page has a controller (and so a practice round) without its arena being loaded.
 * The zone of the mini-game (the Zone Cartridge in the controller) is kept with it: {@link #zoneOf}.
 * <p>
 * A controller claims its page when it gets it and again every few seconds ({@code MiniGameControllerBlockEntity});
 * a home whose controller is gone, or holds another page, gives way to the next claim once its chunk is loaded.
 */
public final class MiniGameControllers extends PersistentState {
    private static final String ID = "steveparty_mini_game_controllers";
    private static final Type<MiniGameControllers> TYPE = new Type<>(MiniGameControllers::new, MiniGameControllers::fromNbt, null);

    private final Map<UUID, GlobalPos> homes = new LinkedHashMap<>();
    /** The zone of the pages whose controller holds a Zone Cartridge with a zone. */
    private final Map<UUID, PageZone> zones = new LinkedHashMap<>();

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

    public Optional<PageZone> zone(UUID page) {
        return Optional.ofNullable(zones.get(page));
    }

    /** @return true if a Mini-game Controller holds the page. */
    public static boolean has(MinecraftServer server, UUID page) {
        return get(server).homes.containsKey(page);
    }

    /**
     * The zone of the mini-game of a page: the one of the Zone Cartridge in the page's Mini-game Controller. Empty
     * when the page has no controller, the controller no cartridge, or the cartridge no zone (or one too big).
     */
    public static Optional<PageZone> zoneOf(MinecraftServer server, UUID page) {
        return get(server).zone(page);
    }

    /** @return true if the controller at {@code pos} may hold the page: no other controller is its home. */
    public static boolean isFreeFor(MinecraftServer server, UUID page, GlobalPos pos) {
        GlobalPos home = get(server).homes.get(page);
        return home == null || home.equals(pos) || !holds(server, home, page);
    }

    /**
     * The controller at {@code pos} holds (or is about to hold) the page: it becomes its home unless another one is.
     *
     * @param zone the zone of its Zone Cartridge, null for none
     * @return false if another controller is the home of the page
     */
    public static boolean claim(MinecraftServer server, UUID page, GlobalPos pos, @Nullable PageZone zone) {
        MiniGameControllers controllers = get(server);
        GlobalPos home = controllers.homes.get(page);
        if (!pos.equals(home)) {
            if (home != null && holds(server, home, page)) return false;
            controllers.homes.put(page, pos);
            controllers.markDirty();
        }
        if (!java.util.Objects.equals(zone, controllers.zones.get(page))) {
            if (zone == null) controllers.zones.remove(page);
            else controllers.zones.put(page, zone);
            controllers.markDirty();
        }
        return true;
    }

    /** The controller at {@code pos} no longer holds the page. */
    public static void release(MinecraftServer server, UUID page, GlobalPos pos) {
        MiniGameControllers controllers = get(server);
        if (!controllers.homes.remove(page, pos)) return;
        controllers.zones.remove(page);
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
            PageZone zone = zones.get(page);
            if (zone != null) PageZone.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, zone).result().ifPresent(encoded -> home.put("Zone", encoded));
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
            RegistryKey<net.minecraft.world.World> world = RegistryKey.of(RegistryKeys.WORLD, dimension);
            controllers.homes.put(home.getUuid("Page"), GlobalPos.create(world, BlockPos.fromLong(home.getLong("Pos"))));
            if (home.contains("Zone")) PageZone.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, home.get("Zone")).result()
                    .ifPresent(zone -> controllers.zones.put(home.getUuid("Page"), zone));
        }
        return controllers;
    }
}
