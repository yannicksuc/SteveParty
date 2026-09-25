package fr.lordfinn.steveparty.client.token;

import com.google.common.collect.MapMaker;
import fr.lordfinn.steveparty.client.mixin.ModelPartAccessor;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoCube;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.cache.object.GeoVertex;

import java.util.List;
import java.util.Map;

/**
 * Horizontal "foot anchor" of a mob model: the point, in the model's own space, equidistant between its lowest
 * limbs (centre of the XZ footprint of every vertex close to the model's lowest point, see {@link #anchorOf}: between
 * the 4 legs of a quadruped, the 2 feet of a biped, the centre of the lowest part when there is only one).
 * <p>
 * Tokens are drawn shifted by minus this anchor (model space, after the body yaw rotation, so the shift turns with
 * the body), so that the base, drawn at the entity origin, sits right under the mob's limbs. Only the X-Z plane is
 * affected: the vertical position is untouched.
 * <p>
 * Computed once per model instance (a baby model is another instance) in a neutral pose, then cached (weak keys,
 * identity): stable, no jitter while the mob walks, no allocation per frame. Render thread only.
 */
public final class TokenFootAnchor {
    /** Vertices at most this far above the lowest one count as "lowest limbs" (1.5 pixel, in model units). */
    private static final float EPSILON = 1.5F / 16.0F;
    /**
     * ...or at most this fraction of the model height above it, when that is more: short enough to leave out the
     * body and the tails (a horse's tail ends ~20% of its height above the ground), long enough to catch limbs of
     * uneven lengths (the ghast's tentacles).
     */
    private static final float BAND_FRACTION = 0.15F;

    /** Anchor of a model: horizontal offset of its limbs' centre from the model origin, in model units (blocks). */
    public record Anchor(float x, float z) {
        public static final Anchor ZERO = new Anchor(0.0F, 0.0F);

        public boolean isZero() {
            return this.x == 0.0F && this.z == 0.0F;
        }
    }

    private static final Map<Object, Anchor> CACHE = new MapMaker().weakKeys().makeMap();

    private TokenFootAnchor() {
    }

    /** Cached anchor of {@code model} (a vanilla {@link Model} or a GeckoLib {@link BakedGeoModel}), or null. */
    public static Anchor cached(Object model) {
        return CACHE.get(model);
    }

    /**
     * Anchor of a vanilla model, from the pose its parts are in right now (the caller puts it in a neutral pose
     * first). Vanilla model space is Y down: the lowest limbs have the greatest Y. Hidden parts are ignored, like
     * {@link ModelPart#render} does.
     */
    public static Anchor computeVanilla(Model model) {
        FloatArrayList points = new FloatArrayList();
        collect(model.getRootPart(), new MatrixStack(), points, new Vector3f());
        Anchor anchor = anchorOf(points, true);
        CACHE.put(model, anchor);
        return anchor;
    }

    private static void collect(ModelPart part, MatrixStack matrices, FloatArrayList points, Vector3f scratch) {
        if (!part.visible) return;
        ModelPartAccessor access = (ModelPartAccessor) (Object) part;
        matrices.push();
        part.rotate(matrices);
        if (!part.hidden) {
            Matrix4f matrix = matrices.peek().getPositionMatrix();
            for (ModelPart.Cuboid cuboid : access.steveparty$getCuboids()) {
                for (int corner = 0; corner < 8; corner++) {
                    float x = ((corner & 1) == 0 ? cuboid.minX : cuboid.maxX) / 16.0F;
                    float y = ((corner & 2) == 0 ? cuboid.minY : cuboid.maxY) / 16.0F;
                    float z = ((corner & 4) == 0 ? cuboid.minZ : cuboid.maxZ) / 16.0F;
                    add(points, matrix.transformPosition(x, y, z, scratch));
                }
            }
        }
        for (ModelPart child : access.steveparty$getChildren().values()) {
            collect(child, matrices, points, scratch);
        }
        matrices.pop();
    }

    /**
     * Anchor of a GeckoLib model in its rest pose (the bones' initial snapshots, or their current values if they
     * were never animated), following the transforms of {@code GeoEntityRenderer#renderRecursively} and
     * {@code GeoRenderer#renderCube}. GeckoLib model space is Y up: the lowest limbs have the smallest Y.
     */
    public static Anchor computeGeckoLib(BakedGeoModel model) {
        FloatArrayList points = new FloatArrayList();
        MatrixStack matrices = new MatrixStack();
        Vector3f scratch = new Vector3f();
        for (GeoBone bone : model.topLevelBones()) {
            collect(bone, matrices, points, scratch);
        }
        Anchor anchor = anchorOf(points, false);
        CACHE.put(model, anchor);
        return anchor;
    }

