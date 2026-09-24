package fr.lordfinn.steveparty.items.custom.teleportation_books;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGameTeleports;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.persistent_state.TeleportationHistoryStorage;
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
            case TP_REGISTERED_POS -> getTpRegisteredPos(book, player);
        };
    }

    /**
     * The arrival pad of the player in the mini-game being played by the closest running party: the pad the player
     * was sent to, or a free pad of their team (see {@link MiniGameTeleports#assign}).
     */
    private static BlockPos getTpMiniGamePos(PlayerEntity player) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return null;
        PartyControllerEntity partyController = PartyControllerEntity
                .getClosestActivePartyControllerEntity(player.getWorld(), player.getBlockPos(), -1).orElse(null);
        if (partyController == null) return null;
        if (!(partyController.getPartyData().getCurrentStep() instanceof MiniGamePartyStep miniGame)) return null;
        return miniGame.getOrAssignPad(partyController, serverPlayer);
    }

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
