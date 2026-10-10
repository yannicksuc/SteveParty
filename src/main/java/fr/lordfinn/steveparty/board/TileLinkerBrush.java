package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.BlockOriginComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the Tile Linker Brush does, server side: links are painted. Holding the use button (the brush held in use, see
 * {@link TileLinkerBrushItem#usageTick}), every board space the player's look sweeps over (up to {@link WrenchActions#LONG_REACH} blocks, found by what is seen: see {@link BrushAim})
 * is linked from the previous one of the stroke; going over a link again the same way erases it (the other way adds
 * the way back, both kept). The stroke ends when
 * the button is released. Between two ticks, the look is followed step by step: a quick sweep skips no tile.
 * <p>
 * The dangling links of the board spaces around (to a cell without board space) are <b>ghosts</b> the brush aims at
 * like tiles: reached from their board space, the link is erased; a ghost is only a target, the stroke goes on from the
 * last board space. A <b>blob</b> (« un pâté »: the look lingering on one spot without board space, see
 * {@link BrushAim.Blob}) links the last board space of the stroke to the cell of that spot (see
 * {@link BrushAim#blobCell}): a cell planned for a tile, a ghost until one is placed there.
 * <p>
 * The brush remembers its <b>anchor</b>, the last board space it painted (on the item, never shown: the brush selects
 * nothing, it paints as it sweeps): a chest clicked then joins the
 * anchor's inventory tile, a trading stall, cash register or Boxed Trader becomes its shop (see
 * {@link WrenchActions#initialize}), and with the brush in the off hand, each placed board space is linked from it.
 * <p>
 * Its settings are picked on its wheel (left click, client side): the level (0-15, the slot the redstone power selects
 * on a 16-slot board space; none: the slot powered at the time), the kind of Cartridge the board spaces a painted path
 * reaches get (theirs swapped for it, links kept; never the space a stroke starts from; none picked: they keep theirs,
 * an empty one gets a plain Cartridge), undo and
 * redo. A board space with a single slot ignores the level.
 */
public final class TileLinkerBrush {
    /** The held brush paints every tick: longer without a use, the stroke has ended. */
    private static final int STROKE_GAP = 6;
    /** No level: the slot powered at the time (the active one). */
    public static final int POWERED = -1;
    /** A sweep between two ticks is followed by this many degrees at most per step... */
    private static final float SWEEP_STEP = 1.5f;
    /** ...in this many steps at most. */
    private static final int MAX_SWEEP_STEPS = 16;

    private static final class Stroke {
        @Nullable BlockPos last;
        long lastUse;
        float pitch, yaw;
        boolean looked;
        final BrushAim.Blob blob = new BrushAim.Blob();
        /** The cells blobbed or whose ghost was erased in this stroke: neither erased nor blobbed again before it ends. */
        final Set<BlockPos> cells = new HashSet<>();
        Set<BlockPos> ghosts = Set.of();
        /**
         * The board space the stroke started on, while nothing was linked from it yet: it gets the picked kind of
         * Cartridge once a path is painted from it, or when the stroke ends on it alone; never after linking (or failing
         * to link) its Spawn Marker or a chest.
         */
        @Nullable BlockPos start;
    }

    private static final Map<UUID, Stroke> STROKES = ServerMemory.forgetOnStop(new HashMap<>());

    private TileLinkerBrush() {
    }

    public static void initialize() {
        // The strokes left without an end (brush put away, player gone) are forgotten
        ServerTickEvents.END_SERVER_TICK.register(TileLinkerBrush::tick);
    }

    private static void tick(MinecraftServer server) {
        if (STROKES.isEmpty()) return;
        STROKES.entrySet().removeIf(entry -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            return player == null || !isBrush(player.getMainHandStack())
                    || player.getServerWorld().getTime() - entry.getValue().lastUse > STROKE_GAP;
        });
    }

    public static boolean isBrush(ItemStack stack) {
        return stack.getItem() instanceof TileLinkerBrushItem;
    }

    // ---------------------------------------------------------------- settings (picked on the wheel)

    /** The level of the brush: 0-15, or {@link #POWERED}. */
    public static int level(ItemStack brush) {
        Integer level = brush.get(ModComponents.LINK_LEVEL);
        return level == null || level < 0 || level > 15 ? POWERED : level;
    }

    /** Sets the level ({@link #POWERED} or 0-15; anything else is ignored). */
    public static boolean setLevel(ServerPlayerEntity player, ItemStack brush, int level) {
        if (level < POWERED || level > 15) return false;
        if (level == POWERED) brush.remove(ModComponents.LINK_LEVEL);
        else brush.set(ModComponents.LINK_LEVEL, level);
        player.sendMessage(Text.translatable("message.steveparty.tile_linker_brush.level", levelText(level)), true);
        player.playSoundToPlayer(SoundEvents.BLOCK_LEVER_CLICK, SoundCategory.PLAYERS, 0.5f, 0.8f + Math.max(level, 0) * 0.05f);
        return true;
    }

    public static Text levelText(int level) {
        return level == POWERED ? Text.translatable("item.steveparty.tile_linker_brush.level.powered") : Text.literal(Integer.toString(level));
    }

    /**
     * The kind of Cartridge picked on the wheel: the board spaces a painted path reaches get one of it instead of theirs (taken from the
     * inventory, theirs given back). Null: none picked, the board spaces keep their cartridge (an empty one gets a plain
     * Cartridge).
     */
    public static @Nullable Item cartridge(ItemStack brush) {
        Item item = brush.get(ModComponents.LINK_CARTRIDGE);
        return item instanceof CartridgeItem ? item : null;
    }

    /** Picks the kind of Cartridge the painted board spaces get; null: none, they keep theirs. Not a cartridge: ignored. */
    public static boolean setCartridge(ServerPlayerEntity player, ItemStack brush, @Nullable Item cartridge) {
        if (cartridge == null) {
            brush.remove(ModComponents.LINK_CARTRIDGE);
            player.sendMessage(Text.translatable("message.steveparty.tile_linker_brush.cartridge.keep"), true);
        } else {
            if (!(cartridge instanceof CartridgeItem)) return false;
            brush.set(ModComponents.LINK_CARTRIDGE, cartridge);
            player.sendMessage(Text.translatable("message.steveparty.tile_linker_brush.cartridge", new ItemStack(cartridge).getName()), true);
        }
        player.playSoundToPlayer(ModSounds.SELECT_SOUND_EVENT, SoundCategory.PLAYERS, 0.5f, 1.3f);
        return true;
    }

    /** Undo (or redo) the last link edit; the anchor follows. */
    public static boolean undo(ServerPlayerEntity player, ItemStack brush, boolean undo) {
        if (!LinkHistory.undo(player, undo, brush)) return false;
        player.playSoundToPlayer(ModSounds.CANCEL_SOUND_EVENT, SoundCategory.PLAYERS, 0.6f, undo ? 0.8f : 1.2f);
        return true;
    }

    // ---------------------------------------------------------------- anchor

    /** The last board space the brush painted, in {@code world}, or null. */
    public static @Nullable BlockPos anchor(ItemStack brush, World world) {
        BlockOriginComponent origin = brush.get(ModComponents.BLOCK_ORIGIN_COMPONENT);
        if (origin == null || origin.origin().equals(BlockOriginComponent.DEFAULT_ORIGIN)) return null;
        if (!origin.world().isEmpty() && !origin.world().equals(BoardLinks.worldName(world))) return null;
        return origin.origin();
    }

    static void setAnchor(ItemStack brush, World world, BlockPos pos) {
        if (pos.equals(anchor(brush, world))) return;
        brush.set(ModComponents.BLOCK_ORIGIN_COMPONENT, new BlockOriginComponent(pos.toImmutable(), BoardLinks.worldName(world)));
    }

    // ---------------------------------------------------------------- strokes

    /** A use of the brush (the button pressed, then each tick while held): the stroke goes on, or a new one starts. */
    public static void use(ServerPlayerEntity player, ItemStack brush, ServerWorld world) {
        Stroke stroke = STROKES.get(player.getUuid());
        long now = world.getTime();
        if (stroke == null || now - stroke.lastUse > STROKE_GAP || now < stroke.lastUse) {
            stroke = new Stroke();
            STROKES.put(player.getUuid(), stroke);
        }
        stroke.lastUse = now;
        stroke.ghosts = BrushAim.ghosts(player, world, level(brush)).keySet();
        BlockPos aimed = sweep(player, brush, world, stroke);
        BlockHitResult surface = aimed == null ? BrushAim.surface(player, world) : null;
        if (stroke.blob.tick(surface == null ? null : surface.getPos())) blob(player, brush, world, stroke, surface);
    }

    /** Ends the stroke of {@code player} (the next use starts a new one). */
    public static void endStroke(ServerPlayerEntity player) {
        Stroke stroke = STROKES.remove(player.getUuid());
        // A space only touched: it gets the picked kind of Cartridge
        if (stroke != null && stroke.start != null && isBrush(player.getMainHandStack())) {
            giveCartridge(player, player.getMainHandStack(), player.getServerWorld(), stroke.start);
        }
    }

    /** Paints every board space (or ghost) the look crossed since the last tick, in order; the one aimed now, or null. */
    private static @Nullable BlockPos sweep(ServerPlayerEntity player, ItemStack brush, ServerWorld world, Stroke stroke) {
        float pitch = player.getPitch(), yaw = player.getYaw();
        int steps = 1;
        float fromPitch = pitch, fromYaw = yaw;
        if (stroke.looked) {
            fromPitch = stroke.pitch;
            fromYaw = stroke.yaw;
            float turned = Math.max(Math.abs(pitch - fromPitch), Math.abs(MathHelper.wrapDegrees(yaw - fromYaw)));
            steps = MathHelper.clamp(MathHelper.ceil(turned / SWEEP_STEP), 1, MAX_SWEEP_STEPS);
        }
        stroke.looked = true;
        stroke.pitch = pitch;
        stroke.yaw = yaw;
        BlockPos last = null, pos = null;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            pos = BrushAim.aimed(player, world, MathHelper.lerp(t, fromPitch, pitch),
                    fromYaw + MathHelper.wrapDegrees(yaw - fromYaw) * t, stroke.ghosts,
                    target -> BrushLinks.aims(world, stroke.last, level(brush), target));
            if (pos == null || pos.equals(last)) continue;
            last = pos;
            paint(player, brush, world, pos);
        }
        return pos;
    }

    /**
     * The stroke of {@code player} reaches the block at {@code pos}: a holder (a block holding a cartridge, see
     * {@link BrushLinks}) is linked from the previous one of the stroke and the stroke goes on from it; anything else
     * (a ghost, a chest, a stall, a switchable block...) is only a target of the previous holder.
     */
    public static void paint(ServerPlayerEntity player, ItemStack brush, ServerWorld world, BlockPos pos) {
        Stroke stroke = STROKES.computeIfAbsent(player.getUuid(), uuid -> {
            Stroke started = new Stroke();
            started.lastUse = world.getTime();
            return started;
        });
        if (pos.equals(stroke.last)) return;
        if (!BrushLinks.isHolder(world, pos)) {
            target(player, brush, world, stroke, pos);
            return;
        }
        BlockPos from = stroke.last;
        stroke.last = pos.toImmutable();
        BrushLinks.Held origin = from == null ? null : BrushLinks.holder(world, from, level(brush));
        if (origin == null) {
            // The first holder of a stroke: only the brush touching it (nothing selected, nothing said); its cartridge
            // waits to know what the stroke links from it
            setAnchor(brush, world, pos);
            stroke.start = pos.toImmutable();
            world.playSound(null, player.getBlockPos(), SoundEvents.ITEM_BRUSH_BRUSHING_GENERIC, SoundCategory.PLAYERS, 0.5f, 1.2f);
            return;
        }
        if (isPath(world, brush, from, pos)) {
            if (from.equals(stroke.start)) giveCartridge(player, brush, world, from);
            giveCartridge(player, brush, world, pos);
        }
        stroke.start = null;
        WrenchActions.recorded(player, world, brush, () -> {
            setAnchor(brush, world, pos);
            link(player, brush, world, from, pos);
        });
    }

    /** Whether reaching {@code to} from the holder {@code from} paints a path between board spaces (not a container link). */
    private static boolean isPath(World world, ItemStack brush, BlockPos from, BlockPos to) {
        return BrushLinks.kindFor(BrushLinks.of(world, from, level(brush)), world, to) instanceof CartridgeLinks.BoardPaths;
    }

    /**
     * A kind of Cartridge picked on the wheel: the board space at {@code pos} gets one in the slot of the brush's level
     * (links kept, its own given back, see {@link WrenchActions#swapCartridge}). Given to the spaces a path is painted
     * between, and to the space a stroke only touched; never to a space whose Spawn Marker or chests a stroke links (or
     * fails to), nor to one reached as a container: a mob, shop or chest space keeps its role.
     */
    private static void giveCartridge(ServerPlayerEntity player, ItemStack brush, ServerWorld world, BlockPos pos) {
        if (cartridge(brush) == null || !(BoardLinks.container(world, pos) instanceof BoardSpaceBlockEntity target)) return;
        WrenchActions.swapCartridge(player, world, target.getPos(), target, BoardLinks.slotOf(target, level(brush)), true);
    }

    /** From the holder {@code from} to the holder {@code to}: its link erased if there is one, else added if it can be. */
    private static void link(ServerPlayerEntity player, ItemStack brush, ServerWorld world, BlockPos from, BlockPos to) {
        List<BrushLinkable> kinds = BrushLinks.of(world, from, level(brush));
        BrushLinkable kind = BrushLinks.kindFor(kinds, world, to);
        if (kind == null) {
            // A holder linked to nothing of the kind: a router, a Hop Switch...
            boolean board = kinds.stream().anyMatch(k -> k instanceof CartridgeLinks.BoardPaths);
            WrenchActions.warn(player, Text.translatable(board ? "message.steveparty.tile_linker_brush.not_board_space"
                    : "message.steveparty.tile_linker_brush.not_target", BoardText.pos(to)));
            return;
        }
        toggle(player, world, from, kind, to);
    }

    /**
     * Something not holding a cartridge reached (a ghost, a chest, a stall...): a target of the last holder of the
     * stroke, linked or unlinked once per stroke; the stroke goes on from that holder.
     */
    private static void target(ServerPlayerEntity player, ItemStack brush, ServerWorld world, Stroke stroke, BlockPos pos) {
        BlockPos from = stroke.last;
        if (from == null || stroke.cells.contains(pos)) return;
        BrushLinkable kind = BrushLinks.kindFor(BrushLinks.of(world, from, level(brush)), world, pos);
        // Its marker or a chest reached (or refused): the space the stroke started on keeps its cartridge
        if (kind != null && !(kind instanceof CartridgeLinks.BoardPaths)) stroke.start = null;
        if (kind == null) {
            // A chest or a Spawn Marker the cartridge takes none of: said, once a stroke
            String refusal = BrushLinks.refusal(world, from, level(brush), pos);
            if (refusal != null) {
                stroke.start = null;
                stroke.cells.add(pos.toImmutable());
                WrenchActions.warn(player, Text.translatable(refusal, BoardText.pos(from), BoardText.pos(pos)));
            }
            return;
        }
        // A ghost is only erased (a blob plans a cell, see blob)
        if (kind == null || (kind instanceof CartridgeLinks.BoardPaths && !kind.linked(world, pos))) return;
        stroke.cells.add(pos.toImmutable());
        WrenchActions.recorded(player, world, brush, () -> toggle(player, world, from, kind, pos));
    }

    /** {@code target} linked from the holder at {@code from} (the kind {@code kind} of its links), or unlinked if it was. */
    static void toggle(ServerPlayerEntity player, ServerWorld world, BlockPos from, BrushLinkable kind, BlockPos target) {
        BrushLinks.Held held = BrushLinks.holder(world, from, POWERED);
        if (held != null && !held.canEdit(player)) {
            cannotEdit(player, from);
            return;
        }
        if (kind.linked(world, target)) kind.unlink(player, world, target);
        else kind.link(player, world, target);
    }

    /** The player may not change the holder at {@code pos} (adventure mode, protected area, a party running...). */
    static void cannotEdit(ServerPlayerEntity player, BlockPos pos) {
        WrenchActions.warn(player, Text.translatable("message.steveparty.tile_linker_brush.cannot_edit", BoardText.pos(pos)));
    }

    /** A blob made on {@code surface}: the last board space of the stroke is linked to its cell. */
    private static void blob(ServerPlayerEntity player, ItemStack brush, ServerWorld world, Stroke stroke, BlockHitResult surface) {
        BlockPos from = stroke.last;
        CartridgeContainerBlockEntity origin = from == null ? null : BoardLinks.container(world, from);
        if (origin == null) return;
        BlockPos cell = BrushAim.blobCell(world, surface.getBlockPos());
        int slot = BoardLinks.slotOf(origin, level(brush));
        if (cell.equals(from) || stroke.cells.contains(cell) || BoardLinks.links(origin, slot).contains(cell)) return;
        stroke.cells.add(cell);
        // A path planned from the space the stroke started on: it gets the picked kind of Cartridge
        if (from.equals(stroke.start)) {
            stroke.start = null;
            giveCartridge(player, brush, world, from);
        }
        WrenchActions.recorded(player, world, brush, () -> {
            if (!WrenchActions.addLink(player, world, from, origin, slot, cell)) return;
            say(player, Text.translatable("message.steveparty.tile_linker_brush.blob", BoardText.pos(from), BoardText.pos(cell)));
            world.playSound(null, surface.getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.PLAYERS, 0.7f, 0.8f);
        });
    }

    static void erased(ServerPlayerEntity player, ServerWorld world, BlockPos from, BlockPos to) {
        say(player, Text.translatable("message.steveparty.tile_linker_brush.erased", BoardText.pos(from), BoardText.pos(to)));
        world.playSound(null, player.getBlockPos(), ModSounds.CANCEL_SOUND_EVENT, SoundCategory.PLAYERS, 0.6f, 1f);
    }

    private static void say(ServerPlayerEntity player, Text text) {
        WrenchActions.say(player, text);
    }
}
