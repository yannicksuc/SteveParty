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
 * taken from the cartridge's linked chests; a slow die picks the head whose prize the token's player wins (see
 * {@link TrichaudronPrizes}). The turn goes on once it has gone. Only in a running party. Nothing to give (no chest,
 * empty chests, none of the items set) and the space sleeps (its face dimmed, its panel « empty » or « no chest »): a
 * plain landing. Going over the tile does nothing.
 */
public class TrichaudronTileBehavior extends MobTileBehavior {

    public TrichaudronTileBehavior() {
        super(BoardSpaceType.TILE_TRICHAUDRON);
    }

    /** How often (ticks) a space looks into its chests to know whether it sleeps (its tile's face). */
    public static final int SLEEP_CHECK_TICKS = 40;

    /** Looks into its chests now and then: asleep or awake, its tile's face follows. */
    @Override
    public void tick(ServerWorld world, BoardSpaceBlockEntity space, ItemStack type, int ticks) {
        if (ticks % SLEEP_CHECK_TICKS == Math.floorMod(space.getPos().hashCode(), SLEEP_CHECK_TICKS)) refreshSleep(world, space);
    }

    /** Whether {@code space}'s chests have anything to give, recorded on its cartridge (its face). */
    public static void refreshSleep(ServerWorld world, BoardSpaceBlockEntity space) {
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof TrichaudronCartridgeItem)) return;
        refreshSleep(space, !TrichaudronCartridgeItem.available(cartridge, world).isEmpty());
    }

    /** {@code space} has prizes to give or not: its cartridge records it, its tile is sent again if that changed. */
    public static void refreshSleep(BoardSpaceBlockEntity space, boolean hasPrizes) {
        ItemStack cartridge = space.getActiveCartridgeItemStack();
        if (!(cartridge.getItem() instanceof TrichaudronCartridgeItem) || !TrichaudronCartridgeItem.setAsleep(cartridge, !hasPrizes)) return;
        space.markDirty();
        space.update();
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
            boolean chests = TrichaudronCartridgeItem.hasChests(tile.getActiveCartridgeItemStack());
            BoardSequences.tell(party, Text.translatable(chests ? "message.steveparty.trichaudron_space.asleep"
                    : "message.steveparty.trichaudron_space.no_chest").formatted(Formatting.GRAY));
        }
    }

    /** Asleep (as last seen in its chests): a plain landing. */
    @Override
    public TileFeedback.Landing landing(BoardSpaceBlockEntity boardSpaceEntity, ItemStack stack) {
        return TrichaudronCartridgeItem.isAsleep(stack) ? TileFeedback.Landing.DEFAULT : TileFeedback.Landing.TRICHAUDRON;
    }

    @Override
    public Status getStatus(BoardSpaceBlockEntity boardSpaceBlockEntity, ItemStack stack) {
        return TrichaudronCartridgeItem.isAsleep(stack) ? Status.NEUTRAL : Status.GOOD;
    }

    /**
     * In game: what its chests can give now, circling over the space, and how many; nothing: « no chest » or « empty ».
     */
    @Override
    public void describe(ServerWorld world, BoardSpaceBlockEntity space, ItemStack stack, TileInfo.Builder info) {
        if (!(stack.getItem() instanceof TrichaudronCartridgeItem)) return;
        List<ItemStack> prizes = TrichaudronCartridgeItem.available(stack, world);
        refreshSleep(space, !prizes.isEmpty());
        if (prizes.isEmpty()) {
            info.line(TileInfo.dim(TileInfo.line(TrichaudronCartridgeItem.hasChests(stack) ? "trichaudron.empty" : "trichaudron.no_chest")));
            return;
        }
        info.line(TileInfo.good(TileInfo.line("trichaudron.prizes", prizes.size())));
        for (ItemStack prize : prizes) info.item(prize);
    }
}
