package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.Status;
import fr.lordfinn.steveparty.screen_handlers.custom.DiceForgeScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;

public class DiceForgeScreen extends HandledScreen<DiceForgeScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/dice_forge.png");
    /**
     * Widgets sheet (64x64): button normal (0,0), hovered (0,16), disabled (0,32), gauge fill (0,48); each 40x14.
     */
    private static final Identifier WIDGETS = Steveparty.id("textures/gui/dice_forge_widgets.png");
    private static final int BUTTON_X = 134, BUTTON_Y = 2, BUTTON_W = 40, BUTTON_H = 14;
    private static final float GHOST_ALPHA = 0.35f;
    private static final String KEY = "gui.steveparty.dice_forge.";

    private final ItemStack gravityCore = new ItemStack(ModBlocks.GRAVITY_CORE);
    private float lastDelta = 0f;

    public DiceForgeScreen(DiceForgeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 225;
        this.playerInventoryTitleY = this.backgroundHeight - 93;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        RenderSystem.setShaderTexture(0, TEXTURE);
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        context.drawTexture(RenderLayer::getGuiOpaqueTexturedBackground,
                TEXTURE, x, y, 0,0,
                this.backgroundWidth, this.backgroundHeight, 256, 256);

        // Fragment slots and center slot frames (not baked in the texture)
        for (int[] pos : DiceForgeScreenHandler.FRAGMENT_POSITIONS) {
            drawSlotFrame(context, x + pos[0], y + pos[1], 0x30FFFFFF, 0x60FFFFFF);
        }
        drawSlotFrame(context, x + DiceForgeScreenHandler.CENTER_X, y + DiceForgeScreenHandler.CENTER_Y,
                0x40FFD8A0, 0x90FFE8C0);

        drawCraftButton(context, x + BUTTON_X, y + BUTTON_Y, mouseX, mouseY, delta);
    }

    private static void drawSlotFrame(DrawContext context, int slotX, int slotY, int fill, int border) {
        context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, fill);
        context.fill(slotX - 1, slotY - 1, slotX + 17, slotY, border);
        context.fill(slotX - 1, slotY + 16, slotX + 17, slotY + 17, border);
        context.fill(slotX - 1, slotY, slotX, slotY + 16, border);
        context.fill(slotX + 16, slotY, slotX + 17, slotY + 16, border);
    }

    // ------------------------------------------------------------------ craft button

    private boolean isButtonEnabled() {
        return handler.isRunning() || handler.getStatus().allowsRunning();
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        return isPointWithinBounds(BUTTON_X, BUTTON_Y, BUTTON_W, BUTTON_H, mouseX, mouseY);
    }

    private float getSmoothProgress(float delta) {
        if (!handler.isRunning()) return 0f;
        float progress = handler.getProgress();
        if (!handler.isBlocked() && progress < 1f) progress += delta / DiceForgeBlockEntity.CRAFT_TIME;
        return MathHelper.clamp(progress, 0f, 1f);
    }

    private void drawCraftButton(DrawContext context, int bx, int by, int mouseX, int mouseY, float delta) {
        boolean enabled = isButtonEnabled();
        int v = !enabled ? 32 : isOverButton(mouseX, mouseY) ? 16 : 0;
        context.drawTexture(RenderLayer::getGuiTextured, WIDGETS, bx, by, 0, v, BUTTON_W, BUTTON_H, 64, 64);

        // Gauge filling the button with the craft progress
        int fillWidth = Math.round((BUTTON_W - 2) * getSmoothProgress(delta));
        if (fillWidth > 0) {
            context.drawTexture(RenderLayer::getGuiTextured, WIDGETS, bx + 1, by + 1, 1, 49, fillWidth, BUTTON_H - 2, 64, 64);
        }

        Text label = handler.isRunning()
                ? Text.translatableWithFallback(KEY + "stop", "Stop")
                : Text.translatableWithFallback(KEY + "craft", "Craft");
        int color = enabled ? 0xFFFFFF : 0x9A94A8;
        context.drawCenteredTextWithShadow(textRenderer, label, bx + BUTTON_W / 2, by + (BUTTON_H - 8) / 2, color);
    }

    private List<Text> getButtonTooltip() {
        List<Text> lines = new ArrayList<>();
        Status status = handler.getStatus();
        if (handler.isRunning()) {
            lines.add(Text.translatableWithFallback(KEY + "stop_hint", "Click to stop production"));
            lines.add(Text.translatableWithFallback(KEY + "progress", "Progress: %s%%",
                    Math.round(handler.getProgress() * 100)).formatted(Formatting.GRAY));
        } else if (status.allowsRunning()) {
            lines.add(Text.translatableWithFallback(KEY + "start_hint",
                    "Start forging: loops until stopped or a slot runs out"));
        }
        if (status != Status.OK) lines.add(getStatusText(status).formatted(Formatting.RED));
        if (handler.isPowered()) {
            lines.add(Text.translatableWithFallback(KEY + "redstone_powered",
                    "Redstone: powered (production enabled)").formatted(Formatting.DARK_RED));
        }
        return lines;
    }

    private static net.minecraft.text.MutableText getStatusText(Status status) {
        return switch (status) {
            case NOT_ACTIVATED -> Text.translatableWithFallback(KEY + "status.not_activated",
                    "Insert a gravity core first (right-click the forge with it or use the center slot)");
            case NOT_ENOUGH_FACES -> Text.translatableWithFallback(KEY + "status.not_enough_faces",
                    "Place at least %s dice faces", DiceForgeBlockEntity.MIN_FACES);
            case LAYOUT_CHANGED -> Text.translatableWithFallback(KEY + "status.layout_changed",
                    "The faces changed since the craft started");
            case MISSING_FRAGMENT -> Text.translatableWithFallback(KEY + "status.missing_fragment",
                    "Put star fragments in the 4 slots around the core");
            case DUPLICATE_FRAGMENT -> Text.translatableWithFallback(KEY + "status.duplicate_fragment",
                    "Each fragment colour must be different (only black can be repeated)");
            case OUTPUT_BLOCKED -> Text.translatableWithFallback(KEY + "status.output_blocked",
                    "The output slot is full or holds another die");
            case OK -> Text.empty();
        };
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isOverButton(mouseX, mouseY)) {
            if (isButtonEnabled() && client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, DiceForgeScreenHandler.BUTTON_TOGGLE);
                MinecraftClient.getInstance().getSoundManager()
                        .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ slots: ghosts, preview, progress overlay

    @Override
    protected void drawSlot(DrawContext context, Slot slot) {
        int index = slot.id;
        if (index >= DiceForgeBlockEntity.SIZE) {
            super.drawSlot(context, slot);
            return;
        }
        if (!slot.hasStack()) {
            ItemStack ghost = getGhostStack(index);
            if (!ghost.isEmpty()) drawTranslucentItem(context, ghost, slot.x, slot.y, GHOST_ALPHA);
        }
        super.drawSlot(context, slot);

        if (index == CENTER_SLOT && handler.isRunning()) {
            // Item cooldown-like overlay: the veil shrinks as the craft progresses
            float remaining = 1f - getSmoothProgress(lastDelta);
            if (remaining > 0f) {
                int top = slot.y + MathHelper.floor(16f * (1f - remaining));
                int bottom = top + MathHelper.ceil(16f * remaining);
                context.getMatrices().push();
                context.getMatrices().translate(0, 0, 200);
                context.fill(RenderLayer.getGuiOverlay(), slot.x, top, slot.x + 16, bottom, 0x80FFFFFF);
                context.getMatrices().pop();
            }
        }
    }

    /** @return what to show at low opacity in an empty forge slot (remembered face/fragment, preview, core). */
    private ItemStack getGhostStack(int index) {
        if (index == CENTER_SLOT) {
            if (!handler.isActivated()) return gravityCore;
            return DiceForgeBlockEntity.createDie(handler.getInventory());
        }
        Item ghost = handler.getGhost(index);
        return ghost == null ? ItemStack.EMPTY : new ItemStack(ghost);
    }

    private static void drawTranslucentItem(DrawContext context, ItemStack stack, int x, int y, float alpha) {
        context.draw();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        context.drawItem(stack, x, y);
        context.draw();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.lastDelta = delta;
        super.render(context, mouseX, mouseY, delta);
        this.drawMouseoverTooltip(context, mouseX, mouseY);

        if (isOverButton(mouseX, mouseY)) {
            context.drawTooltip(textRenderer, getButtonTooltip(), mouseX, mouseY);
        } else if (focusedSlot != null && !focusedSlot.hasStack() && focusedSlot.id < DiceForgeBlockEntity.SIZE
                && handler.getCursorStack().isEmpty()) {
            drawGhostTooltip(context, focusedSlot.id, mouseX, mouseY);
        }
    }

    private void drawGhostTooltip(DrawContext context, int index, int mouseX, int mouseY) {
        ItemStack ghost = getGhostStack(index);
        if (ghost.isEmpty()) return;
        List<Text> lines = new ArrayList<>();
        if (index == CENTER_SLOT && !handler.isActivated()) {
            lines.add(Text.translatableWithFallback(KEY + "insert_core", "Gravity core slot"));
            lines.add(getStatusText(Status.NOT_ACTIVATED).formatted(Formatting.GRAY));
        } else if (index == CENTER_SLOT) {
            lines.addAll(getTooltipFromItem(ghost));
            lines.add(Text.translatableWithFallback(KEY + "preview", "Preview of the forged die").formatted(Formatting.DARK_GRAY));
        } else {
            lines.add(Text.translatableWithFallback(KEY + "missing", "Missing: %s", ghost.getName()).formatted(Formatting.RED));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }
}
