package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TeamDisposition;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.items.custom.MiniGamesCatalogueItem;
import fr.lordfinn.steveparty.persistent_state.TeleportationHistoryStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadBooksStorage;
import fr.lordfinn.steveparty.persistent_state.TeleportationPadStorageManager;
import fr.lordfinn.steveparty.screen_handlers.custom.HereWeGoBookScreenHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.components.ModComponents.*;

public class HereWeGoBookItem extends AbstractTeleportationBookItem {
    public HereWeGoBookItem(Settings settings) {
        super(settings);
    }

    /**
     * Where the book sends the player, or why it can't.
     *
     * @param pos     the block to land on (null: no teleport)
     * @param failure message for the player when there is no destination (null: nothing to say)
     */
    public record Destination(@Nullable BlockPos pos, @Nullable Text failure) {
        static Destination to(@Nullable BlockPos pos) {
            return new Destination(pos, null);
        }

        static Destination fail(String key, String fallback) {
            return new Destination(null, Text.translatableWithFallback(key, fallback));
        }
    }

    public static BlockPos getTpPos(ItemStack book, PlayerEntity player) {
        return getDestination(book, player).pos();
    }

    /**
     * Resolves the destination of {@code book} for {@code player}. In mini-game mode this takes a place in the
     * current mini-game for him (the same place again if he already has one).
     */
    public static Destination getDestination(ItemStack book, PlayerEntity player) {
        State state = State.fromInt(book.getOrDefault(STATE, 0));
        return switch (state) {
            case TP_TO_MINIGAME -> getTpMiniGameDestination(player);
            case TP_BACK_LAST_USED_TP_PAD -> Destination.to(getTpBackLastUsedTpPadPos(player));
            case TP_REGISTERED_POS -> Destination.to(getTpRegisteredPos(book, player));
        };
    }

    // ------------------------------------------------------------------ mini-game mode

    /**
     * Sends the player to one of the « Here we come » books (pads) of the mini-game the nearest running party is
     * playing. Each book lists conditions (group, capacity, fill priority): the player fills a condition of his
     * group that still has room, the highest fill priority first.
     */
    private static Destination getTpMiniGameDestination(PlayerEntity player) {
        if (!(player.getWorld() instanceof ServerWorld)) return Destination.to(null);
        PartyControllerEntity partyController = getNearestActivePartyController(player);
        if (partyController == null || !(partyController.getPartyData().getCurrentStep() instanceof MiniGamePartyStep step))
            return Destination.fail("message.steveparty.here_we_go.no_minigame", "No mini-game in progress");
        if (!step.isMiniGameChosen())
            return Destination.fail("message.steveparty.here_we_go.minigame_not_chosen", "The mini-game is not chosen yet");

        ItemStack catalogue = partyController.getCatalogue();
        ItemStack currentMiniGamePage = MiniGamesCatalogueItem.getCurrentMiniGame(catalogue);
        if (currentMiniGamePage.isEmpty())
            return Destination.fail("message.steveparty.here_we_go.no_minigame", "No mini-game in progress");

        List<HereWeComeBookInfo> books = getValidBooks(player, getMiniGameDestinations(currentMiniGamePage));
        if (books.isEmpty())
            return Destination.fail("message.steveparty.here_we_go.no_destination", "This mini-game has no « Here we come » book to go to");

        TeamDisposition teamDisposition = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(catalogue);
        BlockPos pos = getTpMiniGamePosFromBooks(player, partyController, step, books, teamDisposition);
        if (pos == null)
            return Destination.fail("message.steveparty.here_we_go.no_room", "No room left for you in this mini-game");
        return Destination.to(pos);
    }

    private static PartyControllerEntity getNearestActivePartyController(PlayerEntity player) {
        return PartyControllerEntity.getClosestActivePartyControllerEntity(player.getWorld(), player.getBlockPos(), -1)
                .orElse(null);
    }

