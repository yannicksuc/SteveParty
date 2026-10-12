package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlock;
import fr.lordfinn.steveparty.blocks.custom.StepControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import fr.lordfinn.steveparty.podium.Podiums;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Tile Linker Brush's link from a Step Controller to the party element it drives (see
 * {@link StepControllerBlockEntity}): painted from the controller to the element, or from a board space or a Party
 * Controller to the controller. One element at a time: painting another one replaces it, painting it again removes it.
 * Drawn yellow and dotted by the brush overlay.
 */
public final class StepControllerLinks {
    /** The colours of the dots, from the Step Controller to its element. */
    public static final int NEAR = 0xFFF2A6, FAR = 0xFFC21A;

    static final BrushLinks.Provider PROVIDER = (world, holder, held, out) -> {
        if (holder instanceof StepControllerBlockEntity stepController) out.add(new Element(stepController));
    };

    private StepControllerLinks() {
    }

    /** The colour (rgb) of the dot {@code t} of the way from the Step Controller (0) to its element (1). */
    public static int gradient(double t) {
        return Argb.lerp(NEAR, FAR, (float) Math.clamp(t, 0, 1)) & 0xFFFFFF;
    }

    /**
     * Whether a block in {@code state} with {@code blockEntity} is an element of a party a Step Controller can drive:
     * a board space or any block of a board holding a cartridge (its board's party), the Party Controller, a Mini-game
     * Controller, a podium, a goal pole base or a pipe of a mini-game page (the mini-game of its page).
     */
    public static boolean drivable(BlockState state, @Nullable BlockEntity blockEntity) {
        if (state.getBlock() instanceof StepControllerBlock) return false;
        return blockEntity instanceof CartridgeContainerBlockEntity || blockEntity instanceof PartyControllerEntity
                || blockEntity instanceof MiniGameControllerBlockEntity || Podiums.linkable(state) || MiniGamePipes.linkable(state);
    }

    /** The element at {@code pos} a Step Controller would be linked to (a part of a large tile: its tile), or null. */
    public static @Nullable BlockPos element(World world, BlockPos pos) {
        BlockPos resolved = BoardSpaces.resolve(world, pos);
        return drivable(world.getBlockState(resolved), world.getBlockEntity(resolved)) ? resolved.toImmutable() : null;
    }

    /**
     * Reaching the Step Controller {@code to} from the holder {@code from} (a board space, a Party Controller...): the
     * controller's link to {@code from}, painted the other way round; null if {@code to} is no Step Controller or
     * can't drive {@code from}.
     */
    static @Nullable BrushLinkable reverse(World world, BlockPos from, BlockPos to) {
        if (!(world.getBlockEntity(to) instanceof StepControllerBlockEntity stepController)) return null;
        Element element = new Element(stepController);
        return element.accepts(world, from) ? element : null;
    }

    /** A Step Controller's element. */
    public record Element(StepControllerBlockEntity stepController) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return stepController.getPos();
        }

        @Override
        public int color() {
            return FAR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            BlockPos element = element(world, target);
            return element != null && !element.equals(holder());
        }

        @Override
        public List<BlockPos> targets(World world) {
            GlobalPos link = stepController.getLink();
            return link != null && link.dimension().equals(world.getRegistryKey()) ? List.of(link.pos()) : List.of();
        }

        @Override
        public boolean linked(World world, BlockPos target) {
            return targets(world).contains(BoardSpaces.resolve(world, target));
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            BlockPos element = element(world, target);
            if (element == null) return false;
            set(player, GlobalPos.create(world.getRegistryKey(), element));
            WrenchActions.say(player, Text.translatable("message.steveparty.step_controller.linked",
                    BoardText.pos(holder()), world.getBlockState(element).getBlock().getName()));
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.45f, 1.5f);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            set(player, null);
            TileLinkerBrush.erased(player, world, holder(), BoardSpaces.resolve(world, target));
            return true;
        }

        private void set(ServerPlayerEntity player, @Nullable GlobalPos link) {
            LinkHistory.record(player, new LinkHistory.StepLinkChange(holder(), stepController.getLink(), link));
            stepController.setLink(link);
        }
    }
}
