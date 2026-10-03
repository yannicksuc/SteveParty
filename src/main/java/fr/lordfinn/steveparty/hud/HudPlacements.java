package fr.lordfinn.steveparty.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where each party HUD sits, per player: one of nine anchors, an offset from it in GUI pixels and a scale.
 * <p>
 * An anchor is a point of the screen (a corner, the middle of an edge, the centre) and the same point of the HUD: a
 * HUD anchored on the right grows to the left, one anchored in the middle stays centred when its width changes, one
 * anchored at the bottom grows upward. The offset is kept in GUI pixels, so a layout survives window resizes, GUI
 * scale changes and the HUD's own changes of size.
 * <p>
 * The notice starts « attached »: it follows the turn bar, just under it, while both are at their default place;
 * moved by the player, it is a HUD like the others.
 * <p>
 * Pure (the client draws, the GameTests check the maths, the file format and its migration).
 */
public final class HudPlacements {
    /** Room kept between a HUD and the edge of the screen it is anchored to. */
    public static final int MARGIN = 2;
    public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;
    /** Scales go by eighths: whole and half steps stay pixel-exact. */
    public static final float SCALE_STEP = 0.125f;
    /** The file's format: 2 since the nine anchors of each HUD and the notice under the turn bar. */
    public static final int VERSION = 2;

    public enum Anchor {
        TOP_LEFT(0, 0), TOP(0.5f, 0), TOP_RIGHT(1, 0),
        LEFT(0, 0.5f), CENTER(0.5f, 0.5f), RIGHT(1, 0.5f),
        BOTTOM_LEFT(0, 1), BOTTOM(0.5f, 1), BOTTOM_RIGHT(1, 1);

        /** Where the anchor is, as a fraction of the screen (and of the HUD: its matching point sits there). */
        public final float fx, fy;

        Anchor(float fx, float fy) {
            this.fx = fx;
            this.fy = fy;
        }

        /** The anchor of the screen's third a point is in (the zones of the nine anchors). */
        public static Anchor nearest(float x, float y, int screenWidth, int screenHeight) {
            int column = x < screenWidth / 3f ? 0 : x > screenWidth * 2 / 3f ? 2 : 1;
            int row = y < screenHeight / 3f ? 0 : y > screenHeight * 2 / 3f ? 2 : 1;
            return values()[row * 3 + column];
        }

        /** The anchor's point on the screen. */
        public float screenX(int screenWidth) {
            return fx * screenWidth;
        }

        public float screenY(int screenHeight) {
            return fy * screenHeight;
        }
    }

    public enum Hud {
        /** The turn bar: the strip of steps (and the « toi » bubbles under it). */
        TURN_BAR(Anchor.TOP, 0, MARGIN),
        /** The standings: rank, stars, coins and bonuses of each player; at the middle of the right edge. */
        STANDINGS(Anchor.RIGHT, -MARGIN, 0),
        /** The notice: what is happening now; under the turn bar (attached to it) until moved. */
        NOTICE(Anchor.TOP, 0, MARGIN + 66);

        public final Anchor defaultAnchor;
        public final int defaultDx, defaultDy;

        Hud(Anchor anchor, int dx, int dy) {
            this.defaultAnchor = anchor;
            this.defaultDx = dx;
            this.defaultDy = dy;
        }

        public Placement defaults() {
            Placement placement = new Placement();
            placement.anchor = defaultAnchor;
            placement.dx = defaultDx;
            placement.dy = defaultDy;
            placement.attached = this == NOTICE;
            return placement;
        }
    }

    /** One HUD's place (saved as is in the file). */
    public static final class Placement {
        public Anchor anchor = Anchor.TOP;
        public int dx;
        public int dy;
        public float scale = 1;
        public boolean visible = true;
        /** The notice only: under the turn bar, following it, while both are at their default place. */
        public boolean attached;

        public Placement copy() {
            Placement copy = new Placement();
            copy.anchor = anchor;
            copy.dx = dx;
            copy.dy = dy;
            copy.scale = scale;
            copy.visible = visible;
            copy.attached = attached;
            return copy;
        }

        /** At its default anchor and offset (its scale and visibility aside). */
        public boolean atDefault(Hud hud) {
            return anchor == hud.defaultAnchor && dx == hud.defaultDx && dy == hud.defaultDy;
        }

        Placement sanitized(Hud hud) {
            if (anchor == null) {
                Placement defaults = hud.defaults();
                anchor = defaults.anchor;
                dx = defaults.dx;
                dy = defaults.dy;
            }
            if (!Float.isFinite(scale)) scale = 1;
            scale = snapScale(scale);
            dx = Math.clamp(dx, -10000, 10000);
            dy = Math.clamp(dy, -10000, 10000);
            if (hud != Hud.NOTICE) attached = false;
            return this;
        }
    }

    /** Every HUD's place, and whether the party HUD is hidden (the toggle key). */
    public static final class Layout {
        public final Map<Hud, Placement> placements = new EnumMap<>(Hud.class);
        public boolean hidden;

        public Layout() {
            for (Hud hud : Hud.values()) placements.put(hud, hud.defaults());
        }

        public Placement get(Hud hud) {
            return placements.get(hud);
        }
    }

