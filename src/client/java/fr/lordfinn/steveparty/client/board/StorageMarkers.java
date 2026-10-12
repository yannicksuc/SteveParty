package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.BrushLinks;
import fr.lordfinn.steveparty.board.TileLinkerBrush;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeContainers;
import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small chest floating above each storage around (a block a cartridge or the Party Controller can take as a container:
 * {@link CartridgeContainers#accepts}) while the Tile Linker Brush or a cartridge taking containers is in hand: dim
 * when nothing links it, on a plate of the storage links' green ({@code CartridgeLinks.CONTAINER_COLOR}) with its number in the list of the cartridge linking it
 * when linked (as the numbers of a held cartridge's chests). No word: the icon says it. Scanned every
 * {@link #REFRESH} ticks in the loaded chunks within {@link BoardView#RADIUS}; a double chest has one mark.
 */
public final class StorageMarkers {
    private static final int REFRESH = 10;
    /** The dim mark of a storage linked to nothing: light grey, half see-through. */
    private static final int UNLINKED = 0x90D0D0D0;
    private static final float SCALE = 1f / 40f;
    /** Above the block's centre: where the numbers of a cartridge's chests stand (see BrushOverlay). */
    private static final double HEIGHT = 0.95;

    /** A storage: where its mark stands, the blocks it covers (two for a double chest), its number (0: not linked). */
    private record Marker(Vec3d at, List<BlockPos> blocks, int number) {
    }

    private static List<Marker> markers = List.of();
    private static int age = REFRESH;

    private StorageMarkers() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(StorageMarkers::tick);
    }

    /** Whether the player holds what shows the marks: the brush in the main hand, a cartridge taking containers in either. */
    public static boolean shown(@Nullable ClientPlayerEntity player) {
        return player != null && (TileLinkerBrush.isBrush(player.getMainHandStack())
                || CartridgeContainers.linksContainers(player.getMainHandStack())
                || CartridgeContainers.linksContainers(player.getOffHandStack()));
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || !shown(client.player)) {
            markers = List.of();
            age = REFRESH;
            return;
        }
        if (++age < REFRESH) return;
        age = 0;
        markers = scan(client.world, client.player.getEyePos(), BoardView.RADIUS);
    }

    private static List<Marker> scan(ClientWorld world, Vec3d center, double radius) {
        // The holders a little farther than the marks: a chest at the edge is often linked from beyond it
        Map<BlockPos, Integer> numbers = BrushLinks.linkedStorages(world, center, radius + 16);
        List<Marker> found = new ArrayList<>();
        double radius2 = radius * radius;
        for (int chunkX = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.x - radius)); chunkX <= ChunkSectionPos.getSectionCoord(MathHelper.floor(center.x + radius)); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(MathHelper.floor(center.z - radius)); chunkZ <= ChunkSectionPos.getSectionCoord(MathHelper.floor(center.z + radius)); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos pos = blockEntity.getPos();
                    if (Vec3d.ofCenter(pos).squaredDistanceTo(center) > radius2) continue;
                    if (!CartridgeContainers.accepts(blockEntity.getCachedState(), blockEntity)) continue;
                    BlockPos other = CartridgeContainers.otherHalf(world, pos);
                    // A double chest: one mark between its halves, made by the half that comes first
                    if (other != null && other.compareTo(pos) < 0) continue;
                    Vec3d at = Vec3d.ofCenter(pos).add(0, HEIGHT, 0);
                    int number = numbers.getOrDefault(pos, 0);
                    if (other == null) {
                        found.add(new Marker(at, List.of(pos), number));
                    } else {
                        if (number == 0) number = numbers.getOrDefault(other, 0);
                        found.add(new Marker(at.add(Vec3d.ofCenter(other).subtract(Vec3d.ofCenter(pos)).multiply(0.5)), List.of(pos, other), number));
                    }
                }
            }
        }
        return found;
    }

    /** Draws the marks, but on the storages in {@code skip} (numbered already by what is drawn there). */
    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, Camera camera, Set<BlockPos> skip) {
        if (markers.isEmpty()) return;
        consumers.draw();
        for (Marker marker : markers) {
            if (marker.blocks().stream().anyMatch(skip::contains)) continue;
            if (marker.number() == 0) {
                WorldDraw.floatingText(matrices, consumers, camera, marker.at(), Tooltips.Icons.of(Tooltips.Icons.STORAGE), UNLINKED, SCALE);
            } else {
                // The plate in the storage links' green (CartridgeLinks.CONTAINER_COLOR)
                Text label = Text.empty().append(Tooltips.Icons.of(Tooltips.Icons.STORAGE)).append(" " + marker.number());
                WorldDraw.plateLabel(matrices, consumers, camera, marker.at(), label, WorldDraw.Plate.GREEN, WorldDraw.PLATE_TEXT, SCALE);
            }
        }
    }
}
