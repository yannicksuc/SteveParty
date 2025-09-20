package fr.lordfinn.steveparty.utils;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.world.World;

public interface TickableBlockEntity {
    void tick();

    static <T extends BlockEntity> BlockEntityTicker<T> getTicker(World pWorld) {
        return (w, pos, state, blockEntity) -> {
            if (blockEntity instanceof TickableBlockEntity tickable) {
                tickable.tick();
            }
        };
    }
}