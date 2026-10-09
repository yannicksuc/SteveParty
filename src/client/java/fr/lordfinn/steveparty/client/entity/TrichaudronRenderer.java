package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.render.geo.EmissiveLayer;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronHead;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.GeoBone;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import java.util.List;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;
import software.bernie.geckolib.cache.texture.AnimatableTexture;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;
import software.bernie.geckolib.util.RenderUtil;

/**
 * Draws a Trichaudron: its shell and skin (TrichaudronModel), then
 * <ul>
 *     <li><b>its veins</b> ({@link #veinsTint}): the animated glow layer (trichaudron_veins.png) drawn emissive, brighter
 *     the fuller its tank, dying out as it dies;</li>
 *     <li><b>its tank's lava</b> and <b>its vents</b> ({@link TankAndVentLayer}): on the hidden {@code tank_lava} and
 *     {@code vent<suffix>} bones, their own planes (as authored: the cubes' faces) drawn again with our own animated
 *     lava (26 x 28 px of each 34 x 34 frame) and with each head's vent state texture (idle, charging, spitting:
 *     TrichaudronEntity#getVent), both full bright.</li>
 * </ul>
 */
public class TrichaudronRenderer extends GeoEntityRenderer<TrichaudronEntity> {
    private static final Identifier VEINS = Steveparty.id("textures/entity/trichaudron_veins.png");
    private static final Identifier LAVA = Steveparty.id("textures/entity/trichaudron_lava.png");
    private static final Identifier[] VENT = {Steveparty.id("textures/entity/trichaudron_vent_idle.png"),
            Steveparty.id("textures/entity/trichaudron_vent_charging.png"), Steveparty.id("textures/entity/trichaudron_vent_spitting.png")};
    /** The lava texture's frames are 34 px; the tank's plane shows 26 x 28 of them. */
    private static final float LAVA_U = 26 / 34f, LAVA_V = 28 / 34f;

    /** The reins: a plain wool texture, tinted a rope's tan. */
    private static final Identifier ROPE = Identifier.ofVanilla("textures/block/white_wool.png");
    /** A rein's sag (blocks at its middle), its thickness, its segments. */
    private static final float REIN_SAG = 0.35f, REIN_WIDTH = 0.05f;
    private static final int REIN_SEGMENTS = 16;
    private final TrichaudronModel model;

    public TrichaudronRenderer(EntityRendererFactory.Context context) {
        this(context, new TrichaudronModel());
    }

    private TrichaudronRenderer(EntityRendererFactory.Context context, TrichaudronModel model) {
        super(context, model);
        this.model = model;
        this.shadowRadius = 1.6f;
        addRenderLayer(new EmissiveLayer<>(this, trichaudron -> VEINS, trichaudron -> true, TrichaudronRenderer::veinsTint).animated());
        addRenderLayer(new TankAndVentLayer(this));
    }

