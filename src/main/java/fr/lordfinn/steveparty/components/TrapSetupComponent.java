package fr.lordfinn.steveparty.components;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.powerups.effects.TrapKind;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Uuids;

import java.util.UUID;

/**
 * A signed Trap ({@link ModComponents#TRAP_SETUP}), like a written book: what it does once sprung and who signed it.
 * It can't be changed any more. A Trap without it is unsigned: it steals {@link TrapKind#COINS} coins
 * ({@link #DEFAULT}).
 */
public record TrapSetupComponent(TrapKind kind, int amount, String signer, UUID signerId) {
    public static final Codec<TrapSetupComponent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TrapKind.CODEC.fieldOf("kind").forGetter(TrapSetupComponent::kind),
            Codec.INT.optionalFieldOf("amount", 0).forGetter(TrapSetupComponent::amount),
            Codec.STRING.fieldOf("signer").forGetter(TrapSetupComponent::signer),
            Uuids.CODEC.fieldOf("signer_id").forGetter(TrapSetupComponent::signerId)
    ).apply(instance, TrapSetupComponent::new));

    /** What an unsigned Trap does. */
    public static final Effect DEFAULT = new Effect(TrapKind.COINS, TrapKind.COINS.defaultAmount);

    /** What a Trap does: its kind and its amount (coins, spaces; 0 for the others), always within its bounds. */
    public record Effect(TrapKind kind, int amount) {
        public Effect {
            amount = kind.clamp(amount);
        }

        /** What it does, in a few words (« steals 10 coins »): tooltips, the announcement, the setup screen. */
        public MutableText describe() {
            return Text.translatable("powerup.steveparty.trap.effect." + kind.id(), amount);
        }
    }

    public Effect effect() {
        return new Effect(kind, amount);
    }

    /** What the Trap {@code stack} does: its signed effect, else the default one. */
    public static Effect effectOf(ItemStack stack) {
        TrapSetupComponent setup = stack.get(ModComponents.TRAP_SETUP);
        return setup == null ? DEFAULT : setup.effect();
    }

    public static boolean isSigned(ItemStack stack) {
        return stack.contains(ModComponents.TRAP_SETUP);
    }
}
