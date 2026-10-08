package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartEntity;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.TntMinecartEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws a Boomcart: its iron (BoomcartModel), its eyes' warm pixels glowing on top, and its load, from the model's
 * "load" bone so it rocks with the body, the way the vanilla TNT minecart draws its block:
 * <ul>
 *     <li><b>TNT</b>: the vanilla TNT block (resource packs apply), 12 px, sunk in the cart and centred on the body;
 *     flashing white once lit, as vanilla primed TNT.</li>
 *     <li><b>Firework</b>: the vanilla barrel, open, the same size, and three rockets in it (single cubes, our texture),
 *     their heights and tilts as in v12.</li>
 *     <li><b>Fuses</b>: two crossed planes on the TNT's top, smaller ones on each rocket's tip, tilted with it. Their
 *     texture's frame goes unlit, lit, about to blow ({@link #fuseFrame}); lit, they glow and burn down, sinking into
 *     the load.</li>
 * </ul>
 * The load's placements are the v12 ones (art/previews/boomcart/models/v12_*.json), in its block-model coordinates:
 * pixels, x 0..16, the face toward +z ({@link LoadLayer#toV12}).
 */
public class BoomcartRenderer extends GeoEntityRenderer<BoomcartEntity> {
    private static final Identifier LOAD_TEXTURE = Steveparty.id("textures/entity/boomcart_load.png");
    private static final Identifier[] GLOW = {Steveparty.id("textures/entity/boomcart_glow.png"),
            Steveparty.id("textures/entity/boomcart_open_glow.png"), Steveparty.id("textures/entity/boomcart_lit_glow.png")};
    private static final int FULL_BRIGHT = LightmapTextureManager.MAX_LIGHT_COORDINATE;
    /** boomcart_load.png's size, and its fuse frames (7x9 each, side by side from this row). */
    private static final float LOAD_W = 64, LOAD_H = 32;
    private static final int FUSE_ROW = 10, FUSE_W = 7, FUSE_H = 9;

    public BoomcartRenderer(EntityRendererFactory.Context context) {
        super(context, new BoomcartModel());
        this.shadowRadius = 0.5f;
        addRenderLayer(new GlowLayer(this));
        addRenderLayer(new LoadLayer(this));
    }

    /** The fuse's texture frame: 0 unlit, 1 lit, 2 about to blow (flickering toward it as the fuse runs out). */
    static int fuseFrame(BoomcartEntity boomcart) {
        int fuse = boomcart.getFuse();
        if (fuse < 0) return 0;
        int period = fuse < 30 ? 1 : fuse < 80 ? 2 : 4;
        return (boomcart.age / period) % 2 == 0 ? 2 : 1;
    }

    /** How far the fuse has burnt, 0..1. */
    static float burnt(BoomcartEntity boomcart, float partialTick) {
        int total = boomcart.getFuseTotal();
        if (!boomcart.isLit() || total <= 0) return 0;
        return Math.clamp(1 - (boomcart.getFuse() - partialTick) / total, 0f, 1f);
    }

    /** Its eyes' warm pixels, glowing in the dark mines. */
    private static final class GlowLayer extends GeoRenderLayer<BoomcartEntity> {
        GlowLayer(GeoRenderer<BoomcartEntity> renderer) {
            super(renderer);
        }

        @Override
        public void render(MatrixStack poseStack, BoomcartEntity boomcart, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                           VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick,
                           int packedLight, int packedOverlay) {
            if (boomcart.deathTime > 0) return;
            RenderLayer layer = RenderLayer.getEyes(GLOW[BoomcartModel.eyes(boomcart)]);
            getRenderer().reRender(bakedModel, poseStack, bufferSource, boomcart, layer, bufferSource.getBuffer(layer),
                    partialTick, FULL_BRIGHT, OverlayTexture.DEFAULT_UV, 0xFFFFFFFF);
        }
    }

    private static final class LoadLayer extends GeoRenderLayer<BoomcartEntity> {
        private static final BlockState TNT = Blocks.TNT.getDefaultState();
        private static final BlockState BARREL = Blocks.BARREL.getDefaultState().with(BarrelBlock.OPEN, true);

        /** A rocket of v12_d1_firework: its cube, its tilt (axis, degrees, around origin), its faces' uv. */
        private record Rocket(float[] from, float[] to, char axis, float angle, float[] origin, float[][] uv) {
        }

        // uv: north, south, east, west, up, down (pixels of boomcart_load.png)
        private static final Rocket[] ROCKETS = {
                new Rocket(new float[]{4.3f, 11, 6}, new float[]{7.3f, 20, 9}, 'z', 6, new float[]{5.8f, 14, 7.5f},
                        new float[][]{{0, 0, 3, 9}, {3, 0, 6, 9}, {6, 0, 9, 9}, {9, 0, 12, 9}, {12, 0, 15, 3}, {15, 0, 18, 3}}),
                new Rocket(new float[]{8.6f, 11, 6}, new float[]{11.6f, 18.5f, 9}, 'z', -6, new float[]{10.1f, 14, 7.5f},
                        new float[][]{{25, 0, 28, 8}, {28, 0, 31, 8}, {31, 0, 34, 8}, {34, 0, 37, 8}, {37, 0, 40, 3}, {40, 0, 43, 3}}),
                new Rocket(new float[]{6.5f, 11, 9.8f}, new float[]{9.5f, 17, 12.8f}, 'x', 8, new float[]{8, 14, 11.3f},
                        new float[][]{{43, 0, 46, 6}, {46, 0, 49, 6}, {49, 0, 52, 6}, {52, 0, 55, 6}, {55, 0, 58, 3}, {58, 0, 61, 3}}),
        };
        /** The rockets' fuses: 6 px high on their tips, 4.67 wide (the TNT's: 9 x 7). */
        private static final float ROCKET_FUSE_H = 6, ROCKET_FUSE_W = 14 / 3f;
        /** The fuse sinks this far into its load as it burns (px): the TNT's, the rockets'. */
        private static final float TNT_FUSE_SINK = 5, ROCKET_FUSE_SINK = 3;

        LoadLayer(GeoRenderer<BoomcartEntity> renderer) {
            super(renderer);
        }

        @Override
        public void renderForBone(MatrixStack poseStack, BoomcartEntity boomcart, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, float partialTick,
                                  int packedLight, int packedOverlay) {
            if (!bone.getName().equals("load")) return;
            poseStack.push();
            toV12(poseStack);
            int frame = fuseFrame(boomcart);
            float burnt = burnt(boomcart, partialTick);
            int fuseLight = boomcart.isLit() ? FULL_BRIGHT : packedLight;
            VertexConsumer load;
            if (boomcart.carriesFirework()) {
                block(poseStack, bufferSource, BARREL, packedLight, false);
                load = bufferSource.getBuffer(RenderLayer.getEntityCutoutNoCull(LOAD_TEXTURE));
                for (Rocket rocket : ROCKETS) {
                    poseStack.push();
                    tilt(poseStack, rocket.axis, rocket.angle, rocket.origin);
                    cube(poseStack.peek(), load, rocket.from, rocket.to, rocket.uv, packedLight, packedOverlay);
                    float cx = (rocket.from[0] + rocket.to[0]) / 2, cz = (rocket.from[2] + rocket.to[2]) / 2;
                    float y = rocket.to[1] - ROCKET_FUSE_SINK * burnt;
                    float[] uv = fuseUv(frame);
                    plane(poseStack.peek(), load, cx - ROCKET_FUSE_W / 2, y, cz, cx + ROCKET_FUSE_W / 2, y + ROCKET_FUSE_H, cz,
                            uv, fuseLight);
                    plane(poseStack.peek(), load, cx, y, cz - ROCKET_FUSE_W / 2, cx, y + ROCKET_FUSE_H, cz + ROCKET_FUSE_W / 2,
                            uv, fuseLight);
                    poseStack.pop();
                }
            } else {
                boolean flash = boomcart.isLit() && boomcart.getFuse() / 5 % 2 == 0;
                block(poseStack, bufferSource, TNT, packedLight, flash);
                load = bufferSource.getBuffer(RenderLayer.getEntityCutoutNoCull(LOAD_TEXTURE));
                float y = 14 - TNT_FUSE_SINK * burnt;
                float[] uv = fuseUv(frame);
                for (float angle : new float[]{45, -45}) {
                    poseStack.push();
                    tilt(poseStack, 'y', angle, new float[]{8, 14, 9});
                    plane(poseStack.peek(), load, 4.5f, y, 9, 11.5f, y + 9, 9, uv, fuseLight);
                    poseStack.pop();
                }
            }
            poseStack.pop();
            // Give GeckoLib its buffer back (see GeoRenderLayer#renderForBone)
            bufferSource.getBuffer(renderType);
        }

        /**
         * From the load bone's frame (the model's: blocks, x as in Blockbench, facing -z) to v12's: pixels, the body
         * on x 0..16, z 0..18, facing +z. Half a turn around y: v12 (x, y, z) is the model's (8 - x, y, 10 - z).
         */
        static void toV12(MatrixStack poseStack) {
            poseStack.translate(0.5f, 0, 0.625f);
            poseStack.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180));
            poseStack.scale(1 / 16f, 1 / 16f, 1 / 16f);
        }

        /** A vanilla block, 12 px, sunk in the cart: v12's [2, 2, 3] to [14, 14, 15]. */
        private static void block(MatrixStack poseStack, VertexConsumerProvider bufferSource, BlockState state, int light,
                                  boolean flash) {
            poseStack.push();
            poseStack.translate(2, 2, 3);
            poseStack.scale(12, 12, 12);
            TntMinecartEntityRenderer.renderFlashingBlock(MinecraftClient.getInstance().getBlockRenderManager(), state,
                    poseStack, bufferSource, light, flash);
            poseStack.pop();
        }

        /** A block-model rotation: around one axis, through origin (pixels). */
        private static void tilt(MatrixStack poseStack, char axis, float angle, float[] origin) {
            poseStack.translate(origin[0], origin[1], origin[2]);
            poseStack.multiply(switch (axis) {
                case 'x' -> RotationAxis.POSITIVE_X.rotationDegrees(angle);
                case 'y' -> RotationAxis.POSITIVE_Y.rotationDegrees(angle);
                default -> RotationAxis.POSITIVE_Z.rotationDegrees(angle);
            });
            poseStack.translate(-origin[0], -origin[1], -origin[2]);
        }

        private static float[] fuseUv(int frame) {
            return new float[]{frame * FUSE_W, FUSE_ROW, frame * FUSE_W + FUSE_W, FUSE_ROW + FUSE_H};
        }

        /**
         * A cube in block-model terms: faces north, south, east, west, up, down, their uv [u1, v1, u2, v2] in pixels,
         * mapped as Minecraft's block models map them.
         */
        private static void cube(MatrixStack.Entry entry, VertexConsumer consumer, float[] f, float[] t, float[][] uv,
                                 int light, int overlay) {
            float x0 = f[0], y0 = f[1], z0 = f[2], x1 = t[0], y1 = t[1], z1 = t[2];
            // each face: top-left, bottom-left, bottom-right, top-right, seen from outside
            quad(entry, consumer, uv[0], light, overlay, 0, 0, -1, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0);
            quad(entry, consumer, uv[1], light, overlay, 0, 0, 1, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1);
            quad(entry, consumer, uv[2], light, overlay, 1, 0, 0, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0);
            quad(entry, consumer, uv[3], light, overlay, -1, 0, 0, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1);
            quad(entry, consumer, uv[4], light, overlay, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            quad(entry, consumer, uv[5], light, overlay, 0, -1, 0, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1);
        }

        /** A flat plane from (x0, y0, z0) to (x1, y1, z1), upright, seen from both sides (the layer doesn't cull). */
        private static void plane(MatrixStack.Entry entry, VertexConsumer consumer, float x0, float y0, float z0,
                                  float x1, float y1, float z1, float[] uv, int light) {
            Vector3f normal = new Vector3f(z1 - z0, 0, x0 - x1).normalize();
            quad(entry, consumer, uv, light, OverlayTexture.DEFAULT_UV, normal.x, 0, normal.z,
                    x0, y1, z0, x0, y0, z0, x1, y0, z1, x1, y1, z1);
        }

        private static void quad(MatrixStack.Entry entry, VertexConsumer consumer, float[] uv, int light, int overlay,
                                 float nx, float ny, float nz, float... p) {
            float u0 = uv[0] / LOAD_W, v0 = uv[1] / LOAD_H, u1 = uv[2] / LOAD_W, v1 = uv[3] / LOAD_H;
            float[] us = {u0, u0, u1, u1}, vs = {v0, v1, v1, v0};
            for (int i = 0; i < 4; i++) {
                consumer.vertex(entry, p[i * 3], p[i * 3 + 1], p[i * 3 + 2]).color(0xFFFFFFFF).texture(us[i], vs[i])
                        .overlay(overlay).light(light).normal(entry, nx, ny, nz);
            }
        }
    }
}
