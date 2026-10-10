package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlock;
import fr.lordfinn.steveparty.blocks.custom.SpawnMarkerBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.board.CartridgeLinks;
import fr.lordfinn.steveparty.client.screens.SpawnMarkerScreen;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeSpawnMarker;
import net.minecraft.entity.EntityType;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.items.custom.cartridges.CartridgeItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
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
 * The Spawn Markers seen by those who build the board: all but invisible otherwise (a little plate), they show where
 * their mob will be (its outline, its size, lifted as set) with two eyes on the side it will face (no chevrons: those
 * are the brush's links) and a label (« always
 * visible » when their mob lives there all the party long),
 * for whoever holds the Tile Linker Brush, the Wrench or a cartridge, or wears the Explorer's Helmet with its lamp lit.
 * The markers around are looked for every {@link #REFRESH} ticks, only then.
 */
final class SpawnMarkerView {
    private static final int REFRESH = 10;
    private static final double RADIUS = 32;
    /** Half the size of an eye of the mob's outline. */
    private static final double EYE = 0.05;
    private static final float LABEL_SCALE = 1f / 48f;
    private static List<Marker> markers = List.of();
    private static int age = REFRESH;

    /** A marker, the way its mob faces (a yaw), its mob's size (a generic one when no space links it) and how high above it the mob appears. */
    private record Marker(BlockPos pos, float yaw, boolean resident, double lift, float width, float height) {
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
        WorldRenderEvents.LAST.register(SpawnMarkerView::render);
    }

    /** Whether {@code player} sees the markers: a board tool or a cartridge in hand, or a lit Explorer's Helmet on. */
    static boolean sees(ClientPlayerEntity player) {
        return ExplorerHelmet.view(player).shown() || shows(player.getMainHandStack().getItem()) || shows(player.getOffHandStack().getItem());
    }

    private static boolean shows(Item item) {
        return item instanceof WrenchItem || item instanceof CartridgeItem;
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
                    if (!(blockEntity instanceof SpawnMarkerBlockEntity marker)) continue;
                    BlockState state = blockEntity.getCachedState();
                    if (!state.contains(SpawnMarkerBlock.ROTATION)) continue;
                    EntityType<?> mob = null;
                    if (marker.getOwner() != null && world.getBlockEntity(marker.getOwner()) instanceof BoardSpaceBlockEntity space)
                        mob = CartridgeSpawnMarker.mobOf(space.getActiveCartridgeItemStack());
                    float width = mob == null ? 0.6f : mob.getWidth(), height = mob == null ? 1.0f : mob.getHeight();
                    found.add(new Marker(blockEntity.getPos(), SpawnMarkerBlock.yaw(state), marker.isResident(), marker.getLift(),
                            width, height));
                }
            }
        }
        return found;
    }

    /**
     * Two little bright cubes on the outline, where it meets the way the mob will face, at 80% of its height: its eyes,
     * which way it looks (any of the 16, diagonals too), without the chevrons of the brush's links.
     */
    private static void eyes(MatrixStack matrices, VertexConsumerProvider consumers, Camera camera, Vec3d base, Marker marker,
                             double w, int color) {
        Vec3d way = Vec3d.fromPolar(0, marker.yaw());
        // Out to the side of the box along that way (a diagonal reaches its corner), a hair outside
        double reach = w / Math.max(Math.abs(way.x), Math.abs(way.z)) + EYE;
        Vec3d front = base.add(way.multiply(reach)).add(0, marker.height() * 0.8, 0);
        Vec3d side = new Vec3d(-way.z, 0, way.x).multiply(Math.min(0.15, w * 0.5));
        Vec3d size = new Vec3d(EYE, EYE, EYE);
        for (Vec3d eye : new Vec3d[]{front.add(side), front.subtract(side)})
            WorldDraw.box(matrices, consumers, camera, eye.subtract(size), eye.add(size), color, 0.85f);
    }

    private static void render(WorldRenderContext context) {
        if (markers.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        if (world == null || matrices == null) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Camera camera = context.camera();
        float now = world.getTime() + context.tickCounter().getTickDelta(true);
        float pulse = 0.22f + 0.1f * (float) Math.sin(now / 6.0);
        int color = CartridgeLinks.SPAWN_COLOR;
        for (Marker marker : markers) {
            Vec3d plate = Vec3d.ofBottomCenter(marker.pos());
            Vec3d base = plate.add(0, marker.lift(), 0);
            // Where its mob will be, its size, and the way it will face
            double w = marker.width() / 2;
            WorldDraw.box(matrices, consumers, camera, base.add(-w, 0.01, -w), base.add(w, marker.height(), w), color, pulse);
            eyes(matrices, consumers, camera, base, marker, w, color);
            // Lifted: a thin thread from the marker up (or down) to it
            if (marker.lift() != 0) {
                WorldDraw.box(matrices, consumers, camera, new Vec3d(plate.x - 0.02, Math.min(plate.y, base.y), plate.z - 0.02),
                        new Vec3d(plate.x + 0.02, Math.max(plate.y, base.y), plate.z + 0.02), color, 0.6f);
            }
        }
        consumers.draw();
        for (Marker marker : markers) {
            Text label = Text.translatable(marker.resident() ? "hud.steveparty.spawn_marker.resident" : "hud.steveparty.spawn_marker.label");
            WorldDraw.plateLabel(matrices, consumers, camera, Vec3d.ofBottomCenter(marker.pos()).add(0, marker.lift() + marker.height() + 0.4, 0), label,
                    marker.resident() ? WorldDraw.Plate.PURPLE : WorldDraw.Plate.TEAL, WorldDraw.PLATE_TEXT, LABEL_SCALE);
        }
        consumers.draw();
    }
}
