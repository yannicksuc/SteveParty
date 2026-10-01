package fr.lordfinn.steveparty.blocks.custom;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerBlockUse;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerReaction;
import fr.lordfinn.steveparty.blocks.custom.villager.VillagerSoul;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.item.Item;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootWorldContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.block.WireOrientation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A villager squashed into a block (a piston pushed it down), its face on the top. Falls like sand; a player landing
 * on it from high enough reveals a boxed trader (PlayerFallMixin).
 * <p>
 * It is alive: its {@link VillagerBlockEntity} reacts to what happens around it (players, mobs, weather, music...),
 * and its renderer animates it (it is drawn by the block entity renderer, not as a baked block, see
 * {@link #getRenderType}). This block only forwards its own interactions: right-clicks (with an empty hand or an
 * item, see {@link VillagerBlockUse}), being stepped or landed on, projectiles.
 */
public class VillagerBlock extends FallingBlock implements BlockEntityProvider {
    public static final MapCodec<VillagerBlock> CODEC = Block.createCodec(VillagerBlock::new);
    private static final BlockSoundGroup VILLAGER_SOUND_GROUP = new BlockSoundGroup(0.8f, 1, SoundEvents.ENTITY_VILLAGER_DEATH, SoundEvents.ENTITY_VILLAGER_AMBIENT, SoundEvents.ENTITY_VILLAGER_CELEBRATE, SoundEvents.ENTITY_VILLAGER_HURT, SoundEvents.ENTITY_VILLAGER_NO);
    /** Minimum delay (ticks) between two identical neighbor messages for the same block, to avoid chat spam. */
    private static final int NEIGHBOR_MESSAGE_COOLDOWN = 100;
    private static final Map<String, Long> LAST_NEIGHBOR_MESSAGES = new HashMap<>();
    /** Fall distance (blocks) from which a player landing on it is a belly flop. */
    public static final float BELLY_FLOP_FALL = 3.0F;

    public VillagerBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected MapCodec<? extends FallingBlock> getCodec() {
        return CODEC;
    }

    // Helper method to send a message to the closest player within 10 blocks
    private void sendMessageToPlayer(World world, BlockPos pos, String translationKey) {
        if (world.isClient) return;
        if (world.getClosestPlayer(pos.getX(), pos.getY(), pos.getZ(), 10, false) instanceof ServerPlayerEntity closestPlayer) {
            closestPlayer.sendMessage(Text.translatable(translationKey), false);
        }
    }

    // Same as sendMessageToPlayer, but rate-limited: neighborUpdate can fire many times per second
    private void sendNeighborMessage(World world, BlockPos pos, String translationKey) {
        long now = world.getTime();
        String key = world.getRegistryKey().getValue() + "|" + pos.asLong() + "|" + translationKey;
        Long last = LAST_NEIGHBOR_MESSAGES.get(key);
        if (last != null && now - last >= 0 && now - last < NEIGHBOR_MESSAGE_COOLDOWN) return;
        if (LAST_NEIGHBOR_MESSAGES.size() > 256) {
            LAST_NEIGHBOR_MESSAGES.values().removeIf(t -> now - t >= NEIGHBOR_MESSAGE_COOLDOWN || now < t);
        }
        LAST_NEIGHBOR_MESSAGES.put(key, now);
        sendMessageToPlayer(world, pos, translationKey);
    }

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
        sendMessageToPlayer(world, pos, "villagerblock.break");
        if (world instanceof ServerWorld server) farewell(server, pos);
        return state;
    }

    /** Broken: a sad farewell, and its little ghost sighs and floats away. */
    private static void farewell(ServerWorld world, BlockPos pos) {
        world.playSound(null, pos, ModSounds.VILLAGER_BLOCK_FAREWELL, SoundCategory.BLOCKS, 1f, 1f);
        world.playSound(null, pos, ModSounds.VILLAGER_BLOCK_SIGH, SoundCategory.BLOCKS, 0.8f, 1f);
        double x = pos.getX() + 0.5, y = pos.getY() + 0.5, z = pos.getZ() + 0.5;
        world.spawnParticles(ParticleTypes.POOF, x, y, z, 10, 0.3, 0.3, 0.3, 0.02);
        world.spawnParticles(ParticleTypes.FALLING_WATER, x, y + 0.4, z, 6, 0.3, 0.1, 0.3, 0);
        // the ghost: soul wisps rising slowly
        world.spawnParticles(ParticleTypes.SOUL, x, y + 0.3, z, 5, 0.15, 0.2, 0.15, 0.03);
    }

    /**
     * A player starts breaking it (no tool needed, but it takes a moment): it pleads. The clients animate the rest
     * from the breaking progress (VillagerBlockEntity#getMiningStage): trembling, tears, sobs.
     */
    @Override
    protected void onBlockBreakStart(BlockState state, World world, BlockPos pos, PlayerEntity player) {
        super.onBlockBreakStart(state, world, pos, player);
        if (!world.isClient && !player.isCreative() && world.getBlockEntity(pos) instanceof VillagerBlockEntity villager) {
            villager.react(VillagerReaction.PLEAD, player);
        }
    }

    /** The dropped villager block keeps the villager inside (placed again, it is the same villager). */
    @Override
    protected List<ItemStack> getDroppedStacks(BlockState state, LootWorldContext.Builder builder) {
        List<ItemStack> drops = super.getDroppedStacks(state, builder);
        if (builder.getOptional(LootContextParameters.BLOCK_ENTITY) instanceof VillagerBlockEntity villager
                && villager.getSoul() != null) {
            for (ItemStack drop : drops) {
                if (drop.isOf(asItem())) {
                    NbtCompound data = villager.keptData();
                    data.putString("id", Registries.BLOCK_ENTITY_TYPE.getId(ModBlockEntities.VILLAGER_BLOCK_ENTITY).toString());
                    drop.set(DataComponentTypes.BLOCK_ENTITY_DATA, NbtComponent.of(data));
                }
            }
        }
        return drops;
    }

    /** Falls like sand, with the villager inside (the falling block carries the block entity data). */
    @Override
    protected void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (canFallThrough(world.getBlockState(pos.down())) && pos.getY() >= world.getBottomY()) {
            NbtCompound kept = world.getBlockEntity(pos) instanceof VillagerBlockEntity villager ? villager.keptData() : null;
            FallingBlockEntity falling = FallingBlockEntity.spawnFromBlock(world, pos, state);
            falling.blockEntityData = kept;
            configureFallingBlockEntity(falling);
        }
    }

    /** Who is squashed inside (profession and name), for a villager block item that keeps a villager. */
    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        super.appendTooltip(stack, context, tooltip, options);
        NbtComponent data = stack.get(DataComponentTypes.BLOCK_ENTITY_DATA);
        if (data == null || !data.contains(VillagerSoul.KEY)) return;
        NbtCompound soul = data.copyNbt().getCompound(VillagerSoul.KEY);
        String profession = soul.getCompound("VillagerData").getString("profession");
        Identifier id = Identifier.tryParse(profession.isEmpty() ? "minecraft:none" : profession);
        Text job = Text.translatable(EntityType.VILLAGER.getTranslationKey() + "." + (id == null ? "none" : id.getPath()));
        Text name = null;
        if (soul.contains("CustomName", NbtElement.STRING_TYPE) && context.getRegistryLookup() != null) {
            try {
                name = Text.Serialization.fromJson(soul.getString("CustomName"), context.getRegistryLookup());
            } catch (RuntimeException ignored) {
                // an unreadable name: the profession alone
            }
        }
        tooltip.add((name != null ? Text.translatable("tooltip.steveparty.villager_block.named", name, job)
                : Text.translatable("tooltip.steveparty.villager_block.villager", job)).formatted(Formatting.GRAY));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack itemStack) {
        super.onPlaced(world, pos, state, placer, itemStack);

        // Handling water interaction
        if (world.getBlockState(pos.up()).isOf(Blocks.WATER)) {
            sendMessageToPlayer(world, pos, "villagerblock.underwater");
            world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        // Handling fire interaction
        else if (world.getBlockState(pos.up()).isOf(Blocks.FIRE) || world.getBlockState(pos.down()).isOf(Blocks.FIRE)) {
            sendMessageToPlayer(world, pos, "villagerblock.onfire");
            world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_HURT, SoundCategory.BLOCKS, 1.0F, 1.0F);
        }

        // Handling sky interaction
        else if (world.isAir(pos.down())) {
            sendMessageToPlayer(world, pos, "villagerblock.sky");
            world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.BLOCKS, 1.0F, 1.0F);
        } else {
            sendMessageToPlayer(world, pos, "villagerblock.place");
        }
    }

    @Override
    protected void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, @Nullable WireOrientation wireOrientation, boolean notify) {
        super.neighborUpdate(state, world, pos, sourceBlock, wireOrientation, notify);
        if (world.isClient) return;
        if (world.getBlockState(pos.north()).isOf(Blocks.GRASS_BLOCK) &&
                world.getBlockState(pos.south()).isOf(Blocks.GRASS_BLOCK) &&
                world.getBlockState(pos.east()).isOf(Blocks.GRASS_BLOCK) &&
                world.getBlockState(pos.west()).isOf(Blocks.GRASS_BLOCK)) {
            sendNeighborMessage(world, pos, "villagerblock.grass");
        }

        if (world.getBlockState(pos.north()).isOf(Blocks.STONE) &&
                world.getBlockState(pos.south()).isOf(Blocks.STONE) &&
                world.getBlockState(pos.east()).isOf(Blocks.STONE) &&
                world.getBlockState(pos.west()).isOf(Blocks.STONE)) {
            sendNeighborMessage(world, pos, "villagerblock.stone");
        }
    }

    @Override
    protected BlockSoundGroup getSoundGroup(BlockState state) {
        return VILLAGER_SOUND_GROUP;
    }

    // --- Alive: reactions ---

    /**
     * Drawn by its block entity renderer (squash, hops, turning its face...). The falling block and the piston
     * still draw its baked model (FallingBlockEntityRendererVillagerMixin; pistons draw any model).
     */
    @Override
    protected BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.INVISIBLE;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new VillagerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.VILLAGER_BLOCK_ENTITY) return null;
        return world.isClient
                ? (w, pos, s, be) -> ((VillagerBlockEntity) be).clientTick()
                : (w, pos, s, be) -> ((VillagerBlockEntity) be).serverTick((ServerWorld) w);
    }

    /** The reactions reach the clients as block events (see {@link VillagerBlockEntity#react}). */
    @Override
    protected boolean onSyncedBlockEvent(BlockState state, World world, BlockPos pos, int type, int data) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        return blockEntity != null && blockEntity.onSyncedBlockEvent(type, data);
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (stack.isEmpty()) return ActionResult.PASS_TO_DEFAULT_BLOCK_ACTION;
        VillagerReaction reaction = VillagerBlockUse.reactionTo(stack);
        // a block to place: placed as usual (building around it has its own reactions)
        if (reaction == null) return ActionResult.PASS;
        if (!world.isClient && world.getBlockEntity(pos) instanceof VillagerBlockEntity villager) {
            villager.onRightClick(player, reaction);
        }
        // items it doesn't want keep their own use: it only shakes its head
        return VillagerBlockUse.takesUse(stack) ? ActionResult.SUCCESS : ActionResult.PASS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof VillagerBlockEntity villager) {
            villager.onRightClick(player, VillagerReaction.POKED);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        super.onSteppedOn(world, pos, state, entity);
        if (!world.isClient && entity instanceof PlayerEntity player && !player.isSpectator()
                && world.getBlockEntity(pos) instanceof VillagerBlockEntity villager) {
            villager.onSteppedOn(player);
        }
    }

    @Override
    public void onLandedUpon(World world, BlockState state, BlockPos pos, Entity entity, float fallDistance) {
        if (!world.isClient && fallDistance >= BELLY_FLOP_FALL && entity instanceof PlayerEntity player
                && !player.isSpectator() && world.getBlockEntity(pos) instanceof VillagerBlockEntity villager) {
            villager.react(VillagerReaction.BELLY_FLOP, player);
        }
        super.onLandedUpon(world, state, pos, entity, fallDistance);
    }

    @Override
    protected void onProjectileHit(World world, BlockState state, BlockHitResult hit, ProjectileEntity projectile) {
        super.onProjectileHit(world, state, hit, projectile);
        if (!world.isClient && world.getBlockEntity(hit.getBlockPos()) instanceof VillagerBlockEntity villager) {
            villager.react(projectile instanceof SnowballEntity ? VillagerReaction.SHIVER : VillagerReaction.OUCH,
                    projectile.getOwner());
        }
    }
}
