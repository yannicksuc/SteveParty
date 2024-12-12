package fr.lordfinn.steveparty.blocks;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.MiniGamesCatalogue;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.ComponentMap;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

import static fr.lordfinn.steveparty.components.ModComponents.CATALOGUE;

public class PartyControllerEntity extends BlockEntity {
    public ItemStack catalogue = ItemStack.EMPTY;
    public long lastTime = 0;
    public PartyControllerEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PARTY_CONTROLLER_ENTITY, pos, state);
    }

    @Override
    protected void readComponents(ComponentsAccess components) {
        super.readComponents(components);
        this.catalogue = components.getOrDefault(CATALOGUE, ItemStack.EMPTY);
    }

    @Override
    protected void addComponents(ComponentMap.Builder componentMapBuilder) {
        super.addComponents(componentMapBuilder);
        componentMapBuilder.add(CATALOGUE, this.catalogue);
    }

    @Override
    public void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        if (catalogue != null && !catalogue.isEmpty()) {
            NbtElement item = catalogue.toNbt(wrapper, new NbtCompound());
            nbt.put("catalogue", item);
            nbt.putBoolean("isCatalogued", true);
        } else {
            nbt.putBoolean("isCatalogued", false);
        }
        super.writeNbt(nbt, wrapper);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    @Override
    public void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup wrapper) {
        super.readNbt(nbt, wrapper);
        Steveparty.LOGGER.info("READ NBT");
        try {
            Optional<ItemStack> socketedStoryNbt = ItemStack.fromNbt(wrapper, nbt.get("catalogue"));
            socketedStoryNbt.ifPresentOrElse(stack -> catalogue = stack, () -> catalogue = ItemStack.EMPTY);
            boolean isCatalogued = nbt.getBoolean("isCatalogued");
            if (!isCatalogued)
                catalogue = ItemStack.EMPTY;
        } catch (Exception e) {
            Steveparty.LOGGER.error("Failed to read NBT", e);
        }
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public void markDirty() {
        if (this.world != null && !this.world.isClient && this.world instanceof ServerWorld) {
            this.world.updateListeners(this.pos, this.getCachedState(), this.getCachedState(), 3);
        }
        super.markDirty();
    }

    public boolean setCatalogue(ItemStack itemStack) {
        if (world == null || world.isClient) return !catalogue.isEmpty();

        if (!catalogue.isEmpty()) {
            Entity holder = itemStack.getHolder();
            if (holder instanceof ServerPlayerEntity player) {
                player.giveOrDropStack(catalogue);
            } else {
                ItemScatterer.spawn(world, pos.getX() + 0.5f, pos.getY() + 0.5f, pos.getZ() + 0.5f, catalogue);
            }
        }
        if (itemStack.isEmpty() || itemStack.getItem() instanceof MiniGamesCatalogue)
            catalogue = itemStack.copy();
        this.markDirty();
        return !catalogue.isEmpty();
    }
}
