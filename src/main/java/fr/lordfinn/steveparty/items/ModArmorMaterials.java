package fr.lordfinn.steveparty.items;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.BandanaItem;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;

import java.util.List;
import java.util.Map;

/** The mod's armour materials (registry entries, referenced by the armour items of {@link ModItems}). */
public final class ModArmorMaterials {
    /**
     * Bandana: no armour, a soft cloth sound. Its layer is the teal one; the colour of each stack is drawn by the
     * client armour renderer (textures/models/armor/bandana_&lt;colour&gt;_layer_1.png, see {@link BandanaItem#textureLayer}).
     */
    public static final RegistryEntry<ArmorMaterial> BANDANA = register("bandana", new ArmorMaterial(
            Map.of(ArmorItem.Type.HELMET, 0),
            0,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
            () -> Ingredient.EMPTY,
            List.of(new ArmorMaterial.Layer(BandanaItem.textureLayer(0))),
            0.0f,
            0.0f));

    /** Triple Jump Shoes (drawn by GeckoLib): 3 armour, toughness 1, enchantability 25, repaired with leather. */
    public static final RegistryEntry<ArmorMaterial> TRIPLE_JUMP_SHOES = register("triple_jump_shoes", new ArmorMaterial(
            Map.of(ArmorItem.Type.BOOTS, 3),
            25,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER,
            () -> Ingredient.ofItems(Items.LEATHER),
            List.of(new ArmorMaterial.Layer(Steveparty.id("triple_jump_shoes"))),
            1.0f,
            0.0f));

    private ModArmorMaterials() {
    }

    private static RegistryEntry<ArmorMaterial> register(String id, ArmorMaterial material) {
        return Registry.registerReference(Registries.ARMOR_MATERIAL, Steveparty.id(id), material);
    }
}