    private HudPlacements() {
    }

    public static float snapScale(float scale) {
        return Math.clamp(Math.round(scale / SCALE_STEP) * SCALE_STEP, MIN_SCALE, MAX_SCALE);
    }

    // ------------------------------------------------------------------ the maths

    /**
     * The left of a HUD on the screen (GUI pixels), kept on the screen: its anchor's point at the screen's matching
     * point, moved by the offset.
     *
     * @param width its width once scaled
     */
    public static float x(Placement placement, int screenWidth, float width) {
        float x = placement.anchor.fx * screenWidth + placement.dx - placement.anchor.fx * width;
        return Math.clamp(x, 0, Math.max(0, screenWidth - width));
    }

    public static float y(Placement placement, int screenHeight, float height) {
        float y = placement.anchor.fy * screenHeight + placement.dy - placement.anchor.fy * height;
        return Math.clamp(y, 0, Math.max(0, screenHeight - height));
    }

    /** Places a HUD whose top-left corner is now at (x, y): anchored to the zone its centre is in. */
    public static void place(Placement placement, float x, float y, float width, float height, int screenWidth, int screenHeight) {
        anchor(placement, Anchor.nearest(x + width / 2, y + height / 2, screenWidth, screenHeight), x, y, width, height, screenWidth, screenHeight);
    }

    /** Anchors a HUD whose top-left corner is at (x, y) to {@code anchor}, where it is (the offset that keeps it there). */
    public static void anchor(Placement placement, Anchor anchor, float x, float y, float width, float height, int screenWidth, int screenHeight) {
        placement.anchor = anchor;
        placement.dx = Math.round(x + anchor.fx * width - anchor.fx * screenWidth);
        placement.dy = Math.round(y + anchor.fy * height - anchor.fy * screenHeight);
        placement.attached = false;
    }

    /** Puts a HUD at an anchor (the anchor picker): against its edges, {@link #MARGIN} away from them. */
    public static void moveTo(Placement placement, Anchor anchor) {
        placement.anchor = anchor;
        placement.dx = anchor.fx == 0 ? MARGIN : anchor.fx == 1 ? -MARGIN : 0;
        placement.dy = anchor.fy == 0 ? MARGIN : anchor.fy == 1 ? -MARGIN : 0;
        placement.attached = false;
    }

    // ------------------------------------------------------------------ the file

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** The saved file (Gson). */
    private static final class Saved {
        int version;
        Placement turnBar;
        Placement standings;
        Placement notice;
        boolean hidden;
    }

    /** The defaults of the first format: a HUD still there was never moved, it takes the new default. */
    private static final Placement OLD_STANDINGS = old(Anchor.TOP_LEFT, MARGIN, MARGIN), OLD_NOTICE = old(Anchor.BOTTOM, 0, -57);

    private static Placement old(Anchor anchor, int dx, int dy) {
        Placement placement = new Placement();
        placement.anchor = anchor;
        placement.dx = dx;
        placement.dy = dy;
        return placement;
    }

    public static String write(Layout layout) {
        Saved saved = new Saved();
        saved.version = VERSION;
        saved.turnBar = layout.get(Hud.TURN_BAR);
        saved.standings = layout.get(Hud.STANDINGS);
        saved.notice = layout.get(Hud.NOTICE);
        saved.hidden = layout.hidden;
        return GSON.toJson(saved);
    }

    /**
     * Reads a saved layout. A file of the first format (no version) is migrated: the places the player chose are
     * kept; the HUDs left at their former default place (the standings at the top left, the notice over the hotbar)
     * take the new defaults (the standings at the middle of the right edge, the notice under the turn bar).
     *
     * @throws JsonParseException a file that is no layout
     */
    public static Layout read(String json) {
        JsonObject object = JsonParser.parseString(json).getAsJsonObject();
        Saved saved = GSON.fromJson(object, Saved.class);
        Layout layout = new Layout();
        if (saved == null) return layout;
        boolean migrate = saved.version < VERSION;
        if (saved.turnBar != null) layout.placements.put(Hud.TURN_BAR, saved.turnBar.sanitized(Hud.TURN_BAR));
        if (saved.standings != null) {
            Placement standings = saved.standings.sanitized(Hud.STANDINGS);
            if (migrate && same(standings, OLD_STANDINGS)) standings = keepLook(Hud.STANDINGS.defaults(), standings);
            layout.placements.put(Hud.STANDINGS, standings);
        }
        if (saved.notice != null) {
            Placement notice = saved.notice.sanitized(Hud.NOTICE);
            if (migrate) {
                // The first format had no « attached »: a notice moved by the player stays where it was put
                notice.attached = false;
                if (same(notice, OLD_NOTICE)) notice = keepLook(Hud.NOTICE.defaults(), notice);
            }
            layout.placements.put(Hud.NOTICE, notice);
        }
        layout.hidden = saved.hidden;
        return layout;
    }

    private static boolean same(Placement a, Placement b) {
        return a.anchor == b.anchor && a.dx == b.dx && a.dy == b.dy;
    }

    /** The default place, the player's scale and visibility. */
    private static Placement keepLook(Placement place, Placement look) {
        place.scale = look.scale;
        place.visible = look.visible;
        return place;
    }
}
