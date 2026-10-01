package fr.lordfinn.steveparty.client.telescope;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * The Telescope's shape, for the block and its item: a tripod of three sticks under a copper hub, and a copper tube
 * (eyepiece behind, a wider ring and its lens in front) that turns and tilts freely on top. In block space, from the
 * centre of the block's floor, y up; the tube points to the south (+z) when its yaw is 0.
 */
public final class TelescopeModel {
    public static final Identifier TEXTURE = Steveparty.id("textures/entity/telescope.png");
    /** How the tube is tilted when nobody looks through it (degrees, negative: up). */
    public static final float REST_PITCH = -35f;
    private static final float LEG_SPLAY = 25f * MathHelper.RADIANS_PER_DEGREE;

    private final ModelPart base, tube;

    public TelescopeModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData base = root.addChild("base", ModelPartBuilder.create()
                .uv(8, 0).cuboid(-2f, 10f, -2f, 4f, 2f, 4f)
                .uv(8, 6).cuboid(-1f, 12f, -1f, 2f, 3f, 2f), ModelTransform.NONE);
        for (int i = 0; i < 3; i++) {
            base.addChild("leg" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-1f, -12f, -1f, 2f, 12f, 2f),
                    ModelTransform.of(0f, 11f, 0f, LEG_SPLAY, i * MathHelper.TAU / 3f + MathHelper.PI, 0f));
        }
        root.addChild("tube", ModelPartBuilder.create()
                .uv(0, 16).cuboid(-2f, -2f, -6f, 4f, 4f, 12f)
                .uv(32, 0).cuboid(-3f, -3f, 5f, 6f, 6f, 2f)
                .uv(8, 11).cuboid(-1f, -1f, -9f, 2f, 2f, 3f), ModelTransform.pivot(0f, 17f, 0f));
        ModelPart model = TexturedModelData.of(data, 64, 32).createModel();
        this.base = model.getChild("base");
        this.tube = model.getChild("tube");
    }

    /**
     * @param baseYaw   where the tripod faces (degrees, as a player's yaw)
     * @param tubeYaw   where the tube points (degrees, as a player's yaw)
     * @param tubePitch its tilt (degrees, as a player's pitch: negative is up)
     */
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay,
                       float baseYaw, float tubeYaw, float tubePitch) {
        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        base.yaw = -baseYaw * MathHelper.RADIANS_PER_DEGREE;
        tube.yaw = -tubeYaw * MathHelper.RADIANS_PER_DEGREE;
        tube.pitch = tubePitch * MathHelper.RADIANS_PER_DEGREE;
        base.render(matrices, vertices, light, overlay);
        tube.render(matrices, vertices, light, overlay);
    }
}
