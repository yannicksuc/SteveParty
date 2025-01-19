package fr.lordfinn.steveparty.client.screens;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.jetbrains.annotations.NotNull;

import static fr.lordfinn.steveparty.client.StevepartyClient.PARTY_STEPS_HUD;
import static fr.lordfinn.steveparty.client.gui.PartyStepsHud.*;
public class PartyStepsScreen extends Screen {
    private boolean isDragging = false;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public PartyStepsScreen() {
        super(Text.empty());
    }

    @Override
    protected void init() {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        PARTY_STEPS_HUD.drawHud(context);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Check if the mouse is over the HUD and the left mouse button is clicked
        if (button == 0 && isMouseOverHud((int) mouseX, (int) mouseY)) {
            isDragging = true; // Start dragging
            dragOffsetX = (int) (mouseX - hudX);
            dragOffsetY = (int) (mouseY - hudY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        // If dragging is active, update the HUD's position
        if (isDragging && button == 0) {
            Window window = getWindowsSize();
            hudX = Math.max(0, Math.min(window.width, (int) mouseX - dragOffsetX));
            hudY = Math.max(0, Math.min(window.height, (int) mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    private static @NotNull PartyStepsScreen.Window getWindowsSize() {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getScaledWidth() - 10;
        int height = client.getWindow().getScaledHeight() - 10;
        return new Window(width, height);
    }

    private record Window(int width, int height) {
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        // Stop dragging when the left mouse button is released
        if (button == 0) {
            isDragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        Window window = getWindowsSize();
        if (client == null) return false;
        if (client.options.forwardKey.matchesKey(keyCode, scanCode)) hudY = Math.max(hudY - 1, 0);
        else if (client.options.backKey.matchesKey(keyCode, scanCode)) hudY = Math.min(hudY + 1, window.height);
        else if (client.options.leftKey.matchesKey(keyCode, scanCode)) hudX = Math.max(hudX - 1, 0);
        else if (client.options.rightKey.matchesKey(keyCode, scanCode)) hudX = Math.min(hudX + 1, window.width);
        else return super.keyPressed(keyCode, scanCode, modifiers);
        return true;
    }

    private static boolean isMouseOverHud(int mouseX, int mouseY) {
        Window window = getWindowsSize();
        return mouseX >= hudX && mouseX <= hudX + window.width
                && mouseY >= hudY && mouseY <= hudY + window.height;
    }

    @Override
    public void close() {
        super.close();
        canDisplay = true;
    }
}