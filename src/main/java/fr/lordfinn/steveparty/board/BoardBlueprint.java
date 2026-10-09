package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.ATileBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TilePartBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileLayout;
import fr.lordfinn.steveparty.components.DestinationsComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Board blueprints: copy a piece of board and paste it elsewhere, turned, with its links. The save format keeps
 * absolute positions: the links are translated (and turned) only while pasting — a link inside the copied area follows
 * the copy, a link leaving it is kept or cut. Also moving the links of an area after a WorldEdit move, and templates.
 * Every paste is undoable like the Wrench actions.
 */
public final class BoardBlueprint {
    /** Largest area copied at once. */
    public static final int MAX_VOLUME = 64 * 64 * 64;

    /** A copied block, relative to the anchor (where the player stood). */
    public record Entry(BlockPos relative, BlockState state, @Nullable NbtCompound data) {
    }

    /**
     * @param box      the copied area, in the world it was copied from (the links pointing into it follow the copy)
     * @param anchor   the player's position when copying
     */
    public record Clip(BlockBox box, BlockPos anchor, List<Entry> entries) {
        public int boardSpaces() {
            return (int) entries.stream().filter(e -> e.data() != null && e.state().getBlock() instanceof ABoardSpaceBlock).count();
        }
    }

    private static final Map<UUID, Clip> CLIPBOARDS = new HashMap<>();

    private BoardBlueprint() {
    }

    public static @Nullable Clip clipboard(UUID player) {
        return CLIPBOARDS.get(player);
    }

    public static void clear() {
        CLIPBOARDS.clear();
    }

    // ---------------------------------------------------------------- copy

