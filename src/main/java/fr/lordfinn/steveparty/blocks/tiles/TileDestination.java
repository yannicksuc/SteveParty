package fr.lordfinn.steveparty.blocks.tiles;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.math.BlockPos;

public record TileDestination(BlockPos position, boolean isTile) {

    @Override
    public String toString() {
        return "PositionWithType{" +
                "position=" + position +
                ", isTile=" + isTile +
                '}';
    }

    public NbtElement toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("x", position.getX());
        nbt.putInt("y", position.getY());
        nbt.putInt("z", position.getZ());
        nbt.putBoolean("isTile", isTile);
        return nbt;
    }

    public static TileDestination fromNbt(NbtCompound nbt) {
        return new TileDestination(new BlockPos(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z")), nbt.getBoolean("isTile"));
    }
}
