package fr.lordfinn.steveparty.gametest;

import java.util.List;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.LootTables;
import net.minecraft.loot.LootTable;
import net.minecraft.registry.RegistryKey;
import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.loot.ModLootTableModifiers;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.PotionContentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

public class LootAndBrewingGameTests implements SteveGameTest {

    /** Awkward potion + any star fragment = Luck (vanilla has no Luck recipe; rabbit foot stays Leaping). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void starFragmentBrewsLuck(TestContext context) {
        var brewing = context.getWorld().getBrewingRecipeRegistry();
        ItemStack awkward = PotionContentsComponent.createStack(Items.POTION, Potions.AWKWARD);
        ItemStack result = brewing.craft(new ItemStack(ModItems.RED_STAR_FRAGMENT), awkward);
        PotionContentsComponent contents = result.get(DataComponentTypes.POTION_CONTENTS);
        context.assertTrue(contents != null && contents.matches(Potions.LUCK), "awkward + star fragment = luck");
        ItemStack leaping = brewing.craft(new ItemStack(Items.RABBIT_FOOT), awkward);
        context.assertTrue(leaping.get(DataComponentTypes.POTION_CONTENTS).matches(Potions.LEAPING), "rabbit foot still gives leaping");
        context.complete();
    }

    /** Game Master is a treasure: not at the enchanting table, not traded by librarians. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void gameMasterIsTreasureOnly(TestContext context) {
        RegistryEntry<Enchantment> gameMaster = context.getWorld().getRegistryManager()
                .getWrapperOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(ModLootTableModifiers.GAME_MASTER);
        context.assertTrue(gameMaster.isIn(EnchantmentTags.TREASURE), "treasure");
        context.assertTrue(!gameMaster.isIn(EnchantmentTags.IN_ENCHANTING_TABLE), "not in enchanting table");
        context.assertTrue(!gameMaster.isIn(EnchantmentTags.TRADEABLE), "not tradeable");
        context.complete();
    }

    /** The Wooden Die is an uncommon find in village chests: in every house chest's table, and found in some. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void villageChestsMayHoldAWoodenDie(TestContext context) {
        for (RegistryKey<LootTable> house : List.of(LootTables.VILLAGE_PLAINS_CHEST, LootTables.VILLAGE_DESERT_HOUSE_CHEST,
                LootTables.VILLAGE_SAVANNA_HOUSE_CHEST, LootTables.VILLAGE_SNOWY_HOUSE_CHEST, LootTables.VILLAGE_TAIGA_HOUSE_CHEST)) {
            Float chance = ModLootTableModifiers.WOODEN_DIE_CHESTS.get(house);
            context.assertTrue(chance != null && chance > 0.05F && chance < 0.3F, "uncommon in " + house.getValue() + ": " + chance);
        }
        ServerWorld world = context.getWorld();
        LootTable table = world.getServer().getReloadableRegistries().getLootTable(LootTables.VILLAGE_PLAINS_CHEST);
        LootContextParameterSet parameters = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(context.getAbsolutePos(BlockPos.ORIGIN)))
                .build(LootContextTypes.CHEST);
        int chests = 400, found = 0;
        for (int seed = 0; seed < chests; seed++) {
            for (ItemStack stack : table.generateLoot(parameters, seed)) {
                if (stack.isOf(ModItems.WOODEN_DICE)) found++;
            }
        }
        context.assertTrue(found > chests / 20 && found < chests / 4, "wooden dice in " + found + " of " + chests + " plains chests");
        context.complete();
    }
}
