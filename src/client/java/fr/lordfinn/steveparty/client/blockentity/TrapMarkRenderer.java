package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.powerups.effects.TrapKind;
import fr.lordfinn.steveparty.utils.Argb;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

import java.util.EnumMap;
import java.util.Map;

/**
 * A Trap set on a board space ({@link BoardSpaceBlockEntity#getTrapMark}), drawn by the space's renderer: jaws hugging
 * the space's outline (a low frame in the setter's colour, iron teeth up along its inner edge, darker hinges at its
 * corners), sized and turned like the space (small, standard or large tile, turned 45 degrees, lowered or sloped: the
 * caller's matrices are the tile's), and over the space a floating badge picturing what the trap does, facing the
 * camera, Mario Party style. Client side only, nothing ticks.
 */
public final class TrapMarkRenderer {
    private static final Identifier WHITE = Identifier.ofVanilla("textures/misc/white.png");
    private static final Map<TrapKind, Identifier> ICONS = new EnumMap<>(TrapKind.class);

    static {
        for (TrapKind kind : TrapKind.values()) ICONS.put(kind, Steveparty.id("textures/gui/trap/" + kind.id() + ".png"));
    }

    /** The frame: its width out of the space, its height; the teeth: their size, height, spacing. */
    private static final float FRAME_W = 0.09f, FRAME_H = 0.08f, GAP = 0.015f;
    private static final float TOOTH = 0.05f, TOOTH_H = 0.11f, TOOTH_STEP = 0.2f, HINGE = 0.15f, HINGE_H = 0.12f;
    private static final int IRON_TOP = 0xD8DDE3, IRON_SIDE = 0x8C939C;
    /** The badge: its size (blocks), its height above where tokens stand, how far it bobs. */
    private static final float ICON_SIZE = 0.6f, ICON_Y = 1.85f, BOB = 0.06f;

    private TrapMarkRenderer() {
    }

    /** The texture of the badge of {@code kind} (also the setup screen's). */
    public static Identifier icon(TrapKind kind) {
        return ICONS.get(kind);
    }

    /**
     * The jaws around a space of half width {@code half} (blocks), centred on (0.5, 0.5) of the current matrices (the
     * tile's frame, its floor at y 0), turned {@code direction} eighths of a turn.
     */
    public static void jaws(BoardSpaceBlockEntity.TrapMark mark, float half, int direction, MatrixStack matrices,
                            VertexConsumerProvider consumers, int light) {
        VertexConsumer c = consumers.getBuffer(RenderLayer.getEntityCutout(WHITE));
        matrices.push();
        matrices.translate(0.5, 0, 0.5);
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(direction * 45f));
        MatrixStack.Entry e = matrices.peek();
        int top = lighten(mark.color(), 0.15f);
        int side = Argb.scale(mark.color(), 0.6f) & 0xFFFFFF;
        int hingeTop = Argb.scale(mark.color(), 0.45f) & 0xFFFFFF, hingeSide = Argb.scale(mark.color(), 0.3f) & 0xFFFFFF;
        float in = half + GAP, out = in + FRAME_W;
        // The four bars of the frame
        box(e, c, -out, 0, -out, out, FRAME_H, -in, top, side, light);
        box(e, c, -out, 0, in, out, FRAME_H, out, top, side, light);
        box(e, c, -out, 0, -in, -in, FRAME_H, in, top, side, light);
        box(e, c, in, 0, -in, out, FRAME_H, in, top, side, light);
        // Teeth up along the inner edge of each bar, evenly spaced
        int teeth = Math.max(2, Math.round(2 * in / TOOTH_STEP));
        float step = 2 * in / teeth;
        for (int i = 0; i < teeth; i++) {
            float t = -in + step * (i + 0.5f) - TOOTH / 2;
            box(e, c, t, FRAME_H, -in - TOOTH, t + TOOTH, FRAME_H + TOOTH_H, -in, IRON_TOP, IRON_SIDE, light);
            box(e, c, t, FRAME_H, in, t + TOOTH, FRAME_H + TOOTH_H, in + TOOTH, IRON_TOP, IRON_SIDE, light);
            box(e, c, -in - TOOTH, FRAME_H, t, -in, FRAME_H + TOOTH_H, t + TOOTH, IRON_TOP, IRON_SIDE, light);
            box(e, c, in, FRAME_H, t, in + TOOTH, FRAME_H + TOOTH_H, t + TOOTH, IRON_TOP, IRON_SIDE, light);
        }
        // The hinges at the corners
        float h = HINGE / 2, m = in + FRAME_W / 2;
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int sz = -1; sz <= 1; sz += 2) {
                box(e, c, sx * m - h, 0, sz * m - h, sx * m + h, HINGE_H, sz * m + h, hingeTop, hingeSide, light);
            }
        }
        matrices.pop();
    }

    /**
     * The badge of what the trap does, floating over ({@code x}, {@code y}, {@code z}) (where tokens stand, in the
     * block's frame), facing the camera, bobbing gently; lit whatever the light.
     */
    public static void icon(BoardSpaceBlockEntity.TrapMark mark, double x, double y, double z, float time,
                            MatrixStack matrices, VertexConsumerProvider consumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        VertexConsumer c = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(icon(mark.kind())));
        matrices.push();
        matrices.translate(x, y + ICON_Y + BOB * Math.sin(time * 0.08), z);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        float s = ICON_SIZE / 2;
        MatrixStack.Entry e = matrices.peek();
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        // Facing the camera (the dispatcher's rotation looks along -z): u from left to right, v from top to bottom
        vertex(e, c, -s, -s, 0, 0, 1, 0xFFFFFF, light, 0, 0, 1);
        vertex(e, c, s, -s, 0, 1, 1, 0xFFFFFF, light, 0, 0, 1);
        vertex(e, c, s, s, 0, 1, 0, 0xFFFFFF, light, 0, 0, 1);
        vertex(e, c, -s, s, 0, 0, 0, 0xFFFFFF, light, 0, 0, 1);
        matrices.pop();
    }

    private static int lighten(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * k) << 16) | ((int) (g + (255 - g) * k) << 8) | (int) (b + (255 - b) * k);
    }

    /** An axis-aligned box: its top in {@code top}, its sides and bottom in {@code side}. */
    private static void box(MatrixStack.Entry e, VertexConsumer c, float x0, float y0, float z0,
                            float x1, float y1, float z1, int top, int side, int light) {
        float[][] faces = {
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0}};
        for (float[] f : faces) {
            int rgb = f[13] > 0 ? top : side;
            for (int v = 0; v < 4; v++)
                vertex(e, c, f[v * 3], f[v * 3 + 1], f[v * 3 + 2], 0.5f, 0.5f, rgb, light, f[12], f[13], f[14]);
        }
    }

    private static void vertex(MatrixStack.Entry e, VertexConsumer c, float x, float y, float z, float u, float v,
                               int rgb, int light, float nx, float ny, float nz) {
        c.vertex(e.getPositionMatrix(), x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, 255)
                .texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }
}
