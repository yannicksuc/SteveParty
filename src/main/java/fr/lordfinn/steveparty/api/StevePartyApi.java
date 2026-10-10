package fr.lordfinn.steveparty.api;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/**
 * The public entry of Steve Party for addons: its mod id, its entrypoints, and where its registries
 * ({@link StevePartyRegistries}) and events ({@code fr.lordfinn.steveparty.api.event}) are.
 * <p>
 * Everything an addon needs is in {@code fr.lordfinn.steveparty.api}; the rest of Steve Party's code may change
 * without notice.
 */
public final class StevePartyApi {
    /** Steve Party's mod id (the namespace of its content). */
    public static final String MOD_ID = Steveparty.MOD_ID;
    /** The entrypoint of the addons, both sides ({@link StevePartyAddon}). */
    public static final String ENTRYPOINT = "steveparty";
    /** The client entrypoint of the addons ({@code fr.lordfinn.steveparty.api.client.StevePartyClientAddon}). */
    public static final String CLIENT_ENTRYPOINT = "steveparty:client";

    private StevePartyApi() {
    }

    /** An id in Steve Party's namespace. */
    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    /**
     * Calls the entrypoints {@code name} of every mod, one after the other: one that fails is logged with its mod
     * and does not stop the others. Steve Party calls it itself, once, at the end of its initialization.
     */
    public static <T> void invokeEntrypoints(String name, Class<T> type, Consumer<T> call) {
        for (EntrypointContainer<T> container : FabricLoader.getInstance().getEntrypointContainers(name, type)) {
            String mod = container.getProvider().getMetadata().getId();
            try {
                call.accept(container.getEntrypoint());
                Steveparty.LOGGER.info("Steve Party addon loaded: {}", mod);
            } catch (Throwable e) {
                Steveparty.LOGGER.error("Steve Party addon {} failed to initialize", mod, e);
            }
        }
    }
}
