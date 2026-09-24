package fr.lordfinn.steveparty.client.gui;

import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.client.screens.PartyStepsScreen;
import fr.lordfinn.steveparty.client.utils.ConfigurationManager;
import fr.lordfinn.steveparty.client.utils.SkinUtils;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PartyStepsHud implements HudRenderCallback {
    private static PartyData data = new PartyData();
    private static final Identifier DEFAULT_ICON = Identifier.of("steveparty", "textures/gui/steps/default.png");
    private static final Identifier BG_ICON = Identifier.of("steveparty", "textures/gui/step-background.png");
    private static final Map<PartyStepType, Identifier> STEP_ICON_MAP = new HashMap<>();
    private static final int CURRENT_STEP_COLOR = 0xFFFFFFFF;
    private static final int UPCOMING_STEP_COLOR = ColorHelper.fromFloats(0.3F, 1.0F, 1.0F, 1.0F);

    public static int hudX = 0; // Default X position
    public static int hudY = 0; // Default Y position
    public static boolean canDisplay = true; // Default width

    private static final KeyBinding toggleMovableModeKey = KeyBindingHelper.registerKeyBinding(
            new KeyBinding("key.steveparty.toggle_hud_movable_mode", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_M, "category.steveparty")
    );

    static {
        // Preload specific icons based on step types
        STEP_ICON_MAP.put(PartyStepType.MINI_GAME, Identifier.of("steveparty", "textures/gui/steps/mini_game.png"));
        STEP_ICON_MAP.put(PartyStepType.TOKEN_TURN, Identifier.of("steveparty", "textures/gui/steps/token_turn.png"));
        STEP_ICON_MAP.put(PartyStepType.BASIC_GAME_GENERATOR, Identifier.of("steveparty", "textures/gui/steps/game_generator.png"));
        STEP_ICON_MAP.put(PartyStepType.START_ROLLS, Identifier.of("steveparty", "textures/gui/steps/start_rolls.png"));
    }

    public PartyStepsHud() {
        initialize();
    }

    public void initialize() {
        hudX = ConfigurationManager.getPartyStepsHudX();
        hudY = ConfigurationManager.getPartyStepsHudY();
    }

    public static void registerKeyHandlers() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleMovableModeKey.wasPressed()) {
                MinecraftClient.getInstance().setScreen(new PartyStepsScreen());
                canDisplay = false;
            }
        });
    }

    public static void updateSteps(PartyData partyData) {
        data = partyData;
    }

    public static void clearData() {
        data = new PartyData();
    }

    @Override
    public void onHudRender(DrawContext drawContext, RenderTickCounter renderTickCounter) {
        if (canDisplay)
            drawHud(drawContext);
    }

    public void drawHud(DrawContext drawContext) {
        if (!hasValidPartyData()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (data.getSteps().isEmpty() || data.getStepIndex() < 0 || data.getStepIndex() >= data.getSteps().size())
            return;

        // Colors are baked into the vertices (DrawContext is deferred since 1.21.2: global shader
        // color / depth / blend state would not apply and would leak into the rest of the UI).
        int startX = calculateStepWidth(client, data.getSteps().get(data.getStepIndex())) + hudX + 57;
        for (int i = data.getStepIndex() + 1; i < data.getSteps().size(); i++) {
            startX = drawStep(drawContext, i, client, startX, UPCOMING_STEP_COLOR); // Reduced opacity for other steps
        }
        drawStep(drawContext, data.getStepIndex(), client, hudX + 12, CURRENT_STEP_COLOR);
        drawScores(drawContext, client);
    }

    /** Coins and stars of each player, under the steps (only when the party controller has coin / star items). */
    private void drawScores(DrawContext drawContext, MinecraftClient client) {
        PartyData.ScoreBoard scores = data.getScores();
        if (scores.entries().isEmpty()) return;
        ItemStack coin = iconOf(scores.coinItem());
        ItemStack star = iconOf(scores.starItem());
        int x = hudX + 4;
        int y = hudY + 36;
        int nameWidth = scores.entries().stream().mapToInt(entry -> client.textRenderer.getWidth(entry.name())).max().orElse(0);
        int rowWidth = 20 + nameWidth + (coin.isEmpty() ? 0 : 42) + (star.isEmpty() ? 0 : 42);
        drawContext.fill(x - 2, y - 2, x + rowWidth, y + scores.entries().size() * 18, 0x80000000);
        for (PartyData.ScoreEntry entry : scores.entries()) {
            Identifier skin = SkinUtils.getPlayerSkin(entry.player());
            if (skin != null)
                drawContext.drawTexture(RenderLayer::getGuiTextured, skin, x, y, 16, 16, 16, 16, 128, 128);
            int textY = y + 4;
            drawContext.drawText(client.textRenderer, entry.name(), x + 20, textY, 0xFFFFFFFF, true);
            int cx = x + 20 + nameWidth + 4;
            if (!coin.isEmpty()) {
                drawContext.drawItem(coin, cx, y);
                drawContext.drawText(client.textRenderer, String.valueOf(entry.coins()), cx + 17, textY, 0xFFFFD700, true);
                cx += 42;
            }
            if (!star.isEmpty()) {
                drawContext.drawItem(star, cx, y);
                drawContext.drawText(client.textRenderer, String.valueOf(entry.stars()), cx + 17, textY, 0xFFFFFF55, true);
            }
            y += 18;
        }
    }

    private static ItemStack iconOf(String itemId) {
        Identifier id = itemId.isEmpty() ? null : Identifier.tryParse(itemId);
        if (id == null || !Registries.ITEM.containsId(id)) return ItemStack.EMPTY;
        return new ItemStack(Registries.ITEM.get(id));
    }

    private int drawStep(DrawContext drawContext, int i, MinecraftClient client, int startX, int color) {
        PartyStep step = data.getSteps().get(i);
        drawStepBackground(drawContext, client, step, startX, hudY + 8, color);
        drawStepIcon(drawContext, client, step, startX, hudY + 8, color);
        drawStepText(drawContext, client, step, startX + 25, color);
        startX += calculateStepWidth(client, step) + 45;
        return startX;
    }

    private boolean hasValidPartyData() {
        return data.getSteps() != null && !data.getSteps().isEmpty();
    }

    private void drawStepIcon(DrawContext drawContext, MinecraftClient client, PartyStep step, int x, int y, int color) {
        Identifier icon = getStepIcon(step);
        if (icon != null) {
            drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, icon, x+3, y, 0, 0, 16, 16, 16,16, color);
        }
        if (step.getType() == PartyStepType.TOKEN_TURN) {
            drawPlayerSkinHead(drawContext, (TokenTurnPartyStep) step, x, y, 20, color);
        }
    }

    private static void drawPlayerSkinHead(DrawContext drawContext, TokenTurnPartyStep step, int x, int y, int side, int color) {
        UUID playerUuid = step.getOwnerUUID();
        if (playerUuid == null)
            return;
        Identifier skinTexture = SkinUtils.getPlayerSkin(playerUuid);
        if (skinTexture == null) {
            drawContext.fill(x, y, x + side, y + side, ColorHelper.withAlpha(ColorHelper.getAlpha(color), Color.GRAY.getRGB()));
        } else {
            drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, skinTexture, x + 3, y, 16, 16, 16, 16, 128, 128, color);
        }
    }

    private void drawStepBackground(DrawContext drawContext, MinecraftClient client, PartyStep step, int x, int y, int color) {
        int stepWidth = calculateStepWidth(client, step);
        drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, BG_ICON, x -8, y -8, 0, 0, 27, 32, 400,64, color);
        drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, BG_ICON, x +19, y -8, 0, 32, stepWidth + 10, 32, 400,64, color);
        drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, BG_ICON, x +19+stepWidth + 10, y -8, 27, 0, 12, 32, 400,64, color);
    }

    private Identifier getStepIcon(PartyStep step) {
        PartyStepType stepType = step.getType();
        return STEP_ICON_MAP.getOrDefault(stepType, DEFAULT_ICON);
    }

    private void drawStepText(DrawContext drawContext, MinecraftClient client, PartyStep step, int x, int color) {
        Text stepName = Text.translatable(step.getName());
        int textY = hudY + 7 + (20 - client.textRenderer.fontHeight) / 2;
        drawContext.drawText(client.textRenderer, stepName, x, textY, color, true);
    }

    private Color getStepColor(PartyStep step) {
        Integer colorValue = step.getStatus().getColor().getColorValue();
        return colorValue != null ? new Color(colorValue) : Color.GRAY;
    }

    private int calculateStepWidth(MinecraftClient client, PartyStep step) {
        Text stepName = Text.translatable(step.getName());
        return client.textRenderer.getWidth(stepName); // 20px for icon width
    }

    public static void saveConfigOnExit() {
        ConfigurationManager.setPartyStepsHudPosition(hudX, hudY);
    }
}
