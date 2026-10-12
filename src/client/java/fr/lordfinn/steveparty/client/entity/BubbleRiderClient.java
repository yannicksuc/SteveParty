package fr.lordfinn.steveparty.client.entity;

import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEntity;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleEvents;
import fr.lordfinn.steveparty.entities.custom.bubble.BubbleRiding;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * A player in a Bubbler's bubble: his attack key punches his way out of it ({@link BubbleEvents.RiderPunch}: it
 * pops); his jump key charges a puff, shown in a horse's jump bar over the experience bar (in first person, he sees
 * his bubble's film around him: BubbleEntityRenderer). His own keys and puff move his bubble at once on his screen
 * ({@link BubbleEntity#clientSteer}), before the server's word.
 */
public final class BubbleRiderClient {
    private static final Identifier JUMP_BACKGROUND = Identifier.ofVanilla("hud/jump_bar_background");
    private static final Identifier JUMP_PROGRESS = Identifier.ofVanilla("hud/jump_bar_progress");
    /** The strain's red zone in the jump bar: a warning pink-red (RGB), at most this opaque (deeper as it grows). */
    private static final int STRAIN_ZONE_COLOR = 0xE8405A;
    private static final float STRAIN_ZONE_ALPHA = 0.85f;


    /** The puff charged (ticks), and the tick it may charge again from; a puff released this tick, for the prediction. */
    private static int charge, rested;
    private static int ticks;
    private static Vec3d released = Vec3d.ZERO;

    private BubbleRiderClient() {
    }

    /** The bubble this player rides, or null. */
    private static BubbleEntity bubbleOf(ClientPlayerEntity player) {
        return player != null && player.getVehicle() instanceof BubbleEntity bubble ? bubble : null;
    }

    public static void initialize() {
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (client.currentScreen != null || !(player.getVehicle() instanceof BubbleEntity)) return false;
            if (clickCount > 0 && ClientPlayNetworking.canSend(BubbleEvents.RiderPunch.ID)) {
                ClientPlayNetworking.send(new BubbleEvents.RiderPunch());
                player.swingHand(Hand.MAIN_HAND);
            }
            return true;
        });
        ClientTickEvents.START_CLIENT_TICK.register(BubbleRiderClient::tick);
        BubbleEntity.clientSteer = BubbleRiderClient::steer;
        HudRenderCallback.EVENT.register(BubbleRiderClient::drawHud);
    }

    /** The jump key: charging while held, a puff on release (as the server counts it). */
    private static void tick(MinecraftClient client) {
        ticks++;
        BubbleEntity bubble = bubbleOf(client.player);
        if (bubble == null) {
            charge = 0;
            return;
        }
        if (client.options.jumpKey.isPressed() && client.currentScreen == null) {
            if (ticks >= rested) charge = Math.min(BubbleRiding.CHARGE_MAX, charge + 1);
        } else if (charge > 0) {
            if (charge >= BubbleRiding.CHARGE_MIN) {
                released = BubbleRiding.puff(charge, client.player.getRotationVector(), bubble.getPassengerList().size());
                rested = ticks + BubbleRiding.PUFF_COOLDOWN;
            }
            charge = 0;
        }
    }

    /** What this player's keys and puff add to his bubble's speed this tick, on his screen. */
    private static Vec3d steer(BubbleEntity bubble) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.getVehicle() != bubble) return Vec3d.ZERO;
        int riders = Math.max(1, bubble.getPassengerList().size());
        Vec3d push = BubbleRiding.keys(player.forwardSpeed, player.sidewaysSpeed, player.getYaw()).multiply(BubbleRiding.DRIFT_ACCEL / riders);
        if (released != Vec3d.ZERO) {
            push = push.add(released);
            released = Vec3d.ZERO;
            bubble.release();
        }
        return push;
    }

    /**
     * The puff's charge in a horse's jump bar, and its bubble's strain in the same bar: a red zone eating it from its
     * right end as the strain grows (gone once the bubble is calm again); while a puff would be risky (strained past
     * {@link BubbleRiding#STRAIN_SAFE}) the charge itself blushes and throbs. No text: the bar heats up.
     */
    private static void drawHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        BubbleEntity bubble = bubbleOf(client.player);
        if (client.options.hudHidden || bubble == null) return;
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        int x = width / 2 - 91, y = height - 29;
        float strain = bubble.strain();
        context.drawGuiTexture(JUMP_BACKGROUND, x, y, 182, 5);
        int red = Math.round(182 * strain);
        if (red > 0) {
            // inside the bar's frame (its top and bottom pixel rows and its ends stay as they are)
            int alpha = Math.round(255 * (STRAIN_ZONE_ALPHA * (0.5f + 0.5f * strain)));
            context.fill(x + 182 - red, y + 1, x + 181, y + 4, alpha << 24 | STRAIN_ZONE_COLOR);
        }
        int filled = Math.round(182 * charge / (float) BubbleRiding.CHARGE_MAX);
        if (filled > 0) {
            float risk = BubbleRiding.strainPopChance(strain);
            if (risk > 0) {
                float throb = 0.75f + 0.25f * MathHelper.sin(ticks * 0.9f);
                context.setShaderColor(1, 1 - 0.6f * risk * throb, 1 - 0.6f * risk * throb, 1);
            }
            context.drawGuiTexture(JUMP_PROGRESS, 182, 5, 0, 0, x, y, filled, 5);
            context.setShaderColor(1, 1, 1, 1);
        }
    }
}
