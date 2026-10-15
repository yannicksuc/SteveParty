package fr.lordfinn.steveparty.client.entity.parachute;

import fr.lordfinn.steveparty.client.render.HitboxHarness;
import fr.lordfinn.steveparty.client.utils.WorldView;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.items.custom.parachute.ParachuteItem;
import fr.lordfinn.steveparty.items.custom.parachute.ParachuteState;
import fr.lordfinn.steveparty.items.custom.parachute.Parachutes;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Client side of the parachute: the canopy over whoever carries one (a player, any mob), fitted to its hitbox
 * ({@link HitboxHarness}): above its top, sized to its width (within bounds), four lead ropes from the canopy's
 * corners down to the centre of its hitbox. Open, it unfolds from a bundle in {@link #UNFOLD_TICKS} ticks (overshooting
 * a little) and sways around that centre; packed (a mob's, waiting to fall), it is a small bundle on its back.
 * <p>
 * Drawn with the entity (LivingEntityRendererParachuteMixin), opaque: cutout canopy, lead ropes, all writing depth. In
 * first person the player's own body is not drawn: his canopy is, after the entities (BEFORE_DEBUG_RENDER), its ropes
 * whole, down to his chest: looking up they meet under him, looking ahead they pass by the screen's edges.
 */
public final class ParachuteClient {
    /**
     * The canopy's scale: the model as is over a player, over another hitbox in proportion to its width (a player's:
     * {@code PLAYER_WIDTH}), within bounds (a chicken's, a ravager's).
     */
    private static final float PLAYER_WIDTH = 0.6f, MIN_FIT = 0.67f, MAX_FIT = 2.4f;
    /** The canopy model's half-side and height (blocks at scale 1: 12 and 12 px). */
    private static final float MODEL_HALF_SIDE = 12 / 16f, MODEL_HEIGHT = 12 / 16f;
    /** How far over the hitbox's top the canopy's bottom is (blocks at scale 1). */
    private static final float GAP = 0.6f;
    /**
     * Packed: the bundle's share of the canopy's width and height; it sits half sunk into the hitbox's top (on the
     * back, under a name tag).
     */
    private static final float PACKED_WIDTH = 0.35f, PACKED_HEIGHT = 0.35f;
    /** Unfolding (ticks) and its overshoot. */
    private static final float UNFOLD_TICKS = 8, OVERSHOOT = 1.6f;
    /** The sway: degrees each way, speeds (radians a tick). */
    private static final float SWAY = 5, SWAY_X_SPEED = 0.11f, SWAY_Z_SPEED = 0.083f;
    /** The ropes hang from the canopy's corners, this far in (share of its half-side). */
    private static final float ROPE_CORNER = 0.85f;

    private static ParachuteRenderer renderer;

    private ParachuteClient() {
    }

    public static void initialize() {
        ColorProviderRegistry.ITEM.register((stack, tint) -> tint == 1 ? ParachuteItem.color(stack) : 0xFFFFFFFF, ModItems.PARACHUTE);
        WorldRenderEvents.BEFORE_DEBUG_RENDER.register(ParachuteClient::renderFirstPerson);
    }

    private static ParachuteRenderer renderer() {
        if (renderer == null) renderer = new ParachuteRenderer();
        return renderer;
    }

    /** Draws {@code entity}'s parachute, if it carries one, from its feet (an entity renderer's origin). */
    public static void render(LivingEntity entity, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers) {
        ParachuteState state = Parachutes.get(entity);
        if (state == null || entity.isInvisible()) return;
        float time = entity.getWorld().getTime() + tickDelta;
        float open = state.open() ? unfolded((time - state.since()) / UNFOLD_TICKS) : 0;
        float fit = HitboxHarness.fit(entity, 1 / PLAYER_WIDTH, MIN_FIT, MAX_FIT);
        float width = fit * MathHelper.lerp(open, PACKED_WIDTH, 1), height = fit * MathHelper.lerp(open, PACKED_HEIGHT, 1);
        float packedSink = -fit * PACKED_HEIGHT * MODEL_HEIGHT / 2;
        float bottom = entity.getHeight() + MathHelper.lerp(MathHelper.clamp(open, 0, 1), packedSink, fit * GAP);
        Vector3f center = HitboxHarness.center(entity);

        matrices.push();
        // turned with the body, swaying around the hitbox's centre (where the ropes meet)
        matrices.translate(center.x, center.y, center.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, entity.prevBodyYaw, entity.bodyYaw)));
        float sway = SWAY * MathHelper.clamp(open, 0, 1);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(time * SWAY_X_SPEED) * sway));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.cos(time * SWAY_Z_SPEED) * sway));
        matrices.translate(-center.x, -center.y, -center.z);

        Vec3d feet = entity.getLerpedPos(tickDelta);
        int canopyLight = WorldRenderer.getLightmapCoordinates(entity.getWorld(), BlockPos.ofFloored(feet.add(0, bottom + height / 2, 0)));
        int centerLight = WorldRenderer.getLightmapCoordinates(entity.getWorld(), BlockPos.ofFloored(feet.add(0, center.y, 0)));
        float corner = width * MODEL_HALF_SIDE * ROPE_CORNER;
        for (int x = -1; x <= 1; x += 2) {
            for (int z = -1; z <= 1; z += 2) {
                HitboxHarness.rope(matrices, consumers, center, new Vector3f(x * corner, bottom, z * corner), centerLight, canopyLight);
            }
        }
        matrices.translate(0, bottom, 0);
        matrices.scale(width, height, width);
        renderer().render(matrices, consumers, canopyLight, state.color(), tickDelta);
        matrices.pop();
    }

    /** Its unfolding at {@code t} (0..1 over {@link #UNFOLD_TICKS}): eased out, overshooting a little before it settles. */
    private static float unfolded(float t) {
        if (t >= 1) return 1;
        float u = Math.max(t, 0) - 1;
        return 1 + (OVERSHOOT + 1) * u * u * u + OVERSHOOT * u * u;
    }

    /** First person, the player's body isn't drawn: his own canopy is, here. */
    private static void renderFirstPerson(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        MatrixStack matrices = context.matrixStack();
        if (player == null || matrices == null || !context.camera().getFocusedEntity().equals(player) || context.camera().isThirdPerson()) return;
        if (Parachutes.get(player) == null) return;
        float tickDelta = context.tickCounter().getTickDelta(true);
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        WorldView.draw(context, () -> {
            Vec3d at = player.getLerpedPos(tickDelta).subtract(context.camera().getPos());
            matrices.push();
            matrices.translate(at.x, at.y, at.z);
            render(player, tickDelta, matrices, consumers);
            matrices.pop();
            consumers.draw();
        });
    }
}
