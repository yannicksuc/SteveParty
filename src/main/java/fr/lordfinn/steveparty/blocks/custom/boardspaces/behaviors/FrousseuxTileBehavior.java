package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.items.custom.cartridges.FrousseuxCartridgeItem;
import fr.lordfinn.steveparty.service.FrousseuxThefts;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The role of a Frousseux Cartridge: a token stopping here sends a Frousseux to steal coins or stars from another
 * player, picked by its player (see {@link FrousseuxThefts}); the turn goes on once the Frousseux has vanished. Only
 * in a running party (coins and stars are the party's). No other player, or nobody to play the token: a plain landing
 * and a message. Going over the tile does nothing.
 */
public class FrousseuxTileBehavior extends MobTileBehavior {

    public FrousseuxTileBehavior() {
        super(BoardSpaceType.TILE_FROUSSEUX, FrousseuxCartridgeItem.COLOR);
    }

    @Override
    public boolean keepsTurn(MobEntity token) {
        return FrousseuxThefts.isRunning(token);
    }

    @Override
    protected void startShow(ServerWorld world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity tile,
                             @Nullable PartyControllerEntity party, Runnable onDone) {
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        boolean stars = FrousseuxCartridgeItem.stealsStars(cartridge);
        FrousseuxThefts.Start start = FrousseuxThefts.start(world, pos, token, party, stars,
                FrousseuxCartridgeItem.amount(cartridge), onDone);
        TileFeedback.Landing landing = start == FrousseuxThefts.Start.STARTED ? TileFeedback.Landing.FROUSSEUX : TileFeedback.Landing.DEFAULT;
        TileFeedback.land(world, tile, token, party, landing, landing.noticeKey());
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.FROUSSEUX;
    }
}
