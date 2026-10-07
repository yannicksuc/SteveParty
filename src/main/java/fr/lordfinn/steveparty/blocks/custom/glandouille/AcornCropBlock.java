package fr.lordfinn.steveparty.blocks.custom.glandouille;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleVariant;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.CropBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.SpawnReason;
import net.minecraft.item.ItemConvertible;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

/**
 * A planted acorn (the Acorn item on farmland): it grows like a crop, 4 stages; ripe, it hatches into a young
 * Glandouille (the block is gone, the Glandouille stands there).
 */
public class AcornCropBlock extends CropBlock {
    public static final MapCodec<AcornCropBlock> CODEC = createCodec(AcornCropBlock::new);
    public static final int MAX_AGE = 3;
    public static final IntProperty AGE = Properties.AGE_3;
    private static final VoxelShape[] SHAPES = {
            Block.createCuboidShape(5, 0, 5, 11, 5, 11),
            Block.createCuboidShape(5, 0, 5, 11, 9, 11),
            Block.createCuboidShape(4, 0, 4, 12, 13, 12),
            Block.createCuboidShape(3, 0, 3, 13, 11, 13)};

    public AcornCropBlock(Settings settings) {
        super(settings);
    }

    @Override
    public MapCodec<? extends CropBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected IntProperty getAgeProperty() {
        return AGE;
    }

    @Override
    public int getMaxAge() {
        return MAX_AGE;
    }

    @Override
    protected ItemConvertible getSeedsItem() {
        return ModItems.ACORN;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(AGE);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        // Shifted a little within its block, like grass and flowers (see the block's offset settings)
        net.minecraft.util.math.Vec3d offset = state.getModelOffset(world, pos);
        return SHAPES[Math.min(MAX_AGE, state.get(AGE))].offset(offset.x, offset.y, offset.z);
    }

    /** Planted acorns stand a little off the middle of their block, less than flowers do. */
    @Override
    public float getMaxHorizontalModelOffset() {
        return 0.12f;
    }

    /** Ripe: it hatches (one random tick in two); else it grows like any crop. */
    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (isMature(state)) {
            if (random.nextBoolean()) hatch(world, pos);
            return;
        }
        super.randomTick(state, world, pos, random);
    }

    /**
     * The ripe acorn at {@code pos} becomes a young Glandouille (with its cap), asleep (by itself or after the last bone
     * meal). Returns it, or null if it failed.
     */
    public static @Nullable GlandouilleEntity hatch(ServerWorld world, BlockPos pos) {
        GlandouilleEntity young = ModEntities.GLANDOUILLE.create(world);
        if (young == null) return null;
        world.removeBlock(pos, false);
        young.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
        young.initialize(world, world.getLocalDifficulty(pos), SpawnReason.BREEDING, null);
        young.setVariant(GlandouilleVariant.YOUNG);
        young.setHat(true);
        young.setPersistent();
        world.spawnEntity(young);
        young.hatchAsleep();
        world.playSound(null, pos, ModSounds.GLANDOUILLE_HATCH, SoundCategory.BLOCKS, 1f, 1f);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0);
        return young;
    }
}
