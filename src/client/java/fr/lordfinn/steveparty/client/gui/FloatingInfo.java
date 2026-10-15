package fr.lordfinn.steveparty.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The one way the mod shows info floating in the world: a panel drawn over what it is about (a board space's panel,
 * the outcome roulette), falling back on a small card on the left of the screen, half way down, while that panel is
 * out of sight. Every feature showing such info goes through it, so that they all decide alike when the panel is seen
 * and all fall back on the same card:
 * <ol>
 *     <li>in the world renderer, {@link #inSight} says whether the panel can be drawn there (near enough, in front of
 *     the camera, within its field of view); when it is drawn, {@link #drawnInWorld} says so;</li>
 *     <li>in the HUD, {@link #needsCard} says whether it was not drawn lately: then {@link #card} draws its card.</li>
 * </ol>
 * <pre>{@code
 * // world render
 * if (FloatingInfo.inSight(camera, middle, FAR)) { drawPanel(...); FloatingInfo.drawnInWorld(KEY); }
 * // HUD
 * if (FloatingInfo.needsCard(KEY)) FloatingInfo.card(context, KEY, width, height, ToolHud.Plate.TEAL, 1, 0, card -> ...);
 * }</pre>
 * The card says what the panel would, in short (show, don't tell: a title, a few lines with their icons, no
 * coordinates: see {@link #withoutPlaces}), one card pixel per HUD pixel ({@link #SCALE}: never a fraction, which
 * blurs it). It stays out of
 * the way of the crosshair, of a minimap (top left), of the chat, of the standings (right) and of the tool HUD. When
 * several want the slot, they stack down from the newest, centred on the screen's middle, {@link #MAX_CARDS} at most
 * and no more than fit between the boss bars and the chat ({@link #CLEAR}).
 */
public final class FloatingInfo {
    /** A card pixel, in HUD pixels: a whole number, every pixel of the card on the screen's pixels. */
    public static final float SCALE = 1f;
    /** Card pixels: the margin inside the plate, the widest a card gets (a longer line scrolls). */
    public static final int PAD = 5, MAX_WIDTH = 200;
    /** Cards shown at once at most (the newest). */
    public static final int MAX_CARDS = 3;
    /** HUD pixels: from the screen's edge, between two cards. */
    private static final int MARGIN = 6, GAP = 3;
    /** HUD pixels kept clear above and below the cards: the boss bars on top, the hotbar and the chat below. */
    private static final int CLEAR = 60;
    /** A panel drawn in the world this recently (nanoseconds) is seen: no card. */
    private static final long SEEN_NANOS = 150_000_000L;
    /** Frames further apart than this (nanoseconds, the HUD hidden...): the cards start over. */
    private static final long FRAME_GAP_NANOS = 250_000_000L;
    /** The coordinates of a place (see BoardText.pos): the world panel tells them, the card doesn't. */
    private static final String PLACE_KEY = "message.steveparty.wrench.pos";

    private static final Map<Object, Long> DRAWN_AT = new HashMap<>();
    /** When each card shown came (its rank: the newest on top). */
    private static final Map<Object, Long> CARD_SINCE = new HashMap<>();

    private record Slot(Object key, int height) {
    }

    /** The frame the cards are being asked in, the cards it asked, and the last frame's, laid out. */
    private static DrawContext frame;
    private static long frameAt;
    private static List<Slot> asked = new ArrayList<>();
    private static List<Slot> laidOut = List.of();

    private FloatingInfo() {
    }

    /** What a card shows, drawn in card pixels from its top left corner (the plate already under it). */
    @FunctionalInterface
    public interface Content {
        void draw(DrawContext context);
    }

    // ---------------------------------------------------------------- in the world

    /**
     * Whether a panel whose middle is at {@code point} can be drawn in the world: no farther than {@code maxDistance}
     * blocks from the camera, in front of it and within its field of view.
     */
    public static boolean inSight(Camera camera, Vec3d point, double maxDistance) {
        Vec3d to = point.subtract(camera.getPos());
        if (to.lengthSquared() > maxDistance * maxDistance) return false;
        Vec3d look = Vec3d.fromPolar(camera.getPitch(), camera.getYaw());
        double ahead = to.dotProduct(look);
        if (ahead <= 0.5) return false;
        Vec3d right = look.crossProduct(new Vec3d(0, 1, 0));
        if (right.lengthSquared() < 1.0E-6) return true; // looking straight up or down at it
        right = right.normalize();
        Vec3d up = right.crossProduct(look).normalize();
        MinecraftClient client = MinecraftClient.getInstance();
        double vertical = Math.tan(Math.toRadians(client.options.getFov().getValue() / 2.0));
        double aspect = client.getWindow().getFramebufferWidth() / (double) Math.max(1, client.getWindow().getFramebufferHeight());
        return Math.abs(to.dotProduct(right)) / ahead < vertical * aspect && Math.abs(to.dotProduct(up)) / ahead < vertical;
    }

    /** The panel of {@code key} was drawn in the world this frame: its card stays away. */
    public static void drawnInWorld(Object key) {
        DRAWN_AT.put(key, System.nanoTime());
    }

    /** Whether the panel of {@code key} was not drawn in the world lately: its card stands in for it. */
    public static boolean needsCard(Object key) {
        Long at = DRAWN_AT.get(key);
        if (at == null) return true;
        if (System.nanoTime() - at < SEEN_NANOS) return false;
        DRAWN_AT.remove(key);
        return true;
    }

    // ---------------------------------------------------------------- on the screen

    /**
     * Draws the card of {@code key}, {@code width} x {@code height} card pixels on a {@code plate}, in its place among
     * the cards shown (laid out on the previous frame: a new card shows from the next one).
     *
     * @param slide how far off its place it is, towards the screen's edge (HUD pixels), sliding in
     */
    public static void card(DrawContext context, Object key, int width, int height, ToolHud.Plate plate, float alpha, float slide,
                            Content content) {
        long now = System.nanoTime();
        if (context != frame) nextFrame(context, now);
        CARD_SINCE.putIfAbsent(key, now);
        asked.add(new Slot(key, Math.round(height * SCALE)));
        int total = -GAP, top = -1;
        for (Slot slot : laidOut) {
            if (slot.key.equals(key)) top = total + GAP;
            total += slot.height + GAP;
        }
        if (top < 0) return;
        int y = context.getScaledWindowHeight() / 2 - total / 2 + top;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(MARGIN - Math.round(slide), y, 0);
        matrices.scale(SCALE, SCALE, 1);
        HudDepth.onTop(context, () -> {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1f, 1f, 1f, MathHelper.clamp(alpha, 0f, 1f));
            context.drawGuiTexture(plate.sprite, 0, 0, width, height);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.disableBlend();
        });
        content.draw(context);
        matrices.pop();
    }

    /** A new frame: the cards the last one asked for are laid out, the newest first; the ones gone are forgotten. */
    private static void nextFrame(DrawContext context, long now) {
        List<Slot> last = now - frameAt > FRAME_GAP_NANOS ? List.of() : asked;
        frame = context;
        frameAt = now;
        CARD_SINCE.keySet().removeIf(key -> last.stream().noneMatch(slot -> slot.key.equals(key)));
        List<Slot> sorted = new ArrayList<>(last);
        sorted.sort(Comparator.comparingLong((Slot slot) -> CARD_SINCE.getOrDefault(slot.key, now)).reversed());
        // The newest first, as many as fit (the newest always shows)
        int room = context.getScaledWindowHeight() - 2 * CLEAR, used = -GAP;
        List<Slot> shown = new ArrayList<>();
        for (Slot slot : sorted) {
            if (shown.size() == MAX_CARDS || (!shown.isEmpty() && used + GAP + slot.height > room)) break;
            shown.add(slot);
            used += GAP + slot.height;
        }
        laidOut = List.copyOf(shown);
        asked = new ArrayList<>();
    }

    /** {@code text} without the coordinates it tells (« in the bubble (12, 64, -3) » → « in the bubble »). */
    public static Text withoutPlaces(Text text) {
        MutableText copy;
        if (text.getContent() instanceof TranslatableTextContent translatable) {
            if (translatable.getKey().equals(PLACE_KEY)) return Text.empty();
            Object[] args = translatable.getArgs().clone();
            for (int i = 0; i < args.length; i++) if (args[i] instanceof Text arg) args[i] = withoutPlaces(arg);
            copy = MutableText.of(new TranslatableTextContent(translatable.getKey(), translatable.getFallback(), args));
        } else {
            copy = MutableText.of(text.getContent());
        }
        copy.setStyle(text.getStyle());
        for (Text sibling : text.getSiblings()) copy.append(withoutPlaces(sibling));
        return copy;
    }
}
