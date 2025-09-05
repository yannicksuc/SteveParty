package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.Steveparty;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShearsItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(ShearsItem.class)
public abstract class ShearsItemMixin {
    @Inject(method = "postMine", at = @At("HEAD"), cancellable = true)
    public void allowCustomBlocks(ItemStack stack, World world, BlockState state, BlockPos pos, LivingEntity miner, CallbackInfoReturnable<Boolean> cir) {
        if (state.streamTags().anyMatch(tag -> tag.equals(TagKey.of(RegistryKeys.BLOCK, Steveparty.id("switchable"))))) {
            if (!world.isClient && !state.isIn(BlockTags.FIRE)) {
                stack.damage(1, miner, EquipmentSlot.MAINHAND);
            }
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "createToolComponent", at = @At("RETURN"), cancellable = true)
    private static void addCustomBlocksToToolComponent(CallbackInfoReturnable<ToolComponent> cir) {
        ToolComponent original = cir.getReturnValue();

        RegistryEntryLookup<Block> lookup = Registries.createEntryLookup(Registries.BLOCK);

        RegistryEntryList<Block> switchableBlocks = lookup.getOrThrow(TagKey.of(RegistryKeys.BLOCK, Steveparty.id("switchable")));

        ToolComponent newComp = new ToolComponent(
                Stream.concat(
                        original.rules().stream(),
                        Stream.of(ToolComponent.Rule.of(switchableBlocks, 6.0f))
                ).toList(),
                original.defaultMiningSpeed(),
                original.damagePerBlock()
        );

        cir.setReturnValue(newComp);
    }
}
