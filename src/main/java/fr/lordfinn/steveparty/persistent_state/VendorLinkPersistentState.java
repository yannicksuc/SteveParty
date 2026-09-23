package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Single source of truth for the blocks (trading stalls, cash registers, storages) linked to a shopkeeper
 * with the Shopkeeper Key. Positions are stored with their dimension.
 * <p>
 * Old saves stored positions without dimension: those "legacy" positions are kept as they are and match any
 * dimension, until the trader resolves its links ({@link #getVendorLinks(UUID, RegistryKey)}), which pins
 * them to the trader's dimension.
 */
public class VendorLinkPersistentState extends PersistentState {
    private static final Type<VendorLinkPersistentState> TYPE = new Type<>(
            VendorLinkPersistentState::new,
            VendorLinkPersistentState::createFromNbt,
            null
    );

    private final Map<UUID, Set<GlobalPos>> vendorLinks = new HashMap<>();
    /** Positions read from saves made before dimensions were stored (dimension unknown). */
    private final Map<UUID, Set<BlockPos>> legacyVendorLinks = new HashMap<>();
    /**
     * Owner (player UUID) of each trader: the first player who linked a Shopkeeper Key to it. Mirrors the owner
     * saved on the trader entity, so it can be checked while the trader is not loaded.
     */
    private final Map<UUID, UUID> vendorOwners = new HashMap<>();

    public VendorLinkPersistentState() {
        super();
    }

    private static VendorLinkPersistentState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        return fromNbt(nbt);
    }

    /** Reads a state from its saved NBT (current or legacy dimension-less format). */
    public static VendorLinkPersistentState fromNbt(NbtCompound nbt) {
        VendorLinkPersistentState state = new VendorLinkPersistentState();
        state.readFromNbt(nbt);
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList vendorList = new NbtList();

        Set<UUID> vendors = new HashSet<>(vendorLinks.keySet());
        vendors.addAll(legacyVendorLinks.keySet());
        for (UUID vendorId : vendors) {
            NbtCompound vendorTag = new NbtCompound();
            vendorTag.putUuid("VendorId", vendorId);

            NbtList posList = new NbtList();
            for (GlobalPos globalPos : vendorLinks.getOrDefault(vendorId, Collections.emptySet())) {
                NbtCompound posTag = writePos(globalPos.pos());
                posTag.putString("Dimension", globalPos.dimension().getValue().toString());
                posList.add(posTag);
            }
            // Legacy positions are written back without dimension so they keep matching any dimension
            for (BlockPos pos : legacyVendorLinks.getOrDefault(vendorId, Collections.emptySet())) {
                posList.add(writePos(pos));
            }

            vendorTag.put("Positions", posList);
            vendorList.add(vendorTag);
        }

        nbt.put("Vendors", vendorList);

        NbtList ownerList = new NbtList();
        vendorOwners.forEach((vendorId, owner) -> {
            NbtCompound ownerTag = new NbtCompound();
            ownerTag.putUuid("VendorId", vendorId);
            ownerTag.putUuid("Owner", owner);
            ownerList.add(ownerTag);
        });
        nbt.put("Owners", ownerList);
        return nbt;
    }

    private static NbtCompound writePos(BlockPos pos) {
        NbtCompound posTag = new NbtCompound();
        posTag.putInt("X", pos.getX());
        posTag.putInt("Y", pos.getY());
        posTag.putInt("Z", pos.getZ());
        return posTag;
    }

    protected void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("Vendors")) {
            NbtList vendorList = nbt.getList("Vendors", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < vendorList.size(); i++) {
                NbtCompound vendorTag = vendorList.getCompound(i);
                UUID vendorId = vendorTag.getUuid("VendorId");

                NbtList posList = vendorTag.getList("Positions", NbtElement.COMPOUND_TYPE);
                for (int j = 0; j < posList.size(); j++) {
                    NbtCompound posTag = posList.getCompound(j);
                    BlockPos pos = new BlockPos(
                            posTag.getInt("X"),
                            posTag.getInt("Y"),
                            posTag.getInt("Z")
                    );
                    Identifier dimensionId = posTag.contains("Dimension", NbtElement.STRING_TYPE)
                            ? Identifier.tryParse(posTag.getString("Dimension")) : null;
                    if (dimensionId != null) {
                        RegistryKey<World> dimension = RegistryKey.of(RegistryKeys.WORLD, dimensionId);
                        vendorLinks.computeIfAbsent(vendorId, k -> new HashSet<>()).add(GlobalPos.create(dimension, pos));
                    } else {
                        legacyVendorLinks.computeIfAbsent(vendorId, k -> new HashSet<>()).add(pos);
                    }
                }
            }
        }
        if (nbt.contains("Owners")) {
            NbtList ownerList = nbt.getList("Owners", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < ownerList.size(); i++) {
                NbtCompound ownerTag = ownerList.getCompound(i);
                if (ownerTag.containsUuid("VendorId") && ownerTag.containsUuid("Owner")) {
                    vendorOwners.put(ownerTag.getUuid("VendorId"), ownerTag.getUuid("Owner"));
                }
            }
        }
    }

    /** @return the player owning the trader, or null if no player linked a key to it yet (or before ownership existed). */
    @Nullable
    public UUID getOwner(UUID vendorId) {
        return vendorOwners.get(vendorId);
    }

    /** Records the trader's owner (kept in sync with the owner saved on the trader entity). */
    public void setOwner(UUID vendorId, UUID owner) {
        if (!owner.equals(vendorOwners.put(vendorId, owner))) markDirty();
    }

    /**
     * Ownership check of a link operation: a trader without owner is claimed by the player (first link, or
     * migration of a trader created before ownership existed).
     *
     * @return true if the player is (now) the owner of the trader
     */
    public boolean claimOrCheckOwner(UUID vendorId, UUID player) {
        UUID owner = vendorOwners.get(vendorId);
        if (owner == null) {
            setOwner(vendorId, player);
            return true;
        }
        return owner.equals(player);
    }

    /**
     * Removes every link to the block at this position (the block was broken), so a new block placed there
     * does not inherit the links. Legacy (dimension-less) links at the same coordinates are removed too.
     *
     * @return true if at least one link was removed
     */
    public boolean unlinkPosition(GlobalPos pos) {
        boolean modified = false;
        for (Iterator<Map.Entry<UUID, Set<GlobalPos>>> it = vendorLinks.entrySet().iterator(); it.hasNext(); ) {
            Set<GlobalPos> positions = it.next().getValue();
            modified |= positions.remove(pos);
            if (positions.isEmpty()) it.remove();
        }
        for (Iterator<Map.Entry<UUID, Set<BlockPos>>> it = legacyVendorLinks.entrySet().iterator(); it.hasNext(); ) {
            Set<BlockPos> positions = it.next().getValue();
            modified |= positions.remove(pos.pos());
            if (positions.isEmpty()) it.remove();
        }
        if (modified) markDirty();
        return modified;
    }

    /**
     * Called when a shop block (trading stall, cash register) is really removed from the world (not on a state
     * change of the same block): forgets its links so a block placed there later starts unlinked.
     */
    public static void onShopBlockRemoved(World world, BlockPos pos) {
        if (world.isClient) return;
        VendorLinkPersistentState state = get(world.getServer());
        if (state != null) state.unlinkPosition(GlobalPos.create(world.getRegistryKey(), pos));
        TraderStallRegistry.unlinkStallFromAllTraders(pos);
    }

    public static VendorLinkPersistentState get(MinecraftServer server) {
        return VendorLinkPersistentState.getOrCreate(server, TYPE, "vendor_links");
    }

    public void linkBlock(UUID vendorId, GlobalPos pos) {
        removeLegacy(vendorId, pos.pos());
        vendorLinks.computeIfAbsent(vendorId, k -> new HashSet<>()).add(pos);
        markDirty();
    }

    public boolean isBlockLinkedToVendor(UUID vendorId, GlobalPos pos) {
        Set<GlobalPos> positions = vendorLinks.get(vendorId);
        if (positions != null && positions.contains(pos)) return true;
        Set<BlockPos> legacy = legacyVendorLinks.get(vendorId);
        return legacy != null && legacy.contains(pos.pos());
    }

    public void unlinkBlock(UUID vendorId, GlobalPos pos) {
        boolean modified = removeLegacy(vendorId, pos.pos());
        Set<GlobalPos> positions = vendorLinks.get(vendorId);
        if (positions != null) {
            modified |= positions.remove(pos);
            if (positions.isEmpty()) {
                vendorLinks.remove(vendorId);
            }
        }
        if (modified) markDirty();
    }

    /**
     * Links the block if it was not linked to the vendor, unlinks it otherwise.
     *
     * @return true if the block is now linked
     */
    public boolean toggleLink(UUID vendorId, GlobalPos pos) {
        if (isBlockLinkedToVendor(vendorId, pos)) {
            unlinkBlock(vendorId, pos);
            return false;
        }
        linkBlock(vendorId, pos);
        return true;
    }

    private boolean removeLegacy(UUID vendorId, BlockPos pos) {
        Set<BlockPos> legacy = legacyVendorLinks.get(vendorId);
        if (legacy == null || !legacy.remove(pos)) return false;
        if (legacy.isEmpty()) legacyVendorLinks.remove(vendorId);
        markDirty();
        return true;
    }

    /** Every position linked to the vendor, in all dimensions (legacy positions excluded). */
    public Set<GlobalPos> getVendorLinks(UUID vendorId) {
        return Collections.unmodifiableSet(vendorLinks.getOrDefault(vendorId, Collections.emptySet()));
    }

    /**
     * Positions linked to the vendor in the given dimension. Called from the trader's own world: the legacy
     * (dimension-less) positions of this vendor are migrated to that dimension.
     */
    public Set<BlockPos> getVendorLinks(UUID vendorId, RegistryKey<World> dimension) {
        Set<BlockPos> legacy = legacyVendorLinks.remove(vendorId);
        if (legacy != null) {
            Set<GlobalPos> positions = vendorLinks.computeIfAbsent(vendorId, k -> new HashSet<>());
            legacy.forEach(pos -> positions.add(GlobalPos.create(dimension, pos)));
            markDirty();
        }
        return getLinkedPositionsIn(vendorId, dimension);
    }

    /** Positions linked to the vendor in the given dimension, without migrating anything (read only). */
    public Set<BlockPos> getLinkedPositionsIn(UUID vendorId, RegistryKey<World> dimension) {
        Set<BlockPos> result = new HashSet<>();
        for (GlobalPos globalPos : vendorLinks.getOrDefault(vendorId, Collections.emptySet())) {
            if (globalPos.dimension().equals(dimension)) result.add(globalPos.pos());
        }
        result.addAll(legacyVendorLinks.getOrDefault(vendorId, Collections.emptySet()));
        return result;
    }

    /** Every vendor the block at this position is linked to. */
    public Set<UUID> getVendorsLinkedTo(GlobalPos pos) {
        Set<UUID> vendors = new HashSet<>();
        vendorLinks.forEach((vendorId, positions) -> {
            if (positions.contains(pos)) vendors.add(vendorId);
        });
        legacyVendorLinks.forEach((vendorId, positions) -> {
            if (positions.contains(pos.pos())) vendors.add(vendorId);
        });
        return vendors;
    }

    protected static <T extends VendorLinkPersistentState> T getOrCreate(
            MinecraftServer server,
            Type<T> type,
            String name
    ) {
        if (server == null || server.getWorld(World.OVERWORLD) == null) return null;
        PersistentStateManager manager = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getPersistentStateManager();
        if (manager == null) return null;
        // Only mark dirty on actual modification (linkBlock/unlinkBlock), not on every access
        return manager.getOrCreate(type, name);
    }
}
