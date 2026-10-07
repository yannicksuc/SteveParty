package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.BlockOriginComponent;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the Tile Linker Brush does, server side: links are painted. Holding the use button, every board space the
 * player's look sweeps over (up to {@link WrenchActions#LONG_REACH} blocks, found by what is seen: see {@link BrushAim})
 * is linked from the previous one of the stroke; going over a link again (either way) erases it. The stroke ends when
 * the button is released. Between two ticks, the look is followed step by step: a quick sweep skips no tile.
 * <p>
 * The brush remembers its <b>anchor</b>, the last board space it painted (on the item): a chest clicked then joins the
 * anchor's inventory tile, a trading stall, cash register or Boxed Trader becomes its shop (see
 * {@link WrenchActions#initialize}), and with the brush in the off hand, each placed board space is linked from it.
 * <p>
 * Its settings are picked on its wheel (left click, client side): the level (0-15, the slot the redstone power selects
 * on a 16-slot board space; none: the slot powered at the time), the kind of Cartridge the painted board spaces get
 * (theirs swapped for it, links kept; none picked: they keep theirs, an empty one gets a plain Cartridge), undo and
 * redo. A board space with a single slot ignores the level.
 */
public final class TileLinkerBrush {
    /** Vanilla repeats the use every 4 ticks while the button is held: longer without one, the stroke has ended. */
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
    }

    private static final Map<UUID, Stroke> STROKES = fr.lordfinn.steveparty.utils.ServerMemory.forgetOnStop(new HashMap<>());

    private TileLinkerBrush() {
    }

    public static void initialize() {
        // Between two repeated uses, the sweep is painted every tick
        ServerTickEvents.END_SERVER_TICK.register(TileLinkerBrush::tick);
    }

    private static void tick(MinecraftServer server) {
        if (STROKES.isEmpty()) return;
        Iterator<Map.Entry<UUID, Stroke>> it = STROKES.entrySet().iterator();
        List<Runnable> paints = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Stroke> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !isBrush(player.getMainHandStack())
                    || player.getServerWorld().getTime() - entry.getValue().lastUse > STROKE_GAP) {
                it.remove();
                continue;
            }
            Stroke stroke = entry.getValue();
            paints.add(() -> sweep(player, player.getMainHandStack(), player.getServerWorld(), stroke));
        }
        paints.forEach(Runnable::run);
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
     * The kind of Cartridge picked on the wheel: the board spaces painted get one of it instead of theirs (taken from the
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

    /** A use of the brush (the button pressed, or repeated while held): the stroke goes on, or a new one starts. */
    public static void use(ServerPlayerEntity player, ItemStack brush, ServerWorld world) {
        Stroke stroke = STROKES.get(player.getUuid());
        long now = world.getTime();
        if (stroke == null || now - stroke.lastUse > STROKE_GAP || now < stroke.lastUse) {
            stroke = new Stroke();
            STROKES.put(player.getUuid(), stroke);
        }
        stroke.lastUse = now;
        sweep(player, brush, world, stroke);
    }

    /** Ends the stroke of {@code player} (the next use starts a new one). */
    public static void endStroke(ServerPlayerEntity player) {
        STROKES.remove(player.getUuid());
    }

    /** Paints every board space the look crossed since the last tick, in order. */
    private static void sweep(ServerPlayerEntity player, ItemStack brush, ServerWorld world, Stroke stroke) {
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
        BlockPos last = null;
        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            BlockPos pos = BrushAim.aimed(player, world, MathHelper.lerp(t, fromPitch, pitch),
                    fromYaw + MathHelper.wrapDegrees(yaw - fromYaw) * t);
            if (pos == null || pos.equals(last)) continue;
            last = pos;
            paint(player, brush, world, pos);
        }
    }

    /** The stroke of {@code player} reaches the board space (or router) at {@code pos}. */
    public static void paint(ServerPlayerEntity player, ItemStack brush, ServerWorld world, BlockPos pos) {
        Stroke stroke = STROKES.computeIfAbsent(player.getUuid(), uuid -> {
            Stroke started = new Stroke();
            started.lastUse = world.getTime();
            return started;
        });
        if (pos.equals(stroke.last)) return;
        BlockPos from = stroke.last;
        stroke.last = pos.toImmutable();
        CartridgeContainerBlockEntity origin = from == null ? null : BoardLinks.container(world, from);
        CartridgeContainerBlockEntity target = BoardLinks.container(world, pos);
        // A kind of Cartridge picked on the wheel: the painted board space gets one (links kept)
        if (target instanceof BoardSpaceBlockEntity && cartridge(brush) != null) {
            WrenchActions.swapCartridge(player, world, pos, target, BoardLinks.slotOf(target, level(brush)), true);
        }
        if (origin == null || target == null) {
            if (target != null) setAnchor(brush, world, pos);
            say(player, Text.translatable("message.steveparty.tile_linker_brush.start", BoardText.pos(pos)));
            world.playSound(null, player.getBlockPos(), ModSounds.SELECT_SOUND_EVENT, SoundCategory.PLAYERS, 0.5f, 1.4f);
            return;
        }
        WrenchActions.recorded(player, world, brush, () -> {
            setAnchor(brush, world, pos);
            link(player, brush, world, from, origin, pos, target);
        });
    }

    private static void link(ServerPlayerEntity player, ItemStack brush, ServerWorld world, BlockPos from,
                             CartridgeContainerBlockEntity origin, BlockPos to, CartridgeContainerBlockEntity target) {
        int level = level(brush);
        int slot = BoardLinks.slotOf(origin, level);
        // Over a link again (either way): erased
        if (BoardLinks.links(origin, slot).contains(to)) {
            WrenchActions.removeLink(player, world, origin, slot, to);
            erased(player, world, from, to);
            return;
        }
        int back = BoardLinks.slotOf(target, level);
        if (BoardLinks.links(target, back).contains(from)) {
            WrenchActions.removeLink(player, world, target, back, from);
            erased(player, world, to, from);
            return;
        }
        if (!(target instanceof BoardSpaceBlockEntity)) {
            say(player, Text.translatable("message.steveparty.tile_linker_brush.not_board_space"));
            return;
        }
        if (WrenchActions.addLink(player, world, from, origin, slot, to)) {
            say(player, Text.translatable("message.steveparty.tile_linker_brush.linked", BoardText.pos(from), BoardText.pos(to)));
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.45f, 1.2f);
        }
    }

    private static void erased(ServerPlayerEntity player, ServerWorld world, BlockPos from, BlockPos to) {
        say(player, Text.translatable("message.steveparty.tile_linker_brush.erased", BoardText.pos(from), BoardText.pos(to)));
        world.playSound(null, player.getBlockPos(), ModSounds.CANCEL_SOUND_EVENT, SoundCategory.PLAYERS, 0.6f, 1f);
    }

    private static void say(ServerPlayerEntity player, Text text) {
        WrenchActions.say(player, text);
    }
}
