package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.client.particle.FloatingText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)

public class FloatingTextRenderer {
    private static final List<FloatingText> ACTIVE = new ArrayList<>();

    public static void spawn(String text, double x, double y, double z) {
        ACTIVE.add(new FloatingText(text, x, y, z));
    }

    private static void tick() {
        ACTIVE.removeIf(ft -> !ft.tick());
    }

    public static void registerRenderCallback() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            tick();
            MinecraftClient mc = MinecraftClient.getInstance();
            var matrices = context.matrixStack();
            var textRenderer = mc.textRenderer;
            Vec3d camPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

            float tickDelta = context.tickCounter().getTickDelta(true); // fraction of tick passed

            for (FloatingText ft : new ArrayList<>(ACTIVE)) {
                ft.render(matrices, camPos.x, camPos.y, camPos.z, textRenderer, tickDelta);
            }
        });
    }

    public static void spawn(String text, Vector3f pos, Vector3f velocity, float duration, float scale, int color, float fadeStart) {
        ACTIVE.add(new FloatingText(text, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z, duration, scale, color, fadeStart));
    }
}

