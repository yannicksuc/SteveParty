package fr.lordfinn.steveparty.entities.custom.bubble;

import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.jetbrains.annotations.Nullable;

/**
 * A lead used in the air ties the bubble the player looks at from afar, up to {@link BubbleRiding#LEAD_REACH} blocks
 * (one floating over its Bubbler is out of a hand's reach), never through a wall. Both sides work it out the same way:
 * the client swings the arm, the server ties.
 */
public final class BubbleLeads {
    private BubbleLeads() {
    }

    public static void initialize() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.LEAD) || player.isSpectator()) return TypedActionResult.pass(stack);
            BubbleEntity bubble = aimed(player);
            if (bubble == null) return TypedActionResult.pass(stack);
            if (!world.isClient) {
                bubble.attachLeash(player, true);
                stack.decrementUnlessCreative(1, player);
            }
            return TypedActionResult.success(stack, world.isClient);
        });
    }

    /** The bubble {@code player} looks at within {@link BubbleRiding#LEAD_REACH} blocks that a lead can tie, or null. */
    public static @Nullable BubbleEntity aimed(PlayerEntity player) {
        Vec3d eyes = player.getEyePos();
        Vec3d end = eyes.add(player.getRotationVec(1.0f).multiply(BubbleRiding.LEAD_REACH));
        HitResult block = player.getWorld().raycast(new RaycastContext(eyes, end, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        if (block.getType() != HitResult.Type.MISS) end = block.getPos();
        Box around = player.getBoundingBox().stretch(end.subtract(eyes)).expand(1.0);
        EntityHitResult hit = ProjectileUtil.raycast(player, eyes, end, around,
                entity -> entity instanceof BubbleEntity bubble && !bubble.hasPassenger(player) && bubble.canLeashAttachTo()
                        && bubble.getLeashHolder() != player, eyes.squaredDistanceTo(end));
        return hit != null && hit.getEntity() instanceof BubbleEntity bubble ? bubble : null;
    }
}
