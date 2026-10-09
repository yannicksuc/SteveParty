package fr.lordfinn.steveparty.client.blockentity;

import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.boardspaces.BoardSpaceType;
import fr.lordfinn.steveparty.items.custom.cartridges.KeyGateCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.PotCartridgeItem;
import fr.lordfinn.steveparty.items.custom.cartridges.ThresholdCartridgeItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.joml.Matrix4f;

/**
 * What the board rule cartridges show over their space, drawn by the tile's renderer: the condition of a Threshold
 * obstacle (« 7 or more », « DOUBLE »), what a Common pot holds; always facing the camera, readable from all around.
 */
public final class BoardRuleOverlays {
    /** Height of the label's middle above the tile's block. */
    private static final double LABEL_Y = 1.7;
    private static final float LABEL_SCALE = 0.03f;

    private BoardRuleOverlays() {
    }

    public static void render(BoardSpaceBlockEntity entity, BoardSpaceType tileType, ItemStack stack, double centreX, double centreZ,
                              float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        if (stack.isEmpty()) return;
        if (tileType == BoardSpaceType.TILE_THRESHOLD && stack.getItem() instanceof ThresholdCartridgeItem) {
            label(ThresholdCartridgeItem.label(stack), centreX, centreZ, 0xFFFFFF, matrices, consumers);
        } else if (tileType == BoardSpaceType.TILE_POT && stack.getItem() instanceof PotCartridgeItem) {
            // What the pot holds, in coin gold
            label(Text.translatable("gui.steveparty.pot.label", PotCartridgeItem.coins(stack)), centreX, centreZ, 0xFFD54A, matrices, consumers);
        } else if (tileType == BoardSpaceType.TILE_KEY_GATE && stack.getItem() instanceof KeyGateCartridgeItem) {
            gates(entity, stack, centreX, centreZ, tickDelta, matrices, consumers);
        }
    }

    /** The renderer of the check points: what their role shows over them (they have no face to draw). */
    public static void renderCheckPoint(BoardSpaceBlockEntity entity, float tickDelta, MatrixStack matrices,
                                        VertexConsumerProvider consumers, int light, int overlay) {
        net.minecraft.block.BlockState state = entity.getCachedState();
        if (!state.contains(fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE)) return;
        BoardSpaceType type = state.get(fr.lordfinn.steveparty.blocks.custom.boardspaces.ABoardSpaceBlock.TILE_TYPE);
        if (type == BoardSpaceType.DEFAULT || type == BoardSpaceType.TILE_START) return;
        render(entity, type, fr.lordfinn.steveparty.client.utils.BoardSpaceClientUtils.getDisplayedCartridge(entity),
                0.5, 0.5, tickDelta, matrices, consumers, light);
    }

