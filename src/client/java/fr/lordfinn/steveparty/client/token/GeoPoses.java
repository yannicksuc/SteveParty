package fr.lordfinn.steveparty.client.token;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.TokenBase;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.animation.keyframe.BoneAnimation;
import software.bernie.geckolib.animation.keyframe.Keyframe;
import software.bernie.geckolib.animation.keyframe.KeyframeStack;
import software.bernie.geckolib.cache.GeckoLibCache;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.math.MathValue;
import software.bernie.geckolib.loading.object.BakedAnimations;
import software.bernie.geckolib.model.GeoModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Poses of the mob pawns drawn by GeckoLib (ours and any modded GeckoLib mob): a pose is a moment of one of the
 * mob's own animations, its bones frozen there (see {@code GeoModelPawnPoseMixin}). The candidates are every animation
 * of the mob (by name, so the same order everywhere) at the start, a third and two thirds of it; the moments that
 * look like one already kept are dropped. Pose number 0 is the pawn as it is drawn without a pose.
 */
public final class GeoPoses {
    private static final Logger LOGGER = LoggerFactory.getLogger(Steveparty.MOD_ID + "/geo-poses");
    private static final double[] MOMENTS = {0.0, 1.0 / 3.0, 2.0 / 3.0};
    private static final float ANGLE_EPSILON = 0.2F;
    private static final float POSITION_EPSILON = 1.0F;
    private static final float SCALE_EPSILON = 0.1F;

    /** One bone's frozen values; NaN where the moment does not set it (the bone keeps its rest value). */
    private record BonePose(String bone, float rotX, float rotY, float rotZ, float posX, float posY, float posZ,
                            float scaleX, float scaleY, float scaleZ) {
    }

    /** A moment of an animation: the frozen bones. */
    public record Pose(String animation, double tick, List<BonePose> bones) {
    }

    private static final Map<EntityType<?>, List<Pose>> POSES = new HashMap<>();

    private GeoPoses() {
    }

    /** How many poses the mob type has, the still one included, once known (else -1). */
    public static int count(EntityType<?> type) {
        List<Pose> poses = POSES.get(type);
        return poses == null ? -1 : poses.size() + 1;
    }

    /**
     * After the model animated {@code animatable} for this frame: a posed pawn's bones are set to its pose instead.
     * Never throws (a model it can't read keeps its own animation).
     */
    public static <T extends GeoAnimatable> void apply(GeoModel<T> model, T animatable) {
        if (!(animatable instanceof Entity entity) || !TokenBase.isToken(entity)) return;
        int number = ((TokenizedEntityInterface) entity).steveparty$getTokenPose();
        if (number <= 0) return;
        List<Pose> poses = POSES.computeIfAbsent(entity.getType(), type -> find(model, animatable));
        if (poses.isEmpty()) return;
        int index = number % (poses.size() + 1);
        if (index == 0) return;
        try {
            freeze(model, poses.get(index - 1));
        } catch (Throwable error) {
            LOGGER.warn("Pose of the pawn of {} not applied: {}", EntityType.getId(entity.getType()), error.toString());
        }
    }

    private static <T extends GeoAnimatable> void freeze(GeoModel<T> model, Pose pose) {
        Map<String, BonePose> byBone = new HashMap<>();
        for (BonePose bone : pose.bones()) byBone.put(bone.bone(), bone);
        for (GeoBone bone : model.getAnimationProcessor().getRegisteredBones()) {
            var rest = bone.getInitialSnapshot();
            if (rest == null) continue;
            BonePose frozen = byBone.get(bone.getName());
            bone.setRotX(rest.getRotX() + orZero(frozen == null ? Float.NaN : frozen.rotX()));
            bone.setRotY(rest.getRotY() + orZero(frozen == null ? Float.NaN : frozen.rotY()));
            bone.setRotZ(rest.getRotZ() + orZero(frozen == null ? Float.NaN : frozen.rotZ()));
            bone.setPosX(orZero(frozen == null ? Float.NaN : frozen.posX()));
            bone.setPosY(orZero(frozen == null ? Float.NaN : frozen.posY()));
            bone.setPosZ(orZero(frozen == null ? Float.NaN : frozen.posZ()));
            bone.setScaleX(orOne(frozen == null ? Float.NaN : frozen.scaleX()));
            bone.setScaleY(orOne(frozen == null ? Float.NaN : frozen.scaleY()));
            bone.setScaleZ(orOne(frozen == null ? Float.NaN : frozen.scaleZ()));
        }
    }

    private static float orZero(float value) {
        return Float.isNaN(value) ? 0 : value;
    }

