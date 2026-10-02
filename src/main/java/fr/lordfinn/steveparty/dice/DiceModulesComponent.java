package fr.lordfinn.steveparty.dice;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * The modules of a die ({@link DiceModule}): how many of each it carries, by module id (sorted, so the same modules
 * always give stackable dice). Ids no module is registered for are kept but do nothing.
 */
public record DiceModulesComponent(Map<String, Integer> counts) {
    public static final Codec<DiceModulesComponent> CODEC = Codec.unboundedMap(Codec.STRING, Codec.intRange(1, 64))
            .xmap(DiceModulesComponent::new, DiceModulesComponent::counts);

    public static final ComponentType<DiceModulesComponent> TYPE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Steveparty.id("dice-modules"),
            ComponentType.<DiceModulesComponent>builder().codec(CODEC).build());

    public DiceModulesComponent {
        counts = Collections.unmodifiableMap(new TreeMap<>(counts));
    }

    /** Forces the class (and so the component type) to be registered. */
    public static void initialize() {
        // Class loading registers TYPE
    }
}
