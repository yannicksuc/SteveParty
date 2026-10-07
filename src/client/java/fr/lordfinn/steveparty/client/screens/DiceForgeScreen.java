package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.client.gui.HandCursor;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.Status;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.screen_handlers.custom.DiceForgeScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;

/**
 * Dice forge screen: a launch star, the galaxy in its heart. The galaxy holds the 12 die faces (their count is their
 * weight), the 5 star fragments around the core and, in its center, the gravity core. The five gems on the points of the star hold the dice modules put on every die
 * forged; the capsule under the galaxy takes the blank faces in on its left and gives the forged die on its right,
 * and its arrow is the FORGE button (it fills with the progress).
 */
public class DiceForgeScreen extends HandledScreen<DiceForgeScreenHandler> {
    /**
     * 512 x 512 atlas (the art sources): the background at (0, 0), the overlay drawn over
     * the galaxy (its ring, the module gems, the capsule) at (256, 0), then the light slots drawn on the galaxy.
     */
    private static final Identifier TEXTURE = Steveparty.id("textures/gui/dice_forge.png");
    private static final int ATLAS_SIZE = 512, OVERLAY_U = 256;
    private static final int LIGHT_SLOT_V = 336, LIGHT_SQUARE_U = 0, LIGHT_ROUND_U = 18, LIGHT_SLOT_SIZE = 18;
    /** The galaxy, drawn on the background's navy disc, under the overlay, slowly turning (its slots stay still). */
    private static final Identifier GALAXY = Steveparty.id("textures/gui/dice_forge_galaxy.png");
    private static final int GALAXY_SIZE = 142;
    private static final int GALAXY_CENTER_X = DiceForgeScreenHandler.GALAXY_X, GALAXY_CENTER_Y = DiceForgeScreenHandler.GALAXY_Y;
    /** One turn of the galaxy (ms). */
    private static final long GALAXY_TURN_MS = 150_000;
    private static final float GHOST_ALPHA = 0.35f;
    private static final String KEY = "gui.steveparty.dice_forge.";
    /** Tooltips wrap at this width (GUI px), so that long hints stay readable. */
    private static final int TOOLTIP_WIDTH = 170;

    /**
     * The FORGE button is the arrow of the capsule, between the blank faces and the die (only the screen draws it):
     * its shaft from ARROW_X0, its head's base at ARROW_X1, and the box that takes the clicks.
     */
    private static final int ARROW_X0 = DiceForgeScreenHandler.BLANK_X + 18, ARROW_X1 = DiceForgeScreenHandler.OUTPUT_X - 5,
            ARROW_Y = DiceForgeScreenHandler.BLANK_Y + 8;
    private static final int[][] ARROW_PIXELS = arrowPixels();
    private static final int BUTTON_X0 = DiceForgeScreenHandler.BLANK_X + 17, BUTTON_X1 = DiceForgeScreenHandler.OUTPUT_X - 1,
            BUTTON_Y0 = DiceForgeScreenHandler.BLANK_Y, BUTTON_Y1 = DiceForgeScreenHandler.BLANK_Y + 16;
    /**
     * The arrow: pale gold at rest, white when hovered, lilac while forging (the part still to fill), muted violet
     * when it cannot be pressed.
     */
    private static final int ARROW_IDLE = 0xFFFFF0A8, ARROW_HOVERED = 0xFFFFFFFF, ARROW_RUNNING = 0xFFD9C2FF,
            ARROW_OFF = 0xFF9B7FC8, ARROW_OFF_HOVERED = 0xFFC9B5EE;
    /** While forging it fills from the left through a smooth gradient of these colours, violet to gold; orange when blocked. */
    private static final int[] GAUGE_COLORS = {0xFF8A3FFC, 0xFFD23CF0, 0xFFFF4FA3, 0xFFFF8A3D, 0xFFFFD35A};
    private static final int GAUGE_BLOCKED = 0xFFE0703A;
    /** The button's frame: white when hovered (like a vanilla button), and its sunken look while held down. */
    private static final int BUTTON_HOVER_FRAME = 0xFFFFFFFF, BUTTON_PRESSED_FILL = 0x70000000, BUTTON_PRESSED_SHADOW = 0xC0000000;
    /** Ready to forge, the resting arrow pulses towards white once per PULSE_MS, by at most PULSE_AMOUNT. */
    private static final long PULSE_MS = 1200;
    private static final float PULSE_AMOUNT = 0.55f;
    /** Free GUI pixels kept around the forge; with less room (large GUI scales) the whole screen is shrunk to fit. */
    private static final int FIT_MARGIN = 4;

