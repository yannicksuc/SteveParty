package fr.lordfinn.steveparty.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.Window;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.fabricmc.loader.api.FabricLoader;

/**
 * The Steve Party Maker title screen background, drawn in place of the vanilla panorama (TitleScreenBackgroundMixin)
 * when FancyMenu is loaded (the modpack's title screen; the mod alone keeps the vanilla one) and
 * textures/gui/title/sky.png exists: the poster layers back to front with a smooth mouse parallax, then the
 * animated logo (the 72 frames of art/logo/steve_party_maker_animated.gif) laid out on its own at the top centre.
 * Textures: the art sources, linear filtering (.mcmeta) so the sub-pixel motion never shimmers.
 * FancyMenu keeps the buttons, panel and texts on top (modpack layout steveparty_title_screen.txt, no background).
 */
public final class TitleScreenBackground {
    /** The poster's shared frame, in source pixels. */
    private static final float FRAME_W = 1920, FRAME_H = 1080;
    /** Cover scale on top of "just covers the screen", so the parallax never shows an edge. */
    private static final float OVERSCAN = 1.07f;

    /** Back to front: texture, size, offset in the frame (the PNGs are cropped to their opaque bounds), depth (0..1). */
    private static final Layer[] LAYERS = {
            new Layer("sky", 1920, 1080, 0, 0, 0.05f),
            new Layer("crowd", 1920, 514, 0, 563, 0.2f),
            new Layer("heroes", 1892, 1041, 0, 39, 0.4f),
            new Layer("foreground", 1920, 395, 0, 685, 0.75f),
    };

    private static final Identifier LOGO = Steveparty.id("textures/gui/title/logo.png");
    private static final int LOGO_W = 464, LOGO_H = 376, LOGO_PAD = 2, LOGO_COLS = 8, LOGO_FRAMES = 72;
    private static final int LOGO_SHEET_W = LOGO_COLS * (LOGO_W + 2 * LOGO_PAD), LOGO_SHEET_H = 9 * (LOGO_H + 2 * LOGO_PAD);
    private static final long LOGO_FRAME_NANOS = 50_000_000L;
    private static final float LOGO_DEPTH = 0.12f;
    /** Logo box, as fractions of the screen height: top margin, max height. */
    private static final float LOGO_TOP = 0.035f, LOGO_MAX_H = 0.39f;
    /** The FancyMenu button panel's top, in GUI pixels above the bottom (layout: bottom-centered, y = -100). */
    private static final int PANEL_TOP_FROM_BOTTOM = 100, LOGO_PANEL_GAP = 6;

    /** Easing rate toward the target (1/s): about 0.3 s to close 2/3 of the gap, the same at any frame rate. */
    private static final double EASE_RATE = 3.5;
    /** Seconds without mouse motion before the idle drift fades in, and the fade length. */
    private static final double IDLE_DELAY = 2, IDLE_FADE = 3;

    private static Boolean available;
    private static boolean texturesLoaded;
    private static long lastNanos, startNanos, lastMoveNanos;
    private static double lastMouseX = Double.NaN, lastMouseY;
    /** Eased parallax position, -1..1 on each axis (0 = centre). */
    private static double posX, posY;

    private TitleScreenBackground() {
    }

