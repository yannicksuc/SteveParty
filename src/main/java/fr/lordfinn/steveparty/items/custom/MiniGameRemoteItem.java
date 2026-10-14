package fr.lordfinn.steveparty.items.custom;

import net.minecraft.block.entity.BlockEntity;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.screen_handlers.RemoteAccess;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Remote: the screen of a Party Controller or of a Mini-game Controller, from afar (playtest of 2026-10-13).
 * <ul>
 *     <li>A sneaking click on a Party Controller or a Mini-game Controller links the remote to it (on the same one
 *     again: unlinks it).</li>
 *     <li>A click anywhere opens its controller's screen, wherever the player is (its chunk is loaded and kept while
 *     the screen is open, see {@link RemoteAccess}): pause, stop, « Play », « Ready »... as at the controller.</li>
 * </ul>
 */
public class MiniGameRemoteItem extends Item {
    private static final String KEY = "message.steveparty.mini_game_remote.";
    /** Ticks between two presses. */
    private static final int COOLDOWN_TICKS = 10;

    public MiniGameRemoteItem(Settings settings) {
        super(settings);
    }

    /** Whether the remote links to the block holding {@code blockEntity} (sneak + click): a Party Controller or a Mini-game Controller. */
    public static boolean linkable(@Nullable BlockEntity blockEntity) {
        return blockEntity instanceof MiniGameControllerBlockEntity || blockEntity instanceof PartyControllerEntity;
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (player.isSneaking() && linkable(world.getBlockEntity(pos))) {
            if (!world.isClient) link(context.getStack(), player, GlobalPos.create(world.getRegistryKey(), pos.toImmutable()));
            return ActionResult.success(world.isClient);
        }
        if (world.isClient) return ActionResult.SUCCESS;
        if (player instanceof ServerPlayerEntity serverPlayer) press(context.getStack(), serverPlayer);
        return ActionResult.SUCCESS;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player) press(stack, player);
        return TypedActionResult.success(stack, world.isClient);
    }

    /** Links the remote to the controller at {@code controller}, or unlinks it if it was linked to it. */
    static void link(ItemStack stack, PlayerEntity player, GlobalPos controller) {
        if (controller.equals(stack.get(ModComponents.MINI_GAME_REMOTE_LINK))) {
            stack.remove(ModComponents.MINI_GAME_REMOTE_LINK);
            player.sendMessage(Text.translatable(KEY + "unlinked"), true);
            ModSounds.playCancel(player.getWorld(), controller.pos());
            return;
        }
        stack.set(ModComponents.MINI_GAME_REMOTE_LINK, controller);
        BlockPos pos = controller.pos();
        Text name = player.getWorld().getBlockState(pos).getBlock().getName();
        player.sendMessage(Text.translatable(KEY + "linked", name, pos.getX(), pos.getY(), pos.getZ()).formatted(Formatting.GREEN), true);
        ModSounds.playSelect(player.getWorld(), pos);
    }

    /** The controller the remote is linked to, its chunk loaded; null if none, or it is no more. */
    public static @Nullable BlockEntity controllerOf(ServerPlayerEntity player, ItemStack stack) {
        GlobalPos link = stack.get(ModComponents.MINI_GAME_REMOTE_LINK);
        ServerWorld world = link == null ? null : player.server.getWorld(link.dimension());
        if (world == null) return null;
        world.getChunk(link.pos().getX() >> 4, link.pos().getZ() >> 4);
        BlockEntity controller = world.getBlockEntity(link.pos());
        return linkable(controller) ? controller : null;
    }

    /**
     * A press of the remote by {@code player}: its controller's screen opens. The player is told why it did not.
     *
     * @return true if the screen opened
     */
    public static boolean press(ItemStack stack, ServerPlayerEntity player) {
        if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) return false;
        if (!stack.contains(ModComponents.MINI_GAME_REMOTE_LINK)) {
            player.sendMessage(Text.translatable(KEY + "not_linked").formatted(Formatting.RED), true);
            return false;
        }
        player.getItemCooldownManager().set(stack.getItem(), COOLDOWN_TICKS);
        BlockEntity controller = controllerOf(player, stack);
        if (controller == null) {
            player.sendMessage(Text.translatable(KEY + "gone").formatted(Formatting.RED), true);
            ModSounds.playCancel(player.getWorld(), player.getBlockPos());
            return false;
        }
        if (!RemoteAccess.open(player, controller)) return false;
        ModSounds.playSelect(player.getWorld(), player.getBlockPos());
        return true;
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return stack.contains(ModComponents.MINI_GAME_REMOTE_LINK) || super.hasGlint(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        GlobalPos link = stack.get(ModComponents.MINI_GAME_REMOTE_LINK);
        Tooltips tips = Tooltips.of(tooltip);
        if (link == null) {
            tips.warn(Text.translatable("tooltip.steveparty.mini_game_remote.not_linked"));
        } else {
            BlockPos pos = link.pos();
            tips.state("tooltip.steveparty.mini_game_remote.linked", Tooltips.good(Text.translatable("tooltip.steveparty.position",
                    pos.getX(), pos.getY(), pos.getZ())));
        }
        tips.summary("tooltip.steveparty.mini_game_remote.play");
        tips.more(more -> more
                .use(Tooltips.Keys.sneakUse(), "tooltip.steveparty.mini_game_remote.link")
                .use(Tooltips.Keys.use(), "tooltip.steveparty.mini_game_remote.use"));
    }
}
