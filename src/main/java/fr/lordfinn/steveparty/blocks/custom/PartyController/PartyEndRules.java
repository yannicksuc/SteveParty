package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.EventPartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.items.custom.cartridges.EndCartridgeItem;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.MessageUtils;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.UUID;

/**
 * What a pawn reaching an End space (EndCartridgeItem) does to its party: it is noted among the arrivals, in its
 * order, and the steps still to come are cut as the cartridge says (the round played out, the end right after this
 * turn, one more turn for the others, or the pawns that arrived set aside on the End space until every pawn arrives).
 * The steps are only cut once, at the first arrival (the others arriving meanwhile only add to the arrivals), except
 * while waiting for everyone.
 */
public final class PartyEndRules {
    private PartyEndRules() {
    }

    /** {@code token} stopped on the End space at {@code space} whose cartridge is {@code cartridge}. */
    public static void arrived(PartyControllerEntity party, ServerWorld world, MobEntity token, BlockPos space, ItemStack cartridge) {
        PartyData data = party.getPartyData();
        UUID uuid = token.getUuid();
        if (!data.isStarted() || !data.getTokens().contains(uuid) || data.getArrivals().contains(uuid)) return;
        boolean first = data.getArrivals().isEmpty();
        data.getArrivals().add(uuid);
        EndCartridgeItem.Ending ending = EndCartridgeItem.ending(cartridge);
        if (first) data.setEndRanking(EndCartridgeItem.ranking(cartridge).ordinal());
        announce(party, world, uuid, data.getArrivals().size(), ending, first);
        switch (ending) {
            case FINISH_ROUND -> {
                if (first) cutAfterRound(data);
            }
            case AT_ONCE -> {
                if (first) cutAfterCurrent(data);
            }
            case ONE_MORE_TURN -> {
                if (first) {
                    cutAfterRound(data);
                    oneMoreTurn(party, world, data);
                }
            }
            case WAIT_FOR_ALL -> {
                // Set aside on the End space, its turns gone; once every pawn is there, the party ends
                removeTurnsOf(data, uuid);
                party.tokenHomes().parking().park(world, uuid, space);
                if (data.getArrivals().containsAll(data.getTokens())) cutAfterCurrent(data);
            }
        }
        party.markDirty();
        party.sendPacketToInterestedPlayers();
    }

    /** Everyone of the party hears who arrived, and what comes next. */
    private static void announce(PartyControllerEntity party, ServerWorld world, UUID token, int place,
                                 EndCartridgeItem.Ending ending, boolean first) {
        Text name = party.getTokenDisplayName(world, token);
        Text line = Text.translatableWithFallback("message.steveparty.end_space.arrived", "%s arrives %s!", name,
                Podiums.placeText(place)).styled(style -> style.withColor(0xFFC52E).withBold(first));
        MessageUtils.sendToPlayers(party.getPartyAudience(), line, MessageUtils.MessageType.CHAT);
        if (first) MessageUtils.sendToPlayers(party.getPartyAudience(),
                Text.translatable("message.steveparty.end_space.next." + ending.id()).styled(style -> style.withColor(0xE0E0E0)),
                MessageUtils.MessageType.CHAT);
        for (var player : party.getPartyAudience())
            player.playSoundToPlayer(first ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.ENTITY_PLAYER_LEVELUP,
                    SoundCategory.PLAYERS, 0.7f, first ? 1f : 1.4f);
    }

    /** The index of the party's END step (the last step), -1 if none. */
    private static int endIndex(PartyData data) {
        List<PartyStep> steps = data.getSteps();
        for (int i = steps.size() - 1; i >= 0; i--) if (steps.get(i).getType() == PartyStepType.END) return i;
        return -1;
    }

    /** Nothing more is played after the current step: the END step comes next. */
    static void cutAfterCurrent(PartyData data) {
        int end = endIndex(data), from = data.getStepIndex() + 1;
        if (end > from) data.getSteps().subList(from, end).clear();
    }

    /**
     * The turns of the round still to come are played, then the END step: the turns that follow in the play order (a
     * replay too), up to the first one going back in it (the next round, even with no mini-game in between).
     */
    static void cutAfterRound(PartyData data) {
        List<PartyStep> steps = data.getSteps();
        int end = endIndex(data), from = data.getStepIndex() + 1;
        int rank = data.getCurrentStep() instanceof TokenTurnPartyStep turn ? data.getTokenRank(turn.getTokenUUID()) : 0;
        while (from < end) {
            PartyStep step = steps.get(from);
            if (step instanceof EventPartyStep event && event.isTransition()) {
                from++;
                continue;
            }
            if (!(step instanceof TokenTurnPartyStep turn) || data.getTokenRank(turn.getTokenUUID()) < rank) break;
            rank = data.getTokenRank(turn.getTokenUUID());
            from++;
        }
        if (end > from) steps.subList(from, end).clear();
    }

    /** One more turn for each pawn that has not arrived, in the play order, before the END step. */
    private static void oneMoreTurn(PartyControllerEntity party, ServerWorld world, PartyData data) {
        int end = endIndex(data);
        if (end < 0) return;
        int at = end;
        for (UUID token : data.getTokens()) {
            if (data.getArrivals().contains(token)) continue;
            TokenTurnPartyStep turn = new TokenTurnPartyStep(token, party.getTokenOwner(token));
            turn.setTokenName(party.getTokenDisplayName(world, token).getString());
            data.getSteps().add(at++, turn);
        }
    }

    /** The turns of {@code token} after the current step are gone (it waits on the End space). */
    private static void removeTurnsOf(PartyData data, UUID token) {
        List<PartyStep> steps = data.getSteps();
        for (int i = steps.size() - 1; i > data.getStepIndex(); i--) {
            if (steps.get(i) instanceof TokenTurnPartyStep turn && token.equals(turn.getTokenUUID())) steps.remove(i);
        }
    }
}
