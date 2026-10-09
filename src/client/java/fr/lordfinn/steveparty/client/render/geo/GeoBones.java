package fr.lordfinn.steveparty.client.render.geo;

import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import software.bernie.geckolib.animation.AnimationProcessor;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;

/** Null-safe bone tweaks: a bone missing from the model (another variant, an older file) is skipped. */
public final class GeoBones {
    private GeoBones() {
    }

    public static void hide(AnimationProcessor<?> processor, String name, boolean hidden) {
        hide(processor.getBone(name), hidden);
    }

    public static void hide(AnimationProcessor<?> processor, String[] names, boolean hidden) {
        for (String name : names) hide(processor.getBone(name), hidden);
    }

    public static void hide(BakedGeoModel model, String name, boolean hidden) {
        model.getBone(name).ifPresent(bone -> bone.setHidden(hidden));
    }

    public static void hide(BakedGeoModel model, String[] names, boolean hidden) {
        for (String name : names) hide(model, name, hidden);
    }

    /** Hides these bones for one pass, remembering in {@code was} whether each was hidden ({@link #restore}). */
    public static void hideFor(BakedGeoModel model, String[] names, boolean[] was) {
        for (int i = 0; i < names.length; i++) {
            GeoBone bone = model.getBone(names[i]).orElse(null);
            if (bone == null) continue;
            was[i] = bone.isHidden();
            bone.setHidden(true);
        }
    }

    /** Puts back what {@link #hideFor} hid. */
    public static void restore(BakedGeoModel model, String[] names, boolean[] was) {
        for (int i = 0; i < names.length; i++) {
            int index = i;
            model.getBone(names[i]).ifPresent(bone -> bone.setHidden(was[index]));
        }
    }

    public static void hide(@Nullable GeoBone bone, boolean hidden) {
        if (bone != null) bone.setHidden(hidden);
    }

    /** Hides the bone's own cubes but not its children ({@link GeoBone#setHidden} hides them too). */
    public static void hideOnlyItself(@Nullable GeoBone bone) {
        if (bone == null) return;
        bone.setHidden(true);
        bone.setChildrenHidden(false);
    }

    public static void hideOnlyItself(AnimationProcessor<?> processor, String name) {
        hideOnlyItself(processor.getBone(name));
    }

    public static void hideOnlyItself(BakedGeoModel model, String name) {
        model.getBone(name).ifPresent(GeoBones::hideOnlyItself);
    }

    public static void scale(@Nullable GeoBone bone, float x, float y, float z) {
        if (bone == null) return;
        bone.setScaleX(x);
        bone.setScaleY(y);
        bone.setScaleZ(z);
    }

    public static void scale(@Nullable GeoBone bone, float scale) {
        scale(bone, scale, scale, scale);
    }

    // ------------------------------------------------------------------ offsets on top of the animation

    private static final int RX = 0, RY = 1, RZ = 2, SX = 3, SY = 4, SZ = 5, CHANNELS = 6;
    /** Per bone and channel: the value we last wrote, and the base we wrote it on (NaN: never). */
    private static final Map<GeoBone, float[]> WRITTEN = new WeakHashMap<>();

    /**
     * A rotation added to a bone's current one, or a scale multiplied into it, without ever piling up. GeckoLib leaves a
     * bone that no running animation keys exactly as it was (and our own write marks it as changed, so it isn't even
     * eased back to its rest): adding to its current value again next frame would add to our own last offset, and the
     * offsets would add up frame after frame (a Trichaudron's shell ended up lying on its side). So when the bone still
     * holds the very value we wrote, we start again from the base we wrote it on.
     */
    public static void addRotX(@Nullable GeoBone bone, float radians) {
        if (bone != null) bone.setRotX(offset(bone, RX, bone.getRotX(), radians, false));
    }

    public static void addRotY(@Nullable GeoBone bone, float radians) {
        if (bone != null) bone.setRotY(offset(bone, RY, bone.getRotY(), radians, false));
    }

    public static void addRotZ(@Nullable GeoBone bone, float radians) {
        if (bone != null) bone.setRotZ(offset(bone, RZ, bone.getRotZ(), radians, false));
    }

    /** Multiplies the bone's x and y scale (see {@link #addRotX}: no compounding frame after frame). */
    public static void multiplyScaleXY(@Nullable GeoBone bone, float factor) {
        if (bone == null) return;
        bone.setScaleX(offset(bone, SX, bone.getScaleX(), factor, true));
        bone.setScaleY(offset(bone, SY, bone.getScaleY(), factor, true));
    }

    private static float offset(GeoBone bone, int channel, float current, float amount, boolean multiply) {
        float[] written = WRITTEN.computeIfAbsent(bone, b -> {
            float[] fresh = new float[CHANNELS * 2];
            Arrays.fill(fresh, Float.NaN);
            return fresh;
        });
        float base = written[channel] == current ? written[CHANNELS + channel] : current; // our own last write: its base
        float value = multiply ? base * amount : base + amount;
        written[channel] = value;
        written[CHANNELS + channel] = base;
        return value;
    }
}
