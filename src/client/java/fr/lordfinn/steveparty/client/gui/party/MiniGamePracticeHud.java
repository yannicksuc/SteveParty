package fr.lordfinn.steveparty.client.gui.party;

import fr.lordfinn.steveparty.client.gui.ToolHud.Plate;
import fr.lordfinn.steveparty.payloads.custom.MiniGamePagePayloads;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The practice round of a party's mini-game, on screen: a chip at the top (« Practice — [Y] Ready 2/4 », green once
 * the player is ready; without the key for those who only watch) and, under it, the players of the mini-game, each
 * on a plate that turns green when he is ready. The key (« Ready for the mini-game », Y by default, in the controls)
 * says the player is ready, or no longer is, from anywhere.
 */
public final class MiniGamePracticeHud {
    private static final int CHIP_HEIGHT = 15, NAME_HEIGHT = 13, GAP = 2;
    private static final int NAME_MAX = 80;

    private static KeyBinding readyKey;
    private static MiniGamePagePayloads.@Nullable Practice practice;

    private MiniGamePracticeHud() {
    }

    public static void initialize() {
        readyKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.steveparty.minigame_ready",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Y, "category.steveparty"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (readyKey.wasPressed()) {
                if (client.currentScreen == null && isVoter()) toggleReady();
            }
        });
        HudRenderCallback.EVENT.register(MiniGamePracticeHud::render);
    }

    /** The practice round as the server tells it, null (or not shown) once it is over. */
    public static void show(MiniGamePagePayloads.@Nullable Practice shown) {
        practice = shown != null && shown.show() ? shown : null;
    }

    public static void clear() {
        practice = null;
    }

    public static MiniGamePagePayloads.@Nullable Practice practice() {
        return practice;
    }

    /** The key that says « ready », as the controls show it. */
    public static Text keyText() {
        return readyKey.getBoundKeyLocalizedText();
    }

    private static @Nullable String self() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? null : client.player.getGameProfile().getName();
    }

    /** @return true if the player is one of the players of the practice round being played. */
    public static boolean isVoter() {
        String self = self();
        return practice != null && self != null && practice.voters().stream().anyMatch(voter -> voter.name().equals(self));
    }

    public static boolean isReady() {
        String self = self();
        return practice != null && self != null && practice.voters().stream().anyMatch(voter -> voter.ready() && voter.name().equals(self));
    }

    /** Says the player is ready, or no longer is (the key, the Mini-game Controller's button). */
    public static void toggleReady() {
        if (ClientPlayNetworking.canSend(MiniGamePagePayloads.Ready.ID)) ClientPlayNetworking.send(new MiniGamePagePayloads.Ready());
    }

    private static void render(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (practice == null || client.options.hudHidden || client.getDebugHud().shouldShowDebugHud()) return;
        TextRenderer font = HudDraw.font();
        int screenWidth = context.getScaledWindowWidth();
        boolean voter = isVoter(), ready = isReady();
        long count = practice.readyCount();
        int total = practice.voters().size();
        Text label = voter
                ? Text.translatable(ready ? "hud.steveparty.minigame.practice.ready" : "hud.steveparty.minigame.practice", keyText(), count, total)
                : Text.translatable("hud.steveparty.minigame.practice.watching", count, total);
        OrderedText text = HudDraw.fit(label, screenWidth - 40);
        // Under the party's turn bar when it is shown at the top of the screen, over the middle
        int y = 6;
        float[] bar = PartyHud.bounds(PartyHudLayout.Hud.TURN_BAR);
        if (PartyHud.model() != null && PartyHudLayout.get(PartyHudLayout.Hud.TURN_BAR).visible && bar[2] > 0
                && bar[1] < 30 && bar[0] < screenWidth / 2f + 60 && bar[0] + bar[2] > screenWidth / 2f - 60)
            y = Math.round(bar[1] + bar[3]) + 4;
        int width = font.getWidth(text) + 14, x = (screenWidth - width) / 2;
        HudDraw.plate(context, ready ? Plate.GREEN : Plate.ORANGE, x, y, width, CHIP_HEIGHT, 1);
        HudDraw.text(context, text, x + 7, y + 4, HudDraw.TEXT, 1);

        // Who is ready: a plate per player, on as many rows as needed
        List<List<MiniGamePagePayloads.Practice.Voter>> rows = new ArrayList<>();
        List<MiniGamePagePayloads.Practice.Voter> row = new ArrayList<>();
        int rowWidth = 0;
        for (MiniGamePagePayloads.Practice.Voter each : practice.voters()) {
            int w = nameWidth(font, each);
            if (!row.isEmpty() && rowWidth + GAP + w > screenWidth - 16) {
                rows.add(row);
                row = new ArrayList<>();
                rowWidth = 0;
            }
            rowWidth += (row.isEmpty() ? 0 : GAP) + w;
            row.add(each);
        }
        if (!row.isEmpty()) rows.add(row);
        int top = y + CHIP_HEIGHT + GAP;
        for (List<MiniGamePagePayloads.Practice.Voter> line : rows) {
            int lineWidth = -GAP;
            for (MiniGamePagePayloads.Practice.Voter each : line) lineWidth += GAP + nameWidth(font, each);
            int left = (screenWidth - lineWidth) / 2;
            for (MiniGamePagePayloads.Practice.Voter each : line) {
                int w = nameWidth(font, each);
                HudDraw.plate(context, each.ready() ? Plate.GREEN : Plate.TEAL, left, top, w, NAME_HEIGHT, 1);
                HudDraw.text(context, HudDraw.fit(Text.literal(each.name()), NAME_MAX), left + 5, top + 3, each.ready() ? HudDraw.TEXT : HudDraw.TEXT_SOFT, 1);
                left += w + GAP;
            }
            top += NAME_HEIGHT + GAP;
        }
    }

    private static int nameWidth(TextRenderer font, MiniGamePagePayloads.Practice.Voter voter) {
        return Math.min(NAME_MAX, font.getWidth(voter.name())) + 10;
    }
}
