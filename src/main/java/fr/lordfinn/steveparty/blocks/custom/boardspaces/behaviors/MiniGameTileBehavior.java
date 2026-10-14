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
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.minigame.MiniGamePageData;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.UUID;

/**
 * « Mini-jeu »: a pawn stopping here starts a mini-game right after its turn (MiniGameCartridgeItem), its teams made
 * by the cartridge's format, the pawn's owner being the one who landed.
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
                MiniGameCartridgeItem.formats(stack), partyController.getTokenOwner(token.getUuid()), MiniGameCartridgeItem.page(stack)));
    }

    /** Its formats, and its page when it always plays one. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof MiniGameCartridgeItem)) return;
        info.line(TileInfo.Glyph.ALL, TileInfo.line("minigame.format", TileInfo.value(MiniGameCartridgeItem.formatsText(stack))));
        UUID page = MiniGameCartridgeItem.page(stack);
        if (page == null) return;
        String title = MiniGamePages.find(world.getServer(), page).map(MiniGamePageData::title).orElse("");
        info.line(new ItemStack(ModItems.MINI_GAME_PAGE), TileInfo.line("minigame.page", TileInfo.value(
                title.isEmpty() ? ModItems.MINI_GAME_PAGE.getName() : Text.literal(title))));
    }
}
