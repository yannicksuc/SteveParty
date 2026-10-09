package fr.lordfinn.steveparty.client.render.geo;

import org.jetbrains.annotations.Nullable;
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
}
