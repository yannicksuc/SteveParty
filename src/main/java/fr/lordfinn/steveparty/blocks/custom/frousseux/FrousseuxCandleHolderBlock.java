package fr.lordfinn.steveparty.blocks.custom.frousseux;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.IntProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A tamed Frousseux turned into a candle holder: a decoration that gives light. It is the Frousseux itself, asleep:
 * its whole identity is kept in the block entity ({@link FrousseuxCandleHolderBlockEntity}), drawn by its block
 * entity renderer as itself, facing whoever put it there, its flame as it was.
 * <ul>
 *     <li><b>Light</b>: its flame's ({@link #LIGHT}, from its health: 15, 12, 9 or 6, as the Frousseux's own).</li>
 *     <li><b>Right-click with an empty hand</b>: it wakes up, the same Frousseux (same UUID, name, owner, health),
 *     and follows its owner again. Holding an item, the item is used as usual (blocks are placed against it).</li>
 *     <li><b>Broken</b>: it drops as its item, which keeps it (block entity data); placed, it is back as it was.
 *     Broken in creative, it drops too, like a shulker box: never lost.</li>
 * </ul>
 */
public class FrousseuxCandleHolderBlock extends BlockWithEntity {
    public static final MapCodec<FrousseuxCandleHolderBlock> CODEC = createCodec(FrousseuxCandleHolderBlock::new);
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final IntProperty LIGHT = Properties.LEVEL_15;
    /** Its body and hands, sitting on the floor of its block (the flame left out). */
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 9, 13);
    /** Entity data that says where it was, not who it is: left out of the kept data. */
    private static final String[] WHERE = {"Pos", "Motion", "Rotation", "FallDistance", "Fire", "OnGround", "Light",
            "LightLevel", "Sitting", "Air", "PortalCooldown", "Leash", "HurtTime", "DeathTime", "HurtByTimestamp"};

    public FrousseuxCandleHolderBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(LIGHT, FrousseuxEntity.Flame.FULL.light));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIGHT);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    /** Drawn by its block entity renderer (the Frousseux model). */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FrousseuxCandleHolderBlockEntity(pos, state);
    }

    /** Placed from its item: facing the player, its light from the flame it kept. */
    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite())
                .with(LIGHT, FrousseuxCandleHolderBlockEntity.flameOf(keptIn(ctx.getStack())).light);
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    // ---------------------------------------------------------------- from and back to the Frousseux

    /** Its entity data, without where it was. */
    public static NbtCompound keptData(FrousseuxEntity frousseux) {
        NbtCompound data = new NbtCompound();
        frousseux.writeNbt(data);
        for (String key : WHERE) data.remove(key);
        return data;
    }

    /** Where it may sleep: where it floats, else just above (air, or what is replaceable there; never a liquid). */
    public static @Nullable BlockPos spotFor(World world, FrousseuxEntity frousseux) {
        BlockPos at = BlockPos.ofFloored(frousseux.getX(), frousseux.getY() + 0.1, frousseux.getZ());
        for (BlockPos pos : new BlockPos[]{at, at.up()}) {
            BlockState here = world.getBlockState(pos);
            if (here.isReplaceable() && here.getFluidState().isEmpty() && world.isInBuildLimit(pos)) return pos;
        }
        return null;
    }

    /**
     * Turns {@code frousseux} into a candle holder, facing {@code player}. False (a puff of smoke) if there is no
     * room for it.
     */
    public static boolean fallAsleep(FrousseuxEntity frousseux, ServerWorld world, PlayerEntity player) {
        BlockPos pos = spotFor(world, frousseux);
        if (pos == null) {
            world.spawnParticles(ParticleTypes.SMOKE, frousseux.getX(), frousseux.getBodyY(0.6), frousseux.getZ(), 5, 0.15, 0.15, 0.15, 0.01);
            return false;
        }
        NbtCompound data = keptData(frousseux);
        int light = frousseux.getFlame().light;
        frousseux.discard(); // its moving light goes with it
        BlockState state = ModBlocks.FROUSSEUX_CANDLE_HOLDER.getDefaultState()
                .with(FACING, player.getHorizontalFacing().getOpposite()).with(LIGHT, light);
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        if (world.getBlockEntity(pos) instanceof FrousseuxCandleHolderBlockEntity holder) holder.setFrousseux(data);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.4, z = pos.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.WAX_ON, x, y, z, 8, 0.25, 0.25, 0.25, 0.5);
        world.spawnParticles(ParticleTypes.SMALL_FLAME, x, y + 0.4, z, 4, 0.05, 0.05, 0.05, 0.01);
        world.playSound(null, pos, SoundEvents.BLOCK_CANDLE_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    /** Wakes the Frousseux up where its candle holder was, as it was; the block goes. */
    public static @Nullable FrousseuxEntity wakeUp(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof FrousseuxCandleHolderBlockEntity holder)) return null;
        FrousseuxEntity frousseux = ModEntities.FROUSSEUX.create(world);
        if (frousseux == null) return null;
        NbtCompound data = holder.getFrousseux();
        if (!data.isEmpty()) frousseux.readNbt(data);
        else frousseux.setColor(FrousseuxColor.PLAIN); // a candle holder that never held one: a wild plain one
        // its UUID taken meanwhile (a copied item placed twice): a new one, the rest kept
        if (world.getEntity(frousseux.getUuid()) != null) frousseux.setUuid(UUID.randomUUID());
        float yaw = world.getBlockState(pos).get(FACING).asRotation();
        frousseux.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5, yaw, 0);
        frousseux.setHeadYaw(yaw);
        frousseux.setBodyYaw(yaw);
        frousseux.setSitting(false);
        world.removeBlock(pos, false);
        world.spawnEntity(frousseux);
        world.spawnParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.5);
        world.playSound(null, pos, ModSounds.FROUSSEUX_RELIGHT, SoundCategory.NEUTRAL, 1.0f, 1.2f);
        return frousseux;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world instanceof ServerWorld server) wakeUp(server, pos);
        return ActionResult.success(world.isClient);
    }

    /** An item in hand keeps its own use (a block is placed against it); only an empty hand wakes it. */
    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player,
                                             Hand hand, BlockHitResult hit) {
        return stack.isEmpty() ? ItemActionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION : ItemActionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    // ---------------------------------------------------------------- its item

    /** The Frousseux an item keeps, or an empty compound. */
    public static NbtCompound keptIn(ItemStack stack) {
        NbtComponent data = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        return data == null ? new NbtCompound() : data.copyNbt().getCompound(FrousseuxCandleHolderBlockEntity.KEY);
    }

    /** Its item, keeping the Frousseux (placed, the block entity reads it back: vanilla block entity data). */
    public static ItemStack itemOf(FrousseuxCandleHolderBlockEntity holder) {
        ItemStack stack = new ItemStack(ModBlocks.FROUSSEUX_CANDLE_HOLDER);
        if (holder.getFrousseux().isEmpty()) return stack;
        NbtCompound data = new NbtCompound();
        data.putString("id", Registries.BLOCK_ENTITY_TYPE.getId(ModBlockEntities.FROUSSEUX_CANDLE_HOLDER).toString());
        data.put(FrousseuxCandleHolderBlockEntity.KEY, holder.getFrousseux().copy());
        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(data));
        return stack;
    }

    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        List<ItemStack> drops = super.getDroppedStacks(state, builder);
        if (builder.getOptional(LootContextParameters.BLOCK_ENTITY) instanceof FrousseuxCandleHolderBlockEntity holder) {
            drops.replaceAll(drop -> drop.isOf(asItem()) ? itemOf(holder) : drop);
        }
        return drops;
    }

    /** Broken in creative, it drops all the same (a Frousseux is never lost), like a shulker box. */
    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && player.isCreative() && world.getBlockEntity(pos) instanceof FrousseuxCandleHolderBlockEntity holder
                && !holder.getFrousseux().isEmpty()) {
            Block.dropStack(world, pos, itemOf(holder));
        }
        return super.onBreak(world, pos, state, player);
    }

    /** Which Frousseux it keeps: its candle's colour and its name. */
    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        NbtCompound kept = keptIn(stack);
        if (kept.isEmpty()) {
            tooltip.add(Text.translatable("tooltip.steveparty.frousseux_candle_holder.empty").formatted(Formatting.GRAY));
            return;
        }
        Text candle = FrousseuxCandleHolderBlockEntity.colorOf(kept).candle.getName();
        Text name = null;
        if (kept.contains("CustomName", NbtElement.STRING_TYPE) && context.getRegistryLookup() != null) {
            try {
                name = Text.Serialization.fromJson(kept.getString("CustomName"), context.getRegistryLookup());
            } catch (RuntimeException ignored) {
                // an unreadable name: the candle alone
            }
        }
        tooltip.add((name != null ? Text.translatable("tooltip.steveparty.frousseux_candle_holder.named", name, candle)
                : Text.translatable("tooltip.steveparty.frousseux_candle_holder.kept", candle)).formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.steveparty.frousseux_candle_holder.wake").formatted(Formatting.DARK_GRAY));
    }
}
