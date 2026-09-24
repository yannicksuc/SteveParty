package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.loot.ModLootTableModifiers;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
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

public class LootAndBrewingGameTests implements FabricGameTest {

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
                .getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(ModLootTableModifiers.GAME_MASTER);
        context.assertTrue(gameMaster.isIn(EnchantmentTags.TREASURE), "treasure");
        context.assertTrue(!gameMaster.isIn(EnchantmentTags.IN_ENCHANTING_TABLE), "not in enchanting table");
        context.assertTrue(!gameMaster.isIn(EnchantmentTags.TRADEABLE), "not tradeable");
        context.complete();
    }
}
