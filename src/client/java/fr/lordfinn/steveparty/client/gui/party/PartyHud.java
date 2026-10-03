package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyLiveData;
import fr.lordfinn.steveparty.client.gui.party.PartyHudLayout.Hud;
import fr.lordfinn.steveparty.client.mixin.BossBarHudAccessor;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The party HUDs of a party's audience: the turn bar ({@link TurnBarHud}) and the standings ({@link StandingsHud}),
 * each placed and scaled by the player ({@link PartyHudLayout}, {@link PartyHudEditScreen}). Shown while this client
 * receives a running party (the server sends it to the party's interested players), hidden with F1 and the debug
 * screen; they fade in and out.
 * <p>
 * The party data ({@link PartyData}, sent when the steps change) and its live state ({@link PartyLiveData}, sent when
 * the turn or the standings change) are turned into a {@link PartyHudModel} once per change, not per frame.
 */
public final class PartyHud {
    private static final double START = System.nanoTime();
    private static final float FADE_RATE = 0.3f;
    /** The least width (unscaled) worth giving the turn bar beside the standings. */
    private static final int TURN_BAR_BESIDE = 200;

    private static PartyData data = new PartyData();
    private static PartyLiveData live = PartyLiveData.EMPTY;
    private static @Nullable PartyHudModel model;
    /** The last model shown, kept while the HUDs fade out. */
    private static @Nullable PartyHudModel shown;
    private static boolean dirty;

    private static final TurnBarHud TURN_BAR = new TurnBarHud();
    private static final StandingsHud STANDINGS = new StandingsHud();
    private static final NoticeHud NOTICE = new NoticeHud();
    private static final float[] TURN_BAR_ALPHA = {0};
    private static final float[] STANDINGS_ALPHA = {0};
    private static final float[] NOTICE_ALPHA = {0};
    /** How far up the notice is moved (eased) to clear the vanilla action bar and the tools' HUD. */
    private static float noticeLift;
    private static double lastFrame;
    /** Where each HUD was drawn last (x, y, width, height once scaled): for the layout screen. */
    private static final float[][] BOUNDS = new float[Hud.values().length][4];

    private static KeyBinding layoutKey;
    /** While the layout screen is open it draws the HUDs itself. */
    static boolean editing;
    /** A HUD is being dragged on the layout screen: drawn where the mouse puts it. */
    static boolean dragging;

    private PartyHud() {
    }

    public static void initialize() {
        PartyHudLayout.load();
        layoutKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.steveparty.party_hud_layout",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.steveparty"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (layoutKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new PartyHudEditScreen(null));
            }
        });
        HudRenderCallback.EVENT.register(PartyHud::render);
        // /partyhud: the same screen, for those who look for it in the commands
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("partyhud").executes(context -> {
                    MinecraftClient client = context.getSource().getClient();
                    // Next tick: the chat screen closes first
                    client.send(() -> client.setScreen(new PartyHudEditScreen(null)));
                    return 1;
                })));
    }

    // ------------------------------------------------------------------ data

    public static void onPartyData(PartyData partyData) {
        // A party that is over (END step) or gone (empty data sent when the controller is broken): nothing to show
        data = partyData != null && partyData.isStarted() ? partyData : new PartyData();
        if (!data.isStarted()) live = PartyLiveData.EMPTY;
        dirty = true;
    }

    public static void onLiveData(PartyLiveData liveData) {
        live = liveData;
        dirty = true;
    }

    public static void clear() {
        data = new PartyData();
        live = PartyLiveData.EMPTY;
        model = null;
        dirty = false;
    }

    /** The model of the running party, null when none (rebuilt once after a change). */
    static @Nullable PartyHudModel model() {
        if (dirty) {
            dirty = false;
            MinecraftClient client = MinecraftClient.getInstance();
            model = data.isStarted() && !data.getSteps().isEmpty()
                    ? PartyHudModel.build(data, live, client.player == null ? null : client.player.getUuid()) : null;
        }
        return model;
    }

    /** Animation clock, in ticks (smooth: frames between ticks included, goes on while the game is paused). */
    static double now() {
        return (System.nanoTime() - START) / 5.0e7;
    }

    static float[] bounds(Hud hud) {
        return BOUNDS[hud.ordinal()];
    }

    // ------------------------------------------------------------------ drawing

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (editing || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) return;
        PartyHudModel current = model();
        // The player list (Tab) is drawn at the top too: the turn bar steps aside while it is shown
        boolean playerList = client.options.playerListKey.isPressed();
        draw(context, current, !playerList, true, false);
    }

    /**
     * Draws both HUDs where the layout puts them.
     *
     * @param turnBarShown whether the turn bar may be shown (it fades out otherwise)
     * @param preview      the layout screen: no fade, hidden HUDs drawn see-through
     */
    static void draw(DrawContext context, @Nullable PartyHudModel current, boolean turnBarShown, boolean standingsShown, boolean preview) {
        double now = now();
        float delta = (float) MathHelper.clamp(now - lastFrame, 0, 5);
        lastFrame = now;
        if (current != null) shown = current;
        PartyHudModel drawn = current != null ? current : shown;
        if (drawn == null) return;

        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        boolean notice = placeHud(Hud.NOTICE, drawn, current != null && drawn.action != PartyHudModel.Action.NONE, preview, NOTICE_ALPHA, delta, now, screenWidth, screenHeight,
                screenWidth - 2 * PartyHudLayout.MARGIN);
        boolean standings = placeHud(Hud.STANDINGS, drawn, current != null && standingsShown && !drawn.players.isEmpty(), preview, STANDINGS_ALPHA, delta, now, screenWidth, screenHeight, 0);
        boolean turnBar = placeHud(Hud.TURN_BAR, drawn, current != null && turnBarShown, preview, TURN_BAR_ALPHA, delta, now, screenWidth, screenHeight,
                turnBarRoom(standings, screenWidth, screenHeight));
        // The two never cover each other (but while the player drags one on the layout screen)
        if (turnBar && standings && !dragging) separate(screenWidth, screenHeight);
        if (turnBar) drawHud(context, Hud.TURN_BAR, TURN_BAR_ALPHA[0], preview, now);
        if (standings) drawHud(context, Hud.STANDINGS, STANDINGS_ALPHA[0], preview, now);
        if (notice) drawHud(context, Hud.NOTICE, NOTICE_ALPHA[0], preview, now);
        if (current == null && TURN_BAR_ALPHA[0] <= 0 && STANDINGS_ALPHA[0] <= 0 && NOTICE_ALPHA[0] <= 0) shown = null;
    }

    /**
     * The width the turn bar may take (it fills it with the steps to come): the screen's, or what the standings leave
     * beside them when the bar is at their height and that is enough for a bar (else the bar takes the screen's width,
     * and {@link #separate} puts the standings under it).
     */
    private static float turnBarRoom(boolean standings, int screenWidth, int screenHeight) {
        float full = screenWidth - 2 * PartyHudLayout.MARGIN;
        if (!standings) return full;
        float scale = PartyHudLayout.get(Hud.TURN_BAR).scale;
        float height = TurnBarHud.HEIGHT * scale;
        float y = PartyHudLayout.y(Hud.TURN_BAR, screenHeight, height);
        float[] list = BOUNDS[Hud.STANDINGS.ordinal()];
        if (y >= list[1] + list[3] || list[1] >= y + height) return full;
        float beside = Math.max(list[0], screenWidth - list[0] - list[2]) - 2 * PartyHudLayout.MARGIN;
        return beside >= TURN_BAR_BESIDE * scale ? beside : full;
    }

    /**
     * When the turn bar and the standings overlap (small screens, large scales): the turn bar moves aside if there is
     * room next to the standings, otherwise the standings go under the turn bar.
     */
    private static void separate(int screenWidth, int screenHeight) {
        float[] bar = BOUNDS[Hud.TURN_BAR.ordinal()];
        float[] list = BOUNDS[Hud.STANDINGS.ordinal()];
        boolean overlap = bar[0] < list[0] + list[2] && list[0] < bar[0] + bar[2] && bar[1] < list[1] + list[3] && list[1] < bar[1] + bar[3];
        if (!overlap) return;
        int margin = PartyHudLayout.MARGIN;
        boolean listOnTheLeft = list[0] + list[2] / 2 < bar[0] + bar[2] / 2;
        float besideX = listOnTheLeft ? list[0] + list[2] + margin : list[0] - margin - bar[2];
        if (besideX >= margin && besideX + bar[2] <= screenWidth - margin) {
            bar[0] = besideX;
        } else if (list[1] >= bar[1]) {
            list[1] = Math.min(bar[1] + bar[3] + margin, Math.max(0, screenHeight - list[3]));
        } else {
            bar[1] = Math.min(list[1] + list[3] + margin, Math.max(0, screenHeight - bar[3]));
        }
    }

    private static void drawHud(DrawContext context, Hud hud, float alpha, boolean preview, double now) {
        PartyHudLayout.Placement placement = PartyHudLayout.get(hud);
        float[] bounds = BOUNDS[hud.ordinal()];
        // Sliding in from its edge while fading in
        float slide = preview ? 0 : (1 - HudDraw.easeOutCubic(alpha)) * 6 * (placement.anchor.fy > 0.5f ? 1 : -1);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(bounds[0], bounds[1] + slide, 0);
        matrices.scale(placement.scale, placement.scale, 1);
        switch (hud) {
            case TURN_BAR -> TURN_BAR.draw(context, alpha, now);
            case STANDINGS -> STANDINGS.draw(context, alpha, now);
            case NOTICE -> NOTICE.draw(context, alpha, now);
        }
        matrices.pop();
    }

    /** Fades a HUD, lays it out and places it (in {@link #BOUNDS}): false if it is not drawn. */
    private static boolean placeHud(Hud hud, PartyHudModel drawn, boolean wanted, boolean preview,
                                    float[] alpha, float delta, double now, int screenWidth, int screenHeight, float room) {
        PartyHudLayout.Placement placement = PartyHudLayout.get(hud);
        float scale = placement.scale;
        if (preview) {
            alpha[0] = placement.visible ? 1 : 0.35f;
        } else {
            float target = wanted && placement.visible ? 1 : 0;
            alpha[0] = target > alpha[0] ? Math.min(1, HudDraw.approach(alpha[0], target, FADE_RATE, delta) + 0.001f)
                    : Math.max(0, HudDraw.approach(alpha[0], target, FADE_RATE, delta) - 0.001f);
        }
        if (alpha[0] <= 0.02f) return false;

        int width, height;
        switch (hud) {
            case TURN_BAR -> {
                TURN_BAR.update(drawn, (int) (room / scale), now);
                width = TURN_BAR.width();
                height = TURN_BAR.height();
            }
            case STANDINGS -> {
                STANDINGS.update(drawn, (int) (screenHeight * 0.55f / scale), now);
                width = STANDINGS.width();
                height = STANDINGS.height();
            }
            default -> {
                NOTICE.update(drawn, (int) (room / scale), now);
                width = NOTICE.width();
                height = NOTICE.height();
            }
        }
        float scaledWidth = width * scale, scaledHeight = height * scale;
        float x = PartyHudLayout.x(hud, screenWidth, scaledWidth);
        float y = PartyHudLayout.y(hud, screenHeight, scaledHeight);
        if (hud == Hud.NOTICE) y -= noticeLift(placement, y + scaledHeight, preview, delta);
        else y = keepClear(x, y, scaledWidth, scaledHeight, screenWidth, screenHeight);
        float[] bounds = BOUNDS[hud.ordinal()];
        bounds[0] = x;
        bounds[1] = y;
        bounds[2] = scaledWidth;
        bounds[3] = scaledHeight;
        return true;
    }

    /**
     * The notice at its place over the hotbar (its default anchor): it goes up over the vanilla action bar while a
     * message shows there (rather than hiding: nothing is lost), and over the tools' HUD (Wrench, Stencil Hammer...)
     * and the held item's name above it. Moved elsewhere by the player, it stays where it was put.
     *
     * @param bottom the notice's bottom where the layout puts it
     */
    private static float noticeLift(PartyHudLayout.Placement placement, float bottom, boolean preview, float delta) {
        float target = 0;
        if (!preview && placement.anchor == PartyHudLayout.Anchor.BOTTOM) {
            MinecraftClient client = MinecraftClient.getInstance();
            int toolTop = fr.lordfinn.steveparty.client.gui.ToolHud.occupiedTop();
            // The held item's name goes over the tools' HUD: room for it
            if (toolTop >= 0) target = Math.max(0, bottom - (toolTop - 14));
            if (((fr.lordfinn.steveparty.client.mixin.InGameHudAccessor) client.inGameHud).steveparty$getOverlayRemaining() > 0)
                target += 14;
        }
        noticeLift = HudDraw.approach(noticeLift, target, 0.4f, delta);
        return Math.round(noticeLift);
    }

    /**
     * Moves a HUD at the top of the screen down under the boss bars (top centre) and the status effect icons (top
     * right) when it would cover them.
     */
    private static float keepClear(float x, float y, float width, float height, int screenWidth, int screenHeight) {
        MinecraftClient client = MinecraftClient.getInstance();
        int bars = ((BossBarHudAccessor) client.inGameHud.getBossBarHud()).steveparty$getBossBars().size();
        if (bars > 0 && x < screenWidth / 2f + 92 && x + width > screenWidth / 2f - 92) {
            // Vanilla: a bar every 19 pixels from y = 12, its name above it; it stops drawing at a third of the screen
            float bottom = Math.min(12 + 19 * bars - 7, screenHeight / 3f + 5) + 2;
            if (y < bottom) y = bottom;
        }
        if (client.player != null && !client.player.getStatusEffects().isEmpty()) {
            int beneficial = 0, harmful = 0;
            for (StatusEffectInstance effect : client.player.getStatusEffects()) {
                if (!effect.shouldShowIcon()) continue;
                if (effect.getEffectType().value().isBeneficial()) beneficial++;
                else harmful++;
            }
            int icons = Math.max(beneficial, harmful);
            float bottom = harmful > 0 ? 52 : beneficial > 0 ? 26 : 0;
            if (icons > 0 && y < bottom && x + width > screenWidth - 25 * icons - 1) y = bottom + 1;
        }
        return Math.min(y, Math.max(0, screenHeight - height));
    }
}
