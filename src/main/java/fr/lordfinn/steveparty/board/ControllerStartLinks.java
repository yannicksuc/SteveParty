package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBoard;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.utils.ServerMemory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

/**
 * The Tile Linker Brush's links from a Party Controller to its start tiles: painted from the controller to a start
 * tile, or from a start tile to the controller; painted again, removed. They are its only start tiles (see
 * {@link PartyBoard}): none linked, no start (nothing is ever found around it).
 * <p>
 * Server side only, its texts built here: a client without it still paints them (the brush is server side), and sees
 * them as particle trails (when linked, and when a stroke starts on the controller).
 */
public final class ControllerStartLinks {
    /** The colour of its links (rgb). */
    public static final int COLOR = 0xFFD24C;

    static final BrushLinks.Provider PROVIDER = (world, holder, held, out) -> {
        if (holder instanceof PartyControllerEntity controller && held.slot() == BrushLinks.BANK) out.add(new Starts(controller));
    };

    private ControllerStartLinks() {
    }

    /** Reaching the Party Controller {@code to} from the start tile {@code from}: its link to it, painted the other way round. */
    static @Nullable BrushLinkable reverse(World world, BlockPos from, BlockPos to) {
        if (!(world.getBlockEntity(to) instanceof PartyControllerEntity controller)) return null;
        Starts starts = new Starts(controller);
        BlockPos start = BoardSpaces.resolve(world, from);
        return starts.linked(world, start) || starts.accepts(world, start) ? starts : null;
    }

    /** The links of the controller at {@code pos} shown as particle trails (a client without them drawn in its overlay). */
    static void show(ServerWorld world, BlockPos pos) {
        if (!(world.getBlockEntity(pos) instanceof PartyControllerEntity controller)) return;
        for (BlockPos start : controller.getStartLinks()) BoardLinks.trail(world, pos, start, COLOR);
    }

    /** What the Wrench's board check adds about the start tiles of {@code controller}. */
    public static Text status(ServerPlayerEntity player, PartyControllerEntity controller) {
        int linked = controller.getStartLinks().size();
        if (linked == 0) return howToLink(player);
        return Text.literal(french(player) ? "Départs reliés : " + linked
                : "Start tiles linked: " + linked).formatted(Formatting.GRAY);
    }

    /** The line of {@code /steveparty links} about the start tiles, for {@code player} (null: English). */
    public static Text linksLine(@Nullable ServerPlayerEntity player) {
        return Text.literal(player != null && french(player)
                ? "Du Party Controller à une case départ : ses départs (seuls ceux-là comptent)"
                : "From the Party Controller to a start tile: its starts (only those count)");
    }

    /** Whether the player reads French (their client's language). */
    public static boolean french(ServerPlayerEntity player) {
        String language = player.getClientOptions().language();
        return language != null && language.startsWith("fr");
    }

    /**
     * How to link the start tiles of a controller that has none linked, for {@code player} (in their language; the
     * clients of before have no key for it).
     */
    public static Text howToLink(ServerPlayerEntity player) {
        return Text.literal(french(player)
                ? "Aucun départ relié : Pinceau lieur, du Party Controller vers chaque case départ (une par joueur)"
                : "No start tile linked: Tile Linker Brush, from the Party Controller to each start tile (one per player)")
                .formatted(Formatting.GRAY);
    }

    /** A Party Controller's start tiles. */
    public record Starts(PartyControllerEntity controller) implements BrushLinkable {
        @Override
        public BlockPos holder() {
            return controller.getPos();
        }

        @Override
        public int color() {
            return COLOR;
        }

        @Override
        public boolean accepts(World world, BlockPos target) {
            return PartyBoard.isStart(world, BoardSpaces.resolve(world, target));
        }

        @Override
        public List<BlockPos> targets(World world) {
            return controller.getStartLinks();
        }

        @Override
        public boolean linked(World world, BlockPos target) {
            return controller.getStartLinks().contains(BoardSpaces.resolve(world, target));
        }

        @Override
        public boolean link(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            BlockPos start = BoardSpaces.resolve(world, target).toImmutable();
            if (!PartyBoard.isStart(world, start)) return false;
            List<BlockPos> links = new ArrayList<>(controller.getStartLinks());
            links.add(start);
            set(player, links);
            WrenchActions.say(player, Text.translatable("message.steveparty.tile_linker_brush.linked", BoardText.pos(holder()), BoardText.pos(start)), WrenchActions.linkWord(true));
            world.playSound(null, player.getBlockPos(), SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.PLAYERS, 0.45f, 1.5f);
            BoardLinks.trail(world, holder(), start, COLOR);
            return true;
        }

        @Override
        public boolean unlink(ServerPlayerEntity player, ServerWorld world, BlockPos target) {
            BlockPos start = BoardSpaces.resolve(world, target);
            List<BlockPos> links = new ArrayList<>(controller.getStartLinks());
            if (!links.remove(start)) return false;
            set(player, links);
            TileLinkerBrush.erased(player, world, holder(), start);
            BoardLinks.trail(world, holder(), start, BoardLinks.CUT_COLOR);
            return true;
        }

        private void set(ServerPlayerEntity player, List<BlockPos> links) {
            LinkHistory.record(player, new LinkHistory.StartLinksChange(holder(), List.copyOf(controller.getStartLinks()), List.copyOf(links)));
            controller.setStartLinks(links);
        }
    }
}
