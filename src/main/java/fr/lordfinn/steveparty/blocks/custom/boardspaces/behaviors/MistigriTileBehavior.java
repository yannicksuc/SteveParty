package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import java.util.List;
import java.util.Comparator;
import java.util.ArrayList;
import net.minecraft.text.Text;
import fr.lordfinn.steveparty.items.custom.cartridges.MistigriCartridgeItem;
import fr.lordfinn.steveparty.components.MistigriOutcomes;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.service.AdvanceBackMoves;
import fr.lordfinn.steveparty.service.MistigriSentences;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * The role of a Mistigri Cartridge: a token stopping here meets the Mistigri, who rolls his loaded die and passes a
 * sentence on its player (see {@link MistigriSentences}); the turn goes on once he has gone (or once a move back has
 * landed). Only in a running party (coins and stars are the party's). Nobody to play the token, or no sentence left
 * in the cartridge: a plain landing and a message. Going over the tile does nothing.
 */
public class MistigriTileBehavior extends MobTileBehavior {

    public MistigriTileBehavior() {
        super(BoardSpaceType.TILE_MISTIGRI);
    }

    @Override
    public boolean keepsTurn(MobEntity token) {
        return MistigriSentences.isRunning(token) || AdvanceBackMoves.isWaiting(token);
    }

    @Override
    protected void startShow(ServerWorld world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity tile,
                             @Nullable PartyControllerEntity party, Runnable onDone) {
        ItemStack cartridge = tile.getActiveCartridgeItemStack();
        MistigriSentences.Start start = MistigriSentences.start(world, pos, token, party, cartridge, null, onDone);
        TileFeedback.Landing landing = start == MistigriSentences.Start.STARTED ? TileFeedback.Landing.MISTIGRI : TileFeedback.Landing.DEFAULT;
        TileFeedback.land(world, tile, token, party, landing);
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TileFeedback.Landing.MISTIGRI;
    }

    @Override
    public Status getStatus(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        return Status.BAD;
    }

    /** In game: his three likeliest sentences and their chances. Helmet: the others. */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof MistigriCartridgeItem)) return;
        MistigriOutcomes outcomes = MistigriOutcomes.of(stack);
        double[] chances = outcomes.chances();
        List<Integer> rows = new ArrayList<>();
        for (int row = 0; row < chances.length; row++) if (chances[row] > 0) rows.add(row);
        rows.sort(Comparator.comparingDouble(row -> -chances[row]));
        for (int i = 0; i < rows.size(); i++) {
            MistigriOutcomes.Outcome outcome = outcomes.get(rows.get(i));
            Text line = TileInfo.line("mistigri", TileInfo.rgb(Math.round(100 * chances[rows.get(i)]) + "%",
                    outcome.kind().tone() > 0 ? TileInfo.GOOD : TileInfo.BAD), outcome.text());
            info.line(ItemStack.EMPTY, line, i < 3 ? TileInfo.Layer.PLAY : TileInfo.Layer.DETAIL);
        }
    }
}
