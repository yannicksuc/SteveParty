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
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

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
    /** The galaxy, drawn under TEXTURE (which has a hole for it) and slowly turning. */
    private static final Identifier GALAXY = Steveparty.id("textures/gui/dice_forge_galaxy.png");
    private static final int GALAXY_SIZE = 142, GALAXY_CENTER_X = 88, GALAXY_CENTER_Y = 71;
    /** One turn of the galaxy (ms). */
    private static final long GALAXY_TURN_MS = 90_000;
    private static final float GHOST_ALPHA = 0.35f;
    private static final String KEY = "gui.steveparty.dice_forge.";

    // The core button, drawn pixel by pixel over the vortex center (CORE_X/Y: its center, between 4 pixels)
    private static final int CORE_X = DiceForgeScreenHandler.CENTER_X + 8, CORE_Y = DiceForgeScreenHandler.CENTER_Y + 8;
    private static final int BUTTON_SIZE = 24;
    /**
     * Pixel-art parts of the round button, from its edge inwards: outline, progress gauge (2 px), inner line, bevel,
     * face (-1: outside). The outer disc and the inner one are each a clean pixel circle; the gauge fills between.
     */
    private static final int[][] BUTTON_PARTS = buttonParts();
    private static final int PART_OUTLINE = 0, PART_GAUGE = 1, PART_LINE = 2, PART_BEVEL = 3, PART_FACE = 4;
    /** Outline and inner line: lit top-left, in shadow bottom-right, like the button itself. */
    private static final int CONTOUR_LIGHT = 0xFF4A2F78, CONTOUR_MID = 0xFF25163A, CONTOUR_DARK = 0xFF000000;
    /** The same, neutral grey, while the button cannot be pressed. */
    private static final int OFF_CONTOUR_LIGHT = 0xFF2C2A33, OFF_CONTOUR_MID = 0xFF1A1920;
    /** The gauge fills clockwise through a smooth gradient of these colours, violet to gold; orange when blocked. */
    private static final int[] GAUGE_COLORS = {0xFF8A3FFC, 0xFFD23CF0, 0xFFFF4FA3, 0xFFFF8A3D, 0xFFFFD35A};
    private static final int GAUGE_TRACK = 0xFF505050, GAUGE_BLOCKED = 0xFFE0703A;

    private final ItemStack gravityCore = new ItemStack(ModBlocks.GRAVITY_CORE);
    private final ItemStack blankFace = new ItemStack(net.minecraft.registry.Registries.ITEM.get(Steveparty.id("blank_dice_face")));

    private static int[][] buttonParts() {
        int[][] outer = peelDisc(BUTTON_SIZE, 11.8f), inner = peelDisc(BUTTON_SIZE, 8.9f);
        int[][] parts = new int[BUTTON_SIZE][BUTTON_SIZE];
        for (int y = 0; y < BUTTON_SIZE; y++) {
            for (int x = 0; x < BUTTON_SIZE; x++) {
                if (inner[y][x] >= 0) parts[y][x] = Math.min(PART_LINE + inner[y][x], PART_FACE);
                else if (outer[y][x] == 0) parts[y][x] = PART_OUTLINE;
                else parts[y][x] = outer[y][x] > 0 ? PART_GAUGE : -1;
            }
        }
        return parts;
    }

    /** @return for each pixel of a {@code size}-wide disc, its layer counted from the edge (-1 outside it). */
    private static int[][] peelDisc(int size, float radius) {
        int[][] layers = new int[size][size];
        float center = (size - 1) / 2f;
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                float dx = x - center, dy = y - center;
                layers[y][x] = dx * dx + dy * dy <= radius * radius ? Integer.MAX_VALUE : -1;
            }
        }
        // Peel it: the pixels of what is left that touch its outside (4 neighbors) make the next layer
        for (int layer = 0; ; layer++) {
            boolean peeled = false;
            int[][] snapshot = new int[size][];
            for (int y = 0; y < size; y++) snapshot[y] = layers[y].clone();
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if (snapshot[y][x] != Integer.MAX_VALUE) continue;
                    if (isOutside(snapshot, x + 1, y) || isOutside(snapshot, x - 1, y)
                            || isOutside(snapshot, x, y + 1) || isOutside(snapshot, x, y - 1)) {
                        layers[y][x] = layer;
                        peeled = true;
                    }
                }
            }
            if (!peeled) return layers;
        }
    }

    private static boolean isOutside(int[][] layers, int x, int y) {
        return y < 0 || y >= layers.length || x < 0 || x >= layers[y].length || layers[y][x] != Integer.MAX_VALUE;
    }

    public DiceForgeScreen(DiceForgeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 176;
        this.backgroundHeight = 225;
        this.playerInventoryTitleY = this.backgroundHeight - 93;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        drawGalaxy(context, x + GALAXY_CENTER_X, y + GALAXY_CENTER_Y);
        // Over it: its bevelled rim, the squares under the faces and the inventory panel
        context.drawTexture(RenderLayer::getGuiTextured, TEXTURE, x, y, 0, 0,
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

    /** The galaxy turns slowly around its center, under the rest of the screen. */
    private static void drawGalaxy(DrawContext context, int centerX, int centerY) {
        float angle = (Util.getMeasuringTimeMs() % GALAXY_TURN_MS) / (float) GALAXY_TURN_MS * 360f;
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(centerX, centerY, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        int half = GALAXY_SIZE / 2;
        context.drawTexture(RenderLayer::getGuiTextured, GALAXY, -half, -half, 0, 0,
                GALAXY_SIZE, GALAXY_SIZE, GALAXY_SIZE, GALAXY_SIZE);
        matrices.pop();
    }

    /** @return the gauge colour at {@code t} (0 at the top, 1 back to it), blended smoothly between GAUGE_COLORS. */
    private static int gaugeColor(float t) {
        float scaled = MathHelper.clamp(t, 0f, 1f) * (GAUGE_COLORS.length - 1);
        int from = Math.min(GAUGE_COLORS.length - 2, (int) scaled);
        return ColorHelper.lerp(scaled - from, GAUGE_COLORS[from], GAUGE_COLORS[from + 1]);
    }

    /** No title: the forge speaks for itself (the player inventory title stays). */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(this.textRenderer, this.playerInventoryTitle, this.playerInventoryTitleX,
                this.playerInventoryTitleY, 0x404040, false);
    }

    private boolean isOverButton(double mouseX, double mouseY) {
        if (!handler.isActivated()) return false;
        int px = (int) Math.floor(mouseX) - (this.x + CORE_X) + BUTTON_SIZE / 2;
        int py = (int) Math.floor(mouseY) - (this.y + CORE_Y) + BUTTON_SIZE / 2;
        return px >= 0 && py >= 0 && px < BUTTON_SIZE && py < BUTTON_SIZE && BUTTON_PARTS[py][px] >= 0;
    }

    private float getSmoothProgress(float delta) {
        if (!handler.isRunning()) return 0f;
        float progress = handler.getProgress();
        if (!handler.isBlocked() && progress < 1f) progress += delta / DiceForgeBlockEntity.CRAFT_TIME;
        return MathHelper.clamp(progress, 0f, 1f);
    }

    /**
     * The core button: a round violet button with a bevel (light top-left, dark bottom-right, like the vanilla
     * buttons), lighter when hovered, pressed in (bevel reversed) while the forge runs, dull when it cannot be
     * pressed; around it, the progress gauge, outlined in black so that it shows on the bright vortex.
     */
    private void drawCoreButton(DrawContext context, int cx, int cy, int mouseX, int mouseY, float delta) {
        boolean enabled = isButtonEnabled();
        boolean hovered = enabled && isOverButton(mouseX, mouseY);
        boolean pressed = handler.isRunning();
        float progress = getSmoothProgress(delta);
        // Disabled: neutral grey (the violet belongs to the button you can press), the core faded (see below)
        int face = !enabled ? 0xFF25232A : hovered ? 0xFF3C2560 : 0xFF2A1840;
        int light = !enabled ? 0xFF4C4958 : hovered ? 0xFF9B7BD0 : 0xFF7A5AA8;
        int dark = !enabled ? 0xFF141317 : 0xFF140A20;
        int contourLight = enabled ? CONTOUR_LIGHT : OFF_CONTOUR_LIGHT, contourMid = enabled ? CONTOUR_MID : OFF_CONTOUR_MID;
        int half = BUTTON_SIZE / 2;

        for (int y = 0; y < BUTTON_SIZE; y++) {
            for (int x = 0; x < BUTTON_SIZE; x++) {
                int part = BUTTON_PARTS[y][x];
                if (part < 0) continue;
                float px = x - (BUTTON_SIZE - 1) / 2f, py = y - (BUTTON_SIZE - 1) / 2f;
                // Which side of the light the pixel is on: top-left (< -1), bottom-right (> 1), or in between
                float side = px + py;
                int color;
                if (part == PART_OUTLINE && hovered) {
                    color = 0xFFFFFFFF; // hovered: white outline, like the vanilla buttons
                } else if (part == PART_OUTLINE || part == PART_LINE) {
                    color = side < -1 ? contourLight : side > 1 ? CONTOUR_DARK : contourMid;
                } else if (part == PART_GAUGE) {
                    // Clockwise from the top
                    float angle = (float) Math.toDegrees(Math.atan2(px, -py));
                    if (angle < 0) angle += 360f;
                    color = angle >= progress * 360f ? GAUGE_TRACK
                            : handler.isBlocked() ? GAUGE_BLOCKED
                            : gaugeColor(angle / 360f);
                } else if (part == PART_BEVEL && Math.abs(side) > 1) {
                    color = (side < 0) != pressed ? light : dark;
                } else {
                    color = face;
                }
                context.fill(cx - half + x, cy - half + y, cx - half + x + 1, cy - half + y + 1, color);
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
