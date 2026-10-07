package fr.lordfinn.steveparty.client.gui.wheel;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * A radial wheel, in the manner of CS:GO's buy wheel, shared by the tools held in hand (the Tile Linker Brush, the
 * Stencil Hammer: each gives its {@link Layout} through a {@link Provider}).
 * <ul>
 *     <li>A left click with the tool opens it, centred on the screen. The mouse then moves a cursor on the wheel, not
 *     the camera; the player keeps walking and sneaking (no screen opens, no key is released).</li>
 *     <li>Long click: releasing the button picks the sector under the cursor and closes the wheel. Short click: the
 *     wheel stays open, the next click picks (and closes).</li>
 *     <li>The sector under the cursor grows and its explanation shows under the wheel. Nothing under the cursor (the
 *     middle): closing picks nothing. A right click, another item in hand or a screen closes it too.</li>
 * </ul>
 * The pick runs the sector's action (client side: it sends its packet to the server).
 */
public final class ToolWheel {
    /** Held longer than this (ms), the button picks on release; shorter, the wheel stays open. */
    private static final long LONG_PRESS_MS = 220;
    /** Degrees left empty between two sectors. */
    private static final float GAP = 1.4f;
    /** How much a hovered sector grows outward. */
    private static final float HOVER_GROW = 6;

    /** Something drawn at the middle of a sector (an item, a number, a stencil...). */
    @FunctionalInterface
    public interface Icon {
        void draw(DrawContext context, int centerX, int centerY);
    }

    /**
     * A sector of the wheel.
     *
     * @param label       its name (bold, in the middle of the wheel while hovered)
     * @param description its explanation (under the wheel while hovered)
     * @param color       its fill (ARGB)
     * @param icon        drawn at its middle
     * @param selected    the tool's current setting (marked on the edge)
     * @param enabled     false: shown greyed, picks nothing
     * @param pick        what the pick does
     */
    public record Sector(Text label, Text description, int color, @Nullable Icon icon, boolean selected, boolean enabled,
                         Runnable pick) {
    }

    /**
     * Sectors spread evenly over an arc of a ring, from {@code from} to {@code to} degrees (0: top, clockwise; {@code
     * from} may be negative).
     */
    public record Arc(int ring, float from, float to, List<Sector> sectors) {
    }

    /**
     * A ring: between {@code inner} and {@code outer} (GUI pixels from the centre).
     */
    public record Ring(float inner, float outer) {
    }

    /**
     * The look of a wheel.
     *
     * @param backdrop  the disc behind the rings
     * @param hub       the middle disc
     * @param edge      outlines and separators
     * @param highlight outline of the hovered sector, mark of the selected one
     * @param text      text colour of the explanation plate
     * @param plate     background of the explanation plate
     */
    public record Theme(int backdrop, int hub, int edge, int highlight, int text, int plate) {
    }

    /** What a wheel shows: its rings, its sectors, its title (in the hub when nothing is hovered). */
    public record Layout(Theme theme, List<Ring> rings, List<Arc> arcs, Text title, @Nullable Icon hubIcon) {
    }

    /** A tool with a wheel. */
    public interface Provider {
        boolean handles(ItemStack stack);

        Layout layout(MinecraftClient client, ItemStack stack);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();

    private static @Nullable Provider open;
    private static int openSlot;
    private static long openedAt;
    private static boolean sticky;
    private static double cursorX, cursorY;
    private static @Nullable Layout layout;

    private ToolWheel() {
    }

    public static void register(Provider provider) {
        PROVIDERS.add(provider);
    }

