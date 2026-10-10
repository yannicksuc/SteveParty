package fr.lordfinn.steveparty.api.registry;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

/**
 * The keys under which some content is saved in items and worlds (a dice module on a die, the power-up used in a
 * turn...): the path alone for Steve Party's own content ({@code lucky}), the whole id for an addon's
 * ({@code myaddon:sticky}).
 */
public final class ContentKeys {
    private ContentKeys() {
    }

    /** The saved key of {@code id}. */
    public static String key(Identifier id) {
        return id.getNamespace().equals(Steveparty.MOD_ID) ? id.getPath() : id.toString();
    }

    /** The id a saved key stands for ({@code lucky} is {@code steveparty:lucky}); null for a key that is no id. */
    public static @Nullable Identifier id(String key) {
        return key.indexOf(':') >= 0 ? Identifier.tryParse(key) : Identifier.tryParse(Steveparty.MOD_ID + ":" + key);
    }
}
