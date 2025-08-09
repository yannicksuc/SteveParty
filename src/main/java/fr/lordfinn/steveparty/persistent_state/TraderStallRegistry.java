package fr.lordfinn.steveparty.persistent_state;

import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.Entity;
import java.util.*;
import java.util.function.Function;

public class TraderStallRegistry {
    private static final Map<UUID, Set<BlockPos>> traderToStalls = new HashMap<>();
    private static final Map<BlockPos, Set<UUID>> stallToTraders = new HashMap<>();

    public static void linkTraderToStall(UUID traderId, BlockPos stallPos) {
        traderToStalls.computeIfAbsent(traderId, k -> new HashSet<>()).add(stallPos);
        stallToTraders.computeIfAbsent(stallPos, k -> new HashSet<>()).add(traderId);
    }

    public static void unlinkTraderFromStall(UUID traderId, BlockPos stallPos) {
        Set<BlockPos> stalls = traderToStalls.get(traderId);
        if (stalls != null) {
            stalls.remove(stallPos);
            if (stalls.isEmpty()) {
                traderToStalls.remove(traderId);
            }
        }

        Set<UUID> traders = stallToTraders.get(stallPos);
        if (traders != null) {
            traders.remove(traderId);
            if (traders.isEmpty()) {
                stallToTraders.remove(stallPos);
            }
        }
    }

    public static Set<BlockPos> getLinkedStalls(UUID traderId) {
        return traderToStalls.getOrDefault(traderId, Collections.emptySet());
    }

    public static Set<UUID> getLinkedTraders(BlockPos stallPos) {
        return stallToTraders.getOrDefault(stallPos, Collections.emptySet());
    }

    private static <K, V> void unlinkAll(K key, Map<K, Set<V>> primaryMap, Map<V, Set<K>> secondaryMap, Function<V, Set<K>> secondaryMapGetter) {
        Set<V> linkedValues = primaryMap.remove(key);
        if (linkedValues != null) {
            for (V value : linkedValues) {
                Set<K> linkedKeys = secondaryMapGetter.apply(value);
                if (linkedKeys != null) {
                    linkedKeys.remove(key);
                    if (linkedKeys.isEmpty()) {
                        secondaryMap.remove(value);
                    }
                }
            }
        }
    }

    public static void unlinkTraderFromAllStalls(UUID traderId) {
        unlinkAll(traderId, traderToStalls, stallToTraders, stallToTraders::get);
    }

    public static void unlinkStallFromAllTraders(BlockPos stallPos) {
        unlinkAll(stallPos, stallToTraders, traderToStalls, traderToStalls::get);
    }
}
