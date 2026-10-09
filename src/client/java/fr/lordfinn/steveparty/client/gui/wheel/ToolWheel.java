package fr.lordfinn.steveparty.client.gui.wheel;

import fr.lordfinn.steveparty.utils.Argb;
import fr.lordfinn.steveparty.client.gui.GuiItems;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A radial wheel shared by the tools held in hand (the Tile Linker Brush, the Stencil Hammer: each gives its
 * {@link Layout} through a {@link Provider}), drawn in pixel art like the top part of the mod's block screens (the
 * cash register's blue, the party controller's purple...): a panel in the tool's own colour ({@link Theme}), its sectors
 * plates of that colour with a dark outline and a light bevel top left, the same 2-pixel gap between any two, a
 * coloured sector (a dye, a redstone level) in its own colour; no smoothing.
 * <ul>
 *     <li>A left click with the tool opens it, centred on the screen. The mouse then moves a cursor on the wheel, not
 *     the camera; the player keeps walking and sneaking (no screen opens, no key is released).</li>
 *     <li>Long click: releasing the button picks the sector under the cursor and closes the wheel. Short click: the
 *     wheel stays open, the next click picks (and closes).</li>
 *     <li>The sector under the cursor grows and a vanilla tooltip names it, in a few words. A sector may lead to
 *     another page of the wheel (the wheel stays open on it; its hub goes back). Nothing under the cursor: closing
 *     picks nothing. A right click, another item in hand or a screen closes it too.</li>
 *     <li>The first times a tool's wheel opens, a single sector is put forward (see {@link Layout#featured()}): the
 *     others are dimmed, and its tooltip shows until something is hovered.</li>
 * </ul>
 * The pick runs the sector's action (client side: it sends its packet to the server).
 */
public final class ToolWheel {
    /** A sector's explanation under its name wraps at this width (pixels). */
    private static final int HINT_WIDTH = 200;

    /** Held longer than this (ms), the button picks on release; shorter, the wheel stays open. */
    private static final long LONG_PRESS_MS = 220;
    /** Half the gap between two sectors (pixels): the gap is as wide everywhere. */
    private static final double HALF_GAP = 1.0;
    /** How much a hovered sector grows outward (pixels). */
    private static final int HOVER_GROW = 3;

    private static final int GOLD_OUTLINE = 0xFF5B2E00, GOLD_LIGHT = 0xFFFFF87E, GOLD = 0xFFFFD83D;

    /**
     * The colours of a tool's panel, as the mod's block screens build theirs (the cash register: outline 001f40, rim
     * 0064b5, body 45a1cf, light 69c2f1, its slots 007cad in a 0064b5 edge).
     *
     * @param outline the darkest line, all round
     * @param rim     the dark tint: between the sectors, the panel's border, a slot's edge
     * @param body    the panel and its plates
     * @param light   the bevel top left
     * @param slot    a slot sunk into the panel
     */
    public record Theme(int outline, int rim, int body, int light, int slot) {
        public static Theme of(int outline, int rim, int body, int light, int slot) {
            return new Theme(0xFF000000 | outline, 0xFF000000 | rim, 0xFF000000 | body, 0xFF000000 | light, 0xFF000000 | slot);
        }
    }

    /** The Stencil Hammer's wood (its handle). */
    public static final Theme WOOD = Theme.of(0x2a180c, 0x6e482c, 0x9c6b3f, 0xc4925a, 0x7a5232);
    /** The Tile Linker Brush's red (its bristles' paint). */
    public static final Theme RED = Theme.of(0x3a0508, 0x8a1a20, 0xc8333b, 0xe86a6f, 0x9e2229);
    /** A slate grey frame for wheels whose sectors each have their own colour (the brush's). */
    public static final Theme SLATE = Theme.of(0x1c1d22, 0x4a4d57, 0x7d808c, 0xb5b8c2, 0x5d606b);

    /** Something drawn at the middle of a sector (an item, a number, a stencil...). */
    @FunctionalInterface
    public interface Icon {
        void draw(DrawContext context, int centerX, int centerY);
    }

