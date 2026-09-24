package fr.lordfinn.steveparty.blocks.custom.signs;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.stencil.StencilShape;
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
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

/**
 * Plastic road sign: a round, square, diamond, star or heart plate of one of the 16 plastic colours on top of any
 * fence or wall ({@link SignPosts}, plastic fences included), turning in 16 directions. Every plate fills the 16x16
 * pixel grid (the diamond is the square turned by 45°). Stencils paint on the plate. The wrench changes the plate.
 */
public class PlasticRoadSignBlock extends AbstractStencilSignBlock {
    public static final MapCodec<PlasticRoadSignBlock> CODEC = createCodec(PlasticRoadSignBlock::new);
    public static final EnumProperty<Plate> PLATE = EnumProperty.of("plate", Plate.class);

    public enum Plate implements StringIdentifiable {
        ROUND("round"), SQUARE("square"), DIAMOND("diamond"), STAR("star"), HEART("heart");

        private final String name;
        private byte[] mask;

        Plate(String name) {
            this.name = name;
        }

        /** @return the plate's pixels on the 16x16 grid, as a stencil shape (the diamond is a square, turned when drawn). */
        public byte[] mask() {
            if (mask == null) {
                mask = switch (this) {
                    case ROUND -> StencilShape.fromRows(
                            ".....######.....",
                            "...##########...",
                            "..############..",
                            ".##############.",
                            ".##############.",
                            "################",
                            "################",
                            "################",
                            "################",
                            "################",
                            "################",
                            ".##############.",
                            ".##############.",
                            "..############..",
                            "...##########...",
                            ".....######.....");
                    case SQUARE, DIAMOND -> StencilShape.full();
                    case STAR -> StencilShape.fromRows(
                            "......####......",
                            "......####......",
                            ".....######.....",
                            ".....######.....",
                            "################",
                            "################",
                            ".##############.",
                            "..############..",
                            "...##########...",
                            "...##########...",
                            "..############..",
                            "..#####..#####..",
                            ".#####....#####.",
                            ".####......####.",
                            "####........####",
                            "###..........###");
                    case HEART -> StencilShape.fromRows(
                            "................",
                            ".#####....#####.",
                            "#######..#######",
                            "################",
                            "################",
                            "################",
                            "################",
                            ".##############.",
                            ".##############.",
                            "..############..",
                            "...##########...",
                            "....########....",
                            ".....######.....",
                            "......####......",
                            ".......##.......",
                            "................");
                };
            }
            return mask.clone();
        }

        /** @return true if the plate is drawn turned by 45° (the diamond). */
        public boolean turned() {
            return this == DIAMOND;
        }

        @Override
        public String asString() {
            return name;
        }

        public Plate next() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    /** Plate in model space (pixels, front facing north), in front of the post. */
    public static final float PLATE_Z = 3, PLATE_DEPTH = 1;
    private static final SignShapes.Box PLATE_BOX = new SignShapes.Box(0, 0, PLATE_Z, 16, 16, PLATE_Z + PLATE_DEPTH);
    private static final SignShapes.BoardOutline OUTLINE = new SignShapes.BoardOutline(PLATE_BOX);

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
    public boolean hangsOnPosts() {
        return true;
    }

    @Override
    public float boardBack() {
        return (float) (PLATE_Z + PLATE_DEPTH);
    }

    @Override
    protected boolean canStandAt(WorldView world, BlockPos pos) {
        return SignPosts.standsOnPost(world, pos);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return OUTLINE.get(this, world, pos, state);
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
