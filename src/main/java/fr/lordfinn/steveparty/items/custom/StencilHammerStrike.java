package fr.lordfinn.steveparty.items.custom;

import fr.lordfinn.steveparty.payloads.custom.StencilHammerStrikePayload;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * A Stencil Hammer strike: the paint is applied at once by the server (as before), and every client plays the strike
 * around it: the swing (wind-up, then a hard smash), the impact on the surface (thud, splash of paint, tiny camera
 * shake for the striker), and the stencil pattern stamped from the point that was hit.
 * <p>
 * The striking player plays it on its own client as soon as it clicks (through {@link #localStrike}); the server sends
 * it to the other players around. The hammer then cools down for the length of the swing.
 */
public final class StencilHammerStrike {
    /** Whole strike, in ticks: wind-up, smash, impact at {@link #IMPACT_TICK}, recovery. */
    public static final int DURATION = 11;
    public static final int IMPACT_TICK = 5;
    /** No new strike before this one has landed and mostly recovered. */
    public static final int COOLDOWN = 9;

    /** Set by the client: plays the local player's own strike without waiting for the server. */
    public static @Nullable Consumer<StencilHammerStrikePayload> localStrike;

    private StencilHammerStrike() {
    }

    public static boolean isCoolingDown(PlayerEntity player, Hand hand) {
        return player.getItemCooldownManager().isCoolingDown(player.getStackInHand(hand));
    }

    /**
     * Plays a strike of {@code player}'s hammer (in {@code hand}) on the {@code side} face, landing at {@code hit};
     * {@code canvasPos} is the block whose symbol is stamped. Called on both sides once the paint is known to apply.
     */
    public static void strike(World world, PlayerEntity player, Hand hand, BlockPos canvasPos, Vec3d hit, Direction side,
                              @Nullable DyeColor color) {
        player.getItemCooldownManager().set(player.getStackInHand(hand), COOLDOWN);
        StencilHammerStrikePayload payload = new StencilHammerStrikePayload(player.getId(), hand == Hand.MAIN_HAND, canvasPos,
                hit.toVector3f(), side, color == null ? -1 : color.getId());
        if (world.isClient) {
            if (localStrike != null) localStrike.accept(payload);
            return;
        }
        if (!(world instanceof ServerWorld)) return;
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) ServerPlayNetworking.send(other, payload);
        }
    }
}
