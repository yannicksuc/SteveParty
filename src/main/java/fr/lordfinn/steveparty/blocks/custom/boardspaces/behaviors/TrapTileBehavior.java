package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.BoardSpaceRedstoneRouterBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.BoardTraps;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The role of a Trap Cartridge (see {@link BoardTraps}): in a running party, a token stopping here springs the trap of
 * another player (a malus landing), or, nothing sprung, its player may set one of their Traps here; the turn waits for
 * the answer (and for a move back). Outside a party: a plain landing. A Router reading it pulses the malus level when
 * a trap sprang.
 */
public class TrapTileBehavior extends ABoardSpaceBehavior {
    public TrapTileBehavior() {
        super(BoardSpaceType.TILE_TRAP);
    }

    @Override
    public boolean keepsTurn(MobEntity token) {
        return BoardTraps.isAsking(token) || AdvanceBackMoves.isWaiting(token);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity,
                                     @Nullable PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        if (!isPartyRunning(partyController)) {
            landPlain(serverWorld, boardSpaceEntity, token, partyController);
            return;
        }
        BoardTraps.Outcome outcome = BoardTraps.spring(serverWorld, boardSpaceEntity, token, partyController);
        boolean sprung = outcome == BoardTraps.Outcome.SPRUNG;
        TileFeedback.Landing landing = sprung ? TileFeedback.Landing.BAD : TileFeedback.Landing.DEFAULT;
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing,
                sprung ? "message.steveparty.tile_landed.trap" : landing.noticeKey());
        if (sprung || outcome == BoardTraps.Outcome.DISARMED) return;
        BoardTraps.offer(serverWorld, boardSpaceEntity, token, resumeTurn(partyController));
    }

    @Override
    public int comparatorLevel(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        if (boardSpaceEntity.getWorld() instanceof ServerWorld world
                && BoardTraps.lastOutcome(world, boardSpaceEntity.getPos()) == BoardTraps.Outcome.SPRUNG) {
            return BoardSpaceRedstoneRouterBlockEntity.LEVEL_MALUS;
        }
        return BoardSpaceRedstoneRouterBlockEntity.LEVEL_DEFAULT;
    }
}
