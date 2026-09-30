package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.TileTeleport;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.TeleportTargetsComponent;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.cartridges.TeleportCartridgeItem;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The arrivals of the Teleport tiles, and the Wrench's Teleport mode that links them. They live in their own component
 * of the Teleport Cartridge ({@link TeleportTargetsComponent}), never among the path links: a token never walks them.
 * <ul>
 *     <li>no origin: a click on a Teleport tile picks it (a tile without cartridge gets one: the Teleport Cartridge in
 *     the off hand, or a new one in creative; a Teleport Cartridge in the off hand replaces another cartridge, its path
 *     links kept);</li>
 *     <li>then a click on a board space adds it as an arrival, a second click removes it; a click on the Teleport tile
 *     again unbinds.</li>
 * </ul>
 * Recorded for undo like the other Wrench edits.
 */
public final class TeleportLinks {
    /** Colour of the teleport trails (and of the board view's arcs). */
    public static final int COLOR = 0xB266FF;

    private TeleportLinks() {
    }

    // ---------------------------------------------------------------- reading and writing

    public static boolean isTeleportCartridge(ItemStack cartridge) {
        return cartridge.getItem() instanceof TeleportCartridgeItem;
    }

    /** The arrivals of the cartridge in {@code slot} (empty if it is no Teleport Cartridge). */
    public static List<BlockPos> targets(CartridgeContainerBlockEntity container, int slot) {
        return targets(container.getStack(slot));
    }

    public static List<BlockPos> targets(ItemStack cartridge) {
        if (!isTeleportCartridge(cartridge)) return List.of();
        return TileTeleport.settings(cartridge).targets();
    }

    /**
     * Writes the arrivals of the Teleport Cartridge in {@code slot}, saves and syncs the board space.
     *
     * @return false if it holds no Teleport Cartridge
     */
    public static boolean setTargets(CartridgeContainerBlockEntity container, int slot, List<BlockPos> targets) {
        ItemStack cartridge = container.getStack(slot);
        if (!isTeleportCartridge(cartridge)) return false;
        cartridge.set(ModComponents.TELEPORT_TARGETS, TileTeleport.settings(cartridge).withTargets(targets));
        BoardLinks.sync(container);
        return true;
    }

    /** Removes the arrivals of the cartridge in {@code slot} (a copied board space). @return how many */
    static int clearTargets(CartridgeContainerBlockEntity container, int slot) {
        int count = targets(container, slot).size();
        if (count > 0) setTargets(container, slot, List.of());
        return count;
    }

    /** Undo / redo of the arrivals of a Teleport Cartridge. */
    public record TargetsChange(BlockPos pos, int slot, List<BlockPos> before, List<BlockPos> after) implements LinkHistory.Change {
        @Override
        public boolean apply(ServerWorld world, boolean undo) {
            CartridgeContainerBlockEntity container = BoardLinks.container(world, pos);
            if (container == null || !targets(container, slot).equals(undo ? after : before)) return false;
            return setTargets(container, slot, undo ? before : after);
        }
    }

    // ---------------------------------------------------------------- the Wrench's Teleport mode

