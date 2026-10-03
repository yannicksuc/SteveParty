package fr.lordfinn.steveparty.client.minigame;

import fr.lordfinn.steveparty.blocks.custom.MiniGameControllerBlockEntity;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import fr.lordfinn.steveparty.components.ZoneSelection;
import fr.lordfinn.steveparty.items.custom.MiniGamePageItem;
import fr.lordfinn.steveparty.items.custom.WrenchItem;
import fr.lordfinn.steveparty.items.custom.ZoneCartridgeItem;
import fr.lordfinn.steveparty.minigame.PageZone;
import fr.lordfinn.steveparty.minigame.ZoneFaces;
import fr.lordfinn.steveparty.payloads.custom.ZoneCartridgeScrollPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * The Zone Cartridge on the client: what its holder sees and its mouse wheel.
 * <ul>
 *     <li>In the world (drawn like the Wrench's overlay, stuck to the blocks): the box of the cartridge held, as an
 *     outline with see-through faces (red when too big), the face looked at brighter, the first corner of a box
 *     being drawn with the box a click would make; and, cartridge, Mini-game Page or Wrench in hand, the zone of the
 *     Mini-game Controllers around (green).</li>
 *     <li>Above the hotbar (the tools' HUD): the size of the box, and the gestures available now.</li>
 *     <li>Sneak + wheel, a face looked at: the face moves (the server finds the face again and decides).</li>
 * </ul>
 */
public final class ZoneCartridgeClient {
    private static final int HELD = 0x3FD0FF, TOO_BIG = 0xFF4040, HOME = 0x4CFF4C, CORNER = 0xFFD83D;
    /** Blocks around the player within which the zones of the controllers are shown. */
    private static final double SHOW_RADIUS = 64;
    private static final float FACE_ALPHA = 0.08f, LOOKED_ALPHA = 0.24f;

    /** The face of the held cartridge's box the player looks at, null for none. */
    private static @Nullable Direction lookedFace;

    private ZoneCartridgeClient() {
    }

    public static void initialize() {
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ZoneCartridgeClient::render);
        HudRenderCallback.EVENT.register(ZoneCartridgeClient::renderHud);
    }

    private static boolean isCartridge(ItemStack stack) {
        return stack.getItem() instanceof ZoneCartridgeItem;
    }

    /** The selection of the cartridge in the main hand, in the dimension the player is in; null otherwise. */
    private static @Nullable ZoneSelection mainSelection(MinecraftClient client) {
        if (client.player == null || client.world == null || !isCartridge(client.player.getMainHandStack())) return null;
        ZoneSelection selection = ZoneCartridgeItem.selection(client.player.getMainHandStack());
        return selection != null && selection.dimension().equals(client.world.getRegistryKey()) ? selection : null;
    }

    /**
     * Mouse wheel hook: sneaking with a cartridge that has a box in the main hand, the wheel moves the face looked at
     * (up: outward, down: inward; Ctrl: four blocks) instead of changing the hotbar slot.
     *
     * @return true if the scroll was used
     */
    public static boolean onScroll(double vertical) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null || client.player == null || !client.player.isSneaking()) return false;
        ZoneSelection selection = mainSelection(client);
        if (selection == null || selection.box().isEmpty()) return false;
        if (vertical == 0) return true;
        int amount = (vertical > 0 ? 1 : -1) * (Screen.hasControlDown() ? ZoneCartridgeItem.FAST_STEP : 1);
        ClientPlayNetworking.send(new ZoneCartridgeScrollPayload(amount));
        return true;
    }

    // ------------------------------------------------------------------ in the world

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        lookedFace = null;
        if (player == null || world == null || matrices == null) return;
        ItemStack main = player.getMainHandStack(), off = player.getOffHandStack();
        ItemStack cartridge = isCartridge(main) ? main : isCartridge(off) ? off : null;
        boolean showsHomes = cartridge != null || main.getItem() instanceof MiniGamePageItem || off.getItem() instanceof MiniGamePageItem
                || main.getItem() instanceof WrenchItem || off.getItem() instanceof WrenchItem;
        if (!showsHomes) return;
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        Vec3d camera = context.camera().getPos();
        float tickDelta = context.tickCounter().getTickDelta(true);

        // The zones of the controllers around
        for (MiniGameControllerBlockEntity controller : MiniGameControllerBlockEntity.clientLoaded(world)) {
            ZoneSelection home = controller.getSelection();
            if (home == null || home.box().isEmpty() || !home.dimension().equals(world.getRegistryKey())
                    || !controller.getPos().isWithinDistance(player.getPos(), SHOW_RADIUS)) continue;
            BlockBox box = home.box().get();
            drawBox(matrices, consumers, camera, PageZone.bounds(box), PageZone.tooBig(box) ? TOO_BIG : HOME, FACE_ALPHA, null);
        }

        // The cartridge held: its box, the face looked at, the corner of the box being drawn
        ZoneSelection selection = cartridge == null ? null : ZoneCartridgeItem.selection(cartridge);
        if (selection != null && selection.dimension().equals(world.getRegistryKey())) {
            if (selection.box().isPresent()) {
                Box bounds = PageZone.bounds(selection.box().get());
                if (cartridge == main) lookedFace = ZoneFaces.lookedAt(player.getCameraPosVec(tickDelta), player.getRotationVec(tickDelta), bounds);
                drawBox(matrices, consumers, camera, bounds, PageZone.tooBig(selection.box().get()) ? TOO_BIG : HELD,
                        selection.corner().isPresent() ? FACE_ALPHA / 2 : FACE_ALPHA, selection.corner().isPresent() ? null : lookedFace);
            }
            if (selection.corner().isPresent()) {
                BlockPos corner = selection.corner().get();
                drawBox(matrices, consumers, camera, new Box(corner), CORNER, 0.3f, null);
                // The box a click on the block aimed at would make
                if (cartridge == main && client.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                    BlockBox preview = BlockBox.create(corner, hit.getBlockPos());
                    drawBox(matrices, consumers, camera, PageZone.bounds(preview), PageZone.tooBig(preview) ? TOO_BIG : CORNER, FACE_ALPHA, null);
                }
            }
        }
        consumers.draw();
    }

    /**
     * A box as an outline with see-through faces, {@code bright} brighter. Each face is drawn a hair toward the
     * camera, so that it does not flicker on the blocks it lies on, and the farthest first.
     */
    static void drawBox(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d camera, Box box, int rgb, float alpha,
                                @Nullable Direction bright) {
        float red = ((rgb >> 16) & 0xFF) / 255f, green = ((rgb >> 8) & 0xFF) / 255f, blue = (rgb & 0xFF) / 255f;
        Vec3d center = box.getCenter();
        double far = Math.sqrt(camera.squaredDistanceTo(center)) + box.getLengthX() + box.getLengthY() + box.getLengthZ();
        double lift = 0.004 * Math.max(1, far / 16);
        Direction[] faces = Direction.values();
        Arrays.sort(faces, Comparator.comparingDouble((Direction face) -> -camera.squaredDistanceTo(faceCenter(box, face))));
        VertexConsumer quads = consumers.getBuffer(RenderLayer.getDebugQuads());
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        for (Direction face : faces) {
            double plane = plane(box, face);
            double eye = face.getAxis().choose(camera.x, camera.y, camera.z);
            boolean outside = face.getDirection() == Direction.AxisDirection.POSITIVE ? eye > plane : eye < plane;
            double at = plane + (outside ? 1 : -1) * face.getDirection().offset() * lift;
            float a = face == bright ? LOOKED_ALPHA : alpha;
            double x0 = box.minX - camera.x, y0 = box.minY - camera.y, z0 = box.minZ - camera.z;
            double x1 = box.maxX - camera.x, y1 = box.maxY - camera.y, z1 = box.maxZ - camera.z;
            switch (face.getAxis()) {
                case X -> {
                    double x = at - camera.x;
                    quad(quads, matrix, x, y0, z0, x, y1, z0, x, y1, z1, x, y0, z1, red, green, blue, a);
                }
                case Y -> {
                    double y = at - camera.y;
                    quad(quads, matrix, x0, y, z0, x1, y, z0, x1, y, z1, x0, y, z1, red, green, blue, a);
                }
                case Z -> {
                    double z = at - camera.z;
                    quad(quads, matrix, x0, y0, z, x1, y0, z, x1, y1, z, x0, y1, z, red, green, blue, a);
                }
            }
        }
        VertexRendering.drawBox(matrices, consumers.getBuffer(RenderLayer.getLines()), box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z,
                box.maxX - camera.x, box.maxY - camera.y, box.maxZ - camera.z, red, green, blue, 1f);
    }

    private static double plane(Box box, Direction face) {
        return face.getDirection() == Direction.AxisDirection.POSITIVE ? box.getMax(face.getAxis()) : box.getMin(face.getAxis());
    }

    private static Vec3d faceCenter(Box box, Direction face) {
        Vec3d center = box.getCenter();
        double plane = plane(box, face);
        return switch (face.getAxis()) {
            case X -> new Vec3d(plane, center.y, center.z);
            case Y -> new Vec3d(center.x, plane, center.z);
            case Z -> new Vec3d(center.x, center.y, plane);
        };
    }

    private static void quad(VertexConsumer consumer, Matrix4f matrix, double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz, float red, float green, float blue, float alpha) {
        consumer.vertex(matrix, (float) ax, (float) ay, (float) az).color(red, green, blue, alpha);
        consumer.vertex(matrix, (float) bx, (float) by, (float) bz).color(red, green, blue, alpha);
        consumer.vertex(matrix, (float) cx, (float) cy, (float) cz).color(red, green, blue, alpha);
        consumer.vertex(matrix, (float) dx, (float) dy, (float) dz).color(red, green, blue, alpha);
    }

    // ------------------------------------------------------------------ above the hotbar

    /** The cartridge's HUD, in the tools' look ({@link ToolHud}): the size of its box (red when too big), and what can be done now. */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.currentScreen != null || client.player == null || client.world == null
                || !isCartridge(client.player.getMainHandStack())) return;
        ZoneSelection selection = mainSelection(client);
        List<ToolHud.Element> plates = new ArrayList<>();
        BlockBox box = selection == null ? null : selection.box().orElse(null);
        if (box == null) {
            plates.add(plate(context, Text.translatable("hud.steveparty.zone_cartridge.none"), ToolHud.Plate.TEAL));
        } else if (PageZone.tooBig(box)) {
            plates.add(plate(context, Text.translatable("hud.steveparty.zone_cartridge.too_big", ZoneCartridgeItem.size(box), PageZone.MAX_SIDE), ToolHud.Plate.RED));
        } else {
            plates.add(plate(context, ZoneCartridgeItem.size(box), ToolHud.Plate.TEAL));
        }
        boolean drawing = selection != null && selection.corner().isPresent();
        if (drawing) {
            BlockPos corner = selection.corner().get();
            plates.add(plate(context, Text.translatable("hud.steveparty.zone_cartridge.corner", corner.getX(), corner.getY(), corner.getZ()), ToolHud.Plate.GOLD));
        } else if (box != null && lookedFace != null) {
            plates.add(plate(context, Text.translatable("hud.steveparty.zone_cartridge.face",
                    Text.translatable("hud.steveparty.zone_cartridge.face." + lookedFace.asString())), ToolHud.Plate.GOLD));
        }
        int top = ToolHud.rows(context, List.of(plates), 4);
        Text hint = Text.translatable(drawing ? "hud.steveparty.zone_cartridge.hint.corner"
                : box == null ? "hud.steveparty.zone_cartridge.hint.none" : "hud.steveparty.zone_cartridge.hint.box");
        ToolHud.hint(context, hint, context.getScaledWindowWidth() / 2, top);
    }

    private static ToolHud.Element plate(DrawContext context, Text text, ToolHud.Plate plate) {
        return ToolHud.element(ToolHud.textPlateWidth(text), (x, y) -> ToolHud.textPlate(context, x, y, text, plate));
    }
}
