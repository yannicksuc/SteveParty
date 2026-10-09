package fr.lordfinn.steveparty.blocks.custom.PartyController;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static fr.lordfinn.steveparty.blocks.custom.PartyController.PartyControllerEntity.START_TILES_SEARCH_RADIUS;
import static fr.lordfinn.steveparty.components.ModComponents.TB_START_BOUND_ENTITY;

/** The board around a Party Controller: its board spaces and the tokens bound to its start tiles (no chunk is loaded). */
public final class PartyBoard {
    private PartyBoard() {}

    /** The tokens bound to the start tiles around {@code center}, in their order, each with its start tile (the first one bound to it). */
    static Map<UUID, BlockPos> startTokenTiles(ServerWorld serverWorld, BlockPos center) {
        Map<UUID, BlockPos> tokens = new LinkedHashMap<>();
        for (BlockPos tilePos : boardSpaces(serverWorld, center, BoardSpaceType.TILE_START)) {
            if (!(serverWorld.getBlockEntity(tilePos) instanceof BoardSpaceBlockEntity tile)) continue;
            String potentialUuid = tile.getActiveCartridgeItemStack().get(TB_START_BOUND_ENTITY);
            if (potentialUuid == null) continue;
            try {
                tokens.putIfAbsent(UUID.fromString(potentialUuid), tilePos);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return tokens;
    }

    /**
     * The board spaces of the type {@code type} within {@link PartyControllerEntity#START_TILES_SEARCH_RADIUS} blocks
     * of {@code center}, in the loaded chunks (none is loaded for this), sorted by z, then y, then x.
     */
    static List<BlockPos> boardSpaces(ServerWorld world, BlockPos center, BoardSpaceType type) {
        List<BlockPos> spaces = new ArrayList<>();
        int minX = center.getX() - START_TILES_SEARCH_RADIUS, maxX = center.getX() + START_TILES_SEARCH_RADIUS;
        int minY = center.getY() - START_TILES_SEARCH_RADIUS, maxY = center.getY() + START_TILES_SEARCH_RADIUS;
        int minZ = center.getZ() - START_TILES_SEARCH_RADIUS, maxZ = center.getZ() + START_TILES_SEARCH_RADIUS;

        for (int chunkX = ChunkSectionPos.getSectionCoord(minX); chunkX <= ChunkSectionPos.getSectionCoord(maxX); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(minZ); chunkZ <= ChunkSectionPos.getSectionCoord(maxZ); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!(blockEntity instanceof BoardSpaceBlockEntity)) continue;
                    BlockPos pos = blockEntity.getPos();
                    if (pos.getX() < minX || pos.getX() > maxX || pos.getY() < minY || pos.getY() > maxY
                            || pos.getZ() < minZ || pos.getZ() > maxZ) continue;
                    BlockState state = chunk.getBlockState(pos);
                    if (state.getBlock() instanceof ABoardSpaceBlock && state.get(ABoardSpaceBlock.TILE_TYPE) == type) {
                        spaces.add(pos.toImmutable());
                    }
                }
            }
        }
        // Same order as the former full scan (x first, then y, then z)
        spaces.sort(Comparator.comparingInt(BlockPos::getZ).thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX));
        return spaces;
    }
}
