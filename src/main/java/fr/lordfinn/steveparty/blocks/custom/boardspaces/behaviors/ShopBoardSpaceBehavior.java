package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import net.minecraft.server.world.ServerWorld;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.items.custom.cartridges.ShopCartridgeItem;
import fr.lordfinn.steveparty.service.ShopStops;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * The Shop Cartridge's role (lime green): a shop stop. On a tile, the token ending its move there opens the shop for its
 * owner, and the turn waits for the stop; a check point stops the tokens passing through instead (see
 * {@link ShopStops} and {@link fr.lordfinn.steveparty.service.TokenMovementService}).
 */
public class ShopBoardSpaceBehavior extends ABoardSpaceBehavior {
    public ShopBoardSpaceBehavior() {
        super(BoardSpaceType.BOARD_SPACE_SHOP);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        super.onDestinationReached(world, pos, token, boardSpaceEntity, partyController);
        if (boardSpaceEntity != null && ABoardSpaceBlock.countsAsStep(boardSpaceEntity.getCachedState().getBlock())) {
            ShopStops.onLanding(token, boardSpaceEntity, partyController);
        }
    }

    /** The turn goes on when the shop stop ends (see {@link ShopStops}). */
    @Override
    public boolean keepsTurn(MobEntity token) {
        return ShopStops.isShopping(token.getUuid());
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.SHOP;
    }

    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        setColor(boardSpaceBlockEntity, ShopCartridgeItem.COLOR);
    }

    /** How many things its stop lets buy. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (stack.getItem() instanceof ShopCartridgeItem)
            info.line(TileInfo.line("shop", TileInfo.value(ShopCartridgeItem.purchases(stack))));
    }
}
