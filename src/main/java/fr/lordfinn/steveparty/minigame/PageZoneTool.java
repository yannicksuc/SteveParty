package fr.lordfinn.steveparty.minigame;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.PageZoneMode;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * The zone of a mini-game is drawn with its page in hand, in <b>zone mode</b> ({@link PageZoneMode} on the item):
 * the « Tracer la zone » button of the page's editor closes it and puts the page in that mode.
 * <ul>
 *     <li>Right-click a block: the first corner. Right-click another block (same dimension): the second corner, and
 *     the page has its zone (a box with a side longer than {@link PageZone#MAX_SIDE} is refused, the first corner
 *     kept). Every block is a corner in this mode: a pipe or a controller clicked is not linked.</li>
 *     <li>Looking at a face of the zone (from inside or outside, from afar), sneak + mouse wheel moves that face by one
 *     block, outward (wheel up) or inward; with Ctrl, by {@value #FAST_STEP}.</li>
 *     <li>Sneak + right-click in the air, Échap, or opening the editor: the mode ends (the zone stays).</li>
 * </ul>
 * Out of the mode, a click with the page links pipes, podiums and controllers as ever. Everything is decided by the
 * server: the corners are the blocks it was told were clicked, the face moved is the one it finds the player looking
 * at, and only a player who may write on the page draws its zone. The page keeps the zone: its linked copies share it.
 */
public final class PageZoneTool {
    /** Blocks a face moves by with Ctrl held. */
    public static final int FAST_STEP = 4;

    private PageZoneTool() {
    }

    public static void initialize() {
        // Before the blocks: in zone mode, every block clicked is a corner (a pipe is not linked, a controller not filled)
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!isInMode(stack) || player.isSpectator()) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            if (player instanceof ServerPlayerEntity serverPlayer) click(serverPlayer, stack, hit.getBlockPos());
            return ActionResult.SUCCESS;
        });
    }

    public static boolean isInMode(ItemStack stack) {
        return MiniGamePages.isPage(stack) && stack.contains(ModComponents.PAGE_ZONE_MODE);
    }

    /** The page in zone mode the player holds (main hand first), null for none. */
    public static @Nullable ItemStack held(PlayerEntity player) {
        for (Hand hand : Hand.values()) if (isInMode(player.getStackInHand(hand))) return player.getStackInHand(hand);
        return null;
    }

    /** The first corner of the box being drawn with the page, empty for none. */
    public static Optional<GlobalPos> corner(ItemStack stack) {
        PageZoneMode mode = stack.get(ModComponents.PAGE_ZONE_MODE);
        return mode == null ? Optional.empty() : mode.corner();
    }

    /**
     * The page held in {@code hand} goes in zone mode (the editor's button).
     *
     * @return false if the player may not write on it
     */
    public static boolean start(ServerPlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        if (!MiniGamePages.isPage(stack) || !MiniGamePages.canEdit(player)) return false;
        MiniGamePages.ensureId(stack);
        stack.set(ModComponents.PAGE_ZONE_MODE, PageZoneMode.START);
        player.sendMessage(Text.translatable("message.steveparty.page_zone.mode"), true);
        player.playSoundToPlayer(SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.6F, 1.4F);
        return true;
    }

    /** The mode of the page ends (the zone stays). @return false if it was not in the mode */
    public static boolean end(@Nullable PlayerEntity player, ItemStack stack) {
        if (!stack.contains(ModComponents.PAGE_ZONE_MODE)) return false;
        stack.remove(ModComponents.PAGE_ZONE_MODE);
        if (player != null) player.sendMessage(Text.translatable("message.steveparty.page_zone.mode_end"), true);
        return true;
    }

    /** What a click on a block makes of the mode: a new first corner, or (the second corner) the box to give the page. */
    public record Click(Optional<GlobalPos> corner, Optional<BlockBox> box) {
    }

    /**
     * A block was clicked: the first corner of a box, or the second one of the box being drawn (in the same dimension).
     */
    public static Click click(Optional<GlobalPos> corner, GlobalPos clicked) {
        if (corner.isPresent() && corner.get().dimension().equals(clicked.dimension())) {
            return new Click(Optional.empty(), Optional.of(BlockBox.create(corner.get().pos(), clicked.pos())));
        }
        return new Click(Optional.of(GlobalPos.create(clicked.dimension(), clicked.pos().toImmutable())), Optional.empty());
    }

    /**
     * Server side: a block clicked with a page in zone mode.
     *
     * @return true if the page's zone changed
     */
    public static boolean click(ServerPlayerEntity player, ItemStack stack, BlockPos pos) {
        UUID id = MiniGamePages.idOf(stack);
        if (!isInMode(stack) || id == null) return false;
        if (!MiniGamePages.canEdit(player)) {
            end(player, stack);
            return false;
        }
        World world = player.getWorld();
        Click click = click(corner(stack), GlobalPos.create(world.getRegistryKey(), pos));
        if (click.box().isEmpty()) {
            stack.set(ModComponents.PAGE_ZONE_MODE, new PageZoneMode(click.corner()));
            player.sendMessage(Text.translatable("message.steveparty.page_zone.corner", pos(pos)), true);
            world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4F, 1.0F);
            return false;
        }
        BlockBox box = click.box().get();
        if (PageZone.tooBig(box)) {
            // The first corner stays: another block for the second one
            player.sendMessage(Text.translatable("message.steveparty.page_zone.too_big", size(box), PageZone.MAX_SIDE).formatted(Formatting.RED), true);
            return false;
        }
        stack.set(ModComponents.PAGE_ZONE_MODE, PageZoneMode.START);
        MiniGamePages.update(player.server, MiniGamePages.get(player.server, id).withZone(new PageZone(world.getRegistryKey(), box)));
        player.sendMessage(Text.translatable("message.steveparty.page_zone.zone", size(box)), true);
        world.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4F, 1.3F);
        return true;
    }

    /**
     * Sneak + mouse wheel (server side): the face of the page's zone the player looks at moves, outward when
     * {@code amount} is positive. Only a page in zone mode, a zone in the player's dimension, and one or
     * {@value #FAST_STEP} blocks at a time.
     *
     * @return true if a face moved
     */
    public static boolean scroll(ServerPlayerEntity player, @Nullable ItemStack stack, int amount) {
        UUID id = stack == null || !isInMode(stack) ? null : MiniGamePages.idOf(stack);
        if (id == null || amount == 0 || !player.isSneaking() || !MiniGamePages.canEdit(player)) return false;
        MiniGamePageData page = MiniGamePages.get(player.server, id);
        PageZone zone = page.zone();
        if (zone == null || !zone.dimension().equals(player.getWorld().getRegistryKey())) return false;
        Direction face = ZoneFaces.lookedAt(player.getEyePos(), player.getRotationVec(1), PageZone.bounds(zone.box()));
        if (face == null) {
            player.sendMessage(Text.translatable("message.steveparty.page_zone.no_face").formatted(Formatting.RED), true);
            return false;
        }
        int step = Integer.signum(amount) * (Math.abs(amount) >= FAST_STEP ? FAST_STEP : 1);
        World world = player.getWorld();
        BlockBox moved = ZoneFaces.moved(zone.box(), face, step, world.getBottomY(), world.getBottomY() + world.getHeight() - 1);
        if (moved == null) {
            player.sendMessage(Text.translatable(step > 0 ? "message.steveparty.page_zone.max" : "message.steveparty.page_zone.min",
                    PageZone.MAX_SIDE).formatted(Formatting.RED), true);
            return false;
        }
        MiniGamePages.update(player.server, page.withZone(new PageZone(zone.dimension(), moved)));
        player.sendMessage(Text.translatable("message.steveparty.page_zone.zone", size(moved)), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.3F, step > 0 ? 1.2F : 0.9F);
        return true;
    }

    // ------------------------------------------------------------------ texts

    /** « 24 × 12 × 30 ». */
    public static MutableText size(BlockBox box) {
        return Text.translatable("tooltip.steveparty.page_zone.size", box.getBlockCountX(), box.getBlockCountY(), box.getBlockCountZ());
    }

    private static Text pos(BlockPos pos) {
        return Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }
}
