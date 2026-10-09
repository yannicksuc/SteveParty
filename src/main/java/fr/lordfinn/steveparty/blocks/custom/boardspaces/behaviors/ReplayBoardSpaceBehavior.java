package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Rejouer / Roll Again: a token ending its move here during its owner's turn gives that owner another turn right
 * away (roll again, move again), then the turn order goes on as before. The extra turn never gives another one: a
 * Replay tile reached by the replay move is only a landing (no endless chain). See
 * {@link TokenTurnPartyStep#grantReplay}.
 */
public class ReplayBoardSpaceBehavior extends ABoardSpaceBehavior {
    public ReplayBoardSpaceBehavior() {
        super(BoardSpaceType.TILE_REPLAY);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        // The extra turn is inserted right after the current one: the caller then goes on with the next step, that turn
        TileFeedback.Landing landing = grant(partyController, token);
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing, landing.noticeKey());
    }

    /**
     * Gives the replay if the token lands here during its own (normal) turn.
     *
     * @return the landing to play: {@link TileFeedback.Landing#REPLAY} if granted, {@link TileFeedback.Landing#REPLAY_SPENT}
     * during the extra turn itself, else the default landing (outside the token's turn)
     */
    public static TileFeedback.Landing grant(@Nullable PartyControllerEntity party, MobEntity token) {
        if (party == null || party.isRemoved()) return TileFeedback.Landing.DEFAULT;
        if (!(party.getPartyData().getCurrentStep() instanceof TokenTurnPartyStep turn)
                || !token.getUuid().equals(turn.getTokenUUID())) return TileFeedback.Landing.DEFAULT;
        if (turn.isReplay()) return TileFeedback.Landing.REPLAY_SPENT;
        return turn.grantReplay(party) != null ? TileFeedback.Landing.REPLAY : TileFeedback.Landing.DEFAULT;
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.REPLAY;
    }
}
