package fr.lordfinn.steveparty.client.screens;

import fr.lordfinn.steveparty.Steveparty;
import fr.lordfinn.steveparty.client.tokenspell.MobTextureColors;
import fr.lordfinn.steveparty.client.tokenspell.TokenSpellHand;
import fr.lordfinn.steveparty.items.custom.TokenizerWandItem;
import fr.lordfinn.steveparty.particles.KamekShapeEffect;
import fr.lordfinn.steveparty.payloads.custom.TokenSpellPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.MAX_TOKEN_SIZE;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.MIN_TOKEN_SIZE;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.NO_COLOR;
import static fr.lordfinn.steveparty.items.custom.TokenizerWandItem.TOKEN_SIZE_STEP;

/**
 * Token spell of the Tokenizer Wand, in the style of Kamek's magic: no panel, the player draws a magic circle
 * directly on the screen, around the targeted mob. Non-pausing: the world keeps being rendered.
 * <ol>
 *   <li>Charging (short): Kamek's shapes gather into the wand and around the mob, a faint guide circle (dotted, pulsing,
 *   a sparkle running round it) forms: the circle to trace over, not the result.</li>
 *   <li>Drawing: the player traces a circle freehand, anywhere on the screen, holding the left or the right mouse
 *   button (particles stream from the wand and along the stroke while it is held). On release, a circle is fitted
 *   to the stroke (centroid, mean radius) and the stroke morphs into the clean circle, centred back on the mob. The
 *   bigger the circle, the bigger the token: the radius is proportional to the size (a full-size circle is
 *   {@link TokenizerWandItem#MAX_TOKEN_SIZE} blocks). No size is shown, no fine-tuning: the drawing decides.
 *   Something that is not a loop is ignored with a brief message, and the player draws again. The only text on
 *   screen is the incantation, next to a small ring icon (the shape to draw). The same circle, in shapes, is
 *   mirrored on the ground around the mob, and sparkles mark the token's future height. The first person wand
 *   follows the cursor ({@link TokenSpellHand}).</li>
 *   <li>Validation (right after the morph): the circle locks and flashes (the rounder the drawing, the more shapes
 *   burst out), a stream of shapes flies from the wand to the mob, then the spell is sent.</li>
 *   <li>Transformation: played for everyone around by the squish animation (jelly growth pulses and swirling
 *   shapes, see SquishAnimations).</li>
 * </ol>
 * Esc cancels without doing anything (before the circle is drawn).
 */
public class TokenSpellScreen extends Screen {
    private static final Identifier[] SPRITES = {Steveparty.id("textures/particle/kamek_circle.png"),
            Steveparty.id("textures/particle/kamek_triangle.png"), Steveparty.id("textures/particle/kamek_square.png"),
            Steveparty.id("textures/particle/kamek_sparkle.png")};
    private static final int SPARKLE = 3;
    /** Short: it never blocks drawing (a stroke can already be traced while it charges). */
    private static final int CHARGE_TICKS = 8;
    private static final int VALIDATE_TICKS = 12;
    /** Duration of the morph of a drawn stroke into the clean circle. */
    private static final int MORPH_TICKS = 9;
    private static final int MAX_STROKE_POINTS = 800;
    private static final float MIN_DRAWN_RADIUS = 8;
    private static final int HEIGHT_MARK_COLOR = 0xC150EB;
    /**
     * Beyond this distance (blocks) the spell is cast at once, before the mob gets out of reach (the server accepts
     * up to {@link TokenizerWandItem#MAX_SPELL_DISTANCE}).
     */
    private static final double AUTO_CAST_DISTANCE = 10;
    /** Radius of the ground circle around the mob, per block of token size. */
    private static final double WORLD_RADIUS_PER_BLOCK = 0.6;
    /** How fast the camera catches up with a moving mob (1/s: about 95 % of the way in 0.75 s). */
    private static final float CAMERA_FOLLOW_SPEED = 4F;

    private enum Phase { CHARGING, DRAWING, VALIDATING }

    private final MobEntity mob;
    private final int color;
    private float size;
    private int ticks;
    private Phase phase = Phase.CHARGING;
    private int phaseTicks;
    /** Tracing a stroke (left button held). */
    private boolean dragging;
    /** The mouse button tracing the stroke (left or right). */
    private int drawButton = GLFW.GLFW_MOUSE_BUTTON_LEFT;
    private float shownRadius = -1;
    /** The freehand stroke being traced (GUI coordinates) and its length. */
    private final List<float[]> stroke = new ArrayList<>();
    private float strokeLength;
    /** Morph of the finished stroke into the clean circle: its points, their angle around the fitted centre. */
    private final List<float[]> morphFrom = new ArrayList<>();
    private final List<Float> morphAngles = new ArrayList<>();
    private int morphTicks = -1;
    /** How round the last drawing was, 0..1: the rounder, the more sparkles when the spell is cast. */
    private float roundness = 0.5F;
    /** Until a circle is drawn, the default circle is only a faint guide; its opacity (0..1). */
    private boolean guide = true;
    private long lastCameraNanos;
    private float guideFade;
    /** Until this tick, the hint says the last stroke was not a loop. */
    private int failedUntil = -1;
    /** Centre of the circle: where the mob is drawn on the screen (followed smoothly). */
    private float centerX, centerY;
    private boolean centered;
    /** Kamek shapes and sparkles drawn on the screen (GUI coordinates). */
    private final List<GuiShape> shapes = new ArrayList<>();

