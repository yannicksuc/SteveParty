package fr.lordfinn.steveparty.registry;

import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Old registry ids that now point to renamed entries, so saved worlds (chunk palettes, inventories) keep their
 * blocks and items. Filled while registering, read-only afterwards. Used by {@code SimpleRegistryAliasMixin}.
 */
public final class RegistryAliases {
    private static final Map<Identifier, Identifier> ALIASES = new HashMap<>();

    private RegistryAliases() {
    }

    public static void add(Identifier oldId, Identifier newId) {
        ALIASES.put(oldId, newId);
    }

    public static Identifier resolve(Identifier id) {
        return id == null ? null : ALIASES.getOrDefault(id, id);
    }
}
