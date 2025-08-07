package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.lordfinn.steveparty.components.ModComponents.*;

public class HereWeGoBookItem extends AbstractTeleportationBookItem {
    public HereWeGoBookItem(Settings settings) {
        super(settings);
    }

    public static BlockPos getTpPos(ItemStack book, PlayerEntity player) {
        State state = State.fromInt(book.getOrDefault(STATE, 0));
        return switch (state) {
            case TP_TO_MINIGAME -> getTpMiniGamePos(player);
            case TP_BACK_LAST_USED_TP_PAD -> getTpBackLastUsedTpPadPos(player);
            case TP_REGISTERED_POS -> getTpRegisteredPos(book);
        };
    }

    private static BlockPos getTpMiniGamePos(PlayerEntity player) {
        PartyControllerEntity partyController = getNearestActivePartyController(player);
        if (partyController == null) return null;

        ItemStack catalogue = partyController.getCatalogue();
        ItemStack currentMiniGamePage = MiniGamesCatalogueItem.getCurrentMiniGame(catalogue);
        if (currentMiniGamePage.isEmpty()) return null;

        List<BlockPos> destinations = getMiniGameDestinations(currentMiniGamePage);
        if (destinations.isEmpty()) return null;

        TeamDisposition teamDisposition = MiniGamesCatalogueItem.getCurrentMiniGameTeamDisposition(catalogue);
        List<HereWeComeBookInfo> validBooks = getValidBooks(player, destinations);

        if (validBooks.isEmpty()) return null;

        return getTpMiniGamePosFromBooks(player, partyController, validBooks, teamDisposition);
    }

    private static PartyControllerEntity getNearestActivePartyController(PlayerEntity player) {
        return PartyControllerEntity.getClosestActivePartyControllerEntity(player.getBlockPos(), -1)
                .orElse(null);
    }

    private static List<BlockPos> getMiniGameDestinations(ItemStack miniGamePage) {
        return miniGamePage.getOrDefault(DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT).destinations();
    }

    private static List<HereWeComeBookInfo> getValidBooks(PlayerEntity player, List<BlockPos> destinations) {
        ServerWorld world = (ServerWorld) player.getWorld();
        TeleportationPadBooksStorage booksStorage = TeleportationPadStorageManager.getBooksStorage(world);
        TeleportationHistoryStorage historyStorage = TeleportationPadStorageManager.getTeleportationHistoryStorage(world);

        List<HereWeComeBookInfo> books = new ArrayList<>();
        for (BlockPos pos : destinations) {
            ItemStack bookStack = booksStorage.getTeleportationPadBook(pos);
            if (isValidTeleportationBook(bookStack)) {
                books.add(new HereWeComeBookInfo(
                        pos,
                        bookStack.getOrDefault(TP_TARGETS, List.of()),
                        historyStorage.get(pos)));
            }
        }
        return books;
    }

    private static boolean isValidTeleportationBook(ItemStack bookStack) {
        return !bookStack.isEmpty() && bookStack.getItem() instanceof HereWeComeBookItem;
    }

    private static BlockPos getTpMiniGamePosFromBooks(PlayerEntity player, PartyControllerEntity partyController, List<HereWeComeBookInfo> books, TeamDisposition teamDisposition) {
        exchangeTeamAAndBBasedOnSizeIfNeeded(books);
        //TODO
        return null;
    }

    private static void exchangeTeamAAndBBasedOnSizeIfNeeded(List<HereWeComeBookInfo> books) {
        AtomicInteger teamASize = new AtomicInteger();
        AtomicInteger teamBSize = new AtomicInteger();
        for (HereWeComeBookInfo bookInfo : books) {
            bookInfo.conditions().forEach(target -> {
                if (target.getGroup().equals(TeleportingTarget.Group.PLAYER_TEAM_A)) teamASize.getAndAdd(target.getCheckedFillCapacity());
                if (target.getGroup().equals(TeleportingTarget.Group.PLAYER_TEAM_B)) teamBSize.getAndAdd(target.getCheckedFillCapacity());
            });
        }

        if (teamASize.get() > teamBSize.get()) {
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

    record HereWeComeBookInfo(BlockPos bookPos, List<TeleportingTarget> conditions, List<UUID> alreadyTeleportedPlayers) {}

    private static BlockPos getTpBackLastUsedTpPadPos(PlayerEntity player) {
        TeleportationHistoryStorage storage = TeleportationPadStorageManager.getTeleportationHistoryStorage((ServerWorld) player.getWorld());
        return storage.get(player.getUuid()).fromPos();
    }

    private static BlockPos getTpRegisteredPos(ItemStack book) {
        DestinationsComponent component = book.getOrDefault(DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT);
        if (component.destinations().isEmpty()) return null;
        return component.destinations().get(new Random(component.destinations().size()).nextInt());
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

    public static void handleHereWeGoBookPayload(ServerPlayerEntity player, int newState) {
        ItemStack bookStack = player.getMainHandStack();
        if (bookStack.getItem() instanceof AbstractTeleportationBookItem) {
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
            throw new IllegalArgumentException("Unexpected value: " + i);
        }
    }
}
