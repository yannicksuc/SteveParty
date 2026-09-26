package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyController;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainer;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.BlockOriginComponent;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.payloads.custom.WrenchActionPayload;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What the Wrench does, server side. Everything the Wrench remembers lives on the item (origin, mode, chain: see
 * {@link WrenchState}), so each player has their own.
 * <ul>
 *     <li>right click on a board space: the action of the mode (Trace: chain links; Edit: add / remove a link from a
 *     fixed origin; Cut: remove the outgoing links). Holding the button and sweeping over board spaces repeats it, up
 *     to {@link #LONG_REACH} blocks away;</li>
 *     <li>sneak + right click on a cartridge container: its interface; elsewhere: ends the chain.</li>
 * </ul>
 */
public final class WrenchActions {
    /** Board spaces can be linked this far away (the right click in the air casts a ray). */
    public static final double LONG_REACH = 32;
    /** Vanilla repeats the use every 4 ticks while the button is held: the same board space again is not a new click. */
    private static final int REPEAT_TICKS = 4;

    private record LastUse(BlockPos pos, long tick) {
    }

    private static final Map<UUID, LastUse> LAST_USES = new HashMap<>();
    private static final Map<UUID, Text> LAST_LABELS = new HashMap<>();

    private WrenchActions() {
    }

    // ---------------------------------------------------------------- entry points

    /** Right click on a block, within reach. */
    public static ActionResult useOnBlock(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos clicked) {
        BlockPos pos = BoardSpaces.resolve(world, clicked);
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PartyController) {
            // The board this controller plays: checked, the report in the chat
            if (!isRepeat(player, pos, world.getTime())) BoardValidator.send(player, BoardValidator.check(world, pos));
            return ActionResult.SUCCESS;
        }
        if (player.isSneaking()) {
            if (state.getBlock() instanceof CartridgeContainer container) return container.openContainerScreen(state, world, pos, player);
            endChain(player, wrench, world, true);
            return ActionResult.SUCCESS;
        }
        CartridgeContainerBlockEntity container = BoardLinks.container(world, pos);
        if (isRepeat(player, pos, world.getTime())) return ActionResult.SUCCESS;
        if (container == null) {
            // The ground between two board spaces while sweeping; in Edit, a link to a block that is no board space
            // (any more) can still be removed by clicking it
            if (WrenchState.of(wrench).mode() == WrenchMode.EDIT) recorded(player, world, wrench, () -> removeStaleLink(player, wrench, world, pos));
            return ActionResult.SUCCESS;
        }
        recorded(player, world, wrench, () -> click(player, wrench, world, pos, container));
        return ActionResult.SUCCESS;
    }

    /** Runs a Wrench action, recording what it changes for undo (labelled by its last message). */
    public static void recorded(ServerPlayerEntity player, World world, @Nullable ItemStack wrench, Runnable action) {
        LAST_LABELS.remove(player.getUuid());
        LinkHistory.begin(player, world, wrench);
        try {
            action.run();
        } finally {
            LinkHistory.commit(player, LAST_LABELS.getOrDefault(player.getUuid(), Text.empty()), wrench);
        }
    }

    /** The action bar message of an action (also its label in the undo history). */
    public static void say(ServerPlayerEntity player, Text text) {
        LAST_LABELS.put(player.getUuid(), text);
        player.sendMessage(text, true);
    }

    /** Right click in the air: sneaking ends the chain, else the board space aimed at up to {@link #LONG_REACH} blocks. */
    public static ActionResult use(ServerPlayerEntity player, ItemStack wrench, ServerWorld world) {
        if (player.isSneaking()) {
            endChain(player, wrench, world, true);
            return ActionResult.SUCCESS;
        }
        BlockPos pos = aimedBoardSpace(player, world);
        if (pos == null) return ActionResult.PASS;
        if (isRepeat(player, pos, world.getTime())) return ActionResult.SUCCESS;
        CartridgeContainerBlockEntity container = BoardLinks.container(world, pos);
        if (container != null) recorded(player, world, wrench, () -> click(player, wrench, world, pos, container));
        return ActionResult.SUCCESS;
    }

    /** The board space (or router) the player aims at, up to {@link #LONG_REACH} blocks away, or null. */
    public static @Nullable BlockPos aimedBoardSpace(net.minecraft.entity.player.PlayerEntity player, World world) {
        HitResult hit = player.raycast(LONG_REACH, 1f, false);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = BoardSpaces.resolve(world, blockHit.getBlockPos());
        return BoardLinks.container(world, pos) != null ? pos : null;
    }

    private static boolean isRepeat(ServerPlayerEntity player, BlockPos pos, long tick) {
        LastUse last = LAST_USES.put(player.getUuid(), new LastUse(pos, tick));
        return last != null && last.pos().equals(pos) && tick - last.tick() <= REPEAT_TICKS && tick >= last.tick();
    }

    private static void click(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, CartridgeContainerBlockEntity container) {
        switch (WrenchState.of(wrench).mode()) {
            case TRACE -> trace(player, wrench, world, pos, container);
            case EDIT -> edit(player, wrench, world, pos, container);
            case CUT -> cut(player, wrench, world, pos, container);
        }
    }

    // ---------------------------------------------------------------- origin

    /** The origin of the wrench in {@code world}, or null (none, or in another world: then it is forgotten). */
    public static @Nullable BlockPos origin(ItemStack wrench, World world) {
        BlockOriginComponent origin = wrench.get(ModComponents.BLOCK_ORIGIN_COMPONENT);
        if (origin == null || origin.origin().equals(BlockOriginComponent.DEFAULT_ORIGIN)) return null;
        if (!origin.world().isEmpty() && !origin.world().equals(BoardLinks.worldName(world))) return null;
        return origin.origin();
    }

    private static void setOrigin(ItemStack wrench, World world, BlockPos pos, WrenchState state) {
        wrench.set(ModComponents.BLOCK_ORIGIN_COMPONENT, new BlockOriginComponent(pos, BoardLinks.worldName(world)));
        wrench.set(ModComponents.WRENCH_STATE, state);
    }

    public static void clearOrigin(ItemStack wrench) {
        wrench.remove(ModComponents.BLOCK_ORIGIN_COMPONENT);
        wrench.set(ModComponents.WRENCH_STATE, WrenchState.of(wrench).withChain(Optional.empty(), 0).withSlot(WrenchState.ACTIVE_SLOT));
    }

    /** Ends the chain (Trace) or unbinds the origin (Edit). */
    public static void endChain(ServerPlayerEntity player, ItemStack wrench, World world, boolean message) {
        BlockPos origin = origin(wrench, world);
        if (origin == null && wrench.get(ModComponents.BLOCK_ORIGIN_COMPONENT) == null) return;
        WrenchState state = WrenchState.of(wrench);
        clearOrigin(wrench);
        if (!message || origin == null) return;
        if (state.mode() == WrenchMode.TRACE && state.chainLength() > 0) {
            say(player, Text.translatable("message.steveparty.wrench.trace.end", state.chainLength()));
        } else {
            say(player, Text.translatable("message.steveparty.wrench.unbound", origin.getX(), origin.getY(), origin.getZ()));
        }
        playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
    }

    // ---------------------------------------------------------------- Trace

    private static void trace(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, CartridgeContainerBlockEntity container) {
        if (!(container instanceof BoardSpaceBlockEntity)) {
            say(player, Text.translatable("message.steveparty.wrench.trace.not_board_space"));
            return;
        }
        WrenchState state = WrenchState.of(wrench);
        BlockPos origin = origin(wrench, world);
        if (origin == null || BoardLinks.container(world, origin) == null) {
            if (swapCartridge(player, world, pos, container, WrenchState.ACTIVE_SLOT)) return;
            startChain(player, wrench, world, pos, container, state);
            return;
        }
        if (pos.equals(origin)) {
            if (swapCartridge(player, world, pos, container, state.slot())) return;
            endChain(player, wrench, world, true);
            return;
        }
        CartridgeContainerBlockEntity originContainer = BoardLinks.container(world, origin);
        int slot = BoardLinks.slotOf(originContainer, state.slot());
        int n = Math.max(1, state.chainLength());
        if (BoardLinks.links(originContainer, slot).contains(pos)) {
            // Already linked: follow the path from there
            setOrigin(wrench, world, pos, state.withChain(state.chainStart().or(() -> Optional.of(origin)), n + 1).withSlot(WrenchState.ACTIVE_SLOT));
            say(player, Text.translatable("message.steveparty.wrench.trace.walk", n, n + 1));
            playChainSound(world, player, n + 1);
            return;
        }
        if (!addLink(player, world, origin, originContainer, slot, pos)) {
            endChain(player, wrench, world, false);
            return;
        }
        boolean closing = state.chainStart().map(pos::equals).orElse(false);
        boolean joining = !closing && !BoardLinks.links(container, BoardLinks.slotOf(container, WrenchState.ACTIVE_SLOT)).isEmpty();
        playChainSound(world, player, n + 1);
        if (closing) {
            clearOrigin(wrench);
            say(player, Text.translatable("message.steveparty.wrench.trace.loop", n));
            loopClosed(world, player, pos);
        } else if (joining) {
            clearOrigin(wrench);
            say(player, Text.translatable("message.steveparty.wrench.trace.join", BoardText.pos(pos), n));
        } else {
            setOrigin(wrench, world, pos, state.withChain(state.chainStart().or(() -> Optional.of(origin)), n + 1).withSlot(WrenchState.ACTIVE_SLOT));
            say(player, Text.translatable("message.steveparty.wrench.trace.link", n, n + 1, n + 1));
        }
    }

    private static void startChain(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos,
                                   CartridgeContainerBlockEntity container, WrenchState state) {
        setOrigin(wrench, world, pos, state.withChain(Optional.of(pos), 1).withSlot(WrenchState.ACTIVE_SLOT));
        int existing = BoardLinks.boardSpaceLinks(world, BoardLinks.links(container, BoardLinks.slotOf(container, WrenchState.ACTIVE_SLOT)));
        if (existing > 0) {
            say(player, Text.translatable("message.steveparty.wrench.trace.fork", BoardText.pos(pos), existing));
        } else {
            say(player, Text.translatable("message.steveparty.wrench.trace.start", BoardText.pos(pos)));
        }
        playSound(world, player, ModSounds.SELECT_SOUND_EVENT, 1f);
    }

    // ---------------------------------------------------------------- Edit

    private static void edit(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, CartridgeContainerBlockEntity container) {
        WrenchState state = WrenchState.of(wrench);
        BlockPos origin = origin(wrench, world);
        if (origin == null || BoardLinks.container(world, origin) == null) {
            if (swapCartridge(player, world, pos, container, WrenchState.ACTIVE_SLOT)) return;
            setOrigin(wrench, world, pos, state.withChain(Optional.empty(), 0).withSlot(WrenchState.ACTIVE_SLOT));
            say(player, Text.translatable("message.steveparty.wrench.bound", pos.getX(), pos.getY(), pos.getZ()));
            playSound(world, player, SoundEvents.BLOCK_BEACON_ACTIVATE, 1f);
            return;
        }
        if (pos.equals(origin)) {
            if (swapCartridge(player, world, pos, container, state.slot())) return;
            endChain(player, wrench, world, true);
            return;
        }
        CartridgeContainerBlockEntity originContainer = BoardLinks.container(world, origin);
        int slot = BoardLinks.slotOf(originContainer, state.slot());
        if (BoardLinks.links(originContainer, slot).contains(pos)) {
            removeLink(player, world, originContainer, slot, pos);
            say(player, Text.translatable("message.steveparty.wrench.edit.removed", BoardText.pos(origin), BoardText.pos(pos)));
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
            return;
        }
        if (!(container instanceof BoardSpaceBlockEntity)) {
            say(player, Text.translatable("message.steveparty.wrench.trace.not_board_space"));
            return;
        }
        if (addLink(player, world, origin, originContainer, slot, pos)) {
            say(player, Text.translatable("message.steveparty.wrench.edit.added", BoardText.pos(origin), BoardText.pos(pos)));
            playSound(world, player, ModSounds.SELECT_SOUND_EVENT, 1f);
        }
    }

    /** Edit mode: clicking a block that is not a board space (any more) removes the origin's link to it, if any. */
    private static void removeStaleLink(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos) {
        BlockPos origin = origin(wrench, world);
        if (origin == null) return;
        CartridgeContainerBlockEntity originContainer = BoardLinks.container(world, origin);
        if (originContainer == null) return;
        int slot = BoardLinks.slotOf(originContainer, WrenchState.of(wrench).slot());
        List<BlockPos> links = BoardLinks.links(originContainer, slot);
        BlockPos stale = links.contains(pos) ? pos : links.contains(pos.up()) ? pos.up() : null;
        if (stale == null) return;
        removeLink(player, world, originContainer, slot, stale);
        say(player, Text.translatable("message.steveparty.wrench.edit.removed", BoardText.pos(origin), BoardText.pos(stale)));
        playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
    }

    // ---------------------------------------------------------------- Cut

    private static void cut(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, CartridgeContainerBlockEntity container) {
        WrenchState state = WrenchState.of(wrench);
        int slot = BoardLinks.slotOf(container, pos.equals(origin(wrench, world)) ? state.slot() : WrenchState.ACTIVE_SLOT);
        List<BlockPos> links = BoardLinks.links(container, slot);
        if (links.isEmpty()) {
            say(player, Text.translatable("message.steveparty.wrench.cut.none", BoardText.pos(pos)));
            return;
        }
        for (BlockPos link : links) BoardLinks.trail(world, pos, link, BoardLinks.CUT_COLOR);
        writeLinks(player, world, container, slot, List.of());
        say(player, Text.translatable("message.steveparty.wrench.cut.done", links.size(), BoardText.pos(pos)));
        playSound(world, player, SoundEvents.ENTITY_SHEEP_SHEAR, 1f);
    }

    // ---------------------------------------------------------------- cartridge type swap (links kept)

    /**
     * Clicking a board space holding a cartridge while holding another kind of cartridge in the off hand: that one
     * replaces it and takes its links (the replaced cartridge goes back to the inventory, except in creative).
     *
     * @return true if the cartridge was swapped
     */
    private static boolean swapCartridge(ServerPlayerEntity player, ServerWorld world, BlockPos pos,
                                         CartridgeContainerBlockEntity container, int requestedSlot) {
        ItemStack offHand = player.getOffHandStack();
        if (!(offHand.getItem() instanceof CartridgeItem)) return false;
        int slot = BoardLinks.slotOf(container, requestedSlot);
        ItemStack current = container.getStack(slot);
        if (current.isEmpty() || current.getItem() == offHand.getItem()) return false;
        ItemStack replacement = offHand.copyWithCount(1);
        replacement.set(ModComponents.DESTINATIONS_COMPONENT,
                current.getOrDefault(ModComponents.DESTINATIONS_COMPONENT, DestinationsComponent.DEFAULT));
        boolean creative = player.getAbilities().creativeMode;
        if (!creative) offHand.decrement(1);
        ItemStack removed = container.removeStack(slot);
        container.setStack(slot, replacement);
        BoardLinks.sync(container);
        BoardLinks.linkNearestChest(player, container, slot);
        if (!creative && !removed.isEmpty()) player.getInventory().offerOrDrop(removed);
        say(player, Text.translatable("message.steveparty.wrench.cartridge_swapped", BoardText.pos(pos),
                current.getName(), replacement.getName()));
        playSound(world, player, ModSounds.SELECT_SOUND_EVENT, 1.3f);
        return true;
    }

    // ---------------------------------------------------------------- link writes

    /**
     * Adds the link {@code origin → target} (inserting a cartridge in the origin if needed) and turns the origin toward
     * its target when it is its first link to a board space.
     *
     * @return false if the origin has no cartridge and none could be supplied
     */
    private static boolean addLink(ServerPlayerEntity player, ServerWorld world, BlockPos origin,
                                   CartridgeContainerBlockEntity originContainer, int slot, BlockPos target) {
        ItemStack cartridge = BoardLinks.ensureCartridge(player, originContainer, slot);
        if (cartridge.isEmpty()) {
            say(player, Text.translatable("message.steveparty.wrench.no_cartridge"));
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 0.7f);
            return false;
        }
        List<BlockPos> links = new ArrayList<>(BoardLinks.links(cartridge));
        if (links.contains(target)) return true;
        links.add(target);
        writeLinks(player, world, originContainer, slot, links);
        if (BoardLinks.boardSpaceLinks(world, links) == 1) {
            int before = BoardLinks.orient(world, origin, target);
            if (before >= 0) {
                LinkHistory.record(player, new LinkHistory.RotationChange(origin, before,
                        world.getBlockState(origin).get(fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock.ROTATION_8)));
            }
        }
        BoardLinks.trail(world, origin, target, BoardLinks.LINK_COLOR);
        starPop(world, target);
        return true;
    }

    private static void removeLink(ServerPlayerEntity player, ServerWorld world, CartridgeContainerBlockEntity container, int slot, BlockPos target) {
        List<BlockPos> links = new ArrayList<>(BoardLinks.links(container, slot));
        links.remove(target);
        writeLinks(player, world, container, slot, links);
        BoardLinks.trail(world, container.getPos(), target, BoardLinks.CUT_COLOR);
    }

    /** Writes the links of a cartridge, recorded for undo. */
    static void writeLinks(@Nullable ServerPlayerEntity player, ServerWorld world, CartridgeContainerBlockEntity container, int slot, List<BlockPos> links) {
        List<BlockPos> before = List.copyOf(BoardLinks.links(container, slot));
        if (BoardLinks.setLinks(world, container, slot, links)) {
            LinkHistory.record(player, new LinkHistory.LinksChange(container.getPos().toImmutable(), slot, before, List.copyOf(links)));
        }
    }

    // ---------------------------------------------------------------- linked on placement

    /** A board space placed farther than this from the origin is not linked automatically. */
    public static final double AUTO_LINK_DISTANCE = 10;

    /**
     * A board space was placed by {@code placer}: with a tracing Wrench (auto link on) in the off hand, or in the hotbar
     * with a chain going on, it is linked from the origin, the origin turns toward it and it becomes the new origin.
     * With the Wrench in the off hand and no chain yet, it starts one. So a loop is built by placing its tiles only.
     */
    public static void onBoardSpacePlaced(World world, BlockPos pos, @Nullable net.minecraft.entity.LivingEntity placer, ItemStack placedFrom) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        dropCopiedLinks(serverWorld, pos, placer, placedFrom);
        if (!(placer instanceof ServerPlayerEntity player)) return;
        ItemStack wrench = tracingWrench(player);
        if (wrench == null) return;
        WrenchState state = WrenchState.of(wrench);
        CartridgeContainerBlockEntity placed = BoardLinks.container(world, pos);
        if (!(placed instanceof BoardSpaceBlockEntity)) return;
        BlockPos origin = origin(wrench, world);
        CartridgeContainerBlockEntity originContainer = origin == null ? null : BoardLinks.container(world, origin);
        recorded(player, world, wrench, () -> {
            if (originContainer == null) {
                if (wrench == player.getOffHandStack()) startChain(player, wrench, serverWorld, pos, placed, state);
                return;
            }
            double distance = Math.sqrt(origin.getSquaredDistance(pos));
            if (distance > AUTO_LINK_DISTANCE) {
                say(player, Text.translatable("message.steveparty.wrench.auto_link.too_far", (int) Math.round(distance), (int) AUTO_LINK_DISTANCE));
                return;
            }
            int slot = BoardLinks.slotOf(originContainer, state.slot());
            int n = Math.max(1, state.chainLength());
            if (!addLink(player, serverWorld, origin, originContainer, slot, pos)) {
                endChain(player, wrench, world, false);
                return;
            }
            setOrigin(wrench, world, pos, state.withChain(state.chainStart().or(() -> Optional.of(origin)), n + 1).withSlot(WrenchState.ACTIVE_SLOT));
            say(player, Text.translatable("message.steveparty.wrench.auto_link.linked", n, n + 1, n + 1));
            playChainSound(world, player, n + 1);
        });
    }

    /**
     * A board space placed from an item holding its data (creative pick block with Ctrl, a copied item...): its
     * cartridges come without their links, which pointed at the neighbours of the original.
     */
    private static void dropCopiedLinks(ServerWorld world, BlockPos pos, @Nullable net.minecraft.entity.LivingEntity placer, ItemStack placedFrom) {
        if (!placedFrom.contains(net.minecraft.component.DataComponentTypes.BLOCK_ENTITY_DATA)) return;
        if (!(world.getBlockEntity(pos) instanceof CartridgeContainerBlockEntity container)) return;
        int dropped = 0;
        for (int slot = 0; slot < container.size(); slot++) {
            List<BlockPos> links = BoardLinks.links(container, slot);
            if (links.isEmpty()) continue;
            dropped += links.size();
            BoardLinks.setLinks(world, container, slot, List.of());
        }
        if (dropped > 0 && placer instanceof ServerPlayerEntity player) {
            player.sendMessage(Text.translatable("message.steveparty.wrench.copy_without_links", dropped), false);
        }
    }

    /** The Wrench that links placed board spaces: in the off hand, or in the hotbar with a chain going on. */
    private static @Nullable ItemStack tracingWrench(ServerPlayerEntity player) {
        ItemStack offHand = player.getOffHandStack();
        if (isWrench(offHand)) return isAutoLinking(offHand) ? offHand : null;
        for (int i = 0; i < net.minecraft.entity.player.PlayerInventory.getHotbarSize(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (isWrench(stack) && isAutoLinking(stack) && origin(stack, player.getWorld()) != null) return stack;
        }
        return null;
    }

    private static boolean isAutoLinking(ItemStack wrench) {
        WrenchState state = WrenchState.of(wrench);
        return state.mode() == WrenchMode.TRACE && state.autoLink();
    }

    // ---------------------------------------------------------------- chests of inventory tiles

    public static void initialize() {
        // A click on a chest with the Wrench whose origin is an inventory tile: that tile's chest (even without sneaking)
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (hand != net.minecraft.util.Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
            ItemStack wrench = player.getMainHandStack();
            if (!isWrench(wrench) || !BoardLinks.isChest(world, hit.getBlockPos())) return ActionResult.PASS;
            BlockPos origin = origin(wrench, world);
            CartridgeContainerBlockEntity container = origin == null ? null : BoardLinks.container(world, origin);
            if (container == null) return ActionResult.PASS;
            int slot = BoardLinks.slotOf(container, WrenchState.of(wrench).slot());
            if (!(container.getStack(slot).getItem() instanceof fr.lordfinn.steveparty.items.custom.cartridges.InventoryCartridgeItem)) {
                return ActionResult.PASS;
            }
            if (world.isClient) return ActionResult.SUCCESS;
            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            BlockPos chest = hit.getBlockPos().toImmutable();
            recorded(serverPlayer, world, wrench, () -> linkChest(serverPlayer, (ServerWorld) world, container, slot, chest));
            return ActionResult.SUCCESS;
        });
    }

    /** Links (or, the same chest again, unlinks) {@code chest} to the inventory cartridge in {@code slot}. */
    private static void linkChest(ServerPlayerEntity player, ServerWorld world, CartridgeContainerBlockEntity container, int slot, BlockPos chest) {
        ItemStack cartridge = container.getStack(slot);
        BlockPos before = cartridge.get(ModComponents.INVENTORY_POS);
        BlockPos after = chest.equals(before) ? null : chest;
        if (after == null) cartridge.remove(ModComponents.INVENTORY_POS);
        else cartridge.set(ModComponents.INVENTORY_POS, after);
        BoardLinks.sync(container);
        LinkHistory.record(player, new LinkHistory.ChestChange(container.getPos().toImmutable(), slot, before, after));
        if (after != null) {
            BoardLinks.trail(world, container.getPos(), chest, 0x3C8CFF);
            say(player, Text.translatable("message.steveparty.wrench.chest.linked", BoardText.pos(chest), BoardText.pos(container.getPos())));
            playSound(world, player, ModSounds.SELECT_SOUND_EVENT, 1.1f);
        } else {
            say(player, Text.translatable("message.steveparty.wrench.chest.unlinked", BoardText.pos(chest), BoardText.pos(container.getPos())));
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
        }
    }

    // ---------------------------------------------------------------- controls sent by the client

    /** A control of {@link fr.lordfinn.steveparty.payloads.custom.WrenchActionPayload}, on the wrench in the main hand. */
    public static void control(ServerPlayerEntity player, ItemStack wrench, WrenchActionPayload.Action action, int direction) {
        World world = player.getWorld();
        WrenchState state = WrenchState.of(wrench);
        switch (action) {
            case MODE -> {
                WrenchMode mode = state.mode().cycle(direction);
                wrench.set(ModComponents.WRENCH_STATE, state.withMode(mode));
                say(player, Text.translatable("message.steveparty.wrench.mode", mode.displayName()));
                playSound(world, player, SoundEvents.UI_BUTTON_CLICK.value(), 1.4f);
            }
            case UNDO, REDO -> {
                if (LinkHistory.undo(player, action == WrenchActionPayload.Action.UNDO, wrench)) {
                    playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, action == WrenchActionPayload.Action.UNDO ? 0.8f : 1.2f);
                }
            }
            case AUTO_LINK -> {
                boolean autoLink = !state.autoLink();
                wrench.set(ModComponents.WRENCH_STATE, state.withAutoLink(autoLink));
                say(player, Text.translatable(autoLink ? "message.steveparty.wrench.auto_link.on" : "message.steveparty.wrench.auto_link.off"));
                playSound(world, player, SoundEvents.UI_BUTTON_CLICK.value(), autoLink ? 1.6f : 1.0f);
            }
            case SLOT -> {
                BlockPos origin = origin(wrench, world);
                CartridgeContainerBlockEntity container = origin == null ? null : BoardLinks.container(world, origin);
                if (container == null || container.size() <= 1) return;
                // -1 (the active slot, the redstone's), then 0..15
                int slot = Math.floorMod(state.slot() + 1 + direction, container.size() + 1) - 1;
                wrench.set(ModComponents.WRENCH_STATE, state.withSlot(slot));
                say(player, slotText(container, slot));
                playSound(world, player, SoundEvents.UI_BUTTON_CLICK.value(), 1.2f + slot * 0.04f);
            }
        }
    }

    /** "Slot 4/16 (3 links)" or "Active slot (redstone 2)". */
    public static Text slotText(CartridgeContainerBlockEntity container, int slot) {
        int actual = BoardLinks.slotOf(container, slot);
        int links = BoardLinks.links(container, actual).size();
        return slot < 0
                ? Text.translatable("message.steveparty.wrench.slot.active", actual + 1, container.size(), links)
                : Text.translatable("message.steveparty.wrench.slot", actual + 1, container.size(), links);
    }

    // ---------------------------------------------------------------- sounds

    /** Semitones of the major pentatonic scale: the chain climbs it, one note per board space. */
    private static final int[] PENTATONIC = {0, 2, 4, 7, 9};

    private static void playChainSound(World world, ServerPlayerEntity player, int length) {
        // From half pitch (the first space) up two octaves of the pentatonic scale, then it stays on the top note
        int step = Math.min(Math.max(length - 1, 0), 10);
        int semitones = 12 * (step / 5) + PENTATONIC[step % 5] - 12;
        float pitch = (float) Math.pow(2, semitones / 12.0);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.45f, pitch);
    }

    /** A new link: a small star pop on the linked space (few particles, short). */
    private static void starPop(ServerWorld world, BlockPos target) {
        net.minecraft.util.math.Vec3d at = BoardSpaces.standPos(world, target).add(0, 0.35, 0);
        world.spawnParticles(fr.lordfinn.steveparty.particles.KamekShapeEffect.sparkle(2.4F, 0.8F, 9, fr.lordfinn.steveparty.particles.KamekShapeEffect.YELLOW),
                at.x, at.y, at.z, 3, 0.15, 0.1, 0.15, 0.02);
    }

    /** The loop is closed: light confetti on the space and a short major chord. */
    private static void loopClosed(ServerWorld world, ServerPlayerEntity player, BlockPos pos) {
        net.minecraft.util.math.Vec3d at = BoardSpaces.standPos(world, pos).add(0, 0.6, 0);
        world.spawnParticles(fr.lordfinn.steveparty.particles.KamekShapeEffect.shape(1.6F, 0.88F, 22), at.x, at.y, at.z, 12, 0.35, 0.2, 0.35, 0.12);
        for (float pitch : new float[]{1.0F, 1.26F, 1.5F}) {
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), SoundCategory.PLAYERS, 0.4F, pitch);
        }
    }

    private static void playSound(World world, ServerPlayerEntity player, SoundEvent sound, float pitch) {
        world.playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, 0.6f, pitch);
    }

    /** @return whether {@code stack} is a wrench */
    public static boolean isWrench(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem;
    }
}
