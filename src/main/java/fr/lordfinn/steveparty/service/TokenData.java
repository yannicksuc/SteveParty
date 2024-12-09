package fr.lordfinn.steveparty.service;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;

import java.util.UUID;

public class TokenData {
    private UUID ownerUuid;
    private int coin;
    private int star;

    // Constructor
    public TokenData(UUID ownerUuid, int coin, int star) {
        this.ownerUuid = ownerUuid;
        this.coin = coin;
        this.star = star;
    }

    // Default constructor
    public TokenData() {
    }

    public TokenData(NbtCompound compound) {
        fromNbt(compound);
    }
    // Getters and Setters
    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public void setOwnerUuid(UUID ownerUuid) {
        this.ownerUuid = ownerUuid;
    }

    public int getCoin() {
        return coin;
    }

    public void setCoin(int coin) {
        this.coin = coin;
    }

    public int getStar() {
        return star;
    }

    public void setStar(int star) {
        this.star = star;
    }

    /**
     * Populates the TokenData instance from an NBT tag.
     *
     * @param nbt the NBT data
     */
    public void fromNbt(NbtCompound nbt) {
        if (nbt.contains("OwnerUUID")) {
            this.ownerUuid = nbt.getUuid("OwnerUUID");
        }
        this.coin = nbt.getInt("Coin");
        this.star = nbt.getInt("Star");
    }

    /**
     * Saves the TokenData instance to an NBT tag.
     *
     * @param nbt the NBT tag to populate
     * @return the populated NBT tag
     */
    public NbtCompound toNbt(NbtCompound nbt) {
        if (ownerUuid != null) {
            nbt.putUuid("OwnerUUID", ownerUuid);
        }
        nbt.putInt("Coin", coin);
        nbt.putInt("Star", star);
        return nbt;
    }

    public void writeToPacket(PacketByteBuf buf) {
        buf.writeUuid(ownerUuid);
        buf.writeInt(coin);
        buf.writeInt(star);
    }

    public static TokenData fromBuf(PacketByteBuf buf) {
        TokenData token = new TokenData();
        token.setOwnerUuid(buf.readUuid());
        token.setCoin(buf.readInt());
        token.setStar(buf.readInt());
        return token;
    }
}

