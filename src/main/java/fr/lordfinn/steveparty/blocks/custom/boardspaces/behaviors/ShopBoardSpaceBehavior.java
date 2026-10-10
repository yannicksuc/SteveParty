package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import net.minecraft.server.world.ServerWorld;
import fr.lordfinn.steveparty.blocks.custom.TradingStallBlockEntity;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.service.BoardShop;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import java.util.List;
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
 * owner, and the turn waits for the stop (the space's merchant appears meanwhile); a check point stops the tokens passing
 * through instead (see
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

    /**
     * In game: how many things its stop lets buy, then each offer: its price with its real currency's icon, what it
     * sells (« sold out » when its stock lacks it), and the items sold circling over the space. No offer: says so.
     */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof ShopCartridgeItem)) return;
        List<TradingStallBlockEntity.ExactTradeOffer> offers = ShopCartridgeItem.offers(stack);
        if (offers.isEmpty()) {
            info.line(TileInfo.Glyph.SHOP, TileInfo.dim(TileInfo.line("shop.no_offer")));
            return;
        }
        info.line(TileInfo.Glyph.SHOP, TileInfo.value(TileInfo.line("shop", ShopCartridgeItem.purchases(stack))));
        BoardShop shop = new BoardShop(world, space.getPos());
        for (TradingStallBlockEntity.ExactTradeOffer offer : offers) {
            ItemStack sold = offer.getSellItem();
            MutableText price = TileInfo.coins(offer.getFirstPrice().getCount());
            if (!offer.getSecondPrice().isEmpty())
                price = TileInfo.line("shop.price_and", price, offer.getSecondPrice().getCount(), offer.getSecondPrice().getName());
            Text item = sold.getCount() > 1 ? TileInfo.line("shop.many", sold.getName(), sold.getCount()) : sold.getName();
            boolean soldOut = !shop.inStock(sold);
            info.line(offer.getFirstPrice(), soldOut ? TileInfo.dim(TileInfo.line("shop.sold_out", price, item))
                    : TileInfo.line("shop.offer", price, item));
            info.item(sold);
        }
        // No container of its own: the party's bank
        if (CartridgeContainers.partyBankOf(stack, world, space.getPos()) != null) info.line(TileInfo.value(TileInfo.line("party_bank")));
    }
}
