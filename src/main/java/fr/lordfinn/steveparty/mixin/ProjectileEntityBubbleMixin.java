package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.effect.BubbledEffect;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import fr.lordfinn.steveparty.particles.MulaSparkleEffect;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Projectiles and the Bubbler's bubbles:
 * <ul>
 *     <li>what a bubble's riders shoot or throw from inside it (arrows, tridents, snowballs, eggs, potions...) flies out
 *     through it, never hitting it ({@link BubbleEntity#letsOut}): it neither pops nor pushes it;</li>
 *     <li>such a projectile is bubbled (server, from its first tick: its owner rides a bubble and it starts in that
 *     bubble): a trail of tiny iridescent sparkles and pops follows it, and the living thing it hits, besides the usual
 *     (an arrow's damage, its fire, a potion's effects), is Bubbled ({@link BubbledEffect#PROJECTILE_TICKS}).</li>
 * </ul>
 */
@Mixin(ProjectileEntity.class)
public abstract class ProjectileEntityBubbleMixin {
    /** The iridescent tints of a bubbled projectile's trail, blue and pink. */
    @Unique
    private static final int[] TRAIL_COLORS = {0xB4DCFF, 0xFFC4EC};

    /** Whether it was shot from inside a bubble; whether its first tick looked. */
    @Unique
    private boolean steveparty$bubbled, steveparty$looked;

    @Inject(method = "canHit(Lnet/minecraft/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void steveparty$throughBubbleFromInside(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (entity instanceof BubbleEntity bubble && bubble.letsOut((ProjectileEntity) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void steveparty$bubbledTrail(CallbackInfo ci) {
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        if (!(self.getWorld() instanceof ServerWorld world)) return;
        if (!steveparty$looked) {
            steveparty$looked = true;
            Entity owner = self.getOwner();
            if (owner != null && owner.getVehicle() instanceof BubbleEntity bubble && bubble.getBoundingBox().contains(self.getPos())) {
                steveparty$bubbled = true;
            }
        }
        if (!steveparty$bubbled || self.getVelocity().lengthSquared() < 0.0025) return;
        int color = TRAIL_COLORS[(self.age >> 1) & 1];
        world.spawnParticles(new MulaSparkleEffect(color, 0.6f, MulaSparkleEffect.TWINKLE), self.getX(), self.getY(), self.getZ(),
                1, 0.05, 0.05, 0.05, 0);
        if (self.age % 3 == 0) world.spawnParticles(ParticleTypes.BUBBLE_POP, self.getX(), self.getY(), self.getZ(), 1, 0.05, 0.05, 0.05, 0);
    }

    /** A bubbled projectile hitting a living thing bubbles it, after its usual effect. */
    @Inject(method = "onCollision(Lnet/minecraft/util/hit/HitResult;)V", at = @At("TAIL"))
    private void steveparty$bubbleTheTarget(HitResult hit, CallbackInfo ci) {
        if (!steveparty$bubbled || !(hit instanceof EntityHitResult entityHit)) return;
        if (!(entityHit.getEntity() instanceof LivingEntity target) || !target.isAlive()) return;
        ProjectileEntity self = (ProjectileEntity) (Object) this;
        BubbledEffect.bubble(target, BubbledEffect.PROJECTILE_TICKS, self.getOwner() instanceof LivingEntity owner ? owner : null);
    }

    /** Bubbled, it stays so when saved. */
    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void steveparty$writeBubbled(NbtCompound nbt, CallbackInfo ci) {
        if (steveparty$bubbled) nbt.putBoolean("steveparty:bubbled", true);
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void steveparty$readBubbled(NbtCompound nbt, CallbackInfo ci) {
        steveparty$bubbled = nbt.getBoolean("steveparty:bubbled");
        steveparty$looked = true;
    }
}
