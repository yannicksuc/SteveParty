package fr.lordfinn.steveparty.blocks.custom.PartyController.journal;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.powerups.PowerUpLimit;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where the game writes down, in the journal of the party it plays for ({@link PartyJournal}), what it does during a
 * party: the items it moves ({@link #moved}) and the board spaces it changes ({@link #space}). Nothing is written outside
 * a running party (no frame open), nor for what players and the map maker do themselves.
 */
public final class TurnJournal {
    private TurnJournal() {
    }

    /**
     * The game moved {@code count} items like {@code item} from {@code from} to {@code to}. {@code party}: the party it
     * did it for, null to find it out from a player or a bank of the move.
     */
    public static void moved(@Nullable PartyControllerEntity party, JournalPlace from, JournalPlace to, ItemStack item, int count) {
        // Items from nowhere known have nowhere to go back to: nothing to undo
        if (count <= 0 || item.isEmpty() || from.equals(to) || from == JournalPlace.GONE) return;
        PartyControllerEntity journalOf = party != null ? party : partyOf(from, to);
        if (journalOf == null || journalOf.isRemoved() || !journalOf.getPartyData().isStarted()) return;
        if (journalOf.getJournal().current() == null) return;
        journalOf.getJournal().record(new JournalTransfer(from, to, item.copyWithCount(1), count));
        journalOf.journalChanged();
    }

    /**
     * Items pooled then dealt out (a share between players): each part dealt is noted as coming from the first ones put
     * in, in order, so that each player who received gives back to those who paid.
     */
    public static void shared(@Nullable PartyControllerEntity party, ItemStack item, Map<JournalPlace, Integer> paidIn,
                              Map<JournalPlace, Integer> dealtOut) {
        List<JournalPlace> sources = new ArrayList<>(paidIn.keySet());
        List<Integer> left = new ArrayList<>(paidIn.values());
        int next = 0;
        for (Map.Entry<JournalPlace, Integer> out : dealtOut.entrySet()) {
            int wanted = out.getValue();
            while (wanted > 0 && next < sources.size()) {
                int part = Math.min(wanted, left.get(next));
                moved(party, sources.get(next), out.getKey(), item, part);
                wanted -= part;
                left.set(next, left.get(next) - part);
                if (left.get(next) <= 0) next++;
            }
        }
    }

    /** {@code space} is about to change something a party may undo (its Trap mark, a counter, a cartridge's state). */
    public static void space(BoardSpaceBlockEntity space) {
        if (!(space.getWorld() instanceof ServerWorld world)) return;
        PartyControllerEntity party = PartyControllerEntity.getPartyOfBoardSpace(world, space.getPos(),
                PartyControllerEntity.BOARD_NEARBY_RADIUS, false).orElse(null);
        if (party == null || party.getJournal().current() == null) return;
        party.getJournal().space(space);
        party.journalChanged();
    }

    /** {@link #space(BoardSpaceBlockEntity)} for the board space at {@code pos}, if it is one. */
    public static void space(ServerWorld world, BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space) space(space);
    }

    /** The party of a player of the move, else the controller of its bank. */
    private static @Nullable PartyControllerEntity partyOf(JournalPlace from, JournalPlace to) {
        for (JournalPlace place : new JournalPlace[]{to, from}) {
            if (place instanceof JournalPlace.Player player) {
                PartyControllerEntity party = PowerUpLimit.partyOf(player.id());
                if (party != null) return party;
            }
        }
        for (JournalPlace place : new JournalPlace[]{from, to}) {
            if (place instanceof JournalPlace.Bank bank) {
                for (PartyControllerEntity controller : PartyControllerEntity.getActivePartyControllers()) {
                    if (!controller.isRemoved() && controller.getGlobalPos().equals(bank.controller())) return controller;
                }
            }
        }
        return null;
    }
}
