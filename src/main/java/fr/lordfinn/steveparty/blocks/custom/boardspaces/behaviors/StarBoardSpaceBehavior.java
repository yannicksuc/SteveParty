package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.items.custom.cartridges.StarCartridgeItem;
import fr.lordfinn.steveparty.service.PartyStars;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Star Cartridge's role (yellow): a star space. A token ending its move on the one holding its party's star may buy
 * it, and the turn waits for its player's answer; tokens passing over it are offered the star by
 * {@link PartyStars#onTileReached} (unless the cartridge sells it only to the tokens stopping there).
 */
public class StarBoardSpaceBehavior extends ABoardSpaceBehavior {
    public StarBoardSpaceBehavior() {
        super(BoardSpaceType.TILE_STAR);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        super.onDestinationReached(world, pos, token, boardSpaceEntity, partyController);
        if (boardSpaceEntity != null) PartyStars.onLanding(token, boardSpaceEntity, partyController);
    }

    /** The turn goes on once the player chose to buy the star or not (see {@link PartyStars}). */
    @Override
    public boolean keepsTurn(MobEntity token) {
        return PartyStars.isDeciding(token.getUuid());
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.STAR;
    }

    /** Always the star's yellow: no other role is yellow. */
    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        setColor(boardSpaceBlockEntity, StarCartridgeItem.COLOR);
    }
}
