package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.client.access.BoxCostumeRenderState;
import fr.lordfinn.steveparty.client.access.StencilHammerRenderState;
import fr.lordfinn.steveparty.client.access.TelescopeRenderState;
import fr.lordfinn.steveparty.client.pipe.PipeTravellerPose;
import fr.lordfinn.steveparty.payloads.custom.SixSevenPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The « 6-7 » ({@link fr.lordfinn.steveparty.dice.SixSeven}), client side: from the start tick the server gave, the
 * dancers hold their forearms out in front and weigh « six… seven… » with their hands, in turn, for
 * {@link #DURATION} ticks; worked out from the world time each frame (no packet while it plays). Whatever the player
 * does with his hands (a swing, an item used, a pose of the mod's) takes over.
 */
public final class SixSevenClient {
    /** How long it lasts, and its ease in / out (ticks). */
    public static final int DURATION = 50, EASE_IN = 5, EASE_OUT = 8;
    /** Up / down beats a second. */
    private static final float BEATS_PER_SECOND = 2f;
    /** The arms held out (a little under horizontal), toward the middle, and how far the hands go up / down. */
    private static final float HOLD_PITCH = -1.3f, HOLD_INWARD = 0.18f, WEIGH = 0.38f;
    /** The body bobbing down with each beat (model px). */
    private static final float BOB = 0.6f;
    /** The second note, after the first (ticks). */
    private static final int SEVEN_AT = 6;

    private static final Map<UUID, Long> STARTS = new HashMap<>();
    private static long notesAt = Long.MIN_VALUE;
    private static double notesX, notesY, notesZ;

    private SixSevenClient() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(SixSevenClient::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            STARTS.clear();
            notesAt = Long.MIN_VALUE;
        });
    }

    public static void onPayload(SixSevenPayload payload) {
        for (UUID dancer : payload.dancers()) STARTS.put(dancer, payload.startTick());
        notesAt = payload.startTick();
        notesX = payload.x();
        notesY = payload.y();
        notesZ = payload.z();
    }

    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (world == null) return;
        long now = world.getTime();
        STARTS.values().removeIf(start -> now - start > DURATION || now < start - DURATION);
        // « six… seven! »: two notes, the second higher
        if (notesAt != Long.MIN_VALUE) {
            long since = now - notesAt;
            if (since == 0) note(world, 1.0f);
            else if (since == SEVEN_AT) {
                note(world, 1.335f);
                notesAt = Long.MIN_VALUE;
            } else if (since > SEVEN_AT || since < 0) notesAt = Long.MIN_VALUE;
        }
    }

    private static void note(ClientWorld world, float pitch) {
        world.playSound(notesX, notesY, notesZ, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.PLAYERS, 0.8f, pitch, false);
    }

    /** Where {@code entity} is in its 6-7 (ticks since the start, partial), or -1 if it is not doing one. */
    public static float progress(Entity entity) {
        if (STARTS.isEmpty()) return -1f;
        Long start = STARTS.get(entity.getUuid());
        if (start == null) return -1f;
        float t = entity.getWorld().getTime() - start + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
        return t < 0f || t > DURATION ? -1f : t;
    }

    /** How much of the gesture shows at {@code t} (0 to 1, eased in and out). */
    public static float ease(float t) {
        if (t < 0f) return 0f;
        float in = MathHelper.clamp(t / EASE_IN, 0f, 1f), out = MathHelper.clamp((DURATION - t) / EASE_OUT, 0f, 1f);
        float e = Math.min(in, out);
        return e * e * (3f - 2f * e);
    }

    /** The beat at {@code t}: 1 the right hand up, -1 the left one. */
    public static float beat(float t) {
        return MathHelper.sin(t / 20f * BEATS_PER_SECOND * MathHelper.TAU);
    }

    /**
     * How much of the gesture {@code entity} shows now: 0 when it is not doing one, or its hands are busy with something
     * else (an item used, a swing, riding, swimming, a pose of the mod's).
     */
    public static float weight(LivingEntity entity, float t, float handSwingProgress) {
        if (t < 0f || entity.isUsingItem() || entity.hasVehicle() || entity.isSleeping() || entity.isFallFlying()
                || entity.isInSwimmingPose() || PipeTravellerPose.carrier(entity) != null
                || GlandouilleCarryClient.carrying(entity) || TrichaudronRiderClient.riding(entity) || posedByTheMod(entity)) return 0f;
        float swing = handSwingProgress > 0f ? MathHelper.sin(MathHelper.sqrt(handSwingProgress) * MathHelper.PI) : 0f;
        return ease(t) * (1f - swing);
    }

    /** At a Telescope, striking with a Stencil Hammer or in a Box Costume: those poses keep the arms. */
    private static boolean posedByTheMod(LivingEntity entity) {
        return entity instanceof TelescopeRenderState telescope && telescope.steveparty$telescopeEase() > 0f
                || entity instanceof StencilHammerRenderState hammer && hammer.steveparty$getHammerStrike() >= 0f
                || entity instanceof BoxCostumeRenderState costume && costume.steveparty$getBoxCostume() != null;
    }

    /** Poses {@code model} (after its own pose, before the sleeves copy it). */
    public static void pose(BipedEntityModel<?> model, LivingEntity entity) {
        float t = progress(entity);
        float w = weight(entity, t, model.handSwingProgress);
        if (w <= 0f) return;
        float beat = beat(t);
        hold(model.rightArm, w, HOLD_PITCH - beat * WEIGH, -HOLD_INWARD);
        hold(model.leftArm, w, HOLD_PITCH + beat * WEIGH, HOLD_INWARD);
        // a little dip with each beat
        float bob = Math.abs(beat) * BOB * w;
        model.head.pivotY += bob;
        model.hat.pivotY += bob;
        model.body.pivotY += bob;
        model.rightArm.pivotY += bob;
        model.leftArm.pivotY += bob;
    }

    private static void hold(ModelPart arm, float w, float pitch, float yaw) {
        arm.pitch = MathHelper.lerp(w, arm.pitch, pitch);
        arm.yaw = MathHelper.lerp(w, arm.yaw, yaw);
        arm.roll = MathHelper.lerp(w, arm.roll, 0f);
    }

    /** First person: how high a hand goes (screen units), {@code side} 1 the right hand; 0 when not dancing. */
    public static float handLift(@Nullable LivingEntity player, float side, float handSwingProgress) {
        if (player == null) return 0f;
        float t = progress(player);
        float w = weight(player, t, handSwingProgress);
        return w <= 0f ? 0f : w * (0.12f + side * beat(t) * 0.14f);
    }

    /** First person: whether the empty off hand shows, to weigh with the other one. */
    public static boolean showsOffHand(@Nullable LivingEntity player) {
        return player != null && weight(player, progress(player), 0f) > 0.01f;
    }
}