    private static List<BlockPos> getMiniGameDestinations(ItemStack miniGamePage) {
        return miniGamePage.getOrDefault(DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations();
    }

    private static List<HereWeComeBookInfo> getValidBooks(PlayerEntity player, List<BlockPos> destinations) {
        ServerWorld world = (ServerWorld) player.getWorld();
        TeleportationPadBooksStorage booksStorage = TeleportationPadStorageManager.getBooksStorage(world);

        List<HereWeComeBookInfo> books = new ArrayList<>();
        for (BlockPos pos : destinations) {
            ItemStack bookStack = booksStorage.getTeleportationPadBook(pos);
            if (isValidTeleportationBook(bookStack)) {
                // Work on copies: the targets stored in the book component must never be mutated
                List<TeleportingTarget> conditions = new ArrayList<>();
                for (TeleportingTarget target : bookStack.getOrDefault(TP_TARGETS, List.<TeleportingTarget>of())) {
                    if (target != null) conditions.add(target.copy());
                }
                books.add(new HereWeComeBookInfo(pos, conditions));
            }
        }
        return books;
    }

    private static boolean isValidTeleportationBook(ItemStack bookStack) {
        return !bookStack.isEmpty() && bookStack.getItem() instanceof HereWeComeBookItem;
    }

    /** What the player is in the current mini-game, for the conditions of the books. */
    private enum Role { TEAM_A, TEAM_B, PLAYER, SPECTATOR }

    private static Role getRole(PlayerEntity player, PartyControllerEntity partyController, @Nullable TeamDisposition disposition) {
        UUID uuid = player.getUuid();
        if (disposition != null) {
            if (disposition.getTeamA().contains(uuid)) return Role.TEAM_A;
            if (disposition.getTeamB().contains(uuid)) return Role.TEAM_B;
            return Role.SPECTATOR;
        }
        // No disposition recorded: a player owning a token plays, without team
        for (UUID token : partyController.getPartyData().getTokens()) {
            if (partyController.isTokenOwnedBy(token, uuid)) return Role.PLAYER;
        }
        return Role.SPECTATOR;
    }

    private static boolean accepts(TeleportingTarget.Group group, Role role) {
        return switch (group) {
            case EVERYONE -> true;
            case PLAYERS -> role != Role.SPECTATOR;
            case SPECTATORS -> role == Role.SPECTATOR;
            case PLAYER_TEAM_A -> role == Role.TEAM_A;
            case PLAYER_TEAM_B -> role == Role.TEAM_B;
        };
    }

    /** The more specific condition wins a tie: a team before "players"/"spectators" before "everyone". */
    private static int specificity(TeleportingTarget.Group group) {
        return switch (group) {
            case PLAYER_TEAM_A, PLAYER_TEAM_B -> 2;
            case PLAYERS, SPECTATORS -> 1;
            case EVERYONE -> 0;
        };
    }

    private static BlockPos getTpMiniGamePosFromBooks(PlayerEntity player, PartyControllerEntity partyController, MiniGamePartyStep step,
                                                      List<HereWeComeBookInfo> books, @Nullable TeamDisposition teamDisposition) {
        Map<UUID, MiniGamePartyStep.Destination> taken = step.getDestinations();

        // Already sent somewhere in this mini-game (came back, fell...): same place again
        MiniGamePartyStep.Destination previous = taken.get(player.getUuid());
        if (previous != null) {
            for (HereWeComeBookInfo book : books) {
                if (book.bookPos().equals(previous.pad()) && previous.targetIndex() < book.conditions().size())
                    return book.bookPos();
            }
            taken.remove(player.getUuid()); // that book is gone: find him another place
        }

        // Team A of the books = the team with the smaller capacity, as the team A of the disposition is the smaller one
        exchangeTeamAAndBBasedOnSizeIfNeeded(books);
        Role role = getRole(player, partyController, teamDisposition);

        Map<MiniGamePartyStep.Destination, Integer> fill = new HashMap<>();
        for (MiniGamePartyStep.Destination destination : taken.values()) fill.merge(destination, 1, Integer::sum);

        MiniGamePartyStep.Destination best = null;
        TeleportingTarget bestTarget = null;
        int bestFill = 0;
        int ties = 0;
        Random random = player.getRandom();
        for (HereWeComeBookInfo book : books) {
            for (int i = 0; i < book.conditions().size(); i++) {
                TeleportingTarget target = book.conditions().get(i);
                if (!accepts(target.getGroup(), role)) continue;
                MiniGamePartyStep.Destination candidate = new MiniGamePartyStep.Destination(book.bookPos(), i);
                int filled = fill.getOrDefault(candidate, 0);
                if (filled >= target.getCheckedFillCapacity()) continue; // full

                int comparison = bestTarget == null ? 1 : compare(target, filled, bestTarget, bestFill);
                if (comparison < 0) continue;
                if (comparison == 0) {
                    // Same priority, same specificity, same fill: pick one of the tied places at random
                    ties++;
                    if (random.nextInt(ties) != 0) continue;
                } else {
                    ties = 1;
                }
                best = candidate;
                bestTarget = target;
                bestFill = filled;
            }
        }
        if (best == null) return null;
        taken.put(player.getUuid(), best);
        partyController.markDirty();
        return best.pad();
    }

    /** > 0 if place {@code a} (filled by {@code aFill} players) should be filled before place {@code b}. */
    private static int compare(TeleportingTarget a, int aFill, TeleportingTarget b, int bFill) {
        int c = Integer.compare(a.getFillPriorityWeight(), b.getFillPriorityWeight());
        if (c != 0) return c;
        c = Integer.compare(specificity(a.getGroup()), specificity(b.getGroup()));
        if (c != 0) return c;
        return Integer.compare(bFill, aFill); // the emptier place first: spreads the players
    }

    /**
     * Swaps team A and B conditions if team A has more capacity. Only mutates the (copied) conditions of
     * {@code books}, never the book components.
     */
    private static void exchangeTeamAAndBBasedOnSizeIfNeeded(List<HereWeComeBookInfo> books) {
        // long: getCheckedFillCapacity() returns Integer.MAX_VALUE for "no limit", summing ints would overflow
        long teamASize = 0;
        long teamBSize = 0;
        for (HereWeComeBookInfo bookInfo : books) {
            for (TeleportingTarget target : bookInfo.conditions()) {
                if (target.getGroup().equals(TeleportingTarget.Group.PLAYER_TEAM_A)) teamASize += target.getCheckedFillCapacity();
                if (target.getGroup().equals(TeleportingTarget.Group.PLAYER_TEAM_B)) teamBSize += target.getCheckedFillCapacity();
            }
        }

        if (teamASize > teamBSize) {
            for (HereWeComeBookInfo bookInfo : books) {
                bookInfo.conditions().forEach(target -> {
                    if (target.getGroup().equals(TeleportingTarget.Group.PLAYER_TEAM_A)) {
                        target.setGroup(TeleportingTarget.Group.PLAYER_TEAM_B);
                    } else if (target.getGroup().equals(TeleportingTarget.Group.PLAYER_TEAM_B)) {
                        target.setGroup(TeleportingTarget.Group.PLAYER_TEAM_A);
                    }});
            }
        }
    }

    record HereWeComeBookInfo(BlockPos bookPos, List<TeleportingTarget> conditions) {}

    // ------------------------------------------------------------------ other modes

    private static BlockPos getTpBackLastUsedTpPadPos(PlayerEntity player) {
        TeleportationHistoryStorage storage = TeleportationPadStorageManager.getTeleportationHistoryStorage((ServerWorld) player.getWorld());
        var lastTeleportation = storage.get(player.getUuid());
        // The player may never have used a teleportation pad
        return lastTeleportation == null ? null : lastTeleportation.fromPos();
    }

    private static BlockPos getTpRegisteredPos(ItemStack book, PlayerEntity player) {
        DestinationsComponent component = book.getOrDefault(DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT);
        List<BlockPos> destinations = component.destinations();
        if (destinations == null || destinations.isEmpty()) return null;
        return destinations.get(player.getWorld().getRandom().nextInt(destinations.size()));
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        int state = stack.getOrDefault(STATE, 0);
        if (state == State.TP_REGISTERED_POS.ordinal()) {
            appendDestinationsSelectorTooltip(stack, context, tooltip, type);
        }
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        int state = context.getStack().getOrDefault(STATE, 0);
        if (state == State.TP_REGISTERED_POS.ordinal()) {
            return useOnBlockDestinationsSelector(context);
        }
        return ActionResult.PASS;
    }

    @Override
    public ScreenHandler createScreenHandler(int syncId, PlayerInventory inv, PlayerEntity player) {
        return new HereWeGoBookScreenHandler(syncId, inv);
    }

    /** Must run on the server thread. */
    public static void handleHereWeGoBookPayload(ServerPlayerEntity player, int newState) {
        // The book screen must be open, with the book in the main hand (where it was opened from)
        if (!(player.currentScreenHandler instanceof HereWeGoBookScreenHandler handler) || !handler.canUse(player)) return;
        if (newState < 0 || newState >= State.values().length) return;
        ItemStack bookStack = player.getMainHandStack();
        if (bookStack.getItem() instanceof HereWeGoBookItem) {
            bookStack.set(STATE, newState);
            player.getInventory().markDirty();
        }
    }

    public enum State {
        TP_TO_MINIGAME(0),
        TP_BACK_LAST_USED_TP_PAD(1),
        TP_REGISTERED_POS(2);
        private final int value;

        State(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        // Static method to get enum from int
        public static State fromInt(int i) {
            for (State e : State.values()) {
                if (e.getValue() == i) {
                    return e;
                }
            }
            // Corrupted/unknown value: fall back to the default state instead of crashing
            return TP_TO_MINIGAME;
        }
    }
}