    private static void collect(GeoBone bone, MatrixStack matrices, FloatArrayList points, Vector3f scratch) {
        BoneSnapshot rest = bone.getInitialSnapshot();
        float posX = rest != null ? rest.getOffsetX() : bone.getPosX();
        float posY = rest != null ? rest.getOffsetY() : bone.getPosY();
        float posZ = rest != null ? rest.getOffsetZ() : bone.getPosZ();
        float rotX = rest != null ? rest.getRotX() : bone.getRotX();
        float rotY = rest != null ? rest.getRotY() : bone.getRotY();
        float rotZ = rest != null ? rest.getRotZ() : bone.getRotZ();
        float scaleX = rest != null ? rest.getScaleX() : bone.getScaleX();
        float scaleY = rest != null ? rest.getScaleY() : bone.getScaleY();
        float scaleZ = rest != null ? rest.getScaleZ() : bone.getScaleZ();
        matrices.push();
        matrices.translate(-posX / 16.0F, posY / 16.0F, posZ / 16.0F);
        matrices.translate(bone.getPivotX() / 16.0F, bone.getPivotY() / 16.0F, bone.getPivotZ() / 16.0F);
        if (rotZ != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Z.rotation(rotZ));
        if (rotY != 0.0F) matrices.multiply(RotationAxis.POSITIVE_Y.rotation(rotY));
        if (rotX != 0.0F) matrices.multiply(RotationAxis.POSITIVE_X.rotation(rotX));
        matrices.scale(scaleX, scaleY, scaleZ);
        matrices.translate(-bone.getPivotX() / 16.0F, -bone.getPivotY() / 16.0F, -bone.getPivotZ() / 16.0F);
        if (!Boolean.TRUE.equals(bone.shouldNeverRender())) {
            for (GeoCube cube : bone.getCubes()) {
                matrices.push();
                float pivotX = (float) cube.pivot().x / 16.0F;
                float pivotY = (float) cube.pivot().y / 16.0F;
                float pivotZ = (float) cube.pivot().z / 16.0F;
                matrices.translate(pivotX, pivotY, pivotZ);
                matrices.multiply(new Quaternionf().rotationXYZ(0.0F, 0.0F, (float) cube.rotation().z));
                matrices.multiply(new Quaternionf().rotationXYZ(0.0F, (float) cube.rotation().y, 0.0F));
                matrices.multiply(new Quaternionf().rotationXYZ((float) cube.rotation().x, 0.0F, 0.0F));
                matrices.translate(-pivotX, -pivotY, -pivotZ);
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                for (GeoQuad quad : cube.quads()) {
                    if (quad == null) continue;
                    for (GeoVertex vertex : quad.vertices()) {
                        Vector3f position = vertex.position();
                        add(points, matrix.transformPosition(position.x(), position.y(), position.z(), scratch));
                    }
                }
                matrices.pop();
            }
        }
        List<GeoBone> children = bone.getChildBones();
        for (GeoBone child : children) {
            collect(child, matrices, points, scratch);
        }
        matrices.pop();
    }

    private static void add(FloatArrayList points, Vector3f point) {
        points.add(point.x);
        points.add(point.y);
        points.add(point.z);
    }

    /**
     * Centre of the XZ bounding box of the points close to the lowest one ({@code yDown}: the lowest point has the
     * greatest Y): within {@link #EPSILON}, or {@link #BAND_FRACTION} of the model height if that is more (so that
     * limbs of slightly different lengths, like the ghast's tentacles, all count).
     */
    static Anchor anchorOf(FloatArrayList points, boolean yDown) {
        int size = points.size();
        if (size < 3) return Anchor.ZERO;
        float lowest = points.getFloat(1);
        float highest = lowest;
        for (int i = 4; i < size; i += 3) {
            float y = points.getFloat(i);
            lowest = yDown ? Math.max(lowest, y) : Math.min(lowest, y);
            highest = yDown ? Math.min(highest, y) : Math.max(highest, y);
        }
        float band = Math.max(EPSILON, Math.abs(highest - lowest) * BAND_FRACTION);
        float minX = Float.POSITIVE_INFINITY, maxX = Float.NEGATIVE_INFINITY;
        float minZ = Float.POSITIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < size; i += 3) {
            float y = points.getFloat(i + 1);
            if (Math.abs(y - lowest) > band) continue;
            float x = points.getFloat(i);
            float z = points.getFloat(i + 2);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }
        float x = (minX + maxX) * 0.5F;
        float z = (minZ + maxZ) * 0.5F;
        // Snap tiny offsets (float noise of symmetric models) to 0
        if (Math.abs(x) < 1.0E-4F) x = 0.0F;
        if (Math.abs(z) < 1.0E-4F) z = 0.0F;
        return x == 0.0F && z == 0.0F ? Anchor.ZERO : new Anchor(x, z);
    }
}
