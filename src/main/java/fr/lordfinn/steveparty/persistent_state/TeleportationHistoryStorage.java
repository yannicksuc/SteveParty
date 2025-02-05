package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TeleportationHistoryStorage extends PersistentState {
    public static final Type<TeleportationHistoryStorage> TYPE =
            new Type<>(
                    TeleportationHistoryStorage::new,
                    TeleportationHistoryStorage::readNbt,
                    DataFixTypes.LEVEL
            );

    private final Map<UUID, TeleportationRecord> teleportationHistory = new HashMap<>();

    public void addTeleportation(UUID player, BlockPos fromPos, BlockPos toPos) {
        teleportationHistory.put(player, new TeleportationRecord(fromPos, toPos));
        markDirty();
    }

    public void removeTeleportation(UUID player) {
        teleportationHistory.remove(player);
        markDirty();
    }

    public TeleportationRecord get(UUID player) {
        return teleportationHistory.get(player);
    }

    public List<UUID> get(BlockPos pos) {
        return teleportationHistory.entrySet().stream().filter(entry -> entry.getValue().fromPos().equals(pos)).map(Map.Entry::getKey).toList();
    }


    public static TeleportationHistoryStorage readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        TeleportationHistoryStorage storage = new TeleportationHistoryStorage();

        NbtList tpList = nbt.getList("teleportationHistory", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < tpList.size(); i++) {
            NbtCompound tpData = tpList.getCompound(i);
            UUID playerUuid = UUID.fromString(tpData.getString("playerUuid"));
            BlockPos fromPos = new BlockPos(tpData.getInt("fromX"), tpData.getInt("fromY"), tpData.getInt("fromZ"));
            BlockPos toPos = new BlockPos(tpData.getInt("toX"), tpData.getInt("toY"), tpData.getInt("toZ"));
            storage.teleportationHistory.put(playerUuid, new TeleportationRecord(fromPos, toPos));
        }

        return storage;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList tpList = new NbtList();

        for (Map.Entry<UUID, TeleportationRecord> entry : teleportationHistory.entrySet()) {
            NbtCompound tpData = new NbtCompound();
            UUID playerUuid = entry.getKey();
            TeleportationRecord record = entry.getValue();

            tpData.putString("playerUuid", playerUuid.toString());

            // Save from position
            tpData.putInt("fromX", record.fromPos.getX());
            tpData.putInt("fromY", record.fromPos.getY());
            tpData.putInt("fromZ", record.fromPos.getZ());

            // Save to position
            tpData.putInt("toX", record.toPos.getX());
            tpData.putInt("toY", record.toPos.getY());
            tpData.putInt("toZ", record.toPos.getZ());

            tpList.add(tpData);
        }

        nbt.put("teleportationHistory", tpList);
        return nbt;
    }

    public record TeleportationRecord(BlockPos fromPos, BlockPos toPos) {
    }
}
