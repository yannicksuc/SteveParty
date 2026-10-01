package fr.lordfinn.steveparty.client.telescope;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlockEntities;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.entities.custom.MulaEntity;
import fr.lordfinn.steveparty.payloads.custom.TelescopePayloads;
import fr.lordfinn.steveparty.telescope.TelescopeMath;
import fr.lordfinn.steveparty.telescope.TelescopeService;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BuiltinItemRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The Telescope on the player's own client: the view through it (zoom, round scope, the real sky behind), the game
 * "Replay the night", and his guide stars.
 * <p>
 * <b>Replay the night</b>: the wheel goes back through the past nights the server listed (one step = one night whose
 * Mulas he has not found), and that night's shooting stars cross his sky again, in a loop, drawn here from a few
 * numbers (TelescopeMath): no entity, nothing for the others to see. Keeping the reticle on a star fills a gauge; full,
 * the server is told and the site becomes one of his guide stars.
 * <p>
 * <b>Guide stars</b>: a twinkling star over each site he found, drawn in his sky at night: low on the horizon and
 * small when far, high and bright when close. Looking at it names its direction and distance in the action bar.
 */
public final class TelescopeClient {
    private TelescopeClient() {
    }

    private static final Identifier GLOW = Steveparty.id("textures/entity/mula_hallo.png");
    private static final Identifier HEART = Steveparty.id("textures/entity/mula_wisp.png");
    private static final Identifier SCOPE = Steveparty.id("textures/gui/telescope_scope.png");
    /** Field of view through the telescope (x the normal one), and how far the player may stand from it. */
    private static final float ZOOM = 0.6f;
    private static final double MAX_DISTANCE = 4;
    /** Ticks of the sparkle after a find; a guide star is named when looked at within this angle (degrees). */
    private static final int SUCCESS_TICKS = 60;
    private static final double GUIDE_LOOK_DEGREES = 4, POINTER_DEGREES = 14;
    private static final int GUIDE_COLOR = 0xFFE9A0;

    private static @Nullable BlockPos watching;
    private static long today;
    private static List<TelescopePayloads.Night> nights = new ArrayList<>();
    /** 0: tonight's sky; n: the n-th night back (nights.get(n - 1)). */
    private static int index;
    private static int replayTicks;
    private static float gauge, prevGauge;
    private static boolean tracking;
    private static int successTicks = -1;
    /** A night was found at this telescope since the player came to it. */
    private static boolean foundHere;
    private static float zoom, prevZoom;
    private static List<TelescopePayloads.Guide> guides = List.of();
    private static final double[] POINT = new double[3];

    public static void initialize() {
        BlockEntityRendererFactories.register(ModBlockEntities.TELESCOPE_ENTITY, TelescopeBlockEntityRenderer::new);
        TelescopeModel icon = new TelescopeModel();
        BuiltinItemRendererRegistry.INSTANCE.register(ModBlocks.TELESCOPE, (stack, mode, matrices, vertexConsumers, light, overlay) -> {
            matrices.push();
            matrices.translate(0.5f, 0f, 0.5f);
            icon.render(matrices, vertexConsumers, light, overlay, 0f, 0f, TelescopeModel.REST_PITCH);
            matrices.pop();
        });
        ClientPlayNetworking.registerGlobalReceiver(TelescopePayloads.Open.ID,
                (payload, context) -> context.client().execute(() -> open(payload)));
        ClientPlayNetworking.registerGlobalReceiver(TelescopePayloads.Guides.ID,
                (payload, context) -> context.client().execute(() -> guides = List.copyOf(payload.guides())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            close(false);
            guides = List.of();
            zoom = prevZoom = 0;
        });
        ClientTickEvents.END_CLIENT_TICK.register(TelescopeClient::tick);
        HudRenderCallback.EVENT.register(TelescopeClient::renderHud);
        WorldRenderEvents.END.register(TelescopeClient::renderSky);
    }

    // ---------------------------------------------------------------- state

    public static boolean isWatching() {
        return watching != null;
    }

    public static boolean isWatching(BlockPos pos) {
        return pos.equals(watching);
    }

    /** x the field of view (1: not looking through a telescope). */
    public static float fovMultiplier(float tickDelta) {
        float z = MathHelper.lerp(tickDelta, prevZoom, zoom);
        return z <= 0 ? 1f : MathHelper.lerp(z * z * (3 - 2 * z), 1f, ZOOM);
    }