    /**
     * A sector of the wheel.
     *
     * @param label    its name (the tooltip's title)
     * @param hint     a few words under it in the tooltip, or null
     * @param color    its plate (RGB; the alpha is ignored)
     * @param icon     drawn at its middle
     * @param selected the tool's current setting (a gold frame)
     * @param enabled  false: shown greyed, picks nothing
     * @param stays    its pick keeps the wheel open (it shows another page)
     * @param pick     what the pick does
     */
    public record Sector(Text label, @Nullable Text hint, int color, @Nullable Icon icon, boolean selected, boolean enabled,
                         boolean stays, Runnable pick) {
        public Sector(Text label, @Nullable Text hint, int color, @Nullable Icon icon, boolean selected, boolean enabled, Runnable pick) {
            this(label, hint, color, icon, selected, enabled, false, pick);
        }
    }

    /**
     * Sectors spread evenly over an arc of a ring, from {@code from} to {@code to} degrees (0: top, clockwise; {@code
     * from} may be negative).
     */
    public record Arc(int ring, float from, float to, List<Sector> sectors) {
    }

    /** A ring: between {@code inner} and {@code outer} (GUI pixels from the centre). */
    public record Ring(float inner, float outer) {
    }

    /**
     * What a wheel shows.
     *
     * @param rings    its rings
     * @param arcs     its sectors
     * @param hubIcon  drawn in the hub
     * @param hub      what a pick in the hub does (back to the first page...), null: nothing
     * @param featured the sector put forward the first times the wheel opens, null: none
     * @param featuredHint the tooltip line of the featured sector while it is put forward (null: its own hint)
     */
    public record Layout(List<Ring> rings, List<Arc> arcs, @Nullable Icon hubIcon, @Nullable Sector hub,
                         @Nullable Sector featured, @Nullable Text featuredHint, Theme theme) {
    }

    /** A tool with a wheel. */
    public interface Provider {
        boolean handles(ItemStack stack);

        /** The wheel on {@code page} (0 when it opens; see {@link #showPage}). */
        Layout layout(MinecraftClient client, ItemStack stack, int page);
    }

    private static final List<Provider> PROVIDERS = new ArrayList<>();

    private static @Nullable Provider open;
    private static int openSlot;
    private static long openedAt;
    private static boolean sticky;
    private static boolean onboarding;
    private static int page;
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

