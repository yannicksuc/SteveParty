package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;

import java.util.*;

public class VendorLinkPersistentState extends PersistentState {
    private static final Type<VendorLinkPersistentState> TYPE = new Type<>(
            VendorLinkPersistentState::new,
            VendorLinkPersistentState::createFromNbt,
            null
    );

    private final Map<UUID, Set<BlockPos>> vendorLinks = new HashMap<>();

    public VendorLinkPersistentState() {
        super();
    }

    private static VendorLinkPersistentState createFromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        VendorLinkPersistentState state = new VendorLinkPersistentState();
        state.readFromNbt(nbt);
        return state;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList vendorList = new NbtList();

        for (Map.Entry<UUID, Set<BlockPos>> entry : vendorLinks.entrySet()) {
            NbtCompound vendorTag = new NbtCompound();
            vendorTag.putUuid("VendorId", entry.getKey());

            NbtList posList = new NbtList();
            for (BlockPos pos : entry.getValue()) {
                NbtCompound posTag = new NbtCompound();
                posTag.putInt("X", pos.getX());
                posTag.putInt("Y", pos.getY());
                posTag.putInt("Z", pos.getZ());
                posList.add(posTag);
            }

            vendorTag.put("Positions", posList);
            vendorList.add(vendorTag);
        }

        nbt.put("Vendors", vendorList);
        return nbt;
    }

    protected void readFromNbt(NbtCompound nbt) {
        if (nbt.contains("Vendors")) {
            NbtList vendorList = nbt.getList("Vendors", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < vendorList.size(); i++) {
                NbtCompound vendorTag = vendorList.getCompound(i);
                UUID vendorId = vendorTag.getUuid("VendorId");

                NbtList posList = vendorTag.getList("Positions", NbtElement.COMPOUND_TYPE);
                Set<BlockPos> positions = new HashSet<>();
                for (int j = 0; j < posList.size(); j++) {
                    NbtCompound posTag = posList.getCompound(j);
                    positions.add(new BlockPos(
                            posTag.getInt("X"),
                            posTag.getInt("Y"),
                            posTag.getInt("Z")
                    ));
                }

                vendorLinks.put(vendorId, positions);
            }
        }
    }

    public void linkBlock(UUID vendorId, BlockPos pos) {
        vendorLinks.computeIfAbsent(vendorId, k -> new HashSet<>()).add(pos);
        markDirty();
    }

    public static VendorLinkPersistentState get(MinecraftServer server) {
        return VendorLinkPersistentState.getOrCreate(server, TYPE, "vendor_links");
    }

    public boolean isBlockLinkedToVendor(UUID vendorId, BlockPos pos) {
        Set<BlockPos> positions = vendorLinks.get(vendorId);
        return positions != null && positions.contains(pos);
    }

    public void unlinkBlock(UUID vendorId, BlockPos pos) {
        Set<BlockPos> positions = vendorLinks.get(vendorId);
        if (positions != null) {
            positions.remove(pos);
            if (positions.isEmpty()) {
                vendorLinks.remove(vendorId);
            }
            markDirty();
        }
    }

    public Set<BlockPos> getVendorLinks(UUID vendorId) {
        return vendorLinks.getOrDefault(vendorId, Collections.emptySet());
    }

    protected static <T extends VendorLinkPersistentState> T getOrCreate(
            MinecraftServer server,
            Type<T> type,
            String name
    ) {
        if (server == null || server.getWorld(World.OVERWORLD) == null) return null;
        PersistentStateManager manager = Objects.requireNonNull(server.getWorld(World.OVERWORLD)).getPersistentStateManager();
        if (manager == null) return null;
        T state = manager.getOrCreate(type, name);
        state.markDirty();
        return state;
    }
}
