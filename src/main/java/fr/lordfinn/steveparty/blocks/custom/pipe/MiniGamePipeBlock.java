package fr.lordfinn.steveparty.blocks.custom.pipe;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.minigame.MiniGamePageNetworking;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePipeIndex;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ItemActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The mini-game pipes (Copper, Iron, Golden: they differ by how far they send, see {@link Reach}):
 * a pipe like any other (it joins the pipes of any colour) that can be programmed with a
 * mini-game page, shown in the notch on its side. Programmed, it is the way into that mini-game out of a party: see
 * {@code MiniGamePipes}.
 * <ul>
 *     <li>A click with a page prints it on the pipe: the page is not used up (a linked copy of it goes in, the one
 *     that was there is wiped off).</li>
 *     <li>A click with an empty hand on the notch, or sneaking anywhere on it, wipes the page off: nothing comes back.</li>
 *     <li>Broken, it keeps its page: the pipe item carries it, placed again it is still printed.</li>
 *     <li>Unlinked from the page's editor, it is wiped off too ({@link #wipe}).</li>
 * </ul>
 * Programming it takes the right to build.
 */
public class MiniGamePipeBlock extends PipeBlock {
    private static final Direction[] NOTCH_SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN};

    /** How far a mini-game pipe sends to its mini-game. */
    public enum Reach {
        /** Like any warp: at most {@link PipeNetworks#WARP_RADIUS} blocks, in a loaded chunk of its dimension. */
        NEAR,
        /** Anywhere in its dimension. */
        DIMENSION,
        /** Anywhere, the other dimensions too. */
        EVERYWHERE
    }

    public MiniGamePipeBlock(PipeKind kind, Settings settings) {
        super(kind, 0, settings);
    }

    @Override
    protected MapCodec<? extends Block> getCodec() {
        return createCodec(settings -> new MiniGamePipeBlock(kind(), settings));
    }

    public Reach reach() {
        return switch (kind()) {
            case GOLDEN -> Reach.EVERYWHERE;
            case IRON -> Reach.DIMENSION;
            default -> Reach.NEAR;
        };
    }

    /** How far the mini-game pipe {@code state} sends, {@link Reach#NEAR} if it is not one. */
    public static Reach reachOf(BlockState state) {
        return state.getBlock() instanceof MiniGamePipeBlock pipe ? pipe.reach() : Reach.NEAR;
    }

    /**
     * @return true if a mini-game pipe at {@code from} in {@code world} sends as far as the mouth at {@code to}: see {@link Reach}
     */
    public static boolean reaches(Reach reach, ServerWorld world, BlockPos from, GlobalPos to) {
        if (reach == Reach.EVERYWHERE) return true;
        if (!to.dimension().equals(world.getRegistryKey())) return false;
        if (reach == Reach.DIMENSION) return true;
        return from.getSquaredDistance(to.pos()) <= PipeNetworks.WARP_RADIUS * PipeNetworks.WARP_RADIUS
                && world.getChunkManager().isChunkLoaded(to.pos().getX() >> 4, to.pos().getZ() >> 4);
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MiniGamePipeBlockEntity(pos, state);
    }

    /** The side its notch is on: the first plain side (no pipe joined, no mouth, no cap), the sides before top and bottom; null if it has none. */
    public static @Nullable Direction notchSide(BlockState state) {
        PipeShape.Face[] faces = PipeShape.faces(PipeShape.mask(state), PipeShape.solid(state));
        for (Direction side : NOTCH_SIDES) {
            if (faces[side.ordinal()] == PipeShape.Face.CLOSED) return side;
        }
        return null;
    }

    /** The page of the mini-game pipe at {@code pos}, empty if it is not one or holds none. */
    public static ItemStack pageAt(World world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof MiniGamePipeBlockEntity pipe ? pipe.getPage() : ItemStack.EMPTY;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType options) {
        ItemStack page = MiniGamePipeBlockEntity.pageOf(stack);
        Tooltips tips = Tooltips.of(tooltip);
        if (!page.isEmpty()) tips.state("tooltip.steveparty.minigame_pipe.page", Tooltips.value(page.getName()));
        tips
                .state("tooltip.steveparty.minigame_pipe.reach",
                        Tooltips.value(Text.translatable("tooltip.steveparty.minigame_pipe.reach." + reach().name().toLowerCase(Locale.ROOT))))
                .summary("tooltip.steveparty.minigame_pipe.what")
                .more(more -> more
                        .use(Tooltips.Keys.use(), "tooltip.steveparty.minigame_pipe.program")
                        .use(Tooltips.Keys.sneakUse(), "tooltip.steveparty.minigame_pipe.take")
                        .note("tooltip.steveparty.minigame_pipe.kept")
                        .note("tooltip.steveparty.minigame_pipe.colours")
                        .note("tooltip.steveparty.minigame_pipe.own"));
    }

    @Override
    protected ItemActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!MiniGamePages.isPage(stack)) return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
        if (player instanceof ServerPlayerEntity serverPlayer) program(serverPlayer, hand, pos);
        return ItemActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        // An empty hand on its notch, or sneaking: the page wiped off
        if (!pageAt(world, pos).isEmpty() && player.getMainHandStack().isEmpty() && (player.isSneaking() || hit.getSide() == notchSide(state))) {
            if (player instanceof ServerPlayerEntity serverPlayer) takePage(serverPlayer, pos);
            return ActionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hit);
    }

    /**
     * Prints the page held in {@code hand} on the mini-game pipe at {@code pos}: a linked copy goes in, the page held is
     * not used up; the page that was there is wiped off.
     *
     * @return false if the player may not build, holds no page, or the pipe already has that page
     */
    public static boolean program(ServerPlayerEntity player, Hand hand, BlockPos pos) {
        ItemStack held = player.getStackInHand(hand);
        if (!(player.getWorld().getBlockEntity(pos) instanceof MiniGamePipeBlockEntity pipe) || !MiniGamePages.isPage(held)) return false;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.not_allowed").formatted(Formatting.RED), true);
            return false;
        }
        UUID id = MiniGamePageNetworking.ensureSinglePage(player, hand);
        ItemStack page = player.getStackInHand(hand).copyWithCount(1);
        if (id.equals(MiniGamePages.idOf(pipe.getPage()))) {
            player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.already", page.getName()), true);
            return false;
        }
        MiniGamePages.refresh(player.server, page);
        pipe.setPage(page);
        ModSounds.playSelect(player.getWorld(), pos);
        player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.programmed", page.getName()), true);
        return true;
    }

    /** Wipes the page off the mini-game pipe at {@code pos}: nothing comes back. @return false if there is none, or the player may not build */
    public static boolean takePage(ServerPlayerEntity player, BlockPos pos) {
        if (!(player.getWorld().getBlockEntity(pos) instanceof MiniGamePipeBlockEntity pipe) || pipe.getPage().isEmpty()) return false;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.not_allowed").formatted(Formatting.RED), true);
            return false;
        }
        pipe.setPage(ItemStack.EMPTY);
        ModSounds.playCancel(player.getWorld(), pos);
        player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.page_taken"), true);
        return true;
    }

    /**
     * The mini-game pipe at {@code pipe} unlinked from the page {@code page} (its editor): wiped off if it still has
     * that page, its chunk loaded or not (loaded for the change).
     *
     * @return true if it was wiped off
     */
    public static boolean wipe(MinecraftServer server, GlobalPos pipe, UUID page) {
        ServerWorld world = server.getWorld(pipe.dimension());
        if (world == null) {
            MiniGamePipeIndex.remove(server, pipe);
            return false;
        }
        if (world.getBlockEntity(pipe.pos()) instanceof MiniGamePipeBlockEntity entity && page.equals(MiniGamePages.idOf(entity.getPage()))) {
            entity.setPage(ItemStack.EMPTY);
            ModSounds.playCancel(world, pipe.pos());
            return true;
        }
        // Not that page (or no pipe any more): only the index forgets it
        MiniGamePipeIndex.Entry entry = MiniGamePipeIndex.at(server, pipe);
        if (entry != null && entry.page().equals(page)) MiniGamePipeIndex.remove(server, pipe);
        return false;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // No longer a mini-game pipe: out of the index of the programmed pipes
        if (!newState.isOf(this) && world instanceof ServerWorld server) {
            MiniGamePipeIndex.remove(server.getServer(), GlobalPos.create(server.getRegistryKey(), pos));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
