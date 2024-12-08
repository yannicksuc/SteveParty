package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.component.ComponentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

public class ModComponents {
    public static final ComponentType<TileBehaviorComponent> TILE_BEHAVIOR_COMPONENT = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Steveparty.MOD_ID, "tile-behavior-component"),
            ComponentType.<TileBehaviorComponent>builder().codec(TileBehaviorComponent.CODEC).build()
    );

    public static final ComponentType<MobEntityComponent> MOB_ENTITY_COMPONENT = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Steveparty.MOD_ID, "mob-entity-component"),
            ComponentType.<MobEntityComponent>builder().codec(MobEntityComponent.CODEC).build()
    );

    public static final ComponentType<BlockPos> BLOCK_POS = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Steveparty.MOD_ID, "block-pos"),
            ComponentType.<BlockPos>builder().codec(BlockPos.CODEC).build()
    );

    public static final ComponentType<Integer> ROLLING_VALUE = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Steveparty.MOD_ID, "rolling-value"),
            ComponentType.<Integer>builder().codec(Codec.INT).build()
    );

    public static final ComponentType<Boolean> IS_ROLLING = Registry.register(
            Registries.DATA_COMPONENT_TYPE,
            Identifier.of(Steveparty.MOD_ID, "is-rolling"),
            ComponentType.<Boolean>builder().codec(Codec.BOOL).build()
    );

    public static void initialize() {
        Steveparty.LOGGER.info("Registering {} components", Steveparty.MOD_ID);
    }
}
