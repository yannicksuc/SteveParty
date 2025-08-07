package fr.lordfinn.steveparty.recipes;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModRecipes {

    public static void register() {
        // Enregistrez vos types de recettes et sérialiseurs
        ModRecipeTypes.register();
        ModRecipeSerializers.register();
    }
}
