package fr.lordfinn.steveparty.service;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.Identifier;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MobOwnershipPersistentState extends PersistentState {

    private final Map<Identifier, UUID> mobOwners = new HashMap<>();

    public MobOwnershipPersistentState() {
        super();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        // Sauvegarder les données persistantes
        mobOwners.forEach((id, uuid) -> nbt.putUuid(id.toString(), uuid));
        return nbt;
    }

    public MobOwnershipPersistentState(NbtCompound nbt) {
        this();
        // Charger les données persistantes
        nbt.getKeys().forEach(key -> mobOwners.put(Identifier.of(key), nbt.getUuid(key)));
    }

    public void addOwner(Identifier entityId, UUID ownerUUID) {
        mobOwners.put(entityId, ownerUUID);
        markDirty(); // Signale que les données doivent être sauvegardées
    }

    public UUID getOwner(Identifier entityId) {
        return mobOwners.get(entityId);
    }
}
