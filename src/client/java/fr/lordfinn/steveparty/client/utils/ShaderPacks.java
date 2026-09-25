package fr.lordfinn.steveparty.client.utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether a shader pack is in use (Iris, looked up by reflection: Iris is not a dependency of the mod). Some glowing
 * effects need another render layer with a shader pack, to be composited in front of the clouds.
 */
public final class ShaderPacks {
    private static final MethodHandle IN_USE;
    private static final Object API;

    static {
        MethodHandle inUse = null;
        Object api = null;
        try {
            Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            api = apiClass.getMethod("getInstance").invoke(null);
            inUse = MethodHandles.publicLookup().findVirtual(apiClass, "isShaderPackInUse", MethodType.methodType(boolean.class));
        } catch (Throwable ignored) {
            // No Iris: never a shader pack
        }
        IN_USE = inUse;
        API = api;
    }

    private ShaderPacks() {
    }

    public static boolean inUse() {
        if (IN_USE == null) return false;
        try {
            return (boolean) IN_USE.invoke(API);
        } catch (Throwable e) {
            return false;
        }
    }
}
