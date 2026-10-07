package fr.lordfinn.steveparty.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import fr.lordfinn.steveparty.stencil.StencilUnlocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.List;

/**
 * {@code steveparty:random_stencil_pattern}: cuts a random pattern of the stencil library in the stencil, one to
 * find in chests (never a free one). Half of the time, when the chest is opened by a player, one they have not
 * unlocked yet, so the last ones do not take forever to find.
 */
public class RandomStencilPatternLootFunction extends ConditionalLootFunction {
    public static final MapCodec<RandomStencilPatternLootFunction> CODEC = RecordCodecBuilder.mapCodec(
            instance -> addConditionsField(instance).apply(instance, RandomStencilPatternLootFunction::new));
    public static final LootFunctionType<RandomStencilPatternLootFunction> TYPE = Registry.register(
            Registries.LOOT_FUNCTION_TYPE, Steveparty.id("random_stencil_pattern"), new LootFunctionType<>(CODEC));

    protected RandomStencilPatternLootFunction(List<LootCondition> conditions) {
        super(conditions);
    }

    public static ConditionalLootFunction.Builder<?> builder() {
        return builder(RandomStencilPatternLootFunction::new);
    }

    @Override
    public LootFunctionType<RandomStencilPatternLootFunction> getType() {
        return TYPE;
    }

    @Override
    protected ItemStack process(ItemStack stack, LootContext context) {
        if (stack.getItem() instanceof StencilItem) {
            StencilItem.setShape(pick(context).shape(), stack);
        }
        return stack;
    }

    private static StencilPatterns.Pattern pick(LootContext context) {
        if (context.get(LootContextParameters.THIS_ENTITY) instanceof PlayerEntity player && context.getRandom().nextBoolean()) {
            StencilUnlocks unlocks = StencilUnlocks.of(player);
            List<StencilPatterns.Pattern> missing = StencilPatterns.lockable().stream().filter(p -> !unlocks.isUnlocked(p)).toList();
            if (!missing.isEmpty()) return missing.get(context.getRandom().nextInt(missing.size()));
        }
        return StencilPatterns.random(context.getRandom());
    }

    public static void initialize() {
    }
}
