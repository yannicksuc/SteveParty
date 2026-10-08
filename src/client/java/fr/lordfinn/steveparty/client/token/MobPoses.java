package fr.lordfinn.steveparty.client.token;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.TokenizedEntityInterface;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Poses of the mob pawns drawn by a vanilla-style model ({@code LivingEntityRenderer}): what the server keeps is a
 * number ({@code TokenPoses}); here it becomes a set of frozen inputs fed to the mob's own model instead of its live
 * ones: where its legs are in their stride, the time its idle animations show, where it looks, its arm swing, and the
 * states its model reads (sitting, aggressive, sneaking).
 * <p>
 * Which poses a mob has: the first time a client draws a mob type as a posed pawn, every candidate pose is tried on
 * its model and only the ones that move its parts noticeably (compared to the poses kept before) are kept. The
 * candidates and their order are fixed, so every client finds the same list. A model that throws, or that no
 * candidate moves, keeps its still pose only.
 */
public final class MobPoses {
    private static final Logger LOGGER = LoggerFactory.getLogger(Steveparty.MOD_ID + "/mob-poses");

    /**
     * Frozen render inputs. {@code age} is the time its idle animations show (the same for every pawn of the type, on
     * every client: not the age it froze at); a stride period of the usual limb swing ({@code cos(limbAngle * 0.6662)})
     * is {@link #STRIDE} long.
     */
    public record Pose(float limbAngle, float limbDistance, float age, float headYaw, float headPitch, float swing,
                       boolean sitting, boolean attacking, boolean sneaking) {
    }

    private static final float STRIDE = MathHelper.TAU / 0.6662F;
    /** The still pawn (pose number 0). */
    public static final Pose STILL = new Pose(0, 0, 0, 0, 0, 0, false, false, false);
    private static final List<Pose> CANDIDATES = List.of(
            new Pose(0, 1, 0, 0, 0, 0, false, false, false),                  // stride, one leg forward
            new Pose(STRIDE / 2, 1, 0, 0, 0, 0, false, false, false),         // the other one
            new Pose(STRIDE / 4, 1, 0, 0, 0, 0, false, false, false),
            new Pose(0, 0, 0, 45, 0, 0, false, false, false),                 // looking aside
            new Pose(0, 0, 0, -45, 0, 0, false, false, false),
            new Pose(0, 0, 0, 0, -35, 0, false, false, false),                // looking up
            new Pose(0, 0, 0, 0, 35, 0, false, false, false),                 // looking down
            new Pose(0, 0, 0, 0, 0, 0, true, false, false),                   // sitting
            new Pose(0, 0, 0, 0, 0, 0, false, true, false),                   // aggressive
            new Pose(0, 0, 0, 0, 0, 0.5F, false, false, false),               // mid swing
            new Pose(0, 0, 0, 0, 0, 0, false, false, true),                   // sneaking
            new Pose(0, 0, 5, 0, 0, 0, false, false, false),                  // idle animations, at other times...
            new Pose(0, 0, 10, 0, 0, 0, false, false, false),
            new Pose(0, 0, 20, 0, 0, 0, false, false, false),
            new Pose(0, 0, 40, 0, 0, 0, false, false, false),
            new Pose(0, 1, 0, 30, -20, 0, false, true, false),                // charging
            new Pose(STRIDE / 2, 0.6F, 15, -30, 15, 0.3F, false, false, false),
            new Pose(0, 0, 0, 25, 20, 0, true, false, false));               // sitting, looking around

    /** Below these differences, two poses look the same: radians, model pixels, scale. */
    private static final float ANGLE_EPSILON = 0.2F;
    private static final float PIVOT_EPSILON = 1.0F;
    private static final float SCALE_EPSILON = 0.1F;

    private static final Map<EntityType<?>, List<Pose>> POSES = new HashMap<>();
    private static final Map<Class<?>, List<Field>> PART_FIELDS = new HashMap<>();

    /** What a pose changed on the entity for one frame, put back after it. */
    public record Frame(Pose pose, boolean sitting, boolean attacking, boolean sneaking) {
    }

    private MobPoses() {
    }

    /** The pose {@code entity} (a pawn) is drawn in by {@code model}, or null for its still pose. */
    public static @Nullable Pose of(LivingEntity entity, EntityModel<?> model) {
        int number = ((TokenizedEntityInterface) entity).steveparty$getTokenPose();
        if (number <= 0) return null;
        List<Pose> poses = POSES.computeIfAbsent(entity.getType(), type -> find(entity, model));
        Pose pose = poses.get(number % poses.size());
        return pose == STILL ? null : pose;
    }

    /** How many poses the mob type has, once known (else -1). */
    public static int count(EntityType<?> type) {
        List<Pose> poses = POSES.get(type);
        return poses == null ? -1 : poses.size();
    }

    /** Sets the states {@code pose} needs on {@code entity} for this frame; {@link #end} puts them back. */
    public static Frame begin(LivingEntity entity, Pose pose) {
        Frame frame = new Frame(pose, sitting(entity), entity instanceof MobEntity mob && mob.isAttacking(), entity.isSneaking());
        setStates(entity, pose.sitting(), pose.attacking(), pose.sneaking());
        return frame;
    }

