package fr.lordfinn.steveparty.entities;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.entities.custom.ForgeCoreEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Identifier;

public class ModEntities {
    public static final RegistryKey<EntityType<?>> DICE_ENTITY_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("dice"));
    public static final EntityType<DiceEntity> DICE_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("dice"),
            EntityType.Builder
                    .create(DiceEntity::new, SpawnGroup.MISC)
                    .dimensions(1f, 1f)
                    // The dice keeps moving (thrown, then springing around its target): synced every tick so the
                    // client does not draw it blocks behind its real position (see DiceEntity#updateTrackedPositionAndAngles)
                    .trackingTickInterval(1)
                    .build(DICE_ENTITY_KEY.getValue().toString())
    );

    public static final RegistryKey<EntityType<?>> BOXED_TRADER_ENTITY_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("boxed_trader"));
    public static final EntityType<BoxedTraderEntity> BOXED_TRADER_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("boxed_trader"),
            EntityType.Builder
                    .create(BoxedTraderEntity::new, SpawnGroup.MISC)
                    .dimensions(1f, 1.5f)
                    .build(BOXED_TRADER_ENTITY_KEY.getValue().toString())
    );

    public static final RegistryKey<EntityType<?>> DIRECTION_DISPLAY_ENTITY_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("direction_display"));
    public static final EntityType<DirectionDisplayEntity> DIRECTION_DISPLAY_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("direction_display"),
            EntityType.Builder
                    .<DirectionDisplayEntity>create(DirectionDisplayEntity::new, SpawnGroup.MISC)
                    .dimensions(1f, 1f)
                    .build(DIRECTION_DISPLAY_ENTITY_KEY.getValue().toString())
    );

    public static final RegistryKey<EntityType<?>> MULA_ENTITY_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("mula"));
    public static final EntityType<? extends MulaEntity> MULA_ENTITY = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("mula"),
            EntityType.Builder
                    .create(MulaEntity::new, SpawnGroup.MISC)
                    // the hitbox is the body cube of the model, the eyes at the model's eyes (both scale with its size)
                    .dimensions(MulaEntity.MODEL_SIZE, MulaEntity.MODEL_SIZE)
                    .eyeHeight(MulaEntity.MODEL_EYE_HEIGHT)
                    .makeFireImmune()
                    .build(MULA_ENTITY_KEY.getValue().toString())
    );

    /** A burst Mula flying away as a shooting star (a show: never saved, its path is a formula). */
    public static final RegistryKey<EntityType<?>> MULA_STAR_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("mula_star"));
    public static final EntityType<fr.lordfinn.steveparty.entities.custom.MulaStarEntity> MULA_STAR = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("mula_star"),
            EntityType.Builder
                    .<fr.lordfinn.steveparty.entities.custom.MulaStarEntity>create(fr.lordfinn.steveparty.entities.custom.MulaStarEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .maxTrackingRange(16)
                    .trackingTickInterval(20)
                    .build(MULA_STAR_KEY.getValue().toString())
    );

    /** Hitbox of the dice forge core in the sky: hitting it blows the core up. */
    public static final RegistryKey<EntityType<?>> FORGE_CORE_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("forge_core"));
    public static final EntityType<ForgeCoreEntity> FORGE_CORE = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("forge_core"),
            EntityType.Builder
                    .<ForgeCoreEntity>create(ForgeCoreEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.5f)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .build(FORGE_CORE_KEY.getValue().toString())
    );

    /** What carries a traveller through a travel pipe (invisible, ridden). */
    public static final RegistryKey<EntityType<?>> PIPE_CARRIER_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("pipe_carrier"));
    public static final EntityType<fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity> PIPE_CARRIER = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("pipe_carrier"),
            EntityType.Builder
                    .<fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity>create(fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f)
                    .disableSummon()
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .trackingTickInterval(20)
                    .build(PIPE_CARRIER_KEY.getValue().toString())
    );

    /** The Glandouille, a grumpy little acorn (see GlandouilleEntity). Its hitbox scales with its variant. */
    public static final RegistryKey<EntityType<?>> GLANDOUILLE_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("glandouille"));
    public static final EntityType<fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity> GLANDOUILLE = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("glandouille"),
            EntityType.Builder
                    .<fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity>create(fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity::new, SpawnGroup.CREATURE)
                    .dimensions(fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity.MODEL_WIDTH,
                            fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity.MODEL_HEIGHT)
                    .eyeHeight(0.55f * fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity.SIZE)
                    .maxTrackingRange(10)
                    .build(GLANDOUILLE_KEY.getValue().toString())
    );

    public static void initialize() {
        // The Boxed Trader was the Hiding Trader: worlds saved before the rename keep their merchants and spawn eggs
        fr.lordfinn.steveparty.registry.RegistryAliases.add(Steveparty.id("hiding_trader"), Steveparty.id("boxed_trader"));
        fr.lordfinn.steveparty.registry.RegistryAliases.add(Steveparty.id("hiding_trader_spawn_egg"), Steveparty.id("boxed_trader_spawn_egg"));
        //FabricDefaultAttributeRegistry.register(ModEntities.DIRECTION_DISPLAY_ENTITY, DirectionDisplayEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.DICE_ENTITY, DiceEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BOXED_TRADER_ENTITY, BoxedTraderEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.MULA_ENTITY, MulaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.GLANDOUILLE, fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity.setAttributes());
    }
}
