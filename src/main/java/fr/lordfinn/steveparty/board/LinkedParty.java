package fr.lordfinn.steveparty.board;

import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity;
import fr.lordfinn.steveparty.blocks.custom.PodiumBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.CartridgeContainerBlockEntity;
import fr.lordfinn.steveparty.minigame.MiniGamePages;
import fr.lordfinn.steveparty.minigame.MiniGamePagesState;
import fr.lordfinn.steveparty.minigame.MiniGamePipes;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What a party element a block is linked to with the Tile Linker Brush belongs to (the Party Rewinder, the Party
 * Antenna, see {@link PartyLinks#drivable}): a Party Controller is its own party; any block of a board holding
 * a cartridge (a space, a Checkpoint, a router...) belongs to the party of its board, a Piggy Bank to the party of
 * its own link; a Mini-game
 * Controller, a podium or a pipe to the mini-game of its pages (the party playing it, while one does).
 *
 * @param party the party of the element, null for none (a mini-game's element: see {@link #playingParty})
 * @param pages the mini-game pages of the element, empty for a board's
 */
public record LinkedParty(@Nullable PartyControllerEntity party, List<UUID> pages) {
    public static final LinkedParty NONE = new LinkedParty(null, List.of());

    /** Its party, or the party playing the mini-game of its pages right now; null for none. */
    public @Nullable PartyControllerEntity playingParty() {
        if (party != null || pages.isEmpty()) return party;
        return PartyControllerEntity.getPartyPlayingPage(pages).orElse(null);
    }

    /**
     * What the element at {@code at} belongs to, seen from {@code here} (its server).
     *
     * @param load whether the element's chunk may be loaded to find it out, or not (none then)
     */
    public static LinkedParty of(ServerWorld here, GlobalPos at, boolean load) {
        return of(here, at, load, 0);
    }

    /** How many {@link PartyLinked} blocks holding a cartridge are followed one to the next at most (a loop ends). */
    private static final int MAX_HOPS = 4;

    private static LinkedParty of(ServerWorld here, GlobalPos at, boolean load, int hops) {
        ServerWorld world = here.getServer().getWorld(at.dimension());
        BlockPos pos = at.pos();
        if (world == null || (!load && !world.isChunkLoaded(pos))) return NONE;
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof PartyControllerEntity party) return new LinkedParty(party, List.of());
        // A block linked to its party itself (a Piggy Bank): its party is the one of its own link, none unlinked
        if (blockEntity instanceof PartyLinked linked && blockEntity instanceof CartridgeContainerBlockEntity) {
            GlobalPos next = linked.partyLink().get();
            return next == null || hops >= MAX_HOPS ? NONE : of(world, next, load, hops + 1);
        }
        if (blockEntity instanceof CartridgeContainerBlockEntity) {
            return new LinkedParty(PartyControllerEntity.getControllerOfBoardSpace(world, pos, PartyControllerEntity.BOARD_NEARBY_RADIUS).orElse(null), List.of());
        }
        if (blockEntity instanceof MiniGameControllerBlockEntity controller) {
            UUID page = controller.getPageId();
            return page == null ? NONE : new LinkedParty(null, List.of(page));
        }
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof PodiumBlock) return new LinkedParty(null, MiniGamePages.pageIdsAt(world, PodiumBlock.bottomOf(world, pos)));
        if (MiniGamePipes.linkable(state)) {
            List<UUID> pages = new ArrayList<>();
            for (MiniGamePagesState.Linked linked : MiniGamePages.linksAt(world.getServer(), at)) pages.add(linked.page().id());
            return new LinkedParty(null, pages);
        }
        return new LinkedParty(null, MiniGamePages.pageIdsAt(world, pos));
    }
}