    static void click(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos, CartridgeContainerBlockEntity container) {
        WrenchState state = WrenchState.of(wrench);
        BlockPos origin = WrenchActions.origin(wrench, world);
        CartridgeContainerBlockEntity originContainer = origin == null ? null : BoardLinks.container(world, origin);
        int slot = originContainer == null ? 0 : BoardLinks.slotOf(originContainer, state.slot());
        if (originContainer == null || !isTeleportCartridge(originContainer.getStack(slot))) {
            pick(player, wrench, world, pos, container, state);
            return;
        }
        if (pos.equals(origin)) {
            WrenchActions.endChain(player, wrench, world, true);
            return;
        }
        if (!(container instanceof BoardSpaceBlockEntity)) {
            WrenchActions.say(player, Text.translatable("message.steveparty.wrench.teleport.not_board_space"));
            return;
        }
        List<BlockPos> before = List.copyOf(targets(originContainer, slot));
        List<BlockPos> after = new ArrayList<>(before);
        boolean removed = after.remove(pos);
        if (!removed) after.add(pos.toImmutable());
        write(player, originContainer, slot, before, after);
        if (removed) {
            BoardLinks.trail(world, origin, pos, BoardLinks.CUT_COLOR);
            WrenchActions.say(player, Text.translatable("message.steveparty.wrench.teleport.removed", BoardText.pos(pos),
                    BoardText.pos(origin), BoardText.num(after.size())));
            WrenchActions.playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 1f);
        } else {
            arc(world, origin, pos);
            WrenchActions.say(player, Text.translatable("message.steveparty.wrench.teleport.added", BoardText.pos(pos),
                    BoardText.pos(origin), BoardText.num(after.size())));
            WrenchActions.playSound(world, player, SoundEvents.ITEM_CHORUS_FRUIT_TELEPORT, 1.6f);
        }
    }

    /** No Teleport tile picked yet: {@code pos} becomes the origin if it is (or can be made) one. */
    private static void pick(ServerPlayerEntity player, ItemStack wrench, ServerWorld world, BlockPos pos,
                             CartridgeContainerBlockEntity container, WrenchState state) {
        if (!(container instanceof BoardSpaceBlockEntity)) {
            WrenchActions.say(player, Text.translatable("message.steveparty.wrench.teleport.not_board_space"));
            return;
        }
        int slot = BoardLinks.slotOf(container, WrenchState.ACTIVE_SLOT);
        if (!isTeleportCartridge(container.getStack(slot)) && !supplyCartridge(player, world, pos, container, slot)) {
            WrenchActions.say(player, Text.translatable("message.steveparty.wrench.teleport.not_teleport", BoardText.pos(pos)));
            WrenchActions.playSound(world, player, ModSounds.CANCEL_SOUND_EVENT, 0.7f);
            return;
        }
        WrenchActions.setOrigin(wrench, world, pos, state.withChain(Optional.empty(), 0).withSlot(WrenchState.ACTIVE_SLOT));
        List<BlockPos> targets = targets(container, slot);
        for (BlockPos target : targets) arc(world, pos, target);
        WrenchActions.say(player, Text.translatable("message.steveparty.wrench.teleport.origin", BoardText.pos(pos), BoardText.num(targets.size())));
        WrenchActions.playSound(world, player, SoundEvents.BLOCK_BEACON_ACTIVATE, 1.4f);
    }

    /**
     * Puts a Teleport Cartridge in the board space: the one in the off hand (replacing another cartridge, links kept),
     * or a new one in creative when the slot is empty.
     *
     * @return true if it holds a Teleport Cartridge now
     */
    private static boolean supplyCartridge(ServerPlayerEntity player, ServerWorld world, BlockPos pos, CartridgeContainerBlockEntity container, int slot) {
        ItemStack offHand = player.getOffHandStack();
        ItemStack current = container.getStack(slot);
        if (isTeleportCartridge(offHand)) {
            if (!current.isEmpty()) WrenchActions.swapCartridge(player, world, pos, container, slot);
            else BoardLinks.ensureCartridge(player, container, slot);
            // Never the arrivals of the stack it comes from (they belong to another tile)
            if (!targets(container, slot).isEmpty()) setTargets(container, slot, List.of());
        } else if (current.isEmpty() && player.getAbilities().creativeMode) {
            container.setStack(slot, new ItemStack(ModItems.TELEPORT_CARTRIDGE));
            BoardLinks.sync(container);
        }
        return isTeleportCartridge(container.getStack(slot));
    }

    private static void write(@Nullable ServerPlayerEntity player, CartridgeContainerBlockEntity container, int slot,
                              List<BlockPos> before, List<BlockPos> after) {
        if (setTargets(container, slot, after)) {
            LinkHistory.record(player, new TargetsChange(container.getPos().toImmutable(), slot, before, List.copyOf(after)));
        }
    }

    /** A trail of purple sparkles arching from the teleport tile to its arrival (seen by everyone around). */
    public static void arc(ServerWorld world, BlockPos from, BlockPos to) {
        Vec3d a = BoardSpaces.standPos(world, from).add(0, 0.25, 0), b = BoardSpaces.standPos(world, to).add(0, 0.25, 0);
        double length = a.distanceTo(b);
        double height = arcHeight(length);
        int count = Math.max(4, (int) Math.ceil(length / 0.4));
        net.minecraft.particle.DustParticleEffect dust = new net.minecraft.particle.DustParticleEffect(COLOR, 1.0F);
        for (int i = 0; i <= count; i++) {
            double t = i / (double) count;
            Vec3d p = a.lerp(b, t).add(0, 4 * height * t * (1 - t), 0);
            world.spawnParticles(i % 3 == 0 ? new fr.lordfinn.steveparty.particles.MulaSparkleEffect(TileTeleport.ACCENT, 0.8F,
                    fr.lordfinn.steveparty.particles.MulaSparkleEffect.TWINKLE) : dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }

    /** How high the arc between two spaces {@code length} blocks apart goes (shared with the board view). */
    public static double arcHeight(double length) {
        return Math.clamp(0.6 + 0.2 * length, 0.8, 4.0);
    }
}
