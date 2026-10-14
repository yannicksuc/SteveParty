package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.EventTimeOut;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.items.custom.cartridges.EventCartridgeItem;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * « Événement »: a pawn stopping here starts a time out of its party right after its turn (EventCartridgeItem,
 * {@link EventTimeOut}): the event bells ring, the players come out of the cartridge's pipes until the event ends.
 */
public class EventTileBehavior extends ABoardSpaceBehavior {
    public EventTileBehavior() {
        super(BoardSpaceType.TILE_EVENT);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        super.onDestinationReached(world, pos, token, boardSpaceEntity, partyController);
        if (boardSpaceEntity == null || !isPartyRunning(partyController)) return;
        ItemStack stack = boardSpaceEntity.getActiveCartridgeItemStack();
        if (!(stack.getItem() instanceof EventCartridgeItem)) return;
        slipIn(partyController, EventPartyStep.eventSpace(EventCartridgeItem.channel(stack),
                EventCartridgeItem.pipes(stack), EventCartridgeItem.seconds(stack)));
    }

    /** Its hourglass and its pipes. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof EventCartridgeItem)) return;
        info.line(TileInfo.Glyph.CYCLE, TileInfo.line("event.hourglass",
                TileInfo.value(EventTimeOut.durationText(EventCartridgeItem.seconds(stack)))));
        int pipes = EventCartridgeItem.pipes(stack).size();
        if (pipes > 0) info.line(TileInfo.Glyph.ALL, TileInfo.line("event.pipes", TileInfo.value(pipes)));
    }
}
