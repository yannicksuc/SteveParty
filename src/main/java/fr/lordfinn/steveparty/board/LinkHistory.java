package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.BlockOriginComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ShopLinkComponent;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Undo / redo of the link edits, per player (by UUID), in memory, the last {@link #MAX} actions. An action is what one
 * click (or placement, or command) changed: links of a cartridge, a tile's rotation, the chest of an inventory tile,
 * and the anchor of the tool. A change whose board space was edited since (by someone else, the interface...)
 * is skipped, with a message.
 */
public final class LinkHistory {
    public static final int MAX = 32;

    /** One undoable change in the world. */
    public sealed interface Change permits LinksChange, RotationChange, ChestChange, ShopChange, BlockChange {
        /** Puts {@code from} back to {@code to} if the world still shows {@code from}; false if it changed since. */
        boolean apply(ServerWorld world, boolean undo);

        /** The block it changes. */
        BlockPos pos();
    }

    public record LinksChange(BlockPos pos, int slot, List<BlockPos> before, List<BlockPos> after) implements Change {
        @Override
        public boolean apply(ServerWorld world, boolean undo) {
            // Any holder's cartridge: a board space's, a router's, a Hop Switch's...
            BrushLinks.Held held = BrushLinks.held(world, pos, slot);
            if (held == null || !(held.cartridge().getItem() instanceof CartridgeItem)) return false;
            List<BlockPos> expected = undo ? after : before;
            if (!BoardLinks.links(held.cartridge()).equals(expected)) return false;
            BoardLinks.setLinks(held.cartridge(), undo ? before : after, world);
            held.sync().run();
            return true;
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

    /** The containers of an Inventory Cartridge, before and after (in their order). */
    public record ChestChange(BlockPos pos, int slot, List<GlobalPos> before,
                              List<GlobalPos> after) implements Change {
        @Override
        public boolean apply(ServerWorld world, boolean undo) {
            BrushLinks.Held held = BrushLinks.held(world, pos, slot);
            if (held == null) return false;
            ItemStack cartridge = held.cartridge();
            if (cartridge.isEmpty() || !CartridgeContainers.of(cartridge, world.getRegistryKey())
                    .equals(undo ? after : before)) return false;
            CartridgeContainers.set(cartridge, undo ? before : after);
            held.sync().run();
            return true;
        }
    }

    /** The shop chosen for the Shop Cartridge in {@code slot} (null: the nearest merchant). */
    public record ShopChange(BlockPos pos, int slot, @Nullable ShopLinkComponent before,
                             @Nullable ShopLinkComponent after) implements Change {
        @Override
        public boolean apply(ServerWorld world, boolean undo) {
            CartridgeContainerBlockEntity container = BoardLinks.container(world, pos);
            if (container == null) return false;
            ItemStack cartridge = container.getStack(slot);
            if (cartridge.isEmpty() || !Objects.equals(cartridge.get(ModComponents.SHOP_LINK), undo ? after : before)) return false;
            ShopLinkComponent value = undo ? before : after;
            if (value == null) cartridge.remove(ModComponents.SHOP_LINK);
            else cartridge.set(ModComponents.SHOP_LINK, value);
            BoardLinks.sync(container);
            return true;
        }
    }

    /**
     * A block placed by a paste or a template: {@code before} (with its block entity data) comes back on undo if the
     * block is still {@code after}; redo places {@code after} again with its data.
     */
    public record BlockChange(BlockPos pos, BlockState before, @Nullable NbtCompound beforeData,
                              BlockState after, @Nullable NbtCompound afterData) implements Change {
        @Override
        public boolean apply(ServerWorld world, boolean undo) {
            if (world.getBlockState(pos) != (undo ? after : before)) return false;
            BlockState state = undo ? before : after;
            NbtCompound data = undo ? beforeData : afterData;
            world.setBlockState(pos, state, Block.NOTIFY_ALL);
            if (data != null && world.getBlockEntity(pos) instanceof BlockEntity blockEntity) {
                blockEntity.read(data, world.getRegistryManager());
                blockEntity.markDirty();
                world.updateListeners(pos, state, state, Block.NOTIFY_ALL);
            }
            return true;
        }

        /** The block at {@code pos} now, with its block entity data. */
        static NbtCompound data(ServerWorld world, BlockPos pos) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            return blockEntity == null ? null : blockEntity.createNbt(world.getRegistryManager());
        }
    }

    /** The anchor of the tool (the Tile Linker Brush) around an action, restored on the tool in hand. */
    public record ToolSnapshot(@Nullable BlockOriginComponent anchor) {
        static ToolSnapshot of(ItemStack tool) {
            return new ToolSnapshot(tool.get(ModComponents.BLOCK_ORIGIN_COMPONENT));
        }

        void restore(ItemStack tool) {
            if (anchor == null) tool.remove(ModComponents.BLOCK_ORIGIN_COMPONENT);
            else tool.set(ModComponents.BLOCK_ORIGIN_COMPONENT, anchor);
        }
    }

    public record Action(RegistryKey<World> world, Text label, List<Change> changes,
                         @Nullable ToolSnapshot toolBefore, @Nullable ToolSnapshot toolAfter) {
    }

    private static final class Pending {
        final RegistryKey<World> world;
        final List<Change> changes = new ArrayList<>();
        final @Nullable ToolSnapshot toolBefore;

        Pending(RegistryKey<World> world, @Nullable ToolSnapshot toolBefore) {
            this.world = world;
            this.toolBefore = toolBefore;
        }
    }

    private static final Map<UUID, Deque<Action>> UNDO = new HashMap<>();
    private static final Map<UUID, Deque<Action>> REDO = new HashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private LinkHistory() {
    }

    // ---------------------------------------------------------------- recording

    /** Starts recording what {@code player} changes (the tool, if any, is snapshotted to be restored on undo). */
    public static void begin(ServerPlayerEntity player, World world, @Nullable ItemStack tool) {
        PENDING.put(player.getUuid(), new Pending(world.getRegistryKey(), tool == null ? null : ToolSnapshot.of(tool)));
    }

    /** Records a change of the action being recorded (nothing if none). */
    public static void record(@Nullable ServerPlayerEntity player, Change change) {
        if (player == null) return;
        Pending pending = PENDING.get(player.getUuid());
        if (pending != null) pending.changes.add(change);
    }

    /** Ends the action being recorded: kept (and the redo stack emptied) if it changed something. */
    public static void commit(ServerPlayerEntity player, Text label, @Nullable ItemStack tool) {
        Pending pending = PENDING.remove(player.getUuid());
        if (pending == null || pending.changes.isEmpty()) return;
        push(UNDO, player.getUuid(), new Action(pending.world, label, List.copyOf(pending.changes), pending.toolBefore,
                tool == null ? null : ToolSnapshot.of(tool)));
        REDO.remove(player.getUuid());
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
     * Undoes (or redoes) the last action of {@code player}, restoring the anchor of the tool in {@code tool} if given.
     *
     * @return false if there was nothing to undo / redo
     */
    public static boolean undo(ServerPlayerEntity player, boolean undo, @Nullable ItemStack tool) {
        // Changing the board again takes the right to build: not a spectator, not in adventure mode (a party started
        // since), and only where the player may build (checked change by change, they may be far apart)
        if (player.isSpectator() || !player.canModifyBlocks()) return false;
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
        if (undo) Collections.reverse(changes);
        for (Change change : changes) {
            if (!world.canPlayerModifyAt(player, change.pos()) || !change.apply(world, undo)) skipped++;
        }
        ToolSnapshot snapshot = undo ? action.toolBefore() : action.toolAfter();
        if (tool != null && snapshot != null && world == player.getWorld()) snapshot.restore(tool);
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
