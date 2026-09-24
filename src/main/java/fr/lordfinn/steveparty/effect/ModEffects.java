package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.entity.effect.StatusEffect;
import fr.lordfinn.steveparty.items.ModItems;
import net.minecraft.item.Item;

import java.util.List;
import net.minecraft.potion.Potions;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;

import static net.minecraft.potion.Potions.LUCK;

public class ModEffects {
    public static final RegistryEntry<StatusEffect> SQUISHED = register("squished", new SquishEffect());

    public static void initialize() {
        // Recipes must be added to the registry being built by the game, not to a throwaway builder
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            // Vanilla has the Luck potion but no brewing recipe for it (Awkward + rabbit foot is Leaping):
            // any star fragment brews it. Splash/lingering variants come from vanilla's generic recipes.
            for (Item fragment : List.of(ModItems.BLUE_STAR_FRAGMENT, ModItems.PURPLE_STAR_FRAGMENT, ModItems.RED_STAR_FRAGMENT,
                    ModItems.YELLOW_STAR_FRAGMENT, ModItems.GREEN_STAR_FRAGMENT, ModItems.BLACK_STAR_FRAGMENT)) {
                builder.registerPotionRecipe(Potions.AWKWARD, fragment, LUCK);
            }
        });
    }

    @SuppressWarnings("SameParameterValue")
    private static RegistryEntry<StatusEffect> register(String id, StatusEffect statusEffect) {
        return Registry.registerReference(Registries.STATUS_EFFECT, Steveparty.id(id), statusEffect);
    }
}
