package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyEnding;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The Tile Linker Brush's links from a Party Controller to where its party ends (see PartyEnding):
 * <ul>
 *     <li>its Finish spaces ({@link Arrivals}): painted from the controller to a Finish space, or the other way round,
 *     like its start tiles; the pawns gather there once the party is over;</li>
 *     <li>its podiums ({@link Podiums}): painted from the controller to a podium (any block of a column: its group
 *     comes along); the pawns of the best places stand on them, the ranking over them.</li>
 * </ul>
 * Painted again, removed. Server side texts, like {@link ControllerStartLinks}.
 */
public final class ControllerEndLinks {
    /** The colour of the links to its Finish spaces (their raspberry) and to its podiums (gold). */
    public static final int ARRIVAL_COLOR = 0xD94F70, PODIUM_COLOR = 0xE8B83A;

    static final BrushLinks.Provider PROVIDER = (world, holder, held, out) -> {
        if (holder instanceof PartyControllerEntity controller && held.slot() == BrushLinks.BANK) {
            out.add(new Arrivals(controller));
            out.add(new Podiums(controller));
        }
    };

    private ControllerEndLinks() {
    }

    /** Reaching the Party Controller {@code to} from the Finish space {@code from}: its link to it, painted the other way round. */
    static @Nullable BrushLinkable reverse(World world, BlockPos from, BlockPos to) {
        if (!(world.getBlockEntity(to) instanceof PartyControllerEntity controller)) return null;
        Arrivals arrivals = new Arrivals(controller);
        BlockPos space = BoardSpaces.resolve(world, from);
        return arrivals.linked(world, space) || arrivals.accepts(world, space) ? arrivals : null;
    }

    /** Its links shown as particle trails (when a stroke starts on the controller). */
    static void show(ServerWorld world, PartyControllerEntity controller) {
        for (BlockPos space : controller.getArrivalLinks()) BoardLinks.trail(world, controller.getPos(), space, ARRIVAL_COLOR);
        for (BlockPos podium : controller.getPodiumLinks()) BoardLinks.trail(world, controller.getPos(), podium, PODIUM_COLOR);
    }

    /** A Party Controller's Finish spaces. */
    public record Arrivals(PartyControllerEntity controller) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return controller.getPos();
        }

        @Override
        public int color() {
            return ARRIVAL_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return PartyEnding.isFinish(world, BoardSpaces.resolve(world, target));
        }

        @Override
        public List<BlockPos> targets(World world) {
            return controller.getArrivalLinks();
        }

        @Override
        public boolean linked(World world, BlockPos target) {
            return controller.getArrivalLinks().contains(BoardSpaces.resolve(world, target));
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            BlockPos space = BoardSpaces.resolve(world, target).toImmutable();
            if (!PartyEnding.isFinish(world, space)) return false;
            List<BlockPos> links = new ArrayList<>(controller.getArrivalLinks());
            links.add(space);
            set(player, links);
            saidLinked(player, world, holder(), space, ARRIVAL_COLOR);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            List<BlockPos> links = new ArrayList<>(controller.getArrivalLinks());
            BlockPos space = BoardSpaces.resolve(world, target);
            if (!links.remove(space)) return false;
            set(player, links);
            saidUnlinked(player, world, holder(), space);
            return true;
        }

        private void set(ServerPlayerEntity player, List<BlockPos> links) {
            LinkHistory.record(player, new LinkHistory.ControllerLinksChange(holder(), false, List.copyOf(controller.getArrivalLinks()), List.copyOf(links)));
            controller.setArrivalLinks(links);
        }
    }

    /** A Party Controller's podiums: the bottom of the column painted. */
    public record Podiums(PartyControllerEntity controller) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return controller.getPos();
        }

        @Override
        public int color() {
            return PODIUM_COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return PodiumBlock.isPodium(world.getBlockState(target));
        }

        @Override
        public List<BlockPos> targets(World world) {
            return controller.getPodiumLinks();
        }

        @Override
        public boolean linked(World world, BlockPos target) {
            return PodiumBlock.isPodium(world.getBlockState(target)) && controller.getPodiumLinks().contains(PodiumBlock.bottomOf(world, target));
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            if (!accepts(world, target)) return false;
            BlockPos bottom = PodiumBlock.bottomOf(world, target);
            List<BlockPos> links = new ArrayList<>(controller.getPodiumLinks());
            links.add(bottom);
            set(player, links);
            saidLinked(player, world, holder(), bottom, PODIUM_COLOR);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            List<BlockPos> links = new ArrayList<>(controller.getPodiumLinks());
            BlockPos bottom = PodiumBlock.bottomOf(world, target);
            if (!links.remove(bottom)) return false;
            set(player, links);
            saidUnlinked(player, world, holder(), bottom);
            return true;
        }

        private void set(ServerPlayerEntity player, List<BlockPos> links) {
            LinkHistory.record(player, new LinkHistory.ControllerLinksChange(holder(), true, List.copyOf(controller.getPodiumLinks()), List.copyOf(links)));
            controller.setPodiumLinks(links);
        }
    }

    private static void saidLinked(ServerPlayerEntity player, ServerWorld world, BlockPos from, BlockPos to, int color) {
        WrenchActions.say(player, Text.translatable("message.steveparty.tile_linker_brush.linked", BoardText.pos(from), BoardText.pos(to)));
        world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.45f, 1.5f);
        BoardLinks.trail(world, from, to, color);
    }

    private static void saidUnlinked(ServerPlayerEntity player, ServerWorld world, BlockPos from, BlockPos to) {
        TileLinkerBrush.erased(player, world, from, to);
        BoardLinks.trail(world, from, to, BoardLinks.CUT_COLOR);
    }
}
