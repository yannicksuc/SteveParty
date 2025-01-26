package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpace;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.payloads.UpdateColoredTilePayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static fr.lordfinn.steveparty.events.ModEvents.handleDyeInteraction;
import static net.minecraft.util.ActionResult.PASS;
import static net.minecraft.util.ActionResult.SUCCESS;

public abstract class ABoardSpaceBehavior {
    protected final BoardSpaceType tileType;
    private SoundEvent activateSound = SoundEvents.BLOCK_NOTE_BLOCK_HARP.value();

    public ABoardSpaceBehavior(BoardSpaceType tileType) {
        this.tileType = tileType;
    }

    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof PlayerEntity)
            onPlayerStep(world, pos, state, entity);
        if (entity instanceof MobEntity && ((TokenizedEntityInterface)entity).steveparty$isTokenized()) {
            onPieceStep(world, pos, state, (MobEntity) entity);
        }
    }

    private void onPlayerStep(World world, BlockPos pos, BlockState state, Entity entity) {}

    protected static BoardSpaceBlockEntity getTileEntity(World world, BlockPos pos) {
        return BoardSpace.getBoardSpaceEntity(world, pos);
    }

    protected static ItemStack getBehaviorItemstack(World world, BlockPos pos) {
        BoardSpaceBlockEntity tileEntity = getTileEntity(world, pos);
        return getBehaviorItemstack(tileEntity);
    }

    protected static ItemStack getBehaviorItemstack(BoardSpaceBlockEntity tileEntity) {
        if (tileEntity == null) return null;
        return tileEntity.getActiveTileBehaviorItemStack();
    }

    public void onPieceStep(World world, BlockPos pos, BlockState state, MobEntity entity) {}

    public void tick(ServerWorld world, BoardSpaceBlockEntity state, ItemStack type, int ticks) {}

    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return SUCCESS;
    }

    public ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (stack == null || !(stack.getItem() instanceof DyeItem dye)) return PASS;
        final int newColor = dye.getColor().getEntityColor();
        BoardSpaceBlockEntity tileEntity = getTileEntity(world, pos);
        setColor(tileEntity, newColor);
        if (!player.getAbilities().creativeMode) {
            stack.decrement(1);
        }
        return SUCCESS;
    }

    public boolean needToStop(World world, BlockPos pos) {
        return false;
    }

    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        this.playActivateSound(world, pos);
    }

    protected void setActivateSound(SoundEvent activateSound) {
        this.activateSound = activateSound;
    }

    private void playActivateSound(World world, BlockPos pos) {
        if (world == null || this.activateSound == null) return;
        world.playSound(null, pos, this.activateSound, SoundCategory.BLOCKS, 1.0F, 1.0F);
    }

    public static void setColor(BoardSpaceBlockEntity tileEntity, int color) {
        ItemStack behaviorItemstack = getBehaviorItemstack(tileEntity);
        if (behaviorItemstack == null) return;
        behaviorItemstack.set(ModComponents.TB_START_COLOR, color);
        tileEntity.markDirty();
        World world = tileEntity.getWorld();
        if (world == null) return;
        for (PlayerEntity player : world.getPlayers()) {
            ServerPlayNetworking.send((ServerPlayerEntity) player, new UpdateColoredTilePayload(tileEntity.getPos(), color));
        }
    }
}
