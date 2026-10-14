package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.payloads.custom.PodiumPreviewPayload;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The podium modes being shown (client side), by the bottom block of their columns: the column lights up, a Steve
 * statue stands on the one a player would go to, for a few seconds after the Wrench changed the mode (see
 * {@link PodiumPreviewPayload}). Drawn by {@link PodiumRenderer}.
 */
public final class PodiumPreviews {
    /** A column shown: until when (world time), since when, with a statue, in which mode. */
    public record Shown(long since, long until, boolean statue, int mode) {
    }

    private static final Map<BlockPos, Shown> SHOWN = new HashMap<>();

    private PodiumPreviews() {
    }

    /** A new mode shown: what was shown before goes away. */
    public static void show(PodiumPreviewPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        long now = client.world.getTime();
        SHOWN.clear();
        for (BlockPos column : payload.columns()) {
            SHOWN.put(column.toImmutable(), new Shown(now, now + payload.ticks(), payload.statue().map(column::equals).orElse(false), payload.mode()));
        }
    }

    /** What the column with that bottom block shows now, null for nothing. */
    public static @Nullable Shown at(BlockPos bottom, long now) {
        Shown shown = SHOWN.get(bottom);
        if (shown == null) return null;
        if (now >= shown.until() || now < shown.since()) {
            SHOWN.remove(bottom);
            return null;
        }
        return shown;
    }

    public static void clear() {
        SHOWN.clear();
    }
}
