package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.CommonPots;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * The role of a Common pot Cartridge (see {@link CommonPots}): a token passing over the space (walking forward) puts
 * its stake in, a token stopping on it wins the pot (in a party at its landing, outside a party when it stops there).
 * The space looks after its nest and its Pie ({@link CommonPots#care}). On a check point tokens only pass: the pot
 * grows there and is never won (put it on a tile).
 */
public class PotTileBehavior extends ABoardSpaceBehavior {
    public PotTileBehavior() {
        super(BoardSpaceType.TILE_POT);
    }

    @Override
    public boolean onTokenReached(ServerWorld world, BoardSpaceBlockEntity space, MobEntity token, int stepsLeft) {
        if (CommonPots.potOf(space) == null) return false;
        if (stepsLeft > 0) {
            if (!AdvanceBackMoves.isRouted(token)) CommonPots.pass(world, space, token);
        } else if (ABoardSpaceBlock.countsAsStep(space.getCachedState().getBlock())
                && PartyControllerEntity.getRunningPartyOf(token.getUuid()).isEmpty()) {
            // Outside a party there is no landing: it wins when it stops here
            CommonPots.win(world, space, token);
        }
        return false;
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity,
                                     @Nullable PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        CommonPots.Win win = CommonPots.win(serverWorld, boardSpaceEntity, token);
        TileFeedback.Landing landing = win.isEmpty() ? TileFeedback.Landing.DEFAULT : TileFeedback.Landing.GOOD;
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, landing,
                win.isEmpty() ? landing.noticeKey() : "message.steveparty.tile_landed.pot", win.coins());
    }

    @Override
    public void tick(ServerWorld world, BoardSpaceBlockEntity state, ItemStack type, int ticks) {
        if (ticks % CommonPots.CARE_INTERVAL == 0 && type.getItem() instanceof PotCartridgeItem) CommonPots.care(world, state, type);
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.GOOD;
    }

    @Override
    public Status getStatus(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        return Status.GOOD;
    }

    /** What the pot holds (its coins, the items stolen into it) and what passing costs. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof PotCartridgeItem)) return;
        info.line(new ItemStack(ModItems.COIN), TileInfo.line("pot", TileInfo.coins(PotCartridgeItem.coins(stack))));
        info.line(TileInfo.line("pot.stake", TileInfo.coins(PotCartridgeItem.stake(stack))));
        for (ItemStack item : PotCartridgeItem.items(stack)) info.item(item);
    }
}
