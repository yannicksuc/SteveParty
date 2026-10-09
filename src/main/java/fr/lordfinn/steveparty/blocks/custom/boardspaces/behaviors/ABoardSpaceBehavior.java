package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.payloads.custom.UpdateColoredTilePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import static net.minecraft.util.ActionResult.PASS;
import static net.minecraft.util.ActionResult.SUCCESS;

public abstract class ABoardSpaceBehavior {
    protected final BoardSpaceType tileType;

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
        return ABoardSpaceBlock.getBoardSpaceEntity(world, pos);
    }

    protected static ItemStack getActiveCartdridgeItemstack(World world, BlockPos pos) {
        BoardSpaceBlockEntity tileEntity = getTileEntity(world, pos);
        return getActiveCartdridgeItemstack(tileEntity);
    }

    protected static ItemStack getActiveCartdridgeItemstack(BoardSpaceBlockEntity tileEntity) {
        if (tileEntity == null) return null;
        return tileEntity.getActiveCartridgeItemStack();
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

    /**
     * A token reached this board space during a move (any token, in a party or not), {@code stepsLeft} steps still to
     * walk after it (0: it stops here). Called once per arrival, before its landing. Returns true to end its move here
     * (its steps left are lost and it lands here, check point included: a Threshold obstacle it does not get over).
     */
    public boolean onTokenReached(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, int stepsLeft) {
        return false;
    }

    /** A token stopped here at the end of its move: the landing feedback of this role (sound, particles, notice). */
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        if (world instanceof ServerWorld serverWorld && boardSpaceEntity != null)
            TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController);
    }

    /**
     * After {@link #onDestinationReached}: true if this role holds the turn (a shop stop) and moves the party on itself
     * later; false (the default) and the next step comes right away.
     */
    public boolean keepsTurn(MobEntity token) {
        return false;
    }

    /** How landing on this role feels (see {@link TileFeedback.Landing}); roles without their own: the default one. */
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.DEFAULT;
    }

    /**
     * The comparator level of a Router driving this board space when a token stops here (see
     * {@link fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity}): by default the level of its
     * landing kind. A new role overrides it with a level of its own (2 to 15; 1 is a token passing; 0 or less: no pulse).
     */
    public int comparatorLevel(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity.landingSignal(landing(boardSpaceEntity, stack));
    }

    public static void setColor(BoardSpaceBlockEntity tileEntity, int color) {
        ItemStack behaviorItemstack = getActiveCartdridgeItemstack(tileEntity);
        // Never write components on an empty stack (it may be the shared ItemStack.EMPTY instance)
        if (behaviorItemstack == null || behaviorItemstack.isEmpty()) return;
        Integer previousColor = behaviorItemstack.get(ModComponents.COLOR);
        if (previousColor != null && previousColor == color) return; // nothing changed, nothing to send
        behaviorItemstack.set(ModComponents.COLOR, color);
        World world = tileEntity.getWorld();
        if (!(world instanceof ServerWorld serverWorld)) return;
        for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, tileEntity.getPos())) {
            ServerPlayNetworking.send(player, new UpdateColoredTilePayload(tileEntity.getPos(), color));
        }
    }

    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
    }

    public Status getStatus(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        return Status.NEUTRAL;
    }

    public enum Status {
        BAD,
        GOOD,
        NEUTRAL
    }
}
