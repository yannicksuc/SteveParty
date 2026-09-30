package fr.lordfinn.steveparty.service;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

/**
 * What {@link TokenService} remembers of a token: its owner. A party's stars and coins are not stored per token: they
 * are the items the owner holds (see {@code PartyCurrency}).
 */
public class TokenData {
    private UUID ownerUuid;

    public TokenData(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public TokenData() {
    }

    public TokenData(NbtCompound compound) {
        fromNbt(compound);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("OwnerUUID")) {
            this.ownerUuid = nbt.getUuid("OwnerUUID");
        }
    }

    public NbtCompound toNbt(NbtCompound nbt) {
        if (ownerUuid != null) {
            nbt.putUuid("OwnerUUID", ownerUuid);
        }
        return nbt;
    }

    public void writeToPacket(PacketByteBuf buf) {
        buf.writeUuid(ownerUuid);
    }

    public static TokenData fromBuf(PacketByteBuf buf) {
        return new TokenData(buf.readUuid());
    }
}
