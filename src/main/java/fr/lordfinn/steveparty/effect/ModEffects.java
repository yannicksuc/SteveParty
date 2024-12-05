package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModEffects {
    public static final StatusEffect SQUISH = new SquishEffect();
    public static void initialize() {
        Registry.register(Registries.STATUS_EFFECT, Identifier.of(Steveparty.MOD_ID, "squish"), SQUISH);
    }
}