    public static void end(LivingEntity entity, Frame frame) {
        setStates(entity, frame.sitting(), frame.attacking(), frame.sneaking());
    }

    private static boolean sitting(LivingEntity entity) {
        return entity instanceof TameableEntity tameable && tameable.isInSittingPose();
    }

    private static void setStates(LivingEntity entity, boolean sitting, boolean attacking, boolean sneaking) {
        if (entity instanceof TameableEntity tameable && tameable.isInSittingPose() != sitting) tameable.setInSittingPose(sitting);
        if (entity instanceof MobEntity mob && mob.isAttacking() != attacking) mob.setAttacking(attacking);
        if (entity.isSneaking() != sneaking) entity.setSneaking(sneaking);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Pose> find(LivingEntity entity, EntityModel<?> model) {
        List<ModelPart> parts = parts(model);
        if (parts.isEmpty()) return List.of(STILL);
        Frame before = new Frame(STILL, sitting(entity), entity instanceof MobEntity mob && mob.isAttacking(), entity.isSneaking());
        float swingBefore = model.handSwingProgress;
        List<Pose> poses = new ArrayList<>();
        List<float[]> seen = new ArrayList<>();
        try {
            poses.add(STILL);
            seen.add(sample((EntityModel) model, entity, STILL, parts));
            for (Pose candidate : CANDIDATES) {
                float[] snapshot = sample((EntityModel) model, entity, candidate, parts);
                if (seen.stream().noneMatch(other -> same(other, snapshot))) {
                    poses.add(candidate);
                    seen.add(snapshot);
                }
            }
        } catch (Throwable error) {
            LOGGER.warn("No poses for the pawns of {}: its model failed ({})", EntityType.getId(entity.getType()), error.toString());
            return List.of(STILL);
        } finally {
            end(entity, before);
            model.handSwingProgress = swingBefore;
        }
        return List.copyOf(poses);
    }

    private static float[] sample(EntityModel<LivingEntity> model, LivingEntity entity, Pose pose, List<ModelPart> parts) {
        setStates(entity, pose.sitting(), pose.attacking(), pose.sneaking());
        model.handSwingProgress = pose.swing();
        model.child = entity.isBaby();
        model.animateModel(entity, pose.limbAngle(), pose.limbDistance(), 0.0F);
        model.setAngles(entity, pose.limbAngle(), pose.limbDistance(), pose.age(), pose.headYaw(), pose.headPitch());
        float[] snapshot = new float[parts.size() * 10];
        for (int i = 0; i < parts.size(); i++) {
            ModelPart part = parts.get(i);
            int at = i * 10;
            snapshot[at] = part.pivotX;
            snapshot[at + 1] = part.pivotY;
            snapshot[at + 2] = part.pivotZ;
            snapshot[at + 3] = part.pitch;
            snapshot[at + 4] = part.yaw;
            snapshot[at + 5] = part.roll;
            snapshot[at + 6] = part.xScale;
            snapshot[at + 7] = part.yScale;
            snapshot[at + 8] = part.zScale;
            snapshot[at + 9] = part.visible ? 1 : 0;
        }
        return snapshot;
    }

    private static boolean same(float[] a, float[] b) {
        for (int at = 0; at < a.length; at += 10) {
            for (int i = 0; i < 3; i++) if (Math.abs(a[at + i] - b[at + i]) > PIVOT_EPSILON) return false;
            for (int i = 3; i < 6; i++) if (Math.abs(MathHelper.wrapDegrees((a[at + i] - b[at + i]) * MathHelper.DEGREES_PER_RADIAN))
                    * MathHelper.RADIANS_PER_DEGREE > ANGLE_EPSILON) return false;
            for (int i = 6; i < 9; i++) if (Math.abs(a[at + i] - b[at + i]) > SCALE_EPSILON) return false;
            if (a[at + 9] != b[at + 9]) return false;
        }
        return true;
    }

    /** Every part of the model: the ones it keeps in fields (any class of its hierarchy), and all their children. */
    private static List<ModelPart> parts(Model model) {
        Set<ModelPart> parts = Collections.newSetFromMap(new IdentityHashMap<>());
        List<ModelPart> ordered = new ArrayList<>();
        for (Field field : partFields(model.getClass())) {
            try {
                if (field.get(model) instanceof ModelPart root) {
                    root.traverse().forEach(part -> {
                        if (parts.add(part)) ordered.add(part);
                    });
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // a field it won't give: its parts are probably reachable from another one
            }
        }
        return ordered;
    }

    private static List<Field> partFields(Class<?> modelClass) {
        return PART_FIELDS.computeIfAbsent(modelClass, type -> {
            List<Field> fields = new ArrayList<>();
            for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field field : c.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !ModelPart.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true);
                        fields.add(field);
                    } catch (RuntimeException ignored) {
                        // not reachable: skipped
                    }
                }
            }
            return fields;
        });
    }

    /** Forgets the poses found (resources reloaded: the models may have changed). */
    public static void clear() {
        POSES.clear();
        PART_FIELDS.clear();
    }
}
