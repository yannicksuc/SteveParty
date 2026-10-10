package fr.lordfinn.steveparty.service;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.entities.BoardActor;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import fr.lordfinn.steveparty.items.custom.cartridges.FrousseuxCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.GlandouilleCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * The mobs living on their Spawn Marker (a marker set to « always visible », see {@link SpawnMarkerBlockEntity#isResident}):
 * while a party runs on the board of the space linked to the marker, its mob stands on the marker, facing its way,
 * doing nothing but its idle animations (a board actor: a hologram, no AI, invulnerable, never saved: see
 * {@link BoardActors}). When a token lands on the space, the show's mob takes its place (it steps out, see
 * {@link #showStarts}); once the show is over, it is back on its marker. It goes when the party ends, the marker
 * breaks or is set back to « only on landing », or the space's cartridge changes. Never saved: after a reload or a
 * restart in the middle of a party, it is made again on its marker.
 * <p>
 * One mob per marker. Server thread only; the markers loaded are known by their block entities' load / unload, looked
 * at every {@link #CHECK_INTERVAL} ticks (a few lookups each, nothing for a marker in the default mode).
 */
public final class MarkerResidents {
    /** How often the markers are looked at (ticks). */
    public static final int CHECK_INTERVAL = 20;
    /** The mob of each kind of mob cartridge, as it lives on its marker. */
    private static final Map<Class<? extends Item>, Function<ServerWorld, MobEntity>> MOBS = new LinkedHashMap<>();
    /** The Spawn Markers loaded. */
    private static final Set<GlobalPos> MARKERS = ServerMemory.forgetOnStop(new HashSet<>());
    /** The mob on each marker, with its BoardActors sequence. */
    private static final Map<GlobalPos, Resident> RESIDENTS = ServerMemory.forgetOnStop(new HashMap<>());
    /** The markers whose mob is out for a show (the show's BoardActors sequence). */
    private static final Map<GlobalPos, UUID> SHOWS = ServerMemory.forgetOnStop(new HashMap<>());
    private static int age;

    private record Resident(UUID sequence, MobEntity mob, Item kind) {
    }

    static {
        register(FrousseuxCartridgeItem.class, world -> {
            FrousseuxEntity one = ModEntities.FROUSSEUX.create(world);
            if (one != null) one.setColor(FrousseuxColor.random(world.getRandom()));
            return one;
        });
        register(GlandouilleCartridgeItem.class, world -> {
            GlandouilleEntity one = ModEntities.GLANDOUILLE.create(world);
            if (one != null) {
                one.setVariant(GlandouilleVariant.CLASSIC);
                one.setHat(true);
            }
            return one;
        });
        register(MistigriCartridgeItem.class, ModEntities.MISTIGRI::create);
        register(ShopCartridgeItem.class, ModEntities.BOXED_TRADER_ENTITY::create);
        register(TrichaudronCartridgeItem.class, world -> {
            TrichaudronEntity one = ModEntities.TRICHAUDRON.create(world);
            if (one != null) one.setTank(TrichaudronEntity.TANK_MAX);
            return one;
        });
    }

    private MarkerResidents() {
    }

    /** The mob living on the markers of the spaces of {@code cartridge} (a board actor made of it: one of ours). */
    public static void register(Class<? extends Item> cartridge, Function<ServerWorld, MobEntity> mob) {
        MOBS.put(cartridge, mob);
    }

    public static void initialize() {
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof SpawnMarkerBlockEntity) MARKERS.add(key(world, blockEntity.getPos()));
        });
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, world) -> {
            if (blockEntity instanceof SpawnMarkerBlockEntity) remove(world, blockEntity.getPos());
        });
        ServerTickEvents.END_SERVER_TICK.register(MarkerResidents::tick);
    }

    private static void tick(MinecraftServer server) {
        if (MARKERS.isEmpty()) return;
        // A show over: its mob is back on its marker at once
        if (!SHOWS.isEmpty()) {
            for (Map.Entry<GlobalPos, UUID> show : new ArrayList<>(SHOWS.entrySet())) {
                if (BoardActors.isRunning(show.getValue())) continue;
                ServerWorld world = server.getWorld(show.getKey().dimension());
                if (world != null) check(world, show.getKey().pos());
            }
        }
        if (++age < CHECK_INTERVAL) return;
        age = 0;
        update(server);
    }

    /** Every marker loaded looked at now (also for the GameTests). */
    public static void update(MinecraftServer server) {
        for (GlobalPos marker : new ArrayList<>(MARKERS)) {
            ServerWorld world = server.getWorld(marker.dimension());
            if (world != null) check(world, marker.pos());
        }
    }

    /** The marker at {@code pos}: its mob made, kept in place, or removed, as it should be now. */
    public static void check(ServerWorld world, BlockPos pos) {
        GlobalPos key = key(world, pos);
        Item kind = wanted(world, pos);
        Resident resident = RESIDENTS.get(key);
        if (kind == null) {
            SHOWS.remove(key);
            dismiss(key);
            return;
        }
        UUID show = SHOWS.get(key);
        if (show != null) {
            if (BoardActors.isRunning(show)) {
                dismiss(key);
                return;
            }
            SHOWS.remove(key);
        }
        if (resident != null && (resident.mob().isRemoved() || resident.kind() != kind)) {
            dismiss(key);
            resident = null;
        }
        BoardMobSpots.Spot spot = BoardMobSpots.at(world, pos);
        Vec3d at = spot.pos();
        float yaw = spot.yaw();
        if (resident == null) {
            spawn(world, key, kind, at, yaw);
            return;
        }
        // Kept on its marker, facing its way (nothing moves it, but a turned marker...)
        MobEntity mob = resident.mob();
        if (mob.squaredDistanceTo(at) > 0.01 || Math.abs(mob.getYaw() - yaw) > 1) place(mob, at, yaw);
    }

    /** The kind of cartridge whose mob should live on the marker at {@code pos} now, null for none. */
    private static @Nullable Item wanted(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker) || !marker.isResident()) return null;
        BlockPos owner = marker.getOwner();
        if (owner == null) return null;
        BoardSpaceBlockEntity space = ABoardSpaceBlock.getBoardSpaceEntity(world, owner);
        if (space == null) return null;
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!pos.equals(CartridgeSpawnMarker.linked(cartridge, world)) || !MOBS.containsKey(cartridge.getItem().getClass())) return null;
        // Only while a party runs on its board
        if (PartyControllerEntity.getPartyOfBoardSpace(world, owner, PartyControllerEntity.BOARD_NEARBY_RADIUS, false).isEmpty())
            return null;
        return cartridge.getItem();
    }

    private static void spawn(ServerWorld world, GlobalPos key, Item kind, Vec3d at, float yaw) {
        MobEntity mob = MOBS.get(kind.getClass()).apply(world);
        if (mob == null) return;
        if (mob instanceof BoardActor actor) actor.makeBoardActor();
        mob.setNoGravity(true); // it stays as high as its marker says, even over the void
        UUID sequence = UUID.randomUUID();
        BoardActors.join(sequence, mob);
        place(mob, at, yaw);
        world.spawnEntity(mob);
        RESIDENTS.put(key, new Resident(sequence, mob, kind));
        world.spawnParticles(ParticleTypes.POOF, at.x, at.y + 0.4, at.z, 8, 0.25, 0.25, 0.25, 0.01);
    }

    private static void place(MobEntity mob, Vec3d at, float yaw) {
        mob.refreshPositionAndAngles(at.x, at.y, at.z, yaw, 0);
        mob.setHeadYaw(yaw);
        mob.setBodyYaw(yaw);
        mob.setVelocity(Vec3d.ZERO);
    }

    private static void dismiss(GlobalPos key) {
        Resident resident = RESIDENTS.remove(key);
        if (resident != null) BoardActors.end(resident.sequence());
    }

    /**
     * The show {@code sequence} of the space linked to the marker at {@code marker} starts there: its mob steps out
     * (the show's mob appears in its place), and is back once the show's actors are gone.
     */
    public static void showStarts(ServerWorld world, BlockPos marker, UUID sequence) {
        GlobalPos key = key(world, marker);
        if (!RESIDENTS.containsKey(key)) return;
        dismiss(key);
        SHOWS.put(key, sequence);
    }

    /** The marker at {@code pos} changed (mode, facing, link): its mob made again as it should be. */
    public static void refresh(ServerWorld world, @Nullable BlockPos pos) {
        if (pos == null) return;
        GlobalPos key = key(world, pos);
        if (!SHOWS.containsKey(key)) dismiss(key);
        if (MARKERS.contains(key)) check(world, pos);
    }

    /** The marker at {@code pos} is gone (broken, unloaded): its mob goes. */
    public static void remove(ServerWorld world, BlockPos pos) {
        GlobalPos key = key(world, pos);
        MARKERS.remove(key);
        SHOWS.remove(key);
        dismiss(key);
    }

    /** The mob living on the marker at {@code pos} now, null for none (for the GameTests). */
    public static @Nullable MobEntity resident(ServerWorld world, BlockPos pos) {
        Resident resident = RESIDENTS.get(key(world, pos));
        return resident == null || resident.mob().isRemoved() ? null : resident.mob();
    }

    private static GlobalPos key(net.minecraft.world.World world, BlockPos pos) {
        return GlobalPos.create(world.getRegistryKey(), pos.toImmutable());
    }
}
