package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.BlockOriginComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Undo / redo of the link edits, per player (by UUID), in memory, the last {@link #MAX} actions. An action is what one
 * click (or placement, or command) changed: links of a cartridge, a tile's rotation, the chest of an inventory tile,
 * and the wrench's origin and chain. A change whose board space was edited since (by someone else, the interface...)
 * is skipped, with a message.
 */
public final class LinkHistory {
    public static final int MAX = 32;

    /** One undoable change in the world. */
    public sealed interface Change permits LinksChange, RotationChange, ChestChange {
        /** Puts {@code from} back to {@code to} if the world still shows {@code from}; false if it changed since. */
        boolean apply(ServerWorld world, boolean undo);
    }

    public record LinksChange(BlockPos pos, int slot, List<BlockPos> before, List<BlockPos> after) implements Change {
        @Override
        public boolean apply(ServerWorld world, boolean undo) {
            CartridgeContainerBlockEntity container = BoardLinks.container(world, pos);
            if (container == null) return false;
            List<BlockPos> expected = undo ? after : before;
            if (!BoardLinks.links(container, slot).equals(expected)) return false;
            return BoardLinks.setLinks(world, container, slot, undo ? before : after);
        }
    }

    public record RotationChange(BlockPos pos, int before, int after) implements Change {
        @Override
        public boolean apply(ServerWorld world, boolean undo) {
            BlockState state = world.getBlockState(pos);
            if (!(state.getBlock() instanceof ATileBlock) || state.get(ATileBlock.ROTATION_8) != (undo ? after : before)) return false;
            world.setBlockState(pos, state.with(ATileBlock.ROTATION_8, undo ? before : after), Block.NOTIFY_ALL);
            return true;
        }
    }

    public record ChestChange(BlockPos pos, int slot, @Nullable BlockPos before, @Nullable BlockPos after) implements Change {
        @Override
        public boolean apply(ServerWorld world, boolean undo) {
            CartridgeContainerBlockEntity container = BoardLinks.container(world, pos);
            if (container == null) return false;
            ItemStack cartridge = container.getStack(slot);
            if (cartridge.isEmpty() || !Objects.equals(cartridge.get(ModComponents.INVENTORY_POS), undo ? after : before)) return false;
            BlockPos value = undo ? before : after;
            if (value == null) cartridge.remove(ModComponents.INVENTORY_POS);
            else cartridge.set(ModComponents.INVENTORY_POS, value);
            BoardLinks.sync(container);
            return true;
        }
    }

    /** The wrench's origin and state around an action (restored on the wrench in hand). */
    public record WrenchSnapshot(@Nullable BlockOriginComponent origin, WrenchState state) {
        static WrenchSnapshot of(ItemStack wrench) {
            return new WrenchSnapshot(wrench.get(ModComponents.BLOCK_ORIGIN_COMPONENT), WrenchState.of(wrench));
        }

        void restore(ItemStack wrench) {
            if (origin == null) wrench.remove(ModComponents.BLOCK_ORIGIN_COMPONENT);
            else wrench.set(ModComponents.BLOCK_ORIGIN_COMPONENT, origin);
            // The mode and auto link are settings, not history: only the chain and slot come back
            WrenchState current = WrenchState.of(wrench);
            wrench.set(ModComponents.WRENCH_STATE, current.withChain(state.chainStart(), state.chainLength()).withSlot(state.slot()));
        }
    }

    public record Action(RegistryKey<World> world, Text label, List<Change> changes,
                         @Nullable WrenchSnapshot wrenchBefore, @Nullable WrenchSnapshot wrenchAfter) {
    }

    private static final class Pending {
        final RegistryKey<World> world;
        final List<Change> changes = new ArrayList<>();
        final @Nullable WrenchSnapshot wrenchBefore;

        Pending(RegistryKey<World> world, @Nullable WrenchSnapshot wrenchBefore) {
            this.world = world;
            this.wrenchBefore = wrenchBefore;
        }
    }

    private static final Map<UUID, Deque<Action>> UNDO = new HashMap<>();
    private static final Map<UUID, Deque<Action>> REDO = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private LinkHistory() {
    }

    // ---------------------------------------------------------------- recording

    /** Starts recording what {@code player} changes (the wrench, if any, is snapshotted to be restored on undo). */
    public static void begin(ServerPlayerEntity player, World world, @Nullable ItemStack wrench) {
        PENDING.put(player.getUuid(), new Pending(world.getRegistryKey(), wrench == null ? null : WrenchSnapshot.of(wrench)));
    }

    /** Records a change of the action being recorded (nothing if none). */
    public static void record(@Nullable ServerPlayerEntity player, Change change) {
        if (player == null) return;
        Pending pending = PENDING.get(player.getUuid());
        if (pending != null) pending.changes.add(change);
    }

    /** Ends the action being recorded: kept (and the redo stack emptied) if it changed something. */
    public static void commit(ServerPlayerEntity player, Text label, @Nullable ItemStack wrench) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending == null || pending.changes.isEmpty()) return;
        push(UNDO, player.getUuid(), new Action(pending.world, label, List.copyOf(pending.changes), pending.wrenchBefore,
                wrench == null ? null : WrenchSnapshot.of(wrench)));
        REDO.remove(player.getUuid());
    }

    /** Drops the action being recorded, if any. */
    public static void abort(ServerPlayerEntity player) {
        PENDING.remove(player.getUuid());
    }

    private static void push(Map<UUID, Deque<Action>> stacks, UUID player, Action action) {
        Deque<Action> stack = stacks.computeIfAbsent(player, k -> new ArrayDeque<>());
        stack.push(action);
        while (stack.size() > MAX) stack.removeLast();
    }

    // ---------------------------------------------------------------- undo / redo

    public static int size(UUID player, boolean undo) {
        Deque<Action> stack = (undo ? UNDO : REDO).get(player);
        return stack == null ? 0 : stack.size();
    }

    /**
     * Undoes (or redoes) the last action of {@code player}, restoring the chain of the wrench in {@code wrench} if given.
     *
     * @return false if there was nothing to undo / redo
     */
    public static boolean undo(ServerPlayerEntity player, boolean undo, @Nullable ItemStack wrench) {
        Deque<Action> stack = (undo ? UNDO : REDO).get(player.getUuid());
        if (stack == null || stack.isEmpty()) {
            player.sendMessage(Text.translatable(undo ? "message.steveparty.wrench.undo.empty" : "message.steveparty.wrench.redo.empty"), true);
            return false;
        }
        Action action = stack.pop();
        ServerWorld world = player.getServer() == null ? null : player.getServer().getWorld(action.world());
        if (world == null) return false;
        int skipped = 0;
        List<Change> changes = new ArrayList<>(action.changes());
        if (undo) java.util.Collections.reverse(changes);
        for (Change change : changes) {
            if (!change.apply(world, undo)) skipped++;
        }
        WrenchSnapshot snapshot = undo ? action.wrenchBefore() : action.wrenchAfter();
        if (wrench != null && snapshot != null && world == player.getWorld()) snapshot.restore(wrench);
        push(undo ? REDO : UNDO, player.getUuid(), action);
        Text message = Text.translatable(undo ? "message.steveparty.wrench.undo" : "message.steveparty.wrench.redo", action.label());
        if (skipped > 0) message = message.copy().append(Text.translatable("message.steveparty.wrench.undo.skipped", skipped));
        player.sendMessage(message, true);
        return true;
    }

    /** Forgets everything (server stopped). */
    public static void clear() {
        UNDO.clear();
        REDO.clear();
        PENDING.clear();
    }
}
