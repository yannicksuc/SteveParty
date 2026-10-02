package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.block.DragonEggBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The egg of the dragon never teleports across the border of a mini-game zone in session: it only picks places on
 * its own side (an egg of the zone stays in it, one from outside never lands in it).
 */
@Mixin(DragonEggBlock.class)
public abstract class ZoneBubbleDragonEggMixin {

    @ModifyExpressionValue(method = "teleport", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/border/WorldBorder;contains(Lnet/minecraft/util/math/BlockPos;)Z"))
    private boolean steveparty$stayOnSideOfZoneBorder(boolean inWorld, @Local(argsOnly = true) World world,
                                                      @Local(argsOnly = true) BlockPos pos, @Local(ordinal = 1) BlockPos target) {
        if (!ZoneBorder.ACTIVE || !inWorld) return inWorld;
        return !ZoneBorder.across(world, pos, target);
    }
}
