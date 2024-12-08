package fr.lordfinn.steveparty.entities;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final RegistryKey<EntityType<?>> DICE_ENTITY_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Identifier.of(Steveparty.MOD_ID, "dice"));
    public static final EntityType<DiceEntity> DICE_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Steveparty.MOD_ID, "dice"),
            EntityType.Builder
                    .<DiceEntity>create(DiceEntity::new, SpawnGroup.MISC)
                    .dimensions(1f, 1f)
                    .build(DICE_ENTITY_KEY)
    );

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(ModEntities.DICE_ENTITY, DiceEntity.setAttributes());
    }
}
