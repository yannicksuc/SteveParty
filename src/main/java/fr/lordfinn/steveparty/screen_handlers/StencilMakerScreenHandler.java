package fr.lordfinn.steveparty.screen_handlers;

import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class StencilMakerScreenHandler extends ScreenHandler {
    private final StencilMakerBlockEntity blockEntity;
    private final World world;
    private final BlockPos pos;

    public StencilMakerScreenHandler(int syncId, PlayerInventory playerInventory, StencilMakerBlockEntity blockEntity) {
        super(ModScreensHandlers.STENCIL_MAKER_SCREEN_HANDLER, syncId);
        this.blockEntity = blockEntity;
        this.world = blockEntity.getWorld();
        this.pos = blockEntity.getPos();
    }

    public StencilMakerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload blockPosPayload) {
        this(syncId, playerInventory, (StencilMakerBlockEntity) playerInventory.player.getWorld().getBlockEntity(blockPosPayload.pos()));
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return null;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        if (this.blockEntity.getWorld() == null) return false;
        return this.blockEntity.getWorld().getBlockEntity(pos) == this.blockEntity;
    }

    public StencilMakerBlockEntity getBlockEntity() {
        return blockEntity;
    }

    public void save(byte[] shape) {
        StencilItem.setShape(shape, blockEntity.getStencil());
        //Implement save using CustomPayload
    }
}
