package fr.lordfinn.steveparty.blocks.custom.boardspaces.behaviors;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileFeedback;
import fr.lordfinn.steveparty.board.TileInfo;
import fr.lordfinn.steveparty.items.custom.cartridges.TrichaudronCartridgeItem;
import fr.lordfinn.steveparty.service.BoardSequences;
import fr.lordfinn.steveparty.service.TrichaudronPrizes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The role of a Trichaudron Cartridge: a token stopping here meets the Trichaudron, whose three heads hold hidden prizes
 * from the cartridge's stock; a slow die picks the head whose prize the token's player wins (see
 * {@link TrichaudronPrizes}). The turn goes on once it has gone. Only in a running party. Out of prizes the space
 * sleeps (its face dimmed, its panel « empty »): a plain landing. Going over the tile does nothing.
 */
public class TrichaudronTileBehavior extends MobTileBehavior {

    public TrichaudronTileBehavior() {
        super(BoardSpaceType.TILE_TRICHAUDRON);
    }

    @Override
    public boolean keepsTurn(MobEntity token) {
        return TrichaudronPrizes.isRunning(token);
    }

    @Override
    protected void startShow(ServerWorld world, BlockPos pos, MobEntity token, BoardSpaceBlockEntity tile,
                             @Nullable PartyControllerEntity party, Runnable onDone) {
        TrichaudronPrizes.Start start = TrichaudronPrizes.start(world, pos, token, party, onDone);
        if (start == TrichaudronPrizes.Start.STARTED) {
            TileFeedback.land(world, tile, token, party, TileFeedback.Landing.TRICHAUDRON);
            return;
        }
        landPlain(world, tile, token, party);
        if (start == TrichaudronPrizes.Start.EMPTY && party != null) {
            BoardSequences.tell(party, Text.translatable("message.steveparty.trichaudron_space.asleep").formatted(Formatting.GRAY));
        }
    }

    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TrichaudronCartridgeItem.isEmpty(stack) ? TileFeedback.Landing.DEFAULT : TileFeedback.Landing.TRICHAUDRON;
    }

    @Override
    public Status getStatus(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        return TrichaudronCartridgeItem.isEmpty(stack) ? Status.NEUTRAL : Status.GOOD;
    }

    /** In game: how many prizes are left, circling over the space; out of prizes, « empty ». */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof TrichaudronCartridgeItem)) return;
        List<ItemStack> prizes = TrichaudronCartridgeItem.prizes(stack);
        if (prizes.isEmpty()) {
            info.line(TileInfo.dim(TileInfo.line("trichaudron.empty")));
            return;
        }
        info.line(TileInfo.good(TileInfo.line("trichaudron.prizes", prizes.size())));
        for (ItemStack prize : prizes) info.item(prize);
    }
}
