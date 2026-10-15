package fr.lordfinn.steveparty.client.board;

import fr.lordfinn.steveparty.board.PartyLinked;
import fr.lordfinn.steveparty.client.gui.FloatingInfo;
import fr.lordfinn.steveparty.client.gui.HudDepth;
import fr.lordfinn.steveparty.client.gui.NotLinkedMark;
import fr.lordfinn.steveparty.client.gui.ToolHud;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * The default « linked to no party » of a {@link PartyLinked} block with no panel of its own (the Party Rewinder...):
 * looked at while unlinked, a small gold plate over it holding the Tile Linker Brush crossed out in red
 * ({@link NotLinkedMark}), as the Piggy Bank's panel starts. No words. Out of sight, its card on the screen
 * ({@link FloatingInfo}). A block showing it itself ({@link PartyLinked#showsNotLinkedItself}) never gets it.
 * <p>
 * Rendering (minecraft-rendering): a world panel in {@code AFTER_ENTITIES}, main target; the plate writes depth, the
 * brush then its opaque cross one step toward the camera each, every batch drawn at once.
 */
public final class NotLinkedPanelClient {
    private static final double AIM_DISTANCE = 24, PANEL_DISTANCE = 16;
    /** Text pixels: padding, the icon (as on the Piggy Bank's panel). */
    private static final int PAD = 5, ICON = 9;
    private static final float SCALE = 1f / 48f, MAX_GROW = 2.5f;
    /** Above the block, a little towards the camera. */
    private static final double ABOVE = 1.1, AHEAD = 0.3;

    private record CardKey(BlockPos pos) {
    }

    private NotLinkedPanelClient() {
    }

    public static void initialize() {
        WorldRenderEvents.AFTER_ENTITIES.register(NotLinkedPanelClient::render);
        HudRenderCallback.EVENT.register(NotLinkedPanelClient::renderHud);
    }

    /** The unlinked party-linked block with no panel of its own under the crosshair, or null. */
    private static @Nullable BlockPos aimed(MinecraftClient client) {
        Entity viewer = client.getCameraEntity();
        if (client.world == null || viewer == null) return null;
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return null;
        if (block.getPos().squaredDistanceTo(viewer.getEyePos()) > AIM_DISTANCE * AIM_DISTANCE) return null;
        return client.world.getBlockEntity(block.getBlockPos()) instanceof PartyLinked linked
                && !linked.showsNotLinkedItself() && !linked.isPartyLinked() ? block.getBlockPos().toImmutable() : null;
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        MatrixStack matrices = context.matrixStack();
        BlockPos pos = aimed(client);
        if (pos == null || matrices == null || client.options.hudHidden) return;
        Camera camera = context.camera();
        Vec3d toCamera = new Vec3d(camera.getPos().x - pos.getX() - 0.5, 0, camera.getPos().z - pos.getZ() - 0.5);
        toCamera = toCamera.lengthSquared() < 1.0E-4 ? Vec3d.ZERO : toCamera.normalize().multiply(AHEAD);
        Vec3d anchor = new Vec3d(pos.getX() + 0.5, pos.getY() + ABOVE, pos.getZ() + 0.5).add(toCamera);
        double distance = Math.sqrt(anchor.squaredDistanceTo(camera.getPos()));
        float scale = SCALE * (float) Math.clamp(distance / 7.0, 1.0, MAX_GROW);
        int size = ICON + 2 * PAD;
        Vec3d middle = anchor.add(0, size * scale / 2, 0);
        if (!FloatingInfo.inSight(camera, middle, PANEL_DISTANCE)) return;
        FloatingInfo.drawnInWorld(new CardKey(pos));
        VertexConsumerProvider.Immediate consumers = client.getBufferBuilders().getEntityVertexConsumers();
        matrices.push();
        matrices.translate(middle.x - camera.getPos().x, middle.y - camera.getPos().y, middle.z - camera.getPos().z);
        matrices.multiply(camera.getRotation());
        matrices.scale(scale, -scale, scale);
        float half = size / 2f;
        WorldDraw.plate(matrices, consumers, WorldDraw.Plate.GOLD, -half, -half, half, half);
        consumers.draw();
        // A square plate holding one icon: centred, its padding even
        NotLinkedMark.world(matrices, consumers, client.world, -half + PAD, -half + PAD, ICON, 0.5f);
        consumers.draw();
        matrices.pop();
    }

    /** Its plate out of sight (too far...): its card on the screen, the same icon. */
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        BlockPos pos = aimed(client);
        if (pos == null || client.options.hudHidden || client.currentScreen != null) return;
        CardKey key = new CardKey(pos);
        if (!FloatingInfo.needsCard(key)) return;
        int size = ICON + 2 * FloatingInfo.PAD;
        FloatingInfo.card(context, key, size, size, ToolHud.Plate.GOLD, 1f, 0, card -> {
            card.getMatrices().push();
            card.getMatrices().translate(FloatingInfo.PAD, FloatingInfo.PAD, 0);
            card.getMatrices().scale(ICON / 16f, ICON / 16f, 1);
            HudDepth.item(card, () -> card.drawItem(NotLinkedMark.BRUSH, 0, 0));
            HudDepth.onTop(card, () -> NotLinkedMark.crossOnIcon(card, 0, 0));
            card.getMatrices().pop();
        });
    }
}
