package fr.lordfinn.steveparty.blocks.custom.frousseux;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.entities.ModEntities;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxColor;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.BlockStateComponent;
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
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
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
import net.minecraft.util.math.RotationPropertyHelper;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * A tamed Frousseux turned into a candle holder: a decoration that gives light, like the oxidised copper golem's
 * statue. It is the Frousseux itself, asleep as a candle: its whole identity is kept in the block entity
 * ({@link FrousseuxCandleHolderBlockEntity}).
 * <ul>
 *     <li><b>Its look</b>: a still candle of its wax, eyes closed, its two little fists beside it, facing whoever put
 *     it there ({@link #ROTATION}, 16 ways); on the ground, or on a {@link #SAUCER saucer} (the Candle Saucer, a gold
 *     tray, crafted onto its item). Its block model (models/block/frousseux_candle_holder*.json, its pool tinted its
 *     candle's colour, {@link #COLOR}) drawn by its block entity renderer turned its way, with its flame, the only
 *     thing of it that moves.</li>
 *     <li><b>Light</b>: its flame's ({@link #FLAME}, its stage when it fell asleep: 15, 12, 9 or 6, as the
 *     Frousseux's own).</li>
 *     <li><b>Right-click with an empty hand</b>: it wakes up, the same Frousseux (same UUID, name, owner, health),
 *     and follows its owner again; its saucer drops. Only its owner may wake it (or an operator, or a player in
 *     creative); anyone else is told so. Holding an item, the item is used as usual (blocks are placed against it).</li>
 *     <li><b>Broken</b>: it drops as its item, which keeps it (block entity data) and its saucer (block state);
 *     placed, it is back as it was. Broken in creative, it drops too, like a shulker box: never lost.</li>
 * </ul>
 */
public class FrousseuxCandleHolderBlock extends BlockWithEntity {
    public static final MapCodec<FrousseuxCandleHolderBlock> CODEC = createCodec(FrousseuxCandleHolderBlock::new);
    /** Which way it faces: 16 directions, as a sign or a skull (see {@link #yawOf}). */
    public static final IntProperty ROTATION = Properties.ROTATION;
    /** Its candle's colour (its pool's tint, its flame's colour). */
    public static final EnumProperty<FrousseuxColor> COLOR = EnumProperty.of("color", FrousseuxColor.class);
    /** Its flame's stage ({@link FrousseuxEntity.Flame} ordinal): its size and its light. */
    public static final IntProperty FLAME = IntProperty.of("flame", 0, FrousseuxEntity.Flame.values().length - 1);
    /** Standing on a Candle Saucer. */
    public static final BooleanProperty SAUCER = BooleanProperty.of("saucer");
    /**
     * Its candle and fists on the ground; on its saucer, the tray and the candle one pixel up (the flame left out).
     * Whichever way it faces: square about its middle.
     */
    private static final VoxelShape SHAPE = VoxelShapes.union(Block.createCuboidShape(3.5, 0, 3.5, 12.5, 9, 12.5),
            Block.createCuboidShape(1, 0, 1, 15, 3, 15));
    private static final VoxelShape SAUCER_SHAPE = VoxelShapes.union(Block.createCuboidShape(0, 0, 0, 16, 2, 16),
            Block.createCuboidShape(3.5, 2, 3.5, 12.5, 10, 12.5));
    /** Entity data that says where it was, not who it is: left out of the kept data. */
    private static final String[] WHERE = {"Pos", "Motion", "Rotation", "FallDistance", "Fire", "OnGround", "Light",
            "LightLevel", "Sitting", "Air", "PortalCooldown", "Leash", "HurtTime", "DeathTime", "HurtByTimestamp"};

    public FrousseuxCandleHolderBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(ROTATION, 0).with(COLOR, FrousseuxColor.PLAIN).with(FLAME, 0)
                .with(SAUCER, false));
    }

    @Override
    protected MapCodec<? extends BlockWithEntity> getCodec() {
        return CODEC;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(ROTATION, COLOR, FLAME, SAUCER);
    }

    /** Its light: its flame's. */
    public static int lightOf(BlockState state) {
        return FrousseuxEntity.Flame.values()[state.get(FLAME)].light;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(SAUCER) ? SAUCER_SHAPE : SHAPE;
    }

    /**
     * Drawn whole by its block entity renderer (its block model turned its way, and its flame): a block model turns
     * by quarters only, it faces 16 ways (as skulls and banners are drawn).
     */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    /** The way it faces, as a yaw (its front's): towards whoever put it there. */
    public static float yawOf(BlockState state) {
        return RotationPropertyHelper.toDegrees(state.get(ROTATION));
    }

    /** Facing back towards someone looking this way ({@code yaw}). */
    public static int rotationFacing(float yaw) {
        return RotationPropertyHelper.fromYaw(yaw + 180);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new FrousseuxCandleHolderBlockEntity(pos, state);
    }

    /**
     * Placed from its item: facing the player, its colour and flame from the Frousseux it keeps (its saucer: the
     * item's block state, applied by the block item).
     */
    @Override
    public @Nullable BlockState getPlacementState(ItemPlacementContext ctx) {
        NbtCompound kept = keptIn(ctx.getStack());
        return getDefaultState().with(ROTATION, rotationFacing(ctx.getPlayerYaw()))
                .with(COLOR, FrousseuxCandleHolderBlockEntity.colorOf(kept))
                .with(FLAME, FrousseuxCandleHolderBlockEntity.flameOf(kept).ordinal());
    }

    @Override
    protected BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(ROTATION, rotation.rotate(state.get(ROTATION), RotationPropertyHelper.getMax() + 1));
    }

    @Override
    protected BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.with(ROTATION, mirror.mirror(state.get(ROTATION), RotationPropertyHelper.getMax() + 1));
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
        BlockState state = ModBlocks.FROUSSEUX_CANDLE_HOLDER.getDefaultState()
                .with(ROTATION, rotationFacing(player.getYaw())).with(COLOR, frousseux.getColor())
                .with(FLAME, frousseux.getFlame().ordinal());
        frousseux.discard(); // its moving light goes with it
        world.setBlockState(pos, state, Block.NOTIFY_ALL);
        if (world.getBlockEntity(pos) instanceof FrousseuxCandleHolderBlockEntity holder) holder.setFrousseux(data);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.4, z = pos.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.WAX_ON, x, y, z, 8, 0.25, 0.25, 0.25, 0.5);
        world.spawnParticles(ParticleTypes.SMALL_FLAME, x, y + 0.4, z, 4, 0.05, 0.05, 0.05, 0.01);
        world.playSound(null, pos, SoundEvents.BLOCK_CANDLE_PLACE, SoundCategory.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    /** Wakes the Frousseux up where its candle holder was, as it was; the block goes, its saucer drops. */
    public static @Nullable FrousseuxEntity wakeUp(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof FrousseuxCandleHolderBlockEntity holder)) return null;
        boolean saucer = world.getBlockState(pos).get(SAUCER);
        FrousseuxEntity frousseux = ModEntities.FROUSSEUX.create(world);
        if (frousseux == null) return null;
        NbtCompound data = holder.getFrousseux();
        if (!data.isEmpty()) frousseux.readNbt(data);
        else frousseux.setColor(FrousseuxColor.PLAIN); // a candle holder that never held one: a wild plain one
        // its UUID taken meanwhile (a copied item placed twice): a new one, the rest kept
        if (world.getEntity(frousseux.getUuid()) != null) frousseux.setUuid(UUID.randomUUID());
        float yaw = yawOf(world.getBlockState(pos));
        frousseux.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.05, pos.getZ() + 0.5, yaw, 0);
        frousseux.setHeadYaw(yaw);
        frousseux.setBodyYaw(yaw);
        frousseux.setSitting(false);
        world.removeBlock(pos, false);
        world.spawnEntity(frousseux);
        if (saucer) Block.dropStack(world, pos, new ItemStack(ModItems.CANDLE_SAUCER));
        world.spawnParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5, 8, 0.25, 0.25, 0.25, 0.5);
        world.playSound(null, pos, ModSounds.FROUSSEUX_RELIGHT, SoundCategory.NEUTRAL, 1.0f, 1.2f);
        return frousseux;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (world instanceof ServerWorld server && world.getBlockEntity(pos) instanceof FrousseuxCandleHolderBlockEntity holder) {
            if (mayWake(holder, player)) wakeUp(server, pos);
            else player.sendMessage(Text.translatable("message.steveparty.frousseux_candle_holder.not_yours"), true);
        }
        return ActionResult.success(world.isClient);
    }

    /** Only its owner wakes it (or an operator, or a player in creative); one that never had an owner, anyone. */
    public static boolean mayWake(FrousseuxCandleHolderBlockEntity holder, PlayerEntity player) {
        return isOwnersOrNobodys(holder, player) || player.isCreative() || player.hasPermissionLevel(2);
    }

    /** Its owner's (or one that never had an owner). */
    public static boolean isOwnersOrNobodys(FrousseuxCandleHolderBlockEntity holder, PlayerEntity player) {
        UUID owner = holder.getOwner();
        return owner == null || owner.equals(player.getUuid());
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

    /**
     * Its item, keeping the Frousseux (placed, the block entity reads it back: vanilla block entity data) and its
     * saucer (the block state the block item applies when placed).
     */
    public static ItemStack itemOf(FrousseuxCandleHolderBlockEntity holder) {
        ItemStack stack = new ItemStack(ModBlocks.FROUSSEUX_CANDLE_HOLDER);
        if (holder.getCachedState().get(SAUCER)) setOnSaucer(stack, true);
        if (holder.getFrousseux().isEmpty()) return stack;
        NbtCompound data = new NbtCompound();
        data.putString("id", Registries.BLOCK_ENTITY_TYPE.getId(ModBlockEntities.FROUSSEUX_CANDLE_HOLDER).toString());
        data.put(FrousseuxCandleHolderBlockEntity.KEY, holder.getFrousseux().copy());
        stack.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(data));
        return stack;
    }

    /** Whether its item stands on a saucer. */
    public static boolean isOnSaucer(ItemStack stack) {
        BlockStateComponent state = stack.get(DataComponentTypes.BLOCK_STATE);
        return state != null && Boolean.TRUE.equals(state.getValue(SAUCER));
    }

    /** Its item on a saucer, or off it (the rest of its data kept). */
    public static void setOnSaucer(ItemStack stack, boolean saucer) {
        if (saucer) stack.set(DataComponentTypes.BLOCK_STATE, BlockStateComponent.DEFAULT.with(SAUCER, true));
        else stack.remove(DataComponentTypes.BLOCK_STATE);
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

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        NbtCompound kept = keptIn(stack);
        if (kept.isEmpty()) return; // (only a tamed Frousseux makes one: an empty one has nothing to say)
        Text candle = FrousseuxCandleHolderBlockEntity.colorOf(kept).candle.getName();
        Text name = null;
        if (kept.contains("CustomName", NbtElement.STRING_TYPE) && context.getRegistryLookup() != null) {
            try {
                name = Text.Serialization.fromJson(kept.getString("CustomName"), context.getRegistryLookup());
            } catch (RuntimeException ignored) {
                // an unreadable name: the candle alone
            }
        }
        Tooltips tips = Tooltips.of(tooltip).tags(Tooltips.Tag.CREATURE);
        tips.state(name != null ? Text.translatable("tooltip.steveparty.frousseux_candle_holder.named", Tooltips.value(name), Tooltips.value(candle))
                : Text.translatable("tooltip.steveparty.frousseux_candle_holder.kept", Tooltips.value(candle)));
        if (isOnSaucer(stack)) tips.state(Text.translatable("tooltip.steveparty.frousseux_candle_holder.saucer"));
        tips.more(more -> more.use(Tooltips.Keys.use(), "tooltip.steveparty.frousseux_candle_holder.wake"));
    }
}
