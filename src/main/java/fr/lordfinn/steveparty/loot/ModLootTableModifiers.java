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

/**
 * The Game Master enchantment is a treasure: never at the enchanting table nor from librarians,
 * only found in woodland mansions or dropped by evokers and illusioners.
 */
public final class ModLootTableModifiers {
    public static final RegistryKey<Enchantment> GAME_MASTER = RegistryKey.of(RegistryKeys.ENCHANTMENT, Steveparty.id("game_master"));

    /** Per woodland mansion chest. */
    private static final float MANSION_CHEST_CHANCE = 0.25F;
    /** Evokers are common (raids): keep it rare. */
    private static final float EVOKER_CHANCE = 0.05F;
    /** Illusioners are rare (never spawn naturally in vanilla survival). */
    private static final float ILLUSIONER_CHANCE = 0.25F;

    private ModLootTableModifiers() {
    }

    public static void initialize() {
        RegistryKey<LootTable> evoker = EntityType.EVOKER.getLootTableKey().orElse(null);
        RegistryKey<LootTable> illusioner = EntityType.ILLUSIONER.getLootTableKey().orElse(null);

        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {
            if (!source.isBuiltin()) return; // leave datapack-replaced tables alone
            if (key.equals(LootTables.WOODLAND_MANSION_CHEST)) {
                tableBuilder.pool(gameMasterBookPool(registries, MANSION_CHEST_CHANCE, false));
            } else if (key.equals(evoker)) {
                tableBuilder.pool(gameMasterBookPool(registries, EVOKER_CHANCE, true));
            } else if (key.equals(illusioner)) {
                tableBuilder.pool(gameMasterBookPool(registries, ILLUSIONER_CHANCE, true));
            }
        });
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
