package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the Tile Linker Brush does, server side: links are painted. Holding the use button, every board space the
 * player aims at (up to {@link WrenchActions#LONG_REACH} blocks) is linked from the previous one of the stroke; going
 * over a link again (either way) erases it. The stroke ends when the button is released.
 * <p>
 * The link goes in the cartridge of the brush's level (0-15, the slot the redstone power selects on a 16-slot board
 * space; none: the slot powered at the time, as the Wrench does); a board space with a single slot ignores it. Every
 * painted link is a Wrench action: undone with a left click in the air.
 */
public final class TileLinkerBrush {
    /** Vanilla repeats the use every 4 ticks while the button is held: longer without one, the stroke has ended. */
    private static final int STROKE_GAP = 6;
    /** No level: the slot powered at the time (the active one). */
    public static final int POWERED = -1;

    private static final class Stroke {
        @Nullable BlockPos last;
        long lastUse;
    }

    private static final Map<UUID, Stroke> STROKES = fr.lordfinn.steveparty.utils.ServerMemory.forgetOnStop(new HashMap<>());

    private TileLinkerBrush() {
    }

    public static void initialize() {
        // Between two repeated uses, the aimed board space is painted every tick: a quick sweep skips none
        ServerTickEvents.END_SERVER_TICK.register(TileLinkerBrush::tick);
    }

    private static void tick(MinecraftServer server) {
        if (STROKES.isEmpty()) return;
        Iterator<Map.Entry<UUID, Stroke>> it = STROKES.entrySet().iterator();
        List<Runnable> paints = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Stroke> entry = it.next();
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !(player.getMainHandStack().getItem() instanceof TileLinkerBrushItem)
                    || player.getServerWorld().getTime() - entry.getValue().lastUse > STROKE_GAP) {
                it.remove();
                continue;
            }
            paints.add(() -> paintAimed(player, player.getMainHandStack(), player.getServerWorld()));
        }
        paints.forEach(Runnable::run);
    }

    /** The level of the brush: 0-15, or {@link #POWERED}. */
    public static int level(ItemStack brush) {
        Integer level = brush.get(ModComponents.LINK_LEVEL);
        return level == null || level < 0 || level > 15 ? POWERED : level;
    }

    /** Next / previous level: powered, 0, 1... 15, powered. */
    public static void cycleLevel(ServerPlayerEntity player, ItemStack brush, int direction) {
        int level = Math.floorMod(level(brush) + 1 + Integer.signum(direction == 0 ? 1 : direction), 17) - 1;
        if (level == POWERED) brush.remove(ModComponents.LINK_LEVEL);
        else brush.set(ModComponents.LINK_LEVEL, level);
        player.sendMessage(Text.translatable("message.steveparty.tile_linker_brush.level", levelText(level)), true);
        player.getWorld().playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.6f, 1.2f + Math.max(level, 0) * 0.04f);
    }

    public static Text levelText(int level) {
        return level == POWERED ? Text.translatable("item.steveparty.tile_linker_brush.level.powered") : Text.literal(Integer.toString(level));
    }

    /** A use of the brush (the button pressed, or repeated while held): the stroke goes on, or a new one starts. */
    public static void use(ServerPlayerEntity player, ItemStack brush, ServerWorld world) {
        Stroke stroke = STROKES.get(player.getUuid());
        long now = world.getTime();
        if (stroke == null || now - stroke.lastUse > STROKE_GAP || now < stroke.lastUse) {
            stroke = new Stroke();
            STROKES.put(player.getUuid(), stroke);
        }
        stroke.lastUse = now;
        paintAimed(player, brush, world);
    }

    /** Ends the stroke of {@code player} (the next use starts a new one). */
    public static void endStroke(ServerPlayerEntity player) {
        STROKES.remove(player.getUuid());
    }

    private static void paintAimed(ServerPlayerEntity player, ItemStack brush, ServerWorld world) {
        BlockPos pos = WrenchActions.aimedBoardSpace(player, world);
        if (pos != null) paint(player, brush, world, pos);
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
        if (origin == null || target == null) {
            say(player, Text.translatable("message.steveparty.tile_linker_brush.start", BoardText.pos(pos)));
            world.playSound(null, player.getBlockPos(), ModSounds.SELECT_SOUND_EVENT, SoundCategory.PLAYERS, 0.5f, 1.4f);
            return;
        }
        WrenchActions.recorded(player, world, null, () -> link(player, brush, world, from, origin, pos, target));
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
            say(player, Text.translatable("message.steveparty.wrench.trace.not_board_space"));
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
