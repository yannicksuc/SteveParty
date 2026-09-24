package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.Status;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
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
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;

/**
 * Dice forge screen. The vortex holds the 12 die faces (their count is their weight) and, in its center, the core,
 * which is the FORGE button (a golden ring around it shows the progress): blank faces go in on its left, the forged
 * die comes out on its right, and the 4 star fragments sit on its diagonals.
 */
public class DiceForgeScreen extends HandledScreen<DiceForgeScreenHandler> {
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/dice_forge.png");
    private static final float GHOST_ALPHA = 0.35f;
    private static final String KEY = "gui.steveparty.dice_forge.";

    // The core button, drawn pixel by pixel over the vortex center
    private static final int CORE_X = DiceForgeScreenHandler.CENTER_X + 8, CORE_Y = DiceForgeScreenHandler.CENTER_Y + 8;
    private static final float DISC_RADIUS = 9.5f, RING_INNER = 9.5f, RING_OUTER = 11.5f;
    private static final int RING_EXTENT = 12;

    private final ItemStack gravityCore = new ItemStack(ModBlocks.GRAVITY_CORE);
    private final ItemStack blankFace = new ItemStack(net.minecraft.registry.Registries.ITEM.get(Steveparty.id("blank_dice_face")));

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
                TEXTURE, x, y, 0, 0,
                this.backgroundWidth, this.backgroundHeight, 256, 256);

        // Faces: the brighter the frame, the more likely the face
        int totalWeight = getTotalWeight();
        for (int i = 0; i < FACE_SLOTS; i++) {
            ItemStack stack = handler.getInventory().getStack(i);
            if (!DiceFace.isFace(stack) || totalWeight <= 0) continue;
            int alpha = 0x50 + Math.round(0xAF * stack.getCount() / (float) getMaxWeight());
            int[] pos = DiceForgeScreenHandler.FACE_POSITIONS[i];
            drawSlotFrame(context, x + pos[0], y + pos[1], 0x30FFD890, (alpha << 24) | 0xFFE08C);
        }
        for (int[] pos : DiceForgeScreenHandler.FRAGMENT_POSITIONS) {
            drawSlotFrame(context, x + pos[0], y + pos[1], 0x30FFFFFF, 0x60FFFFFF);
        }
        drawSlotFrame(context, x + DiceForgeScreenHandler.BLANK_X, y + DiceForgeScreenHandler.BLANK_Y, 0x40FFC8F0, 0xE0FFC8F0);
        drawSlotFrame(context, x + DiceForgeScreenHandler.OUTPUT_X, y + DiceForgeScreenHandler.OUTPUT_Y, 0x40FFE696, 0xE0FFE696);

        if (handler.isActivated()) {
            drawCoreButton(context, x + CORE_X, y + CORE_Y, mouseX, mouseY, delta);
        } else {
            drawSlotFrame(context, x + DiceForgeScreenHandler.CENTER_X, y + DiceForgeScreenHandler.CENTER_Y, 0x40FFD8A0, 0x90FFE8C0);
        }
    }

    private static void drawSlotFrame(DrawContext context, int slotX, int slotY, int fill, int border) {
        context.fill(slotX - 1, slotY - 1, slotX + 17, slotY + 17, fill);
        context.fill(slotX - 1, slotY - 1, slotX + 17, slotY, border);
        context.fill(slotX - 1, slotY + 16, slotX + 17, slotY + 17, border);
        context.fill(slotX - 1, slotY, slotX, slotY + 16, border);
        context.fill(slotX + 16, slotY, slotX + 17, slotY + 16, border);
    }

    // ------------------------------------------------------------------ weights

    private int getTotalWeight() {
        int total = 0;
        for (int i = 0; i < FACE_SLOTS; i++) {
            ItemStack stack = handler.getInventory().getStack(i);
            if (DiceFace.isFace(stack)) total += stack.getCount();
        }
        return total;
    }

    private int getMaxWeight() {
        int max = 1;
        for (int i = 0; i < FACE_SLOTS; i++) {
            ItemStack stack = handler.getInventory().getStack(i);
            if (DiceFace.isFace(stack)) max = Math.max(max, stack.getCount());
        }
        return max;
    }

    // ------------------------------------------------------------------ core button

    private boolean isButtonEnabled() {
        return handler.isActivated() && (handler.isRunning() || handler.getStatus().allowsRunning());
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        if (!handler.isActivated()) return false;
        double dx = mouseX - (this.x + CORE_X), dy = mouseY - (this.y + CORE_Y);
        return dx * dx + dy * dy <= RING_OUTER * RING_OUTER;
    }

    private float getSmoothProgress(float delta) {
        if (!handler.isRunning()) return 0f;
        float progress = handler.getProgress();
        if (!handler.isBlocked() && progress < 1f) progress += delta / DiceForgeBlockEntity.CRAFT_TIME;
        return MathHelper.clamp(progress, 0f, 1f);
    }

    private void drawCoreButton(DrawContext context, int cx, int cy, int mouseX, int mouseY, float delta) {
        boolean enabled = isButtonEnabled();
        boolean hovered = isOverButton(mouseX, mouseY);
        float progress = getSmoothProgress(delta);
        int disc = enabled ? 0xE8200C38 : 0xE0181420;
        int rim = !enabled ? 0xFF5E586C : hovered ? 0xFFFFE08A : 0xFFD8C8F0;
        int ringFill = handler.isBlocked() ? 0xFFE0703A : 0xFFFFD35A;
        int ringTrack = handler.isRunning() ? 0x55FFFFFF : 0x30FFFFFF;

        for (int dy = -RING_EXTENT; dy < RING_EXTENT; dy++) {
            for (int dx = -RING_EXTENT; dx < RING_EXTENT; dx++) {
                float px = dx + 0.5f, py = dy + 0.5f;
                float d = MathHelper.sqrt(px * px + py * py);
                int color;
                if (d < DISC_RADIUS - 1f) {
                    color = disc;
                } else if (d < DISC_RADIUS) {
                    color = rim;
                } else if (d >= RING_INNER + 0.5f && d < RING_OUTER) {
                    // Clockwise from the top
                    float angle = (float) Math.toDegrees(Math.atan2(px, -py));
                    if (angle < 0) angle += 360f;
                    color = angle < progress * 360f ? ringFill : ringTrack;
                } else {
                    continue;
                }
                context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
        }
        if (enabled) {
            context.drawItem(gravityCore, cx - 8, cy - 8);
        } else {
            drawTranslucentItem(context, gravityCore, cx - 8, cy - 8, 0.45f);
        }
    }

    private List<Text> getButtonTooltip() {
        List<Text> lines = new ArrayList<>();
        Status status = handler.getStatus();
        if (handler.isRunning()) {
            lines.add(Text.translatableWithFallback(KEY + "stop_hint", "Click to stop production"));
            lines.add(Text.translatableWithFallback(KEY + "progress", "Progress: %s%%",
                    Math.round(handler.getProgress() * 100)).formatted(Formatting.GRAY));
        } else if (status.allowsRunning()) {
            lines.add(Text.translatableWithFallback(KEY + "core_hint", "Click the core to forge"));
            lines.add(Text.translatableWithFallback(KEY + "start_hint",
                    "Start forging: loops until stopped or a slot runs out").formatted(Formatting.GRAY));
        }
        if (status != Status.OK) lines.add(getStatusText(status).formatted(Formatting.RED));
        if (handler.isPowered()) {
            lines.add(Text.translatableWithFallback(KEY + "redstone_powered",
                    "Redstone: powered (production enabled)").formatted(Formatting.DARK_RED));
        }
        return lines;
    }

    private MutableText getStatusText(Status status) {
        return switch (status) {
            case NOT_ACTIVATED -> Text.translatableWithFallback(KEY + "status.not_activated",
                    "Insert a gravity core first (right-click the forge with it or use the center slot)");
            case NOT_ENOUGH_FACES -> DiceForgeBlockEntity.MIN_FACES <= 1
                    ? Text.translatableWithFallback(KEY + "status.no_face", "Place at least one dice face")
                    : Text.translatableWithFallback(KEY + "status.not_enough_faces",
                    "Place at least %s dice faces", DiceForgeBlockEntity.MIN_FACES);
            case LAYOUT_CHANGED -> Text.translatableWithFallback(KEY + "status.layout_changed",
                    "The faces changed since the craft started");
            case MISSING_FRAGMENT -> Text.translatableWithFallback(KEY + "status.missing_fragment",
                    "Put star fragments in the 4 slots around the core");
            case DUPLICATE_FRAGMENT -> Text.translatableWithFallback(KEY + "status.duplicate_fragment",
                    "Each fragment colour must be different (only black can be repeated)");
            case OUTPUT_BLOCKED -> Text.translatableWithFallback(KEY + "status.output_blocked",
                    "The output slot is full or holds another die");
            case NOT_ENOUGH_BLANK_FACES -> Text.translatableWithFallback(KEY + "status.not_enough_blank_faces",
                    "Each die needs %s blank faces (one per face placed on the ring)",
                    Math.max(1, countFaces(handler.getInventory())));
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

    // ------------------------------------------------------------------ slots: ghosts and previews

    @Override
    protected void drawSlot(DrawContext context, Slot slot) {
        int index = slot.id;
        if (index < DiceForgeBlockEntity.SIZE && !slot.hasStack()) {
            ItemStack ghost = getGhostStack(index);
            if (!ghost.isEmpty()) drawTranslucentItem(context, ghost, slot.x, slot.y, GHOST_ALPHA);
        }
        super.drawSlot(context, slot);
    }

    /** @return what to show at low opacity in an empty forge slot (remembered item, hint or preview). */
    private ItemStack getGhostStack(int index) {
        if (index == CENTER_SLOT) return handler.isActivated() ? ItemStack.EMPTY : gravityCore;
        if (index == OUTPUT_SLOT) return DiceForgeBlockEntity.createDie(handler.getInventory());
        Item ghost = handler.getGhost(index);
        if (ghost != null) return new ItemStack(ghost);
        return index == BLANK_SLOT ? blankFace : ItemStack.EMPTY;
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
        super.render(context, mouseX, mouseY, delta);

        if (isOverButton(mouseX, mouseY)) {
            context.drawTooltip(textRenderer, getButtonTooltip(), mouseX, mouseY);
        } else if (focusedSlot != null && focusedSlot.id < DiceForgeBlockEntity.SIZE && handler.getCursorStack().isEmpty()) {
            if (focusedSlot.hasStack()) {
                drawForgeItemTooltip(context, focusedSlot, mouseX, mouseY);
            } else {
                drawGhostTooltip(context, focusedSlot.id, mouseX, mouseY);
            }
        } else {
            this.drawMouseoverTooltip(context, mouseX, mouseY);
        }
    }

    /** Item tooltip, plus the weight and chance of a face, or what the blank faces are for. */
    private void drawForgeItemTooltip(DrawContext context, Slot slot, int mouseX, int mouseY) {
        ItemStack stack = slot.getStack();
        List<Text> lines = new ArrayList<>(getTooltipFromItem(stack));
        if (slot.id < FACE_SLOTS && DiceFace.isFace(stack)) {
            int total = Math.max(1, getTotalWeight());
            String chance = String.format(Locale.ROOT, "%.1f", 100f * stack.getCount() / total);
            lines.add(Text.translatableWithFallback(KEY + "weight", "Weight %s: %s%% chance per roll",
                    stack.getCount(), chance).formatted(Formatting.GOLD));
        } else if (slot.id == BLANK_SLOT) {
            lines.add(getBlankFacesHint().formatted(Formatting.LIGHT_PURPLE));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private MutableText getBlankFacesHint() {
        return Text.translatableWithFallback(KEY + "blank_hint",
                "Consumed: one per face on the ring (%s per die)", countFaces(handler.getInventory()));
    }

    private void drawGhostTooltip(DrawContext context, int index, int mouseX, int mouseY) {
        ItemStack ghost = getGhostStack(index);
        if (ghost.isEmpty()) return;
        List<Text> lines = new ArrayList<>();
        if (index == CENTER_SLOT) {
            lines.add(Text.translatableWithFallback(KEY + "insert_core", "Gravity core slot"));
            lines.add(getStatusText(Status.NOT_ACTIVATED).formatted(Formatting.GRAY));
        } else if (index == OUTPUT_SLOT) {
            lines.addAll(getTooltipFromItem(ghost));
            lines.add(Text.translatableWithFallback(KEY + "preview", "Preview of the forged die").formatted(Formatting.DARK_GRAY));
        } else if (index == BLANK_SLOT && handler.getGhost(index) == null) {
            lines.add(Text.translatableWithFallback(KEY + "blank_slot", "Blank dice faces"));
            lines.add(getBlankFacesHint().formatted(Formatting.GRAY));
        } else {
            lines.add(Text.translatableWithFallback(KEY + "missing", "Missing: %s", ghost.getName()).formatted(Formatting.RED));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }
}
