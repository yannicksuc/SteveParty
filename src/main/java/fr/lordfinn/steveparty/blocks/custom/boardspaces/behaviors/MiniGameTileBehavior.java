package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.items.custom.cartridges.MiniGameCartridgeItem;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;

/**
 * « Mini-jeu »: a pawn stopping here starts a mini-game right after its turn (MiniGameCartridgeItem), its teams made
 * by the cartridge's mode, the pawn's owner being the one who landed.
 */
public class MiniGameTileBehavior extends ABoardSpaceBehavior {
    public MiniGameTileBehavior() {
        super(BoardSpaceType.TILE_MINIGAME);
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        super.onDestinationReached(world, pos, token, boardSpaceEntity, partyController);
        if (boardSpaceEntity == null || !isPartyRunning(partyController)) return;
        ItemStack stack = boardSpaceEntity.getActiveCartridgeItemStack();
        if (!(stack.getItem() instanceof MiniGameCartridgeItem)) return;
        slipIn(partyController, MiniGamePartyStep.ofSpace(new ArrayList<>(partyController.getPartyData().getTokens()),
                MiniGameCartridgeItem.mode(stack), partyController.getTokenOwner(token.getUuid())));
    }

    /** Its mode. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof MiniGameCartridgeItem)) return;
        info.line(TileInfo.Glyph.ALL, TileInfo.line("minigame.mode", TileInfo.value(
                Text.translatable("gui.steveparty.cartridge_menu.minigame.mode." + MiniGameCartridgeItem.mode(stack).id()))));
    }
}
