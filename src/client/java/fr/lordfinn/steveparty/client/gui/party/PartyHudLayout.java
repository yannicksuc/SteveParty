package fr.lordfinn.steveparty.client.gui.party;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.math.MathHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;

/**
 * Where each party HUD is drawn, per player: an anchor (a corner, an edge's middle or the centre of the screen), an
 * offset from it and a scale, so that a layout survives window resizes and GUI scale changes. Kept in
 * {@code config/steveparty-hud.json}; a missing or broken file gives the default layout.
 */
public final class PartyHudLayout {
    private static final Logger LOGGER = LoggerFactory.getLogger("steveparty");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "steveparty-hud.json";
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.5f;
    /** Scales go by eighths: whole and half steps stay pixel-exact. */
    public static final float SCALE_STEP = 0.125f;
    /** Room kept between a HUD and the edge of the screen it is anchored to. */
    public static final int MARGIN = 2;

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

        /** The anchor of the screen third a point is in. */
        public static Anchor nearest(float x, float y, int screenWidth, int screenHeight) {
            int column = x < screenWidth / 3f ? 0 : x > screenWidth * 2 / 3f ? 2 : 1;
            int row = y < screenHeight / 3f ? 0 : y > screenHeight * 2 / 3f ? 2 : 1;
            return values()[row * 3 + column];
        }
    }

    public enum Hud {
        /** The turn bar at the top: round, turn order, what is happening. */
        TURN_BAR(Anchor.TOP, 0, MARGIN),
        /** The standings: rank, stars, coins and power-ups of each player. */
        STANDINGS(Anchor.TOP_LEFT, MARGIN, MARGIN);

        final Anchor defaultAnchor;
        final int defaultDx, defaultDy;

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

        Placement sanitized(Hud hud) {
            if (anchor == null) {
                Placement defaults = hud.defaults();
                anchor = defaults.anchor;
                dx = defaults.dx;
                dy = defaults.dy;
            }
            if (!Float.isFinite(scale)) scale = 1;
            scale = snapScale(scale);
            dx = MathHelper.clamp(dx, -10000, 10000);
            dy = MathHelper.clamp(dy, -10000, 10000);
            return this;
        }
    }

    /** The saved file. */
    private static final class Saved {
        Placement turnBar;
        Placement standings;
    }

    private static final Map<Hud, Placement> PLACEMENTS = new EnumMap<>(Hud.class);
    /** Bumped at each change, so that the HUDs place themselves again. */
    private static int revision;

    static {
        for (Hud hud : Hud.values()) PLACEMENTS.put(hud, hud.defaults());
    }

    private PartyHudLayout() {
    }

    public static Placement get(Hud hud) {
        return PLACEMENTS.get(hud);
    }

    public static int revision() {
        return revision;
    }

    public static void changed() {
        revision++;
    }

    public static void reset(Hud hud) {
        PLACEMENTS.put(hud, hud.defaults());
        changed();
    }

    public static float snapScale(float scale) {
        return MathHelper.clamp(Math.round(scale / SCALE_STEP) * SCALE_STEP, MIN_SCALE, MAX_SCALE);
    }

    /**
     * Top-left corner of a HUD on the screen (GUI pixels), kept on the screen.
     *
     * @param width  its width once scaled
     * @param height its height once scaled
     */
    public static float x(Hud hud, int screenWidth, float width) {
        Placement placement = get(hud);
        float x = placement.anchor.fx * screenWidth + placement.dx - placement.anchor.fx * width;
        return MathHelper.clamp(x, 0, Math.max(0, screenWidth - width));
    }

    public static float y(Hud hud, int screenHeight, float height) {
        Placement placement = get(hud);
        float y = placement.anchor.fy * screenHeight + placement.dy - placement.anchor.fy * height;
        return MathHelper.clamp(y, 0, Math.max(0, screenHeight - height));
    }

    /** Places a HUD whose scaled top-left corner is now at (x, y): anchored to the nearest corner / edge / centre. */
    public static void place(Hud hud, float x, float y, float width, float height, int screenWidth, int screenHeight) {
        Placement placement = get(hud);
        Anchor anchor = Anchor.nearest(x + width / 2, y + height / 2, screenWidth, screenHeight);
        placement.anchor = anchor;
        placement.dx = Math.round(x + anchor.fx * width - anchor.fx * screenWidth);
        placement.dy = Math.round(y + anchor.fy * height - anchor.fy * screenHeight);
        changed();
    }

    // ------------------------------------------------------------------ file

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        Path file = file();
        for (Hud hud : Hud.values()) PLACEMENTS.put(hud, hud.defaults());
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Saved saved = GSON.fromJson(reader, Saved.class);
                if (saved != null) {
                    if (saved.turnBar != null) PLACEMENTS.put(Hud.TURN_BAR, saved.turnBar.sanitized(Hud.TURN_BAR));
                    if (saved.standings != null) PLACEMENTS.put(Hud.STANDINGS, saved.standings.sanitized(Hud.STANDINGS));
                }
            } catch (IOException | JsonParseException | IllegalStateException e) {
                LOGGER.warn("Unreadable {}, using the default party HUD layout", FILE_NAME, e);
            }
        }
        changed();
    }

    /** Writes to a temporary file then moves it over the layout, so a crash never leaves a truncated file. */
    public static void save() {
        Saved saved = new Saved();
        saved.turnBar = get(Hud.TURN_BAR);
        saved.standings = get(Hud.STANDINGS);
        Path file = file();
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(saved, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to save the party HUD layout", e);
        }
    }
}
