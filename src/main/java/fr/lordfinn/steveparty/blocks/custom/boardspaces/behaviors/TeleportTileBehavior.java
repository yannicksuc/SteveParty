package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The role of a Teleport Cartridge (see {@link TileTeleport}): a token landing here is warped to one of the tile's
 * arrivals, and the turn goes on once it has reappeared there. Its move ends on the arrival without landing there,
 * unless the cartridge says so (and never on to another teleport tile). Going over the tile does nothing.
 */
public class TeleportTileBehavior extends ABoardSpaceBehavior {

    public TeleportTileBehavior() {
        super(BoardSpaceType.TILE_TELEPORT);
    }

    /** The turn goes on once the token has reappeared on the arrival. */
    @Override
    public boolean keepsTurn(MobEntity token) {
        return TileTeleport.isTeleporting(token);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity,
                                     @Nullable PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        ItemStack cartridge = boardSpaceEntity.getActiveCartridgeItemStack();
        // The notice « 🌀 X est téléporté ! » and the whirl (a plain landing without arrival: see landing())
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController);
        BlockPos target = TileTeleport.pick(serverWorld, boardSpaceEntity, cartridge);
        if (target == null) return; // no arrival: an ordinary space, the party goes on right away (keepsTurn false)
        boolean landOnTarget = TileTeleport.settings(cartridge).landOnTarget();
        PartyStep step = partyController == null ? null : partyController.getPartyData().getCurrentStep();
        // No second move during the warp (a dice rolled meanwhile would move it again)
        if (token instanceof TokenizedEntityInterface tokenized) {
            tokenized.steveparty$setStatus(TokenStatus.clearStatus(tokenized.steveparty$getStatus(), TokenStatus.CAN_MOVE));
        }
        TileTeleport.teleport(serverWorld, token, pos, target, () -> {
            boolean arrivalEndsTurn = landOnTarget && landOn(serverWorld, target, token, partyController);
            if (partyController != null && !partyController.isRemoved() && step != null
                    && partyController.getPartyData().getCurrentStep() == step && !arrivalEndsTurn) {
                partyController.nextStep();
            }
        });
    }

    /**
     * The cartridge's option: the arrival's role plays as if the token had landed there (a teleport tile never
     * teleports again).
     *
     * @return true if that role ends the turn itself
     */
    private static boolean landOn(ServerWorld world, BlockPos target, MobEntity token, @Nullable PartyControllerEntity party) {
        BoardSpaceBlockEntity arrival = ABoardSpaceBlock.getBoardSpaceEntity(world, target);
        if (arrival == null || party == null) return false;
        ABoardSpaceBehavior behavior = arrival.getBoardSpaceBehavior();
        if (behavior == null || behavior instanceof TeleportTileBehavior) return false;
        behavior.onDestinationReached(world, target, token, arrival, party);
        return behavior.keepsTurn(token);
    }

    /** The teleport jingle and notice when it has somewhere to send the token, else a plain landing. */
    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        World world = boardSpaceEntity.getWorld();
        return world != null && !TileTeleport.validTargets(world, boardSpaceEntity.getPos(), stack).isEmpty()
                ? TileFeedback.Landing.TELEPORT : TileFeedback.Landing.DEFAULT;
    }

    /** A Router's comparator reads 8 when a token lands here (a plain landing's level when it sends nowhere). */
    @Override
    public int comparatorLevel(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return landing(boardSpaceEntity, stack) == TileFeedback.Landing.TELEPORT
                ? fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity.LEVEL_TELEPORT
                : super.comparatorLevel(boardSpaceEntity, stack);
    }

    /** Purple unless dyed. */
    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        if (!stack.contains(ModComponents.COLOR)) setColor(boardSpaceBlockEntity, TileTeleport.COLOR);
    }
}
