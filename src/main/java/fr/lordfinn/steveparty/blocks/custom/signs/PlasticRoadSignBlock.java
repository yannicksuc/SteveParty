package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Plastic road sign on a metal pole: a round, square, diamond, star or heart plate of one of the 16 plastic
 * colours, turning in 16 directions. Stencils paint on the plate (tilted by 45° on the diamond). The wrench changes
 * the plate's shape.
 */
public class PlasticRoadSignBlock extends AbstractStencilSignBlock {
    public static final MapCodec<PlasticRoadSignBlock> CODEC = createCodec(PlasticRoadSignBlock::new);
    public static final EnumProperty<Plate> PLATE = EnumProperty.of("plate", Plate.class);

    public enum Plate implements StringIdentifiable {
        ROUND("round"), SQUARE("square"), DIAMOND("diamond"), STAR("star"), HEART("heart");

        private final String name;

        Plate(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return name;
        }

        public Plate next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    // Model space (pixels, front facing north)
    private static final VoxelShape[] SHAPES = SignShapes.rotations(
            new SignShapes.Box(7, 0, 8, 9, 24, 10),
            new SignShapes.Box(1, 12, 7, 15, 26, 8));

    public PlasticRoadSignBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.getDefaultState().with(PLATE, Plate.ROUND));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(PLATE);
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos below = pos.down();
        return world.getBlockState(below).isSideSolid(world, below, Direction.UP, net.minecraft.block.SideShapeType.CENTER);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPES[state.get(ROTATION)];
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                         Hand hand, BlockHitResult hit) {
        if (hand == Hand.MAIN_HAND && stack.isOf(ModItems.WRENCH)) {
            if (!world.isClient) {
                world.setBlockState(pos, state.with(PLATE, state.get(PLATE).next()), Block.NOTIFY_ALL);
                world.playSound(null, pos, SoundEvents.BLOCK_BAMBOO_WOOD_HIT, SoundCategory.BLOCKS, 1.0F, 1.2F);
            }
            return ActionResult.SUCCESS;
        }
        return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
    }

    @Override
    public ItemStack getPickStack(WorldView world, BlockPos pos, BlockState state) {
        ItemStack stack = super.getPickStack(world, pos, state);
        stack.set(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT.with(PLATE, state.get(PLATE)));
        return stack;
    }
}
