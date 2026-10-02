package fr.lordfinn.steveparty.screen_handlers.custom;

import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
import fr.lordfinn.steveparty.payloads.custom.BlockPosPayload;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import fr.lordfinn.steveparty.screen_handlers.ModScreensHandlers;
import fr.lordfinn.steveparty.screen_handlers.ScreenHandlerChecks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The Mini-game Controller's screen: its two slots (the Mini-game Page, the Zone Cartridge), the player's inventory,
 * and what the mini-game of the page is doing, worked out by the server a few times a second and synced as
 * properties ({@link State}, who would play, the votes of a practice round). Its buttons: « Play » / « Stop » out of
 * a party, « Ready » during a party's practice round. Changing the slots takes the right to build; playing and
 * voting do not.
 */
public class MiniGameControllerScreenHandler extends ScreenHandler {
    public static final int SLOT_PAGE = 0, SLOT_ZONE = 1, PLAYER_SLOTS = 2;
    public static final int BUTTON_PLAY = 0, BUTTON_READY = 1;
    /** Ticks between two looks at the mini-game. */
    public static final int SYNC_INTERVAL = 5;

    // Layout (shared with the screen)
    public static final int WIDTH = 248, PANEL_HEIGHT = 136;
    public static final int INVENTORY_Y = PANEL_HEIGHT + 4;
    public static final int PAGE_X = 11, PAGE_Y = 71, ZONE_X = 11, ZONE_Y = 91;

    /** What the mini-game of the page is doing. */
    public enum State {
        /** No page in the controller. */
        NO_PAGE,
        /** It can be played now, out of any party, with those near its pipes. */
        READY,
        /** It is being played out of any party. */
        RUNNING,
        /** No way to play ticked on the page has its pipes. */
        NO_PIPE,
        /** Nobody stands near a players or team pipe of the page. */
        NOBODY,
        /** Those near the pipes fit no way to play of the page. */
        NOT_ENOUGH,
        /** A party plays its practice round. */
        PARTY_PRACTICE,
        /** A party plays its real round. */
        PARTY_PLAYING
    }

    private static final int P_STATE = 0, P_PLAYERS = 1, P_MODE = 2, P_READY = 3, P_VOTERS = 4, P_FLAGS = 5,
            P_ZONE_X = 6, P_ZONE_Y = 7, P_ZONE_Z = 8, PROPERTIES = 9;
    private static final int FLAG_VOTER = 1, FLAG_READY = 2;

    private final @Nullable MiniGameControllerBlockEntity controller;
    private final BlockPos pos;
    private final PlayerEntity player;
    private final PropertyDelegate properties = new ArrayPropertyDelegate(PROPERTIES);
    private int ticks;

    /** Server side. */
    public MiniGameControllerScreenHandler(int syncId, PlayerInventory playerInventory, MiniGameControllerBlockEntity controller) {
        this(syncId, playerInventory, controller.getPos(), controller, controller.inventory());
        look();
    }

