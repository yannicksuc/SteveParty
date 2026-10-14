package fr.lordfinn.steveparty.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.blocks.ModBlocks;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity;
import fr.lordfinn.steveparty.client.gui.GuiItems;
import fr.lordfinn.steveparty.client.gui.HandCursor;
import fr.lordfinn.steveparty.client.gui.HitArea;
import fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.Status;
import fr.lordfinn.steveparty.components.DiceFacesComponent.DiceFace;
import fr.lordfinn.steveparty.dice.DiceModules;
import fr.lordfinn.steveparty.items.ModItems;
import fr.lordfinn.steveparty.screen_handlers.custom.DiceForgeScreenHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static fr.lordfinn.steveparty.blocks.custom.DiceForgeBlockEntity.*;

/**
 * Dice forge screen: a launch star, the galaxy in its heart. The galaxy holds the 12 die faces (their count is their
 * weight), the 5 star fragments around the core and, in its center, the gravity core. The five gems on the points of the star hold the dice modules put on every die
 * forged; the capsule under the galaxy takes the blank faces in on its left and gives the forged die on its right,
 * and its arrow is the FORGE button (it fills with the progress).
 * <p>
 * The forge shows itself step by step ({@link Step}): the core first, then the fragments round it, then the faces and
 * the capsule, then the module gems; the slots of a step not reached yet are hidden (unless they hold something). An
 * empty slot shows the silhouette of what it takes: the fragment colours still free turning, the faces 1 to 10.
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
    /** The module silhouettes are optional: fainter and tinted violet, never mistaken for a module put in. */
    private static final float MODULE_GHOST_ALPHA = 0.3f, MODULE_GHOST_R = 0.7f, MODULE_GHOST_G = 0.6f;
    /** Before the core is in, the galaxy is dimmed (multiplied by this): it lights up with the core. */
    private static final float SLEEPING_GALAXY = 0.45f;
    /** The module gem (one per point of the star, its slot at +4, +4) and the capsule (drawn at FRAME_X, FRAME_Y). */
    private static final int GEM_U = 0, GEM_V = 400, GEM_SIZE = 24, CAPSULE_U = 32, CAPSULE_V = 400;
    /** The fragment silhouettes turn through the colours still free every FRAGMENT_CYCLE_MS, the faces 1 to 10 faster. */
    private static final long FRAGMENT_CYCLE_MS = 3000, FACE_CYCLE_MS = 1500;
    /** The fragment colours the silhouettes show, in rainbow order (not black: dark on the galaxy, it would not show). */
    private static final List<Item> FRAGMENT_CYCLE = List.of(ModItems.RED_STAR_FRAGMENT, ModItems.ORANGE_STAR_FRAGMENT,
            ModItems.YELLOW_STAR_FRAGMENT, ModItems.LIME_STAR_FRAGMENT, ModItems.GREEN_STAR_FRAGMENT,
            ModItems.CYAN_STAR_FRAGMENT, ModItems.LIGHT_BLUE_STAR_FRAGMENT, ModItems.BLUE_STAR_FRAGMENT,
            ModItems.PURPLE_STAR_FRAGMENT, ModItems.MAGENTA_STAR_FRAGMENT, ModItems.PINK_STAR_FRAGMENT,
            ModItems.WHITE_STAR_FRAGMENT, ModItems.LIGHT_GRAY_STAR_FRAGMENT, ModItems.GRAY_STAR_FRAGMENT,
            ModItems.BROWN_STAR_FRAGMENT);
    /** Clicked while it can't forge, the FORGE button makes the first missing thing pulse for HINT_MS. */
    private static final long HINT_MS = 1600, HINT_PULSE_MS = 400;
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
    /**
     * The FORGE button is the whole capsule (its slots aside: hovering a slot is hovering the slot). Its hovered frame
     * and its pressed inside are sprites of the atlas, at their place on the screen (see the texture script).
     */
    private static final int CAPSULE_CX = DiceForgeScreenHandler.BLANK_X + 23, CAPSULE_CY = DiceForgeScreenHandler.BLANK_Y + 8;
    private static final int BUTTON_X0 = CAPSULE_CX - 31, BUTTON_X1 = CAPSULE_CX + 32,
            BUTTON_Y0 = CAPSULE_CY - 13, BUTTON_Y1 = CAPSULE_CY + 14;
    private static final int FRAME_U = 40, FRAME_V = 336, FRAME_X = CAPSULE_CX - 32, FRAME_Y = CAPSULE_CY - 14,
            FRAME_W = 65, FRAME_H = 29;
    private static final int SUNK_U = 40, SUNK_V = 366, SUNK_X = CAPSULE_CX - 28, SUNK_Y = CAPSULE_CY - 10,
            SUNK_W = 57, SUNK_H = 21;
    /**
     * The arrow: pale gold at rest, white when hovered, lilac while forging (the part still to fill), muted violet
     * when it cannot be pressed.
     */
    private static final int ARROW_IDLE = 0xFFFFF0A8, ARROW_HOVERED = 0xFFFFFFFF, ARROW_RUNNING = 0xFFD9C2FF,
            ARROW_OFF = 0xFF9B7FC8, ARROW_OFF_HOVERED = 0xFFC9B5EE;
    /** While forging it fills from the left through a smooth gradient of these colours, violet to gold; orange when blocked. */
    private static final int[] GAUGE_COLORS = {0xFF8A3FFC, 0xFFD23CF0, 0xFFFF4FA3, 0xFFFF8A3D, 0xFFFFD35A};
    private static final int GAUGE_BLOCKED = 0xFFE0703A;
    /**
     * Everything in and not pressed yet, the FORGE capsule breathes, dark then light, once per PULSE_MS (eased): the
     * screen fades it to its dimmed then its lit version (same outline, rim and slots: only its violet inside changes),
     * its arrow from ARROW_SHADE to ARROW_IDLE.
     */
    private static final long PULSE_MS = 2000;
    private static final int CAPSULE_LIT_V = 432, CAPSULE_DIM_V = 464;
    private static final int ARROW_SHADE = 0xFFC9962E;
    /** Free GUI pixels kept around the forge; with less room (large GUI scales) the whole screen is shrunk to fit. */
    private static final int FIT_MARGIN = 4;

    /** The FORGE button is held down (clicked on it, not released yet). */
    private boolean buttonPressed;
    /** Scale the screen is drawn at: 1, or less when the window is too small for it at this GUI scale. */
    private float fit = 1f;

    /** The steps the forge is filled in: each shows its slots once the ones before are done. */
    private enum Step {CORE, FRAGMENTS, FACES, MODULES}

    /** Which steps are shown this frame (by ordinal), see {@link #updateSteps}. */
    private final boolean[] shownSteps = new boolean[Step.values().length];
    /** The step being filled this frame. */
    private Step step = Step.CORE;
    /** Until when the first missing thing pulses (FORGE clicked while it can't forge). */
    private long hintUntil;

    private final ItemStack gravityCore = new ItemStack(ModBlocks.GRAVITY_CORE);
    /** The die to come, before the ring has a face. */
    private final ItemStack plainDie = new ItemStack(ModItems.DEFAULT_DICE);
    private final ItemStack blankFace = new ItemStack(Registries.ITEM.get(Steveparty.id("blank_dice_face")));
    /**
     * The module slots are optional: each gem shows a different module, still, where the slots to fill show their
     * items turning.
     */
    private final List<ItemStack> moduleGhosts = DiceModules.all().stream().limit(MODULE_SLOTS)
            .map(module -> new ItemStack(module.item())).toList();

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
        handler.setShownSlots(index -> shownSteps[stepOf(index).ordinal()]);
        updateSteps();
    }

    // ------------------------------------------------------------------ steps

    private static Step stepOf(int slot) {
        if (slot == CENTER_SLOT) return Step.CORE;
        if (slot >= FIRST_FRAGMENT_SLOT && slot < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS) return Step.FRAGMENTS;
        if (isModuleSlot(slot)) return Step.MODULES;
        return Step.FACES; // the faces, and the capsule: blank faces and the die
    }

    /** @return true when the 5 fragment slots hold fragments of different colours (black may repeat). */
    private static boolean areFragmentsDone(Inventory inventory) {
        Set<Item> colours = new HashSet<>();
        for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!isStarFragment(stack) || !isInfiniteFragment(stack) && !colours.add(stack.getItem())) return false;
        }
        return true;
    }

    /**
     * The step being filled, and the steps shown: those up to it, and any holding something (filled by a hopper, or
     * still holding its items once the core is taken back).
     */
    private void updateSteps() {
        Inventory inventory = handler.getInventory();
        int faces = countFaces(inventory);
        ItemStack blanks = inventory.getStack(BLANK_SLOT);
        if (!handler.isActivated()) step = Step.CORE;
        else if (!areFragmentsDone(inventory)) step = Step.FRAGMENTS;
        else if (faces < DiceForgeBlockEntity.MIN_FACES || !isBlankFace(blanks) || blanks.getCount() < faces) step = Step.FACES;
        else step = Step.MODULES;
        for (Step each : Step.values()) shownSteps[each.ordinal()] = each.ordinal() <= step.ordinal();
        for (int i = 0; i < DiceForgeBlockEntity.SIZE; i++) {
            if (!inventory.getStack(i).isEmpty()) shownSteps[stepOf(i).ordinal()] = true;
        }
    }

    private boolean isShown(Step each) {
        return shownSteps[each.ordinal()];
    }

    /** @return the forge slot of the first thing missing to forge, or -1. */
    private int getFirstMissingSlot() {
        Inventory inventory = handler.getInventory();
        return switch (step) {
            case CORE -> CENTER_SLOT;
            case FRAGMENTS -> {
                for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
                    if (inventory.getStack(i).isEmpty()) yield i;
                }
                yield -1;
            }
            case FACES -> countFaces(inventory) < DiceForgeBlockEntity.MIN_FACES ? 0 : BLANK_SLOT;
            case MODULES -> -1;
        };
    }

    /**
     * At a large GUI scale the forge (330 px tall) does not fit in the window: the screen is then laid out on a larger
     * virtual screen and drawn shrunk ({@link #fit}), mouse coordinates converted, so the star and the whole inventory
     * stay visible and usable. The screen's size stays the real one afterwards: other mods laying out around it (REI's
     * panel) see the window as it is, and the forge's real bounds ({@link #getFittedBounds}).
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
        this.width = realWidth;
        this.height = realHeight;
    }

    /** The forge as drawn in the window (shrunk at large GUI scales): x, y, width, height in GUI pixels. */
    public int[] getFittedBounds() {
        int left = MathHelper.floor(this.x * fit), top = MathHelper.floor(this.y * fit);
        return new int[]{left, top, MathHelper.ceil((this.x + backgroundWidth) * fit) - left,
                MathHelper.ceil((this.y + backgroundHeight) * fit) - top};
    }

    /** The darkened world behind the screen covers the whole window, not just its shrunk part. */
    @Override
    public void renderInGameBackground(DrawContext context) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.scale(1f / fit, 1f / fit, 1f);
        super.renderInGameBackground(context);
        matrices.pop();
    }

    private double toScreen(double coordinate) {
        return coordinate / fit;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        int x = this.x, y = this.y;
        RenderSystem.enableBlend();
        context.drawTexture(TEXTURE, x, y, 0, 0, this.backgroundWidth, this.backgroundHeight, ATLAS_SIZE, ATLAS_SIZE);
        // Asleep without its core, the galaxy lights up when the core goes in
        float light = handler.isActivated() ? 1f : SLEEPING_GALAXY;
        RenderSystem.setShaderColor(light, light, light, 1f);
        drawGalaxy(context, x + GALAXY_CENTER_X, y + GALAXY_CENTER_Y);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Light slots on the galaxy, where the faces and fragments are shown
        if (isShown(Step.FACES)) {
            for (int i = 0; i < FACE_SLOTS; i++) drawLightSlot(context, x, y, handler.getSlot(i), LIGHT_SQUARE_U);
        }
        // The core's place, before it is in
        if (!handler.isActivated()) drawLightSlot(context, x, y, handler.getSlot(CENTER_SLOT), LIGHT_ROUND_U);
        if (isShown(Step.FRAGMENTS)) {
            for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
                drawLightSlot(context, x, y, handler.getSlot(i), LIGHT_ROUND_U);
            }
        }
        // Faces: the brighter the gold contour, the more likely the face
        int totalWeight = getTotalWeight();
        for (int i = 0; i < FACE_SLOTS; i++) {
            Slot slot = handler.getSlot(i);
            if (!DiceFace.isFace(slot.getStack()) || totalWeight <= 0) continue;
            int alpha = 0x50 + Math.round(0xAF * slot.getStack().getCount() / (float) getMaxWeight());
            drawSlotContour(context, x + slot.x, y + slot.y, (alpha << 24) | 0xFFE08C);
        }

        // Over the galaxy: its ring, then the capsule and the module gems once shown
        RenderSystem.enableBlend(); // the face contours (fills) above leave blending off
        RenderSystem.defaultBlendFunc();
        context.drawTexture(TEXTURE, x, y, OVERLAY_U, 0, this.backgroundWidth, this.backgroundHeight, ATLAS_SIZE, ATLAS_SIZE);
        if (isShown(Step.FACES)) {
            context.drawTexture(TEXTURE, x + FRAME_X, y + FRAME_Y, CAPSULE_U, CAPSULE_V, FRAME_W, FRAME_H, ATLAS_SIZE, ATLAS_SIZE);
            if (isReadyToForge()) {
                // Ready to forge: the capsule breathes, dark then light, until it is pressed
                float pulse = getPulse();
                float fade = pulse < 0.5f ? 1f - 2f * pulse : 2f * pulse - 1f;
                RenderSystem.setShaderColor(1f, 1f, 1f, fade);
                context.drawTexture(TEXTURE, x + FRAME_X, y + FRAME_Y, CAPSULE_U, pulse < 0.5f ? CAPSULE_DIM_V : CAPSULE_LIT_V,
                        FRAME_W, FRAME_H, ATLAS_SIZE, ATLAS_SIZE);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        }
        if (isShown(Step.MODULES)) {
            for (int[] gem : DiceForgeScreenHandler.MODULE_POSITIONS) {
                context.drawTexture(TEXTURE, x + gem[0] - 4, y + gem[1] - 4, GEM_U, GEM_V, GEM_SIZE, GEM_SIZE, ATLAS_SIZE, ATLAS_SIZE);
            }
        }
        RenderSystem.disableBlend();

        if (isShown(Step.FACES)) drawArrowButton(context, x, y, mouseX, mouseY, delta);
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
        if (!isShown(Step.FACES)) return false;
        double px = mouseX - this.x, py = mouseY - this.y;
        if (px < BUTTON_X0 || px >= BUTTON_X1 || py < BUTTON_Y0 || py >= BUTTON_Y1) return false;
        // Its two slots are slots
        return !isOverSlot(px, py, DiceForgeScreenHandler.BLANK_X, DiceForgeScreenHandler.BLANK_Y)
                && !isOverSlot(px, py, DiceForgeScreenHandler.OUTPUT_X, DiceForgeScreenHandler.OUTPUT_Y);
    }

    private static boolean isOverSlot(double px, double py, int slotX, int slotY) {
        return HitArea.contains(px, py, slotX - 1, slotY - 1, 18, 18);
    }

    private float getSmoothProgress(float delta) {
        if (!handler.isRunning()) return 0f;
        float progress = handler.getProgress();
        if (!handler.isBlocked() && progress < 1f) progress += delta / DiceForgeBlockEntity.CRAFT_TIME;
        return MathHelper.clamp(progress, 0f, 1f);
    }

    /**
     * The FORGE button, the capsule with its arrow from the blank faces to the die: framed in white when hovered (the
     * whole capsule), the arrow muted when it cannot be pressed; held down, and as long as it forges, the capsule
     * stays sunken; while forging the arrow fills from the left as the die is built (orange when the forge is blocked).
     */
    private void drawArrowButton(DrawContext context, int x, int y, int mouseX, int mouseY, float delta) {
        boolean enabled = isButtonEnabled();
        boolean hovered = isOverButton(mouseX, mouseY);
        boolean pressed = enabled && (hovered && buttonPressed || handler.isRunning());
        RenderSystem.enableBlend();
        // Sunken: darker inside, a shadow along its top and left edges, the arrow one pixel lower
        if (pressed) context.drawTexture(TEXTURE, x + SUNK_X, y + SUNK_Y, SUNK_U, SUNK_V, SUNK_W, SUNK_H, ATLAS_SIZE, ATLAS_SIZE);
        if (hovered && enabled) context.drawTexture(TEXTURE, x + FRAME_X, y + FRAME_Y, FRAME_U, FRAME_V, FRAME_W, FRAME_H, ATLAS_SIZE, ATLAS_SIZE);
        RenderSystem.disableBlend();

        int base = hovered ? (enabled ? ARROW_HOVERED : ARROW_OFF_HOVERED) : !enabled ? ARROW_OFF
                : handler.isRunning() ? ARROW_RUNNING : ARROW_IDLE;
        // Ready to forge, not pressed yet: the arrow pulses with its capsule
        if (!hovered && isReadyToForge()) base = ColorHelper.Argb.lerp(getPulse(), ARROW_SHADE, ARROW_IDLE);
        float length = ARROW_X1 + 2 - ARROW_X0;
        float progress = getSmoothProgress(delta);
        int shift = pressed ? 1 : 0;
        for (int[] pixel : ARROW_PIXELS) {
            float t = (pixel[0] - ARROW_X0) / length;
            int color = t >= progress ? base : handler.isBlocked() ? GAUGE_BLOCKED : gaugeColor(t);
            if (pressed) color = ColorHelper.Argb.lerp(0.25f, color, 0xFF000000);
            int px = x + pixel[0] + shift, py = y + pixel[1] + shift;
            context.fill(px, py, px + 1, py + 1, color);
        }
    }

    /** Everything is in (core, fragments, faces, blank faces), the output has room, and it is not forging yet. */
    private boolean isReadyToForge() {
        return isButtonEnabled() && !handler.isRunning() && handler.getStatus() == Status.OK;
    }

    /** @return the FORGE pulse: 0 (dark) to 1 (light) and back once per PULSE_MS, eased. */
    private static float getPulse() {
        return 0.5f - 0.5f * MathHelper.cos((Util.getMeasuringTimeMs() % PULSE_MS) / (float) PULSE_MS * MathHelper.TAU);
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
            // It can't forge yet: what is missing first pulses
            if (!isButtonEnabled()) hintUntil = Util.getMeasuringTimeMs() + HINT_MS;
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
            boolean module = isModuleSlot(index);
            if (!ghost.isEmpty()) GuiItems.tinted(context, ghost, slot.x, slot.y, module ? MODULE_GHOST_R : 1f,
                    module ? MODULE_GHOST_G : 1f, 1f, getGhostAlpha(index));
        }
        super.drawSlot(context, slot);
    }

    /** The silhouettes' opacity; the first missing thing pulses a moment after a click on FORGE that can't forge. */
    private float getGhostAlpha(int index) {
        long now = Util.getMeasuringTimeMs();
        if (now >= hintUntil || index != getFirstMissingSlot()) return isModuleSlot(index) ? MODULE_GHOST_ALPHA : GHOST_ALPHA;
        float wave = 0.5f - 0.5f * MathHelper.cos((now % HINT_PULSE_MS) / (float) HINT_PULSE_MS * MathHelper.TAU);
        return MathHelper.lerp(wave, GHOST_ALPHA, 1f);
    }

    /**
     * @return what to show at low opacity in an empty forge slot: the item it remembers (used up by the last die), or
     * what it takes (the core, a fragment colour still free, a face while the ring is empty, blank faces, a module),
     * or the die it makes.
     */
    private ItemStack getGhostStack(int index) {
        if (index == CENTER_SLOT) return handler.isActivated() ? ItemStack.EMPTY : gravityCore;
        if (index == OUTPUT_SLOT) {
            ItemStack die = DiceForgeBlockEntity.createDie(handler.getInventory());
            return die.isEmpty() ? plainDie : die;
        }
        Item ghost = handler.getGhost(index);
        if (ghost != null) return new ItemStack(ghost);
        if (index == BLANK_SLOT) return blankFace;
        if (index >= FIRST_FRAGMENT_SLOT && index < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS) return getFragmentGhost(index);
        if (index < FACE_SLOTS) {
            // Only while the ring is empty: once a face is in, the other face slots are free to use or not
            if (countFaces(handler.getInventory()) > 0) return ItemStack.EMPTY;
            long turn = Util.getMeasuringTimeMs() / FACE_CYCLE_MS;
            return new ItemStack(Registries.ITEM.get(Steveparty.id("dice_face_" + (1 + turn % 10))));
        }
        if (isModuleSlot(index)) {
            int gem = index - FIRST_MODULE_SLOT;
            return gem < moduleGhosts.size() ? moduleGhosts.get(gem) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }

    /**
     * An empty fragment slot shows a colour no other slot holds, the empty slots each a different one, all turning
     * together through the colours still free.
     */
    private ItemStack getFragmentGhost(int index) {
        Inventory inventory = handler.getInventory();
        List<Item> free = new ArrayList<>(FRAGMENT_CYCLE);
        int empties = 0, rank = 0;
        for (int i = FIRST_FRAGMENT_SLOT; i < FIRST_FRAGMENT_SLOT + FRAGMENT_SLOTS; i++) {
            ItemStack stack = inventory.getStack(i);
            if (!stack.isEmpty() && !isInfiniteFragment(stack)) free.remove(stack.getItem());
            if (stack.isEmpty()) {
                if (i == index) rank = empties;
                empties++;
            }
        }
        long turn = Util.getMeasuringTimeMs() / FRAGMENT_CYCLE_MS;
        return new ItemStack(free.get((int) ((rank + turn) % free.size())));
    }

    @Override
    public void removed() {
        HandCursor.reset();
        super.removed();
    }

    @Override
    public void render(DrawContext context, int realMouseX, int realMouseY, float delta) {
        updateSteps();
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
