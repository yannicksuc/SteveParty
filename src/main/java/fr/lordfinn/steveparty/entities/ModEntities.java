package fr.lordfinn.steveparty.entities;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.entities.custom.BoxedTraderEntity;
import fr.lordfinn.steveparty.entities.custom.DiceEntity;
import fr.lordfinn.steveparty.entities.custom.DirectionDisplayEntity;
import fr.lordfinn.steveparty.entities.custom.ForgeCoreEntity;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaStarEntity;
import fr.lordfinn.steveparty.entities.custom.PipeCarrierEntity;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartEntity;
import fr.lordfinn.steveparty.entities.custom.frousseux.FrousseuxEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronPartEntity;
import fr.lordfinn.steveparty.entities.custom.trichaudron.TrichaudronParts;
import fr.lordfinn.steveparty.entities.custom.glandouille.GlandouilleEntity;
import fr.lordfinn.steveparty.entities.custom.magpie.MagpieEntity;
import fr.lordfinn.steveparty.entities.custom.magpie.WildMagpieEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriDieEntity;
import fr.lordfinn.steveparty.entities.custom.mistigri.MistigriEntity;
import fr.lordfinn.steveparty.entities.custom.pawn.PlayerPawnEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;

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
    public static final EntityType<MulaStarEntity> MULA_STAR = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("mula_star"),
            EntityType.Builder
                    .<MulaStarEntity>create(MulaStarEntity::new, SpawnGroup.MISC)
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
    public static final EntityType<PipeCarrierEntity> PIPE_CARRIER = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("pipe_carrier"),
            EntityType.Builder
                    .<PipeCarrierEntity>create(PipeCarrierEntity::new, SpawnGroup.MISC)
                    .dimensions(0.25f, 0.25f)
                    .disableSummon()
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .trackingTickInterval(20)
                    .build(PIPE_CARRIER_KEY.getValue().toString())
    );

    /** The Glandouille, a grumpy little acorn (see GlandouilleEntity). Its hitbox scales with its variant. */
    public static final RegistryKey<EntityType<?>> GLANDOUILLE_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("glandouille"));
    public static final EntityType<GlandouilleEntity> GLANDOUILLE = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("glandouille"),
            EntityType.Builder
                    .<GlandouilleEntity>create(GlandouilleEntity::new, SpawnGroup.CREATURE)
                    .dimensions(GlandouilleEntity.MODEL_WIDTH,
                            GlandouilleEntity.MODEL_HEIGHT)
                    .eyeHeight(0.55f * GlandouilleEntity.SIZE)
                    .maxTrackingRange(10)
                    .build(GLANDOUILLE_KEY.getValue().toString())
    );

    /** The Frousseux (Wickling), a little candle ghost of the caves (see FrousseuxEntity). */
    public static final RegistryKey<EntityType<?>> FROUSSEUX_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("frousseux"));
    public static final EntityType<FrousseuxEntity> FROUSSEUX = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("frousseux"),
            EntityType.Builder
                    .<FrousseuxEntity>create(FrousseuxEntity::new, SpawnGroup.AMBIENT)
                    .dimensions(FrousseuxEntity.WIDTH,
                            FrousseuxEntity.HEIGHT)
                    .eyeHeight(0.32f)
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .build(FROUSSEUX_KEY.getValue().toString())
    );

    /** The Boomcart (Pétaroule), a grumpy living mine cart loaded with TNT (see BoomcartEntity). */
    public static final RegistryKey<EntityType<?>> BOOMCART_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("boomcart"));
    public static final EntityType<BoomcartEntity> BOOMCART = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("boomcart"),
            EntityType.Builder
                    .<BoomcartEntity>create(BoomcartEntity::new, SpawnGroup.MONSTER)
                    .dimensions(BoomcartEntity.WIDTH,
                            BoomcartEntity.HEIGHT)
                    .eyeHeight(0.45f)
                    .maxTrackingRange(10)
                    .build(BOOMCART_KEY.getValue().toString())
    );

    /** The Trichaudron (EN: Tricauldron), a huge tortoise of the Nether with a tank of lava on its back (see TrichaudronEntity). */
    public static final RegistryKey<EntityType<?>> TRICHAUDRON_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("trichaudron"));
    public static final EntityType<TrichaudronEntity> TRICHAUDRON = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("trichaudron"),
            EntityType.Builder
                    .<TrichaudronEntity>create(TrichaudronEntity::new, SpawnGroup.MONSTER)
                    .dimensions(TrichaudronEntity.WIDTH,
                            TrichaudronEntity.HEIGHT)
                    .eyeHeight(TrichaudronEntity.EYE_HEIGHT)
                    .makeFireImmune()
                    .maxTrackingRange(12)
                    .trackingTickInterval(1) // ridden, it moves server side: every tick keeps the riders smooth
                    .build(TRICHAUDRON_KEY.getValue().toString())
    );

    /** A hit box of a Trichaudron's heads and necks (see TrichaudronPartEntity): invisible, never saved. */
    public static final RegistryKey<EntityType<?>> TRICHAUDRON_PART_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("trichaudron_part"));
    public static final EntityType<TrichaudronPartEntity> TRICHAUDRON_PART = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("trichaudron_part"),
            EntityType.Builder
                    .<TrichaudronPartEntity>create(TrichaudronPartEntity::new, SpawnGroup.MISC)
                    .dimensions(TrichaudronParts.HEAD_SIZE, TrichaudronParts.HEAD_SIZE)
                    .disableSaving()
                    .disableSummon()
                    .makeFireImmune()
                    .maxTrackingRange(12)
                    .trackingTickInterval(1) // on the heads every tick: where the player aims is where it is drawn
                    .build(TRICHAUDRON_PART_KEY.getValue().toString())
    );

    /** A player turned into a pawn: a statue of the player on a token base (see PlayerPawnEntity). */
    public static final RegistryKey<EntityType<?>> PLAYER_PAWN_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("player_pawn"));
    public static final EntityType<PlayerPawnEntity> PLAYER_PAWN = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("player_pawn"),
            EntityType.Builder
                    .<PlayerPawnEntity>create(PlayerPawnEntity::new, SpawnGroup.MISC)
                    .dimensions(PlayerPawnEntity.WIDTH,
                            PlayerPawnEntity.HEIGHT)
                    .eyeHeight(PlayerPawnEntity.EYE_HEIGHT)
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .build(PLAYER_PAWN_KEY.getValue().toString())
    );

    /** The Mistigri, the big black witch's cat of bad luck (see MistigriEntity). */
    public static final RegistryKey<EntityType<?>> MISTIGRI_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("mistigri"));
    public static final EntityType<MistigriEntity> MISTIGRI = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("mistigri"),
            EntityType.Builder
                    .<MistigriEntity>create(MistigriEntity::new, SpawnGroup.CREATURE)
                    .dimensions(MistigriEntity.WIDTH,
                            MistigriEntity.HEIGHT)
                    .eyeHeight(1.2f)
                    .maxTrackingRange(10)
                    .build(MISTIGRI_KEY.getValue().toString())
    );

    /** The Mistigri's giant loaded die, a prop of his board space (see MistigriSentences). */
    public static final RegistryKey<EntityType<?>> MISTIGRI_DIE_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("mistigri_die"));
    public static final EntityType<MistigriDieEntity> MISTIGRI_DIE = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("mistigri_die"),
            EntityType.Builder
                    .<MistigriDieEntity>create(MistigriDieEntity::new, SpawnGroup.MISC)
                    .dimensions(1.5f, 1.5f)
                    .makeFireImmune()
                    .maxTrackingRange(10)
                    .trackingTickInterval(1)
                    .build(MISTIGRI_DIE_KEY.getValue().toString())
    );

    /** The Pie, the keeper of a Common pot, living on its nest by the space (see MagpieEntity). */
    public static final RegistryKey<EntityType<?>> MAGPIE_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("magpie"));
    public static final EntityType<MagpieEntity> MAGPIE = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("magpie"),
            EntityType.Builder
                    .<MagpieEntity>create(MagpieEntity::new, SpawnGroup.MISC)
                    .dimensions(0.5f, 0.6f)
                    .makeFireImmune()
                    .maxTrackingRange(8)
                    .trackingTickInterval(1)
                    .build(MAGPIE_KEY.getValue().toString())
    );

    /** A wild Pie of the woods, flying from tree to tree (see WildMagpieEntity, WildMagpieSpawns). */
    public static final RegistryKey<EntityType<?>> WILD_MAGPIE_KEY = RegistryKey.of(Registries.ENTITY_TYPE.getKey(), Steveparty.id("wild_magpie"));
    public static final EntityType<WildMagpieEntity> WILD_MAGPIE = Registry.register(Registries.ENTITY_TYPE,
            Steveparty.id("wild_magpie"),
            EntityType.Builder
                    .<WildMagpieEntity>create(WildMagpieEntity::new, SpawnGroup.CREATURE)
                    .dimensions(0.5f, 0.6f)
                    .maxTrackingRange(8)
                    .trackingTickInterval(2)
                    .build(WILD_MAGPIE_KEY.getValue().toString())
    );

    public static void initialize() {
        FabricDefaultAttributeRegistry.register(ModEntities.DICE_ENTITY, DiceEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BOXED_TRADER_ENTITY, BoxedTraderEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.MULA_ENTITY, MulaEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.GLANDOUILLE, GlandouilleEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.WILD_MAGPIE, WildMagpieEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.PLAYER_PAWN, PlayerPawnEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.FROUSSEUX, FrousseuxEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.BOOMCART, BoomcartEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.MISTIGRI, MistigriEntity.setAttributes());
        FabricDefaultAttributeRegistry.register(ModEntities.TRICHAUDRON, TrichaudronEntity.setAttributes());
    }
}
