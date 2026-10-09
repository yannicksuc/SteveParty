package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.MiniGamePartyStep;
import fr.lordfinn.steveparty.components.ModComponents;
import fr.lordfinn.steveparty.minigame.MiniGameTest;
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
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * The Mini-game Remote: the buttons of a Mini-game Controller, from afar (playtest of 2026-10-06, #72).
 * <ul>
 *     <li>A sneaking click on a Mini-game Controller links the remote to it (on the same one again: unlinks it).</li>
 *     <li>A click anywhere: out of a party, « Play » or « Stop » of its controller, as its screen's button (the
 *     players near the page's pipes play; the one holding the remote watches if he is not near one of them); while
 *     a party plays its mini-game, the holder's « Ready » vote of the practice round.</li>
 * </ul>
 * The controller's chunk is loaded to press it, a second at most between two presses.
 */
public class MiniGameRemoteItem extends Item {
    private static final String KEY = "message.steveparty.mini_game_remote.";
    /** Ticks between two presses. */
    private static final int COOLDOWN_TICKS = 20;
    /** The reasons the controller's screen gives (without arguments) for not playing, said by the remote too. */
    private static final Set<MiniGameTest.Status> SAID = Set.of(MiniGameTest.Status.NO_PIPE, MiniGameTest.Status.NOBODY,
            MiniGameTest.Status.NOT_ENOUGH, MiniGameTest.Status.PARTY_PLAYING, MiniGameTest.Status.ZONE_TOO_BIG, MiniGameTest.Status.ZONE_BUSY,
            MiniGameTest.Status.ZONE_NO_WORLD, MiniGameTest.Status.ZONE_TOO_FULL);

    public MiniGameRemoteItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        PlayerEntity player = context.getPlayer();
        if (player == null) return ActionResult.PASS;
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (player.isSneaking() && world.getBlockEntity(pos) instanceof MiniGameControllerBlockEntity) {
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
        player.sendMessage(Text.translatable(KEY + "linked", pos.getX(), pos.getY(), pos.getZ()).formatted(Formatting.GREEN), true);
        ModSounds.playSelect(player.getWorld(), pos);
    }

    /** The controller the remote is linked to, its chunk loaded; null if none, or it is no more. */
    public static @Nullable MiniGameControllerBlockEntity controllerOf(ServerPlayerEntity player, ItemStack stack) {
        GlobalPos link = stack.get(ModComponents.MINI_GAME_REMOTE_LINK);
        ServerWorld world = link == null ? null : player.server.getWorld(link.dimension());
        if (world == null) return null;
        world.getChunk(link.pos().getX() >> 4, link.pos().getZ() >> 4);
        return world.getBlockEntity(link.pos()) instanceof MiniGameControllerBlockEntity controller ? controller : null;
    }

    /**
     * A press of the remote by {@code player}: see the class. The player is told what it did, or why it did nothing.
     *
     * @return true if it did something
     */
    public static boolean press(ItemStack stack, ServerPlayerEntity player) {
        if (player.getItemCooldownManager().isCoolingDown(stack.getItem())) return false;
        if (!stack.contains(ModComponents.MINI_GAME_REMOTE_LINK)) {
            player.sendMessage(Text.translatable(KEY + "not_linked").formatted(Formatting.RED), true);
            return false;
        }
        player.getItemCooldownManager().set(stack.getItem(), COOLDOWN_TICKS);
        MiniGameControllerBlockEntity controller = controllerOf(player, stack);
        if (controller == null) {
            player.sendMessage(Text.translatable(KEY + "gone").formatted(Formatting.RED), true);
            return false;
        }
        UUID page = controller.getPageId();
        if (page == null) {
            player.sendMessage(Text.translatable(KEY + "no_page").formatted(Formatting.RED), true);
            return false;
        }
        // A party plays it: the vote for the real round
        if (PartyControllerEntity.getPartyPlayingPage(List.of(page)).isPresent()) {
            if (!MiniGamePartyStep.toggleReady(player)) {
                player.sendMessage(Text.translatable(KEY + "no_vote").formatted(Formatting.RED), true);
                return false;
            }
            player.sendMessage(Text.translatable(KEY + "voted"), true);
            ModSounds.playSelect(player.getWorld(), player.getBlockPos());
            return true;
        }
        boolean running = MiniGameTest.of(page) != null;
        if (controller.playOrStop(player)) {
            player.sendMessage(Text.translatable(KEY + (running ? "stopped" : "started")).formatted(running ? Formatting.GOLD : Formatting.GREEN), true);
            ModSounds.playSelect(player.getWorld(), player.getBlockPos());
            return true;
        }
        MiniGameTest.Status status = MiniGameTest.check(player.server, page).status();
        Text why = SAID.contains(status) ? Text.translatable("gui.steveparty.mini_game_controller.status." + status.name().toLowerCase(Locale.ROOT))
                : Text.translatable(KEY + "cannot");
        player.sendMessage(why.copy().formatted(Formatting.RED), true);
        ModSounds.playCancel(player.getWorld(), player.getBlockPos());
        return false;
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
                .use(Tooltips.Keys.use(), "tooltip.steveparty.mini_game_remote.use")
                .note("tooltip.steveparty.mini_game_remote.ready"));
    }
}
