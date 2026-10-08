package fr.lordfinn.steveparty.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import fr.lordfinn.steveparty.entities.custom.boomcart.BoomcartEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.DetectorRailBlock;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Detector rails see a Boomcart on them as they see a minecart (it rolls on rails like one): in the same box, the
 * vanilla minecart's detection box. Their comparator output stays the minecarts' only.
 */
@Mixin(DetectorRailBlock.class)
public abstract class DetectorRailBoomcartMixin {
    @ModifyExpressionValue(method = "updatePoweredStatus",
            at = @At(value = "INVOKE", target = "Ljava/util/List;isEmpty()Z"))
    private boolean steveparty$seeBoomcarts(boolean noCart, World world, BlockPos pos, BlockState state) {
        if (!noCart) return false;
        Box box = new Box(pos.getX() + 0.2, pos.getY(), pos.getZ() + 0.2,
                pos.getX() + 0.8, pos.getY() + 0.8, pos.getZ() + 0.8);
        return world.getEntitiesByClass(BoomcartEntity.class, box, Entity::isAlive).isEmpty();
    }
}
