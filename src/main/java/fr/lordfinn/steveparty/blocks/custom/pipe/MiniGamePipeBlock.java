package fr.lordfinn.steveparty.blocks.custom.pipe;

import com.mojang.serialization.MapCodec;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.sounds.ModSounds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The mini-game pipes (Copper, Iron, Golden: they differ by how far they send, see {@link Reach}):
 * a pipe like any other (it joins the pipes of any colour) that can be programmed with a
 * mini-game page, shown in the notch on its side. Programmed, it is the way into that mini-game out of a party: see
 * {@code MiniGamePipes}.
 * <ul>
 *     <li>A click with a page puts it in (the one already there comes back).</li>
 *     <li>A click on the notch, or a sneaking click anywhere on it, takes the page back.</li>
 *     <li>Broken, it drops its page.</li>
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
    public static boolean reaches(Reach reach, net.minecraft.server.world.ServerWorld world, BlockPos from, net.minecraft.util.math.GlobalPos to) {
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
    public void appendTooltip(ItemStack stack, net.minecraft.item.Item.TooltipContext context, List<Text> tooltip, net.minecraft.item.tooltip.TooltipType options) {
        for (String line : new String[]{"what", "program", "colours", "direct", "own", "take"}) {
            tooltip.add(Text.translatable("tooltip.steveparty.minigame_pipe." + line)
                    .formatted(line.equals("what") ? Formatting.GOLD : Formatting.GRAY));
        }
        tooltip.add(Text.translatable("tooltip.steveparty.minigame_pipe.reach." + reach().name().toLowerCase(java.util.Locale.ROOT))
                .formatted(Formatting.YELLOW));
    }

    @Override
    protected ActionResult onUseWithItem(ItemStack stack, BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!MiniGamePages.isPage(stack)) return super.onUseWithItem(stack, state, world, pos, player, hand, hit);
        if (player instanceof ServerPlayerEntity serverPlayer) program(serverPlayer, hand, pos);
        return ActionResult.SUCCESS;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!pageAt(world, pos).isEmpty() && (player.isSneaking() || hit.getSide() == notchSide(state))) {
            if (player instanceof ServerPlayerEntity serverPlayer) takePage(serverPlayer, pos);
            return ActionResult.SUCCESS;
        }
        return super.onUse(state, world, pos, player, hit);
    }

    /**
     * Programs the mini-game pipe at {@code pos} with the page held in {@code hand} (one page of the stack); the page
     * that was there goes back to the player.
     *
     * @return false if the player may not build, or holds no page
     */
    public static boolean program(ServerPlayerEntity player, Hand hand, BlockPos pos) {
        ItemStack held = player.getStackInHand(hand);
        if (!(player.getWorld().getBlockEntity(pos) instanceof MiniGamePipeBlockEntity pipe) || !MiniGamePages.isPage(held)) return false;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.not_allowed").formatted(Formatting.RED), true);
            return false;
        }
        ItemStack before = pipe.getPage();
        ItemStack page = held.split(1);
        MiniGamePages.refresh(player.server, page);
        pipe.setPage(page);
        if (!before.isEmpty()) player.getInventory().offerOrDrop(before);
        ModSounds.playSelect(player.getWorld(), pos);
        player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.programmed", page.getName()), true);
        return true;
    }

    /** Takes the page out of the mini-game pipe at {@code pos}, into the player's hands. @return false if there is none, or the player may not build */
    public static boolean takePage(ServerPlayerEntity player, BlockPos pos) {
        if (!(player.getWorld().getBlockEntity(pos) instanceof MiniGamePipeBlockEntity pipe) || pipe.getPage().isEmpty()) return false;
        if (!MiniGamePages.canEdit(player)) {
            player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.not_allowed").formatted(Formatting.RED), true);
            return false;
        }
        ItemStack page = pipe.getPage();
        pipe.setPage(ItemStack.EMPTY);
        if (player.getMainHandStack().isEmpty()) player.setStackInHand(Hand.MAIN_HAND, page);
        else player.getInventory().offerOrDrop(page);
        ModSounds.playCancel(player.getWorld(), pos);
        player.sendMessage(Text.translatable("message.steveparty.minigame_pipe.page_taken"), true);
        return true;
    }

    @Override
    protected void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        // No longer a mini-game pipe: out of the index of the programmed pipes
        if (!newState.isOf(this) && world instanceof net.minecraft.server.world.ServerWorld server) {
            fr.lordfinn.steveparty.minigame.MiniGamePipeIndex.set(server.getServer(), net.minecraft.util.math.GlobalPos.create(server.getRegistryKey(), pos), null);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