    private static final class GuiShape {
        float x, y, vx, vy;
        int age;
        final int life, sprite, color;

        GuiShape(float x, float y, float vx, float vy, int life, int sprite, int color) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.sprite = sprite;
            this.color = color;
        }
    }

    public TokenSpellScreen(MobEntity mob, float initialSize, boolean resize, int currentColor) {
        super(Text.translatableWithFallback(resize ? "screen.steveparty.token_spell.resize_title" : "screen.steveparty.token_spell.title",
                resize ? "Resizing spell" : "Token spell"));
        this.mob = mob;
        this.size = snap(initialSize);
        boolean colorKept = resize && currentColor != NO_COLOR;
        // Computed once: when the texture has tied colours, the pick is random and must not change while drawing
        this.color = colorKept ? currentColor : MobTextureColors.pickColor(mob);
    }

    @Override
    protected void init() {
        centered = false;
        centerX = width / 2F;
        centerY = height / 2F;
        // The button that used the wand on the mob may still be held: that same press already draws (released, it
        // casts), no need to click again. Only when the screen first opens (init also runs on a resize).
        if (phase == Phase.CHARGING && phaseTicks == 0 && !dragging && client != null) {
            long window = client.getWindow().getHandle();
            for (int button : new int[]{GLFW.GLFW_MOUSE_BUTTON_RIGHT, GLFW.GLFW_MOUSE_BUTTON_LEFT}) {
                if (GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS) {
                    startStroke(button, (float) (client.mouse.getX() * width / client.getWindow().getWidth()),
                            (float) (client.mouse.getY() * height / client.getWindow().getHeight()));
                    break;
                }
            }
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------------ phases

    @Override
    public void tick() {
        super.tick();
        ticks++;
        phaseTicks++;
        // Cancelled only when the spell can't happen at all: Esc, no more wand, the mob gone
        if (client == null || client.player == null || client.world == null || mob.isRemoved() || !mob.isAlive()
                || mob.getWorld() != client.world
                || TokenizerWandItem.heldWand(client.player).isEmpty()) {
            close();
            return;
        }
        // The mob runs off (too far, or out of sight): the spell is cast at once, with the circle as it is
        if (phase != Phase.VALIDATING && !morphing()
                && (client.player.squaredDistanceTo(mob) > AUTO_CAST_DISTANCE * AUTO_CAST_DISTANCE || !client.player.canSee(mob))) {
            castNow();
        }
        switch (phase) {
            case CHARGING -> {
                chargeParticles();
                // Charging never blocks a stroke: it can already be traced
                if (dragging) {
                    wandTrail();
                    strokeShapes();
                }
                if (phaseTicks >= CHARGE_TICKS) setPhase(Phase.DRAWING);
            }
            case DRAWING -> {
                groundCircle();
                heightMarks();
                if (dragging) {
                    wandTrail();
                    strokeShapes();
                } else if (!guide && !morphing() && ticks % 4 == 0) {
                    emitAlongCircle(1, SPARKLE);
                }
                // Released: the stroke became the circle, the spell is cast at once
                if (morphTicks >= 0 && ++morphTicks >= MORPH_TICKS) setPhase(Phase.VALIDATING);
            }
            case VALIDATING -> {
                groundCircle();
                if (phaseTicks <= 8) shapeStream();
                if (phaseTicks >= VALIDATE_TICKS) {
                    confirm();
                    return;
                }
            }
        }
        tickShapes();
    }

    private void setPhase(Phase next) {
        phase = next;
        phaseTicks = 0;
        if (next == Phase.VALIDATING) {
            // (Charging -> drawing keeps a stroke already being traced)
            dragging = false;
            guide = false;
            stroke.clear();
            morphTicks = -1;
            // The circle locks: a burst of shapes out of it, all the bigger as the drawing was round
            burstFromCircle(10 + Math.round(roundness * 22));
        }
    }

    /**
     * While the button is held, a steady stream (a few per tick, whether the cursor moves or not): shapes and sparkles
     * pop out of the tip of the stroke, and one out of a random point along it.
     */
    private void strokeShapes() {
        if (stroke.isEmpty()) return;
        Random random = client.world.random;
        float[] tip = stroke.getLast();
        for (int i = 0; i < 2; i++) {
            float angle = random.nextFloat() * MathHelper.TAU;
            addShape(tip[0], tip[1], MathHelper.cos(angle) * 0.9F, MathHelper.sin(angle) * 0.9F, 8 + random.nextInt(6),
                    i == 0 ? SPARKLE : random.nextInt(SPARKLE));
        }
        float[] along = stroke.get(random.nextInt(stroke.size()));
        addShape(along[0], along[1], (random.nextFloat() - 0.5F) * 0.8F, -0.3F - random.nextFloat() * 0.4F, 10 + random.nextInt(6),
                random.nextBoolean() ? SPARKLE : random.nextInt(SPARKLE));
    }

    private void confirm() {
        if (ClientPlayNetworking.canSend(TokenSpellPayload.ID)) {
            ClientPlayNetworking.send(new TokenSpellPayload(mob.getId(), size, color));
        }
        close();
    }

    @Override
    public void removed() {
        super.removed();
        TokenSpellHand.clear();
    }

    // ------------------------------------------------------------------ world particles

    private Vec3d mobCenter() {
        return new Vec3d(mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ());
    }

    /** Charging: shapes gather into the wand's tip, and around the mob. */
    private void chargeParticles() {
        Random random = client.world.random;
        Vec3d tip = TokenSpellHand.tipInWorld(0.7);
        for (int i = 0; i < 2; i++) {
            Vec3d offset = new Vec3d(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).multiply(0.35);
            int life = 7;
            client.world.addParticle(KamekShapeEffect.shape(0.12F, 1.0F, life), tip.x + offset.x, tip.y + offset.y, tip.z + offset.z,
                    -offset.x / life, -offset.y / life, -offset.z / life);
        }
        Vec3d center = mobCenter();
        double radius = Math.max(1.0, mob.getWidth() + 0.6);
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * MathHelper.TAU;
            double dy = (random.nextDouble() - 0.5) * mob.getHeight();
            Vec3d from = center.add(Math.cos(angle) * radius, dy, Math.sin(angle) * radius);
            int life = 10;
            Vec3d velocity = center.subtract(from).multiply(1.0 / life)
                    .add(-Math.sin(angle) * 0.05, 0, Math.cos(angle) * 0.05); // spirals in
            client.world.addParticle(i == 0 ? KamekShapeEffect.sparkle(0.9F, 1.0F, life, KamekShapeEffect.RANDOM_COLOR)
                    : KamekShapeEffect.shape(0.8F, 1.0F, life), from.x, from.y, from.z, velocity.x, velocity.y, velocity.z);
        }
    }

    /** The drawn circle, mirrored in Kamek shapes on the ground around the mob. */
    private void groundCircle() {
        double radius = size * WORLD_RADIUS_PER_BLOCK;
        int points = 12;
        for (int i = 0; i < points; i++) {
            double angle = ticks * 0.09 + i * MathHelper.TAU / points;
            client.world.addParticle(KamekShapeEffect.shape(0.8F, 0F, 3), mob.getX() + Math.cos(angle) * radius,
                    mob.getY() + 0.15, mob.getZ() + Math.sin(angle) * radius, 0, 0, 0);
        }
    }

    /** Sparkles of the token colour at the future height of the token. */
    private void heightMarks() {
        if (ticks % 2 != 0) return;
        EntityDimensions body = mob.getDimensions(EntityPose.STANDING);
        float current = Math.max(body.width(), body.height());
        if (current <= 0) return;
        float ratio = size / current;
        // A token stands on a base: its hitbox is higher than its body
        double baseOffset = Math.max(0, mob.getHeight() - body.height());
        double height = baseOffset + body.height() * ratio;
        double radius = body.width() * ratio / 2 + 0.25;
        // Lightened: dark token colours (a cow's brown) would read as black specks
        KamekShapeEffect sparkle = KamekShapeEffect.sparkle(1.0F, 0F, 5,
                lerpColor(color == NO_COLOR ? HEIGHT_MARK_COLOR : color, 0xFFFFFF, 0.45F));
        for (int i = 0; i < 4; i++) {
            double angle = -ticks * 0.15 + i * Math.PI / 2;
            client.world.addParticle(sparkle, mob.getX() + Math.cos(angle) * radius, mob.getY() + height + 0.05,
                    mob.getZ() + Math.sin(angle) * radius, 0, 0, 0);
        }
    }

    /** Drawing: shapes trail from the wand's tip. */
    private void wandTrail() {
        Random random = client.world.random;
        Vec3d tip = TokenSpellHand.tipInWorld(0.7);
        // A steady stream while the button is held: two shapes and a sparkle per tick
        for (int i = 0; i < 3; i++) {
            KamekShapeEffect effect = i == 2 ? KamekShapeEffect.sparkle(0.14F, 0.9F, 0, KamekShapeEffect.RANDOM_COLOR)
                    : KamekShapeEffect.shape(0.12F, 0.9F, 0);
            client.world.addParticle(effect, tip.x, tip.y, tip.z, (random.nextDouble() - 0.5) * 0.012,
                    (random.nextDouble() - 0.5) * 0.012, (random.nextDouble() - 0.5) * 0.012);
        }
    }

    /** Validation: Kamek's stream of shapes, from the wand's tip to the mob. Each dies as it reaches the mob. */
    private void shapeStream() {
        Random random = client.world.random;
        Vec3d tip = TokenSpellHand.tipInWorld(0.7);
        Vec3d path = mobCenter().subtract(tip);
        for (int i = 0; i < 3; i++) {
            int life = 6 + random.nextInt(5);
            Vec3d velocity = path.multiply(1.0 / life).add((random.nextDouble() - 0.5) * 0.04,
                    (random.nextDouble() - 0.5) * 0.04, (random.nextDouble() - 0.5) * 0.04);
            client.world.addParticle(i == 0 ? KamekShapeEffect.sparkle(0.5F, 1.0F, life, 0xFFFFFF)
                    : KamekShapeEffect.shape(0.45F, 1.0F, life), tip.x, tip.y, tip.z, velocity.x, velocity.y, velocity.z);
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (phase == Phase.VALIDATING || morphing() || dragging) return true;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            startStroke(button, (float) mouseX, (float) mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Casts right away: a stroke being traced is fitted (if it is a circle, it gives the size and morphs, then the
     * spell goes), else the current circle (drawn, or the initial size) is cast straight away.
     */
    private void castNow() {
        if (dragging) {
            finishStroke();
            if (morphing()) return;
        }
        setPhase(Phase.VALIDATING);
    }

    /** A new stroke (either button, also during charging): released, it becomes the circle and the spell is cast. */
    private void startStroke(int button, float x, float y) {
        dragging = true;
        drawButton = button;
        stroke.clear();
        morphTicks = -1;
        strokeLength = 0;
        traceTo(x, y);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == drawButton) {
            traceTo((float) mouseX, (float) mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging && button == drawButton) {
            traceTo((float) mouseX, (float) mouseY);
            finishStroke();
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ------------------------------------------------------------------ freehand drawing

    /** Adds the cursor to the stroke. */
    private void traceTo(float x, float y) {
        // A minimized window reports the cursor at infinity: those points are dropped, the others kept on screen
        if (!dragging || !Float.isFinite(x) || !Float.isFinite(y) || width <= 0 || height <= 0) return;
        x = MathHelper.clamp(x, 0, width);
        y = MathHelper.clamp(y, 0, height);
        if (!stroke.isEmpty()) {
            float[] last = stroke.getLast();
            float step = (float) Math.hypot(x - last[0], y - last[1]);
            if (step < 2) return;
            strokeLength += step;
        }
        if (stroke.size() >= MAX_STROKE_POINTS) return;
        stroke.add(new float[]{x, y});
    }

    /**
     * Fits a circle to the stroke (centroid, mean distance to it): its radius gives the size, with the same scale as
     * before (a full-size circle is {@link TokenizerWandItem#MAX_TOKEN_SIZE} blocks). A stroke that is not a loop, or
     * too small, is ignored with a hint (nothing is cast, the player draws again). Otherwise the stroke morphs into
     * the clean circle around the mob, then the spell is cast.
     */
    private void finishStroke() {
        dragging = false;
        if (stroke.size() < 12) {
            failStroke();
            return;
        }
        float sumX = 0, sumY = 0;
        for (float[] point : stroke) {
            sumX += point[0];
            sumY += point[1];
        }
        float fitX = sumX / stroke.size(), fitY = sumY / stroke.size();
        float sum = 0, sumSquares = 0;
        boolean[] sectors = new boolean[12];
        for (float[] point : stroke) {
            float distance = (float) Math.hypot(point[0] - fitX, point[1] - fitY);
            sum += distance;
            sumSquares += distance * distance;
            double angle = Math.atan2(point[1] - fitY, point[0] - fitX) + Math.PI;
            sectors[Math.min(11, (int) (angle / MathHelper.TAU * 12))] = true;
        }
        int covered = 0;
        for (boolean sector : sectors) if (sector) covered++;
        float meanRadius = sum / stroke.size();
        // A loop goes (nearly) all the way around its centre
        if (covered < 10 || meanRadius < MIN_DRAWN_RADIUS) {
            failStroke();
            return;
        }
        float deviation = (float) Math.sqrt(Math.max(0, sumSquares / stroke.size() - meanRadius * meanRadius));
        roundness = MathHelper.clamp(1 - deviation / meanRadius * 2.5F, 0, 1);
        size = snap(meanRadius / maxRadius() * MAX_TOKEN_SIZE);
        // Morph: every point of the stroke slides to its place on the clean circle, around the mob
        morphFrom.clear();
        morphAngles.clear();
        for (float[] point : stroke) {
            morphFrom.add(point.clone());
            morphAngles.add((float) Math.atan2(point[1] - fitY, point[0] - fitX));
        }
        morphTicks = 0;
        guide = false;
        if (phase == Phase.CHARGING) {
            // Drawn (and released) while it was still charging: no need to wait
            phase = Phase.DRAWING;
            phaseTicks = 0;
        }
        stroke.clear();
        shownRadius = radiusFor(size);
        if (client != null && client.world != null) emitAlongCircle(6, SPARKLE);
    }

    private void failStroke() {
        stroke.clear();
        failedUntil = ticks + 40;
    }

    private boolean morphing() {
        return morphTicks >= 0 && morphTicks < MORPH_TICKS;
    }

    /**
     * The camera keeps the mob in sight if it moves: the player's yaw / pitch ease towards the mob's centre every frame
     * (exponential smoothing on real time, so the same at any frame rate; no snapping). The mouse stays free: it only
     * moves the cursor on the screen.
     */
    private void followMobWithCamera() {
        long now = System.nanoTime();
        float dt = lastCameraNanos == 0 ? 0 : Math.min(0.1F, (now - lastCameraNanos) / 1.0E9F);
        lastCameraNanos = now;
        PlayerEntity player = client != null ? client.player : null;
        if (player == null || dt <= 0) return;
        Vec3d to = new Vec3d(mob.getX(), mob.getY() + mob.getHeight() / 2, mob.getZ()).subtract(player.getEyePos());
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        if (horizontal < 1.0E-3 && Math.abs(to.y) < 1.0E-3) return;
        float targetYaw = (float) (MathHelper.atan2(to.z, to.x) * MathHelper.DEGREES_PER_RADIAN) - 90;
        float targetPitch = (float) -(MathHelper.atan2(to.y, horizontal) * MathHelper.DEGREES_PER_RADIAN);
        float k = 1 - (float) Math.exp(-dt * CAMERA_FOLLOW_SPEED);
        float yaw = player.getYaw() + MathHelper.wrapDegrees(targetYaw - player.getYaw()) * k;
        float pitch = MathHelper.clamp(player.getPitch() + (targetPitch - player.getPitch()) * k, -90, 90);
        // Also the previous values: the render interpolation must not pull the view back
        player.setYaw(yaw);
        player.setPitch(pitch);
        player.prevYaw = yaw;
        player.prevPitch = pitch;
        player.setHeadYaw(yaw);
    }

    /** The circle is centred on the mob, wherever it is drawn on the screen. */
    private void followMob(float delta) {
        Vec3d center = mob.getLerpedPos(delta).add(0, mob.getHeight() / 2, 0);
        float[] screen = TokenSpellHand.worldToScreen(center);
        float x = screen == null ? width / 2F : (screen[0] + 1) * width / 2F;
        float y = screen == null ? height / 2F : (screen[1] + 1) * height / 2F;
        x = MathHelper.clamp(x, 0, width);
        y = MathHelper.clamp(y, 0, height);
        if (!centered) {
            centerX = x;
            centerY = y;
            centered = true;
        } else {
            centerX = MathHelper.lerp(0.3F, centerX, x);
            centerY = MathHelper.lerp(0.3F, centerY, y);
        }
    }

    // ------------------------------------------------------------------ size

    private static float snap(float size) {
        float snapped = Math.round(size / TOKEN_SIZE_STEP) * TOKEN_SIZE_STEP;
        return MathHelper.clamp(snapped, MIN_TOKEN_SIZE, MAX_TOKEN_SIZE);
    }

    /** Radius (GUI pixels) of a full-size circle: fits the screen, the token texts above it. */
    private float maxRadius() {
        return MathHelper.clamp(height * 0.32F, 50, 140);
    }

    private float radiusFor(float tokenSize) {
        return tokenSize / MAX_TOKEN_SIZE * maxRadius();
    }

    // ------------------------------------------------------------------ screen shapes

    private float[] pointOnCircle(double towardsX, double towardsY, float radius) {
        double dx = towardsX - centerX, dy = towardsY - centerY;
        double length = Math.max(1.0E-3, Math.hypot(dx, dy));
        return new float[]{(float) (centerX + dx / length * radius), (float) (centerY + dy / length * radius),
                (float) (dx / length), (float) (dy / length)};
    }

    /** A shape popping out of a point of the circle, drifting outwards. */
    private void addShape(float[] point, float speed, int sprite) {
        Random random = client.world.random;
        addShape(point[0], point[1], point[2] * speed + (random.nextFloat() - 0.5F), point[3] * speed + (random.nextFloat() - 0.5F),
                10 + random.nextInt(8), sprite);
    }

    private void addShape(float x, float y, float vx, float vy, int life, int sprite) {
        if (shapes.size() >= 96) shapes.removeFirst();
        Random random = client.world.random;
        int shapeColor = sprite == SPARKLE && random.nextBoolean() ? 0xFFFFFF
                : KamekShapeEffect.COLORS[random.nextInt(KamekShapeEffect.COLORS.length)];
        shapes.add(new GuiShape(x, y, vx, vy, life, sprite, shapeColor));
    }

    /** {@code count} shapes (sprite -1: random shape) popping out of random points of the circle. */
    private void emitAlongCircle(int count, int sprite) {
        Random random = client.world.random;
        float radius = radiusFor(size);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * MathHelper.TAU;
            addShape(pointOnCircle(centerX + Math.cos(angle), centerY + Math.sin(angle), radius), 0.8F,
                    sprite < 0 ? random.nextInt(SPARKLE) : sprite);
        }
    }

    private void burstFromCircle(int count) {
        if (client == null || client.world == null) return;
        Random random = client.world.random;
        float radius = radiusFor(size);
        for (int i = 0; i < count; i++) {
            double angle = i * MathHelper.TAU / count + random.nextDouble() * 0.2;
            addShape(pointOnCircle(centerX + Math.cos(angle), centerY + Math.sin(angle), radius), 3.0F,
                    i % 3 == 0 ? SPARKLE : random.nextInt(SPARKLE));
        }
    }

    /** Charging: shapes spiral in from outside and settle on the forming circle. */
    private void chargeScreenShapes() {
        Random random = client.world.random;
        float radius = radiusFor(size);
        for (int i = 0; i < 3; i++) {
            double angle = random.nextDouble() * MathHelper.TAU;
            double from = radius + 50 + random.nextDouble() * 40;
            float x = (float) (centerX + Math.cos(angle) * from), y = (float) (centerY + Math.sin(angle) * from);
            int life = 10 + random.nextInt(4);
            float targetX = (float) (centerX + Math.cos(angle + 0.6) * radius);
            float targetY = (float) (centerY + Math.sin(angle + 0.6) * radius);
            addShape(x, y, (targetX - x) / life, (targetY - y) / life, life, i == 0 ? SPARKLE : random.nextInt(SPARKLE));
        }
    }

    private void tickShapes() {
        if (phase == Phase.CHARGING) chargeScreenShapes();
        shapes.removeIf(shape -> ++shape.age >= shape.life);
        for (GuiShape shape : shapes) {
            shape.x += shape.vx;
            shape.y += shape.vy;
            // Charging shapes keep their speed (they must reach the circle), the others slow down
            if (phase != Phase.CHARGING) {
                shape.vx *= 0.88F;
                shape.vy *= 0.88F;
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    /** No blur nor darkening, no panel: only the magic is drawn over the world. */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        followMobWithCamera();
        followMob(delta);
        // Follows the cursor every frame while the button is held (vanilla only sends drag events to a focused window)
        if (dragging) {
            traceTo(mouseX, mouseY);
            // A release the screen never got (e.g. the press started before it opened) still ends the stroke
            if (client != null && GLFW.glfwGetMouseButton(client.getWindow().getHandle(), drawButton) == GLFW.GLFW_RELEASE) {
                finishStroke();
            }
        }
        float press = phase == Phase.VALIDATING ? 1 : dragging ? 0.7F : 0;
        TokenSpellHand.aim(width == 0 ? 0 : mouseX * 2F / width - 1, height == 0 ? 0 : mouseY * 2F / height - 1, press);
        super.render(context, mouseX, mouseY, delta);

        float time = ticks + delta;
        float target = radiusFor(size);
        float radius;
        float flash = 0;
        // The guide is always the same reference circle on screen (whatever the mob, its size or its distance):
        // drawing right over it gives a DEFAULT_TOKEN_SIZE token, bigger or smaller in proportion
        float guideRadius = radiusFor(TokenizerWandItem.DEFAULT_TOKEN_SIZE);
        switch (phase) {
            case CHARGING -> {
                float t = MathHelper.clamp((phaseTicks + delta) / CHARGE_TICKS, 0, 1);
                // Forms with a little overshoot
                float c1 = 1.7F, c3 = c1 + 1;
                float forming = 1 + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
                radius = target * forming;
                guideRadius *= forming;
                shownRadius = radius;
            }
            case VALIDATING -> {
                float t = MathHelper.clamp((phaseTicks + delta) / VALIDATE_TICKS, 0, 1);
                // Locks: two bright flashes, then tightens around the mob as the spell leaves
                flash = Math.max(0, MathHelper.sin(t * MathHelper.PI * 4)) * (1 - t * 0.5F);
                radius = target * (1 - 0.25F * t * t);
            }
            default -> {
                shownRadius = shownRadius < 0 ? target : MathHelper.lerp(0.35F, shownRadius, target);
                radius = shownRadius;
            }
        }
        // The default circle is only a guide to trace over: a faint ghost, fading out as soon as the player draws
        guideFade = guide && !dragging ? Math.min(1, guideFade + 0.08F) : Math.max(0, guideFade - 0.12F);
        if (guide && guideFade > 0.01F && phase != Phase.VALIDATING) {
            drawGuide(context, centerX, centerY, Math.max(0, guideRadius), time, guideFade);
        }
        if (dragging) {
            // Freehand: only the stroke being traced, its tip twinkling under the cursor
            drawStroke(context, stroke, time);
            if (!stroke.isEmpty()) {
                float[] tip = stroke.getLast();
                drawSprite(context, SPARKLE, tip[0], tip[1], 0xFFFFFF, 255);
            }
        } else if (phase == Phase.DRAWING && morphing()) {
            drawStroke(context, morphPoints(delta, radius), time);
        } else if (!guide) {
            drawMagicCircle(context, centerX, centerY, Math.max(0, radius), time, flash, phase == Phase.VALIDATING ? 2 : 1);
        }
        drawShapes(context, delta);
        drawTexts(context, time);
    }

    /**
     * The guide: a faint, gently pulsing dotted ghost of the circle to trace over, and a sparkle travelling round it
     * clockwise to suggest the drawing movement.
     */
    private void drawGuide(DrawContext context, float cx, float cy, float radius, float time, float fade) {
        if (radius < 1) return;
        float pulse = 0.5F + 0.5F * MathHelper.sin(time * 0.15F);
        // About 35-50 % opacity: clearly a ghost, yet readable on the sky as on the grass
        int alpha = (int) ((90 + 40 * pulse) * fade);
        int points = Math.max(24, (int) (radius * MathHelper.TAU / 5));
        int[] colors = KamekShapeEffect.COLORS;
        for (int i = 0; i < points; i++) {
            float angle = i * MathHelper.TAU / points;
            int x = Math.round(cx + MathHelper.cos(angle) * radius);
            int y = Math.round(cy + MathHelper.sin(angle) * radius);
            int rgb = lerpColor(colors[(i * colors.length / points) % colors.length], 0xFFFFFF, 0.2F);
            context.fill(x + 1, y + 1, x + 3, y + 3, (alpha / 3 << 24));
            context.fill(x, y, x + 2, y + 2, (alpha << 24) | rgb);
        }
        // The travelling sparkle, with a short fading tail
        for (int k = 0; k < 4; k++) {
            float angle = time * 0.09F - k * 0.12F - MathHelper.HALF_PI;
            drawSprite(context, SPARKLE, cx + MathHelper.cos(angle) * radius, cy + MathHelper.sin(angle) * radius,
                    0xFFFFFF, (int) ((200 - k * 50) * fade));
        }
    }

    /**
     * A soft, glowing, slightly irregular brush stroke in Kamek's four colours (flowing around), a fine inner line of
     * the token's colour, and twinkling sparkles riding on it.
     */
    private void drawMagicCircle(DrawContext context, float cx, float cy, float radius, float time, float flash, int thickness) {
        if (radius < 1) return;
        int points = Math.max(48, (int) (radius * MathHelper.TAU * 0.9F));
        int[] colors = KamekShapeEffect.COLORS;
        for (int i = 0; i < points; i++) {
            float angle = i * MathHelper.TAU / points;
            // Brush irregularity, slowly flowing
            float wobble = MathHelper.sin(angle * 6 + time * 0.2F) * 0.9F + MathHelper.sin(angle * 11 - time * 0.13F) * 0.5F;
            float r = radius + wobble;
            int x = Math.round(cx + MathHelper.cos(angle) * r);
            int y = Math.round(cy + MathHelper.sin(angle) * r);
            float along = (angle / MathHelper.TAU * colors.length + time * 0.03F) % colors.length;
            int index = (int) along;
            float blend = along - index;
            blend = blend * blend * (3 - 2 * blend);
            int rgb = lerpColor(colors[index], colors[(index + 1) % colors.length], blend);
            if (flash > 0) rgb = lerpColor(rgb, 0xFFFFFF, flash);
            context.fill(x - 1 - thickness / 2, y - 1 - thickness / 2, x + 1 + thickness, y + 1 + thickness, 0x26000000 | rgb);
            context.fill(x, y, x + thickness, y + thickness, 0xF0000000 | rgb);
        }
        // The token's colour, as a fine dotted line inside
        if (color != NO_COLOR && radius > 6) {
            for (int i = 0; i < points; i += 3) {
                float angle = i * MathHelper.TAU / points - time * 0.02F;
                int x = Math.round(cx + MathHelper.cos(angle) * (radius - 4));
                int y = Math.round(cy + MathHelper.sin(angle) * (radius - 4));
                context.fill(x, y, x + 1, y + 1, 0xA0000000 | color);
            }
        }
        // Sparkles riding on the circle
        for (int i = 0; i < 5; i++) {
            float angle = time * 0.05F + i * MathHelper.TAU / 5;
            float twinkle = 0.5F + 0.5F * MathHelper.sin(time * 0.6F + i * 2.1F);
            drawSprite(context, SPARKLE, cx + MathHelper.cos(angle) * radius, cy + MathHelper.sin(angle) * radius,
                    i % 2 == 0 ? 0xFFFFFF : colors[i % colors.length], (int) (120 + 135 * twinkle));
        }
    }

    /** The drawn stroke sliding into the clean circle around the mob (eased, the loop closing as it goes). */
    private List<float[]> morphPoints(float delta, float radius) {
        float t = MathHelper.clamp((morphTicks + delta) / MORPH_TICKS, 0, 1);
        t = t * t * (3 - 2 * t);
        List<float[]> points = new ArrayList<>(morphFrom.size() + 1);
        for (int i = 0; i < morphFrom.size(); i++) {
            float[] from = morphFrom.get(i);
            float angle = morphAngles.get(i);
            float toX = centerX + MathHelper.cos(angle) * radius, toY = centerY + MathHelper.sin(angle) * radius;
            points.add(new float[]{MathHelper.lerp(t, from[0], toX), MathHelper.lerp(t, from[1], toY)});
        }
        if (!points.isEmpty()) points.add(points.getFirst());
        return points;
    }

    /**
     * A freehand stroke in the same brush as the magic circle: soft glow, Kamek's four colours flowing along it, and
     * sparkles twinkling on it.
     */
    private int strokeQuads;

    private void drawStroke(DrawContext context, List<float[]> points, float time) {
        int[] colors = KamekShapeEffect.COLORS;
        float length = 0, nextSparkle = 20;
        for (int i = 1; i < points.size(); i++) {
            float[] a = points.get(i - 1), b = points.get(i);
            float segment = (float) Math.hypot(b[0] - a[0], b[1] - a[1]);
            if (!Float.isFinite(segment)) continue;
            // (one dot per pixel, but never thousands for a single segment)
            int steps = MathHelper.clamp((int) segment, 1, 512);
            for (int s = 0; s < steps; s++) {
                float f = (float) s / steps;
                int x = Math.round(MathHelper.lerp(f, a[0], b[0]));
                int y = Math.round(MathHelper.lerp(f, a[1], b[1]));
                float along = ((length + f * segment) / 30F + time * 0.03F) % colors.length;
                int index = (int) along;
                float blend = along - index;
                blend = blend * blend * (3 - 2 * blend);
                int rgb = lerpColor(colors[index], colors[(index + 1) % colors.length], blend);
                context.fill(x - 1, y - 1, x + 2, y + 2, 0x26000000 | rgb);
                context.fill(x, y, x + 1, y + 1, 0xF0000000 | rgb);
                // A long stroke is thousands of quads: flush them regularly (one huge batch overflowed the GUI's
                // vertex buffer and crashed the game)
                if (++strokeQuads % 2048 == 0) context.draw();
            }
            length += segment;
            if (length - nextSparkle > 400) nextSparkle = length - 400;
            while (length >= nextSparkle) {
                float twinkle = 0.5F + 0.5F * MathHelper.sin(time * 0.6F + nextSparkle * 0.1F);
                drawSprite(context, SPARKLE, b[0], b[1], (int) nextSparkle % 80 < 40 ? 0xFFFFFF : colors[(int) (nextSparkle / 40) % colors.length],
                        (int) (110 + 145 * twinkle));
                nextSparkle += 40;
            }
        }
    }

    private void drawShapes(DrawContext context, float delta) {
        for (GuiShape shape : shapes) {
            float life = (shape.age + delta) / shape.life;
            int alpha = (int) (255 * (life < 0.6F ? 1 : Math.max(0, 1 - (life - 0.6F) / 0.4F)));
            if (alpha <= 8) continue;
            drawSprite(context, shape.sprite, shape.x + shape.vx * delta, shape.y + shape.vy * delta, shape.color, alpha);
        }
    }

    private void drawSprite(DrawContext context, int sprite, float x, float y, int rgb, int alpha) {
        context.drawTexture(RenderLayer::getGuiTextured, SPRITES[sprite], Math.round(x) - 4, Math.round(y) - 4, 0, 0,
                8, 8, 8, 8, (MathHelper.clamp(alpha, 0, 255) << 24) | rgb);
    }

    /**
     * The only text on screen: the spell's incantation at the top, its letters in Kamek's colours with a gentle wave
     * and a shimmer running through them, next to a small pixel-art ring (the shape to draw). A scribble that is not
     * a circle gets a brief message under it.
     */
    private void drawTexts(DrawContext context, float time) {
        String spell = Text.translatableWithFallback("screen.steveparty.token_spell.incantation", "Tokenificus!").getString();
        int scale = 2;
        int ringSize = RING.length * scale;
        int gap = 6;
        int textWidth = textRenderer.getWidth(spell) * scale;
        int x0 = (width - ringSize - gap - textWidth) / 2;
        int y0 = 6;
        drawRingIcon(context, x0, y0 + (textRenderer.fontHeight * scale - ringSize) / 2 - 1, scale, time);

        var matrices = context.getMatrices();
        float x = x0 + ringSize + gap;
        int[] colors = KamekShapeEffect.COLORS;
        for (int i = 0; i < spell.length(); i++) {
            String letter = String.valueOf(spell.charAt(i));
            // Pure spell colours, one per letter, shifting along slowly (blends between them look muddy on letters)
            int rgb = colors[Math.floorMod(i + (int) (time / 10), colors.length)];
            float shimmer = (float) Math.pow(Math.max(0, MathHelper.sin(time * 0.12F - i * 0.45F)), 12);
            rgb = lerpColor(rgb, 0xFFFFFF, shimmer * 0.8F);
            float wave = MathHelper.sin(time * 0.2F + i * 0.7F) * 1.5F;
            matrices.push();
            matrices.translate(x, y0 + wave, 0);
            // Dark outline (one GUI pixel around), so every colour reads on the sky and on the grass
            for (int[] offset : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}}) {
                matrices.push();
                matrices.translate(offset[0], offset[1], 0);
                matrices.scale(scale, scale, 1);
                context.drawText(textRenderer, letter, 0, 0, 0xFF1E1530, false);
                matrices.pop();
            }
            matrices.scale(scale, scale, 1);
            context.drawText(textRenderer, letter, 0, 0, 0xFF000000 | rgb, false);
            matrices.pop();
            x += textRenderer.getWidth(letter) * scale;
        }

        if (ticks < failedUntil) {
            // Well below the title (its letters wave by up to 2 pixels), on a small dark plate so it reads on the sky
            float fade = MathHelper.clamp((failedUntil - ticks) / 10F, 0, 1);
            if (fade > 0.05F) {
                Text message = Text.translatableWithFallback("screen.steveparty.token_spell.not_a_circle", "That's not a circle: try again");
                int messageWidth = textRenderer.getWidth(message);
                int messageY = y0 + textRenderer.fontHeight * scale + 9;
                int left = (width - messageWidth) / 2;
                context.fill(left - 4, messageY - 3, left + messageWidth + 4, messageY + textRenderer.fontHeight + 2,
                        ((int) (0x90 * fade) << 24) | 0x1E1530);
                context.drawText(textRenderer, message, left, messageY, ((int) (255 * fade) << 24) | 0xFFFFFF, false);
            }
        }
    }

    /** Pixel-art ring (1 = drawn), the shape the player has to draw. */
    private static final String[] RING = {
            "...#####...",
            "..#.....#..",
            ".#.......#.",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            "#.........#",
            ".#.......#.",
            "..#.....#..",
            "...#####..."};

    /** The ring icon: Kamek's four colours flowing around it, a bright pixel running round. */
    private void drawRingIcon(DrawContext context, int x0, int y0, int scale, float time) {
        int[] colors = KamekShapeEffect.COLORS;
        float center = (RING.length - 1) / 2F;
        float shimmerAngle = (time * 0.15F) % MathHelper.TAU;
        for (int row = 0; row < RING.length; row++) {
            for (int column = 0; column < RING[row].length(); column++) {
                if (RING[row].charAt(column) != '#') continue;
                float angle = (float) Math.atan2(row - center, column - center) + MathHelper.PI;
                float along = (angle / MathHelper.TAU * colors.length + time * 0.03F) % colors.length;
                int index = (int) along;
                float blend = along - index;
                int rgb = lerpColor(colors[index], colors[(index + 1) % colors.length], blend * blend * (3 - 2 * blend));
                float distance = Math.abs(MathHelper.wrapDegrees((angle - shimmerAngle) * MathHelper.DEGREES_PER_RADIAN));
                if (distance < 25) rgb = lerpColor(rgb, 0xFFFFFF, 1 - distance / 25);
                int x = x0 + column * scale, y = y0 + row * scale;
                // Thick pixels with a soft shadow, like the letters
                context.fill(x + 1, y + 1, x + scale + 1, y + scale + 1, 0x60000000);
                context.fill(x, y, x + scale, y + scale, 0xFF000000 | rgb);
            }
        }
    }

    private static int lerpColor(int from, int to, float t) {
        int r = (int) MathHelper.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) MathHelper.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) MathHelper.lerp(t, from & 0xFF, to & 0xFF);
        return (r << 16) | (g << 8) | b;
    }
}
