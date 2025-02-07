package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.StencilMakerScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class StencilMakerBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPosPayload> {
    ItemStack stencil = ItemStack.EMPTY;
    boolean stencilIn = false;
    public StencilMakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STENCIL_MAKER_ENTITY, pos, state);
    }

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        if (stencil != null && !stencil.isEmpty()) {
            NbtElement stencilNbt = stencil.toNbt(registries);
            nbt.put("stencil", stencilNbt);
        }
        nbt.putBoolean("stencilIn", (stencil != null && !stencil.isEmpty()));
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        if (nbt.contains("stencil", NbtElement.COMPOUND_TYPE)) {
            ItemStack.fromNbt(registries, nbt.get("stencil")).ifPresent(itemStack -> this.stencil = itemStack);
        }
        this.stencilIn = nbt.getBoolean("stencilIn");
        if (!stencilIn)
            stencil = ItemStack.EMPTY;
    }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registryLookup) {
        return createNbt(registryLookup);
    }

    public void swapStencil(ItemStack itemStack, PlayerEntity player) {
        if ((itemStack == null || itemStack.isEmpty()) && player.getMainHandStack().isEmpty()) {
            player.getInventory().setStack(EquipmentSlot.MAINHAND.getIndex(), this.stencil.copy());
            this.stencil = ItemStack.EMPTY;
            stencilIn = false;
            this.markDirty();
        } else if (itemStack != null && !itemStack.isEmpty() && itemStack.getItem() instanceof StencilItem) {
            this.stencil = itemStack.copy();
            player.getMainHandStack().setCount(0);
            stencilIn = true;
            this.markDirty();
        }
    }

    public ItemStack getStencil() {
        return this.stencil;
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("steveparty.block.stencil_maker");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new StencilMakerScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public BlockPosPayload getScreenOpeningData(ServerPlayerEntity serverPlayerEntity) {
        return new BlockPosPayload(this.pos);
    }
}
