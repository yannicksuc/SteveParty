package fr.lordfinn.steveparty.client.renderer;

import fr.lordfinn.steveparty.client.particle.FloatingText;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.util.math.Vec3d;
import org.joml.Quaternionf;
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

    public static void clear() {
        ACTIVE.clear();
    }

    public static void registerRenderCallback() {
        // Movement / lifetime advance once per game tick; rendering only interpolates.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.isPaused() || ACTIVE.isEmpty()) return;
            tick();
        });

        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            if (ACTIVE.isEmpty()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            var matrices = context.matrixStack();
            if (matrices == null) return;
            var textRenderer = mc.textRenderer;
            Vec3d camPos = context.camera().getPos();
            Quaternionf cameraRotation = context.camera().getRotation();

            float tickDelta = context.tickCounter().getTickDelta(true); // fraction of tick passed
            VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();

            for (FloatingText ft : ACTIVE) {
                ft.render(matrices, camPos.x, camPos.y, camPos.z, cameraRotation, textRenderer, vertexConsumers, tickDelta);
            }
            vertexConsumers.draw(); // single flush for the whole batch
        });
    }

    public static void spawn(String text, Vector3f pos, Vector3f velocity, float duration, float scale, int color, float fadeStart) {
        ACTIVE.add(new FloatingText(text, pos.x, pos.y, pos.z, velocity.x, velocity.y, velocity.z, duration, scale, color, fadeStart));
    }
}
