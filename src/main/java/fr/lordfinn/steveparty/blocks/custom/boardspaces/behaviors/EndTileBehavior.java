package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyEndRules;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.items.custom.cartridges.EndCartridgeItem;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * « Fin »: the finish of the board (EndCartridgeItem). A pawn reaching it stops there, and its party heads for its end
 * as the cartridge says (see {@link PartyEndRules}).
 */
public class EndTileBehavior extends ABoardSpaceBehavior {
    public EndTileBehavior() {
        super(BoardSpaceType.TILE_END);
    }

    @Override
    public boolean needToStop(World world, BlockPos pos) {
        return true;
    }

    @Override
    public void onDestinationReached(World world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity boardSpaceEntity, PartyControllerEntity partyController) {
        if (!(world instanceof ServerWorld serverWorld) || boardSpaceEntity == null) return;
        TileFeedback.land(serverWorld, boardSpaceEntity, token, partyController, TileFeedback.Landing.STOP);
        if (isPartyRunning(partyController))
            PartyEndRules.arrived(partyController, serverWorld, token, boardSpaceEntity.getPos(), boardSpaceEntity.getActiveCartridgeItemStack());
    }

    /** What comes when a pawn arrives, and how the party is ranked. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof EndCartridgeItem)) return;
        String k = "gui.steveparty.cartridge_menu.end.";
        info.line(TileInfo.Glyph.CYCLE, TileInfo.line("end.ending",
                TileInfo.value(Text.translatable("hud.steveparty.tile_info.end.ending."
                        + EndCartridgeItem.ending(stack).id()))));
        info.line(TileInfo.Glyph.ALL, TileInfo.line("end.ranking",
                TileInfo.value(Text.translatable(k + "ranking."
                        + EndCartridgeItem.ranking(stack).id()))));
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.STOP;
    }
}
