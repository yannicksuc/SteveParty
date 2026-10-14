package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyBoard;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaces;
import fr.lordfinn.steveparty.payloads.custom.BrushTutorialPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * What the Tile Linker Brush's tutorial counts (its toasts are the client's): each link the player paints or erases
 * is told to their client ({@link BrushTutorialPayload}), which steps it fits.
 */
public final class BrushTutorialSteps {
    /** The steps of the tutorial, in their recommended order. */
    public enum Step {
        /** A start tile linked to the Party Controller. */
        CONTROLLER,
        /** A start tile linked to the board (a start is no part of the loop: its link goes out to it). */
        STARTS,
        /** Board spaces linked together (counted). */
        TILES,
        /** A link removed (painted over again). */
        ERASE
    }

    private BrushTutorialSteps() {
    }

    /** {@code player} linked {@code target} from the holder {@code from} with a link of {@code kind}. */
    static void linked(ServerPlayerEntity player, World world, BlockPos from, BrushLinkable kind, BlockPos target) {
        if (kind instanceof ControllerStartLinks.Starts) tell(player, Step.CONTROLLER);
        else if (kind instanceof CartridgeLinks.BoardPaths) pathLinked(player, world, from);
    }

    /** {@code player} linked a path from the board space at {@code from} (painted, planned by a blob, placed). */
    static void pathLinked(ServerPlayerEntity player, World world, BlockPos from) {
        tell(player, PartyBoard.isStart(world, BoardSpaces.resolve(world, from)) ? Step.STARTS : Step.TILES);
    }

    /** {@code player} erased a link. */
    static void erased(ServerPlayerEntity player) {
        tell(player, Step.ERASE);
    }

    private static void tell(ServerPlayerEntity player, Step step) {
        if (ServerPlayNetworking.canSend(player, BrushTutorialPayload.ID)) {
            ServerPlayNetworking.send(player, new BrushTutorialPayload(step.ordinal()));
        }
    }
}
