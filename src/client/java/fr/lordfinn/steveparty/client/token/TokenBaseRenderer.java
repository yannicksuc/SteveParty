package fr.lordfinn.steveparty.client.token;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.TokenBase;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.event.GeoRenderEvent;

import java.util.List;

/**
 * Draws the coloured pawn base ("socle") under the tokens: a small octagonal disc, {@link TokenBase#BASE_HEIGHT}
 * high, lying on the ground at the entity's feet, world aligned (it ignores the body yaw and the model scale).
 * <p>
 * The texture ({@link #TEXTURE}, 32x16, grayscale) is tinted with the token colour through the vertex colour:
 * left 16x16 = top face (an octagon: lip, light rim, bevel, fill), right half = sides (rows 0-2, top to bottom) and
 * bottom (rows 8-15). Standard entity render layer, so it is lit, fogged, outlined and shader (Iris) friendly.
 * <p>
 * Vanilla living entities: {@code TokenBaseLivingEntityRendererMixin}. GeckoLib entities (Mula...): the GeckoLib
 * pre-render event registered by {@link #initialize()}. Render thread only, no allocation per frame.
 */
public final class TokenBaseRenderer {
    public static final Identifier TEXTURE = Steveparty.id("textures/entity/token_base.png");
    /** Base colour of a token whose name is not coloured. */
    public static final int DEFAULT_COLOR = 0xC8C8C8;

    private static final float MIN_RADIUS = 0.2F;
    private static final float MAX_RADIUS = 0.75F;
    /** Radius of the base relatively to the width of the token. */
    private static final float RADIUS_PER_WIDTH = 0.55F;

    private static final float TEXTURE_WIDTH = 32.0F;
    private static final float TEXTURE_HEIGHT = 16.0F;
    /** Side strip: 3 pixels high; each of the 8 sides is 2 * tan(22.5°) * 8 ≈ 6.6 pixels long, like on the top. */
    private static final float SIDE_U0 = 16.0F / TEXTURE_WIDTH;
    private static final float SIDE_U1 = (16.0F + 16.0F * (float) Math.tan(Math.PI / 8.0)) / TEXTURE_WIDTH;
    private static final float SIDE_V1 = 3.0F / TEXTURE_HEIGHT;
    private static final float BOTTOM_U = 24.0F / TEXTURE_WIDTH;
    private static final float BOTTOM_V = 12.0F / TEXTURE_HEIGHT;

    /** Octagon with flat sides facing the axes: corner k at angle 22.5° + 45° * k, for an apothem of 1. */
    private static final float[] CORNER_X = new float[8];
    private static final float[] CORNER_Z = new float[8];
    /** Outward normal of the side between corners k and k + 1. */
    private static final float[] SIDE_NX = new float[8];
    private static final float[] SIDE_NZ = new float[8];
    /** Top / bottom faces as 3 quads (trapezoid, rectangle, trapezoid): corner indices. */
    private static final int[][] CAP_QUADS = {{0, 1, 2, 3}, {3, 4, 7, 0}, {4, 5, 6, 7}};

    static {
        double circumradius = 1.0 / Math.cos(Math.PI / 8.0);
        for (int k = 0; k < 8; k++) {
            double angle = Math.PI / 8.0 + k * Math.PI / 4.0;
            CORNER_X[k] = (float) (Math.cos(angle) * circumradius);
            CORNER_Z[k] = (float) (Math.sin(angle) * circumradius);
            double middle = (k + 1) * Math.PI / 4.0;
            SIDE_NX[k] = (float) Math.cos(middle);
            SIDE_NZ[k] = (float) Math.sin(middle);
        }
    }

    // Render thread scratch vectors
    private static final Vector3f POSITION = new Vector3f();
    private static final Vector3f NORMAL = new Vector3f();

    private TokenBaseRenderer() {
    }

