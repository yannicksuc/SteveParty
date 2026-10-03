package fr.lordfinn.steveparty.client.gui.party;

import com.google.gson.JsonParseException;
import fr.lordfinn.steveparty.hud.HudPlacements;
import fr.lordfinn.steveparty.hud.HudPlacements.Anchor;
import fr.lordfinn.steveparty.hud.HudPlacements.Hud;
import fr.lordfinn.steveparty.hud.HudPlacements.Placement;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The party HUDs' layout of this player ({@link HudPlacements}: each HUD at one of nine anchors, an offset and a
 * scale; the party HUD hidden or not), kept in {@code config/steveparty-hud.json}. A missing or broken file gives the
 * default layout; a file of the first format is migrated.
 */
public final class PartyHudLayout {
    private static final Logger LOGGER = LoggerFactory.getLogger("steveparty");
    private static final String FILE_NAME = "steveparty-hud.json";
    public static final int MARGIN = HudPlacements.MARGIN;
    public static final float SCALE_STEP = HudPlacements.SCALE_STEP;

    private static HudPlacements.Layout layout = new HudPlacements.Layout();

    private PartyHudLayout() {
    }

    public static Placement get(Hud hud) {
        return layout.get(hud);
    }

    /** The party HUD hidden with the toggle key (the layout screen shows it all the same). */
    public static boolean hidden() {
        return layout.hidden;
    }

    public static void setHidden(boolean hidden) {
        layout.hidden = hidden;
        save();
    }

    public static void changed() {
        // The notice leaves the turn bar as soon as one of them is moved
        if (!get(Hud.TURN_BAR).atDefault(Hud.TURN_BAR) || !get(Hud.NOTICE).atDefault(Hud.NOTICE)) get(Hud.NOTICE).attached = false;
    }

    public static void reset(Hud hud) {
        layout.placements.put(hud, hud.defaults());
        // The turn bar back at its place: a notice left at its place follows it again
        if (hud == Hud.TURN_BAR && get(Hud.NOTICE).atDefault(Hud.NOTICE)) get(Hud.NOTICE).attached = true;
    }

    public static float snapScale(float scale) {
        return HudPlacements.snapScale(scale);
    }

    /** The left of a HUD on the screen, kept on it ({@code width}: once scaled). */
    public static float x(Hud hud, int screenWidth, float width) {
        return HudPlacements.x(get(hud), screenWidth, width);
    }

    public static float y(Hud hud, int screenHeight, float height) {
        return HudPlacements.y(get(hud), screenHeight, height);
    }

    /** Places a HUD whose scaled top-left corner is now at (x, y): anchored to the zone its centre is in. */
    public static void place(Hud hud, float x, float y, float width, float height, int screenWidth, int screenHeight) {
        HudPlacements.place(get(hud), x, y, width, height, screenWidth, screenHeight);
        changed();
    }

    /** The anchor picker: the HUD goes to that anchor. */
    public static void moveTo(Hud hud, Anchor anchor) {
        HudPlacements.moveTo(get(hud), anchor);
        changed();
    }

    // ------------------------------------------------------------------ file

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static void load() {
        layout = new HudPlacements.Layout();
        Path file = file();
        if (!Files.exists(file)) return;
        try {
            layout = HudPlacements.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | JsonParseException | IllegalStateException e) {
            LOGGER.warn("Unreadable {}, using the default party HUD layout", FILE_NAME, e);
        }
    }

    /** Writes to a temporary file then moves it over the layout, so a crash never leaves a truncated file. */
    public static void save() {
        Path file = file();
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temp, HudPlacements.write(layout), StandardCharsets.UTF_8);
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
