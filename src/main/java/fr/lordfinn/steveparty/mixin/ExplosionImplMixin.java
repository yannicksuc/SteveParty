package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.persistent_state.ShopProtection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.explosion.Explosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Explosions don't destroy the blocks of an owned shop (their contents would be scattered): see {@link ShopProtection}.
 * 1.21.1: the blocks are filtered in {@code collectBlocksAndDamageEntities}, right after they are collected.
 */
@Mixin(Explosion.class)
public abstract class ExplosionImplMixin {
    @Shadow
    @Final
    private World world;
    @Shadow
    @Final
    private ObjectArrayList<BlockPos> affectedBlocks;

    @Inject(method = "collectBlocksAndDamageEntities", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;addAll(Ljava/util/Collection;)Z", shift = At.Shift.AFTER))
    private void steveparty$keepShopBlocks(CallbackInfo ci) {
        if (world.isClient) return;
        affectedBlocks.removeIf(pos -> ShopProtection.isProtected(world, pos));
    }
}
