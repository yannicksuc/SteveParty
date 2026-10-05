package fr.lordfinn.steveparty.mixin;

import fr.lordfinn.steveparty.minigame.zone.ZoneBorder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * A participant of a mini-game session is not teleported out of its zone, nor anyone else into it (ender pearl,
 * command, another dimension...), unless the mod does it itself ({@link ZoneBorder#blocksTeleport}). And what a
 * player does on its own tick (an item it finishes using) stays on its side of the border.
 */
@Mixin(value = ServerPlayerEntity.class, priority = 2000)
public abstract class ZoneBubbleServerPlayerMixin {

    @Inject(method = "teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/entity/Entity;",
            at = @At("HEAD"), cancellable = true)
    private void steveparty$noTeleportAcrossZoneBorder(TeleportTarget target, CallbackInfoReturnable<Entity> cir) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksTeleport((ServerPlayerEntity) (Object) this, target)) cir.setReturnValue(null);
    }

    /**
     * 1.21.1: a teleport in the same world (a command...) does not go through {@code teleportTo}, judged here. A relative
     * move is judged where it ends (the player is sent back if need be); another world goes through {@code teleportTo}.
     */
    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FF)Z", at = @At("HEAD"), cancellable = true)
    private void steveparty$noSameWorldTeleportAcrossZoneBorder(ServerWorld world, double x, double y, double z, Set<PositionFlag> flags,
                                                                float yaw, float pitch, CallbackInfoReturnable<Boolean> cir) {
        if (ZoneBorder.ACTIVE && flags.isEmpty() && steveparty$blocksSameWorldTeleport(world, x, y, z, yaw, pitch)) cir.setReturnValue(false);
    }

    @Inject(method = "teleport(Lnet/minecraft/server/world/ServerWorld;DDDFF)V", at = @At("HEAD"), cancellable = true)
    private void steveparty$noSameWorldTeleportAcrossZoneBorderV(ServerWorld world, double x, double y, double z, float yaw, float pitch,
                                                                CallbackInfo ci) {
        if (ZoneBorder.ACTIVE && steveparty$blocksSameWorldTeleport(world, x, y, z, yaw, pitch)) ci.cancel();
    }

    @Unique
    private boolean steveparty$blocksSameWorldTeleport(ServerWorld world, double x, double y, double z, float yaw, float pitch) {
        ServerPlayerEntity self = (ServerPlayerEntity) (Object) this;
        if (world != self.getWorld()) return false;
        return ZoneBorder.blocksTeleport(self, new TeleportTarget(world, new Vec3d(x, y, z), Vec3d.ZERO, yaw, pitch, TeleportTarget.NO_OP));
    }

    /** The drop of a server player, which does not go through the one of {@code PlayerEntity}: see {@link ZoneBubblePlayerEntityMixin}. */
    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At("HEAD"), cancellable = true)
    private void steveparty$noSessionItemOutOfZone(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<ItemEntity> cir) {
        if (!ZoneBorder.ACTIVE) return;
        if (ZoneBorder.blocksDrop((ServerPlayerEntity) (Object) this)) cir.setReturnValue(null);
    }

    @Inject(method = "playerTick()V", at = @At("HEAD"))
    private void steveparty$enterPlayerTick(CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.enterPlayer((ServerPlayerEntity) (Object) this);
    }

    @Inject(method = "playerTick()V", at = @At("RETURN"))
    private void steveparty$exitPlayerTick(CallbackInfo ci) {
        if (ZoneBorder.ACTIVE) ZoneBorder.exit(((ServerPlayerEntity) (Object) this).getWorld());
    }
}