    @Override
    public void render(TrichaudronEntity trichaudron, float entityYaw, float partialTick, MatrixStack poseStack,
                       VertexConsumerProvider bufferSource, int packedLight) {
        poseStack.push();
        poseStack.translate(0, -trichaudron.lavaSink(partialTick), 0); // in shallow lava: no knee flush with its surface
        super.render(trichaudron, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        poseStack.pop();
        renderReins(trichaudron, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * Each rider's reins: a rope from each hand to the top of the neck of the head he holds (the first rider the centre
     * head, then the left, then the right: TrichaudronEntity#headOf), sagging. The neck's end is its skull bone as just
     * drawn (TrichaudronModel#reinBone), so the reins follow the neck however it bends and turns.
     */
    private void renderReins(TrichaudronEntity trichaudron, float partialTick, MatrixStack poseStack,
                             VertexConsumerProvider bufferSource, int light) {
        List<Entity> riders = trichaudron.getPassengerList();
        Vec3d origin = trichaudron.getLerpedPos(partialTick);
        VertexConsumer rope = bufferSource.getBuffer(RenderLayer.getEntityCutoutNoCull(ROPE));
        MatrixStack.Entry entry = poseStack.peek();
        if (riders.isEmpty()) return;
        float sink = trichaudron.lavaSink(partialTick);
        for (Entity passenger : riders) {
            int i = trichaudron.headOf(passenger);
            if (i < 0 || !(passenger instanceof PlayerEntity rider)) continue;
            GeoBone bone = model.reinBone(i);
            Vector3d local = bone == null ? null : bone.getLocalPosition();
            Vec3d neck = local == null ? trichaudron.neckTop(i, partialTick).subtract(origin)
                    : new Vec3d(local.x, local.y - sink, local.z);
            float yaw = MathHelper.lerpAngleDegrees(partialTick, rider.prevBodyYaw, rider.bodyYaw);
            Vec3d seat = rider.getLerpedPos(partialTick).subtract(origin);
            Vec3d ahead = Vec3d.fromPolar(0, yaw), side = Vec3d.fromPolar(0, yaw + 90);
            for (int hand = -1; hand <= 1; hand += 2) {
                Vec3d grip = seat.add(ahead.multiply(0.55)).add(side.multiply(0.2 * hand)).add(0, 1.05, 0);
                rein(rope, entry, grip, neck, light);
            }
        }
    }

    private static void rein(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d from, Vec3d to, int light) {
        Vec3d prev = from;
        for (int k = 1; k <= REIN_SEGMENTS; k++) {
            float t = k / (float) REIN_SEGMENTS;
            Vec3d next = from.lerp(to, t).add(0, -REIN_SAG * 4 * t * (1 - t), 0);
            ribbon(consumer, entry, prev, next, new Vec3d(REIN_WIDTH, 0, 0), light);
            ribbon(consumer, entry, prev, next, new Vec3d(0, REIN_WIDTH, 0), light);
            ribbon(consumer, entry, prev, next, new Vec3d(0, 0, REIN_WIDTH), light);
            prev = next;
        }
    }

    private static void ribbon(VertexConsumer consumer, MatrixStack.Entry entry, Vec3d a, Vec3d b, Vec3d half, int light) {
        float u0 = 0.25f, u1 = 0.35f, v0 = 0.25f, v1 = 0.35f;
        int colour = 0xFFC9A26B;
        consumer.vertex(entry, (float) (a.x - half.x), (float) (a.y - half.y), (float) (a.z - half.z)).color(colour).texture(u0, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        consumer.vertex(entry, (float) (a.x + half.x), (float) (a.y + half.y), (float) (a.z + half.z)).color(colour).texture(u1, v0)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        consumer.vertex(entry, (float) (b.x + half.x), (float) (b.y + half.y), (float) (b.z + half.z)).color(colour).texture(u1, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
        consumer.vertex(entry, (float) (b.x - half.x), (float) (b.y - half.y), (float) (b.z - half.z)).color(colour).texture(u0, v1)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0, 1, 0);
    }

    /** The veins' glow, 0..1: dim when the tank is empty, whole when full, out once dead. */
    static float glow(TrichaudronEntity trichaudron, float partialTick) {
        float level = trichaudron.tankLevel(partialTick) / TrichaudronEntity.TANK_MAX;
        float glow = 0.55f + 0.45f * level;
        if (trichaudron.deathTime > 0) glow *= Math.max(0, 1 - (trichaudron.deathTime + partialTick) / 20f);
        return glow;
    }

    /** The veins' tint: grey by {@link #glow}, none (0) once too faint to see. */
    private static int veinsTint(TrichaudronEntity trichaudron, float partialTick) {
        float glow = glow(trichaudron, partialTick);
        if (glow <= 0.01f) return 0;
        int c = MathHelper.clamp(Math.round(glow * 255), 0, 255);
        return 0xFF000000 | c << 16 | c << 8 | c;
    }

    private static final class TankAndVentLayer extends GeoRenderLayer<TrichaudronEntity> {
        TankAndVentLayer(GeoRenderer<TrichaudronEntity> renderer) {
            super(renderer);
        }

        /** The head whose vent this bone is, or -1. */
        private static int ventOf(String bone) {
            for (TrichaudronHead head : TrichaudronEntity.HEADS) if (bone.equals(head.name("vent"))) return head.index();
            return bone.equals("vent") ? 0 : -1; // a one-headed model
        }

        @Override
        public void renderForBone(MatrixStack poseStack, TrichaudronEntity trichaudron, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick,
                                  int packedLight, int packedOverlay) {
            String name = bone.getName();
            if (name.equals("tank_lava")) {
                if (trichaudron.tankLevel(partialTick) < 0.05f) return;
                AnimatableTexture.setAndUpdate(LAVA);
                drawPlanes(poseStack, bone, bufferSource.getBuffer(RenderLayer.getEntityTranslucent(LAVA)), LAVA_U, LAVA_V);
            } else {
                int head = ventOf(name);
                if (head < 0 || trichaudron.deathTime > 0) return;
                Identifier texture = VENT[MathHelper.clamp(trichaudron.getVent(head), 0, VENT.length - 1)];
                AnimatableTexture.setAndUpdate(texture);
                drawPlanes(poseStack, bone, bufferSource.getBuffer(RenderLayer.getEntityTranslucent(texture)), 1, 1);
            }
            // Give GeckoLib its buffer back (see GeoRenderLayer#renderForBone)
            bufferSource.getBuffer(renderType);
        }

        /**
         * The bone's cubes' faces again, full bright, their UV stretched over {@code 0..uMax, 0..vMax} of another
         * texture (the authored UV belongs to the main texture). The pose stack is at the bone (GeckoLib's).
         */
        private static void drawPlanes(MatrixStack poseStack, GeoBone bone, VertexConsumer consumer, float uMax, float vMax) {
            for (GeoCube cube : bone.getCubes()) {
                poseStack.push();
                RenderUtil.translateToPivotPoint(poseStack, cube);
                RenderUtil.rotateMatrixAroundCube(poseStack, cube);
                RenderUtil.translateAwayFromPivotPoint(poseStack, cube);
                MatrixStack.Entry entry = poseStack.peek();
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) continue;
                    GeoVertex[] vertices = quad.vertices();
                    float u0 = Float.MAX_VALUE, u1 = -Float.MAX_VALUE, v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
                    for (GeoVertex vertex : vertices) {
                        u0 = Math.min(u0, vertex.texU());
                        u1 = Math.max(u1, vertex.texU());
                        v0 = Math.min(v0, vertex.texV());
                        v1 = Math.max(v1, vertex.texV());
                    }
                    if (u1 - u0 < 1.0e-6f || v1 - v0 < 1.0e-6f) continue; // a face with no texture (the plane's edges)
                    Vector3f normal = quad.normal();
                    for (GeoVertex vertex : vertices) {
                        Vector3f at = vertex.position();
                        consumer.vertex(entry, at.x(), at.y(), at.z()).color(0xFFFFFFFF)
                                .texture((vertex.texU() - u0) / (u1 - u0) * uMax, (vertex.texV() - v0) / (v1 - v0) * vMax)
                                .overlay(OverlayTexture.DEFAULT_UV).light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                                .normal(entry, normal.x(), normal.y(), normal.z());
                    }
                }
                poseStack.pop();
            }
        }
    }
}
