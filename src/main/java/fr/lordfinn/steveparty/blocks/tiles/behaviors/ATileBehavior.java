package fr.lordfinn.steveparty.blocks.tiles.behaviors;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.tiles.Tile;
import fr.lordfinn.steveparty.blocks.tiles.TileEntity;
import fr.lordfinn.steveparty.blocks.tiles.TileType;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static net.minecraft.util.ActionResult.PASS;
import static net.minecraft.util.ActionResult.SUCCESS;

public abstract class ATileBehavior {
    protected final TileType tileType;

    public ATileBehavior(TileType tileType) {
        this.tileType = tileType;
    }

    public TileType getTileType() {
        return tileType;
    }

    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (entity instanceof PlayerEntity)
            onPlayerStep(world, pos, state, entity);
        if (entity instanceof MobEntity && ((TokenizedEntityInterface)entity).steveparty$isTokenized()) {
            onPieceStep(world, pos, state, entity);
        }
    }

    private void onPlayerStep(World world, BlockPos pos, BlockState state, Entity entity) {
    }

    protected Tile getTile(BlockState state) {
        return (Tile) state.getBlock();
    }

    protected TileEntity getTileEntity(World world, BlockPos pos) {
        return Tile.getTileEntity(world, pos);
    }

    protected ItemStack getBehaviorItemstack(World world, BlockPos pos) {
        TileEntity tileEntity = getTileEntity(world, pos);
        return getBehaviorItemstack(tileEntity);
    }

    protected ItemStack getBehaviorItemstack(TileEntity tileEntity) {
        if (tileEntity == null) return null;
        return tileEntity.getActiveTileBehaviorItemStack();
    }

    public void onPieceStep(World world, BlockPos pos, BlockState state, Entity entity) {};

    public void tick(ServerWorld world, TileEntity state, ItemStack type, int ticks) {}

    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return SUCCESS;
    }

    public ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        return SUCCESS;
    }
}
