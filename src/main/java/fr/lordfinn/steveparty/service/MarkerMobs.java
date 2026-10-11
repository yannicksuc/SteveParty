package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.board.BoardLinks;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.EntityType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each Spawn Bubble shows in its bubble: the kinds of mob of the cartridges linked to it
 * ({@link SpawnMarkerBlockEntity#getLinkedMobs}) and whether a party runs on their board. The cartridges know their
 * bubble, not the reverse: a bubble keeps the spaces known to link it ({@link SpawnMarkerBlockEntity#getLinkers}), and
 * looks at them again when told ({@link #touch}: a link made or cut, a space's cartridges changed, a space broken; at
 * the end of the tick), when it loads, and every {@link #INTERVAL} ticks whatever happened (a few lookups per bubble).
 * A space unloaded keeps what it had. Server thread only.
 */
public final class MarkerMobs {
    /** How often every bubble loaded is looked at again (ticks). */
    public static final int INTERVAL = 20;
    /** The bubbles loaded. */
    private static final Set<GlobalPos> LOADED = ServerMemory.forgetOnStop(new HashSet<>());
    /** The bubbles to look at, at the end of the tick. */
    private static final Set<GlobalPos> PENDING = ServerMemory.forgetOnStop(new LinkedHashSet<>());
    private static int age;

    private MarkerMobs() {
    }

    public static void initialize() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (!(blockEntity instanceof SpawnMarkerBlockEntity)) return;
            GlobalPos key = GlobalPos.create(world.getRegistryKey(), blockEntity.getPos().toImmutable());
            LOADED.add(key);
            PENDING.add(key);
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof SpawnMarkerBlockEntity)
                LOADED.remove(GlobalPos.create(world.getRegistryKey(), blockEntity.getPos()));
        });
        ServerTickEvents.END_SERVER_TICK.register(MarkerMobs::tick);
    }

    private static void tick(MinecraftServer server) {
        if (++age >= INTERVAL) {
            age = 0;
            PENDING.addAll(LOADED);
        }
        if (PENDING.isEmpty()) return;
        List<GlobalPos> now = new ArrayList<>(PENDING);
        PENDING.clear();
        for (GlobalPos marker : now) {
            ServerWorld world = server.getWorld(marker.dimension());
            if (world != null) refresh(world, marker.pos());
        }
    }

    /** The bubble at {@code marker} looked at again at the end of the tick (nothing for null or a client world). */
    public static void touch(World world, @Nullable BlockPos marker) {
        if (marker != null && !world.isClient) PENDING.add(GlobalPos.create(world.getRegistryKey(), marker.toImmutable()));
    }

    /**
     * The cartridges of the space at {@code tile} changed, or it broke: the bubbles it links (it is known to them now)
     * and those that knew it are looked at again.
     */
    public static void spaceChanged(World world, BlockPos tile) {
        if (world.isClient) return;
        for (GlobalPos marker : LOADED) {
            if (marker.dimension().equals(world.getRegistryKey()) && world.getBlockEntity(marker.pos()) instanceof SpawnMarkerBlockEntity entity
                    && entity.getLinkers().contains(tile)) touch(world, marker.pos());
        }
        CartridgeContainerBlockEntity container = BoardLinks.container(world, tile);
        if (container == null) return;
        for (int slot = 0; slot < container.size(); slot++) {
            BlockPos marker = CartridgeSpawnMarker.marker(container.getStack(slot), world);
            if (marker != null && world.getBlockEntity(marker) instanceof SpawnMarkerBlockEntity entity) {
                entity.addLinker(tile);
                touch(world, marker);
            }
        }
    }

    /** The bubble at {@code pos} told now what links it (also for the GameTests). */
    @SuppressWarnings("deprecation") // isChunkLoaded(BlockPos) would not do: an unloaded space must not be loaded for this
    public static void refresh(ServerWorld world, BlockPos pos) {
        if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(pos.getZ()))) return;
        if (!(world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker)) return;
        Map<BlockPos, List<EntityType<?>>> links = new LinkedHashMap<>();
        boolean party = false;
        for (BlockPos tile : marker.getLinkers()) {
            if (!world.isChunkLoaded(ChunkSectionPos.getSectionCoord(tile.getX()), ChunkSectionPos.getSectionCoord(tile.getZ()))) {
                List<EntityType<?>> known = marker.getLinks().get(tile);
                if (known != null) links.put(tile, known);
                continue;
            }
            List<EntityType<?>> mobs = mobsLinking(world, tile, pos);
            if (mobs.isEmpty()) continue;
            links.put(tile, mobs);
            if (!party) party = PartyControllerEntity.getPartyOfBoardSpace(world, tile, PartyControllerEntity.BOARD_NEARBY_RADIUS, false).isPresent();
        }
        marker.setLinks(links, party);
    }

    /** The kinds of mob of the cartridges of the space at {@code tile} linked to the bubble at {@code marker}. */
    private static List<EntityType<?>> mobsLinking(ServerWorld world, BlockPos tile, BlockPos marker) {
        CartridgeContainerBlockEntity container = BoardLinks.container(world, tile);
        if (container == null) return List.of();
        Set<EntityType<?>> kinds = new LinkedHashSet<>();
        for (int slot = 0; slot < container.size(); slot++) {
            ItemStack cartridge = container.getStack(slot);
            if (!CartridgeSpawnMarker.spawnsMobs(cartridge) || !marker.equals(CartridgeSpawnMarker.linked(cartridge, world))) continue;
            EntityType<?> mob = CartridgeSpawnMarker.mobOf(cartridge);
            if (mob != null) kinds.add(mob);
        }
        return List.copyOf(kinds);
    }
}
