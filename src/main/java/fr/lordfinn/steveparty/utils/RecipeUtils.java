package fr.lordfinn.steveparty.utils;

import net.minecraft.item.Item;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;

public class RecipeUtils {

    public static Ingredient ingredientFromTag(RegistryWrapper<Item> itemRegistry, Identifier tagId) {
        TagKey<Item> tagKey = TagKey.of(RegistryKeys.ITEM, tagId);
        return itemRegistry.getOptional(tagKey)
                .map(Ingredient::fromTag)
                .orElseThrow(() -> new IllegalArgumentException("Tag not found: " + tagId));
    }
}