    /** Copies the blocks of {@code box} (air and large tile parts left out: a large tile takes its parts back). */
    public static Clip copy(ServerWorld world, BlockBox box, BlockPos anchor) {
        List<Entry> entries = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof TilePartBlock) continue;
            BlockEntity blockEntity = world.getBlockEntity(pos);
            NbtCompound data = blockEntity == null ? null : blockEntity.createNbt(world.getRegistryManager());
            entries.add(new Entry(pos.subtract(anchor), state, data));
        }
        return new Clip(box, anchor.toImmutable(), entries);
    }

    public static Clip copyFor(ServerPlayerEntity player, ServerWorld world, BlockBox box) {
        Clip clip = copy(world, box, player.getBlockPos());
        CLIPBOARDS.put(player.getUuid(), clip);
        return clip;
    }

    // ---------------------------------------------------------------- paste

    /**
     * Pastes {@code clip} at {@code anchor}, turned by {@code rotation}. Links inside the copied area follow the copy;
     * links leaving it are kept if {@code keepOutsideLinks}, else cut. Start tiles lose their token (it stays on the
     * original). Recorded for undo when {@code player} is given.
     *
     * @return the number of blocks placed
     */
    public static int paste(ServerWorld world, Clip clip, BlockPos anchor, BlockRotation rotation, boolean keepOutsideLinks,
                            @Nullable ServerPlayerEntity player) {
        Function<BlockPos, BlockPos> place = source -> anchor.add(source.subtract(clip.anchor()).rotate(rotation));
        Function<BlockPos, BlockPos> follow = link -> clip.box().contains(link) ? place.apply(link) : keepOutsideLinks ? link : null;
        List<BlockPos> containers = new ArrayList<>();
        for (Entry entry : clip.entries()) {
            BlockPos target = place.apply(clip.anchor().add(entry.relative()));
            BlockState state = turn(entry.state(), rotation);
            BlockState before = world.getBlockState(target);
            NbtCompound beforeData = LinkHistory.BlockChange.data(world, target);
            world.setBlockState(target, state, Block.NOTIFY_ALL);
            BlockEntity blockEntity = world.getBlockEntity(target);
            if (entry.data() != null && blockEntity != null) {
                blockEntity.read(entry.data(), world.getRegistryManager());
                if (blockEntity instanceof CartridgeContainerBlockEntity container) {
                    remapCartridges(container, follow);
                    containers.add(target);
                }
                blockEntity.markDirty();
            }
            BlockState after = world.getBlockState(target);
            LinkHistory.record(player, new LinkHistory.BlockChange(target, before, beforeData, after, LinkHistory.BlockChange.data(world, target)));
        }
        for (BlockPos pos : containers) {
            if (world.getBlockEntity(pos) instanceof CartridgeContainerBlockEntity container) BoardLinks.sync(container);
        }
        return clip.entries().size();
    }

    /** A block turned; a large tile also turns the side it spreads toward from its own block (which stays its anchor). */
    private static BlockState turn(BlockState state, BlockRotation rotation) {
        BlockState turned = state.rotate(rotation);
        if (turned.getBlock() instanceof ATileBlock && turned.get(ATileBlock.SIZE).isLarge()) {
            TileLayout layout = turned.get(ATileBlock.SIZE);
            BlockPos side = new BlockPos(layout.dx(), 0, layout.dz()).rotate(rotation);
            turned = turned.with(ATileBlock.SIZE, TileLayout.large(side.getX(), side.getZ()));
        }
        return turned;
    }

    /** Links and chests of every cartridge through {@code follow} (null: cut); start tokens dropped. */
    private static void remapCartridges(CartridgeContainerBlockEntity container, Function<BlockPos, BlockPos> follow) {
        for (int slot = 0; slot < container.size(); slot++) {
            ItemStack cartridge = container.getStack(slot);
            if (!(cartridge.getItem() instanceof CartridgeItem)) continue;
            DestinationsComponent links = cartridge.get(ModComponents.DESTINATIONS_COMPONENT);
            if (links != null) {
                List<BlockPos> moved = new ArrayList<>();
                for (BlockPos link : links.destinations()) {
                    BlockPos target = follow.apply(link);
                    if (target != null) moved.add(target);
                }
                if (moved.isEmpty()) cartridge.remove(ModComponents.DESTINATIONS_COMPONENT);
                else cartridge.set(ModComponents.DESTINATIONS_COMPONENT, new DestinationsComponent(moved, links.world()));
            }
            // Its containers follow too (those that fall outside are dropped), in the world it is pasted in
            if (!CartridgeContainers.isEmpty(cartridge)) {
                var dimension = container.getWorld() == null ? World.OVERWORLD : container.getWorld().getRegistryKey();
                List<GlobalPos> chests = new ArrayList<>();
                for (GlobalPos chest : CartridgeContainers.of(cartridge, dimension)) {
                    BlockPos target = follow.apply(chest.pos());
                    if (target != null) chests.add(GlobalPos.create(dimension, target));
                }
                CartridgeContainers.set(cartridge, chests);
            }
            cartridge.remove(ModComponents.TB_START_BOUND_ENTITY);
        }
    }

    // ---------------------------------------------------------------- moved with another tool

    /**
     * After the board spaces of {@code box} were moved there by {@code offset} (WorldEdit, structure blocks...): their
     * links (and chests) that pointed into the former area follow them. Links to the outside are left as they are.
     *
     * @return the number of links and chests moved
     */
    public static int translate(ServerWorld world, BlockBox box, Vec3i offset, @Nullable ServerPlayerEntity player) {
        BlockBox former = box.offset(-offset.getX(), -offset.getY(), -offset.getZ());
        int moved = 0;
        for (BlockPos pos : BlockPos.iterate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMaxX(), box.getMaxY(), box.getMaxZ())) {
            if (!(world.getBlockEntity(pos) instanceof CartridgeContainerBlockEntity container)) continue;
            CartridgeContainerBlockEntity edited = BoardLinks.container(world, pos);
            if (edited == null) continue;
            for (int slot = 0; slot < container.size(); slot++) {
                ItemStack cartridge = container.getStack(slot);
                List<BlockPos> links = BoardLinks.links(cartridge);
                List<BlockPos> shifted = new ArrayList<>();
                int changed = 0;
                for (BlockPos link : links) {
                    if (former.contains(link)) {
                        shifted.add(link.add(offset));
                        changed++;
                    } else {
                        shifted.add(link);
                    }
                }
                if (changed > 0) {
                    WrenchActions.writeLinks(player, world, container, slot, shifted);
                    moved += changed;
                }
                // Its containers that were in the former area follow it
                List<GlobalPos> chests = CartridgeContainers.of(cartridge, world.getRegistryKey());
                List<GlobalPos> followed = new ArrayList<>();
                int chestsMoved = 0;
                for (GlobalPos chest : chests) {
                    boolean inside = chest.dimension().equals(world.getRegistryKey()) && former.contains(chest.pos());
                    followed.add(inside ? GlobalPos.create(chest.dimension(), chest.pos().add(offset)) : chest);
                    if (inside) chestsMoved++;
                }
                if (chestsMoved > 0) {
                    CartridgeContainers.set(cartridge, followed);
                    LinkHistory.record(player, new LinkHistory.ChestChange(pos.toImmutable(), slot, chests, followed));
                    BoardLinks.sync(container);
                    moved += chestsMoved;
                }
            }
        }
        return moved;
    }

    // ---------------------------------------------------------------- templates

    public enum Template { LOOP, LINE }

    /**
     * Places {@code count} Tiles in front of {@code origin} (toward {@code facing}), {@code spacing} blocks apart,
     * each with a Cartridge linked to the next one and turned toward it: a loop (closed, rectangular) or a line. The
     * first one gets a Start Cartridge. Recorded for undo when {@code player} is given.
     *
     * @return the positions of the tiles, in order
     */
    public static List<BlockPos> template(ServerWorld world, Template template, BlockPos origin, Direction facing, int count, int spacing,
                                          @Nullable ServerPlayerEntity player) {
        List<BlockPos> cells = new ArrayList<>();
        Direction right = facing.rotateYClockwise();
        if (template == Template.LINE) {
            for (int i = 0; i < count; i++) cells.add(origin.offset(facing, (i + 1) * spacing));
        } else {
            int half = count / 2;
            int length = Math.max(1, (half + 1) / 2), width = Math.max(1, half - length);
            BlockPos corner = origin.offset(facing, spacing);
            // Forward along the right side, across, back, across
            int[][] sides = {{0, length}, {1, width}, {2, length}, {3, width}};
            BlockPos cell = corner;
            cells.add(cell);
            for (int[] side : sides) {
                Direction direction = switch (side[0]) {
                    case 0 -> facing;
                    case 1 -> right;
                    case 2 -> facing.getOpposite();
                    default -> right.getOpposite();
                };
                for (int i = 0; i < side[1]; i++) {
                    cell = cell.offset(direction, spacing);
                    if (!cell.equals(corner)) cells.add(cell);
                }
            }
        }
        List<BlockState> befores = new ArrayList<>();
        List<NbtCompound> beforeData = new ArrayList<>();
        for (BlockPos cell : cells) {
            befores.add(world.getBlockState(cell));
            beforeData.add(LinkHistory.BlockChange.data(world, cell));
            world.setBlockState(cell, ModBlocks.TILE.getDefaultState(), Block.NOTIFY_ALL);
        }
        boolean closed = template == Template.LOOP;
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            if (!(world.getBlockEntity(cell) instanceof CartridgeContainerBlockEntity container)) continue;
            ItemStack cartridge = new ItemStack(i == 0 ? ModItems.TILE_BEHAVIOR_START : ModItems.BOARD_SPACE_BEHAVIOR);
            boolean last = i == cells.size() - 1;
            BlockPos next = last ? (closed ? cells.getFirst() : null) : cells.get(i + 1);
            BoardLinks.setLinks(cartridge, next == null ? List.of() : List.of(next), world);
            container.setStack(0, cartridge);
            if (next != null) BoardLinks.orient(world, cell, next);
            BoardLinks.sync(container);
        }
        for (int i = 0; i < cells.size(); i++) {
            BlockPos cell = cells.get(i);
            LinkHistory.record(player, new LinkHistory.BlockChange(cell, befores.get(i), beforeData.get(i),
                    world.getBlockState(cell), LinkHistory.BlockChange.data(world, cell)));
        }
        return cells;
    }
}
