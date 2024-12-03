package fr.lordfinn.steveparty.components;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.component.ComponentType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModComponents {
    public static final ComponentType<TileBehaviorComponent> TILE_BEHAVIOR_COMPONENT = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Steveparty.MOD_ID, "tile-behavior-component"),
            ComponentType.<TileBehaviorComponent>builder().codec(TileBehaviorComponent.CODEC).build()
    );
    public static void initialize() {
        Steveparty.LOGGER.info("Registering {} components", Steveparty.MOD_ID);
    }
}