    /** Client side. */
    public MiniGameControllerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPosPayload payload) {
        this(syncId, playerInventory, payload.pos(), null, new SimpleInventory(MiniGameControllerBlockEntity.SLOTS));
    }

    private MiniGameControllerScreenHandler(int syncId, PlayerInventory playerInventory, BlockPos pos,
                                            @Nullable MiniGameControllerBlockEntity controller, Inventory inventory) {
        super(ModScreensHandlers.MINI_GAME_CONTROLLER_SCREEN_HANDLER, syncId);
        this.controller = controller;
        this.pos = pos;
        this.player = playerInventory.player;
        addSlot(new Slot(inventory, MiniGameControllerBlockEntity.SLOT_PAGE, PAGE_X, PAGE_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return MiniGamePages.isPage(stack) && MiniGamePages.canEdit(player) && (controller == null || controller.accepts(stack));
            }

            @Override
            public boolean canTakeItems(PlayerEntity playerEntity) {
                return MiniGamePages.canEdit(playerEntity);
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });
        addSlot(new Slot(inventory, MiniGameControllerBlockEntity.SLOT_ZONE, ZONE_X, ZONE_Y) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return MiniGameControllerBlockEntity.isZoneCartridge(stack) && MiniGamePages.canEdit(player);
            }

            @Override
            public boolean canTakeItems(PlayerEntity playerEntity) {
                return MiniGamePages.canEdit(playerEntity);
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });
        int invX = (WIDTH - 162) / 2 + 1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, invX + col * 18, INVENTORY_Y + 14 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, invX + col * 18, INVENTORY_Y + 14 + 58));
        addProperties(properties);
    }

    // ------------------------------------------------------------------ what the mini-game is doing (server)

    /** Looks at the mini-game of the page: its state, who would play, the votes. */
    private void look() {
        if (controller == null || controller.getWorld() == null || controller.getWorld().getServer() == null) return;
        MinecraftServer server = controller.getWorld().getServer();
        UUID page = controller.getPageId();
        State state = State.NO_PAGE;
        int players = 0, mode = -1, ready = 0, voters = 0, flags = 0;
        Optional<PartyControllerEntity> party = page == null ? Optional.empty() : PartyControllerEntity.getPartyPlayingPage(List.of(page));
        if (party.isPresent() && party.get().getPartyData().getCurrentStep() instanceof MiniGamePartyStep step) {
            state = step.isPractice() ? State.PARTY_PRACTICE : State.PARTY_PLAYING;
            players = step.getParticipants().size();
            if (step.isPractice()) {
                List<MiniGamePagePayloads.Practice.Voter> votes = step.voters(party.get());
                voters = votes.size();
                ready = (int) votes.stream().filter(MiniGamePagePayloads.Practice.Voter::ready).count();
                if (step.getParticipants().contains(player.getUuid())) flags |= FLAG_VOTER;
                if (step.getReady().contains(player.getUuid())) flags |= FLAG_READY;
            }
        } else if (page != null) {
            MiniGameTest.Plan plan = MiniGameTest.check(server, page);
            state = switch (plan.status()) {
                case READY -> State.READY;
                case RUNNING -> State.RUNNING;
                case NO_PIPE -> State.NO_PIPE;
                case NOBODY -> State.NOBODY;
                case NOT_ENOUGH -> State.NOT_ENOUGH;
                case PARTY_PLAYING -> State.PARTY_PLAYING;
            };
            players = plan.players().size();
            mode = plan.mode() == null ? -1 : plan.mode().ordinal();
        }
        properties.set(P_STATE, state.ordinal());
        properties.set(P_PLAYERS, players);
        properties.set(P_MODE, mode);
        properties.set(P_READY, ready);
        properties.set(P_VOTERS, voters);
        properties.set(P_FLAGS, flags);
        fr.lordfinn.steveparty.components.ZoneSelection selection = controller.getSelection();
        net.minecraft.util.math.BlockBox box = selection == null ? null : selection.box().orElse(null);
        properties.set(P_ZONE_X, box == null ? 0 : Math.min(Short.MAX_VALUE, box.getBlockCountX()));
        properties.set(P_ZONE_Y, box == null ? 0 : Math.min(Short.MAX_VALUE, box.getBlockCountY()));
        properties.set(P_ZONE_Z, box == null ? 0 : Math.min(Short.MAX_VALUE, box.getBlockCountZ()));
    }

    @Override
    public void sendContentUpdates() {
        if (controller != null && ticks++ % SYNC_INTERVAL == 0) look();
        super.sendContentUpdates();
    }

    public State state() {
        int state = properties.get(P_STATE);
        return state >= 0 && state < State.values().length ? State.values()[state] : State.NO_PAGE;
    }

    /** The players who would play (out of a party), or who play (a party). */
    public int players() {
        return properties.get(P_PLAYERS);
    }

    /** Ordinal of the way it would be played out of a party, -1 for none. */
    public int mode() {
        return properties.get(P_MODE);
    }

    /** The players of the practice round who are ready. */
    public int readyCount() {
        return properties.get(P_READY);
    }

    /** The connected players of the practice round. */
    public int voters() {
        return properties.get(P_VOTERS);
    }

    /** @return true if the player is one of the players of the practice round. */
    public boolean isVoter() {
        return (properties.get(P_FLAGS) & FLAG_VOTER) != 0;
    }

    public boolean isReady() {
        return (properties.get(P_FLAGS) & FLAG_READY) != 0;
    }

    /** The size of the box of the Zone Cartridge (0, 0, 0: none); a side over {@code PageZone.MAX_SIDE}: too big, no zone. */
    public int[] zoneSize() {
        return new int[]{properties.get(P_ZONE_X), properties.get(P_ZONE_Y), properties.get(P_ZONE_Z)};
    }

    // ------------------------------------------------------------------ clicks

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (controller == null || !(player instanceof ServerPlayerEntity serverPlayer)) return false;
        UUID page = controller.getPageId();
        if (page == null || player.isSpectator()) return false;
        look();
        switch (id) {
            case BUTTON_PLAY -> {
                if (state() == State.RUNNING) MiniGameTest.stop(page);
                else if (state() != State.READY
                        || MiniGameTest.start(serverPlayer.server, page, serverPlayer, MiniGameTest.COUNTDOWN_SECONDS) != MiniGameTest.Status.READY) return false;
            }
            case BUTTON_READY -> {
                if (!MiniGamePartyStep.toggleReady(serverPlayer)) return false;
            }
            default -> {
                return false;
            }
        }
        look();
        return true;
    }

    @Override
    public void onSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player) {
        // A page that has its controller elsewhere: said, rather than silently refused
        if (slotIndex == SLOT_PAGE && controller != null && MiniGamePages.isPage(getCursorStack()) && !controller.accepts(getCursorStack())) {
            player.sendMessage(Text.translatable("message.steveparty.mini_game_controller.other_home").formatted(Formatting.RED), true);
        }
        super.onSlotClick(slotIndex, button, actionType, player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasStack()) return ItemStack.EMPTY;
        ItemStack stack = slot.getStack();
        ItemStack original = stack.copy();
        if (index < PLAYER_SLOTS) {
            if (!slot.canTakeItems(player) || !insertItem(stack, PLAYER_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = MiniGamePages.isPage(stack) ? SLOT_PAGE : MiniGameControllerBlockEntity.isZoneCartridge(stack) ? SLOT_ZONE : -1;
            if (target < 0 || slots.get(target).hasStack() || !slots.get(target).canInsert(stack)) return ItemStack.EMPTY;
            slots.get(target).setStack(stack.split(1));
        }
        if (stack.isEmpty()) slot.setStack(ItemStack.EMPTY);
        else slot.markDirty();
        return original;
    }

    public BlockPos getPos() {
        return pos;
    }

    public @Nullable MiniGameControllerBlockEntity getController() {
        return controller;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return controller == null || ScreenHandlerChecks.canUseBlockEntity(controller, player);
    }
}
