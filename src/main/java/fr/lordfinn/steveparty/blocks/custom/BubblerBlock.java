package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleRiding;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The Bubbler: a low blue plate (the Spawn Bubble's) with a grille, forever breathing out tiny bubbles. A player, a
 * mob or a dropped item stepping onto it is enclosed in a bubble ({@link BubbleEntity#enclose}; a sneaking player walks
 * over it) that rises two blocks a level of the redstone signal it gets ({@link #POWER}, 1 to 15: 2 to 30 blocks; none:
 * 30, see {@link BubbleRiding#launchHeight}) and hovers there. Its tiny bubbles rise just as high: they show where a bubble
 * stops (its block entity only draws them, BubblerRenderer).
 */
public class BubblerBlock extends BlockWithEntity {
    public static final MapCodec<BubblerBlock> CODEC = createCodec(BubblerBlock::new);
    /** The redstone signal it gets (kept in its state: the clients draw its tiny bubbles as high as it sends bubbles). */
    public static final IntProperty POWER = Properties.POWER;
    /** How high its plate is (blocks): a bubble starts there. */
    public static final double TOP = 4 / 16.0;
    private static final VoxelShape SHAPE = Block.createCuboidShape(0, 0, 0, 16, 4, 16);

    public BubblerBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(POWER, 0));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(POWER, Math.min(15, ctx.getWorld().getReceivedRedstonePower(ctx.getBlockPos())));
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
        if (world.isClient) return;
        int power = Math.min(15, world.getReceivedRedstonePower(pos));
        if (power != state.get(POWER)) world.setBlockState(pos, state.with(POWER, power), Block.NOTIFY_LISTENERS);
    }

    /** How high this Bubbler sends its bubbles (blocks over it). */
    public static int launchHeight(BlockState state) {
        return BubbleRiding.launchHeight(state.contains(POWER) ? state.get(POWER) : 0);
    }

    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (world instanceof ServerWorld serverWorld && entity.getY() < pos.getY() + 1) {
            BubbleEntity.enclose(serverWorld, entity, pos, launchHeight(state));
        }
    }

    /** Now and then, a gentle bubbling. */
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(8) == 0) {
            world.playSound(pos.getX() + 0.5, pos.getY() + TOP, pos.getZ() + 0.5, ModSounds.BUBBLER_BUBBLING, SoundCategory.BLOCKS,
                    0.25f + random.nextFloat() * 0.1f, 0.9f + random.nextFloat() * 0.3f, false);
        }
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new BubblerBlockEntity(pos, state);
    }
}