    /** Opens the wheel of the tool in hand again, on its first page, staying open (back from a screen). */
    public static void reopen() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        Provider provider = provider(client.player.getMainHandStack());
        if (provider == null) return;
        open(client, provider);
        sticky = true;
    }

    /** Shows another page of the open wheel (a sector that {@link Sector#stays}). */
    public static void showPage(int page) {
        ToolWheel.page = page;
        MinecraftClient client = MinecraftClient.getInstance();
        if (open != null && client.player != null) layout = open.layout(client, client.player.getMainHandStack(), page);
    }

    private static @Nullable Provider provider(ItemStack stack) {
        for (Provider provider : PROVIDERS) if (provider.handles(stack)) return provider;
        return null;
    }

    private static String key(Provider provider) {
        return provider.getClass().getSimpleName();
    }

    private static void open(MinecraftClient client, Provider provider) {
        if (client.player == null) return;
        open = provider;
        openSlot = client.player.getInventory().selectedSlot;
        openedAt = System.currentTimeMillis();
        sticky = false;
        page = 0;
        cursorX = 0;
        cursorY = 0;
        onboarding = ToolWheelHints.onboarding(key(provider));
        ToolWheelHints.opened(key(provider));
        layout = provider.layout(client, client.player.getMainHandStack(), page);
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
        layout = open.layout(client, client.player.getMainHandStack(), page);
        if (!sticky && !client.options.attackKey.isPressed()) {
            if (System.currentTimeMillis() - openedAt < LONG_PRESS_MS) sticky = true;
            else pickAndClose(client);
        }
    }

    private static void pickAndClose(MinecraftClient client) {
        Layout shown = layout;
        Provider provider = open;
        Sector hovered = hovered();
        if (hovered != null && hovered.enabled() && hovered.stays()) {
            click(client);
            hovered.pick().run();
            sticky = true;
            return;
        }
        close();
        if (hovered == null || !hovered.enabled()) return;
        if (shown != null && provider != null && shown.featured() != null && hovered.label().equals(shown.featured().label())) {
            ToolWheelHints.learned(key(provider));
        }
        click(client);
        hovered.pick().run();
    }

    private static void click(MinecraftClient client) {
        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.9F, 0.5F));
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

    private static float hubRadius(Layout layout) {
        return layout.rings().isEmpty() ? 20 : layout.rings().getFirst().inner() - 3;
    }

    /** The hovered sector: an arc and its index, or the hub ({@code arc} null). */
    public record Hover(@Nullable Arc arc, int index) {
    }

    private static @Nullable Sector hovered() {
        Hover hover = hover();
        if (hover == null || layout == null) return null;
        return hover.arc() == null ? layout.hub() : hover.arc().sectors().get(hover.index());
    }

    private static @Nullable Hover hover() {
        Layout layout = ToolWheel.layout;
        return layout == null ? null : hoverAt(layout, cursorX, cursorY);
    }

    /**
     * The sector of {@code layout} at ({@code x}, {@code y}) from its centre (unscaled GUI pixels): the hub (if it has
     * a sector), else the nearest ring's sector in that direction, however far.
     */
    public static @Nullable Hover hoverAt(Layout layout, double x, double y) {
        if (layout.rings().isEmpty()) return null;
        double length = Math.sqrt(x * x + y * y);
        // The hub: its own sector (back...), or nothing
        if (length < hubRadius(layout)) return layout.hub() != null ? new Hover(null, 0) : null;
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
        float angle = (float) Math.toDegrees(Math.atan2(x, -y));
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

    /** How far from the centre a hovered plate reaches (unscaled GUI pixels): its ring's outside, grown. */
    public static float reach(Layout layout) {
        return outerRadius(layout) + HOVER_GROW;
    }

    // ---------------------------------------------------------------- drawing

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        Layout layout = ToolWheel.layout;
        if (open == null || layout == null || client.options.hudHidden) return;
        int width = context.getScaledWindowWidth(), height = context.getScaledWindowHeight();
        float outer = outerRadius(layout);
        // Smaller on a small screen (large GUI scale): the wheel fits
        float fit = Math.min(1f, (height / 2f - 12) / (outer + HOVER_GROW + 2));
        int cx = width / 2, cy = height / 2;
        Hover hover = hover();
        boolean cta = onboarding && layout.featured() != null;
        boolean blink = cta && client.world != null && (client.world.getTime() / 8) % 2 == 0;

        drawPlates(context, layout, cx, cy, hover == null ? null : hover.arc(), hover == null ? -1 : hover.index(),
                hover != null && hover.arc() == null, fit, cta, blink);

        // Icons
        for (Arc arc : layout.arcs()) {
            Ring ring = layout.rings().get(arc.ring());
            float step = (arc.to() - arc.from()) / Math.max(1, arc.sectors().size());
            for (int i = 0; i < arc.sectors().size(); i++) {
                Sector sector = arc.sectors().get(i);
                if (sector.icon() == null) continue;
                boolean hovered = hover != null && hover.arc() == arc && hover.index() == i;
                double mid = Math.toRadians(arc.from() + step * (i + 0.5));
                double r = ((ring.inner() + ring.outer()) / 2) * fit + (hovered && sector.enabled() ? HOVER_GROW / 2.0 : 0);
                sector.icon().draw(context, cx + (int) Math.round(Math.sin(mid) * r), cy + (int) Math.round(-Math.cos(mid) * r));
            }
        }
        if (layout.hubIcon() != null) layout.hubIcon().draw(context, cx, cy);

        // Where the mouse points: a small pixel cursor, above the icons (items and sprites drawn higher up in depth)
        int dotX = cx + (int) Math.round(cursorX * fit), dotY = cy + (int) Math.round(cursorY * fit);
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 300);
        context.fill(dotX - 2, dotY - 1, dotX + 2, dotY + 1, 0xFF1E1E1E);
        context.fill(dotX - 1, dotY - 2, dotX + 1, dotY + 2, 0xFF1E1E1E);
        context.fill(dotX - 1, dotY - 1, dotX + 1, dotY + 1, 0xFFFFFFFF);
        context.getMatrices().pop();

        // A vanilla tooltip: the hovered sector in a few words (or, the first times, the one put forward)
        Sector hovered = hovered();
        List<OrderedText> lines = new ArrayList<>();
        int tipX = dotX, tipY = dotY;
        if (hovered != null) {
            lines.add(hovered.label().copy().formatted(hovered.enabled() ? Formatting.WHITE : Formatting.GRAY).asOrderedText());
            Text hint = cta && layout.featured() == hovered && layout.featuredHint() != null ? layout.featuredHint() : hovered.hint();
            if (hint != null) lines.addAll(client.textRenderer.wrapLines(hint.copy().formatted(Formatting.GRAY), HINT_WIDTH));
        } else if (cta) {
            Sector featured = layout.featured();
            lines.add(featured.label().copy().formatted(Formatting.YELLOW).asOrderedText());
            Text hint = layout.featuredHint() != null ? layout.featuredHint() : featured.hint();
            if (hint != null) lines.addAll(client.textRenderer.wrapLines(hint.copy().formatted(Formatting.GRAY), HINT_WIDTH));
            int[] at = centreOf(layout, featured, fit);
            if (at != null) {
                tipX = cx + at[0];
                tipY = cy + at[1];
            }
        }
        if (!lines.isEmpty()) {
            // Over the icons, even those drawn high (a level's number, the cursor)
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, 600);
            context.drawOrderedTooltip(client.textRenderer, lines, tipX, tipY);
            context.getMatrices().pop();
        }
    }

    /**
     * The wheel's plates centred on ({@code cx}, {@code cy}), pixel by pixel (cached: rasterised again only when
     * something shown changes): the hovered one grown outward and lighter, the hub lighter when hovered. Every wheel
     * draws its plates through here (the tool wheels, the hammer's refill).
     */
    public static void drawPlates(DrawContext context, Layout layout, int cx, int cy, @Nullable Arc hoverArc, int hoverIndex,
                                  boolean hubHovered, float fit, boolean cta, boolean blink) {
        for (WheelRaster.Run run : WheelRaster.runs(layout, hoverArc, hoverIndex, hubHovered, fit, cta, blink)) {
            context.fill(cx + run.x0(), cy + run.y(), cx + run.x1(), cy + run.y() + 1, run.color());
        }
    }

    /**
     * An item drawn {@code scale} times its size (2: still whole pixels), centred, on its drop shadow (see
     * {@link #drawItemShadow}), like the wheel's sprite icons.
     */
    public static Icon item(ItemStack stack, float scale) {
        return (context, x, y) -> {
            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0);
            context.getMatrices().scale(scale, scale, 1);
            context.getMatrices().push();
            context.getMatrices().translate(0, 0, -50);
            drawItemShadow(context, stack, -8, -8);
            context.getMatrices().pop();
            context.drawItem(stack, -8, -8);
            context.getMatrices().pop();
        };
    }

    /**
     * The drop shadow of an item drawn at ({@code x}, {@code y}) (its top left corner): its silhouette darkened, one of
     * its pixels down right. Drawn before the item, so the item covers it.
     */
    public static void drawItemShadow(DrawContext context, ItemStack stack, int x, int y) {
        GuiItems.tinted(context, stack, x + 1, y + 1, 0.16f, 0.06f, 0.08f, 1f);
    }

    /** Where the middle of {@code sector} is drawn (from the centre), or null. */
    private static int @Nullable [] centreOf(Layout layout, Sector sector, float fit) {
        for (Arc arc : layout.arcs()) {
            int i = arc.sectors().indexOf(sector);
            if (i < 0) continue;
            Ring ring = layout.rings().get(arc.ring());
            float step = (arc.to() - arc.from()) / Math.max(1, arc.sectors().size());
            double mid = Math.toRadians(arc.from() + step * (i + 0.5));
            double r = ((ring.inner() + ring.outer()) / 2) * fit;
            return new int[]{(int) Math.round(Math.sin(mid) * r), (int) Math.round(-Math.cos(mid) * r)};
        }
        return null;
    }

    /**
     * The wheel's plates as runs of pixels of one colour, row by row: each pixel is classified once (sector, gap,
     * outline, bevel, fill) by its distance to the sector's edges, so the gaps keep the same width at any radius.
     */
    public static final class WheelRaster {
        public record Run(int x0, int x1, int y, int color) {
        }

        private static List<Run> cached = List.of();
        private static @Nullable String cachedKey;

        public static List<Run> runs(Layout layout, @Nullable Arc hoverArc, int hoverIndex, boolean hubHovered, float fit, boolean cta, boolean blink) {
            String key = key(layout, hoverArc, hoverIndex, hubHovered, fit, cta, blink);
            if (key.equals(cachedKey)) return cached;
            List<Run> runs = new ArrayList<>();
            int hub = Math.round(hubRadius(layout) * fit);
            int max = Math.round((outerRadius(layout) + 2) * fit) + HOVER_GROW + 1;
            for (int py = -max; py < max; py++) {
                int start = 0, color = 0;
                for (int px = -max; px <= max; px++) {
                    int c = px == max ? 0 : pixel(layout, hoverArc, hoverIndex, hubHovered, fit, hub, max, cta, blink, px + 0.5, py + 0.5);
                    if (c != color || px == max) {
                        if (color != 0) runs.add(new Run(start, px, py, color));
                        start = px;
                        color = c;
                    }
                }
            }
            cached = runs;
            cachedKey = key;
            return runs;
        }

        private static String key(Layout layout, @Nullable Arc hoverArc, int hoverIndex, boolean hubHovered, float fit, boolean cta, boolean blink) {
            StringBuilder key = new StringBuilder();
            key.append(layout.theme()).append('|').append(fit).append('|').append(hubHovered).append('|').append(cta).append(blink).append('|');
            for (Ring ring : layout.rings()) key.append(ring.inner()).append(',').append(ring.outer()).append(';');
            for (Arc arc : layout.arcs()) {
                key.append('[').append(arc.ring()).append(',').append(arc.from()).append(',').append(arc.to());
                if (arc == hoverArc) key.append('h').append(hoverIndex);
                for (Sector s : arc.sectors()) {
                    key.append(',').append(Integer.toHexString(s.color())).append(s.selected() ? 's' : '-').append(s.enabled() ? 'e' : '-')
                            .append(s == layout.featured() ? 'f' : '-');
                }
                key.append(']');
            }
            return key.toString();
        }

        private static int pixel(Layout layout, @Nullable Arc hoverArc, int hoverIndex, boolean hubHovered, float fit, int hub, int max,
                                 boolean cta, boolean blink, double x, double y) {
            double d = Math.sqrt(x * x + y * y);
            Theme theme = layout.theme();
            int backdrop = theme.rim();
            if (d < hub) {
                double e = hub - d;
                if (e < 1) return theme.outline();
                if (e < 2) return x + y < 0 ? theme.light() : theme.rim();
                return hubHovered ? Argb.opaque(Argb.lerp(theme.body(), 0xFFFFFFFF, 0.2f)) : theme.body();
            }
            double angle = Math.toDegrees(Math.atan2(x, -y));
            for (int r = 0; r < layout.rings().size(); r++) {
                Ring ring = layout.rings().get(r);
                int inner = Math.round(ring.inner() * fit), outer = Math.round(ring.outer() * fit);
                if (d < inner || d >= outer + HOVER_GROW) continue;
                for (Arc arc : layout.arcs()) {
                    if (arc.ring() != r || arc.sectors().isEmpty()) continue;
                    double span = arc.to() - arc.from();
                    double relative = MathHelper.floorMod(angle - arc.from(), 360.0);
                    if (relative >= span) continue;
                    int count = arc.sectors().size();
                    double step = span / count;
                    int index = Math.min(count - 1, (int) (relative / step));
                    Sector sector = arc.sectors().get(index);
                    boolean hovered = arc == hoverArc && index == hoverIndex && sector.enabled();
                    int out = outer + (hovered ? HOVER_GROW : 0);
                    if (d >= out) return backdrop;
                    // The distance to each edge, and the way out of the plate there (for the bevel)
                    double edge = d - inner, nx = -x / d, ny = -y / d;
                    if (out - d < edge) {
                        edge = out - d;
                        nx = x / d;
                        ny = y / d;
                    }
                    boolean whole = span >= 360 && count == 1;
                    if (!whole) {
                        double from = relative - step * index, to = step * (index + 1) - relative;
                        double b0 = Math.toRadians(arc.from() + step * index), b1 = Math.toRadians(arc.from() + step * (index + 1));
                        double p0 = from >= 90 ? Double.MAX_VALUE : d * Math.sin(Math.toRadians(from));
                        double p1 = to >= 90 ? Double.MAX_VALUE : d * Math.sin(Math.toRadians(to));
                        if (p0 < HALF_GAP || p1 < HALF_GAP) return backdrop;
                        if (p0 - HALF_GAP < edge) {
                            edge = p0 - HALF_GAP;
                            nx = -Math.cos(b0);
                            ny = -Math.sin(b0);
                        }
                        if (p1 - HALF_GAP < edge) {
                            edge = p1 - HALF_GAP;
                            nx = Math.cos(b1);
                            ny = Math.sin(b1);
                        }
                    }
                    return plate(theme, sector, hovered, cta && sector == layout.featured(), cta && sector != layout.featured(), blink, edge, nx + ny < 0);
                }
                return backdrop;
            }
            int rim = Math.round((outerRadius(layout) + 2) * fit);
            return d < rim - 1 ? backdrop : d < rim ? theme.outline() : 0;
        }

        /** A pixel of a sector's plate, {@code edge} pixels in from its nearest edge. */
        private static int plate(Theme theme, Sector sector, boolean hovered, boolean featured, boolean dimmed, boolean blink, double edge, boolean lit) {
            int base = 0xFF000000 | sector.color();
            if (!sector.enabled()) base = Argb.opaque(Argb.lerp(base, theme.rim(), 0.7f));
            else if (dimmed) base = Argb.opaque(Argb.lerp(base, theme.rim(), 0.55f));
            // A plate of the panel takes its outline, light bevel and dark rim; a coloured one its own colour's
            boolean panel = (base & 0xFFFFFF) == (theme.body() & 0xFFFFFF);
            int outline = panel ? theme.outline() : Argb.opaque(Argb.scale(base, 0.4f));
            int light = panel ? theme.light() : Argb.opaque(Argb.lerp(base, 0xFFFFFFFF, 0.45f));
            int shadow = panel ? theme.rim() : Argb.opaque(Argb.scale(base, 0.7f));
            int fill = hovered ? Argb.opaque(Argb.lerp(base, 0xFFFFFFFF, 0.2f)) : base;
            if (featured) {
                if (edge < 2) return blink ? GOLD : GOLD_OUTLINE;
                if (edge < 3) return lit ? light : shadow;
                return fill;
            }
            if (sector.selected()) {
                if (edge < 1) return hovered ? 0xFFFFFFFF : GOLD_OUTLINE;
                if (edge < 2) return GOLD_LIGHT;
                if (edge < 3) return GOLD;
                return fill;
            }
            if (edge < 1) return outline;
            if (edge < 2) return lit ? light : shadow;
            return fill;
        }

    }
}