    private static float orOne(float value) {
        return Float.isNaN(value) ? 1 : value;
    }

    private static <T extends GeoAnimatable> List<Pose> find(GeoModel<T> model, T animatable) {
        try {
            Identifier resource = model.getAnimationResource(animatable);
            BakedAnimations baked = resource == null ? null : GeckoLibCache.getBakedAnimations().get(resource);
            if (baked == null) return List.of();
            List<Pose> poses = new ArrayList<>();
            for (Animation animation : new TreeMap<>(baked.animations()).values()) {
                for (double moment : MOMENTS) {
                    Pose candidate = sample(animation, animation.length() * moment);
                    if (candidate.bones().isEmpty()) continue;
                    if (poses.stream().noneMatch(other -> same(other, candidate))) poses.add(candidate);
                }
            }
            return List.copyOf(poses);
        } catch (Throwable error) {
            LOGGER.warn("No poses for the pawns of {}: its animations could not be read ({})",
                    animatable instanceof Entity entity ? EntityType.getId(entity.getType()) : animatable, error.toString());
            return List.of();
        }
    }

    private static Pose sample(Animation animation, double tick) {
        List<BonePose> bones = new ArrayList<>();
        for (BoneAnimation bone : animation.boneAnimations()) {
            float[] rot = at(bone.rotationKeyFrames(), tick);
            float[] pos = at(bone.positionKeyFrames(), tick);
            float[] scale = at(bone.scaleKeyFrames(), tick);
            bones.add(new BonePose(bone.boneName(), rot[0], rot[1], rot[2], pos[0], pos[1], pos[2], scale[0], scale[1], scale[2]));
        }
        bones.sort((a, b) -> a.bone().compareTo(b.bone()));
        return new Pose(animation.name(), tick, List.copyOf(bones));
    }

    private static float[] at(KeyframeStack<Keyframe<MathValue>> stack, double tick) {
        return new float[]{at(stack.xKeyframes(), tick), at(stack.yKeyframes(), tick), at(stack.zKeyframes(), tick)};
    }

    /** The value of one axis at {@code tick}: linear between the keyframe's start and end (easings left out). */
    private static float at(List<Keyframe<MathValue>> keyframes, double tick) {
        if (keyframes == null || keyframes.isEmpty()) return Float.NaN;
        double start = 0;
        for (int i = 0; i < keyframes.size(); i++) {
            Keyframe<MathValue> keyframe = keyframes.get(i);
            double length = keyframe.length();
            if (tick <= start + length || i == keyframes.size() - 1) {
                double progress = length <= 0 ? 1 : MathHelper.clamp((tick - start) / length, 0, 1);
                return (float) MathHelper.lerp(progress, keyframe.startValue().get(), keyframe.endValue().get());
            }
            start += length;
        }
        return Float.NaN;
    }

    /** Whether two moments look alike: every bone they set within the margins. */
    private static boolean same(Pose a, Pose b) {
        Map<String, BonePose> others = new HashMap<>();
        for (BonePose bone : b.bones()) others.put(bone.bone(), bone);
        for (BonePose bone : a.bones()) {
            BonePose other = others.remove(bone.bone());
            if (other == null ? moves(bone) : !close(bone, other)) return false;
        }
        for (BonePose other : others.values()) if (moves(other)) return false;
        return true;
    }

    private static boolean moves(BonePose bone) {
        return !close(bone, new BonePose(bone.bone(), 0, 0, 0, 0, 0, 0, 1, 1, 1));
    }

    private static boolean close(BonePose a, BonePose b) {
        return close(a.rotX(), b.rotX(), 0, ANGLE_EPSILON) && close(a.rotY(), b.rotY(), 0, ANGLE_EPSILON)
                && close(a.rotZ(), b.rotZ(), 0, ANGLE_EPSILON) && close(a.posX(), b.posX(), 0, POSITION_EPSILON)
                && close(a.posY(), b.posY(), 0, POSITION_EPSILON) && close(a.posZ(), b.posZ(), 0, POSITION_EPSILON)
                && close(a.scaleX(), b.scaleX(), 1, SCALE_EPSILON) && close(a.scaleY(), b.scaleY(), 1, SCALE_EPSILON)
                && close(a.scaleZ(), b.scaleZ(), 1, SCALE_EPSILON);
    }

    /** NaN (not set) counts as {@code rest}. */
    private static boolean close(float a, float b, float rest, float epsilon) {
        return Math.abs((Float.isNaN(a) ? rest : a) - (Float.isNaN(b) ? rest : b)) <= epsilon;
    }

    public static void clear() {
        POSES.clear();
    }
}