    /** Hooks the base into the GeckoLib renderers (Mula...), which do not go through LivingEntityRenderer. */
    public static void initialize() {
        GeoRenderEvent.Entity.Pre.EVENT.register(event -> {
            Entity entity = event.getEntity();
            if (entity == null || !TokenBase.isToken(entity)) return true;
            MatrixStack matrices = event.getPoseStack();
            // Fired inside the renderer's push / pop, before the model and its layers: translations stay local
            if (!entity.isInvisible()) {
                render(matrices, event.getBufferSource(), event.getPackedLight(), colorOf(entity),
                        radiusFor(entity.getWidth()), TokenBase.BASE_HEIGHT);
            }
            // GeckoLib's upside-down (Dinnerbone) transform already uses getHeight(), base included
            if (!(entity instanceof LivingEntity living && LivingEntityRenderer.shouldFlipUpsideDown(living))) {
                matrices.translate(0.0F, TokenBase.BASE_HEIGHT, 0.0F);
            }
            return true;
        });
    }

    /** Radius of the base (centre to flat side) of a token {@code width} blocks wide. */
    public static float radiusFor(float width) {
        return MathHelper.clamp(width * RADIUS_PER_WIDTH, MIN_RADIUS, MAX_RADIUS);
    }

    /**
     * Token colour (0xRRGGBB): the colour of its custom name (same lookup as {@code MessageUtils#getColorFromText}),
     * or {@link #DEFAULT_COLOR} if the name is not coloured. No allocation.
     */
    public static int colorOf(Entity entity) {
        Text name = entity.getCustomName();
        if (name == null) return DEFAULT_COLOR;
        List<Text> siblings = name.getSiblings();
        for (int i = 0, size = siblings.size(); i < size; i++) {
            TextColor color = siblings.get(i).getStyle().getColor();
            if (color != null) return color.getRgb() & 0xFFFFFF;
        }
        TextColor color = name.getStyle().getColor();
        return color != null ? color.getRgb() & 0xFFFFFF : DEFAULT_COLOR;
    }

    /** Draws a base centred on the origin of {@code matrices}, its bottom at y = 0. */
    public static void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int rgb,
                              float radius, float height) {
        VertexConsumer consumer = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        MatrixStack.Entry entry = matrices.peek();
        int color = 0xFF000000 | rgb;
        int overlay = OverlayTexture.DEFAULT_UV;

        // Top: the 16x16 octagon of the texture, flat sides on the texture borders
        for (int[] quad : CAP_QUADS) {
            for (int corner : quad) {
                float x = CORNER_X[corner];
                float z = CORNER_Z[corner];
                vertex(consumer, entry, x * radius, height, z * radius, color,
                        (0.5F + x * 0.5F) * 16.0F / TEXTURE_WIDTH, (0.5F + z * 0.5F) * 16.0F / TEXTURE_HEIGHT,
                        overlay, light, 0.0F, 1.0F, 0.0F);
            }
        }
        // Sides
        for (int k = 0; k < 8; k++) {
            int next = (k + 1) & 7;
            float x0 = CORNER_X[k] * radius, z0 = CORNER_Z[k] * radius;
            float x1 = CORNER_X[next] * radius, z1 = CORNER_Z[next] * radius;
            float nx = SIDE_NX[k], nz = SIDE_NZ[k];
            vertex(consumer, entry, x0, height, z0, color, SIDE_U0, 0.0F, overlay, light, nx, 0.0F, nz);
            vertex(consumer, entry, x0, 0.0F, z0, color, SIDE_U0, SIDE_V1, overlay, light, nx, 0.0F, nz);
            vertex(consumer, entry, x1, 0.0F, z1, color, SIDE_U1, SIDE_V1, overlay, light, nx, 0.0F, nz);
            vertex(consumer, entry, x1, height, z1, color, SIDE_U1, 0.0F, overlay, light, nx, 0.0F, nz);
        }
        // Bottom (seen from below, e.g. on the floating start tile): plain dark area of the texture
        for (int[] quad : CAP_QUADS) {
            for (int i = 3; i >= 0; i--) {
                int corner = quad[i];
                vertex(consumer, entry, CORNER_X[corner] * radius, 0.0F, CORNER_Z[corner] * radius, color,
                        BOTTOM_U, BOTTOM_V, overlay, light, 0.0F, -1.0F, 0.0F);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float x, float y, float z, int color,
                               float u, float v, int overlay, int light, float nx, float ny, float nz) {
        Matrix4f position = entry.getPositionMatrix();
        position.transformPosition(x, y, z, POSITION);
        entry.transformNormal(nx, ny, nz, NORMAL);
        consumer.vertex(POSITION.x, POSITION.y, POSITION.z, color, u, v, overlay, light, NORMAL.x, NORMAL.y, NORMAL.z);
    }
}