    public static void initialize() {
        // The left click of a tool with a wheel: opens it, or picks when it stayed open; never attacks nor breaks
        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (client.currentScreen != null) return false;
            if (open != null) {
                if (clickCount > 0 && sticky) pickAndClose(client);
                return true;
            }
            Provider provider = provider(player.getMainHandStack());
            if (provider == null) return false;
            if (clickCount > 0) open(client, provider);
            return true;
        });
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            // A right click closes the wheel without picking (and does not use the tool)
            if (open != null && client.options.useKey.wasPressed()) {
                while (client.options.useKey.wasPressed()) {
                    // drained
                }
                close();
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(ToolWheel::tick);
        HudRenderCallback.EVENT.register(ToolWheel::render);
    }

    public static boolean isOpen() {
        return open != null;
    }

    private static @Nullable Provider provider(ItemStack stack) {
        for (Provider provider : PROVIDERS) if (provider.handles(stack)) return provider;
        return null;
    }

    private static void open(MinecraftClient client, Provider provider) {
        if (client.player == null) return;
        open = provider;
        openSlot = client.player.getInventory().selectedSlot;
        openedAt = System.currentTimeMillis();
        sticky = false;
        cursorX = 0;
        cursorY = 0;
        layout = provider.layout(client, client.player.getMainHandStack());
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.5F, 0.35F));
    }

    private static void close() {
        open = null;
        layout = null;
    }

    private static void tick(MinecraftClient client) {
        if (open == null) return;
        if (client.player == null || client.currentScreen != null || !client.player.isAlive()
                || client.player.getInventory().selectedSlot != openSlot || !open.handles(client.player.getMainHandStack())) {
            close();
            return;
        }
        layout = open.layout(client, client.player.getMainHandStack());
        if (!sticky && !client.options.attackKey.isPressed()) {
            if (System.currentTimeMillis() - openedAt < LONG_PRESS_MS) sticky = true;
            else pickAndClose(client);
        }
    }

    private static void pickAndClose(MinecraftClient client) {
        Sector hovered = hovered();
        close();
        if (hovered == null || !hovered.enabled()) return;
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.9F, 0.5F));
        hovered.pick().run();
    }

    /**
     * Mouse movement while the wheel is open: moves its cursor (in screen pixels, as the camera would have turned).
     *
     * @return true if the wheel took it (the camera stays still)
     */
    public static boolean moveCursor(double dx, double dy) {
        if (open == null || layout == null) return false;
        double scale = MinecraftClient.getInstance().getWindow().getScaleFactor();
        cursorX += dx / scale;
        cursorY += dy / scale;
        // Kept on the wheel: a hard flick still lands on the outer ring
        double max = outerRadius(layout) + 8;
        double length = Math.sqrt(cursorX * cursorX + cursorY * cursorY);
        if (length > max) {
            cursorX *= max / length;
            cursorY *= max / length;
        }
        return true;
    }

    // ---------------------------------------------------------------- hover

    private static float outerRadius(Layout layout) {
        float outer = 0;
        for (Ring ring : layout.rings()) outer = Math.max(outer, ring.outer());
        return outer;
    }

    /** The hovered arc and sector index, or null. */
    private record Hover(Arc arc, int index) {
        Sector sector() {
            return arc.sectors().get(index);
        }
    }

    private static @Nullable Sector hovered() {
        Hover hover = hover();
        return hover == null ? null : hover.sector();
    }

    private static @Nullable Hover hover() {
        Layout layout = ToolWheel.layout;
        if (layout == null || layout.rings().isEmpty()) return null;
        double length = Math.sqrt(cursorX * cursorX + cursorY * cursorY);
        // The hub: nothing
        if (length < layout.rings().getFirst().inner() * 0.6) return null;
        // The ring the cursor is over (between two rings: the nearer)
        int ring = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < layout.rings().size(); i++) {
            Ring r = layout.rings().get(i);
            double distance = length < r.inner() ? r.inner() - length : length > r.outer() ? length - r.outer() : 0;
            if (distance < best) {
                best = distance;
                ring = i;
            }
        }
        float angle = (float) Math.toDegrees(Math.atan2(cursorX, -cursorY));
        for (Arc arc : layout.arcs()) {
            if (arc.ring() != ring || arc.sectors().isEmpty()) continue;
            float span = arc.to() - arc.from();
            float relative = MathHelper.floorMod(angle - arc.from(), 360f);
            if (relative >= span) continue;
            int index = Math.min(arc.sectors().size() - 1, (int) (relative / (span / arc.sectors().size())));
            return new Hover(arc, index);
        }
        return null;
    }

    // ---------------------------------------------------------------- drawing

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        Layout layout = ToolWheel.layout;
        if (open == null || layout == null || client.options.hudHidden) return;
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        float outer = outerRadius(layout);
        // Smaller on a small screen (large GUI scale): the wheel and its explanation fit
        float fit = Math.min(1f, (height / 2f - 34) / (outer + HOVER_GROW));
        int cx = width / 2, cy = height / 2;
        Theme theme = layout.theme();
        Hover hover = hover();

        context.getMatrices().push();
        context.getMatrices().translate(cx, cy, 0);
        context.getMatrices().scale(fit, fit, 1);
        Matrix4f matrix = context.getMatrices().peek().getPositionMatrix();
        VertexConsumer consumer = context.getVertexConsumers().getBuffer(RenderLayer.getGui());

        // Backdrop and hub
        annulus(consumer, matrix, 0, outer + 3, 0, 360, theme.backdrop());
        Ring first = layout.rings().getFirst();
        annulus(consumer, matrix, 0, first.inner() - 3, 0, 360, theme.hub());
        annulus(consumer, matrix, first.inner() - 4, first.inner() - 3, 0, 360, theme.edge());

        // Sectors
        for (Arc arc : layout.arcs()) {
            Ring ring = layout.rings().get(arc.ring());
            float step = (arc.to() - arc.from()) / Math.max(1, arc.sectors().size());
            for (int i = 0; i < arc.sectors().size(); i++) {
                Sector sector = arc.sectors().get(i);
                float a0 = arc.from() + step * i + GAP / 2, a1 = arc.from() + step * (i + 1) - GAP / 2;
                boolean hovered = hover != null && hover.arc() == arc && hover.index() == i;
                float grow = hovered && sector.enabled() ? HOVER_GROW : 0;
                int fill = sector.enabled() ? (hovered ? brighten(sector.color()) : sector.color()) : greyed(sector.color());
                annulus(consumer, matrix, ring.inner(), ring.outer() + grow, a0, a1, fill);
                if (hovered && sector.enabled()) {
                    annulus(consumer, matrix, ring.outer() + grow, ring.outer() + grow + 1.5f, a0, a1, theme.highlight());
                    annulus(consumer, matrix, ring.inner() - 1.5f, ring.inner(), a0, a1, theme.highlight());
                }
                if (sector.selected()) {
                    float inset = (ring.outer() - ring.inner()) * 0.12f;
                    annulus(consumer, matrix, ring.outer() + grow - inset - 2.5f, ring.outer() + grow - inset, a0 + 2, a1 - 2, theme.highlight());
                }
            }
        }
        context.draw();

        // Icons
        for (Arc arc : layout.arcs()) {
            Ring ring = layout.rings().get(arc.ring());
            float step = (arc.to() - arc.from()) / Math.max(1, arc.sectors().size());
            for (int i = 0; i < arc.sectors().size(); i++) {
                Sector sector = arc.sectors().get(i);
                if (sector.icon() == null) continue;
                boolean hovered = hover != null && hover.arc() == arc && hover.index() == i;
                double mid = Math.toRadians(arc.from() + step * (i + 0.5));
                double r = (ring.inner() + ring.outer()) / 2 + (hovered ? HOVER_GROW / 2 : 0);
                sector.icon().draw(context, (int) Math.round(Math.sin(mid) * r), (int) Math.round(-Math.cos(mid) * r));
            }
        }

        // Where the mouse points: a small dot
        int dotX = (int) Math.round(cursorX), dotY = (int) Math.round(cursorY);
        context.fill(dotX - 2, dotY - 2, dotX + 2, dotY + 2, theme.edge());
        context.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, theme.highlight());

        // Hub: the hovered sector's name, or the wheel's title
        Text hubText = hover != null ? hover.sector().label() : layout.title();
        float hubWidth = (first.inner() - 6) * 2;
        List<OrderedText> hubLines = client.textRenderer.wrapLines(hubText.copy().formatted(Formatting.BOLD), (int) (hubWidth / 0.75f));
        if (hover == null && layout.hubIcon() != null) layout.hubIcon().draw(context, 0, -10);
        context.getMatrices().push();
        context.getMatrices().scale(0.75f, 0.75f, 1);
        List<OrderedText> shown = hubLines.subList(0, Math.min(3, hubLines.size()));
        // In the scaled space: lines 10 high, centred (below the icon when there is one)
        int lineY = hover == null && layout.hubIcon() != null ? 2 : -shown.size() * 5;
        for (OrderedText line : shown) {
            context.drawText(client.textRenderer, line, -client.textRenderer.getWidth(line) / 2, lineY, 0xFFFFFFFF, true);
            lineY += 10;
        }
        context.getMatrices().pop();
        context.getMatrices().pop();

        // The explanation, under the wheel
        if (hover != null) {
            Text description = hover.sector().description();
            int plateWidth = Math.min(width - 16, 260);
            List<OrderedText> lines = client.textRenderer.wrapLines(description, plateWidth - 12);
            int plateHeight = lines.size() * 10 + 8;
            int top = Math.min(height - plateHeight - 4, (int) (cy + (outer + HOVER_GROW + 6) * fit));
            int left = cx - plateWidth / 2;
            context.fill(left, top, left + plateWidth, top + plateHeight, theme.plate());
            context.drawBorder(left, top, plateWidth, plateHeight, theme.edge());
            int y = top + 5;
            for (OrderedText line : lines) {
                context.drawText(client.textRenderer, line, cx - client.textRenderer.getWidth(line) / 2, y, theme.text(), false);
                y += 10;
            }
        }
    }

    /** A ring sector from {@code a0} to {@code a1} degrees (0: top, clockwise), between radii {@code r0} and {@code r1}. */
    private static void annulus(VertexConsumer consumer, Matrix4f matrix, float r0, float r1, float a0, float a1, int argb) {
        if (a1 <= a0 || r1 <= r0) return;
        int steps = Math.max(1, MathHelper.ceil((a1 - a0) / 4f));
        for (int i = 0; i < steps; i++) {
            double b0 = Math.toRadians(a0 + (a1 - a0) * i / steps), b1 = Math.toRadians(a0 + (a1 - a0) * (i + 1) / steps);
            float s0 = (float) Math.sin(b0), c0 = (float) -Math.cos(b0), s1 = (float) Math.sin(b1), c1 = (float) -Math.cos(b1);
            // Same winding as DrawContext#fill (counter-clockwise on screen): inner b0, inner b1, outer b1, outer b0
            consumer.vertex(matrix, s0 * r0, c0 * r0, 0).color(argb);
            consumer.vertex(matrix, s1 * r0, c1 * r0, 0).color(argb);
            consumer.vertex(matrix, s1 * r1, c1 * r1, 0).color(argb);
            consumer.vertex(matrix, s0 * r1, c0 * r1, 0).color(argb);
        }
    }

    private static int brighten(int argb) {
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        r = r + (255 - r) * 2 / 5;
        g = g + (255 - g) * 2 / 5;
        b = b + (255 - b) * 2 / 5;
        return Math.max(a, 0xE0) << 24 | r << 16 | g << 8 | b;
    }

    private static int greyed(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int grey = (r * 3 + g * 6 + b) / 10 / 2 + 30;
        return 0x90 << 24 | grey << 16 | grey << 8 | grey;
    }
}
