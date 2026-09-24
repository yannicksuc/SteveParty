package fr.lordfinn.steveparty.client.flag;

import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether an Iris shader pack is in use, without depending on Iris at compile time. Shader packs light the world
 * themselves and turn off vanilla's block face shading: geometry that bakes that shading into its colour (like the
 * goal pole flags) must then leave it out, or it looks darker than the blocks around it.
 */
public final class ShaderPacks {
    /** {@code IrisApi.getInstance().isShaderPackInUse()}, bound; null without Iris. Called without allocating. */
    @Nullable
    private static final MethodHandle IN_USE = find();

    private ShaderPacks() {}

    @Nullable
    private static MethodHandle find() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) return null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Object instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api)).invoke();
            return lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class)).bindTo(instance);
        } catch (Throwable e) {
            return null;
        }
    }

    public static boolean inUse() {
        if (IN_USE == null) return false;
        try {
            return (boolean) IN_USE.invokeExact();
        } catch (Throwable e) {
            return false;
        }
    }
}
