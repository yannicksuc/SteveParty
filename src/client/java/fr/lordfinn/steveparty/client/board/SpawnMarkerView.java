package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.client.screens.SpawnMarkerScreen;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.items.custom.TileLinkerBrushItem;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import fr.lordfinn.steveparty.client.utils.WorldView;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.Item;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * The Spawn Bubbles seen by those who build the board: a label over each bubble (« always visible » when its mob lives
 * there all the party long), for whoever holds the Tile Linker Brush, the Wrench or a cartridge, or wears the
 * Explorer's Helmet with its lamp lit. Where the mob will be and which way it faces, the bubble shows it itself (its
 * mob or arrow inside, see SpawnBubbleRenderer). The bubbles around are looked for every {@link #REFRESH} ticks, only
 * then.
 */
public final class SpawnMarkerView {
    private static final int REFRESH = 10;
    private static final double RADIUS = 32;
    /** Over the bubble's top (blocks). */
    private static final double LABEL_OVER = 5 / 16.0 + 0.3;
    private static final float LABEL_SCALE = 1f / 48f;
    private static List<Marker> markers = List.of();
    private static int age = REFRESH;

    /** A bubble, and how high above its plate it floats. */
    private record Marker(BlockPos pos, boolean resident, double lift) {
    }

    private SpawnMarkerView() {
    }

    static void initialize() {
        SpawnMarkerBlock.openMenu = pos -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null && client.world.getBlockEntity(pos) instanceof SpawnMarkerBlockEntity marker)
                client.setScreen(new SpawnMarkerScreen(pos, marker));
        };
        ClientTickEvents.END_CLIENT_TICK.register(SpawnMarkerView::tick);
        WorldRenderEvents.LAST.register(context -> WorldView.draw(context, () -> render(context)));
    }

    /** Whether {@code player} sees the labels: a board tool or a cartridge in hand, or a lit Explorer's Helmet on. */
    public static boolean sees(ClientPlayerEntity player) {
        return ExplorerHelmet.view(player).shown() || shows(player.getMainHandStack().getItem()) || shows(player.getOffHandStack().getItem());
    }

    private static boolean shows(Item item) {
        return item instanceof TileLinkerBrushItem || item instanceof WrenchItem || item instanceof CartridgeItem;
    }

    private static void tick(MinecraftClient client) {
        if (client.world == null || client.player == null || !sees(client.player)) {
            markers = List.of();
            age = REFRESH;
            return;
        }
        if (++age < REFRESH) return;
        age = 0;
        markers = find(client.world, client.player.getPos());
    }

    private static List<Marker> find(ClientWorld world, Vec3d center) {
        List<Marker> found = new ArrayList<>();
        int minX = MathHelper.floor(center.x - RADIUS), maxX = MathHelper.floor(center.x + RADIUS);
        int minZ = MathHelper.floor(center.z - RADIUS), maxZ = MathHelper.floor(center.z + RADIUS);
        for (int chunkX = ChunkSectionPos.getSectionCoord(minX); chunkX <= ChunkSectionPos.getSectionCoord(maxX); chunkX++) {
            for (int chunkZ = ChunkSectionPos.getSectionCoord(minZ); chunkZ <= ChunkSectionPos.getSectionCoord(maxZ); chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof SpawnMarkerBlockEntity marker)
                        found.add(new Marker(blockEntity.getPos(), marker.isResident(), marker.getLift()));
                }
            }
        }
        return found;
    }

    private static void render(WorldRenderContext context) {
        if (markers.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        if (client.world == null || matrices == null) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        for (Marker marker : markers) {
            Text label = Text.translatable(marker.resident() ? "hud.steveparty.spawn_marker.resident" : "hud.steveparty.spawn_marker.label");
            Vec3d at = SpawnMarkerBlock.bubbleCenter(marker.pos(), marker.lift()).add(0, LABEL_OVER, 0);
            WorldDraw.plateLabel(matrices, consumers, camera, at, label,
                    marker.resident() ? WorldDraw.Plate.PURPLE : WorldDraw.Plate.TEAL, WorldDraw.PLATE_TEXT, LABEL_SCALE);
        }
        consumers.draw();
    }
}
