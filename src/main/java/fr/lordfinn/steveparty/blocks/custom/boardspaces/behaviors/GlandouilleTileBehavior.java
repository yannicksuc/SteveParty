package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.entities.TokenStatus;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.events.TileReachedEvent;
import fr.lordfinn.steveparty.items.custom.cartridges.GlandouilleCartridgeItem;
import fr.lordfinn.steveparty.service.GlandouillePushes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The role of a Glandouille Cartridge: a token stopping here is pushed {@link GlandouilleCartridgeItem#distance}
 * spaces on by a tower of {@link GlandouilleCartridgeItem#tower} Glandouilles, with the tokens the tower meets (one
 * falls off for each; out of Glandouilles, it stops short, see {@link GlandouillePushes}); the turn
 * goes on once the tower has gone. A cartridge without destination (0 spaces), or a tile leading nowhere: nothing
 * happens (a plain landing). The lone Glandouille setting: it tries, fails, sulks; nobody moves. Going over the tile
 * does nothing. Outside a party it plays too, when a token's move ends here.
 */
public class GlandouilleTileBehavior extends ABoardSpaceBehavior {

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
        List<BlockPos> route = GlandouillePushes.route(world, pos, distance);
        if (GlandouilleCartridgeItem.lone(cartridge)) {
            return GlandouillePushes.pushAlone(world, pos, token, route.isEmpty() ? null : route.getFirst(), onDone);
        }
        return GlandouillePushes.pushTower(world, pos, route, token, GlandouilleCartridgeItem.tower(cartridge), onDone);
    }

    @Override
    public boolean keepsTurn(MobEntity token) {
        return GlandouillePushes.isRunning(token);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity,
                                     @Nullable PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        PartyStep step = partyController == null ? null : partyController.getPartyData().getCurrentStep();
        ItemStack cartridge = boardSpaceEntity.getActiveCartridgeItemStack();
        boolean lone = GlandouilleCartridgeItem.lone(cartridge);
        int distance = GlandouilleCartridgeItem.distance(cartridge);
        boolean played = play(serverWorld, boardSpaceEntity, token, () -> {
            if (partyController != null && !partyController.isRemoved() && step != null
                    && partyController.getPartyData().getCurrentStep() == step) {
                partyController.nextStep();
            }
        });
        if (!played) {
            TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, TileFeedback.Landing.DEFAULT,
                    TileFeedback.Landing.DEFAULT.noticeKey());
            return;
        }
        // No second move during the push (a dice rolled meanwhile would move it again)
        if (!lone && token instanceof TokenizedEntityInterface tokenized) {
            tokenized.steveparty$setStatus(TokenStatus.clearStatus(tokenized.steveparty$getStatus(), TokenStatus.CAN_MOVE));
        }
        TileFeedback.Landing landing = TileFeedback.Landing.GLANDOUILLE;
        if (lone) TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing, landing.noticeKey() + ".lone");
        else TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing, landing.noticeKey(), distance);
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return GlandouilleCartridgeItem.distance(stack) == 0 ? TileFeedback.Landing.DEFAULT : TileFeedback.Landing.GLANDOUILLE;
    }

    @Override
    public void updateBoardSpaceColor(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        if (!stack.contains(fr.lordfinn.steveparty.components.ModComponents.COLOR)) setColor(boardSpaceBlockEntity, GlandouilleCartridgeItem.COLOR);
    }
}
