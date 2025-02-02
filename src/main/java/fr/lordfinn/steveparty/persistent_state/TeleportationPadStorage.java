package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TeleportationPadStorage extends PersistentState {
    public static final PersistentState.Type<TeleportationPadStorage> TYPE =
            new PersistentState.Type<>(
                    TeleportationPadStorage::new,
                    TeleportationPadStorage::readNbt,
                    DataFixTypes.LEVEL
            );

    private final Map<BlockPos, ItemStack> teleportationPads = new HashMap<>();

    public void addTeleportationPad(BlockPos pos, ItemStack book) {
        teleportationPads.put(pos, book);
        markDirty(); // Marks the state as dirty to ensure it gets saved
    }

    public void removeTeleportationPad(BlockPos pos) {
        teleportationPads.remove(pos);
        markDirty();
    }

    public ItemStack getTeleportationPadBook(BlockPos pos) {
        return teleportationPads.get(pos);
    }

    public static TeleportationPadStorage readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        TeleportationPadStorage storage = new TeleportationPadStorage();

        NbtList padList = nbt.getList("teleportationPads", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < padList.size(); i++) {
            NbtCompound padData = padList.getCompound(i);

            // Read BlockPos
            BlockPos pos = new BlockPos(padData.getInt("x"), padData.getInt("y"), padData.getInt("z"));

            // Read ItemStack
            Optional<ItemStack> book = ItemStack.fromNbt(registries, padData.getCompound("book"));

            book.ifPresent(itemStack -> storage.teleportationPads.put(pos, itemStack));
        }

        return storage;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        NbtList padList = new NbtList();

        for (Map.Entry<BlockPos, ItemStack> entry : teleportationPads.entrySet()) {
            NbtCompound padData = new NbtCompound();
            BlockPos pos = entry.getKey();
            ItemStack book = entry.getValue();

            // Save BlockPos
            padData.putInt("x", pos.getX());
            padData.putInt("y", pos.getY());
            padData.putInt("z", pos.getZ());

            // Save ItemStack
            padData.put("book", book.toNbt(registries));

            padList.add(padData);
        }

        nbt.put("teleportationPads", padList);
        return nbt;
    }
}
