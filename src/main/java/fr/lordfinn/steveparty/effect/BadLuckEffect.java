package fr.lordfinn.steveparty.effect;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;

/**
 * Bad Luck from a Mistigri (crossing his path, hitting him): vanilla's Bad Luck with a black cat for an icon, which
 * players read. Same behaviour: luck −1 per level, so loot tables roll as for vanilla's.
 */
public class BadLuckEffect extends StatusEffect {
    private static final int COLOR = 0x2A1638;

    public BadLuckEffect() {
        super(StatusEffectCategory.HARMFUL, COLOR);
        addAttributeModifier(EntityAttributes.GENERIC_LUCK, Steveparty.id("effect.bad_luck"), -1.0,
                EntityAttributeModifier.Operation.ADD_VALUE);
    }
}