    /** The FORGE button is held down (clicked on it, not released yet). */
    private boolean buttonPressed;
    /** Scale the screen is drawn at: 1, or less when the window is too small for it at this GUI scale. */
    private float fit = 1f;

    private final ItemStack gravityCore = new ItemStack(ModBlocks.GRAVITY_CORE);
    private final ItemStack blankFace = new ItemStack(net.minecraft.registry.Registries.ITEM.get(Steveparty.id("blank_dice_face")));

    /** The arrow's pixels: a 2 px shaft, then a head 6 px tall narrowing over 3 columns. */
    private static int[][] arrowPixels() {
        List<int[]> pixels = new ArrayList<>();
        for (int x = ARROW_X0; x < ARROW_X1; x++) {
            pixels.add(new int[]{x, ARROW_Y - 1});
            pixels.add(new int[]{x, ARROW_Y});
        }
        for (int k = 0; k < 3; k++) {
            for (int y = ARROW_Y - 1 - (2 - k); y <= ARROW_Y + (2 - k); y++) pixels.add(new int[]{ARROW_X1 + k, y});
        }
        return pixels.toArray(new int[0][]);
    }

    public DiceForgeScreen(DiceForgeScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundWidth = 236;
        this.backgroundHeight = 330;
    }

    /**
     * At a large GUI scale the forge (330 px tall) does not fit in the window: the screen is then laid out on a larger
     * virtual screen and drawn shrunk ({@link #fit}), mouse coordinates converted, so the star and the whole inventory
     * stay visible and usable.
     */
    @Override
    protected void init() {
        int realWidth = this.width, realHeight = this.height;
        if (client != null) {
            realWidth = client.getWindow().getScaledWidth();
            realHeight = client.getWindow().getScaledHeight();
        }
        fit = Math.min(1f, Math.min(realWidth / (float) (backgroundWidth + 2 * FIT_MARGIN),
                realHeight / (float) (backgroundHeight + 2 * FIT_MARGIN)));
        this.width = MathHelper.ceil(realWidth / fit);
        this.height = MathHelper.ceil(realHeight / fit);
        super.init();
    }

    private double toScreen(double coordinate) {
        return coordinate / fit;
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
            drawSlotContour(context, x + slot.x, y + slot.y, (alpha << 24) | 0xFFE08C);
        }

        // Over the galaxy: its ring, the module gems and the capsule
        context.drawTexture(TEXTURE, x, y, OVERLAY_U, 0, this.backgroundWidth, this.backgroundHeight, ATLAS_SIZE, ATLAS_SIZE);
        RenderSystem.disableBlend();

