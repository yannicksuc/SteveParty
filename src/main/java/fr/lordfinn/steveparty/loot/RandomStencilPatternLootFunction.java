package fr.lordfinn.steveparty.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.items.custom.StencilItem;
import fr.lordfinn.steveparty.stencil.StencilPatterns;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.function.ConditionalLootFunction;
import net.minecraft.loot.function.LootFunctionType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

import java.util.List;

/** {@code steveparty:random_stencil_pattern}: cuts a random pattern of the stencil library in the stencil. */
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
            StencilItem.setShape(StencilPatterns.random(context.getRandom()).shape(), stack);
        }
        return stack;
    }

    public static void initialize() {
    }
}
