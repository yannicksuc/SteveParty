package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.components.ModComponents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.EntityAnchorArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;

import java.util.*;

import static fr.lordfinn.steveparty.Steveparty.SCHEDULER;
import static fr.lordfinn.steveparty.events.ModEvents.handleDyeInteraction;
import static fr.lordfinn.steveparty.sounds.ModSounds.CANCEL_SOUND_EVENT;
import static fr.lordfinn.steveparty.sounds.ModSounds.SELECT_SOUND_EVENT;
import static fr.lordfinn.steveparty.utils.MessageUtils.getColorFromText;
import static net.minecraft.util.ActionResult.PASS;
import static net.minecraft.util.ActionResult.SUCCESS;

public class StartTileBehavior extends ABoardSpaceBehavior {
    private static final double AMPLITUDE = 0.2;
    private static final double SPEED = 0.05f;
    private static final Set<UUID> recentlyUnboundEntities = new HashSet<>();

    public StartTileBehavior() {
        super(BoardSpaceType.TILE_START);

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClient && isBoundedEntity(entity)) {
                unboundEntity((ServerWorld) world, entity);
                return ActionResult.SUCCESS;
            }
            return PASS;
        });
    }


    private void unboundEntity(ServerWorld world, Entity entity) {
        if (recentlyUnboundEntities.contains(entity.getUuid())) return;

        BlockPos tilePos = entity.getBlockPos();
        BlockState state = world.getBlockState(tilePos);
        if (state == null || !ABoardSpaceBlock.countsAsStep(state.getBlock())) {
            tilePos = tilePos.subtract(new Vec3i(0, 1, 0));
            state = world.getBlockState(tilePos);
        }
        if (state == null || !ABoardSpaceBlock.countsAsStep(state.getBlock())) {
            return;
        }
        BoardSpaceBlockEntity tileEntity = getTileEntity(world, tilePos);
        if (tileEntity == null) return;
        ItemStack stack = getActiveCartdridgeItemstack(tileEntity);
        if (stack == null || stack.isEmpty()) return;
        stack.remove(ModComponents.TB_START_BOUND_ENTITY);
        tileEntity.update();
        recentlyUnboundEntities.add(entity.getUuid());
        entity.getWorld().playSound(
                null,
                entity.getBlockPos(),
                SoundEvents.BLOCK_BEACON_DEACTIVATE,
                SoundCategory.AMBIENT,
                1F,
                1F
        );
        SCHEDULER.schedule(UUID.randomUUID(), 60, () -> recentlyUnboundEntities.remove(entity.getUuid()));
    }

    @Override
    public  ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit)
    {
        if (world.isClient) return SUCCESS;
        BoardSpaceBlockEntity tileEntity = getTileEntity(world, pos);
        ItemStack stack = getActiveCartdridgeItemstack(tileEntity);
        if (stack == null || stack.isEmpty()) return PASS;
        String owner = stack.get(ModComponents.TB_START_OWNER);
        if (owner == null || !owner.equals(player.getUuidAsString()))  {
            owner = player.getUuidAsString();
            world.playSound(
                    null,
                    pos,
                    SELECT_SOUND_EVENT,
                    SoundCategory.AMBIENT,
                    0.5F,
                    1.0F
            );
        } else {
            owner = null;
            world.playSound(
                    null,
                    pos,
                    CANCEL_SOUND_EVENT,
                    SoundCategory.AMBIENT,
                    0.5F,
                    1.0F
            );
        }
        MobEntity entity = (MobEntity) getBoundedEntity((ServerWorld) world, stack, pos);
        if (entity != null) {
            ((TokenizedEntityInterface)entity).steveparty$setTokenOwner(owner == null ? null : world.getPlayerByUuid(UUID.fromString(owner)));
        }
        stack.set(ModComponents.TB_START_OWNER, owner);
        tileEntity.update(); // saves and syncs the owner (rendered client-side)
        return SUCCESS;
    }

    @Override
    public void onPieceStep(World world, BlockPos pos, BlockState state, MobEntity entity) {
        if (world.isClient) return;
        ItemStack stack = getActiveCartdridgeItemstack(world, pos);
        if (stack == null || stack.isEmpty()) return;
        Entity boundEntity = getBoundedEntity((ServerWorld) world, stack, pos);
        if (boundEntity == null && !recentlyUnboundEntities.contains(entity.getUuid())) {
            setBoundEntity(stack, entity, pos);
        }
        super.onPieceStep(world, pos, state, entity);
    }

    private void setBoundEntity(ItemStack stack, MobEntity entity, BlockPos pos) {
        stack.set(ModComponents.TB_START_BOUND_ENTITY, entity == null ? null : entity.getUuidAsString());
        if (entity == null) return;

        UUID owner = ((TokenizedEntityInterface)entity).steveparty$getTokenOwner();
        stack.set(ModComponents.TB_START_OWNER, owner == null ? null : owner.toString());
        entity.setVelocity(0, 0, 0);
        BoardSpaceBlockEntity tileEntity = getTileEntity(entity.getWorld(), pos);
        if (tileEntity != null) {
            setColor(tileEntity, getColorFromText(entity.getCustomName()));
            tileEntity.update(); // saves and syncs the binding / owner
        }
        entity.getWorld().playSound(
                null,
                entity.getBlockPos(),
                SoundEvents.BLOCK_BEACON_ACTIVATE,
                SoundCategory.AMBIENT,
                1F,
                1F
        );
    }

    private static boolean isBoundedEntity(Entity entity) {
        if (!(entity instanceof MobEntity token) || !((TokenizedEntityInterface) token).steveparty$isTokenized()) return false;
        ItemStack stack = getActiveCartdridgeItemstack(entity.getWorld(), entity.getBlockPos());
        if (stack == null || stack.isEmpty()) return false;
        String bound_entity = stack.get(ModComponents.TB_START_BOUND_ENTITY);
        return bound_entity != null && bound_entity.equals(entity.getUuidAsString());
    }

    private Entity getBoundedEntity(ServerWorld world, ItemStack stack, BlockPos pos) {
        String bound_entity = stack.get(ModComponents.TB_START_BOUND_ENTITY);
        if (bound_entity == null) {
            return null;
        }
        UUID boundUuid;
        try {
            boundUuid = UUID.fromString(bound_entity);
        } catch (IllegalArgumentException e) {
            return null;
        }
        Entity entity = world.getEntity(boundUuid);
        // null may only mean "not loaded yet" (chunk loading, reload): keep the binding
        if (entity == null) {
            return null;
        }
        double distance = entity.getPos().distanceTo(new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
        if (distance <= 2)
            return entity;
        return null;
    }

    private void animateBoundedEntity(Entity boundEntity, BlockPos pos, ServerWorld world, int ticks) {
        PlayerEntity closestEntity = world.getClosestPlayer(boundEntity, 6);
        if (closestEntity == null) return;

        if (Math.abs(new Vec3d(boundEntity.getVelocity().x,0, boundEntity.getVelocity().z).length()) > 0.2f) {
            unboundEntity(world, boundEntity);
            return;
        }
        boundEntity.setPos(pos.getX() + 0.5, pos.getY() + 0.5 + Math.sin((ticks * SPEED) * Math.PI) * AMPLITUDE, pos.getZ() + 0.5);

        boundEntity.lookAt(EntityAnchorArgumentType.EntityAnchor.EYES, closestEntity.getPos());
        double deltaX = closestEntity.getX() - boundEntity.getX();
        double deltaZ = closestEntity.getZ() - boundEntity.getZ();
        float yaw = (float) (Math.toDegrees(Math.atan2(deltaZ, deltaX)) - 90.0);

        boundEntity.setYaw(yaw);
        boundEntity.setBodyYaw(yaw);
        boundEntity.setHeadYaw(yaw);
        if (world.getTime() % 20 == 0) {
            world.spawnParticles(ParticleTypes.WAX_OFF, boundEntity.getX(), boundEntity.getY() + boundEntity.getHeight() / 2, boundEntity.getZ(), 5, 0.5, 2, 0.5, 0.2);
            world.playSound(
                    null,
                    boundEntity.getBlockPos(),
                    SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,
                    SoundCategory.AMBIENT,
                    0.2F,
                    1.0F
            );
        }
    }

    @Override
    public void tick(ServerWorld world, BoardSpaceBlockEntity state, ItemStack type, int ticks) {
        Entity entity = getBoundedEntity(world, type, state.getPos());
        if (entity == null) {
            return;
        }
        animateBoundedEntity(entity, state.getPos(), world, ticks);
        super.tick(world, state, type, ticks);
    }

    @Override
    public ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (stack == null || !(stack.getItem() instanceof DyeItem dye)) return PASS;
        final int newColor = dye.getColor().getEntityColor();
        BoardSpaceBlockEntity tileEntity = getTileEntity(world, pos);
        ItemStack behaviorItemstack = getActiveCartdridgeItemstack(tileEntity);
        if (behaviorItemstack == null || !(world instanceof ServerWorld serverWorld)) return SUCCESS;
        LivingEntity entity = getBoundedEntity(serverWorld, behaviorItemstack, pos) instanceof LivingEntity living ? living : null;
        if (entity != null)
            handleDyeInteraction(player.getAbilities().creativeMode, dye, entity, stack);
        else {
            setColor(tileEntity, newColor);
            if (!player.getAbilities().creativeMode) {
                stack.decrement(1);
            }
        }
        return SUCCESS;
    }

       @SuppressWarnings("SameParameterValue")
    private List<Entity> getAroundEntities(Entity entity, int radius) {
        return entity.getWorld().getOtherEntities(entity, entity.getBoundingBox().expand(radius));
    }
}
