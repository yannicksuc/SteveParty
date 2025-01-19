package fr.lordfinn.steveparty.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.blocks.custom.PartyController.PartyData;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStep;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.PartyStepType;
import fr.lordfinn.steveparty.blocks.custom.PartyController.steps.TokenTurnPartyStep;
import fr.lordfinn.steveparty.client.screens.PartyStepsScreen;
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
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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

        initShader();
        int startX = calculateStepWidth(client, data.getSteps().get(data.getStepIndex())) + hudX + 57;
        for (int i = data.getStepIndex() + 1; i < data.getSteps().size(); i++) {
            startX = drawStep(drawContext, i, client, startX, true);
            RenderSystem.setShaderColor(1, 1, 1, 0.3f); // Reduced opacity for other steps
        }
        drawStep(drawContext, data.getStepIndex(), client, hudX + 12, true);
        RenderSystem.setShaderColor(1, 1, 1, 1f);
        drawStep(drawContext, data.getStepIndex(), client, hudX + 12, true);
        resetShader();
    }

    private static void initShader() {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1, 1, 1, 0.3f);
    }

    private static void resetShader() {
        RenderSystem.clearShader();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1f);
    }

    private int drawStep(DrawContext drawContext, int i, MinecraftClient client, int startX, boolean draw) {
        PartyStep step = data.getSteps().get(i);
        if (draw) {
            drawStepBackground(drawContext, client, step, startX, hudY + 8);
            drawStepIcon(drawContext, client, step, startX, hudY + 8, 20);
            drawStepText(drawContext, client, step, startX + 25);
        }
        startX += calculateStepWidth(client, step) + 45;
        return startX;
    }

    private boolean hasValidPartyData() {
        return data.getSteps() != null && !data.getSteps().isEmpty();
    }

    private void drawStepIcon(DrawContext drawContext, MinecraftClient client, PartyStep step, int x, int y, int side) {
        Identifier icon = getStepIcon(step);
        if (icon != null) {
            drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, icon, x+3, y, 0, 0, 16, 16, 16,16);
        }
        if (step.getType() == PartyStepType.TOKEN_TURN) {
            drawPlayerSkinHead(drawContext, (TokenTurnPartyStep) step, x, y, side);
        }
    }

    private static void drawPlayerSkinHead(DrawContext drawContext, TokenTurnPartyStep step, int x, int y, int side) {
        UUID playerUuid = step.getOwnerUUID();
        if (playerUuid == null)
            return;
        Identifier skinTexture = SkinUtils.getPlayerSkin(playerUuid);
        if (skinTexture == null) {
            drawContext.fill(x, y, x + side, y + side, Color.GRAY.getRGB());
        } else {
            drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, skinTexture, x + 3, y, 16, 16, 16, 16, 128, 128);
        }
    }

    private void drawStepBackground(DrawContext drawContext, MinecraftClient client, PartyStep step, int x, int y) {
        drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, BG_ICON, x -8, y -8, 0, 0, 27, 32, 400,64);
        drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, BG_ICON, x +19, y -8, 0, 32, calculateStepWidth(client, step) + 10, 32, 400,64);
        drawContext.drawTexture(RenderLayer::getGuiTexturedOverlay, BG_ICON, x +19+calculateStepWidth(client, step) + 10, y -8, 27, 0, 12, 32, 400,64);
    }

    private Identifier getStepIcon(PartyStep step) {
        PartyStepType stepType = step.getType();
        return STEP_ICON_MAP.getOrDefault(stepType, DEFAULT_ICON);
    }

    private void drawStepText(DrawContext drawContext, MinecraftClient client, PartyStep step, int x) {
        Text stepName = Text.translatable(step.getName());
        int textY = hudY + 7 + (20 - client.textRenderer.fontHeight) / 2;
        drawContext.drawText(client.textRenderer, stepName, x, textY, 0xFFFFFF, true);
    }

    private Color getStepColor(PartyStep step) {
        Integer colorValue = step.getStatus().getColor().getColorValue();
        return colorValue != null ? new Color(colorValue) : Color.GRAY;
    }

    private int calculateStepWidth(MinecraftClient client, PartyStep step) {
        Text stepName = Text.translatable(step.getName());
        return client.textRenderer.getWidth(stepName); // 20px for icon width
    }
}
