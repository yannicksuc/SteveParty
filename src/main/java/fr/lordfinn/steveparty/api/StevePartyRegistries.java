package fr.lordfinn.steveparty.api;

import fr.lordfinn.steveparty.api.registry.IdRegistry;

/**
 * The registries of Steve Party's game content, keyed by {@link net.minecraft.util.Identifier}
 * ({@link IdRegistry}). Steve Party fills them with its own content during its initialization; an addon adds its own
 * from its {@link StevePartyAddon} entrypoint, under its own namespace.
 */
public final class StevePartyRegistries {
    private StevePartyRegistries() {
    }
}
