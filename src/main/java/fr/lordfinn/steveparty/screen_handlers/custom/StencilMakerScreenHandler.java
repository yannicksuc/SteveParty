package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.StencilMakerBlockEntity;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
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
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return ScreenHandlerChecks.canUseBlockEntity(this.blockEntity, player);
    }

    public StencilMakerBlockEntity getBlockEntity() {
        return blockEntity;
    }
}
