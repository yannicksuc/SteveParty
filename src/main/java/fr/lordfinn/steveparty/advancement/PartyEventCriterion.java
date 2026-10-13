package fr.lordfinn.steveparty.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancement.criterion.AbstractCriterion;
import net.minecraft.entity.Entity;
import net.minecraft.loot.context.LootContext;
import net.minecraft.predicate.NumberRange;
import net.minecraft.predicate.entity.EntityPredicate;
import net.minecraft.predicate.entity.LootContextPredicate;
import net.minecraft.predicate.entity.LootContextPredicateValidator;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * {@code steveparty:event}: one trigger for every SteveParty moment an advancement listens to.
 * {@code event} names the moment, {@code value} (int range) matches its number (a height, a total, a count...),
 * {@code entity} the creature involved. Absent fields match anything.
 */
public class PartyEventCriterion extends AbstractCriterion<PartyEventCriterion.Conditions> {

    @Override
    public Codec<Conditions> getConditionsCodec() {
        return Conditions.CODEC;
    }

    public void trigger(ServerPlayerEntity player, String event, int value, @Nullable Entity entity) {
        LootContext context = entity == null ? null : EntityPredicate.createAdvancementEntityLootContext(player, entity);
        trigger(player, conditions -> conditions.matches(event, value, context));
    }

    public record Conditions(Optional<LootContextPredicate> player, String event,
                             Optional<NumberRange.IntRange> value,
                             Optional<LootContextPredicate> entity) implements AbstractCriterion.Conditions {

        public static final Codec<Conditions> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("player").forGetter(Conditions::player),
                Codec.STRING.fieldOf("event").forGetter(Conditions::event),
                NumberRange.IntRange.CODEC.optionalFieldOf("value").forGetter(Conditions::value),
                EntityPredicate.LOOT_CONTEXT_PREDICATE_CODEC.optionalFieldOf("entity").forGetter(Conditions::entity)
        ).apply(instance, Conditions::new));

        boolean matches(String firedEvent, int firedValue, @Nullable LootContext entityContext) {
            if (!event.equals(firedEvent)) return false;
            if (value.isPresent() && !value.get().test(firedValue)) return false;
            if (entity.isPresent()) return entityContext != null && entity.get().test(entityContext);
            return true;
        }

        @Override
        public void validate(LootContextPredicateValidator validator) {
            AbstractCriterion.Conditions.super.validate(validator);
            validator.validateEntityPredicate(entity, ".entity");
        }
    }
}