        drawArrowButton(context, x, y, mouseX, mouseY, delta);
    }

    private static void drawLightSlot(DrawContext context, int x, int y, Slot slot, int u) {
        context.drawTexture(TEXTURE, x + slot.x - 1, y + slot.y - 1, u, LIGHT_SLOT_V,
                LIGHT_SLOT_SIZE, LIGHT_SLOT_SIZE, ATLAS_SIZE, ATLAS_SIZE);
    }

    /** The contour of a light square slot in one colour, its sides meeting diagonally (rounded corners). */
    private static void drawSlotContour(DrawContext context, int slotX, int slotY, int color) {
        context.fill(slotX, slotY - 1, slotX + 16, slotY, color);
        context.fill(slotX, slotY + 16, slotX + 16, slotY + 17, color);
        context.fill(slotX - 1, slotY, slotX, slotY + 16, color);
        context.fill(slotX + 16, slotY, slotX + 17, slotY + 16, color);
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

    // ------------------------------------------------------------------ core and FORGE button

    private boolean isButtonEnabled() {
        return handler.isActivated() && (handler.isRunning() || handler.getStatus().allowsRunning());
    }

    /** The galaxy turns slowly around its center, on the background's navy disc and under its ring. */
    private static void drawGalaxy(DrawContext context, int centerX, int centerY) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(centerX, centerY, 0);
        float angle = (Util.getMeasuringTimeMs() % GALAXY_TURN_MS) / (float) GALAXY_TURN_MS * 360f;
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        int half = GALAXY_SIZE / 2;
        context.drawTexture(GALAXY, -half, -half, 0, 0, GALAXY_SIZE, GALAXY_SIZE, GALAXY_SIZE, GALAXY_SIZE);
        matrices.pop();
    }

    /** @return the gauge colour at {@code t} (0 at the arrow's tail, 1 at its tip), blended smoothly between GAUGE_COLORS. */
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
        double px = mouseX - this.x, py = mouseY - this.y;
        return px >= BUTTON_X0 && px < BUTTON_X1 && py >= BUTTON_Y0 && py < BUTTON_Y1;
    }

    private float getSmoothProgress(float delta) {
        if (!handler.isRunning()) return 0f;
        float progress = handler.getProgress();
        if (!handler.isBlocked() && progress < 1f) progress += delta / DiceForgeBlockEntity.CRAFT_TIME;
        return MathHelper.clamp(progress, 0f, 1f);
    }

    /**
     * The FORGE button, the arrow from the blank faces to the die: white when hovered, muted when it cannot be
     * pressed; while forging it fills from the left as the die is built (orange when the forge is blocked). Only the
     * gap between the two slots is the button: hovering a slot is hovering the slot.
     */
    private void drawArrowButton(DrawContext context, int x, int y, int mouseX, int mouseY, float delta) {
        boolean enabled = isButtonEnabled();
        boolean hovered = isOverButton(mouseX, mouseY);
        boolean pressed = enabled && hovered && buttonPressed;
        int left = x + BUTTON_X0, top = y + BUTTON_Y0, right = x + BUTTON_X1, bottom = y + BUTTON_Y1;
        if (pressed) {
            // Sunken: darker inside, a shadow along its top and left edges, the arrow one pixel lower
            context.fill(left, top, right, bottom, BUTTON_PRESSED_FILL);
            context.fill(left, top, right, top + 1, BUTTON_PRESSED_SHADOW);
            context.fill(left, top + 1, left + 1, bottom, BUTTON_PRESSED_SHADOW);
        }
        if (hovered && enabled) context.drawBorder(left - 1, top - 1, right - left + 2, bottom - top + 2, BUTTON_HOVER_FRAME);

        int base = hovered ? (enabled ? ARROW_HOVERED : ARROW_OFF_HOVERED) : !enabled ? ARROW_OFF
                : handler.isRunning() ? ARROW_RUNNING : ARROW_IDLE;
        if (!hovered && enabled && !handler.isRunning() && handler.getStatus() == Status.OK) {
            // Ready to forge: a gentle pulse invites the click
            float wave = 0.5f - 0.5f * MathHelper.cos((Util.getMeasuringTimeMs() % PULSE_MS) / (float) PULSE_MS * MathHelper.TAU);
            base = ColorHelper.Argb.lerp(wave * PULSE_AMOUNT, ARROW_IDLE, ARROW_HOVERED);
        }
        float progress = getSmoothProgress(delta);
        float length = ARROW_X1 + 2 - ARROW_X0;
        int shift = pressed ? 1 : 0;
        for (int[] pixel : ARROW_PIXELS) {
            float t = (pixel[0] - ARROW_X0) / length;
            int color = t >= progress ? base : handler.isBlocked() ? GAUGE_BLOCKED : gaugeColor(t);
            if (pressed) color = ColorHelper.Argb.lerp(0.25f, color, 0xFF000000);
            int px = x + pixel[0] + shift, py = y + pixel[1] + shift;
            context.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** The FORGE arrow: what a click does now, the progress, and what is missing. */
    private List<Text> getButtonTooltip() {
        List<Text> lines = new ArrayList<>();
        Status status = handler.getStatus();
        if (handler.isRunning()) {
            lines.add(Text.translatableWithFallback(KEY + "forging", "Forging...").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
            lines.add(point(Formatting.LIGHT_PURPLE, Text.translatableWithFallback(KEY + "progress", "Progress: %s%%",
                    Math.round(handler.getProgress() * 100))));
            lines.add(point(Formatting.LIGHT_PURPLE, Text.translatableWithFallback(KEY + "stop_hint", "Click to stop production")));
        } else {
            lines.add(Text.translatableWithFallback(KEY + "forge", "Forge").formatted(Formatting.GOLD, Formatting.BOLD));
            if (status.allowsRunning()) {
                lines.add(point(Formatting.GOLD, Text.translatableWithFallback(KEY + "core_hint", "Click the arrow to forge")));
                lines.add(point(Formatting.GOLD, Text.translatableWithFallback(KEY + "start_hint",
                        "Start forging: loops until stopped or a slot runs out")));
            }
        }
        if (status != Status.OK) lines.add(point(Formatting.RED, getStatusText(status).formatted(Formatting.RED)));
        if (handler.isPowered()) {
            lines.add(point(Formatting.DARK_RED, Text.translatableWithFallback(KEY + "redstone_powered",
                    "Redstone: powered (production enabled)").formatted(Formatting.DARK_RED)));
        }
        return lines;
    }

    /** "• text", the bullet in {@code colour}, the text grey unless it already has a colour. */
    private static Text point(Formatting colour, MutableText text) {
        if (text.getStyle().getColor() == null) text.formatted(Formatting.GRAY);
        return Text.literal("• ").formatted(colour).append(text);
    }

    /**
     * The output slot: the die being made (or the dice made, when there are some): its faces and modules, what each
     * die costs, what is missing, then the die's own tooltip.
     */
    private List<Text> getOutputTooltip(ItemStack shown, boolean made) {
        Inventory inventory = handler.getInventory();
        int modules = 0;
        for (int i = FIRST_MODULE_SLOT; i < FIRST_MODULE_SLOT + MODULE_SLOTS; i++) modules += inventory.getStack(i).getCount();
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatableWithFallback(KEY + "output", "Forged die").formatted(Formatting.GOLD, Formatting.BOLD));
        if (made) {
            lines.add(point(Formatting.GREEN, Text.translatableWithFallback(KEY + "output.ready", "%s ready: take them",
                    shown.getCount()).formatted(Formatting.GREEN)));
        } else {
            lines.add(point(Formatting.GOLD, Text.translatableWithFallback(KEY + "output.preview", "Preview of the next die")));
        }
        lines.add(point(Formatting.GOLD, Text.translatableWithFallback(KEY + "output.faces", "%s faces, %s modules",
                countFaces(inventory), modules)));
        lines.add(point(Formatting.GOLD, Text.translatableWithFallback(KEY + "output.cost",
                "Each die uses %s blank faces and one fragment of each colour", Math.max(1, countFaces(inventory)))));
        Status status = handler.getStatus();
        if (status != Status.OK && status != Status.OUTPUT_BLOCKED) {
            lines.add(point(Formatting.RED, getStatusText(status).formatted(Formatting.RED)));
        }
        List<Text> item = getTooltipFromItem(shown);
        if (item.size() > 1) lines.addAll(item.subList(1, item.size()));
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
        mouseX = toScreen(mouseX);
        mouseY = toScreen(mouseY);
        if (button == 0 && isOverButton(mouseX, mouseY)) {
            buttonPressed = isButtonEnabled();
            if (isButtonEnabled() && client != null && client.interactionManager != null) {
                client.interactionManager.clickButton(handler.syncId, DiceForgeScreenHandler.BUTTON_TOGGLE);
                MinecraftClient.getInstance().getSoundManager()
                        .play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) buttonPressed = false;
        return super.mouseReleased(toScreen(mouseX), toScreen(mouseY), button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(toScreen(mouseX), toScreen(mouseY), button, toScreen(deltaX), toScreen(deltaY));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return super.mouseScrolled(toScreen(mouseX), toScreen(mouseY), horizontalAmount, verticalAmount);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        super.mouseMoved(toScreen(mouseX), toScreen(mouseY));
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
    public void removed() {
        HandCursor.reset();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int realMouseX, int realMouseY, float delta) {
        // Drawn shrunk when the window is too small (see init), the tooltips at full size
        int mouseX = (int) toScreen(realMouseX), mouseY = (int) toScreen(realMouseY);
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.scale(fit, fit, 1f);
        super.render(context, mouseX, mouseY, delta);
        matrices.pop();
        mouseX = realMouseX;
        mouseY = realMouseY;
        // The hand over the FORGE arrow, only when it can be clicked
        HandCursor.update(isButtonEnabled() && isOverButton(toScreen(mouseX), toScreen(mouseY)));

        if (isOverButton(toScreen(mouseX), toScreen(mouseY))) {
            drawWrappedTooltip(context, getButtonTooltip(), mouseX, mouseY);
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
        if (slot.id == OUTPUT_SLOT) {
            drawWrappedTooltip(context, getOutputTooltip(stack, true), mouseX, mouseY);
            return;
        }
        if (slot.id == CENTER_SLOT) {
            // The core in the forge: what it does there, and that it can be taken back
            drawWrappedTooltip(context, List.of(stack.getName().copy().formatted(Formatting.LIGHT_PURPLE),
                    Text.translatableWithFallback(KEY + "core_in_place", "Wakes the forge up").formatted(Formatting.GRAY),
                    Text.translatableWithFallback(KEY + "core_take", "Take it out to put the forge to sleep")
                            .formatted(Formatting.GRAY)), mouseX, mouseY);
            return;
        }
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
        drawWrappedTooltip(context, lines, mouseX, mouseY);
    }

    private void drawWrappedTooltip(DrawContext context, List<Text> lines, int mouseX, int mouseY) {
        List<OrderedText> wrapped = new ArrayList<>();
        for (Text line : lines) wrapped.addAll(textRenderer.wrapLines(line, TOOLTIP_WIDTH));
        context.drawOrderedTooltip(textRenderer, wrapped, mouseX, mouseY);
    }

    /** A slot's guide: its name in bold and colour, its tags ([Optional], [Not consumed]...), then short points in grey. */
    private static List<Text> slotGuide(String name, String nameFallback, Formatting colour, Tag[] tags, String[][] points) {
        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatableWithFallback(KEY + name, nameFallback).formatted(colour, Formatting.BOLD));
        if (tags.length > 0) {
            MutableText line = Text.empty();
            for (int i = 0; i < tags.length; i++) {
                if (i > 0) line.append(Text.literal(" "));
                line.append(Text.literal("[").append(Text.translatableWithFallback(KEY + tags[i].key(), tags[i].fallback()))
                        .append("]").formatted(tags[i].colour()));
            }
            lines.add(line);
        }
        for (String[] point : points) {
            lines.add(Text.literal("• ").formatted(colour)
                    .append(Text.translatableWithFallback(KEY + point[0], point[1]).formatted(Formatting.GRAY)));
        }
        return lines;
    }

    /** A tag of a slot's guide, in its own colour. */
    private record Tag(String key, String fallback, Formatting colour) {
    }

    private static final Tag NOT_CONSUMED = new Tag("tag.not_consumed", "Not consumed", Formatting.GREEN);
    private static final Tag OPTIONAL = new Tag("tag.optional", "Optional", Formatting.AQUA);
    private static final Tag[] FACE_TAGS = {NOT_CONSUMED}, FRAGMENT_TAGS = {}, MODULE_TAGS = {OPTIONAL, NOT_CONSUMED};

    private static final String[][] FACE_POINTS = {
            {"face_slot.weight", "Stack size = the face's weight"},
            {"face_slot.chance", "The heavier, the more often it comes up"}};
    private static final String[][] FRAGMENT_POINTS = {
            {"fragment_slot.all", "Fill all 5 slots"},
            {"fragment_slot.colours", "One colour per slot"},
            {"fragment_slot.black", "Black can repeat and is not consumed"},
            {"fragment_slot.use", "One fragment of each colour per die"}};
    private static final String[][] MODULE_POINTS = {
            {"module_slot.every", "Put on every die forged"},
            {"module_slot.count", "Stack size = how many the die gets"}};

    private MutableText getBlankFacesHint() {
        return Text.translatableWithFallback(KEY + "blank_hint",
                "Consumed: one per face on the ring (%s per die)", countFaces(handler.getInventory()));
    }

    private void drawGhostTooltip(DrawContext context, int index, int mouseX, int mouseY) {
        List<Text> guide = null;
        if (isModuleSlot(index)) {
            guide = slotGuide("module_slot", "Die module slot", Formatting.AQUA, MODULE_TAGS, MODULE_POINTS);
        } else if (index >= FIRST_FRAGMENT_SLOT && index < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS) {
            guide = slotGuide("fragment_slot", "Star fragment slot", Formatting.LIGHT_PURPLE, FRAGMENT_TAGS, FRAGMENT_POINTS);
        } else if (index < FACE_SLOTS) {
            guide = slotGuide("face_slot", "Die face slot", Formatting.GOLD, FACE_TAGS, FACE_POINTS);
        }
        if (guide != null) {
            Item remembered = handler.getGhost(index);
            if (remembered != null) {
                guide.add(1, Text.translatableWithFallback(KEY + "missing", "Missing: %s",
                        new ItemStack(remembered).getName()).formatted(Formatting.RED));
            }
            drawWrappedTooltip(context, guide, mouseX, mouseY);
            return;
        }
        ItemStack ghost = getGhostStack(index);
        if (ghost.isEmpty()) return;
        List<Text> lines = new ArrayList<>();
        if (index == CENTER_SLOT) {
            lines.add(Text.translatableWithFallback(KEY + "insert_core", "Gravity core slot"));
            lines.add(getStatusText(Status.NOT_ACTIVATED).formatted(Formatting.GRAY));
        } else if (index == OUTPUT_SLOT) {
            lines.addAll(getOutputTooltip(ghost, false));
        } else if (index == BLANK_SLOT && handler.getGhost(index) == null) {
            lines.add(Text.translatableWithFallback(KEY + "blank_slot", "Blank dice faces"));
            lines.add(getBlankFacesHint().formatted(Formatting.GRAY));
        } else {
            lines.add(Text.translatableWithFallback(KEY + "missing", "Missing: %s", ghost.getName()).formatted(Formatting.RED));
        }
        drawWrappedTooltip(context, lines, mouseX, mouseY);
    }
}