    public static void initialize() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Steveparty.id("title_screen_background");
            }

            @Override
            public void reload(ResourceManager manager) {
                available = null;
            }
        });
        // ~70 MB of VRAM (the logo sheet is 3744x3420): give it back once in a world, reloaded on the next title screen.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (texturesLoaded && client.world != null) {
                texturesLoaded = false;
                for (Layer layer : LAYERS) client.getTextureManager().destroyTexture(layer.texture);
                client.getTextureManager().destroyTexture(LOGO);
            }
        });
    }

    public static boolean isAvailable() {
        if (available == null) {
            available = FabricLoader.getInstance().isModLoaded("fancymenu")
                    && MinecraftClient.getInstance().getResourceManager().getResource(LAYERS[0].texture).isPresent();
        }
        return available;
    }

    public static void render(DrawContext context, int width, int height) {
        MinecraftClient client = MinecraftClient.getInstance();
        long now = System.nanoTime();
        if (lastNanos == 0) startNanos = lastMoveNanos = lastNanos = now;
        double dt = Math.min((now - lastNanos) / 1e9, 0.1);
        lastNanos = now;
        double t = (now - startNanos) / 1e9;
        texturesLoaded = true;

        updateParallax(client, now, t, dt);

        float scale = Math.max(width / FRAME_W, height / FRAME_H) * OVERSCAN;
        float frameX = (width - FRAME_W * scale) / 2, frameY = (height - FRAME_H * scale) / 2;
        // Same travel on both axes, bounded by the tighter margin so no layer (depth <= 1) ever uncovers an edge.
        float travel = Math.min(-frameX, -frameY);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (Layer layer : LAYERS) {
            context.getMatrices().push();
            context.getMatrices().translate(frameX - (float) posX * layer.depth * travel + layer.x * scale,
                    frameY - (float) posY * layer.depth * travel + layer.y * scale, 0);
            context.getMatrices().scale(scale, scale, 1);
            context.drawTexture(layer.texture, 0, 0, 0, 0, layer.width, layer.height, layer.width, layer.height);
            context.getMatrices().pop();
        }
        renderLogo(context, width, height, travel, now, t);
        RenderSystem.disableBlend();
    }

    private static void updateParallax(MinecraftClient client, long now, double t, double dt) {
        Window window = client.getWindow();
        double mouseX = client.mouse.getX(), mouseY = client.mouse.getY();
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            lastMoveNanos = now;
        }
        double targetX = mouseX / Math.max(1, window.getWidth()) * 2 - 1;
        double targetY = mouseY / Math.max(1, window.getHeight()) * 2 - 1;
        // Idle drift: a slow figure of two incommensurate sines, faded in once the mouse rests.
        double idle = MathHelper.clamp(((now - lastMoveNanos) / 1e9 - IDLE_DELAY) / IDLE_FADE, 0, 1);
        idle = idle * idle * (3 - 2 * idle);
        targetX = MathHelper.clamp(targetX + idle * 0.45 * Math.sin(t * Math.PI * 2 / 23), -1, 1);
        targetY = MathHelper.clamp(targetY + idle * 0.3 * Math.sin(t * Math.PI * 2 / 17 + 1.3), -1, 1);
        double k = 1 - Math.exp(-EASE_RATE * dt);
        posX += (targetX - posX) * k;
        posY += (targetY - posY) * k;
    }

    private static void renderLogo(DrawContext context, int width, int height, float travel, long now, double t) {
        float top = height * LOGO_TOP;
        float logoH = Math.min(height * LOGO_MAX_H, height - PANEL_TOP_FROM_BOTTOM - LOGO_PANEL_GAP - top);
        logoH = Math.min(logoH, width * 0.9f * LOGO_H / LOGO_W);
        if (logoH <= 0) return;
        float logoScale = logoH / LOGO_H;
        // Gentle bob and breath, out of phase so it floats rather than pulses.
        float bob = (float) Math.sin(t * Math.PI * 2 / 4.2) * height * 0.005f;
        float breath = 1 + (float) Math.sin(t * Math.PI * 2 / 5.6 + 0.8) * 0.008f;
        float centerX = width / 2f - (float) posX * LOGO_DEPTH * travel;
        float centerY = top + logoH / 2 - (float) posY * LOGO_DEPTH * travel + bob;

        int frame = (int) ((now - startNanos) / LOGO_FRAME_NANOS % LOGO_FRAMES);
        int u = (frame % LOGO_COLS) * (LOGO_W + 2 * LOGO_PAD) + LOGO_PAD;
        int v = (frame / LOGO_COLS) * (LOGO_H + 2 * LOGO_PAD) + LOGO_PAD;

        context.getMatrices().push();
        context.getMatrices().translate(centerX, centerY, 0);
        context.getMatrices().scale(logoScale * breath, logoScale * breath, 1);
        context.getMatrices().translate(-LOGO_W / 2f, -LOGO_H / 2f, 0);
        context.drawTexture(LOGO, 0, 0, LOGO_W, LOGO_H, u, v, LOGO_W, LOGO_H, LOGO_SHEET_W, LOGO_SHEET_H);
        context.getMatrices().pop();
    }

    private record Layer(Identifier texture, int width, int height, int x, int y, float depth) {
        Layer(String name, int width, int height, int x, int y, float depth) {
            this(Steveparty.id("textures/gui/title/" + name + ".png"), width, height, x, y, depth);
        }
    }
}
