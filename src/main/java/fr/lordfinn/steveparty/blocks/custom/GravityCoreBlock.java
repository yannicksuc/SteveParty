package fr.lordfinn.steveparty.blocks.custom;

import fr.lordfinn.steveparty.utils.TickableBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;


/**
 * GravityCoreBlock
 *
 * Epic block that pulls nearby entities like a small planet ({@link GravityCoreBlockEntity}, same pull as the dice
 * forge core, with a fixed reach) and emits particles in a swirling circular pattern.
 */
public class GravityCoreBlock extends Block implements BlockEntityProvider {

    // --- Block shape ---
    public static final VoxelShape SHAPE = Block.createCuboidShape(
            4.0D, 8.0D, 4.0D,
            12.0D, 16.0D, 12.0D
    );

    public GravityCoreBlock(Settings settings) {
        super(settings);
    }

    // --- Block shapes for collision and outline ---
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    // --- Client-side: particle effects ---
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextFloat() < 0.3F) { // 10% chance per tick
            spawnCircleParticles(world, pos, random, 0.1D);
        }
    }

    /**
     * Spawn 8 particles in a circle perpendicular to a random normal vector.
     */
    private void spawnCircleParticles(World world, BlockPos pos, Random random, double speed) {
        Vec3d center = getBlockCenter(pos);
        Vec3d normal = generateRandomNormal(random);
        Vec3d[] planeVectors = getPerpendicularVectors(normal);

        spawnParticlesInCircle(world, center, planeVectors[0], planeVectors[1], speed, 8);
    }

    /**
     * Returns the center coordinates of the block shape.
     */
    private Vec3d getBlockCenter(BlockPos pos) {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + 0.75, pos.getZ() + 0.5);
    }

    /**
     * Generates a random unit vector on the sphere (random normal).
     */
    private Vec3d generateRandomNormal(Random random) {
        double theta = random.nextDouble() * 2 * Math.PI; // azimuth
        double phi = random.nextDouble() * Math.PI;       // elevation
        return new Vec3d(
                Math.sin(phi) * Math.cos(theta),
                Math.cos(phi),
                Math.sin(phi) * Math.sin(theta)
        );
    }

    /**
     * Returns two perpendicular vectors forming a plane perpendicular to the normal.
     */
    private Vec3d[] getPerpendicularVectors(Vec3d normal) {
        Vec3d u = (Math.abs(normal.y) < 0.99)
                ? new Vec3d(-normal.z, 0, normal.x).normalize()
                : new Vec3d(1, 0, 0);
        Vec3d v = normal.crossProduct(u).normalize();
        return new Vec3d[]{u, v};
    }

    /**
     * Spawns a given number of particles in a circle on the plane defined by u and v vectors.
     */
    private void spawnParticlesInCircle(World world, Vec3d center, Vec3d u, Vec3d v, double speed, int count) {
        for (int i = 0; i < count; i++) {
            double angle = 2 * Math.PI * i / count;
            Vec3d velocity = u.multiply(Math.cos(angle) * speed).add(v.multiply(Math.sin(angle) * speed));

            world.addParticle(ParticleTypes.END_ROD,
                    center.x, center.y, center.z,
                    velocity.x, velocity.y, velocity.z);
        }
    }

    // --- Server-side: scheduled tick logic ---
    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);
        if (!world.isClient) {
            scheduleNextTick(world, pos);
        }
    }

    private void scheduleNextTick(World world, BlockPos pos) {
        world.scheduleBlockTick(pos, this, 20); // 1 second later
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        playAmbientSounds(world, pos, random);
        scheduleNextTick(world, pos);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new GravityCoreBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return TickableBlockEntity.getTicker(world);
    }

    /**
     * Play ambient sounds occasionally to enhance the epic effect.
     */
    private void playAmbientSounds(ServerWorld world, BlockPos pos, Random random) {
        if (random.nextFloat() < 0.5F) {
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.BLOCKS, 0.8F, 0.5F + random.nextFloat() * 0.5F);
        }
        if (random.nextFloat() < 0.2F) {
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT,
                    SoundCategory.BLOCKS, 0.4F, 0.5F + random.nextFloat() * 0.3F);
        }
    }
}
