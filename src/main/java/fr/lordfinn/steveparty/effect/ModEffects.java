package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.potion.Potions;
import net.minecraft.recipe.BrewingRecipeRegistry;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.resource.featuretoggle.FeatureSet;
import net.minecraft.util.Identifier;

import static net.minecraft.potion.Potions.LUCK;

public class ModEffects {
    public static final RegistryEntry<StatusEffect> SQUISHED = register("squished", new SquishEffect());

    public static void initialize() {
        BrewingRecipeRegistry.Builder builder = new BrewingRecipeRegistry.Builder(FeatureSet.empty());
        builder.registerPotionRecipe(Potions.AWKWARD, Items.RABBIT_FOOT, LUCK);
        builder.registerPotionRecipe(LUCK, Items.GUNPOWDER, LUCK);
    }

    @SuppressWarnings("SameParameterValue")
    private static RegistryEntry<StatusEffect> register(String id, StatusEffect statusEffect) {
        return Registry.registerReference(Registries.STATUS_EFFECT, Identifier.of(Steveparty.MOD_ID, id), statusEffect);
    }
}
