package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.utils.GravityPull;
import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** A placed gravity core pulls what is around it like the forge core does, with a fixed reach and strength. */
public class GravityCoreBlockEntity extends BlockEntity implements TickableBlockEntity {
    /** Reach (blocks) and pull (blocks per tick²): the same everywhere within reach, it does not move. */
    public static final double RANGE = 8, STRENGTH = 0.15;
    /** Radius of the orbit what it pulls ends up circling on (blocks). */
    public static final double ORBIT = 2;

    public GravityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GRAVITY_CORE_ENTITY, pos, state);
    }

    @Override
    public void tick() {
        if (world == null) return;
        // Center of the 8x8x8 core, in the upper half of the block
        Vec3d center = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5);
        GravityPull.pullAround(world, center, RANGE, STRENGTH, false, ORBIT);
    }
}
