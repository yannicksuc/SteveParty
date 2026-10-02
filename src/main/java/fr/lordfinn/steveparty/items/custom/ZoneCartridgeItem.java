package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.components.ZoneSelection;
import fr.lordfinn.steveparty.minigame.PageZone;
import fr.lordfinn.steveparty.minigame.ZoneFaces;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.RegistryKey;
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
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * The Zone Cartridge: draws the zone of a mini-game, a box of blocks, and carries it ({@link ZoneSelection}). In the
 * zone slot of a Mini-game Controller, it gives that zone to the mini-game of the controller's page.
 * <ul>
 *     <li>Right-click a block: the first corner. Right-click another block: the second corner, and the box is drawn
 *     (a box already drawn is kept until then).</li>
 *     <li>Looking at a face of the box (from inside or outside, from afar), sneak + mouse wheel moves that face by
 *     one block, outward (wheel up) or inward; with Ctrl, by {@value #FAST_STEP}.</li>
 *     <li>Sneak + right-click in the air: the selection is cleared.</li>
 * </ul>
 * Everything is decided by the server: the corners are the blocks it was told were clicked, the face moved is the
 * one it finds the player looking at. A side can't be grown past {@link PageZone#MAX_SIDE} blocks; a box whose
 * corners are farther apart is kept (shown in red) but is no zone until it is shrunk.
 */
public class ZoneCartridgeItem extends Item {
    /** Blocks a face moves by with Ctrl held. */
    public static final int FAST_STEP = 4;

    public ZoneCartridgeItem(Settings settings) {
        super(settings);
    }

    public static @Nullable ZoneSelection selection(ItemStack stack) {
        return stack.get(ModComponents.ZONE_SELECTION);
    }

    /** The zone the cartridge carries: its box, when it has one that is not too big. */
    public static Optional<PageZone> zone(ItemStack stack) {
        ZoneSelection selection = stack.getItem() instanceof ZoneCartridgeItem ? selection(stack) : null;
        return selection == null ? Optional.empty() : selection.zone().filter(zone -> !zone.tooBig());
    }

    /**
     * A block was clicked with the cartridge: the first corner of a box, or the second one of the box being drawn
     * (in the same dimension).
     *
     * @return the new selection
     */
    public static ZoneSelection click(@Nullable ZoneSelection selection, RegistryKey<World> dimension, BlockPos pos) {
        if (selection != null && selection.dimension().equals(dimension) && selection.corner().isPresent()) {
            return new ZoneSelection(dimension, Optional.empty(), Optional.of(BlockBox.create(selection.corner().get(), pos)));
        }
        // A first corner: the box already drawn here stays until the second one
        Optional<BlockBox> kept = selection != null && selection.dimension().equals(dimension) ? selection.box() : Optional.empty();
        return new ZoneSelection(dimension, Optional.of(pos.toImmutable()), kept);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack stack = context.getStack();
        ZoneSelection selection = click(selection(stack), world.getRegistryKey(), context.getBlockPos());
        stack.set(ModComponents.ZONE_SELECTION, selection);
        PlayerEntity player = context.getPlayer();
        if (player != null) {
            player.sendMessage(selection.corner().isPresent()
                    ? Text.translatable("message.steveparty.zone_cartridge.corner", pos(context.getBlockPos()))
                    : sizeMessage(selection.box().orElseThrow()), true);
            world.playSound(null, context.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4F,
                    selection.corner().isPresent() ? 1.0F : 1.3F);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!user.isSneaking() || selection(stack) == null) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        stack.remove(ModComponents.ZONE_SELECTION);
        user.sendMessage(Text.translatable("message.steveparty.zone_cartridge.cleared"), true);
        world.playSound(null, user.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.4F, 0.7F);
        return ActionResult.SUCCESS;
    }

    /**
     * Sneak + mouse wheel (server side): the face of the box the player looks at moves, outward when {@code amount} is
     * positive. Only a cartridge in the main hand, a box in the player's dimension, and one or {@value #FAST_STEP}
     * blocks at a time.
     *
     * @return true if a face moved
     */
    public static boolean scroll(ServerPlayerEntity player, ItemStack stack, int amount) {
        ZoneSelection selection = stack.getItem() instanceof ZoneCartridgeItem ? selection(stack) : null;
        if (selection == null || selection.box().isEmpty() || amount == 0 || !player.isSneaking()
                || !selection.dimension().equals(player.getWorld().getRegistryKey())) return false;
        BlockBox box = selection.box().get();
        Direction face = ZoneFaces.lookedAt(player.getEyePos(), player.getRotationVec(1), PageZone.bounds(box));
        if (face == null) {
            player.sendMessage(Text.translatable("message.steveparty.zone_cartridge.no_face").formatted(Formatting.RED), true);
            return false;
        }
        int step = Integer.signum(amount) * (Math.abs(amount) >= FAST_STEP ? FAST_STEP : 1);
        World world = player.getWorld();
        BlockBox moved = ZoneFaces.moved(box, face, step, world.getBottomY(), world.getBottomY() + world.getHeight() - 1);
        if (moved == null) {
            player.sendMessage(Text.translatable(step > 0 ? "message.steveparty.zone_cartridge.max" : "message.steveparty.zone_cartridge.min",
                    PageZone.MAX_SIDE).formatted(Formatting.RED), true);
            return false;
        }
        stack.set(ModComponents.ZONE_SELECTION, new ZoneSelection(selection.dimension(), selection.corner(), Optional.of(moved)));
        player.sendMessage(sizeMessage(moved), true);
        world.playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundCategory.PLAYERS, 0.3F, step > 0 ? 1.2F : 0.9F);
        return true;
    }

    // ------------------------------------------------------------------ texts

    /** « 24 × 12 × 30 ». */
    public static MutableText size(BlockBox box) {
        return Text.translatable("tooltip.steveparty.zone_cartridge.size", box.getBlockCountX(), box.getBlockCountY(), box.getBlockCountZ());
    }

    private static Text sizeMessage(BlockBox box) {
        return PageZone.tooBig(box)
                ? Text.translatable("message.steveparty.zone_cartridge.too_big", size(box), PageZone.MAX_SIDE).formatted(Formatting.RED)
                : Text.translatable("message.steveparty.zone_cartridge.zone", size(box));
    }

    private static Text pos(BlockPos pos) {
        return Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ());
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);
        ZoneSelection selection = selection(stack);
        if (selection != null && selection.box().isPresent()) {
            BlockBox box = selection.box().get();
            boolean tooBig = PageZone.tooBig(box);
            tooltip.add(Text.translatable(tooBig ? "tooltip.steveparty.zone_cartridge.too_big" : "tooltip.steveparty.zone_cartridge.zone", size(box), PageZone.MAX_SIDE)
                    .formatted(tooBig ? Formatting.RED : Formatting.GOLD));
            tooltip.add(Text.translatable("tooltip.steveparty.zone_cartridge.where",
                    pos(new BlockPos(box.getMinX(), box.getMinY(), box.getMinZ())), pos(new BlockPos(box.getMaxX(), box.getMaxY(), box.getMaxZ())),
                    selection.dimension().getValue().toString()).formatted(Formatting.GRAY));
        } else {
            tooltip.add(Text.translatable("tooltip.steveparty.zone_cartridge.none").formatted(Formatting.GRAY));
        }
        if (selection != null && selection.corner().isPresent()) {
            tooltip.add(Text.translatable("tooltip.steveparty.zone_cartridge.corner", pos(selection.corner().get())).formatted(Formatting.GRAY));
        }
        for (String line : List.of("draw", "faces", "clear", "controller")) {
            tooltip.add(Text.translatable("tooltip.steveparty.zone_cartridge." + line).formatted(Formatting.DARK_GRAY));
        }
    }
}
