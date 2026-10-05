package fr.lordfinn.steveparty.client.telescope;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.telescope.TelescopeMath;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.ModelIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.random.Random;

/**
 * The Telescope's shape, for the block and its item: a tall tripod of three sticks under a copper hub, and on top the
 * game's own spyglass (its in-hand model and texture, as the resource packs give them: nothing copied), bigger, turning
 * and tilting freely about {@link TelescopeMath#PIVOT_HEIGHT}. In block space, from the centre of the block's floor,
 * y up.
 */
public final class TelescopeModel {
    public static final Identifier TEXTURE = Steveparty.id("textures/entity/telescope.png");
    private static final ModelIdentifier SPYGLASS = ModelIdentifier.ofInventoryVariant(Identifier.ofVanilla("spyglass_in_hand"));
    /** How the tube is tilted when nobody looks through it (degrees, negative: up). */
    public static final float REST_PITCH = -35f;
    /** The tube's length (blocks): the spyglass is scaled to it. */
    private static final float TUBE_LENGTH = 1.3f;
    private static final float LEG_SPLAY = 13.5f * MathHelper.RADIANS_PER_DEGREE;

    private final ModelPart base;
    private final ItemStack spyglass = new ItemStack(Items.SPYGLASS);

    /** The spyglass model it was measured on, and what was found: its two ends along y, its axis, its wider end. */
    private BakedModel measured;
    private float low, high, centreX, centreZ;
    private boolean wideOnTop;

    public TelescopeModel() {
        ModelData data = new ModelData();
        ModelPartData root = data.getRoot();
        ModelPartData base = root.addChild("base", ModelPartBuilder.create()
                // the hub: wide enough to cover the top ends of the three sticks, which end inside it
                .uv(8, 0).cuboid(-3f, 25f, -3f, 6f, 2f, 6f)
                // the pin: standing on the hub, up to the tube's pivot
                .uv(8, 8).cuboid(-1f, 27f, -1f, 2f, 4f, 2f), ModelTransform.NONE);
        for (int i = 0; i < 3; i++) {
            base.addChild("leg" + i, ModelPartBuilder.create().uv(0, 0).cuboid(-1f, -27f, -1f, 2f, 27f, 2f),
                    ModelTransform.of(0f, 26f, 0f, LEG_SPLAY, i * MathHelper.TAU / 3f + MathHelper.PI, 0f));
        }
        this.base = TexturedModelData.of(data, 64, 32).createModel().getChild("base");
    }

    /** Finds the spyglass model's ends and which is the wide one (the lens): any resource pack's model will do. */
    private void measure(BakedModel model) {
        measured = model;
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minZ = Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;
        low = Float.MAX_VALUE;
        high = -Float.MAX_VALUE;
        Random random = Random.create(42);
        java.util.List<float[]> points = new java.util.ArrayList<>();
        for (int d = -1; d < Direction.values().length; d++) {
            for (BakedQuad quad : model.getQuads(null, d < 0 ? null : Direction.values()[d], random)) {
                int[] data = quad.getVertexData();
                int stride = data.length / 4;
                for (int v = 0; v < 4; v++) {
                    float x = Float.intBitsToFloat(data[v * stride]), y = Float.intBitsToFloat(data[v * stride + 1]),
                            z = Float.intBitsToFloat(data[v * stride + 2]);
                    points.add(new float[]{x, y, z});
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                    low = Math.min(low, y);
                    high = Math.max(high, y);
                }
            }
        }
        if (points.isEmpty()) {
            low = 0;
            high = 1;
            centreX = centreZ = 0.5f;
            wideOnTop = true;
            return;
        }
        centreX = (minX + maxX) / 2;
        centreZ = (minZ + maxZ) / 2;
        float topWidth = 0, bottomWidth = 0, third = (high - low) / 3;
        for (float[] p : points) {
            float w = Math.max(Math.abs(p[0] - centreX), Math.abs(p[2] - centreZ));
            if (p[1] > high - third) topWidth = Math.max(topWidth, w);
            if (p[1] < low + third) bottomWidth = Math.max(bottomWidth, w);
        }
        wideOnTop = topWidth >= bottomWidth;
    }

    /**
     * @param baseYaw   where the tripod faces (degrees, as a player's yaw)
     * @param tubeYaw   where the tube points (degrees, as a player's yaw)
     * @param tubePitch its tilt (degrees, as a player's pitch: negative is up)
     * @param tube      false: the tripod alone
     */
    public void render(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, int overlay,
                       float baseYaw, float tubeYaw, float tubePitch, boolean tube) {
        VertexConsumer vertices = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        base.yaw = -baseYaw * MathHelper.RADIANS_PER_DEGREE;
        base.render(matrices, vertices, light, overlay);
        if (!tube) return;

        MinecraftClient client = MinecraftClient.getInstance();
        BakedModel model = client.getBakedModelManager().getModel(SPYGLASS);
        if (model != measured) measure(model);
        float scale = TUBE_LENGTH / Math.max(0.01f, high - low);
        float back = (float) TelescopeMath.EYEPIECE_BACK / scale;
        matrices.push();
        matrices.translate(0f, (float) TelescopeMath.PIVOT_HEIGHT, 0f);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-tubeYaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tubePitch));
        // the spyglass stands along y: laid along the aim (+z), its lens in front, its eyepiece just behind the pivot
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(wideOnTop ? 90f : -90f));
        matrices.scale(scale, scale, scale);
        matrices.translate(0.5f - centreX, wideOnTop ? -back - (low - 0.5f) : back - (high - 0.5f), 0.5f - centreZ);
        client.getItemRenderer().renderItem(spyglass, ModelTransformationMode.NONE, false, matrices, vertexConsumers, light, overlay, model);
        matrices.pop();
    }
}
