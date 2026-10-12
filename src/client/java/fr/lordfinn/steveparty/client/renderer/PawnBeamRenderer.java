package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.client.payloads.ClientPayloads;
import fr.lordfinn.steveparty.payloads.custom.PawnBeamsPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * The light beams over the pawns (see fr.lordfinn.steveparty.service.PawnBeams): a {@link HaloBeam} in the pawn's
 * colour, from its feet up to the build height, following it as it walks; it fades in when the server shows it and
 * out when it no longer does. The pawns come from {@link PawnBeamsPayload}; one the client does not have (too far)
 * keeps the place the server gave.
 * <p>
 * Drawn like the star's beam ({@link StarSpaceRenderer}): after the translucent terrain (AFTER_TRANSLUCENT), over the
 * board tiles and the water, remembered for the clouds' mask. Never from an entity or block entity renderer.
 */
@Environment(EnvType.CLIENT)
public final class PawnBeamRenderer {
    /** A pawn's beam is narrower than the star's: a pawn is small. */
    private static final float RADIUS = 0.3F;
    /** Ticks a beam takes to fade in or out. */
    private static final int FADE_TICKS = 8;
    /** Farther than this, no beam (as the star's), in blocks. */
    private static final double MAX_DISTANCE = 256;
    /** The server tells every second while there are beams; nothing heard for this long (another world...): none. */
    private static final long FORGET_AFTER_TICKS = 60;

    /** A pawn's beam: its colour, its place as told, whether it is shown, its opacity now and at the last tick. */
    private static final class Beam {
        int color;
        float x, y, z;
        boolean shown;
        float alpha, prevAlpha;
    }

    private static final Map<Integer, Beam> BEAMS = new HashMap<>();
    private static long heardAt;

    private PawnBeamRenderer() {
    }

    public static void initialize() {
        ClientPayloads.receive(PawnBeamsPayload.ID, (payload, context) -> {
            heardAt = context.client().world == null ? 0 : context.client().world.getTime();
            for (Beam beam : BEAMS.values()) beam.shown = false;
            for (PawnBeamsPayload.Beam told : payload.beams()) {
                Beam beam = BEAMS.computeIfAbsent(told.entityId(), id -> new Beam());
                beam.color = told.color();
                beam.x = told.x();
                beam.y = told.y();
                beam.z = told.z();
                beam.shown = true;
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(PawnBeamRenderer::tick);
        WorldRenderEvents.AFTER_TRANSLUCENT.register(PawnBeamRenderer::render);
    }

    /** The fades; a beam faded out is forgotten. */
    private static void tick(MinecraftClient client) {
        ClientWorld world = client.world;
        if (BEAMS.isEmpty() || world == null || client.isPaused()) return;
        boolean forgotten = Math.abs(world.getTime() - heardAt) > FORGET_AFTER_TICKS;
        Iterator<Beam> it = BEAMS.values().iterator();
        while (it.hasNext()) {
            Beam beam = it.next();
            if (forgotten) beam.shown = false;
            beam.prevAlpha = beam.alpha;
            beam.alpha = MathHelper.clamp(beam.alpha + (beam.shown ? 1F : -1F) / FADE_TICKS, 0, 1);
            if (!beam.shown && beam.alpha == 0 && beam.prevAlpha == 0) it.remove();
        }
    }

    private static void render(WorldRenderContext context) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (BEAMS.isEmpty() || context.matrixStack() == null || world == null) return;
        MatrixStack matrices = context.matrixStack();
        Vec3d camera = context.camera().getPos();
        float delta = context.tickCounter().getTickDelta(true);
        float time = world.getTime() + delta;
        VertexConsumerProvider.Immediate consumers = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        boolean drawn = false;
        for (Map.Entry<Integer, Beam> entry : BEAMS.entrySet()) {
            Beam beam = entry.getValue();
            float alpha = MathHelper.lerp(delta, beam.prevAlpha, beam.alpha);
            if (alpha <= 0) continue;
            Entity pawn = world.getEntityById(entry.getKey());
            Vec3d at = pawn != null ? pawn.getLerpedPos(delta) : new Vec3d(beam.x, beam.y, beam.z);
            if (at.squaredDistanceTo(camera) > MAX_DISTANCE * MAX_DISTANCE) continue;
            matrices.push();
            matrices.translate(at.x - camera.x, at.y - camera.y, at.z - camera.z);
            // Eased: it opens and closes softly
            HaloBeam.render(matrices, consumers, beam.color, RADIUS, (float) Math.max(1, world.getTopY() - at.y),
                    alpha * alpha * (3 - 2 * alpha), time);
            matrices.pop();
            drawn = true;
        }
        if (drawn) consumers.draw();
    }

    /** A new connection: no beam known. */
    public static void clear() {
        BEAMS.clear();
    }
}
