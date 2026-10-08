package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.cartridges.FrousseuxCartridgeItem;
import fr.lordfinn.steveparty.service.FrousseuxThefts;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The role of a Frousseux Cartridge: a token stopping here sends a Frousseux to steal coins or stars from another
 * player, picked by its player (see {@link FrousseuxThefts}); the turn goes on once the Frousseux has vanished. Only
 * in a running party (coins and stars are the party's). No other player, or nobody to play the token: a plain landing
 * and a message. Going over the tile does nothing.
 */
public class FrousseuxTileBehavior extends ABoardSpaceBehavior {

    public FrousseuxTileBehavior() {
        super(BoardSpaceType.TILE_FROUSSEUX);
    }

    @Override
    public boolean keepsTurn(MobEntity token) {
        return FrousseuxThefts.isRunning(token);
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
        boolean stars = FrousseuxCartridgeItem.stealsStars(cartridge);
        FrousseuxThefts.Start start = FrousseuxThefts.start(serverWorld, pos, token, partyController, stars,
                FrousseuxCartridgeItem.amount(cartridge), () -> {
                    if (!partyController.isRemoved() && step != null && partyController.getPartyData().getCurrentStep() == step) {
                        partyController.nextStep();
                    }
                });
        TileFeedback.Landing landing = start == FrousseuxThefts.Start.STARTED ? TileFeedback.Landing.FROUSSEUX : TileFeedback.Landing.DEFAULT;
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing, landing.noticeKey());
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.FROUSSEUX;
    }

    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        if (!stack.contains(ModComponents.COLOR)) setColor(boardSpaceBlockEntity, FrousseuxCartridgeItem.COLOR);
    }
}
