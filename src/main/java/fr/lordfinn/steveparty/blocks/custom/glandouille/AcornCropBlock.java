package fr.lordfinn.steveparty.blocks.custom.glandouille;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleSpawns;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

/**
 * A planted acorn (the Acorn item on farmland): it grows like a crop, 4 stages; ripe, it hatches into a Glandouille
 * (the block is gone, the Glandouille stands there) of its biome's kind ({@link GlandouilleSpawns#sproutVariant}).
 * Barely sprouted, it may pop out of the ground at once instead, as a young one ({@link #earlyPopChance}).
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
        Vec3d offset = state.getModelOffset(world, pos);
        return SHAPES[Math.min(MAX_AGE, state.get(AGE))].offset(offset.x, offset.y, offset.z);
    }

    /** Planted acorns stand a little off the middle of their block, less than flowers do. */
    @Override
    public float getMaxHorizontalModelOffset() {
        return 0.12f;
    }

    /** A crop stops ticking once ripe; this one goes on: ripe, it hatches. */
    @Override
    protected boolean hasRandomTicks(BlockState state) {
        return true;
    }

    /** One dose of bone meal in three hatches a ripe acorn (each dose is used up all the same). */
    public static final int HATCH_DOSES = 3;

    /** Bone meal always takes: one stage per dose while it grows; ripe, it may hatch. */
    @Override
    public boolean isFertilizable(WorldView world, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean canGrow(World world, Random random, BlockPos pos, BlockState state) {
        return !isMature(state) || random.nextInt(HATCH_DOSES) == 0;
    }

    /** Exactly one stage per dose of bone meal (no jump to ripe). */
    @Override
    protected int getGrowthAmount(World world) {
        return 1;
    }

    /** Chance that a sprout leaving its first stage pops out of the ground at once, as a young one (tests change it). */
    public static float earlyPopChance = 0.05f;

    @Override
    public void grow(ServerWorld world, Random random, BlockPos pos, BlockState state) {
        if (isMature(state)) {
            hatch(world, pos);
            return;
        }
        super.grow(world, random, pos, state);
        popEarly(world, random, pos, state);
    }

    /** Ripe: it hatches (one random tick in two); else it grows like any crop. */
    @Override
    protected void randomTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (isMature(state)) {
            if (random.nextBoolean()) hatch(world, pos);
            return;
        }
        super.randomTick(state, world, pos, random);
        popEarly(world, random, pos, state);
    }

    /** {@code before} just grew out of its first stage: it may pop out at once, as a young one (whatever the biome). */
    private void popEarly(ServerWorld world, Random random, BlockPos pos, BlockState before) {
        if (getAge(before) != 0) return;
        BlockState now = world.getBlockState(pos);
        if (!now.isOf(this) || getAge(now) != 1 || random.nextFloat() >= earlyPopChance) return;
        hatch(world, pos, GlandouilleVariant.YOUNG);
    }

    /** The ripe acorn at {@code pos} hatches into a Glandouille of its biome's kind. */
    public static @Nullable GlandouilleEntity hatch(ServerWorld world, BlockPos pos) {
        return hatch(world, pos, GlandouilleSpawns.sproutVariant(world, pos));
    }

    /**
     * The acorn at {@code pos} becomes a {@code variant} Glandouille (with its cap), asleep (by itself or after the last
     * bone meal). Returns it, or null if it failed.
     */
    public static @Nullable GlandouilleEntity hatch(ServerWorld world, BlockPos pos, GlandouilleVariant variant) {
        GlandouilleEntity hatched = ModEntities.GLANDOUILLE.create(world);
        if (hatched == null) return null;
        world.removeBlock(pos, false);
        hatched.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, world.random.nextFloat() * 360f, 0);
        hatched.initialize(world, world.getLocalDifficulty(pos), SpawnReason.BREEDING, null);
        hatched.setVariant(variant);
        hatched.setHat(true);
        hatched.setPersistent();
        world.spawnEntity(hatched);
        hatched.hatchAsleep();
        world.playSound(null, pos, ModSounds.GLANDOUILLE_HATCH, SoundCategory.BLOCKS, 1f, 1f);
        world.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 10, 0.3, 0.3, 0.3, 0);
        return hatched;
    }
}
