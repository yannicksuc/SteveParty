package fr.lordfinn.steveparty.board;

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

    private WrenchActions() {
    }

    // ---------------------------------------------------------------- entry points

    /** Right click on a block, within reach. */
    public static ActionResult useOnBlock(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos clicked) {
        BlockPos pos = BoardSpaces.resolve(world, clicked);
        BlockState state = world.getBlockState(pos);
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
            if (WrenchState.of(wrench).mode() == WrenchMode.EDIT) removeStaleLink(player, wrench, world, pos);
            return ActionResult.SUCCESS;
        }
        click(player, wrench, world, pos, container);
        return ActionResult.SUCCESS;
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
        if (container != null) click(player, wrench, world, pos, container);
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
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.end", state.chainLength()), true);
        } else {
            player.sendMessage(Text.translatable("message.steveparty.wrench.unbound", origin.getX(), origin.getY(), origin.getZ()), true);
        }
        playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
    }

    // ---------------------------------------------------------------- Trace

    private static void trace(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, CartridgeContainerBlockEntity container) {
        if (!(container instanceof BoardSpaceBlockEntity)) {
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.not_board_space"), true);
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
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.walk", n, n + 1), true);
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
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.loop", n), true);
            playSound(world, player, SoundEvents.ENTITY_PLAYER_LEVELUP, 1.2f);
        } else if (joining) {
            clearOrigin(wrench);
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.join", BoardText.pos(pos), n), true);
        } else {
            setOrigin(wrench, world, pos, state.withChain(state.chainStart().or(() -> Optional.of(origin)), n + 1).withSlot(WrenchState.ACTIVE_SLOT));
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.link", n, n + 1, n + 1), true);
        }
    }

    private static void startChain(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos,
                                   CartridgeContainerBlockEntity container, WrenchState state) {
        setOrigin(wrench, world, pos, state.withChain(Optional.of(pos), 1).withSlot(WrenchState.ACTIVE_SLOT));
        int existing = BoardLinks.boardSpaceLinks(world, BoardLinks.links(container, BoardLinks.slotOf(container, WrenchState.ACTIVE_SLOT)));
        if (existing > 0) {
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.fork", BoardText.pos(pos), existing), true);
        } else {
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.start", BoardText.pos(pos)), true);
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
            player.sendMessage(Text.translatable("message.steveparty.wrench.bound", pos.getX(), pos.getY(), pos.getZ()), true);
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
            removeLink(world, originContainer, slot, pos);
            player.sendMessage(Text.translatable("message.steveparty.wrench.edit.removed", BoardText.pos(origin), BoardText.pos(pos)), true);
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
            return;
        }
        if (!(container instanceof BoardSpaceBlockEntity)) {
            player.sendMessage(Text.translatable("message.steveparty.wrench.trace.not_board_space"), true);
            return;
        }
        if (addLink(player, world, origin, originContainer, slot, pos)) {
            player.sendMessage(Text.translatable("message.steveparty.wrench.edit.added", BoardText.pos(origin), BoardText.pos(pos)), true);
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
        removeLink(world, originContainer, slot, stale);
        player.sendMessage(Text.translatable("message.steveparty.wrench.edit.removed", BoardText.pos(origin), BoardText.pos(stale)), true);
        playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
    }

    // ---------------------------------------------------------------- Cut

    private static void cut(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, CartridgeContainerBlockEntity container) {
        WrenchState state = WrenchState.of(wrench);
        int slot = BoardLinks.slotOf(container, pos.equals(origin(wrench, world)) ? state.slot() : WrenchState.ACTIVE_SLOT);
        List<BlockPos> links = BoardLinks.links(container, slot);
        if (links.isEmpty()) {
            player.sendMessage(Text.translatable("message.steveparty.wrench.cut.none", BoardText.pos(pos)), true);
            return;
        }
        for (BlockPos link : links) BoardLinks.trail(world, pos, link, BoardLinks.CUT_COLOR);
        writeLinks(world, container, slot, List.of());
        player.sendMessage(Text.translatable("message.steveparty.wrench.cut.done", links.size(), BoardText.pos(pos)), true);
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
        if (!creative && !removed.isEmpty()) player.getInventory().offerOrDrop(removed);
        player.sendMessage(Text.translatable("message.steveparty.wrench.cartridge_swapped", BoardText.pos(pos),
                current.getName(), replacement.getName()), true);
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
            player.sendMessage(Text.translatable("message.steveparty.wrench.no_cartridge"), true);
            playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 0.7f);
            return false;
        }
        List<BlockPos> links = new ArrayList<>(BoardLinks.links(cartridge));
        if (links.contains(target)) return true;
        links.add(target);
        writeLinks(world, originContainer, slot, links);
        if (BoardLinks.boardSpaceLinks(world, links) == 1) BoardLinks.orient(world, origin, target);
        BoardLinks.trail(world, origin, target, BoardLinks.LINK_COLOR);
        return true;
    }

    private static void removeLink(ServerWorld world, CartridgeContainerBlockEntity container, int slot, BlockPos target) {
        List<BlockPos> links = new ArrayList<>(BoardLinks.links(container, slot));
        links.remove(target);
        writeLinks(world, container, slot, links);
        BoardLinks.trail(world, container.getPos(), target, BoardLinks.CUT_COLOR);
    }

    private static void writeLinks(ServerWorld world, CartridgeContainerBlockEntity container, int slot, List<BlockPos> links) {
        BoardLinks.setLinks(world, container, slot, links);
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
                player.sendMessage(Text.translatable("message.steveparty.wrench.mode", mode.displayName()), true);
                playSound(world, player, SoundEvents.UI_BUTTON_CLICK.value(), 1.4f);
            }
        }
    }

    // ---------------------------------------------------------------- sounds

    private static void playChainSound(World world, ServerPlayerEntity player, int length) {
        // One semitone higher per board space of the chain, from half pitch (1) to double pitch (25)
        float pitch = (float) Math.pow(2, (Math.min(length, 25) - 13) / 12.0);
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), SoundCategory.PLAYERS, 0.6f, pitch);
    }

    private static void playSound(World world, ServerPlayerEntity player, SoundEvent sound, float pitch) {
        world.playSound(null, player.getBlockPos(), sound, SoundCategory.PLAYERS, 0.6f, pitch);
    }

    /** @return whether {@code stack} is a wrench */
    public static boolean isWrench(ItemStack stack) {
        return stack.getItem() instanceof WrenchItem;
    }
}
