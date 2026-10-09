package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.items.tooltip.Tooltips;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.entities.custom.MulaFood;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.text.Text;

import java.util.Locale;

/** "Mula food: Green" under every food, seed or potion a Mula eats (MulaFood), in that Mula's colour. */
public final class MulaFoodTooltip {
    private MulaFoodTooltip() {
    }

    public static void register() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            MulaEntity.MulaVariant variant = MulaFood.eatenBy(stack);
            if (variant == null) return;
            Tooltips.tag(lines, Text.translatable("tooltip.steveparty.mula_food",
                    Text.translatable("mula.steveparty.colour." + variant.name().toLowerCase(Locale.ROOT))), variant.getGlowColor());
        });
    }
}
