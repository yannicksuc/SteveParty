package fr.lordfinn.steveparty.entities;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.CustomizableMerchant;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
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

    public static final RegistryKey<EntityType<?>> CUSTOM_MERCHANT_ENTITY_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Identifier.of(Steveparty.MOD_ID, "customizable_merchant"));
    public static final EntityType<CustomizableMerchant> CUSTOM_MERCHANT_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Steveparty.MOD_ID, "customizable_merchant"),
            EntityType.Builder
                    .<CustomizableMerchant>create(CustomizableMerchant::new, SpawnGroup.MISC)
                    .dimensions(1f, 2f)
                    .build(CUSTOM_MERCHANT_ENTITY_KEY)
    );

    public static final RegistryKey<EntityType<?>> DIRECTION_DISPLAY_ENTITY_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Identifier.of(Steveparty.MOD_ID, "dice"));
    public static final EntityType<DirectionDisplayEntity> DIRECTION_DISPLAY_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Identifier.of(Steveparty.MOD_ID, "direction_display"),
            EntityType.Builder
                    .<DirectionDisplayEntity>create(DirectionDisplayEntity::new, SpawnGroup.MISC)
                    .dimensions(1f, 1f)
                    .build(DIRECTION_DISPLAY_ENTITY_KEY)
    );

    public static void initialize() {
        //FabricDefaultAttributeRegistry.register(ModEntities.DIRECTION_DISPLAY_ENTITY, DirectionDisplayEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.DICE_ENTITY, DiceEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.CUSTOM_MERCHANT_ENTITY, CustomizableMerchant.setAttributes());
    }
}
