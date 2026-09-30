package fr.lordfinn.steveparty.loot;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.condition.KilledByPlayerLootCondition;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetEnchantmentsLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;

import java.util.Map;

/**
 * The Game Master enchantment is a treasure: never at the enchanting table nor from librarians,
 * only found in woodland mansions or dropped by evokers and illusioners.
 * <p>
 * Stencils cut to a random pattern of the library ({@link fr.lordfinn.steveparty.stencil.StencilPatterns}) are
 * found in the chests of many structures ({@link #STENCIL_CHESTS}).
 */
public final class ModLootTableModifiers {
    public static final RegistryKey<Enchantment> GAME_MASTER = RegistryKey.of(RegistryKeys.ENCHANTMENT, Steveparty.id("game_master"));

    /** Per woodland mansion chest. */
    private static final float MANSION_CHEST_CHANCE = 0.25F;
    /** Evokers are common (raids): keep it rare. */
    private static final float EVOKER_CHANCE = 0.05F;
    /** Illusioners are rare (never spawn naturally in vanilla survival). */
    private static final float ILLUSIONER_CHANCE = 0.25F;

    /** Chest → chance to find 1 or 2 patterned stencils in it. */
    private static final Map<RegistryKey<LootTable>, Float> STENCIL_CHESTS = Map.ofEntries(
            Map.entry(LootTables.VILLAGE_PLAINS_CHEST, 0.25F),
            Map.entry(LootTables.VILLAGE_DESERT_HOUSE_CHEST, 0.25F),
            Map.entry(LootTables.VILLAGE_SAVANNA_HOUSE_CHEST, 0.25F),
            Map.entry(LootTables.VILLAGE_SNOWY_HOUSE_CHEST, 0.25F),
            Map.entry(LootTables.VILLAGE_TAIGA_HOUSE_CHEST, 0.25F),
            Map.entry(LootTables.VILLAGE_CARTOGRAPHER_CHEST, 0.5F),
            Map.entry(LootTables.VILLAGE_MASON_CHEST, 0.35F),
            Map.entry(LootTables.SIMPLE_DUNGEON_CHEST, 0.3F),
            Map.entry(LootTables.ABANDONED_MINESHAFT_CHEST, 0.3F),
            Map.entry(LootTables.DESERT_PYRAMID_CHEST, 0.35F),
            Map.entry(LootTables.JUNGLE_TEMPLE_CHEST, 0.35F),
            Map.entry(LootTables.STRONGHOLD_LIBRARY_CHEST, 0.4F),
            Map.entry(LootTables.STRONGHOLD_CORRIDOR_CHEST, 0.2F),
            Map.entry(LootTables.WOODLAND_MANSION_CHEST, 0.3F),
            Map.entry(LootTables.PILLAGER_OUTPOST_CHEST, 0.3F),
            Map.entry(LootTables.SHIPWRECK_MAP_CHEST, 0.35F),
            Map.entry(LootTables.SHIPWRECK_SUPPLY_CHEST, 0.25F),
            Map.entry(LootTables.BURIED_TREASURE_CHEST, 0.3F),
            Map.entry(LootTables.UNDERWATER_RUIN_SMALL_CHEST, 0.2F),
            Map.entry(LootTables.UNDERWATER_RUIN_BIG_CHEST, 0.3F),
            Map.entry(LootTables.IGLOO_CHEST_CHEST, 0.4F),
            Map.entry(LootTables.RUINED_PORTAL_CHEST, 0.2F),
            Map.entry(LootTables.ANCIENT_CITY_CHEST, 0.3F),
            Map.entry(LootTables.BASTION_OTHER_CHEST, 0.2F),
            Map.entry(LootTables.TRIAL_CHAMBERS_SUPPLY_CHEST, 0.25F),
            Map.entry(LootTables.TRIAL_CHAMBERS_CORRIDOR_CHEST, 0.25F));

    private ModLootTableModifiers() {
    }

    public static void initialize() {
        RegistryKey<LootTable> evoker = EntityType.EVOKER.getLootTableKey().orElse(null);
        RegistryKey<LootTable> illusioner = EntityType.ILLUSIONER.getLootTableKey().orElse(null);

        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) return; // leave datapack-replaced tables alone
            Float stencilChance = STENCIL_CHESTS.get(key);
            if (stencilChance != null) tableBuilder.pool(stencilPool(stencilChance));
            if (key.equals(LootTables.WOODLAND_MANSION_CHEST)) {
                tableBuilder.pool(gameMasterBookPool(registries, MANSION_CHEST_CHANCE, false));
            } else if (key.equals(evoker)) {
                tableBuilder.pool(gameMasterBookPool(registries, EVOKER_CHANCE, true));
            } else if (key.equals(illusioner)) {
                tableBuilder.pool(gameMasterBookPool(registries, ILLUSIONER_CHANCE, true));
            }
        });
    }

    private static LootPool.Builder stencilPool(float chance) {
        return LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1))
                .conditionally(RandomChanceLootCondition.builder(chance))
                .with(ItemEntry.builder(ModItems.STENCIL)
                        .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1, 2)))
                        .apply(RandomStencilPatternLootFunction.builder()));
    }

    private static LootPool.Builder gameMasterBookPool(RegistryWrapper.WrapperLookup registries, float chance, boolean killedByPlayer) {
        RegistryEntry<Enchantment> gameMaster = registries.getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(GAME_MASTER);
        LootPool.Builder pool = LootPool.builder()
                .rolls(ConstantLootNumberProvider.create(1))
                .conditionally(RandomChanceLootCondition.builder(chance))
                .with(ItemEntry.builder(Items.ENCHANTED_BOOK)
                        .apply(new SetEnchantmentsLootFunction.Builder()
                                .enchantment(gameMaster, ConstantLootNumberProvider.create(1))));
        if (killedByPlayer) pool.conditionally(KilledByPlayerLootCondition.builder());
        return pool;
    }
}
