package fr.lordfinn.steveparty.client.gui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Where an item's icon really shows in its 16 px square: a 3D item (a die, a block) seldom fills it evenly, so a panel
 * centring the square leaves its visible pixels off centre. Each icon asked for is drawn once off screen (between two
 * frames, in a client tick: nothing of the frame is bound then) and the offset that centres its visible pixels is kept,
 * in whole icon pixels. Until then: no offset.
 */
public final class IconBounds {
    /** Off-screen pixels per icon pixel, and room for an icon up to twice its size. */
    private static final int RES = 4, SIZE = 32 * RES;
    private static final int[] NONE = {0, 0};

    private record Key(Item item, float scale) {
    }

    private static final Map<Key, int[]> OFFSETS = new HashMap<>();
    private static final Set<Key> PENDING = new LinkedHashSet<>();

    private IconBounds() {
    }

    public static void initialize() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (PENDING.isEmpty()) return;
            for (Key key : PENDING) OFFSETS.put(key, measure(client, key));
            PENDING.clear();
        });
    }

    /**
     * The offset (icon pixels, x then y down) that centres the visible pixels of {@code stack}'s icon drawn
     * {@code scale} times its size; {0, 0} until measured.
     */
    public static int[] offset(ItemStack stack, float scale) {
        Key key = new Key(stack.getItem(), scale);
        int[] offset = OFFSETS.get(key);
        if (offset != null) return offset;
        PENDING.add(key);
        return NONE;
    }

    private static int[] measure(MinecraftClient client, Key key) {
        SimpleFramebuffer target = new SimpleFramebuffer(SIZE, SIZE, true, MinecraftClient.IS_SYSTEM_MAC);
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        VertexSorter sorting = RenderSystem.getVertexSorting();
        Matrix4fStack view = RenderSystem.getModelViewStack();
        int[] offset = NONE;
        try {
            target.setClearColor(0, 0, 0, 0);
            target.clear(MinecraftClient.IS_SYSTEM_MAC);
            target.beginWrite(true);
            // As the GUI is drawn (GameRenderer#render)
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, SIZE, SIZE, 0, 1000, 21000), VertexSorter.BY_Z);
            view.pushMatrix();
            view.translation(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            DrawContext context = new DrawContext(client, client.getBufferBuilders().getEntityVertexConsumers());
            context.getMatrices().translate(SIZE / 2f, SIZE / 2f, 0);
            context.getMatrices().scale(RES * key.scale(), RES * key.scale(), 1);
            context.drawItem(new ItemStack(key.item()), -8, -8);
            context.draw();
            try (NativeImage image = new NativeImage(SIZE, SIZE, false)) {
                RenderSystem.bindTexture(target.getColorAttachment());
                image.loadFromTextureImage(0, false);
                image.mirrorVertically();
                int minX = SIZE, minY = SIZE, maxX = -1, maxY = -1;
                for (int y = 0; y < SIZE; y++) {
                    for (int x = 0; x < SIZE; x++) {
                        if ((image.getColor(x, y) >>> 24) == 0) continue;
                        minX = Math.min(minX, x);
                        maxX = Math.max(maxX, x);
                        minY = Math.min(minY, y);
                        maxY = Math.max(maxY, y);
                    }
                }
                if (maxX >= 0) {
                    // Pixels from the square's centre to the visible pixels' centre, in icon pixels
                    float cx = ((minX + maxX + 1) / 2f - SIZE / 2f) / RES, cy = ((minY + maxY + 1) / 2f - SIZE / 2f) / RES;
                    offset = new int[]{-Math.round(cx), -Math.round(cy)};
                }
            }
        } finally {
            view.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            GlStateManager._bindTexture(0);
            target.delete();
            client.getFramebuffer().beginWrite(true);
        }
        return offset;
    }
}
