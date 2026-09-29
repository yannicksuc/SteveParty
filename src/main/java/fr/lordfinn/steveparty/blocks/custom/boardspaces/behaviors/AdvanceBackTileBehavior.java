package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback.Landing;
import fr.lordfinn.steveparty.items.custom.cartridges.AdvanceBackCartridgeItem;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Move Forward / Back tile: a token ending its move here moves on a few more spaces, forward or back (its cartridge's
 * setting, see {@link AdvanceBackCartridgeItem}); the rules are {@link AdvanceBackMoves}'. Its turn ends where it lands
 * then, with that space's own landing.
 */
public class AdvanceBackTileBehavior extends ABoardSpaceBehavior {

    public AdvanceBackTileBehavior() {
        super(BoardSpaceType.TILE_ADVANCE_BACK);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        // No chain: reached by an extra move, this tile is an ordinary space (see AdvanceBackMoves)
        if (AdvanceBackMoves.isExtraMove(token)) {
            TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, Landing.DEFAULT, Landing.DEFAULT.noticeKey());
            return;
        }
        int steps = AdvanceBackCartridgeItem.steps(boardSpaceEntity.getActiveCartridgeItemStack());
        int walked = AdvanceBackMoves.launch(serverWorld, token, pos, steps);
        Landing landing = steps < 0 ? Landing.BACK : Landing.ADVANCE;
        if (walked == 0) {
            TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing, landing.noticeKey() + ".blocked");
            return;
        }
        String notice = landing.noticeKey() + (walked == 1 ? ".one" : "");
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing, notice, walked);
    }

    /** The extra move just decided holds the turn: it ends where the token lands then (see AdvanceBackMoves). */
    @Override
    public boolean keepsTurn(MobEntity token) {
        return AdvanceBackMoves.isWaiting(token);
    }

    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        setColor(boardSpaceBlockEntity, AdvanceBackCartridgeItem.color(AdvanceBackCartridgeItem.steps(stack)));
    }

    @Override
    public Status getStatus(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        return AdvanceBackCartridgeItem.steps(stack) < 0 ? Status.BAD : Status.GOOD;
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return AdvanceBackCartridgeItem.steps(stack) < 0 ? Landing.BACK : Landing.ADVANCE;
    }
}