    /** The first-person hand is hidden while looking through. */
    public static boolean hidesHand() {
        return watching != null || zoom > 0.05f;
    }

    private static void open(TelescopePayloads.Open payload) {
        boolean same = payload.pos().equals(watching);
        watching = payload.pos();
        today = payload.day();
        nights = new ArrayList<>(payload.nights());
        if (!same || index > nights.size()) setIndex(0);
        if (!same) foundHere = false;
        if (!same) play(SoundEvents.ITEM_SPYGLASS_USE, 1f, 1f);
    }

    private static void close(boolean sound) {
        if (watching == null) return;
        watching = null;
        nights = new ArrayList<>();
        setIndex(0);
        successTicks = -1;
        if (sound) play(SoundEvents.ITEM_SPYGLASS_STOP_USING, 1f, 1f);
    }

    private static void setIndex(int i) {
        index = i;
        replayTicks = 0;
        gauge = prevGauge = 0;
        tracking = false;
    }

    private static @Nullable TelescopePayloads.Night night() {
        return index > 0 && index <= nights.size() ? nights.get(index - 1) : null;
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound, pitch, volume));
    }

    /** The wheel while looking through: back through the nights (down) or forward to tonight (up). */
    public static boolean onScroll(double vertical) {
        if (watching == null || MinecraftClient.getInstance().currentScreen != null) return false;
        if (vertical == 0) return true;
        int next = MathHelper.clamp(index + (vertical < 0 ? 1 : -1), 0, nights.size());
        if (next != index) {
            setIndex(next);
            play(SoundEvents.ITEM_SPYGLASS_USE, 0.8f + 0.1f * next, 0.8f);
        }
        return true;
    }

    // ---------------------------------------------------------------- tick

    private static boolean hasSky(ClientWorld world) {
        return world.getDimension().hasSkyLight() && !world.getDimension().hasCeiling();
    }

    private static void tick(MinecraftClient client) {
        prevZoom = zoom;
        zoom = MathHelper.clamp(zoom + (watching != null ? 0.2f : -0.25f), 0f, 1f);
        ClientPlayerEntity player = client.player;
        ClientWorld world = client.world;
        if (player == null || world == null) {
            close(false);
            return;
        }
        if (successTicks >= 0 && ++successTicks > SUCCESS_TICKS) successTicks = -1;
        if (watching != null) {
            boolean night = hasSky(world) && TelescopeMath.isNightTime(world.getTimeOfDay());
            if (!night || !player.isAlive() || client.options.sneakKey.isPressed()
                    || !world.getBlockState(watching).isOf(ModBlocks.TELESCOPE)
                    || player.getEyePos().squaredDistanceTo(Vec3d.ofCenter(watching)) > MAX_DISTANCE * MAX_DISTANCE) {
                if (!night) client.inGameHud.setOverlayMessage(Text.translatable("message.steveparty.telescope.need_night_sky"), false);
                close(true);
            }
        }
        TelescopePayloads.Night night = night();
        if (watching != null && night != null && !client.isPaused()) {
            replayTicks++;
            Vec3d look = player.getRotationVec(1f);
            double error = -1;
            for (int i = 0; i < TelescopeMath.STARS; i++) {
                double u = TelescopeMath.progress(i, replayTicks);
                if (TelescopeMath.fade(u) < 0.3f) continue;
                starDirection(night, i, u);
                double a = TelescopeMath.angle(look.x, look.y, look.z, POINT[0], POINT[1], POINT[2]);
                if (error < 0 || a < error) error = a;
            }
            prevGauge = gauge;
            gauge = TelescopeMath.step(gauge, error);
            boolean was = tracking;
            tracking = TelescopeMath.tracking(error);
            // a soft note climbing with the gauge while a star is held
            if (tracking && (!was || replayTicks % 8 == 0)) {
                play(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.6f + 1.2f * gauge, 0.35f);
            }
            if (gauge >= 1f) success(client, night);
        }
        if (watching == null || night == null) guideHint(client, player, world);
    }

    private static void starDirection(TelescopePayloads.Night night, int star, double progress) {
        TelescopeMath.direction(night.dirX(), night.dirZ(), TelescopeMath.side(night.id(), star),
                TelescopeMath.height(night.id(), star), progress, POINT);
    }

    private static int starColor(TelescopePayloads.Night night, int star) {
        int[] colours = night.colours();
        return colours.length == 0 ? 0xFFFFFF : MulaEntity.MulaVariant.byId(colours[star % colours.length]).getGlowColor();
    }

    /** The star was followed to the end: the server records the find; a chime and sparkles, for this player only. */
    private static void success(MinecraftClient client, TelescopePayloads.Night night) {
        ClientPlayNetworking.send(new TelescopePayloads.Found(night.id()));
        play(SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 1.5f, 1f);
        play(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1f);
        play(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.6f, 0.35f);
        if (watching != null && client.world != null) {
            Vec3d top = Vec3d.ofCenter(watching).add(0, 0.7, 0);
            int[] colours = night.colours();
            for (int i = 0; i < 24; i++) {
                MulaEntity.MulaVariant variant = MulaEntity.MulaVariant.byId(colours.length == 0 ? 0 : colours[i % colours.length]);
                double a = i * 2.39996, r = 0.25 + 0.03 * i;
                client.world.addParticle(variant.getTwinkle(), top.x + Math.cos(a) * r, top.y + 0.04 * i, top.z + Math.sin(a) * r,
                        Math.cos(a) * 0.02, 0.03, Math.sin(a) * 0.02);
            }
        }
        nights.remove(night);
        foundHere = true;
        setIndex(0);
        successTicks = 0;
    }

    /** Looking at one of his guide stars: its direction and distance in the action bar. */
    private static void guideHint(MinecraftClient client, ClientPlayerEntity player, ClientWorld world) {
        if (guides.isEmpty() || player.age % 10 != 0 || guideAlpha(world, 1f) < 0.2f) return;
        if (!world.isSkyVisible(BlockPos.ofFloored(player.getEyePos()))) return;
        Vec3d look = player.getRotationVec(1f), eye = player.getEyePos();
        for (TelescopePayloads.Guide guide : guides) {
            double dx = guide.x() + 0.5 - eye.x, dz = guide.z() + 0.5 - eye.z;
            guideDirection(dx, dz);
            if (TelescopeMath.angle(look.x, look.y, look.z, POINT[0], POINT[1], POINT[2]) > GUIDE_LOOK_DEGREES) continue;
            client.inGameHud.setOverlayMessage(TelescopeService.guideText(dx, dz), false);
            return;
        }
    }

    private static void guideDirection(double dx, double dz) {
        double distance = Math.sqrt(dx * dx + dz * dz);
        double elevation = Math.toRadians(TelescopeMath.guideElevation(distance));
        double flat = distance < 0.01 ? 0 : Math.cos(elevation) / distance;
        POINT[0] = dx * flat;
        POINT[1] = Math.sin(elevation);
        POINT[2] = dz * flat;
    }

    /** How visible the guide stars are: at night, in a dimension with a sky, dimmed by the rain. */
    private static float guideAlpha(ClientWorld world, float tickDelta) {
        if (!hasSky(world)) return 0;
        long t = Math.floorMod(world.getTimeOfDay(), 24000L);
        float in = MathHelper.clamp((t - 12700) / 500f, 0f, 1f), out = MathHelper.clamp((23300 - t) / 500f, 0f, 1f);
        return in * out * (1f - world.getRainGradient(tickDelta));
    }

    // ---------------------------------------------------------------- the sky

    private static BufferBuilder glowBuffer, heartBuffer;
    private static BufferAllocator heartAllocator;

    private static void renderSky(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null || client.player == null || !hasSky(world)) return;
        TelescopePayloads.Night night = watching != null ? night() : null;
        float tickDelta = context.tickCounter().getTickDelta(false);
        float guideAlpha = guides.isEmpty() ? 0 : guideAlpha(world, tickDelta);
        if (night == null && guideAlpha <= 0.01f) return;

        Vec3d camera = context.camera().getPos();
        // On the sky's dome: beyond the terrain drawn, but before the clouds (which would hide them)
        float dome = MathHelper.clamp(client.gameRenderer.getViewDistance() * 1.8f, 64f, 320f);
        float clouds = world.getDimensionEffects().getCloudsHeight();
        double toClouds = Float.isNaN(clouds) ? Double.MAX_VALUE : clouds - 2 - camera.y;
        float time = world.getTime() + tickDelta;

        RenderLayer glowLayer = RenderLayer.getEntityTranslucentEmissive(GLOW, false);
        RenderLayer heartLayer = RenderLayer.getEntityTranslucentEmissive(HEART, false);
        glowBuffer = Tessellator.getInstance().begin(glowLayer.getDrawMode(), glowLayer.getVertexFormat());
        // two buffers are filled at once: the second has its own allocator
        if (heartAllocator == null) heartAllocator = new BufferAllocator(8192);
        heartBuffer = new BufferBuilder(heartAllocator, heartLayer.getDrawMode(), heartLayer.getVertexFormat());

        if (night != null) {
            double ticks = replayTicks + (client.isPaused() ? 0 : tickDelta);
            float show = MathHelper.clamp(zoom * 2f - 1f, 0f, 1f);
            for (int i = 0; i < TelescopeMath.STARS && show > 0; i++) {
                double u = TelescopeMath.progress(i, ticks);
                float fade = TelescopeMath.fade(u) * show;
                if (fade <= 0.01f) continue;
                int color = starColor(night, i);
                int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
                float twinkle = 0.85f + 0.15f * MathHelper.sin(time * 1.7f + i);
                // the comet tail: where it was a little earlier, fading and shrinking
                int tail = 22;
                for (int k = tail; k >= 1; k--) {
                    double before = u - k * 0.0025;
                    if (before < 0) continue;
                    float s = 1f - k / (float) (tail + 1);
                    starDirection(night, i, before);
                    float d = distance(dome, toClouds);
                    quad(glowBuffer, d, 0.03f * (0.3f + 0.7f * s) * twinkle, 1f, r, g, b, (int) (255 * s * fade));
                    quad(heartBuffer, d, 0.011f * (0.3f + 0.7f * s) * twinkle, 1f, 255, 255, 255, (int) (230 * s * s * fade));
                }
                starDirection(night, i, u);
                float d = distance(dome, toClouds);
                quad(glowBuffer, d, 0.06f * twinkle, 1f, r, g, b, (int) (255 * fade));
                quad(glowBuffer, d, 0.045f * twinkle, 1f, r, g, b, (int) (255 * fade));
                quad(glowBuffer, d, 0.028f * twinkle, 1f, 255, 255, 255, (int) (230 * fade));
                quad(heartBuffer, d, 0.02f * twinkle, 1f, 255, 255, 255, (int) (255 * fade));
            }
        }
        if (guideAlpha > 0.01f) {
            int r = (GUIDE_COLOR >> 16) & 0xFF, g = (GUIDE_COLOR >> 8) & 0xFF, b = GUIDE_COLOR & 0xFF;
            for (TelescopePayloads.Guide guide : guides) {
                double dx = guide.x() + 0.5 - camera.x, dz = guide.z() + 0.5 - camera.z;
                float bright = TelescopeMath.guideBrightness(Math.sqrt(dx * dx + dz * dz));
                guideDirection(dx, dz);
                float d = distance(dome, toClouds);
                float pulse = 0.8f + 0.2f * MathHelper.sin(time * 0.35f + guide.id()) + 0.08f * MathHelper.sin(time * 1.9f + guide.id() * 3);
                float size = 0.03f * bright * pulse;
                int alpha = (int) (255 * guideAlpha * (0.6f + 0.4f * bright));
                quad(glowBuffer, d, size * 1.6f, 1f, r, g, b, alpha);
                // a four-pointed sparkle: two long thin glows crossed
                quad(glowBuffer, d, size * 3.4f, 0.16f, 255, 244, 200, (int) (alpha * 0.9f));
                quad(glowBuffer, d, size * 0.55f, 6.2f, 255, 244, 200, (int) (alpha * 0.9f));
                quad(heartBuffer, d, size * 0.6f, 1f, 255, 255, 255, alpha);
            }
        }

        // After the whole world (clouds included): the camera's rotation is no longer applied
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.mul(context.positionMatrix());
        Fog fog = RenderSystem.getShaderFog();
        RenderSystem.setShaderFog(Fog.DUMMY);
        try {
            BuiltBuffer built = glowBuffer.endNullable();
            if (built != null) glowLayer.draw(built);
            built = heartBuffer.endNullable();
            if (built != null) heartLayer.draw(built);
        } finally {
            RenderSystem.setShaderFog(fog);
            modelView.popMatrix();
            glowBuffer = heartBuffer = null;
        }
    }

    /** How far along POINT the star is drawn: on the dome, or just under the clouds when they are nearer. */
    private static float distance(float dome, double toClouds) {
        if (toClouds > 0 && POINT[1] > 0.01) return (float) Math.min(dome, Math.max(16, toClouds / POINT[1]));
        return dome;
    }

    /**
     * A glow facing the camera, at {@code distance} along POINT, {@code half} wide (x the distance: an angle) and
     * {@code stretch} times as high.
     */
    private static void quad(VertexConsumer vertices, float distance, float half, float stretch, int r, int g, int b, int alpha) {
        if (alpha <= 0) return;
        float x = (float) POINT[0], y = (float) POINT[1], z = (float) POINT[2];
        // right = up x direction (level), top = direction x right
        float rx = z, rz = -x, rl = MathHelper.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4f) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        float ux = y * rz, uy = z * rx - x * rz, uz = -y * rx;
        float w = half * distance, h = w * stretch;
        float cx = x * distance, cy = y * distance, cz = z * distance;
        vertex(vertices, cx - rx * w - ux * h, cy - uy * h, cz - rz * w - uz * h, 0f, 1f, r, g, b, alpha);
        vertex(vertices, cx + rx * w - ux * h, cy - uy * h, cz + rz * w - uz * h, 1f, 1f, r, g, b, alpha);
        vertex(vertices, cx + rx * w + ux * h, cy + uy * h, cz + rz * w + uz * h, 1f, 0f, r, g, b, alpha);
        vertex(vertices, cx - rx * w + ux * h, cy + uy * h, cz - rz * w + uz * h, 0f, 0f, r, g, b, alpha);
    }

    private static void vertex(VertexConsumer vertices, float x, float y, float z, float u, float v, int r, int g, int b, int alpha) {
        vertices.vertex(x, y, z).color(r, g, b, Math.min(255, alpha)).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(0xF000F0).normal(0f, 1f, 0f);
    }

    // ---------------------------------------------------------------- the view

    /** A dot on the rim of the scope, towards the nearest star that is out of the view (in that star's colour). */
    private static void pointer(DrawContext context, ClientPlayerEntity player, TelescopePayloads.Night night, float tickDelta,
                                int cx, int cy, float radius) {
        Vec3d look = player.getRotationVec(tickDelta);
        double best = Double.MAX_VALUE, bx = 0, by = 0, bz = 0;
        int star = -1;
        for (int i = 0; i < TelescopeMath.STARS; i++) {
            double u = TelescopeMath.progress(i, replayTicks + tickDelta);
            if (TelescopeMath.fade(u) < 0.3f) continue;
            starDirection(night, i, u);
            double a = TelescopeMath.angle(look.x, look.y, look.z, POINT[0], POINT[1], POINT[2]);
            if (a < best) {
                best = a;
                star = i;
                bx = POINT[0];
                by = POINT[1];
                bz = POINT[2];
            }
        }
        if (star < 0 || best < POINTER_DEGREES) return;
        // the star's direction on the screen: along the camera's right and up
        double rx = -look.z, rz = look.x, rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-4) return;
        rx /= rl;
        rz /= rl;
        double ux = -look.y * rz, uy = look.x * rz - look.z * rx, uz = look.y * rx;
        double sx = bx * rx + bz * rz, sy = bx * ux + by * uy + bz * uz, sl = Math.sqrt(sx * sx + sy * sy);
        if (sl < 1e-4) return;
        int x = cx + (int) Math.round(sx / sl * radius), y = cy - (int) Math.round(sy / sl * radius);
        int color = 0xFF000000 | starColor(night, star);
        context.fill(x - 2, y - 1, x + 3, y + 2, color);
        context.fill(x - 1, y - 2, x + 2, y + 3, color);
        context.fill(x - 1, y - 1, x + 2, y + 2, 0xFFFFFFFF);
    }

    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        float tickDelta = tickCounter.getTickDelta(false);
        float z = MathHelper.lerp(tickDelta, prevZoom, zoom);
        if (z <= 0.01f || client.player == null) return;
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();

        // The scope: a round opening, growing in as the eye comes to it
        float scale = MathHelper.lerp(z, 0.5f, 1.0f);
        float side = Math.min(width, height);
        int size = MathHelper.floor(side * Math.min(width / side, height / side) * scale);
        int left = (width - size) / 2, top = (height - size) / 2, right = left + size, bottom = top + size;
        context.drawTexture(RenderLayer::getGuiTextured, SCOPE, left, top, 0f, 0f, size, size, size, size);
        context.fill(0, bottom, width, height, 0xFF000000);
        context.fill(0, 0, width, top, 0xFF000000);
        context.fill(0, top, left, bottom, 0xFF000000);
        context.fill(right, top, width, bottom, 0xFF000000);
        if (watching == null) return;

        TextRenderer text = client.textRenderer;
        int cx = width / 2, cy = height / 2;
        TelescopePayloads.Night night = night();
        // inside the opening (the ring is 8 of the texture's 128 pixels from its edge)
        int textTop = Math.max(4, top + size * 12 / 128);
        context.drawCenteredTextWithShadow(text, Text.translatable("gui.steveparty.telescope.title").formatted(Formatting.GOLD), cx, textTop, 0xFFFFFF);
        Text when;
        if (night == null) {
            when = Text.translatable("gui.steveparty.telescope.tonight", today);
        } else {
            long ago = Math.max(0, today - night.day());
            Text agoText = ago == 0 ? Text.translatable("gui.steveparty.telescope.ago.tonight")
                    : ago == 1 ? Text.translatable("gui.steveparty.telescope.ago.last_night")
                    : Text.translatable("gui.steveparty.telescope.ago.nights", ago);
            when = Text.translatable("gui.steveparty.telescope.night", night.day(), agoText, index, nights.size());
        }
        context.drawCenteredTextWithShadow(text, when, cx, textTop + 12, 0xFFFFFF);

        Text hint;
        if (successTicks >= 0) hint = Text.translatable("gui.steveparty.telescope.found").formatted(Formatting.YELLOW);
        else if (night != null) hint = Text.translatable("gui.steveparty.telescope.hint.track");
        else if (nights.isEmpty()) hint = Text.translatable(foundHere ? "gui.steveparty.telescope.hint.all_found" : "gui.steveparty.telescope.hint.none");
        else hint = Text.translatable("gui.steveparty.telescope.hint.wheel", nights.size());
        context.drawCenteredTextWithShadow(text, hint, cx, height - 58, 0xFFFFFF);
        context.drawCenteredTextWithShadow(text, Text.translatable("gui.steveparty.telescope.hint.leave").formatted(Formatting.GRAY),
                cx, height - 46, 0xFFFFFF);

        if (night != null) {
            // The reticle: four corners, golden while a star is held, and the gauge under it
            int color = tracking ? 0xFFFFD75A : 0xB0FFFFFF;
            int r = 12, l = 4;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    int x = cx + sx * r, y = cy + sy * r;
                    context.fill(Math.min(x, x - sx * l), y, Math.max(x, x - sx * l) + 1, y + 1, color);
                    context.fill(x, Math.min(y, y - sy * l), x + 1, Math.max(y, y - sy * l) + 1, color);
                }
            }
            pointer(context, client.player, night, tickDelta, cx, cy, size * 0.4f);
            float filled = MathHelper.lerp(tickDelta, prevGauge, gauge);
            int half = 30, y = cy + r + 8;
            context.fill(cx - half - 1, y - 1, cx + half + 1, y + 4, 0xA0000000);
            context.fill(cx - half, y, cx - half + Math.round(2 * half * filled), y + 3, tracking ? 0xFFFFD75A : 0xFFB09A5A);
        }
        if (successTicks >= 0) {
            // Sparkles bursting from the reticle
            float t = (successTicks + tickDelta) / SUCCESS_TICKS;
            int alpha = MathHelper.clamp((int) (255 * (1 - t) * 1.4f), 0, 255);
            for (int i = 0; i < 12 && alpha > 4; i++) {
                double a = i * 2.39996 + t * 1.5;
                float d = (18 + 9 * (i % 4)) * (0.3f + 2.2f * t);
                int s = 6 + (i % 3) * 3;
                int x = cx + Math.round((float) Math.cos(a) * d) - s / 2, y = cy + Math.round((float) Math.sin(a) * d) - s / 2;
                context.drawTexture(RenderLayer::getGuiTextured, HEART, x, y, 0f, 0f, s, s, s, s,
                        ColorHelper.withAlpha(alpha, (i & 1) == 0 ? GUIDE_COLOR : 0xFFFFFF));
            }
        }
    }
}
