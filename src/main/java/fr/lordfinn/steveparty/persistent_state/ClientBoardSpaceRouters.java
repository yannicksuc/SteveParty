package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;

public class ClientBoardSpaceRouters {
    private static final Map<BlockPos, BlockPos> ROUTER_MAP = new HashMap<>();

    public static void update(Map<BlockPos, BlockPos> map) {
        ROUTER_MAP.clear();
        ROUTER_MAP.putAll(map);
    }

    public static BlockPos getRouter(BlockPos boardSpace) {
        return ROUTER_MAP.get(boardSpace);
    }
}
