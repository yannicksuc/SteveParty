package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.items.custom.teleportation_books.AbstractTeleportationBookItem;
import fr.lordfinn.steveparty.payloads.custom.HereWeGoBookPayload;
import fr.lordfinn.steveparty.screen_handlers.TeleportationBookScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.STATE;

public class HereWeGoBookScreen extends TeleportationBookScreen {
    private static final int OPTION_WIDTH = 100, RADIO_BOX_SIZE = 9;
    private static final int RADIO_BOX_X = 4, RADIO_BOX_Y = 183, TICK_X = 15, TICK_Y = 183;
    private static final int TEXT_COLOR_SELECTED = 0x000000, TEXT_COLOR_NORMAL = 0x808080, TEXT_COLOR_HOVER = 0xFFD700;
    private static final int RADIO_BOX_SPACING = 12, X_OFFSET = 14, Y_OFFSET = 27, MAX_TEXT_WIDTH = 105;

    private final List<Text> options = List.of(
            Text.translatable("gui.steveparty.here_we_go.tp_to_current_mini-game"),
            Text.translatable("gui.steveparty.here_we_go.tp_to_last_used"),
            Text.translatable("gui.steveparty.here_we_go.tp_to_pos")
    );
    private int selectedOption = 0;
    private int hoveredOption = -1;

    public HereWeGoBookScreen(TeleportationBookScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.selectedOption = inventory.player.getMainHandStack().getOrDefault(STATE, 0);
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        super.drawBackground(context, delta, mouseX, mouseY);
        drawSelectionOptions(context, x + X_OFFSET, y + Y_OFFSET, mouseX, mouseY);
    }

    private void drawSelectionOptions(DrawContext context, int startX, int startY, int mouseX, int mouseY) {
        List<List<String>> wrappedOptions = wrapOptions();
        int currentY = startY;

        for (int i = 0; i < wrappedOptions.size(); i++) {
            int textColor = (i == selectedOption) ? TEXT_COLOR_SELECTED : (i == hoveredOption) ? TEXT_COLOR_HOVER : TEXT_COLOR_NORMAL;
            context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, startX, currentY, RADIO_BOX_X, RADIO_BOX_Y, RADIO_BOX_SIZE, RADIO_BOX_SIZE, 256, 256);
            if (i == selectedOption) context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, startX, currentY, TICK_X, TICK_Y, RADIO_BOX_SIZE, RADIO_BOX_SIZE, 256, 256);
            for (String line : wrappedOptions.get(i)) {
                context.drawText(textRenderer, line, startX + 14, currentY + 1, textColor, false);
                currentY += RADIO_BOX_SPACING;
            }
            currentY += RADIO_BOX_SPACING;
        }
    }

    private List<List<String>> wrapOptions() {
        List<List<String>> wrappedOptions = new ArrayList<>();
        for (Text option : options) wrappedOptions.add(wrapText(option.getString()));
        return wrappedOptions;
    }

    private List<String> wrapText(String text) {
        List<String> wrappedLines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : text.split(" ")) {
            if (textRenderer.getWidth(currentLine + " " + word) <= MAX_TEXT_WIDTH) {
                if (!currentLine.isEmpty()) currentLine.append(" ");
                currentLine.append(word);
            } else {
                wrappedLines.add(currentLine.toString());
                currentLine.setLength(0);
                currentLine.append(word);
            }
        }
        if (!currentLine.isEmpty()) wrappedLines.add(currentLine.toString());
        return wrappedLines;
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        hoveredOption = getHoveredOption(mouseX, mouseY);
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int clickedOption = getHoveredOption(mouseX, mouseY);
            if (clickedOption != -1) {
                selectedOption = clickedOption;
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0F, 0.5F));
                if (this.client != null && this.client.player instanceof ClientPlayerEntity player
                        && this.client.player.getMainHandStack() instanceof ItemStack bookStack
                        && bookStack.getItem() instanceof AbstractTeleportationBookItem) {
                    bookStack.set(STATE, selectedOption);
                    ClientPlayNetworking.send(new HereWeGoBookPayload(selectedOption));
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private int getHoveredOption(double mouseX, double mouseY) {
        int startX = (width - backgroundWidth) / 2 + X_OFFSET;
        int currentY = (height - backgroundHeight) / 2 + Y_OFFSET;
        List<List<String>> wrappedOptions = wrapOptions();
        for (int i = 0; i < wrappedOptions.size(); i++) {
            for (int lineIndex = 0; lineIndex < wrappedOptions.get(i).size(); lineIndex++) {
                if (mouseX >= startX && mouseX <= startX + OPTION_WIDTH && mouseY >= currentY && mouseY <= currentY + RADIO_BOX_SPACING) {
                    return i;
                }
                currentY += RADIO_BOX_SPACING;
            }
            currentY += RADIO_BOX_SPACING;
        }
        return -1;
    }
}
