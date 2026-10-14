package fr.lordfinn.steveparty.blocks.custom.PartyController.journal;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyCurrencyText;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import fr.lordfinn.steveparty.service.PartyStars;
import fr.lordfinn.steveparty.service.TurnMoves;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Undoes frames of a party's turn journal, back to the start of the oldest one: the items the game moved go back where
 * they came from, the board spaces changed are as they were, the pawns stand where they stood, the party's data and star
 * are those of then. Server thread, all at once (one call, one tick): nobody can act in between.
 * <p>
 * Never duplicates anything. A transfer is undone by <em>taking back first</em> from where the items went, then giving
 * the place they came from exactly what came back: what was used, thrown, stored elsewhere or given to someone is not
 * there, so nothing comes back for it (a red line says so). A player gets nothing back of what he paid unless everything
 * he gained can be taken back from him (an item bought and given away: the price stays paid); this is worked out on the
 * real counts before anything moves ({@link #plan}), then done for real with the same rule.
 */
public final class JournalRewind {
    private JournalRewind() {
    }

    /**
     * Undoes {@code frames} (newest first, already taken out of the journal: never undone twice) and puts the party back
     * as it stood at the start of the last one.
     *
     * @return the red lines of what could not be undone, in order
     */
    public static List<Text> undo(PartyControllerEntity controller, List<JournalFrame> frames) {
        List<Text> lines = new ArrayList<>();
        if (frames.isEmpty() || !(controller.getWorld() instanceof ServerWorld world)) return lines;
        MinecraftServer server = world.getServer();
        closeScreens(controller, server);
        List<JournalTransfer> undoing = new ArrayList<>();
        for (JournalFrame frame : frames) undoing.addAll(frame.transfers.reversed());
        Set<JournalPlace> keeping = plan(server, undoing);
        List<Line> failed = new ArrayList<>();
        for (JournalTransfer transfer : undoing) undo(controller, server, transfer, keeping, failed);
        failed.forEach(line -> lines.add(line.text(server)));
        for (JournalFrame frame : frames) {
            frame.spaces.forEach((pos, state) -> {
                if (world.getBlockEntity(pos) instanceof BoardSpaceBlockEntity space) space.restoreJournalState(state);
            });
        }
        JournalFrame start = frames.getLast();
        restorePawns(controller, world, start, lines);
        controller.setPartyData(new PartyData(start.party.copy()));
        controller.setStarSpace(start.star);
        PartyStars.sync(world);
        return lines;
    }

    /** Before anything moves: what a player was trading, holding on his cursor or in a menu, is back in his inventory. */
    private static void closeScreens(PartyControllerEntity controller, MinecraftServer server) {
        Set<UUID> players = new HashSet<>(controller.getPlayersInOrder());
        controller.getPartyAudience().forEach(player -> players.add(player.getUuid()));
        for (UUID id : players) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(id);
            if (player != null && player.currentScreenHandler != player.playerScreenHandler) player.closeHandledScreen();
        }
    }

    /**
     * The players who will get nothing back: those from whom something they gained can't be taken back (worked out on
     * the real counts, newest transfer first). Leaving their refunds out only lowers what they hold, so the set only
     * grows until it holds still.
     */
    static Set<JournalPlace> plan(MinecraftServer server, List<JournalTransfer> undoing) {
        Set<JournalPlace> keeping = new HashSet<>();
        Map<JournalPlace, Optional<JournalPlace.Live>> lives = new HashMap<>();
        while (true) {
            Set<JournalPlace> shortOf = simulate(server, undoing, keeping, lives);
            if (keeping.containsAll(shortOf)) return keeping;
            keeping.addAll(shortOf);
        }
    }

    private record Held(JournalPlace place, int kind) {
    }

    private static Set<JournalPlace> simulate(MinecraftServer server, List<JournalTransfer> undoing, Set<JournalPlace> keeping,
                                              Map<JournalPlace, Optional<JournalPlace.Live>> lives) {
        List<ItemStack> kinds = new ArrayList<>();
        Map<Held, Long> available = new HashMap<>();
        Map<Held, Long> room = new HashMap<>();
        Set<JournalPlace> shortOf = new HashSet<>();
        for (JournalTransfer transfer : undoing) {
            int kind = kindOf(kinds, transfer.item());
            Held to = new Held(transfer.to(), kind), from = new Held(transfer.from(), kind);
            long there = available.computeIfAbsent(to, held -> (long) live(server, lives, held.place()).map(live -> live.available(transfer.item())).orElse(0));
            if (transfer.to() instanceof JournalPlace.Player && there < transfer.count()) shortOf.add(transfer.to());
            if (keeping.contains(transfer.from())) continue;
            long fits = room.computeIfAbsent(from, held -> (long) live(server, lives, held.place()).map(live -> live.room(transfer.item())).orElse(0));
            long move = Math.min(transfer.count(), Math.min(there, fits));
            if (move <= 0) continue;
            long fromHeld = available.computeIfAbsent(from, held -> (long) live(server, lives, held.place()).map(live -> live.available(transfer.item())).orElse(0));
            long toRoom = room.computeIfAbsent(to, held -> (long) live(server, lives, held.place()).map(live -> live.room(transfer.item())).orElse(0));
            available.put(to, there - move);
            available.put(from, fromHeld + move);
            room.put(from, fits == Integer.MAX_VALUE ? fits : fits - move);
            room.put(to, toRoom == Integer.MAX_VALUE ? toRoom : toRoom + move);
        }
        return shortOf;
    }

    /** One transfer, for real: taken back from where it went, then given to where it came from, what came back only. */
    private static void undo(PartyControllerEntity controller, MinecraftServer server, JournalTransfer transfer,
                             Set<JournalPlace> keeping, List<Line> failed) {
        ItemStack item = transfer.item();
        if (keeping.contains(transfer.from())) {
            fail(failed, Reason.KEPT, transfer.from(), item, transfer.count());
            return;
        }
        if (transfer.to() == JournalPlace.GONE) {
            fail(failed, Reason.USED, transfer.to(), item, transfer.count());
            return;
        }
        JournalPlace.Live to = transfer.to().live(server);
        JournalPlace.Live from = transfer.from().live(server);
        if (to == null || from == null) {
            JournalPlace away = to == null ? transfer.to() : transfer.from();
            fail(failed, away == JournalPlace.GONE ? Reason.USED : Reason.AWAY, away, item, transfer.count());
            return;
        }
        int there = to.available(item);
        int fits = from.room(item);
        int move = Math.min(transfer.count(), Math.min(there, fits));
        int taken = move > 0 ? to.take(item, move) : 0;
        int given = taken > 0 ? from.put(item, taken) : 0;
        if (given < taken) {
            // Never lost: what did not go in goes back where it was taken from, else falls by the Party Controller
            int back = to.put(item, taken - given);
            int fallen = taken - given - back;
            if (fallen > 0) drop(controller, item, fallen);
        }
        int missing = transfer.count() - given;
        if (missing <= 0) return;
        int notThere = Math.min(missing, Math.max(0, transfer.count() - there));
        fail(failed, Reason.NOT_THERE, transfer.to(), item, notThere);
        fail(failed, Reason.FULL, transfer.from(), item, missing - notThere);
    }

    private static void drop(PartyControllerEntity controller, ItemStack item, int count) {
        if (!(controller.getWorld() instanceof ServerWorld world)) return;
        BlockPos pos = controller.getPos();
        int left = count;
        while (left > 0) {
            int size = Math.min(left, item.getMaxCount());
            world.spawnEntity(new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, item.copyWithCount(size)));
            left -= size;
        }
    }

    private static void restorePawns(PartyControllerEntity controller, ServerWorld world, JournalFrame start, List<Text> lines) {
        Set<UUID> tokens = new HashSet<>(controller.getPartyData().getTokens());
        tokens.addAll(start.pawns.keySet());
        for (UUID id : tokens) {
            TurnMoves.forget(id);
            Steveparty.SCHEDULER.cancel(id);
            JournalFrame.Pose pose = start.pawns.get(id);
            Entity entity = world.getEntity(id);
            if (pose == null) continue;
            if (!(entity instanceof TokenizedEntityInterface token)) {
                lines.add(Text.translatable("journal.steveparty.not_undone.pawn", controller.getTokenDisplayName(world, id)).formatted(Formatting.RED));
                continue;
            }
            token.steveparty$stopMoving();
            token.steveparty$setNbSteps(0);
            entity.refreshPositionAndAngles(pose.x(), pose.y(), pose.z(), pose.yaw(), pose.pitch());
            entity.setHeadYaw(pose.headYaw());
            if (entity instanceof LivingEntity living) living.setBodyYaw(pose.bodyYaw());
        }
    }

    // ---------------------------------------------------------------- what could not be undone

    enum Reason {
        /** Not there any more where it went (used, thrown, stored, given away...). */
        NOT_THERE("journal.steveparty.not_undone.not_there"),
        /** Where it came from is full. */
        FULL("journal.steveparty.not_undone.full"),
        /** Not given back: its player did not give back everything he gained. */
        KEPT("journal.steveparty.not_undone.kept"),
        /** Out of reach (a player offline or in a mini-game, a chest unloaded or gone). */
        AWAY("journal.steveparty.not_undone.away"),
        /** Used up by the game. */
        USED("journal.steveparty.not_undone.used");

        final String key;

        Reason(String key) {
            this.key = key;
        }
    }

    private static final class Line {
        final Reason reason;
        final JournalPlace place;
        final ItemStack item;
        int count;

        Line(Reason reason, JournalPlace place, ItemStack item) {
            this.reason = reason;
            this.place = place;
            this.item = item;
        }

        Text text(MinecraftServer server) {
            MutableText amount = PartyCurrencyText.amount(item, count).styled(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackContent(item))));
            return Text.translatable(reason.key, amount, place.name(server)).formatted(Formatting.RED);
        }
    }

    private static void fail(List<Line> failed, Reason reason, JournalPlace place, ItemStack item, int count) {
        if (count <= 0) return;
        for (Line line : failed) {
            if (line.reason == reason && line.place.equals(place) && ItemStack.areItemsAndComponentsEqual(line.item, item)) {
                line.count += count;
                return;
            }
        }
        Line line = new Line(reason, place, item);
        line.count = count;
        failed.add(line);
    }

    private static Optional<JournalPlace.Live> live(MinecraftServer server, Map<JournalPlace, Optional<JournalPlace.Live>> lives, JournalPlace place) {
        return lives.computeIfAbsent(place, p -> Optional.ofNullable(p.live(server)));
    }

    private static int kindOf(List<ItemStack> kinds, ItemStack item) {
        for (int i = 0; i < kinds.size(); i++) if (ItemStack.areItemsAndComponentsEqual(kinds.get(i), item)) return i;
        kinds.add(item);
        return kinds.size() - 1;
    }
}
