package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.Status;
import fr.lordfinn.steveparty.client.mixin.SlotAccessor;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.screen_handlers.custom.DiceForgeScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
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
 * Dice forge screen: a launch star, the galaxy in its heart. The galaxy holds the 12 die faces (their count is their
 * weight), the 5 star fragments around the core and, in its center, the core, which is the FORGE button (a golden
 * ring around it shows the progress). The five gems on the points of the star hold the dice modules put on every die
 * forged; the capsule under the galaxy takes the blank faces in on its left and gives the forged die on its right.
 * <p>
 * The galaxy turns slowly; the faces turn with it, the fragments the other way and faster. Only the client moves
 * those slots ({@link #turnSlots}): the server never reads slot positions.
 */
public class DiceForgeScreen extends HandledScreen<DiceForgeScreenHandler> {
    /**
     * 512 x 512 atlas (the art sources): the background at (0, 0), the overlay drawn over
     * the galaxy (its ring, the module gems, the capsule) at (256, 0), then the light slots drawn on the galaxy.
     */
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/dice_forge.png");
    private static final int ATLAS_SIZE = 512, OVERLAY_U = 256;
    /** Part of each turning slot's position below one pixel (GUI px), by slot id; 0 for the slots that stay still. */
    private final float[] subPixelX = new float[DiceForgeBlockEntity.SIZE], subPixelY = new float[DiceForgeBlockEntity.SIZE];
    private static final int LIGHT_SLOT_V = 336, LIGHT_SQUARE_U = 0, LIGHT_ROUND_U = 18, LIGHT_SLOT_SIZE = 18;
    /** The galaxy, drawn on the background's navy disc, under the overlay, and slowly turning. */
    private static final Identifier GALAXY = Steveparty.id("textures/gui/dice_forge_galaxy.png");
    private static final int GALAXY_SIZE = 142;
    private static final int GALAXY_CENTER_X = DiceForgeScreenHandler.GALAXY_X, GALAXY_CENTER_Y = DiceForgeScreenHandler.GALAXY_Y;
    /** One turn of the galaxy (ms): slow enough for the slots turning with it to be easy to aim at. */
    private static final long GALAXY_TURN_MS = 150_000;
    /** The fragments turn the other way, this much faster than the galaxy. */
    private static final float FRAGMENT_SPIN = -1.5f;
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
    /** A light halo on the galaxy: a thin gold circle (white when hovered, faded when it cannot be pressed). */
    private static final int OUTLINE_GOLD = 0xC8FFF0A8, OUTLINE_HOVERED = 0xFFFFFFFF, OUTLINE_OFF = 0x60FFFFFF;
    /** The veil inside it, its inner line and bevel (lit top-left, in shadow bottom-right; reversed while pressed). */
    private static final int VEIL = 0x28FFFFFF, VEIL_HOVERED = 0x50FFFFFF, VEIL_OFF = 0x14FFFFFF;
    private static final int LINE = 0x40FFFFFF, BEVEL_LIGHT = 0x50FFFFFF, BEVEL_DARK = 0x40000000;
    /** The gauge fills clockwise through a smooth gradient of these colours, violet to gold; orange when blocked. */
    private static final int[] GAUGE_COLORS = {0xFF8A3FFC, 0xFFD23CF0, 0xFFFF4FA3, 0xFFFF8A3D, 0xFFFFD35A};
    private static final int GAUGE_TRACK = VEIL, GAUGE_BLOCKED = 0xFFE0703A;

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
        this.backgroundWidth = 236;
        this.backgroundHeight = 330;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = (this.width - this.backgroundWidth) / 2;
        int y = (this.height - this.backgroundHeight) / 2;
        RenderSystem.enableBlend();
        context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, ATLAS_SIZE, ATLAS_SIZE);
        drawGalaxy(context, x + GALAXY_CENTER_X, y + GALAXY_CENTER_Y);

        // Light slots on the galaxy, where the faces and fragments are this frame
        for (int i = 0; i < FACE_SLOTS; i++) {
            drawLightSlot(context, x, y, handler.getSlot(i), LIGHT_SQUARE_U);
        }
        for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
            drawLightSlot(context, x, y, handler.getSlot(i), LIGHT_ROUND_U);
        }
        // Faces: the brighter the gold contour, the more likely the face
        int totalWeight = getTotalWeight();
        for (int i = 0; i < FACE_SLOTS; i++) {
            Slot slot = handler.getSlot(i);
            if (!DiceFace.isFace(slot.getStack()) || totalWeight <= 0) continue;
            int alpha = 0x50 + Math.round(0xAF * slot.getStack().getCount() / (float) getMaxWeight());
            pushSubPixel(context, slot);
            drawSlotContour(context, x + slot.x, y + slot.y, (alpha << 24) | 0xFFE08C);
            context.getMatrices().pop();
        }

        // Over the galaxy: its ring, the module gems and the capsule
        context.drawTexture(TEXTURE, x, y, OVERLAY_U, 0, this.backgroundWidth, this.backgroundHeight, ATLAS_SIZE, ATLAS_SIZE);
        RenderSystem.disableBlend();

        drawCoreButton(context, x + CORE_X, y + CORE_Y, mouseX, mouseY, delta);
    }

    private void drawLightSlot(DrawContext context, int x, int y, Slot slot, int u) {
        pushSubPixel(context, slot);
        context.drawTexture(TEXTURE, x + slot.x - 1, y + slot.y - 1, u, LIGHT_SLOT_V,
                LIGHT_SLOT_SIZE, LIGHT_SLOT_SIZE, ATLAS_SIZE, ATLAS_SIZE);
        context.getMatrices().pop();
    }

    /** Shifts the drawing by the part of a turning slot's position below one pixel, for a smooth rotation. */
    private void pushSubPixel(DrawContext context, Slot slot) {
        context.getMatrices().push();
        if (slot.id < subPixelX.length) context.getMatrices().translate(subPixelX[slot.id], subPixelY[slot.id], 0);
    }

    /** The contour of a light square slot (no corners), in one colour. */
    private static void drawSlotContour(DrawContext context, int slotX, int slotY, int color) {
        context.fill(slotX + 1, slotY - 1, slotX + 15, slotY, color);
        context.fill(slotX + 1, slotY + 16, slotX + 15, slotY + 17, color);
        context.fill(slotX - 1, slotY + 1, slotX, slotY + 15, color);
        context.fill(slotX + 16, slotY + 1, slotX + 17, slotY + 15, color);
    }

    // ------------------------------------------------------------------ turning slots

    /** @return the galaxy's angle now (degrees, clockwise on screen). */
    private static float getGalaxyAngle() {
        return (Util.getMeasuringTimeMs() % GALAXY_TURN_MS) / (float) GALAXY_TURN_MS * 360f;
    }

    /**
     * Moves the face slots with the galaxy (same angle, as if it carried them) and the fragment slots the other way,
     * faster; the items stay upright. The slot keeps the whole pixel (clicks), the rest below one pixel shifts its
     * drawing, so that the slots glide instead of stepping from pixel to pixel. Done before each frame.
     */
    private void turnSlots() {
        float angle = getGalaxyAngle();
        for (int i = 0; i < FACE_SLOTS; i++) {
            int[] rest = DiceForgeScreenHandler.FACE_POSITIONS[i];
            turnSlot(handler.getSlot(i), rest[0], rest[1], angle);
        }
        for (int i = 0; i < FRAGMENT_SLOTS; i++) {
            int[] rest = DiceForgeScreenHandler.FRAGMENT_POSITIONS[i];
            turnSlot(handler.getSlot(FIRST_FRAGMENT_SLOT + i), rest[0], rest[1], angle * FRAGMENT_SPIN);
        }
    }

    private void turnSlot(Slot slot, int restX, int restY, float degrees) {
        double a = Math.toRadians(degrees), cos = Math.cos(a), sin = Math.sin(a);
        double dx = restX + 8 - GALAXY_CENTER_X, dy = restY + 8 - GALAXY_CENTER_Y;
        double sx = GALAXY_CENTER_X + dx * cos - dy * sin - 8, sy = GALAXY_CENTER_Y + dx * sin + dy * cos - 8;
        int px = (int) Math.floor(sx), py = (int) Math.floor(sy);
        SlotAccessor accessor = (SlotAccessor) slot;
        accessor.steveparty$setX(px);
        accessor.steveparty$setY(py);
        subPixelX[slot.id] = (float) (sx - px);
        subPixelY[slot.id] = (float) (sy - py);
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

    /** The galaxy turns slowly around its center, on the background's navy disc and under its ring. */
    private static void drawGalaxy(DrawContext context, int centerX, int centerY) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(centerX, centerY, 0);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(getGalaxyAngle()));
        int half = GALAXY_SIZE / 2;
        context.drawTexture(GALAXY, -half, -half, 0, 0,
                GALAXY_SIZE, GALAXY_SIZE, GALAXY_SIZE, GALAXY_SIZE);
        matrices.pop();
    }

    /** @return the gauge colour at {@code t} (0 at the top, 1 back to it), blended smoothly between GAUGE_COLORS. */
    private static int gaugeColor(float t) {
        float scaled = MathHelper.clamp(t, 0f, 1f) * (GAUGE_COLORS.length - 1);
        int from = Math.min(GAUGE_COLORS.length - 2, (int) scaled);
        return ColorHelper.Argb.lerp(scaled - from, GAUGE_COLORS[from], GAUGE_COLORS[from + 1]);
    }

    /** No titles: the forge speaks for itself, and the star leaves no room above the inventory. */
    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
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
     * The core: a light halo on the galaxy, ringed with a thin gold circle. Once the forge is activated it is the
     * FORGE button: brighter when hovered, its bevel (light top-left, dark bottom-right) reversed while the forge
     * runs, faded when it cannot be pressed; inside the gold circle, the progress gauge. Before that, it is the
     * gravity core slot (the slot draws the core's ghost).
     */
    private void drawCoreButton(DrawContext context, int cx, int cy, int mouseX, int mouseY, float delta) {
        boolean activated = handler.isActivated();
        boolean enabled = isButtonEnabled();
        boolean hovered = enabled && isOverButton(mouseX, mouseY);
        boolean pressed = handler.isRunning();
        float progress = getSmoothProgress(delta);
        int veil = !activated ? VEIL : !enabled ? VEIL_OFF : hovered ? VEIL_HOVERED : VEIL;
        int outline = hovered ? OUTLINE_HOVERED : activated && !enabled ? OUTLINE_OFF : OUTLINE_GOLD;
        int half = BUTTON_SIZE / 2;

        for (int y = 0; y < BUTTON_SIZE; y++) {
            for (int x = 0; x < BUTTON_SIZE; x++) {
                int part = BUTTON_PARTS[y][x];
                if (part < 0) continue;
                float px = x - (BUTTON_SIZE - 1) / 2f, py = y - (BUTTON_SIZE - 1) / 2f;
                // Which side of the light the pixel is on: top-left (< -1), bottom-right (> 1), or in between
                float side = px + py;
                int color;
                if (part == PART_OUTLINE) {
                    color = outline;
                } else if (part == PART_GAUGE) {
                    // Clockwise from the top
                    float angle = (float) Math.toDegrees(Math.atan2(px, -py));
                    if (angle < 0) angle += 360f;
                    color = angle >= progress * 360f ? GAUGE_TRACK
                            : handler.isBlocked() ? GAUGE_BLOCKED
                            : gaugeColor(angle / 360f);
                } else if (!activated) {
                    color = veil; // the core slot: a plain halo
                } else if (part == PART_LINE) {
                    color = LINE;
                } else if (part == PART_BEVEL && Math.abs(side) > 1) {
                    color = (side < 0) != pressed ? BEVEL_LIGHT : BEVEL_DARK;
                } else {
                    color = veil;
                }
                context.fill(cx - half + x, cy - half + y, cx - half + x + 1, cy - half + y + 1, color);
            }
        }
        if (!activated) return;
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
                    "Put star fragments in the 5 slots around the core");
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
            if (!ghost.isEmpty()) {
                pushSubPixel(context, slot);
                drawTranslucentItem(context, ghost, slot.x, slot.y, GHOST_ALPHA);
                context.getMatrices().pop();
            }
        }
        pushSubPixel(context, slot);
        super.drawSlot(context, slot);
        context.getMatrices().pop();
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
        turnSlots();
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
        } else if (isModuleSlot(slot.id)) {
            lines.add(Text.translatableWithFallback(KEY + "module_hint",
                    "Not consumed: every die forged carries it").formatted(Formatting.AQUA));
        }
        context.drawTooltip(textRenderer, lines, mouseX, mouseY);
    }

    private MutableText getBlankFacesHint() {
        return Text.translatableWithFallback(KEY + "blank_hint",
                "Consumed: one per face on the ring (%s per die)", countFaces(handler.getInventory()));
    }

    private void drawGhostTooltip(DrawContext context, int index, int mouseX, int mouseY) {
        if (isModuleSlot(index)) {
            context.drawTooltip(textRenderer, List.of(
                    Text.translatableWithFallback(KEY + "module_slot", "Dice module slot"),
                    Text.translatableWithFallback(KEY + "module_slot_hint",
                            "Optional. A module placed here is put on every die forged, and is not consumed; its count is the count the die gets").formatted(Formatting.GRAY)),
                    mouseX, mouseY);
            return;
        }
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