    /** A line of text over the space, facing the camera, lit whatever the light (a sign of the board). */
    static void label(Text text, double centreX, double centreZ, int color, MatrixStack matrices, VertexConsumerProvider consumers) {
        MinecraftClient client = MinecraftClient.getInstance();
        TextRenderer font = client.textRenderer;
        matrices.push();
        matrices.translate(centreX, LABEL_Y, centreZ);
        matrices.multiply(client.getEntityRenderDispatcher().getRotation());
        matrices.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        float x = -font.getWidth(text) / 2f;
        // Shadowed, no backdrop (a backdrop fights with the glyphs for the same depth)
        font.draw(text, x, -4, color, true, matrix, consumers, TextRenderer.TextLayerType.NORMAL, 0,
                LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    // ---------------------------------------------------------------- the Key gate's holograms

    private static final net.minecraft.util.Identifier WHITE = net.minecraft.util.Identifier.ofVanilla("textures/misc/white.png");
    /** The gate: how far from the space's middle, half its width, its height, its posts' thickness (blocks). */
    private static final float GATE_OUT = 0.9f, GATE_HALF = 0.5f, GATE_H = 1.3f, POST = 0.08f;
    private static final int VEIL_BANDS = 10;

    /**
     * A closed gate on each locked exit of a Key gate space (the exits are the cartridge's destinations): a frame and a
     * veil rippling in the cartridge's colour. A gate kept open shows nothing.
     */
    private static void gates(BoardSpaceBlockEntity entity, ItemStack stack, double centreX, double centreZ, float tickDelta,
                              MatrixStack matrices, VertexConsumerProvider consumers) {
        if (fr.lordfinn.steveparty.items.custom.cartridges.BoardRuleCartridgeItem.state(stack, KeyGateCartridgeItem.OPENED,
                Integer.MIN_VALUE) != Integer.MIN_VALUE) return;
        fr.lordfinn.steveparty.components.DestinationsComponent destinations = stack.get(fr.lordfinn.steveparty.components.ModComponents.DESTINATIONS_COMPONENT);
        if (destinations == null || entity.getWorld() == null) return;
        int color = stack.getOrDefault(fr.lordfinn.steveparty.components.ModComponents.COLOR, KeyGateCartridgeItem.COLOR);
        float time = (entity.getWorld().getTime() % 2400) + tickDelta;
        net.minecraft.client.render.VertexConsumer consumer = consumers.getBuffer(net.minecraft.client.render.RenderLayer.getEntityTranslucent(WHITE));
        java.util.Set<net.minecraft.util.math.Direction> drawn = java.util.EnumSet.noneOf(net.minecraft.util.math.Direction.class);
        for (net.minecraft.util.math.BlockPos exit : destinations.destinations()) {
            net.minecraft.util.math.Direction side = KeyGateCartridgeItem.sideOf(entity.getPos(), exit);
            if (!KeyGateCartridgeItem.isLocked(stack, side) || !drawn.add(side)) continue;
            matrices.push();
            matrices.translate(centreX + side.getOffsetX() * GATE_OUT, 0.12, centreZ + side.getOffsetZ() * GATE_OUT);
            // The gate spans across the way out: along x for a north / south exit, along z for east / west
            if (side.getAxis() == net.minecraft.util.math.Direction.Axis.X)
                matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(90));
            gate(matrices.peek(), consumer, color, time);
            matrices.pop();
        }
    }

    /** One gate in its own frame: x across, y up, z through. */
    private static void gate(MatrixStack.Entry entry, net.minecraft.client.render.VertexConsumer consumer, int color, float time) {
        int frame = darken(color, 0.55f);
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        box(entry, consumer, -GATE_HALF - POST, 0, -POST, -GATE_HALF + POST, GATE_H, POST, frame, 255, light);
        box(entry, consumer, GATE_HALF - POST, 0, -POST, GATE_HALF + POST, GATE_H, POST, frame, 255, light);
        box(entry, consumer, -GATE_HALF - POST, GATE_H, -POST, GATE_HALF + POST, GATE_H + 2 * POST, POST, frame, 255, light);
        // The veil: bands rippling up, lighter crests
        float band = GATE_H / VEIL_BANDS;
        for (int i = 0; i < VEIL_BANDS; i++) {
            float wave = (float) Math.sin(time * 0.15 - i * 0.7);
            int shade = lighten(color, 0.12f + 0.2f * wave);
            int alpha = (int) (150 + 50 * wave);
            quad(entry, consumer, -GATE_HALF + POST, i * band, GATE_HALF - POST, (i + 1) * band, shade, alpha, light);
        }
    }

    private static int darken(int rgb, float k) {
        return ((int) (((rgb >> 16) & 255) * k) << 16) | ((int) (((rgb >> 8) & 255) * k) << 8) | (int) ((rgb & 255) * k);
    }

    private static int lighten(int rgb, float k) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((int) (r + (255 - r) * k) << 16) | ((int) (g + (255 - g) * k) << 8) | (int) (b + (255 - b) * k);
    }

    /** A flat quad at z 0 from (x0, y0) to (x1, y1), seen from both sides. */
    private static void quad(MatrixStack.Entry e, net.minecraft.client.render.VertexConsumer c, float x0, float y0, float x1, float y1,
                             int rgb, int alpha, int light) {
        vertex(e, c, x0, y0, 0, rgb, alpha, light, 0, 0, 1);
        vertex(e, c, x1, y0, 0, rgb, alpha, light, 0, 0, 1);
        vertex(e, c, x1, y1, 0, rgb, alpha, light, 0, 0, 1);
        vertex(e, c, x0, y1, 0, rgb, alpha, light, 0, 0, 1);
        vertex(e, c, x0, y1, 0, rgb, alpha, light, 0, 0, -1);
        vertex(e, c, x1, y1, 0, rgb, alpha, light, 0, 0, -1);
        vertex(e, c, x1, y0, 0, rgb, alpha, light, 0, 0, -1);
        vertex(e, c, x0, y0, 0, rgb, alpha, light, 0, 0, -1);
    }

    /** An axis-aligned box, its 6 faces. */
    private static void box(MatrixStack.Entry e, net.minecraft.client.render.VertexConsumer c, float x0, float y0, float z0,
                            float x1, float y1, float z1, int rgb, int alpha, int light) {
        float[][] faces = {
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0}};
        for (float[] f : faces) {
            for (int v = 0; v < 4; v++) vertex(e, c, f[v * 3], f[v * 3 + 1], f[v * 3 + 2], rgb, alpha, light, f[12], f[13], f[14]);
        }
    }

    private static void vertex(MatrixStack.Entry e, net.minecraft.client.render.VertexConsumer c, float x, float y, float z,
                               int rgb, int alpha, int light, float nx, float ny, float nz) {
        c.vertex(e.getPositionMatrix(), x, y, z).color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha)
                .texture(0.5f, 0.5f).overlay(net.minecraft.client.render.OverlayTexture.DEFAULT_UV).light(light).normal(e, nx, ny, nz);
    }
}
