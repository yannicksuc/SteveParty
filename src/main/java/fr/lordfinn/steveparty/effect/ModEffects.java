package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

public class ModEffects {
    public static final RegistryEntry<StatusEffect> SQUISHED = register("squished", new SquishEffect());

    public static void initialize() {
    }
    @SuppressWarnings("SameParameterValue")
    private static RegistryEntry<StatusEffect> register(String id, StatusEffect statusEffect) {
        return Registry.registerReference(Registries.STATUS_EFFECT, Identifier.of(Steveparty.MOD_ID, id), statusEffect);
    }
}
