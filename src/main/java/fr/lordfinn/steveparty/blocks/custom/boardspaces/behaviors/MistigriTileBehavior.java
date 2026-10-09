package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.MistigriSentences;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The role of a Mistigri Cartridge: a token stopping here meets the Mistigri, who rolls his loaded die and passes a
 * sentence on its player (see {@link MistigriSentences}); the turn goes on once he has gone (or once a move back has
 * landed). Only in a running party (coins and stars are the party's). Nobody to play the token, or no sentence left
 * in the cartridge: a plain landing and a message. Going over the tile does nothing.
 */
public class MistigriTileBehavior extends ABoardSpaceBehavior {

    public MistigriTileBehavior() {
        super(BoardSpaceType.TILE_MISTIGRI);
    }

    @Override
    public boolean keepsTurn(MobEntity token) {
        return MistigriSentences.isRunning(token) || AdvanceBackMoves.isWaiting(token);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity,
                                     @Nullable PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        if (partyController == null || partyController.isRemoved() || !partyController.getPartyData().isStarted()) {
            TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, TileFeedback.Landing.DEFAULT,
                    TileFeedback.Landing.DEFAULT.noticeKey());
            return;
        }
        PartyStep step = partyController.getPartyData().getCurrentStep();
        ItemStack cartridge = boardSpaceEntity.getActiveCartridgeItemStack();
        MistigriSentences.Start start = MistigriSentences.start(serverWorld, pos, token, partyController, cartridge, null, () -> {
            if (!partyController.isRemoved() && step != null && partyController.getPartyData().getCurrentStep() == step) {
                partyController.nextStep();
            }
        });
        TileFeedback.Landing landing = start == MistigriSentences.Start.STARTED ? TileFeedback.Landing.MISTIGRI : TileFeedback.Landing.DEFAULT;
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing, landing.noticeKey());
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.MISTIGRI;
    }

    @Override
    public Status getStatus(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        return Status.BAD;
    }

    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        if (!stack.contains(ModComponents.COLOR)) setColor(boardSpaceBlockEntity, MistigriCartridgeItem.COLOR);
    }
}
