package fr.lordfinn.steveparty.client.items;

import fr.lordfinn.steveparty.client.entity.DiceEntityRenderer;
import fr.lordfinn.steveparty.components.DiceFacesComponent;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.components.DiceFacesComponent.Kind;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.utils.Easing;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * A die item (one, two or three dice) drawn as dice that keep rolling over: every {@link #STEP} ticks each die rolls a
 * quarter turn, alternately forward and sideways, and the face that lands underneath takes another of the die's faces
 * (its forged faces but the blank ones, or 1 to 10 for a plain die), each painted like the thrown die paints it. Everything follows from
 * the time (nothing kept between frames); the dice sit where the item models used to put their cubes, the models keep
 * their display transforms.
 */
public final class DiceItemRenderer implements BuiltinItemRendererRegistry.DynamicItemRenderer {
    /** Ticks between two rolls, and how long a roll lasts. */
    private static final int STEP = 18;
    private static final float ROLL = 8f;
    /** Ticks a die of a pair or a trio lags behind the previous one. */
    private static final float LAG = 7f;
    /** Half the side of a die: 4 pixels, as in the item models. */
    private static final float HALF = 4 / 16f;

    /** The cube faces: down, up, north, south, west, east (normal, then texture right and texture down). */
    private static final float[][] FACES = {
            {0, -1, 0, 1, 0, 0, 0, 0, -1}, {0, 1, 0, 1, 0, 0, 0, 0, 1},
            {0, 0, -1, -1, 0, 0, 0, -1, 0}, {0, 0, 1, 1, 0, 0, 0, -1, 0},
            {-1, 0, 0, 0, 0, 1, 0, -1, 0}, {1, 0, 0, 0, 0, -1, 0, -1, 0}};
    /** Rolls alternate around X and Z, so the orientation comes back every 6 rolls, each face landing underneath once. */
    private static final int CYCLE = 6;
    /** The orientation after n rolls (n mod 6). */
    private static final Quaternionf[] ORIENTATIONS = new Quaternionf[CYCLE];
    /** For each face, the roll (1..6) of the cycle that lands it underneath. */
    private static final int[] LANDING = new int[FACES.length];
    /** Plain dice: 1 to 10. */
    private static final List<DiceFace> PLAIN = new ArrayList<>();
    /** The layer of each face texture, by kind and value. */
    private static final RenderLayer[][] LAYERS = new RenderLayer[Kind.values().length][DiceFace.MAX_COINS + 2];
    /** The partial roll, reused (render thread only). */
    private static final Quaternionf ROLLING = new Quaternionf();

    static {
        for (int value = DiceEntity.MIN; value <= DiceEntity.MAX; value++) PLAIN.add(new DiceFace(Kind.NORMAL, value));
        Quaternionf orientation = new Quaternionf();
        Vector3f direction = new Vector3f();
        for (int roll = 1; roll <= CYCLE; roll++) {
            orientation.premul(rollAxis(roll - 1, 90f, new Quaternionf()));
            ORIENTATIONS[roll % CYCLE] = new Quaternionf(orientation);
            for (int face = 0; face < FACES.length; face++) {
                orientation.transform(direction.set(FACES[face][0], FACES[face][1], FACES[face][2]));
                if (direction.y < -0.5f) LANDING[face] = roll;
            }
        }
    }

    /** Where each die sits, as the cubes of the item models: centre, then the element's rotation (origin, axis, angle). */
    private final float[][] dice;

    private DiceItemRenderer(float[][] dice) {
        this.dice = dice;
    }

    /** One die, in the middle (models/item/default_dice.json). */
    public static DiceItemRenderer single() {
        return new DiceItemRenderer(new float[][]{{8, 8, 8, 8, 8, 8, 1, 0}});
    }

    /** Two dice, one leaning back above, one turned in front below (models/item/double_dice.json). */
    public static DiceItemRenderer pair() {
        return new DiceItemRenderer(new float[][]{{8, 10, 11, 6, 14, 15, 0, -22.5f}, {11, 4, 3, 9, 8, 7, 1, 45}});
    }

    /** Three dice heaped up (models/item/triple_dice.json). */
    public static DiceItemRenderer trio() {
        return new DiceItemRenderer(new float[][]{{10, 9, 14, 8, 13, 18, 0, -22.5f}, {4, 13, 5, 2, 17, 9, 2, 22.5f},
                {11, 4, 5, 9, 8, 9, 1, 0}});
    }

    @Override
    public void render(ItemStack stack, ModelTransformationMode mode, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        DiceFacesComponent component = stack.get(DiceFacesComponent.TYPE);
        List<DiceFace> faces = component == null || component.faces().isEmpty() ? PLAIN : shownFaces(component);
        double time = time();
        for (int i = 0; i < dice.length; i++) {
            float[] die = dice[i];
            matrices.push();
            matrices.translate(die[3] / 16f, die[4] / 16f, die[5] / 16f);
            if (die[7] != 0) matrices.multiply(axis((int) die[6]).rotationDegrees(die[7]));
            matrices.translate((die[0] - die[3]) / 16f, (die[1] - die[4]) / 16f, (die[2] - die[5]) / 16f);
            drawDie(faces, i, time - i * LAG, matrices, vertexConsumers, light, overlay);
            matrices.pop();
        }
    }

    /** The faces a die rolls through: its blank faces left out (unless it only has blank ones), last result kept. */
    private DiceFacesComponent lastComponent;
    private List<DiceFace> lastShown;

    private List<DiceFace> shownFaces(DiceFacesComponent component) {
        if (component.equals(lastComponent)) return lastShown;
        List<DiceFace> shown = component.faces().stream().filter(face -> face.kind() != Kind.BLANK).toList();
        lastComponent = component;
        lastShown = shown.isEmpty() ? component.faces() : shown;
        return lastShown;
    }

    /** The world ticks with the partial tick (the time since start up with no world). */
    private static double time() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return Util.getMeasuringTimeMs() / 50.0;
        return client.world.getTime() + client.getRenderTickCounter().getTickDelta(false);
    }

    /** Die {@code index} at {@code time}: the rolls done, the one under way, each face with its current value. */
    private static void drawDie(List<DiceFace> faces, int index, double time, MatrixStack matrices,
                                VertexConsumerProvider vertexConsumers, int light, int overlay) {
        long rolls = (long) Math.floor(time / STEP);
        float progress = Easing.smoothstep(Easing.clamp01((float) (time - rolls * STEP) / ROLL));
        if (progress > 0) matrices.multiply(rollAxis(rolls, 90f * progress, ROLLING));
        matrices.multiply(ORIENTATIONS[(int) Math.floorMod(rolls, CYCLE)]);
        MatrixStack.Entry entry = matrices.peek();
        for (int face = 0; face < FACES.length; face++) {
            // How many times this face has landed underneath: it shows a new value each time
            long landings = Math.floorDiv(rolls - LANDING[face], CYCLE) + 1;
            DiceFace shown = faces.get(pick(index, face, landings, faces.size()));
            VertexConsumer consumer = vertexConsumers.getBuffer(layer(shown));
            quad(consumer, entry, FACES[face], light, overlay);
        }
    }

    /** The roll after {@code rolls} rolls: around X, then Z, then X... */
    private static Quaternionf rollAxis(long rolls, float degrees, Quaternionf into) {
        float radians = degrees * MathHelper.RADIANS_PER_DEGREE;
        return (rolls & 1) == 0 ? into.rotationX(radians) : into.rotationZ(radians);
    }

    private static RotationAxis axis(int axis) {
        return switch (axis) {
            case 0 -> RotationAxis.POSITIVE_X;
            case 1 -> RotationAxis.POSITIVE_Y;
            default -> RotationAxis.POSITIVE_Z;
        };
    }

    /** A face of the die, chosen from the die, face and landing (deterministic). */
    private static int pick(int index, int face, long landings, int count) {
        long hash = MathHelper.hashCode(index * 7 + face, (int) landings, 0xD1CE);
        return (int) Math.floorMod(hash ^ (hash >>> 29), (long) count);
    }

    private static RenderLayer layer(DiceFace face) {
        int value = MathHelper.clamp(face.value(), 0, LAYERS[0].length - 1);
        RenderLayer[] byValue = LAYERS[face.kind().ordinal()];
        RenderLayer layer = byValue[value];
        if (layer == null) {
            layer = RenderLayer.getEntityCutout(DiceEntityRenderer.getTexture(face.kind(), value));
            byValue[value] = layer;
        }
        return layer;
    }

    /** One face of the die: its normal, then the texture right and down directions. */
    private static void quad(VertexConsumer consumer, MatrixStack.Entry entry, float[] f, int light, int overlay) {
        vertex(consumer, entry, f, -1, -1, 0, 0, light, overlay);
        vertex(consumer, entry, f, -1, 1, 0, 1, light, overlay);
        vertex(consumer, entry, f, 1, 1, 1, 1, light, overlay);
        vertex(consumer, entry, f, 1, -1, 1, 0, light, overlay);
    }

    private static void vertex(VertexConsumer consumer, MatrixStack.Entry entry, float[] f, int right, int down,
                               float u, float v, int light, int overlay) {
        float x = (f[0] + f[3] * right + f[6] * down) * HALF;
        float y = (f[1] + f[4] * right + f[7] * down) * HALF;
        float z = (f[2] + f[5] * right + f[8] * down) * HALF;
        consumer.vertex(entry, x, y, z).color(0xFFFFFFFF).texture(u, v).overlay(overlay).light(light)
                .normal(entry, f[0], f[1], f[2]);
    }
}
