package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.board.ExplorerHelmet;
import fr.lordfinn.steveparty.items.ModItems;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import net.minecraft.util.Identifier;

/**
 * The worn Explorer's Helmet: its own little model on the head (a khaki dome with a brown band, a wide brim and a 4x4
 * headlamp at the front of the dome), on textures/models/armor/explorer_helmet.png (64x32, laid out by
 * the art sources). While the helmet shows the board the lamp is lit (full bright) and casts a
 * beam: a flat truncated pyramid of light widening forward, additive, fading toward its end (no block light, no
 * collision). Dark when its key switched it off: everyone sees which. The beam is never drawn for the player's own
 * head in first person, nor in a screen (the inventory's player preview).
 */
public final class ExplorerHelmetRenderer {
    public static final Identifier TEXTURE = Steveparty.id("textures/models/armor/explorer_helmet.png");
    /** The lamp's front, its middle (head pixels, the face toward -z), and the beam: length, half-sizes at both ends. */
    private static final float LAMP_FRONT = -5.85f, LAMP_Y = -8.2f;
    private static final float BEAM_LENGTH = 44, BEAM_START = 1.7f, BEAM_END = 13, CORE_START = 0.9f, CORE_END = 6.5f;
    private static final int BEAM_RGB = 0xFFE29A;
    private static final float BEAM_ALPHA = 0.30f, CORE_ALPHA = 0.32f;
    private static ModelPart helmet, lampOff, lampOn;

    private ExplorerHelmetRenderer() {
    }

    /** The model, in the head's space (pixels, y down, the face toward -z). */
    static TexturedModelData model() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData shell = root.addChild("helmet", ModelPartBuilder.create(), ModelTransform.NONE);
        shell.addChild("dome", ModelPartBuilder.create().uv(0, 0).cuboid(-4, -10.2f, -4, 8, 4, 8, new Dilation(0.8f)), ModelTransform.NONE);
        shell.addChild("brim", ModelPartBuilder.create().uv(0, 12).cuboid(-6.5f, -5.5f, -7, 13, 1, 14), ModelTransform.NONE);
        root.addChild("lamp_off", ModelPartBuilder.create().uv(32, 0).cuboid(-2, LAMP_Y - 2, LAMP_FRONT + 0.05f, 4, 4, 1), ModelTransform.NONE);
        root.addChild("lamp_on", ModelPartBuilder.create().uv(42, 0).cuboid(-2, LAMP_Y - 2, LAMP_FRONT + 0.05f, 4, 4, 1), ModelTransform.NONE);
        return TexturedModelData.of(data, 64, 32);
    }

    public static void register() {
        ArmorRenderer.register((matrices, vertexConsumers, stack, entity, slot, light, contextModel) -> {
            if (helmet == null) {
                ModelPart root = model().createModel();
                helmet = root.getChild("helmet");
                lampOff = root.getChild("lamp_off");
                lampOn = root.getChild("lamp_on");
            }
            if (!contextModel.head.visible) return;
            boolean lit = ExplorerHelmet.lit(stack);
            VertexConsumer solid = ItemRenderer.getArmorGlintConsumer(vertexConsumers, RenderLayer.getArmorCutoutNoCull(TEXTURE), stack.hasGlint());
            matrices.push();
            contextModel.head.rotate(matrices);
            helmet.render(matrices, solid, light, OverlayTexture.DEFAULT_UV);
            if (lit) {
                lampOn.render(matrices, solid, LightmapTextureManager.MAX_LIGHT_COORDINATE, OverlayTexture.DEFAULT_UV);
                if (showsBeam(entity)) {
                    matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
                    VertexConsumer beam = vertexConsumers.getBuffer(BeamLayer.BEAM);
                    beam(beam, matrices.peek().getPositionMatrix(), BEAM_START, BEAM_END, BEAM_ALPHA);
                    beam(beam, matrices.peek().getPositionMatrix(), CORE_START, CORE_END, CORE_ALPHA);
                }
            } else {
                lampOff.render(matrices, solid, light, OverlayTexture.DEFAULT_UV);
            }
            matrices.pop();
        }, ModItems.EXPLORER_HELMET);
    }

    /** Not for the player's own head seen from inside, nor in a screen (orthographic projection: a GUI preview). */
    private static boolean showsBeam(LivingEntity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (entity == client.getCameraEntity() && client.options.getPerspective().isFirstPerson()) return false;
        return Math.abs(RenderSystem.getProjectionMatrix().m23()) > 0.5f;
    }

    /** The 4 sides of a truncated pyramid from the lamp forward, opaque-ish at the lamp, clear at its end. */
    private static void beam(VertexConsumer consumer, Matrix4f matrix, float start, float end, float alpha) {
        float z0 = LAMP_FRONT, z1 = LAMP_FRONT - BEAM_LENGTH;
        int a0 = Math.round(alpha * 255);
        int r = (BEAM_RGB >> 16) & 0xFF, g = (BEAM_RGB >> 8) & 0xFF, b = BEAM_RGB & 0xFF;
        float[][] near = corners(start), far = corners(end);
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            consumer.vertex(matrix, near[i][0], LAMP_Y + near[i][1], z0).color(r, g, b, a0);
            consumer.vertex(matrix, near[j][0], LAMP_Y + near[j][1], z0).color(r, g, b, a0);
            consumer.vertex(matrix, far[j][0], LAMP_Y + far[j][1], z1).color(r, g, b, 0);
            consumer.vertex(matrix, far[i][0], LAMP_Y + far[i][1], z1).color(r, g, b, 0);
        }
    }

    /** The 4 corners of a square of half-size {@code h}, flattened a little (a flat pyramid: wider than tall). */
    private static float[][] corners(float h) {
        float w = h, v = h * 0.7f;
        return new float[][]{{-w, -v}, {w, -v}, {w, v}, {-w, v}};
    }

    /** The beam's render layer: positions and colours, additive like lightning, both sides, no depth written. */
    private static final class BeamLayer extends RenderLayer {
        static final RenderLayer BEAM = RenderLayer.of("steveparty_explorer_helmet_beam", VertexFormats.POSITION_COLOR,
                VertexFormat.DrawMode.QUADS, 1536, false, true, MultiPhaseParameters.builder()
                        .program(LIGHTNING_PROGRAM)
                        .transparency(LIGHTNING_TRANSPARENCY)
                        .cull(DISABLE_CULLING)
                        .writeMaskState(COLOR_MASK)
                        .build(false));

        private BeamLayer(String name, VertexFormat format, VertexFormat.DrawMode mode, int size, boolean crumbling, boolean translucent,
                          Runnable begin, Runnable end) {
            super(name, format, mode, size, crumbling, translucent, begin, end);
        }
    }
}
