package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.items.custom.teleportation_books.TeleportingTarget;
import fr.lordfinn.steveparty.payloads.HereWeComeBookPayload;
import fr.lordfinn.steveparty.screen_handlers.TeleportationBookScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.components.ModComponents.TP_TARGETS;

public class HereWeComeBookScreen extends TeleportationBookScreen {
    private final List<TeleportingTarget> targets;

    private static final int SQUARE_ICONS_SIZE = 9;
    private static final int GROUP_ICON_START_X = 4, GROUP_ICON_START_Y = 194, GROUP_ICON_INTERVAL_X = 2;
    private static final int PLUS_ICON_X = 4, PLUS_ICON_Y = 205, MINUS_ICON_X = 15, MINUS_ICON_Y = 205;
    private static final int TEXT_INPUT_X = 26, TEXT_INPUT_Y = 205, TEXT_INPUT_WIDTH = 44, TEXT_INPUT_HEIGHT = 9;
    private static final int TEXT_COLOR_NORMAL = 0x808080, TEXT_COLOR_HOVER = 0xFFD700;
    private static final int LINE_SPACING = 12, START_Y_OFFSET = 27;
    private static final int GROUP_OFFSET_X = 15, TEXT_CAPACITY_OFFSET_X = 27, TEXT_PRIORITY_OFFSET_X = 74, MINUS_OFFSET_X = 121;
    private static final int PLUS_OFFSET_X = 15;

    public HereWeComeBookScreen(TeleportationBookScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.targets = new ArrayList<>(inventory.player.getMainHandStack().getOrDefault(TP_TARGETS, List.of()));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        super.drawBackground(context, delta, mouseX, mouseY);
        int currentY = y + START_Y_OFFSET;

        for (int i = 0; i < targets.size(); i++) {
            TeleportingTarget target = targets.get(i);
            drawTargetRow(context, target, x, currentY, i, mouseX, mouseY);
            currentY += LINE_SPACING;
        }
        int plusIconY = isMouseOver(mouseX, mouseY, x + PLUS_OFFSET_X, currentY, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE) ? PLUS_ICON_Y + LINE_SPACING : PLUS_ICON_Y;
        context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, x + PLUS_OFFSET_X, currentY, PLUS_ICON_X, plusIconY, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE, 256, 256);
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        super.drawForeground(context, mouseX, mouseY);
        int currentY = y + START_Y_OFFSET;

