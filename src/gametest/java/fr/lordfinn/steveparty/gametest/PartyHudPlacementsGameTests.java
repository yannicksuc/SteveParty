package fr.lordfinn.steveparty.gametest;

import fr.lordfinn.steveparty.gametest.kit.SteveGameTest;
import fr.lordfinn.steveparty.hud.HudPlacements;
import fr.lordfinn.steveparty.hud.HudPlacements.Anchor;
import fr.lordfinn.steveparty.hud.HudPlacements.Hud;
import fr.lordfinn.steveparty.hud.HudPlacements.Placement;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * Where the party HUDs sit ({@link HudPlacements}): nine anchors, an offset kept in GUI pixels through screen and
 * size changes, the new defaults, the file and the migration of the first format.
 */
public class PartyHudPlacementsGameTests implements SteveGameTest {
    /** The anchor decides which point of the HUD sticks to which point of the screen. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void anchorsKeepTheirPoint(TestContext context) {
        Placement right = new Placement();
        HudPlacements.moveTo(right, Anchor.RIGHT);
        context.assertEquals(HudPlacements.x(right, 427, 100), 427f - 2 - 100, "anchored right: its right edge 2 px from the screen's");
        context.assertEquals(HudPlacements.x(right, 427, 160), 427f - 2 - 160, "wider: it grows to the left");
        context.assertEquals(HudPlacements.y(right, 240, 80), 80f, "vertically centred: (240 - 80) / 2");
        context.assertEquals(HudPlacements.y(right, 240, 120), 60f, "taller: still centred");

        Placement top = Hud.TURN_BAR.defaults();
        context.assertEquals(HudPlacements.x(top, 427, 201), (427 - 201) / 2f, "top centre: centred");
        context.assertEquals(HudPlacements.x(top, 480, 201), (480 - 201) / 2f, "centred on another screen too");

        Placement bottom = new Placement();
        HudPlacements.moveTo(bottom, Anchor.BOTTOM_LEFT);
        context.assertEquals(HudPlacements.y(bottom, 240, 30), 240f - 2 - 30, "anchored at the bottom");
        context.assertEquals(HudPlacements.y(bottom, 240, 50), 240f - 2 - 50, "taller: it grows upward");

        // Dropped somewhere: the zone of its centre gives the anchor, the offset keeps it where it was dropped
        Placement dropped = new Placement();
        HudPlacements.place(dropped, 300, 150, 100, 40, 427, 240);
        context.assertEquals(dropped.anchor, Anchor.BOTTOM_RIGHT, "its centre (350, 170) is in the bottom right third");
        context.assertEquals(HudPlacements.x(dropped, 427, 100), 300f, "drawn where it was dropped");
        context.assertEquals(HudPlacements.y(dropped, 240, 40), 150f, "drawn where it was dropped");
        // The offset is in GUI pixels from the anchor: on a bigger screen, it stays as far from its corner
        context.assertEquals(HudPlacements.x(dropped, 480, 100), 480f - (427 - 300), "as far from the right edge on a wider screen");
        context.assertEquals(Anchor.nearest(10, 10, 427, 240), Anchor.TOP_LEFT, "zones: top left");
        context.assertEquals(Anchor.nearest(213, 120, 427, 240), Anchor.CENTER, "zones: centre");
        // Kept on the screen
        Placement far = new Placement();
        far.anchor = Anchor.TOP_LEFT;
        far.dx = 5000;
        context.assertEquals(HudPlacements.x(far, 427, 100), 327f, "never off the screen");
        context.complete();
    }

    /** The new defaults: the bar at the top, the notice under it (attached), the standings at the middle of the right edge. */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void newDefaults(TestContext context) {
        HudPlacements.Layout layout = new HudPlacements.Layout();
        context.assertEquals(layout.get(Hud.TURN_BAR).anchor, Anchor.TOP, "the turn bar: top centre");
        context.assertEquals(layout.get(Hud.NOTICE).anchor, Anchor.TOP, "the notice: top centre");
        context.assertTrue(layout.get(Hud.NOTICE).attached, "the notice follows the bar");
        context.assertEquals(layout.get(Hud.STANDINGS).anchor, Anchor.RIGHT, "the standings: middle right");
        context.assertTrue(!layout.hidden, "shown");
        Placement notice = layout.get(Hud.NOTICE);
        HudPlacements.place(notice, 10, 100, 100, 20, 427, 240);
        context.assertTrue(!notice.attached, "moved: on its own");
        context.complete();
    }

    /** The file: written and read back; the first format migrated (moved HUDs kept, untouched ones get the new defaults). */
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void fileAndMigration(TestContext context) {
        HudPlacements.Layout layout = new HudPlacements.Layout();
        HudPlacements.moveTo(layout.get(Hud.STANDINGS), Anchor.BOTTOM_LEFT);
        layout.get(Hud.TURN_BAR).scale = 1.5f;
        layout.hidden = true;
        HudPlacements.Layout back = HudPlacements.read(HudPlacements.write(layout));
        context.assertEquals(back.get(Hud.STANDINGS).anchor, Anchor.BOTTOM_LEFT, "the anchor saved");
        context.assertEquals(back.get(Hud.TURN_BAR).scale, 1.5f, "the scale saved");
        context.assertTrue(back.hidden, "hidden saved");
        context.assertTrue(back.get(Hud.NOTICE).attached, "the notice still attached");

        // The first format: no version, the notice over the hotbar and the standings at the top left by default
        String old = "{\"turnBar\":{\"anchor\":\"TOP\",\"dx\":0,\"dy\":2,\"scale\":1.25,\"visible\":true},"
                + "\"standings\":{\"anchor\":\"TOP_LEFT\",\"dx\":2,\"dy\":2,\"scale\":1.0,\"visible\":false},"
                + "\"notice\":{\"anchor\":\"BOTTOM\",\"dx\":0,\"dy\":-57,\"scale\":1.0,\"visible\":true}}";
        HudPlacements.Layout migrated = HudPlacements.read(old);
        context.assertEquals(migrated.get(Hud.TURN_BAR).scale, 1.25f, "the bar's scale kept");
        context.assertEquals(migrated.get(Hud.STANDINGS).anchor, Anchor.RIGHT, "standings left at their old default: the new one");
        context.assertTrue(!migrated.get(Hud.STANDINGS).visible, "their visibility kept");
        context.assertTrue(migrated.get(Hud.NOTICE).anchor == Anchor.TOP && migrated.get(Hud.NOTICE).attached, "notice at its old default: under the bar now");
        String moved = "{\"standings\":{\"anchor\":\"BOTTOM_RIGHT\",\"dx\":-10,\"dy\":-30,\"scale\":1.0,\"visible\":true},"
                + "\"notice\":{\"anchor\":\"LEFT\",\"dx\":4,\"dy\":0,\"scale\":1.0,\"visible\":true}}";
        HudPlacements.Layout kept = HudPlacements.read(moved);
        context.assertTrue(kept.get(Hud.STANDINGS).anchor == Anchor.BOTTOM_RIGHT && kept.get(Hud.STANDINGS).dx == -10, "moved standings kept");
        context.assertTrue(kept.get(Hud.NOTICE).anchor == Anchor.LEFT && !kept.get(Hud.NOTICE).attached, "a moved notice kept, on its own");
        context.complete();
    }
}
