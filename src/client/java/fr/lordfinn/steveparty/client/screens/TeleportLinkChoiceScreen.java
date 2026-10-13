package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.client.gui.PartyGui;
import fr.lordfinn.steveparty.client.gui.UiText;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.payloads.custom.TeleportLinkChoicePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

/**
 * The Tile Linker Brush painted from a Teleport tile to a board space it has no link to: two buttons, « TP » (an
 * ender pearl: a token landing on the tile is sent there) or « Lien » (the brush: a path), each telling more on hover.
 * Closing it links nothing. Painted with the mod's GUI kit like the dice pickers.
 */
public class TeleportLinkChoiceScreen extends Screen {
    private static final String KEY = "gui.steveparty.teleport_link_choice.";
    private static final int BUTTON_W = 46, BUTTON_H = 36, GAP = 6, PAD = 10, TOP = 16;
    /** The click that painted the link does not answer it. */
    private static final long INPUT_DELAY_MS = 250;

    private final TeleportLinkChoicePayload question;
    private final long shownAt = Util.getMeasuringTimeMs();
    private final ItemStack teleportIcon = new ItemStack(Items.ENDER_PEARL);
    private final ItemStack linkIcon = new ItemStack(ModItems.TILE_LINKER_BRUSH);
    private int panelX, panelY, panelWidth, panelHeight;

    public TeleportLinkChoiceScreen(TeleportLinkChoicePayload question) {
        super(Text.translatable(KEY + "title"));
        this.question = question;
    }

    /** The question from the server: shown at once (the brush's stroke stops there). */
    public static void onPayload(MinecraftClient client, TeleportLinkChoicePayload payload) {
        if (client.world == null || client.player == null) return;
        client.setScreen(new TeleportLinkChoiceScreen(payload));
    }

    @Override
    protected void init() {
        panelWidth = Math.max(2 * BUTTON_W + GAP, textRenderer.getWidth(title) + 32) + 2 * PAD;
        panelHeight = TOP + BUTTON_H + PAD;
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
    }

    /** {x, y} of the button {@code index} (0: TP, 1: link). */
    private int[] at(int index) {
        int left = panelX + (panelWidth - (2 * BUTTON_W + GAP)) / 2;
        return new int[]{left + index * (BUTTON_W + GAP), panelY + TOP};
    }

    private int buttonAt(double mouseX, double mouseY) {
        for (int i = 0; i < 2; i++) {
            int[] b = at(i);
            if (HitArea.contains(mouseX, mouseY, b[0], b[1], BUTTON_W, BUTTON_H)) return i;
        }
        return -1;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // No blur: the tiles stay readable behind it
        context.fill(0, 0, width, height, 0x50000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        PartyGui.panel(context, panelX, panelY, panelWidth, panelHeight, PartyGui.PANEL);
        PartyGui.titlePlate(context, textRenderer, panelX + panelWidth / 2, panelY - 11, 0, title, PartyGui.BUTTON_SELECTED);
        int hovered = buttonAt(mouseX, mouseY);
        for (int i = 0; i < 2; i++) {
            int[] b = at(i);
            // Neither is picked beforehand
            PartyGui.button(context, b[0], b[1], BUTTON_W, BUTTON_H, i == hovered ? PartyGui.BUTTON.brighter() : PartyGui.BUTTON, false);
            context.drawItem(i == 0 ? teleportIcon : linkIcon, b[0] + (BUTTON_W - 16) / 2, b[1] + 5);
            UiText.centered(context, textRenderer, Text.translatable(KEY + (i == 0 ? "teleport" : "link")), b[0], b[1] + 24,
                    BUTTON_W, PartyGui.TEXT_DARK, false);
        }
        if (hovered >= 0) {
            context.drawTooltip(textRenderer, Text.translatable(KEY + (hovered == 0 ? "teleport.tip" : "link.tip")), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = buttonAt(mouseX, mouseY);
        if (button == 0 && index >= 0) {
            pick(index == 0);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 1: TP, 2: link. */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_1 || keyCode == GLFW.GLFW_KEY_KP_1) {
            pick(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_2 || keyCode == GLFW.GLFW_KEY_KP_2) {
            pick(false);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void pick(boolean teleport) {
        if (Util.getMeasuringTimeMs() - shownAt < INPUT_DELAY_MS) return;
        ClientPlayNetworking.send(new TeleportLinkChoicePayload(question.from(), question.to(), teleport));
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
