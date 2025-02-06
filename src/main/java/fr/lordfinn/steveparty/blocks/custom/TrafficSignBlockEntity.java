package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.DyeColor;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import static net.minecraft.block.Block.NOTIFY_ALL;

public class TrafficSignBlockEntity extends BlockEntity {
    private DyeColor color = DyeColor.WHITE; // Default color
    private boolean isGlowing = false; // Default to not glowing
    private byte[] shape = null;

    public TrafficSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRAFFIC_SIGN_ENTITY, pos, state);
    }

    public int getRotation() {
        return getCachedState().get(TrafficSignBlock.ROTATION);
    }

    public DyeColor getColor() {
        return color;
    }

    public void setColor(DyeColor color) {
        this.color = color;
        updateListeners();
    }

    public boolean isGlowing() {
        return isGlowing;
    }

    public void setGlowing(boolean glowing) {
        this.isGlowing = glowing;
        updateListeners();
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        NbtCompound nbt = new NbtCompound();
        writeNbt(nbt, this.getWorld().getRegistryManager());
        return BlockEntityUpdateS2CPacket.create(this);
    }

    private void updateListeners() {
        this.markDirty();
        this.getWorld().updateListeners(this.getPos(), this.getCachedState(), this.getCachedState(), NOTIFY_ALL);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        if (this.shape != null)
            nbt.putByteArray("SymbolShape", this.shape);
        if (this.color != null)
            nbt.putString("Color", this.color.getName());
        nbt.putBoolean("IsGlowing", this.isGlowing);
        super.writeNbt(nbt, registries);
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("SymbolShape", NbtElement.BYTE_ARRAY_TYPE))
            this.shape = nbt.getByteArray("SymbolShape");
        if (nbt.contains("Color", NbtElement.STRING_TYPE))
            this.color = DyeColor.byName(nbt.getString("Color"), DyeColor.WHITE);
        if (nbt.contains("IsGlowing"))
            this.isGlowing = nbt.getBoolean("IsGlowing");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        NbtCompound nbt = new NbtCompound();
        this.writeNbt(nbt, registries);
        return nbt;
    }

    public void setShape(byte[] shape) {
        this.shape = shape;
        updateListeners();
    }

    public byte[] getShape() {
        return shape;
    }
}