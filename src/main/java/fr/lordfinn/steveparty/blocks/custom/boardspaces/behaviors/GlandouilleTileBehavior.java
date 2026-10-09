package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.items.custom.cartridges.GlandouilleCartridgeItem;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.GlandouillePushes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The role of a Glandouille Cartridge: a token stopping here is pushed {@link GlandouilleCartridgeItem#distance}
 * spaces on (back along the path, as a Reversed die moves it, if negative) by a tower of
 * {@link GlandouilleCartridgeItem#tower} Glandouilles, with the tokens the tower meets (one
 * falls off for each; out of Glandouilles, it stops short, see {@link GlandouillePushes}); the turn
 * goes on once the tower has gone. A cartridge without destination (0 spaces), or a tile leading nowhere: nothing
 * happens (a plain landing). The lone Glandouille setting: it tries, fails, sulks; nobody moves. Going over the tile
 * does nothing. Outside a party it plays too, when a token's move ends here.
 */
public class GlandouilleTileBehavior extends MobTileBehavior {

    public GlandouilleTileBehavior() {
        super(BoardSpaceType.TILE_GLANDOUILLE);
    }

    /** Free play: a token ending its move on a Glandouille tile outside a party is pushed too. */
    public static void initialize() {
        TileReachedEvent.EVENT.register((token, tile) -> {
            if (token.getWorld() instanceof ServerWorld world && tile != null
                    && tile.getBoardSpaceBehavior() instanceof GlandouilleTileBehavior
                    && token instanceof TokenizedEntityInterface tokenized && tokenized.steveparty$getNbSteps() == 0
                    && ABoardSpaceBlock.countsAsStep(tile.getCachedState().getBlock())
                    && !TileFeedback.isInRunningParty(token.getUuid()) && !GlandouillePushes.isRunning(token)) {
                play(world, tile, token, () -> {
                });
            }
            return ActionResult.PASS;
        });
    }

    /** What happens when {@code token} stops on {@code tile}; {@code onDone} runs when it is over. True if anything happens. */
    public static boolean play(ServerWorld world, BoardSpaceBlockEntity tile, MobEntity token, Runnable onDone) {
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        int distance = GlandouilleCartridgeItem.distance(cartridge);
        if (distance == 0) return false;
        BlockPos pos = tile.getPos();
        // backward: the way a Reversed die walks a token back (the way it came, then the links backward)
        List<BlockPos> route = distance > 0 ? GlandouillePushes.route(world, pos, distance)
                : AdvanceBackMoves.planBack(world, token, pos, -distance).spaces();
        if (GlandouilleCartridgeItem.lone(cartridge)) {
            return GlandouillePushes.pushAlone(world, pos, token, route.isEmpty() ? null : route.getFirst(), onDone);
        }
        return GlandouillePushes.pushTower(world, pos, route, token, GlandouilleCartridgeItem.tower(cartridge), onDone);
    }

    @Override
    public boolean keepsTurn(MobEntity token) {
        return GlandouillePushes.isRunning(token);
    }

    /** It plays outside a party too (see {@link #initialize}). */
    @Override
    protected boolean partyOnly() {
        return false;
    }

    @Override
    protected void startShow(ServerWorld world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity tile,
                             @Nullable PartyControllerEntity party, Runnable onDone) {
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        boolean lone = GlandouilleCartridgeItem.lone(cartridge);
        int distance = GlandouilleCartridgeItem.distance(cartridge);
        if (!play(world, tile, token, onDone)) {
            landPlain(world, tile, token, party);
            return;
        }
        // No second move during the push (a dice rolled meanwhile would move it again)
        if (!lone && token instanceof TokenizedEntityInterface tokenized) {
            tokenized.steveparty$setStatus(TokenStatus.clearStatus(tokenized.steveparty$getStatus(), TokenStatus.CAN_MOVE));
        }
        TileFeedback.Landing landing = TileFeedback.Landing.GLANDOUILLE;
        if (lone) TileFeedback.land(world, tile, token, party, landing, landing.noticeKey() + ".lone");
        else if (distance < 0) TileFeedback.land(world, tile, token, party, landing, landing.noticeKey() + ".back", -distance);
        else TileFeedback.land(world, tile, token, party, landing, landing.noticeKey(), distance);
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return GlandouilleCartridgeItem.distance(stack) == 0 ? TileFeedback.Landing.DEFAULT : TileFeedback.Landing.GLANDOUILLE;
    }

    /** How far its tower pushes the tokens. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        int distance = GlandouilleCartridgeItem.distance(stack);
        if (distance == 0) return;
        info.line(distance > 0 ? TileInfo.line("glandouille", TileInfo.value(distance))
                : TileInfo.line("glandouille.back", TileInfo.bad(-distance)));
    }
}