        for (int i = 0; i < targets.size(); i++) {
            TeleportingTarget target = targets.get(i);
            drawTooltipRow(context, target, x, currentY, i, mouseX, mouseY);
            currentY += LINE_SPACING;
        }
    }

    private void drawTargetRow(DrawContext context, TeleportingTarget target, int startX, int startY, int index, int mouseX, int mouseY) {

        boolean hoverCapacity = isMouseOver(mouseX, mouseY, startX + TEXT_CAPACITY_OFFSET_X, startY, TEXT_INPUT_WIDTH, TEXT_INPUT_HEIGHT);
        boolean hoverPriority = isMouseOver(mouseX, mouseY, startX + TEXT_PRIORITY_OFFSET_X, startY, TEXT_INPUT_WIDTH, TEXT_INPUT_HEIGHT);
        boolean hoverMinus = isMouseOver(mouseX, mouseY, startX + MINUS_OFFSET_X, startY, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE);

        int groupTextureX = GROUP_ICON_START_X + (target.group.ordinal() * (SQUARE_ICONS_SIZE + GROUP_ICON_INTERVAL_X));
        context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, startX + GROUP_OFFSET_X, startY, groupTextureX, GROUP_ICON_START_Y, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE, 256, 256);

        context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, startX + TEXT_CAPACITY_OFFSET_X, startY, TEXT_INPUT_X, hoverCapacity ? TEXT_INPUT_Y + LINE_SPACING : TEXT_INPUT_Y, TEXT_INPUT_WIDTH, TEXT_INPUT_HEIGHT, 256, 256);
        context.drawText(textRenderer, target.fillCapacity == 0 ? "∞" : String.valueOf(target.fillCapacity), startX + TEXT_CAPACITY_OFFSET_X + 1, startY + 1, hoverCapacity ? TEXT_COLOR_HOVER : TEXT_COLOR_NORMAL, false);

        context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, startX + TEXT_PRIORITY_OFFSET_X, startY, TEXT_INPUT_X, hoverPriority ? TEXT_INPUT_Y + LINE_SPACING : TEXT_INPUT_Y, TEXT_INPUT_WIDTH, TEXT_INPUT_HEIGHT, 256, 256);
        context.drawText(textRenderer, String.valueOf(target.fillPriorityWeight), startX + TEXT_PRIORITY_OFFSET_X + 1, startY + 1, hoverPriority ? TEXT_COLOR_HOVER : TEXT_COLOR_NORMAL, false);

        context.drawTexture(RenderLayer::getGuiTexturedOverlay, TEXTURE, startX + MINUS_OFFSET_X, startY, MINUS_ICON_X, hoverMinus ? MINUS_ICON_Y + LINE_SPACING : MINUS_ICON_Y, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE, 256, 256);
    }

    private void drawTooltipRow(DrawContext context, TeleportingTarget target, int startX, int startY, int index, int mouseX, int mouseY) {

        boolean hoverCapacity = isMouseOver(mouseX, mouseY, startX + TEXT_CAPACITY_OFFSET_X, startY, TEXT_INPUT_WIDTH, TEXT_INPUT_HEIGHT);
        boolean hoverPriority = isMouseOver(mouseX, mouseY, startX + TEXT_PRIORITY_OFFSET_X, startY, TEXT_INPUT_WIDTH, TEXT_INPUT_HEIGHT);
        boolean hoverGroup = isMouseOver(mouseX, mouseY, startX + GROUP_OFFSET_X, startY, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE);

        if (hoverGroup) {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable("gui.steveparty.group").setStyle(Style.EMPTY.withColor(0xFFD700).withBold(true)));
            lines.add(Text.translatable("gui.steveparty.group_description").setStyle(Style.EMPTY.withItalic(true)));
            TeleportingTarget.Group[] groups = TeleportingTarget.Group.values();
            int selectedIndex = target.group.ordinal();
            lines.add(MutableText.of(Text.of(groups[selectedIndex - 1 > 0 ? selectedIndex - 1 : groups.length - 1].name()).getContent()).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
            lines.add(MutableText.of(Text.of("-> "+ groups[selectedIndex].name()).getContent()).setStyle(Style.EMPTY.withBold(true)));
            lines.add(MutableText.of(Text.of(groups[selectedIndex + 1 < groups.length ? selectedIndex + 1 : 0].name()).getContent()).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
            lines.add(MutableText.of(Text.of("...").getContent()).setStyle(Style.EMPTY.withColor(Formatting.GRAY)));
            context.drawTooltip(textRenderer, lines, mouseX -x, mouseY-y);
        }

        if (hoverCapacity) {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable("gui.steveparty.capacity").setStyle(Style.EMPTY.withColor(0xFFD700).withBold(true)));
            lines.add(Text.translatable("gui.steveparty.capacity_description").setStyle(Style.EMPTY.withItalic(true)));
            context.drawTooltip(textRenderer, lines, mouseX-x, mouseY-y);
        }

        if (hoverPriority) {
            List<Text> lines = new ArrayList<>();
            lines.add(Text.translatable("gui.steveparty.priority").setStyle(Style.EMPTY.withColor(0xFFD700).withBold(true)));
            lines.add(Text.translatable("gui.steveparty.priority_description").setStyle(Style.EMPTY.withItalic(true)));
            context.drawTooltip(textRenderer, lines, mouseX-x, mouseY-y);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int startY = y + START_Y_OFFSET;
            for (int i = 0; i < targets.size(); i++) {
                if (isMouseOver(mouseX, mouseY, x + MINUS_OFFSET_X, startY, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE)) {
                    targets.remove(i);
                    sendUpdateToServer();

                    // 🎵 Son pour la suppression d'une cible
                    MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.6F));
                    return true;
                }
                startY += LINE_SPACING;
            }

            if (isMouseOver(mouseX, mouseY, x + PLUS_OFFSET_X, startY, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE)) {
                targets.add(new TeleportingTarget(TeleportingTarget.Group.EVERYONE, 0, 0));
                sendUpdateToServer();

                // 🎵 Son pour l'ajout d'une nouvelle cible
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.8F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int i = 0;
        for (TeleportingTarget target : targets) {
            boolean playedSound = false;

            // 🎛️ Gestion du changement de groupe avec le scroll
            if (isMouseOver(mouseX, mouseY, x + GROUP_OFFSET_X, y + START_Y_OFFSET + i * LINE_SPACING, SQUARE_ICONS_SIZE, SQUARE_ICONS_SIZE)) {
                int index = target.group.ordinal() + (verticalAmount > 0 ? 1 : -1);
                if (index >= TeleportingTarget.Group.values().length) {
                    index = 0;
                } else if (index < 0) {
                    index = TeleportingTarget.Group.values().length - 1;
                }
                target.group = TeleportingTarget.Group.values()[index];
                sendUpdateToServer();
                playedSound = true;
            }

            // 🔢 Gestion du scroll sur fillCapacity
            if (isMouseOver(mouseX, mouseY, x + TEXT_CAPACITY_OFFSET_X, y + START_Y_OFFSET + i * LINE_SPACING, TEXT_INPUT_WIDTH, TEXT_INPUT_HEIGHT)) {
                if (verticalAmount > 0) {
                    target.fillCapacity = (target.fillCapacity == 0) ? 1 : target.fillCapacity + 1;
                } else {
                    target.fillCapacity = (target.fillCapacity == 1) ? 0 : Math.max(0, target.fillCapacity - 1);
                }
                sendUpdateToServer();
                playedSound = true;
            }

            // 📶 Gestion du scroll sur fillPriorityWeight
            if (isMouseOver(mouseX, mouseY, x + TEXT_PRIORITY_OFFSET_X, y + START_Y_OFFSET + i * LINE_SPACING, TEXT_INPUT_WIDTH, TEXT_INPUT_HEIGHT)) {
                target.fillPriorityWeight = Math.max(0, target.fillPriorityWeight + (verticalAmount > 0 ? 1 : -1));
                sendUpdateToServer();
                playedSound = true;
            }

            // 🎵 Jouer le son si un changement a été effectué
            if (playedSound) {
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F, 0.7F));
                return true;
            }

            i++;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    private void sendUpdateToServer() {
        if (this.client != null && this.client.player instanceof ClientPlayerEntity player) {
            player.getMainHandStack().set(TP_TARGETS, targets);
            ClientPlayNetworking.send(new HereWeComeBookPayload(targets));
        }
    }

    private boolean isMouseOver(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
